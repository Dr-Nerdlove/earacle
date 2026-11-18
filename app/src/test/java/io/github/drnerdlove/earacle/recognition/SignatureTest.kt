package io.github.drnerdlove.earacle.recognition

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.zip.CRC32
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignatureTest {
    private fun peak(pass: Int, mag: Int, bin: Int) = FrequencyPeak(pass, mag, bin)

    private val sample = Signature(
        sampleRateHz = 16000,
        numberSamples = 16000 * 3,
        peaksByBand = listOf(
            listOf(peak(1, 100, 30), peak(300, 200, 40)),
            listOf(peak(2, 110, 60)),
            emptyList(),
            listOf(peak(5, 120, 900), peak(6, 130, 901)),
        ),
    )

    private fun le(bytes: ByteArray) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

    @Test
    fun headerFieldsAreAtTheirOffsets() {
        val data = sample.encodeToBinary()
        val b = le(data)
        assertEquals(0xcafe2580.toInt(), b.getInt(0))
        assertEquals(0x94119c00.toInt(), b.getInt(12))
        assertEquals(3 shl 27, b.getInt(28)) // 16000 Hz -> id 3
        assertEquals(48000 + 3840, b.getInt(40)) // samples + 0.24 * rate
        assertEquals((15 shl 19) + 0x40000, b.getInt(44))
        assertEquals(0x40000000, b.getInt(48))
        for (offset in intArrayOf(16, 20, 24, 32, 36)) assertEquals(0, b.getInt(offset))
        assertEquals(0x60030040, b.getInt(56)) // first band
    }

    @Test
    fun crcCoversEverythingAfterTheFirstEightBytes() {
        val data = sample.encodeToBinary()
        val crc = CRC32().apply { update(data, 8, data.size - 8) }
        assertEquals(crc.value.toInt(), le(data).getInt(4))
    }

    @Test
    fun bothSizeFieldsAreTotalMinusHeader() {
        val data = sample.encodeToBinary()
        assertEquals(data.size - 48, le(data).getInt(8))
        assertEquals(data.size - 48, le(data).getInt(52))
    }

    @Test
    fun bandsArePaddedToFourBytesAndEmptyBandsAreSkipped() {
        val one = Signature(16000, 100, listOf(listOf(peak(1, 1, 1)), emptyList(), emptyList(), emptyList()))
        val data = one.encodeToBinary()
        // 5 bytes of peak data (1 + 2 + 2) -> length 5, padded to 8
        assertEquals(56 + 8 + 8, data.size)
        assertEquals(5, le(data).getInt(60))
        assertEquals(0, data[56 + 8 + 5].toInt())
        assertEquals(0, data[56 + 8 + 7].toInt())
        assertEquals(0, data.size % 4)

        // band ids of the sample: 0, 1 and 3 present, 2 skipped
        val ids = mutableListOf<Int>()
        val sampleData = sample.encodeToBinary()
        var pos = 56
        while (pos < sampleData.size) {
            ids += le(sampleData).getInt(pos) - 0x60030040
            pos += 8 + le(sampleData).getInt(pos + 4) + (4 - le(sampleData).getInt(pos + 4) % 4) % 4
        }
        assertEquals(listOf(0, 1, 3), ids)
    }

    @Test
    fun smallPassDeltasUseOneByteAndGapsOf255UseAJump() {
        val s = Signature(
            16000, 100,
            listOf(listOf(peak(10, 0x1234, 0x5678), peak(264, 7, 8), peak(265, 9, 10)), emptyList(), emptyList(), emptyList()),
        )
        val data = s.encodeToBinary()
        val b = le(data)
        var p = 64
        assertEquals(10, data[p].toInt() and 0xFF); p += 1
        assertEquals(0x1234, b.getShort(p).toInt()); assertEquals(0x5678, b.getShort(p + 2).toInt()); p += 4
        // gap 254 is still a 1-byte delta
        assertEquals(254, data[p].toInt() and 0xFF); p += 1 + 4
        // next: gap of exactly 255 from 264 would need a jump; here 1
        assertEquals(1, data[p].toInt() and 0xFF)

        val jump = Signature(
            16000, 100,
            listOf(listOf(peak(10, 1, 2), peak(265, 3, 4)), emptyList(), emptyList(), emptyList()),
        )
        val jb = jump.encodeToBinary()
        var q = 64
        assertEquals(10, jb[q].toInt() and 0xFF); q += 5
        assertEquals(0xFF, jb[q].toInt() and 0xFF)
        assertEquals(265, le(jb).getInt(q + 1))
        assertEquals(0, jb[q + 5].toInt()) // delta after the jump is 0, as in SongRec
        assertEquals(3, le(jb).getShort(q + 6).toInt())
    }

    @Test
    fun uriHasPrefixAndBase64RoundTrips() {
        val uri = sample.encodeToUri()
        assertTrue(uri.startsWith("data:audio/vnd.shazam.sig;base64,"))
        val decoded = Base64.getDecoder().decode(uri.removePrefix("data:audio/vnd.shazam.sig;base64,"))
        assertTrue(decoded.contentEquals(sample.encodeToBinary()))
    }

    @Test
    fun encodeThenDecodeGivesTheSameSignature() {
        assertEquals(sample, SignatureDecoder.decode(sample.encodeToBinary()))
        assertEquals(sample, SignatureDecoder.decodeFromUri(sample.encodeToUri()))
    }
}
