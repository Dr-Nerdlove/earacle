// Ported from SongRec by marin-m (GPL-3.0-or-later), used under this project's AGPL-3.0.
// Original: https://github.com/marin-m/SongRec
package io.github.drnerdlove.earacle.recognition

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.TimeZone
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Recognizes songs through the unofficial Shazam API, like SongRec does. */
class ShazamRecognizer : SongRecognizer {
    override suspend fun recognize(recording: Recording): RecognitionResult =
        withContext(Dispatchers.IO) {
            val signature = SignatureGenerator.fromSamples(recording.samples)
            val body = ShazamApi.buildRequestBody(
                signature,
                System.currentTimeMillis(),
                TimeZone.getDefault().id,
            ).toByteArray(Charsets.UTF_8)
            val url = ShazamApi.buildUrl(UUID.randomUUID().toString(), UUID.randomUUID().toString())

            var connection: HttpURLConnection? = null
            try {
                connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Content-Language", "en_US")
                // On Android this is the real Dalvik user agent, like SongRec's Dalvik list.
                System.getProperty("http.agent")?.let { connection.setRequestProperty("User-Agent", it) }
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }

                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
                ShazamApi.parseResponse(status, text)
            } catch (e: IOException) {
                RecognitionResult.Failure(FailureReason.NETWORK)
            } finally {
                connection?.disconnect()
            }
        }
}
