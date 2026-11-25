package io.github.drnerdlove.earacle.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StageModeTest {
    @Test
    fun textSizeStepsUpAndDown() {
        assertEquals(48, nextTextSize(40, up = true))
        assertEquals(32, nextTextSize(40, up = false))
        assertEquals(StageText.DEFAULT_SP + StageText.STEP_SP, nextTextSize(StageText.DEFAULT_SP, up = true))
    }

    @Test
    fun textSizeStopsAtTheUpperLimit() {
        assertEquals(StageText.MAX_SP, nextTextSize(StageText.MAX_SP - StageText.STEP_SP, up = true))
        assertEquals(StageText.MAX_SP, nextTextSize(StageText.MAX_SP, up = true))
        assertEquals(StageText.MAX_SP, nextTextSize(StageText.MAX_SP + 100, up = true))
    }

    @Test
    fun textSizeStopsAtTheLowerLimit() {
        assertEquals(StageText.MIN_SP, nextTextSize(StageText.MIN_SP + StageText.STEP_SP, up = false))
        assertEquals(StageText.MIN_SP, nextTextSize(StageText.MIN_SP, up = false))
        assertEquals(StageText.MIN_SP, nextTextSize(-5, up = false))
    }

    @Test
    fun aStoredSizeOutsideTheRangeIsBroughtBack() {
        assertEquals(StageText.MAX_SP, clampTextSize(500))
        assertEquals(StageText.MIN_SP, clampTextSize(0))
        assertEquals(44, clampTextSize(44))
        // Stepping from a stored value that is too large starts from the limit, not from the stored value.
        assertEquals(StageText.MAX_SP - StageText.STEP_SP, nextTextSize(500, up = false))
    }

    @Test
    fun settingsChangeOnlyWhatIsAskedFor() {
        val start = StageSettings()
        assertFalse(start.enabled)
        assertEquals(StageText.DEFAULT_SP, start.textSizeSp)
        val on = start.withEnabled(true)
        assertTrue(on.enabled)
        assertEquals(start.textSizeSp, on.textSizeSp)
        val bigger = on.withTextStep(up = true)
        assertTrue(bigger.enabled)
        assertEquals(nextTextSize(start.textSizeSp, up = true), bigger.textSizeSp)
        assertFalse(bigger.withEnabled(false).enabled)
    }

    @Test
    fun stageScreenNeedsTheSwitchAndSyncedLyrics() {
        val synced = Lyrics.Synced(listOf(LyricLine(0, "a")))
        assertTrue(showsStageMode(true, synced))
        assertFalse(showsStageMode(false, synced))
        // While the lyrics are still loading the stage screen stays, so it does not flicker.
        assertTrue(showsStageMode(true, null))
        assertFalse(showsStageMode(false, null))
        // Nothing to enlarge: the normal screen shows the message or the plain text.
        assertFalse(showsStageMode(true, Lyrics.Plain("text")))
        assertFalse(showsStageMode(true, Lyrics.NotFound))
        assertFalse(showsStageMode(true, Lyrics.Instrumental))
        assertFalse(showsStageMode(true, Lyrics.Failure))
    }

    private val lines = listOf(
        LyricLine(10_000, "first"),
        LyricLine(14_000, "second"),
        LyricLine(18_000, ""),
        LyricLine(40_000, "third"),
    )

    @Test
    fun beforeTheFirstLineOnlyTheNextOneIsShown() {
        assertEquals(StageLines(-1, 0), stageLines(lines, 0))
        assertEquals(StageLines(-1, 0), stageLines(lines, 9_999))
    }

    @Test
    fun inTheMiddleTheCurrentAndTheNextAreShown() {
        assertEquals(StageLines(0, 1), stageLines(lines, 10_000))
        assertEquals(StageLines(1, 3), stageLines(lines, 17_999))
    }

    @Test
    fun anEmptyLineIsCurrentAndTheNextLineWithTextFollows() {
        assertEquals(StageLines(2, 3), stageLines(lines, 25_000))
    }

    @Test
    fun atTheLastLineThereIsNoNextOne() {
        assertEquals(StageLines(3, null), stageLines(lines, 40_000))
        assertEquals(StageLines(3, null), stageLines(lines, 500_000))
    }

    @Test
    fun trailingEmptyLinesAreNotShownAsNext() {
        val withTail = listOf(LyricLine(0, "only"), LyricLine(5_000, ""), LyricLine(9_000, "  "))
        assertEquals(StageLines(0, null), stageLines(withTail, 1_000))
    }

    @Test
    fun noLinesAtAll() {
        assertEquals(StageLines(-1, null), stageLines(emptyList(), 1_000))
    }

    @Test
    fun duringTheCountdownTheNextLineIsTheOneBeingCountedDownTo() {
        for (position in listOf(0L, 5_000L, 19_000L, 30_000L, 37_500L)) {
            val pause = countdown(lines, position)
            assertNotNull("a pause at $position", pause)
            assertEquals(pause!!.nextIndex, stageLines(lines, position).nextIndex)
        }
        assertNull(countdown(lines, 40_000))
    }
}
