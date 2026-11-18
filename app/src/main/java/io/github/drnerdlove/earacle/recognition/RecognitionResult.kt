package io.github.drnerdlove.earacle.recognition

/** Outcome of one recognition attempt. */
sealed class RecognitionResult {
    /**
     * A recognized song. [offsetSeconds] is Shazam's reported position in the song
     * (assumed to be the position at the start of the recorded sample).
     */
    data class Match(
        val title: String,
        val artist: String,
        val offsetSeconds: Double,
        val shazamKey: String?,
        val coverArtUrl: String?,
    ) : RecognitionResult()

    /** The service answered, but does not know the song. */
    data object NoMatch : RecognitionResult()

    data class Failure(val reason: FailureReason) : RecognitionResult()
}

enum class FailureReason { NETWORK, RATE_LIMITED, SERVER }

/** A recorded audio window. [startedAtElapsedMs] is SystemClock.elapsedRealtime() at its start. */
class Recording(
    val samples: ShortArray,
    val sampleRateHz: Int,
    val startedAtElapsedMs: Long,
)

/** Recognition stays behind this interface: the unofficial Shazam API can break at any time. */
interface SongRecognizer {
    suspend fun recognize(recording: Recording): RecognitionResult
}
