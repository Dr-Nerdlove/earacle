package io.github.drnerdlove.earacle.lyrics

/** What the search screen says about an entry. */
enum class PickerKind { SYNCED, PLAIN, INSTRUMENTAL }

/** The kind of lyrics [track] offers, or null when it has none and is not instrumental (never offered). */
fun pickerKind(track: LrclibTrack): PickerKind? = when {
    track.syncedLyrics != null -> PickerKind.SYNCED
    track.plainLyrics != null -> PickerKind.PLAIN
    track.instrumental -> PickerKind.INSTRUMENTAL
    else -> null
}

/**
 * The entries of a search answer as the picker lists them: entries without any lyrics that are not instrumental
 * are left out, entries with synced lyrics come first, otherwise LRCLIB's order stays (the sort is stable).
 */
fun searchResultsForPicker(tracks: List<LrclibTrack>): List<LrclibTrack> =
    tracks.filter { pickerKind(it) != null }.sortedBy { if (pickerKind(it) == PickerKind.SYNCED) 0 else 1 }

/**
 * What a tap on a lyric line changes.
 *
 * @property shiftMs the new timing shift.
 * @property anchorMs when not null: the clock is (re)started so that the song is at this position right now.
 */
data class TapResult(val shiftMs: Long, val anchorMs: Long? = null)

/**
 * The effect of a tap on the line starting at [lineTimeMs], or null when the tap does not count.
 *
 * - A recognized song (point 1): the position is known, so the tap only corrects the shift, and only for a line
 *   within [SyncShift.TAP_RANGE_MS] of the shown position.
 * - A song picked by hand: there is nothing else to correct the timing, so every line counts, with no range
 *   limit; the clock starts (or restarts) at the line and the shift goes back to 0.
 *
 * [rawPositionMs] is the position without the shift, [shiftMs] the current shift.
 */
fun tapResult(byHand: Boolean, lineTimeMs: Long, rawPositionMs: Long, shiftMs: Long): TapResult? {
    if (byHand) return TapResult(shiftMs = 0L, anchorMs = (lineTimeMs + SyncShift.TAP_LEAD_MS).coerceAtLeast(0L))
    if (!isTapInRange(lineTimeMs, syncedPositionMs(rawPositionMs, shiftMs))) return null
    return TapResult(shiftForTap(lineTimeMs, rawPositionMs))
}

/** The line to highlight: none (-1) while the clock of a picked song has not started yet. */
fun highlightedLineIndex(lines: List<LyricLine>, positionMs: Long, clockRunning: Boolean): Int =
    if (clockRunning) currentLineIndex(lines, positionMs) else -1

/** Listening along (re-recognizing the song) only runs for recognized songs: Shazam may be down for a picked one. */
fun listensAlong(byHand: Boolean): Boolean = !byHand

/** What the search field starts with when it is opened for a recognized song: its title (without the extras) and artist. */
fun searchPrefill(title: String, artist: String): String = "${displayTitle(title)} $artist".trim()
