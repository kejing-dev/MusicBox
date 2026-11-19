package io.embodit.musicbox.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v0.1.3 classics (26 public-domain tunes): every tune is checked for its pin count, the comb range (18 tines G4..C6),
 * the drum grid (columns 0..63, no duplicate cell, <= 256 pins), its source line, its group, and its opening notes
 * written here as note names, so a wrong solfege -> tine conversion fails the test.
 */
class SongsTest {
    private val presets: List<Song> by lazy { SongCodec.decodeAll(File("src/main/assets/songs.txt").readText(), preset = true) }
    private val raw: List<String> by lazy { File("src/main/assets/songs.txt").readLines().filter { it.isNotBlank() } }

    /** The comb, low to high (tools/make_sounds.py): G4 .. C6, every semitone. */
    private fun tine(note: String): Int = Comb.of(note).also { require(it >= 0) { "$note is not on the comb" } }

    /** id -> (pins, group, opening as "col:note") */
    private val expected = mapOf(
        "twinkle" to Triple(50, "kids", "0:C5 1:C5 2:G5 3:G5 4:A5 5:A5 6:G5"),
        "mary" to Triple(58, "kids", "0:E5 1:D5 2:C5 3:D5 4:E5 5:E5 6:E5"),
        "lightlyRow" to Triple(51, "kids", "0:G5 1:E5 2:E5 4:F5 5:D5 6:D5"),
        "london" to Triple(54, "kids", "0:G5 1:A5 2:G5 3:F5 4:E5 5:F5 6:G5 8:D5 9:E5 10:F5"),
        "rowboat" to Triple(54, "kids", "0:C5 3:C5 6:C5 8:D5 9:E5 12:C5 24:C6 27:G5"),
        "tigers" to Triple(40, "kids", "0:C5 2:D5 4:E5 6:C5 16:E5 18:F5 20:G5 32:G5 33:A5 34:G5 35:F5 36:E5 38:C5 48:C5 50:G4 52:C5"),
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
        // v0.1.3: back once the comb has sharps and G4..B4
        "greensleeves" to Triple(46, "folk", "0:D5 1:F5 3:G5 4:A5 5:A#5 6:A5 7:G5 9:E5 17:C#5"),
        "auldLangSyne" to Triple(36, "folk", "0:G4 2:C5 5:C5 6:C5 8:E5 10:D5 13:C5 14:D5 16:E5"),
        "scarborough" to Triple(40, "folk", "0:A4 2:A4 3:E5 5:E5 6:B4 7:C5 8:B4 9:A4 12:E5 14:G5 15:A5 19:F#5"),
        "farewell" to Triple(37, "folk", "0:G5 2:E5 3:G5 4:C6 8:A5 10:C6 12:G5"),
        "furElise" to Triple(37, "classic", "0:B5 1:A#5 2:B5 3:A#5 4:B5 5:F#5 6:A5 7:G5 8:E5 11:G4 12:B4 13:E5"),
        "turkish" to Triple(29, "classic", "0:B4 1:A4 2:G#4 3:A4 4:C5 8:D5 9:C5 10:B4 11:C5 12:E5 16:F5 17:E5 18:D#5 19:E5"),
        "birthday" to Triple(30, "holiday", "0:G4 1:G4 2:A4 4:G4 6:C5 8:B4 12:G4 13:G4 14:A4 16:G4 18:D5 20:C5"),
        "newYear" to Triple(35, "holiday", "0:C5 1:C5 2:C5 4:G4 6:E5 7:E5 8:E5 10:C5"),
        "silentNight" to Triple(46, "holiday", "0:D5 1:E5 2:D5 3:B4 12:A5 14:A5 15:F#5"),
    )

    @Test fun twentySixTunesInFourGroups() {
        assertEquals(26, presets.size)
        assertEquals(expected.keys, presets.map { it.id }.toSet())
        assertEquals("twinkle", presets.first().id) // the default tune and the fallback after a delete
        val groups = SongGroup.grouped(presets)
        assertEquals(listOf(SongGroup.KIDS, SongGroup.FOLK, SongGroup.CLASSIC, SongGroup.HOLIDAY), groups.map { it.first })
        assertEquals(listOf(7, 7, 7, 5), groups.map { it.second.size })
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
            assertTrue("${s.id} bpm ${s.bpm}", s.bpm in 60..240)
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
            for (p in opening) assertTrue("${s.id}: ${Comb.name(p.tine)} at column ${p.col}", p in s.pins)
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
        assertTrue(b.tapTine(5))
        val m = b.mine.single()
        assertEquals("", m.group)
        assertEquals("茉莉花 · 改", m.nameZh)
        assertEquals(Drum.MY_CAP, 6)
    }

    @Test fun theNewTunesUseTheWiderComb() {
        val byId = presets.associateBy { it.id }
        // the tunes left out in v0.1.2 now need what the 8-tine comb did not have: sharps or the low fourth
        for (id in listOf("greensleeves", "furElise", "turkish", "scarborough", "silentNight")) assertTrue("$id has a sharp", byId.getValue(id).pins.any { Comb.sharp(it.tine) })
        for (id in listOf("birthday", "auldLangSyne", "newYear", "furElise", "silentNight", "tigers")) assertTrue("$id goes under C5", byId.getValue(id).pins.any { it.tine < 5 })
        // the 17 v0.1.2 tunes stay on the old naturals C5..C6 (no transposition), except Frere Jacques's low sol
        val old = listOf("twinkle", "mary", "lightlyRow", "london", "rowboat", "macdonald", "jasmine", "susanna", "saints", "ode", "canon", "lullaby", "morning", "largo", "jingle", "joy")
        for (id in old) assertTrue("$id on C5..C6 naturals", byId.getValue(id).pins.all { it.tine in Drum.FROM_V1.toList() })
        assertEquals(setOf(50, 58), byId.getValue("tigers").pins.filter { it.tine == 0 }.map { it.col }.toSet())
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
