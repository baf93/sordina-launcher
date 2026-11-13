@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.sordina.ui.home

import app.sordina.R
import app.sordina.tr
import app.sordina.trn

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
import androidx.compose.foundation.layout.widthIn
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
import app.sordina.data.Scene
import app.sordina.ui.clickableNoRipple
import app.sordina.ui.LocalFeel
import app.sordina.ui.surface
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
/** Una riga del programma: a sinistra l'ora (o "adesso"), a destra cosa. [day]: il giorno, se non è oggi; fa da titoletto sopra. */
data class AgendaRow(val label: String, val text: String, val key: String, val accent: Boolean = false, val day: String? = null)

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
    // Le altre Case: sera, notte, auto
    val scene: Scene = Scene.DAY,
    /** Sera e notte: la prossima sveglia ("7:00") se c'è */
    val alarm: String? = null,
    /** Sera e notte: gli appuntamenti di domani e che tempo farà */
    val tomorrow: List<AgendaRow> = emptyList(),
    val tomorrowWeather: String? = null,
    /** "La notte comincia alle 23:00", "Fino alle 7:00 solo l'importante" */
    val sceneLine: String? = null,
    /** Sera: com'è andata oggi */
    val recap: String? = null,
    /** Auto: chi chiami di solito adesso (nome breve, nome completo) */
    val people: List<Pair<String, String>> = emptyList(),
    /** Auto: i nomi delle app scelte per navigare e per la musica (null = "Naviga", "Musica") */
    val navLabel: String? = null,
    val musicLabel: String? = null,
    /** Auto: c'è Home Assistant, un pulsante per aprirlo */
    val homeApp: Boolean = false,
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
    onSheet: (Boolean) -> Unit,
    onOpenHeld: (Held) -> Unit,
    onReadAll: () -> Unit,
    onReadGroup: (String) -> Unit = {},
    onScene: (String) -> Unit = {},
) {
    val gestures = Modifier.homeGestures(onSwipeUp, onSwipeDown, onLongPress)
    when (state.scene) {
        // In auto niente pressione lunga: si toccano pulsanti grandi, le impostazioni non devono aprirsi per sbaglio
        Scene.CAR -> { CarHome(state, Modifier.homeGestures(onSwipeUp, onSwipeDown, {}), onSearch, onMicDown, onMicUp, onAction, onCard, onScene) { onSheet(true) }; return }
        Scene.NIGHT -> { NightHome(state, gestures, onSearch, onMicDown, onMicUp, onAction, onCard, onScene) { onSheet(true) }; return }
        else -> Unit
    }
    // La sera Casa resta quella di sempre, con l'accento più caldo e uno sguardo a domani
    if (state.scene == Scene.EVENING) Evening { DayHome(state, gestures, onSearch, onMicDown, onMicUp, onAction, onCard, onAgenda, onScene, onSheet) }
    else DayHome(state, gestures, onSearch, onMicDown, onMicUp, onAction, onCard, onAgenda, onScene, onSheet)
}

/** Su: le app. Giù: la tendina. Tieni premuto: le impostazioni. Uguale in tutte le Case. */
@Composable
internal fun Modifier.homeGestures(onSwipeUp: () -> Unit, onSwipeDown: () -> Unit, onLongPress: () -> Unit): Modifier {
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    return this
        .pointerInput(Unit) {
            var total = 0f
            detectVerticalDragGestures(
                onDragStart = { total = 0f },
                onDragEnd = { if (total < -threshold) onSwipeUp() else if (total > threshold) onSwipeDown() },
                onVerticalDrag = { _, dy -> total += dy },
            )
        }
        .pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) }
}

@Composable
private fun DayHome(
    state: HomeState,
    gestures: Modifier,
    onSearch: () -> Unit,
    onMicDown: () -> Unit,
    onMicUp: () -> Unit,
    onAction: (Card, CardAction) -> Unit,
    onCard: (Card) -> Unit,
    onAgenda: (String) -> Unit,
    onScene: (String) -> Unit,
    onSheet: (Boolean) -> Unit,
) {
    val now = rememberNow()
    Column(
        Modifier
            .fillMaxSize()
            .then(gestures)
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
            state.agenda.forEachIndexed { i, r ->
                if (r.day != null && r.day != state.agenda.getOrNull(i - 1)?.day) Text(r.day, Modifier.padding(top = if (i == 0) 0.dp else 10.dp, bottom = 2.dp), style = TextStyle(fontSize = 13.sp, color = c.soft))
                AgendaLine(r) { onAgenda(r.key) }
            }
            if (state.scene == Scene.EVENING) EveningBlock(state, onScene)
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
}

@Composable
internal fun AgendaLine(r: AgendaRow, onClick: () -> Unit) {
    val c = S.c
    Row(Modifier.fillMaxWidth().clickableNoRipple(onClick).padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        // Colonna dell'ora: almeno 64, mai attaccata al testo (anche con "11:30 PM")
        Text(r.label, Modifier.widthIn(min = 64.dp).padding(end = 12.dp), maxLines = 1, style = TextStyle(fontSize = 17.sp, color = if (r.accent) c.accent else c.soft))
        Text(r.text, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 17.sp, color = c.ink))
    }
}

/** Barra comoda per il pollice: tocco per scrivere, microfono per parlare. */
@Composable
internal fun SearchBar(state: HomeState, onSearch: () -> Unit, onMicDown: () -> Unit, onMicUp: () -> Unit) {
    val c = S.c
    val feel = LocalFeel.current
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp))
            .background(surface()).clickable(onClick = onSearch).padding(start = 22.dp, end = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val text = if (state.listening) state.partial.ifBlank { tr(R.string.home_listening) } else tr(R.string.home_search)
        Text(text, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontSize = 16.sp, color = if (state.listening) c.ink else c.soft))
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(if (state.listening) c.accent else androidx.compose.ui.graphics.Color.Transparent)
                .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(); feel.tick(); onMicDown(); waitForUpOrCancellation(); onMicUp() } },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Mic, tr(R.string.speak), Modifier.size(22.dp), tint = if (state.listening) c.bg else c.accent)
        }
    }
}

internal fun weatherIcon(w: String): ImageVector = when {
    w.endsWith(tr(R.string.wx_clear)) -> Icons.Rounded.WbSunny
    tr(R.string.wx_rain) in w -> Icons.Rounded.Umbrella
    tr(R.string.wx_snow) in w -> Icons.Rounded.AcUnit
    tr(R.string.wx_storm) in w -> Icons.Rounded.Thunderstorm
    else -> Icons.Rounded.Cloud
}

/** In fondo: quante notifiche sono in sordina e fino a quando. */
@Composable
internal fun SordinaRow(state: HomeState, pill: Boolean = false, onClick: () -> Unit) {
    val c = S.c
    val on = state.waiting > 0
    // Con caratteri molto grandi la riga si accorcia in coda invece di toccare i bordi
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) { Row(
        (if (pill) Modifier.clip(RoundedCornerShape(18.dp)).background(surface()).padding(horizontal = 14.dp) else Modifier)
            .heightIn(min = 36.dp).clickableNoRipple(onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.NotificationsOff, null, Modifier.size(16.dp), tint = c.soft)
        Spacer(Modifier.width(6.dp))
        Text(if (on) trn(R.plurals.n_waiting, state.waiting) else tr(R.string.nothing_waiting), style = TextStyle(fontSize = 14.sp, color = c.soft))
        val tail = listOfNotNull(state.moment, if (on) state.until else null)
        if (tail.isNotEmpty()) Text(" · " + tail.joinToString(" · "), Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 14.sp, color = c.soft))
    } }
}

/**
 * La pila, come lo Smart Stack di iOS: una sola cornice ferma, il contenuto scorre in su o in giù dentro.
 * Con più di una scheda, i puntini a destra dicono dove sei.
 */
@Composable
internal fun Stack(cards: List<Card>, glass: Boolean, onAction: (Card, CardAction) -> Unit, onCard: (Card) -> Unit) {
    val c = S.c
    val feel = LocalFeel.current
    val pager = rememberPagerState { cards.size }
    val first = cards.first().key
    LaunchedEffect(first) { if (pager.currentPage != 0) pager.scrollToPage(0) }
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.drop(1).collect { feel.tick() } }
    Box(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(160.dp).clip(RoundedCornerShape(24.dp))
            .background(surface()),
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

