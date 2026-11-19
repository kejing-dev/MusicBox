package io.embodit.musicbox.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v0.1.2 classics (17 public-domain tunes): every tune is checked for its pin count, the comb range (8 tines C5..C6),
 * the drum grid (columns 0..63, no duplicate cell, <= 256 pins), its source line, its group, and its opening notes
 * written here as note names, so a wrong solfege -> tine conversion fails the test.
 */
class SongsTest {
    private val presets: List<Song> by lazy { SongCodec.decodeAll(File("src/main/assets/songs.txt").readText(), preset = true) }
    private val raw: List<String> by lazy { File("src/main/assets/songs.txt").readLines().filter { it.isNotBlank() } }

    /** The comb, low to high (tools/make_sounds.py): tine index = position in this list. */
    private val comb = listOf("C5", "D5", "E5", "F5", "G5", "A5", "B5", "C6")

    private fun tine(note: String): Int = comb.indexOf(note).also { require(it >= 0) { "$note is not on the comb" } }

    /** id -> (pins, group, opening as "col:note") */
    private val expected = mapOf(
        "twinkle" to Triple(50, "kids", "0:C5 1:C5 2:G5 3:G5 4:A5 5:A5 6:G5"),
        "mary" to Triple(58, "kids", "0:E5 1:D5 2:C5 3:D5 4:E5 5:E5 6:E5"),
        "lightlyRow" to Triple(51, "kids", "0:G5 1:E5 2:E5 4:F5 5:D5 6:D5"),
        "london" to Triple(54, "kids", "0:G5 1:A5 2:G5 3:F5 4:E5 5:F5 6:G5 8:D5 9:E5 10:F5"),
        "rowboat" to Triple(54, "kids", "0:C5 3:C5 6:C5 8:D5 9:E5 12:C5 24:C6 27:G5"),
        "tigers" to Triple(40, "kids", "0:C5 2:D5 4:E5 6:C5 16:E5 18:F5 20:G5 32:G5 33:A5 34:G5 35:F5 36:E5 38:C5"),
        "macdonald" to Triple(56, "kids", "0:F5 1:F5 2:F5 3:C5 4:D5 5:D5 6:C5 8:A5 9:A5 10:G5 11:G5 12:F5"),
        "jasmine" to Triple(45, "folk", "0:E5 2:E5 3:G5 4:A5 5:C6 6:C6 7:A5 8:G5 10:G5 11:A5 12:G5"),
        "susanna" to Triple(59, "folk", "0:C5 1:D5 2:E5 3:G5 4:G5 5:A5 6:G5 7:E5 8:C5 9:D5"),
        "saints" to Triple(37, "folk", "0:C5 1:E5 2:F5 3:G5 8:C5 9:E5 10:F5 11:G5"),
        "ode" to Triple(66, "classic", "0:E5 1:E5 2:F5 3:G5 4:G5 5:F5 6:E5 7:D5"),
        "canon" to Triple(64, "classic", "0:A5 2:G5 4:F5 6:E5 8:D5 10:C5"),
        "lullaby" to Triple(36, "classic", "0:E5 1:E5 2:G5 5:E5 6:E5 8:G5 11:E5 12:G5 14:C6 16:B5 18:A5 19:A5 20:G5"),
        "morning" to Triple(29, "classic", "0:G5 2:E5 3:D5 5:C5 6:D5 8:E5"),
        "largo" to Triple(50, "classic", "0:E5 1:G5 2:G5 4:E5 5:D5 6:C5 8:D5 9:E5 10:G5 11:E5 12:D5"),
        "jingle" to Triple(51, "holiday", "0:E5 1:E5 2:E5 4:E5 5:E5 6:E5 8:E5 9:G5 10:C5 11:D5 12:E5"),
        "joy" to Triple(44, "holiday", "0:C6 2:B5 3:A5 4:G5 7:F5 8:E5 10:D5 12:C5"),
    )

    @Test fun seventeenTunesInFourGroups() {
        assertEquals(17, presets.size)
        assertEquals(expected.keys, presets.map { it.id }.toSet())
        assertEquals("twinkle", presets.first().id) // the default tune and the fallback after a delete
        val groups = SongGroup.grouped(presets)
        assertEquals(listOf(SongGroup.KIDS, SongGroup.FOLK, SongGroup.CLASSIC, SongGroup.HOLIDAY), groups.map { it.first })
        assertEquals(listOf(7, 3, 5, 2), groups.map { it.second.size })
    }

    @Test fun everyTunePinsRangeAndGrid() {
        for (s in presets) {
            val (n, group, _) = expected.getValue(s.id)
            assertEquals("${s.id} pin count", n, s.pins.size)
            assertEquals("${s.id} group", group, s.group)
            assertTrue("${s.id} <= ${Drum.PIN_CAP} pins", s.pins.size <= Drum.PIN_CAP)
            assertTrue("${s.id} tines on the comb", s.pins.all { it.tine in 0 until Drum.TINES })
            assertTrue("${s.id} columns on the drum", s.pins.all { it.col in 0 until Drum.COLS })
            assertTrue("${s.id} length", s.length in 16..Drum.COLS && s.length % 4 == 0)
            assertTrue("${s.id} bpm ${s.bpm}", s.bpm in 60..140)
            // the file has no duplicate cell (a Set would hide one)
            val cells = raw.first { it.startsWith("${s.id}|") }.split("|")[8].split(";")
            assertEquals("${s.id} duplicate cells", cells.size, cells.toSet().size)
        }
    }

    @Test fun everyTuneHasItsSourceInBothLanguages() {
        for (s in presets) {
            assertTrue("${s.id} zh source", s.licZh.startsWith("公版 · ") && s.licZh.length > 5)
            assertTrue("${s.id} en source", s.licEn.startsWith("Public domain · ") && s.licEn.length > 16)
            assertTrue("${s.id} names / tags", s.nameZh.isNotBlank() && s.nameEn.isNotBlank() && s.tagZh.isNotBlank() && s.tagEn.isNotBlank())
        }
    }

    @Test fun openingsMatchTheNoteNames() {
        for (s in presets) {
            val opening = expected.getValue(s.id).third.split(" ").map { val (c, n) = it.split(":"); Pin(c.toInt(), tine(n)) }
            for (p in opening) assertTrue("${s.id}: ${comb[p.tine]} at column ${p.col}", p in s.pins)
            // the melody note is the highest pin of its column in these openings (harmony sits under it, or is absent)
            for (p in opening) assertTrue("${s.id}: col ${p.col} top", s.pins.filter { it.col == p.col }.maxOf { it.tine } >= p.tine)
        }
    }

    @Test fun codecKeepsTheGroupAndMyTunesHaveNone() {
        val s = presets.first { it.id == "jasmine" }
        assertEquals(s, SongCodec.decodeLine(SongCodec.encodeLine(s), true))
        val mine = Song("m1", false, "我的曲子 1", "My tune 1", pins = setOf(Pin(0, 0)), updatedAt = 5L)
        assertEquals(10, SongCodec.encodeLine(mine).split("|").size) // the my-tunes file format is unchanged
        assertEquals(mine, SongCodec.decodeLine(SongCodec.encodeLine(mine), false))
    }

    @Test fun forkOfAClassicIsMineWithoutAGroup() {
        val b = MusicBox(presets, emptyList(), "jasmine").apply { finishLoading(openLid = true) }
        b.advance(1.0)
        assertTrue(b.tapTine(0))
        val m = b.mine.single()
        assertEquals("", m.group)
        assertEquals("茉莉花 · 改", m.nameZh)
        assertEquals(Drum.MY_CAP, 6)
    }

    @Test fun previewPlaysTheOpeningAtTheTunesTempo() {
        for (s in presets) {
            val plan = PreviewPlan.of(s)
            assertTrue("${s.id} preview not empty", plan.isNotEmpty())
            assertTrue("${s.id} preview <= 8 s", plan.last().first <= PreviewPlan.SECONDS + 1e-9)
            assertEquals("${s.id} starts at once", 0.0, plan.first().first, 1e-9)
            val spc = 60.0 / s.bpm
            for ((at, tines) in plan) {
                val col = Math.round(at / spc).toInt()
                assertEquals("${s.id} col $col", s.pins.filter { it.col == col }.map { it.tine }.sorted(), tines)
            }
        }
        // a short tune previews whole: morning is 36 columns at 132 / min = 16 s, so 8 s = its first 18 columns
        val m = presets.first { it.id == "morning" }
        assertTrue(PreviewPlan.of(m).all { it.first < 8.0 + 1e-9 })
    }
}
