package io.embodit.musicbox.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** D35 occlusion (design occlusionCheck), D17 target size, D14 / D16 view angles, D25 dancer calibration. */
class LayoutTest {
    private val eyes = Layout.eyesContent()

    /** The drum is a cylinder: its AABB corners are empty space, so the drum gets an exact test. */
    private fun hitsDrum(a: V3, b: V3): Boolean {
        val n = 400
        for (i in 1 until n) {
            val p = a.lerp(b, i / n.toFloat())
            if (p.x < Layout.DRUM.x - Layout.DRUM_L / 2 || p.x > Layout.DRUM.x + Layout.DRUM_L / 2) continue
            if (hypot((p.y - Layout.DRUM.y).toDouble(), (p.z - Layout.DRUM.z).toDouble()) < Layout.DRUM_R - 0.0005) return true
        }
        return false
    }

    private fun blocked(eye: V3, p: V3, solids: List<Aabb>): String? =
        solids.filter { it.name != "drum" }.firstOrNull { it.hitBy(eye, p) }?.name ?: if (solids.any { it.name == "drum" } && hitsDrum(eye, p)) "drum" else null

    @Test fun songCardSeenFromEveryEye() {
        for (up in listOf(true, false)) {
            val solids = Layout.solids(up)
            for ((n, e) in eyes) for (c in Layout.cardCorners()) {
                assertEquals("card corner $c from $n (dancer up=$up)", null, blocked(e, c, solids))
                // the floating notes stay under the card's lower edge (y <= +0.05 at z +0.02)
                val note = V3(c.x, 0.05f, 0.02f)
                assertTrue(Layout.cardCorners().minOf { it.y } > 0.05f)
                assertTrue(note.y < Layout.cardCorners().minOf { it.y })
            }
        }
    }

    @Test fun tinesAndLabelsSeenFromEveryEye() {
        val solids = Layout.solids(true).filter { it.name != "combBase" }
        for ((n, e) in eyes) for (r in 0 until Drum.TINES) {
            val x = Layout.tineX(r)
            for (z in listOf(Layout.tineRootZ(r) - 0.002f, (Layout.tineRootZ(r) + Layout.TINE_TIP_Z) / 2, Layout.TINE_TIP_Z + 0.001f)) {
                assertEquals("tine $r z=$z from $n", null, blocked(e, V3(x, Layout.TINE_Y + 0.0005f, z), solids))
            }
            // the label strip (colour dot + number) under each tine
            assertEquals("label $r from $n", null, blocked(e, V3(x, Layout.LABELS.y + 0.002f, Layout.LABELS.z + 0.002f), Layout.solids(true)))
            // QA #4 q4-2: the low-octave dot row (and the high dot, the digits, the bottom edge) is seen, not under the wall
            for (v in listOf(Layout.LABEL_HIGH_DOT_V, 0.5f, Layout.LABEL_LOW_DOT_V, 0.97f)) {
                assertEquals("label $r v=$v from $n", null, blocked(e, Layout.labelPoint(x, v), Layout.solids(true)))
            }
        }
    }

    @Test fun labelStripAboveTheFrontWall() {
        // every texture row above the lowered wall's top (WIN_TOP) and in front of the tines' click boxes
        for (v in listOf(0f, Layout.LABEL_LOW_DOT_V, 1f)) {
            val p = Layout.labelPoint(Layout.LABELS.x, v, out = 0f)
            assertTrue("label v=$v y=${p.y} above wall ${Layout.WIN_TOP}", p.y > Layout.WIN_TOP + 0.0005f)
            assertTrue("label v=$v z=${p.z} in front of the tines", p.z > Layout.TINE_HIT_Z1)
            assertTrue("label v=$v z=${p.z} inside the box front", p.z < Layout.BZ1)
        }
    }

    @Test fun pinsNearThePluckLineSeen() {
        val solids = Layout.solids(true).filter { it.name != "drum" }
        for ((n, e) in eyes) for (c in 0..3) for (r in listOf(0, 8, Drum.TINES - 1)) {
            val p = Layout.pinHead(c, r, 0.0)
            assertEquals("pin $c/$r from $n", null, blocked(e, p, solids))
        }
    }

    @Test fun gaugeAndPlateSeen() {
        val solids = Layout.solids(true)
        for ((n, e) in eyes) {
            for (i in 0 until 8) assertEquals("gauge $i from $n", null, blocked(e, V3(Layout.gaugeX(i), Layout.PLATE.y, Layout.PLATE.z + 0.001f), solids))
            assertEquals("plate from $n", null, blocked(e, V3(Layout.PLATE.x, Layout.PLATE.y, Layout.PLATE.z + 0.001f), solids))
        }
    }

    @Test fun targetsBigEnoughAtArmsLength() {
        // design distance 0.6 m (nearHint pulls the box to arm's length); profile D17: >= 2° and >= 4 cm, gaps >= 1.5 cm or 0.3 x size
        assertTrue(Layout.angleDeg(Layout.TINE_HIT_Z1 - Layout.TINE_HIT_Z0, 0.6f) >= 3f)
        assertTrue(Layout.angleDeg(Layout.PLATE_W, 0.6f) >= 3f)
        assertTrue(Layout.angleDeg(Layout.KEY_HIT_MAX.y - Layout.KEY_HIT_MIN.y, 0.6f) >= 3f)
        // v0.1.3 (18 tines at real 11.1 mm pitch, user "Widen the comb"): a 3D tine is now 1.3 cm wide (~1.2° at 0.6 m), under
        // D17, so the >= 48 dp 2D keyboard is the way to pin a note (deviation DV-comb18); the 3D tines still take taps and
        // their boxes never overlap each other or the drum capsule
        val tine = Layout.TINE_HIT_W * Layout.K
        assertTrue("tine target ${tine * 100} cm", tine >= 0.0125f)
        for (r in 0 until Drum.TINES - 1) assertTrue(Layout.tineX(r + 1) - Layout.tineX(r) > Layout.TINE_HIT_W)
        val drumFront = Layout.DRUM.z + kotlin.math.sqrt(Layout.DRUM_HIT_R * Layout.DRUM_HIT_R - (Layout.TINE_HIT_Y0 - Layout.DRUM.y).let { it * it })
        assertTrue("drum front $drumFront", drumFront < Layout.TINE_HIT_Z0)
        // the comb spans the drum: first and last tine inside the comb base / drum length
        assertTrue(Layout.tineX(0) - Layout.TINE_PITCH / 2 >= Layout.DRUM.x - Layout.DRUM_L / 2 - 0.001f)
        assertTrue(Layout.tineX(Drum.TINES - 1) + Layout.TINE_PITCH / 2 <= Layout.DRUM.x + Layout.DRUM_L / 2 + 0.001f)
        // graded comb: every tine longer than the one above it, tips on one line
        for (r in 0 until Drum.TINES - 1) assertTrue(Layout.tineLen(r) > Layout.tineLen(r + 1))
    }

    /**
     * The 2D pin keyboard (ui/control/Keyboard.kt) in the 640 dp window (24 dp padding each side): 11 white keys and 7 black
     * keys, every key >= 48 dp wide and tall (B19), black keys never touch each other, each sits over the gap after its natural.
     */
    @Test fun keyboardKeysAre48dp() {
        val content = 640f - 2 * 24f; val gap = 4f; val n = Comb.whites.size
        val w = (content - gap * (n - 1)) / n
        assertEquals(11, n); assertEquals(7, Comb.blacks.size)
        assertTrue("white $w dp", w >= 48f)
        val bw = 48f
        val xs = Comb.blacks.map { r -> (Comb.whites.indexOf(r - 1) + 1) * (w + gap) - gap / 2 - bw / 2 }
        for (i in 0 until xs.size - 1) assertTrue("black keys ${xs[i]} / ${xs[i + 1]}", xs[i + 1] - xs[i] >= bw + 2f)
        assertTrue(xs.first() >= 0f && xs.last() + bw <= content)
        for (r in Comb.blacks) assertTrue(Comb.whites.indexOf(r - 1) >= 0 && Comb.whites.indexOf(r + 1) >= 0)
    }

    @Test fun tiltRoundTrips() {
        for (p in Layout.contentPoints()) {
            val q = Layout.toContent(Layout.toVolume(p))
            assertTrue("$p -> $q", (q - p).length < 1e-5f)
        }
    }

    /** QA #1 known issue + D54: from the default 1.75 m eye-height spot the comb is seen from >= 12° above its plane, box below the eye line. */
    @Test fun combFacesTheUserAndTheBoxSitsBelowTheEyes() {
        val eye = Layout.eyesVolume()["emulatorDefault"]!!
        val mid = Layout.toVolume(V3(Layout.tineX(4), Layout.TINE_Y, (Layout.TINE_ROOT_Z + Layout.TINE_TIP_Z) / 2))
        // the comb plane's normal (content +Y) in the volume frame
        val n = Layout.toVolume(V3(0f, 1f, 0f), lift = 0f)
        val d = eye - mid
        val elev = Math.toDegrees(kotlin.math.asin(((d.x * n.x + d.y * n.y + d.z * n.z) / d.length).toDouble()))
        val before = run {
            val m0 = V3(Layout.tineX(4), Layout.TINE_Y, (Layout.TINE_ROOT_Z + Layout.TINE_TIP_Z) / 2); val d0 = eye - m0
            Math.toDegrees(kotlin.math.asin((d0.y / d0.length).toDouble()))
        }
        assertTrue("comb seen from $elev° above its plane (was $before°)", elev >= before + 11 && elev >= 12)
        // D54: the whole box top (closed) is below the eye line from every design eye and the default spot
        for ((name, e) in Layout.eyesVolume()) for (x in listOf(Layout.BX0, Layout.BX1)) for (z in listOf(Layout.BZ0, Layout.BZ1)) {
            val top = Layout.toVolume(V3(x, Layout.BY1, z))
            assertTrue("box top $top above eye $name", top.y < e.y)
        }
    }

    /** D25: dancer.glb (tools/calibrate_dancer.py) toe at the origin, 0.095 m tall, fits the well, OPAQUE. */
    @Test fun dancerCalibrated() {
        val b = File("src/main/assets/models/dancer.glb").readBytes()
        val len = java.nio.ByteBuffer.wrap(b, 12, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
        val json = String(b, 20, len)
        val min = Regex("\"min\":\\[([^\\]]+)]").findAll(json).map { m -> m.groupValues[1].split(",").map { it.toFloat() } }.first { it.size == 3 }
        val max = Regex("\"max\":\\[([^\\]]+)]").findAll(json).map { m -> m.groupValues[1].split(",").map { it.toFloat() } }.first { it.size == 3 }
        assertEquals(0f, min[1], 0.001f)
        assertEquals(Layout.DANCER_H, max[1] - min[1], Layout.DANCER_H * 0.1f)
        assertTrue("x ${min[0]}..${max[0]}", maxOf(abs(min[0]), abs(max[0])) < Layout.WELL_R)
        assertTrue("z ${min[2]}..${max[2]}", maxOf(abs(min[2]), abs(max[2])) < Layout.WELL_R)
        assertTrue(json.contains("\"alphaMode\":\"OPAQUE\"") || !json.contains("alphaMode"))
        assertTrue(!json.contains("\"BLEND\""))
    }

    /** Every model except the glass is OPAQUE (D24). */
    @Test fun modelsOpaque() {
        for (f in File("src/main/assets/models").listFiles()!!.filter { it.name.endsWith(".glb") && it.name != "glass.glb" }) {
            val b = f.readBytes()
            val len = java.nio.ByteBuffer.wrap(b, 12, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
            assertTrue(f.name, !String(b, 20, len).contains("BLEND"))
        }
    }
}
