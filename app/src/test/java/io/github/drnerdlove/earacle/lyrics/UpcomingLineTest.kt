package io.github.drnerdlove.earacle.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpcomingLineTest {
    private val lines = listOf(
        LyricLine(1_000L, "First"),
        LyricLine(5_000L, "Second"),
        LyricLine(9_000L, ""),
        LyricLine(20_000L, "After the break"),
    )

    @Test
    fun theLineAfterTheCurrentOneIsUpcoming() {
        assertEquals(1, upcomingLineIndex(lines, 2_000L, clockRunning = true))
    }

    @Test
    fun beforeTheFirstLineTheFirstLineIsUpcoming() {
        assertEquals(0, upcomingLineIndex(lines, 0L, clockRunning = true))
    }

    @Test
    fun anEmptyLineIsSkipped() {
        assertEquals(3, upcomingLineIndex(lines, 6_000L, clockRunning = true))
        assertEquals(3, upcomingLineIndex(lines, 10_000L, clockRunning = true))
    }

    @Test
    fun theLastLineHasNoUpcomingLine() {
        assertNull(upcomingLineIndex(lines, 30_000L, clockRunning = true))
    }

    @Test
    fun aPickedSongWithoutClockPreviewsNothing() {
        assertNull(upcomingLineIndex(lines, 0L, clockRunning = false))
        assertNull(upcomingLineIndex(lines, 6_000L, clockRunning = false))
    }
}
