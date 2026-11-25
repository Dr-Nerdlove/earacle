package io.github.drnerdlove.earacle.lyrics

/** Tunable values of stage mode (very large lyrics for singing along from a distance). */
object StageText {
    /** Smallest size of the line being sung, in sp. */
    const val MIN_SP = 32

    /** Largest size of the line being sung, in sp. Bigger than this does not fit a phone in portrait. */
    const val MAX_SP = 64

    /** One press of `A−` / `A+` changes the size by this much. */
    const val STEP_SP = 8

    /** Size at the first start. */
    const val DEFAULT_SP = 40

    /** The next line below the current one is drawn at this fraction of the current size. */
    const val NEXT_LINE_SCALE = 0.6f
}

/** Keeps a stored or computed text size within [StageText.MIN_SP] and [StageText.MAX_SP]. */
fun clampTextSize(sizeSp: Int): Int = sizeSp.coerceIn(StageText.MIN_SP, StageText.MAX_SP)

/** The size after pressing `A+` ([up] = true) or `A−`; it stops at the lower and the upper limit. */
fun nextTextSize(currentSp: Int, up: Boolean): Int =
    clampTextSize(clampTextSize(currentSp) + if (up) StageText.STEP_SP else -StageText.STEP_SP)

/** What is remembered about stage mode after the app is closed. */
data class StageSettings(
    val enabled: Boolean = false,
    val textSizeSp: Int = StageText.DEFAULT_SP,
) {
    fun withEnabled(on: Boolean) = copy(enabled = on)

    /** One step larger ([up] = true) or smaller. */
    fun withTextStep(up: Boolean) = copy(textSizeSp = nextTextSize(textSizeSp, up))
}

/** Where [StageSettings] are kept between runs of the app (SharedPreferences on the phone). */
interface StageStore {
    fun load(): StageSettings
    fun save(settings: StageSettings)
}

/**
 * Whether the stage screen is shown instead of the normal one: only when the user switched it on and the
 * song has synced lyrics, or they are still being looked up (so the screen does not flicker between two
 * songs). Plain lyrics or a message have no lines to enlarge and use the normal screen.
 */
fun showsStageMode(enabled: Boolean, lyrics: Lyrics?): Boolean =
    enabled && (lyrics == null || lyrics is Lyrics.Synced)

/**
 * The lines stage mode shows: the current one in the middle and the next one below it. All other lines are
 * hidden.
 *
 * @property currentIndex line being sung, or -1 before the first line starts. An empty line (an
 *   instrumental part) can be current too; the screen then shows ♪ or the countdown dots in its place.
 * @property nextIndex next line with text after the current one, or null at the end of the song. This is the
 *   line the countdown ([countdown]) counts down to.
 */
data class StageLines(val currentIndex: Int, val nextIndex: Int?)

/** The lines stage mode shows at [positionMs]. [lines] must be sorted by time. */
fun stageLines(lines: List<LyricLine>, positionMs: Long): StageLines {
    val current = currentLineIndex(lines, positionMs)
    var next = current + 1
    while (next < lines.size && lines[next].text.isBlank()) next++
    return StageLines(current, if (next < lines.size) next else null)
}
