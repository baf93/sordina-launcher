@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.sordina.ui.intent

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import app.sordina.data.Search
import app.sordina.ui.Cap
import app.sordina.ui.Serif
import app.sordina.ui.rememberNow
import app.sordina.ui.shortLabel
import app.sordina.ui.theme.S

/** Azioni sulla singola proposta (pressione lunga). */
data class ProposalActions(
    val isPinned: (AppEntry) -> Boolean,
    val togglePin: (AppEntry) -> Unit,
    val exclude: (AppEntry) -> Unit,
    val info: (AppEntry) -> Unit,
)

/**
 * Intenzione: la barra in alto, senza tastiera; sotto le sei proposte.
 * Toccando la barra si scrive; scorrendo ancora in su si arriva a Tutte le app.
 */
@Composable
fun IntentScreen(
    catalog: AppCatalog,
    apps: List<AppEntry>,
    proposals: List<AppEntry>,
    usage: Map<String, Double>,
    needsUsagePermission: Boolean,
    actions: ProposalActions,
    onLaunch: (AppEntry) -> Unit,
    onWebSearch: (String) -> Unit,
    onAllApps: () -> Unit,
    onGrantUsage: () -> Unit,
) {
    val c = S.c
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val now = rememberNow()
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    val results = remember(query, apps, usage) { Search.apps(query, apps, usage) }

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
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Serif("Cosa vuoi fare?", size = 19.sp, color = c.soft, italic = true)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = S.style.serif, fontSize = 19.sp, color = c.ink),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        results.firstOrNull()?.let(onLaunch) ?: if (query.isNotBlank()) onWebSearch(query) else Unit
                    }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.5.dp).background(c.ink))
        Spacer(Modifier.height(16.dp))

        if (query.isNotBlank()) {
            results.forEach { app ->
                ResultRow(catalog, app) { onLaunch(app) }
                Spacer(Modifier.height(8.dp))
            }
            ResultRow(null, null, title = "Cerca «${query.trim()}» sul web") { onWebSearch(query.trim()) }
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
    val sizePx = with(LocalDensity.current) { 22.dp.roundToPx() }
    val pinned = actions.isPinned(app)
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(c.tile)
                .combinedClickable(onClick = onClick, onLongClick = { menu = true })
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
            DropdownMenuItem(
                text = { Text(if (pinned) "Non tenere più qui" else "Tieni qui") },
                onClick = { menu = false; actions.togglePin(app) },
            )
            DropdownMenuItem(text = { Text("Non propormela più") }, onClick = { menu = false; actions.exclude(app) })
            DropdownMenuItem(text = { Text("Informazioni app") }, onClick = { menu = false; actions.info(app) })
        }
    }
}

@Composable
private fun ResultRow(catalog: AppCatalog?, app: AppEntry?, title: String? = null, onClick: () -> Unit) {
    val c = S.c
    val sizePx = with(LocalDensity.current) { 22.dp.roundToPx() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.tile)
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (app != null && catalog != null) {
            val icon = remember(app.key) { catalog.icon(app, sizePx) }
            if (icon != null) Image(icon, null, Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
        }
        Serif(title ?: app?.label.orEmpty(), size = 16.sp, color = if (app == null) c.soft else c.ink, italic = app == null)
    }
}
