@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.sordina.ui.intent

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sordina.data.AppCatalog
import app.sordina.data.AppEntry
import app.sordina.data.Candidate
import app.sordina.data.Kind
import app.sordina.data.Understanding
import app.sordina.ui.Cap
import app.sordina.ui.Primary
import app.sordina.ui.Serif
import app.sordina.ui.clickableNoRipple
import app.sordina.ui.rememberNow
import app.sordina.ui.shortLabel
import app.sordina.ui.theme.S

/** Azioni sulla singola proposta (pressione lunga). */
data class ProposalActions(
    val isPinned: (AppEntry) -> Boolean,
    val togglePin: (AppEntry) -> Unit,
    val exclude: (AppEntry) -> Unit,
    val info: (AppEntry) -> Unit,
    val why: (AppEntry) -> String,
)

/** Un avviso breve in basso, con Annulla per le azioni verso te stesso. */
data class Notice(val text: String, val undo: (() -> Unit)? = null, val id: Long = System.nanoTime())

/** Stato della voce. */
data class VoiceState(val available: Boolean, val listening: Boolean, val partial: String)

/**
 * Intenzione: la barra in alto, senza tastiera; sotto le sei proposte.
 * Toccando la barra si scrive, tenendo premuto il microfono si parla; scorrendo ancora in su si arriva a Tutte le app.
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
) {
    val c = S.c
    val focus = remember { FocusRequester() }
    val now = rememberNow()
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    val result = remember(query) { understand(query) }
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
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        Cap(now.shortLabel())
        Spacer(Modifier.height(10.dp))
        // Barra delle intenzioni
        Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Serif(
                    if (voice.listening) (voice.partial.ifEmpty { "Ti ascolto…" }) else "Cosa vuoi fare?",
                    size = 19.sp, color = if (voice.listening) c.accent else c.soft, italic = true,
                )
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = S.style.serif, fontSize = 19.sp, color = c.ink),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        result.candidates.firstOrNull()?.let { onRun(it, 0) }
                    }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (query.isNotEmpty()) {
                Serif("×", Modifier.clickableNoRipple { onQuery("") }.padding(horizontal = 10.dp), size = 22.sp, color = c.soft)
            }
            if (voice.available) Mic(voice.listening, onVoiceDown, onVoiceUp)
        }
        Box(Modifier.fillMaxWidth().height(1.5.dp).background(c.ink))
        Spacer(Modifier.height(16.dp))

        if (query.isNotBlank()) {
            result.candidates.forEachIndexed { i, cand ->
                if (i == 0 && result.confident && cand.toOthers) ReadyCard(cand) { onRun(cand, 0) }
                else CandidateRow(catalog, cand, highlight = i == 0 && cand.kind != Kind.WEB) { onRun(cand, i) }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.weight(1f))
        } else {
            if (needsUsagePermission) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.card)
                        .border(1.dp, c.line, RoundedCornerShape(14.dp))
                        .combinedClickable(onClick = onGrantUsage)
                        .padding(14.dp),
                ) {
                    Cap("Impara le tue abitudini")
                    Spacer(Modifier.height(6.dp))
                    Serif("Consenti l'accesso all'utilizzo: le proposte nasceranno da cosa apri a ogni ora. Resta tutto sul telefono.", size = 14.sp)
                }
                Spacer(Modifier.height(12.dp))
            }
            ProposalGrid(catalog, proposals, actions, onLaunch)
            Spacer(Modifier.weight(1f))
            Serif(
                "Tutte le app ↑",
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .combinedClickable(onClick = onAllApps)
                    .padding(12.dp),
                size = 14.sp, color = c.soft, italic = true,
            )
        }
        notice?.let { n -> NoticeBar(n) }
    }
}

/** Microfono: tieni premuto, parli, rilasci. */
@Composable
private fun Mic(listening: Boolean, onDown: () -> Unit, onUp: () -> Unit) {
    val c = S.c
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(50))
            .background(if (listening) c.accent else c.tile)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    onDown()
                    waitForUpOrCancellation()
                    onUp()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        MicGlyph(if (listening) c.bg else c.ink)
    }
}

@Composable
private fun MicGlyph(color: androidx.compose.ui.graphics.Color) {
    androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()
        drawRoundRect(color, topLeft = androidx.compose.ui.geometry.Offset(w * 0.34f, 0f),
            size = androidx.compose.ui.geometry.Size(w * 0.32f, h * 0.62f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.16f))
        drawArc(color, 0f, 180f, false, topLeft = androidx.compose.ui.geometry.Offset(w * 0.18f, h * 0.22f),
            size = androidx.compose.ui.geometry.Size(w * 0.64f, h * 0.56f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
        drawLine(color, androidx.compose.ui.geometry.Offset(w / 2, h * 0.78f), androidx.compose.ui.geometry.Offset(w / 2, h), stroke)
    }
}

/** Azione verso un'altra persona, già pronta: serve il tuo tocco. */
@Composable
private fun ReadyCard(cand: Candidate, onGo: () -> Unit) {
    val c = S.c
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.card)
            .border(1.dp, c.line, RoundedCornerShape(16.dp)).padding(14.dp),
    ) {
        Cap("Pronto, manca il tuo tocco")
        Spacer(Modifier.height(6.dp))
        Serif(cand.title, size = 20.sp)
        cand.subtitle?.let { Serif(it, size = 14.sp, color = c.soft, italic = true, maxLines = 3) }
        Spacer(Modifier.height(10.dp))
        Primary(if (cand.title.startsWith("Chiama")) "Chiama" else "Apri la chat", Modifier.fillMaxWidth(), onGo)
    }
}

@Composable
private fun CandidateRow(catalog: AppCatalog, cand: Candidate, highlight: Boolean, onClick: () -> Unit) {
    val c = S.c
    val sizePx = with(LocalDensity.current) { 22.dp.roundToPx() }
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (cand.kind == Kind.WEB) c.bg.copy(alpha = 0f) else c.tile)
            .let { if (highlight) it.border(1.dp, c.accent.copy(alpha = 0.5f), shape) else it }
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val app = cand.app
        if (app != null) {
            val icon = remember(app.key) { catalog.icon(app, sizePx) }
            if (icon != null) Image(icon, null, Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Serif(cand.title, size = 16.sp, color = if (cand.kind == Kind.WEB) c.soft else c.ink, italic = cand.kind == Kind.WEB, maxLines = 2)
            cand.subtitle?.let { Serif(it, size = 13.sp, color = c.soft, italic = true, maxLines = 2) }
        }
        if (cand.kind != Kind.APP && cand.kind != Kind.WEB) Cap(cand.kind.label, Modifier.padding(start = 8.dp), color = c.soft)
    }
}

@Composable
private fun NoticeBar(n: Notice) {
    val c = S.c
    var visible by remember(n.id) { mutableStateOf(true) }
    LaunchedEffect(n.id) { kotlinx.coroutines.delay(if (n.undo != null) 6000 else 3500); visible = false }
    if (!visible) return
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).background(c.ink)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Serif(n.text, Modifier.weight(1f), size = 14.sp, color = c.bg, maxLines = 3)
        n.undo?.let { u ->
            Serif("Annulla", Modifier.clickableNoRipple { visible = false; u() }.padding(start = 12.dp), size = 14.sp, color = c.accent)
        }
    }
}

@Composable
private fun ProposalGrid(
    catalog: AppCatalog,
    proposals: List<AppEntry>,
    actions: ProposalActions,
    onLaunch: (AppEntry) -> Unit,
) {
    proposals.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { app -> ProposalTile(catalog, app, actions, Modifier.weight(1f)) { onLaunch(app) } }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ProposalTile(
    catalog: AppCatalog,
    app: AppEntry,
    actions: ProposalActions,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val c = S.c
    var menu by remember { mutableStateOf(false) }
    var why by remember { mutableStateOf<String?>(null) }
    val sizePx = with(LocalDensity.current) { 22.dp.roundToPx() }
    val pinned = actions.isPinned(app)
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(c.tile)
                .combinedClickable(onClick = onClick, onLongClick = { why = null; menu = true })
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = remember(app.key) { catalog.icon(app, sizePx) }
                if (icon != null) Image(icon, null, Modifier.size(22.dp).alpha(0.9f))
                Spacer(Modifier.weight(1f))
                if (pinned) Text("●", style = TextStyle(color = c.accent, fontSize = 9.sp))
            }
            Spacer(Modifier.height(8.dp))
            Serif(app.label, size = 15.sp, maxLines = 2)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            why?.let { w -> DropdownMenuItem(text = { Text(w, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic) }, onClick = { menu = false }) }
            if (why == null) DropdownMenuItem(text = { Text("Perché è qui?") }, onClick = { why = actions.why(app) })
            DropdownMenuItem(
                text = { Text(if (pinned) "Non tenere più qui" else "Tieni qui") },
                onClick = { menu = false; actions.togglePin(app) },
            )
            DropdownMenuItem(text = { Text("Non propormela più") }, onClick = { menu = false; actions.exclude(app) })
            DropdownMenuItem(text = { Text("Informazioni app") }, onClick = { menu = false; actions.info(app) })
        }
    }
}

