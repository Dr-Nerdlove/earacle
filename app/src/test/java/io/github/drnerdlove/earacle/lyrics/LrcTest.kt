package io.github.drnerdlove.earacle.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcTest {
    @Test
    fun parsesTimesAndTexts() {
        val lines = Lrc.parse("[00:01.33] First made-up line?\n[01:07.27] Second made-up line")
        assertEquals(
            listOf(LyricLine(1330, "First made-up line?"), LyricLine(67270, "Second made-up line")),
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
    fun timestampWithoutLeadingTimestampIsNotALine() {
        assertEquals(emptyList<LyricLine>(), Lrc.parse("Intro [00:09.00] text"))
    }

    @Test
    fun wordTagsAreRemovedAndKeptAsWordTimes() {
        val line = Lrc.parse("[00:12.00] <00:12.00> Hello <00:12.50> world").single()
        assertEquals("Hello world", line.text)
        assertEquals(listOf(WordTime(0, 12_000), WordTime(6, 12_500)), line.words)
        assertEquals(null, line.endTimeMs)
    }

    @Test
    fun squareBracketTagsInTheMiddleAreWordTagsToo() {
        val line = Lrc.parse("[00:01.00] Meet me [00:02.00]at [00:03.00]five").single()
        assertEquals("Meet me at five", line.text)
        assertEquals(listOf(WordTime(8, 2000), WordTime(11, 3000)), line.words)
    }

    @Test
    fun tagAtTheEndIsTheEndTimeOfTheLastWord() {
        val line = Lrc.parse("[00:12.00]<00:12.00>Hello <00:12.50>world<00:13.20>").single()
        assertEquals("Hello world", line.text)
        assertEquals(2, line.words.size)
        assertEquals(13_200L, line.endTimeMs)
    }

    @Test
    fun brokenTagsAreDroppedNotShown() {
        val line = Lrc.parse("[00:01.00] <00:> Hi <1:2:3:4>there <ab:cd>").single()
        assertEquals("Hi there <ab:cd>", line.text)
    }

    @Test
    fun wordTimesGoingBackAreDropped() {
        val line = Lrc.parse("[00:10.00] <00:10.00>a <00:09.00>b <00:11.00>c").single()
        assertEquals("a b c", line.text)
        assertEquals(listOf(WordTime(0, 10_000), WordTime(4, 11_000)), line.words)
    }

    @Test
    fun mixedFileKeepsPlainLinesUnchanged() {
        val lines = Lrc.parse("[00:01.00] Plain line\n[00:05.00] <00:05.00> Tagged <00:05.40> line")
        assertEquals(LyricLine(1000, "Plain line"), lines[0])
        assertEquals("Tagged line", lines[1].text)
        assertEquals(2, lines[1].words.size)
    }

    @Test
    fun repeatedLineDoesNotKeepWordTimes() {
        val lines = Lrc.parse("[00:10.00][00:30.00] <00:10.00> Chorus <00:10.50> line")
        assertEquals(listOf("Chorus line", "Chorus line"), lines.map { it.text })
        assertEquals(listOf(emptyList<WordTime>(), emptyList<WordTime>()), lines.map { it.words })
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

    private val song = listOf(LyricLine(1000, "a"), LyricLine(5000, "b"), LyricLine(9000, "c"))

    @Test
    fun currentLineOfEmptyListIsMinusOne() {
        assertEquals(-1, currentLineIndex(emptyList(), 5000))
    }

    @Test
    fun currentLineBeforeFirstLineIsMinusOne() {
        assertEquals(-1, currentLineIndex(song, 999))
        assertEquals(-1, currentLineIndex(song, 0))
    }

    @Test
    fun currentLineOnExactTimestampIsThatLine() {
        assertEquals(0, currentLineIndex(song, 1000))
        assertEquals(1, currentLineIndex(song, 5000))
        assertEquals(2, currentLineIndex(song, 9000))
    }

    @Test
    fun currentLineBetweenTwoLinesIsTheEarlierOne() {
        assertEquals(0, currentLineIndex(song, 4999))
        assertEquals(1, currentLineIndex(song, 8999))
    }

    @Test
    fun currentLineAfterLastLineIsTheLastLine() {
        assertEquals(2, currentLineIndex(song, 600000))
    }

    @Test
    fun currentLineWithSameTimestampIsTheLaterIndex() {
        val lines = listOf(LyricLine(1000, "a"), LyricLine(2000, "b"), LyricLine(2000, "c"), LyricLine(3000, "d"))
        assertEquals(2, currentLineIndex(lines, 2000))
        assertEquals(2, currentLineIndex(lines, 2500))
    }
}
