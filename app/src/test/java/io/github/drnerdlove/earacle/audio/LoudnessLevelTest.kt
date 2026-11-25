package io.github.drnerdlove.earacle.audio

import io.github.drnerdlove.earacle.recognition.SongTracking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class LoudnessLevelTest {
    @Test
    fun silenceIsTheFloor() {
        assertEquals(MIN_WAVE, waveAmplitude(0.0), 0f)
        assertEquals(MIN_WAVE, waveAmplitude(SongTracking.SILENCE_RMS), 0f)
        assertEquals(MIN_WAVE, waveAmplitude(Double.NaN), 0f)
        assertEquals(MIN_WAVE, waveAmplitude(-5.0), 0f)
    }

    @Test
    fun loudIsOne() {
        assertEquals(1f, waveAmplitude(LOUD_RMS), 0f)
        assertEquals(1f, waveAmplitude(30000.0), 0f)
    }

    @Test
    fun risesWithLoudnessInBetween() {
        var last = waveAmplitude(SongTracking.SILENCE_RMS)
        for (rms in listOf(80.0, 150.0, 300.0, 600.0, 1200.0, 2500.0, 3900.0)) {
            val now = waveAmplitude(rms)
            assertTrue("$rms: $now should exceed $last", now > last)
            assertTrue(now in MIN_WAVE..1f)
            last = now
        }
    }

    @Test
    fun midpointOfTheLogScaleIsTheMiddleHeight() {
        val geometricMean = sqrt(SongTracking.SILENCE_RMS * LOUD_RMS)
        assertEquals((MIN_WAVE + 1f) / 2, waveAmplitude(geometricMean), 0.001f)
    }

    @Test
    fun chunkRmsOfKnownSamples() {
        val samples = shortArrayOf(100, -100, 100, -100, 3000, 3000)
        assertEquals(100.0, chunkRms(samples, 0, 4), 1e-9)
        assertEquals(3000.0, chunkRms(samples, 4, 2), 1e-9)
    }

    @Test
    fun emptyChunkIsZero() {
        assertEquals(0.0, chunkRms(ShortArray(3), 1, 0), 0.0)
        assertEquals(0.0, chunkRms(ShortArray(3), 1, -2), 0.0)
    }

    @Test
    fun smoothingRisesFasterThanItFalls() {
        val up = smoothAmplitude(0.2f, 1f) - 0.2f
        val down = 1f - smoothAmplitude(1f, 0.2f)
        assertTrue("up $up should exceed down $down", up > down)
        assertEquals(0.2f + 0.8f * WAVE_RISE, smoothAmplitude(0.2f, 1f), 1e-6f)
        assertEquals(1f - 0.8f * WAVE_FALL, smoothAmplitude(1f, 0.2f), 1e-6f)
        assertEquals(0.5f, smoothAmplitude(0.5f, 0.5f), 0f)
    }
}
