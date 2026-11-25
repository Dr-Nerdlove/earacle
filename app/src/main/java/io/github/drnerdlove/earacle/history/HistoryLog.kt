package io.github.drnerdlove.earacle.history

/**
 * The history and its file together: every change is kept in memory ([history]) and written to the [store].
 * Not thread-safe: the caller runs all calls one after the other on one thread (they block on file access).
 */
class HistoryLog(private val store: HistoryStore) {
    var history = SongHistory()
        private set

    /** The last deleted entry and where it was, for [undoDelete]. */
    private var undo: Pair<HistoryEntry, Int>? = null

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

    /** Deletes the entry with [id]; the last one deleted can be put back with [undoDelete]. */
    fun delete(id: String) {
        val index = history.indexOf(id)
        if (index < 0) return
        undo = history.entries[index] to index
        history = history.delete(id)
        store.rewrite(history.entries)
    }

    /** Puts the last deleted entry back where it was. Does nothing when there is none. */
    fun undoDelete() {
        val (entry, index) = undo ?: return
        undo = null
        history = history.restore(entry, index)
        store.rewrite(history.entries)
    }

    fun clear() {
        undo = null
        history = history.clear()
        store.clear()
    }
}
