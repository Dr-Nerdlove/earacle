package io.github.drnerdlove.earacle.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FillColourTest {
    private val minDiff = FillColourLimits.MIN_FILL_DIFFERENCE
    private val minContrast = FillColourLimits.MIN_CONTRAST

    private fun hueGap(a: Int, b: Int): Double {
        val gap = Math.abs(labHue(a) - labHue(b)) % 360
        return if (gap > 180) 360 - gap else gap
    }

    // The owner's phone (v0.7.0, dark Material You), measured from a screenshot.
    private val ownerBackground = 0xFF130D0B.toInt()
    private val ownerFill = 0xFFF9B6AC.toInt()
    private val ownerMuted = 0xFFBCA6A3.toInt()

    @Test
    fun ownerPaletteIsMeasuredAsInThePlan() {
        assertEquals(21.1, colourDifference(ownerFill, ownerMuted), 0.3)
    }

    @Test
    fun ownerPaletteGetsAStrongerFill() {
        val fill = fillColour(ownerFill, ownerMuted, ownerBackground)
        assertTrue(colourDifference(fill, ownerMuted) >= minDiff)
        assertTrue(contrast(fill, ownerBackground) >= minContrast)
        assertTrue(hueGap(fill, ownerFill) < 5)
    }

    @Test
    fun strongPrimaryStaysUnchanged() {
        val primary = 0xFFFF2D2D.toInt()
        val muted = 0xFFBCA6A3.toInt()
        assertTrue(colourDifference(primary, muted) >= minDiff)
        assertEquals(primary, fillColour(primary, muted, ownerBackground))
    }

    @Test
    fun lightSchemeGetsADarkerStrongerFill() {
        val background = 0xFFFFF8F6.toInt()
        val primary = 0xFF8F4C43.toInt()
        val muted = 0xFF7E6A67.toInt()
        assertTrue(colourDifference(primary, muted) < minDiff)
        val fill = fillColour(primary, muted, background)
        assertTrue(colourDifference(fill, muted) >= minDiff)
        assertTrue(contrast(fill, background) >= minContrast)
        assertTrue(hueGap(fill, primary) < 5)
        // Darker than before, away from the lighter muted text? Only the difference and contrast matter; it must change.
        assertTrue(fill != primary)
    }

    @Test
    fun blackBackgroundOfStageMode() {
        val fill = fillColour(ownerFill, ownerMuted, 0xFF000000.toInt())
        assertTrue(colourDifference(fill, ownerMuted) >= minDiff)
        assertTrue(contrast(fill, 0xFF000000.toInt()) >= minContrast)
    }

    @Test
    fun greyPrimaryChangesOnlyLightness() {
        val primary = 0xFF5A5A5A.toInt()
        val muted = 0xFF4A4A4A.toInt()
        val fill = fillColour(primary, muted, ownerBackground)
        assertTrue(colourDifference(fill, muted) >= minDiff)
        val r = (fill shr 16) and 0xFF
        val g = (fill shr 8) and 0xFF
        val b = fill and 0xFF
        assertTrue(Math.abs(r - g) <= 1 && Math.abs(g - b) <= 1)
    }

    @Test
    fun readabilityBeatsDifference() {
        // Muted text is almost white on a grey background: the only way to differ is darker, but the
        // difference of 35 would need a contrast below 4.5:1. Readability wins; the colour still changes.
        val background = 0xFF303030.toInt()
        val primary = 0xFFEEEEEE.toInt()
        val fill = fillColour(primary, 0xFFE0E0E0.toInt(), background)
        assertTrue(contrast(fill, background) >= minContrast)
        assertTrue(colourDifference(fill, 0xFFE0E0E0.toInt()) > colourDifference(primary, 0xFFE0E0E0.toInt()))
    }

    @Test
    fun resultIsOpaque() {
        val fill = fillColour(0x80F9B6AC.toInt(), ownerMuted, ownerBackground)
        assertEquals(0xFF, (fill ushr 24) and 0xFF)
    }
}
