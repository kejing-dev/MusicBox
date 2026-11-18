package io.embodit.musicbox.ui.control

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SegmentControl
import com.pico.spatial.ui.design.SegmentItem
import com.pico.spatial.ui.design.Slider
import com.pico.spatial.ui.design.Text
import io.embodit.musicbox.domain.Speed
import io.embodit.musicbox.domain.Volume
import io.embodit.musicbox.platform.TAG
import io.embodit.musicbox.ui.theme.MBColors

/** Text that logs "OVERFLOW <panel>/<control>" whenever it is cut (profile D13; the overflow sweep reads the log). */
@Composable
internal fun Txt(
    text: String,
    tag: String,
    style: TextStyle = PicoTheme.typography.bodyLarge,
    color: Color = PicoTheme.colorScheme.labelPrimary,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    align: TextAlign = TextAlign.Start,
) {
    Text(
        text = text, style = style, color = color, modifier = modifier, maxLines = maxLines, softWrap = maxLines > 1, textAlign = align,
        onTextLayout = { if (it.hasVisualOverflow) Log.w(TAG, "OVERFLOW $tag \"$text\"") },
    )
}

/** Buttons are 64 dp tall (profile B31: main buttons >= 64 dp; gate button-height). */
@Composable
internal fun PrimaryButton(text: String, tag: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: Int? = null) {
    Button(
        onClick = onClick, modifier = modifier, enabled = enabled,
        size = ButtonDefaults.Regular.copy(height = 64.dp, minWidth = 160.dp),
        shape = RoundedCornerShape(32.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PicoTheme.colorScheme.interaction, contentColor = PicoTheme.colorScheme.labelPrimaryLight),
    ) {
        if (icon != null) {
            Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp), tint = PicoTheme.colorScheme.labelPrimaryLight)
            Box(Modifier.width(8.dp))
        }
        // design-style: inherited-content-color Button
        Txt(text, "$tag/primary", style = PicoTheme.typography.titleMedium, color = PicoTheme.colorScheme.labelPrimaryLight)
    }
}

@Composable
internal fun SecondaryButton(text: String, tag: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, danger: Boolean = false, icon: Int? = null) {
    val fg = if (danger) MBColors.OnError else PicoTheme.colorScheme.labelPrimary
    Button(
        onClick = onClick, modifier = modifier, enabled = enabled,
        size = ButtonDefaults.Regular.copy(height = 64.dp, minWidth = 88.dp),
        shape = RoundedCornerShape(32.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (danger) MBColors.Error else PicoTheme.colorScheme.fillSecondary, contentColor = fg),
    ) {
        if (icon != null) {
            Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp), tint = fg)
            Box(Modifier.width(6.dp))
        }
        // design-style: inherited-content-color Button
        Txt(text, "$tag/button", style = PicoTheme.typography.labelLarge, color = fg)
    }
}

/** A switch-like button (D19): its on / off state is the fill, and a small lamp on the left. */
@Composable
internal fun ToggleButton(text: String, tag: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val fg = if (on) PicoTheme.colorScheme.labelPrimaryLight else PicoTheme.colorScheme.labelPrimary
    Button(
        onClick = onClick, modifier = modifier,
        size = ButtonDefaults.Regular.copy(height = 64.dp, minWidth = 120.dp),
        shape = RoundedCornerShape(32.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (on) PicoTheme.colorScheme.interaction else PicoTheme.colorScheme.fillSecondary, contentColor = fg),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (on) MBColors.PrimaryContainer else Color.Transparent).border(1.5.dp, fg, CircleShape))
        Box(Modifier.width(8.dp))
        // design-style: inherited-content-color Button
        Txt(text, "$tag/toggle", style = PicoTheme.typography.labelLarge, color = fg)
    }
}

/** A labelled row holding a same-page option switch (SegmentControl is never used to change pages). */
@Composable
internal fun OptionRow(label: String, tag: String, labelWidth: Dp = 112.dp, content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
        Txt(label, "$tag/label", style = PicoTheme.typography.labelLarge, color = PicoTheme.colorScheme.labelPrimary, modifier = Modifier.width(labelWidth))
        content()
    }
}

@Composable
internal fun <T> Segments(options: List<Pair<T, String>>, selected: T, tag: String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    // B19: the control itself is >= 48 dp tall (the default track is ~37 dp)
    SegmentControl(modifier = modifier.height(52.dp)) {
        options.forEach { (value, label) ->
            SegmentItem(
                selected = value == selected,
                onClick = { onSelect(value) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                title = {
                    // design-style: inherited-content-color SegmentItem
                    Txt(label, "$tag/segment", style = PicoTheme.typography.labelLarge, color = PicoTheme.colorScheme.labelPrimary)
                },
            )
        }
    }
}

/**
 * Volume: a draggable slider that snaps to three stops (low / mid / high) on release and shows the percent (B20).
 * Local float while dragging, snap in onValueChangeFinished; callbacks through rememberUpdatedState (gate slider-stale).
 */
@Composable
internal fun VolumeSlider(level: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val cb by rememberUpdatedState(onChange)
    var local by remember { mutableFloatStateOf(level / 2f) }
    LaunchedEffect(level) { local = level / 2f }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = local,
            onValueChange = { v -> local = v },
            onValueChangeFinished = { val s = Volume.snap(local); local = s / 2f; cb(s) },
            modifier = Modifier.weight(1f).height(48.dp),
            valueRange = 0f..1f,
        )
        Box(Modifier.width(12.dp))
        Txt("${Volume.PERCENT[Volume.snap(local)]}%", "settings/volume-pct", style = PicoTheme.typography.labelLarge, modifier = Modifier.width(60.dp))
    }
}

/** Speed 50..150 %, snapping to 75 / 100 / 125 % near those stops (design tempo.speedSlider). */
@Composable
internal fun SpeedSlider(speed: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val cb by rememberUpdatedState(onChange)
    var local by remember { mutableFloatStateOf(speed) }
    LaunchedEffect(speed) { local = speed }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = local,
            onValueChange = { v -> local = v },
            onValueChangeFinished = { val s = Speed.snap(local); local = s; cb(s) },
            modifier = Modifier.weight(1f).height(48.dp),
            valueRange = 0.5f..1.5f,
        )
        Box(Modifier.width(12.dp))
        Txt("${Speed.percent(local)}%", "play/speed-pct", style = PicoTheme.typography.labelLarge, modifier = Modifier.width(60.dp))
    }
}

/** The spring gauge (8 cells, read-only; the same state as the box's brass cells, D38). */
@Composable
internal fun SpringCells(cells: Int, flash: Boolean, modifier: Modifier = Modifier) {
    Row(modifier.height(28.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until 8) {
            val lit = i < cells
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(6.dp))
                    .background(if (flash && cells >= 8) MBColors.Amber else if (lit) MBColors.Brass else MBColors.BrassDim.copy(alpha = 0.35f))
                    .border(1.dp, MBColors.BrassDim, RoundedCornerShape(6.dp)),
            )
            if (i < 7) Box(Modifier.width(6.dp))
        }
    }
}
