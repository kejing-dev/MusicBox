package io.embodit.musicbox.ui.board

import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.SceneUpdateContext
import com.pico.spatial.core.ecs.System
import com.pico.spatial.core.ecs.ViewCoordinateSpace
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.dsl.registerSystem
import com.pico.spatial.ui.foundation.dsl.unregisterSystem
import com.pico.spatial.ui.geometry.Offset3D
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import io.embodit.musicbox.domain.Finish
import io.embodit.musicbox.domain.Layout
import io.embodit.musicbox.domain.Lines
import io.embodit.musicbox.platform.Graph
import io.embodit.musicbox.platform.TAG
import io.embodit.musicbox.ui.board.input.BoardInputConfig
import io.embodit.musicbox.ui.board.input.BoardInputListener
import io.embodit.musicbox.ui.board.input.BoardInputLog
import io.embodit.musicbox.ui.board.input.InputSource
import io.embodit.musicbox.ui.board.input.Offset3M
import io.embodit.musicbox.ui.board.input.TargetResolver
import io.embodit.musicbox.ui.board.input.boardInput
import io.embodit.musicbox.ui.lineText
import io.embodit.musicbox.ui.theme.localized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min

/** Runs the volume's per-frame drawing inside the ECS (never in Compose). The simulation itself runs in BoxCtl's thread. */
class BoxFrameSystem : System() {
    override fun update(context: SceneUpdateContext) { SceneHolder.scene?.onFrame() }
}

internal object SceneHolder {
    @Volatile var scene: BoxScene? = null
}

/**
 * The box volume (default container). Opens the control window beside it, shows the box, and routes every pinch /
 * trigger / drag on a holder (lid, tine:r, drum, key, nameplate) to the box through the factory board-input template
 * (D50: hand ray, controller ray and eye-hand alike; drags only on the key and the drum, design input.drag).
 */
@Composable
fun BoxScreen() {
    val context = LocalContext.current
    val graph = remember(context) { Graph.from(context) }
    val ui by graph.box.ui.collectAsStateWithLifecycle()
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    val navigator = LocalSpatialNavigator.current
    val scope = rememberCoroutineScope()
    val scene = remember { BoxScene(graph.box, scope) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    DisposableEffect(navigator) {
        graph.window.attach(navigator)
        graph.window.open("start")
        onDispose { graph.window.detach() }
    }

    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            when (e) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> { Log.i(TAG, "LIFECYCLE background"); graph.box.onBackground() }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    Log.i(TAG, "LIFECYCLE resume panel=${graph.window.panelState}")
                    if (graph.window.panelState == "ON_DESTROY") graph.window.open("resume")
                }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    DisposableEffect(scene) {
        SceneHolder.scene = scene
        registerSystem<BoxFrameSystem>()
        onDispose {
            unregisterSystem<BoxFrameSystem>()
            if (SceneHolder.scene === scene) SceneHolder.scene = null
            scene.dispose()
        }
    }

    // the card's two lines (D38: the same table and the same state source as the panel's status sentence)
    val loc = remember(context, settings.language) { context.localized(settings.language) }
    val zh = loc.getString(io.embodit.musicbox.R.string.lang_code) == "zh"
    val key = Lines.key(Lines.Ctx(ui.state, ui.notice, graph.window.panelOpen, ui.nearHint, ui.coachPin))
    scene.cardText = (ui.song?.name(zh) ?: "") to lineText(loc, key)
    scene.zh = zh
    scene.wantFinish = settings.finish
    scene.panelOpen = graph.window.panelOpen
    LaunchedEffect(ui.state) { graph.box.coachCheck() }

    SpatialView(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewSize = it }
            .boardInput(remember { BoxTargets() }, remember(graph, scene) { BoxListener(graph, scene) }, BoardInputConfig(dragStartM = 0.008f, logTag = TAG)),
        initial = { content, _ ->
            content.addEntity(scene.root)
            val quick = withContext(NonCancellable + Dispatchers.IO) {
                runCatching {
                    // gate:allow geometry B0 loading stand-in (design lifecycle "首帧兜底"), removed when the GLBs are in
                    val slab = MeshResource.createBox(Vector3(Layout.BX1 - Layout.BX0, Layout.BY1 - Layout.BY0, Layout.BZ1 - Layout.BZ0), cornerRadius = 0.004f)
                    val mat = PhysicallyBasedMaterial.create().apply { setBaseColor(Color4(0.29f, 0.20f, 0.13f, 1f)); setRoughness(0.7f) }
                    slab to mat
                }.getOrNull()
            }
            quick?.let { (s, m) -> scene.showFallback(s, m) }
            val t0 = android.os.SystemClock.elapsedRealtime()
            suspend fun load(n: String): Entity = Entity.loadSuspend("asset://models/$n.glb").also { it.enabled = false; content.addEntity(it) }
            val models = runCatching {
                Models(
                    boxes = mapOf(Finish.WALNUT to load("box_walnut"), Finish.MAPLE to load("box_maple"), Finish.RED to load("box_red")),
                    lids = mapOf(Finish.WALNUT to load("lid_walnut"), Finish.MAPLE to load("lid_maple"), Finish.RED to load("lid_red")),
                    glass = runCatching { load("glass") }.getOrNull(),
                    stage = load("stage"), dancer = null,
                    drum = load("drum"), pin = load("pin"), tine = load("tine"), combBase = load("combbase"), key = load("key"),
                )
            }.onFailure { Log.e(TAG, "MODELS failed", it) }.getOrNull()
            val resources = withContext(NonCancellable + Dispatchers.Default) { SceneResources.create() }
            Log.i(TAG, "MODELS loaded=${models != null} in ${android.os.SystemClock.elapsedRealtime() - t0} ms")
            val prefs = context.getSharedPreferences("debug", android.content.Context.MODE_PRIVATE)
            if (prefs.getBoolean("b0hold", false)) { Log.i(TAG, "B0 hold 10 s"); kotlinx.coroutines.delay(10_000) }
            if (models != null) {
                // the dancer last (design lifecycle): if it fails the stage stays empty, nothing breaks
                val dancer = runCatching { load("dancer") }.onFailure { Log.w(TAG, "DANCER load failed: $it") }.getOrNull()
                scene.install(resources, Models(models.boxes, models.lids, models.glass, models.stage, dancer, models.drum, models.pin, models.tine, models.combBase, models.key))
            }
            graph.box.act("loaded") { it.finishLoading() }
            if (!graph.settings.value.coachDone) scene.coachLidPulseAt = android.os.SystemClock.uptimeMillis()
        },
        update = { content, _ ->
            if (viewSize != IntSize.Zero) runCatching {
                val p = content.convertPosition(Vector3.ZERO, ViewCoordinateSpace.Local, content.localSpatialCoordinateSpace)
                val s = min(abs(p.x) / (Layout.VOL_W / 2), abs(p.y) / (Layout.VOL_H / 2))
                if (s.isFinite() && s > 0.1f) scene.setScale(s.coerceIn(0.3f, 5f))
            }.onFailure { Log.w(TAG, "SCALE measure failed: $it") }
        },
    )
}

/** Hit entity (or an ancestor) -> the holder name (lid, tine:r, drum, key, nameplate). */
internal class BoxTargets : TargetResolver<String> by TargetResolver.byName(::targetOf) {
    companion object {
        fun targetOf(name: String): String? = name.takeIf { BoxNames.hitOf(it) != BoxNames.None }

        /** Hit replay (build.md 命中回放): [path] = the hit node's name first, then its parents'. */
        fun resolvePath(path: List<String>): String? = io.embodit.musicbox.ui.board.input.resolvePath(path, ::targetOf)
    }
}

/** Taps / drags -> the box (the same calls the panel and the debug broadcast make, D29). */
internal class BoxListener(private val graph: Graph, private val scene: BoxScene) : BoardInputListener<String> {
    private var dragging: BoxNames.Hit? = null

    override fun onTap(target: String?, source: InputSource, position: Offset3D?) {
        BoardInputLog.write(TAG, "INPUT tap src=${source.label} target=$target -> part=${BoxNames.hitOf(target)}")
        routeTap(graph, scene, target)
    }

    override fun onDragStart(target: String, source: InputSource): Boolean {
        val h = BoxNames.hitOf(target)
        BoardInputLog.write(TAG, "INPUT drag src=${source.label} target=$target -> part=$h")
        graph.box.touched3d()
        dragging = when (h) {
            BoxNames.Key -> { graph.box.act("key drag") { it.keyDragStart() }; h }
            BoxNames.DrumHit -> { graph.box.act("drum drag") { it.scrubStart() }; h }
            else -> null
        }
        return dragging != null
    }

    override fun onDragMove(totalM: Offset3M) {
        val down = -totalM.y.toDouble()
        when (dragging) {
            BoxNames.Key -> graph.box.act("key", log = false) { it.keyDragMove(down) }
            BoxNames.DrumHit -> graph.box.act("drum", log = false) { it.scrubMove(down / scene.contentScale) }
            else -> Unit
        }
    }

    override fun onDragEnd() { endDrag() }

    override fun onDragCancel() { endDrag() }

    private fun endDrag() {
        when (dragging) {
            BoxNames.Key -> graph.box.act("key release") { it.keyDragEnd() }
            BoxNames.DrumHit -> graph.box.act("drum release") { it.endScrub() }
            else -> Unit
        }
        dragging = null
    }

    override fun onHover(target: String?, source: InputSource) {
        val now = android.os.SystemClock.uptimeMillis()
        if (target != scene.hover && target != null) Log.i(TAG, "HOVER $target")
        scene.hover = target; scene.hoverAt = now
    }
}

/** Holder name -> the one box call. */
internal fun routeTap(graph: Graph, scene: BoxScene?, name: String?) {
    val h = BoxNames.hitOf(name)
    if (h != BoxNames.None) graph.box.touched3d()
    when (h) {
        BoxNames.Lid -> {
            val opening = graph.box.act("tap lid") { b -> val o = !b.lidTarget; b.tapLid(); o }
            if (opening && !graph.settings.value.coachDone && scene != null && scene.coachGlowAt < 0) scene.coachGlowAt = android.os.SystemClock.uptimeMillis()
        }
        is BoxNames.Tine -> { graph.box.act("tap tine ${h.r}") { it.tapTine(h.r) }; graph.box.coachCheck() }
        BoxNames.Key -> graph.box.act("tap key") { it.tapKey() }
        BoxNames.Plate -> if (!graph.window.panelOpen) graph.window.open("nameplate") else Log.i(TAG, "PLATE tap (panel already open)")
        BoxNames.DrumHit -> Log.i(TAG, "DRUM tap: no action (drag it)")
        BoxNames.None -> Unit
    }
}
