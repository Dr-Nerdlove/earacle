package io.github.drnerdlove.earacle.lyrics

/** Tunable values of the countdown dots. */
object CountdownTiming {
    /** A pause must last at least this long (from the estimated end of the last sung line) to get dots. */
    const val MIN_PAUSE_MS = 6_000L

    /** Number of dots. */
    const val DOTS = 3

    /** Time one dot stands for. */
    const val DOT_MS = 1_000L
}

/**
 * Dots shown in a long pause before the next line is sung.
 *
 * @property nextIndex index of the line that will be sung next (empty lines are skipped).
 * @property remainingMs time until that line starts.
 * @property litDots dots still lit: 3 while more than 2 s are left, 2 down to 1 s, 1 for the last second.
 */
data class Countdown(val nextIndex: Int, val remainingMs: Long, val litDots: Int)

/**
 * Dots still lit with [remainingMs] left: one per started second, from [CountdownTiming.DOTS] down to 0.
 * With exactly 3 s left all three are lit, with exactly 2 s two, with exactly 1 s one, at 0 s none, so the
 * last dot goes out just as the singer starts.
 */
fun litDots(remainingMs: Long): Int {
    if (remainingMs <= 0L) return 0
    val started = (remainingMs + CountdownTiming.DOT_MS - 1) / CountdownTiming.DOT_MS
    return minOf(started, CountdownTiming.DOTS.toLong()).toInt()
}

/**
 * The countdown at [positionMs], or null when no dots should be shown: outside a long pause, or when no
 * line follows (end of the song, or only empty lines left). [lines] must be sorted by time.
 *
 * The pause begins at the estimated end of the last line that has text (or at 0 before the first one), so
 * empty lines (instrumental parts) do not interrupt it, and ends when the next line with text starts.
 */
fun countdown(lines: List<LyricLine>, positionMs: Long): Countdown? {
    val current = currentLineIndex(lines, positionMs)
    var next = current + 1
    while (next < lines.size && lines[next].text.isBlank()) next++
    if (next >= lines.size) return null
    var lastSung = current
    while (lastSung >= 0 && lines[lastSung].text.isBlank()) lastSung--
    val pauseStart = if (lastSung < 0) 0L else estimatedLineEndMs(lines, lastSung)
    if (positionMs < pauseStart) return null
    val nextStart = lines[next].timeMs
    if (nextStart - pauseStart < CountdownTiming.MIN_PAUSE_MS) return null
    val remaining = nextStart - positionMs
    return Countdown(next, remaining, litDots(remaining))
}
