package io.embodit.musicbox.ui.control

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pico.spatial.ui.design.CircularProgressIndicator
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.windows.TabBar
import com.pico.spatial.ui.platform.ViewPoint
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import io.embodit.musicbox.BuildConfig
import io.embodit.musicbox.R
import io.embodit.musicbox.domain.BoxState
import io.embodit.musicbox.domain.Drum
import io.embodit.musicbox.domain.Finish
import io.embodit.musicbox.domain.Language
import io.embodit.musicbox.domain.Lines
import io.embodit.musicbox.domain.Settings
import io.embodit.musicbox.domain.Song
import io.embodit.musicbox.platform.BoxUi
import io.embodit.musicbox.platform.Graph
import io.embodit.musicbox.platform.TAG
import io.embodit.musicbox.ui.Confirm
import io.embodit.musicbox.ui.Page
import io.embodit.musicbox.ui.Ui
import io.embodit.musicbox.ui.board.SceneHolder
import io.embodit.musicbox.ui.lineRes
import io.embodit.musicbox.ui.theme.MBColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * The control window (Planar WindowContainer beside the box, 560 x 600 dp): all 2D UI, three pages switched by the
 * native SpatialUI TabBar (D8): Play / Tunes / Settings. No page scrolls (D44); each page spreads its rows between the
 * header and the last row, so the blank outside the content is only the window padding. Confirmations unfold in place (P4).
 */
@Composable
fun ControlWindow() {
    val context = LocalContext.current
    val graph = remember(context) { Graph.from(context) }
    val ui by graph.box.ui.collectAsStateWithLifecycle()
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    val nav = LocalSpatialNavigator.current
    DisposableEffect(owner, nav) {
        graph.window.attachPanel(nav)
        val obs = LifecycleEventObserver { _, e -> graph.window.onPanelLifecycle(e.name) }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(Ui.page) {
        Log.i(TAG, "PAGE ${Ui.page} ${Ui.page.stateId}")
        if (Ui.page != Page.SETTINGS) Ui.howTo = false
        if (Ui.confirm is Confirm.Quit && Ui.page != Page.SETTINGS) Ui.confirm = null
    }

    Box(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp)) {
        val c = Ui.confirm
        when {
            ui.state == BoxState.LOADING -> LoadingPage(settings, graph)
            c != null -> ConfirmPage(c, ui, settings, graph)
            Ui.page == Page.PLAY -> PlayPage(ui, settings, graph)
            Ui.page == Page.SONGS -> SongsPage(ui, settings, graph)
            Ui.howTo -> HowToPage(settings, graph)
            else -> SettingsPage(settings, graph)
        }
    }

    // The native TabBar is its own system window: resolve the labels here, in the app-language context (ui_gates tabbar-lang).
    val tabs = listOf(
        Triple(Page.PLAY, R.drawable.ic_tab_play, stringResource(R.string.tab_play)),
        Triple(Page.SONGS, R.drawable.ic_tab_songs, stringResource(R.string.tab_songs)),
        Triple(Page.SETTINGS, R.drawable.ic_tab_settings, stringResource(R.string.tab_settings)),
    )
    NativeTabs(tabs)
}

@Composable
private fun PageColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween, content = content)
}

@Composable
internal fun isZh(): Boolean = stringResource(R.string.lang_code) == "zh"

@Composable
private fun MuteButton(settings: Settings, graph: Graph, tag: String) {
    ToggleButton(stringResource(if (settings.muted) R.string.btn_muted else R.string.btn_mute), "$tag/mute", on = settings.muted,
        onClick = { graph.settings.update { it.copy(muted = !it.muted) } })
}

/** Page header (64 dp): a title (+ a small line) and [静音] (D15: mute is one glance away; B31 exception). */
@Composable
private fun Header(title: String, sub: String?, tag: String, settings: Settings, graph: Graph) {
    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Txt(title, "$tag/title", style = PicoTheme.typography.titleLarge)
            if (sub != null) Txt(sub, "$tag/sub", style = PicoTheme.typography.bodySmall, color = PicoTheme.colorScheme.labelSecondary)
        }
        Spacer(Modifier.width(12.dp))
        MuteButton(settings, graph, tag)
    }
}

// ------------------------------------------------------------------ B0

@Composable
private fun LoadingPage(settings: Settings, graph: Graph) {
    PageColumn {
        Header(stringResource(R.string.app_name), null, "loading", settings, graph)
        Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Txt(stringResource(R.string.line_loading), "loading/line", style = PicoTheme.typography.titleMedium)
        }
    }
}

// ------------------------------------------------------------------ P1 Play

@Composable
private fun PlayPage(ui: BoxUi, settings: Settings, graph: Graph) {
    val zh = isZh()
    val song = ui.song
    val key = Lines.key(Lines.Ctx(ui.state, ui.notice, true, ui.nearHint, ui.coachPin))
    val block = Lines.editBlockKey(ui.state)
    val playing = ui.state.playing || ui.state == BoxState.REWINDING || ui.state == BoxState.SCRUB
    PageColumn {
        Header(song?.name(zh) ?: "", song?.tag(zh)?.takeIf { it.isNotEmpty() }, "play", settings, graph)
        // the status sentence: the same line as the lid card (D38); with the near hint, the state line follows
        Column(Modifier.fillMaxWidth().height(104.dp).clip(RoundedCornerShape(24.dp)).background(MBColors.TagFace).padding(horizontal = 20.dp), verticalArrangement = Arrangement.Center) {
            Txt(stringResource(lineRes(key)), "play/status", style = PicoTheme.typography.titleMedium, color = MBColors.Ink, maxLines = 2)
            if (key == "nearHint") Txt(stringResource(lineRes(Lines.stateKey(ui.state))), "play/status2", style = PicoTheme.typography.bodyMedium, color = MBColors.InkSecondary, maxLines = 1)
        }
        if (playing || ui.state == BoxState.LID_PAUSED) {
            PrimaryButton(stringResource(R.string.btn_stop), "play/stop", icon = R.drawable.ic_stop, onClick = { graph.box.act("btn stop") { it.stop() } }, modifier = Modifier.fillMaxWidth())
        } else {
            PrimaryButton(stringResource(R.string.btn_wind_play), "play/wind", icon = R.drawable.ic_play, enabled = ui.state != BoxState.SWAPPING,
                onClick = { graph.box.act("btn wind&play") { it.windAndPlay() } }, modifier = Modifier.fillMaxWidth())
        }
        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt(stringResource(R.string.spring_label), "play/spring", style = PicoTheme.typography.labelLarge, modifier = Modifier.width(96.dp))
            SpringCells(ui.cells, ui.gaugeFlash, Modifier.weight(1f))
        }
        OptionRow(stringResource(R.string.speed_label), "play/speed", labelWidth = 96.dp) {
            SpeedSlider(settings.speed, onChange = { v -> graph.settings.update { it.copy(speed = v) } }, modifier = Modifier.weight(1f))
        }
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SecondaryButton(stringResource(R.string.btn_back), "play/back", onClick = { graph.box.act("btn back") { it.back() } }, enabled = ui.canEdit, modifier = Modifier.weight(1f))
                SecondaryButton(stringResource(R.string.btn_rest), "play/rest", onClick = { graph.box.act("btn rest") { it.rest() } }, enabled = ui.canEdit, modifier = Modifier.weight(1f))
                SecondaryButton(stringResource(R.string.btn_undo), "play/undo", onClick = { graph.box.act("btn undo") { it.undo() } }, enabled = ui.canUndo, modifier = Modifier.weight(1f))
                SecondaryButton(stringResource(R.string.btn_clear), "play/clear", onClick = { Ui.confirm = Confirm.Clear; Log.i(TAG, "CONFIRM clear P4") },
                    enabled = ui.canEdit && (song?.pins?.isNotEmpty() == true), modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            val caption = if (block != null) stringResource(lineRes(block)) else stringResource(R.string.pins_line, song?.pins?.size ?: 0, ui.col + 1, song?.length ?: 16)
            Txt(caption, "play/caption", style = PicoTheme.typography.bodySmall, color = PicoTheme.colorScheme.labelSecondary, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

// ------------------------------------------------------------------ P2 Tunes

@Composable
private fun SongsPage(ui: BoxUi, settings: Settings, graph: Graph) {
    val zh = isZh()
    val mineTab = Ui.songsTab == 1
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt(stringResource(R.string.songs_label), "songs/label", style = PicoTheme.typography.titleLarge, modifier = Modifier.width(84.dp))
            Segments(listOf(0 to stringResource(R.string.seg_presets), 1 to stringResource(R.string.seg_mine)), Ui.songsTab, "songs/seg", { Ui.songsTab = it }, Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            if (mineTab) {
                val full = ui.mine.size >= Drum.MY_CAP
                PrimaryButton(stringResource(if (full) R.string.new_full else R.string.btn_new_tune), "songs/new", icon = R.drawable.ic_add, enabled = !full,
                    onClick = { graph.box.act("new tune") { it.newSong() } })
            } else MuteButton(settings, graph, "songs")
        }
        if (!mineTab) {
            Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.SpaceBetween) {
                ui.presets.forEach { s -> SongRow(s, zh, s.id == ui.song?.id, sub = "${s.tag(zh)} · ${s.lic(zh)}", graph = graph, onDelete = null) }
            }
        } else if (ui.mine.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Txt(stringResource(R.string.mine_empty), "songs/empty", style = PicoTheme.typography.titleMedium, color = MBColors.InkSecondary, maxLines = 3, align = TextAlign.Center)
            }
        } else {
            val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.US) }
            Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = if (ui.mine.size >= 4) Arrangement.SpaceBetween else Arrangement.Center) {
                ui.mine.forEachIndexed { i, s ->
                    if (i > 0 && ui.mine.size < 4) Spacer(Modifier.height(16.dp))
                    SongRow(s, zh, s.id == ui.song?.id, sub = stringResource(R.string.mine_line, s.pins.size, fmt.format(Date(s.updatedAt))), graph = graph,
                        onDelete = { Ui.confirm = Confirm.Delete(s.id); Log.i(TAG, "CONFIRM delete ${s.id} P4") })
                }
            }
        }
    }
}

@Composable
private fun SongRow(s: Song, zh: Boolean, current: Boolean, sub: String, graph: Graph, onDelete: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(20.dp)).background(if (current) MBColors.PrimaryContainer else MBColors.TagFace)
                .clickable { Log.i(TAG, "SONG pick ${s.id}"); graph.box.act("song ${s.id}") { it.selectSong(s.id) }; graph.box.saveNow() }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (current) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(MBColors.Primary))
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Txt(s.name(zh), "songs/name", style = PicoTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = MBColors.Ink)
                Txt(sub, "songs/sub", style = PicoTheme.typography.bodySmall, color = MBColors.InkSecondary)
            }
            if (current) Txt(stringResource(R.string.now_playing), "songs/current", style = PicoTheme.typography.labelMedium, color = MBColors.Primary)
        }
        if (onDelete != null) {
            Spacer(Modifier.width(16.dp))
            SecondaryButton(stringResource(R.string.btn_delete), "songs/delete", icon = R.drawable.ic_delete, onClick = onDelete, modifier = Modifier.width(128.dp))
        }
    }
}

// ------------------------------------------------------------------ P3 Settings

@Composable
private fun SettingsPage(settings: Settings, graph: Graph) {
    fun upd(change: (Settings) -> Settings) { graph.settings.update(change) }
    PageColumn {
        Header(stringResource(R.string.settings_title), null, "settings", settings, graph)
        OptionRow(stringResource(R.string.settings_language), "settings/lang", labelWidth = 104.dp) {
            Segments(listOf(Language.SYSTEM to stringResource(R.string.lang_system), Language.ZH to stringResource(R.string.lang_zh), Language.EN to stringResource(R.string.lang_en)),
                settings.language, "settings/lang", { l -> upd { it.copy(language = l) } }, Modifier.weight(1f))
        }
        OptionRow(stringResource(R.string.settings_volume), "settings/volume", labelWidth = 104.dp) {
            VolumeSlider(settings.volume, onChange = { v -> upd { it.copy(volume = v) } }, modifier = Modifier.weight(1f))
        }
        OptionRow(stringResource(R.string.settings_finish), "settings/finish", labelWidth = 104.dp) {
            Segments(listOf(Finish.WALNUT to stringResource(R.string.finish_walnut), Finish.MAPLE to stringResource(R.string.finish_maple), Finish.RED to stringResource(R.string.finish_red)),
                settings.finish, "settings/finish", { f -> upd { it.copy(finish = f) } }, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SecondaryButton(stringResource(R.string.settings_how), "settings/how", icon = R.drawable.ic_help, onClick = { Ui.howTo = true }, modifier = Modifier.weight(1f))
            SecondaryButton(stringResource(R.string.settings_coach), "settings/coach", icon = R.drawable.ic_replay, onClick = {
                upd { it.copy(coachDone = false) }
                SceneHolder.scene?.let { it.coachLidPulseAt = android.os.SystemClock.uptimeMillis(); it.coachGlowAt = -1L }
                graph.box.debugNearHint(true)
            }, modifier = Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt(stringResource(R.string.settings_about_label), "settings/about-label", style = PicoTheme.typography.labelLarge, modifier = Modifier.width(104.dp))
            Txt(stringResource(R.string.settings_about_text, BuildConfig.VERSION_NAME), "settings/about", style = PicoTheme.typography.bodySmall, color = PicoTheme.colorScheme.labelSecondary, maxLines = 2, modifier = Modifier.weight(1f))
        }
        // D26: quit is always on the first screen of settings, last row; the confirmation unfolds in place
        SecondaryButton(stringResource(R.string.settings_exit), "settings/exit", onClick = { Ui.confirm = Confirm.Quit; Log.i(TAG, "CONFIRM quit P4") }, icon = R.drawable.ic_exit, modifier = Modifier.fillMaxWidth(), danger = true)
    }
}

/** B19: one line per main action (gesture · controller). */
@Composable
private fun HowToPage(settings: Settings, graph: Graph) {
    PageColumn {
        Header(stringResource(R.string.how_to_title), null, "how", settings, graph)
        listOf(R.string.how_to_lid, R.string.how_to_pin, R.string.how_to_wind, R.string.how_to_scrub, R.string.how_to_panel, R.string.how_to_move).forEachIndexed { i, id ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(MBColors.Primary), contentAlignment = Alignment.Center) {
                    Txt("${i + 1}", "how/n", style = PicoTheme.typography.labelMedium, color = MBColors.OnPrimary)
                }
                Spacer(Modifier.width(12.dp))
                Txt(stringResource(id), "how/line", style = PicoTheme.typography.bodyMedium, maxLines = 2, modifier = Modifier.weight(1f))
            }
        }
        SecondaryButton(stringResource(R.string.btn_close_how), "how/back", onClick = { Ui.howTo = false }, modifier = Modifier.fillMaxWidth())
    }
}

// ------------------------------------------------------------------ P4 Confirm

@Composable
private fun ConfirmPage(c: Confirm, ui: BoxUi, settings: Settings, graph: Graph) {
    val context = LocalContext.current
    val zh = isZh()
    val (q, line, yes, icon) = when (c) {
        Confirm.Clear -> listOf(stringResource(R.string.confirm_clear), stringResource(R.string.confirm_clear_line), stringResource(R.string.confirm_yes_clear), R.drawable.ic_clear)
        is Confirm.Delete -> {
            val name = ui.mine.firstOrNull { it.id == c.id }?.name(zh) ?: ""
            listOf(stringResource(R.string.confirm_delete, name), stringResource(R.string.confirm_delete_line), stringResource(R.string.confirm_yes_delete), R.drawable.ic_delete)
        }
        Confirm.Quit -> listOf(stringResource(R.string.confirm_quit), stringResource(R.string.confirm_quit_line), stringResource(R.string.confirm_yes_quit), R.drawable.ic_exit)
    }
    val onYes = {
        when (c) {
            Confirm.Clear -> { graph.box.act("clear") { it.clear() }; Unit }
            is Confirm.Delete -> { graph.box.act("delete ${c.id}") { it.deleteSong(c.id) }; graph.box.saveNow() }
            Confirm.Quit -> exitApp(context, graph)
        }
        Ui.confirm = null
    }
    PageColumn {
        Header(stringResource(R.string.app_name), null, "confirm", settings, graph)
        Column(Modifier.fillMaxWidth().weight(1f).padding(vertical = 16.dp).clip(RoundedCornerShape(28.dp)).background(MBColors.TagFace).padding(28.dp), verticalArrangement = Arrangement.SpaceEvenly) {
            Icon(painter = painterResource(icon as Int), contentDescription = null, modifier = Modifier.size(56.dp), tint = MBColors.Ink)
            Txt(q as String, "confirm/q", style = PicoTheme.typography.titleLarge.copy(fontSize = 28.sp), color = MBColors.Ink, maxLines = 2)
            Txt(line as String, "confirm/line", style = PicoTheme.typography.bodyLarge, color = MBColors.InkSecondary, maxLines = 2)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SecondaryButton(stringResource(R.string.btn_cancel), "confirm/cancel", onClick = { Ui.confirm = null }, modifier = Modifier.weight(1f))
                SecondaryButton(yes as String, "confirm/yes", danger = true, onClick = onYes, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** D26 / D44 exit: save, release, close both window containers, remove every task, then end the process. */
internal fun exitApp(context: Context, graph: Graph) {
    Log.i(TAG, "EXIT save + release + close windows + finishAndRemoveTask + exitProcess")
    runCatching { graph.box.onBackground() }
    runCatching { graph.box.stop() }
    runCatching { graph.sounds.release() }
    runCatching { graph.window.closeAll() }
    runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.appTasks.forEach { runCatching { it.finishAndRemoveTask() } }
    }
    Handler(Looper.getMainLooper()).postDelayed({ exitProcess(0) }, 400)
}

/** The native SpatialUI TabBar (D8); labels arrive already resolved in the app language. */
@Composable
private fun NativeTabs(tabs: List<Triple<Page, Int, String>>) {
    TabBar(followViewpoints = setOf(ViewPoint.Front, ViewPoint.Left)) {
        tabs.forEach { (page, icon, label) ->
            item(
                selected = Ui.page == page,
                onClick = { Ui.page = page; Ui.confirm = null },
                mainContent = { Icon(painter = painterResource(icon), contentDescription = label) },
                supportContent = { Txt(label, "tabbar/${page.name}", style = PicoTheme.typography.labelMedium) },
            )
        }
    }
}
