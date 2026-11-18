// Ported from SongRec by marin-m (GPL-3.0-or-later), used under this project's AGPL-3.0.
// Original: https://github.com/marin-m/SongRec
package io.github.drnerdlove.earacle.recognition

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Iterative radix-2 FFT for real input of size [SIZE], returning [BINS] (SIZE / 2 + 1) bins,
 * plus the Hanning window used by the Shazam fingerprint.
 */
object Fft {
    const val SIZE = 2048
    const val BINS = SIZE / 2 + 1

    /** Hanning window without the leading and trailing zeros: numpy hanning(2050)[1:-1]. */
    val hanningWindow: FloatArray = FloatArray(SIZE) { i ->
        (0.5 - 0.5 * cos(2.0 * PI * (i + 1) / (SIZE + 1))).toFloat()
    }

    private val cosTable = DoubleArray(SIZE / 2) { cos(2.0 * PI * it / SIZE) }
    private val sinTable = DoubleArray(SIZE / 2) { -sin(2.0 * PI * it / SIZE) }
    private val bitReversed = IntArray(SIZE).also { table ->
        val bits = Integer.numberOfTrailingZeros(SIZE)
        for (i in 0 until SIZE) table[i] = Integer.reverse(i) ushr (32 - bits)
    }

    /**
     * Forward FFT of [input] (SIZE values). Writes bins 0..1024 into [re] and [im]
     * (both at least [BINS] long). Not thread safe for shared work arrays, which are
     * allocated per call here.
     */
    fun realForward(input: DoubleArray, re: DoubleArray, im: DoubleArray) {
        require(input.size == SIZE) { "input must have $SIZE values" }
        require(re.size >= BINS && im.size >= BINS) { "output arrays too small" }
        val xr = DoubleArray(SIZE)
        val xi = DoubleArray(SIZE)
        for (i in 0 until SIZE) xr[bitReversed[i]] = input[i]
        var half = 1
        while (half < SIZE) {
            val step = SIZE / (half * 2)
            var start = 0
            while (start < SIZE) {
                for (k in 0 until half) {
                    val wr = cosTable[k * step]
                    val wi = sinTable[k * step]
                    val a = start + k
                    val b = a + half
                    val tr = xr[b] * wr - xi[b] * wi
                    val ti = xr[b] * wi + xi[b] * wr
                    xr[b] = xr[a] - tr
                    xi[b] = xi[a] - ti
                    xr[a] += tr
                    xi[a] += ti
                }
                start += half * 2
            }
            half *= 2
        }
        for (i in 0 until BINS) {
            re[i] = xr[i]
            im[i] = xi[i]
        }
    }
}
