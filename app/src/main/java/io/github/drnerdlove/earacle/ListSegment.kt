package io.github.drnerdlove.earacle

/**
 * Where an item sits in a group of rounded list items (Material 3 Expressive "segmented" lists): the outer ends of
 * the group get big corners, the joins between two items small ones. Kept free of Compose so it can be unit-tested.
 */
internal data class SegmentCorners(val bigTop: Boolean, val bigBottom: Boolean)

/** The corners of item [index] (0-based) in a group of [count] items; a single item is round at both ends. */
internal fun segmentCorners(index: Int, count: Int): SegmentCorners =
    SegmentCorners(bigTop = index == 0, bigBottom = index == count - 1)
