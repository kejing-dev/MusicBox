package io.embodit.musicbox.domain

/** One pin on the drum: column 0..63, tine 0..7 (tine 0 = C5 "1 do"). */
data class Pin(val col: Int, val tine: Int) {
    init { require(col in 0 until Drum.COLS && tine in 0 until Drum.TINES) { "pin $col/$tine" } }
}

object Drum {
    const val COLS = 64
    const val TINES = 8
    const val PIN_CAP = 256
    const val MY_CAP = 6

    /** Song length L: last pin column + 1 rounded up to a multiple of 4; an empty drum is 16, the most 64. */
    fun length(pins: Set<Pin>): Int {
        val last = pins.maxOfOrNull { it.col } ?: return 16
        return minOf(COLS, ((last + 1 + 3) / 4) * 4)
    }
}

/** A tune (design dataModel.Song). Presets are read-only; "mine" are the user's own (at most six). */
data class Song(
    val id: String,
    val preset: Boolean,
    val nameZh: String,
    val nameEn: String,
    val tagZh: String = "",
    val tagEn: String = "",
    val licZh: String = "",
    val licEn: String = "",
    val bpm: Int = 100,
    val pins: Set<Pin> = emptySet(),
    val updatedAt: Long = 0L,
) {
    val length: Int get() = Drum.length(pins)
    fun name(zh: Boolean) = if (zh) nameZh else nameEn
    fun tag(zh: Boolean) = if (zh) tagZh else tagEn
    fun lic(zh: Boolean) = if (zh) licZh else licEn
}

/** `id|nameZh|nameEn|tagZh|tagEn|licZh|licEn|bpm|c.t;c.t…` (t = tine 1..8), the format of assets/songs.txt and files/songs.txt. */
object SongCodec {
    fun decodeLine(line: String, preset: Boolean): Song? = runCatching {
        val f = line.trim().split("|")
        if (f.size < 9) return null
        val pins = if (f[8].isBlank()) emptySet() else f[8].split(";").map { p ->
            val (c, t) = p.split("."); Pin(c.toInt(), t.toInt() - 1)
        }.toSet()
        require(pins.size <= Drum.PIN_CAP)
        Song(f[0], preset, f[1], f[2], f[3], f[4], f[5], f[6], f[7].toInt(), pins, f.getOrNull(9)?.toLongOrNull() ?: 0L)
    }.getOrNull()

    fun encodeLine(s: Song): String = listOf(
        s.id, s.nameZh.clean(), s.nameEn.clean(), s.tagZh.clean(), s.tagEn.clean(), s.licZh.clean(), s.licEn.clean(), s.bpm.toString(),
        s.pins.sortedWith(compareBy({ it.col }, { it.tine })).joinToString(";") { "${it.col}.${it.tine + 1}" }, s.updatedAt.toString(),
    ).joinToString("|")

    fun decodeAll(text: String, preset: Boolean): List<Song> = text.lines().filter { it.isNotBlank() }.mapNotNull { decodeLine(it, preset) }

    fun encodeAll(songs: List<Song>): String = songs.joinToString("\n") { encodeLine(it) } + "\n"

    private fun String.clean() = replace("|", "/").replace("\n", " ")
}

/** Names for the user's tunes (design: auto names, no renaming). */
object SongNames {
    /** "我的曲子 n" / "My tune n" with the lowest n not in use. */
    fun fresh(mine: List<Song>): Pair<String, String> {
        var n = 1
        while (mine.any { it.nameZh == "我的曲子 $n" || it.nameEn == "My tune $n" }) n++
        return "我的曲子 $n" to "My tune $n"
    }

    /** "小星星 · 改" (then "· 改 2" …) for an edited copy of a preset. */
    fun fork(of: Song, mine: List<Song>): Pair<String, String> {
        var n = 1
        fun zh(k: Int) = if (k == 1) "${of.nameZh} · 改" else "${of.nameZh} · 改 $k"
        fun en(k: Int) = if (k == 1) "${of.nameEn} · mine" else "${of.nameEn} · mine $k"
        while (mine.any { it.nameZh == zh(n) || it.nameEn == en(n) }) n++
        return zh(n) to en(n)
    }
}
