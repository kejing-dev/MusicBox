package io.embodit.musicbox.domain

/** One pin on the drum: column 0..63, tine 0..17 (v0.1.3: tine 0 = G4, chromatic up to tine 17 = C6; C5 "1 do" = tine 5). */
data class Pin(val col: Int, val tine: Int) {
    init { require(col in 0 until Drum.COLS && tine in 0 until Drum.TINES) { "pin $col/$tine" } }
}

object Drum {
    const val COLS = 64
    /** v0.1.3 (QA #3 q3-1, user "Widen the comb"): 18 tines, G4..C6 with every semitone (was 8 naturals C5..C6). */
    const val TINES = 18
    /** The old 8-tine comb (C5 D5 E5 F5 G5 A5 B5 C6, files written before v0.1.3) -> today's tine index. */
    val FROM_V1 = intArrayOf(5, 7, 9, 10, 12, 14, 16, 17)
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
    /** Presets only: the Tunes-list group ([SongGroup] id); empty for my tunes. */
    val group: String = "",
) {
    val length: Int get() = Drum.length(pins)
    fun name(zh: Boolean) = if (zh) nameZh else nameEn
    fun tag(zh: Boolean) = if (zh) tagZh else tagEn
    fun lic(zh: Boolean) = if (zh) licZh else licEn
}

/** The Tunes-list groups of the classics, in list order (v0.1.2: 17 tunes did not fit one screen). */
enum class SongGroup(val id: String) {
    KIDS("kids"), FOLK("folk"), CLASSIC("classic"), HOLIDAY("holiday");

    companion object {
        fun of(s: Song): SongGroup = entries.firstOrNull { it.id == s.group } ?: KIDS

        /** The classics grouped in list order; songs keep their file order inside a group. */
        fun grouped(presets: List<Song>): List<Pair<SongGroup, List<Song>>> =
            entries.map { g -> g to presets.filter { of(it) == g } }.filter { it.second.isNotEmpty() }
    }
}

/**
 * `id|nameZh|nameEn|tagZh|tagEn|licZh|licEn|bpm|c.t;c.t…|updatedAt|group` (the last two optional), the format of
 * assets/songs.txt and files/songs.txt. v0.1.3: a file that starts with the line [HEADER] numbers tines 1..18 (G4..C6);
 * a file without it was written by v0.1.0–v0.1.2 (tines 1..8 = C5..C6) and is read through [Drum.FROM_V1], so my tunes
 * keep their pitch after the update.
 */
object SongCodec {
    const val HEADER = "#comb 18 G4-C6"

    fun decodeLine(line: String, preset: Boolean, v1: Boolean = false): Song? = runCatching {
        val f = line.trim().split("|")
        if (f.size < 9) return null
        val pins = if (f[8].isBlank()) emptySet() else f[8].split(";").map { p ->
            val (c, t) = p.split(".")
            Pin(c.toInt(), if (v1) Drum.FROM_V1[t.toInt() - 1] else t.toInt() - 1)
        }.toSet()
        require(pins.size <= Drum.PIN_CAP)
        Song(f[0], preset, f[1], f[2], f[3], f[4], f[5], f[6], f[7].toInt(), pins, f.getOrNull(9)?.toLongOrNull() ?: 0L, f.getOrNull(10)?.trim() ?: "")
    }.getOrNull()

    fun encodeLine(s: Song): String = listOf(
        s.id, s.nameZh.clean(), s.nameEn.clean(), s.tagZh.clean(), s.tagEn.clean(), s.licZh.clean(), s.licEn.clean(), s.bpm.toString(),
        s.pins.sortedWith(compareBy({ it.col }, { it.tine })).joinToString(";") { "${it.col}.${it.tine + 1}" }, s.updatedAt.toString(),
    ).let { if (s.group.isNotEmpty()) it + s.group.clean() else it }.joinToString("|")

    fun decodeAll(text: String, preset: Boolean): List<Song> {
        val lines = text.lines().filter { it.isNotBlank() }
        val v1 = lines.firstOrNull()?.trim() != HEADER
        return lines.filterNot { it.startsWith("#") }.mapNotNull { decodeLine(it, preset, v1) }
    }

    fun encodeAll(songs: List<Song>): String = HEADER + "\n" + songs.joinToString("\n") { encodeLine(it) } + "\n"

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

/** [试听] (v0.1.2): which columns of a tune its preview plays, and when, at the tune's own bpm (speed 1). */
object PreviewPlan {
    const val SECONDS = 8.0

    /** (seconds from the start, tines plucked) for each non-empty column inside the first [maxS] seconds of the tune. */
    fun of(song: Song, maxS: Double = SECONDS): List<Pair<Double, List<Int>>> {
        val spc = 60.0 / song.bpm.coerceAtLeast(1)
        val cols = minOf(song.length, (maxS / spc).toInt() + 1)
        return song.pins.filter { it.col < cols }.groupBy { it.col }.toSortedMap()
            .map { (c, ps) -> c * spc to ps.map { it.tine }.sorted() }
    }
}

/**
 * The 18-tine comb (v0.1.3): G4..C6 chromatic, equal temperament A4 = 440 (tools/make_sounds.py tunes the samples to
 * these). Natural tines are the "white keys", sharps the "black keys" (editor keyboard, roll, labels).
 */
object Comb {
    private val LETTER = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val DEGREE = arrayOf("1", "#1", "2", "#2", "3", "4", "#4", "5", "#5", "6", "#6", "7")
    const val LOW_MIDI = 67 // G4

    fun midi(r: Int): Int = LOW_MIDI + r
    fun sharp(r: Int): Boolean = LETTER[midi(r) % 12].length > 1
    /** "G4", "C#5", "C6". */
    fun name(r: Int): String = LETTER[midi(r) % 12] + (midi(r) / 12 - 1)
    /** Tine index of a note name ("C5" -> 5); -1 when it is not on the comb. */
    fun of(note: String): Int = (0 until Drum.TINES).firstOrNull { name(it) == note } ?: -1
    /** Letter without the octave ("G#"). */
    fun letter(r: Int): String = LETTER[midi(r) % 12]
    /** Numbered notation (jianpu, do = C5): the degree ("5", "#5") and its octave dot: -1 = dot below, +1 = dot above. */
    fun jianpu(r: Int): Pair<String, Int> = DEGREE[midi(r) % 12] to when { midi(r) < 72 -> -1; midi(r) >= 84 -> 1; else -> 0 }
    /**
     * Pin / dot / note colour per tine (ARGB). The naturals keep the v0.1.2 colours of C5..C6 (C red, D orange, E yellow,
     * F green, G teal, A blue, B violet, high C pink); G4 / A4 / B4 share their pitch class's colour; every sharp is slate,
     * like a black key.
     */
    val COLOR: IntArray = IntArray(18) { r ->
        val natural = mapOf("C" to 0xFFE5484D, "D" to 0xFFF2994A, "E" to 0xFFF2C94C, "F" to 0xFF6FCF97, "G" to 0xFF2DB3A6, "A" to 0xFF56A4F2, "B" to 0xFF8C7CF0)
        when {
            r == 17 -> 0xFFE06CC1
            sharp(r) -> SHARP_COLOR
            else -> natural.getValue(letter(r))
        }.toInt()
    }
    const val SHARP_COLOR = 0xFF5B6470

    val whites: List<Int> get() = (0 until Drum.TINES).filter { !sharp(it) }
    val blacks: List<Int> get() = (0 until Drum.TINES).filter { sharp(it) }
}
