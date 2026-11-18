// Ported from SongRec by marin-m (GPL-3.0-or-later), used under this project's AGPL-3.0.
// Original: https://github.com/marin-m/SongRec
package io.github.drnerdlove.earacle.recognition

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.zip.CRC32

/** One spectral peak. [peakMagnitude] and [correctedPeakFrequencyBin] are unsigned 16-bit values. */
data class FrequencyPeak(
    val fftPassNumber: Int,
    val peakMagnitude: Int,
    val correctedPeakFrequencyBin: Int,
)

/**
 * A Shazam signature. [peaksByBand] has 4 lists (bands 250-520, 520-1450, 1450-3500 and
 * 3500-5500 Hz, ids 0..3), each sorted by [FrequencyPeak.fftPassNumber].
 */
data class Signature(
    val sampleRateHz: Int,
    val numberSamples: Int,
    val peaksByBand: List<List<FrequencyPeak>>,
) {
    /** Byte-exact port of SongRec's encode_to_binary (little endian). */
    fun encodeToBinary(): ByteArray {
        val sampleRateId = when (sampleRateHz) {
            8000 -> 1
            11025 -> 2
            16000 -> 3
            32000 -> 4
            44100 -> 5
            48000 -> 6
            else -> throw IllegalArgumentException("Invalid sample rate: $sampleRateHz")
        }

        val body = ByteArrayOutputStream()
        peaksByBand.forEachIndexed { band, peaks ->
            if (peaks.isEmpty()) return@forEachIndexed
            val peakBytes = ByteArrayOutputStream()
            var fftPassNumber = 0
            for (peak in peaks) {
                require(peak.fftPassNumber >= fftPassNumber) { "peaks must be sorted by pass number" }
                if (peak.fftPassNumber - fftPassNumber >= 255) {
                    peakBytes.write(0xFF)
                    writeU32(peakBytes, peak.fftPassNumber)
                    fftPassNumber = peak.fftPassNumber
                }
                peakBytes.write(peak.fftPassNumber - fftPassNumber)
                writeU16(peakBytes, peak.peakMagnitude)
                writeU16(peakBytes, peak.correctedPeakFrequencyBin)
                fftPassNumber = peak.fftPassNumber
            }
            val bytes = peakBytes.toByteArray()
            writeU32(body, 0x60030040 + band)
            writeU32(body, bytes.size)
            body.write(bytes)
            repeat((4 - bytes.size % 4) % 4) { body.write(0) }
        }
        val bodyBytes = body.toByteArray()
        val sizeMinusHeader = bodyBytes.size + 8 // includes the 8-byte first chunk header

        val out = ByteBuffer.allocate(48 + 8 + bodyBytes.size).order(ByteOrder.LITTLE_ENDIAN)
        out.putInt(0xcafe2580.toInt())
        out.putInt(0) // crc32, written below
        out.putInt(sizeMinusHeader)
        out.putInt(0x94119c00.toInt())
        out.putInt(0).putInt(0).putInt(0)
        out.putInt(sampleRateId shl 27)
        out.putInt(0).putInt(0)
        out.putInt(numberSamples + (sampleRateHz.toFloat() * 0.24f).toInt())
        out.putInt((15 shl 19) + 0x40000)
        out.putInt(0x40000000)
        out.putInt(sizeMinusHeader)
        out.put(bodyBytes)
        val result = out.array()
        val crc = CRC32()
        crc.update(result, 8, result.size - 8)
        out.putInt(4, crc.value.toInt())
        return result
    }

    fun encodeToUri(): String = DATA_URI_PREFIX + Base64.getEncoder().encodeToString(encodeToBinary())

    companion object {
        const val DATA_URI_PREFIX = "data:audio/vnd.shazam.sig;base64,"

        private fun writeU16(out: ByteArrayOutputStream, value: Int) {
            out.write(value and 0xFF)
            out.write((value ushr 8) and 0xFF)
        }

        private fun writeU32(out: ByteArrayOutputStream, value: Int) {
            writeU16(out, value and 0xFFFF)
            writeU16(out, (value ushr 16) and 0xFFFF)
        }
    }
}
