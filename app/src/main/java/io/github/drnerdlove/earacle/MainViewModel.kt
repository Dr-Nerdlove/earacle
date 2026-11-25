package io.github.drnerdlove.earacle

import android.os.SystemClock
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.drnerdlove.earacle.audio.MicrophoneException
import io.github.drnerdlove.earacle.audio.MicrophoneRecorder
import io.github.drnerdlove.earacle.history.HistoryEntry
import io.github.drnerdlove.earacle.history.HistoryLog
import io.github.drnerdlove.earacle.history.HistoryStore
import io.github.drnerdlove.earacle.history.SongHistory
import io.github.drnerdlove.earacle.lyrics.LrclibApi
import io.github.drnerdlove.earacle.lyrics.LrclibTrack
import io.github.drnerdlove.earacle.lyrics.Lyrics
import io.github.drnerdlove.earacle.lyrics.LyricsSearch
import io.github.drnerdlove.earacle.lyrics.StageSettings
import io.github.drnerdlove.earacle.lyrics.StageStore
import io.github.drnerdlove.earacle.lyrics.SyncShift
import io.github.drnerdlove.earacle.lyrics.LrclibClient
import io.github.drnerdlove.earacle.lyrics.LyricsProvider
import io.github.drnerdlove.earacle.lyrics.listensAlong
import io.github.drnerdlove.earacle.lyrics.lrclibId
import io.github.drnerdlove.earacle.lyrics.nudgedShift
import io.github.drnerdlove.earacle.lyrics.searchResultsForPicker
import io.github.drnerdlove.earacle.lyrics.tapResult
import io.github.drnerdlove.earacle.lyrics.songDurationSeconds
import io.github.drnerdlove.earacle.recognition.FailureReason
import io.github.drnerdlove.earacle.recognition.RecognitionResult
import io.github.drnerdlove.earacle.recognition.ShazamRecognizer
import io.github.drnerdlove.earacle.recognition.SongRecognizer
import io.github.drnerdlove.earacle.recognition.SongTracking
import io.github.drnerdlove.earacle.recognition.positionSeconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import java.util.UUID

enum class ErrorKind { MICROPHONE, NETWORK, RATE_LIMITED, SERVER }

sealed interface UiState {
    data object Idle : UiState
    data class Listening(val progress: Float) : UiState
    data object Recognizing : UiState
    data class Found(
        val match: RecognitionResult.Match,
        val recordingStartedAtElapsedMs: Long,
        /** Null while the lyrics are still being looked up. */
        val lyrics: Lyrics? = null,
        /** True while the app is recording again to listen along. */
        val relistening: Boolean = false,
        /**
         * Manual timing correction in milliseconds, added to the position (see [SyncShift]). It belongs
         * to the song: the same song keeps it, a new Found state starts at 0.
         */
        val shiftMs: Long = 0,
        /**
         * True for a song the user picked in the search (no recognition): listening along does not run, and
         * every tap on a line sets the clock.
         */
        val byHand: Boolean = false,
        /**
         * False for a song picked by hand until the first tap on a line: nobody knows where in the song the
         * music is yet, so nothing is highlighted. Always true for a recognized song.
         */
        val clockRunning: Boolean = true,
    ) : UiState
    data object NotFound : UiState
    data class Error(val kind: ErrorKind) : UiState
    data object PermissionDenied : UiState
}

/** The search screen (point 7). It lives next to [UiState], so a running recognition or listening along is not disturbed. */
data class SearchState(
    val query: String,
    /** Null until a search has finished; empty when nothing was found. */
    val results: List<LrclibTrack>? = null,
    val loading: Boolean = false,
    /** The last search failed (no connection or an unusable answer). */
    val failed: Boolean = false,
)

class MainViewModel(
    private val recognizer: SongRecognizer = ShazamRecognizer(),
    private val lyricsProvider: LyricsProvider = LrclibClient(),
    private val lyricsSearch: LyricsSearch = LrclibClient(),
) : ViewModel() {
    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var job: Job? = null
    private var trackingJob: Job? = null
    private var searchJob: Job? = null

    private val _search = MutableStateFlow<SearchState?>(null)

    /** The search screen while it is open, else null. */
    val search: StateFlow<SearchState?> = _search.asStateFlow()

    /** Opens the search screen; [prefill] is the text of the field (the recognized title and artist, if any). */
    fun openSearch(prefill: String = "") {
        searchJob?.cancel()
        _search.value = SearchState(prefill)
    }

    fun setSearchQuery(query: String) {
        _search.update { it?.copy(query = query) }
    }

    fun closeSearch() {
        searchJob?.cancel()
        _search.value = null
    }

    /** Searches LRCLIB for the text in the field; a blank field does nothing. */
    fun searchSongs() {
        val query = _search.value?.query?.trim().orEmpty()
        if (query.isEmpty()) return
        searchJob?.cancel()
        _search.update { it?.copy(loading = true, failed = false, results = null) }
        searchJob = viewModelScope.launch {
            val found = lyricsSearch.search(query)
            _search.update {
                it?.copy(loading = false, failed = found == null, results = found?.let(::searchResultsForPicker))
            }
        }
    }

    /**
     * Opens the lyrics of a picked entry without a second lookup. The clock does not run until the first tap on a
     * line; listening along stops (it only belongs to recognized songs).
     */
    fun pickResult(track: LrclibTrack) {
        searchJob?.cancel()
        trackingJob?.cancel()
        val match = RecognitionResult.Match(
            title = track.trackName,
            artist = track.artistName,
            offsetSeconds = 0.0,
            shazamKey = null,
            coverArtUrl = null,
        )
        val lyrics = LrclibApi.toLyrics(track)
        _state.value = UiState.Found(
            match,
            recordingStartedAtElapsedMs = SystemClock.elapsedRealtime(),
            lyrics = lyrics,
            byHand = true,
            clockRunning = false,
        )
        _search.value = null
        _historyOpen.value = false
        recordInHistory(match, byHand = true, lyrics)
    }

    // --- History (point 8) ---

    private var historyLog: HistoryLog? = null

    /** All file access of the history runs here, one call after the other, off the main thread. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val historyDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val _history = MutableStateFlow(SongHistory())

    /** The history, oldest first (the history page lists it the other way round). */
    val history: StateFlow<SongHistory> = _history.asStateFlow()

    private val _historyOpen = MutableStateFlow(false)

    /** True while the history page is shown. It lies over the search and the lyrics screen, which stay as they are. */
    val historyOpen: StateFlow<Boolean> = _historyOpen.asStateFlow()

    /** Loads the history from [store] once; later calls (after a rotation) change nothing. */
    fun attachHistoryStore(store: HistoryStore) {
        if (historyLog != null) return
        val log = HistoryLog(store)
        historyLog = log
        viewModelScope.launch(historyDispatcher) { _history.value = log.load() }
    }

    fun openHistory() {
        _historyOpen.value = true
    }

    fun closeHistory() {
        _historyOpen.value = false
    }

    /**
     * Adds the song to the history unless it is the same as the newest entry heard a moment ago. Called once the
     * lyrics lookup has finished, so the entry can carry the LRCLIB id; [timeMs] is when the song was heard.
     */
    private fun recordInHistory(
        match: RecognitionResult.Match,
        byHand: Boolean,
        lyrics: Lyrics?,
        timeMs: Long = System.currentTimeMillis(),
    ) {
        val log = historyLog ?: return
        val entry = HistoryEntry(UUID.randomUUID().toString(), timeMs, match.title, match.artist, byHand, lyrics.lrclibId)
        viewModelScope.launch(historyDispatcher) {
            if (log.record(entry)) _history.value = log.history
        }
    }

    fun deleteHistoryEntry(id: String) {
        val log = historyLog ?: return
        viewModelScope.launch(historyDispatcher) {
            log.delete(id)
            _history.value = log.history
        }
    }

    /** Puts the entry deleted last back where it was. */
    fun undoHistoryDelete() {
        val log = historyLog ?: return
        viewModelScope.launch(historyDispatcher) {
            log.undoDelete()
            _history.value = log.history
        }
    }

    fun clearHistory() {
        val log = historyLog ?: return
        viewModelScope.launch(historyDispatcher) {
            log.clear()
            _history.value = log.history
        }
    }

    /**
     * Opens a song of the history like one picked by hand: nothing is highlighted until the first tap on a line. The
     * lyrics are exactly the version of the entry when it has an LRCLIB id, else the normal lookup by title and
     * artist runs. This adds no new entry.
     */
    fun openFromHistory(entry: HistoryEntry) {
        searchJob?.cancel()
        trackingJob?.cancel()
        val match = RecognitionResult.Match(
            title = entry.title,
            artist = entry.artist,
            offsetSeconds = 0.0,
            shazamKey = null,
            coverArtUrl = null,
        )
        _state.value = UiState.Found(
            match,
            recordingStartedAtElapsedMs = SystemClock.elapsedRealtime(),
            byHand = true,
            clockRunning = false,
        )
        _search.value = null
        _historyOpen.value = false
        loadLyrics(match, lrclibId = entry.lrclibId, recordAtMs = null)
    }

    /** The microphone is silenced in the background (Android 11+), so listening along waits for the app to show. */
    private val appVisible = MutableStateFlow(false)

    private var stageStore: StageStore? = null
    private val _stage = MutableStateFlow(StageSettings())

    /** Stage mode on/off and its text size. Lives here so a rotation keeps it; [StageStore] keeps it across runs. */
    val stage: StateFlow<StageSettings> = _stage.asStateFlow()

    /** Loads the saved stage mode settings once; later calls (after a rotation) change nothing. */
    fun attachStageStore(store: StageStore) {
        if (stageStore != null) return
        stageStore = store
        _stage.value = store.load()
    }

    fun setStageMode(enabled: Boolean) = updateStage { it.withEnabled(enabled) }

    /** One step larger ([up] = true) or smaller; stops at the limits. */
    fun changeStageTextSize(up: Boolean) = updateStage { it.withTextStep(up) }

    private fun updateStage(change: (StageSettings) -> StageSettings) {
        val updated = change(_stage.value)
        _stage.value = updated
        stageStore?.save(updated)
    }

    val isBusy: Boolean
        get() = _state.value is UiState.Listening || _state.value is UiState.Recognizing

    fun setAppVisible(visible: Boolean) {
        appVisible.value = visible
    }

    fun onPermissionDenied() {
        _state.value = UiState.PermissionDenied
    }

    /** The caller must hold the RECORD_AUDIO permission. */
    @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    fun recognize() {
        if (isBusy) return
        // A recording for listening along may still hold the microphone: stop it and wait until it is released.
        val tracking = trackingJob
        tracking?.cancel()
        _state.value = UiState.Listening(0f)
        job = viewModelScope.launch {
            try {
                tracking?.join()
                val recording = MicrophoneRecorder.record { progress ->
                    _state.value = UiState.Listening(progress)
                }
                _state.value = UiState.Recognizing
                _state.value = when (val result = recognizer.recognize(recording)) {
                    is RecognitionResult.Match -> UiState.Found(result, recording.startedAtElapsedMs)
                    RecognitionResult.NoMatch -> UiState.NotFound
                    is RecognitionResult.Failure -> UiState.Error(errorKind(result.reason))
                }
                (_state.value as? UiState.Found)?.let {
                    loadLyrics(it.match, recordAtMs = System.currentTimeMillis())
                    if (listensAlong(it.byHand)) startTracking()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: MicrophoneException) {
                _state.value = UiState.Error(ErrorKind.MICROPHONE)
            } catch (e: SecurityException) {
                _state.value = UiState.PermissionDenied
            }
        }
    }

    /**
     * Back to the start screen: stops a running recognition and listening along. A lyrics lookup that is still
     * running cannot bring the old song back (it only fills a [UiState.Found] that is on screen) but still adds
     * the song to the history.
     */
    fun goHome() {
        job?.cancel()
        trackingJob?.cancel()
        _state.value = UiState.Idle
    }

    /**
     * The user tapped the lyric line starting at [lineTimeMs]: it is being sung right now. In a recognized song
     * the shift is set to make it the current line (false and no change for a line too far away); in a song
     * picked by hand the clock starts at that line. See [tapResult].
     */
    fun tapLine(lineTimeMs: Long): Boolean {
        var accepted = false
        _state.update { current ->
            if (current !is UiState.Found) return@update current
            val raw = rawPositionMs(current)
            val result = tapResult(current.byHand, lineTimeMs, raw, current.shiftMs) ?: return@update current
            accepted = true
            val anchor = result.anchorMs
            if (anchor == null) {
                current.copy(shiftMs = result.shiftMs)
            } else {
                current.copy(
                    match = current.match.copy(offsetSeconds = anchor / 1000.0),
                    recordingStartedAtElapsedMs = SystemClock.elapsedRealtime(),
                    shiftMs = result.shiftMs,
                    clockRunning = true,
                )
            }
        }
        return accepted
    }

    /** Moves the lyrics by [deltaMs] (positive: ahead, the next line comes sooner). */
    fun nudgeShift(deltaMs: Long) {
        _state.update { if (it is UiState.Found) it.copy(shiftMs = nudgedShift(it.shiftMs, deltaMs)) else it }
    }

    fun resetShift() {
        _state.update { if (it is UiState.Found) it.copy(shiftMs = 0) else it }
    }

    /** Position from the recognition result, capped at the song length, without the manual shift. */
    private fun rawPositionMs(found: UiState.Found): Long {
        val seconds = SongTracking.displayPosition(
            positionSeconds(found.match.offsetSeconds, found.recordingStartedAtElapsedMs, SystemClock.elapsedRealtime()),
            found.lyrics.songDurationSeconds,
        )
        return (seconds * 1000).toLong()
    }

    private fun errorKind(reason: FailureReason) = when (reason) {
        FailureReason.NETWORK -> ErrorKind.NETWORK
        FailureReason.RATE_LIMITED -> ErrorKind.RATE_LIMITED
        FailureReason.SERVER -> ErrorKind.SERVER
    }

    @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    private fun startTracking() {
        trackingJob?.cancel()
        trackingJob = viewModelScope.launch { trackLoop() }
    }

    /**
     * Keeps listening along while the app is visible: re-recognizes the song every now and then,
     * follows song changes and corrects drift. Stops quietly (the display stays) after
     * [SongTracking.MAX_MISSES] answers without a match or when the microphone is unusable.
     */
    @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    private suspend fun trackLoop() {
        var misses = 0
        var confirmations = 0
        var rateLimited = false
        while (true) {
            val found = _state.value as? UiState.Found ?: return
            if (!listensAlong(found.byHand)) return
            val position = positionSeconds(
                found.match.offsetSeconds,
                found.recordingStartedAtElapsedMs,
                SystemClock.elapsedRealtime(),
            )
            val duration = found.lyrics.songDurationSeconds
            val pauseMs = if (rateLimited) {
                SongTracking.RATE_LIMIT_PAUSE_MS
            } else {
                SongTracking.pauseMs(confirmations, durationKnown = duration != null)
            }
            rateLimited = false
            delay(SongTracking.nextListenDelayMs(position, duration, pauseMs))
            appVisible.first { it }
            setRelistening(true)
            val result = try {
                val recording = MicrophoneRecorder.record { }
                // Silence is never sent to the service.
                val answer = if (SongTracking.isSilent(recording.samples)) {
                    RecognitionResult.NoMatch
                } else {
                    recognizer.recognize(recording)
                }
                answer to recording.startedAtElapsedMs
            } catch (e: CancellationException) {
                throw e
            } catch (e: MicrophoneException) {
                setRelistening(false)
                return
            } catch (e: SecurityException) {
                setRelistening(false)
                return
            }
            val (answer, startedAt) = result
            when (answer) {
                is RecognitionResult.Match -> {
                    misses = 0
                    var changed = false
                    _state.update { current ->
                        if (current !is UiState.Found) {
                            current
                        } else if (SongTracking.isSameSong(current.match, answer)) {
                            // Same song: correct the position, keep the lyrics.
                            current.copy(match = answer, recordingStartedAtElapsedMs = startedAt, relistening = false)
                        } else {
                            changed = true
                            UiState.Found(answer, startedAt)
                        }
                    }
                    if (changed) {
                        confirmations = 0
                        loadLyrics(answer, recordAtMs = System.currentTimeMillis())
                    } else {
                        confirmations++
                    }
                }
                RecognitionResult.NoMatch, is RecognitionResult.Failure -> {
                    setRelistening(false)
                    misses++
                    confirmations = 0
                    if (answer is RecognitionResult.Failure && answer.reason == FailureReason.RATE_LIMITED) {
                        rateLimited = true
                    }
                    if (misses >= SongTracking.MAX_MISSES) return
                }
            }
        }
    }

    private fun setRelistening(value: Boolean) {
        _state.update { if (it is UiState.Found && it.relistening != value) it.copy(relistening = value) else it }
    }

    /**
     * Looks up the lyrics in the background and adds them to the current [UiState.Found] if it still
     * shows the same song and has no lyrics yet. Other changes to it (position, relistening flag)
     * do not matter; a newer state for another song is left alone. With a [lrclibId] exactly that version is
     * loaded first. With a [recordAtMs] (when the song was heard) the song is added to the history afterwards, also when the screen has moved on.
     */
    private fun loadLyrics(match: RecognitionResult.Match, lrclibId: Long? = null, recordAtMs: Long?) {
        viewModelScope.launch {
            val lyrics = lrclibId?.let { lyricsProvider.findById(it) } ?: lyricsProvider.find(match.title, match.artist)
            _state.update { current ->
                if (current is UiState.Found && current.lyrics == null && SongTracking.isSameSong(current.match, match)) {
                    current.copy(lyrics = lyrics)
                } else {
                    current
                }
            }
            if (recordAtMs != null) recordInHistory(match, byHand = false, lyrics, recordAtMs)
        }
    }
}
