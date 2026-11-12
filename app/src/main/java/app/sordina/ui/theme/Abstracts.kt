package app.sordina.ui.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.random.Random

/**
 * Gli sfondi di Sordina, disegnati nel codice: niente immagini da includere, nessuna licenza.
 * Tre tipi: Tinte (forme sfocate su un colore), Dune (onde a strati, opache) e Forme (geometrie piatte, stile Bauhaus). Tutti con un filo di grana.
 */
object Abstracts {
    enum class Kind(val label: String) { SOFT("Tinte"), DUNE("Dune"), SHAPES("Forme") }

    /** colors: per Dune dal cielo al primo piano; per Forme [fondo, tre colori, punto]. */
    class Preset(val kind: Kind, val name: String, val colors: List<Int>)

    private fun c(v: Long) = v.toInt()

    val all = listOf(
        // Tinte: [fondo, tre forme sfocate]
        Preset(Kind.SOFT, "Sabbia", listOf(c(0xFFE6DCCF), c(0xFFD9A88A), c(0xFFB9C4B0), c(0xFFF3E9DC))),
        Preset(Kind.SOFT, "Salvia", listOf(c(0xFF2E3A32), c(0xFF6E8A72), c(0xFFA7B59A), c(0xFF1C2420))),
        Preset(Kind.SOFT, "Notte", listOf(c(0xFF14161C), c(0xFF2D3A5A), c(0xFF5A4A72), c(0xFF1E2C3C))),
        Preset(Kind.SOFT, "Argilla", listOf(c(0xFF3A2620), c(0xFFC4623A), c(0xFF8A4A3A), c(0xFFE0A47A))),
        Preset(Kind.SOFT, "Nebbia", listOf(c(0xFFDCE1E6), c(0xFFA9B8C9), c(0xFFC9C2D6), c(0xFFEEF0F2))),
        Preset(Kind.SOFT, "Oceano", listOf(c(0xFF0F2A33), c(0xFF2F7A7A), c(0xFF1D4E5F), c(0xFF7FB3A8))),
        Preset(Kind.DUNE, "Deserto", listOf(c(0xFFF1E4D3), c(0xFFE3B58F), c(0xFFC97B52), c(0xFF8C4A33), c(0xFF4A2A20))),
        Preset(Kind.DUNE, "Notte", listOf(c(0xFF0B1324), c(0xFF1C2C4C), c(0xFF2C4A7A), c(0xFF4B72B0), c(0xFF9DB7E0))),
        Preset(Kind.DUNE, "Salvia", listOf(c(0xFFE7EBDF), c(0xFFB9C7A8), c(0xFF8AA17A), c(0xFF587256), c(0xFF2C3D2E))),
        Preset(Kind.DUNE, "Tramonto", listOf(c(0xFFFFD9B0), c(0xFFF59E72), c(0xFFD9606A), c(0xFF8E3D6E), c(0xFF3A1E47))),
        Preset(Kind.DUNE, "Rosa", listOf(c(0xFFF6E3E4), c(0xFFE8B9C0), c(0xFFC98C9C), c(0xFF8F5B73), c(0xFF4B2F42))),
        Preset(Kind.DUNE, "Ardesia", listOf(c(0xFF1A1C1F), c(0xFF2B2F35), c(0xFF40464F), c(0xFF5E6672), c(0xFF8C95A1))),
        Preset(Kind.SHAPES, "Bauhaus", listOf(c(0xFFE9E4DA), c(0xFFE2582F), c(0xFF24456B), c(0xFFF2B33D), c(0xFF1F1F1F))),
        Preset(Kind.SHAPES, "Notte", listOf(c(0xFF161616), c(0xFF3F6B5C), c(0xFFC9A16A), c(0xFFB6533A), c(0xFFE8E1D4))),
        Preset(Kind.SHAPES, "Pastello", listOf(c(0xFFF4EDE4), c(0xFFF2A7A0), c(0xFFA8C5DA), c(0xFFF6D186), c(0xFF3B3B3B))),
        Preset(Kind.SHAPES, "Oliva", listOf(c(0xFF2F3324), c(0xFFA3A35B), c(0xFFE0C9A6), c(0xFF6E7A4E), c(0xFFF0E6D2))),
        Preset(Kind.SHAPES, "Mare", listOf(c(0xFFE8EEF0), c(0xFF1F4E79), c(0xFF7FB3D5), c(0xFFF4A259), c(0xFF0F2A44))),
        Preset(Kind.SHAPES, "Cotto", listOf(c(0xFF3B241C), c(0xFFD97A4E), c(0xFFF2C9A0), c(0xFF8C3B2B), c(0xFFF7EBDD))),
    )

    fun render(index: Int, w: Int, h: Int, grain: Boolean = true): Bitmap {
        val i = index.coerceIn(0, all.size - 1)
        val p = all[i]
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        when (p.kind) {
            Kind.SOFT -> soft(cv, w, h, p.colors)
            Kind.DUNE -> dunes(cv, w, h, p.colors, Random(i * 31 + 7))
            Kind.SHAPES -> shapes(cv, w, h, p.colors, i)
        }
        if (grain) grain(bmp, w, h, Random(i))
        return bmp
    }

    /** Tre forme grandi e sfocate che si fondono sul colore di fondo. */
    private fun soft(cv: Canvas, w: Int, h: Int, col: List<Int>) {
        cv.drawColor(col[0])
        val spots = listOf(0.15f to 0.2f, 0.9f to 0.55f, 0.3f to 0.95f)
        val r = maxOf(w, h) * 0.75f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        spots.forEachIndexed { k, (x, y) ->
            val color = col[k + 1]
            paint.shader = RadialGradient(x * w, y * h, r, intArrayOf(color, color and 0x00FFFFFF), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            cv.drawCircle(x * w, y * h, r, paint)
        }
    }

    /** Strati di onde dal cielo al primo piano, ognuno con un'ombra morbida sopra. */
    private fun dunes(cv: Canvas, w: Int, h: Int, col: List<Int>, rnd: Random) {
        cv.drawColor(col[0])
        val layers = col.size - 1
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val u = w / 90f
        for (l in 1..layers) {
            val base = h * (0.30f + 0.62f * (l - 1) / (layers - 1).coerceAtLeast(1))
            val amp = h * (0.05f + rnd.nextFloat() * 0.05f)
            val path = Path()
            path.moveTo(0f, base + amp * (rnd.nextFloat() - 0.5f))
            path.cubicTo(w * 0.33f, base - amp, w * 0.5f, base + amp * 1.2f, w * 0.75f, base)
            path.cubicTo(w * 0.88f, base - amp * 0.8f, w * 0.95f, base - amp * 0.2f, w.toFloat(), base - amp * (rnd.nextFloat() - 0.3f))
            path.lineTo(w.toFloat(), h.toFloat()); path.lineTo(0f, h.toFloat()); path.close()
            paint.color = col[l]
            paint.setShadowLayer(6f * u, 0f, -2f * u, 0x33000000)
            cv.drawPath(path, paint)
        }
    }

    /** Forme piatte e grandi: un cerchio, una pillola, un mezzo cerchio, un arco e un punto. Tre composizioni. */
    private fun shapes(cv: Canvas, w: Int, h: Int, col: List<Int>, i: Int) {
        cv.drawColor(col[0])
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val W = w.toFloat(); val H = h.toFloat()
        fun circle(x: Float, y: Float, r: Float, k: Int) { p.style = Paint.Style.FILL; p.color = col[k]; cv.drawCircle(x * W, y * H, r * W, p) }
        fun pill(l: Float, t: Float, r: Float, b: Float, k: Int) { p.style = Paint.Style.FILL; p.color = col[k]; val rad = minOf(r - l, (b - t) * H / W) * W / 2; cv.drawRoundRect(l * W, t * H, r * W, b * H, rad, rad, p) }
        fun half(x: Float, y: Float, r: Float, start: Float, k: Int) { p.style = Paint.Style.FILL; p.color = col[k]; cv.drawArc(x * W - r * W, y * H - r * W, x * W + r * W, y * H + r * W, start, 180f, true, p) }
        fun arc(x: Float, y: Float, r: Float, start: Float, k: Int) { p.style = Paint.Style.STROKE; p.strokeWidth = W * 0.07f; p.strokeCap = Paint.Cap.ROUND; p.color = col[k]; cv.drawArc(x * W - r * W, y * H - r * W, x * W + r * W, y * H + r * W, start, 90f, false, p) }
        when (i % 3) {
            0 -> { circle(0.75f, 0.22f, 0.42f, 1); pill(-0.12f, 0.48f, 0.62f, 0.85f, 2); half(0.95f, 1.0f, 0.5f, 180f, 3); circle(0.2f, 0.2f, 0.1f, 4); arc(0.7f, 0.62f, 0.2f, 180f, 4) }
            1 -> { circle(0.22f, 0.3f, 0.45f, 1); pill(0.52f, 0.42f, 1.12f, 1.05f, 2); half(-0.05f, 1.0f, 0.42f, 180f, 3); circle(0.82f, 0.15f, 0.08f, 4); arc(0.3f, 0.66f, 0.18f, 270f, 4) }
            else -> { half(0.5f, 0.0f, 0.62f, 0f, 1); circle(0.28f, 0.62f, 0.3f, 2); pill(0.58f, 0.55f, 0.92f, 1.08f, 3); circle(0.82f, 0.4f, 0.06f, 4); arc(0.25f, 0.9f, 0.22f, 180f, 4) }
        }
    }

    private fun grain(bmp: Bitmap, w: Int, h: Int, rnd: Random) {
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        for (k in px.indices) {
            val n = rnd.nextInt(-7, 8)
            val c = px[k]
            val r = (((c shr 16) and 255) + n).coerceIn(0, 255)
            val g = (((c shr 8) and 255) + n).coerceIn(0, 255)
            val b = ((c and 255) + n).coerceIn(0, 255)
            px[k] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
    }
}
