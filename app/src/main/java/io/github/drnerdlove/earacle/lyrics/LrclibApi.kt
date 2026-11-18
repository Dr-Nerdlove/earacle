package io.github.drnerdlove.earacle.lyrics

import java.net.URLEncoder
import java.util.Locale
import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** One entry of an LRCLIB search answer. */
data class LrclibTrack(
    val trackName: String,
    val artistName: String,
    val durationSeconds: Double,
    val instrumental: Boolean,
    val plainLyrics: String?,
    val syncedLyrics: String?,
)

/** Pure helpers for the LRCLIB API (no network access here). */
object LrclibApi {
    const val BASE_URL = "https://lrclib.net"

    /**
     * Search by title and artist. `/api/get` is not used: it needs the exact album and duration,
     * which the recognition result does not give.
     */
    fun buildSearchUrl(title: String, artist: String): String =
        "$BASE_URL/api/search?track_name=${enc(title)}&artist_name=${enc(artist)}"

    /** Looser search by free text, used when the exact search finds nothing. */
    fun buildFreeSearchUrl(title: String, artist: String): String =
        "$BASE_URL/api/search?q=${enc("$artist $title")}"

    /** Returns null when [body] is not a JSON array of tracks. */
    fun parseSearchResults(body: String): List<LrclibTrack>? = try {
        val array = JSONArray(body)
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            LrclibTrack(
                trackName = o.optString("trackName"),
                artistName = o.optString("artistName"),
                durationSeconds = o.optDouble("duration", 0.0),
                instrumental = o.optBoolean("instrumental", false),
                plainLyrics = textOrNull(o, "plainLyrics"),
                syncedLyrics = textOrNull(o, "syncedLyrics"),
            )
        }
    } catch (e: JSONException) {
        null
    }

    /**
     * The text of [key], or null when it is missing, JSON null blank or the text "null". Needed because Android's
     * org.json turns a JSON null into the string "null" in optString (the JVM test library does not).
     */
    private fun textOrNull(o: JSONObject, key: String): String? =
        if (o.isNull(key)) null else o.optString(key).takeIf { it.isNotBlank() && it != "null" }

    /**
     * Picks the best entry for the recognized [title] and [artist], or null when none is the
     * same song (wrong lyrics are worse than none). Entries with synced lyrics win over plain
     * ones. Among versions with different lengths (radio edit, album, live) the length is chosen
     * by [durationSeconds] when known, else the length most entries agree on (the usual version).
     */
    fun pickBest(
        tracks: List<LrclibTrack>,
        title: String,
        artist: String,
        durationSeconds: Double? = null,
    ): LrclibTrack? {
        val wantedTitle = normalize(title)
        val wantedArtist = normalize(artist)
        val wantedIsVariant = isVariant(title)
        val sameSong = tracks.filter {
            normalize(it.trackName) == wantedTitle &&
                artistMatches(wantedArtist, normalize(it.artistName)) &&
                (wantedIsVariant || !isVariant(it.trackName))
        }
        val usable = sameSong.filter { it.syncedLyrics != null }
            .ifEmpty { sameSong.filter { it.plainLyrics != null } }
            .ifEmpty { return sameSong.firstOrNull { it.instrumental } }
        if (durationSeconds != null) {
            return usable.minByOrNull { abs(it.durationSeconds - durationSeconds) }
        }
        return usable.maxByOrNull { candidate ->
            usable.count { abs(it.durationSeconds - candidate.durationSeconds) <= DURATION_TOLERANCE }
        } // maxBy returns the first maximum, so LRCLIB's own order breaks ties.
    }

    /** Turns a picked entry into what the app shows. Synced lyrics that do not parse fall back to plain. */
    fun toLyrics(track: LrclibTrack): Lyrics {
        track.syncedLyrics?.let { text ->
            val lines = Lrc.parse(text)
            if (lines.isNotEmpty()) return Lyrics.Synced(lines)
        }
        track.plainLyrics?.let { return Lyrics.Plain(it.trim()) }
        return if (track.instrumental) Lyrics.Instrumental else Lyrics.NotFound
    }

    /** Lower case, without text in brackets ("(Remastered)"), a suffix after " - ", punctuation and extra spaces. */
    internal fun normalize(s: String): String =
        s.lowercase(Locale.ROOT)
            .replace(Regex(" - .*$"), "") // "Title - Remastered 2011"
            .replace(Regex("""[(\[][^)\]]*[)\]]"""), " ")
            .replace("&", " and ")
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()

    // A live, remix or acoustic version has different timing: never offer it for the studio song.
    // Remastered and similar tags in brackets do not change the timing and are ignored.
    private val variantWords = Regex("""\b(live|remix|acoustic|karaoke|instrumental|cover|demo)\b""", RegexOption.IGNORE_CASE)

    private fun isVariant(title: String): Boolean = variantWords.containsMatchIn(title)

    // Shazam may list "Artist & Guest" or "Artist feat. Guest" where LRCLIB has only "Artist".
    private fun artistMatches(wanted: String, found: String): Boolean =
        wanted == found || wanted.startsWith("$found ") || found.startsWith("$wanted ")

    private const val DURATION_TOLERANCE = 3.0

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
