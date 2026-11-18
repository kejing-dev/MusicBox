package io.embodit.musicbox.data

import android.content.Context
import android.util.Log
import io.embodit.musicbox.domain.Finish
import io.embodit.musicbox.domain.Language
import io.embodit.musicbox.domain.Settings
import io.embodit.musicbox.domain.Song
import io.embodit.musicbox.domain.SongCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** The presets shipped in assets/songs.txt (tools/songs.py). */
object Presets {
    fun load(context: Context): List<Song> =
        runCatching { context.assets.open("songs.txt").bufferedReader(Charsets.UTF_8).use { SongCodec.decodeAll(it.readText(), preset = true) } }
            .onFailure { Log.e("MusicBox", "PRESETS failed", it) }.getOrDefault(emptyList())
}

/**
 * My tunes in files/songs.txt, written atomically: a temp file, a read-back check, then a rename; the previous good copy
 * stays as songs.bak (SpatialChess Stores.kt pattern, design dataModel.storage).
 */
class SongStore(context: Context) {
    private val dir = context.filesDir
    private val file = File(dir, "songs.txt")
    private val backup = File(dir, "songs.bak")

    fun load(): List<Song> =
        listOf(file, backup).firstNotNullOfOrNull { f -> runCatching { if (f.exists()) SongCodec.decodeAll(f.readText(), preset = false) else null }.getOrNull() }
            ?: emptyList()

    @Synchronized
    fun save(mine: List<Song>) {
        val text = SongCodec.encodeAll(mine)
        runCatching {
            val tmp = File(dir, "songs.tmp")
            tmp.writeText(text)
            check(SongCodec.decodeAll(tmp.readText(), false) == mine.map { it.copy(preset = false) }) { "read-back mismatch" }
            if (file.exists()) file.copyTo(backup, overwrite = true)
            if (!tmp.renameTo(file)) { file.delete(); check(tmp.renameTo(file)) }
        }.onFailure { Log.e("MusicBox", "SAVE failed", it) }
    }
}

/** App settings in SharedPreferences, exposed as a StateFlow so both windows recompose on change. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings.asStateFlow()
    val value: Settings get() = _settings.value

    fun update(change: (Settings) -> Settings) = set(change(_settings.value))

    fun set(s: Settings) {
        _settings.value = s
        prefs.edit()
            .putString("language", s.language.name)
            .putInt("volume", s.volume)
            .putBoolean("muted", s.muted)
            .putFloat("speed", s.speed)
            .putString("finish", s.finish.name)
            .putBoolean("coachDone", s.coachDone)
            .putString("lastSongId", s.lastSongId)
            .apply()
    }

    private fun read(): Settings = runCatching {
        Settings(
            language = Language.valueOf(prefs.getString("language", Language.SYSTEM.name)!!),
            volume = prefs.getInt("volume", 2).coerceIn(0, 2),
            muted = prefs.getBoolean("muted", false),
            speed = prefs.getFloat("speed", 1f).coerceIn(0.5f, 1.5f),
            finish = Finish.valueOf(prefs.getString("finish", Finish.WALNUT.name)!!),
            coachDone = prefs.getBoolean("coachDone", false),
            lastSongId = prefs.getString("lastSongId", null),
        )
    }.getOrElse { Settings() }
}
