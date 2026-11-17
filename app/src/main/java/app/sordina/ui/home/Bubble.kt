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
    val tall = card != null && (card.image != null || card.kind == CardKind.ASK) && card.key == open && heard == null && reply == null

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
    // La casa che chiede: se la domanda o una risposta non ci stanno, il fumetto si può allargare verso l'alto
    var cut by remember(card.key) { mutableStateOf(false) }
    var chipsCut by remember(card.key) { mutableStateOf(setOf<Int>()) }
    val grows = ask && (expanded || cut || chipsCut.isNotEmpty())
    Steppable(count, enter, onStep) { swipe ->
        Box(swipe) {
            Column(
                Modifier.fillMaxWidth().clip(BubbleShape).background(surface())
                    // Con l'immagine (o la domanda tagliata): trascina in su per allargare, in giù per richiudere (di lato resta il passare alla scheda dopo)
                    .then(if (picture == null && !grows) Modifier else Modifier.pointerInput(expanded) {
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
                    // A destra 15 + 1 di aria nel tondo = 16, come il testo a sinistra
                    .heightIn(min = 84.dp).padding(start = TAIL + 16.dp, end = 15.dp, top = if (picture != null || grows) 0.dp else 12.dp, bottom = 12.dp),
            ) {
            // C'è un'immagine (o la domanda della casa non ci sta): una freccetta al centro in alto la apre verso l'alto (e, girata, la richiude)
            if (picture != null || grows) Box(
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
            // In testa la firma: l'icona piccola dell'app e il suo nome (la domanda della casa no: le serve tutto lo spazio).
            // I tondi stanno a destra, centrati su tutto il fumetto e non solo sul testo
            if (card.kind == CardKind.MUSIC) MusicRow(card, at, count, onAction)
            else Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Signature(card, at, count)
                    Spacer(Modifier.height(4.dp))
                    when (card.look) {
                        CardLook.CODE -> {
                            // Il codice si vede solo toccandolo (chi guarda il telefono non lo legge); "Copia" non ha bisogno di vederlo
                            var shown by remember(card.key) { mutableStateOf(false) }
                            Text(
                                if (shown) card.title else card.title.map { if (it.isLetterOrDigit()) '•' else it }.joinToString(""),
                                maxLines = 1, modifier = Modifier.clickableNoRipple { shown = !shown },
                                style = TextStyle(fontSize = 26.sp, color = c.ink, fontFamily = FontFamily.Monospace, letterSpacing = 3.sp),
                            )
                        }
                        CardLook.AMOUNT -> Text(card.title, maxLines = 1, style = TextStyle(fontSize = 22.sp, color = c.ink))
                        else -> Text(
                            card.title, maxLines = if (ask && expanded) 12 else 2, overflow = TextOverflow.Ellipsis,
                            onTextLayout = { if (ask && !expanded) cut = it.hasVisualOverflow },
                            style = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, color = c.ink),
                        )
                    }
                    if (!ask) card.subtitle?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, color = c.soft)) }
                    // La casa chiede: le sue risposte (fino a tre, col testo di Home Assistant) si dividono tutta la larghezza
                    // Compatto: su una riga sola, così il fumetto sta nella sua zona anche con tre risposte;
                    // ognuna larga in proporzione al suo testo (con un minimo), in tre un po' più piccole.
                    // Allargato: per intero; se qualcuna era tagliata, una per riga
                    val few = card.actions.take(3)
                    if (ask && expanded && chipsCut.isNotEmpty()) Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        few.forEachIndexed { i, a -> Chip(a.label, filled = i == 0, Modifier.fillMaxWidth()) { feel.confirm(); onAction(card, a) } }
                    }
                    else if (ask && expanded) Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        few.forEachIndexed { i, a -> Chip(a.label, filled = i == 0, Modifier.weight(1f)) { feel.confirm(); onAction(card, a) } }
                    }
                    else if (ask) Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        few.forEachIndexed { i, a ->
                            Chip(
                                a.label, filled = i == 0, Modifier.weight(a.label.length.coerceIn(3, 14) + 4f), tight = few.size > 2,
                                onCut = { off -> chipsCut = if (off) chipsCut + i else chipsCut - i },
                            ) { feel.confirm(); onAction(card, a) }
                        }
                    }
                    card.bar?.let { b ->
                        Spacer(Modifier.height(10.dp))
                        Track(listOf(b.at(ticking(b.value == null))))
                    }
                    card.step?.let { s -> Spacer(Modifier.height(8.dp)); Track(List(4) { if (it < s) 1f else 0f }) }
                }
                primary?.let { a ->
                    Spacer(Modifier.width(4.dp))
                    Round(iconOf(a), a.label, main = true) { feel.confirm(); onAction(card, a) }
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

/**
 * La firma del fumetto: l'icona piccola dell'app e il suo nome, quieti, in testa. Se sono più d'uno, "2/3".
 */
@Composable
private fun Signature(card: Card, at: Int, count: Int) {
    val c = S.c
    Row(Modifier.fillMaxWidth().height(18.dp), verticalAlignment = Alignment.CenterVertically) {
        appIcon(card.pkg, 14.dp)?.let { ic ->
            Image(ic, null, Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(card.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            style = TextStyle(fontSize = 12.sp, letterSpacing = 0.2.sp, color = c.soft))
        if (count > 1) Text(" · ${at + 1}/$count", maxLines = 1, style = TextStyle(fontSize = 12.sp, color = c.soft))
    }
}

/**
 * Un comando del fumetto, tondo: quello principale pieno col colore di Sordina, gli altri più piccoli su fondo chiaro.
 * Lo spazio per il pollice è sempre 44.
 */
@Composable
private fun Round(icon: ImageVector, label: String, main: Boolean, onClick: () -> Unit) {
    val c = S.c
    Box(Modifier.size(44.dp).clickableNoRipple(onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(if (main) 42.dp else 34.dp).clip(CircleShape).background(if (main) c.accent else c.line),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(if (main) 22.dp else 18.dp), tint = if (main) c.bg else c.ink) }
    }
}

/**
 * La musica: il brano (fino a due righe, i titoli possono essere lunghi) e l'artista su una riga;
 * pausa nel tondo pieno, il brano dopo in quello chiaro. In pausa il brano si fa più quieto.
 */
@Composable
private fun MusicRow(card: Card, at: Int, count: Int, onAction: (Card, CardAction) -> Unit) {
    val c = S.c
    val feel = LocalFeel.current
    val toggle = card.actions.firstOrNull { it.op == CardOp.MusicToggle }
    val next = card.actions.firstOrNull { it.op == CardOp.MusicNext }
    val paused = toggle?.icon == "play"
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Signature(card, at, count)
            Spacer(Modifier.height(4.dp))
            Text(card.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, color = if (paused) c.soft else c.ink))
            card.subtitle?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(1.dp))
                Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, color = c.soft))
            }
        }
        Spacer(Modifier.width(4.dp))
        toggle?.let { a -> Round(if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, a.label, main = true) { feel.confirm(); onAction(card, a) } }
        next?.let { a -> Round(Icons.Rounded.SkipNext, a.label, main = false) { feel.tick(); onAction(card, a) } }
    }
}

/** Icone delle app nei fumetti, già pronte: tornando a Casa si vedono subito. */
private val ICONS = java.util.concurrent.ConcurrentHashMap<String, ImageBitmap>()

/** L'icona dell'app [pkg], caricata fuori dal thread principale; null finché non c'è (o se l'app non esiste). */
@Composable
private fun appIcon(pkg: String?, size: androidx.compose.ui.unit.Dp): ImageBitmap? {
    if (pkg == null) return null
    val pm = androidx.compose.ui.platform.LocalContext.current.packageManager
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { size.roundToPx() }
    val key = "$pkg@$px"
    val icon by androidx.compose.runtime.produceState(ICONS[key], key) {
        if (value == null) value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            app.sordina.data.AppIcons.forPackage(pm, pkg, px)
        }?.also { ICONS[key] = it }
    }
    return icon
}

@Composable
private fun Chip(label: String, filled: Boolean, modifier: Modifier, tight: Boolean = false, onCut: (Boolean) -> Unit = {}, onClick: () -> Unit) {
    val c = S.c
    Box(
        modifier.height(34.dp).clip(RoundedCornerShape(17.dp))
            .then(if (filled) Modifier.background(c.accent) else Modifier.border(1.dp, c.line, RoundedCornerShape(17.dp)))
            .clickable(onClick = onClick).padding(horizontal = if (tight) 8.dp else 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, onTextLayout = { onCut(it.hasVisualOverflow) }, style = TextStyle(fontSize = if (tight) 14.sp else 15.sp, color = if (filled) c.bg else c.ink)) }
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
