package io.github.drnerdlove.earacle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackNavigationTest {
    @Test
    fun startScreenLetsTheSystemCloseTheApp() {
        assertNull(backAction(isIdle = true, stageShown = false))
    }

    @Test
    fun otherScreensGoHome() {
        assertEquals(BackAction.GO_HOME, backAction(isIdle = false, stageShown = false))
    }

    @Test
    fun stageModeIsLeftFirst() {
        assertEquals(BackAction.LEAVE_STAGE, backAction(isIdle = false, stageShown = true))
    }

    @Test
    fun historyBackEndsTheSelectionFirst() {
        assertEquals(HistoryBackAction.END_SELECTION, historyBackAction(selecting = true))
    }

    @Test
    fun historyBackClosesThePageWithoutASelection() {
        assertEquals(HistoryBackAction.CLOSE_HISTORY, historyBackAction(selecting = false))
    }
}
