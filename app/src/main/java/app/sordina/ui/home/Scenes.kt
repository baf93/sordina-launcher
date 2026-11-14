package app.sordina.ui.home

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import app.sordina.R
import app.sordina.tr

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sordina.data.Card
import app.sordina.data.CardAction
import app.sordina.ui.LocalFeel
import app.sordina.ui.LocalGlass
import app.sordina.ui.clickableNoRipple
import app.sordina.ui.rememberNow
import app.sordina.ui.surface
import app.sordina.ui.theme.LocalSordina
import app.sordina.ui.theme.S
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

// ── Sera ──────────────────────────────────────────────────────────────────────

/** La sera l'accento si scalda: stessa Casa, luce di fine giornata. */
@Composable
internal fun Evening(content: @Composable () -> Unit) {
    val s = LocalSordina.current
    CompositionLocalProvider(LocalSordina provides s.copy(colors = s.colors.copy(accent = Color(0xFFC98A4B))), content = content)
}

/** Sotto il programma di oggi: domani (sveglia, appuntamenti, tempo), quando comincia la notte, com'è andata oggi. */
@Composable
internal fun TomorrowCorner(state: HomeState, onScene: (String) -> Unit) {
    val c = S.c
    // Allineato a sinistra dentro l'angolo: le icone stanno in colonna
    // Tutto il blocco porta alle sveglie (il meteo si apre già da quello di oggi)
    Column(Modifier.padding(top = 4.dp, start = 12.dp).clickableNoRipple { onScene("alarm") }, horizontalAlignment = Alignment.Start) {
        Text(tr(R.string.tomorrow_cap), Modifier.padding(bottom = 6.dp), style = TextStyle(fontSize = 13.sp, color = c.soft))
        // La sveglia c'è sempre: senza, l'icona spenta; un tocco apre le sveglie
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Alarm, null, Modifier.size(18.dp), tint = if (state.alarm != null) c.accent else c.soft)
            // Senza sveglia, un trattino: si vede che non c'è
            Spacer(Modifier.width(6.dp))
            Text(state.alarm ?: "—", style = TextStyle(fontSize = 18.sp, color = if (state.alarm != null) c.ink else c.soft))
        }
        state.tomorrowWeather?.let { w ->
            // Solo le temperature: il cielo lo dice l'icona
            val temps = w.split(' ').filter { '°' in it || it == "/" }.joinToString(" ").ifBlank { w }
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(weatherIcon(w), null, Modifier.size(18.dp), tint = c.soft)
                Spacer(Modifier.width(6.dp))
                Text(temps, style = TextStyle(fontSize = 15.sp, color = c.soft))
            }
        }
    }
}

/** Sveglia, i primi appuntamenti di domani e il tempo: uguale la sera e la notte. */
@Composable
private fun Tomorrow(state: HomeState, onScene: (String) -> Unit) {
    val c = S.c
    AgendaLine(AgendaRow(state.alarm ?: "—", tr(if (state.alarm != null) R.string.scene_alarm else R.string.scene_no_alarm), "alarm", accent = state.alarm != null)) { onScene("alarm") }
    state.tomorrow.take(2).forEach { r -> AgendaLine(r) { onScene(r.key) } }
    state.tomorrowWeather?.let { w ->
        Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(64.dp)) { Icon(weatherIcon(w), null, Modifier.size(18.dp), tint = c.soft) }
            Text(w, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 17.sp, color = c.ink))
        }
    }
}

// ── Notte ─────────────────────────────────────────────────────────────────────

/** Di notte tutto si spegne: nero pieno, testo smorzato, niente foto né vetro. */
@Composable
private fun Night(content: @Composable () -> Unit) {
    val s = LocalSordina.current
    val night = s.colors.copy(
        bg = Color.Black, ink = Color(0xFFB9B3A9), soft = Color(0xFF6F6A63), line = Color(0x1FFFFFFF),
        accent = Color(0xFFB0783F), tile = Color(0xFF121212), card = Color(0xFF121212), dot = Color(0xFF3A3A3A), dark = true,
    )
    CompositionLocalProvider(LocalSordina provides s.copy(colors = night), LocalGlass provides false, content = content)
}

/** Casa di notte: l'ora grande e quieta, la sveglia, fino a quando resta tutto in sordina, e domani. */
@Composable
internal fun NightHome(
    state: HomeState,
    gestures: Modifier,
    onSearch: () -> Unit,
    onMicDown: () -> Unit,
    onMicUp: () -> Unit,
    onAction: (Card, CardAction) -> Unit,
    onCard: (Card) -> Unit,
    onScene: (String) -> Unit,
    onSheet: () -> Unit,
) = Night {
    val c = S.c
    val now = rememberNow()
    val short = LocalConfiguration.current.screenHeightDp < 700
    Column(Modifier.fillMaxSize().background(c.bg).then(gestures).systemBarsPadding().padding(top = 28.dp, bottom = 12.dp)) {
        val day = now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())).replaceFirstChar { it.uppercase() }
        Text(day, Modifier.padding(horizontal = 28.dp), style = TextStyle(fontSize = 17.sp, color = c.soft))
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(now.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
                style = TextStyle(fontSize = if (short) 72.sp else 88.sp, fontWeight = FontWeight.Light, color = c.ink))
            Row(Modifier.padding(top = 4.dp).clickableNoRipple { onScene("alarm") }, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Alarm, null, Modifier.size(18.dp), tint = if (state.alarm != null) c.accent else c.soft)
                Spacer(Modifier.width(6.dp))
                // Con la sveglia: anche quanto manca ("Sveglia alle 7:00 · tra 7 h 20 min")
                val left = state.alarmAt?.let { at -> ((at - System.currentTimeMillis()) / 60_000L).toInt().coerceAtLeast(0) }
                val text = when {
                    state.alarm == null -> tr(R.string.scene_no_alarm)
                    left != null -> tr(R.string.n_alarm_in, state.alarm, app.sordina.ui.shortDuration(left))
                    else -> tr(R.string.scene_alarm_at, state.alarm)
                }
                Text(text, style = TextStyle(fontSize = 17.sp, color = c.soft))
            }
            state.sceneLine?.let {
                Text(it, Modifier.padding(top = 10.dp), textAlign = TextAlign.Center, style = TextStyle(fontSize = 14.sp, color = c.soft))
            }
        }
        Spacer(Modifier.weight(1f))
        if (state.tomorrow.isNotEmpty() || state.tomorrowWeather != null) Column(Modifier.padding(horizontal = 28.dp)) {
            Text(tr(R.string.scene_tomorrow), Modifier.padding(bottom = 2.dp), style = TextStyle(fontSize = 13.sp, color = c.soft))
            state.tomorrow.take(if (short) 1 else 2).forEach { r -> AgendaLine(r) { onScene(r.key) } }
            state.tomorrowWeather?.let { w ->
                Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(64.dp)) { Icon(weatherIcon(w), null, Modifier.size(18.dp), tint = c.soft) }
                    Text(w, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 17.sp, color = c.ink))
                }
            }
            Spacer(Modifier.height(18.dp))
        }
        if (state.cards.isNotEmpty()) {
            Stack(state.cards, false, onAction, onCard)
            Spacer(Modifier.height(18.dp))
        }
        SearchBar(state, onSearch, onMicDown, onMicUp)
        // La barra sta nello stesso punto di giorno e di sera
        Spacer(Modifier.height(36.dp))
        SordinaRow(state) { onSheet() }
        Spacer(Modifier.height(20.dp))
    }
}

// ── Auto ──────────────────────────────────────────────────────────────────────


/**
 * Casa in auto: tutto grande e a portata di pollice, per guardare un attimo e basta.
 * In alto l'ora; in basso chi chiami di solito, le tue app (navigazione, musica, casa) e un microfono largo:
 * un tocco e parli, si chiude da solo quando smetti. La navigazione in corso arriva come scheda.
 */
@Composable
internal fun CarHome(
    state: HomeState,
    gestures: Modifier,
    @Suppress("UNUSED_PARAMETER") onSearch: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onMicDown: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onMicUp: () -> Unit,
    onAction: (Card, CardAction) -> Unit,
    onCard: (Card) -> Unit,
    onScene: (String) -> Unit,
    onSheet: () -> Unit,
) {
    val c = S.c
    val now = rememberNow()
    val feel = LocalFeel.current
    // Su schermi bassi, con una scheda aperta (musica, navigazione), le persone lasciano il posto
    val roomy = LocalConfiguration.current.screenHeightDp >= 700 || state.cards.isEmpty()
    CompositionLocalProvider(LocalGlass provides false) {
    Column(Modifier.fillMaxSize().background(c.bg).then(gestures).systemBarsPadding().padding(top = 20.dp, bottom = 12.dp)) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text(now.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)), style = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.Light, color = c.ink))
            val line = if (state.listening) state.partial.ifBlank { "…" } else state.sceneLine
            line?.let { Text(it, maxLines = 3, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = if (state.listening) 22.sp else 16.sp, color = if (state.listening) c.ink else c.soft)) }
        }
        // Il prossimo impegno, solo quello: com'è nella Casa di sempre (il giorno sopra, l'ora in accento, il titolo)
        state.next?.takeIf { !state.listening }?.let { e ->
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp)) {
                e.day?.let { Text(it, Modifier.padding(bottom = 2.dp), style = TextStyle(fontSize = 13.sp, color = c.soft)) }
                AgendaLine(e.copy(accent = true)) { feel.tick(); onScene(e.key) }
            }
        }
        Spacer(Modifier.weight(1f))
        if (state.cards.isNotEmpty()) {
            Stack(state.cards, false, onAction, onCard)
            Spacer(Modifier.height(12.dp))
        }
        if (roomy && state.people.isNotEmpty()) {
            // Una fila di nomi interi: un tocco chiama, una pressione lunga la toglie da qui
            androidx.compose.foundation.lazy.LazyRow(
                Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.people.size, key = { state.people[it].second }) { i ->
                    val name = state.people[i].second
                    Row(
                        Modifier.height(64.dp).clip(RoundedCornerShape(32.dp)).background(surface())
                            .pointerInput(name) { detectTapGestures(onTap = { feel.tick(); onScene("call:$name") }, onLongPress = { feel.tick(); onScene("hide:$name") }) }
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(name, maxLines = 1, softWrap = false, style = TextStyle(fontSize = 18.sp, color = c.ink))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Una sola riga: con meno app i pulsanti si allargano fino a coprire tutta la larghezza
            state.carApps.forEach { s -> CarTile(s) { feel.tick(); onScene("app:${s.pkg}") } }
        }
        Spacer(Modifier.height(10.dp))
        // Il microfono: largo quanto lo schermo, si colpisce senza guardare
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(72.dp).clip(RoundedCornerShape(36.dp))
                .background(if (state.listening) c.ink else c.accent).clickableNoRipple { feel.tick(); onScene("mic") },
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Mic, null, Modifier.size(30.dp), tint = c.bg)
            Spacer(Modifier.width(10.dp))
            Text(tr(if (state.listening) R.string.car_listening else R.string.car_hold), maxLines = 1, style = TextStyle(fontSize = 18.sp, color = c.bg))
        }
        Spacer(Modifier.height(14.dp))
        SordinaRow(state) { onSheet() }
        Spacer(Modifier.height(8.dp))
    }
    }
}

@Composable
private fun RowScope.CarTile(s: CarShortcut, onClick: () -> Unit) {
    // Solo l'icona dell'app, grande: il nome resta per chi usa il lettore dello schermo
    Box(
        Modifier.weight(1f).height(76.dp).clip(RoundedCornerShape(24.dp)).background(surface()).clickableNoRipple(onClick)
            .semantics { contentDescription = s.label },
        contentAlignment = Alignment.Center,
    ) {
        s.icon?.let { androidx.compose.foundation.Image(it, null, Modifier.size(44.dp)) }
            ?: Text(s.label.take(1).uppercase(), style = TextStyle(fontSize = 24.sp, color = S.c.accent))
    }
}
