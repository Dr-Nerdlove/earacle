package io.github.drnerdlove.earacle.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryLineTest {
    @Test
    fun anEntryComesBackUnchanged() {
        val entry = HistoryEntry("id-1", 1_764_000_000_123L, "Ü, \"Quoted\" – Title", "Björk & Co", byHand = true, lrclibId = 4711L)
        assertEquals(entry, HistoryLine.decode(HistoryLine.encode(entry)))
    }

    @Test
    fun anEntryWithoutLrclibIdComesBackWithoutOne() {
        val entry = HistoryEntry("id-2", 5L, "T", "A", byHand = false)
        val line = HistoryLine.encode(entry)
        assertTrue("lrclib" !in line)
        assertEquals(entry, HistoryLine.decode(line))
    }

    @Test
    fun aLineIsOneLineEvenWithLineBreaksInTheText() {
        val line = HistoryLine.encode(HistoryEntry("i", 1, "Two\nLines", "A", false))
        assertTrue('\n' !in line)
        assertEquals("Two\nLines", HistoryLine.decode(line)?.title)
    }

    @Test
    fun brokenLinesAreNull() {
        assertNull(HistoryLine.decode(""))
        assertNull(HistoryLine.decode("not json"))
        assertNull(HistoryLine.decode("""{"v":1,"id":"a","t":12,"ti"""))
        assertNull(HistoryLine.decode("""{"v":1,"id":"a","title":"T","artist":"A"}""")) // no time
        assertNull(HistoryLine.decode("""{"v":1,"id":"","t":1,"title":"T","artist":"A"}"""))
    }

    @Test
    fun anUnknownVersionIsNull() {
        assertNull(HistoryLine.decode("""{"v":2,"id":"a","t":1,"title":"T","artist":"A"}"""))
        assertNull(HistoryLine.decode("""{"id":"a","t":1,"title":"T","artist":"A"}"""))
    }
}
