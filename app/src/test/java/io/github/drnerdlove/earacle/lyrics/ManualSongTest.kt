package io.github.drnerdlove.earacle.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualSongTest {
    private fun track(
        name: String,
        synced: String? = null,
        plain: String? = null,
        instrumental: Boolean = false,
    ) = LrclibTrack(name, "Artist", 200.0, instrumental, plain, synced)

    @Test
    fun pickerShowsTheKindOfLyrics() {
        assertEquals(PickerKind.SYNCED, pickerKind(track("a", synced = "[00:01.00] x", plain = "x")))
        assertEquals(PickerKind.PLAIN, pickerKind(track("a", plain = "x")))
        assertEquals(PickerKind.INSTRUMENTAL, pickerKind(track("a", instrumental = true)))
        assertNull(pickerKind(track("a")))
    }

    @Test
    fun entriesWithoutLyricsAreLeftOut() {
        val result = searchResultsForPicker(listOf(track("none"), track("plain", plain = "x"), track("inst", instrumental = true)))
        assertEquals(listOf("plain", "inst"), result.map { it.trackName })
    }

    @Test
    fun syncedEntriesComeFirstAndOtherwiseTheOrderStays() {
        val result = searchResultsForPicker(
            listOf(
                track("plain1", plain = "x"),
                track("synced1", synced = "[00:01.00] x"),
                track("inst", instrumental = true),
                track("synced2", synced = "[00:01.00] y", plain = "y"),
                track("plain2", plain = "z"),
            ),
        )
        assertEquals(listOf("synced1", "synced2", "plain1", "inst", "plain2"), result.map { it.trackName })
    }

    @Test
    fun recognizedSongTapOnlyCorrectsTheShiftWithinRange() {
        // Position 24 s, line at 30 s: shift +6 s, no anchor.
        assertEquals(TapResult(6_000L, null), tapResult(false, 30_000, 24_000, 0))
        // A line more than 30 s away does not count.
        assertNull(tapResult(false, 100_000, 24_000, 0))
        assertNull(tapResult(false, 0, 90_000, 0))
    }

    @Test
    fun pickedSongTapAnchorsTheClockAtAnyLine() {
        // No range limit and no shift limit: the clock simply starts at the line.
        assertEquals(TapResult(0L, 100_000L), tapResult(true, 100_000, 0, 0))
        assertEquals(TapResult(0L, 250_000L), tapResult(true, 250_000, 12_000, 7_000))
        assertEquals(TapResult(0L, 0L), tapResult(true, 0, 90_000, -4_000))
    }

    @Test
    fun nothingIsHighlightedBeforeTheClockStarts() {
        val lines = listOf(LyricLine(0, "a"), LyricLine(5_000, "b"))
        assertEquals(-1, highlightedLineIndex(lines, 6_000, clockRunning = false))
        assertEquals(1, highlightedLineIndex(lines, 6_000, clockRunning = true))
        assertEquals(0, highlightedLineIndex(lines, 0, clockRunning = true))
    }

    @Test
    fun pickedSongsDoNotListenAlong() {
        assertFalse(listensAlong(byHand = true))
        assertTrue(listensAlong(byHand = false))
    }

    @Test
    fun prefillHasTitleWithoutExtrasAndArtist() {
        assertEquals("Let It Go Idina Menzel", searchPrefill("Let It Go [From \"Frozen\"]", "Idina Menzel"))
        assertEquals("Song", searchPrefill("Song", ""))
    }
}
