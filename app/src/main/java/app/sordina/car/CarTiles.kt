package app.sordina.car

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.car.app.CarContext
import androidx.car.app.model.CarIcon
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap

/**
 * Le immagini di Sordina sullo schermo dell'auto, tutte nello stesso stile: un quadrato scuro con gli angoli tondi e
 * il bordo nel colore d'accento. Android Auto disegna griglie e righe come vuole lui (testi tagliati, titoli grigi):
 * quello che conta sta dentro l'immagine.
 *
 * - [tile]: i riquadri di Casa. L'icona (▶ le routine, lo scudo gli allarmi) e, sotto, lo stato; per gli allarmi
 *   anche il nome corto ("Naboo"), mentre sotto il riquadro c'è scritto solo "Allarme". Il fondo dice lo stato: l'accento (morbido) mentre una routine va, verde l'allarme disinserito, rosso
 *   inserito, ambra mentre si inserisce, rosso acceso col bordo bianco se è scattato.
 *   Il nome sta sotto il riquadro, spezzato su due righe da [split] perché l'auto non lo tagli.
 * - [framed]: l'icona di un'app in In sordina, dentro la sua cornice.
 * - [glyph]: un'icona bianca già disegnata (il pulsante Cerca): niente risorse da cercare per l'auto.
 */
internal object CarTiles {
    private const val SIZE = 256
    private const val RADIUS = 44f
    private const val BORDER = 4f
    /** Un margine trasparente: nella griglia grande l'auto mette i riquadri uno attaccato all'altro */
    private const val MARGIN = 24f
    private const val DARK = 0xFF1B2120.toInt()
    private const val GREEN = 0xFF2E7D4F.toInt()
    private const val RED = 0xFFB3362C.toInt()
    private const val ALARM_RED = 0xFFE53935.toInt()
    private const val AMBER = 0xFFB9791A.toInt()

    enum class State { IDLE, RUNNING, ARMING, ARMED, DISARMED, TRIGGERED }

    /** Il colore d'accento scelto in Sordina ("automatico": il verde acqua di Sordina) */
    fun accent(prefs: app.sordina.data.Prefs): Int = prefs.accent.takeIf { it != 0 && it != 1 } ?: 0xFF2E9C93.toInt()

    private data class Key(val glyph: Int, val state: State, val accent: Int, val name: String?, val caption: String?)
    private val cache = HashMap<Key, CarIcon>()

    /** Quante lettere stanno su una riga sotto un riquadro grande */
    private const val LINE = 9

    /**
     * Il nome in due righe, spezzato tra le parole: sotto un riquadro grande l'auto mostra il titolo e il testo su una
     * riga ciascuno, e taglia quello che non ci sta. "Centrale Naboo" → "Centrale" / "Naboo".
     */
    fun split(name: String): Pair<String, String?> {
        if (name.length <= LINE) return name to null
        val words = name.split(' ')
        // Una parola troppo lunga per la riga resta intera: la taglia l'auto ("Buonano…")
        if (words.first().length > LINE) return name to null
        var first = words.first()
        var i = 1
        while (i < words.size && first.length + 1 + words[i].length <= LINE) { first += " " + words[i]; i++ }
        val rest = words.drop(i).joinToString(" ")
        return if (rest.isEmpty()) name to null else first to rest
    }

    private val generic = setOf("centrale", "allarme", "antifurto", "alarm", "alarme", "alarma", "alarmanlage", "panel", "panello")

    /** Il nome di un allarme senza la parola generica davanti: "Centrale Naboo" → "Naboo"; null se resta niente */
    fun shortName(name: String): String? {
        val words = name.trim().split(Regex("\\s+"))
        val rest = words.dropWhile { it.lowercase() in generic }.joinToString(" ")
        return rest.ifEmpty { null }
    }

    /** Bianco sui fondi scuri, quasi nero su quelli chiari (un accento chiaro) */
    private fun inkOn(fill: Int) = if (ColorUtils.calculateLuminance(fill) > 0.45) 0xFF1D1A12.toInt() else Color.WHITE

    private fun frame(c: Canvas, size: Int, fill: Int, edge: Int, margin: Float = MARGIN, border: Float = BORDER, radius: Float = RADIUS) {
        val inset = margin + border / 2
        val r = RectF(inset, inset, size - inset, size - inset)
        c.drawRoundRect(r, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill })
        c.drawRoundRect(r, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = border; color = edge })
    }

    private fun text(s: String, size: Float, color: Int, bold: Boolean, width: Int, lines: Int): StaticLayout {
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        return StaticLayout.Builder.obtain(s, 0, s.length, p, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false).build()
    }

    private fun StaticLayout.drawAt(c: Canvas, x: Float, y: Float) { c.save(); c.translate(x, y); draw(c); c.restore() }

    /**
     * Un riquadro di Casa: l'icona grande al centro e, sotto, se ci sono, un nome corto ("Naboo") e lo stato
     * ("Notte", "Disinserito", "In corso…")
     */
    fun tile(cc: CarContext, glyph: Int, state: State, accent: Int, caption: String? = null, name: String? = null): CarIcon =
        cache.getOrPut(Key(glyph, state, accent, name, caption)) {
            val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val fill = when (state) {
                State.RUNNING -> ColorUtils.blendARGB(DARK, accent, 0.55f)
                State.ARMING -> AMBER
                State.ARMED -> RED
                State.TRIGGERED -> ALARM_RED
                State.DISARMED -> GREEN
                State.IDLE -> DARK
            }
            // Allarme: il bordo del suo colore (scattato: bianco, che si veda); il resto: l'accento
            val edge = when (state) {
                State.TRIGGERED -> Color.WHITE
                State.ARMING, State.ARMED, State.DISARMED -> fill
                else -> accent
            }
            frame(c, SIZE, fill, edge)
            val ink = inkOn(fill)
            val inner = (MARGIN + BORDER + 12).toInt()
            val icon = if (name != null) 64 else 84
            val label = name?.let { text(it, 34f, ink, true, SIZE - 2 * inner, 1) }
            val sub = caption?.let { text(it, 26f, ColorUtils.setAlphaComponent(ink, 0xE0), label == null, SIZE - 2 * inner, if (label == null) 2 else 1) }
            val h = icon + (label?.let { 10 + it.height } ?: 0) + (sub?.let { (if (label == null) 12 else 4) + it.height } ?: 0)
            var y = (SIZE - h) / 2f
            ContextCompat.getDrawable(cc, glyph)?.mutate()?.apply {
                setTint(ink)
                val x = (SIZE - icon) / 2
                setBounds(x, y.toInt(), x + icon, y.toInt() + icon)
                draw(c)
            }
            y += icon + (if (label != null) 10 else 12)
            label?.let { it.drawAt(c, inner.toFloat(), y); y += it.height + 4 }
            sub?.drawAt(c, inner.toFloat(), y)
            CarIcon.Builder(IconCompat.createWithBitmap(bmp)).build()
        }

    private const val SMALL = 160

    private val apps = HashMap<Pair<String, Int>, CarIcon?>()

    /** L'icona di un'app dentro la cornice di Sordina: la si fa una volta sola */
    fun framed(cc: CarContext, pkg: String, accent: Int): CarIcon? = apps.getOrPut(pkg to accent) {
        runCatching {
            val d = cc.packageManager.getApplicationIcon(pkg)
            val bmp = Bitmap.createBitmap(SMALL, SMALL, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            frame(c, SMALL, DARK, accent, margin = 6f, border = 5f, radius = 30f)
            val s = (SMALL * 0.56f).toInt()
            val x = (SMALL - s) / 2
            c.drawBitmap(d.toBitmap(s, s), x.toFloat(), x.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            CarIcon.Builder(IconCompat.createWithBitmap(bmp)).build()
        }.getOrNull()
    }

    private val glyphs = HashMap<Int, CarIcon>()

    /** Un'icona bianca già disegnata: l'auto non deve cercarla tra le risorse dell'app (dopo un aggiornamento sbaglia) */
    fun glyph(cc: CarContext, res: Int): CarIcon = glyphs.getOrPut(res) {
        val d = ContextCompat.getDrawable(cc, res)!!.mutate().apply { setTint(Color.WHITE) }
        CarIcon.Builder(IconCompat.createWithBitmap(d.toBitmap(96, 96))).build()
    }
}
