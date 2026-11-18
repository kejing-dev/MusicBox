package io.embodit.musicbox.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.embodit.musicbox.domain.Finish
import io.embodit.musicbox.domain.Language
import io.embodit.musicbox.domain.MusicBox
import io.embodit.musicbox.domain.Pin
import io.embodit.musicbox.ui.Confirm
import io.embodit.musicbox.ui.Page
import io.embodit.musicbox.ui.Ui
import io.embodit.musicbox.ui.board.BoxNames
import io.embodit.musicbox.ui.board.BoxScene
import io.embodit.musicbox.ui.board.BoxTextures
import io.embodit.musicbox.ui.board.BoxTargets
import io.embodit.musicbox.ui.board.SceneHolder
import io.embodit.musicbox.ui.board.input.BoardInputLog
import io.embodit.musicbox.ui.board.input.resolveUp
import io.embodit.musicbox.ui.board.routeTap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Debug builds only. Taps and drags go through the same routeTap / box calls as the real input (D29); `goto` builds a
 * named state from scratch (it starts with `reset`, so screenshots never inherit the last state, build.md lesson).
 *
 *   adb shell am broadcast -a io.embodit.musicbox.DEBUG -p io.embodit.musicbox --es cmd <cmd> [--es a "<arg>"]
 *
 *   reset                         stop, twinkle, lid closed, spring 0, column 0, panel on Play, no confirm, unfrozen
 *   goto B1|B2|B3|B4|B4r|B5|B6|B6fork|B6full|B7|B8|B9|P2|P2mine|P2mine1..5|P2full|P3|P4clear|P4delete|P4quit|P5|P5open|HOW|EDIT|EDITclosed
 *   tap lid|key|nameplate|drum|tine N      a pinch on that part          drag key|drum <cm>   (+ = down)
 *   spring <0..1> · song <id> · speed <0.5..1.5> · col N · pins "c.t;c.t" (t = 1..8) · windplay · stop · back · rest · undo
 *   hold <ms>|off (freeze the box N ms from now) · lang zh|en|system · page play|songs|settings · finish walnut|maple|red
 *   edit on|off (pin-edit keys on the Play page) · mute on|off · volume 0..2 · coach on|off · nearhint on|off · confirm clear|quit · yes · no · newsong · closepanel
 *   orient on|off (F↑ test textures) · b0hold on|off · replay "<holder>" · dump · evidence
 */
class DebugCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val graph = Graph.from(context)
        val ctl = graph.box
        val cmd = intent.getStringExtra("cmd").orEmpty().trim()
        val a = intent.getStringExtra("a").orEmpty().trim().trim('"')
        val on = a == "on" || a == "true" || a == "1"
        val f = a.split(" ").filter { it.isNotBlank() }
        val scene: BoxScene? = SceneHolder.scene
        Log.i(TAG, "CMD $cmd $a")
        fun later(ms: Long, block: () -> Unit) { graph.scope.launch { delay(ms); block() } }
        fun hold(ms: Long) = ctl.act("hold $ms") { it.freezeAt = it.t + ms / 1000.0 }
        fun reset() {
            ctl.act("reset") { b ->
                b.frozen = false; b.freezeAt = Double.MAX_VALUE
                b.stop(); b.selectSong("twinkle", animate = false); b.debugLidInstant(false); b.debugSetPos(0)
            }
            Ui.page = Page.PLAY; Ui.confirm = null; Ui.howTo = false; Ui.songsTab = 0; Ui.editPins = false
            ctl.debugNearHint(false)
            if (!graph.window.panelOpen) graph.window.open("reset")
        }
        fun open() = ctl.act("lid open") { it.debugLidInstant(true) }
        fun tap(name: String) = routeTap(graph, scene, name)
        fun drag(part: String, cm: Float, steps: Int = 12) {
            graph.scope.launch {
                ctl.act("drag $part start") { if (part == "key") it.keyDragStart() else it.scrubStart() }
                for (i in 1..steps) {
                    val down = cm / 100.0 * i / steps
                    ctl.act("drag $part", log = false) { if (part == "key") it.keyDragMove(down) else it.scrubMove(down / (scene?.contentScale ?: 1.28f)) }
                    delay(30)
                }
            }
        }
        fun dragEnd(part: String) = ctl.act("drag $part end") { if (part == "key") it.keyDragEnd() else it.endScrub() }
        fun mine(n: Int) {
            ctl.act("mine $n") { b ->
                while (b.mine.size > n) b.deleteSong(b.mine.last().id)
                while (b.mine.size < n) b.newSong()
                b.selectSong("twinkle", animate = false)
            }
        }
        when (cmd) {
            "reset" -> reset()
            "goto" -> {
                reset()
                when (a) {
                    "B1" -> Unit
                    "B1near" -> ctl.debugNearHint(true)
                    "B2" -> open()
                    "B3" -> { open(); later(700) { drag("key", 3f) }; later(1300) { hold(0) } }
                    "B4" -> { open(); ctl.act("goto B4") { it.windAndPlay(); it.freezeAt = it.t + MusicBox.REWIND_S + 8 * 60.0 / it.bpm / it.speed + 0.04 } }
                    "B4r" -> { open(); ctl.act("goto B4r") { it.debugSetPos(40); it.setSpringFraction(6.0 / 64) }; later(300) { hold(700) } }
                    "B5" -> { open(); later(600) { drag("drum", 3f) }; later(1100) { hold(0) } }
                    "B6" -> { open(); Ui.editPins = true; ctl.act("goto B6") { b -> b.selectSong(b.mine.firstOrNull()?.id ?: b.newSong()!!.id) }; later(700) { tap("tine:4"); hold(60) } }
                    "B6fork" -> { mine(0); open(); Ui.editPins = true; later(700) { ctl.act("goto fork") { it.debugSetPos(48) }; tap("tine:0"); hold(300) } }
                    "B6full" -> { mine(6); open(); Ui.editPins = true; later(700) { ctl.act("goto full") { it.debugSetPos(48) }; tap("tine:0"); hold(300) } }
                    "B7" -> { ctl.act("goto B7") { it.setSpringFraction(5.0 / 8) } }
                    "B8" -> { open(); later(700) { ctl.act("goto B8") { it.selectSong("ode"); it.freezeAt = it.t + 0.1 } } }
                    "B9" -> { open(); ctl.act("goto B9") { it.debugSetPos(20) }; later(700) { ctl.act("goto B9b") { it.windAndPlay(); it.freezeAt = it.t + 0.15 } } }
                    "P2" -> { open(); Ui.page = Page.SONGS }
                    "P2mine" -> { mine(0); open(); Ui.page = Page.SONGS; Ui.songsTab = 1 }
                    "P2mine1", "P2mine2", "P2mine3", "P2mine4", "P2mine5" -> { mine(a.last() - '0'); open(); Ui.page = Page.SONGS; Ui.songsTab = 1 }
                    "EDIT" -> { open(); Ui.editPins = true }
                    "EDITclosed" -> { Ui.editPins = true }
                    "P2full" -> { mine(6); open(); Ui.page = Page.SONGS; Ui.songsTab = 1 }
                    "P3" -> { open(); Ui.page = Page.SETTINGS }
                    "HOW" -> { open(); Ui.page = Page.SETTINGS; Ui.howTo = true }
                    "P4clear" -> { open(); Ui.confirm = Confirm.Clear }
                    "P4delete" -> { if (ctl.box.mine.isEmpty()) mine(1); open(); Ui.page = Page.SONGS; Ui.songsTab = 1; Ui.confirm = Confirm.Delete(ctl.box.mine.first().id) }
                    "P4quit" -> { open(); Ui.page = Page.SETTINGS; Ui.confirm = Confirm.Quit }
                    "P5" -> graph.window.closeControlOnly()
                    "P5open" -> { open(); graph.window.closeControlOnly() }
                    else -> Log.w(TAG, "CMD goto: unknown state $a")
                }
            }
            "tap" -> tap(if (f.firstOrNull() == "tine") "tine:${f.getOrNull(1)}" else a)
            "drag" -> { val part = f.getOrNull(0) ?: "key"; val cm = f.getOrNull(1)?.toFloatOrNull() ?: 3f; drag(part, cm); later(12 * 30 + 200L) { dragEnd(part) } }
            "spring" -> ctl.act("spring") { it.setSpringFraction(a.toDoubleOrNull() ?: 0.0) }
            "song" -> ctl.act("song") { it.selectSong(a) }
            "speed" -> graph.settings.update { it.copy(speed = (a.toFloatOrNull() ?: 1f).coerceIn(0.5f, 1.5f)) }
            "col" -> ctl.act("col") { it.debugSetPos(a.toIntOrNull() ?: 0) }
            "pins" -> ctl.act("pins") { b -> b.debugSetPins(a.split(";").filter { "." in it }.map { val (c, t) = it.split("."); Pin(c.toInt(), t.toInt() - 1) }.toSet()) }
            "windplay" -> ctl.act("windplay") { it.windAndPlay() }
            "stop" -> ctl.act("stop") { it.stop() }
            "back" -> ctl.act("back") { it.back() }
            "rest" -> ctl.act("rest") { it.rest() }
            "undo" -> ctl.act("undo") { it.undo() }
            "hold" -> if (a == "off") ctl.act("unhold") { it.frozen = false; it.freezeAt = Double.MAX_VALUE } else hold(a.toLongOrNull() ?: 0)
            "lang" -> graph.settings.update { it.copy(language = when (a) { "zh" -> Language.ZH; "en" -> Language.EN; else -> Language.SYSTEM }) }
            "edit" -> Ui.editPins = on
            "page" -> Ui.page = when (a) { "songs" -> Page.SONGS; "settings" -> Page.SETTINGS; else -> Page.PLAY }
            "finish" -> graph.settings.update { it.copy(finish = when (a) { "maple" -> Finish.MAPLE; "red" -> Finish.RED; else -> Finish.WALNUT }) }
            "mute" -> graph.settings.update { it.copy(muted = on) }
            "volume" -> graph.settings.update { it.copy(volume = (a.toIntOrNull() ?: 2).coerceIn(0, 2)) }
            "coach" -> { graph.settings.update { it.copy(coachDone = !on) }; if (on) scene?.coachLidPulseAt = android.os.SystemClock.uptimeMillis() }
            "nearhint" -> ctl.debugNearHint(on)
            "confirm" -> Ui.confirm = if (a == "quit") { Ui.page = Page.SETTINGS; Confirm.Quit } else Confirm.Clear
            "no" -> Ui.confirm = null
            "yes" -> when (val c = Ui.confirm) {
                Confirm.Clear -> { ctl.act("clear") { it.clear() }; Ui.confirm = null }
                is Confirm.Delete -> { ctl.act("delete") { it.deleteSong(c.id) }; Ui.confirm = null }
                Confirm.Quit -> io.embodit.musicbox.ui.control.exitApp(context, graph)
                null -> Unit
            }
            "newsong" -> ctl.act("newsong") { it.newSong() }
            "closepanel" -> graph.window.closeControlOnly()
            "orient" -> { BoxTextures.testF = on; scene?.refreshOrientationTest() }
            "b0hold" -> context.getSharedPreferences("debug", Context.MODE_PRIVATE).edit().putBoolean("b0hold", on).apply()
            "replay" -> {
                val holder = f.firstOrNull() ?: ""
                val hits = scene?.replayEntities(holder).orEmpty()
                if (hits.isEmpty()) BoardInputLog.write(TAG, "REPLAY $holder: no such holder")
                for (e in hits) {
                    val (hit, path) = resolveUp(e, BoxTargets(), 10)
                    BoardInputLog.write(TAG, "REPLAY from=${runCatching { e.getName() }.getOrNull()} hit=$hit path=$path part=${BoxNames.hitOf(hit)}")
                }
                if (hits.isNotEmpty()) tap(holder)
            }
            "dump", "evidence" -> {
                val b = ctl.box
                synchronized(b) {
                    Log.i(TAG, "DUMP state=${b.state().id} song=${b.song.id} pins=${b.pins.size} L=${b.length} pos=${"%.2f".format(b.pos)} spring=${"%.2f".format(b.spring)} " +
                        "lid=${"%.2f".format(b.lidAmt)} dancer=${"%.2f".format(b.dancerAmt)} mine=${b.mine.size} undo=${b.undoDepth} frozen=${b.frozen} panel=${graph.window.panelState} near=${ctl.nearHint}")
                }
                scene?.let { Log.i(TAG, "EVIDENCE ${it.evidence()}") }
            }
            else -> Log.w(TAG, "CMD unknown $cmd")
        }
    }
}
