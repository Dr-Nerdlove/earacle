package io.github.drnerdlove.earacle

import io.github.drnerdlove.earacle.lyrics.Lyrics
import io.github.drnerdlove.earacle.recognition.RecognitionResult

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

/**
 * The state after a recording reported [progress]: only a state that is still [UiState.Listening] takes it. The
 * recorder's blocking read can finish after the recording was cancelled (back gesture), and its callback must not
 * bring the listening screen back over the start screen.
 */
fun listeningProgress(current: UiState, progress: Float): UiState =
    if (current is UiState.Listening) UiState.Listening(progress) else current
