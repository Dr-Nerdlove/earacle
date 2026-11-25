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
    fun buildsTheSearchUrlForTypedText() {
        assertEquals(
            "https://lrclib.net/api/search?q=Bl%C3%BChende+Landschaften+AC%2FDC",
            LrclibApi.buildQueryUrl("  Blühende Landschaften AC/DC \n"),
        )
        assertNull(LrclibApi.buildQueryUrl(""))
        assertNull(LrclibApi.buildQueryUrl("   "))
    }

    @Test
    fun resultsWithoutIdOrAlbumStillParse() {
        val track = LrclibApi.parseSearchResults(
            """[{"trackName":"A","artistName":"B","duration":1,"instrumental":false,"albumName":null,"id":null}]""",
        )!!.single()
        assertNull(track.id)
        assertNull(track.albumName)
    }

    @Test
    fun parsesSearchAnswer() {
        val body = """[{"id":1,"trackName":"A","artistName":"B","albumName":"C","duration":200.0,
            "instrumental":false,"plainLyrics":"plain","syncedLyrics":"[00:01.00] x"},
            {"id":2,"trackName":"I","artistName":"J","duration":10,"instrumental":true,
            "plainLyrics":null,"syncedLyrics":null}]"""
        val tracks = LrclibApi.parseSearchResults(body)!!
        assertEquals(2, tracks.size)
        assertEquals(LrclibTrack("A", "B", 200.0, false, "plain", "[00:01.00] x", id = 1L, albumName = "C"), tracks[0])
        assertNull(tracks[1].albumName)
        assertEquals(2L, tracks[1].id)
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
        assertEquals(Lyrics.Synced(listOf(LyricLine(1000, "A"), LyricLine(2000, "B")), 355.0), synced)
        assertEquals(Lyrics.Plain("Hi", 355.0), LrclibApi.toLyrics(track(synced = null, plain = " Hi \n")))
        // Synced text that is not LRC at all must not hide the plain lyrics.
        assertEquals(Lyrics.Plain("Hi", 355.0), LrclibApi.toLyrics(track(synced = "garbage", plain = "Hi")))
        assertEquals(Lyrics.NotFound, LrclibApi.toLyrics(track(synced = null, plain = null)))
    }

    @Test
    fun toLyricsCarriesTheSongDuration() {
        assertEquals(200.5, (LrclibApi.toLyrics(track(duration = 200.5)) as Lyrics.Synced).durationSeconds)
        assertEquals(200.5, (LrclibApi.toLyrics(track(duration = 200.5, synced = null)) as Lyrics.Plain).durationSeconds)
        // A missing or zero duration is unknown, not "ends immediately".
        assertNull((LrclibApi.toLyrics(track(duration = 0.0)) as Lyrics.Synced).durationSeconds)
    }

    private val shazamArtist = "Star-Lord Band, Steve Szczepkowski & Yohann Boudreault"

    @Test
    fun artistMatchesEverySpellingOfSeveralArtists() {
        listOf(
            "Star-Lord Band, Steve Szczepkowski, Yohann Boudreault",
            "Star-Lord Band \u00b7 Steve Szczepkowski \u00b7 Yohann Boudreault",
            "Star-Lord Band Steve Szczepkowski Yohann Boudreault",
            "Star-Lord Band & Steve Szczepkowski & Yohann Boudreault",
            "Star-Lord Band feat. Steve Szczepkowski & Yohann Boudreault",
            "Star-Lord Band/Steve Szczepkowski/Yohann Boudreault",
            "Star-Lord Band, Steve Szczepkowski, et, Yohann Boudreault",
            "Star-Lord Band",
        ).forEach { assertTrue(it, LrclibApi.artistMatches(shazamArtist, it)) }
    }

    @Test
    fun artistDoesNotMatchADifferentArtist() {
        assertTrue(!LrclibApi.artistMatches("Star-Lord", "Star-Lord Band"))
        assertTrue(!LrclibApi.artistMatches(shazamArtist, "Jamie Lidell"))
        assertTrue(!LrclibApi.artistMatches(shazamArtist, "Star-Lord"))
    }

    @Test
    fun pickBestFindsLyricsDespiteDifferentArtistSpelling() {
        val title = "Bit of Good (Bit of Bad)"
        val tracks = listOf(
            track(title = title, artist = "Star-Lord Band", duration = 23.0, synced = "[00:01.00] bogus"),
            track(title = "Little Bit Of Feel Good", artist = "Jamie Lidell", duration = 200.0),
            track(title = title, artist = "Star-Lord Band, Steve Szczepkowski, Yohann Boudreault", duration = 325.0, synced = "[00:01.00] Real"),
            track(title = title, artist = "Star-Lord Band feat. Steve Szczepkowski & Yohann Boudreault", duration = 325.0, synced = "[00:02.00] Real 2"),
        )
        val best = LrclibApi.pickBest(tracks, title, shazamArtist)
        assertEquals(325.0, best?.durationSeconds ?: 0.0, 0.01)
        assertTrue(best?.syncedLyrics?.contains("Real") == true)
    }

    @Test
    fun parsesLyricsfileAndWordSyncFlag() {
        val body = """[{"trackName":"A","artistName":"B","duration":200.0,"instrumental":false,
            "plainLyrics":null,"syncedLyrics":"[00:01.00] x","lyricsfile":"lines:\n- text: x\n  start_ms: 1000\n",
            "hasWordSync":true},{"trackName":"C","artistName":"D","duration":1,"instrumental":false,
            "plainLyrics":null,"syncedLyrics":null,"lyricsfile":null}]"""
        val tracks = LrclibApi.parseSearchResults(body)!!
        assertEquals("lines:\n- text: x\n  start_ms: 1000\n", tracks[0].lyricsfile)
        assertTrue(tracks[0].hasWordSync)
        assertNull(tracks[1].lyricsfile)
        assertEquals(false, tracks[1].hasWordSync)
    }

    private fun wordTrack(duration: Double, wordSync: Boolean, title: String = "Bohemian Rhapsody", synced: String? = "[00:01.00] Hi") =
        LrclibTrack(title, "Queen", duration, false, null, synced, if (wordSync) "lines: []" else null, wordSync)

    @Test
    fun prefersWordSyncAmongVersionsOfTheSameLength() {
        val plain = wordTrack(355.0, false)
        val words = wordTrack(356.5, true)
        assertEquals(words, LrclibApi.pickBest(listOf(plain, words), "Bohemian Rhapsody", "Queen"))
        assertEquals(words, LrclibApi.pickBest(listOf(plain, words), "Bohemian Rhapsody", "Queen", 355.0))
    }

    @Test
    fun wordSyncNeverChangesTheChosenLength() {
        val usual1 = wordTrack(355.0, false)
        val usual2 = wordTrack(355.5, false)
        val radioEditWithWords = wordTrack(200.0, true)
        assertEquals(usual1, LrclibApi.pickBest(listOf(usual1, usual2, radioEditWithWords), "Bohemian Rhapsody", "Queen"))
        assertEquals(usual1, LrclibApi.pickBest(listOf(usual1, radioEditWithWords), "Bohemian Rhapsody", "Queen", 355.0))
    }

    @Test
    fun wordSyncNeverPicksAnotherSongOrVariant() {
        val studio = wordTrack(355.0, false)
        val live = wordTrack(355.0, true, title = "Bohemian Rhapsody (Live)")
        assertEquals(studio, LrclibApi.pickBest(listOf(studio, live), "Bohemian Rhapsody", "Queen"))
    }

    @Test
    fun wordSyncFlagWithoutLyricsfileDoesNotCount() {
        val a = wordTrack(355.0, false)
        val flagOnly = a.copy(durationSeconds = 355.2, hasWordSync = true, lyricsfile = null)
        assertEquals(a, LrclibApi.pickBest(listOf(a, flagOnly), "Bohemian Rhapsody", "Queen"))
    }
}
