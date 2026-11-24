package io.github.drnerdlove.earacle.lyrics

import kotlin.math.abs

/**
 * Manual timing correction ("shift") for the lyrics of the current song.
 *
 * Direction (decided once): the shift is the number of milliseconds ADDED to the song position. A
 * positive shift moves the lyrics ahead, so the next line comes sooner. It helps when the lyrics lag
 * behind the singer.
 */
object SyncShift {
    /** The shift never leaves ±this many milliseconds. */
    const val MAX_SHIFT_MS = 30_000L

    /** Step of the fine-tuning buttons. */
    const val STEP_MS = 500L

    /** A tap only counts for lines up to this far (before or after) from the current position. */
    const val TAP_RANGE_MS = 30_000L

    /** Extra milliseconds added when a line is tapped, in case taps feel late on the phone. Tunable. */
    const val TAP_LEAD_MS = 0L
}

/** The position used for the lyrics and the shown time: raw position plus [shiftMs], never below 0. */
fun syncedPositionMs(rawPositionMs: Long, shiftMs: Long): Long = (rawPositionMs + shiftMs).coerceAtLeast(0L)

/** Keeps a shift within ±[SyncShift.MAX_SHIFT_MS]. */
fun clampShift(shiftMs: Long): Long = shiftMs.coerceIn(-SyncShift.MAX_SHIFT_MS, SyncShift.MAX_SHIFT_MS)

/**
 * The shift that makes the line starting at [lineTimeMs] the current one right now:
 * line time minus raw position (plus the small lead), within the allowed range.
 */
fun shiftForTap(lineTimeMs: Long, rawPositionMs: Long): Long =
    clampShift(lineTimeMs - rawPositionMs + SyncShift.TAP_LEAD_MS)

/** True when the tapped line is close enough to the current (shifted) position to be a deliberate tap. */
fun isTapInRange(lineTimeMs: Long, syncedPositionMs: Long): Boolean =
    abs(lineTimeMs - syncedPositionMs) <= SyncShift.TAP_RANGE_MS

/** The shift after pressing a fine-tuning button by [deltaMs]. */
fun nudgedShift(shiftMs: Long, deltaMs: Long): Long = clampShift(shiftMs + deltaMs)

/** The shift as shown to the user: "+1.5 s", "−0.5 s" or "±0 s". Sub-50 ms values count as zero. */
fun formatShift(shiftMs: Long): String {
    val tenths = Math.round(shiftMs / 100.0)
    if (tenths == 0L) return "±0 s"
    val sign = if (tenths > 0) "+" else "−"
    val abs = abs(tenths)
    return "$sign${abs / 10}.${abs % 10} s"
}
