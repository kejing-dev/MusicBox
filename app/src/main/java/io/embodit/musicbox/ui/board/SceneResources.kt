package io.embodit.musicbox.ui.board

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.Resource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector3
import io.embodit.musicbox.domain.Layout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal fun Entity.transform(): TransformComponent = components[TransformComponent::class.java]!!

/**
 * Planes, textures, materials and click shapes of the volume (the wood and metal are GLBs). Creating a resource waits on
 * the renderer, so everything is created concurrently on Dispatchers.IO (A9 cold start).
 */
internal class SceneResources private constructor() {
    private val all = ArrayList<Resource>()

    lateinit var cardPlane: MeshResource; private set
    lateinit var platePlane: MeshResource; private set
    lateinit var plateMat: Material; private set
    lateinit var labelsPlane: MeshResource; private set
    lateinit var notePlane: MeshResource; private set
    lateinit var noteMats: List<UnlitMaterial>; private set
    lateinit var gaugePlane: MeshResource; private set
    lateinit var gaugeLit: Material; private set
    lateinit var gaugeDim: Material; private set
    lateinit var gaugeFlash: Material; private set
    lateinit var lidGlowPlane: MeshResource; private set
    lateinit var lineGlowPlane: MeshResource; private set
    lateinit var keyGlowPlane: MeshResource; private set
    lateinit var plateGlowPlane: MeshResource; private set
    lateinit var glowStrong: UnlitMaterial; private set
    lateinit var glowSoft: UnlitMaterial; private set

    lateinit var lidShape: ShapeResource; private set
    lateinit var lidEdgeShape: ShapeResource; private set
    lateinit var tineShape: ShapeResource; private set
    lateinit var drumShape: ShapeResource; private set
    lateinit var keyShape: ShapeResource; private set
    lateinit var plateShape: ShapeResource; private set
    lateinit var physics: PhysicsMaterialResource; private set

    fun <T : Resource> keep(r: T): T { r.toGlobal(); synchronized(all) { all += r }; return r }

    fun close() { synchronized(all) { all.forEach { runCatching { it.close() } }; all.clear() } }

    companion object {
        fun unlit(tex: TextureResource?, opacity: Float = 1f, transparent: Boolean = false): UnlitMaterial =
            UnlitMaterial.create(if (transparent || opacity < 1f) BlendingMode.TRANSPARENT else BlendingMode.OPAQUE).apply {
                setBaseColor(Color4(1f, 1f, 1f, 1f))
                if (opacity < 1f) setOpacity(opacity)
                tex?.let { setBaseColorTexture(it) }
                setCullingMode(MaterialCullingMode.BACK)
                setApplyToneMapping(false)
            }

        suspend fun create(): SceneResources = coroutineScope {
            val r = SceneResources()
            fun <T : Resource> T.keep(): T = r.keep(this)
            val io = Dispatchers.IO
            val noteTex = BoxTextures.TINE_COLORS.map { c -> async(io) { TextureResource.create(BoxTextures.note(c)).keep() } }
            val glowTex = async(io) { TextureResource.create(BoxTextures.glow()).keep() }
            val plateTex = async(io) { TextureResource.create(BoxTextures.plate()).keep() }
            listOf(
                async(io) { r.cardPlane = MeshResource.createPlane(Layout.CARD_W, Layout.CARD_H, 0.004f).keep() },
                async(io) { r.platePlane = MeshResource.createPlane(Layout.PLATE_W, Layout.PLATE_H, 0.002f).keep() },
                async(io) { r.labelsPlane = MeshResource.createPlane(Layout.LABELS_W, Layout.LABELS_H, 0f).keep() },
                async(io) { r.notePlane = MeshResource.createPlane(0.022f, 0.022f, 0f).keep() },
                async(io) { r.gaugePlane = MeshResource.createPlane(Layout.GAUGE_W, Layout.GAUGE_H, 0.001f).keep() },
                async(io) { r.lidGlowPlane = MeshResource.createPlane(0.40f, 0.03f, 0f).keep() },
                async(io) { r.lineGlowPlane = MeshResource.createPlane(Layout.DRUM_L, 0.008f, 0f).keep() },
                async(io) { r.keyGlowPlane = MeshResource.createPlane(0.08f, 0.08f, 0f).keep() },
                async(io) { r.plateGlowPlane = MeshResource.createPlane(0.11f, 0.05f, 0f).keep() },
                async(io) { r.lidShape = ShapeResource.createBox(Vector3(0.38f, Layout.LID_T, Layout.LID_L)).keep() },
                async(io) { r.lidEdgeShape = ShapeResource.createBox(Vector3(0.38f, 0.02f, 0.04f)).keep() },
                async(io) { r.tineShape = ShapeResource.createBox(Vector3(Layout.TINE_HIT_W, Layout.TINE_HIT_Y1 - Layout.TINE_HIT_Y0, Layout.TINE_HIT_Z1 - Layout.TINE_HIT_Z0)).keep() },
                async(io) { r.drumShape = ShapeResource.createCapsule(Layout.DRUM_HIT_R, Layout.DRUM_L).keep() },
                async(io) { r.keyShape = ShapeResource.createBox(Vector3(Layout.KEY_HIT_MAX.x - Layout.KEY_HIT_MIN.x, Layout.KEY_HIT_MAX.y - Layout.KEY_HIT_MIN.y, Layout.KEY_HIT_MAX.z - Layout.KEY_HIT_MIN.z)).keep() },
                async(io) { r.plateShape = ShapeResource.createBox(Vector3(Layout.PLATE_W, Layout.PLATE_H, 0.01f)).keep() },
                async(io) { r.physics = PhysicsMaterialResource().keep() },
                async(io) {
                    r.gaugeDim = PhysicallyBasedMaterial.create().apply { setBaseColor(Color4(0.10f, 0.07f, 0.03f, 1f)); setMetallic(0.8f); setRoughness(0.5f) }.keep()
                },
                async(io) {
                    r.gaugeLit = PhysicallyBasedMaterial.create().apply {
                        setBaseColor(Color4(0.95f, 0.70f, 0.30f, 1f)); setMetallic(0.3f); setRoughness(0.4f); setEmissiveColor(Color4(1f, 0.62f, 0.18f, 1f))
                    }.keep()
                },
                async(io) {
                    r.gaugeFlash = PhysicallyBasedMaterial.create().apply {
                        setBaseColor(Color4(1f, 0.95f, 0.8f, 1f)); setRoughness(0.3f); setEmissiveColor(Color4(1f, 0.95f, 0.75f, 1f))
                    }.keep()
                },
            ).awaitAll()
            r.noteMats = noteTex.map { unlit(it.await(), transparent = true).keep() }
            r.plateMat = unlit(plateTex.await()).keep()
            r.glowStrong = unlit(glowTex.await(), opacity = 0.95f).keep()
            r.glowSoft = unlit(glowTex.await(), opacity = 0.45f).keep()
            r
        }
    }
}
