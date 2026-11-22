package io.github.drnerdlove.earacle.recognition

import java.util.Locale

/** Pure rules for listening along to a song (no Android classes, so they can be tested). */
object SongTracking {
    /** RMS (16-bit samples) below which a recording counts as silence: about -55 dBFS, so quiet music is still sent. */
    const val SILENCE_RMS = 60.0

    /** Longest pause while the song's length is unknown, so a song change is not missed for too long. */
    const val UNKNOWN_DURATION_MAX_PAUSE_MS = 45_000L

    /** Longer pause after the service said it is limiting requests. */
    const val RATE_LIMIT_PAUSE_MS = 60_000L

    /** Consecutive answers without a match before listening along stops (about 2 minutes without music). */
    const val MAX_MISSES = 4

    /** Listen again this long after the song should have ended. */
    const val END_GRACE_SECONDS = 2.0

    /** The same Shazam key, or else the same title and artist ignoring case and extra spaces. */
    fun isSameSong(a: RecognitionResult.Match, b: RecognitionResult.Match): Boolean {
        if (a.shazamKey != null && a.shazamKey == b.shazamKey) return true
        return normalize(a.title) == normalize(b.title) && normalize(a.artist) == normalize(b.artist)
    }

    /** True when the samples are (nearly) silent, or empty: there is nothing to recognize, so nothing is sent. */
    fun isSilent(samples: ShortArray): Boolean {
        if (samples.isEmpty()) return true
        var sum = 0.0
        for (v in samples) sum += v.toDouble() * v
        return Math.sqrt(sum / samples.size) < SILENCE_RMS
    }

    /**
     * Pause after a recognition has finished, before the next 12 s recording. [confirmations] is the number of
     * same-song matches in a row since the song was first shown; the more sure we are, the rarer the checks
     * (recordings start about 27 s, 1 min, 2 min apart). Capped when the song's length is unknown.
     */
    fun pauseMs(confirmations: Int, durationKnown: Boolean): Long {
        val pause = when {
            confirmations <= 0 -> 15_000L
            confirmations == 1 -> 45_000L
            else -> 105_000L
        }
        return if (durationKnown) pause else minOf(pause, UNKNOWN_DURATION_MAX_PAUSE_MS)
    }

    /**
     * How long to wait before the next recording: [pauseMs], or less if the song ends sooner. Once the end has
     * passed (the check at the end found no new song, e.g. a presenter talking), the normal pause applies again,
     * so the app does not record back to back and give up after a few seconds of speech.
     */
    fun nextListenDelayMs(positionSeconds: Double, durationSeconds: Double?, pauseMs: Long): Long {
        if (durationSeconds == null) return maxOf(0L, pauseMs)
        val untilEndMs = ((durationSeconds + END_GRACE_SECONDS - positionSeconds) * 1000).toLong()
        if (untilEndMs < 0) return maxOf(0L, pauseMs)
        return minOf(pauseMs, untilEndMs)
    }

    /** The position to show: never beyond the song's length when that is known. */
    fun displayPosition(positionSeconds: Double, durationSeconds: Double?): Double =
        if (durationSeconds == null) positionSeconds else minOf(positionSeconds, durationSeconds)

    private fun normalize(s: String): String =
        s.trim().lowercase(Locale.ROOT).replace(Regex("""\s+"""), " ")
}
