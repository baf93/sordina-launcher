package app.sordina.ui.calendar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sordina.R
import app.sordina.tr
import app.sordina.ui.Primary
import app.sordina.ui.Sheet
import app.sordina.ui.clickableNoRipple
import app.sordina.ui.theme.S
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Giorno (e ora) in un foglio solo, nello stile di Sordina: il mese a griglia e, se c'è l'ora, due rulli per ore e
 * minuti. Niente finestre di Android. [time] null = solo il giorno.
 */
@Composable
internal fun WhenSheet(
    title: String, day: LocalDate, time: LocalTime?, locale: Locale, is24: Boolean, min: LocalDate? = null,
    onDismiss: () -> Unit, onDone: (LocalDate, LocalTime?) -> Unit,
) {
    var d by remember { mutableStateOf(day) }
    var h by remember { mutableStateOf(time?.hour ?: 0) }
    // Minuti a passi di 5; quelli di prima, se non tondi, si arrotondano
    var m by remember { mutableStateOf(((time?.minute ?: 0) + 2) / 5 % 12) }
    Sheet(title, onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            DayGrid(d, locale, min) { d = it }
            if (time != null) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    val hours = remember(is24, locale) { val f = DateTimeFormatter.ofPattern(if (is24) "HH" else "h a", locale); List(24) { f.format(LocalTime.of(it, 0)) } }
                    val minutes = remember { List(12) { "%02d".format(it * 5) } }
                    Wheel(24, h, { hours[it] }, Modifier.width(if (is24) 88.dp else 120.dp)) { h = it }
                    Text(":", Modifier.padding(horizontal = 6.dp), style = TextStyle(fontSize = 24.sp, color = S.c.ink))
                    Wheel(12, m, { minutes[it] }, Modifier.width(88.dp)) { m = it }
                }
            }
            Spacer(Modifier.height(16.dp))
            Primary(tr(R.string.done), Modifier.fillMaxWidth()) { onDone(d, time?.let { LocalTime.of(h, m * 5) }) }
        }
    }
}

/** Il mese a griglia: ‹ novembre 2026 ›, i giorni come nel calendario; quello scelto pieno */
@Composable
private fun DayGrid(selected: LocalDate, locale: Locale, min: LocalDate?, onPick: (LocalDate) -> Unit) {
    val c = S.c
    var month by remember { mutableStateOf(YearMonth.from(selected)) }
    val today = LocalDate.now()
    val first = WeekFields.of(locale).firstDayOfWeek
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(month).replaceFirstChar { it.titlecase(locale) },
            Modifier.weight(1f).padding(start = 4.dp), style = TextStyle(fontSize = 18.sp, color = c.ink))
        Arrow(Icons.AutoMirrored.Rounded.KeyboardArrowLeft) { month = month.minusMonths(1) }
        Arrow(Icons.AutoMirrored.Rounded.KeyboardArrowRight) { month = month.plusMonths(1) }
    }
    Row(Modifier.fillMaxWidth()) {
        (0L until 7L).forEach { i ->
            Text(first.plus(i).getDisplayName(java.time.format.TextStyle.NARROW, locale), Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = TextStyle(fontSize = 13.sp, color = c.soft))
        }
    }
    val lead = ((month.atDay(1).dayOfWeek.value - first.value) + 7) % 7
    val cells = lead + month.lengthOfMonth()
    for (w in 0 until (cells + 6) / 7) {
        Row(Modifier.fillMaxWidth()) {
            for (k in 0 until 7) {
                val n = w * 7 + k - lead + 1
                Box(Modifier.weight(1f).aspectRatio(1.4f), contentAlignment = Alignment.Center) {
                    if (n in 1..month.lengthOfMonth()) {
                        val date = month.atDay(n)
                        val on = date == selected
                        val ok = min == null || date >= min
                        Box(Modifier.size(38.dp).clip(CircleShape).background(if (on) c.ink else androidx.compose.ui.graphics.Color.Transparent)
                            .let { if (ok) it.clickableNoRipple { onPick(date) } else it }, contentAlignment = Alignment.Center) {
                            Text("$n", style = TextStyle(fontSize = 16.sp, color = when {
                                on -> c.bg; !ok -> c.dot; date == today -> c.accent; else -> c.ink
                            }))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Arrow(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).clickableNoRipple(onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(24.dp), tint = S.c.ink)
    }
}

/** Un rullo: si gira col dito e si ferma da solo su una voce; quella al centro è la scelta */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Wheel(count: Int, selected: Int, label: (Int) -> String, modifier: Modifier, onPick: (Int) -> Unit) {
    val c = S.c
    val itemH = 44.dp
    val state = rememberLazyListState(selected)
    val fling = rememberSnapFlingBehavior(state, SnapPosition.Center)
    // Con due voci vuote sopra e sotto, la voce al centro è la prima visibile se si è fermi; durante il giro la più vicina
    val center by remember {
        derivedStateOf { state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > state.layoutInfo.visibleItemsInfo.firstOrNull()?.size?.div(2) ?: 0) 1 else 0 }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress to center }.collect { (moving, i) -> if (!moving) onPick(i.coerceIn(0, count - 1)) }
    }
    Box(modifier.height(itemH * 5), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(itemH).clip(RoundedCornerShape(12.dp)).background(c.bg))
        LazyColumn(state = state, flingBehavior = fling, contentPadding = PaddingValues(vertical = itemH * 2), horizontalAlignment = Alignment.CenterHorizontally) {
            items(count, key = { it }) { i ->
                val on = i == center
                Box(Modifier.fillMaxWidth().height(itemH), contentAlignment = Alignment.Center) {
                    Text(label(i), style = TextStyle(fontSize = 21.sp, color = if (on) c.ink else c.soft))
                }
            }
        }
    }
}
