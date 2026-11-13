@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.sordina.ui.sordina

import app.sordina.R
import app.sordina.tr
import app.sordina.trn
import app.sordina.data.Level
import app.sordina.data.RuleScope
import app.sordina.data.RuleTarget
import app.sordina.ui.RuleSheet
import app.sordina.ui.RuleLabel
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.Image
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sordina.data.Categories
import app.sordina.data.Category
import app.sordina.data.Held
import app.sordina.ui.Header
import app.sordina.ui.LocalFeel
import app.sordina.ui.clickableNoRipple
import app.sordina.ui.theme.S
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Per quanto lasciar passare un contatto o un'app: ore, oppure null = sempre. */
private val PASS_OPTIONS: List<Pair<String, Int?>> =
    listOf(1, 2, 6, 12, 24).map { trn(R.plurals.for_hours, it) to it } + (tr(R.string.pass_always) to null)

/** Una sezione della pagina: una categoria, oppure le app frenate (category = null). */
private data class Section(val key: String, val label: String?, val groups: List<List<Held>>, val braked: Boolean = false) {
    val total get() = groups.sumOf { g -> g.sumOf { it.count } }
}

private fun List<List<Held>>.recentFirst() = sortedByDescending { g -> g.maxOf { it.time } }

/**
 * La pagina Sordina: quello che aspetta, diviso per categorie in ordine di priorità
 * (prima le persone, in fondo le app frenate). Ogni mittente è un blocco: toccandolo si aprono
 * le anteprime; solo toccando un messaggio si apre l'app.
 */
@Composable
fun SordinaScreen(
    groups: List<List<Held>>,
    overrides: Map<String, String>,
    braked: Set<String>,
    /** Ordine delle categorie; null = niente categorie, per arrivo (la più recente in alto) */
    order: List<Category>?,
    icon: (String) -> ImageBitmap?,
    onOpen: (Held) -> Unit,
    onRead: (String) -> Unit,
    /** Una notifica sola, letta scorrendo */
    onReadOne: (Held) -> Unit,
    onReadAll: () -> Unit,
    onLetPass: (Held, Int?) -> Unit,
    ruleOf: (RuleTarget, RuleScope) -> Level?,
    /** Una regola per tipo, persona o gruppo, app; null la toglie */
    onRule: (RuleTarget, RuleScope, Level?) -> Unit,
    onMove: (key: String, Category) -> Unit,
    onBack: () -> Unit,
) {
    val c = S.c
    val feel = LocalFeel.current
    val sections = remember(groups, overrides, braked, order) {
        val (stopped, rest) = groups.partition { it.first().pkg in braked }
        val main = if (order == null) listOf(Section("ALL", null, rest.recentFirst())).filter { it.groups.isNotEmpty() }
        else rest.groupBy { Categories.of(it.first(), overrides) }.toSortedMap(compareBy { order.indexOf(it) })
            .map { (cat, gs) -> Section(cat.name, cat.label, gs.recentFirst()) }
        main + listOfNotNull(stopped.takeIf { it.isNotEmpty() }?.let { Section("FRENATE", tr(R.string.braked_section), it.recentFirst(), braked = true) })
    }
    val total = groups.sumOf { g -> g.sumOf { it.count } }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var passFor by remember { mutableStateOf<Held?>(null) }
    var moveFor by remember { mutableStateOf<Held?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp)) {
            item(key = "top") {
                Header("Sordina", onBack, subtitle = when (total) {
                            0 -> tr(R.string.sordina_empty)
                            else -> trn(R.plurals.n_notifications, total)
                        }, modifier = Modifier.statusBarsPadding().padding(horizontal = 16.dp))
            }
            sections.forEach { s ->
                if (s.label == null) item(key = "h_" + s.key) { Spacer(Modifier.height(10.dp)) }
                else item(key = "h_" + s.key) {
                    Row(Modifier.animateItem().padding(start = 28.dp, end = 28.dp, top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (s.braked) Icon(Icons.Rounded.Lock, null, Modifier.padding(end = 6.dp).size(16.dp), tint = c.soft)
                        Text(s.label, style = TextStyle(fontSize = 20.sp, color = c.ink))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (s.braked) tr(R.string.stay_here) else "${s.total}",
                            style = TextStyle(fontSize = 14.sp, color = c.soft),
                        )
                    }
                }
                items(s.groups, key = { it.first().groupKey }) { g ->
                    val key = g.first().groupKey
                    Box(Modifier.animateItem().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Block(
                            g, open = key in expanded, dim = s.braked, icon = icon,
                            onToggle = { feel.tick(); expanded = if (key in expanded) expanded - key else expanded + key },
                            onOpen = onOpen, onRead = onRead, onReadOne = onReadOne,
                            onPass = { passFor = g.first() }, onMove = { moveFor = g.first() },
                        )
                    }
                }
            }
        }
        if (total > 0) Box(
            // Il pulsante principale, uguale ovunque: largo, alto 52
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp).fillMaxWidth().height(52.dp)
                .clip(RoundedCornerShape(26.dp)).background(c.ink)
                .clickable { feel.confirm(); onReadAll() },
            contentAlignment = Alignment.Center,
        ) { Text(tr(R.string.all_read), style = TextStyle(fontSize = 16.sp, color = c.bg)) }
    }

    passFor?.let { h ->
        // Una regola per sempre (tipo, persona o gruppo, app) oppure "lascia passare" per qualche ora
        RuleSheet(h.target(), ruleOf, onRule, onDismiss = { passFor = null }) {
            val now = System.currentTimeMillis()
            RuleLabel(tr(R.string.let_pass))
            PASS_OPTIONS.filter { it.second != null }.forEach { (label, hours) ->
                PickerRow(label, untilLabel(now + hours!! * 3_600_000L), highlight = false) { feel.confirm(); passFor = null; onLetPass(h, hours) }
            }
        }
    }
    moveFor?.let { h ->
        val current = Categories.of(h, overrides)
        Picker(tr(R.string.move_title, h.groupTitle), if (h.groupKey.startsWith("p:")) tr(R.string.move_hint_person) else tr(R.string.move_hint_app), onDismiss = { moveFor = null }) {
            Category.entries.forEach { cat ->
                PickerRow(cat.label, if (cat == current) tr(R.string.now_here) else "", highlight = cat == current) {
                    feel.confirm(); moveFor = null; onMove(h.groupKey, cat)
                }
            }
        }
    }
}

private fun untilLabel(at: Long): String {
    val t = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
    return if (t.toLocalDate() == LocalDate.now()) tr(R.string.until_time, t.format(DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)))
    else tr(R.string.until_tomorrow, t.format(DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)))
}

/**
 * Un mittente o un'app, in un blocco arrotondato. Tocco: si aprono le anteprime.
 * Scorrendo di lato oltre il 40%: il blocco è letto tutto; un'anteprima (a blocco aperto) è letta da sola.
 */
@Composable
private fun Block(
    g: List<Held>, open: Boolean, dim: Boolean, icon: (String) -> ImageBitmap?,
    onToggle: () -> Unit, onOpen: (Held) -> Unit, onRead: (String) -> Unit, onReadOne: (Held) -> Unit,
    onPass: () -> Unit, onMove: () -> Unit,
) {
    val c = S.c
    val feel = LocalFeel.current
    val h = g.first()
    val total = g.sumOf { it.count }
    val shape = RoundedCornerShape(22.dp)
    val person = h.groupKey.startsWith("p:")
    val single = g.size == 1
    val arrow by animateFloatAsState(if (open) 180f else 0f, feel.spec(tween(220)), label = "arrow")

    // Il blocco intero si legge scorrendolo (le anteprime aperte, scorse una per una, si leggono da sole)
    Swipeable(h.groupKey, enabled = true, shape = shape, onSwiped = { if (single) onReadOne(h) else onRead(h.groupKey) }) {
        Column(
            Modifier.fillMaxWidth().clip(shape).background(c.card)
                .clickable(onClick = onToggle).padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(Modifier.alpha(if (dim) 0.6f else 1f), verticalAlignment = Alignment.CenterVertically) {
                Avatar(h, person, icon)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(h.groupTitle, Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 17.sp, color = c.ink))
                        if (total > 1) Text("  $total", style = TextStyle(fontSize = 14.sp, color = c.accent))
                    }
                    if (!open) Text(previewLine(h), maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 14.sp, color = c.soft))
                }
                Text(time(g.maxOf { it.time }), Modifier.padding(start = 8.dp), style = TextStyle(fontSize = 12.sp, color = c.soft))
                Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.padding(start = 2.dp).size(20.dp).graphicsLayer { rotationZ = arrow }, tint = c.soft)
            }
            AnimatedVisibility(
                open,
                enter = if (feel.animations) expandVertically(tween(240)) + fadeIn(tween(200)) else fadeIn(tween(0)),
                exit = if (feel.animations) shrinkVertically(tween(200)) + fadeOut(tween(150)) else fadeOut(tween(0)),
            ) {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    // Perché aspetta: la regola che l'ha fermata, da correggere con i tasti qui sotto
                    whyLabel(h.reason)?.let { Text(tr(R.string.why_waiting, it), Modifier.padding(bottom = 8.dp), style = TextStyle(fontSize = 13.sp, color = c.soft)) }
                    // Le anteprime, dalla più vecchia alla più recente, divise da una riga sottile;
                    // titolo bianco e marcato, testo grigio sotto. Tocco su una: si apre l'app lì
                    g.sortedBy { it.time }.forEach { m ->
                        val head = headline(m)
                        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                        Swipeable(m.key, enabled = !single, shape = RoundedCornerShape(12.dp), onSwiped = { onReadOne(m) }) {
                        Column(Modifier.fillMaxWidth().background(c.card).clickable { feel.tick(); onOpen(m) }.padding(horizontal = 2.dp, vertical = 11.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    head ?: (body(m) + if (m.count > 1) "  · ${m.count}" else ""),
                                    Modifier.weight(1f), maxLines = if (head != null) 2 else 5, overflow = TextOverflow.Ellipsis,
                                    style = if (head != null) TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
                                    else TextStyle(fontSize = 15.sp, lineHeight = 20.sp, color = c.ink),
                                )
                                Text(time(m.time), Modifier.padding(start = 10.dp, top = 2.dp), style = TextStyle(fontSize = 12.sp, color = c.soft))
                            }
                            if (head != null) Text(
                                body(m) + if (m.count > 1) "  · ${m.count}" else "",
                                Modifier.padding(top = 2.dp), maxLines = 5, overflow = TextOverflow.Ellipsis,
                                style = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, color = c.soft),
                            )
                        }
                        }
                    }
                    // Tre tasti piccoli, uguali, solo icona
                    Row(Modifier.padding(top = 6.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Action(Icons.Rounded.NotificationsActive, tr(R.string.let_pass), c.accent, Modifier.weight(1f)) { feel.tick(); onPass() }
                        Action(Icons.AutoMirrored.Rounded.DriveFileMove, tr(R.string.move_category), c.ink, Modifier.weight(1f)) { feel.tick(); onMove() }
                        Action(Icons.Rounded.DoneAll, tr(R.string.mark_read), c.ink, Modifier.weight(1f)) { feel.confirm(); onRead(h.groupKey) }
                    }
                }
            }
        }
    }
}

/** Chi o cosa: nei gruppi chi scrive, per le app il titolo della notifica; nelle chat a due nessuno (è il blocco). */
private fun headline(h: Held): String? = when {
    h.isMessage -> h.person?.takeIf { h.conversation != null }
    h.title != null -> h.title
    // Notifiche salvate prima che il titolo fosse separato: "Titolo: testo"
    else -> h.text.substringBefore(": ", "").takeIf { it.isNotBlank() && it.length <= 60 }
}

private fun body(h: Held): String {
    val t = headline(h) ?: return h.text
    return h.text.removePrefix("$t: ").ifBlank { h.text }
}

/** Anteprima su una riga: titolo in evidenza, poi il testo. */
@Composable
private fun previewLine(h: Held): AnnotatedString {
    val c = S.c
    val t = headline(h)
    return buildAnnotatedString {
        if (t != null) withStyle(SpanStyle(color = c.ink, fontWeight = FontWeight.SemiBold)) { append(t); append("  ") }
        append(body(h))
    }
}

@Composable
private fun Action(icon: ImageVector, label: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    val c = S.c
    Box(
        modifier.height(40.dp).clip(RoundedCornerShape(14.dp)).background(c.line).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, Modifier.size(20.dp), tint = tint) }
}

/** App: la sua icona. Persona: l'iniziale, con la piccola icona dell'app da cui scrive. */
@Composable
private fun Avatar(h: Held, person: Boolean, icon: (String) -> ImageBitmap?) {
    val c = S.c
    val bmp = remember(h.pkg) { icon(h.pkg) }
    if (!person && bmp != null) {
        Image(bmp, null, Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)))
        return
    }
    Box(Modifier.size(42.dp)) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(if (person) c.accent.copy(alpha = 0.16f) else c.line),
            contentAlignment = Alignment.Center,
        ) {
            Text(h.groupTitle.trim().take(1).uppercase(), style = TextStyle(fontSize = 16.sp, color = if (person) c.accent else c.soft))
        }
        if (person && bmp != null) Image(
            bmp, null,
            Modifier.align(Alignment.BottomEnd).size(19.dp).clip(RoundedCornerShape(6.dp)).background(c.card).padding(1.5.dp).clip(RoundedCornerShape(5.dp)),
        )
    }
}

@Composable
private fun Picker(title: String, hint: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val c = S.c
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.card,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { Box(Modifier.padding(top = 12.dp, bottom = 14.dp).size(width = 36.dp, height = 4.dp).clip(CircleShape).background(c.dot)) },
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(title, Modifier.padding(horizontal = 24.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 21.sp, color = c.ink))
            Text(hint, Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 10.dp), style = TextStyle(fontSize = 14.sp, color = c.soft))
            content()
        }
    }
}

@Composable
private fun PickerRow(label: String, hint: String, highlight: Boolean, onClick: () -> Unit) {
    val c = S.c
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = TextStyle(fontSize = 17.sp, color = if (highlight) c.accent else c.ink))
        Text(hint, style = TextStyle(fontSize = 14.sp, color = c.soft))
    }
}

private fun time(ms: Long): String {
    val t = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
    return if (t.toLocalDate() == LocalDate.now()) t.format(DateTimeFormatter.ofPattern("H:mm"))
    else t.format(DateTimeFormatter.ofPattern("d/M"))
}

/** La regola che ha trattenuto la notifica, detta per esteso. */
private fun whyLabel(reason: String?): String? = when (reason) {
    null -> null
    "predefinito" -> tr(R.string.why_default)
    "app frenata" -> tr(R.string.why_braked)
    "pagamento" -> tr(R.string.why_payment)
    "regola dell'app", "scelta per l'app" -> tr(R.string.why_app)
    "regola del tipo" -> tr(R.string.why_type)
    "regola della persona", "trattenuta da te" -> tr(R.string.why_person)
    "modalità", "in sordina" -> tr(R.string.why_mode)
    else -> reason
}

/** Un gruppo in sordina come bersaglio di una regola. */
private fun Held.target(): RuleTarget {
    val who = groupKey.takeIf { it.startsWith("p:") }?.removePrefix("p:")
    return RuleTarget(
        pkg = pkg, appLabel = appLabel, channel = channel, channelName = channelName,
        person = who, personTitle = if (who != null) groupTitle else null,
        title = groupTitle, key = key,
    )
}



/** Scorrendo di lato oltre il 40% parte [onSwiped]; sotto compare "Letta". Se [enabled] è falso è un contenitore e basta. */
@Composable
private fun Swipeable(key: Any, enabled: Boolean, shape: androidx.compose.ui.graphics.Shape, onSwiped: () -> Unit, content: @Composable () -> Unit) {
    if (!enabled) {
        Box(Modifier.fillMaxWidth()) { content() }
        return
    }
    val c = S.c
    val feel = LocalFeel.current
    val scope = rememberCoroutineScope()
    val offset = remember(key) { Animatable(0f) }
    var width by remember { mutableIntStateOf(1) }
    val passed = kotlin.math.abs(offset.value) > width * 0.4f
    LaunchedEffect(passed) { if (passed) feel.threshold() }
    Box(
        Modifier.fillMaxWidth().clip(shape).onSizeChanged { width = it.width.coerceAtLeast(1) }
            .pointerInput(key) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            val v = offset.value
                            if (kotlin.math.abs(v) > width * 0.4f) {
                                offset.animateTo(if (v > 0) width.toFloat() else -width.toFloat(), feel.spec(tween(160)))
                                feel.confirm()
                                onSwiped()
                            } else offset.animateTo(0f, feel.spec(spring(dampingRatio = 0.7f, stiffness = 500f)))
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f, feel.spec(spring())) } },
                ) { ch, dx -> ch.consume(); scope.launch { offset.snapTo(offset.value + dx) } }
            },
    ) {
        if (offset.value != 0f) {
            val fg by animateColorAsState(if (passed) c.accent else c.soft, label = "read")
            Row(
                Modifier.matchParentSize().background(if (passed) c.accent.copy(alpha = 0.2f) else c.line).padding(horizontal = 22.dp),
                horizontalArrangement = if (offset.value >= 0) Arrangement.Start else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = fg)
                Spacer(Modifier.width(6.dp))
                Text(if (passed) tr(R.string.read_done) else tr(R.string.swipe_more), style = TextStyle(fontSize = 14.sp, color = fg))
            }
        }
        Box(Modifier.fillMaxWidth().graphicsLayer { translationX = offset.value }) { content() }
    }
}
