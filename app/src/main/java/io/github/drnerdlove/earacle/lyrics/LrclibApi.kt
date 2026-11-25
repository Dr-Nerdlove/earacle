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
    /** Raw Lyricsfile YAML (line ends and word times) when LRCLIB has it. */
    val lyricsfile: String? = null,
    /** LRCLIB's flag that the lyricsfile has word times. */
    val hasWordSync: Boolean = false,
    /** LRCLIB's id of the entry; only shown in lists of results, never needed to fetch lyrics. */
    val id: Long? = null,
    val albumName: String? = null,
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

    /**
     * The URL of LRCLIB's free search for what the user typed in the search screen (all words must appear in title,
     * artist or album), or null when [query] is blank: nothing is requested then.
     */
    fun buildQueryUrl(query: String): String? =
        query.trim().takeIf { it.isNotEmpty() }?.let { "$BASE_URL/api/search?q=${enc(it)}" }

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
                lyricsfile = textOrNull(o, "lyricsfile"),
                hasWordSync = o.optBoolean("hasWordSync", false),
                id = if (o.has("id") && !o.isNull("id")) o.optLong("id") else null,
                albumName = textOrNull(o, "albumName"),
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
        val wantedIsVariant = isVariant(title)
        val sameSong = tracks.filter {
            normalize(it.trackName) == wantedTitle &&
                artistMatches(artist, it.artistName) &&
                (wantedIsVariant || !isVariant(it.trackName))
        }
        val usable = sameSong.filter { it.syncedLyrics != null }
            .ifEmpty { sameSong.filter { it.plainLyrics != null } }
            .ifEmpty { return sameSong.firstOrNull { it.instrumental } }
        val chosen = if (durationSeconds != null) {
            usable.minByOrNull { abs(it.durationSeconds - durationSeconds) }
        } else {
            usable.maxByOrNull { candidate ->
                usable.count { abs(it.durationSeconds - candidate.durationSeconds) <= DURATION_TOLERANCE }
            } // maxBy returns the first maximum, so LRCLIB's own order breaks ties.
        } ?: return null
        return preferWordSync(chosen, usable)
    }

    /**
     * Among the versions of the same length as [chosen] (within [DURATION_TOLERANCE]) and of the same kind (synced
     * or plain), the first with word times, if [chosen] has none. Never changes the song, the variant or the length.
     */
    private fun preferWordSync(chosen: LrclibTrack, usable: List<LrclibTrack>): LrclibTrack {
        if (chosen.hasWordSync && chosen.lyricsfile != null) return chosen
        return usable.firstOrNull {
            it.hasWordSync && it.lyricsfile != null &&
                (it.syncedLyrics != null) == (chosen.syncedLyrics != null) &&
                abs(it.durationSeconds - chosen.durationSeconds) <= DURATION_TOLERANCE
        } ?: chosen
    }

    /**
     * Turns a picked entry into what the app shows: the lyricsfile (real line ends, word times) when it parses,
     * else the synced lyrics, else plain. Anything that does not parse falls back to the next.
     */
    fun toLyrics(track: LrclibTrack): Lyrics {
        val duration = track.durationSeconds.takeIf { it > 0 }
        track.lyricsfile?.let { yaml ->
            val lines = Lyricsfile.parse(yaml)
            if (lines != null) return Lyrics.Synced(lines, duration)
        }
        track.syncedLyrics?.let { text ->
            val lines = Lrc.parse(text)
            if (lines.isNotEmpty()) return Lyrics.Synced(lines, duration)
        }
        track.plainLyrics?.let { return Lyrics.Plain(it.trim(), duration) }
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

    // Separators between artist names: punctuation, and joining words as whole words.
    private val artistSeparators = Regex(
        """[,&·/;+]|\b(?:feat|ft|featuring|with|x|and|et)\b\.?""",
        RegexOption.IGNORE_CASE,
    )

    /** The individual artist names of a raw artist string, normalized, blanks dropped. */
    internal fun artistNames(raw: String): List<String> =
        raw.split(artistSeparators).map { normalize(it) }.filter { it.isNotEmpty() }

    /**
     * Whether [wanted] (as recognized) and [found] (as listed by LRCLIB) name the same artist. Both are raw strings:
     * Shazam and LRCLIB spell several artists differently ("A & B", "A, B", "A feat. B", "A/B", "A B", or only "A").
     * They match when the main (first) artist of one side is among the names of the other side or when all names
     * joined with spaces are equal (no separators at all). A different artist that merely shares a word ("Star-Lord" vs "Star-Lord Band") does not match.
     */
    internal fun artistMatches(wanted: String, found: String): Boolean {
        val w = artistNames(wanted)
        val f = artistNames(found)
        if (w.isEmpty() || f.isEmpty()) return false
        return w.first() in f || f.first() in w || w.joinToString(" ") == f.joinToString(" ")
    }

    private const val DURATION_TOLERANCE = 3.0

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
