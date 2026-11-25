package io.github.drnerdlove.earacle.lyrics

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Looks up lyrics on LRCLIB (lrclib.net, free, no key). */
class LrclibClient : LyricsProvider, LyricsSearch {
    override suspend fun find(title: String, artist: String): Lyrics = withContext(Dispatchers.IO) {
        try {
            for (url in listOf(
                LrclibApi.buildSearchUrl(title, artist),
                LrclibApi.buildFreeSearchUrl(title, artist),
            )) {
                val body = get(url) ?: return@withContext Lyrics.Failure
                val tracks = LrclibApi.parseSearchResults(body) ?: return@withContext Lyrics.Failure
                val best = LrclibApi.pickBest(tracks, title, artist)
                if (best != null) return@withContext LrclibApi.toLyrics(best)
            }
            Lyrics.NotFound
        } catch (e: IOException) {
            Lyrics.Failure
        }
    }

    override suspend fun findById(id: Long): Lyrics? = withContext(Dispatchers.IO) {
        try {
            get(LrclibApi.buildGetUrl(id))?.let { LrclibApi.parseTrackAnswer(it) }?.let { LrclibApi.toLyrics(it) }
        } catch (e: IOException) {
            null
        }
    }

    override suspend fun search(query: String): List<LrclibTrack>? = withContext(Dispatchers.IO) {
        val url = LrclibApi.buildQueryUrl(query) ?: return@withContext emptyList()
        try {
            get(url)?.let { LrclibApi.parseSearchResults(it) }
        } catch (e: IOException) {
            null
        }
    }

    /** The body of a successful answer, or null on any other status. */
    private fun get(url: String): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            // LRCLIB asks clients to name themselves.
            connection.setRequestProperty("User-Agent", "Earacle (private Android app)")
            if (connection.responseCode !in 200..299) return null
            return connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            connection.disconnect()
        }
    }
}
