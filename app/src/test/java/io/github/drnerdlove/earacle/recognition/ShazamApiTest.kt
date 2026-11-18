package io.github.drnerdlove.earacle.recognition

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShazamApiTest {
    @Test
    fun urlIsExactAndCasesTheUuids() {
        val url = ShazamApi.buildUrl("abcdef01-aaaa-bbbb-cccc-0123456789AB", "ABCDEF01-AAAA-BBBB-CCCC-0123456789AB")
        assertEquals(
            "https://amp.shazam.com/discovery/v5/en/US/android/-/tag/" +
                "ABCDEF01-AAAA-BBBB-CCCC-0123456789AB/abcdef01-aaaa-bbbb-cccc-0123456789ab" +
                "?sync=true&webv3=true&sampling=true&connected=&shazamapiversion=v3&sharehub=true&video=v3",
            url,
        )
    }

    @Test
    fun requestBodyHasAllFields() {
        val signature = Signature(16000, 192000, listOf(listOf(FrequencyPeak(1, 2, 3)), emptyList(), emptyList(), emptyList()))
        val json = JSONObject(ShazamApi.buildRequestBody(signature, 1700000000123L, "Europe/Berlin"))
        val geo = json.getJSONObject("geolocation")
        assertEquals(300, geo.getInt("altitude"))
        assertEquals(45, geo.getInt("latitude"))
        assertEquals(2, geo.getInt("longitude"))
        val sig = json.getJSONObject("signature")
        assertEquals(12000, sig.getInt("samplems"))
        assertEquals(1700000000123L, sig.getLong("timestamp"))
        assertTrue(sig.getString("uri").startsWith("data:audio/vnd.shazam.sig;base64,"))
        assertEquals(signature.encodeToUri(), sig.getString("uri"))
        assertEquals(1700000000123L, json.getLong("timestamp"))
        assertEquals("Europe/Berlin", json.getString("timezone"))
    }

    private val matchJson = """
        {"tagid":"x","matches":[{"id":"123","offset":83.52,"timeskew":-0.0003,"frequencyskew":0.0001}],
         "timestamp":1700000000123,
         "track":{"key":"40333609","title":"Hello","subtitle":"Some Artist",
                  "images":{"background":"https://b","coverart":"https://example.com/cover.jpg"}}}
    """.trimIndent()

    @Test
    fun parsesAMatch() {
        val r = ShazamApi.parseResponse(200, matchJson)
        assertEquals(
            RecognitionResult.Match("Hello", "Some Artist", 83.52, "40333609", "https://example.com/cover.jpg"),
            r,
        )
    }

    @Test
    fun matchWithoutCoverArtHasNullCoverUrl() {
        val body = """{"matches":[{"offset":10.5}],"track":{"key":"k","title":"T","subtitle":"A"}}"""
        val r = ShazamApi.parseResponse(200, body) as RecognitionResult.Match
        assertNull(r.coverArtUrl)
        assertEquals(10.5, r.offsetSeconds, 0.0)
    }

    @Test
    fun emptyOrMissingMatchesIsNoMatch() {
        assertEquals(RecognitionResult.NoMatch, ShazamApi.parseResponse(200, """{"matches":[],"tagid":"x"}"""))
        assertEquals(RecognitionResult.NoMatch, ShazamApi.parseResponse(200, """{"tagid":"x"}"""))
    }

    @Test
    fun rateLimitServerErrorsAndGarbageAreFailures() {
        assertEquals(RecognitionResult.Failure(FailureReason.RATE_LIMITED), ShazamApi.parseResponse(429, ""))
        assertEquals(RecognitionResult.Failure(FailureReason.SERVER), ShazamApi.parseResponse(500, "oops"))
        assertEquals(RecognitionResult.Failure(FailureReason.SERVER), ShazamApi.parseResponse(404, matchJson))
        assertEquals(RecognitionResult.Failure(FailureReason.SERVER), ShazamApi.parseResponse(200, "<html>not json"))
    }
}
