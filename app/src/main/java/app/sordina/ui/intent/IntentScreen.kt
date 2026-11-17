@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.sordina.ui.intent

import androidx.compose.ui.input.nestedscroll.nestedScroll

import app.sordina.R
import app.sordina.tr

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
import app.sordina.ui.imeJump
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue
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
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.coroutines.asCoroutineDispatcher
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
import app.sordina.data.Act
import app.sordina.data.AppCatalog
import app.sordina.data.AppEntry
import app.sordina.data.Candidate
import app.sordina.data.Kind
import app.sordina.data.Understanding
import app.sordina.ui.Sheet
import app.sordina.ui.SheetRow
import app.sordina.ui.clickableNoRipple
import app.sordina.ui.LocalFeel
import app.sordina.ui.surface
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
    /** Per le proposte che non sono app (persone, comandi, casa) */
    val whyOf: (Candidate) -> String = { "" },
    val hide: (Candidate) -> Unit = {},
    /** Fissare anche le azioni ("scrivi a Elisa") */
    val isPinnedAction: (Candidate) -> Boolean = { false },
    val togglePinAction: (Candidate) -> Unit = {},
)

/** Un avviso breve in basso, con Annulla per le azioni verso te stesso. */
data class Notice(val text: String, val undo: (() -> Unit)? = null, val id: Long = System.nanoTime())

/** Stato della voce. */
data class VoiceState(val available: Boolean, val listening: Boolean, val partial: String, val level: Float = 0f)

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
    proposals: List<Candidate>,
    needsUsagePermission: Boolean,
    actions: ProposalActions,
    keyboardOnOpen: Boolean,
    voice: VoiceState,
    notice: Notice?,
    onRun: (Candidate, Int) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onAllApps: () -> Unit,
    onHome: () -> Unit = {},
    onGrantUsage: () -> Unit,
    onVoiceDown: () -> Unit,
    onVoiceUp: () -> Unit,
    topDark: Boolean? = null,
    glass: Boolean = false,
    showIcons: Boolean = false,
) {
    val c = S.c
    val icons = remember { HashMap<String, androidx.compose.ui.graphics.ImageBitmap?>() }
    fun appIcon(a: AppEntry) = if (!showIcons) null else icons.getOrPut(a.key) { catalog.icon(a) }
    fun icon(cand: Candidate) = cand.app?.let { appIcon(it) }
    val focus = remember { FocusRequester() }
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    // Capire la frase legge rubrica, cervello e tendina: fuori dal thread dell'interfaccia, una alla volta e in ordine
    // (così quello che si impara dalle riformulazioni non dipende da quale calcolo finisce prima)
    var shown by remember { mutableStateOf("" to Understanding(emptyList(), false)) }
    LaunchedEffect(query) {
        // Un errore nel capire la frase non chiude l'app: resta "Cerca sul web" e l'errore va nel registro
        val r = kotlinx.coroutines.withContext(SEARCH_THREAD) { app.sordina.data.Faults.guard("search") { understand(query) } }
            ?: Understanding(emptyList(), false)
        shown = query to r
    }
    val result = shown.second
    /** Il risultato della frase scritta adesso: se il calcolo non è ancora arrivato (Invio subito), si fa qui */
    fun current() = if (shown.first == query) shown.second else app.sordina.data.Faults.guard("search") { understand(query) } ?: Understanding(emptyList(), false)
    val feelTop = LocalFeel.current
    LaunchedEffect(Unit) { if (keyboardOnOpen) runCatching { focus.requestFocus() } }    // Lo stesso gesto anche sopra i risultati: quello che la lista non usa per scorrere (già in cima, o già in fondo) passa qui.
    // Solo se il gesto è partito con la lista ferma al bordo: scorrendo i risultati non si esce per sbaglio
    val home by androidx.compose.runtime.rememberUpdatedState(onHome)
    val allApps by androidx.compose.runtime.rememberUpdatedState(onAllApps)
    val edge = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            var pulled = 0f
            var scrolled = false
            override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (source != androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput) return androidx.compose.ui.geometry.Offset.Zero
                if (consumed.y != 0f) scrolled = true
                pulled += available.y
                return androidx.compose.ui.geometry.Offset.Zero
            }
            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                val p = pulled; val s = scrolled
                pulled = 0f; scrolled = false
                if (!s && p > threshold) home() else if (!s && p < -threshold) allApps()
                return androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .nestedScroll(edge)
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    // In su: tutte le app; in giù (con la lista già in cima): si torna a Casa
                    onDragEnd = { if (total < -threshold) onAllApps() else if (total > threshold) onHome() },
                    onVerticalDrag = { _, dy -> total += dy },
                )
            }
            .systemBarsPadding()
            .imeJump(),
    ) {
        OnBackdrop(topDark) {
            // Tutte le app: un'icona sola, in alto a destra, lontana dal pollice
            Row(Modifier.fillMaxWidth().padding(start = 28.dp, top = 6.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                // Il titolo: la domanda della barra di Casa, qui scritta in grande
                Text(tr(R.string.home_search), style = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, color = S.c.ink))
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(surface())
                        .clickableNoRipple { feelTop.tick(); onAllApps() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Apps, tr(R.string.all_apps), Modifier.size(22.dp), tint = S.c.ink) }
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                if (query.isNotBlank()) {
                    val ready = result.candidates.firstOrNull()?.takeIf { result.confident && it.toOthers }
                    val first = result.candidates.firstOrNull()
                    if (ready != null) ReadyBlock(ready) { onRun(ready, 0) }
                    else if (first != null) EnterBlock(first, query, icon(first)) { onRun(first, 0) }
                    // Il resto, diviso per tipo come le categorie della pagina Sordina: titolo grande, quanti sono, un blocco
                    val rest = result.candidates.withIndex().filter { it.index > 0 }
                    KIND_ORDER.forEach { kind ->
                        val group = rest.filter { it.value.kind == kind }
                        if (group.isNotEmpty()) {
                            Section(kindTitle(kind), if (group.size > 1) "${group.size}" else null)
                            Column(Modifier.fillMaxWidth().clip(BLOCK).background(surface())) {
                                group.forEachIndexed { j, (i, cand) ->
                                    if (j > 0) Box(Modifier.fillMaxWidth().padding(start = 66.dp).height(1.dp).background(c.line))
                                    CandidateLine(cand, query, icon(cand)) { onRun(cand, i) }
                                }
                            }
                        }
                    }
                } else {
                    if (needsUsagePermission) {
                        Section(tr(R.string.learn_habits))
                        Row(
                            Modifier.fillMaxWidth().clip(BLOCK).background(surface()).clickableNoRipple(onGrantUsage)
                                .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(tr(R.string.usage_explain), Modifier.weight(1f), style = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, color = c.ink))
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.padding(start = 8.dp).size(20.dp), tint = c.soft)
                        }
                    }
                    if (proposals.isNotEmpty()) {
                        // Solo blocchetti, due per riga: comodi col pollice. Le fissate per prime, senza titoli
                        val (fixed, now) = proposals.partition { p -> p.app?.let { actions.isPinned(it) } ?: actions.isPinnedAction(p) }
                        Spacer(Modifier.height(14.dp))
                        run {
                            (fixed + now).take(6).chunked(2).forEach { row ->
                            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { p ->
                                    val a = p.app
                                    if (a != null) ProposalLine(a, actions, appIcon(a), Modifier.weight(1f)) { onLaunch(a) }
                                    else ActionProposal(p, actions, Modifier.weight(1f)) { onRun(p, -1) }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        // Un avviso vale per poco: uno vecchio (arrivato prima di aprire la ricerca) non compare, uno nuovo sparisce da solo
        notice?.let { n ->
            var visible by remember(n.id) { mutableStateOf(System.nanoTime() - n.id < NOTICE_NANOS) }
            LaunchedEffect(n.id) { if (visible) { kotlinx.coroutines.delay(((NOTICE_NANOS - (System.nanoTime() - n.id)) / 1_000_000).coerceAtLeast(0)); visible = false } }
            if (visible) Box(Modifier.padding(horizontal = 16.dp)) { NoticeBar(n) }
        }
        Spacer(Modifier.height(8.dp))
        // La barra; mentre tieni premuto il microfono ne esce un cerchio grande che respira con la voce, come in Casa
        val grow by androidx.compose.animation.core.animateFloatAsState(if (voice.listening) 1f else 0f, androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 500f), label = "mic")
        val level by androidx.compose.animation.core.animateFloatAsState(if (voice.listening) voice.level else 0f, androidx.compose.animation.core.tween(90), label = "level")
        Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(56.dp)) {
        Row(
            Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp))
                .background(surface()).padding(start = 22.dp, end = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text(
                    if (voice.listening) voice.partial.ifEmpty { tr(R.string.home_listening) } else tr(R.string.search_hint),
                    style = TextStyle(fontSize = 17.sp, color = if (voice.listening) c.ink else c.soft),
                )
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 17.sp, color = c.ink),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    // Invio: verso altri (chiamare, scrivere, aprire la porta) solo se la persona è sicura; se no restano le scelte
                    keyboardActions = KeyboardActions(onGo = { val r = current(); r.candidates.firstOrNull()?.takeIf { !it.toOthers || r.confident }?.let { onRun(it, 0) } }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (query.isNotEmpty()) Box(Modifier.size(42.dp).clip(CircleShape).clickableNoRipple { onQuery("") }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, tr(R.string.clear), Modifier.size(20.dp), tint = c.soft)
            }
            if (voice.available) Mic(grow, onVoiceDown, onVoiceUp)
        }
        if (grow > 0.01f) Box(
            Modifier.align(Alignment.CenterEnd).offset(x = 28.dp).requiredSize(88.dp + 36.dp)
                .graphicsLayer { scaleX = grow; scaleY = grow; alpha = grow },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.requiredSize(88.dp + 36.dp * level).clip(CircleShape).background(c.accent.copy(alpha = 0.25f)))
            Box(Modifier.requiredSize(88.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Mic, null, Modifier.size(34.dp), tint = c.bg)
            }
        }
        }
        // La barra sta dove sta in Casa; con la tastiera aperta resta lì se ci sta, altrimenti sale appena sopra
        Spacer(Modifier.height((app.sordina.ui.home.BAR_LIFT - app.sordina.ui.keyboardLift()).coerceAtLeast(12.dp)))
    }
}

/** Il blocco arrotondato di tutte le pagine (Sordina, Impostazioni). */
private val BLOCK = RoundedCornerShape(22.dp)

/** Titolo di sezione come le categorie della pagina Sordina: grande, e accanto, tenue, quanti sono. */
@Composable
private fun Section(title: String, count: String? = null) {
    Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = TextStyle(fontSize = 20.sp, color = S.c.ink))
        if (count != null) Text(count, Modifier.padding(start = 8.dp), style = TextStyle(fontSize = 14.sp, color = S.c.soft))
    }
}

/** Microfono: tieni premuto, parli, rilasci. */
@Composable
private fun Mic(grow: Float, onDown: () -> Unit, onUp: () -> Unit) {
    val c = S.c
    val feel = LocalFeel.current
    Box(
        Modifier.size(42.dp)
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
    ) { if (grow < 0.5f) Icon(Icons.Rounded.Mic, tr(R.string.speak), Modifier.size(22.dp), tint = c.accent) }
}

/** Azione verso un'altra persona, già pronta: serve il tuo tocco. Un blocco, e sotto il pulsante principale. */
@Composable
private fun ReadyBlock(cand: Candidate, onGo: () -> Unit) {
    val c = S.c
    val feel = LocalFeel.current
    // Subito in cima, come il primo risultato: la nota sta dentro il blocco
    Spacer(Modifier.height(6.dp))
    Column(Modifier.fillMaxWidth().clip(BLOCK).background(surface()).padding(14.dp)) {
        Text(tr(R.string.ready_touch), Modifier.padding(bottom = 10.dp), style = TextStyle(fontSize = 13.sp, color = c.soft))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Leading(cand, null, 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(cand.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 19.sp, lineHeight = 24.sp, color = c.ink))
                cand.subtitle?.let { Text(it, Modifier.padding(top = 2.dp), maxLines = 3, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, color = c.soft)) }
            }
        }
        // Il pulsante principale, uguale ovunque: largo, alto 52
        Box(
            Modifier.padding(top = 14.dp).fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(c.ink)
                .clickableNoRipple { feel.confirm(); onGo() },
            contentAlignment = Alignment.Center,
        ) { Text(if (cand.act is Act.Call) tr(R.string.call) else tr(R.string.open_chat), style = TextStyle(fontSize = 16.sp, color = c.bg)) }
    }
}

private val KIND_ORDER = listOf(Kind.APP, Kind.PERSON, Kind.HOME, Kind.ACTION, Kind.SETTING, Kind.CALC, Kind.WEB)

private fun kindTitle(k: Kind) = when (k) {
    Kind.APP -> tr(R.string.kind_apps)
    Kind.PERSON -> tr(R.string.kind_people)
    Kind.HOME -> tr(R.string.kind_home)
    Kind.ACTION -> tr(R.string.kind_actions)
    Kind.SETTING -> tr(R.string.kind_settings)
    Kind.CALC -> tr(R.string.kind_calc)
    Kind.WEB -> tr(R.string.kind_web)
}

/** Cosa fa Invio, detto in chiaro. */
private fun enterVerb(cand: Candidate): String = when (cand.kind) {
    Kind.APP -> tr(R.string.open_x, cand.title)
    Kind.WEB -> cand.title
    else -> cand.title
}

/** Il primo risultato, in evidenza: un blocco da solo, più grande; il tasto tondo scuro dice che parte con Invio. */
@Composable
private fun EnterBlock(cand: Candidate, query: String, icon: androidx.compose.ui.graphics.ImageBitmap?, onClick: () -> Unit) {
    val c = S.c
    val feel = LocalFeel.current
    Spacer(Modifier.height(6.dp))
    Row(
        Modifier.fillMaxWidth().clip(BLOCK).background(surface())
            .combinedClickable(onClick = { feel.tick(); onClick() }).padding(start = 14.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Leading(cand, icon, 48.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                bold(enterVerb(cand), query), color = c.ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 20.sp),
            )
            val sub = cand.subtitle ?: if (cand.kind == Kind.APP) tr(R.string.open_app) else kindTitle(cand.kind)
            Text(sub, Modifier.padding(top = 1.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 14.sp, color = c.soft))
        }
        Box(
            Modifier.padding(start = 10.dp).size(40.dp).clip(CircleShape).background(c.ink),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Rounded.KeyboardReturn, tr(R.string.enter), Modifier.size(19.dp), tint = c.bg) }
    }
}

/** Una riga in un blocco di sezione: come le righe delle Impostazioni. */
@Composable
private fun CandidateLine(cand: Candidate, query: String, icon: androidx.compose.ui.graphics.ImageBitmap?, onClick: () -> Unit) {
    val c = S.c
    val feel = LocalFeel.current
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = { feel.tick(); onClick() }).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Leading(cand, icon, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (cand.kind == Kind.WEB) AnnotatedString(cand.title) else bold(cand.title, query), color = c.ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 17.sp),
            )
            cand.subtitle?.let { Text(it, Modifier.padding(top = 1.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 13.sp, color = c.soft)) }
        }
    }
}

/** Le lettere scritte in grassetto. */
private fun bold(text: String, query: String): AnnotatedString {
    val q = query.trim()
    val i = if (q.isEmpty()) -1 else text.indexOf(q, ignoreCase = true)
    return buildAnnotatedString {
        if (i < 0) { append(text); return@buildAnnotatedString }
        append(text.substring(0, i))
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(text.substring(i, i + q.length)) }
        append(text.substring(i + q.length))
    }
}

/**
 * A sinistra, come nella pagina Sordina e nelle Impostazioni: l'icona dell'app se attive;
 * una persona è un tondo con l'iniziale; un comando è un tondo tenue con il suo simbolo nell'accento.
 */
@Composable
private fun Leading(cand: Candidate, icon: androidx.compose.ui.graphics.ImageBitmap?, size: androidx.compose.ui.unit.Dp) {
    val c = S.c
    if (icon != null) {
        androidx.compose.foundation.Image(icon, null, Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)))
        return
    }
    val person = cand.kind == Kind.PERSON
    val glyph = when (cand.kind) {
        Kind.WEB -> Icons.Rounded.Search
        Kind.SETTING -> Icons.Rounded.Settings
        Kind.HOME -> Icons.Rounded.Home
        Kind.CALC -> Icons.Rounded.Calculate
        Kind.ACTION -> when (cand.act) { is Act.Call -> Icons.Rounded.Call; is Act.Message -> Icons.AutoMirrored.Rounded.Chat; is Act.Timer -> Icons.Rounded.Timer; else -> Icons.Rounded.Bolt }
        else -> null
    }
    Box(
        Modifier.size(size).clip(if (person || glyph != null) CircleShape else RoundedCornerShape(size * 0.3f))
            .background(when { person -> c.accent.copy(alpha = 0.16f); glyph != null -> c.accent.copy(alpha = 0.12f); else -> c.line }),
        contentAlignment = Alignment.Center,
    ) {
        if (glyph != null) Icon(glyph, null, Modifier.size(size * 0.48f), tint = c.accent)
        else Text(
            // L'iniziale di chi o di cosa: il nome vero, non il titolo (che cambia con la lingua: "Chiama …", "Apri …")
            when (val a = cand.act) { is Act.Call -> a.contact.name; is Act.Message -> a.contact.name; else -> cand.app?.label ?: cand.title }.trim().take(1).uppercase(),
            style = TextStyle(fontSize = (size.value * 0.42f).sp, color = if (person) c.accent else c.soft),
        )
    }
}

@Composable
private fun ProposalLine(app: AppEntry, actions: ProposalActions, icon: androidx.compose.ui.graphics.ImageBitmap?, modifier: Modifier, onClick: () -> Unit) {
    val feel = LocalFeel.current
    val c = S.c
    var menu by remember { mutableStateOf(false) }
    val pinned = actions.isPinned(app)
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).clip(BLOCK).background(surface())
                .combinedClickable(onClick = { feel.tick(); onClick() }, onLongClick = { feel.tick(); menu = true })
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) androidx.compose.foundation.Image(icon, null, Modifier.padding(end = 12.dp).size(32.dp).clip(RoundedCornerShape(10.dp)))
            Text(app.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 17.sp, color = c.ink))
        }
        // Fissata: un pallino nel colore principale, nell'angolo
        if (pinned) Box(Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 12.dp).size(7.dp).clip(CircleShape).background(c.accent))
        // Tieni premuto: pochi comandi, solo quelli di questo posto. Freno e info stanno in Tutte le app e nelle Impostazioni
        if (menu) Sheet(app.label, onDismiss = { menu = false }, leading = icon?.let { b -> { androidx.compose.foundation.Image(b, null, Modifier.size(28.dp)) } }) {
            if (pinned) SheetRow(tr(R.string.unpin), Icons.Rounded.PushPin) { menu = false; actions.togglePin(app) }
            else {
                SheetRow(tr(R.string.pin), Icons.Rounded.PushPin) { menu = false; actions.togglePin(app) }
                SheetRow(tr(R.string.dont_suggest), Icons.Rounded.VisibilityOff) { menu = false; actions.exclude(app) }
            }
        }
    }
}

/** Una proposta che non è un'app: una persona, un comando che ripeti, la casa. Stesso blocchetto, con il suo simbolo. */
@Composable
private fun ActionProposal(cand: Candidate, actions: ProposalActions, modifier: Modifier, onClick: () -> Unit) {
    val feel = LocalFeel.current
    val c = S.c
    var menu by remember { mutableStateOf(false) }
    val pinned = actions.isPinnedAction(cand)
    val glyph = when (val a = cand.act) {
        is Act.Call -> Icons.Rounded.Call
        is Act.Message -> Icons.AutoMirrored.Rounded.Chat
        is Act.Assist -> Icons.Rounded.Home
        is Act.Setting, Act.SordinaSettings -> Icons.Rounded.Settings
        else -> if (a is Act.Timer) Icons.Rounded.Timer else Icons.Rounded.Bolt
    }
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).clip(BLOCK).background(surface())
                .combinedClickable(onClick = { feel.tick(); onClick() }, onLongClick = { feel.tick(); menu = true })
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Tondo tenue nell'accento, come i simboli delle Impostazioni
            Box(Modifier.padding(end = 12.dp).size(32.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(glyph, null, Modifier.size(17.dp), tint = c.accent)
            }
            Text(cand.title, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 15.sp, lineHeight = 18.sp, color = c.ink))
        }
        // Come per le app: fissata → solo "Togli da qui"; altrimenti Fissa o Non proporre
        if (pinned) Box(Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 12.dp).size(7.dp).clip(CircleShape).background(c.accent))
        if (menu) Sheet(cand.title, onDismiss = { menu = false }) {
            if (pinned) SheetRow(tr(R.string.unpin), Icons.Rounded.PushPin) { menu = false; actions.togglePinAction(cand) }
            else {
                SheetRow(tr(R.string.pin), Icons.Rounded.PushPin) { menu = false; actions.togglePinAction(cand) }
                SheetRow(tr(R.string.dont_suggest), Icons.Rounded.VisibilityOff) { menu = false; actions.hide(cand) }
            }
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
            Text(tr(R.string.cancel), Modifier.clickableNoRipple { visible = false; u() }.padding(start = 12.dp), style = TextStyle(fontSize = 14.sp, color = c.accent))
        }
    }
}

/** Il thread della ricerca: uno solo, così le frasi si capiscono nell'ordine in cui le scrivi */
private val SEARCH_THREAD = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "sordina-ricerca").apply { isDaemon = true } }.asCoroutineDispatcher()

/** Quanto resta un avviso nella ricerca (anche con "Annulla"): 8 secondi */
private const val NOTICE_NANOS = 8_000_000_000L
