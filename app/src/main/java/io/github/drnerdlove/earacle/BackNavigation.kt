package io.github.drnerdlove.earacle

/** What the system back gesture does on the main screens (the history page and the search handle their own). */
enum class BackAction {
    /** Leave stage mode, like its leave button. */
    LEAVE_STAGE,

    /** Stop what is running and return to the start screen. */
    GO_HOME,
}

/**
 * Decides what back does: leave stage mode when it is shown, else return to the start screen from any other
 * screen. Null on the start screen itself: no handler, so the system closes the app as usual.
 */
fun backAction(isIdle: Boolean, stageShown: Boolean): BackAction? = when {
    stageShown -> BackAction.LEAVE_STAGE
    !isIdle -> BackAction.GO_HOME
    else -> null
}
