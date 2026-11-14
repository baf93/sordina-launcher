package app.sordina.ui.theme

import app.sordina.R

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.random.Random

/**
 * Gli sfondi di Sordina, disegnati nel codice: niente immagini da includere, nessuna licenza.
 * Cinque tipi: Tinte (forme sfocate su un colore), Dune (onde a strati, opache), Forme (geometrie piatte, stile Bauhaus),
 * Sfumature (un colore che scivola nell'altro) e Linee (fili che ondeggiano). Tutti con un filo di grana.
 * I nuovi si aggiungono in fondo: la scelta salvata è un indice.
 */
object Abstracts {
    enum class Kind(private val res: Int) { SOFT(R.string.wk_soft), GRADIENT(R.string.wk_gradient), DUNE(R.string.wk_dune), LINES(R.string.wk_lines), SHAPES(R.string.wk_shapes); val label: String get() = app.sordina.tr(res) }

    /** colors: per Dune dal cielo al primo piano; per Forme [fondo, tre colori, punto]. */
    class Preset(val kind: Kind, private val nameRes: Int, val colors: List<Int>) { val name: String get() = app.sordina.tr(nameRes) }

    private fun c(v: Long) = v.toInt()

    val all = listOf(
        // Tinte: [fondo, tre forme sfocate]
        Preset(Kind.SOFT, R.string.wp_sand, listOf(c(0xFFE6DCCF), c(0xFFD9A88A), c(0xFFB9C4B0), c(0xFFF3E9DC))),
        Preset(Kind.SOFT, R.string.wp_sage, listOf(c(0xFF2E3A32), c(0xFF6E8A72), c(0xFFA7B59A), c(0xFF1C2420))),
        Preset(Kind.SOFT, R.string.wp_night, listOf(c(0xFF14161C), c(0xFF2D3A5A), c(0xFF5A4A72), c(0xFF1E2C3C))),
        Preset(Kind.SOFT, R.string.wp_clay, listOf(c(0xFF3A2620), c(0xFFC4623A), c(0xFF8A4A3A), c(0xFFE0A47A))),
        Preset(Kind.SOFT, R.string.wp_fog, listOf(c(0xFFDCE1E6), c(0xFFA9B8C9), c(0xFFC9C2D6), c(0xFFEEF0F2))),
        Preset(Kind.SOFT, R.string.wp_ocean, listOf(c(0xFF0F2A33), c(0xFF2F7A7A), c(0xFF1D4E5F), c(0xFF7FB3A8))),
        Preset(Kind.DUNE, R.string.wp_desert, listOf(c(0xFFF1E4D3), c(0xFFE3B58F), c(0xFFC97B52), c(0xFF8C4A33), c(0xFF4A2A20))),
        Preset(Kind.DUNE, R.string.wp_night, listOf(c(0xFF0B1324), c(0xFF1C2C4C), c(0xFF2C4A7A), c(0xFF4B72B0), c(0xFF9DB7E0))),
        Preset(Kind.DUNE, R.string.wp_sage, listOf(c(0xFFE7EBDF), c(0xFFB9C7A8), c(0xFF8AA17A), c(0xFF587256), c(0xFF2C3D2E))),
        Preset(Kind.DUNE, R.string.wp_sunset, listOf(c(0xFFFFD9B0), c(0xFFF59E72), c(0xFFD9606A), c(0xFF8E3D6E), c(0xFF3A1E47))),
        Preset(Kind.DUNE, R.string.wp_rose, listOf(c(0xFFF6E3E4), c(0xFFE8B9C0), c(0xFFC98C9C), c(0xFF8F5B73), c(0xFF4B2F42))),
        Preset(Kind.DUNE, R.string.wp_slate, listOf(c(0xFF1A1C1F), c(0xFF2B2F35), c(0xFF40464F), c(0xFF5E6672), c(0xFF8C95A1))),
        Preset(Kind.SHAPES, R.string.wp_bauhaus, listOf(c(0xFFE9E4DA), c(0xFFE2582F), c(0xFF24456B), c(0xFFF2B33D), c(0xFF1F1F1F))),
        Preset(Kind.SHAPES, R.string.wp_night, listOf(c(0xFF161616), c(0xFF3F6B5C), c(0xFFC9A16A), c(0xFFB6533A), c(0xFFE8E1D4))),
        Preset(Kind.SHAPES, R.string.wp_pastel, listOf(c(0xFFF4EDE4), c(0xFFF2A7A0), c(0xFFA8C5DA), c(0xFFF6D186), c(0xFF3B3B3B))),
        Preset(Kind.SHAPES, R.string.wp_olive, listOf(c(0xFF2F3324), c(0xFFA3A35B), c(0xFFE0C9A6), c(0xFF6E7A4E), c(0xFFF0E6D2))),
        Preset(Kind.SHAPES, R.string.wp_sea, listOf(c(0xFFE8EEF0), c(0xFF1F4E79), c(0xFF7FB3D5), c(0xFFF4A259), c(0xFF0F2A44))),
        Preset(Kind.SHAPES, R.string.wp_terracotta, listOf(c(0xFF3B241C), c(0xFFD97A4E), c(0xFFF2C9A0), c(0xFF8C3B2B), c(0xFFF7EBDD))),
        // Tinte, altre
        Preset(Kind.SOFT, R.string.wp_mint, listOf(c(0xFFE3EFE8), c(0xFF9FD3BE), c(0xFFCFE3A8), c(0xFFF5F8F2))),
        Preset(Kind.SOFT, R.string.wp_lavender, listOf(c(0xFF221E33), c(0xFF6F5BA8), c(0xFF3E5A9A), c(0xFFB58BC9))),
        Preset(Kind.SOFT, R.string.wp_peach, listOf(c(0xFFF7E6DA), c(0xFFF4B39A), c(0xFFF2D0A4), c(0xFFE7A7B4))),
        Preset(Kind.SOFT, R.string.wp_aurora, listOf(c(0xFF07131A), c(0xFF1F8A70), c(0xFF3B4FA0), c(0xFF0F5A5C))),
        // Dune, altre
        Preset(Kind.DUNE, R.string.wp_forest, listOf(c(0xFFDDE5D3), c(0xFF8FA886), c(0xFF5C7A5A), c(0xFF34503A), c(0xFF17261C))),
        Preset(Kind.DUNE, R.string.wp_ocean, listOf(c(0xFFDDEFF2), c(0xFF8CC7D1), c(0xFF4A93A8), c(0xFF22607A), c(0xFF0E2F45))),
        Preset(Kind.DUNE, R.string.wp_dawn, listOf(c(0xFFFBEFE4), c(0xFFF6CFC0), c(0xFFD9A6B8), c(0xFF9C7BA6), c(0xFF4E4570))),
        // Forme, altre
        Preset(Kind.SHAPES, R.string.wp_mint, listOf(c(0xFFEFF5F0), c(0xFF2E8C6E), c(0xFFF2B8A2), c(0xFF9ED0C0), c(0xFF1F2A28))),
        Preset(Kind.SHAPES, R.string.wp_ink, listOf(c(0xFF0B0F1A), c(0xFF2B4C8C), c(0xFFE6D3A3), c(0xFF5E7FBF), c(0xFFF4F1EA))),
        Preset(Kind.SHAPES, R.string.wp_lavender, listOf(c(0xFFEEEAF4), c(0xFF8E74C2), c(0xFFF0C27B), c(0xFFC7B8E6), c(0xFF2A2440))),
        // Sfumature: [tre colori dall'alto in basso, bagliore]
        Preset(Kind.GRADIENT, R.string.wp_dawn, listOf(c(0xFFFFE3C8), c(0xFFF6A8A0), c(0xFF8E7CC3), c(0xFFFFF4E6))),
        Preset(Kind.GRADIENT, R.string.wp_night, listOf(c(0xFF0A0E1F), c(0xFF1B2547), c(0xFF3A2D5C), c(0xFF5A6FB0))),
        Preset(Kind.GRADIENT, R.string.wp_mint, listOf(c(0xFFF2FAF5), c(0xFFB8E2CF), c(0xFF6FB7A4), c(0xFFFFFFFF))),
        Preset(Kind.GRADIENT, R.string.wp_ember, listOf(c(0xFF1A0E0A), c(0xFF5A1F14), c(0xFFC4623A), c(0xFFF2A65A))),
        Preset(Kind.GRADIENT, R.string.wp_ocean, listOf(c(0xFF0C2B3A), c(0xFF1F6E86), c(0xFF6FC1C4), c(0xFFBDE8E3))),
        Preset(Kind.GRADIENT, R.string.wp_sand, listOf(c(0xFFF6EFE6), c(0xFFE8D6C0), c(0xFFCFB194), c(0xFFFFFAF2))),
        Preset(Kind.GRADIENT, R.string.wp_aurora, listOf(c(0xFF050B12), c(0xFF0F3B3A), c(0xFF1F8A70), c(0xFF6A5ACD))),
        // Linee: [fondo, filo chiaro, filo scuro]
        Preset(Kind.LINES, R.string.wp_ink, listOf(c(0xFF0E1116), c(0xFF5E7FBF), c(0xFF2A3446))),
        Preset(Kind.LINES, R.string.wp_sand, listOf(c(0xFFF3ECE1), c(0xFFC4623A), c(0xFFD9C8B0))),
        Preset(Kind.LINES, R.string.wp_sage, listOf(c(0xFF1F2A24), c(0xFFA7C4A0), c(0xFF3D5245))),
        Preset(Kind.LINES, R.string.wp_rose, listOf(c(0xFFF8ECEE), c(0xFFC9718A), c(0xFFE8C3CC))),
        Preset(Kind.LINES, R.string.wp_ember, listOf(c(0xFF140C0A), c(0xFFF2A65A), c(0xFF5A2A1C))),
        Preset(Kind.LINES, R.string.wp_fog, listOf(c(0xFFE9EDF1), c(0xFF4A6382), c(0xFFC5CFDA))),
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
            Kind.GRADIENT -> gradient(cv, w, h, p.colors, i)
            Kind.LINES -> lines(cv, w, h, p.colors, Random(i * 17 + 3))
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

    /** Tre colori che scivolano dall'alto in basso, un po' in diagonale, e un bagliore morbido. */
    private fun gradient(cv: Canvas, w: Int, h: Int, col: List<Int>, i: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val tilt = if (i % 2 == 0) 0.25f else -0.25f
        paint.shader = android.graphics.LinearGradient(w * (0.5f - tilt), 0f, w * (0.5f + tilt), h.toFloat(),
            intArrayOf(col[0], col[1], col[2]), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        val gx = if (i % 2 == 0) w * 0.8f else w * 0.2f
        val gy = h * 0.72f
        val r = maxOf(w, h) * 0.55f
        val glow = col[3]
        paint.shader = RadialGradient(gx, gy, r, intArrayOf(glow and 0x00FFFFFF or 0x66000000, glow and 0x00FFFFFF), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        cv.drawCircle(gx, gy, r, paint)
    }

    /** Tanti fili sottili che ondeggiano insieme, dal chiaro allo scuro: come le curve di livello di una mappa. */
    private fun lines(cv: Canvas, w: Int, h: Int, col: List<Int>, rnd: Random) {
        cv.drawColor(col[0])
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = maxOf(1.5f, w / 260f); strokeCap = Paint.Cap.ROUND }
        val n = 34
        val f1 = 2.2f + rnd.nextFloat() * 1.5f
        val f2 = 4.5f + rnd.nextFloat() * 2f
        val ph = rnd.nextFloat() * 6.28f
        for (k in 0 until n) {
            val t = k / (n - 1f)
            paint.color = blend(col[1], col[2], t)
            val base = h * (0.18f + 0.78f * t)
            val amp = h * 0.05f * (1f - 0.5f * t)
            val path = Path()
            var x = 0f
            val step = w / 48f
            while (x <= w + step) {
                val u = x / w
                val y = base + amp * kotlin.math.sin(u * f1 * 3.1416f + ph + t * 1.6f) + amp * 0.4f * kotlin.math.sin(u * f2 * 3.1416f - ph * 0.5f + t * 3f)
                if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                x += step
            }
            cv.drawPath(path, paint)
        }
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        fun ch(s: Int) = (((a shr s) and 255) * (1 - t) + ((b shr s) and 255) * t).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
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
