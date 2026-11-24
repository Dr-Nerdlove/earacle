package io.github.drnerdlove.earacle.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CountdownTest {
    // 20 characters: sung for 1800 ms.
    private val twenty = "a".repeat(20)

    @Test
    fun dotsGoOutOneSecondAtATime() {
        assertEquals(3, litDots(10_000))
        assertEquals(3, litDots(3_000))
        assertEquals(3, litDots(2_001))
        assertEquals(2, litDots(2_000))
        assertEquals(2, litDots(1_001))
        assertEquals(1, litDots(1_000))
        assertEquals(1, litDots(1))
        assertEquals(0, litDots(0))
        assertEquals(0, litDots(-500))
    }

    @Test
    fun countdownBeforeTheFirstLine() {
        val lines = listOf(LyricLine(8_000, "Hello"), LyricLine(12_000, "World"))
        val c = countdown(lines, 0)
        assertEquals(Countdown(nextIndex = 0, remainingMs = 8_000, litDots = 3), c)
        assertEquals(Countdown(0, 2_500, 3), countdown(lines, 5_500))
        assertEquals(Countdown(0, 1_500, 2), countdown(lines, 6_500))
    }

    @Test
    fun noCountdownBeforeAShortIntro() {
        val lines = listOf(LyricLine(5_999, "Hello"))
        assertNull(countdown(lines, 0))
        assertNotNull(countdown(listOf(LyricLine(6_000, "Hello")), 0))
    }

    @Test
    fun countdownInALongGapAfterANormalLine() {
        val lines = listOf(LyricLine(10_000, twenty), LyricLine(40_000, "b"))
        // The line is sung until 11 800: no dots yet.
        assertNull(countdown(lines, 10_000))
        assertNull(countdown(lines, 11_799))
        assertEquals(Countdown(1, 28_200, 3), countdown(lines, 11_800))
        assertEquals(Countdown(1, 2_500, 3), countdown(lines, 37_500))
        assertEquals(Countdown(1, 1_000, 1), countdown(lines, 39_000))
        // When the next line starts, it is the current one and there are no dots.
        assertNull(countdown(lines, 40_000))
    }

    @Test
    fun pauseLengthIsCheckedFromTheEstimatedEndOfTheLine() {
        // Pause from 11 800 to 17 800 is exactly the minimum.
        val exactly = listOf(LyricLine(10_000, twenty), LyricLine(17_800, "b"))
        assertNotNull(countdown(exactly, 11_800))
        // One millisecond less is a short gap: no dots at all.
        val shorter = listOf(LyricLine(10_000, twenty), LyricLine(17_799, "b"))
        assertNull(countdown(shorter, 11_800))
    }

    @Test
    fun noCountdownInAShortGap() {
        val lines = listOf(LyricLine(0, twenty), LyricLine(4_000, "b"))
        for (p in 0L..3_999L step 100) assertNull("at $p", countdown(lines, p))
    }

    @Test
    fun countdownAfterAnEmptyLine() {
        val lines = listOf(LyricLine(0, "Hello"), LyricLine(5_000, ""), LyricLine(20_000, "Back again"))
        // Still singing "Hello" (sung until 1500).
        assertNull(countdown(lines, 1_000))
        // The pause starts when "Hello" is over and does not restart at the empty line.
        assertEquals(Countdown(2, 18_500, 3), countdown(lines, 1_500))
        assertEquals(Countdown(2, 15_000, 3), countdown(lines, 5_000))
        assertEquals(Countdown(2, 2_000, 2), countdown(lines, 18_000))
    }

    @Test
    fun countdownFromAnEmptyFirstLine() {
        val lines = listOf(LyricLine(0, ""), LyricLine(15_000, "Start"))
        assertEquals(Countdown(1, 15_000, 3), countdown(lines, 0))
    }

    @Test
    fun noCountdownAfterTheLastLine() {
        val lines = listOf(LyricLine(0, "a"), LyricLine(10_000, "Last"))
        assertNull(countdown(lines, 100_000))
        // Only empty lines follow: nothing will be sung any more.
        val ending = listOf(LyricLine(0, "a"), LyricLine(30_000, ""))
        assertNull(countdown(ending, 10_000))
        assertNull(countdown(ending, 40_000))
    }

    @Test
    fun noCountdownWithoutLines() {
        assertNull(countdown(emptyList(), 0))
    }
}
