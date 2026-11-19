package io.embodit.musicbox

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.foundation.dsl.DefaultWindowContainer
import com.pico.spatial.ui.foundation.dsl.Form
import com.pico.spatial.ui.foundation.dsl.Placement
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.WindowContainer
import com.pico.spatial.ui.foundation.dsl.WindowContainerSize
import io.embodit.musicbox.ui.Containers
import io.embodit.musicbox.ui.board.BoxScreen
import io.embodit.musicbox.ui.control.ControlWindow
import io.embodit.musicbox.ui.theme.MusicBoxTheme

/**
 * Two surfaces in Shared Space (design windowModel volume_plus_control_window): the music box volume (default container,
 * 0.64 x 0.512 x 0.474 m from the manifest = design 0.50 x 0.40 x 0.37 x k 1.28) and the control window (Planar 640 x 600 dp; v0.1.3 wider for the 18-key keyboard)
 * to its right. Every open uses Placement.right(box) (D44: Shared Space never promises "in front of you").
 */
fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultWindowContainer {
            MusicBoxTheme { BoxScreen() }
        }
        WindowContainer(
            Containers.CONTROL,
            form = Form.Planar,
            defaultSize = WindowContainerSize(width = 640.dp, height = 600.dp),
            placement = { containers.firstOrNull { it.id == Containers.BOX }?.let { Placement.right(it) } ?: Placement.right(containers.first()) },
        ) {
            MusicBoxTheme {
                Box(Modifier.windowConstraints(minWidth = 620.dp, minHeight = 560.dp, maxWidth = 760.dp, maxHeight = 640.dp)) { ControlWindow() }
            }
        }
    }
