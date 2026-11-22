package io.github.drnerdlove.earacle.lyrics

private val bracketed = Regex("""\s*(\([^()\[\]]*\)|\[[^()\[\]]*])""")
private val noise = Regex("""from\s|soundtrack|\bost\b""", RegexOption.IGNORE_CASE)

/**
 * Shortens a song title for display: removes round or square bracketed segments that only name
 * the film, game or soundtrack the song is from, e.g.
 * `Zero to Hero (Music from "Marvel's Guardians ...")` becomes `Zero to Hero`.
 * Other brackets such as "(Remastered 2011)" stay. Never returns a blank string.
 */
fun displayTitle(title: String): String {
    val shortened = bracketed.replace(title) { m ->
        if (noise.containsMatchIn(m.value)) "" else m.value
    }.trim()
    return shortened.ifBlank { title }
}
