package app.sordina.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sordina.ui.theme.S
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

/** Ora attuale, aggiornata allo scoccare di ogni minuto. */
@Composable
fun rememberNow(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            val n = LocalDateTime.now()
            now = n
            delay((60 - n.second) * 1000L - n.nano / 1_000_000 + 50)
        }
    }
    return now
}

fun LocalDateTime.dateLabel(): String {
    val l = Locale.getDefault()
    val day = dayOfWeek.getDisplayName(JTextStyle.FULL, l)
    val month = month.getDisplayName(JTextStyle.FULL, l)
    return "$day $dayOfMonth $month".replaceFirstChar { it.titlecase(l) }
}

fun LocalDateTime.shortLabel(): String {
    val l = Locale.getDefault()
    val day = dayOfWeek.getDisplayName(JTextStyle.SHORT, l).replaceFirstChar { it.titlecase(l) }
    return "$day · " + format(DateTimeFormatter.ofPattern("H:mm"))
}

/** Etichetta piccola in maiuscolo, nel colore d'accento. */
@Composable
fun Cap(text: String, modifier: Modifier = Modifier, color: Color = S.c.accent) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = TextStyle(fontFamily = S.style.data, fontSize = 10.sp, letterSpacing = 1.8.sp, color = color),
    )
}

@Composable
fun Serif(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 16.sp,
    color: Color = S.c.ink,
    italic: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text,
        modifier = modifier,
        maxLines = maxLines,
        style = TextStyle(
            fontFamily = S.style.serif,
            fontSize = size,
            color = color,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        ),
    )
}

/**
 * Linea della giornata, dalle 6 a mezzanotte, con i riepiloghi come pallini.
 * [summaries] in ore (es. 8, 13, 19).
 */
@Composable
fun DayLine(now: LocalDateTime, summaries: List<Int>, modifier: Modifier = Modifier) {
    val c = S.c
    val start = 6.0
    val end = 24.0
    fun pos(h: Double) = ((h - start) / (end - start)).coerceIn(0.0, 1.0).toFloat()
    val nowH = now.hour + now.minute / 60.0
    Canvas(modifier.fillMaxWidth().height(16.dp)) {
        val y = size.height / 2
        val stroke = 2.dp.toPx()
        drawLine(c.line, Offset(0f, y), Offset(size.width, y), stroke)
        val p = pos(nowH) * size.width
        drawLine(c.ink, Offset(0f, y), Offset(p, y), stroke)
        for (h in summaries) {
            val x = pos(h.toDouble()) * size.width
            val r = 4.dp.toPx()
            if (h > nowH) {
                drawCircle(c.bg, r, Offset(x, y))
                drawCircle(c.accent, r, Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
            } else {
                drawCircle(c.accent, r, Offset(x, y))
            }
        }
        drawLine(c.accent, Offset(p, y - 6.dp.toPx()), Offset(p, y + 6.dp.toPx()), 2.dp.toPx())
    }
}
