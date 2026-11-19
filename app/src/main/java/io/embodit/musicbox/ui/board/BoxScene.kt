package io.embodit.musicbox.ui.board

import android.os.SystemClock
import android.util.Log
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.DirectionalLightComponent
import com.pico.spatial.core.ecs.EnvironmentLightingSettingsComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import io.embodit.musicbox.domain.BoxState
import io.embodit.musicbox.domain.Drum
import io.embodit.musicbox.domain.Finish
import io.embodit.musicbox.domain.Layout
import io.embodit.musicbox.domain.MusicBox
import io.embodit.musicbox.domain.Pin
import io.embodit.musicbox.domain.V3
import io.embodit.musicbox.platform.BoxCtl
import io.embodit.musicbox.platform.TAG
import io.embodit.musicbox.ui.board.input.GazeTargets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** The GLB templates (tools/build_musicbox.py, tools/calibrate_dancer.py). */
internal class Models(
    val boxes: Map<Finish, Entity>, val lids: Map<Finish, Entity>, val glass: Entity?, val stage: Entity, val dancer: Entity?,
    val drum: Entity, val pin: Entity, val tine: Entity, val combBase: Entity, val key: Entity,
)

private fun rad(deg: Double): Float = (deg * PI / 180).toFloat()
private fun qx(deg: Double) = Quat(Vector3(1f, 0f, 0f), rad(deg))
private fun qy(deg: Double) = Quat(Vector3(0f, 1f, 0f), rad(deg))
private fun qz(deg: Double) = Quat(Vector3(0f, 0f, 1f), rad(deg))

/**
 * Every entity of the volume. Holders (named for board-input: lid, tine:r, drum, key, nameplate) carry the collider +
 * Interactable + system HoverEffect with the visible mesh as a child (template README). Motion is read from the
 * [MusicBox] each frame (BoxFrameSystem); nothing moves in Compose.
 *
 * ```
 * root (volume centre) └ content (scale s = measured volume / design size; design metres inside)
 *   ├ box_<finish> (one enabled), glass, combbase, labels, nameplate holder (+ plate, glow), 8 gauge cells
 *   ├ lidHinge (rotates about X) ├ lid_<finish> ├ card ├ "lid" holders (closed: whole lid; open: top edge 4 cm) └ lid glow
 *   ├ stage (lift + spin) └ dancer
 *   ├ "drum" holder (capsule along X) └ axis └ spin (rotates about X) ├ drum.glb └ 256 pins (pool)
 *   ├ "tine:r" x 18 └ pivot (bends about X at the root) └ tine.glb
 *   ├ "key" holder └ pivot (rod axis, turns about X) └ key.glb ; key glow
 *   └ pluck-line glow, 16 notes, light
 * ```
 */
internal class BoxScene(private val ctl: BoxCtl, private val scope: CoroutineScope) {
    val root: Entity = Entity().apply { setName("root") }
    private val content = Entity().apply { setName("content") }
    private var res: SceneResources? = null
    private var built = false
    private var fallback: Entity? = null
    private var scale = Layout.K

    private val boxes = HashMap<Finish, Entity>()
    private val lids = HashMap<Finish, Entity>()
    private val lidHinge = Entity().apply { setName("lidHinge") }
    private var lidHolder: Entity? = null
    private var lidEdge: Entity? = null
    private var lidColliderOpen: Boolean? = null
    private var card: ModelEntity? = null
    private var cardMat: UnlitMaterial? = null
    private var cardTex: TextureResource? = null
    private var cardShown: String? = null
    private var cardBusy = false
    @Volatile var cardText: Pair<String, String> = "" to ""
    private var labels: ModelEntity? = null
    private var labelsMat: UnlitMaterial? = null
    private var labelsZh: Boolean? = null
    @Volatile var zh: Boolean = true
    private var lidGlow: ModelEntity? = null
    private var stage: Entity? = null
    private var spin: Entity? = null
    private val pinPool = ArrayList<Entity>()
    private val tinePivots = ArrayList<Entity>()
    private var keyPivot: Entity? = null
    private var keyGlow: ModelEntity? = null
    private var plateGlow: ModelEntity? = null
    private var lineGlow: ModelEntity? = null
    private val gauge = ArrayList<ModelEntity>()
    private val notes = ArrayList<ModelEntity>()
    private var finish: Finish? = null
    @Volatile var wantFinish: Finish = Finish.WALNUT
    @Volatile var panelOpen = true
    @Volatile var coachLidPulseAt = -1L
    @Volatile var coachGlowAt = -1L
    @Volatile var hover: String? = null
    @Volatile var hoverAt = 0L

    init { root.addChild(content); applyContentTransform() }

    /** Scale + the lean toward the user (Layout.TILT_DEG about X, lifted Layout.TILT_LIFT so the bottom stays in the volume). */
    private fun applyContentTransform() {
        content.transform().setScaleVector(Vector3(scale, scale, scale))
        content.transform().setQuaternion(qx(Layout.TILT_DEG.toDouble()))
        content.transform().setPosition(Vector3(0f, Layout.TILT_LIFT * scale, 0f))
    }

    val ready: Boolean get() = built

    /** The measured volume -> content scale (uniform resize keeps the box filling it, design volume.resizeRestriction). */
    fun setScale(s: Float) {
        if (!s.isFinite() || s <= 0f || abs(s - scale) < 1e-4f) return
        scale = s
        applyContentTransform()
        Log.i(TAG, "SCENE scale=$s")
    }

    val contentScale: Float get() = scale

    private fun place(e: Entity, v: V3) = e.transform().setPosition(Vector3(v.x, v.y, v.z))
    private fun place(e: Entity, x: Float, y: Float, z: Float) = e.transform().setPosition(Vector3(x, y, z))

    /** B0 stand-in: one walnut slab where the box goes until the models are in (design lifecycle "首帧兜底"). */
    fun showFallback(slab: MeshResource, material: Material) {
        if (built || fallback != null) return
        val e = ModelEntity(slab, material).apply { setName("fallbackBox") }
        place(e, (Layout.BX0 + Layout.BX1) / 2, (Layout.BY0 + Layout.BY1) / 2, 0f)
        content.addChild(e)
        fallback = e
        Log.i(TAG, "SCENE fallback shown B0")
    }

    private fun clone(e: Entity): Entity = e.clone(Entity.CloneOptions(recursive = true, shouldShareMaterialInstance = true))!!.also { it.enabled = true }

    fun install(r: SceneResources, m: Models) {
        if (built) { r.close(); return }
        res = r
        val t0 = SystemClock.elapsedRealtime()
        for ((f, e) in m.boxes) { val c = clone(e).apply { setName("box:$f"); enabled = false }; content.addChild(c); boxes[f] = c }
        m.glass?.let { content.addChild(clone(it).apply { setName("glass") }) }
        content.addChild(clone(m.combBase).apply { setName("combBase") })

        // lid on its hinge
        place(lidHinge, Layout.HINGE); content.addChild(lidHinge)
        for ((f, e) in m.lids) { val c = clone(e).apply { setName("lidModel:$f"); enabled = false }; lidHinge.addChild(c); lids[f] = c }
        lidHolder = Entity().apply { setName(BoxNames.LID); place(this, (Layout.BX0 + Layout.BX1) / 2, Layout.LID_T / 2, Layout.LID_L / 2); lidHinge.addChild(this) }
        lidEdge = Entity().apply { setName(BoxNames.LID); place(this, (Layout.BX0 + Layout.BX1) / 2, Layout.LID_T / 2, Layout.LID_L - 0.02f); lidHinge.addChild(this) }
        cardMat = SceneResources.unlit(null).also { r.keep(it) }
        card = ModelEntity(r.cardPlane, cardMat!!).apply {
            setName("songCard"); place(this, Layout.CARD_X, -0.0012f, Layout.CARD_FROM_HINGE)
            transform().setQuaternion(qx(90.0)); lidHinge.addChild(this)
        }
        lidGlow = ModelEntity(r.lidGlowPlane, r.glowStrong).apply {
            setName("lidGlow"); place(this, (Layout.BX0 + Layout.BX1) / 2, Layout.LID_T / 2, Layout.LID_L + 0.0015f); enabled = false; lidHinge.addChild(this)
        }

        // dancer lift
        stage = clone(m.stage).apply { setName("stage"); content.addChild(this) }
        m.dancer?.let { d -> stage!!.addChild(clone(d).apply { setName("dancer") }) } ?: Log.w(TAG, "DANCER missing: the stage stays empty")

        // drum: capsule holder along X (capsule axis = Y, turned 90° about Z), spin child turns about X
        val drumHolder = Entity().apply { setName(BoxNames.DRUM); place(this, Layout.DRUM); transform().setQuaternion(qz(90.0)); content.addChild(this) }
        GazeTargets.install(drumHolder, r.drumShape, r.physics)
        val axis = Entity().apply { setName("drumAxis"); transform().setQuaternion(qz(-90.0)); drumHolder.addChild(this) }
        spin = Entity().apply { setName("drumSpin"); axis.addChild(this) }
        spin!!.addChild(clone(m.drum).apply { setName("drumModel") })
        lineGlow = ModelEntity(r.lineGlowPlane, r.glowSoft).apply {
            setName("pluckLine")
            val a = Math.toRadians(Layout.PLUCK_DEG.toDouble())
            place(this, Layout.DRUM.x, Layout.DRUM.y + (0.0455 * sin(a)).toFloat(), Layout.DRUM.z + (0.0455 * kotlin.math.cos(a)).toFloat())
            transform().setQuaternion(qx(-Layout.PLUCK_DEG.toDouble()))
            content.addChild(this)
        }

        // tines
        for (t in 0 until Drum.TINES) {
            val cy = (Layout.TINE_HIT_Y0 + Layout.TINE_HIT_Y1) / 2; val cz = (Layout.TINE_HIT_Z0 + Layout.TINE_HIT_Z1) / 2
            val holder = Entity().apply { setName(BoxNames.tine(t)); place(this, Layout.tineX(t), cy, cz); content.addChild(this) }
            val pivot = Entity().apply { setName("tinePivot:$t"); place(this, 0f, Layout.TINE_Y - cy, Layout.tineRootZ(t) - cz); holder.addChild(this) }
            // tine.glb is one 3.95 cm tine; the bass tines are longer (graded comb, v0.1.3), so each clone stretches along its length
            pivot.addChild(clone(m.tine).apply { setName("tineModel:$t"); transform().setScaleVector(Vector3(1f, 1f, Layout.tineLen(t) / TINE_GLB_LEN)) })
            GazeTargets.install(holder, r.tineShape, r.physics)
            tinePivots += pivot
        }

        // key
        val kc = (Layout.KEY_HIT_MIN + Layout.KEY_HIT_MAX) * 0.5f
        val keyHolder = Entity().apply { setName(BoxNames.KEY); place(this, kc); content.addChild(this) }
        keyPivot = Entity().apply { setName("keyPivot"); place(this, Layout.KEY_ROOT - kc); keyHolder.addChild(this) }
        keyPivot!!.addChild(clone(m.key).apply { setName("keyModel") })
        GazeTargets.install(keyHolder, r.keyShape, r.physics)
        keyGlow = ModelEntity(r.keyGlowPlane, r.glowSoft).apply {
            setName("keyGlow"); place(this, Layout.KEY_ROOT.x + 0.0235f, Layout.KEY_ROOT.y, Layout.KEY_ROOT.z)
            transform().setQuaternion(qy(90.0)); enabled = false; content.addChild(this)
        }

        // front: nameplate (holder + plate), glow, gauge, labels
        val plateHolder = Entity().apply { setName(BoxNames.PLATE); place(this, Layout.PLATE); content.addChild(this) }
        plateHolder.addChild(ModelEntity(r.platePlane, r.plateMat).apply { setName("plateFace"); place(this, 0f, 0f, 0.0006f) })
        GazeTargets.install(plateHolder, r.plateShape, r.physics)
        plateGlow = ModelEntity(r.plateGlowPlane, r.glowStrong).apply { setName("plateGlow"); place(this, Layout.PLATE.x, Layout.PLATE.y, Layout.PLATE.z + 0.0002f); enabled = false; content.addChild(this) }
        for (i in 0 until 8) gauge += ModelEntity(r.gaugePlane, r.gaugeDim).apply { setName("gauge:$i"); place(this, Layout.gaugeX(i), Layout.PLATE.y, Layout.PLATE.z + 0.0004f); content.addChild(this) }
        labelsMat = SceneResources.unlit(null).also { r.keep(it) }
        labels = ModelEntity(r.labelsPlane, labelsMat!!).apply { setName("combLabels"); place(this, Layout.LABELS); transform().setQuaternion(qx(-45.0)); content.addChild(this) }

        // notes
        repeat(MusicBox.NOTES_MAX) { i -> notes += ModelEntity(r.notePlane, r.noteMats[0]).apply { setName("note:$i"); enabled = false; content.addChild(this) } }

        buildLight()
        fallback?.removeFromParent(); fallback = null
        built = true
        Log.i(TAG, "SCENE built in ${SystemClock.elapsedRealtime() - t0} ms (pins follow in 4 frames)")
        scope.launch { buildPins(m.pin) }
    }

    /** The 256-pin pool, cloned over 4 frames (A9 cold start; design lifecycle). */
    private suspend fun buildPins(template: Entity) {
        val s = spin ?: return
        repeat(4) { chunk ->
            repeat(Drum.PIN_CAP / 4) { pinPool += clone(template).apply { setName("pin:${pinPool.size}"); enabled = false; s.addChild(this) } }
            kotlinx.coroutines.delay(16)
            if (chunk == 3) Log.i(TAG, "SCENE pin pool ${pinPool.size}")
        }
    }

    private fun buildLight() {
        runCatching {
            val light = Entity().apply { setName("keyLight") }
            light.components.set(DirectionalLightComponent(com.pico.spatial.core.math.Color4(1f, 0.94f, 0.86f, 1f), LIGHT_LUX, true))
            light.transform().setQuaternion(qy(-30.0).times(qx(-50.0)))
            root.addChild(light)
            runCatching { root.components.set(EnvironmentLightingSettingsComponent(1.0f)) }.onFailure { Log.w(TAG, "ENV LIGHT unavailable: $it") }
            Log.i(TAG, "LIGHT directional $LIGHT_LUX lux + env 1.0")
        }.onFailure { Log.w(TAG, "LIGHT unavailable: $it") }
    }

    private fun refreshTexts() {
        val r = res ?: return
        val (title, line) = cardText
        val key = "$title\n$line\n${BoxTextures.testF}"
        if (key != cardShown && !cardBusy && title.isNotEmpty()) {
            cardBusy = true
            scope.launch {
                val tex = withContext(Dispatchers.IO) { runCatching { TextureResource.create(BoxTextures.card(title, line)) }.getOrNull() }
                if (tex != null) {
                    cardMat?.setBaseColorTexture(tex)
                    cardTex?.let { runCatching { it.close() } }
                    cardTex = tex; cardShown = key
                    Log.i(TAG, "CARD \"$title\" / \"$line\"")
                }
                cardBusy = false
            }
        }
        val z = zh
        if (labelsZh != z) {
            labelsZh = z
            scope.launch {
                val tex = withContext(Dispatchers.IO) { runCatching { TextureResource.create(BoxTextures.labels(z)) }.getOrNull() }
                if (tex != null) { labelsMat?.setBaseColorTexture(tex); r.keep(tex) }
            }
        }
    }

    fun refreshOrientationTest() { cardShown = null; labelsZh = null }

    // ---------------------------------------------------------------- per frame

    fun onFrame() {
        if (!built) return
        val r = res ?: return
        refreshTexts()
        val f = wantFinish
        if (f != finish) {
            finish = f
            boxes.forEach { (k, e) -> e.enabled = k == f }
            lids.forEach { (k, e) -> e.enabled = k == f }
        }
        val b = ctl.box
        val now = SystemClock.uptimeMillis()
        val pos: Double; val lidAmt: Double; val dancerAmt: Double; val dancerAngle: Double; val keyAngle: Double
        val bends = DoubleArray(Drum.TINES)
        val shown: List<Pair<Pin, Double>>
        val noteList: List<Pair<Int, Double>>
        val cells: Int; val flash: Boolean; val st: BoxState; val t: Double
        synchronized(b) {
            pos = b.visualPos(); lidAmt = b.lidAmt; dancerAmt = b.dancerAmt; dancerAngle = b.dancerAngle; keyAngle = b.keyAngle
            for (i in 0 until Drum.TINES) bends[i] = b.tineBend(i)
            shown = (b.shownPins().map { it to b.pinScale(it) } + b.pinGone.map { (p, at) -> p to (1 - (b.t - at) / MusicBox.EDIT_STEP_S).coerceIn(0.0, 1.0) })
            noteList = b.notes.toList()
            cells = b.gaugeCells(); flash = b.t - b.gaugeFlashAt < 0.4
            st = b.state(); t = b.t
        }
        // lid + colliders (closed: the whole lid; open: only the top 4 cm, so rays reach the tines)
        lidHinge.transform().setQuaternion(qx(-Layout.LID_OPEN_DEG * easeInOut(lidAmt)))
        val open = lidAmt > 0.5
        if (open != lidColliderOpen) {
            lidColliderOpen = open
            val on = if (open) lidEdge!! else lidHolder!!
            val off = if (open) lidHolder!! else lidEdge!!
            off.components.remove(CollisionComponent::class.java); off.components.remove(InteractableComponent::class.java)
            GazeTargets.install(on, if (open) r.lidEdgeShape else r.lidShape, r.physics)
        }
        card?.enabled = lidAmt > 0.05
        // coach: two lid pulses (1 s) in B1; key + pluck line glow 1.5 s after the first open
        val lp = coachLidPulseAt
        lidGlow?.enabled = lidAmt < 0.02 && lp > 0 && now - lp < 1000 && ((now - lp) / 250) % 2 == 0L
        val cg = coachGlowAt
        val coachGlow = cg > 0 && now - cg < 1500
        keyGlow?.enabled = coachGlow && ((now - cg) / 300) % 2 == 0L
        lineGlow?.components?.get(ModelComponent::class.java)?.materials?.set(0, if (coachGlow) r.glowStrong else r.glowSoft)
        lineGlow?.enabled = lidAmt > 0.9 && st != BoxState.SWAPPING
        // P5 with the lid closed: the nameplate breathes
        plateGlow?.enabled = !panelOpen && lidAmt < 0.5 && (now / 600) % 2 == 0L

        // dancer: up / down, turns with the drum (x8)
        stage?.let { s ->
            val y = Layout.STAGE_DOWN_Y + (Layout.STAGE_UP_Y - Layout.STAGE_DOWN_Y) * easeInOut(dancerAmt).toFloat()
            place(s, Layout.WELL.x, y, Layout.WELL.z)
            s.transform().setQuaternion(qy(dancerAngle))
        }
        // drum + pins
        spin?.transform()?.setQuaternion(qx(pos * MusicBox.DEG_PER_COL))
        var used = 0
        for ((p, k) in shown) {
            if (k <= 0.001) continue
            val e = pinPool.getOrNull(used++) ?: break
            val a = Layout.PLUCK_DEG + p.col * MusicBox.DEG_PER_COL
            val ar = Math.toRadians(a)
            e.transform().setPosition(Vector3(Layout.tineX(p.tine) - Layout.DRUM.x, (Layout.DRUM_R * sin(ar)).toFloat(), (Layout.DRUM_R * kotlin.math.cos(ar)).toFloat()))
            e.transform().setQuaternion(qx(90.0 - a))
            e.transform().setScaleVector(Vector3(1f, max(0.05, k).toFloat(), 1f))
            e.enabled = true
        }
        for (i in used until pinPool.size) if (pinPool[i].enabled) pinPool[i].enabled = false
        // tines bend (tip down <= 4°), key turns
        for (i in tinePivots.indices) tinePivots[i].transform().setQuaternion(qx(-4.0 * bends[i]))
        keyPivot?.transform()?.setQuaternion(qx(keyAngle))
        // gauge
        for (i in gauge.indices) {
            val m = when { flash && cells >= 8 -> r.gaugeFlash; i < cells -> r.gaugeLit; else -> r.gaugeDim }
            gauge[i].components[ModelComponent::class.java]?.materials?.set(0, m)
        }
        // notes: rise from above the plucked tine to y <= +0.05 in 1.2 s and fade (only the newest 16)
        for (i in notes.indices) {
            val n = noteList.getOrNull(noteList.size - 1 - i)
            val e = notes[i]
            if (n == null) { e.enabled = false; continue }
            val u = ((t - n.second) / 1.2).coerceIn(0.0, 1.0).toFloat()
            e.components[ModelComponent::class.java]?.materials?.set(0, r.noteMats[n.first])
            place(e, Layout.tineX(n.first), -0.06f + 0.11f * (1 - (1 - u) * (1 - u)), 0.02f)
            val sc = if (u > 0.7f) (1 - (u - 0.7f) / 0.3f).coerceIn(0.05f, 1f) else 1f
            e.transform().setScaleVector(Vector3(sc, sc, sc))
            e.enabled = u < 1f
        }
    }

    private fun easeInOut(u: Double): Double { val v = u.coerceIn(0.0, 1.0); return v * v * (3 - 2 * v) }

    /** Debug hit replay: the holder and its deepest child. */
    fun replayEntities(target: String): List<Entity> {
        val out = ArrayList<Entity>()
        fun deepest(e: Entity): Entity { var x = e; while (x.getChildren().isNotEmpty()) x = x.getChildren().first(); return x }
        fun visit(e: Entity) {
            if (runCatching { e.getName() }.getOrNull() == target && e.enabled && e.components[CollisionComponent::class.java] != null) { out += e; deepest(e).takeIf { it !== e }?.let { out += it } }
            e.getChildren().forEach { visit(it) }
        }
        visit(root)
        return out
    }

    fun evidence(): String = "scale=$scale finish=$finish pins=${pinPool.count { it.enabled }}/${pinPool.size} card=\"${cardShown?.replace("\n", " / ")}\" lidCollider=${if (lidColliderOpen == true) "edge" else "whole"}"

    fun dispose() {
        cardTex?.let { runCatching { it.close() } }
        res?.close(); res = null
    }

    companion object {
        /** Spatial SDK light is lux-scale (build.md 2026-11-12); 1500 matches wind-chime / zen-orb in Shared Space. */
        const val LIGHT_LUX = 1500f
        /** tine.glb's length (tools/build_musicbox.py build_tine). */
        const val TINE_GLB_LEN = 0.0395f
    }
}

/** Every holder name (one place). */
object BoxNames {
    const val LID = "lid"
    const val DRUM = "drum"
    const val KEY = "key"
    const val PLATE = "nameplate"
    fun tine(r: Int) = "tine:$r"

    sealed interface Hit
    data object Lid : Hit
    data object DrumHit : Hit
    data object Key : Hit
    data object Plate : Hit
    data class Tine(val r: Int) : Hit
    data object None : Hit

    fun hitOf(name: String?): Hit = when {
        name == null -> None
        name == LID -> Lid
        name == DRUM -> DrumHit
        name == KEY -> Key
        name == PLATE -> Plate
        name.startsWith("tine:") -> name.removePrefix("tine:").toIntOrNull()?.takeIf { it in 0 until Drum.TINES }?.let { Tine(it) } ?: None
        else -> None
    }
}
