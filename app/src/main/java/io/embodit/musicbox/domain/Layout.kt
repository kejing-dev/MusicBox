package io.embodit.musicbox.domain

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class V3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: V3) = V3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: V3) = V3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Float) = V3(x * k, y * k, z * k)
    fun lerp(o: V3, u: Float) = V3(x + (o.x - x) * u, y + (o.y - y) * u, z + (o.z - z) * u)
    val length: Float get() = sqrt(x * x + y * y + z * z)
}

/** An axis-aligned box (design metres, k = 1, volume centre = origin). */
data class Aabb(val name: String, val min: V3, val max: V3) {
    fun corners(): List<V3> = listOf(
        V3(min.x, min.y, min.z), V3(max.x, min.y, min.z), V3(min.x, max.y, min.z), V3(max.x, max.y, min.z),
        V3(min.x, min.y, max.z), V3(max.x, min.y, max.z), V3(min.x, max.y, max.z), V3(max.x, max.y, max.z),
    )

    /** Does the open segment a -> b pass through the box interior (slab test, 0.5 mm tolerance at the ends)? */
    fun hitBy(a: V3, b: V3, eps: Float = 0.0005f): Boolean {
        var t0 = 0f; var t1 = 1f
        val d = b - a
        val lo = floatArrayOf(min.x + eps, min.y + eps, min.z + eps)
        val hi = floatArrayOf(max.x - eps, max.y - eps, max.z - eps)
        val p = floatArrayOf(a.x, a.y, a.z)
        val v = floatArrayOf(d.x, d.y, d.z)
        for (i in 0..2) {
            if (kotlin.math.abs(v[i]) < 1e-9f) { if (p[i] <= lo[i] || p[i] >= hi[i]) return false; continue }
            var ta = (lo[i] - p[i]) / v[i]; var tb = (hi[i] - p[i]) / v[i]
            if (ta > tb) { val s = ta; ta = tb; tb = s }
            t0 = max(t0, ta); t1 = min(t1, tb)
            if (t0 >= t1) return false
        }
        return t1 > 0.001f && t0 < 0.999f
    }
}

/**
 * Every number of the volume (design spatialLayout, metres at k = 1; tools/build_musicbox.py uses the same). The app
 * scales the content root by [VolumeScale] (1.28 at the manifest size).
 */
object Layout {
    const val K = 1.28f
    // volume (k = 1) and the manifest size (k = 1.28)
    const val VOL_W = 0.50f; const val VOL_H = 0.40f; const val VOL_D = 0.37f

    // box
    const val BX0 = -0.21f; const val BX1 = 0.17f; const val BY0 = -0.20f; const val BY1 = -0.07f; const val BZ0 = -0.11f; const val BZ1 = 0.11f
    const val WIN_TOP = -0.11f
    val HINGE = V3(0f, BY1, BZ0)
    const val LID_L = 0.22f
    const val LID_T = 0.015f
    const val LID_OPEN_DEG = 100f

    // dancer well / stage
    val WELL = V3(-0.14f, 0f, 0f)
    const val WELL_R = 0.046f
    const val STAGE_DOWN_Y = -0.185f
    const val STAGE_UP_Y = -0.065f
    const val DANCER_H = 0.095f
    const val DANCER_R = 0.0418f

    // drum + pins
    val DRUM = V3(0.055f, -0.125f, -0.020f)
    const val DRUM_R = 0.045f
    const val DRUM_L = 0.20f
    const val PIN_TOP_R = 0.048f
    /** The pluck line: angle above +Z toward +Y of the tine tips seen from the drum axis (~40.2°). */
    val PLUCK_DEG: Float = Math.toDegrees(atan2(0.030, 0.0355)).toFloat()

    // comb
    const val TINE_Y = -0.095f
    const val TINE_ROOT_Z = 0.055f
    const val TINE_TIP_Z = 0.0155f
    const val TINE_PITCH = 0.025f
    fun tineX(r: Int): Float = 0.055f + (r - 3.5f) * TINE_PITCH
    /** Tine click boxes (design R3-02: full pitch minus a 1 mm gap). */
    const val TINE_HIT_W = 0.024f
    const val TINE_HIT_Y0 = -0.11f; const val TINE_HIT_Y1 = -0.08f
    const val TINE_HIT_Z0 = 0.026f; const val TINE_HIT_Z1 = 0.09f
    /** The drum's click capsule radius (pins included). */
    const val DRUM_HIT_R = 0.048f

    // key
    val KEY_ROOT = V3(0.17f, -0.135f, 0.06f)
    val KEY_HIT_MIN = V3(0.17f, -0.17f, 0.03f)
    val KEY_HIT_MAX = V3(0.22f, -0.10f, 0.09f)

    // front: nameplate, spring gauge, comb labels
    val PLATE = V3(-0.04f, -0.135f, 0.1105f)
    const val PLATE_W = 0.08f; const val PLATE_H = 0.025f
    const val GAUGE_X0 = 0.08f; const val GAUGE_X1 = 0.16f
    const val GAUGE_W = 0.008f; const val GAUGE_H = 0.012f
    fun gaugeX(i: Int): Float = GAUGE_X0 + (GAUGE_X1 - GAUGE_X0) * (i + 0.5f) / 8f
    /** Comb label strip on the lowered front wall's sill, tilted 45° toward the user. */
    val LABELS = V3(0.055f, -0.1085f, 0.104f)
    const val LABELS_W = 0.20f; const val LABELS_H = 0.010f

    // song card on the lid's inner face: 0.30 x 0.07, 0.132..0.203 m from the hinge, x -0.17..+0.13
    const val CARD_W = 0.30f; const val CARD_H = 0.07f
    const val CARD_X = -0.02f
    const val CARD_FROM_HINGE = 0.1675f
    const val CARD_PX_W = 2048; const val CARD_PX_H = 480

    /** A point on the open lid's inner face [along] metres from the hinge (x given). */
    fun lidPoint(x: Float, along: Float, openDeg: Float = LID_OPEN_DEG, out: Float = -0.001f): V3 {
        val a = Math.toRadians(-openDeg.toDouble())
        // lid local (x, y, z) -> rotate about X by a -> hinge
        val y = out.toDouble(); val z = along.toDouble()
        val yy = y * cos(a) - z * sin(a); val zz = y * sin(a) + z * cos(a)
        return V3(x, HINGE.y + yy.toFloat(), HINGE.z + zz.toFloat())
    }

    fun cardCorners(): List<V3> {
        val a0 = CARD_FROM_HINGE - CARD_H / 2; val a1 = CARD_FROM_HINGE + CARD_H / 2
        val x0 = CARD_X - CARD_W / 2; val x1 = CARD_X + CARD_W / 2
        return listOf(lidPoint(x0, a0), lidPoint(x1, a0), lidPoint(x0, a1), lidPoint(x1, a1))
    }

    /** Pin [col] head position (top) when the drum is at visual position [pos]. */
    fun pinHead(col: Int, tine: Int, pos: Double): V3 {
        val deg = PLUCK_DEG + (col - pos) * MusicBox.DEG_PER_COL
        val a = Math.toRadians(deg)
        return V3(tineX(tine), DRUM.y + (PIN_TOP_R * sin(a)).toFloat(), DRUM.z + (PIN_TOP_R * cos(a)).toFloat())
    }

    /** The parts a sight line must not cross (design occlusionCheck). Dancer box at [dancerUp]. */
    fun solids(dancerUp: Boolean): List<Aabb> {
        val stage = if (dancerUp) STAGE_UP_Y else STAGE_DOWN_Y
        return listOf(
            Aabb("frontWallLow", V3(-0.07f, BY0, BZ1 - 0.012f), V3(0.158f, WIN_TOP, BZ1)),
            Aabb("frontWallDancer", V3(BX0, BY0, BZ1 - 0.012f), V3(-0.07f, BY1, BZ1)),
            Aabb("frontWallRight", V3(0.158f, BY0, BZ1 - 0.012f), V3(BX1, BY1, BZ1)),
            Aabb("leftWall", V3(BX0, BY0, BZ0), V3(BX0 + 0.012f, BY1, BZ1)),
            Aabb("rightWall", V3(BX1 - 0.012f, BY0, BZ0), V3(BX1, BY1, BZ1)),
            Aabb("divider", V3(-0.08f, BY0, BZ0), V3(-0.07f, BY1, BZ1)),
            Aabb("combBase", V3(-0.045f, -0.176f, 0.058f), V3(0.155f, TINE_Y, 0.09f)),
            Aabb("drum", V3(DRUM.x - DRUM_L / 2, DRUM.y - DRUM_R, DRUM.z - DRUM_R), V3(DRUM.x + DRUM_L / 2, DRUM.y + DRUM_R, DRUM.z + DRUM_R)),
            Aabb("dancer", V3(WELL.x - DANCER_R, stage, WELL.z - DANCER_R), V3(WELL.x + DANCER_R, stage + DANCER_H, WELL.z + DANCER_R)),
            Aabb("key", KEY_HIT_MIN, KEY_HIT_MAX),
        )
    }

    /**
     * Design eye positions relative to the volume centre, in physical metres (design occlusionCheck.eyePositions) plus the
     * emulator's default placement (the volume ~1.75 m ahead at eye height, native-window-move §4), all converted to k = 1
     * content metres by dividing by K.
     */
    val EYES_PHYSICAL: Map<String, V3> = mapOf(
        "seated" to V3(0f, 0.30f, 0.80f), "standing" to V3(0f, 0.55f, 0.80f),
        "seatedL30" to V3(-0.40f, 0.30f, 0.69f), "seatedR30" to V3(0.40f, 0.30f, 0.69f),
        "standingL30" to V3(-0.40f, 0.55f, 0.69f), "standingR30" to V3(0.40f, 0.55f, 0.69f),
        "emulatorDefault" to V3(0f, 0.10f, 1.75f),
    )

    fun eyesContent(k: Float = K): Map<String, V3> = EYES_PHYSICAL.mapValues { it.value * (1f / k) }

    /** Angular size (degrees) of a target [sizeM] (content metres at k = 1) seen from [distM] physical metres at scale [k]. */
    fun angleDeg(sizeM: Float, distM: Float, k: Float = K): Float = Math.toDegrees(2 * kotlin.math.atan((sizeM * k / 2 / distM).toDouble())).toFloat()

    /** 8 corners of the content (box + open lid + key) for the +-39° projection check. */
    fun contentBounds(): Aabb = Aabb("content", V3(BX0, BY0, -0.163f), V3(0.225f, 0.147f, BZ1))

    @Suppress("unused") val TAU = (2 * PI).toFloat()
}
