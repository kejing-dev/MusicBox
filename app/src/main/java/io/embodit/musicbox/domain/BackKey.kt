package io.embodit.musicbox.domain

/**
 * Controller B = system BACK (QA #4 q4-1, profile D60): the system turns the controller's B into KEYCODE_BACK, and an
 * unhandled BACK first closed the control window, then sent the whole box volume to the background. The app now takes
 * every BACK itself (both activities: the box's LaunchActivity and the control window's stub) and never exits or hides:
 * holding the key / drum (a drag in progress) -> let go of it (the same release a pinch-up does);
 * the control window closed (system x) -> reopen it beside the box (the nameplate's path);
 * otherwise nothing (BACK is harmless while the window is open).
 */
object BackKey {
    enum class Act { RELEASE_HELD, OPEN_WINDOW, NOTHING }

    fun decide(holding: Boolean, panelOpen: Boolean): Act = when {
        holding -> Act.RELEASE_HELD
        !panelOpen -> Act.OPEN_WINDOW
        else -> Act.NOTHING
    }
}
