package io.github.drnerdlove.earacle

import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.drnerdlove.earacle.audio.MicrophoneException
import io.github.drnerdlove.earacle.audio.MicrophoneRecorder
import io.github.drnerdlove.earacle.lyrics.Lyrics
import io.github.drnerdlove.earacle.lyrics.LrclibClient
import io.github.drnerdlove.earacle.lyrics.LyricsProvider
import io.github.drnerdlove.earacle.recognition.FailureReason
import io.github.drnerdlove.earacle.recognition.RecognitionResult
import io.github.drnerdlove.earacle.recognition.ShazamRecognizer
import io.github.drnerdlove.earacle.recognition.SongRecognizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    val isBusy: Boolean
        get() = _state.value is UiState.Listening || _state.value is UiState.Recognizing

    fun onPermissionDenied() {
        _state.value = UiState.PermissionDenied
    }

    /** The caller must hold the RECORD_AUDIO permission. */
    @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    fun recognize() {
        if (isBusy) return
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
                    is RecognitionResult.Failure -> UiState.Error(
                        when (result.reason) {
                            FailureReason.NETWORK -> ErrorKind.NETWORK
                            FailureReason.RATE_LIMITED -> ErrorKind.RATE_LIMITED
                            FailureReason.SERVER -> ErrorKind.SERVER
                        },
                    )
                }
                (_state.value as? UiState.Found)?.let(::loadLyrics)
            } catch (e: CancellationException) {
                throw e
            } catch (e: MicrophoneException) {
                _state.value = UiState.Error(ErrorKind.MICROPHONE)
            } catch (e: SecurityException) {
                _state.value = UiState.PermissionDenied
            }
        }
    }

    /** Looks up the lyrics in the background and adds them to [found] unless a newer state replaced it. */
    private fun loadLyrics(found: UiState.Found) {
        viewModelScope.launch {
            val lyrics = lyricsProvider.find(found.match.title, found.match.artist)
            _state.compareAndSet(found, found.copy(lyrics = lyrics))
        }
    }
}
