package app.sordina.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sordina.R
import app.sordina.data.Card
import app.sordina.data.CardAction
import app.sordina.data.CardKind
import app.sordina.data.CardLook
import app.sordina.data.CardOp
import app.sordina.data.Kind
import app.sordina.tr
import app.sordina.trn
import app.sordina.ui.LocalFeel
import app.sordina.ui.Motion
import app.sordina.ui.clickableNoRipple
import app.sordina.ui.surface
import app.sordina.ui.theme.S
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/** "Di solito adesso": una cosa che il cervello ti porge, già pronta da toccare. */
data class Usual(val id: String, val title: String, val kind: Kind, val icon: ImageBitmap?, val why: String? = null)

/** Sotto la barra, sempre alta uguale: la barra non si sposta mai, qualunque cosa dica la sordina. */
private val ZONE = 156.dp
private val TAIL = 7.dp

/**
 * In fondo a Casa: la sordina e, quando ha qualcosa da dirti, il suo fumetto.
 * Il fumetto dice la cosa in corso più importante (musica, codice, la casa che chiede…) oppure,
 * se non c'è niente in corso, quello che di solito fai adesso. Senza fumetto la sordina sta al centro;
 * quando parla scivola a sinistra. Il tocco sulla sordina apre sempre le notifiche in attesa.
 *
 * Fumetto: tocco = apre la cosa; tondo = l'azione principale; scorri di lato = via; tieni premuto = le altre azioni.
 */
@Composable
internal fun SordinaZone(
    state: HomeState,
    onAction: (Card, CardAction) -> Unit,
    onCard: (Card) -> Unit,
    onScene: (String) -> Unit,
    onSheet: () -> Unit,
) {
    // La musica non si nasconde per un giorno: scorsa via, torna col brano dopo
    var swiped by remember { mutableStateOf(setOf<String>()) }
    val live = state.cards.filter { liveKey(it) !in swiped }
    val card = live.firstOrNull()
    val usual = if (card == null) state.usual else emptyList()
    val talking = (card != null || usual.isNotEmpty()) && !state.away
    val bias by animateFloatAsState(if (talking) -1f else 0f, Motion.enter(320), label = "mascot")
    val shown by animateFloatAsState(if (talking) 1f else 0f, Motion.enter(260), label = "bubble")
    // Mentre il fumetto se ne va si continua a vedere quello che diceva
    val last = remember { mutableStateOf<Pair<Card?, List<Usual>>>(null to emptyList()) }
    if (talking) last.value = card to usual

    Box(Modifier.fillMaxWidth().height(ZONE).padding(horizontal = 16.dp)) {
        if (shown > 0f) {
            val (k, u) = last.value
            Box(
                Modifier.align(Alignment.CenterStart).padding(start = 52.dp).fillMaxWidth()
                    .graphicsLayer { alpha = shown; translationX = (1f - shown) * -24.dp.toPx() },
            ) {
                if (k != null) androidx.compose.runtime.key(k.key) {
                    CardBubble(k, more = live.size - 1, onAction, onCard) { if (k.kind == CardKind.MUSIC) swiped = swiped + liveKey(k) else onAction(k, CardAction("", CardOp.Hide)) }
                } else if (u.isNotEmpty()) UsualBubble(u, onScene)
            }
        }
        Box(Modifier.align(BiasAlignment(bias, 0f))) { Mascot(state, talking, onSheet) }
    }
}

private fun liveKey(c: Card) = if (c.kind == CardKind.MUSIC) "music|${c.title}" else c.key

/** La sordina: al centro col numero accanto; mentre parla, il numero le sta addosso. */
@Composable
private fun Mascot(state: HomeState, talking: Boolean, onSheet: () -> Unit) {
    val c = S.c
    val on = state.waiting > 0
    val said = (listOf(if (on) trn(R.plurals.n_waiting, state.waiting) else tr(R.string.nothing_waiting)) + listOfNotNull(state.moment)).joinToString(" · ")
    val pose = when {
        state.scene == app.sordina.data.Scene.CAR -> app.sordina.ui.Pose.DRIVE
        state.scene == app.sordina.data.Scene.NIGHT -> app.sordina.ui.Pose.NIGHT
        state.moment != null -> app.sordina.ui.Pose.QUIET
        else -> app.sordina.ui.Pose.AWAKE
    }
    val gone by animateFloatAsState(if (state.away) 0f else 1f, Motion.enter(220), label = "away")
    Row(
        (if (state.translucent) Modifier.clip(RoundedCornerShape(24.dp)).background(surface()).padding(horizontal = if (talking) 4.dp else 16.dp, vertical = 4.dp) else Modifier)
            .heightIn(min = 48.dp).clickableNoRipple(onSheet)
            .semantics(mergeDescendants = true) { contentDescription = said },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            app.sordina.ui.MascotGlyph(pose, c.soft, Modifier.size(40.dp).graphicsLayer { alpha = gone; translationY = (1f - gone) * -24.dp.toPx() })
            if (talking && on) Box(
                Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-2).dp).heightIn(min = 18.dp).widthIn(min = 18.dp)
                    .clip(CircleShape).background(c.accent).padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) { Text("${state.waiting}", style = TextStyle(fontSize = 11.sp, color = c.bg)) }
        }
        if (!talking && on) {
            Spacer(Modifier.width(10.dp))
            Text("${state.waiting}", Modifier.graphicsLayer { alpha = gone }, style = TextStyle(fontSize = 24.sp, color = c.soft))
        }
    }
}

/** Un fumetto che si scorre via di lato. */
@Composable
private fun Swipeable(onGone: () -> Unit, content: @Composable (Modifier) -> Unit) {
    val feel = LocalFeel.current
    val off = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var w by remember { mutableIntStateOf(1) }
    content(
        Modifier.onSizeChanged { w = it.width.coerceAtLeast(1) }
            .graphicsLayer { translationX = off.value; alpha = 1f - (abs(off.value) / w).coerceIn(0f, 1f) * 0.9f }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (abs(off.value) > w * 0.3f) {
                                feel.tick()
                                off.animateTo(sign(off.value) * w, tween(160))
                                onGone()
                            } else off.animateTo(0f, spring(dampingRatio = 0.8f))
                        }
                    },
                    onDragCancel = { scope.launch { off.animateTo(0f) } },
                ) { change, dx -> change.consume(); scope.launch { off.snapTo(off.value + dx) } }
            },
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CardBubble(card: Card, more: Int, onAction: (Card, CardAction) -> Unit, onCard: (Card) -> Unit, onGone: () -> Unit) {
    val c = S.c
    val feel = LocalFeel.current
    var menu by remember { mutableStateOf(false) }
    val primary = primaryOf(card)
    val ask = card.kind == CardKind.ASK
    Swipeable(onGone) { swipe ->
        Box(swipe) {
            Row(
                Modifier.fillMaxWidth().clip(BubbleShape).background(surface())
                    .combinedClickable(onClick = { feel.tick(); onCard(card) }, onLongClick = { feel.tick(); menu = true })
                    .heightIn(min = 84.dp).padding(start = TAIL + 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    if (!ask) Text(
                        if (more > 0) "${card.label} · +$more" else card.label,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 13.sp, color = c.soft),
                    )
                    when (card.look) {
                        CardLook.CODE -> Text(card.title, maxLines = 1, style = TextStyle(fontSize = 28.sp, color = c.ink, fontFamily = FontFamily.Monospace, letterSpacing = 3.sp))
                        CardLook.AMOUNT -> Text(card.title, maxLines = 1, style = TextStyle(fontSize = 24.sp, color = c.ink))
                        else -> Text(card.title, maxLines = if (!ask) 2 else if (card.actions.size > 2) 2 else 3, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = if (ask) 16.sp else 19.sp, lineHeight = if (ask) 21.sp else 24.sp, color = c.ink))
                    }
                    if (!ask) card.subtitle?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 15.sp, color = c.soft)) }
                    // La casa chiede: le sue risposte (fino a tre, col testo di Home Assistant) si dividono tutta la larghezza
                    if (ask) androidx.compose.foundation.layout.FlowRow(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                        card.actions.take(3).forEachIndexed { i, a ->
                            // Ognuna larga quanto le serve, poi si allargano a riempire la riga; se non ci stanno vanno a capo
                            Chip(a.label, filled = i == 0, Modifier.weight(1f)) { feel.confirm(); onAction(card, a) }
                        }
                    }
                    card.bar?.let { b ->
                        Spacer(Modifier.height(10.dp))
                        Track(listOf(b.at(ticking(b.value == null))))
                    }
                    card.step?.let { s -> Spacer(Modifier.height(6.dp)); Track(List(4) { if (it < s) 1f else 0f }) }
                }
                primary?.let { a ->
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).background(c.accent).clickable { feel.confirm(); onAction(card, a) }
                            .semantics { contentDescription = a.label },
                        contentAlignment = Alignment.Center,
                    ) { Icon(iconOf(a), null, Modifier.size(24.dp), tint = c.bg) }
                }
                // La musica ha anche il brano dopo, accanto alla pausa: più quieto, si usa meno
                if (card.kind == CardKind.MUSIC) card.actions.firstOrNull { it.op == CardOp.MusicNext }?.let { a ->
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).background(c.line).clickable { feel.tick(); onAction(card, a) }
                            .semantics { contentDescription = a.label },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.SkipNext, null, Modifier.size(26.dp), tint = c.ink) }
                }
            }
            // Tieni premuto: tutte le azioni, e "nascondi per oggi"
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                val all = card.actions + if (card.kind == CardKind.MUSIC) emptyList() else listOf(CardAction(tr(R.string.hide_today), CardOp.Hide))
                all.forEach { a ->
                    DropdownMenuItem(text = { Text(a.label) }, onClick = { menu = false; onAction(card, a) })
                }
            }
        }
    }
}

/**
 * "Di solito adesso": una proposta alla volta, scritta per intero, col suo perché sopra.
 * Tocco o tondo: la fa. Scorri via: la prossima; dopo l'ultima il fumetto tace fino alla prossima fascia.
 * Tieni premuto: non propormela più.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun UsualBubble(usual: List<Usual>, onScene: (String) -> Unit) {
    val c = S.c
    val feel = LocalFeel.current
    var at by remember(usual.map { it.id }) { mutableIntStateOf(0) }
    val u = usual.getOrNull(at) ?: return
    var menu by remember { mutableStateOf(false) }
    androidx.compose.runtime.key(u.id) {
        Swipeable({ if (at + 1 < usual.size) at++ else onScene("usual-hide") }) { swipe ->
            Box(swipe) {
                Row(
                    Modifier.fillMaxWidth().clip(BubbleShape).background(surface())
                        .combinedClickable(onClick = { feel.tick(); onScene("usual:${u.id}") }, onLongClick = { feel.tick(); menu = true })
                        .heightIn(min = 84.dp).padding(start = TAIL + 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(u.why?.trimEnd('.') ?: tr(R.string.usually_now), maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 13.sp, color = c.soft))
                        Text(u.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 19.sp, lineHeight = 24.sp, color = c.ink))
                    }
                    Spacer(Modifier.width(8.dp))
                    // Il tondo: l'icona dell'app se è un'app, altrimenti che genere di cosa è
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).background(if (u.icon != null) androidx.compose.ui.graphics.Color.Transparent else c.accent)
                            .clickable { feel.confirm(); onScene("usual:${u.id}") }.semantics { contentDescription = u.title },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (u.icon != null) Image(u.icon, null, Modifier.size(48.dp).clip(CircleShape))
                        else Icon(kindIcon(u.kind), null, Modifier.size(24.dp), tint = c.bg)
                    }
                }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(tr(R.string.dont_suggest)) }, onClick = { menu = false; onScene("usual-drop:${u.id}") })
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, filled: Boolean, modifier: Modifier, tight: Boolean = false, onClick: () -> Unit) {
    val c = S.c
    Box(
        modifier.height(36.dp).clip(RoundedCornerShape(18.dp))
            .then(if (filled) Modifier.background(c.accent) else Modifier.border(1.dp, c.line, RoundedCornerShape(18.dp)))
            .clickable(onClick = onClick).padding(horizontal = if (tight) 6.dp else 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 15.sp, color = if (filled) c.bg else c.ink)) }
}

/** L'azione del tondo: quella che fai senza aprire niente. Timer, corse e riunioni non ne hanno: il tocco le apre. */
private fun primaryOf(card: Card): CardAction? = when (card.kind) {
    CardKind.ASK -> null
    CardKind.MUSIC -> card.actions.firstOrNull { it.op == CardOp.MusicToggle }
    else -> card.actions.firstOrNull { a ->
        a.op !is CardOp.OpenNotif && a.op !is CardOp.Done && a.op !is CardOp.Later && a.op !is CardOp.OpenEvent && a.op != CardOp.Dismiss && a.op != CardOp.Hide
    }
}

private fun iconOf(a: CardAction): ImageVector = when (val op = a.op) {
    is CardOp.Copy -> Icons.Rounded.ContentCopy
    CardOp.MusicToggle -> if (a.icon == "pause") Icons.Rounded.Pause else Icons.Rounded.PlayArrow
    is CardOp.Query -> if (op.query.startsWith("chiama")) Icons.Rounded.Call else Icons.Rounded.ChatBubble
    is CardOp.Dial -> Icons.Rounded.Call
    is CardOp.Navigate -> Icons.Rounded.Navigation
    is CardOp.OpenUrl -> Icons.Rounded.Videocam
    is CardOp.AddEvent -> Icons.Rounded.EventAvailable
    CardOp.Fix -> Icons.Rounded.Build
    else -> Icons.Rounded.Check
}

private fun kindIcon(k: Kind): ImageVector = when (k) {
    Kind.PERSON -> Icons.Rounded.Person
    Kind.HOME -> Icons.Rounded.Home
    else -> Icons.Rounded.Bolt
}

/** Il fumetto: un rettangolo tondo con la punta verso la sordina (a sinistra, a destra nelle lingue da destra). */
private object BubbleShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val t = with(density) { TAIL.toPx() }
        val r = with(density) { 18.dp.toPx() }
        val rtl = layoutDirection == LayoutDirection.Rtl
        val body = Path().apply {
            addRoundRect(if (rtl) RoundRect(0f, 0f, size.width - t, size.height, CornerRadius(r)) else RoundRect(t, 0f, size.width, size.height, CornerRadius(r)))
        }
        val cy = size.height / 2
        val tip = Path().apply {
            if (rtl) { moveTo(size.width - t - 2f, cy - t); lineTo(size.width, cy); lineTo(size.width - t - 2f, cy + t) }
            else { moveTo(t + 2f, cy - t); lineTo(0f, cy); lineTo(t + 2f, cy + t) }
            close()
        }
        return Outline.Generic(Path().apply { op(body, tip, PathOperation.Union) })
    }
}

/** Una barra sottile divisa in tratti: quanto manca (codice), quanto è passato (riunione), a che punto è (corsa). */
@Composable
private fun Track(parts: List<Float>) {
    val c = S.c
    Row(Modifier.fillMaxWidth().height(3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        parts.forEach { f ->
            Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(c.line)) {
                // Arancio come il tondo: è la parte viva del fumetto
                if (f > 0f) Box(Modifier.fillMaxWidth(f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(c.accent))
            }
        }
    }
}

/** L'ora che avanza, ogni 10 secondi, solo se la barra ne ha bisogno e solo mentre Casa si vede. */
@Composable
private fun ticking(on: Boolean): Long {
    var now by remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }
    if (on) app.sordina.ui.WhileVisible(10_000) { now = System.currentTimeMillis() }
    return now
}
