package app.sordina.ui.theme

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.sordina.R
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/** Un tema decide colori, texture e caratteri. La struttura delle schermate non cambia. */
enum class ThemeFamily(val label: String) { CARTA("Carta"), QUADERNO("Quaderno") }

enum class ThemeMode(val label: String) { AUTO("Automatico"), LIGHT("Chiaro"), DARK("Scuro") }

@Immutable
data class SordinaColors(
    val bg: Color,
    val ink: Color,
    val soft: Color,
    val line: Color,
    val accent: Color,
    val tile: Color,
    val card: Color,
    val dot: Color,
    val dark: Boolean,
)

@Immutable
data class SordinaStyle(
    val family: ThemeFamily,
    val colors: SordinaColors,
    /** Carattere delle parole */
    val serif: FontFamily = Newsreader,
    /** Carattere dell'orologio grande */
    val display: FontFamily = NewsreaderDisplay,
    /** Carattere delle etichette */
    val sans: FontFamily = FontFamily.SansSerif,
    /** Carattere dei dati (Quaderno usa il monospazio per numeri ed etichette) */
    val data: FontFamily,
    /** Telefoni con poca RAM: niente grana né texture */
    val light: Boolean,
)

/**
 * Newsreader (SIL Open Font License), incluso nell'app: i produttori come Samsung
 * sostituiscono il serif di sistema, così il tema resta uguale su ogni telefono.
 */
@OptIn(ExperimentalTextApi::class)
private fun newsreader(weight: Int, opsz: Float) = FontFamily(
    Font(
        R.font.newsreader, FontWeight(weight), FontStyle.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("opsz", opsz)),
    ),
    Font(
        R.font.newsreader_italic, FontWeight(weight), FontStyle.Italic,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("opsz", opsz)),
    ),
)

val Newsreader = newsreader(400, 16f)
val NewsreaderDisplay = newsreader(300, 72f)

private val CartaLight = SordinaColors(
    bg = Color(0xFFF2EDE3), ink = Color(0xFF2B2722), soft = Color(0xFF7D7466),
    line = Color(0xFFDDD4C3), accent = Color(0xFFB4523A), tile = Color(0xFFEBE4D6),
    card = Color(0xFFFBF8F2), dot = Color(0xFFD3C9B6), dark = false,
)
private val CartaDark = SordinaColors(
    bg = Color(0xFF1C1915), ink = Color(0xFFECE4D4), soft = Color(0xFF9B917F),
    line = Color(0xFF3A342B), accent = Color(0xFFE08A63), tile = Color(0xFF27231D),
    card = Color(0xFF24201A), dot = Color(0xFF2F2A23), dark = true,
)

val LocalSordina = staticCompositionLocalOf {
    SordinaStyle(ThemeFamily.CARTA, CartaLight, data = FontFamily.SansSerif, light = false)
}

object S {
    val style: SordinaStyle @Composable get() = LocalSordina.current
    val c: SordinaColors @Composable get() = LocalSordina.current.colors
}

fun isLowRam(context: Context): Boolean {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return am.isLowRamDevice || am.memoryClass <= 192
}

@Composable
fun SordinaTheme(family: ThemeFamily, mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.AUTO -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val low = remember { isLowRam(context) }
    val colors = if (dark) CartaDark else CartaLight
    val style = SordinaStyle(
        family = family,
        colors = colors,
        data = if (family == ThemeFamily.QUADERNO) FontFamily.Monospace else FontFamily.SansSerif,
        light = low,
    )
    val scheme = if (dark) {
        darkColorScheme(primary = colors.accent, background = colors.bg, surface = colors.card,
            onSurface = colors.ink, onBackground = colors.ink)
    } else {
        lightColorScheme(primary = colors.accent, background = colors.bg, surface = colors.card,
            onSurface = colors.ink, onBackground = colors.ink)
    }
    CompositionLocalProvider(LocalSordina provides style) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** Grana della carta: una piccola tessera di rumore generata una volta e ripetuta. */
private val grainCache = HashMap<Boolean, ImageBitmap>()

private fun grainTile(dark: Boolean): ImageBitmap = grainCache.getOrPut(dark) {
    val size = 96
    val rnd = Random(7)
    val pixels = IntArray(size * size) {
        val a = rnd.nextInt(if (dark) 10 else 16)
        val v = if (dark) 255 else 0
        (a shl 24) or (v shl 16) or (v shl 8) or v
    }
    android.graphics.Bitmap.createBitmap(pixels, size, size, android.graphics.Bitmap.Config.ARGB_8888)
        .asImageBitmap()
}

private fun dotTile(px: Int, dotColor: Color): ImageBitmap {
    val bmp = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(
            (dotColor.alpha * 255).toInt(), (dotColor.red * 255).toInt(),
            (dotColor.green * 255).toInt(), (dotColor.blue * 255).toInt(),
        )
    }
    canvas.drawCircle(px / 2f, px / 2f, px / 12f, paint)
    return bmp.asImageBitmap()
}

/** Sfondo del tema: colore, grana (Carta) o puntini (Quaderno). */
@Composable
fun Modifier.paper(): Modifier {
    val style = LocalSordina.current
    val c = style.colors
    return this.drawWithCache {
        val grain = if (!style.light) ShaderBrush(ImageShader(grainTile(c.dark), TileMode.Repeated, TileMode.Repeated)) else null
        val dots = if (style.family == ThemeFamily.QUADERNO) {
            val px = 12.dp.toPx().toInt().coerceAtLeast(8)
            ShaderBrush(ImageShader(dotTile(px, c.dot), TileMode.Repeated, TileMode.Repeated))
        } else null
        onDrawBehind {
            drawRect(c.bg)
            if (dots != null) drawRect(dots)
            if (grain != null) drawRect(grain)
        }
    }
}
