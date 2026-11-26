package io.github.drnerdlove.earacle.history

/**
 * The entries picked on the history page (by their ids, in the order they were picked). Immutable: every change gives
 * a new selection. Selection mode is on while at least one entry is selected.
 */
class HistorySelection(val ids: List<String> = emptyList()) {
    val isSelecting: Boolean get() = ids.isNotEmpty()

    val count: Int get() = ids.size

    operator fun contains(id: String): Boolean = id in ids

    /** A selection of just [id] (a long press outside selection mode). */
    fun start(id: String): HistorySelection = HistorySelection(listOf(id))

    /** With [id] added, or removed when it was selected. Removing the last one ends selection mode. */
    fun toggle(id: String): HistorySelection =
        if (id in ids) HistorySelection(ids - id) else HistorySelection(ids + id)

    /** Without the ids that are not in [existingIds] any more (e.g. deleted elsewhere). */
    fun prune(existingIds: Collection<String>): HistorySelection {
        val existing = existingIds.toSet()
        return if (ids.all { it in existing }) this else HistorySelection(ids.filter { it in existing })
    }

    companion object {
        /** Rebuilds a selection saved as a plain list (see `rememberSaveable` on the history page). */
        fun of(ids: List<String>): HistorySelection = HistorySelection(ids.distinct())
    }
}
