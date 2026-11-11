@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.sordina.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sordina.data.AppEntry
import app.sordina.data.Search
import app.sordina.ui.Cap
import app.sordina.ui.Serif
import app.sordina.ui.theme.S
import kotlinx.coroutines.launch

private sealed interface Row_ {
    data class Header(val letter: String) : Row_
    data class Item(val app: AppEntry) : Row_
}

/** Tutte le app: elenco alfabetico, solo nomi, indice delle lettere. Niente pallini. */
@Composable
fun AllAppsScreen(
    apps: List<AppEntry>,
    hidden: Set<String>,
    onLaunch: (AppEntry) -> Unit,
    onInfo: (AppEntry) -> Unit,
    onUninstall: (AppEntry) -> Unit,
    onToggleHidden: (AppEntry) -> Unit,
) {
    val c = S.c
    var filter by remember { mutableStateOf("") }
    var showHidden by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val visible = remember(apps, hidden, filter) {
        val q = Search.normalize(filter)
        apps.filter { it.key !in hidden && !it.isWork && (q.isEmpty() || Search.score(q, it.normalized) > 0) }
    }
    val work = remember(apps, hidden) { apps.filter { it.isWork && it.key !in hidden } }
    val hiddenApps = remember(apps, hidden) { apps.filter { it.key in hidden } }

    val rows = remember(visible) {
        val out = ArrayList<Row_>()
        var last = ""
        for (a in visible) {
            val l = letterOf(a.label)
            if (l != last) { out += Row_.Header(l); last = l }
            out += Row_.Item(a)
        }
        out
    }
    val letters = remember(rows) { rows.filterIsInstance<Row_.Header>().map { it.letter } }
    fun jump(letter: String) {
        val idx = rows.indexOfFirst { it is Row_.Header && it.letter == letter }
        if (idx >= 0) scope.launch { listState.scrollToItem(idx) }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 20.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Cap("Tutte le app")
            Spacer(Modifier.weight(1f))
            Cap("${visible.size + work.size}", color = c.soft)
        }
        Spacer(Modifier.height(10.dp))
        Box {
            if (filter.isEmpty()) Serif("Filtra…", size = 17.sp, color = c.soft, italic = true)
            BasicTextField(
                value = filter,
                onValueChange = { filter = it },
                singleLine = true,
                textStyle = TextStyle(fontFamily = S.style.serif, fontSize = 17.sp, color = c.ink),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.5.dp).background(c.ink))
        Spacer(Modifier.height(4.dp))

        Row(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.weight(1f).fillMaxHeight(), state = listState) {
                items(rows, key = { r -> if (r is Row_.Item) r.app.key else "h_" + (r as Row_.Header).letter }) { r ->
                    when (r) {
                        is Row_.Header -> Cap(r.letter, Modifier.padding(top = 14.dp, bottom = 2.dp))
                        is Row_.Item -> AppRow(r.app, false, onLaunch, onInfo, onUninstall, onToggleHidden)
                    }
                }
                if (work.isNotEmpty() && filter.isBlank()) {
                    item { Cap("Lavoro", Modifier.padding(top = 20.dp, bottom = 2.dp)) }
                    items(work, key = { "w_" + it.key }) { AppRow(it, false, onLaunch, onInfo, onUninstall, onToggleHidden) }
                }
                if (hiddenApps.isNotEmpty() && filter.isBlank()) {
                    item {
                        Serif(
                            if (showHidden) "Nascoste (${hiddenApps.size}) ▴" else "Nascoste (${hiddenApps.size}) ▾",
                            Modifier.padding(top = 20.dp, bottom = 8.dp).combinedClickable { showHidden = !showHidden },
                            size = 13.sp, color = c.soft, italic = true,
                        )
                    }
                    if (showHidden) items(hiddenApps, key = { "x_" + it.key }) {
                        AppRow(it, true, onLaunch, onInfo, onUninstall, onToggleHidden)
                    }
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
            if (filter.isBlank() && letters.size > 3) LetterIndex(letters, ::jump)
        }
    }
}

@Composable
private fun AppRow(
    app: AppEntry,
    isHidden: Boolean,
    onLaunch: (AppEntry) -> Unit,
    onInfo: (AppEntry) -> Unit,
    onUninstall: (AppEntry) -> Unit,
    onToggleHidden: (AppEntry) -> Unit,
) {
    val c = S.c
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(Modifier.fillMaxWidth().combinedClickable(onClick = { onLaunch(app) }, onLongClick = { menu = true })) {
            Serif(app.label, Modifier.padding(vertical = 9.dp), size = 17.sp, maxLines = 1,
                color = if (isHidden) c.soft else c.ink)
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Informazioni app") }, onClick = { menu = false; onInfo(app) })
            DropdownMenuItem(text = { Text("Disinstalla") }, onClick = { menu = false; onUninstall(app) })
            DropdownMenuItem(
                text = { Text(if (isHidden) "Mostra nell'elenco" else "Nascondi dall'elenco") },
                onClick = { menu = false; onToggleHidden(app) },
            )
        }
    }
}

/** Indice delle lettere sul bordo destro: tocca o scorri col pollice. */
@Composable
private fun LetterIndex(letters: List<String>, onLetter: (String) -> Unit) {
    val c = S.c
    var height by remember { mutableStateOf(1) }
    fun at(y: Float) = letters[((y / height) * letters.size).toInt().coerceIn(0, letters.size - 1)]
    Column(
        Modifier
            .width(22.dp)
            .fillMaxHeight()
            .onSizeChanged { height = it.height.coerceAtLeast(1) }
            .pointerInput(letters) { detectTapGestures { onLetter(at(it.y)) } }
            .pointerInput(letters) {
                detectVerticalDragGestures { change, _ -> onLetter(at(change.position.y)) }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach {
            Text(
                it,
                Modifier.weight(1f),
                style = TextStyle(fontFamily = S.style.sans, fontSize = 10.sp, color = c.soft, textAlign = TextAlign.Center),
            )
        }
    }
}

private fun letterOf(label: String): String {
    val ch = Search.normalize(label).firstOrNull() ?: return "#"
    return if (ch.isLetter()) ch.uppercaseChar().toString() else "#"
}
