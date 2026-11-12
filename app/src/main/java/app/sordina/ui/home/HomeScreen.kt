@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.sordina.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Check
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.tween
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sordina.data.Card
import app.sordina.data.CardAction
import app.sordina.data.Held
import app.sordina.ui.clickableNoRipple
import app.sordina.ui.LocalFeel
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import app.sordina.ui.rememberNow
import app.sordina.ui.theme.S
import app.sordina.ui.theme.OnBackdrop
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Una riga del programma: a sinistra l'ora (o "ora"), a destra cosa. */
data class AgendaRow(val label: String, val text: String, val key: String, val accent: Boolean = false)

/** Quello che Casa mostra, preparato da fuori. */
data class HomeState(
    /** Quante notifiche sono in sordina */
    val waiting: Int,
    /** "fino alle 13" */
    val until: String?,
    /** "Notte", "In auto" se attivo */
    val moment: String?,
    /** Già in ordine: la più importante prima */
    val cards: List<Card>,
    /** Il programma della giornata, in ordine di ora */
    val agenda: List<AgendaRow> = emptyList(),
    /** "10° pioggia", su una riga sua sotto la data */
    val weather: String? = null,
    /** Il microfono sta ascoltando; cosa ha capito finora */
    val listening: Boolean = false,
    val partial: String = "",
    /** Con uno sfondo: testo scuro (true) o chiaro (false) in alto e in basso; null = tema */
    val topDark: Boolean? = null,
    val bottomDark: Boolean? = null,
    val translucent: Boolean = false,
    val glass: Boolean = false,
)

/** Casa: ora, data, meteo, evento; la pila; la barra; in fondo cosa è in sordina. */
@Composable
fun HomeScreen(
    state: HomeState,
    groups: List<List<Held>>,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onLongPress: () -> Unit,
    onSearch: () -> Unit,
    onMicDown: () -> Unit,
    onMicUp: () -> Unit,
    onAction: (Card, CardAction) -> Unit,
    onCard: (Card) -> Unit,
    onAgenda: (String) -> Unit,
    sheetOpen: Boolean,
    onSheet: (Boolean) -> Unit,
    onOpenHeld: (Held) -> Unit,
    onReadAll: () -> Unit,
    onReadGroup: (String) -> Unit = {},
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
            .padding(top = 28.dp, bottom = 12.dp),
    ) {
        OnBackdrop(state.topDark) { Column(Modifier.padding(horizontal = 28.dp)) {
            val c = S.c
            val dayName = now.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())).replaceFirstChar { it.uppercase() }
            val date = now.format(DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault()))
            Text(dayName, style = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, color = c.ink))
            Text(date, style = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, color = c.soft))
            state.weather?.let { w ->
                Row(Modifier.padding(top = 10.dp).clickableNoRipple { onAgenda("weather") }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(weatherIcon(w), null, Modifier.size(20.dp), tint = c.ink)
                    Spacer(Modifier.width(8.dp))
                    Text(w, style = TextStyle(fontSize = 18.sp, color = c.ink))
                }
            }
            if (state.agenda.isNotEmpty()) Spacer(Modifier.height(24.dp))
            state.agenda.forEach { r -> AgendaLine(r) { onAgenda(r.key) } }
        } }
        Spacer(Modifier.weight(1f))
        if (state.cards.isNotEmpty()) {
            Stack(state.cards, state.glass, onAction, onCard)
            Spacer(Modifier.height(18.dp))
        }
        SearchBar(state, onSearch, onMicDown, onMicUp)
        Spacer(Modifier.height(36.dp))
        // Sopra una foto la riga sta su una pillola: si legge su qualsiasi fondo
        if (state.translucent) SordinaRow(state, pill = true) { onSheet(true) }
        else SordinaRow(state) { onSheet(true) }
        Spacer(Modifier.height(20.dp))
    }
    if (sheetOpen) SordinaSheet(groups, state.until, onOpenHeld, onReadGroup, onReadAll) { onSheet(false) }
}

@Composable
private fun AgendaLine(r: AgendaRow, onClick: () -> Unit) {
    val c = S.c
    Row(Modifier.fillMaxWidth().clickableNoRipple(onClick).padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Text(r.label, Modifier.width(64.dp), maxLines = 1, style = TextStyle(fontSize = 17.sp, color = c.soft))
        Text(r.text, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 17.sp, color = if (r.accent) c.accent else c.ink))
    }
}

/** Barra comoda per il pollice: tocco per scrivere, microfono per parlare. */
@Composable
private fun SearchBar(state: HomeState, onSearch: () -> Unit, onMicDown: () -> Unit, onMicUp: () -> Unit) {
    val c = S.c
    val feel = LocalFeel.current
    Row(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp))
            .background(if (state.glass) c.card.copy(alpha = 0.62f) else c.card).clickable(onClick = onSearch).padding(start = 18.dp, end = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, Modifier.size(22.dp), tint = c.soft)
        Spacer(Modifier.width(12.dp))
        val text = if (state.listening) state.partial.ifBlank { "Ti ascolto… lascia per finire" } else "Cosa devi fare?"
        Text(text, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontSize = 16.sp, color = if (state.listening) c.ink else c.soft))
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(if (state.listening) c.accent else androidx.compose.ui.graphics.Color.Transparent)
                .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(); feel.tick(); onMicDown(); waitForUpOrCancellation(); onMicUp() } },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Mic, "Parla", Modifier.size(22.dp), tint = if (state.listening) c.bg else c.ink)
        }
    }
}

private fun weatherIcon(w: String): ImageVector = when {
    "sereno" in w && "poco" !in w -> Icons.Rounded.WbSunny
    "pioggia" in w -> Icons.Rounded.Umbrella
    "neve" in w -> Icons.Rounded.AcUnit
    "temporale" in w -> Icons.Rounded.Thunderstorm
    else -> Icons.Rounded.Cloud
}

/** In fondo: quante notifiche sono in sordina e quando tornano. */
@Composable
private fun SordinaRow(state: HomeState, pill: Boolean = false, onClick: () -> Unit) {
    val c = S.c
    val on = state.waiting > 0
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Row(
        (if (pill) Modifier.clip(RoundedCornerShape(18.dp)).background(c.bg.copy(alpha = 0.82f)).padding(horizontal = 14.dp) else Modifier)
            .heightIn(min = 36.dp).clickableNoRipple(onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.NotificationsOff, null, Modifier.size(16.dp), tint = if (on) c.accent else c.soft)
        Spacer(Modifier.width(6.dp))
        Text(if (on) "${state.waiting} in sordina" else "Niente in sordina", style = TextStyle(fontSize = 14.sp, color = if (on) c.accent else c.soft))
        val tail = listOfNotNull(state.moment, if (on) state.until?.let { "tornano $it" } else null)
        if (tail.isNotEmpty()) Text(" · " + tail.joinToString(" · "), style = TextStyle(fontSize = 14.sp, color = c.soft))
    } }
}

/** Le notifiche in sordina: numero grande, filtri Tutte / Persone / App, righe in un riquadro arrotondato. */
@Composable
private fun SordinaSheet(
    groups: List<List<Held>>,
    until: String?,
    onOpen: (Held) -> Unit,
    onRead: (String) -> Unit,
    onReadAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = S.c
    val feel = LocalFeel.current
    val people = groups.filter { it.first().groupKey.startsWith("p:") }
    val apps = groups.filter { !it.first().groupKey.startsWith("p:") }
    val total = groups.sumOf { g -> g.sumOf { it.count } }
    var filter by remember { mutableIntStateOf(0) } // 0 tutte, 1 persone, 2 app
    val shown = when (filter) { 1 -> people; 2 -> apps; else -> people + apps }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.bg,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        dragHandle = { Box(Modifier.padding(top = 12.dp, bottom = 16.dp).size(width = 36.dp, height = 4.dp).clip(CircleShape).background(c.dot)) },
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.Bottom) {
                if (total > 0) {
                    Text("$total", style = TextStyle(fontSize = 46.sp, lineHeight = 46.sp, color = c.accent))
                    Spacer(Modifier.width(10.dp))
                    Text("in sordina", Modifier.padding(bottom = 6.dp), style = TextStyle(fontSize = 21.sp, color = c.ink))
                } else Text("Niente in sordina", style = TextStyle(fontSize = 26.sp, color = c.ink))
            }
            val calm = if (total == 0) "Quello che arriva aspetta qui, in silenzio."
            else listOfNotNull(until?.let { "Tornano $it" }, "niente di urgente").joinToString(" · ")
            Text(calm, Modifier.padding(start = 24.dp, end = 24.dp, top = 6.dp), style = TextStyle(fontSize = 14.sp, color = c.soft))
            if (groups.isNotEmpty()) {
                Row(Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Tutte", filter == 0) { feel.tick(); filter = 0 }
                    if (people.isNotEmpty()) Chip("Persone ${people.size}", filter == 1) { feel.tick(); filter = 1 }
                    if (apps.isNotEmpty()) Chip("App ${apps.size}", filter == 2) { feel.tick(); filter = 2 }
                }
                LazyColumn(
                    Modifier.heightIn(max = 480.dp).padding(horizontal = 14.dp).clip(RoundedCornerShape(20.dp)),
                ) {
                    itemsIndexed(shown, key = { _, g -> g.first().groupKey }) { i, g ->
                        Box(Modifier.animateItem()) { HeldRow(g, last = i == shown.lastIndex, onRead) { onOpen(g.first()) } }
                    }
                }
                Box(
                    Modifier.padding(horizontal = 14.dp, vertical = 16.dp).fillMaxWidth().height(50.dp).clip(RoundedCornerShape(25.dp)).background(c.ink)
                        .clickable { feel.confirm(); onReadAll(); onDismiss() },
                    contentAlignment = Alignment.Center,
                ) { Text("Tutto letto", style = TextStyle(fontSize = 16.sp, color = c.bg)) }
            } else Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    val c = S.c
    val bg by androidx.compose.animation.animateColorAsState(if (on) c.ink else c.card, label = "chip")
    val fg by androidx.compose.animation.animateColorAsState(if (on) c.bg else c.ink, label = "chipText")
    Box(Modifier.clip(RoundedCornerShape(16.dp)).background(bg).clickableNoRipple(onClick).padding(horizontal = 14.dp, vertical = 7.dp)) {
        Text(text, style = TextStyle(fontSize = 13.sp, color = fg))
    }
}

/**
 * Una riga: chi (o quale app), cosa, quando.
 * Scorrendola di lato diventa "letta" solo se la lasci oltre il 40%: prima torna al suo posto.
 */
@Composable
private fun HeldRow(g: List<Held>, last: Boolean, onRead: (String) -> Unit, onClick: () -> Unit) {
    val c = S.c
    val feel = LocalFeel.current
    val scope = rememberCoroutineScope()
    val h = g.first()
    val total = g.sumOf { it.count }
    val isPerson = h.groupKey.startsWith("p:")
    val offset = remember { Animatable(0f) }
    var width by remember { mutableIntStateOf(1) }
    val passed = kotlin.math.abs(offset.value) > width * 0.4f
    LaunchedEffect(passed) { if (passed) feel.threshold() }
    Box(
        Modifier.fillMaxWidth().onSizeChanged { width = it.width.coerceAtLeast(1) }
            .pointerInput(h.groupKey) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            val v = offset.value
                            if (kotlin.math.abs(v) > width * 0.4f) {
                                offset.animateTo(if (v > 0) width.toFloat() else -width.toFloat(), feel.spec(tween(160)))
                                feel.confirm()
                                onRead(h.groupKey)
                            } else offset.animateTo(0f, feel.spec(spring(dampingRatio = 0.7f, stiffness = 500f)))
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f, feel.spec(spring())) } },
                ) { ch, dx -> ch.consume(); scope.launch { offset.snapTo(offset.value + dx) } }
            },
    ) {
        // Sotto: "Letta" che si accende quando la soglia è superata
        val fg by androidx.compose.animation.animateColorAsState(if (passed) c.accent else c.soft, label = "read")
        Row(
            Modifier.matchParentSize().background(if (passed) c.accent.copy(alpha = 0.22f) else c.line).padding(horizontal = 20.dp),
            horizontalArrangement = if (offset.value >= 0) Arrangement.Start else Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = fg)
            Spacer(Modifier.width(6.dp))
            Text(if (passed) "Letta" else "Scorri ancora", style = TextStyle(fontSize = 14.sp, color = fg))
        }
        Column(
            Modifier.fillMaxWidth().graphicsLayer { translationX = offset.value }.background(c.card)
                .clickable { feel.tick(); onClick() }.padding(horizontal = 18.dp),
        ) {
            Row(Modifier.padding(top = 13.dp), verticalAlignment = Alignment.Bottom) {
                Text(h.groupTitle, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 17.sp, color = c.ink))
                Text(time(h.time), Modifier.padding(start = 12.dp), style = TextStyle(fontSize = 12.sp, color = c.soft))
            }
            val line = when {
                isPerson -> (if (h.conversation != null && h.person != null) "${h.person}: " else "") + h.text
                total > 1 -> "$total notifiche · " + h.text
                else -> h.text
            }
            Text(line, Modifier.padding(top = 2.dp, bottom = 13.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontSize = 14.sp, color = c.soft))
            if (!last) Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.line))
        }
    }
}

private fun time(ms: Long): String {
    val t = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
    return if (t.toLocalDate() == LocalDate.now()) t.format(DateTimeFormatter.ofPattern("H:mm"))
    else t.format(DateTimeFormatter.ofPattern("d/M"))
}

/**
 * La pila, come lo Smart Stack di iOS: una sola cornice ferma, il contenuto scorre in su o in giù dentro.
 * Con più di una scheda, i puntini a destra dicono dove sei.
 */
@Composable
private fun Stack(cards: List<Card>, glass: Boolean, onAction: (Card, CardAction) -> Unit, onCard: (Card) -> Unit) {
    val c = S.c
    val feel = LocalFeel.current
    val pager = rememberPagerState { cards.size }
    val first = cards.first().key
    LaunchedEffect(first) { if (pager.currentPage != 0) pager.scrollToPage(0) }
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.drop(1).collect { feel.tick() } }
    Box(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(160.dp).clip(RoundedCornerShape(24.dp))
            .background(if (glass) c.card.copy(alpha = 0.62f) else c.card),
    ) {
        VerticalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            key = { cards[it].key },
            flingBehavior = PagerDefaults.flingBehavior(state = pager, snapAnimationSpec = feel.spec(spring(dampingRatio = 0.86f, stiffness = 360f))),
        ) { i ->
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    // Chi esce rimpicciolisce e sfuma; chi entra cresce fino al suo posto
                    val off = kotlin.math.abs((pager.currentPage - i) + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                    alpha = 1f - off * 0.9f
                    scaleX = 1f - off * 0.08f; scaleY = scaleX
                },
            ) { CardView(cards[i], onAction, onCard) }
        }
        // Con più di una scheda i puntini restano sempre visibili
        if (cards.size > 1) Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            repeat(cards.size) { i ->
                Box(Modifier.size(5.dp).clip(CircleShape).background(if (i == pager.currentPage) c.ink else c.dot))
            }
        }
    }
}

@Composable
private fun CardView(card: Card, onAction: (Card, CardAction) -> Unit, onCard: (Card) -> Unit) {
    val c = S.c
    val feel = LocalFeel.current
    Column(
        Modifier.fillMaxSize().clickable { feel.tick(); onCard(card) }.padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(card.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 13.sp, color = c.soft))
        Spacer(Modifier.height(4.dp))
        Text(card.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 22.sp, color = c.ink))
        card.subtitle?.let {
            Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 14.sp, color = c.soft))
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            card.actions.forEachIndexed { i, a ->
                if (a.icon != null) RoundIcon(a.icon, primary = a.icon == "pause" || a.icon == "play") { feel.tick(); onAction(card, a) }
                else ActionButton(a.label, primary = i == 0) { feel.confirm(); onAction(card, a) }
            }
        }
    }
}

@Composable
private fun ActionButton(label: String, primary: Boolean, onClick: () -> Unit) {
    val c = S.c
    val shape = RoundedCornerShape(20.dp)
    val m = Modifier.height(40.dp).clip(shape)
    Box(
        (if (primary) m.background(c.ink) else m.border(1.dp, c.dot, shape)).clickable(onClick = onClick).padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, maxLines = 1, style = TextStyle(fontSize = 15.sp, color = if (primary) c.bg else c.ink)) }
}

@Composable
private fun RoundIcon(icon: String, primary: Boolean, onClick: () -> Unit) {
    val c = S.c
    val v = when (icon) {
        "prev" -> Icons.Rounded.SkipPrevious
        "next" -> Icons.Rounded.SkipNext
        "pause" -> Icons.Rounded.Pause
        else -> Icons.Rounded.PlayArrow
    }
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(if (primary) c.ink else c.bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(v, null, Modifier.size(24.dp), tint = if (primary) c.bg else c.ink) }
}
