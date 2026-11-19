package io.embodit.musicbox.ui.control

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
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
import io.embodit.musicbox.domain.SongGroup
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
 * native SpatialUI TabBar (D8): Play / Tunes / Settings. Only the classics list scrolls (v0.1.2, 17 tunes in groups);
 * every other page fits (D44) and spreads its rows between the header and the last row, so the blank outside the content is only the window padding. Confirmations unfold in place (P4).
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
        val obs = LifecycleEventObserver { _, e ->
            graph.window.onPanelLifecycle(e.name)
            if (e == androidx.lifecycle.Lifecycle.Event.ON_STOP) graph.preview.stop("panel stopped")
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(Ui.page) {
        Log.i(TAG, "PAGE ${Ui.page} ${Ui.page.stateId}")
        if (Ui.page != Page.SETTINGS) Ui.howTo = false
        if (Ui.page != Page.SONGS) graph.preview.stop("left tunes")
        if (Ui.confirm is Confirm.Quit && Ui.page != Page.SETTINGS) Ui.confirm = null
    }
    LaunchedEffect(Ui.editPins) { Log.i(TAG, "EDIT view=${Ui.editPins}") }

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
    // the pin-edit view is a focused mode (like a confirm): the TabBar steps aside so the screen stays <= 7 controls (B31)
    val editing = Ui.editPins && Ui.page == Page.PLAY && Ui.confirm == null && ui.state != BoxState.LOADING
    if (!editing) NativeTabs(tabs)
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
            Txt(stringResource(R.string.line_loading), "loading/line", style = PicoTheme.typography.titleMedium, color = MBColors.Ink)
        }
    }
}

// ------------------------------------------------------------------ P1 Play

/**
 * B31: at most 7 tappable controls per screen (tabs count, mute / quit do not). Play view: 3 tabs + [上弦并播放 / 停] +
 * speed slider + [钉谱…] = 6. Edit view ([钉谱…] unfolds it in place, a focused mode like P4: the TabBar steps aside):
 * ◂ / ▸ / 撤销 / 清空… / [钉好了] = 5.
 */
@Composable
private fun PlayPage(ui: BoxUi, settings: Settings, graph: Graph) {
    val zh = isZh()
    val song = ui.song
    val key = Lines.key(Lines.Ctx(ui.state, ui.notice, true, ui.nearHint, ui.coachPin))
    val playing = ui.state.playing || ui.state == BoxState.REWINDING || ui.state == BoxState.SCRUB
    PageColumn {
        Header(song?.name(zh) ?: "", song?.tag(zh)?.takeIf { it.isNotEmpty() }, "play", settings, graph)
        StatusCard(key, ui)
        if (Ui.editPins) EditKeys(ui, graph) else PlayKeys(ui, settings, graph, playing)
    }
}

/** The status sentence: the same line as the lid card (D38). The near hint (first open) gets the amber lamp so it reads first. */
@Composable
private fun StatusCard(key: String, ui: BoxUi) {
    val near = key == "nearHint"
    Row(
        Modifier.fillMaxWidth().height(104.dp).clip(RoundedCornerShape(24.dp)).background(if (near) MBColors.NearFace else MBColors.TagFace).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (near) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(MBColors.Amber), contentAlignment = Alignment.Center) {
                Icon(painter = painterResource(R.drawable.ic_near), contentDescription = null, modifier = Modifier.size(26.dp), tint = MBColors.Ink)
            }
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Txt(stringResource(lineRes(key)), "play/status", style = PicoTheme.typography.titleMedium, color = MBColors.Ink, maxLines = 2)
            if (near) Txt(stringResource(lineRes(Lines.stateKey(ui.state))), "play/status2", style = PicoTheme.typography.bodyMedium, color = MBColors.InkSecondary, maxLines = 1)
        }
    }
}

@Composable
private fun PlayKeys(ui: BoxUi, settings: Settings, graph: Graph, playing: Boolean) {
    val song = ui.song
    if (playing || ui.state == BoxState.LID_PAUSED) {
        PrimaryButton(stringResource(R.string.btn_stop), "play/stop", icon = R.drawable.ic_stop, onClick = { graph.box.act("btn stop") { it.stop() } }, modifier = Modifier.fillMaxWidth())
    } else {
        PrimaryButton(stringResource(R.string.btn_wind_play), "play/wind", icon = R.drawable.ic_play, enabled = ui.state != BoxState.SWAPPING,
            onClick = { graph.preview.stop("wind"); graph.box.act("btn wind&play") { it.windAndPlay() } }, modifier = Modifier.fillMaxWidth())
    }
    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        Txt(stringResource(R.string.spring_label), "play/spring", style = PicoTheme.typography.labelLarge, modifier = Modifier.width(96.dp))
        SpringCells(ui.cells, ui.gaugeFlash, Modifier.weight(1f))
    }
    OptionRow(stringResource(R.string.speed_label), "play/speed", labelWidth = 96.dp) {
        SpeedSlider(settings.speed, onChange = { v -> graph.settings.update { it.copy(speed = v) } }, modifier = Modifier.weight(1f))
    }
    Column {
        SecondaryButton(stringResource(R.string.btn_edit_pins), "play/edit", icon = R.drawable.ic_edit,
            onClick = { Ui.editPins = true; Log.i(TAG, "EDIT open") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Txt(stringResource(R.string.pins_line, song?.pins?.size ?: 0, ui.col + 1, song?.length ?: 16), "play/caption",
            style = PicoTheme.typography.bodySmall, color = PicoTheme.colorScheme.labelSecondary, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

/** The pin-edit keys (◂ back / rest ▸ / undo / clear…) + [钉好了]; grey with the reason when the lid is shut or it is playing (D43). */
@Composable
private fun EditKeys(ui: BoxUi, graph: Graph) {
    val song = ui.song
    val block = Lines.editBlockKey(ui.state)
    val caption = if (block != null) stringResource(lineRes(block)) else stringResource(R.string.edit_caption, song?.pins?.size ?: 0, ui.col + 1, song?.length ?: 16)
    Txt(caption, "edit/caption", style = PicoTheme.typography.bodyMedium, color = if (block != null) MBColors.Ink else MBColors.InkSecondary,
        align = TextAlign.Center, maxLines = 2, modifier = Modifier.fillMaxWidth())
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        SecondaryButton(stringResource(R.string.btn_back), "edit/back", onClick = { graph.box.act("btn back") { it.back() } }, enabled = ui.canEdit, modifier = Modifier.weight(1f))
        SecondaryButton(stringResource(R.string.btn_rest), "edit/rest", onClick = { graph.box.act("btn rest") { it.rest() } }, enabled = ui.canEdit, modifier = Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        SecondaryButton(stringResource(R.string.btn_undo), "edit/undo", icon = R.drawable.ic_undo, onClick = { graph.box.act("btn undo") { it.undo() } }, enabled = ui.canUndo, modifier = Modifier.weight(1f))
        SecondaryButton(stringResource(R.string.btn_clear_pins), "edit/clear", icon = R.drawable.ic_clear, onClick = { Ui.confirm = Confirm.Clear; Log.i(TAG, "CONFIRM clear P4") },
            enabled = ui.canEdit && (song?.pins?.isNotEmpty() == true), modifier = Modifier.weight(1f))
    }
    PrimaryButton(stringResource(R.string.btn_edit_done), "edit/done", icon = R.drawable.ic_done, onClick = { Ui.editPins = false; Log.i(TAG, "EDIT done") }, modifier = Modifier.fillMaxWidth())
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
        if (!mineTab) ClassicsList(ui, zh, graph, Modifier.fillMaxWidth().weight(1f))
        else {
            // my tunes from the top, then the tune on the drum: its roll and [改这首 / 钉谱…] (q1-1: useful, no blank band, D44)
            val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.US) }
            ui.mine.forEach { s ->
                SongRow(s, zh, s.id == ui.song?.id, sub = stringResource(R.string.mine_line, s.pins.size, fmt.format(Date(s.updatedAt))), graph = graph,
                    onDelete = { Ui.confirm = Confirm.Delete(s.id); Log.i(TAG, "CONFIRM delete ${s.id} P4") })
            }
            if (ui.mine.size < Drum.MY_CAP) OnDrumCard(ui, zh, graph, Modifier.fillMaxWidth().weight(1f))
        }
    }
}

/**
 * The classics (v0.1.2: 17 tunes) as one scrolling list in four groups (童谣 / 民歌 / 古典 / 节日), each with a header line.
 * A row loads the tune on the drum; [试听] beside it plays its opening without touching the drum (one preview at a time).
 * Names and sources wrap instead of being cut (D13 / D16).
 */
@Composable
private fun ClassicsList(ui: BoxUi, zh: Boolean, graph: Graph, modifier: Modifier) {
    val previewing by graph.preview.playing.collectAsStateWithLifecycle()
    val groups = remember(ui.presets) { SongGroup.grouped(ui.presets) }
    LaunchedEffect(Ui.classicsScrollTo) {
        val to = Ui.classicsScrollTo ?: return@LaunchedEffect
        Ui.classicsList.scrollToItem(to.coerceAtLeast(0))
        Ui.classicsScrollTo = null
        Log.i(TAG, "SCROLL classics -> $to (first visible ${Ui.classicsList.firstVisibleItemIndex})")
    }
    // D16 first screen: at the top the list ends on the last whole row (no half-cut row); the space under it says how many
    // more tunes are below. Once scrolled, the list takes the full height (rows slide in and out at the edges).
    val density = androidx.compose.ui.platform.LocalDensity.current
    val total = groups.sumOf { it.second.size }
    var firstScreen by remember(zh, ui.song?.id, total) { mutableStateOf<Pair<androidx.compose.ui.unit.Dp, Int>?>(null) }
    val atTop by remember { derivedStateOf { Ui.classicsList.firstVisibleItemIndex == 0 && Ui.classicsList.firstVisibleItemScrollOffset == 0 } }
    LaunchedEffect(zh, ui.song?.id, total) {
        snapshotFlow { Ui.classicsList.layoutInfo }.collect { li ->
            if (firstScreen != null || !atTop || li.visibleItemsInfo.isEmpty()) return@collect
            val whole = li.visibleItemsInfo.filter { it.offset + it.size <= li.viewportEndOffset }
            if (whole.size == li.totalItemsCount) return@collect
            val end = whole.maxOf { it.offset + it.size }
            val rowsShown = whole.count { it.key.toString().startsWith("g-").not() }
            firstScreen = with(density) { end.toDp() } to (total - rowsShown)
            Log.i(TAG, "CLASSICS first screen: ${whole.size} whole items, ${total - rowsShown} below")
        }
    }
    val clip = firstScreen?.takeIf { atTop }
    Column(modifier) {
        LazyColumn(
            if (clip != null) Modifier.fillMaxWidth().height(clip.first) else Modifier.fillMaxWidth().weight(1f),
            state = Ui.classicsList, verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            groups.forEach { (g, songs) ->
                item(key = "g-${g.id}") { GroupHeader(g, songs.size) }
                items(songs, key = { it.id }) { s ->
                    ClassicRow(s, zh, current = s.id == ui.song?.id, previewing = previewing == s.id, graph = graph, ui = ui)
                }
            }
        }
        if (clip != null) {
            Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Txt(stringResource(R.string.more_below, clip.second) + "  ↓", "songs/more", style = PicoTheme.typography.bodyMedium, color = PicoTheme.colorScheme.labelSecondary)
            }
        }
    }
}

@Composable
private fun GroupHeader(g: SongGroup, n: Int) {
    val name = stringResource(when (g) {
        SongGroup.KIDS -> R.string.group_kids
        SongGroup.FOLK -> R.string.group_folk
        SongGroup.CLASSIC -> R.string.group_classic
        SongGroup.HOLIDAY -> R.string.group_holiday
    })
    Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.Bottom) {
        Txt(name, "songs/group", style = PicoTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.width(10.dp))
        Txt(stringResource(R.string.group_count, n), "songs/group-n", style = PicoTheme.typography.bodySmall, color = PicoTheme.colorScheme.labelSecondary)
    }
}

/** One classic: [name / tag · source] loads it on the drum; [试听 / 停] previews its opening. */
@Composable
private fun ClassicRow(s: Song, zh: Boolean, current: Boolean, previewing: Boolean, graph: Graph, ui: BoxUi) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).heightIn(min = 72.dp).clip(RoundedCornerShape(20.dp)).background(if (current) MBColors.PrimaryContainer else MBColors.TagFace)
                .clickable { Log.i(TAG, "SONG pick ${s.id}"); graph.box.act("song ${s.id}") { it.selectSong(s.id) }; graph.box.saveNow() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (current) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(MBColors.Primary))
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Txt(s.name(zh), "songs/name", style = PicoTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = MBColors.Ink, maxLines = 2)
                Txt("${s.tag(zh)} · ${s.lic(zh)}", "songs/sub", style = PicoTheme.typography.bodySmall, color = MBColors.InkSecondary, maxLines = 2)
                if (current) Txt(stringResource(R.string.now_playing), "songs/current", style = PicoTheme.typography.labelMedium, color = MBColors.Primary)
            }
        }
        Spacer(Modifier.width(12.dp))
        val onListen = {
            if (!previewing && (ui.state.playing || ui.state == BoxState.LID_PAUSED)) graph.box.act("preview stops drum") { it.stop() }
            graph.preview.toggle(s)
        }
        if (previewing) PrimaryButton(stringResource(R.string.btn_listen_stop), "songs/listen-stop", icon = R.drawable.ic_stop, onClick = onListen, modifier = Modifier.width(132.dp), minWidth = 132.dp)
        else SecondaryButton(stringResource(R.string.btn_listen), "songs/listen", icon = R.drawable.ic_play, onClick = onListen, modifier = Modifier.width(132.dp))
    }
}

/**
 * The tune on the drum, on the Tunes page: name + pins, a small roll (8 tine rows x its columns, pins in the tine colours,
 * the pluck column marked) and [改这首] (a classic: the first pin saves a copy here) / [钉谱…] (opens the edit keys on Play).
 * With no tunes of your own a line on top says how this list fills. Short space (4–5 tunes) -> one row: name + the key.
 */
@Composable
private fun OnDrumCard(ui: BoxUi, zh: Boolean, graph: Graph, modifier: Modifier) {
    val song = ui.song ?: return
    val editLabel = stringResource(if (song.preset) R.string.btn_change_this else R.string.btn_edit_pins)
    val onEdit = {
        Log.i(TAG, "EDIT from tunes ${song.id}")
        if (ui.state == BoxState.CLOSED && ui.cells == 0) graph.box.act("edit opens lid") { it.openLid() }
        Ui.editPins = true; Ui.page = Page.PLAY
    }
    BoxWithConstraints(modifier.clip(RoundedCornerShape(24.dp)).background(MBColors.TagFace).padding(horizontal = 20.dp, vertical = 12.dp)) {
        val roomy = maxHeight >= 200.dp
        val intro = maxHeight >= 300.dp
        if (!roomy) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Txt(stringResource(R.string.card_on_drum, song.name(zh)), "drum/name", style = PicoTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = MBColors.Ink)
                    Txt(stringResource(R.string.card_pins, song.pins.size, song.length),
                        "drum/pins", style = PicoTheme.typography.bodySmall, color = MBColors.InkSecondary)
                }
                Spacer(Modifier.width(12.dp))
                PrimaryButton(editLabel, "drum/edit", icon = R.drawable.ic_edit, onClick = onEdit)
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (intro && ui.mine.isEmpty()) Txt(stringResource(R.string.mine_empty), "songs/empty", style = PicoTheme.typography.bodyMedium, color = MBColors.Ink, maxLines = 2)
                else if (intro) Txt(stringResource(R.string.mine_room, Drum.MY_CAP - ui.mine.size), "songs/room", style = PicoTheme.typography.bodyMedium, color = MBColors.Ink, maxLines = 2)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt(stringResource(R.string.card_on_drum, song.name(zh)), "drum/name", style = PicoTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = MBColors.Ink, modifier = Modifier.weight(1f))
                    Txt(stringResource(R.string.card_pins, song.pins.size, song.length), "drum/pins", style = PicoTheme.typography.bodySmall, color = MBColors.InkSecondary)
                }
                MiniRoll(song, ui.col, Modifier.fillMaxWidth().weight(1f))
                PrimaryButton(editLabel, "drum/edit", icon = R.drawable.ic_edit, onClick = onEdit, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** A read-only roll: 8 rows (tine 1 at the bottom, like the comb labels low -> high), one cell per column of the tune. */
@Composable
private fun MiniRoll(song: Song, col: Int, modifier: Modifier) {
    val cols = song.length.coerceAtLeast(16)
    val pins = song.pins
    Canvas(modifier.clip(RoundedCornerShape(12.dp)).background(MBColors.RollFace)) {
        val cw = size.width / cols; val rh = size.height / Drum.TINES
        // beat lines every 4 columns, tine lines
        for (c in 0..cols) {
            val strong = c % 4 == 0
            drawLine(if (strong) MBColors.RollLineStrong else MBColors.RollLine, Offset(c * cw, 0f), Offset(c * cw, size.height), strokeWidth = if (strong) 2f else 1f)
        }
        for (r in 0..Drum.TINES) drawLine(MBColors.RollLine, Offset(0f, r * rh), Offset(size.width, r * rh), strokeWidth = 1f)
        if (col in 0 until cols) drawRect(MBColors.Amber.copy(alpha = 0.35f), topLeft = Offset(col * cw, 0f), size = Size(cw, size.height))
        val rad = minOf(cw, rh) * 0.36f
        for (p in pins) {
            if (p.col >= cols) continue
            val cx = (p.col + 0.5f) * cw; val cy = size.height - (p.tine + 0.5f) * rh
            drawCircle(MBColors.TINES[p.tine], radius = rad, center = Offset(cx, cy))
            drawCircle(MBColors.Ink.copy(alpha = 0.55f), radius = rad, center = Offset(cx, cy), style = Stroke(width = 1.5f))
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
    runCatching { graph.preview.stop("exit") }
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
