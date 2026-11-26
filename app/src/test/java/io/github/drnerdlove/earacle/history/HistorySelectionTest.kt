package io.github.drnerdlove.earacle.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HistorySelectionTest {
    @Test
    fun startsEmpty() {
        val s = HistorySelection()
        assertFalse(s.isSelecting)
        assertEquals(0, s.count)
    }

    @Test
    fun startSelectsOnlyThatEntry() {
        val s = HistorySelection().start("a")
        assertTrue(s.isSelecting)
        assertEquals(listOf("a"), s.ids)
        assertTrue("a" in s)
        assertFalse("b" in s)
    }

    @Test
    fun toggleAddsAndRemoves() {
        val s = HistorySelection().start("a").toggle("b")
        assertEquals(listOf("a", "b"), s.ids)
        assertEquals(2, s.count)
        assertEquals(listOf("b"), s.toggle("a").ids)
    }

    @Test
    fun deselectingTheLastEntryEndsSelection() {
        val s = HistorySelection().start("a").toggle("a")
        assertFalse(s.isSelecting)
        assertEquals(0, s.count)
    }

    @Test
    fun pruneDropsIdsThatAreGone() {
        val s = HistorySelection().start("a").toggle("b").toggle("c")
        assertEquals(listOf("a", "c"), s.prune(listOf("c", "a", "x")).ids)
        assertFalse(s.prune(listOf("x")).isSelecting)
        assertSame(s, s.prune(listOf("a", "b", "c")))
    }

    @Test
    fun ofRemovesDuplicates() {
        assertEquals(listOf("a", "b"), HistorySelection.of(listOf("a", "b", "a")).ids)
    }
}
