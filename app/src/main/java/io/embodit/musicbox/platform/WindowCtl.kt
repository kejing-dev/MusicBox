package io.embodit.musicbox.platform

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pico.spatial.ui.platform.containers.SpatialNavigator
import io.embodit.musicbox.ui.Containers

/**
 * Opens / reopens the control window. Shared Space (profile D44 correction): the panel always opens beside the box
 * (Placement.right(box), declared in Main.kt), never "in front of you"; the user moves it with the system handle.
 * The app never reads the head pose. P5 (panel closed with the system [×]): a tap on the brass nameplate reopens it
 * with the same id + tag (design T12). Exit closes both containers before the tasks are removed (D44).
 */
class WindowCtl {
    var panelState by mutableStateOf("never")
        private set
    val panelOpen: Boolean get() = panelState != "ON_DESTROY"

    private var navigator: SpatialNavigator? = null
    private var panelNavigator: SpatialNavigator? = null
    private val main = Handler(Looper.getMainLooper())
    var onPanelChange: ((Boolean) -> Unit)? = null

    /** The box volume hands over its navigator (the window's own composition can't reopen itself). */
    fun attach(nav: SpatialNavigator) { navigator = nav }

    fun detach() { navigator = null; main.removeCallbacksAndMessages(null) }

    fun attachPanel(nav: SpatialNavigator) { panelNavigator = nav }

    /** Opens the control window beside the box (first open, nameplate tap, app icon tapped again). */
    fun open(why: String) {
        Log.i(TAG, "WINDOW open why=$why state=$panelState")
        runCatching { (navigator ?: panelNavigator)?.openWindowContainer(Containers.CONTROL, Containers.CONTROL_TAG) }
            .onFailure { Log.e(TAG, "WINDOW open failed", it) }
    }

    fun onPanelLifecycle(event: String) {
        panelState = event
        Log.i(TAG, "WINDOW lifecycle $event")
        onPanelChange?.invoke(panelOpen)
    }

    /** Debug: the system [×] on the control window (P5). */
    fun closeControlOnly() { runCatching { navigator?.closeWindowContainer(Containers.CONTROL, Containers.CONTROL_TAG) } }

    /** Exit (D26 / D44): close the control window and the box volume itself. */
    fun closeAll() {
        val nav = navigator ?: panelNavigator ?: return
        runCatching { nav.closeWindowContainer(Containers.CONTROL, Containers.CONTROL_TAG) }.onFailure { Log.w(TAG, "EXIT close control: $it") }
        runCatching { navigator?.closeWindowContainer() }.onFailure { Log.w(TAG, "EXIT close box: $it") }
        Log.i(TAG, "EXIT closed all window containers")
    }
}
