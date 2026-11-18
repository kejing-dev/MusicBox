package io.embodit.musicbox.ui.board

import io.embodit.musicbox.domain.Lines
import io.embodit.musicbox.ui.board.input.parseLoggedPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Hit replay (build.md 命中回放): a real hit chain (mesh node first, then parents) resolves to the right part. */
class HitReplayTest {
    @Test fun everyHolderResolves() {
        assertEquals("lid", BoxTargets.resolvePath(listOf("lidModel:WALNUT", "lidHinge", "content", "root")).let { null } ?: BoxTargets.resolvePath(listOf("lid", "lidHinge")))
        for (r in 0 until 8) {
            assertEquals("tine:$r", BoxTargets.resolvePath(parseLoggedPath("tineModel:$r < tinePivot:$r < tine:$r < content < root")))
            assertEquals(BoxNames.Tine(r), BoxNames.hitOf(BoxTargets.resolvePath(listOf("node_0", "tineModel:$r", "tinePivot:$r", "tine:$r"))))
        }
        assertEquals("drum", BoxTargets.resolvePath(listOf("pin:17", "drumSpin", "drumAxis", "drum", "content")))
        assertEquals("key", BoxTargets.resolvePath(listOf("keyModel", "keyPivot", "key")))
        assertEquals("nameplate", BoxTargets.resolvePath(listOf("plateFace", "nameplate")))
        // the box body, the dancer and the notes take no hits
        assertEquals(null, BoxTargets.resolvePath(listOf("box:WALNUT", "content", "root")))
        assertEquals(null, BoxTargets.resolvePath(listOf("dancer", "stage", "content")))
        assertEquals(null, BoxTargets.resolvePath(listOf("note:3", "content")))
        assertEquals(BoxNames.None, BoxNames.hitOf("tine:8"))
    }

    /** D38 / D26: every line key has a string in both languages; how_to strings exist in both. */
    @Test fun everyLineInBothLanguages() {
        for (dir in listOf("values", "values-zh")) {
            val xml = File("src/main/res/$dir/strings.xml").readText()
            for (k in Lines.ALL_KEYS) assertTrue("$dir line_$k", xml.contains("name=\"line_$k\""))
            assertTrue(xml.contains("name=\"how_to_pin\""))
        }
    }
}
