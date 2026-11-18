// Ported from SongRec by marin-m (GPL-3.0-or-later), used under this project's AGPL-3.0.
// Original: https://github.com/marin-m/SongRec
package io.github.drnerdlove.earacle.recognition

import org.json.JSONException
import org.json.JSONObject

/** Pure helpers for the unofficial Shazam API (no network access here). */
object ShazamApi {
    fun buildUrl(uuid1: String, uuid2: String): String =
        "https://amp.shazam.com/discovery/v5/en/US/android/-/tag/${uuid1.uppercase()}/${uuid2.lowercase()}" +
            "?sync=true&webv3=true&sampling=true&connected=&shazamapiversion=v3&sharehub=true&video=v3"

    fun buildRequestBody(signature: Signature, timestampMs: Long, timezone: String): String {
        val sampleMs =
            (signature.numberSamples.toFloat() / signature.sampleRateHz.toFloat() * 1000f).toInt()
        return JSONObject()
            .put(
                "geolocation",
                JSONObject().put("altitude", 300).put("latitude", 45).put("longitude", 2),
            )
            .put(
                "signature",
                JSONObject()
                    .put("samplems", sampleMs)
                    .put("timestamp", timestampMs)
                    .put("uri", signature.encodeToUri()),
            )
            .put("timestamp", timestampMs)
            .put("timezone", timezone)
            .toString()
    }

    fun parseResponse(httpStatus: Int, body: String): RecognitionResult {
        if (httpStatus == 429) return RecognitionResult.Failure(FailureReason.RATE_LIMITED)
        if (httpStatus !in 200..299) return RecognitionResult.Failure(FailureReason.SERVER)
        return try {
            val json = JSONObject(body)
            val matches = json.optJSONArray("matches")
            if (matches == null || matches.length() == 0) return RecognitionResult.NoMatch
            val track = json.getJSONObject("track")
            RecognitionResult.Match(
                title = track.getString("title"),
                artist = track.getString("subtitle"),
                offsetSeconds = matches.getJSONObject(0).getDouble("offset"),
                shazamKey = track.optString("key").takeIf { it.isNotEmpty() },
                coverArtUrl = track.optJSONObject("images")
                    ?.optString("coverart")?.takeIf { it.isNotEmpty() },
            )
        } catch (e: JSONException) {
            RecognitionResult.Failure(FailureReason.SERVER)
        }
    }
}
