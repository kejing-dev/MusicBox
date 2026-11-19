package io.embodit.musicbox.platform

import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.pico.spatial.ui.foundation.dsl.launch
import com.pico.spatial.ui.platform.stub.SpatialLaunchActivity
import io.embodit.musicbox.data.Presets
import io.embodit.musicbox.data.SettingsStore
import io.embodit.musicbox.data.SongStore
import io.embodit.musicbox.domain.BoxState
import io.embodit.musicbox.domain.Drum
import io.embodit.musicbox.domain.MusicBox
import io.embodit.musicbox.domain.Notice
import io.embodit.musicbox.domain.PreviewPlan
import io.embodit.musicbox.domain.Settings
import io.embodit.musicbox.domain.Sfx
import io.embodit.musicbox.domain.Song
import io.embodit.musicbox.domain.Volume
import io.embodit.musicbox.mainApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

const val TAG = "MusicBox"

class LaunchActivity : SpatialLaunchActivity()

class SpatialApplication : Application() {
    lateinit var graph: Graph
        private set

    override fun onCreate() {
        super.onCreate()
        io.embodit.musicbox.ui.board.input.BoardInputLog.attach(this)
        graph = Graph(this)
        launch(::mainApp)
    }
}

/** The process-wide objects (native-window-move.md §3.4: state shared by the volume and the window lives here). */
class Graph(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings = SettingsStore(context)
    val sounds = SoundPlayer(context)
    val songStore = SongStore(context)
    val window = WindowCtl()
    val box = BoxCtl(Presets.load(context), songStore, settings, sounds)
    val preview = Preview(sounds)

    init {
        scope.launch {
            settings.settings.collect { s ->
                sounds.muted = s.muted
                sounds.gain = Volume.gain(s.volume)
                box.applySettings(s)
            }
        }
        box.start()
        // the box and a preview never sound together: a pluck from the drum ends the preview (Preview.start stops the drum)
        box.onPluck = { preview.stop("drum plays") }
    }

    companion object {
        fun from(context: Context): Graph = (context.applicationContext as SpatialApplication).graph
    }
}

/** What the control window shows (published by the simulation thread when it changes). */
data class BoxUi(
    val state: BoxState = BoxState.LOADING,
    val notice: Notice? = null,
    val song: Song? = null,
    val mine: List<Song> = emptyList(),
    val presets: List<Song> = emptyList(),
    val cells: Int = 0,
    val gaugeFlash: Boolean = false,
    val canUndo: Boolean = false,
    val canEdit: Boolean = false,
    val col: Int = 0,
    val nearHint: Boolean = true,
    val coachPin: Boolean = false,
)

/**
 * Owns the [MusicBox] and the simulation thread (design D55: the drum, the spring and the sounds run on wall-clock time
 * in their own thread, so a 10 fps volume never slows the music). Every action locks the box, runs, then plays the
 * sounds it queued right away (tap -> sound with no frame in between, design sound.latency).
 */
class BoxCtl(presets: List<Song>, private val store: SongStore, private val settings: SettingsStore, private val sounds: SoundPlayer) {
    val box = MusicBox(presets, store.load(), settings.value.lastSongId, wallClock = { System.currentTimeMillis() })
    private val _ui = MutableStateFlow(BoxUi(presets = presets))
    val ui: StateFlow<BoxUi> = _ui.asStateFlow()
    private val pending = ArrayList<Sfx>()
    private var savedRevision = 0
    private var dirtyAt = 0L
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    @Volatile var nearHint = true
        private set
    @Volatile var panelOpen = true
    var onPanelLine: (() -> Unit)? = null
    /** Called (on the sim thread) when the drum plucks a tine: ends a running preview. */
    @Volatile var onPluck: (() -> Unit)? = null
    private val io = Handler(Looper.getMainLooper())
    private var lastUnderLog = 0L

    fun start() {
        val th = HandlerThread("box-sim", Process.THREAD_PRIORITY_DISPLAY).also { it.start() }
        thread = th
        val h = Handler(th.looper)
        handler = h
        var last = System.nanoTime()
        val tick = object : Runnable {
            override fun run() {
                val now = System.nanoTime()
                // gate:allow low-fps-slowmo not a frame clamp: the sim thread only stalls when the process is paused
                val dt = ((now - last) / 1e9).coerceIn(0.0, 0.5)
                last = now
                act("tick", log = false) { it.advance(dt) }
                h.postDelayed(this, 4)
            }
        }
        h.post(tick)
    }

    /** Runs [block] on the box under its lock, then plays due sounds, publishes the UI state and schedules a save. */
    fun <T> act(why: String, log: Boolean = true, block: (MusicBox) -> T): T {
        val r: T
        val due = ArrayList<Sfx>()
        synchronized(box) {
            r = block(box)
            pending += box.sfx; box.sfx.clear()
            val it = pending.iterator()
            while (it.hasNext()) { val s = it.next(); if (s.at <= box.t + 1e-6) { due += s; it.remove() } }
            if (box.revision != savedRevision) { savedRevision = box.revision; dirtyAt = SystemClock.uptimeMillis() }
            publish()
        }
        if (due.any { it.kind == Sfx.Kind.TINE && !it.soft }) onPluck?.invoke()
        for (s in due) sounds.play(s)
        if (log) Log.i(TAG, "ACT $why -> ${box.state().id} col=${box.logicalCol()} spring=${"%.1f".format(box.spring)} pins=${box.pins.size}")
        if (dirtyAt > 0 && SystemClock.uptimeMillis() - dirtyAt > 500) { dirtyAt = 0; saveNow() }
        return r
    }

    /** A hit on any 3D part ends the near hint (design stateModifiers.nearHint). */
    fun touched3d() { if (nearHint) { nearHint = false; Log.i(TAG, "NEARHINT off") } }

    fun debugNearHint(on: Boolean) { nearHint = on }

    private fun publish() {
        val b = box
        val st = b.state()
        val s = settings.value
        val coachPin = !s.coachDone && b.songsPlayedToEnd > 0
        val next = BoxUi(
            state = st, notice = b.activeNotice(), song = b.song, mine = b.mine, presets = b.presets,
            cells = b.gaugeCells(), gaugeFlash = b.t - b.gaugeFlashAt < 0.4, canUndo = b.canUndo, canEdit = b.canEdit,
            col = b.logicalCol(), nearHint = nearHint, coachPin = coachPin,
        )
        if (next != _ui.value) _ui.value = next
    }

    fun saveNow() {
        val (mine, id) = synchronized(box) { box.mine to box.song.id }
        Thread { store.save(mine) }.start()
        if (settings.value.lastSongId != id) io.post { settings.update { it.copy(lastSongId = id) } }
        Log.i(TAG, "SAVE ${mine.size} tunes, current=$id")
    }

    fun applySettings(s: Settings) { act("settings", log = false) { it.speed = s.speed.toDouble() } }

    fun onBackground() { act("background") { it.onBackground() }; saveNow() }

    fun stop() { handler?.removeCallbacksAndMessages(null); thread?.quitSafely() }

    /** Coach bookkeeping: the first pin after the coach line ends the coach (design stateModifiers.coach). */
    fun coachCheck() {
        val s = settings.value
        if (!s.coachDone && box.songsPlayedToEnd > 0 && box.lastPinAt > 0 && box.t - box.lastPinAt < 1.0) settings.update { it.copy(coachDone = true) }
    }

    @Suppress("unused") fun pinsCount() = synchronized(box) { box.pins.size.coerceAtMost(Drum.PIN_CAP) }
}

/**
 * Real recordings through a SoundPool (design budget.audio: 8 tine voices + mechanical). D51 / B20: the same tine played
 * again fades the old voice in 10 ms before the new one starts (a real tine is stopped by the next pin); different tines
 * never cut each other; every pluck plays at the same level (no "later sounds quieter" limiter: 10 plucks in a row peak
 * within 1 dB). Musical tines have no random pitch (tuning <= 3 cents); the ratchet clicks rotate 3 recordings +-3 %.
 */
class SoundPlayer(context: Context) {
    @Volatile var muted = false
    @Volatile var gain = 1f

    private val pool = SoundPool.Builder().setMaxStreams(16)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        .build()
    private val tine = IntArray(8)
    private val clicks = ArrayList<Int>()
    private val other = HashMap<Sfx.Kind, Int>()
    private val loaded = HashSet<Int>()
    private val tineStream = IntArray(8)
    private val main = Handler(Looper.getMainLooper())
    private val rnd = java.util.Random(7)
    @Volatile var lastPlayMs = 0L
        private set

    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) synchronized(loaded) { loaded += id } }
        fun load(name: String): Int = runCatching { context.assets.openFd("sounds/$name.wav").use { pool.load(it, 1) } }
            .onFailure { Log.w(TAG, "no sound $name") }.getOrDefault(0)
        for (i in 0 until 8) tine[i] = load("tine_${i + 1}")
        listOf("click", "click_2", "click_3").forEach { clicks += load(it) }
        other[Sfx.Kind.LID_OPEN] = load("lid_open")
        other[Sfx.Kind.LID_CLOSE] = load("lid_close")
        other[Sfx.Kind.STOP] = load("stop")
        other[Sfx.Kind.PIN] = load("pin")
    }

    private fun ok(id: Int) = id != 0 && synchronized(loaded) { id in loaded }

    fun play(s: Sfx) {
        if (muted) return
        val g = gain
        when (s.kind) {
            Sfx.Kind.TINE -> {
                val r = s.tine.coerceIn(0, 7)
                val id = tine[r]
                if (!ok(id)) return
                val old = tineStream[r]
                if (old != 0) { pool.setVolume(old, 0.15f * g, 0.15f * g); main.postDelayed({ pool.stop(old) }, 10) }
                val v = if (s.soft) 0.4f * g else g
                val st = pool.play(id, v, v, 2, 0, 1f)
                tineStream[r] = st
                lastPlayMs = SystemClock.uptimeMillis()
                if (s.soft && st != 0) main.postDelayed({ pool.setVolume(st, 0.1f * g, 0.1f * g); main.postDelayed({ pool.stop(st) }, 30) }, 150)
            }
            Sfx.Kind.CLICK -> {
                val id = clicks[rnd.nextInt(clicks.size)]
                if (ok(id)) pool.play(id, 0.8f * g, 0.8f * g, 1, 0, 0.97f + rnd.nextFloat() * 0.06f)
            }
            else -> other[s.kind]?.takeIf { ok(it) }?.let { pool.play(it, g, g, 1, 0, 1f) }
        }
    }

    fun release() = runCatching { pool.release() }
}

/**
 * [试听] on the Tunes list (v0.1.2): plays the opening of a tune (about 8 s, at most the whole tune) through the same
 * [SoundPlayer] voices as the drum, without touching the drum, the spring or the tune on it. One preview at a time; a
 * second tap, another row, leaving the Tunes page, the drum starting to play, or the app going to the background ends it.
 * The notes are timed on their own audio-priority thread by the wall clock (postAtTime), so a 10 fps panel never slows it.
 */
class Preview(private val sounds: SoundPlayer) {
    private val _playing = MutableStateFlow<String?>(null)
    val playing: StateFlow<String?> = _playing.asStateFlow()
    private val thread = HandlerThread("preview", Process.THREAD_PRIORITY_URGENT_AUDIO).also { it.start() }
    private val h = Handler(thread.looper)
    private val token = Any()

    /** The column times of a preview: the columns of the first [maxS] seconds at the tune's own bpm, no more than its length. */
    fun plan(song: Song): List<Pair<Double, List<Int>>> = PreviewPlan.of(song)

    fun toggle(song: Song) { if (_playing.value == song.id) stop("tap again") else start(song) }

    fun start(song: Song) {
        stop(null)
        val plan = plan(song)
        if (plan.isEmpty()) return
        _playing.value = song.id
        Log.i(TAG, "PREVIEW start ${song.id} notes=${plan.sumOf { it.second.size }} for ${"%.1f".format(plan.last().first)} s")
        val t0 = SystemClock.uptimeMillis() + 60
        for ((at, tines) in plan) {
            h.postAtTime({ for (t in tines) sounds.play(Sfx(Sfx.Kind.TINE, tine = t)) }, token, t0 + (at * 1000).toLong())
        }
        val end = plan.last().first + 1.2
        h.postAtTime({ if (_playing.value == song.id) { _playing.value = null; Log.i(TAG, "PREVIEW end ${song.id}") } }, token, t0 + (end * 1000).toLong())
    }

    fun stop(why: String?) {
        h.removeCallbacksAndMessages(token)
        val was = _playing.value
        if (was != null) { _playing.value = null; if (why != null) Log.i(TAG, "PREVIEW stop $was ($why)") }
    }
}
