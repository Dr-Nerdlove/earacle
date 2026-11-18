package io.github.drnerdlove.earacle.recognition

import java.util.Locale

/**
 * Current position in the song: Shazam's offset plus the time passed since the recording started.
 * The offset is the song position at the START of the recorded sample (confirmed on a real phone).
 */
fun positionSeconds(offsetSeconds: Double, recordingStartedAtElapsedMs: Long, nowElapsedMs: Long): Double =
    offsetSeconds + (nowElapsedMs - recordingStartedAtElapsedMs) / 1000.0

/** "m:ss", or "h:mm:ss" from one hour on; negative values show "0:00". */
fun formatPosition(seconds: Double): String {
    val total = if (seconds < 0.0) 0L else seconds.toLong()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.ROOT, "%d:%02d", m, s)
}
