package io.github.drnerdlove.earacle.lyrics

/** What could be found for a song. */
sealed class Lyrics {
    /** Time-stamped lines, ready to be shown in sync with the music. */
    data class Synced(
        val lines: List<LyricLine>,
        val durationSeconds: Double? = null,
        /** LRCLIB's id of the entry these lyrics come from (kept in the history), if known. */
        val lrclibId: Long? = null,
    ) : Lyrics()

    /** Lyrics without timing. */
    data class Plain(
        val text: String,
        val durationSeconds: Double? = null,
        /** LRCLIB's id of the entry these lyrics come from (kept in the history), if known. */
        val lrclibId: Long? = null,
    ) : Lyrics()

    /** The song is known to be instrumental. */
    data object Instrumental : Lyrics()

    /** LRCLIB has no usable lyrics for this song. */
    data object NotFound : Lyrics()

    /** The lookup itself failed (no connection, server problem). */
    data object Failure : Lyrics()
}

/** The song's length as known from the lyrics source, if any. */
val Lyrics?.songDurationSeconds: Double?
    get() = when (this) {
        is Lyrics.Synced -> durationSeconds
        is Lyrics.Plain -> durationSeconds
        else -> null
    }

/** LRCLIB's id of the entry the lyrics come from, if they have lyrics and it is known. */
val Lyrics?.lrclibId: Long?
    get() = when (this) {
        is Lyrics.Synced -> lrclibId
        is Lyrics.Plain -> lrclibId
        else -> null
    }

/** Lyrics stay behind this interface so another source can be plugged in. */
interface LyricsProvider {
    suspend fun find(title: String, artist: String): Lyrics

    /**
     * Exactly the lyrics with LRCLIB id [id] (opening a song from the history), or null when they cannot be had
     * (gone, or no connection); the caller then falls back to [find].
     */
    suspend fun findById(id: Long): Lyrics? = null
}

/** Free search in the lyrics source, for songs the user picks by hand. */
interface LyricsSearch {
    /** The entries found for [query] (empty for a blank query), or null when the search itself failed. */
    suspend fun search(query: String): List<LrclibTrack>?
}
