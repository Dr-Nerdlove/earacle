package io.github.drnerdlove.earacle.history

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HistoryLogTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val minute = 60_000L
    private fun entry(id: String, t: Long, title: String = "Song $id") = HistoryEntry(id, t, title, "Artist", false)
    private fun file() = File(folder.root, "history.jsonl")
    private fun reloaded() = FileHistoryStore(file()).load().map { it.id }

    @Test
    fun recordedEntriesSurviveAnotherLoad() {
        val log = HistoryLog(FileHistoryStore(file()))
        assertTrue(log.record(entry("a", 0)))
        assertTrue(log.record(entry("b", minute)))
        assertEquals(listOf("a", "b"), reloaded())
        assertEquals(listOf("a", "b"), HistoryLog(FileHistoryStore(file())).load().entries.map { it.id })
    }

    @Test
    fun aDuplicateIsNotWrittenToTheFile() {
        val log = HistoryLog(FileHistoryStore(file()))
        log.record(entry("a", 0, "Atemlos"))
        assertFalse(log.record(entry("b", minute, "atemlos")))
        assertEquals(listOf("a"), reloaded())
        assertEquals(1, log.history.entries.size)
    }

    @Test
    fun deleteAndUndoWorkInMemoryAndInTheFile() {
        val log = HistoryLog(FileHistoryStore(file()))
        listOf("a", "b", "c").forEachIndexed { i, id -> log.record(entry(id, i * 20 * minute)) }
        log.delete("b")
        assertEquals(listOf("a", "c"), reloaded())
        log.undoDelete()
        assertEquals(listOf("a", "b", "c"), reloaded())
        assertEquals(listOf("a", "b", "c"), log.history.entries.map { it.id })
    }

    @Test
    fun undoWithoutADeleteDoesNothingAndOnlyTheLastDeleteCanBeUndone() {
        val log = HistoryLog(FileHistoryStore(file()))
        listOf("a", "b", "c").forEachIndexed { i, id -> log.record(entry(id, i * 20 * minute)) }
        log.undoDelete()
        assertEquals(listOf("a", "b", "c"), reloaded())
        log.delete("a")
        log.delete("c")
        log.undoDelete()
        assertEquals(listOf("b", "c"), reloaded())
        log.undoDelete() // nothing left to undo
        assertEquals(listOf("b", "c"), reloaded())
    }

    @Test
    fun clearDeletesEverythingAndCannotBeUndone() {
        val log = HistoryLog(FileHistoryStore(file()))
        log.record(entry("a", 0))
        log.delete("a")
        log.record(entry("b", 30 * minute))
        log.clear()
        log.undoDelete()
        assertTrue(log.history.entries.isEmpty())
        assertTrue(reloaded().isEmpty())
    }

    @Test
    fun deletingAnUnknownIdChangesNothing() {
        val log = HistoryLog(FileHistoryStore(file()))
        log.record(entry("a", 0))
        log.delete("zzz")
        assertEquals(listOf("a"), reloaded())
    }

    @Test
    fun deleteAllRewritesOnceAndOneUndoPutsAllBack() {
        val store = CountingStore(FileHistoryStore(file()))
        val log = HistoryLog(store)
        listOf("a", "b", "c", "d").forEachIndexed { i, id -> log.record(entry(id, i * 20 * minute)) }
        log.deleteAll(listOf("d", "a", "zzz"))
        assertEquals(listOf("b", "c"), reloaded())
        assertEquals(1, store.rewrites)
        log.undoDelete()
        assertEquals(listOf("a", "b", "c", "d"), reloaded())
        assertEquals(listOf("a", "b", "c", "d"), log.history.entries.map { it.id })
    }

    @Test
    fun aSingleDeleteAfterADeleteAllUndoesOnlyTheSingleOne() {
        val log = HistoryLog(FileHistoryStore(file()))
        listOf("a", "b", "c", "d").forEachIndexed { i, id -> log.record(entry(id, i * 20 * minute)) }
        log.deleteAll(listOf("a", "b"))
        log.delete("c")
        log.undoDelete()
        assertEquals(listOf("c", "d"), reloaded())
    }

    @Test
    fun clearForgetsAMultiDelete() {
        val log = HistoryLog(FileHistoryStore(file()))
        listOf("a", "b").forEachIndexed { i, id -> log.record(entry(id, i * 20 * minute)) }
        log.deleteAll(listOf("a", "b"))
        log.clear()
        log.undoDelete()
        assertTrue(reloaded().isEmpty())
    }

    private class CountingStore(private val inner: HistoryStore) : HistoryStore by inner {
        var rewrites = 0
        override fun rewrite(entries: List<HistoryEntry>) {
            rewrites++
            inner.rewrite(entries)
        }
    }
}
