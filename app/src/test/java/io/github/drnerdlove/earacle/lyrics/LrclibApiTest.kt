package io.github.drnerdlove.earacle.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LrclibApiTest {
    private fun track(
        title: String = "Bohemian Rhapsody",
        artist: String = "Queen",
        duration: Double = 355.0,
        synced: String? = "[00:01.00] Hi",
        plain: String? = "Hi",
        instrumental: Boolean = false,
    ) = LrclibTrack(title, artist, duration, instrumental, plain, synced)

    @Test
    fun buildsEncodedSearchUrls() {
        assertEquals(
            "https://lrclib.net/api/search?track_name=Don%27t+Stop&artist_name=AC%2FDC",
            LrclibApi.buildSearchUrl("Don't Stop", "AC/DC"),
        )
        assertEquals(
            "https://lrclib.net/api/search?q=Queen+Bohemian+Rhapsody",
            LrclibApi.buildFreeSearchUrl("Bohemian Rhapsody", "Queen"),
        )
    }

    @Test
    fun parsesSearchAnswer() {
        val body = """[{"id":1,"trackName":"A","artistName":"B","albumName":"C","duration":200.0,
            "instrumental":false,"plainLyrics":"plain","syncedLyrics":"[00:01.00] x"},
            {"id":2,"trackName":"I","artistName":"J","duration":10,"instrumental":true,
            "plainLyrics":null,"syncedLyrics":null}]"""
        val tracks = LrclibApi.parseSearchResults(body)!!
        assertEquals(2, tracks.size)
        assertEquals(LrclibTrack("A", "B", 200.0, false, "plain", "[00:01.00] x"), tracks[0])
        assertNull(tracks[1].syncedLyrics)
        assertNull(tracks[1].plainLyrics)
        assertTrue(tracks[1].instrumental)
    }

    @Test
    fun instrumentalSongWithJsonNullLyricsShowsInstrumental() {
        // The real LRCLIB answer for "Sirens" by Ludwig Göransson.
        val body = """[{"trackName":"Sirens","artistName":"Ludwig Göransson","duration":236.0,
            "instrumental":true,"plainLyrics":null,"syncedLyrics":null}]"""
        val picked = LrclibApi.pickBest(LrclibApi.parseSearchResults(body)!!, "Sirens", "Ludwig Göransson")!!
        assertEquals(Lyrics.Instrumental, LrclibApi.toLyrics(picked))
    }

    @Test
    fun theTextNullIsNotLyrics() {
        // What Android's org.json hands over for a JSON null: the string "null".
        val body = """[{"trackName":"Sirens","artistName":"Ludwig Göransson","duration":236.0,
            "instrumental":true,"plainLyrics":"null","syncedLyrics":"null"}]"""
        val track = LrclibApi.parseSearchResults(body)!!.single()
        assertNull(track.plainLyrics)
        assertNull(track.syncedLyrics)
    }

    @Test
    fun parseReturnsNullForGarbage() {
        assertNull(LrclibApi.parseSearchResults("<html>"))
        assertNull(LrclibApi.parseSearchResults("""{"message":"x"}"""))
        assertEquals(emptyList<LrclibTrack>(), LrclibApi.parseSearchResults("[]"))
    }

    @Test
    fun ignoresOtherSongsAndArtists() {
        val tracks = listOf(
            track(title = "Bohemian Rhapsody (Live)"),
            track(artist = "Some Cover Band"),
            track(title = "Another One Bites the Dust"),
        )
        assertNull(LrclibApi.pickBest(tracks, "Bohemian Rhapsody", "Queen"))
    }

    @Test
    fun liveVersionIsOnlyOfferedForALiveSong() {
        val live = track(title = "Bohemian Rhapsody (Live)")
        assertNull(LrclibApi.pickBest(listOf(live), "Bohemian Rhapsody", "Queen"))
        assertEquals(live, LrclibApi.pickBest(listOf(live), "Bohemian Rhapsody - Live", "Queen"))
    }

    @Test
    fun matchIgnoresCaseBracketsAndPunctuation() {
        val t = track(title = "Don't Stop Me Now", artist = "Queen")
        assertEquals(t, LrclibApi.pickBest(listOf(t), "DON’T STOP ME NOW (Remastered 2011)", "queen"))
    }

    @Test
    fun acceptsGuestArtists() {
        val t = track(artist = "Queen")
        assertEquals(t, LrclibApi.pickBest(listOf(t), "Bohemian Rhapsody", "Queen & David Bowie"))
        val u = track(artist = "Queen & David Bowie")
        assertEquals(u, LrclibApi.pickBest(listOf(u), "Bohemian Rhapsody", "Queen"))
    }

    @Test
    fun prefersSyncedOverPlain() {
        val plainOnly = track(synced = null)
        val synced = track(duration = 400.0)
        assertEquals(synced, LrclibApi.pickBest(listOf(plainOnly, synced), "Bohemian Rhapsody", "Queen"))
    }

    @Test
    fun fallsBackToPlainWhenNothingIsSynced() {
        val plainOnly = track(synced = null)
        assertEquals(plainOnly, LrclibApi.pickBest(listOf(plainOnly), "Bohemian Rhapsody", "Queen"))
    }

    @Test
    fun picksTheDurationMostEntriesAgreeOn() {
        val live = track(duration = 362.0)
        val a = track(duration = 355.0)
        val b = track(duration = 354.0)
        val c = track(duration = 356.0)
        assertEquals(a, LrclibApi.pickBest(listOf(live, a, b, c), "Bohemian Rhapsody", "Queen"))
    }

    @Test
    fun usesKnownDurationWhenGiven() {
        val radioEdit = track(duration = 210.0)
        val album = track(duration = 355.0)
        val others = List(3) { track(duration = 355.0) }
        assertEquals(
            radioEdit,
            LrclibApi.pickBest(others + listOf(album, radioEdit), "Bohemian Rhapsody", "Queen", 208.0),
        )
    }

    @Test
    fun reportsInstrumentalWhenThatIsAllThereIs() {
        val inst = track(synced = null, plain = null, instrumental = true)
        val picked = LrclibApi.pickBest(listOf(inst), "Bohemian Rhapsody", "Queen")!!
        assertEquals(Lyrics.Instrumental, LrclibApi.toLyrics(picked))
    }

    @Test
    fun noLyricsAtAllMeansNothingPicked() {
        assertNull(LrclibApi.pickBest(listOf(track(synced = null, plain = null)), "Bohemian Rhapsody", "Queen"))
        assertNull(LrclibApi.pickBest(emptyList(), "x", "y"))
    }

    @Test
    fun toLyricsPrefersSyncedAndFallsBackToPlain() {
        val synced = LrclibApi.toLyrics(track(synced = "[00:01.00] A\n[00:02.00] B"))
        assertEquals(Lyrics.Synced(listOf(LyricLine(1000, "A"), LyricLine(2000, "B"))), synced)
        assertEquals(Lyrics.Plain("Hi"), LrclibApi.toLyrics(track(synced = null, plain = " Hi \n")))
        // Synced text that is not LRC at all must not hide the plain lyrics.
        assertEquals(Lyrics.Plain("Hi"), LrclibApi.toLyrics(track(synced = "garbage", plain = "Hi")))
        assertEquals(Lyrics.NotFound, LrclibApi.toLyrics(track(synced = null, plain = null)))
    }
}
