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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlarmOff
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Snooze
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.animateContentSize
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


/** Sotto la barra, sempre alta uguale: la barra non si sposta mai, qualunque cosa dica la sordina. */
private val ZONE = 156.dp

/** Quanto sta in alto la barra di Casa sopra la barra di sistema: la ricerca la tiene nello stesso punto, comodo per il pollice */
internal val BAR_LIFT = 12.dp + ZONE + 16.dp + 12.dp
private val TAIL = 7.dp

/**
 * In fondo a Casa: la sordina e, quando ha qualcosa da dirti, il suo fumetto.
 * Il fumetto dice la cosa in corso più importante (musica, codice, la casa che chiede…) oppure,
 * se non c'è niente in corso, quello che di solito fai adesso. Senza fumetto la sordina sta al centro;
 * quando parla scivola a sinistra. Il tocco sulla sordina apre sempre le notifiche in attesa.
 *
 * Fumetto: tocco = apre la cosa; tondo = l'azione principale; scorri a sinistra = la cosa dopo, a destra = quella prima
 * (in giro, con "2/3" in alto); tieni premuto = le altre azioni e "nascondi".
 */
@Composable
internal fun SordinaZone(
    state: HomeState,
    onAction: (Card, CardAction) -> Unit,
    onCard: (Card) -> Unit,
    onScene: (String) -> Unit,
    onSheet: () -> Unit,
) {
    // La musica non si nasconde per un giorno: nascosta, torna col brano dopo
    var swiped by remember { mutableStateOf(setOf<String>()) }
    val live = state.cards.filter { liveKey(it) !in swiped }
    // Quella su cui sei andato scorrendo resta finché c'è; se se ne va, si torna alla più importante
    var pick by remember { mutableStateOf<String?>(null) }
    var enter by remember { mutableIntStateOf(0) }
    val at = live.indexOfFirst { it.key == pick }.coerceAtLeast(0)
    val card = live.getOrNull(at)
    // La casa che risponde passa davanti a tutto: è la risposta a quello che hai appena chiesto
    // Mentre parli la sordina è già lì che ascolta, col fumetto di quello che dici; poi la ricerca o la risposta prendono il suo posto
    val heard = if (state.listening || state.partial.isNotEmpty()) state.partial else null
    val reply = if (heard != null) null else state.reply
    val talking = heard != null || reply != null || card != null
    val mood = when {
        heard != null -> if (state.listening) app.sordina.ui.Mood.READY else app.sordina.ui.Mood.WAIT
        reply != null -> if (reply.text == null) app.sordina.ui.Mood.WAIT else if (reply.ok) app.sordina.ui.Mood.DONE else app.sordina.ui.Mood.SIGH
        else -> null
    }
    val bias by animateFloatAsState(if (talking) -1f else 0f, Motion.enter(320), label = "mascot")
    val shown by animateFloatAsState(if (talking) 1f else 0f, Motion.enter(260), label = "bubble")
    // Mentre il fumetto se ne va si continua a vedere quello che diceva
    val last = remember { mutableStateOf(Said(null, null, null)) }
    if (talking) last.value = Said(heard, reply, card)

    // Il fumetto con l'immagine aperta: cresce verso l'alto, sopra la barra, col fondo fermo dov'era
    var open by remember { mutableStateOf<String?>(null) }
    val tall = card != null && card.image != null && card.key == open && heard == null && reply == null

    Box(Modifier.fillMaxWidth().height(ZONE).padding(horizontal = 16.dp)) {
        if (shown > 0f) {
            val (hd, rp, k) = last.value
            Box(
                (if (tall) Modifier.align(Alignment.BottomStart).padding(bottom = 20.dp).wrapContentHeight(Alignment.Bottom, unbounded = true)
                else Modifier.align(Alignment.CenterStart))
                    .padding(start = 52.dp).fillMaxWidth()
                    .graphicsLayer { alpha = shown; translationX = (1f - shown) * -24.dp.toPx(); compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha },
            ) {
                if (hd != null) HeardBubble(hd)
                else if (rp != null) ReplyBubble(rp)
                else if (k != null) androidx.compose.runtime.key(k.key) {
                    CardBubble(k, at, live.size, enter, onAction, onCard, expanded = tall, onExpand = { open = if (it) k.key else null },
                        onStep = { step -> if (live.size > 1) { enter = step; pick = live[(at + step).mod(live.size)].key } },
                        onHide = { pick = null; if (k.kind == CardKind.MUSIC) swiped = swiped + liveKey(k) else onAction(k, CardAction("", CardOp.Hide)) })
                }
            }
        }
        Box(Modifier.align(BiasAlignment(bias, 0f))) { Mascot(state, talking, mood, onSheet) }
    }
}

/** Quello che il fumetto sta dicendo (resta mentre se ne va) */
private data class Said(val heard: String?, val reply: app.sordina.ui.HomeReply?, val card: Card?)

/** Mentre parli: i puntini finché non dici niente, poi le tue parole come le sta capendo. */
@Composable
private fun HeardBubble(text: String) {
    val c = S.c
    Box(
        Modifier.clip(BubbleShape).background(surface())
            .animateContentSize(androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 500f))
            .heightIn(min = 84.dp).padding(start = TAIL + 16.dp, end = 18.dp, top = 12.dp, bottom = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (text.isBlank()) app.sordina.ui.Dots(LocalFeel.current.animations)
        else Text(text, maxLines = 3, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, color = c.ink))
    }
}

/** La casa che risponde: i puntini mentre ci pensa, poi la risposta scritta come se la dicesse. */
@Composable
private fun ReplyBubble(r: app.sordina.ui.HomeReply) {
    val anim = LocalFeel.current.animations
    Box(
        Modifier.clip(BubbleShape).background(surface())
            .animateContentSize(androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 500f))
            .heightIn(min = 84.dp).padding(start = TAIL + 16.dp, end = 18.dp, top = 12.dp, bottom = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (r.text == null) app.sordina.ui.Dots(anim)
        else app.sordina.ui.Typed(r.text, r.id, anim, androidx.compose.ui.text.style.TextAlign.Start, maxLines = 4)
    }
}

private fun liveKey(c: Card) = if (c.kind == CardKind.MUSIC) "music|${c.title}" else c.key

/** La sordina: al centro col numero accanto; mentre parla, il numero le sta addosso. */
@Composable
private fun Mascot(state: HomeState, talking: Boolean, mood: app.sordina.ui.Mood?, onSheet: () -> Unit) {
    val c = S.c
    val on = state.waiting > 0
    val said = (listOf(if (on) trn(R.plurals.n_waiting, state.waiting) else tr(R.string.nothing_waiting)) + listOfNotNull(state.moment)).joinToString(" · ")
    val pose = when {
        state.scene == app.sordina.data.Scene.CAR -> app.sordina.ui.Pose.DRIVE
        state.scene == app.sordina.data.Scene.NIGHT -> app.sordina.ui.Pose.NIGHT
        state.moment != null -> app.sordina.ui.Pose.QUIET
        else -> app.sordina.ui.Pose.AWAKE
    }
    Row(
        (if (state.translucent) Modifier.clip(RoundedCornerShape(24.dp)).background(surface()).padding(horizontal = if (talking) 4.dp else 16.dp, vertical = 4.dp) else Modifier)
            .heightIn(min = 48.dp).clickableNoRipple(onSheet)
            .semantics(mergeDescendants = true) { contentDescription = said },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            // Quando risponde per la casa è quella a colori: concentrata mentre aspetta, contenta o col sospiro dopo
            if (mood != null) app.sordina.ui.Mascot(
                mood,
                Modifier.size(44.dp),
            ) else app.sordina.ui.MascotGlyph(pose, if (pose == app.sordina.ui.Pose.QUIET) c.accent else c.soft, Modifier.size(40.dp))
            if (talking && on) Box(
                Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-2).dp).heightIn(min = 18.dp).widthIn(min = 18.dp)
                    .clip(CircleShape).background(c.accent).padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) { Text("${state.waiting}", style = TextStyle(fontSize = 11.sp, color = c.bg)) }
        }
        if (!talking && on) {
            Spacer(Modifier.width(10.dp))
            Text("${state.waiting}", style = TextStyle(fontSize = 24.sp, color = c.soft))
        }
    }
}

/**
 * Un fumetto fra tanti: scorri a sinistra per quello dopo, a destra per quello prima. Il nuovo entra dal lato
 * opposto a quello verso cui hai spinto il vecchio; se è l'unico, torna al suo posto e basta.
 */
@Composable
private fun Steppable(count: Int, enter: Int, onStep: (Int) -> Unit, content: @Composable (Modifier) -> Unit) {
    val feel = LocalFeel.current
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == LayoutDirection.Rtl
    val inFrom = with(androidx.compose.ui.platform.LocalDensity.current) { 56.dp.toPx() }
    val off = remember { Animatable(enter * inFrom * (if (rtl) -1 else 1)) }
    val scope = rememberCoroutineScope()
    var w by remember { mutableIntStateOf(1) }
    LaunchedEffect(Unit) { off.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 500f)) }
    content(
        Modifier.onSizeChanged { w = it.width.coerceAtLeast(1) }
            // ModulateAlpha: niente livello a parte grande quanto il fumetto, così il simbolino che sporge non viene tagliato
            .graphicsLayer { translationX = off.value; alpha = 1f - (abs(off.value) / w).coerceIn(0f, 1f) * 0.9f; compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha }
            .pointerInput(count) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (count > 1 && abs(off.value) > w * 0.25f) {
                                feel.tick()
                                val out = sign(off.value)
                                off.animateTo(out * w * 0.6f, tween(120))
                                // Spinto a sinistra = il dopo (a destra nelle lingue da destra)
                                val step = if ((out < 0) != rtl) 1 else -1
                                onStep(step)
                            } else off.animateTo(0f, spring(dampingRatio = 0.8f))
                        }
                    },
                    onDragCancel = { scope.launch { off.animateTo(0f) } },
                ) { change, dx ->
                    change.consume()
                    // Da solo si sposta poco: si sente che non c'è altro
                    scope.launch { off.snapTo(off.value + if (count > 1) dx else dx * 0.25f) }
                }
            },
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CardBubble(
    card: Card, at: Int, count: Int, enter: Int,
    onAction: (Card, CardAction) -> Unit, onCard: (Card) -> Unit, expanded: Boolean, onExpand: (Boolean) -> Unit,
    onStep: (Int) -> Unit, onHide: () -> Unit,
) {
    val c = S.c
    val feel = LocalFeel.current
    var menu by remember { mutableStateOf(false) }
    val primary = primaryOf(card)
    val ask = card.kind == CardKind.ASK
    val picture = remember(card.image) { card.image?.asImageBitmap() }
    Steppable(count, enter, onStep) { swipe ->
        Box(swipe) {
            Column(
                Modifier.fillMaxWidth().clip(BubbleShape).background(surface())
                    // Con l'immagine: trascina in su per allargare, in giù per richiudere (di lato resta il passare alla scheda dopo)
                    .then(if (picture == null) Modifier else Modifier.pointerInput(expanded) {
                        val need = 32.dp.toPx()
                        var dy = 0f
                        detectVerticalDragGestures(
                            onDragStart = { dy = 0f },
                            onDragEnd = {
                                if (!expanded && dy < -need) { feel.tick(); onExpand(true) }
                                else if (expanded && dy > need) { feel.tick(); onExpand(false) }
                            },
                        ) { ch, d -> ch.consume(); dy += d }
                    })
                    .combinedClickable(onClick = { feel.tick(); onCard(card) }, onLongClick = { feel.tick(); menu = true })
                    .animateContentSize(Motion.enter(260))
                    .heightIn(min = 84.dp).padding(start = TAIL + 16.dp, end = 12.dp, top = if (picture != null) 0.dp else 12.dp, bottom = 12.dp),
            ) {
            // C'è un'immagine: una freccetta al centro in alto la apre verso l'alto (e, girata, la richiude)
            if (picture != null) Box(
                Modifier.align(Alignment.CenterHorizontally).size(width = 56.dp, height = 20.dp)
                    .clickableNoRipple { feel.tick(); onExpand(!expanded) }.semantics { contentDescription = tr(R.string.open) },
                contentAlignment = Alignment.Center,
            ) { Icon(if (expanded) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowUp, null, Modifier.size(20.dp), tint = c.soft) }
            // Allargato: l'immagine sopra a tutto, larga quanto il fumetto, coi suoi rapporti e mai più alta di così
            if (expanded && picture != null) Image(
                picture, null,
                Modifier.padding(top = 2.dp, bottom = 10.dp).fillMaxWidth().heightIn(max = 340.dp)
                    .aspectRatio((picture.width.toFloat() / picture.height).coerceIn(0.75f, 2.2f))
                    .clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop,
            )
            Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (!ask) Text(
                        if (count > 1) "${card.label} · ${at + 1}/$count" else card.label,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 13.sp, color = c.soft),
                    )
                    when (card.look) {
                        CardLook.CODE -> {
                            // Il codice si vede solo toccandolo (chi guarda il telefono non lo legge); "Copia" non ha bisogno di vederlo
                            var shown by remember(card.key) { mutableStateOf(false) }
                            Text(
                                if (shown) card.title else card.title.map { if (it.isLetterOrDigit()) '•' else it }.joinToString(""),
                                maxLines = 1, modifier = Modifier.clickableNoRipple { shown = !shown },
                                style = TextStyle(fontSize = 28.sp, color = c.ink, fontFamily = FontFamily.Monospace, letterSpacing = 3.sp),
                            )
                        }
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
            }
            // Tieni premuto: il foglio di Sordina con tutte le azioni, e "nascondi"
            if (menu) app.sordina.ui.Sheet(card.title, hint = card.label, onDismiss = { menu = false }) {
                card.actions.forEach { a ->
                    app.sordina.ui.SheetRow(a.label, iconOf(a)) { menu = false; onAction(card, a) }
                }
                // Scorrendo si passa alla prossima: nascondere si fa solo da qui (la musica torna col brano dopo)
                app.sordina.ui.SheetRow(tr(R.string.hide_card), Icons.Rounded.VisibilityOff) { menu = false; onHide() }
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
    is CardOp.OpenUrl, is CardOp.Join -> Icons.Rounded.Videocam
    is CardOp.AddEvent -> Icons.Rounded.EventAvailable
    CardOp.Fix -> Icons.Rounded.Build
    // Un pulsante della notifica (sveglia, timer): l'icona dal suo nome
    is CardOp.Fire -> a.label.lowercase().let { l ->
        when {
            listOf("pausa", "pause").any { it in l } -> Icons.Rounded.Pause
            listOf("riprendi", "resume", "avvia", "start").any { it in l } -> Icons.Rounded.PlayArrow
            listOf("posticipa", "snooze", "rimanda").any { it in l } -> Icons.Rounded.Snooze
            listOf("ignora", "dismiss", "stop", "disattiva", "annulla", "cancel", "elimina", "turn off").any { it in l } -> Icons.Rounded.AlarmOff
            else -> Icons.Rounded.Check
        }
    }
    else -> Icons.Rounded.Check
}

private fun kindIcon(k: Kind): ImageVector = when (k) {
    Kind.PERSON -> Icons.Rounded.Person
    Kind.HOME -> Icons.Rounded.Home
    else -> Icons.Rounded.Bolt
}

/** Il fumetto: un rettangolo tondo con la punta verso la sordina (a sinistra, a destra nelle lingue da destra). */
internal object BubbleShape : Shape {
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
