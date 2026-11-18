package io.github.drnerdlove.earacle.lyrics

/** One lyric line that starts at [timeMs] milliseconds into the song. [text] may be empty (a pause). */
data class LyricLine(val timeMs: Long, val text: String)

/** Parser for the LRC format ("[mm:ss.xx] text"). */
object Lrc {
    private val timestamp = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

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
            val content = raw.substring(end).trim()
            for (t in starts) lines += LyricLine(t, content)
        }
        return lines.sortedBy { it.timeMs }
    }

    private fun toMillis(m: MatchResult): Long {
        val minutes = m.groupValues[1].toLong()
        val seconds = m.groupValues[2].toLong()
        val fraction = m.groupValues[3]
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
