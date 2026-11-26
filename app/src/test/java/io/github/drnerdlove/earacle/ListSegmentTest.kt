package io.github.drnerdlove.earacle

import org.junit.Assert.assertEquals
import org.junit.Test

class ListSegmentTest {
    @Test
    fun singleItemIsRoundAtBothEnds() {
        assertEquals(SegmentCorners(bigTop = true, bigBottom = true), segmentCorners(0, 1))
    }

    @Test
    fun groupHasBigCornersOnlyAtItsOuterEnds() {
        assertEquals(SegmentCorners(bigTop = true, bigBottom = false), segmentCorners(0, 3))
        assertEquals(SegmentCorners(bigTop = false, bigBottom = false), segmentCorners(1, 3))
        assertEquals(SegmentCorners(bigTop = false, bigBottom = true), segmentCorners(2, 3))
    }

    @Test
    fun twoItemsJoinWithSmallCorners() {
        assertEquals(SegmentCorners(bigTop = true, bigBottom = false), segmentCorners(0, 2))
        assertEquals(SegmentCorners(bigTop = false, bigBottom = true), segmentCorners(1, 2))
    }
}
