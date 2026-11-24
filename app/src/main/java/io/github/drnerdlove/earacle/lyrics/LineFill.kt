package io.github.drnerdlove.earacle.lyrics

/**
 * How far the line at [index] has been sung at [positionMs], from 0 (not started) to 1 (sung). The line
 * fills at an even pace from its start to its [estimatedLineEndMs] and stays full after that. An empty line
 * (an instrumental part, shown as ♪) has nothing to fill and is always 0. [lines] must be sorted by time.
 */
fun lineProgress(lines: List<LyricLine>, index: Int, positionMs: Long): Float {
    val line = lines[index]
    if (line.text.isBlank()) return 0f
    if (positionMs <= line.timeMs) return 0f
    val end = estimatedLineEndMs(lines, index)
    // The next line can start at the same moment as this one; then there is no time to fill in.
    if (end <= line.timeMs || positionMs >= end) return 1f
    return ((positionMs - line.timeMs).toFloat() / (end - line.timeMs)).coerceIn(0f, 1f)
}

/**
 * Number of visible characters (code points, see [visibleCharacterCount]) of [text] that are filled at
 * [progress]. Rounded down, so the last character is filled only when the line is complete.
 */
fun filledCharacters(text: String, progress: Float): Int {
    val count = visibleCharacterCount(text)
    return (count * progress.coerceIn(0f, 1f)).toInt().coerceIn(0, count)
}

/**
 * The end of the filled part of [text] at [progress] as an index into the string (UTF-16 units), which is
 * what the text layout works with. It always falls between two code points, so an emoji or another
 * character made of two UTF-16 units is never cut in half.
 */
fun filledTextIndex(text: String, progress: Float): Int =
    text.offsetByCodePoints(0, filledCharacters(text, progress))
