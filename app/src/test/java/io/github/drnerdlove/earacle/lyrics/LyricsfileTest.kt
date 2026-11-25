package io.github.drnerdlove.earacle.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** All lyrics here are made up (real lyrics are copyrighted). */
class LyricsfileTest {
    private val full = """
        version: '1.0'
        metadata:
          title: Made Up
          duration_ms: 200000
        lines:
        - text: Alpha beta gamma
          start_ms: 1000
          end_ms: 2500
          words:
          - text: 'Alpha '
            start_ms: 1000
          - text: 'beta '
            start_ms: 1400
          - text: gamma
            start_ms: 2000
        - text: Delta epsilon
          start_ms: 4000
          end_ms: 5200
        - text: Zeta
          start_ms: 6000
    """.trimIndent()

    @Test
    fun readsWordsAndLineEnds() {
        val lines = Lyricsfile.parse(full)!!
        assertEquals(3, lines.size)
        assertEquals(
            LyricLine(1000, "Alpha beta gamma", listOf(WordTime(0, 1000), WordTime(6, 1400), WordTime(11, 2000)), 2500),
            lines[0],
        )
    }

    @Test
    fun lineWithoutWordsKeepsItsEnd() {
        assertEquals(LyricLine(4000, "Delta epsilon", emptyList(), 5200), Lyricsfile.parse(full)!![1])
    }

    @Test
    fun lineWithoutEndHasNone() {
        assertEquals(LyricLine(6000, "Zeta", emptyList(), null), Lyricsfile.parse(full)!![2])
    }

    @Test
    fun wordsThatDoNotJoinToTheTextAreDropped() {
        val yaml = """
            lines:
            - text: Alpha beta
              start_ms: 0
              words:
              - text: 'Alpha '
                start_ms: 0
              - text: 'gamma'
                start_ms: 500
        """.trimIndent()
        assertEquals(LyricLine(0, "Alpha beta"), Lyricsfile.parse(yaml)!!.single())
    }

    @Test
    fun wordWithoutTimeDropsAllWordsOfTheLine() {
        val yaml = """
            lines:
            - text: Alpha beta
              start_ms: 0
              words:
              - text: 'Alpha '
                start_ms: 0
              - text: beta
        """.trimIndent()
        assertEquals(emptyList<WordTime>(), Lyricsfile.parse(yaml)!!.single().words)
    }

    @Test
    fun wordTimesThatGoBackDropTheWords() {
        val yaml = """
            lines:
            - text: Alpha beta
              start_ms: 0
              words:
              - text: 'Alpha '
                start_ms: 900
              - text: beta
                start_ms: 100
        """.trimIndent()
        assertEquals(emptyList<WordTime>(), Lyricsfile.parse(yaml)!!.single().words)
    }

    @Test
    fun quotingSpacesAndUmlautsWork() {
        val yaml = """
            lines:
            - text: "Über 'den' Wolken: ja"
              start_ms: 500
              words:
              - text: "Über "
                start_ms: 500
              - text: "'den' "
                start_ms: 900
              - text: 'Wolken: '
                start_ms: 1200
              - text: "ja"
                start_ms: 1800
        """.trimIndent()
        val line = Lyricsfile.parse(yaml)!!.single()
        assertEquals("Über 'den' Wolken: ja", line.text)
        assertEquals(listOf(0, 5, 11, 19), line.words.map { it.charIndex })
        assertEquals(listOf(500L, 900L, 1200L, 1800L), line.words.map { it.timeMs })
    }

    @Test
    fun numericTextIsKeptAsWritten() {
        val yaml = "lines:\n- text: 1984\n  start_ms: 100\n"
        assertEquals("1984", Lyricsfile.parse(yaml)!!.single().text)
    }

    @Test
    fun emptyLineStaysAsPause() {
        val yaml = "lines:\n- text: ''\n  start_ms: 100\n- text: Hi\n  start_ms: 200\n"
        val lines = Lyricsfile.parse(yaml)!!
        assertEquals(2, lines.size)
        assertEquals("", lines[0].text)
    }

    @Test
    fun linesWithoutStartAreSkippedAndTheRestIsSorted() {
        val yaml = """
            lines:
            - text: Later
              start_ms: 9000
            - text: No start
            - text: Earlier
              start_ms: 3000
        """.trimIndent()
        assertEquals(listOf("Earlier", "Later"), Lyricsfile.parse(yaml)!!.map { it.text })
    }

    @Test
    fun endBeforeStartIsIgnored() {
        val yaml = "lines:\n- text: Hi\n  start_ms: 5000\n  end_ms: 4000\n"
        assertNull(Lyricsfile.parse(yaml)!!.single().endTimeMs)
    }

    @Test
    fun invalidOrOddInputGivesNull() {
        assertNull(Lyricsfile.parse("lines: [unclosed"))
        assertNull(Lyricsfile.parse("just text"))
        assertNull(Lyricsfile.parse(""))
        assertNull(Lyricsfile.parse("lines: []"))
        assertNull(Lyricsfile.parse("lines:\n- text: no start\n"))
        assertNull(Lyricsfile.parse("- a\n- b\n"))
        assertNull(Lyricsfile.parse("lines: &a [*a]"))
    }

    @Test
    fun lyricsfileIsUsedBeforeSyncedLyrics() {
        val track = LrclibTrack("T", "A", 200.0, false, "plain", "[00:01.00] From synced", full, true)
        val lyrics = LrclibApi.toLyrics(track) as Lyrics.Synced
        assertEquals("Alpha beta gamma", lyrics.lines[0].text)
        assertTrue(lyrics.lines[0].words.isNotEmpty())
    }

    @Test
    fun brokenLyricsfileFallsBackToSyncedLyrics() {
        val track = LrclibTrack("T", "A", 200.0, false, "plain", "[00:01.00] From synced", "lines: [oops", true)
        val lyrics = LrclibApi.toLyrics(track) as Lyrics.Synced
        assertEquals("From synced", lyrics.lines[0].text)
    }
}
