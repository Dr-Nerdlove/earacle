package io.github.drnerdlove.earacle.lyrics

/** A word (or other part) of a line that starts at [timeMs]; [charIndex] is its first character in [LyricLine.text] (UTF-16 index). */
data class WordTime(val charIndex: Int, val timeMs: Long)

/**
 * One lyric line that starts at [timeMs] milliseconds into the song. [text] may be empty (a pause).
 * [words] holds the word times of an enhanced ("A2") LRC line, sorted by [WordTime.charIndex]; it is empty for
 * a normal line. [endTimeMs] is the end of the last word where the line has a closing time tag.
 */
data class LyricLine(
    val timeMs: Long,
    val text: String,
    val words: List<WordTime> = emptyList(),
    val endTimeMs: Long? = null,
)

/** Parser for the LRC format ("[mm:ss.xx] text"). */
object Lrc {
    private val timestamp = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    /** A time tag inside a line: `<mm:ss.xx>` or `[mm:ss.xx]`. */
    private val inlineTag = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>|\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    /** A broken time tag such as `<00:>` or `<1:2:3:4>`: only digits, colons and dots around a colon. Dropped, never shown. */
    private val brokenTag = Regex("""<[\d.]*(?::[\d.]*)+>""")

    /**
     * Parses LRC [text] into lines sorted by time. A line with several timestamps becomes several
     * lines; metadata tags such as "[ar:Artist]" and lines without a timestamp are skipped.
     */
    fun parse(text: String): List<LyricLine> {
        val lines = ArrayList<LyricLine>()
        for (raw in text.lineSequence()) {
            val stamps = timestamp.findAll(raw).toList()
            // Timestamps must stand at the start of the line, back to back.
            var end = 0
            val starts = ArrayList<Long>()
            for (m in stamps) {
                if (m.range.first != end) break
                starts += toMillis(m)
                end = m.range.last + 1
            }
            if (starts.isEmpty()) continue
            val (content, words, endTime) = splitWordTags(raw.substring(end))
            for (t in starts) {
                // Word times belong to one start time; a repeated line cannot reuse them.
                lines += if (starts.size == 1) LyricLine(t, content, words, endTime) else LyricLine(t, content)
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    /**
     * Removes the time tags from [raw] and returns the clean, trimmed text, the word times (in text order,
     * never going back in time) and the time of a tag after the last word, if any.
     */
    private fun splitWordTags(raw: String): Triple<String, List<WordTime>, Long?> {
        val text = StringBuilder()
        val tags = ArrayList<Pair<Int, Long>>() // index in text, time
        var last = 0
        for (m in inlineTag.findAll(raw)) {
            text.append(raw.substring(last, m.range.first).replace(brokenTag, ""))
            val g = m.groupValues
            val off = if (g[1].isNotEmpty()) 1 else 4
            tags += text.length to toMillis(g[off], g[off + 1], g[off + 2])
            last = m.range.last + 1
        }
        text.append(raw.substring(last).replace(brokenTag, ""))
        // Collapse the blanks a removed tag leaves behind ("a <t> b" -> "a b"), moving the tag indices along.
        val out = StringBuilder()
        val map = IntArray(text.length + 1)
        for (i in text.indices) {
            map[i] = out.length
            val c = text[i]
            if (c.isWhitespace() && (out.isEmpty() || out.last().isWhitespace())) continue
            out.append(c)
        }
        map[text.length] = out.length
        val trimmed = out.toString().trimEnd()
        val words = ArrayList<WordTime>()
        var endTime: Long? = null
        for ((index, time) in tags) {
            val at = minOf(map[index], trimmed.length)
            // A word cannot start before the one before it.
            if (words.isNotEmpty() && time < words.last().timeMs) continue
            if (at >= trimmed.length) {
                endTime = time
            } else if (words.isEmpty() || at > words.last().charIndex) {
                words += WordTime(at, time)
            }
        }
        return Triple(trimmed, words, endTime)
    }

    private fun toMillis(m: MatchResult): Long = toMillis(m.groupValues[1], m.groupValues[2], m.groupValues[3])

    private fun toMillis(minutesText: String, secondsText: String, fraction: String): Long {
        val minutes = minutesText.toLong()
        val seconds = secondsText.toLong()
        // "5" means 500 ms, "05" 50 ms, "050" 50 ms: the digits are a decimal fraction of a second.
        val millis = if (fraction.isEmpty()) 0L else (("0." + fraction).toDouble() * 1000).let { Math.round(it) }
        return (minutes * 60 + seconds) * 1000 + millis
    }
}

/**
 * Returns the index of the line being sung at [positionMs]: the last line in [lines] whose
 * `timeMs` is at or before the position. [lines] must be sorted by time. Returns -1 if the list is
 * empty or the position is before the first line. Of several lines with the same time, the last wins.
 */
fun currentLineIndex(lines: List<LyricLine>, positionMs: Long): Int {
    var low = 0
    var high = lines.size - 1
    var result = -1
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (lines[mid].timeMs <= positionMs) {
            result = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return result
}
