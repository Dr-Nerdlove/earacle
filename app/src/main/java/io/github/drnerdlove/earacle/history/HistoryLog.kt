package io.github.drnerdlove.earacle.history

/**
 * The history and its file together: every change is kept in memory ([history]) and written to the [store].
 * Not thread-safe: the caller runs all calls one after the other on one thread (they block on file access).
 */
class HistoryLog(private val store: HistoryStore) {
    var history = SongHistory()
        private set

    /** The entries removed by the last delete, each with where it was, for [undoDelete]. */
    private var undo: List<Pair<HistoryEntry, Int>>? = null

    /** Reads the file; call once at the start. */
    fun load(): SongHistory {
        history = SongHistory(store.load())
        undo = null
        return history
    }

    /** Adds [entry] unless it is a duplicate (see [SongHistory.isDuplicate]). Returns whether it was added. */
    fun record(entry: HistoryEntry): Boolean {
        if (history.isDuplicate(entry)) return false
        history = history.add(entry)
        store.append(entry)
        return true
    }

    /** Deletes the entry with [id]; it can be put back with [undoDelete]. */
    fun delete(id: String) = deleteAll(listOf(id))

    /**
     * Deletes the entries with these [ids] in one go (the file is rewritten once); [undoDelete] puts all of them back
     * where they were. Unknown ids are ignored; when none is known, nothing changes (an earlier undo stays possible).
     */
    fun deleteAll(ids: Collection<String>) {
        val gone = ids.toSet()
        val removed = history.entries.mapIndexedNotNull { index, entry -> if (entry.id in gone) entry to index else null }
        if (removed.isEmpty()) return
        undo = removed
        history = history.deleteAll(gone)
        store.rewrite(history.entries)
    }

    /** Puts the entries of the last delete back where they were. Does nothing when there is none. */
    fun undoDelete() {
        val removed = undo ?: return
        undo = null
        history = history.restoreAll(removed)
        store.rewrite(history.entries)
    }

    fun clear() {
        undo = null
        history = history.clear()
        store.clear()
    }
}
