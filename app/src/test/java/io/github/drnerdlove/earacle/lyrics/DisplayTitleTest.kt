package io.github.drnerdlove.earacle.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayTitleTest {
    @Test
    fun removesSoundtrackSegment() {
        assertEquals(
            "Zero to Hero",
            displayTitle("""Zero to Hero (Music from "Marvel's Guardians of the Galaxy: Original Video Game Soundtrack")"""),
        )
    }

    @Test
    fun removesSquareBracketsAndFromSegments() {
        assertEquals("Let It Go", displayTitle("Let It Go [From \"Frozen\"]"))
        assertEquals("Theme", displayTitle("Theme (Game OST)"))
    }

    @Test
    fun keepsOtherBrackets() {
        assertEquals("Bit of Good (Bit of Bad)", displayTitle("Bit of Good (Bit of Bad)"))
        assertEquals("Song (Remastered 2011)", displayTitle("Song (Remastered 2011)"))
        assertEquals("Lost (Most Wanted)", displayTitle("Lost (Most Wanted)"))
    }

    @Test
    fun removesOnlyMatchingSegment() {
        assertEquals("Song (Live)", displayTitle("Song (Live) (From the Film)"))
    }

    @Test
    fun neverBlank() {
        assertEquals("(From Soundtrack)", displayTitle("(From Soundtrack)"))
        assertEquals("", displayTitle(""))
    }
}
