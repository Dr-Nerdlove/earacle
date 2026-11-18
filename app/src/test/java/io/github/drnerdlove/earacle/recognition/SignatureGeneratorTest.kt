package io.github.drnerdlove.earacle.recognition

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignatureGeneratorTest {
    private val rate = 16000

    /**
     * A short tone burst every 500 ms over 12 s (Gaussian envelope, about 100 ms long). The smooth
     * envelope matters: hard burst edges leak energy far from the tone, and that leakage reaches the
     * peak threshold and creates peaks in unrelated bands.
     */
    private fun bursts(
        frequencyHz: Double,
        amplitude: Double = 8000.0,
        sigmaSamples: Double = 200.0,
    ): ShortArray =
        ShortArray(12 * rate) { i ->
            val inBurst = i % (rate / 2)
            val envelope = exp(-0.5 * ((inBurst - 800) / sigmaSamples).pow(2))
            (amplitude * envelope * sin(2.0 * PI * frequencyHz * i / rate)).roundToInt().toShort()
        }

    private fun frequencyOf(peak: FrequencyPeak) = peak.correctedPeakFrequencyBin * (rate / 2.0 / 1024 / 64)

    @Test
    fun silenceHasNoPeaks() {
        val s = SignatureGenerator.fromSamples(ShortArray(12 * rate))
        assertEquals(4, s.peaksByBand.size)
        assertTrue(s.peaksByBand.all { it.isEmpty() })
    }

    @Test
    fun numberSamplesIsTheInputSizeAndRateIs16k() {
        val s = SignatureGenerator.fromSamples(ShortArray(12 * rate + 77))
        assertEquals(12 * rate + 77, s.numberSamples)
        assertEquals(16000, s.sampleRateHz)
    }

    @Test
    fun aThousandHertzToneGivesPeaksOnlyInBandOneNearTheTone() {
        val s = SignatureGenerator.fromSamples(bursts(1000.0))
        assertTrue("band 1 has peaks", s.peaksByBand[1].isNotEmpty())
        assertTrue(s.peaksByBand[0].isEmpty())
        assertTrue(s.peaksByBand[2].isEmpty())
        assertTrue(s.peaksByBand[3].isEmpty())
        for (p in s.peaksByBand[1]) {
            assertEquals(1000.0, frequencyOf(p), 10.0)
        }
    }

    @Test
    fun aThreeHundredHertzToneGivesPeaksOnlyInBandZero() {
        val s = SignatureGenerator.fromSamples(bursts(300.0))
        assertTrue("band 0 has peaks", s.peaksByBand[0].isNotEmpty())
        assertTrue(s.peaksByBand.drop(1).all { it.isEmpty() })
        for (p in s.peaksByBand[0]) assertEquals(300.0, frequencyOf(p), 10.0)
    }

    @Test
    fun higherTonesLandInBandsTwoAndThree() {
        val two = SignatureGenerator.fromSamples(bursts(2500.0))
        assertTrue(two.peaksByBand[2].isNotEmpty())
        assertTrue(listOf(0, 1, 3).all { two.peaksByBand[it].isEmpty() })
        for (p in two.peaksByBand[2]) assertEquals(2500.0, frequencyOf(p), 10.0)

        val three = SignatureGenerator.fromSamples(bursts(4500.0))
        assertTrue(three.peaksByBand[3].isNotEmpty())
        assertTrue(listOf(0, 1, 2).all { three.peaksByBand[it].isEmpty() })
        for (p in three.peaksByBand[3]) assertEquals(4500.0, frequencyOf(p), 10.0)
    }

    @Test
    fun peaksWithinABandAreSortedByPassNumberAndPassNumbersStartAtZero() {
        val s = SignatureGenerator.fromSamples(bursts(1000.0))
        val passes = s.peaksByBand[1].map { it.fftPassNumber }
        assertEquals(passes.sorted(), passes)
        assertTrue(passes.all { it >= 0 })
        // 12 s = 1500 chunks, so at most 1500 - 46 passes
        assertTrue(passes.all { it < 1500 - 45 })
        assertTrue(s.peaksByBand[1].all { it.peakMagnitude in 1..0xFFFF })
    }

    @Test
    fun aSignatureFromRealisticAudioEncodesAndDecodes() {
        val s = SignatureGenerator.fromSamples(bursts(1000.0))
        assertEquals(s, SignatureDecoder.decode(s.encodeToBinary()))
    }

    @Test
    fun partialTrailingChunkIsIgnored() {
        val full = bursts(1000.0)
        val a = SignatureGenerator.fromSamples(full)
        val b = SignatureGenerator.fromSamples(full + ShortArray(100) { 5000 })
        assertEquals(a.peaksByBand, b.peaksByBand)
    }

    @Test
    fun firstPeakPassNumbersMatchTheBurstTimes() {
        // A burst centred at sample 800 is centred in the 2048-sample window of FFT pass
        // (800 + 1024) / 128 - 1 = 13.25; the next burst (0.5 s = 62.5 passes later) at pass 76.
        val passes = SignatureGenerator.fromSamples(bursts(1000.0)).peaksByBand[1].map { it.fftPassNumber }
        assertEquals(13, passes[0])
        assertEquals(76, passes[1])
    }

    @Test
    fun quietPeaksKeepTheLnBeforeMaxQuirk() {
        // Very quiet, short bursts: the spectrum value is between 1/64 and 1, so ln() is negative and
        // the clamp afterwards turns every neighbour into the same magnitude. SongRec's result is a
        // magnitude of 1/64 * 1477.3 + 6144 = 6167 and, because 0/0 becomes 0, an exact bin * 64.
        val s = SignatureGenerator.fromSamples(bursts(1000.0, amplitude = 10.0, sigmaSamples = 20.0))
        assertEquals(24, s.peaksByBand[1].size)
        assertTrue(s.peaksByBand[1].all { it.peakMagnitude == 6167 })
        assertTrue(s.peaksByBand[1].all { it.correctedPeakFrequencyBin == 8192 })
    }

    @Test
    fun bandEdgesAreInclusive() {
        // The same quiet bursts give exact frequencies: 3500.0 Hz is bin 28672 and belongs to band 3.
        val s = SignatureGenerator.fromSamples(bursts(3500.0, amplitude = 10.0, sigmaSamples = 20.0))
        assertEquals(24, s.peaksByBand[3].size)
        assertTrue(s.peaksByBand[3].all { it.correctedPeakFrequencyBin == 28672 })
        assertTrue(s.peaksByBand.take(3).all { it.isEmpty() })
    }
}
