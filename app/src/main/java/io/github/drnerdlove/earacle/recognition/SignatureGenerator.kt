// Ported from SongRec by marin-m (GPL-3.0-or-later), used under this project's AGPL-3.0.
// Original: https://github.com/marin-m/SongRec
package io.github.drnerdlove.earacle.recognition

import kotlin.math.ln
import kotlin.math.max

/**
 * Port of SongRec's make_signature_from_buffer (do_fft, do_peak_spreading, do_peak_recognition).
 * Input is 16 kHz mono PCM; only whole 128-sample chunks are processed. Quirks of the original
 * (ring buffers with u8 wrap-around, ln() before max(), truncating casts) are kept on purpose,
 * because the server matches against fingerprints made by the original.
 */
class SignatureGenerator private constructor() {
    private val ringBufferOfSamples = ShortArray(2048)
    private var ringBufferOfSamplesIndex = 0
    private val reordered = DoubleArray(Fft.SIZE)
    private val fftRe = DoubleArray(Fft.BINS)
    private val fftIm = DoubleArray(Fft.BINS)
    private val fftOutputs = Array(256) { FloatArray(Fft.BINS) }
    private var fftOutputsIndex = 0 // u8: always kept in 0..255
    private val spreadFftOutputs = Array(256) { FloatArray(Fft.BINS) }
    private var spreadFftOutputsIndex = 0 // u8
    private var numSpreadFftsDone = 0
    private val peaks = List(4) { ArrayList<FrequencyPeak>() }

    private fun process(samples: ShortArray) {
        var offset = 0
        while (offset + 128 <= samples.size) {
            doFft(samples, offset)
            doPeakSpreading()
            numSpreadFftsDone += 1
            if (numSpreadFftsDone >= 46) doPeakRecognition()
            offset += 128
        }
    }

    private fun doFft(samples: ShortArray, offset: Int) {
        System.arraycopy(samples, offset, ringBufferOfSamples, ringBufferOfSamplesIndex, 128)
        ringBufferOfSamplesIndex = (ringBufferOfSamplesIndex + 128) and 2047

        val window = Fft.hanningWindow
        for (i in 0 until Fft.SIZE) {
            val sample = ringBufferOfSamples[(i + ringBufferOfSamplesIndex) and 2047].toFloat()
            reordered[i] = (sample * window[i]).toDouble() // f32 multiplication as in the original
        }
        Fft.realForward(reordered, fftRe, fftIm)

        val out = fftOutputs[fftOutputsIndex]
        for (i in 0 until Fft.BINS) {
            val re = fftRe[i].toFloat()
            val im = fftIm[i].toFloat()
            out[i] = max((re * re + im * im) / 131072f, 0.0000000001f)
        }
        fftOutputsIndex = (fftOutputsIndex + 1) and 255
    }

    private fun doPeakSpreading() {
        val real = fftOutputs[(fftOutputsIndex - 1) and 255]
        val spread = spreadFftOutputs[spreadFftOutputsIndex]

        // Frequency-domain spreading (in place, ascending, as in the original)
        System.arraycopy(real, 0, spread, 0, Fft.BINS)
        for (position in 0..1022) {
            spread[position] = max(max(spread[position], spread[position + 1]), spread[position + 2])
        }

        // Time-domain spreading into the 1, 3 and 6 previous outputs
        val copy = spread.copyOf()
        for (position in 0..1024) {
            for (former in FORMER_OUTPUTS) {
                val target = spreadFftOutputs[(spreadFftOutputsIndex - former) and 255]
                target[position] = max(target[position], copy[position])
            }
        }
        spreadFftOutputsIndex = (spreadFftOutputsIndex + 1) and 255
    }

    private fun doPeakRecognition() {
        val fftMinus46 = fftOutputs[(fftOutputsIndex - 46) and 255]
        val fftMinus49 = spreadFftOutputs[(spreadFftOutputsIndex - 49) and 255]

        for (bin in 10..1014) {
            if (fftMinus46[bin] >= 1.0f / 64.0f && fftMinus46[bin] >= fftMinus49[bin - 1]) {
                var maxNeighbor = 0.0f
                for (offset in NEIGHBOR_OFFSETS) {
                    maxNeighbor = max(maxNeighbor, fftMinus49[bin + offset])
                }
                if (fftMinus46[bin] > maxNeighbor) {
                    var maxOther = maxNeighbor
                    for (offset in OTHER_OFFSETS) {
                        val other = spreadFftOutputs[(spreadFftOutputsIndex + offset) and 255]
                        maxOther = max(maxOther, other[bin - 1])
                    }
                    if (fftMinus46[bin] > maxOther) {
                        storePeak(fftMinus46, bin)
                    }
                }
            }
        }
    }

    private fun storePeak(fftMinus46: FloatArray, bin: Int) {
        val fftPassNumber = numSpreadFftsDone - 46

        // ln() first, THEN max(1/64): a quirk of the original that is kept.
        val magnitude = max(ln(fftMinus46[bin]), 1.0f / 64.0f) * 1477.3f + 6144.0f
        val magnitudeBefore = max(ln(fftMinus46[bin - 1]), 1.0f / 64.0f) * 1477.3f + 6144.0f
        val magnitudeAfter = max(ln(fftMinus46[bin + 1]), 1.0f / 64.0f) * 1477.3f + 6144.0f

        val variation1 = magnitude * 2.0f - magnitudeBefore - magnitudeAfter
        val variation2 = (magnitudeAfter - magnitudeBefore) * 32.0f / variation1

        // The original asserts variation1 >= 0 and would crash; here such a peak is skipped.
        // (Also skips NaN.)
        if (!(variation1 >= 0.0f)) return

        // i32 -> u16 truncation, float -> i32 truncates toward zero
        val correctedBin = (bin * 64 + variation2.toInt()) and 0xFFFF
        val frequencyHz = correctedBin.toFloat() * (16000.0f / 2.0f / 1024.0f / 64.0f)

        val band = when (frequencyHz.toInt()) {
            in 250..519 -> 0
            in 520..1449 -> 1
            in 1450..3499 -> 2
            in 3500..5500 -> 3
            else -> return
        }
        // float -> u16 is a saturating cast in Rust
        val storedMagnitude = magnitude.toInt().coerceIn(0, 0xFFFF)
        peaks[band].add(FrequencyPeak(fftPassNumber, storedMagnitude, correctedBin))
    }

    companion object {
        private val FORMER_OUTPUTS = intArrayOf(1, 3, 6)
        private val NEIGHBOR_OFFSETS = intArrayOf(-10, -7, -4, -3, 1, 2, 5, 8)
        private val OTHER_OFFSETS = intArrayOf(
            -53, -45, 165, 172, 179, 186, 193, 200, 214, 221, 228, 235, 242, 249,
        )

        /** Makes a signature from 16 kHz mono 16-bit PCM. */
        fun fromSamples(samples: ShortArray): Signature {
            val generator = SignatureGenerator()
            generator.process(samples)
            return Signature(16000, samples.size, generator.peaks.map { it.toList() })
        }
    }
}
