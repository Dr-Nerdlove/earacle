package io.github.drnerdlove.earacle.lyrics

/** What could be found for a song. */
sealed class Lyrics {
    /** Time-stamped lines, ready to be shown in sync with the music. */
    data class Synced(val lines: List<LyricLine>) : Lyrics()

    /** Lyrics without timing. */
    data class Plain(val text: String) : Lyrics()

    /** The song is known to be instrumental. */
    data object Instrumental : Lyrics()

    /** LRCLIB has no usable lyrics for this song. */
    data object NotFound : Lyrics()

    /** The lookup itself failed (no connection, server problem). */
    data object Failure : Lyrics()
}

/** Lyrics stay behind this interface so another source can be plugged in. */
interface LyricsProvider {
    suspend fun find(title: String, artist: String): Lyrics
}
