package io.github.drnerdlove.earacle.recognition

import org.junit.Assert.assertEquals
import org.junit.Test

class SongPositionTest {
    @Test
    fun positionAddsElapsedTime() {
        assertEquals(83.5 + 14.0, positionSeconds(83.5, 1_000, 15_000), 1e-9)
        assertEquals(83.5, positionSeconds(83.5, 1_000, 1_000), 1e-9)
        assertEquals(10.25, positionSeconds(10.0, 5_000, 5_250), 1e-9)
    }

    @Test
    fun formatsMinutesAndSeconds() {
        assertEquals("0:00", formatPosition(0.0))
        assertEquals("0:09", formatPosition(9.99))
        assertEquals("1:23", formatPosition(83.9))
        assertEquals("10:00", formatPosition(600.0))
        assertEquals("59:59", formatPosition(3599.9))
    }

    @Test
    fun negativeShowsZeroAndHoursUseThreeParts() {
        assertEquals("0:00", formatPosition(-5.0))
        assertEquals("1:00:00", formatPosition(3600.0))
        assertEquals("1:02:03", formatPosition(3723.4))
    }
}
