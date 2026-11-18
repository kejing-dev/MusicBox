package io.embodit.musicbox.domain

enum class Language { SYSTEM, ZH, EN }

enum class Finish { WALNUT, MAPLE, RED }

/** App preferences (design dataModel.Prefs), SharedPreferences. */
data class Settings(
    val language: Language = Language.SYSTEM,
    /** Volume stop 0..2 (low / mid / high), see [Volume]. */
    val volume: Int = 2,
    val muted: Boolean = false,
    /** Global tempo factor 0.5..1.5, snapping to 0.75 / 1.0 / 1.25. */
    val speed: Float = 1f,
    val finish: Finish = Finish.WALNUT,
    val coachDone: Boolean = false,
    val lastSongId: String? = null,
)

/** The three volume stops (B20: three snaps + percent). */
object Volume {
    val PERCENT = intArrayOf(45, 75, 100)
    fun gain(level: Int): Float = PERCENT[level.coerceIn(0, 2)] / 100f
    /** Slider value 0..1 -> the nearest stop. */
    fun snap(v: Float): Int = (v * 2f).let { kotlin.math.round(it).toInt() }.coerceIn(0, 2)
}

/** The tempo slider: 50..150 %, snapping to 75 / 100 / 125 % within 6 % (design tempo.speedSlider). */
object Speed {
    val STOPS = floatArrayOf(0.75f, 1f, 1.25f)
    fun snap(v: Float): Float {
        val c = v.coerceIn(0.5f, 1.5f)
        val s = STOPS.minByOrNull { kotlin.math.abs(it - c) }!!
        return if (kotlin.math.abs(s - c) <= 0.06f) s else (kotlin.math.round(c * 20f) / 20f)
    }
    fun percent(v: Float): Int = kotlin.math.round(v * 100f).toInt()
}
