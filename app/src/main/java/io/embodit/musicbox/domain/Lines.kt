package io.embodit.musicbox.domain

/**
 * The one table both the lid's song card (second line) and the panel's status sentence read (design content.tone.cardLines,
 * D38). Keys match cardLines; the strings live in res/values/strings.xml (+ values-zh) as `line_<key>`.
 */
object Lines {
    data class Ctx(
        val state: BoxState,
        val notice: Notice?,
        val panelOpen: Boolean = true,
        val nearHint: Boolean = false,
        val coachPin: Boolean = false,
    )

    /** The key of the line to show. */
    fun key(c: Ctx): String = when {
        c.state == BoxState.LOADING -> "loading"
        !c.panelOpen -> "P5"
        c.notice != null -> c.notice.key
        c.nearHint -> "nearHint"
        c.state == BoxState.OPEN && c.coachPin -> "coachPin"
        else -> stateKey(c.state)
    }

    fun stateKey(s: BoxState): String = when (s) {
        BoxState.LOADING -> "loading"
        BoxState.CLOSED -> "B1"
        BoxState.OPEN, BoxState.SWAPPING, BoxState.PINNING -> "B2"
        BoxState.WINDING -> "B3"
        BoxState.PLAYING, BoxState.REWINDING -> "B4"
        BoxState.RUNDOWN -> "B4r"
        BoxState.SCRUB -> "B5"
        BoxState.LID_PAUSED -> "B7"
    }

    /** Why the edit row is grey (D43: a disabled key says why), or null when it works. */
    fun editBlockKey(s: BoxState): String? = when (s) {
        BoxState.CLOSED, BoxState.LID_PAUSED, BoxState.LOADING -> "lidClosedEdit"
        BoxState.PLAYING, BoxState.RUNDOWN, BoxState.SCRUB, BoxState.REWINDING -> "playingEdit"
        else -> null
    }

    val ALL_KEYS = listOf(
        "loading", "B1", "B2", "B3", "B4", "B4r", "B5", "B6add", "B6remove", "B7", "presetFork", "full",
        "lidClosedEdit", "playingEdit", "P5", "nearHint", "coachPin",
    )
}
