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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
/** Una scorciatoia in auto: l'app, il suo nome (per il lettore dello schermo), la sua icona */
data class CarShortcut(val pkg: String, val label: String, val icon: androidx.compose.ui.graphics.ImageBitmap?)

/** Una persona in auto: il nome corto sotto la faccia, quello intero per chiamare, la foto della rubrica se c'è */
data class CarPerson(val short: String, val full: String, val photo: androidx.compose.ui.graphics.ImageBitmap?)

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
    /** Quanto forte stai parlando, 0..1 (solo mentre ascolta) */
    val level: Float = 0f,
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
    /** Auto: le quattro persone, scelte da te o quelle che chiami di più */
    val people: List<CarPerson> = emptyList(),
    /** Auto: fino a tre app scelte da te, con la loro icona */
    val carApps: List<CarShortcut> = emptyList(),
    /** Notte: quando suona la sveglia, per dire quanto manca */
    val alarmAt: Long? = null,
    /** Auto: solo il prossimo impegno; [AgendaRow.key] è "nav:luogo" se ha un indirizzo */
    val next: AgendaRow? = null,
    /** La sordina risponde per la casa: il suo fumetto in fondo dice "…" e poi la risposta */
    val reply: app.sordina.ui.HomeReply? = null,
)

/** Casa: ora, data, meteo, evento; la barra; in fondo la sordina col suo fumetto. */
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
    /** Doppio tocco sul fondo: spegne lo schermo (null = spento nelle impostazioni) */
    onDoubleTap: (() -> Unit)? = null,
) {
    val gestures = Modifier.homeGestures(onSwipeUp, onSwipeDown, onLongPress, onDoubleTap)
    when (state.scene) {
        // In auto niente pressione lunga: si toccano pulsanti grandi, le impostazioni non devono aprirsi per sbaglio
        Scene.CAR -> { CarHome(state, Modifier.homeGestures(onSwipeUp, onSwipeDown, {}), onSearch, onMicDown, onMicUp, onAction, onCard, onScene) { onSheet(true) }; return }
        Scene.NIGHT -> { NightHome(state, gestures, onSearch, onMicDown, onMicUp, onAction, onCard, onScene) { onSheet(true) }; return }
        else -> Unit
    }
    // La sera Casa resta quella di sempre, con lo stesso colore: la riconosci dalla sordina e da "domani" nell'angolo
    DayHome(state, gestures, onSearch, onMicDown, onMicUp, onAction, onCard, onAgenda, onScene, onSheet)
}

/** Su: le app. Giù: la tendina. Tieni premuto: le impostazioni. Doppio tocco: spegne (se scelto). Uguale in tutte le Case. */
@Composable
internal fun Modifier.homeGestures(onSwipeUp: () -> Unit, onSwipeDown: () -> Unit, onLongPress: () -> Unit, onDoubleTap: (() -> Unit)? = null): Modifier {
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    val double = androidx.compose.runtime.rememberUpdatedState(onDoubleTap)
    return this
        .pointerInput(Unit) {
            var total = 0f
            detectVerticalDragGestures(
                onDragStart = { total = 0f },
                onDragEnd = { if (total < -threshold) onSwipeUp() else if (total > threshold) onSwipeDown() },
                onVerticalDrag = { _, dy -> total += dy },
            )
        }
        // Il doppio tocco si riconosce solo se è acceso: altrimenti nessuna attesa in più sui tocchi
        .pointerInput(onDoubleTap != null) {
            detectTapGestures(onLongPress = { onLongPress() }, onDoubleTap = if (onDoubleTap != null) ({ double.value?.invoke() }) else null)
        }
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
            Row {
                Column(Modifier.weight(1f)) {
                    Text(dayName, style = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, color = c.ink))
                    Text(date, style = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, color = c.soft))
                }
                // La sera, in alto a destra: la prossima sveglia e il tempo di domani. Niente altro.
                if (state.scene == Scene.EVENING) TomorrowCorner(state, onScene)
            }
            state.weather?.let { w ->
                Row(Modifier.padding(top = 10.dp).clickableNoRipple { onAgenda("weather") }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(weatherIcon(w), null, Modifier.size(20.dp), tint = c.ink)
                    Spacer(Modifier.width(8.dp))
                    Text(w, style = TextStyle(fontSize = 18.sp, color = c.ink))
                }
            }
            // La sera, sotto il meteo e prima degli impegni: com'è andata oggi. Tocco: le statistiche del telefono
            state.recap?.let {
                Text(it, Modifier.padding(top = 10.dp).clickableNoRipple { onScene("usage") }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 15.sp, color = c.soft))
            }
            if (state.agenda.isNotEmpty()) Spacer(Modifier.height(24.dp))
            state.agenda.forEachIndexed { i, r ->
                if (r.day != null && r.day != state.agenda.getOrNull(i - 1)?.day) Text(r.day, Modifier.padding(top = if (i == 0) 0.dp else 10.dp, bottom = 2.dp), style = TextStyle(fontSize = 13.sp, color = c.soft))
                AgendaLine(r) { onAgenda(r.key) }
            }
        } }
        Spacer(Modifier.weight(1f))
        SearchBar(state, onSearch, onMicDown, onMicUp)
        // La barra sta sempre allo stesso posto, in ogni momento: sotto, la zona della sordina è alta uguale con o senza fumetto
        Spacer(Modifier.height(12.dp))
        SordinaZone(state, onAction, onCard, onScene) { onSheet(true) }
        Spacer(Modifier.height(16.dp))
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
    // Mentre tieni premuto il microfono cresce oltre la barra (il dito non lo copre) e respira con la tua voce
    val grow by animateFloatAsState(if (state.listening) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "mic")
    val level by animateFloatAsState(if (state.listening) state.level else 0f, tween(90), label = "level")
    Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(56.dp)) {
        Row(
            Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp))
                .background(surface()).clickable(onClick = onSearch).padding(start = 22.dp, end = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(tr(if (state.listening) R.string.home_listening else R.string.home_search), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontSize = 16.sp, color = if (state.listening) c.ink else c.soft))
            Box(
                Modifier.size(42.dp)
                    // Il tocco resta al microfono: se arrivasse anche alla barra, al rilascio si aprirebbe la ricerca
                    .pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume(); feel.tick(); onMicDown(); waitForUpOrCancellation()?.consume(); onMicUp() } },
                contentAlignment = Alignment.Center,
            ) {
                if (grow < 0.5f) Icon(Icons.Rounded.Mic, tr(R.string.speak), Modifier.size(22.dp), tint = c.accent)
            }
        }
        if (grow > 0.01f) Box(
            // Sul microfono, appena spostato verso l'interno così resta tutto nello schermo: un cerchio pieno da 88 dp, con un alone che segue la voce
            Modifier.align(Alignment.CenterEnd).offset(x = 28.dp).requiredSize(88.dp + 36.dp)
                .graphicsLayer { scaleX = grow; scaleY = grow; alpha = grow },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.requiredSize(88.dp + 36.dp * level).clip(CircleShape).background(c.accent.copy(alpha = 0.25f)))
            Box(Modifier.requiredSize(88.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Mic, null, Modifier.size(34.dp), tint = c.bg)
            }
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
