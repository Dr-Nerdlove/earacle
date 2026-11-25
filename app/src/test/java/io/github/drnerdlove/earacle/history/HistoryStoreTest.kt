package io.github.drnerdlove.earacle.history

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HistoryStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun entry(n: Int) = HistoryEntry("id-$n", 1_000L * n, "Song $n", "Artist ${n % 7}", byHand = n % 3 == 0, lrclibId = if (n % 2 == 0) n.toLong() else null)

    private fun file() = File(folder.root, "files/history.jsonl")

    @Test
    fun aMissingFileIsAnEmptyHistory() {
        assertTrue(FileHistoryStore(file()).load().isEmpty())
    }

    @Test
    fun appendedEntriesComeBackInOrder() {
        val store = FileHistoryStore(file())
        store.append(entry(1))
        store.append(entry(2))
        assertEquals(listOf(entry(1), entry(2)), FileHistoryStore(file()).load())
    }

    @Test
    fun aBrokenLineIsSkippedAndTheOthersStay() {
        val store = FileHistoryStore(file())
        store.append(entry(1))
        file().appendText("garbage\n{\"v\":9,\"id\":\"x\"}\n")
        store.append(entry(2))
        assertEquals(listOf(entry(1), entry(2)), store.load())
    }

    @Test
    fun aCutOffLastLineIsSkippedAndTheNextEntryIsNotLost() {
        val store = FileHistoryStore(file())
        store.append(entry(1))
        val cut = HistoryLine.encode(entry(2)).dropLast(10)
        file().appendText(cut) // the phone died while writing: no line break
        assertEquals(listOf(entry(1)), store.load())
        store.append(entry(3))
        assertEquals(listOf(entry(1), entry(3)), store.load())
    }

    @Test
    fun tenThousandEntriesAreReadBack() {
        val store = FileHistoryStore(file())
        val all = (1..10_000).map { entry(it) }
        store.rewrite(all)
        assertEquals(all, store.load())
        store.append(entry(10_001))
        assertEquals(10_001, store.load().size)
    }

    @Test
    fun rewriteReplacesTheFileAndLeavesNoTemporaryFile() {
        val store = FileHistoryStore(file())
        store.append(entry(1))
        store.append(entry(2))
        store.rewrite(listOf(entry(2)))
        assertEquals(listOf(entry(2)), store.load())
        assertEquals(listOf("history.jsonl"), file().parentFile.list()!!.toList())
    }

    @Test
    fun clearDeletesTheFile() {
        val store = FileHistoryStore(file())
        store.append(entry(1))
        store.clear()
        assertFalse(file().exists())
        assertTrue(store.load().isEmpty())
        store.clear() // nothing to delete: no error
    }

    @Test
    fun aFolderInsteadOfAFileNeverCrashes() {
        val folderAsFile = folder.newFolder("history.jsonl")
        val store = FileHistoryStore(folderAsFile)
        store.append(entry(1))
        store.rewrite(listOf(entry(1)))
        assertTrue(store.load().isEmpty())
    }
}
