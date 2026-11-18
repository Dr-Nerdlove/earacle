package io.github.drnerdlove.earacle.recognition

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

/** Test-only port of SongRec's decode_from_binary, used to check encode -> decode round trips. */
object SignatureDecoder {
    fun decodeFromUri(uri: String): Signature {
        require(uri.startsWith(Signature.DATA_URI_PREFIX))
        return decode(Base64.getDecoder().decode(uri.removePrefix(Signature.DATA_URI_PREFIX)))
    }

    fun decode(data: ByteArray): Signature {
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        require(buf.getInt(0) == 0xcafe2580.toInt()) { "magic1" }
        require(buf.getInt(12) == 0x94119c00.toInt()) { "magic2" }
        require(buf.getInt(8) == data.size - 48) { "size" }
        val crc = java.util.zip.CRC32().apply { update(data, 8, data.size - 8) }
        require(buf.getInt(4) == crc.value.toInt()) { "crc" }
        val rate = when (buf.getInt(28) ushr 27) {
            1 -> 8000; 2 -> 11025; 3 -> 16000; 4 -> 32000; 5 -> 44100; 6 -> 48000
            else -> error("rate")
        }
        val numberSamples = buf.getInt(40) - (rate.toFloat() * 0.24f).toInt()
        require(buf.getInt(48) == 0x40000000) { "first chunk" }
        require(buf.getInt(52) == data.size - 48) { "first chunk size" }
        val bands = List(4) { ArrayList<FrequencyPeak>() }
        var pos = 56
        while (pos < data.size) {
            val band = buf.getInt(pos) - 0x60030040
            val size = buf.getInt(pos + 4)
            pos += 8
            val end = pos + size
            var pass = 0
            while (pos < end) {
                val offset = buf.get(pos).toInt() and 0xFF
                pos += 1
                if (offset == 0xFF) {
                    pass = buf.getInt(pos)
                    pos += 4
                } else {
                    pass += offset
                    val mag = buf.getShort(pos).toInt() and 0xFFFF
                    val bin = buf.getShort(pos + 2).toInt() and 0xFFFF
                    bands[band].add(FrequencyPeak(pass, mag, bin))
                    pos += 4
                }
            }
            pos += (4 - size % 4) % 4
        }
        return Signature(rate, numberSamples, bands)
    }
}
