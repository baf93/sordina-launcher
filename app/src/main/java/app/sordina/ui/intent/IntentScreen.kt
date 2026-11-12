@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.sordina.ui.intent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sordina.data.AppCatalog
import app.sordina.data.AppEntry
import app.sordina.data.Candidate
import app.sordina.data.Kind
import app.sordina.data.Understanding
import app.sordina.ui.clickableNoRipple
import app.sordina.ui.LocalFeel
import app.sordina.ui.theme.OnBackdrop
import app.sordina.ui.theme.S

/** Azioni sulla singola proposta (pressione lunga). */
data class ProposalActions(
    val isPinned: (AppEntry) -> Boolean,
    val togglePin: (AppEntry) -> Unit,
    val exclude: (AppEntry) -> Unit,
    val info: (AppEntry) -> Unit,
    val why: (AppEntry) -> String,
    val brake: (AppEntry) -> Unit = {},
)

/** Un avviso breve in basso, con Annulla per le azioni verso te stesso. */
data class Notice(val text: String, val undo: (() -> Unit)? = null, val id: Long = System.nanoTime())

/** Stato della voce. */
data class VoiceState(val available: Boolean, val listening: Boolean, val partial: String)

/**
 * Ricerca: solo parole, niente icone (le icone invitano ad aprire).
 * La barra sta in basso, sopra la tastiera, dove arriva il pollice; i risultati salgono da lì.
 * Scorrendo in su si arriva a Tutte le app.
 */
@Composable
fun IntentScreen(
    catalog: AppCatalog,
    query: String,
    onQuery: (String) -> Unit,
    understand: (String) -> Understanding,
    proposals: List<AppEntry>,
    needsUsagePermission: Boolean,
    actions: ProposalActions,
    keyboardOnOpen: Boolean,
    voice: VoiceState,
    notice: Notice?,
    onRun: (Candidate, Int) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onAllApps: () -> Unit,
    onGrantUsage: () -> Unit,
    onVoiceDown: () -> Unit,
    onVoiceUp: () -> Unit,
    topDark: Boolean? = null,
    glass: Boolean = false,
    showIcons: Boolean = false,
) {
    val c = S.c
    val iconPx = with(LocalDensity.current) { 26.dp.roundToPx() }
    val icons = remember { HashMap<String, androidx.compose.ui.graphics.ImageBitmap?>() }
    fun appIcon(a: AppEntry) = if (!showIcons) null else icons.getOrPut(a.key) { catalog.icon(a, iconPx) }
    fun icon(cand: Candidate) = cand.app?.let { appIcon(it) }
    val focus = remember { FocusRequester() }
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    val result = remember(query) { understand(query) }
    val feelTop = LocalFeel.current
    LaunchedEffect(Unit) { if (keyboardOnOpen) runCatching { focus.requestFocus() } }

    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total < -threshold) onAllApps() },
                    onVerticalDrag = { _, dy -> total += dy },
                )
            }
            .systemBarsPadding()
            .imePadding()
            .padding(bottom = 12.dp),
    ) {
        OnBackdrop(topDark) {
            // Tutte le app: un'icona sola, in alto a destra, lontana dal pollice
            // Il titolo della sezione e l'icona sulla stessa riga
            Row(Modifier.fillMaxWidth().padding(start = 28.dp, top = 6.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                if (query.isBlank() && proposals.isNotEmpty()) Text("Di solito adesso", style = TextStyle(fontSize = 22.sp, color = S.c.ink))
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(S.c.ink.copy(alpha = 0.09f))
                        .clickableNoRipple { feelTop.tick(); onAllApps() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Apps, "Tutte le app", Modifier.size(22.dp), tint = S.c.ink) }
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                if (query.isNotBlank()) {
                    val ready = result.candidates.firstOrNull()?.takeIf { result.confident && it.toOthers }
                    val first = result.candidates.firstOrNull()
                    if (ready != null) ReadyBlock(ready) { onRun(ready, 0) }
                    else if (first != null) EnterBlock(first, query, icon(first)) { onRun(first, 0) }
                    // Il resto, diviso per tipo: si capisce cosa è cosa
                    val rest = result.candidates.withIndex().filter { it.index > 0 }
                    KIND_ORDER.forEach { kind ->
                        val group = rest.filter { it.value.kind == kind }
                        if (group.isNotEmpty()) {
                            Section(kindTitle(kind))
                            group.forEach { (i, cand) -> CandidateLine(cand, query, icon(cand)) { onRun(cand, i) } }
                        }
                    }
                } else {
                    if (needsUsagePermission) {
                        Section("Impara le tue abitudini")
                        Text(
                            "Consenti l'accesso all'utilizzo: le proposte nasceranno da cosa apri a ogni ora. Resta tutto sul telefono.",
                            Modifier.clickableNoRipple(onGrantUsage).padding(vertical = 6.dp),
                            style = TextStyle(fontSize = 15.sp, color = S.c.ink),
                        )
                    }
                    if (proposals.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        // A blocchetti, due per riga: comodi col pollice, leggibili su qualsiasi sfondo
                        proposals.chunked(2).forEach { row ->
                            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { app -> ProposalLine(app, actions, appIcon(app), Modifier.weight(1f)) { onLaunch(app) } }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        notice?.let { n -> Box(Modifier.padding(horizontal = 16.dp)) { NoticeBar(n) } }
        Spacer(Modifier.height(8.dp))
        // La barra
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp))
                .background(if (glass) c.card.copy(alpha = 0.78f) else c.card).padding(start = 22.dp, end = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text(
                    if (voice.listening) voice.partial.ifEmpty { "Ti ascolto… lascia per finire" } else "Cosa devi fare?",
                    style = TextStyle(fontSize = 17.sp, color = if (voice.listening) c.ink else c.soft),
                )
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 17.sp, color = c.ink),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { result.candidates.firstOrNull()?.let { onRun(it, 0) } }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (query.isNotEmpty()) Box(Modifier.size(42.dp).clip(CircleShape).clickableNoRipple { onQuery("") }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, "Cancella", Modifier.size(20.dp), tint = c.soft)
            }
            if (voice.available) Mic(voice.listening, onVoiceDown, onVoiceUp)
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title.uppercase(), Modifier.padding(top = 20.dp, bottom = 6.dp),
        style = TextStyle(fontSize = 11.sp, letterSpacing = 1.2.sp, color = S.c.soft))
}

/** Microfono: tieni premuto, parli, rilasci. */
@Composable
private fun Mic(listening: Boolean, onDown: () -> Unit, onUp: () -> Unit) {
    val c = S.c
    val feel = LocalFeel.current
    Box(
        Modifier.size(42.dp).clip(CircleShape).background(if (listening) c.accent else androidx.compose.ui.graphics.Color.Transparent)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    feel.tick()
                    onDown()
                    waitForUpOrCancellation()
                    onUp()
                }
            },
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Rounded.Mic, "Parla", Modifier.size(22.dp), tint = if (listening) c.bg else c.ink) }
}

/** La parte già scritta resta in evidenza, il resto è più tenue. */
private fun highlight(text: String, query: String, strong: androidx.compose.ui.graphics.Color, soft: androidx.compose.ui.graphics.Color): AnnotatedString {
    val q = query.trim()
    val i = if (q.isEmpty()) -1 else text.indexOf(q, ignoreCase = true)
    return buildAnnotatedString {
        if (i < 0) { withStyle(SpanStyle(color = strong)) { append(text) }; return@buildAnnotatedString }
        withStyle(SpanStyle(color = soft)) { append(text.substring(0, i)) }
        withStyle(SpanStyle(color = strong)) { append(text.substring(i, i + q.length)) }
        withStyle(SpanStyle(color = soft)) { append(text.substring(i + q.length)) }
    }
}

/** Azione verso un'altra persona, già pronta: serve il tuo tocco. */
@Composable
private fun ReadyBlock(cand: Candidate, onGo: () -> Unit) {
    val c = S.c
    Section("Pronto, manca il tuo tocco")
    Text(cand.title, style = TextStyle(fontSize = 24.sp, color = c.ink))
    cand.subtitle?.let { Text(it, maxLines = 3, style = TextStyle(fontSize = 15.sp, color = c.soft)) }
    Box(
        Modifier.padding(top = 12.dp).height(46.dp).clip(RoundedCornerShape(23.dp)).background(c.accent)
            .clickableNoRipple(onGo).padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) { Text(if (cand.title.startsWith("Chiama")) "Chiama" else "Apri la chat", style = TextStyle(fontSize = 15.sp, color = androidx.compose.ui.graphics.Color.White)) }
}

private val KIND_ORDER = listOf(Kind.APP, Kind.PERSON, Kind.HOME, Kind.ACTION, Kind.SETTING, Kind.CALC, Kind.WEB)

private fun kindTitle(k: Kind) = when (k) {
    Kind.APP -> "App"
    Kind.PERSON -> "Persone"
    Kind.HOME -> "Casa"
    Kind.ACTION -> "Azioni"
    Kind.SETTING -> "Impostazioni"
    Kind.CALC -> "Calcolo"
    Kind.WEB -> "Sul web"
}

/** Cosa fa Invio, detto in chiaro. */
private fun enterVerb(cand: Candidate): String = when (cand.kind) {
    Kind.APP -> "Apri ${cand.title}"
    Kind.WEB -> cand.title
    else -> cand.title
}

@Composable
private fun AppIcon(icon: androidx.compose.ui.graphics.ImageBitmap?, size: androidx.compose.ui.unit.Dp = 26.dp) {
    if (icon == null) return
    androidx.compose.foundation.Image(icon, null, Modifier.padding(end = 14.dp).size(size))
}

/** Il primo risultato, in un riquadro: è quello che parte con Invio. */
@Composable
private fun EnterBlock(cand: Candidate, query: String, icon: androidx.compose.ui.graphics.ImageBitmap?, onClick: () -> Unit) {
    val c = S.c
    Section("Con Invio")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.card.copy(alpha = 0.7f))
            .combinedClickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(icon, 30.dp)
        Column(Modifier.weight(1f)) {
            Text(
                highlight(enterVerb(cand), query, c.ink, c.ink.copy(alpha = 0.6f)),
                maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 21.sp),
            )
            val sub = cand.subtitle ?: kindTitle(cand.kind).let { if (cand.kind == Kind.APP) "App" else it }
            Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 14.sp, color = c.soft))
        }
        Box(
            Modifier.padding(start = 12.dp).clip(RoundedCornerShape(10.dp)).background(c.accent.copy(alpha = 0.18f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) { Text("Invio ↵", style = TextStyle(fontSize = 12.sp, color = c.accent)) }
    }
}

@Composable
private fun CandidateLine(cand: Candidate, query: String, icon: androidx.compose.ui.graphics.ImageBitmap?, onClick: () -> Unit) {
    val c = S.c
    val web = cand.kind == Kind.WEB
    Row(Modifier.fillMaxWidth().combinedClickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(icon)
        Column(Modifier.weight(1f)) {
            Text(
                if (web) AnnotatedString(cand.title) else highlight(cand.title, query, c.ink, c.ink.copy(alpha = 0.6f)),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontSize = if (web) 16.sp else 20.sp, color = c.soft),
            )
            cand.subtitle?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 14.sp, color = c.soft)) }
        }
    }
}

@Composable
private fun ProposalLine(app: AppEntry, actions: ProposalActions, icon: androidx.compose.ui.graphics.ImageBitmap?, modifier: Modifier, onClick: () -> Unit) {
    val feel = LocalFeel.current
    val c = S.c
    var menu by remember { mutableStateOf(false) }
    var why by remember { mutableStateOf<String?>(null) }
    val pinned = actions.isPinned(app)
    val shape = RoundedCornerShape(18.dp)
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).clip(shape).background(c.ink.copy(alpha = 0.09f))
                .border(0.5.dp, c.ink.copy(alpha = 0.08f), shape)
                .combinedClickable(onClick = { feel.tick(); onClick() }, onLongClick = { feel.tick(); why = null; menu = true })
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(icon, 24.dp)
            Text(app.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 17.sp, color = c.ink))
            if (pinned) Box(Modifier.padding(start = 8.dp).size(6.dp).clip(CircleShape).background(c.accent))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            why?.let { w -> DropdownMenuItem(text = { Text(w) }, onClick = { menu = false }) }
            if (why == null) DropdownMenuItem(text = { Text("Perché è qui?") }, onClick = { why = actions.why(app) })
            DropdownMenuItem(text = { Text(if (pinned) "Non tenere più qui" else "Tieni qui") }, onClick = { menu = false; actions.togglePin(app) })
            DropdownMenuItem(text = { Text("Frena") }, onClick = { menu = false; actions.brake(app) })
            DropdownMenuItem(text = { Text("Non propormela più") }, onClick = { menu = false; actions.exclude(app) })
            DropdownMenuItem(text = { Text("Informazioni app") }, onClick = { menu = false; actions.info(app) })
        }
    }
}

@Composable
private fun NoticeBar(n: Notice) {
    val c = S.c
    var visible by remember(n.id) { mutableStateOf(true) }
    LaunchedEffect(n.id) { kotlinx.coroutines.delay(if (n.undo != null) 6000 else 3500); visible = false }
    if (!visible) return
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(22.dp)).background(c.ink)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(n.text, Modifier.weight(1f), maxLines = 3, style = TextStyle(fontSize = 14.sp, color = c.bg))
        n.undo?.let { u ->
            Text("Annulla", Modifier.clickableNoRipple { visible = false; u() }.padding(start = 12.dp), style = TextStyle(fontSize = 14.sp, color = c.accent))
        }
    }
}
