package io.github.drnerdlove.earacle

import android.os.SystemClock
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.drnerdlove.earacle.audio.MicrophoneException
import io.github.drnerdlove.earacle.audio.MicrophoneRecorder
import io.github.drnerdlove.earacle.lyrics.Lyrics
import io.github.drnerdlove.earacle.lyrics.StageSettings
import io.github.drnerdlove.earacle.lyrics.StageStore
import io.github.drnerdlove.earacle.lyrics.SyncShift
import io.github.drnerdlove.earacle.lyrics.LrclibClient
import io.github.drnerdlove.earacle.lyrics.LyricsProvider
import io.github.drnerdlove.earacle.lyrics.isTapInRange
import io.github.drnerdlove.earacle.lyrics.nudgedShift
import io.github.drnerdlove.earacle.lyrics.shiftForTap
import io.github.drnerdlove.earacle.lyrics.songDurationSeconds
import io.github.drnerdlove.earacle.lyrics.syncedPositionMs
import io.github.drnerdlove.earacle.recognition.FailureReason
import io.github.drnerdlove.earacle.recognition.RecognitionResult
import io.github.drnerdlove.earacle.recognition.ShazamRecognizer
import io.github.drnerdlove.earacle.recognition.SongRecognizer
import io.github.drnerdlove.earacle.recognition.SongTracking
import io.github.drnerdlove.earacle.recognition.positionSeconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    ) : UiState
    data object NotFound : UiState
    data class Error(val kind: ErrorKind) : UiState
    data object PermissionDenied : UiState
}

class MainViewModel(
    private val recognizer: SongRecognizer = ShazamRecognizer(),
    private val lyricsProvider: LyricsProvider = LrclibClient(),
) : ViewModel() {
    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var job: Job? = null
    private var trackingJob: Job? = null

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
                    loadLyrics(it.match)
                    startTracking()
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
     * The user tapped the lyric line starting at [lineTimeMs]: it is being sung right now, so the shift
     * is set to make it the current line. Returns false (and changes nothing) for a line too far away.
     */
    fun tapLine(lineTimeMs: Long): Boolean {
        var accepted = false
        _state.update { current ->
            if (current !is UiState.Found) return@update current
            val raw = rawPositionMs(current)
            if (!isTapInRange(lineTimeMs, syncedPositionMs(raw, current.shiftMs))) return@update current
            accepted = true
            current.copy(shiftMs = shiftForTap(lineTimeMs, raw))
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
                        loadLyrics(answer)
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
     * do not matter; a newer state for another song is left alone.
     */
    private fun loadLyrics(match: RecognitionResult.Match) {
        viewModelScope.launch {
            val lyrics = lyricsProvider.find(match.title, match.artist)
            _state.update { current ->
                if (current is UiState.Found && current.lyrics == null && SongTracking.isSameSong(current.match, match)) {
                    current.copy(lyrics = lyrics)
                } else {
                    current
                }
            }
        }
    }
}
