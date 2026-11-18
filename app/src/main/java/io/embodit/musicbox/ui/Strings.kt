package io.embodit.musicbox.ui

import android.content.Context
import io.embodit.musicbox.R

/** The one card / status table (design content.tone.cardLines, D38): key -> string resource. */
fun lineRes(key: String): Int = when (key) {
    "loading" -> R.string.line_loading
    "B1" -> R.string.line_B1
    "B2" -> R.string.line_B2
    "B3" -> R.string.line_B3
    "B4" -> R.string.line_B4
    "B4r" -> R.string.line_B4r
    "B5" -> R.string.line_B5
    "B6add" -> R.string.line_B6add
    "B6remove" -> R.string.line_B6remove
    "B7" -> R.string.line_B7
    "presetFork" -> R.string.line_presetFork
    "full" -> R.string.line_full
    "lidClosedEdit" -> R.string.line_lidClosedEdit
    "playingEdit" -> R.string.line_playingEdit
    "P5" -> R.string.line_P5
    "nearHint" -> R.string.line_nearHint
    "coachPin" -> R.string.line_coachPin
    else -> R.string.line_B2
}

fun lineText(context: Context, key: String): String = context.getString(lineRes(key))
