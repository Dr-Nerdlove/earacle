package io.github.drnerdlove.earacle.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class LineTimingTest {
    @Test
    fun charactersAreCountedByCodePoints() {
        assertEquals(11, visibleCharacterCount("Hello world"))
        assertEquals(4, visibleCharacterCount("Grüß"))
        assertEquals(2, visibleCharacterCount("안녕"))
        // An emoji is one character for the eye although it is two UTF-16 units.
        assertEquals(2, visibleCharacterCount("a😀"))
        assertEquals(0, visibleCharacterCount(""))
    }

    @Test
    fun longLineEndsAfterItsSungTime() {
        // 20 characters x 90 ms = 1800 ms, the next line is far away.
        val lines = listOf(LyricLine(10_000, "a".repeat(20)), LyricLine(60_000, "b"))
        assertEquals(11_800L, estimatedLineEndMs(lines, 0))
    }

    @Test
    fun shortLineTakesAtLeastTheMinimum() {
        val lines = listOf(LyricLine(10_000, "Hi"), LyricLine(60_000, "b"))
        assertEquals(10_000L + LineTiming.MIN_LINE_MS, estimatedLineEndMs(lines, 0))
    }

    @Test
    fun lineEndsAtTheNextLineWhenItComesBeforeTheEstimate() {
        val lines = listOf(LyricLine(10_000, "a".repeat(40)), LyricLine(11_000, "b"))
        assertEquals(11_000L, estimatedLineEndMs(lines, 0))
    }

    @Test
    fun emptyLineEndsAtItsStart() {
        val lines = listOf(LyricLine(10_000, ""), LyricLine(60_000, "b"))
        assertEquals(10_000L, estimatedLineEndMs(lines, 0))
    }

    @Test
    fun lastLineUsesItsSungTime() {
        val lines = listOf(LyricLine(10_000, "a".repeat(20)))
        assertEquals(11_800L, estimatedLineEndMs(lines, 0))
    }
}
