package io.embodit.musicbox.domain

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Box states (design states[] B0..B9); the box is the single state source (D38). */
enum class BoxState(val id: String) {
    LOADING("B0"), CLOSED("B1"), OPEN("B2"), WINDING("B3"), PLAYING("B4"), RUNDOWN("B4r"), SCRUB("B5"),
    PINNING("B6"), LID_PAUSED("B7"), SWAPPING("B8"), REWINDING("B9");

    val playing: Boolean get() = this == PLAYING || this == RUNDOWN
}

/** A one-shot sound the box asks for. [at] = sim time it belongs to (sounds follow the simulation clock, D55). */
data class Sfx(val kind: Kind, val tine: Int = -1, val soft: Boolean = false, val at: Double = 0.0) {
    enum class Kind { TINE, CLICK, LID_OPEN, LID_CLOSE, STOP, PIN }
}

/** Short-lived lines the card / panel show instead of the state line (design content.tone.cardLines). */
enum class Notice(val key: String) {
    ADDED("B6add"), REMOVED("B6remove"), FORKED("presetFork"), FULL("full"), LID_CLOSED_EDIT("lidClosedEdit"), PLAYING_EDIT("playingEdit")
}

private sealed interface Undo {
    data class Add(val pin: Pin, val posBefore: Int, val live: Boolean) : Undo
    data class Remove(val pin: Pin, val posBefore: Int) : Undo
    data class Clear(val pins: Set<Pin>, val posBefore: Int) : Undo
}

/**
 * The music box (design gameSpec): a 64-column x 8-tine drum, a spring measured in steps, a lid with a stopper, the
 * dancer lift. Pure Kotlin, no Android: every action is a method, time only moves through [advance] (the simulation
 * thread calls it with wall-clock dt, so a slow render frame never slows the music, D55). Not thread-safe: callers
 * synchronise on the instance. Sounds are queued in [sfx] and drained by the caller.
 *
 * Drum position [pos] is in columns; an integer pos means that column sits on the pluck line. Crossing an integer in
 * either direction plucks every pin of that column (reverse plays too). Forward = the drum's top turns toward the user.
 */
class MusicBox(
    presets: List<Song>,
    mine: List<Song>,
    startSongId: String? = null,
    private val wallClock: () -> Long = { 0L },
) {
    companion object {
        const val FULL_STEPS = 64.0
        const val NOTCH_STEPS = FULL_STEPS / 24.0        // one ratchet notch = 30 degrees of key = 1/24 of a full wind
        const val TAP_STEPS = 16.0                       // a tap on the key = +25 %
        const val KEY_M_PER_NOTCH = 0.01                 // 1 cm of downward drag = one notch
        const val DRUM_M_PER_STEP = 0.0078               // content metres at k = 1 per drum step (1 cm of hand at k = 1.28)
        const val SCRUB_MAX = 12.0                       // steps / s
        const val EDIT_STEP_S = 0.12
        const val LID_OPEN_S = 0.6
        const val LID_CLOSE_S = 0.5
        const val DANCER_UP_S = 0.5
        const val DANCER_DOWN_S = 0.4
        const val FF_MAX_S = 0.3
        const val REWIND_S = 0.3
        const val SWAP_S = 0.6
        const val NOTICE_S = 2.5
        const val UNDO_DEPTH = 50
        const val NOTES_MAX = 16
        const val DEG_PER_COL = 360.0 / Drum.COLS

        /** Run-down length rd = min(8, max(2, ceil(0.15 x S0))). */
        fun rundown(s0: Double): Double = min(8.0, max(2.0, ceil(0.15 * s0)))

        fun wrap(c: Int): Int = Math.floorMod(c, Drum.COLS)
    }

    // ------------------------------------------------------------------ songs
    val presets: List<Song> = presets
    var mine: List<Song> = mine; private set
    var song: Song = (presets + mine).firstOrNull { it.id == startSongId } ?: presets.firstOrNull() ?: Song("empty", true, "空", "Empty")
        private set
    val pins: Set<Pin> get() = song.pins
    /** Bumped on every change that must be saved (pins, my tunes). */
    var revision = 0; private set

    // ------------------------------------------------------------------ clock / mechanics
    var t = 0.0; private set
    var frozen = false
    var loading = true
    var pos = 0.0; private set
    var spring = 0.0; private set
    var running = false; private set
    private var s0 = 0.0
    var bpm: Int = song.bpm; private set
    var speed = 1.0
        set(v) { field = v.coerceIn(0.5, 1.5) }
    private var ff = false
    private var finishing = false

    var lidTarget = false; private set
    var lidAmt = 0.0; private set
    var dancerAmt = 0.0; private set
    val lidOpen: Boolean get() = lidTarget && lidAmt >= 1.0

    // edit step / undo rotation / snap / rewind / swap visuals
    private var visFrom = 0.0
    private var visStart = -1.0
    private var visDur = 0.0
    private var snapping = false
    private var snapTarget = 0
    private var snapPluck = true
    private var rewindFrom = -1.0
    private var rewindStart = -1.0
    private var playAfterOpen = false
    var swapStart = -10.0; private set
    var swapOldPins: Set<Pin> = emptySet(); private set
    private var swapFromPos = 0.0

    // scrub
    var scrubbing = false; private set
    private var scrubBase = 0.0
    private var scrubTarget = 0.0
    private var resumeAfterScrub = false

    // key
    var keyDragging = false; private set
    private var keyNotches = 0
    var keyAngle = 0.0; private set          // degrees, shown
    private var keyAngleTarget = 0.0
    var gaugeFlashAt = -10.0; private set
    private var keyTapAt = -10.0

    // visuals / feedback
    val pluckAt = DoubleArray(Drum.TINES) { -10.0 }
    val notes = ArrayDeque<Pair<Int, Double>>()
    val pinAddedAt = HashMap<Pin, Double>()
    val pinGone = ArrayList<Pair<Pin, Double>>()
    var dancerAngle = 0.0; private set
    var notice: Notice? = null; private set
    var noticeAt = -10.0; private set
    var lastPinAt = -10.0; private set
    var songsPlayedToEnd = 0; private set

    private val undo = ArrayDeque<Undo>()
    val sfx = ArrayList<Sfx>()

    val length: Int get() = song.length
    val col: Int get() = wrap(pos.roundToInt())
    val locked: Boolean get() = loading || t - swapStart < SWAP_S || rewindStart >= 0

    // ------------------------------------------------------------------ state (D38)

    fun state(): BoxState = when {
        loading -> BoxState.LOADING
        t - swapStart < SWAP_S -> BoxState.SWAPPING
        rewindStart >= 0 -> BoxState.REWINDING
        scrubbing || (snapping && snapPluck && !running) -> BoxState.SCRUB
        !lidOpen -> if (spring > 0) BoxState.LID_PAUSED else BoxState.CLOSED
        keyDragging && !running -> BoxState.WINDING
        running && !finishing && spring <= rundown(s0) && spring <= 8.0 -> BoxState.RUNDOWN
        running -> BoxState.PLAYING
        t - lastPinAt < EDIT_STEP_S -> BoxState.PINNING
        else -> BoxState.OPEN
    }

    fun activeNotice(): Notice? = notice?.takeIf { t - noticeAt < NOTICE_S }

    private fun say(n: Notice) { notice = n; noticeAt = t }

    val canEdit: Boolean get() = lidOpen && !running && !scrubbing && !locked
    val canUndo: Boolean
        get() {
            val top = undo.lastOrNull() ?: return false
            if (locked || !lidOpen) return false
            return if (running || scrubbing) top is Undo.Add && top.live else true
        }
    val undoDepth: Int get() = undo.size

    // ------------------------------------------------------------------ lid

    fun tapLid() {
        if (loading) return
        if (lidTarget) closeLid() else openLid()
    }

    fun openLid() {
        if (lidTarget) return
        lidTarget = true
        sfx += Sfx(Sfx.Kind.LID_OPEN, at = t)
    }

    fun closeLid() {
        if (!lidTarget) return
        lidTarget = false
        playAfterOpen = false
        if (scrubbing) endScrub()
    }

    // ------------------------------------------------------------------ tines (design gameSpec.pinEntry)

    /** A tap on tine [r] (0..7). Returns false when nothing happened (lid closed / locked). */
    fun tapTine(r: Int): Boolean {
        if (r !in 0 until Drum.TINES || locked) return false
        if (!lidOpen) { say(Notice.LID_CLOSED_EDIT); return false }
        if (running || scrubbing) {
            // live pin: nearest column, add only; the tap always sounds
            pluck(r, soft = false)
            val c = wrap(pos.roundToInt())
            val p = Pin(c, r)
            if (p in pins) return true
            if (!ensureEditable()) return true
            if (pins.size >= Drum.PIN_CAP) return true
            setPins(pins + p); pinAddedAt[p] = t; lastPinAt = t
            push(Undo.Add(p, c, live = true))
            say(Notice.ADDED); sfx += Sfx(Sfx.Kind.PIN, at = t)
            return true
        }
        val c = logicalCol()
        val p = Pin(c, r)
        if (p in pins) {
            if (!ensureEditable()) return true
            setPins(pins - p); pinGone += p to t; lastPinAt = t
            push(Undo.Remove(p, c))
            pluck(r, soft = true)
            if (notice != Notice.FORKED || t - noticeAt > 0.01) say(Notice.REMOVED)
            sfx += Sfx(Sfx.Kind.PIN, at = t)
            return true
        }
        if (pins.size >= Drum.PIN_CAP) return true
        val forked = song.preset
        if (!ensureEditable()) return true
        setPins(pins + p); pinAddedAt[p] = t; lastPinAt = t
        push(Undo.Add(p, c, live = false))
        pluck(r, soft = false)
        if (!forked) say(Notice.ADDED)
        sfx += Sfx(Sfx.Kind.PIN, at = t)
        stepTo(c + 1)
        return true
    }

    /** Presets are read-only: the first change copies the tune into "mine" (full = refuse, design T10). */
    private fun ensureEditable(): Boolean {
        if (!song.preset) return true
        if (mine.size >= Drum.MY_CAP) { say(Notice.FULL); return false }
        val (zh, en) = SongNames.fork(song, mine)
        val copy = song.copy(id = newId(), preset = false, nameZh = zh, nameEn = en, tagZh = "", tagEn = "", licZh = song.licZh, licEn = song.licEn, updatedAt = wallClock())
        mine = mine + copy
        song = copy
        undo.clear()
        say(Notice.FORKED)
        revision++
        return true
    }

    private var idSeq = 0
    private fun newId(): String = "my${wallClock()}_${idSeq++}"

    private fun setPins(p: Set<Pin>) {
        val s = song.copy(pins = p, updatedAt = wallClock())
        song = s
        if (!s.preset) mine = mine.map { if (it.id == s.id) s else it }
        revision++
    }

    private fun push(u: Undo) {
        undo.addLast(u)
        while (undo.size > UNDO_DEPTH) undo.removeFirst()
    }

    /** The column under the pluck line when the drum rests (the logical column, even mid-animation). */
    fun logicalCol(): Int = wrap(floor(pos + 0.5).toInt())

    private fun stepTo(target: Int, dur: Double = EDIT_STEP_S) {
        visFrom = visualPos(); visStart = t; visDur = dur
        pos = wrap(target).toDouble()
    }

    // ------------------------------------------------------------------ edit row

    fun back() { if (canEdit) stepTo(logicalCol() - 1) else editRefused() }

    fun rest() { if (canEdit) stepTo(logicalCol() + 1) else editRefused() }

    private fun editRefused() { if (!lidOpen) say(Notice.LID_CLOSED_EDIT) else if (running || scrubbing) say(Notice.PLAYING_EDIT) }

    fun undo(): Boolean {
        if (!canUndo) return false
        val u = undo.removeLast()
        when (u) {
            is Undo.Add -> { setPins(pins - u.pin); pinGone += u.pin to t; if (!u.live) stepTo(u.posBefore) }
            is Undo.Remove -> { setPins(pins + u.pin); pinAddedAt[u.pin] = t; stepTo(u.posBefore) }
            is Undo.Clear -> { setPins(u.pins); u.pins.forEach { pinAddedAt[it] = t }; stepTo(u.posBefore) }
        }
        sfx += Sfx(Sfx.Kind.PIN, at = t)
        return true
    }

    fun clear(): Boolean {
        if (!canEdit || pins.isEmpty()) return false
        val old = pins
        if (!ensureEditable()) return false
        old.forEach { pinGone += it to t }
        setPins(emptySet())
        push(Undo.Clear(old, logicalCol()))
        return true
    }

    // ------------------------------------------------------------------ key / spring

    /** A tap on the key: +25 % (6 notches, a click every 50 ms); full = idle turn + gauge flash. */
    fun tapKey() {
        if (locked) return
        val notches = addSpring(TAP_STEPS)
        for (i in 0 until notches) sfx += Sfx(Sfx.Kind.CLICK, at = t + 0.05 * i)
        keyAngleTarget += 180.0
        keyTapAt = t
        afterWind()
    }

    fun keyDragStart() { if (!locked) { keyDragging = true; keyNotches = 0 } }

    /** [downM] = total downward travel (content metres at k = 1) since the press; up = idle turn only. */
    fun keyDragMove(downM: Double) {
        if (!keyDragging) return
        val want = floor(downM / KEY_M_PER_NOTCH).toInt()
        keyAngle = keyAngleBase + downM / KEY_M_PER_NOTCH * 30.0
        keyAngleTarget = keyAngle
        while (keyNotches < want) {
            keyNotches++
            if (addSpring(NOTCH_STEPS) > 0) sfx += Sfx(Sfx.Kind.CLICK, at = t)
            if (running) s0 = spring
        }
    }

    private var keyAngleBase = 0.0

    fun keyDragEnd() {
        if (!keyDragging) return
        keyDragging = false
        keyAngleBase = keyAngle
        afterWind()
    }

    /** Adds [steps] up to a full wind; returns how many notches actually went in (0 = already full -> flash). */
    private fun addSpring(steps: Double): Int {
        if (spring >= FULL_STEPS - 1e-9) { gaugeFlashAt = t; return 0 }
        val before = spring
        spring = min(FULL_STEPS, spring + steps)
        if (spring >= FULL_STEPS - 1e-9) gaugeFlashAt = t
        return max(1, ceil((spring - before) / NOTCH_STEPS - 1e-6).toInt())
    }

    private fun afterWind() {
        if (running) { s0 = spring; finishing = false; return }
        if (keyDragging) return
        if (lidOpen && spring > 0 && !scrubbing && !locked) start(pluckHere = true)
    }

    fun setSpringFraction(f: Double) { spring = (f.coerceIn(0.0, 1.0) * FULL_STEPS); s0 = spring; finishing = false; if (spring > 0 && lidOpen && !running && !scrubbing) start(true) }

    /** [上弦并播放] (T3): lid closed -> open first; rewind to column 0 (silent, <= 0.3 s); spring = min(L, 64); play. */
    fun windAndPlay() {
        if (locked || running) return
        playAfterOpen = true
        if (!lidTarget) openLid() else if (lidOpen) beginRewind()
    }

    private fun beginRewind() {
        playAfterOpen = false
        if (scrubbing) endScrub()
        snapping = false
        rewindFrom = visualPos()
        rewindStart = t
        visStart = -1.0
        pos = 0.0
    }

    /** [停] (T4): spring to zero, the drum settles on the nearest column (silent). */
    fun stop() {
        playAfterOpen = false
        val was = running || spring > 0
        running = false; ff = false; finishing = false
        spring = 0.0; s0 = 0.0
        if (abs(pos - pos.roundToInt()) > 1e-6) { snapTarget = pos.roundToInt(); snapping = true; snapPluck = false }
        if (was) sfx += Sfx(Sfx.Kind.STOP, at = t)
    }

    private fun start(pluckHere: Boolean) {
        running = true; ff = false; finishing = false
        s0 = spring
        if (pluckHere && abs(pos - pos.roundToInt()) < 1e-6) arriveAt(pos.roundToInt(), playing = true)
    }

    // ------------------------------------------------------------------ drum scrub (T5)

    fun scrubStart() {
        if (locked || !lidOpen) return
        scrubbing = true
        resumeAfterScrub = running
        running = false
        snapping = false
        visStart = -1.0
        scrubBase = pos; scrubTarget = pos
    }

    /** [downM] = total downward hand travel in content metres at k = 1 since the press (down = forward). */
    fun scrubMove(downM: Double) { if (scrubbing) scrubTarget = scrubBase + downM / DRUM_M_PER_STEP }

    fun endScrub() {
        if (!scrubbing) return
        scrubbing = false
        snapTarget = pos.roundToInt(); snapping = true; snapPluck = true
    }

    // ------------------------------------------------------------------ songs

    /** Loads [id] (T9 / B8): stop, spring to zero, old pins retract, the drum turns back to 0, new pins pop up. */
    fun selectSong(id: String, animate: Boolean = true): Boolean {
        val s = (presets + mine).firstOrNull { it.id == id } ?: return false
        if (loading || !animate) {
            song = s; bpm = s.bpm; pos = 0.0; visStart = -1.0; rewindStart = -1.0; swapStart = -10.0
            running = false; ff = false; finishing = false; scrubbing = false; snapping = false; playAfterOpen = false
            spring = 0.0; s0 = 0.0; undo.clear(); pinGone.clear(); pinAddedAt.clear(); notice = null
            return true
        }
        swapOldPins = pins
        swapFromPos = visualPos()
        swapStart = t
        running = false; ff = false; finishing = false; scrubbing = false; snapping = false; playAfterOpen = false
        spring = 0.0; s0 = 0.0
        song = s; bpm = s.bpm
        pos = 0.0; visStart = -1.0; rewindStart = -1.0
        undo.clear(); pinGone.clear(); pinAddedAt.clear()
        notice = null
        return true
    }

    /** [新曲子]: an empty drum, bpm 100; null when my tunes are full. */
    fun newSong(): Song? {
        if (mine.size >= Drum.MY_CAP) { say(Notice.FULL); return null }
        val (zh, en) = SongNames.fresh(mine)
        val s = Song(newId(), false, zh, en, bpm = 100, updatedAt = wallClock())
        mine = mine + s
        revision++
        selectSong(s.id)
        return s
    }

    fun deleteSong(id: String): Boolean {
        val s = mine.firstOrNull { it.id == id } ?: return false
        mine = mine - s
        revision++
        if (song.id == id) selectSong(presets.first().id)
        return true
    }

    // ------------------------------------------------------------------ time

    /** The drum angle shown: the logical position, or the running edit / rewind / swap animation. */
    fun visualPos(): Double {
        if (t - swapStart < SWAP_S) {
            val u = ((t - swapStart - 0.2) / 0.2).coerceIn(0.0, 1.0)
            return swapFromPos + (shortestTo(swapFromPos, 0.0)) * ease(u)
        }
        if (rewindStart >= 0) {
            val u = ((t - rewindStart) / REWIND_S).coerceIn(0.0, 1.0)
            return rewindFrom + shortestBack(rewindFrom) * ease(u)
        }
        if (visStart >= 0 && t - visStart < visDur) {
            val u = (t - visStart) / visDur
            return visFrom + shortestTo(visFrom, pos) * ease(u)
        }
        return pos
    }

    private fun shortestTo(from: Double, to: Double): Double {
        var d = (to - from) % Drum.COLS
        if (d > Drum.COLS / 2) d -= Drum.COLS
        if (d < -Drum.COLS / 2) d += Drum.COLS
        return d
    }

    /** Rewind goes backward to column 0 (design B9: "倒转回第 0 列"). */
    private fun shortestBack(from: Double): Double { val f = ((from % Drum.COLS) + Drum.COLS) % Drum.COLS; return -f }

    private fun ease(u: Double): Double { val v = u.coerceIn(0.0, 1.0); return 1 - (1 - v) * (1 - v) * (1 - v) }

    /** Moves the simulation on by [dt] seconds of wall clock (any chunking gives the same result within float error). */
    fun advance(dt: Double) {
        if (frozen || dt <= 0) return
        var left = dt
        while (left > 1e-9) {
            if (t >= freezeAt) { frozen = true; freezeAt = Double.MAX_VALUE; break }
            val h = min(left, 0.004)
            step(h)
            left -= h
        }
        notes.removeAll { t - it.second > 1.2 }
        pinGone.removeAll { t - it.second > 0.3 }
        if (pinAddedAt.size > 64) pinAddedAt.entries.removeAll { t - it.value > 0.3 }
    }

    private fun step(h: Double) {
        t += h
        // lid and dancer (design gameSpec.lid)
        if (lidTarget) {
            if (lidAmt < 1) lidAmt = min(1.0, lidAmt + h / LID_OPEN_S)
            else dancerAmt = min(1.0, dancerAmt + h / DANCER_UP_S)
        } else {
            if (dancerAmt > 0) dancerAmt = max(0.0, dancerAmt - h / DANCER_DOWN_S)
            else if (lidAmt > 0) { lidAmt = max(0.0, lidAmt - h / LID_CLOSE_S); if (lidAmt == 0.0) sfx += Sfx(Sfx.Kind.LID_CLOSE, at = t) }
        }
        // key settles toward its target (tap = half a turn in 0.3 s)
        if (!keyDragging) {
            val d = keyAngleTarget - keyAngle
            keyAngle += d * min(1.0, h / 0.08)
            keyAngleBase = keyAngle
        }
        if (loading) return
        val nowOpen = lidOpen
        if (nowOpen && !wasOpen && !playAfterOpen && spring > 0 && !running && !scrubbing && rewindStart < 0 && t - swapStart >= SWAP_S) {
            start(pluckHere = abs(pos - pos.roundToInt()) < 1e-6) // B7 -> open -> B4 (the stopper lets go)
        }
        wasOpen = nowOpen
        if (playAfterOpen && lidOpen) beginRewind()
        if (rewindStart >= 0 && t - rewindStart >= REWIND_S) {
            rewindStart = -1.0
            pos = 0.0
            spring = min(length.toDouble(), FULL_STEPS)
            start(pluckHere = true)
        }
        if (t - swapStart < SWAP_S) return
        when {
            scrubbing -> {
                val d = scrubTarget - pos
                val m = SCRUB_MAX * h
                if (abs(d) > 1e-9) move(d.coerceIn(-m, m), pluck = true, dancer = true)
            }
            snapping -> {
                val d = snapTarget - pos
                val m = h / EDIT_STEP_S
                if (abs(d) <= m || abs(d) < 1e-9) {
                    move(d, pluck = snapPluck, dancer = true)
                    pos = wrap(snapTarget).toDouble(); snapping = false
                    if (resumeAfterScrub || spring > 0) { resumeAfterScrub = false; if (lidOpen && spring > 0) start(pluckHere = false) }
                } else move(m * kotlin.math.sign(d), pluck = snapPluck, dancer = true)
            }
            running && lidOpen -> run(h)
        }
    }

    private var wasOpen = false

    private fun rate(): Double {
        val base = bpm / 60.0 * speed
        if (finishing || spring <= 0) return base * 0.4
        val rd = rundown(s0)
        return if (spring <= rd) base * (0.4 + 0.6 * spring / rd) else base
    }

    private fun run(h: Double) {
        var left = h
        while (left > 1e-12 && running) {
            if (ff) {
                val r = (Drum.COLS - length) / FF_MAX_S
                val next = Drum.COLS.toDouble()
                val d = min(next - pos, r * left)
                pos += d; left -= d / r
                if (pos >= next - 1e-9) { pos = 0.0; ff = false; arriveAt(0, playing = true) }
                continue
            }
            val r = rate()
            val nextInt = floor(pos + 1e-9) + 1.0
            var d = min(nextInt - pos, r * left)
            if (!finishing && spring > 0) d = min(d, spring)
            if (!finishing && spring > 0 && spring <= rundown(s0)) d = min(d, 0.05)
            pos += d; left -= d / r
            dancerAngle += d * DEG_PER_COL * 8
            if (!finishing && spring > 0) { spring = max(0.0, spring - d); if (spring <= 1e-9) { spring = 0.0; finishing = true } }
            if (pos >= nextInt - 1e-9) {
                pos = nextInt
                val c = nextInt.roundToInt()
                arriveAt(c, playing = true)
                pos = wrap(c).toDouble()
                if (finishing) {
                    running = false; finishing = false
                    sfx += Sfx(Sfx.Kind.STOP, at = t)
                    if (pos.roundToInt() == length % Drum.COLS || length == Drum.COLS && pos.roundToInt() == 0) songsPlayedToEnd++
                } else if (length < Drum.COLS && pos.roundToInt() == length) ff = true
            }
        }
    }

    /** Moves the drum by [d] columns, plucking every integer crossed (forward: reaching k; reverse: reaching k from above). */
    private fun move(d: Double, pluck: Boolean, dancer: Boolean) {
        val from = pos
        val to = pos + d
        if (pluck) {
            if (d > 0) { var k = floor(from + 1e-9) + 1; while (k <= to + 1e-9) { arriveAt(k.roundToInt(), false); k += 1 } }
            else if (d < 0) { var k = ceil(from - 1e-9) - 1; while (k >= to - 1e-9) { arriveAt(k.roundToInt(), false); k -= 1 } }
        }
        pos = ((to % Drum.COLS) + Drum.COLS) % Drum.COLS
        if (dancer) dancerAngle += d * DEG_PER_COL * 8
    }

    private fun arriveAt(c: Int, @Suppress("UNUSED_PARAMETER") playing: Boolean) {
        val col = wrap(c)
        for (p in pins) if (p.col == col) pluck(p.tine, soft = false)
    }

    private fun pluck(r: Int, soft: Boolean) {
        pluckAt[r] = t
        sfx += Sfx(Sfx.Kind.TINE, r, soft, t)
        if (!soft) {
            notes.addLast(r to t)
            while (notes.size > NOTES_MAX) notes.removeFirst()
        }
    }

    /** The pins the drum shows right now (swap animation: old ones retract first). */
    fun shownPins(): Set<Pin> = if (t - swapStart < 0.2) swapOldPins else pins

    /** Pin height scale 0..1 for the pop / retract animations (120 ms; 200 ms during a swap). */
    fun pinScale(p: Pin): Double {
        val ds = t - swapStart
        if (ds < 0.2) return 1 - ds / 0.2
        if (ds < 0.4) return 0.0
        if (ds < SWAP_S) return (ds - 0.4) / 0.2
        val a = pinAddedAt[p] ?: return 1.0
        return ((t - a) / EDIT_STEP_S).coerceIn(0.0, 1.0)
    }

    /** Tine bend 0..1 (tip down 4 mm then back, 80 ms) for the shown deflection. */
    fun tineBend(r: Int): Double {
        val e = t - pluckAt[r]
        if (e < 0 || e > 0.08) return 0.0
        return if (e < 0.015) e / 0.015 else (1 - (e - 0.015) / 0.065) * kotlin.math.cos((e - 0.015) * 120)
    }

    /** Spring gauge: 8 cells, each 12.5 % (lit count, rounded up so a nearly empty spring still shows one cell). */
    fun gaugeCells(): Int = if (spring <= 1e-9) 0 else ceil(spring / FULL_STEPS * 8 - 1e-9).toInt().coerceIn(1, 8)

    /** Debug: rewind the clock is impossible; this only sets the drum on a column (stopped). */
    fun debugSetPos(c: Int) { if (!running) { pos = wrap(c).toDouble(); visStart = -1.0 } }

    fun debugSetPins(p: Set<Pin>) { setPins(p) }

    /** Debug (QA screenshots, `hold`): the simulation stops itself at sim time [at]. */
    var freezeAt = Double.MAX_VALUE

    /** Debug: lid fully open / closed at once (the dancer follows). */
    fun debugLidInstant(open: Boolean) { lidTarget = open; lidAmt = if (open) 1.0 else 0.0; dancerAmt = lidAmt; wasOpen = open }

    fun finishLoading(openLid: Boolean = false) {
        loading = false
        if (openLid) { lidTarget = true; lidAmt = 1.0; dancerAmt = 1.0 }
    }

    /** Going to the background (design lifecycle): stop, spring to zero; the tune is saved by the caller. */
    fun onBackground() {
        running = false; ff = false; finishing = false; scrubbing = false; keyDragging = false
        spring = 0.0; s0 = 0.0
        if (abs(pos - pos.roundToInt()) > 1e-6) pos = wrap(pos.roundToInt()).toDouble()
    }

    /** Applies a saved my-tunes list (the engine owns the list after start). */
    fun restoreMine(list: List<Song>) { mine = list.take(Drum.MY_CAP) }
}
