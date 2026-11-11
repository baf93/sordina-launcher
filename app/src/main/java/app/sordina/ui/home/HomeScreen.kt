package app.sordina.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sordina.data.Card
import app.sordina.data.CardAction
import app.sordina.ui.clickableNoRipple
import app.sordina.ui.rememberNow
import app.sordina.ui.theme.S
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Quello che Casa mostra, preparato da fuori. */
data class HomeState(
    /** "tutto calmo", "3 in attesa" */
    val line: String,
    val waiting: Int,
    /** Già in ordine: la più importante prima */
    val cards: List<Card>,
    /** "14° nuvoloso" */
    val weather: String? = null,
    /** "Domani 9:00 Riunione" */
    val next: String? = null,
)

/** Casa: ora, data, una frase, la pila di schede. */
@Composable
fun HomeScreen(
    state: HomeState,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onLongPress: () -> Unit,
    onLine: () -> Unit,
    onAction: (Card, CardAction) -> Unit,
    onCard: (Card) -> Unit,
    onWeather: () -> Unit = {},
    onNext: () -> Unit = {},
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
                    onDragEnd = { if (total < -threshold) onSwipeUp() else if (total > threshold) onSwipeDown() },
                    onVerticalDrag = { _, dy -> total += dy },
                )
            }
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) }
            .systemBarsPadding()
            .padding(vertical = 24.dp),
    ) {
        Spacer(Modifier.weight(0.7f))
        Column(Modifier.padding(horizontal = 28.dp)) {
            Text(
                now.format(DateTimeFormatter.ofPattern("H:mm")),
                style = TextStyle(fontSize = 88.sp, lineHeight = 88.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp, color = c.ink),
            )
            Spacer(Modifier.height(4.dp))
            val day = now.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
                .replace(".", "").replaceFirstChar { it.uppercase() }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(day, style = TextStyle(fontSize = 16.sp, color = c.soft))
                Text(" · ", style = TextStyle(fontSize = 16.sp, color = c.soft))
                Text(
                    state.line,
                    Modifier.clickableNoRipple(onLine),
                    style = TextStyle(fontSize = 16.sp, color = if (state.waiting > 0) c.ink else c.soft),
                )
            }
            if (state.weather != null || state.next != null) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    state.weather?.let { Text(it, Modifier.clickableNoRipple(onWeather), maxLines = 1, style = TextStyle(fontSize = 16.sp, color = c.soft)) }
                    if (state.weather != null && state.next != null) Text(" · ", style = TextStyle(fontSize = 16.sp, color = c.soft))
                    state.next?.let { Text(it, Modifier.weight(1f, fill = false).clickableNoRipple(onNext), maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 16.sp, color = c.soft)) }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (state.cards.isNotEmpty()) Stack(state.cards, onAction, onCard)
        Spacer(Modifier.height(16.dp))
    }
}

/** La pila: si scorre in orizzontale, la successiva si intravede a destra. */
@Composable
private fun Stack(cards: List<Card>, onAction: (Card, CardAction) -> Unit, onCard: (Card) -> Unit) {
    val c = S.c
    val pager = rememberPagerState { cards.size }
    // Arriva una scheda più importante: torna davanti
    val first = cards.first().key
    LaunchedEffect(first) { if (pager.currentPage != 0) pager.animateScrollToPage(0) }
    HorizontalPager(
        state = pager,
        contentPadding = PaddingValues(start = 20.dp, end = if (cards.size > 1) 44.dp else 20.dp),
        pageSpacing = 10.dp,
        key = { cards[it].key },
    ) { i -> CardView(cards[i], onAction, onCard) }
    if (cards.size > 1) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(cards.size) { i ->
                Box(
                    Modifier.padding(horizontal = 3.dp).size(6.dp).clip(CircleShape)
                        .background(if (i == pager.currentPage) c.ink else c.dot),
                )
            }
        }
    }
}

@Composable
private fun CardView(card: Card, onAction: (Card, CardAction) -> Unit, onCard: (Card) -> Unit) {
    val c = S.c
    Column(
        Modifier.fillMaxWidth().height(168.dp).clip(RoundedCornerShape(22.dp)).background(c.card)
            .clickableNoRipple { onCard(card) }.padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Text(card.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 13.sp, color = c.soft))
        Spacer(Modifier.height(6.dp))
        Text(card.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 24.sp, color = c.ink, fontWeight = FontWeight.Normal))
        card.subtitle?.let {
            Spacer(Modifier.height(2.dp))
            Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 15.sp, color = c.soft))
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            card.actions.forEachIndexed { i, a ->
                if (a.icon != null) IconButton(a.icon, primary = a.icon == "pause" || a.icon == "play") { onAction(card, a) }
                else ActionButton(a.label, primary = i == 0) { onAction(card, a) }
            }
        }
    }
}

@Composable
private fun ActionButton(label: String, primary: Boolean, onClick: () -> Unit) {
    val c = S.c
    val m = Modifier.height(40.dp).clip(RoundedCornerShape(20.dp))
    Box(
        (if (primary) m.background(c.ink) else m.border(1.dp, c.line, RoundedCornerShape(20.dp)))
            .clickableNoRipple(onClick).padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, maxLines = 1, style = TextStyle(fontSize = 15.sp, color = if (primary) c.bg else c.ink))
    }
}

/** Comandi della musica: simboli semplici disegnati come testo. */
@Composable
private fun IconButton(icon: String, primary: Boolean, onClick: () -> Unit) {
    val c = S.c
    val glyph = when (icon) { "prev" -> "⏮\uFE0E"; "next" -> "⏭\uFE0E"; "pause" -> "⏸\uFE0E"; else -> "▶\uFE0E" }
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(if (primary) c.ink else c.bg).clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) {
        val ink = if (primary) c.bg else c.ink
        if (icon == "pause") Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(2) { Box(Modifier.size(width = 4.dp, height = 16.dp).clip(RoundedCornerShape(1.dp)).background(ink)) }
        } else Text(glyph, style = TextStyle(fontSize = 18.sp, color = ink))
    }
}
