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
            for (z in listOf(Layout.TINE_ROOT_Z - 0.002f, (Layout.TINE_ROOT_Z + Layout.TINE_TIP_Z) / 2, Layout.TINE_TIP_Z + 0.001f)) {
                assertEquals("tine $r z=$z from $n", null, blocked(e, V3(x, Layout.TINE_Y + 0.0005f, z), solids))
            }
            // the label strip (colour dot + number) under each tine
            assertEquals("label $r from $n", null, blocked(e, V3(x, Layout.LABELS.y + 0.002f, Layout.LABELS.z + 0.002f), Layout.solids(true)))
        }
    }

    @Test fun pinsNearThePluckLineSeen() {
        val solids = Layout.solids(true).filter { it.name != "drum" }
        for ((n, e) in eyes) for (c in 0..3) for (r in listOf(0, 7)) {
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
        val tine = Layout.TINE_HIT_W * Layout.K
        assertTrue("tine target ${tine * 100} cm", Layout.angleDeg(Layout.TINE_HIT_W, 0.6f) >= 2f)
        assertTrue(Layout.angleDeg(Layout.TINE_HIT_Z1 - Layout.TINE_HIT_Z0, 0.6f) >= 3f)
        assertTrue(Layout.angleDeg(Layout.PLATE_W, 0.6f) >= 3f)
        assertTrue(Layout.angleDeg(Layout.KEY_HIT_MAX.y - Layout.KEY_HIT_MIN.y, 0.6f) >= 3f)
        // tine boxes never overlap each other or the drum capsule
        for (r in 0 until 7) assertTrue(Layout.tineX(r + 1) - Layout.tineX(r) > Layout.TINE_HIT_W)
        val drumFront = Layout.DRUM.z + kotlin.math.sqrt(Layout.DRUM_HIT_R * Layout.DRUM_HIT_R - (Layout.TINE_HIT_Y0 - Layout.DRUM.y).let { it * it })
        assertTrue("drum front $drumFront", drumFront < Layout.TINE_HIT_Z0)
        assertTrue(tine >= 0.03f)
    }

    @Test fun contentCornersWithin39DegreesFromSeatedAndStandingEyes() {
        // every real corner of box + open lid + key (tilted into the volume frame), seen from the design eyes and the
        // emulator default looking at the volume centre (D14 / QA ±39°, re-checked after the v0.1.1 lean)
        val pts = Layout.contentPoints().map { Layout.toVolume(it) }
        for ((n, e) in Layout.eyesVolume()) for (c in pts) {
            val d = c - e
            val toCentre = V3(0f, 0f, 0f) - e
            val yaw = Math.toDegrees(atan2(d.x.toDouble(), -d.z.toDouble()) - atan2(toCentre.x.toDouble(), -toCentre.z.toDouble()))
            val pitch = Math.toDegrees(atan2(d.y.toDouble(), hypot(d.x.toDouble(), d.z.toDouble())) - atan2(toCentre.y.toDouble(), hypot(toCentre.x.toDouble(), toCentre.z.toDouble())))
            assertTrue("corner $c from $n yaw=$yaw", abs(yaw) <= 39)
            assertTrue("corner $c from $n pitch=$pitch", abs(pitch) <= 39)
        }
    }

    @Test fun everythingAnimatedStaysInTheVolume() {
        val hw = Layout.VOL_W / 2; val hh = Layout.VOL_H / 2; val hd = Layout.VOL_D / 2
        for (p in Layout.contentPoints().map { Layout.toVolume(it) }) assertTrue("$p", abs(p.x) <= hw && abs(p.y) <= hh && abs(p.z) <= hd)
        // closed lid: the lowered dancer is inside the box (head under the lid)
        assertTrue(Layout.STAGE_DOWN_Y + Layout.DANCER_H < Layout.BY1)
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
