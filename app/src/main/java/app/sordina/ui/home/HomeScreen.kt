package app.sordina.ui.home

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sordina.ui.Cap
import app.sordina.ui.DayLine
import app.sordina.ui.Serif
import app.sordina.ui.dateLabel
import app.sordina.ui.rememberNow
import app.sordina.ui.theme.S
import app.sordina.ui.theme.ThemeFamily
import java.time.format.DateTimeFormatter

val SUMMARY_HOURS = listOf(8, 13, 19)

/** Casa: ora, data, una frase di stato, la linea della giornata. Niente da scorrere. */
@Composable
fun HomeScreen(
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onLongPress: () -> Unit,
) {
    val now = rememberNow()
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    val c = S.c
    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (total < -threshold) onSwipeUp() else if (total > threshold) onSwipeDown()
                    },
                    onVerticalDrag = { _, dy -> total += dy },
                )
            }
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) }
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Cap(if (S.style.family == ThemeFamily.QUADERNO) now.format(DateTimeFormatter.ofPattern("dd.MM · EEE")) else now.dateLabel())
        }
        Spacer(Modifier.weight(1f))
        Text(
            now.format(DateTimeFormatter.ofPattern("H:mm")),
            style = TextStyle(
                fontFamily = S.style.display,
                fontSize = 92.sp,
                lineHeight = 92.sp,
                letterSpacing = (-2).sp,
                color = c.ink,
                fontFeatureSettings = if (S.style.family == ThemeFamily.CARTA) "onum" else null,
            ),
        )
        Spacer(Modifier.height(6.dp))
        Serif(phrase(now.hour), size = 17.sp, color = c.soft, italic = true)
        Spacer(Modifier.weight(1.3f))
        DayLine(now, SUMMARY_HOURS)
        Spacer(Modifier.height(18.dp))
        Serif("scorri in su", Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally), size = 12.sp, color = c.soft, italic = true)
    }
}

private fun phrase(hour: Int): String {
    val next = SUMMARY_HOURS.firstOrNull { it > hour }
    val tail = if (next != null) " Prossimo riepilogo alle $next." else ""
    return when (hour) {
        in 5..11 -> "Buongiorno. Niente di urgente.$tail"
        in 12..17 -> "Pomeriggio tranquillo.$tail"
        in 18..22 -> "Sera. Niente di urgente.$tail"
        else -> "Notte. Solo l'importante."
    }
}
