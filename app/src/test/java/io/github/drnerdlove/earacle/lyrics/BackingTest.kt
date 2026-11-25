package io.github.drnerdlove.earacle.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class BackingTest {
    private fun joined(text: String) = splitBacking(text).joinToString("") { it.text }

    @Test
    fun backingAfterLeadKeepsTheSpace() {
        assertEquals(
            listOf(Segment("Dancing ", false), Segment("(Dancing)", true)),
            splitBacking("Dancing (Dancing)"),
        )
    }

    @Test
    fun lineAllInParenthesesIsOneBackingSegment() {
        assertEquals(listOf(Segment("(See you groove)", true)), splitBacking("(See you groove)"))
    }

    @Test
    fun twoPartsInParentheses() {
        assertEquals(
            listOf(
                Segment("(Oh)", true),
                Segment(" I know ", false),
                Segment("(yeah)", true),
                Segment(" it", false),
            ),
            splitBacking("(Oh) I know (yeah) it"),
        )
    }

    @Test
    fun lineWithoutParenthesesIsOneNormalSegment() {
        assertEquals(listOf(Segment("Hello world", false)), splitBacking("Hello world"))
    }

    @Test
    fun unbalancedParenthesesStayNormalText() {
        assertEquals(listOf(Segment("Hey (oh", false)), splitBacking("Hey (oh"))
        assertEquals(listOf(Segment("Hey oh)", false)), splitBacking("Hey oh)"))
        assertEquals(listOf(Segment("a ) b ( c", false)), splitBacking("a ) b ( c"))
    }

    @Test
    fun nestedParenthesesStayNormalText() {
        assertEquals(listOf(Segment("a (b (c))", false)), splitBacking("a (b (c))"))
    }

    @Test
    fun emptyParenthesesAreBackingAndEmptyTextHasNoSegments() {
        assertEquals(listOf(Segment("Hi ", false), Segment("()", true)), splitBacking("Hi ()"))
        assertEquals(emptyList<Segment>(), splitBacking(""))
    }

    @Test
    fun joiningTheSegmentsGivesBackTheOriginal() {
        for (text in listOf("Dancing (Dancing)", "(a)(b)", "  (x)  y  ", "Hey (oh", "a (b (c))", "()", "Ünï (çödé) 🎵 (🎶)")) {
            assertEquals(text, joined(text))
        }
    }
}
