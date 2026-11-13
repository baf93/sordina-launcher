package app.sordina.ui.theme

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/**
 * Lo sfondo di Casa. Per ogni zona (in alto il giorno, in basso la barra) dice se sopra
 * va il testo scuro o chiaro, così le scritte si leggono sempre.
 */
data class Backdrop(
    val mode: Int = 0,
    val image: ImageBitmap? = null,
    /** null = tinta unita: si usano i colori del tema */
    val topDark: Boolean? = null,
    val bottomDark: Boolean? = null,
    /** Colore suggerito dallo sfondo */
    val accent: Int? = null,
) {
    val active get() = mode != 0
}

object Backdrops {
    private fun file(context: Context) = File(context.filesDir, "wallpaper.jpg")

    /** Copia la foto scelta, ridotta alla misura dello schermo. */
    fun save(context: Context, uri: Uri): Boolean = runCatching {
        val dm = context.resources.displayMetrics
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= dm.widthPixels && bounds.outHeight / (sample * 2) >= dm.heightPixels) sample *= 2
        // ImageDecoder rispetta la rotazione EXIF delle foto
        val bmp = if (Build.VERSION.SDK_INT >= 28) {
            android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
                d.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                val s = maxOf(1, minOf(info.size.width, info.size.height) / dm.widthPixels)
                d.setTargetSampleSize(s)
            }
        } else context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return false
        val crop = centerCrop(bmp, dm.widthPixels, dm.heightPixels)
        file(context).outputStream().use { crop.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        true
    }.getOrDefault(false)

    fun load(context: Context, mode: Int, preset: Int = 0): Backdrop = runCatching {
        when (mode) {
            2, 3 -> {
                val dm = context.resources.displayMetrics
                val bmp = if (mode == 3) Abstracts.render(preset, dm.widthPixels / 2, dm.heightPixels / 2)
                else file(context).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) } ?: return Backdrop()
                val small = Bitmap.createScaledBitmap(bmp, 54, (54f * bmp.height / bmp.width).toInt().coerceAtLeast(2), true)
                val h = small.height
                Backdrop(
                    mode = mode, image = bmp.asImageBitmap(),
                    topDark = luminance(small, 0, (h * 0.45f).toInt()) > 0.5f,
                    bottomDark = luminance(small, (h * 0.6f).toInt(), h) > 0.5f,
                    accent = if (Build.VERSION.SDK_INT >= 27) WallpaperColors.fromBitmap(small).primaryColor.toArgb() else null,
                )
            }
            1 -> {
                val wc = if (Build.VERSION.SDK_INT >= 27) WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM) else null
                val dark = wc != null && Build.VERSION.SDK_INT >= 31 && (wc.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
                Backdrop(mode = 1, topDark = dark, bottomDark = dark, accent = wc?.primaryColor?.toArgb())
            }
            else -> Backdrop()
        }
    }.getOrDefault(Backdrop())

    private fun centerCrop(b: Bitmap, w: Int, h: Int): Bitmap {
        val target = h.toFloat() / w
        return if (b.height.toFloat() / b.width > target) {
            val nh = (b.width * target).toInt()
            Bitmap.createBitmap(b, 0, (b.height - nh) / 2, b.width, nh)
        } else {
            val nw = (b.height / target).toInt()
            Bitmap.createBitmap(b, (b.width - nw) / 2, 0, nw, b.height)
        }
    }

    /** Luminosità media (0..1) delle righe [from, to). */
    private fun luminance(b: Bitmap, from: Int, to: Int): Float {
        var sum = 0.0; var n = 0
        for (y in from until to.coerceAtMost(b.height)) for (x in 0 until b.width) {
            val p = b.getPixel(x, y)
            sum += (0.2126 * ((p shr 16) and 255) + 0.7152 * ((p shr 8) and 255) + 0.0722 * (p and 255)) / 255.0
            n++
        }
        return if (n == 0) 0f else (sum / n).toFloat()
    }
}

/** Testo scuro o chiaro per una zona dello sfondo; null lascia i colori del tema. */
@Composable
fun OnBackdrop(dark: Boolean?, content: @Composable () -> Unit) {
    if (dark == null) { content(); return }
    val s = LocalSordina.current
    // Anche i riquadri seguono lo sfondo: testo bianco su riquadri scuri, testo scuro su riquadri chiari
    val cols = if (dark) s.colors.copy(ink = Color(0xFF141414), soft = Color(0xFF3C3C3C), card = Color(0xFFF6F4EF), line = Color(0x1F000000))
    else s.colors.copy(ink = Color.White, soft = Color(0xFFE6E6E6), card = Color(0xFF26262A), line = Color(0x26FFFFFF))
    CompositionLocalProvider(LocalSordina provides s.copy(colors = cols), content = content)
}
