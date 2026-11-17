package app.sordina.ui.theme

import app.sordina.R

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily

/** Un solo tema per ora; l'enum resta per i temi futuri e per le impostazioni salvate. */
enum class ThemeFamily(private val res: Int) { CARTA(R.string.theme_family_sober); val label: String get() = app.sordina.tr(res) }

enum class ThemeMode(private val res: Int) { AUTO(R.string.theme_auto), LIGHT(R.string.theme_light), DARK(R.string.theme_dark); val label: String get() = app.sordina.tr(res) }

@Immutable
data class SordinaColors(
    val bg: Color,
    val ink: Color,
    val soft: Color,
    val line: Color,
    val accent: Color,
    /** Superficie delle schede e dei riquadri */
    val tile: Color,
    val card: Color,
    val dot: Color,
    val dark: Boolean,
)

@Immutable
data class SordinaStyle(
    val family: ThemeFamily,
    val colors: SordinaColors,
    val serif: FontFamily = FontFamily.Default,
    val display: FontFamily = FontFamily.Default,
    val sans: FontFamily = FontFamily.Default,
    val data: FontFamily = FontFamily.Default,
    /** Telefoni con poca RAM: animazioni ridotte */
    val light: Boolean,
)

private val Light = SordinaColors(
    bg = Color(0xFFF4F1EC), ink = Color(0xFF1F1D1A), soft = Color(0xFF8B857C),
    line = Color(0xFFE4DED4), accent = Color(0xFF1E7F78), tile = Color(0xFFFFFFFF),
    card = Color(0xFFFFFFFF), dot = Color(0xFFD4CEC4), dark = false,
)
private val Dark = SordinaColors(
    bg = Color(0xFF121212), ink = Color(0xFFEDEDED), soft = Color(0xFF8C8C8C),
    line = Color(0xFF2A2A2A), accent = Color(0xFF3FB0A6), tile = Color(0xFF1E1E1E),
    card = Color(0xFF1E1E1E), dot = Color(0xFF3A3A3A), dark = true,
)

val LocalSordina = staticCompositionLocalOf { SordinaStyle(ThemeFamily.CARTA, Light, light = false) }

object S {
    val style: SordinaStyle @Composable get() = LocalSordina.current
    val c: SordinaColors @Composable get() = LocalSordina.current.colors
}

fun isLowRam(context: Context): Boolean {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return am.isLowRamDevice || am.memoryClass <= 192
}

/**
 * Nero vero per gli OLED: il fondo resta spento, le schede e la barra si staccano bene dal nero
 * (abbastanza chiare da vederne i bordi, abbastanza scure da consumare poco).
 */
private val Black = Dark.copy(bg = Color.Black, card = Color(0xFF212121), tile = Color(0xFF212121), line = Color(0xFF333333), dot = Color(0xFF444444))

@Composable
fun SordinaTheme(family: ThemeFamily, mode: ThemeMode, accent: Color? = null, black: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.AUTO -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val low = remember { isLowRam(context) }
    val colors = (if (dark) (if (black) Black else Dark) else Light).let { if (accent != null) it.copy(accent = accent) else it }
    val style = SordinaStyle(family = family, colors = colors, light = low)
    val scheme = if (dark) {
        darkColorScheme(primary = colors.accent, background = colors.bg, surface = colors.card, onSurface = colors.ink, onBackground = colors.ink)
    } else {
        lightColorScheme(primary = colors.accent, background = colors.bg, surface = colors.card, onSurface = colors.ink, onBackground = colors.ink)
    }
    CompositionLocalProvider(LocalSordina provides style) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** Sfondo pieno, senza texture. */
@Composable
fun Modifier.paper(): Modifier = this.background(LocalSordina.current.colors.bg)

/** Il colore principale del tema, senza scelte: quello che "Auto" dà quando lo sfondo non ha colori. */
fun themeAccent(dark: Boolean): Color = if (dark) Dark.accent else Light.accent

/**
 * Il colore principale "Auto": quello dello sfondo, reso leggibile (né spento, né troppo scuro o chiaro).
 * Uno sfondo grigio o senza colori dà null: resta il colore del tema.
 */
fun autoAccent(argb: Int?): Int? {
    argb ?: return null
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(argb, hsl)
    if (hsl[1] < 0.18f) return null
    hsl[1] = hsl[1].coerceIn(0.45f, 0.75f)
    hsl[2] = hsl[2].coerceIn(0.48f, 0.62f)
    return androidx.core.graphics.ColorUtils.HSLToColor(hsl)
}
