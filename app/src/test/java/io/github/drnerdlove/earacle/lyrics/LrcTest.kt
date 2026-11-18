package io.github.drnerdlove.earacle.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcTest {
    @Test
    fun parsesTimesAndTexts() {
        val lines = Lrc.parse("[00:01.33] Is this the real life?\n[01:07.27] Caught in a landslide")
        assertEquals(
            listOf(LyricLine(1330, "Is this the real life?"), LyricLine(67270, "Caught in a landslide")),
            lines,
        )
    }

    @Test
    fun fractionDigitsAreADecimalFraction() {
        assertEquals(500L, Lrc.parse("[00:00.5] a").single().timeMs)
        assertEquals(50L, Lrc.parse("[00:00.05] a").single().timeMs)
        assertEquals(1000L, Lrc.parse("[00:01] a").single().timeMs)
        assertEquals(1234L, Lrc.parse("[00:01.234] a").single().timeMs)
    }

    @Test
    fun skipsMetadataAndLinesWithoutTimestamp() {
        val lines = Lrc.parse("[ar:Queen]\n[ti:Bohemian Rhapsody]\n[length: 05:55]\nnot a lyric\n[00:02.00] Hi")
        assertEquals(listOf(LyricLine(2000, "Hi")), lines)
    }

    @Test
    fun repeatedTimestampsGiveSeveralLinesSortedByTime() {
        val lines = Lrc.parse("[00:30.00][00:10.00] Chorus\n[00:20.00] Verse")
        assertEquals(listOf(10000L, 20000L, 30000L), lines.map { it.timeMs })
        assertEquals(listOf("Chorus", "Verse", "Chorus"), lines.map { it.text })
    }

    @Test
    fun timestampInTheMiddleOfTextIsNotATimestamp() {
        assertEquals(
            listOf(LyricLine(1000, "Meet me at [00:05.00] sharp")),
            Lrc.parse("Intro [00:09.00] text\n[00:01.00] Meet me at [00:05.00] sharp"),
        )
    }

    @Test
    fun keepsEmptyLinesAsPauses() {
        val lines = Lrc.parse("[00:05.00] \n[00:09.00] Go")
        assertEquals(LyricLine(5000, ""), lines.first())
    }

    @Test
    fun handlesWindowsLineEndingsAndEmptyInput() {
        assertEquals(listOf("A", "B"), Lrc.parse("[00:01.00] A\r\n[00:02.00] B\r\n").map { it.text })
        assertTrue(Lrc.parse("").isEmpty())
    }
}
