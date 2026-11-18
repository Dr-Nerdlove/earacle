package io.github.drnerdlove.earacle.recognition

import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FftTest {
    @Test
    fun matchesNaiveDftOnPseudoRandomInput() {
        val n = Fft.SIZE
        val random = Random(12345)
        val input = DoubleArray(n) { random.nextDouble() * 2.0 - 1.0 }
        val re = DoubleArray(Fft.BINS)
        val im = DoubleArray(Fft.BINS)
        Fft.realForward(input, re, im)

        var maxMagnitude = 0.0
        for (k in 0 until Fft.BINS) {
            var sr = 0.0
            var si = 0.0
            for (t in 0 until n) {
                val angle = -2.0 * PI * ((k.toLong() * t) % n) / n
                sr += input[t] * cos(angle)
                si += input[t] * sin(angle)
            }
            maxMagnitude = maxOf(maxMagnitude, hypot(sr, si))
            val tolerance = 1e-9 * 100
            assertEquals("re[$k]", sr, re[k], tolerance)
            assertEquals("im[$k]", si, im[k], tolerance)
        }
        assertTrue(maxMagnitude > 10.0) // the comparison is not trivially about zeros
    }

    @Test
    fun pureCosineLandsInItsBin() {
        val input = DoubleArray(Fft.SIZE) { cos(2.0 * PI * 100 * it / Fft.SIZE) }
        val re = DoubleArray(Fft.BINS)
        val im = DoubleArray(Fft.BINS)
        Fft.realForward(input, re, im)
        assertEquals(Fft.SIZE / 2.0, re[100], 1e-9)
        assertEquals(0.0, abs(re[101]), 1e-9)
    }

    @Test
    fun sineHasNegativeImaginaryPart() {
        // Forward transform uses exp(-i...): a sine gives -N/2 in the imaginary part.
        val input = DoubleArray(Fft.SIZE) { sin(2.0 * PI * 37 * it / Fft.SIZE) }
        val re = DoubleArray(Fft.BINS)
        val im = DoubleArray(Fft.BINS)
        Fft.realForward(input, re, im)
        assertEquals(-Fft.SIZE / 2.0, im[37], 1e-9)
    }

    @Test
    fun hanningWindowMatchesSongRecTable() {
        val w = Fft.hanningWindow
        assertEquals(2048, w.size)
        assertEquals(2.3508e-6, w[0].toDouble(), 1e-10)
        assertEquals(9.4032e-6, w[1].toDouble(), 1e-10)
        for (i in 0 until 2048) assertEquals("symmetry at $i", w[i], w[2047 - i], 1e-7f)
        assertEquals(1.0, w[1023].toDouble(), 1e-5)
    }
}
