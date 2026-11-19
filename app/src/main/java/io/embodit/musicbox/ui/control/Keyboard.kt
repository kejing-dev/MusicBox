package io.embodit.musicbox.ui.control

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.PicoTheme
import io.embodit.musicbox.domain.Comb
import io.embodit.musicbox.domain.Drum
import io.embodit.musicbox.platform.TAG
import io.embodit.musicbox.ui.theme.MBColors

/**
 * The pin keyboard (v0.1.3, QA #3 q3-1): one key per tine, G4..C6, laid out like a piano but in two clean rows so no key
 * hides under another: the 11 naturals ("white keys", >= 50 x 64 dp) below, the 7 sharps ("black keys", 48 x 52 dp,
 * dark) above, each centred over the gap between its two naturals. A key does exactly what a tap on that 3D tine does
 * ([onKey] = BoxCtl tapTine, same path as the box). Labels: numbered notation in Chinese (do = C5, dot below = low
 * octave, dot above = high C), note names in English. Each key's top bar is the tine colour (pins, notes, roll).
 */
object KeyboardSpec {
    val GAP: Dp = 4.dp
    val WHITE_H: Dp = 64.dp
    val BLACK_H: Dp = 52.dp
    val BLACK_W: Dp = 48.dp

    /** Left edge of black key [r] (in dp) for white keys [w] dp wide: centred on the boundary after its natural. */
    fun blackX(r: Int, w: Float): Float {
        val i = Comb.whites.indexOf(r - 1)          // the natural just below the sharp
        val gap = GAP.value
        return (i + 1) * (w + gap) - gap / 2 - BLACK_W.value / 2
    }
}

@Composable
internal fun PinKeyboard(zh: Boolean, enabled: Boolean, onKey: (Int) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val n = Comb.whites.size
        val w = (maxWidth.value - KeyboardSpec.GAP.value * (n - 1)) / n
        Column(verticalArrangement = Arrangement.spacedBy(KeyboardSpec.GAP)) {
            Box(Modifier.fillMaxWidth().height(KeyboardSpec.BLACK_H)) {
                for (r in Comb.blacks) {
                    Key(r, zh, enabled, onKey, Modifier.offset(x = KeyboardSpec.blackX(r, w).dp).width(KeyboardSpec.BLACK_W).height(KeyboardSpec.BLACK_H))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(KeyboardSpec.GAP)) {
                for (r in Comb.whites) Key(r, zh, enabled, onKey, Modifier.width(w.dp).height(KeyboardSpec.WHITE_H))
            }
        }
    }
}

@Composable
private fun Key(r: Int, zh: Boolean, enabled: Boolean, onKey: (Int) -> Unit, modifier: Modifier) {
    val black = Comb.sharp(r)
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val pressed by src.collectIsPressedAsState()
    val face = if (black) MBColors.KeyBlack else MBColors.KeyWhite
    val ink = if (black) MBColors.OnKeyBlack else MBColors.Ink
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .background(if (pressed) MBColors.Brass else if (hovered && enabled) face.copy(alpha = 0.82f) else face)
            .border(if (hovered && enabled) 2.dp else 1.dp, if (hovered && enabled) PicoTheme.colorScheme.interaction else MBColors.RollLineStrong, shape)
            .clickable(interactionSource = src, indication = null, enabled = enabled, role = Role.Button) {
                Log.i(TAG, "KEY $r ${Comb.name(r)}"); onKey(r)
            }
            // one accessibility node per key: the clickable one carries the note name (pico-cli interact finds "C5")
            .semantics(mergeDescendants = true) { contentDescription = Comb.name(r) },
        contentAlignment = Alignment.Center,
    ) {
        // the tine colour, as a bar along the top
        Box(Modifier.align(Alignment.TopCenter).offset(y = 5.dp).width(24.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color(Comb.COLOR[r])))
        KeyLabel(r, zh, ink)
    }
}

/** "5" with a dot below (G4), "#1", "1" with a dot above (C6) in Chinese; "G4" / "C#5" in English. */
@Composable
internal fun KeyLabel(r: Int, zh: Boolean, ink: Color) {
    if (!zh) {
        Txt(Comb.name(r), "edit/key$r", style = PicoTheme.typography.labelLarge, color = ink)
        return
    }
    val (deg, oct) = Comb.jianpu(r)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Dot(oct > 0, ink)
        Txt(deg, "edit/key$r", style = PicoTheme.typography.titleMedium, color = ink)
        Dot(oct < 0, ink)
    }
}

@Composable
private fun Dot(show: Boolean, ink: Color) {
    Box(Modifier.size(5.dp).clip(CircleShape).background(if (show) ink else Color.Transparent))
    Spacer(Modifier.height(1.dp))
}

@Suppress("unused") private val keyCount = Drum.TINES
