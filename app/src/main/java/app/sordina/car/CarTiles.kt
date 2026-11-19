package app.sordina.car

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.car.app.CarContext
import androidx.car.app.model.CarIcon
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import app.sordina.R
import app.sordina.data.Search

/**
 * I riquadri delle routine sullo schermo dell'auto. Android Auto disegna la griglia a modo suo (niente bordi, niente
 * sfondi): il riquadro allora sta nell'immagine. Un quadrato arrotondato col bordo nel colore di Sordina, il disegno
 * della routine al centro e, se tocca l'allarme, un bollino d'ottone nell'angolo.
 */
internal object CarTiles {
    private const val SIZE = 192
    private const val FILL = 0xFF1F2B2A.toInt()
    private const val STROKE = 0xFF5BBFB5.toInt()
    private const val GLYPH = 0xFFE6F2F1.toInt()
    private const val BRASS = 0xFFD4A72C.toInt()
    private const val BADGE_GLYPH = 0xFF1A1A1A.toInt()

    /** Il disegno che il nome fa venire in mente: "Entra", "Esco", "Buonanotte", "Luci sala". Se no, l'allarme o ▶ */
    fun glyph(name: String, alarm: Boolean, arm: Boolean): Int {
        val n = " " + Search.normalize(name) + " "
        fun has(vararg k: String) = k.any { n.contains(it) }
        return when {
            has(" entr", "rientr", " arriv", " torn", " home ", " enter", " arrive", " back ") -> R.drawable.car_enter
            has(" esc", " usc", " part", " fuori", " away", " leave", " out ", " exit") -> R.drawable.car_exit
            has("nott", " dorm", " letto", "night", " sleep", " bed") -> R.drawable.car_moon
            has("giorno", "mattin", "svegli", "morning", " wake", " day ") -> R.drawable.car_sun
            has(" luc", " lamp", " light") -> R.drawable.car_bulb
            has(" garage", " box ", " cancell", " gate") -> R.drawable.car_garage
            has(" riscald", " termo", " clima", " caldo", " fresco", " heat", " cool") -> R.drawable.car_thermo
            has(" casa ") -> R.drawable.car_home
            alarm && arm -> R.drawable.car_shield
            alarm -> R.drawable.car_unlock
            else -> R.drawable.car_play
        }
    }

    private val cache = HashMap<Pair<Int, Boolean>, CarIcon>()

    fun tile(cc: CarContext, glyph: Int, alarm: Boolean): CarIcon = cache.getOrPut(glyph to alarm) {
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val stroke = 5f
        val box = RectF(stroke, stroke, SIZE - stroke, SIZE - stroke)
        c.drawRoundRect(box, 44f, 44f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = FILL })
        c.drawRoundRect(box, 44f, 44f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = STROKE; style = Paint.Style.STROKE; strokeWidth = stroke })
        ContextCompat.getDrawable(cc, glyph)?.mutate()?.apply { setTint(GLYPH); setBounds(50, 50, SIZE - 50, SIZE - 50); draw(c) }
        if (alarm && glyph != R.drawable.car_shield && glyph != R.drawable.car_unlock) {
            val r = 30f
            val cx = SIZE - 40f; val cy = 40f
            c.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BRASS })
            ContextCompat.getDrawable(cc, R.drawable.car_lock)?.mutate()?.apply {
                setTint(BADGE_GLYPH); setBounds((cx - 18).toInt(), (cy - 18).toInt(), (cx + 18).toInt(), (cy + 18).toInt()); draw(c)
            }
        }
        CarIcon.Builder(IconCompat.createWithBitmap(bmp)).build()
    }
}
