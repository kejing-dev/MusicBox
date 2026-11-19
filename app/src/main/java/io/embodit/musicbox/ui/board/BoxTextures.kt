package io.embodit.musicbox.ui.board

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.Log
import androidx.core.graphics.PathParser
import io.embodit.musicbox.domain.Comb
import io.embodit.musicbox.domain.Drum
import io.embodit.musicbox.domain.Layout
import io.embodit.musicbox.platform.TAG

/**
 * Canvas textures for the printed / engraved surfaces (the wood and metal are GLBs from tools/build_musicbox.py):
 * the song card inside the lid, the brass nameplate, the comb label strip, the floating note, the glows.
 * Every texture is drawn image-up = the surface's up as the user sees it (D10; `orient on` swaps in "F↑" to check).
 */
object BoxTextures {
    @Volatile var testF = false

    val VELVET = Color.rgb(0x5A, 0x1A, 0x2A)
    val CARD_INK = Color.rgb(0xF3, 0xE6, 0xC8)
    val BRASS = Color.rgb(0xC9, 0xA2, 0x5A)
    val BRASS_DARK = Color.rgb(0x6B, 0x52, 0x22)
    val ENGRAVE = Color.rgb(0x3A, 0x2A, 0x10)
    val TINE_COLORS: IntArray = io.embodit.musicbox.domain.Comb.COLOR

    /** Card line widths, px at the card's sizes (title 1.8 cm, hint 1.4 cm on a 0.30 m / 2048 px card). */
    const val CARD_TEXT_PX = 1920f
    val TITLE_PX = 1.8f / 30f * 2048f * 1.15f
    val HINT_PX = 1.4f / 30f * 2048f * 1.15f

    private fun serif(bold: Boolean) = Typeface.create(Typeface.SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)

    fun titlePaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CARD_INK; textSize = TITLE_PX; typeface = serif(true); textAlign = Paint.Align.CENTER }
    fun hintPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CARD_INK; textSize = HINT_PX; typeface = serif(false); textAlign = Paint.Align.CENTER }

    private fun fit(p: Paint, text: String, tag: String) {
        val w = p.measureText(text)
        if (w > CARD_TEXT_PX) {
            Log.w(TAG, "OVERFLOW card/$tag \"$text\" ${w.toInt()} px > ${CARD_TEXT_PX.toInt()}")
            p.textSize *= CARD_TEXT_PX / w
        }
    }

    private fun orientation(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(Color.WHITE)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = h * 0.7f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        c.drawText("F↑", w / 2f, h * 0.78f, p)
        return bmp
    }

    /** The song card on the lid's inner face: velvet, a thin brass rule, the song name and one line (D38 table). */
    fun card(title: String, line: String): Bitmap {
        val w = Layout.CARD_PX_W; val h = Layout.CARD_PX_H
        if (testF) return orientation(w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(VELVET)
        val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BRASS; style = Paint.Style.STROKE; strokeWidth = 6f }
        c.drawRoundRect(RectF(18f, 18f, w - 18f, h - 18f), 28f, 28f, rule)
        val tp = titlePaint(); fit(tp, title, "title")
        val hp = hintPaint(); fit(hp, line, "line")
        c.drawText(title, w / 2f, h * 0.44f, tp)
        c.drawText(line, w / 2f, h * 0.80f, hp)
        return bmp
    }

    /** The brass nameplate on the front: engraved bilingual name (design spatialLayout.nameplate). 640 x 200 px. */
    fun plate(): Bitmap {
        val w = 640; val h = 200
        if (testF) return orientation(w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, 0f, 0f, h.toFloat(), Color.rgb(0xE2, 0xC2, 0x7E), BRASS_DARK, Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
        val face = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BRASS }
        c.drawRoundRect(RectF(10f, 10f, w - 10f, h - 10f), 18f, 18f, face)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ENGRAVE; textAlign = Paint.Align.CENTER; typeface = serif(true); textSize = 74f }
        c.drawText("八音盒", w / 2f, 100f, p)
        p.textSize = 54f; p.typeface = serif(false)
        c.drawText("Music Box", w / 2f, 168f, p)
        return bmp
    }

    /**
     * Comb labels (v0.1.3, 18 tines G4..C6): one 113 px cell per tine. Naturals: a colour bar + the note in cream on the
     * walnut strip; sharps: a slate "black key" cell with white text. Chinese: numbered notation (do = C5, a dot below
     * for the low octave, above for high C, "#" for sharps); English: note names ("G4", "C#5"). 2048 x 102 px for 0.20 x 0.01 m.
     */
    fun labels(zh: Boolean): Bitmap {
        val w = 2048; val h = 102
        if (testF) return orientation(w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(0x2A, 0x1E, 0x16))
        val cell = w / Drum.TINES.toFloat()
        val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        for (r in 0 until Drum.TINES) {
            // tine r centre x = Layout.tineX(r); the strip spans x -0.045..+0.155
            val cx = ((Layout.tineX(r) - (Layout.LABELS.x - Layout.LABELS_W / 2)) / Layout.LABELS_W) * w
            val sharp = Comb.sharp(r)
            if (sharp) {
                fill.color = Color.rgb(0x2B, 0x2F, 0x35)
                c.drawRoundRect(RectF(cx - cell / 2 + 5, 4f, cx + cell / 2 - 5, h - 4f), 12f, 12f, fill)
            }
            fill.color = TINE_COLORS[r]
            c.drawRoundRect(RectF(cx - 30f, 8f, cx + 30f, 20f), 6f, 6f, fill)
            txt.color = if (sharp) Color.WHITE else CARD_INK
            if (zh) {
                val (deg, oct) = Comb.jianpu(r)
                txt.textSize = if (deg.length > 1) 50f else 58f
                c.drawText(deg, cx, 72f, txt)
                fill.color = txt.color
                val dx = if (deg.length > 1) 12f else 0f
                if (oct < 0) c.drawCircle(cx + dx, 88f, 6f, fill)
                if (oct > 0) c.drawCircle(cx + dx, 28f, 6f, fill)
            } else {
                val n = Comb.name(r)
                txt.textSize = if (n.length > 2) 40f else 48f
                c.drawText(n, cx, 74f, txt)
            }
        }
        return bmp
    }

    private const val NOTE_PATH = "M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z"

    /** ♪ (Material Symbols music_note, Apache-2.0) in [color] with a soft dark rim, 128 px. */
    fun note(color: Int): Bitmap {
        val s = 128
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val path = PathParser.createPathFromPathData(NOTE_PATH)
        c.scale(s / 24f, s / 24f)
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.argb(160, 20, 10, 10); style = Paint.Style.STROKE; strokeWidth = 1.2f }
        c.drawPath(path, rim)
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        return bmp
    }

    /** A soft warm light strip (lid edge pulse, pluck line, key / nameplate glow). */
    fun glow(color: Int = Color.rgb(0xFF, 0xD2, 0x7A), w: Int = 512, h: Int = 64): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; maskFilter = BlurMaskFilter(h * 0.22f, BlurMaskFilter.Blur.NORMAL) }
        c.drawRoundRect(RectF(h * 0.25f, h * 0.25f, w - h * 0.25f, h * 0.75f), h * 0.25f, h * 0.25f, p)
        return bmp
    }
}
