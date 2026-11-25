package io.github.drnerdlove.earacle.lyrics

import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings

/**
 * Reader for the `lyricsfile` field of an LRCLIB answer: YAML with a `lines` list, each line with `text`,
 * `start_ms`, optionally `end_ms` and a `words` list (`text`, `start_ms`). The format may still change at
 * LRCLIB, so everything unexpected is skipped instead of failing.
 */
object Lyricsfile {
    /**
     * Parses [yaml] into lines sorted by start time, or returns null when it is not valid YAML, has no `lines`
     * list or no line with a start time. A line keeps its end time when it has one that is not before its start.
     * Word times are kept only when the words join to exactly the line's text (trailing blanks aside) and every
     * word has a time that does not go back; otherwise the line stays without word times.
     */
    fun parse(yaml: String): List<LyricLine>? {
        val root = try {
            Load(LoadSettings.builder().build()).loadFromString(yaml)
        } catch (e: RuntimeException) {
            return null
        }
        val rawLines = (root as? Map<*, *>)?.get("lines") as? List<*> ?: return null
        val lines = rawLines.mapNotNull { toLine(it) }
        return lines.sortedBy { it.timeMs }.ifEmpty { null }
    }

    private fun toLine(raw: Any?): LyricLine? {
        val map = raw as? Map<*, *> ?: return null
        val start = millis(map["start_ms"]) ?: return null
        val rawText = scalarText(map["text"]) ?: ""
        val text = rawText.trim()
        val end = millis(map["end_ms"])?.takeIf { it >= start }
        val words = if (text.isEmpty() || rawText.first().isWhitespace()) emptyList() else toWords(map["words"], rawText)
        return LyricLine(start, text, words, end)
    }

    /** The word times, or an empty list when the words do not fit the line (see [parse]). */
    private fun toWords(raw: Any?, lineText: String): List<WordTime> {
        val list = raw as? List<*> ?: return emptyList()
        val words = ArrayList<WordTime>()
        val joined = StringBuilder()
        for (item in list) {
            val map = item as? Map<*, *> ?: return emptyList()
            val text = scalarText(map["text"]) ?: return emptyList()
            val time = millis(map["start_ms"]) ?: return emptyList()
            if (words.isNotEmpty() && time < words.last().timeMs) return emptyList()
            if (text.isNotEmpty()) words += WordTime(joined.length, time)
            joined.append(text)
        }
        return if (joined.toString().trimEnd() == lineText.trimEnd()) words else emptyList()
    }

    private fun millis(value: Any?): Long? = (value as? Number)?.toLong()?.takeIf { it >= 0 }

    /** YAML turns `text: 1984` into a number; the text of a line is what was written. */
    private fun scalarText(value: Any?): String? = when (value) {
        is String -> value
        is Number, is Boolean -> value.toString()
        else -> null
    }
}
