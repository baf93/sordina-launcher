package app.sordina.ui.home

import app.sordina.R
import app.sordina.tr

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
internal fun EveningBlock(state: HomeState, onScene: (String) -> Unit) {
    val c = S.c
    Text(tr(R.string.scene_tomorrow), Modifier.padding(top = 20.dp, bottom = 2.dp), style = TextStyle(fontSize = 13.sp, color = c.soft))
    Tomorrow(state, onScene)
    state.sceneLine?.let { Text(it, Modifier.padding(top = 12.dp), style = TextStyle(fontSize = 14.sp, color = c.soft)) }
    state.recap?.let { Text(it, Modifier.padding(top = 2.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 14.sp, color = c.soft)) }
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
                Text(state.alarm?.let { tr(R.string.scene_alarm_at, it) } ?: tr(R.string.scene_no_alarm), style = TextStyle(fontSize = 17.sp, color = c.soft))
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
        Spacer(Modifier.height(28.dp))
        SordinaRow(state) { onSheet() }
        Spacer(Modifier.height(20.dp))
    }
}

// ── Auto ──────────────────────────────────────────────────────────────────────

/**
 * Casa in auto: tutto grande e a portata di pollice, per guardare un attimo e basta.
 * Dove vai, la musica, chi chiami di solito, e al centro il microfono.
 */
@Composable
internal fun CarHome(
    state: HomeState,
    gestures: Modifier,
    onSearch: () -> Unit,
    onMicDown: () -> Unit,
    onMicUp: () -> Unit,
    onAction: (Card, CardAction) -> Unit,
    onCard: (Card) -> Unit,
    onScene: (String) -> Unit,
    onSheet: () -> Unit,
) {
    val c = S.c
    val now = rememberNow()
    val feel = LocalFeel.current
    // Su schermi bassi, con la musica in corso, le persone lasciano il posto
    val roomy = LocalConfiguration.current.screenHeightDp >= 700 || state.cards.isEmpty()
    CompositionLocalProvider(LocalGlass provides false) {
    Column(Modifier.fillMaxSize().background(c.bg).then(gestures).systemBarsPadding().padding(top = 20.dp, bottom = 12.dp)) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text(now.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)), style = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.Light, color = c.ink))
            val line = if (state.listening) state.partial.ifBlank { tr(R.string.home_listening) } else state.sceneLine
            line?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = if (state.listening) 20.sp else 16.sp, color = if (state.listening) c.ink else c.soft)) }
        }
        state.destination?.let { (title, place) ->
            Row(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp).fillMaxWidth().height(76.dp).clip(RoundedCornerShape(24.dp))
                    .background(surface()).clickableNoRipple { feel.tick(); onScene("nav:$place") }.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Navigation, null, Modifier.size(28.dp), tint = c.accent)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr(R.string.car_to, title), maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 18.sp, color = c.ink))
                    Text(place, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 14.sp, color = c.soft))
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (state.cards.isNotEmpty()) {
            Stack(state.cards, false, onAction, onCard)
            Spacer(Modifier.height(12.dp))
        }
        if (roomy && state.people.isNotEmpty()) {
            Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                state.people.take(4).forEach { name ->
                    Column(
                        Modifier.weight(1f).height(92.dp).clip(RoundedCornerShape(22.dp)).background(surface())
                            .clickableNoRipple { feel.tick(); onScene("call:$name") }.padding(horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(c.line), contentAlignment = Alignment.Center) {
                            Text(name.trim().take(1).uppercase(), style = TextStyle(fontSize = 18.sp, color = c.ink))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(name.substringBefore(' '), maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 14.sp, color = c.ink))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        // In basso: navigazione, microfono grande al centro, musica
        Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            CarTile(Icons.Rounded.Navigation, tr(R.string.car_navigate)) { feel.tick(); onScene("nav") }
            Box(
                Modifier.size(84.dp).clip(CircleShape).background(c.accent)
                    .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(); feel.tick(); onMicDown(); waitForUpOrCancellation(); onMicUp() } },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Mic, tr(R.string.speak), Modifier.size(34.dp), tint = c.bg) }
            CarTile(Icons.Rounded.MusicNote, tr(R.string.car_music)) { feel.tick(); onScene("music") }
        }
        Text(tr(R.string.car_hold), Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.Center, style = TextStyle(fontSize = 13.sp, color = c.soft))
        Spacer(Modifier.height(14.dp))
        SordinaRow(state) { onSheet() }
        Spacer(Modifier.height(8.dp))
    }
    }
}

@Composable
private fun RowScope.CarTile(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = S.c
    Column(
        Modifier.weight(1f).height(84.dp).clip(RoundedCornerShape(24.dp)).background(surface()).clickableNoRipple(onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, Modifier.size(28.dp), tint = c.accent)
        Spacer(Modifier.height(4.dp))
        Text(label, maxLines = 1, style = TextStyle(fontSize = 14.sp, color = c.ink))
    }
}
