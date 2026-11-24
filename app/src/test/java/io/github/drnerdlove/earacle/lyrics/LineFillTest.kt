package io.github.drnerdlove.earacle.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class LineFillTest {
    // 20 characters x 90 ms = 1800 ms of singing, the next line is far away: the line is sung 10.0-11.8 s.
    private val lines = listOf(LyricLine(10_000, "a".repeat(20)), LyricLine(60_000, "b"))

    @Test
    fun progressIsZeroAtTheStart() {
        assertEquals(0f, lineProgress(lines, 0, 10_000), 0f)
        assertEquals(0f, lineProgress(lines, 0, 9_000), 0f)
    }

    @Test
    fun progressIsHalfInTheMiddle() {
        assertEquals(0.5f, lineProgress(lines, 0, 10_900), 0.0001f)
    }

    @Test
    fun progressIsOneAtTheEnd() {
        assertEquals(1f, lineProgress(lines, 0, 11_800), 0f)
    }

    @Test
    fun lineStaysFullAfterItsEnd() {
        assertEquals(1f, lineProgress(lines, 0, 30_000), 0f)
    }

    @Test
    fun emptyLineHasNothingToFill() {
        val withPause = listOf(LyricLine(10_000, ""), LyricLine(60_000, "b"))
        assertEquals(0f, lineProgress(withPause, 0, 20_000), 0f)
    }

    @Test
    fun lineFillsUpToTheNextLineWhenItComesBeforeTheEstimate() {
        // 40 characters would take 3.6 s, but the next line starts after 1 s: the fill is done by then.
        val tight = listOf(LyricLine(10_000, "a".repeat(40)), LyricLine(11_000, "b"))
        assertEquals(0.5f, lineProgress(tight, 0, 10_500), 0.0001f)
        assertEquals(1f, lineProgress(tight, 0, 11_000), 0f)
    }

    @Test
    fun lineWithTheSameStartAsTheNextIsFullOnceStarted() {
        val same = listOf(LyricLine(10_000, "Hello"), LyricLine(10_000, "World"))
        assertEquals(0f, lineProgress(same, 0, 10_000), 0f)
        assertEquals(1f, lineProgress(same, 0, 10_001), 0f)
    }

    @Test
    fun filledCharactersFollowTheProgressIncludingSpaces() {
        assertEquals(0, filledCharacters("Hello world", 0f))
        assertEquals(5, filledCharacters("Hello world", 0.5f))
        assertEquals(11, filledCharacters("Hello world", 1f))
        // Out-of-range progress is held at the ends.
        assertEquals(11, filledCharacters("Hello world", 1.5f))
        assertEquals(0, filledCharacters("Hello world", -0.5f))
        assertEquals(0, filledCharacters("", 0.7f))
    }

    @Test
    fun umlautsAndKoreanCountOncePerCharacter() {
        assertEquals(2, filledCharacters("Grüß", 0.5f))
        assertEquals(2, filledTextIndex("Grüß", 0.5f))
        assertEquals(1, filledCharacters("안녕", 0.5f))
        assertEquals(1, filledTextIndex("안녕", 0.5f))
    }

    @Test
    fun emojiIsNeverCutInHalf() {
        // "a😀b😀" is 4 characters but 6 UTF-16 units.
        val text = "a😀b😀"
        assertEquals(2, filledCharacters(text, 0.5f))
        // Two characters filled = "a😀", which ends after 3 UTF-16 units, not inside the emoji.
        assertEquals(3, filledTextIndex(text, 0.5f))
        assertEquals(text.length, filledTextIndex(text, 1f))
        assertEquals(0, filledTextIndex(text, 0f))
    }
}
