@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.sordina.ui.apps

import app.sordina.R
import app.sordina.tr
import app.sordina.trn

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.DeleteOutline
import app.sordina.ui.Sheet
import app.sordina.ui.SheetRow
import app.sordina.ui.theme.S
import kotlinx.coroutines.launch

private sealed interface Row_ {
    data class Header(val letter: String) : Row_
    data class Item(val app: AppEntry) : Row_
}

/** Tutte le app: solo nomi, grandi e leggibili. Lettere in rilievo, indice a destra, filtro in basso. */
@Composable
fun AllAppsScreen(
    apps: List<AppEntry>,
    hidden: Set<String>,
    onLaunch: (AppEntry) -> Unit,
    onInfo: (AppEntry) -> Unit,
    onUninstall: (AppEntry) -> Unit,
    onToggleHidden: (AppEntry) -> Unit,
    isBraked: (AppEntry) -> Boolean = { false },
    onToggleBrake: (AppEntry) -> Unit = {},
    icon: ((AppEntry) -> androidx.compose.ui.graphics.ImageBitmap?)? = null,
) {
    val c = S.c
    var filter by remember { mutableStateOf("") }
    var showHidden by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf<String?>(null) }
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
    // +1: la prima riga della lista è il conteggio
    fun jump(letter: String) {
        val idx = rows.indexOfFirst { it is Row_.Header && it.letter == letter }
        if (idx >= 0) scope.launch { listState.scrollToItem(idx + 1) }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(bottom = 12.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize().padding(start = 28.dp, end = 40.dp), state = listState) {
                item(key = "count") {
                    // Testata come nelle altre pagine: titolo grande, sotto quante sono
                    Column(Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                        Text(tr(R.string.all_apps), style = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, color = c.ink))
                        Text(trn(R.plurals.n_apps, visible.size + work.size), Modifier.padding(top = 2.dp), style = TextStyle(fontSize = 15.sp, color = c.soft))
                    }
                }
                items(rows, key = { r -> if (r is Row_.Item) r.app.key else "h_" + (r as Row_.Header).letter }) { r ->
                    when (r) {
                        is Row_.Header -> Text(r.letter, Modifier.padding(top = 18.dp, bottom = 2.dp), style = TextStyle(fontSize = 26.sp, color = c.accent))
                        is Row_.Item -> AppRow(r.app, icon, false, onLaunch, onInfo, onUninstall, onToggleHidden, isBraked, onToggleBrake)
                    }
                }
                if (work.isNotEmpty() && filter.isBlank()) {
                    item { Text(tr(R.string.apps_lavoro), Modifier.padding(top = 26.dp, bottom = 2.dp), style = TextStyle(fontSize = 26.sp, color = c.accent)) }
                    items(work, key = { "w_" + it.key }) { AppRow(it, icon, false, onLaunch, onInfo, onUninstall, onToggleHidden, isBraked, onToggleBrake) }
                }
                if (hiddenApps.isNotEmpty() && filter.isBlank()) {
                    item {
                        Text(
                            if (showHidden) tr(R.string.apps_hide_hidden) else trn(R.plurals.n_hidden, hiddenApps.size),
                            Modifier.padding(top = 26.dp, bottom = 8.dp).combinedClickable { showHidden = !showHidden },
                            style = TextStyle(fontSize = 15.sp, color = c.soft),
                        )
                    }
                    if (showHidden) items(hiddenApps, key = { "x_" + it.key }) {
                        AppRow(it, icon, true, onLaunch, onInfo, onUninstall, onToggleHidden, isBraked, onToggleBrake)
                    }
                }
                if (visible.isEmpty() && filter.isNotBlank()) item {
                    Text(tr(R.string.apps_none_with, filter), Modifier.padding(top = 24.dp), style = TextStyle(fontSize = 17.sp, color = c.soft))
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
            if (filter.isBlank() && letters.size > 3) {
                LetterIndex(letters, scrubbing, Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) { l -> scrubbing = l; if (l != null) jump(l) }
            }
            // La lettera grande mentre scorri l'indice
            scrubbing?.let { l ->
                Box(
                    Modifier.align(Alignment.Center).size(96.dp).clip(RoundedCornerShape(28.dp)).background(c.card),
                    contentAlignment = Alignment.Center,
                ) { Text(l, style = TextStyle(fontSize = 48.sp, color = c.ink)) }
            }
        }
        // Filtro in basso, sotto il pollice
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(c.card)
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (filter.isEmpty()) Text(tr(R.string.apps_filter), style = TextStyle(fontSize = 16.sp, color = c.soft))
                BasicTextField(
                    value = filter,
                    onValueChange = { filter = it; scope.launch { listState.scrollToItem(0) } },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 16.sp, color = c.ink),
                    cursorBrush = SolidColor(c.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (filter.isNotEmpty()) Text(tr(R.string.clear), Modifier.combinedClickable { filter = "" }.padding(start = 12.dp), style = TextStyle(fontSize = 14.sp, color = c.soft))
        }
    }
}

@Composable
private fun AppRow(
    app: AppEntry,
    icon: ((AppEntry) -> androidx.compose.ui.graphics.ImageBitmap?)?,
    isHidden: Boolean,
    onLaunch: (AppEntry) -> Unit,
    onInfo: (AppEntry) -> Unit,
    onUninstall: (AppEntry) -> Unit,
    onToggleHidden: (AppEntry) -> Unit,
    isBraked: (AppEntry) -> Boolean,
    onToggleBrake: (AppEntry) -> Unit,
) {
    val c = S.c
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.fillMaxWidth().combinedClickable(onClick = { onLaunch(app) }, onLongClick = { menu = true }).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        val bmp = remember(app.key) { icon?.invoke(app) }
        if (bmp != null) androidx.compose.foundation.Image(bmp, null, Modifier.padding(end = 16.dp).size(30.dp))
        Text(
            app.label,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontSize = 20.sp, color = if (isHidden) c.soft else c.ink),
        )
        }
        if (menu) Sheet(app.label, onDismiss = { menu = false }, leading = remember(app.key) { icon?.invoke(app) }?.let { b -> { androidx.compose.foundation.Image(b, null, Modifier.size(28.dp)) } }) {
            SheetRow(if (isBraked(app)) tr(R.string.unbrake) else tr(R.string.brake), Icons.Rounded.PanTool) { menu = false; onToggleBrake(app) }
            SheetRow(if (isHidden) tr(R.string.show_in_list) else tr(R.string.hide_from_list), if (isHidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff) { menu = false; onToggleHidden(app) }
            SheetRow(tr(R.string.app_info), Icons.Rounded.Info) { menu = false; onInfo(app) }
            SheetRow(tr(R.string.uninstall), Icons.Rounded.DeleteOutline) { menu = false; onUninstall(app) }
        }
    }
}

/** Indice delle lettere sul bordo destro: tocca o scorri col pollice. null quando lo lasci. */
@Composable
internal fun LetterIndex(letters: List<String>, current: String?, modifier: Modifier, onLetter: (String?) -> Unit) {
    val c = S.c
    var height by remember { mutableStateOf(1) }
    fun at(y: Float) = letters[((y / height) * letters.size).toInt().coerceIn(0, letters.size - 1)]
    Column(
        modifier
            .width(24.dp)
            .fillMaxHeight(0.82f)
            .onSizeChanged { height = it.height.coerceAtLeast(1) }
            .pointerInput(letters) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    onLetter(at(down.position.y))
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull() ?: break
                        if (!ch.pressed) break
                        ch.consume()
                        onLetter(at(ch.position.y))
                    }
                    onLetter(null)
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach {
            Text(
                it,
                Modifier.weight(1f),
                style = TextStyle(fontSize = 11.sp, color = if (it == current) c.accent else c.soft, textAlign = TextAlign.Center),
            )
        }
    }
}

internal fun letterOf(label: String): String {
    val ch = Search.normalize(label).firstOrNull() ?: return "#"
    return if (ch.isLetter()) ch.uppercaseChar().toString() else "#"
}
