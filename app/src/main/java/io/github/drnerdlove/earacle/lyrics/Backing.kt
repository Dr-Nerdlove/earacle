package io.github.drnerdlove.earacle.lyrics

/** A piece of a lyric line; [backing] is true for text in parentheses (backing vocals), brackets included. */
data class Segment(val text: String, val backing: Boolean)

/**
 * Splits [text] into lead and backing vocals: every `( ... )` part is a backing segment, the rest is lead.
 * Only balanced, non-nested parentheses count; if they are unbalanced or nested anywhere in the line
 * (e.g. `(oh` or `a (b (c))`), the whole line stays one normal segment, so a stray bracket never shrinks
 * half a line. Spaces at the borders stay where they are, so joining the segments gives back exactly
 * [text] (the fill of the current line counts characters in it). Empty text gives no segments.
 */
fun splitBacking(text: String): List<Segment> {
    if (text.isEmpty()) return emptyList()
    var open = false
    for (c in text) {
        when (c) {
            '(' -> if (open) return listOf(Segment(text, false)) else open = true
            ')' -> if (!open) return listOf(Segment(text, false)) else open = false
        }
    }
    if (open) return listOf(Segment(text, false))

    val segments = mutableListOf<Segment>()
    var start = 0
    while (start < text.length) {
        val from = text.indexOf('(', start)
        if (from < 0) {
            segments += Segment(text.substring(start), false)
            break
        }
        if (from > start) segments += Segment(text.substring(start, from), false)
        val to = text.indexOf(')', from)
        segments += Segment(text.substring(from, to + 1), true)
        start = to + 1
    }
    return segments
}
