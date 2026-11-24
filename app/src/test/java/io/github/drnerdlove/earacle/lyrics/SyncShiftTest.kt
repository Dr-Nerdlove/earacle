package io.github.drnerdlove.earacle.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncShiftTest {
    @Test
    fun shiftIsAddedToThePosition() {
        assertEquals(11_500L, syncedPositionMs(10_000, 1_500))
        assertEquals(8_500L, syncedPositionMs(10_000, -1_500))
        assertEquals(0L, syncedPositionMs(1_000, -5_000))
    }

    @Test
    fun tappedLineBecomesCurrent() {
        val lines = listOf(LyricLine(0, "a"), LyricLine(20_000, "b"), LyricLine(30_000, "c"))
        val raw = 24_000L
        // The singer is already on line "c" while the app still shows "b".
        assertEquals(1, currentLineIndex(lines, syncedPositionMs(raw, 0)))
        val shift = shiftForTap(30_000, raw)
        assertEquals(6_000L, shift)
        assertEquals(2, currentLineIndex(lines, syncedPositionMs(raw, shift)))
        // Tapping an earlier line moves the lyrics back.
        assertEquals(-4_000L, shiftForTap(20_000, raw))
    }

    @Test
    fun shiftStaysWithinLimits() {
        assertEquals(30_000L, shiftForTap(100_000, 0))
        assertEquals(-30_000L, shiftForTap(0, 100_000))
        assertEquals(30_000L, nudgedShift(29_800, 500))
        assertEquals(-30_000L, nudgedShift(-29_800, -500))
        assertEquals(500L, nudgedShift(0, SyncShift.STEP_MS))
        assertEquals(1_000L, nudgedShift(1_500, -SyncShift.STEP_MS))
    }

    @Test
    fun tapRangeIsThirtySecondsEitherWay() {
        assertTrue(isTapInRange(40_000, 10_000))
        assertTrue(isTapInRange(0, 30_000))
        assertFalse(isTapInRange(40_001, 10_000))
        assertFalse(isTapInRange(0, 30_001))
    }

    @Test
    fun formatsTheShift() {
        assertEquals("±0 s", formatShift(0))
        assertEquals("±0 s", formatShift(40))
        assertEquals("+0.5 s", formatShift(500))
        assertEquals("+1.5 s", formatShift(1_500))
        assertEquals("−0.5 s", formatShift(-500))
        assertEquals("−12.0 s", formatShift(-12_000))
        assertEquals("+30.0 s", formatShift(30_000))
    }
}
