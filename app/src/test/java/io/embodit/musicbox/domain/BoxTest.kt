package io.embodit.musicbox.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Design verification.unitTests: pin entry, spring, playback, songs, presets fork, undo, swap, low frame rate. */
class BoxTest {
    companion object {
        val PRESETS: List<Song> by lazy { SongCodec.decodeAll(File("src/main/assets/songs.txt").readText(), preset = true) }
        val C = intArrayOf(0, 0, 4, 4, 5, 5, 4) // 1-1-5-5-6-6-5 as tines 0..7
    }

    private fun box(mine: List<Song> = emptyList(), song: String? = null) = MusicBox(PRESETS, mine, song).apply { finishLoading(openLid = true) }

    /** An open box on a fresh tune of mine. */
    private fun mineBox(): MusicBox = box().also { it.newSong() ; it.advance(1.0); it.sfx.clear() }

    private fun plucks(b: MusicBox): List<Pair<Double, Int>> = b.sfx.filter { it.kind == Sfx.Kind.TINE && !it.soft }.map { it.at to it.tine }

    // ------------------------------------------------------------------ songs

    @Test fun songsValid() {
        assertEquals(6, PRESETS.size)
        for (s in PRESETS) {
            assertTrue(s.id, s.pins.isNotEmpty() && s.pins.size <= Drum.PIN_CAP)
            assertTrue(s.id, s.pins.all { it.col in 0..63 && it.tine in 0..7 })
            assertTrue(s.id, s.length in 16..64 && s.length % 4 == 0)
            assertTrue(s.id, s.nameZh.isNotBlank() && s.nameEn.isNotBlank() && s.bpm in 60..140)
        }
        assertEquals(48, PRESETS.first { it.id == "twinkle" }.length)
        // twinkle opens 1 1 5 5 6 6 5
        val tw = PRESETS.first { it.id == "twinkle" }
        for ((c, t) in C.withIndex()) assertTrue("twinkle col $c", Pin(c, t) in tw.pins)
    }

    @Test fun codecRoundTrip() {
        val s = PRESETS[2].copy(preset = false, updatedAt = 123L)
        assertEquals(s, SongCodec.decodeLine(SongCodec.encodeLine(s), false))
    }

    // ------------------------------------------------------------------ pin entry (M1, M2, T6)

    @Test fun pinSevenThenWindAndPlayFromColumnZero() {
        val b = mineBox()
        for (t in C) { assertTrue(b.tapTine(t)); b.advance(0.2) }
        assertEquals(7, b.pins.size)
        assertEquals(7, b.logicalCol())
        assertEquals(8, b.length)
        b.sfx.clear()
        b.windAndPlay()
        b.advance(1.0)
        val p = plucks(b)
        // first pluck (column 0, tine 0) within 1 s, then 0 4 4 5 5 4 in order
        assertTrue("first pluck ${p.firstOrNull()}", p.isNotEmpty() && p.first().first - (b.t - 1.0) < 1.0)
        b.advance(10.0)
        val all = plucks(b).map { it.second }
        assertEquals(C.toList(), all.take(7))
        assertEquals(0.0, b.spring, 1e-9)
        assertEquals(8, b.logicalCol())            // stopped at the end of the tune (L = 8)
        assertFalse(b.running)
        assertTrue(b.sfx.any { it.kind == Sfx.Kind.STOP })
    }

    @Test fun keyTapAtColumnSevenWrapsAndSingsTheSeven() {
        val b = mineBox()
        for (t in C) { b.tapTine(t); b.advance(0.2) }
        b.sfx.clear()
        b.tapKey()                                   // +16 steps, starts at column 7
        val t0 = b.t
        b.advance(0.6 + 0.35)                       // col 7 -> 8 (= L) at 100 bpm 0.6 s, fast-forward <= 0.3 s
        val firstZero = plucks(b).firstOrNull { it.second == 0 }
        assertNotNull(firstZero)
        assertTrue("wrap took ${firstZero!!.first - t0}", firstZero.first - t0 <= 0.6 + 0.3 + 0.01)
        b.advance(5.0)
        assertEquals(C.toList(), plucks(b).map { it.second }.take(7))
    }

    @Test fun chordAndPull() {
        val b = mineBox()
        b.tapTine(0); b.advance(0.2)                 // col 0, now at 1
        b.back(); b.advance(0.2)                     // back to 0
        b.tapTine(2); b.advance(0.2)                 // chord at col 0
        assertTrue(Pin(0, 0) in b.pins && Pin(0, 2) in b.pins)
        assertEquals(1, b.logicalCol())
        b.back(); b.advance(0.2)
        b.tapTine(2); b.advance(0.2)                 // pinned -> pull, no step
        assertFalse(Pin(0, 2) in b.pins)
        assertEquals(0, b.logicalCol())
        assertEquals(Notice.REMOVED, b.activeNotice())
    }

    @Test fun tapsDuringTheStepLandOnTheNewColumn() {
        val b = mineBox()
        b.tapTine(0); b.advance(0.03); b.tapTine(1)  // mid-animation
        assertTrue(Pin(0, 0) in b.pins && Pin(1, 1) in b.pins)
    }

    @Test fun restAndBackStep() {
        val b = mineBox()
        b.rest(); b.rest(); assertEquals(2, b.logicalCol())
        b.back(); assertEquals(1, b.logicalCol())
        b.back(); b.back(); assertEquals(63, b.logicalCol())
    }

    // ------------------------------------------------------------------ live pins (M3, T7)

    @Test fun livePinQuantisesAndOnlyAdds() {
        val b = mineBox()
        b.setSpringFraction(1.0)
        // 100 bpm = 1.6667 steps/s; at 12.4 -> 12, 12.6 -> 13
        b.advance(12.4 / (100 / 60.0))
        assertEquals(12.4, b.pos, 0.02)
        b.tapTine(3)
        assertTrue(Pin(12, 3) in b.pins)
        b.advance(0.2 / (100 / 60.0))
        b.tapTine(5)
        assertTrue(Pin(13, 5) in b.pins)
        b.advance(0.1)
        val before = b.pins
        b.debugSetPins(before + Pin(b.col, 6))
        b.tapTine(6)                                    // already there: no pull while playing
        assertTrue(Pin(b.col, 6) in b.pins)
        assertTrue(b.running)
        assertTrue(b.canUndo)
        b.undo()                                        // undo while playing: only the live pin, no stop
        assertTrue(b.running)
    }

    // ------------------------------------------------------------------ spring (M4)

    @Test fun fourTapsFillAndNeverOverflow() {
        val b = box(song = "twinkle"); b.debugLidInstant(false)
        repeat(4) { b.tapKey() }
        assertEquals(64.0, b.spring, 1e-9)
        assertEquals(8, b.gaugeCells())
        b.sfx.clear(); b.tapKey()
        assertEquals(64.0, b.spring, 1e-9)
        assertTrue(b.sfx.none { it.kind == Sfx.Kind.CLICK })
        assertTrue(b.t - b.gaugeFlashAt < 0.01)
    }

    @Test fun dragSixCentimetresIsAQuarterAndSixClicks() {
        val b = box(song = "twinkle"); b.debugLidInstant(false)
        b.keyDragStart()
        for (i in 1..60) b.keyDragMove(i * 0.001)
        b.keyDragEnd()
        assertEquals(16.0, b.spring, 1e-6)
        assertEquals(6, b.sfx.count { it.kind == Sfx.Kind.CLICK })
        assertEquals(BoxState.LID_PAUSED, b.state())   // closed: wound, waiting (B7)
    }

    @Test fun closedLidKeepsTheSpringAndOpeningPlays() {
        val b = box(song = "twinkle")
        b.setSpringFraction(0.5)
        b.advance(1.0)
        b.closeLid(); b.advance(1.0)
        val pos = b.pos; val spring = b.spring
        b.advance(2.0)
        assertEquals(pos, b.pos, 1e-9); assertEquals(spring, b.spring, 1e-9)
        assertEquals(BoxState.LID_PAUSED, b.state())
        b.openLid(); b.advance(1.5)
        assertTrue(b.running); assertTrue(b.pos != pos)
    }

    @Test fun rundownLengthAndSlowdown() {
        assertEquals(2.0, MusicBox.rundown(8.0), 0.0)
        assertEquals(8.0, MusicBox.rundown(64.0), 0.0)
        assertEquals(3.0, MusicBox.rundown(16.0), 0.0)
        val b = mineBox()
        for (t in C) { b.tapTine(t); b.advance(0.2) }
        b.windAndPlay(); b.advance(0.31)
        b.sfx.clear()
        b.advance(20.0)
        val times = plucks(b).map { it.first }               // columns 1..6 (column 0 sounded when it started)
        val stopAt = b.sfx.first { it.kind == Sfx.Kind.STOP }.at
        // the last two steps (rd = 2 of 8: column 6 -> 8) are slower than the first ones
        val early = times[1] - times[0]
        val late = (stopAt - times.last()) / 2
        assertTrue("late $late vs early $early", late > early * 1.3)
        assertEquals(BoxState.OPEN, b.state())
    }

    @Test fun windAndPlayOnAFullPresetDoesNotOverflow() {
        val b = box(song = "jingle")
        b.windAndPlay(); b.advance(0.35)
        assertEquals(64.0, b.spring, 1.0)
        assertTrue(b.gaugeCells() <= 8)
    }

    @Test fun emptyColumnsFastForwardWithoutSpring() {
        val b = box(song = "twinkle")                       // L = 48
        b.debugSetPos(47)
        b.setSpringFraction(4.0 / 64)
        val s = b.spring
        b.advance(0.6)                                       // 47 -> 48, then 48..63 skipped
        b.advance(0.31)
        assertTrue("pos ${b.pos}", b.pos < 2.0)
        assertTrue(b.spring > s - 3.0)
    }

    // ------------------------------------------------------------------ playback / scrub (M5)

    @Test fun first16StepsMatchTheScore() {
        val b = box(song = "twinkle")
        b.windAndPlay(); b.advance(0.31)
        b.advance(16 / (100 / 60.0) - 0.05)
        val got = plucks(b).map { it.second }
        val want = (0 until 16).flatMap { c -> PRESETS.first { it.id == "twinkle" }.pins.filter { it.col == c }.map { it.tine }.sorted() }
        assertEquals(want, got.take(want.size).let { g -> var i = 0; want.indices.map { g[i++] } })
    }

    @Test fun scrubForwardAndBackPlaysBothWaysAndTurnsTheDancer() {
        val b = box(song = "twinkle")
        b.scrubStart(); b.scrubMove(3 * MusicBox.DRUM_M_PER_STEP); b.advance(1.0)
        val fwd = plucks(b).map { it.second }
        val a0 = b.dancerAngle
        assertTrue(a0 > 0)
        b.sfx.clear()
        b.scrubMove(0.0); b.advance(1.0)
        val back = plucks(b).map { it.second }
        assertTrue(b.dancerAngle < a0)
        b.endScrub(); b.advance(0.3)
        assertEquals(0, b.logicalCol())
        assertEquals(listOf(0, 4, 4), fwd)                 // columns 1, 2, 3
        assertEquals(listOf(4, 0), back.take(2))            // reverse: columns 2, 1, then 0
        assertEquals(setOf(0, 2), back.drop(2).toSet())
    }

    @Test fun scrubSpeedIsCapped() {
        val b = box(song = "twinkle")
        b.scrubStart(); b.scrubMove(30 * MusicBox.DRUM_M_PER_STEP); b.advance(1.0)
        assertTrue(b.pos <= 12.1)
    }

    @Test fun scrubFollowsTheVolumeScale() {
        // 1 cm of hand = 1 step at k = 1.28; at a 2x volume the drum is twice as big, so 2 cm = 1 step
        for ((k, cm) in listOf(0.64f to 0.5, 1.28f to 1.0, 3.84f to 3.0)) {
            val b = box(song = "twinkle")
            b.scrubStart(); b.scrubMove(cm / 100 / k); b.advance(1.0); b.endScrub(); b.advance(0.3)
            assertEquals("k=$k", 1, b.logicalCol())
        }
    }

    // ------------------------------------------------------------------ presets / my tunes (M8, T10)

    @Test fun editingAPresetForksIt() {
        val b = box(song = "twinkle")
        b.debugSetPos(50)
        b.tapTine(2)
        assertEquals(1, b.mine.size)
        assertFalse(b.song.preset)
        assertEquals("小星星 · 改", b.song.nameZh)
        assertTrue(Pin(50, 2) in b.song.pins)
        assertEquals(PRESETS.first { it.id == "twinkle" }.pins, b.presets.first { it.id == "twinkle" }.pins)
        assertEquals(Notice.FORKED, b.activeNotice())
        // a second fork gets a number
        b.selectSong("twinkle"); b.advance(1.0); b.debugSetPos(50); b.tapTine(3)
        assertEquals("小星星 · 改 2", b.song.nameZh)
    }

    @Test fun fullMineRefusesTheForkAndNewTune() {
        val b = box()
        repeat(6) { assertNotNull(b.newSong()) }
        assertEquals(null, b.newSong())
        b.selectSong("twinkle"); b.advance(1.0)
        val n = b.pins.size
        b.debugSetPos(50); b.tapTine(2)
        assertEquals(n, b.pins.size)
        assertEquals(Notice.FULL, b.activeNotice())
    }

    @Test fun undoStackIsFiftyDeep() {
        val b = mineBox()
        repeat(60) { b.tapTine(it % 8); b.advance(0.2) }
        var n = 0
        while (b.undo()) n++
        assertEquals(50, n)
    }

    @Test fun clearIsUndoable() {
        val b = mineBox()
        for (t in C) { b.tapTine(t); b.advance(0.2) }
        assertTrue(b.clear())
        assertTrue(b.pins.isEmpty())
        assertTrue(b.undo())
        assertEquals(7, b.pins.size)
    }

    @Test fun switchingSongsThreeTimesAlwaysSings() {
        val b = box()
        for (id in listOf("ode", "mary", "ode", "canon")) {
            b.selectSong(id); b.advance(0.7)
            assertEquals(BoxState.OPEN, b.state())
            b.sfx.clear(); b.windAndPlay(); b.advance(2.0)
            assertTrue("$id sings", plucks(b).isNotEmpty())
            b.stop(); b.advance(0.3)
        }
    }

    // ------------------------------------------------------------------ D55: wall clock, any chunking

    @Test fun lowFrameRateDoesNotSlowTheMusic() {
        fun run(dt: Double): MusicBox { val b = box(song = "ode"); b.windAndPlay(); var t = 0.0; while (t < 1.3) { b.advance(dt); t += dt }; return b }
        val fine = run(0.004); val coarse = run(0.1)
        assertEquals(fine.pos, coarse.pos, 0.05)
        assertEquals(plucks(fine).size, plucks(coarse).size)
        val b = box(song = "ode"); repeat(10) { b.advance(0.1) }
        assertEquals(1.0, b.t, 0.01)
    }

    // ------------------------------------------------------------------ D38

    @Test fun cardAndPanelReadTheSameLine() {
        val b = box(song = "twinkle")
        b.debugLidInstant(false)
        for (st in listOf<() -> Unit>({ }, { b.debugLidInstant(true) }, { b.tapKey() }, { b.advance(0.5) }, { b.stop() }, { b.closeLid(); b.advance(1.5) })) {
            st()
            val ctx = Lines.Ctx(b.state(), b.activeNotice())
            // the card and the panel call the same function with the same context
            assertEquals(Lines.key(ctx), Lines.key(ctx.copy()))
            assertTrue(Lines.key(ctx) in Lines.ALL_KEYS)
        }
        // gauge cells and the panel's spring cells are the same number
        b.debugLidInstant(false); b.setSpringFraction(5.0 / 8)
        assertEquals(5, b.gaugeCells())
        assertEquals(BoxState.LID_PAUSED, b.state())
        assertEquals("B7", Lines.key(Lines.Ctx(b.state(), null)))
        assertEquals("P5", Lines.key(Lines.Ctx(b.state(), null, panelOpen = false)))
    }
}
