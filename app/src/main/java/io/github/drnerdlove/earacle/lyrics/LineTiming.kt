package io.github.drnerdlove.earacle.lyrics

/** Tunable guesses for how long a line takes to sing; to be tuned on the phone. */
object LineTiming {
    /** Even a one-word line takes at least this long to sing. */
    const val MIN_LINE_MS = 1_500L

    /** Sung time per visible character (spaces included). */
    const val MS_PER_CHAR = 90L
}

/** Number of characters the user sees in [text]. Counts code points, so emoji and rare letters count once. */
fun visibleCharacterCount(text: String): Int = text.codePointCount(0, text.length)

/**
 * Estimated end of the sung part of the line at [index]. LRC has no end times, so it is guessed:
 * start + min(time until the next line, max([LineTiming.MIN_LINE_MS], [LineTiming.MS_PER_CHAR] x characters)).
 * An empty line (an instrumental part) ends at its start. [lines] must be sorted by time.
 */
fun estimatedLineEndMs(lines: List<LyricLine>, index: Int): Long {
    val line = lines[index]
    if (line.text.isBlank()) return line.timeMs
    val sung = maxOf(LineTiming.MIN_LINE_MS, LineTiming.MS_PER_CHAR * visibleCharacterCount(line.text))
    val available = if (index + 1 < lines.size) {
        (lines[index + 1].timeMs - line.timeMs).coerceAtLeast(0L)
    } else {
        sung
    }
    return line.timeMs + minOf(available, sung)
}
