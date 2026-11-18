package io.embodit.musicbox.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Ids of the two window containers. The box volume is the default container (manifest). */
object Containers {
    const val BOX = "MusicBoxVolume"
    const val CONTROL = "MusicBoxControl"
    const val CONTROL_TAG = "control"
}

/** The three pages of the control window (design panels.control: 播放 / 曲子 / 设置). */
enum class Page(val stateId: String) { PLAY("P1"), SONGS("P2"), SETTINGS("P3") }

/** Confirmations unfold in place (P4): clear the drum, delete one of my tunes, quit. */
sealed interface Confirm {
    data object Clear : Confirm
    data class Delete(val id: String) : Confirm
    data object Quit : Confirm
}

/** Window-page state shared process-wide so debug commands can switch pages. */
object Ui {
    var page by mutableStateOf(Page.PLAY)
    var confirm by mutableStateOf<Confirm?>(null)
    var songsTab by mutableStateOf(0)
    var howTo by mutableStateOf(false)
}
