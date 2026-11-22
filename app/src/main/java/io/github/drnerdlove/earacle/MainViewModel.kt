package io.github.drnerdlove.earacle

import android.os.SystemClock
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.drnerdlove.earacle.audio.MicrophoneException
import io.github.drnerdlove.earacle.audio.MicrophoneRecorder
import io.github.drnerdlove.earacle.lyrics.Lyrics
import io.github.drnerdlove.earacle.lyrics.LrclibClient
import io.github.drnerdlove.earacle.lyrics.LyricsProvider
import io.github.drnerdlove.earacle.lyrics.songDurationSeconds
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
        trackingJob?.cancel()
        _state.value = UiState.Listening(0f)
        job = viewModelScope.launch {
            try {
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
        var pauseMs = SongTracking.RELISTEN_PAUSE_MS
        while (true) {
            val found = _state.value as? UiState.Found ?: return
            val position = positionSeconds(
                found.match.offsetSeconds,
                found.recordingStartedAtElapsedMs,
                SystemClock.elapsedRealtime(),
            )
            delay(SongTracking.nextListenDelayMs(position, found.lyrics.songDurationSeconds, pauseMs))
            appVisible.first { it }
            setRelistening(true)
            val result = try {
                val recording = MicrophoneRecorder.record { }
                recognizer.recognize(recording).let { it to recording.startedAtElapsedMs }
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
            pauseMs = SongTracking.RELISTEN_PAUSE_MS
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
                    if (changed) loadLyrics(answer)
                }
                RecognitionResult.NoMatch, is RecognitionResult.Failure -> {
                    setRelistening(false)
                    misses++
                    if (answer is RecognitionResult.Failure && answer.reason == FailureReason.RATE_LIMITED) {
                        pauseMs = SongTracking.RATE_LIMIT_PAUSE_MS
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
