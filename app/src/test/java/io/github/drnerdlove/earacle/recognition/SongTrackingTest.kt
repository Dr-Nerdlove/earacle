package io.github.drnerdlove.earacle.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongTrackingTest {
    private fun match(title: String, artist: String, key: String? = null) =
        RecognitionResult.Match(title, artist, 0.0, key, null)

    @Test
    fun sameKeyIsSameSong() {
        assertTrue(SongTracking.isSameSong(match("A", "X", "k1"), match("Other", "Y", "k1")))
    }

    @Test
    fun differentKeysButSameNamesIsSameSong() {
        assertTrue(SongTracking.isSameSong(match("Hello", "Adele", "k1"), match("Hello", "Adele", "k2")))
    }

    @Test
    fun namesIgnoreCaseAndExtraSpaces() {
        assertTrue(SongTracking.isSameSong(match("  Hello   World ", "ADELE"), match("hello world", "Adele ")))
    }

    @Test
    fun differentSongsAreNotTheSame() {
        assertFalse(SongTracking.isSameSong(match("Hello", "Adele", "k1"), match("Skyfall", "Adele", "k2")))
        assertFalse(SongTracking.isSameSong(match("Hello", "Adele"), match("Hello", "Lionel Richie")))
        assertFalse(SongTracking.isSameSong(match("Hello", "Adele", null), match("Hello", "Other", null)))
    }

    @Test
    fun delayWithoutDurationIsThePause() {
        assertEquals(15_000L, SongTracking.nextListenDelayMs(100.0, null, 15_000))
    }

    @Test
    fun delayIsShortenedWhenTheSongEndsSooner() {
        assertEquals(15_000L, SongTracking.nextListenDelayMs(100.0, 200.0, 15_000))
        // 5 s left plus 2 s grace
        assertEquals(7_000L, SongTracking.nextListenDelayMs(195.0, 200.0, 15_000))
    }

    @Test
    fun delayIsZeroPastTheEnd() {
        assertEquals(0L, SongTracking.nextListenDelayMs(209.0, 200.0, 15_000))
        assertEquals(0L, SongTracking.nextListenDelayMs(202.0, 200.0, 15_000))
    }

    @Test
    fun displayPositionIsCappedAtTheDuration() {
        assertEquals(199.0, SongTracking.displayPosition(210.0, 199.0), 1e-9)
        assertEquals(50.0, SongTracking.displayPosition(50.0, 199.0), 1e-9)
        assertEquals(500.0, SongTracking.displayPosition(500.0, null), 1e-9)
    }

    @Test
    fun silenceIsDetected() {
        assertTrue(SongTracking.isSilent(ShortArray(1000)))
        assertTrue(SongTracking.isSilent(ShortArray(0)))
        val quiet = ShortArray(1000) { i -> if (i % 2 == 0) 20 else -20 }
        assertTrue(SongTracking.isSilent(quiet))
    }

    @Test
    fun musicIsNotSilence() {
        val sine = ShortArray(12_000) { i -> (1000 * Math.sin(2 * Math.PI * 440 * i / 16_000.0)).toInt().toShort() }
        assertFalse(SongTracking.isSilent(sine))
    }

    @Test
    fun pauseGrowsWithConfirmations() {
        assertEquals(15_000L, SongTracking.pauseMs(0, true))
        assertEquals(45_000L, SongTracking.pauseMs(1, true))
        assertEquals(105_000L, SongTracking.pauseMs(2, true))
        assertEquals(105_000L, SongTracking.pauseMs(7, true))
    }

    @Test
    fun pauseIsCappedWhenDurationUnknown() {
        assertEquals(15_000L, SongTracking.pauseMs(0, false))
        assertEquals(45_000L, SongTracking.pauseMs(1, false))
        assertEquals(45_000L, SongTracking.pauseMs(5, false))
    }
}
