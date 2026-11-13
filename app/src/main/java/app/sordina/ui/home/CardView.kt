package app.sordina.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhoneMissed
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sordina.data.Card
import app.sordina.data.CardAction
import app.sordina.data.CardKind
import app.sordina.data.CardLook
import app.sordina.ui.LocalFeel
import app.sordina.ui.theme.S
import kotlinx.coroutines.delay

/** Il simbolo piccolo in alto a sinistra: dice che tipo di cosa è, senza parole. */
private fun glyph(kind: CardKind): ImageVector = when (kind) {
    CardKind.HEALTH -> Icons.Rounded.HealthAndSafety
    CardKind.OTP -> Icons.Rounded.Key
    CardKind.ASK -> Icons.Rounded.Home
    CardKind.CALL -> Icons.Rounded.PhoneMissed
    CardKind.RIDE -> Icons.Rounded.NearMe
    CardKind.EVENT -> Icons.Rounded.CalendarToday
    CardKind.PLAN -> Icons.Rounded.EventAvailable
    CardKind.TIMER -> Icons.Rounded.Timer
    CardKind.REMINDER -> Icons.Rounded.TaskAlt
    CardKind.PAYMENT -> Icons.Rounded.CreditCard
    CardKind.MUSIC -> Icons.Rounded.MusicNote
    CardKind.TICKET -> Icons.Rounded.ConfirmationNumber
    CardKind.PARCEL -> Icons.Rounded.Inventory2
    CardKind.RAIN -> Icons.Rounded.Umbrella
    CardKind.BIRTHDAY -> Icons.Rounded.Cake
    CardKind.HABIT -> Icons.Rounded.Repeat
}

/**
 * Una scheda: in alto simbolo e etichetta, al centro la cosa (titolo, codice, importo o tratta),
 * sotto eventuale barra o passi, in fondo i pulsanti tutti della stessa larghezza.
 * Tocco: apre. Pressione lunga: toglie la scheda per oggi.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun CardView(card: Card, onAction: (Card, CardAction) -> Unit, onCard: (Card) -> Unit) {
    val c = S.c
    val feel = LocalFeel.current
    Column(
        Modifier.fillMaxSize().combinedClickable(
            onClick = { feel.tick(); onCard(card) },
            onLongClick = { onAction(card, CardAction("", app.sordina.data.CardOp.Hide)) },
        )
            // A destra resta posto per i puntini della pila
            .padding(start = 20.dp, end = 28.dp, top = 16.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(glyph(card.kind), null, Modifier.size(15.dp), tint = c.soft)
            Spacer(Modifier.width(6.dp))
            Text(card.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 12.sp, color = c.soft))
        }
        Spacer(Modifier.height(6.dp))
        if (card.avatar) Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(c.line), contentAlignment = Alignment.Center) {
                Text(card.title.trim().take(1).uppercase(), style = TextStyle(fontSize = 17.sp, color = c.ink))
            }
            Spacer(Modifier.width(12.dp))
            Column { Title(card); Subtitle(card) }
        } else {
            Title(card)
            Subtitle(card)
        }
        card.bar?.let { b ->
            Spacer(Modifier.height(8.dp))
            val now = ticking(b.value == null)
            Track(listOf(b.at(now)))
        }
        card.step?.let { s ->
            Spacer(Modifier.height(8.dp))
            Track(List(4) { if (it < s) 1f else 0f })
        }
        Spacer(Modifier.weight(1f))
        Actions(card, onAction)
    }
}

@Composable
private fun Title(card: Card) {
    val c = S.c
    when (card.look) {
        CardLook.CODE -> Text(card.title, maxLines = 1, style = TextStyle(fontSize = 30.sp, color = c.ink, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp))
        CardLook.AMOUNT -> Text(card.title, maxLines = 1, style = TextStyle(fontSize = 28.sp, color = c.ink, fontWeight = FontWeight.Medium))
        CardLook.ROUTE -> {
            val parts = card.title.split(" → ", limit = 2)
            Text(
                buildAnnotatedString {
                    append(parts[0])
                    if (parts.size == 2) { withStyle(SpanStyle(color = c.soft)) { append("  →  ") }; append(parts[1]) }
                },
                maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 22.sp, color = c.ink),
            )
        }
        CardLook.TITLE -> Text(card.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 21.sp, color = c.ink))
    }
}

@Composable
private fun Subtitle(card: Card) {
    card.subtitle?.let {
        Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 14.sp, color = S.c.soft))
    }
}

/** Una barra sottile divisa in tratti (uno solo per l'avanzamento, quattro per i passi del pacco). */
@Composable
private fun Track(parts: List<Float>) {
    val c = S.c
    Row(Modifier.fillMaxWidth().height(3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        parts.forEach { f ->
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(c.line)) {
                if (f > 0f) Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(c.ink))
            }
        }
    }
}

/** L'ora che avanza, ogni 10 secondi, solo se la barra ne ha bisogno. */
@Composable
private fun ticking(on: Boolean): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    if (on) LaunchedEffect(Unit) { while (true) { delay(10_000); now = System.currentTimeMillis() } }
    return now
}

/** I pulsanti: tutti larghi uguali su una riga; la musica ha i suoi tre tondi al centro. */
@Composable
private fun Actions(card: Card, onAction: (Card, CardAction) -> Unit) {
    val feel = LocalFeel.current
    if (card.actions.isEmpty()) return
    if (card.actions.any { it.icon != null }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            card.actions.forEach { a ->
                RoundIcon(a.icon ?: "play", primary = a.icon == "pause" || a.icon == "play") { feel.tick(); onAction(card, a) }
            }
        }
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        card.actions.forEachIndexed { i, a ->
            ActionButton(a.label, primary = i == 0, Modifier.weight(1f)) { feel.confirm(); onAction(card, a) }
        }
    }
}

@Composable
private fun ActionButton(label: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = S.c
    Box(
        modifier.height(38.dp).clip(RoundedCornerShape(19.dp)).background(if (primary) c.ink else c.line)
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 15.sp, color = if (primary) c.bg else c.ink))
    }
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
        Modifier.size(42.dp).clip(CircleShape).background(if (primary) c.ink else c.line).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(v, null, Modifier.size(22.dp), tint = if (primary) c.bg else c.ink) }
}
