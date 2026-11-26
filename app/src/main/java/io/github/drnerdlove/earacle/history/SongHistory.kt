package io.github.drnerdlove.earacle.history

import io.github.drnerdlove.earacle.lyrics.LrclibApi

/**
 * The history of heard songs, oldest first (the order of the file). Immutable: every change gives a new history.
 */
class SongHistory(val entries: List<HistoryEntry> = emptyList()) {
    /** The newest entry first, as the history page lists them. */
    fun newestFirst(): List<HistoryEntry> = entries.asReversed()

    /**
     * Whether [candidate] is the song of the newest entry, heard less than [SAME_SONG_GAP_MS] before: then it is not
     * added again (e.g. "Try again" during the same song). Titles and artists are compared as in the lyrics lookup, so
     * "Atemlos" and "atemlos" are the same song.
     */
    fun isDuplicate(candidate: HistoryEntry): Boolean {
        val newest = entries.lastOrNull() ?: return false
        return sameSong(newest, candidate) && candidate.timeMs - newest.timeMs < SAME_SONG_GAP_MS
    }

    /** This history with [entry] at the end, or this one unchanged when [entry] is a duplicate. */
    fun add(entry: HistoryEntry): SongHistory = if (isDuplicate(entry)) this else SongHistory(entries + entry)

    /** The position of the entry with [id] (0 = oldest), or -1. */
    fun indexOf(id: String): Int = entries.indexOfFirst { it.id == id }

    /** Without the entry with [id] (unchanged when there is none). */
    fun delete(id: String): SongHistory {
        val index = indexOf(id)
        return if (index < 0) this else SongHistory(entries.filterIndexed { i, _ -> i != index })
    }

    /** With [entry] put back at [index] (counted as by [indexOf], limited to the ends): undoes [delete]. */
    fun restore(entry: HistoryEntry, index: Int): SongHistory {
        if (indexOf(entry.id) >= 0) return this
        val at = index.coerceIn(0, entries.size)
        return SongHistory(entries.take(at) + entry + entries.drop(at))
    }

    /** Without the entries with these [ids] (unknown ids are ignored). */
    fun deleteAll(ids: Collection<String>): SongHistory {
        val gone = ids.toSet()
        return if (entries.none { it.id in gone }) this else SongHistory(entries.filter { it.id !in gone })
    }

    /**
     * With the [removed] entries (each with the index it had, as by [indexOf]) put back: undoes [deleteAll]. They are
     * restored from the lowest index up, so every entry returns to its old place; entries already present are skipped.
     */
    fun restoreAll(removed: List<Pair<HistoryEntry, Int>>): SongHistory =
        removed.sortedBy { it.second }.fold(this) { history, (entry, index) -> history.restore(entry, index) }

    fun clear(): SongHistory = SongHistory()

    companion object {
        /**
         * The same song is not added again while the newest entry is the same song and younger than this (10 minutes;
         * tunable). Heard again later, it gets a new entry.
         */
        const val SAME_SONG_GAP_MS = 10 * 60 * 1000L

        fun sameSong(a: HistoryEntry, b: HistoryEntry): Boolean =
            LrclibApi.normalize(a.title) == LrclibApi.normalize(b.title) &&
                LrclibApi.normalize(a.artist) == LrclibApi.normalize(b.artist)
    }
}
