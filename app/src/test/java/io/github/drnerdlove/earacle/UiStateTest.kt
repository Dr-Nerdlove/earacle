package io.github.drnerdlove.earacle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class UiStateTest {
    @Test
    fun listeningTakesNewProgress() {
        assertEquals(UiState.Listening(0.5f), listeningProgress(UiState.Listening(0.2f), 0.5f))
    }

    @Test
    fun idleStaysIdleAfterBackWasPressed() {
        assertSame(UiState.Idle, listeningProgress(UiState.Idle, 0.5f))
    }

    @Test
    fun recognizingStaysUnchanged() {
        assertSame(UiState.Recognizing, listeningProgress(UiState.Recognizing, 0.5f))
    }

    @Test
    fun notFoundStaysUnchanged() {
        assertSame(UiState.NotFound, listeningProgress(UiState.NotFound, 0.5f))
    }
}
