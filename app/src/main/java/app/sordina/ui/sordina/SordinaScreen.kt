@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.sordina.ui.sordina

import app.sordina.ui.Motion

import app.sordina.R
import app.sordina.tr
import app.sordina.trn
import app.sordina.data.Level
import app.sordina.data.RuleOp
import app.sordina.data.RuleTarget
import app.sordina.ui.RuleSheet
import app.sordina.ui.RuleLabel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sordina.data.Categories
import app.sordina.data.Category
import app.sordina.data.Held
import app.sordina.ui.Header
import app.sordina.ui.LocalFeel
import app.sordina.ui.theme.S
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

/** Per quanto lasciar passare un contatto o un'app: ore, oppure null = sempre. */
private val PASS_OPTIONS: List<Pair<String, Int?>> =
    listOf(1, 2, 6, 12, 24).map { trn(R.plurals.for_hours, it) to it } + (tr(R.string.pass_always) to null)

/** Una sezione della pagina: una categoria, oppure le app frenate (category = null). */
private data class Section(val key: String, val label: String?, val groups: List<List<Held>>, val braked: Boolean = false, val locked: Boolean = false) {
    val total get() = groups.sumOf { g -> g.sumOf { it.count } }
}

/** Notifiche appena lette scorrendo: spariscono subito, ma si chiudono davvero solo quando "Annulla" non serve più. */
private data class PendingRead(val keys: Set<String>, val title: String, val count: Int)

private fun List<List<Held>>.recentFirst() = sortedByDescending { g -> g.maxOf { it.time } }

/** Quanto resta la barra "Annulla" dopo uno scorrimento */
private const val UNDO_MILLIS = 4_000L

/**
 * La pagina Sordina, come la tendina di Android: per ogni categoria una pila di righe attaccate
 * (in alto e in fondo gli angoli tondi), nell'ordine scelto nelle impostazioni. Ogni mittente è una riga:
 * toccandola o tirandola giù si aprono i messaggi, scorrendola di lato è letta (con "Annulla"),
 * tenendola premuta si sceglie una regola. Solo toccando un messaggio si apre l'app.
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
    /** Queste notifiche, lette scorrendo (dopo che "Annulla" è scaduto) */
    onReadKeys: (List<String>) -> Unit,
    onReadAll: () -> Unit,
    onLetPass: (Held, Int?) -> Unit,
    /** Le scelte possibili per una notifica, com'è adesso ciascuna, e il tocco che la cambia */
    ruleOps: (RuleTarget) -> List<RuleOp>,
    ruleOn: (RuleTarget, RuleOp) -> Boolean,
    onRule: (RuleTarget, RuleOp) -> Unit,
    onMove: (key: String, Category) -> Unit,
    onBack: () -> Unit,
    /** Le app nascoste: una riga sola finché non si sblocca */
    hiddenRevealed: Boolean = false,
    onRevealHidden: () -> Unit = {},
    /** Sotto i messaggi aperti, la nota "tieni premuto": finché non l'hai fatto una volta */
    holdHint: Boolean = false,
    onHoldLearned: () -> Unit = {},
) {
    val c = S.c
    val feel = LocalFeel.current

    // Le letture in attesa di "Annulla": una alla volta, la precedente si chiude appena ne arriva un'altra
    var pending by remember { mutableStateOf<PendingRead?>(null) }
    val readKeys by rememberUpdatedState(onReadKeys)
    fun commit() { pending?.let { pending = null; readKeys(it.keys.toList()) } }
    fun read(items: List<Held>, title: String) {
        commit()
        pending = PendingRead(items.map { it.key }.toSet(), title, items.sumOf { it.count })
    }
    LaunchedEffect(pending) { if (pending != null) { delay(UNDO_MILLIS); commit() } }
    // Uscendo dalla pagina (Home, un'app aperta) la lettura vale subito
    val pendingNow by rememberUpdatedState(pending)
    DisposableEffect(Unit) { onDispose { pendingNow?.let { readKeys(it.keys.toList()) } } }

    val shown = remember(groups, pending) {
        val gone = pending?.keys ?: emptySet()
        if (gone.isEmpty()) groups else groups.map { g -> g.filter { it.key !in gone } }.filter { it.isNotEmpty() }
    }
    val sections = remember(shown, overrides, braked, order, hiddenRevealed) {
        val (secret, visible) = shown.partition { it.first().reason == app.sordina.service.Classifier.REASON_HIDDEN }
        val (stopped, rest) = visible.partition { it.first().pkg in braked }
        val main = if (order == null) listOf(Section("ALL", null, rest.recentFirst())).filter { it.groups.isNotEmpty() }
        else rest.groupBy { Categories.of(it.first(), overrides) }.toSortedMap(compareBy { order.indexOf(it) })
            .map { (cat, gs) -> Section(cat.name, cat.label, gs.recentFirst()) }
        main + listOfNotNull(
            stopped.takeIf { it.isNotEmpty() }?.let { Section("FRENATE", tr(R.string.braked_section), it.recentFirst(), braked = true) },
            // Nascoste: senza sblocco si vede solo quante sono, mai chi o cosa
            secret.takeIf { it.isNotEmpty() }?.let {
                if (hiddenRevealed) Section("NASCOSTE", tr(R.string.hidden_apps), it.recentFirst(), braked = true)
                else Section("NASCOSTE", tr(R.string.hidden_apps), it, locked = true)
            },
        )
    }
    val total = shown.sumOf { g -> g.sumOf { it.count } }
    val latest = shown.maxOfOrNull { g -> g.maxOf { it.time } }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var actionsFor by remember { mutableStateOf<Held?>(null) }
    var moveFor by remember { mutableStateOf<Held?>(null) }

    val list = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = 96.dp)) {
            item(key = "top") {
                Header(
                    "Sordina", onBack,
                    // Quante e l'ora dell'ultima, niente altro
                    subtitle = if (total > 0 && latest != null) trn(R.plurals.n_waiting2, total) + " · " + time(latest) else null,
                    modifier = Modifier.statusBarsPadding().padding(horizontal = 16.dp),
                )
            }
            // Niente in sordina: la sordina tranquilla
            if (total == 0) item(key = "quiet") {
                Column(Modifier.fillParentMaxHeight(0.8f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    app.sordina.ui.Mascot(app.sordina.ui.Mood.REST, Modifier.size(180.dp))
                    Spacer(Modifier.height(20.dp))
                    Text(tr(R.string.n_quiet_title), style = TextStyle(fontSize = 24.sp, color = c.ink))
                    Spacer(Modifier.height(6.dp))
                    Text(tr(R.string.n_quiet_text), style = TextStyle(fontSize = 16.sp, color = c.soft))
                }
            }
            sections.forEach { s ->
                item(key = "h_" + s.key) {
                    // Il nome della categoria, piccolo e grigio come nella tendina
                    if (s.label == null) Spacer(Modifier.animateItem().height(8.dp))
                    else Row(
                        Modifier.animateItem().fillMaxWidth().then(if (s.locked) Modifier.clickable { feel.tick(); onRevealHidden() } else Modifier)
                            .padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (s.braked || s.locked) Icon(Icons.Rounded.Lock, null, Modifier.padding(end = 5.dp).size(13.dp), tint = c.soft)
                        Text(
                            s.label + when { s.braked -> " · " + tr(R.string.stay_here); s.locked -> " · ${s.total}"; else -> "" },
                            style = TextStyle(fontSize = 13.sp, color = c.soft),
                        )
                    }
                }
                if (!s.locked) itemsIndexed(s.groups, key = { _, g -> g.first().groupKey }) { i, g ->
                    val key = g.first().groupKey
                    // Una pila: angoli grandi solo in cima e in fondo, fra una riga e l'altra appena accennati
                    val top = if (i == 0) 20.dp else 6.dp
                    val bottom = if (i == s.groups.lastIndex) 20.dp else 6.dp
                    Box(Modifier.animateItem().padding(horizontal = 14.dp).padding(bottom = 2.dp)) {
                        Block(
                            g, open = key in expanded, dim = s.braked, icon = icon,
                            shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom),
                            atTop = { !list.canScrollBackward },
                            holdHint = holdHint,
                            onToggle = { feel.tick(); expanded = if (key in expanded) expanded - key else expanded + key },
                            onPullOpen = { expanded = expanded + key },
                            onOpen = { m -> commit(); onOpen(m) },
                            onReadGroup = { read(g, g.first().groupTitle) },
                            onReadOne = { m -> read(listOf(m), m.groupTitle) },
                            onActions = { feel.threshold(); actionsFor = g.first(); onHoldLearned() },
                        )
                    }
                }
            }
            // "Tutto letto" in fondo, piccolo: c'è quando serve, non chiede di essere toccato
            if (total > 0) item(key = "readAll") {
                Box(Modifier.animateItem().fillMaxWidth().padding(top = 18.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).border(1.dp, c.line, RoundedCornerShape(20.dp))
                            .clickable { feel.confirm(); commit(); app.sordina.data.Tally.daily("readAll"); onReadAll() }
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(tr(R.string.all_read), style = TextStyle(fontSize = 14.sp, color = c.ink)) }
                }
            }
        }

        // "Letta · Annulla": per qualche secondo dopo uno scorrimento, poi se ne va da sola
        // L'ultima lettura resta qui anche mentre la barra esce, così il testo non sparisce prima di lei
        val lastPending = remember { arrayOfNulls<PendingRead>(1) }
        pending?.let { lastPending[0] = it }
        AnimatedVisibility(
            pending != null,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 14.dp, end = 14.dp, bottom = 16.dp),
            enter = if (feel.animations) slideInVertically(Motion.enter()) { it / 2 } + fadeIn(Motion.enter()) else fadeIn(tween(0)),
            exit = if (feel.animations) slideOutVertically(Motion.exit()) { it / 2 } + fadeOut(Motion.exit()) else fadeOut(tween(0)),
        ) {
            val p = lastPending[0] ?: return@AnimatedVisibility
            Row(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp)).background(c.ink).padding(start = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    p.title + " · " + trn(R.plurals.n_read, p.count), Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 14.sp, color = c.bg),
                )
                Box(
                    Modifier.height(48.dp).clickable { feel.tick(); pending = null }.padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(tr(R.string.undo), style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = lerp(c.accent, c.bg, 0.15f))) }
            }
        }
    }

    actionsFor?.let { h ->
        // Tenendo premuto: una regola per sempre (tipo, persona o gruppo, app), "lascia passare" per qualche ora, la categoria
        val t = h.target()
        RuleSheet(t, ruleOps(t), ruleOn, onRule, onDismiss = { actionsFor = null }) {
            val now = System.currentTimeMillis()
            RuleLabel(tr(R.string.let_pass))
            PASS_OPTIONS.filter { it.second != null }.forEach { (label, hours) ->
                PickerRow(label, untilLabel(now + hours!! * 3_600_000L), highlight = false) { feel.confirm(); actionsFor = null; onLetPass(h, hours) }
            }
            // Senza categorie (tutto per arrivo) spostarla non cambierebbe niente
            if (order != null) {
                Spacer(Modifier.height(8.dp))
                PickerRow(tr(R.string.move_category), Categories.of(h, overrides).label, highlight = false) { feel.tick(); actionsFor = null; moveFor = h }
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
 * Un mittente o un'app: una riga della pila. Tocco: si aprono i messaggi; tirandola giù si aprono seguendo il dito.
 * Di lato oltre il 40% è letta tutta; un messaggio (a riga aperta) si legge da solo. Tenendo premuto: le regole.
 */
@Composable
private fun Block(
    g: List<Held>, open: Boolean, dim: Boolean, icon: (String) -> ImageBitmap?,
    shape: RoundedCornerShape, atTop: () -> Boolean, holdHint: Boolean,
    onToggle: () -> Unit, onPullOpen: () -> Unit, onOpen: (Held) -> Unit,
    onReadGroup: () -> Unit, onReadOne: (Held) -> Unit, onActions: () -> Unit,
) {
    val c = S.c
    val feel = LocalFeel.current
    val scope = rememberCoroutineScope()
    val h = g.first()
    val total = g.sumOf { it.count }
    val person = h.groupKey.startsWith("p:")
    val single = g.size == 1

    // Quanto sono aperti i messaggi, da 0 a 1: lo muovono sia il tocco sia il dito che tira
    val reveal = remember(h.groupKey) { Animatable(if (open) 1f else 0f) }
    LaunchedEffect(open) {
        val to = if (open) 1f else 0f
        if (reveal.value != to) reveal.animateTo(to, feel.spec(if (open) spring(dampingRatio = 0.85f, stiffness = 420f) else Motion.enter(220)))
    }
    var pulling by remember { mutableStateOf(false) }
    // Altezza piena dei messaggi, per sapere quanto dito serve ad aprirli tutti
    val full = remember { intArrayOf(0) }
    val lit = pulling || open
    val chipColor by animateColorAsState(if (pulling) c.accent else c.soft, label = "chip")
    val arrow by animateFloatAsState(if (open) 180f else 0f, feel.spec(Motion.enter()), label = "arrow")
    val surface by animateColorAsState(if (pulling) lerp(c.card, c.ink, 0.05f) else c.card, label = "lift")

    Swipeable(h.groupKey, enabled = true, shape = shape, onSwiped = { if (single) onReadOne(h) else onReadGroup() }) {
        Column(
            Modifier.fillMaxWidth().clip(shape).background(surface)
                .pullToOpen(
                    enabled = !open, atTop = atTop,
                    onArm = { feel.tick(); pulling = true },
                    onPull = { dy ->
                        val f = (dy / (if (full[0] > 0) full[0].toFloat() else 400f)).coerceIn(0f, 1f)
                        if (reveal.value < OPEN_AT && f >= OPEN_AT) feel.threshold()
                        scope.launch { reveal.snapTo(f) }
                    },
                    onRelease = {
                        pulling = false
                        if (reveal.value >= OPEN_AT) onPullOpen()
                        else scope.launch { reveal.animateTo(0f, feel.spec(spring(dampingRatio = 0.8f, stiffness = 500f))) }
                    },
                )
                .combinedClickable(onClick = onToggle, onLongClick = onActions)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(Modifier.alpha(if (dim) 0.6f else 1f), verticalAlignment = Alignment.CenterVertically) {
                Avatar(h, person, icon)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(h.groupTitle, Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 15.sp, color = c.ink))
                        Text(" · " + time(g.maxOf { it.time }), maxLines = 1, style = TextStyle(fontSize = 12.sp, color = c.soft))
                    }
                    // Chiusa: l'ultima riga. Mentre si apre, sfuma lasciando posto ai messaggi
                    if (!open) Text(
                        previewLine(g.maxBy { it.time }), Modifier.graphicsLayer { alpha = 1f - reveal.value },
                        maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 13.sp, color = c.soft),
                    )
                }
                // Il numero è anche la presa: da qui si tira giù sempre, senza aspettare
                Row(
                    Modifier.padding(start = 10.dp).clip(RoundedCornerShape(11.dp)).border(1.dp, if (pulling) c.accent else c.line, RoundedCornerShape(11.dp))
                        .padding(start = if (total > 1) 8.dp else 3.dp, end = 3.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (total > 1) Text("$total", style = TextStyle(fontSize = 12.sp, color = chipColor))
                    Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(16.dp).graphicsLayer { rotationZ = arrow }, tint = chipColor)
                }
            }
            if (lit || reveal.value > 0f) {
                val msgs = g.sortedBy { it.time }
                Column(
                    Modifier.fillMaxWidth().clipToBounds().layout { m, k ->
                        val p = m.measure(k.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                        full[0] = p.height
                        layout(p.width, (p.height * reveal.value).roundToInt()) { p.place(0, 0) }
                    },
                ) {
                    Column(Modifier.fillMaxWidth().padding(start = 48.dp, top = 6.dp)) {
                        // I messaggi, dal più vecchio al più recente: escono uno dopo l'altro mentre si apre
                        msgs.forEachIndexed { i, m ->
                            key(m.key) {
                                val head = headline(m)
                                Swipeable(m.key, enabled = !single, shape = RoundedCornerShape(10.dp), onSwiped = { onReadOne(m) }) {
                                    Column(
                                        Modifier.fillMaxWidth().graphicsLayer {
                                            val step = 0.5f / msgs.size
                                            alpha = ((reveal.value - i * step) / 0.5f).coerceIn(0f, 1f)
                                        }.background(surface).clickable { feel.tick(); onOpen(m) }.padding(vertical = 7.dp),
                                    ) {
                                        Row(verticalAlignment = Alignment.Top) {
                                            Text(
                                                head ?: (body(m) + if (m.count > 1) "  · ${m.count}" else ""),
                                                Modifier.weight(1f), maxLines = if (head != null) 2 else 5, overflow = TextOverflow.Ellipsis,
                                                style = if (head != null) TextStyle(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
                                                else TextStyle(fontSize = 14.sp, lineHeight = 19.sp, color = c.ink),
                                            )
                                            Text(time(m.time), Modifier.padding(start = 10.dp, top = 2.dp), style = TextStyle(fontSize = 11.sp, color = c.soft))
                                        }
                                        if (head != null) Text(
                                            body(m) + if (m.count > 1) "  · ${m.count}" else "",
                                            Modifier.padding(top = 1.dp), maxLines = 5, overflow = TextOverflow.Ellipsis,
                                            style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, color = c.soft),
                                        )
                                        m.image?.let { HeldPicture(it) }
                                    }
                                }
                            }
                        }
                        if (holdHint) Text(
                            tr(R.string.sordina_hold_hint), Modifier.padding(top = 6.dp),
                            style = TextStyle(fontSize = 12.sp, color = c.soft),
                        )
                    }
                }
            }
        }
    }
}

/** Oltre questa parte, lasciando il dito i messaggi si aprono del tutto; prima tornano chiusi. */
private const val OPEN_AT = 0.35f

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
        if (t != null) withStyle(SpanStyle(color = c.ink)) { append(t); append("  ") }
        append(body(h))
    }
}

/** App: la sua icona. Persona: l'iniziale, con la piccola icona dell'app da cui scrive. */
@Composable
private fun Avatar(h: Held, person: Boolean, icon: (String) -> ImageBitmap?) {
    val c = S.c
    val bmp = remember(h.pkg) { icon(h.pkg) }
    if (!person && bmp != null) {
        Image(bmp, null, Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)))
        return
    }
    Box(Modifier.size(38.dp)) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(if (person) c.accent.copy(alpha = 0.16f) else c.line),
            contentAlignment = Alignment.Center,
        ) {
            Text(h.groupTitle.trim().take(1).uppercase(), style = TextStyle(fontSize = 15.sp, color = if (person) c.accent else c.soft))
        }
        if (person && bmp != null) Image(
            bmp, null,
            Modifier.align(Alignment.BottomEnd).size(17.dp).clip(RoundedCornerShape(5.dp)).background(c.card).padding(1.5.dp).clip(RoundedCornerShape(4.dp)),
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

/** Un gruppo in sordina come bersaglio di una regola. */
private fun Held.target(): RuleTarget {
    val who = groupKey.takeIf { it.startsWith("p:") }?.removePrefix("p:")
    return RuleTarget(
        pkg = pkg, appLabel = appLabel, channel = channel, channelName = channelName,
        person = who, personTitle = if (who != null) groupTitle else null,
        title = groupTitle, key = key,
    )
}

/**
 * Tirare giù una riga chiusa la apre, seguendo il dito. Per non rubare lo scorrimento alla lista:
 * - in cima alla lista (o se la lista è corta e non scorre) il dito verso il basso apre subito;
 * - dalla presa del numero, a destra, apre subito ovunque;
 * - altrove basta un attimo di presa ([HOLD_MILLIS]) prima di tirare.
 * Di lato è [Swipeable] (letta), verso l'alto scorre la lista, il tocco apre e chiude.
 */
private fun Modifier.pullToOpen(
    enabled: Boolean, atTop: () -> Boolean, onArm: () -> Unit, onPull: (Float) -> Unit, onRelease: () -> Unit,
): Modifier =
    if (!enabled) this else pointerInput(Unit) {
        val slop = viewConfiguration.touchSlop
        val handleW = 72.dp.toPx()
        val handleH = 64.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val free = atTop() || (down.position.x > size.width - handleW && down.position.y < handleH)
            var dy = 0f
            if (free) {
                // Si decide al primo movimento: giù è nostro, il resto (lato, su) è della riga o della lista
                var dx = 0f
                while (true) {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    val ch = e.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                    if (!ch.pressed || ch.isConsumed) return@awaitEachGesture
                    val d = ch.positionChange(); dx += d.x; dy += d.y
                    if (abs(dx) > slop || abs(dy) > slop) {
                        if (dy > 0 && dy > abs(dx) * 1.2f) { ch.consume(); dy -= slop; break }
                        return@awaitEachGesture
                    }
                }
            } else {
                // La presa: il dito fermo per un attimo; se si muove o si alza prima, il gesto è della lista
                var moved = false
                val released = withTimeoutOrNull(HOLD_MILLIS) {
                    var mx = 0f; var my = 0f
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        val ch = e.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                        if (!ch.pressed) return@withTimeoutOrNull true
                        val d = ch.positionChange(); mx += d.x; my += d.y
                        if (abs(mx) > slop || abs(my) > slop) { moved = true; return@withTimeoutOrNull true }
                    }
                    @Suppress("UNREACHABLE_CODE") true
                }
                if (released != null || moved) return@awaitEachGesture
            }
            onArm()
            onPull(dy.coerceAtLeast(0f))
            // Da qui il gesto è nostro: la lista non scorre, la riga non va di lato e alzando il dito non è un clic
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                e.changes.forEach { it.consume() }
                val ch = e.changes.firstOrNull { it.id == down.id } ?: break
                if (!ch.pressed) break
                dy += ch.positionChangeIgnoreConsumed().y
                onPull(dy.coerceAtLeast(0f))
            }
            onRelease()
        }
    }

/** A metà lista, quanto tenere ferma una riga prima di tirarla giù (dalla presa del numero non serve) */
private const val HOLD_MILLIS = 120L

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
    val passed = abs(offset.value) > width * 0.4f
    LaunchedEffect(passed) { if (passed) feel.threshold() }
    Box(
        Modifier.fillMaxWidth().clip(shape).onSizeChanged { width = it.width.coerceAtLeast(1) }
            .pointerInput(key) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            val v = offset.value
                            if (abs(v) > width * 0.4f) {
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

/** La foto della notifica (chat, telecamera, anteprima): larga quanto il testo, coi suoi rapporti, mai altissima. */
@Composable
private fun HeldPicture(name: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bmp by androidx.compose.runtime.produceState<ImageBitmap?>(null, name) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { android.graphics.BitmapFactory.decodeFile(app.sordina.data.NotifImages.file(context, name).path)?.asImageBitmap() }.getOrNull()
        }
    }
    val b = bmp ?: return
    androidx.compose.foundation.Image(
        b, null,
        Modifier.padding(top = 8.dp).fillMaxWidth()
            .aspectRatio((b.width.toFloat() / b.height).coerceIn(0.8f, 2.2f))
            .clip(RoundedCornerShape(12.dp)),
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
    )
}
