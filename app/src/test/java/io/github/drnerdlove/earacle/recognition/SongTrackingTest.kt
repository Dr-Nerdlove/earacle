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
}
