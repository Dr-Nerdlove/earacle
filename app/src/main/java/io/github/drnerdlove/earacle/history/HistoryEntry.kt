package io.github.drnerdlove.earacle.history

import org.json.JSONException
import org.json.JSONObject

/** One song in the history (point 8). */
data class HistoryEntry(
    /** Unique, for deleting one entry. */
    val id: String,
    /** When the song was heard or picked, in milliseconds since 1970. */
    val timeMs: Long,
    val title: String,
    val artist: String,
    /** True for a song picked in the search, false for a recognized one. */
    val byHand: Boolean,
    /** LRCLIB's id of the lyrics that were shown, if there were any; opening the entry loads exactly these. */
    val lrclibId: Long? = null,
)

/** The text format of one history entry: one JSON object per line of `history.jsonl`. */
object HistoryLine {
    /** Format version; a line with another version is skipped when reading. */
    const val VERSION = 1

    /** The line for [entry], without a line break. */
    fun encode(entry: HistoryEntry): String = JSONObject().apply {
        put("v", VERSION)
        put("id", entry.id)
        put("t", entry.timeMs)
        put("title", entry.title)
        put("artist", entry.artist)
        put("hand", entry.byHand)
        entry.lrclibId?.let { put("lrclib", it) }
    }.toString()

    /** The entry of [line], or null when the line is broken (e.g. cut off), incomplete or of an unknown version. */
    fun decode(line: String): HistoryEntry? = try {
        val o = JSONObject(line)
        if (o.optInt("v", -1) != VERSION) {
            null
        } else {
            val id = o.getString("id")
            if (id.isEmpty()) {
                null
            } else {
                HistoryEntry(
                    id = id,
                    timeMs = o.getLong("t"),
                    title = o.getString("title"),
                    artist = o.getString("artist"),
                    byHand = o.optBoolean("hand", false),
                    lrclibId = if (o.has("lrclib") && !o.isNull("lrclib")) o.getLong("lrclib") else null,
                )
            }
        }
    } catch (e: JSONException) {
        null
    }
}
