package io.github.drnerdlove.earacle.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SongHistoryTest {
    private val minute = 60_000L

    private fun entry(
        id: String,
        timeMs: Long,
        title: String = "Song $id",
        artist: String = "Artist",
        byHand: Boolean = false,
    ) = HistoryEntry(id, timeMs, title, artist, byHand)

    private fun ids(h: SongHistory) = h.entries.map { it.id }

    @Test
    fun theSameSongWithinTenMinutesIsNotAddedAgain() {
        val first = SongHistory().add(entry("a", 0, "Atemlos", "Helene Fischer"))
        val again = first.add(entry("b", 9 * minute, "Atemlos", "Helene Fischer"))
        assertSame(first, again)
        assertEquals(listOf("a"), ids(again))
    }

    @Test
    fun theSameSongAfterTenMinutesGetsANewEntry() {
        val first = SongHistory().add(entry("a", 0, "Atemlos", "Helene Fischer"))
        val later = first.add(entry("b", SongHistory.SAME_SONG_GAP_MS, "Atemlos", "Helene Fischer"))
        assertEquals(listOf("a", "b"), ids(later))
    }

    @Test
    fun anotherSpellingCountsAsTheSameSong() {
        val first = SongHistory().add(entry("a", 0, "Atemlos", "Helene Fischer"))
        assertFalse(first.add(entry("b", minute, "atemlos (Radio Edit)", "HELENE FISCHER")).entries.size > 1)
    }

    @Test
    fun aDifferentSongInBetweenIsAddedAndTheFirstCanComeBack() {
        var h = SongHistory().add(entry("a", 0, "One", "X"))
        h = h.add(entry("b", minute, "Two", "X"))
        // "One" again soon after: the newest entry is "Two", so it counts as new.
        h = h.add(entry("c", 2 * minute, "One", "X"))
        assertEquals(listOf("a", "b", "c"), ids(h))
    }

    @Test
    fun theSameTitleByAnotherArtistIsAnotherSong() {
        val h = SongHistory().add(entry("a", 0, "Intro", "X")).add(entry("b", minute, "Intro", "Y"))
        assertEquals(listOf("a", "b"), ids(h))
    }

    @Test
    fun newestFirstReversesTheOrder() {
        val h = SongHistory(listOf(entry("a", 1), entry("b", 2), entry("c", 3)))
        assertEquals(listOf("c", "b", "a"), h.newestFirst().map { it.id })
    }

    @Test
    fun deleteRemovesOnlyThatEntryAndUnknownIdsChangeNothing() {
        val h = SongHistory(listOf(entry("a", 1), entry("b", 2), entry("c", 3)))
        assertEquals(listOf("a", "c"), ids(h.delete("b")))
        assertSame(h, h.delete("zzz"))
    }

    @Test
    fun undoPutsTheEntryBackWhereItWas() {
        val h = SongHistory(listOf(entry("a", 1), entry("b", 2), entry("c", 3)))
        val b = h.entries[1]
        assertEquals(listOf("a", "b", "c"), ids(h.delete("b").restore(b, 1)))
        val a = h.entries[0]
        assertEquals(listOf("a", "b", "c"), ids(h.delete("a").restore(a, 0)))
        val c = h.entries[2]
        assertEquals(listOf("a", "b", "c"), ids(h.delete("c").restore(c, 2)))
    }

    @Test
    fun undoWithAnIndexBeyondTheEndPutsItLastAndNeverTwice() {
        val h = SongHistory(listOf(entry("a", 1)))
        val x = entry("x", 9)
        assertEquals(listOf("a", "x"), ids(h.restore(x, 50)))
        assertEquals(listOf("x", "a"), ids(h.restore(x, -3)))
        val once = h.restore(x, 1)
        assertSame(once, once.restore(x, 0))
    }

    @Test
    fun clearEmptiesTheHistory() {
        assertTrue(SongHistory(listOf(entry("a", 1))).clear().entries.isEmpty())
    }

    @Test
    fun deleteAllRemovesTheGivenEntriesAndIgnoresUnknownIds() {
        val h = SongHistory(listOf(entry("a", 0), entry("b", 1), entry("c", 2), entry("d", 3)))
        assertEquals(listOf("b", "d"), ids(h.deleteAll(listOf("a", "c", "zzz"))))
        assertSame(h, h.deleteAll(listOf("zzz")))
    }

    @Test
    fun restoreAllPutsEveryEntryBackWhereItWas() {
        val h = SongHistory(listOf(entry("a", 0), entry("b", 1), entry("c", 2), entry("d", 3), entry("e", 4)))
        val removed = listOf("e", "b", "c").map { id -> h.entries[h.indexOf(id)] to h.indexOf(id) }
        val after = h.deleteAll(listOf("e", "b", "c"))
        assertEquals(listOf("a", "d"), ids(after))
        assertEquals(ids(h), ids(after.restoreAll(removed)))
    }

    @Test
    fun restoreAllSkipsEntriesThatAreAlreadyThere() {
        val h = SongHistory(listOf(entry("a", 0), entry("b", 1)))
        assertEquals(listOf("a", "b"), ids(h.restoreAll(listOf(h.entries[1] to 1))))
    }
}
