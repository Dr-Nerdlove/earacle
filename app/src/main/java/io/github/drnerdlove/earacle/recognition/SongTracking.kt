package io.github.drnerdlove.earacle.recognition

import java.util.Locale

/** Pure rules for listening along to a song (no Android classes, so they can be tested). */
object SongTracking {
    /** Pause after a recognition has finished, before the next 12 s recording (about every 27 s in all). */
    const val RELISTEN_PAUSE_MS = 15_000L

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

    /** How long to wait before the next recording: [pauseMs], or less if the song ends sooner. */
    fun nextListenDelayMs(positionSeconds: Double, durationSeconds: Double?, pauseMs: Long): Long {
        if (durationSeconds == null) return maxOf(0L, pauseMs)
        val untilEndMs = ((durationSeconds + END_GRACE_SECONDS - positionSeconds) * 1000).toLong()
        return maxOf(0L, minOf(pauseMs, untilEndMs))
    }

    /** The position to show: never beyond the song's length when that is known. */
    fun displayPosition(positionSeconds: Double, durationSeconds: Double?): Double =
        if (durationSeconds == null) positionSeconds else minOf(positionSeconds, durationSeconds)

    private fun normalize(s: String): String =
        s.trim().lowercase(Locale.ROOT).replace(Regex("""\s+"""), " ")
}
