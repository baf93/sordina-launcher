package app.sordina.ui

import app.sordina.data.Backup
import app.sordina.ui.home.BubbleShape
import app.sordina.tr

import app.sordina.R

import android.Manifest
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.text.drawText
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.sordina.service.SordinaListener
import app.sordina.service.SamsungSleep
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sordina.SordinaApp
import app.sordina.data.Contact
import app.sordina.data.HomeAssistant
import app.sordina.data.Level
import app.sordina.ui.theme.S
import app.sordina.data.AppEntry
import app.sordina.data.Categories
import app.sordina.data.Search
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import app.sordina.trn
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.animation.core.animateFloat
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/** I passi del primo avvio, in tre parti: prima si guarda come funziona, poi i permessi, poi le scelte. */
private enum class Step(val part: Int) {
    HI(0),
    WAIT(1), FIND(1), BRAKE(1), ASK(1),
    P_NOTIF(2), P_HOME(2), P_BATTERY(2), P_SAMSUNG(2), P_POST(2),
    C_IMPORTANT(3), C_APPS(3), C_PEOPLE(3), C_BRAKE(3), C_HOME(3), C_HA(3),
    DONE(4),
}

/**
 * Primo avvio. Una cosa per schermata: in alto l'onda che si calma a ogni passo e dove sei ("Permessi · 2 di 4"),
 * poi il titolo, una frase e al centro quello che serve vedere. I permessi si chiedono uno alla volta, dicendo
 * prima cosa succederà in Android; quando torni c'è la spunta.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun Onboarding(
    app: SordinaApp,
    perms: PermState,
    apps: List<AppEntry>,
    usage: Map<String, Double>,
    ops: Ops,
    startStep: Int = 0,
    onStep: (Int) -> Unit = {},
    onDone: () -> Unit,
) {
    val steps = remember {
        Step.entries.filter {
            when (it) {
                Step.P_SAMSUNG -> SamsungSleep.isSamsung
                Step.P_POST -> Build.VERSION.SDK_INT >= 33
                else -> true
            }
        }
    }
    val last = steps.size - 1
    var i by rememberSaveable(startStep) { mutableIntStateOf(startStep.coerceIn(0, last)) }
    val step = steps[i]
    // Si ricorda il passo: scegliere Sordina come Home la fa ripartire, e non deve ricominciare da capo
    LaunchedEffect(i) { onStep(i) }
    // Una copia ritrovata dal backup di Google (telefono nuovo o app reinstallata): si può rimettere tutto com'era
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var found by remember { mutableStateOf(Backup.found(ctx)) }
    // Samsung non dice se Sordina è fra le app mai in sospensione: basta aver aperto la pagina
    var samsungDone by remember { mutableStateOf(app.prefs.samsungAsked > 0) }
    var haOpen by rememberSaveable { mutableStateOf(app.ha.configured) }
    val c = S.c
    fun next() { if (i < last) i++ else onDone() }
    androidx.activity.compose.BackHandler(enabled = i > 0) { i-- }

    // Un permesso che manca: il pulsante lo chiede, sotto c'è "Più tardi"
    val ask: (() -> Unit)? = when (step) {
        Step.P_NOTIF -> if (!perms.listener) ({ ops.openListenerSettings() }) else null
        Step.P_HOME -> if (!perms.home) ({ ops.openHomeSettings() }) else null
        Step.P_BATTERY -> if (!perms.battery) ({ ops.openBatterySettings() }) else null
        Step.P_SAMSUNG -> if (!samsungDone) ({ SamsungSleep.openBattery(ctx); samsungDone = true }) else null
        Step.P_POST -> if (!perms.post) ({ ops.request(Manifest.permission.POST_NOTIFICATIONS) }) else null
        else -> null
    }
    val (label, action) = when {
        step == Step.HI && found != null -> tr(R.string.ob_restore) to { if (Backup.restore(ctx)) Backup.restart(ctx) }
        step == Step.HI -> tr(R.string.ob_hi_go) to { next() }
        ask != null -> tr(when (step) {
            Step.P_HOME -> R.string.ob2_home_btn
            Step.P_POST -> R.string.ob_allow
            else -> R.string.ob2_open_settings
        }) to ask
        // "Non ce l'ho": Home Assistant resta spento; si accende da Impostazioni se lo installi
        step == Step.C_HA && !haOpen && !app.ha.configured -> tr(R.string.ob_ha_skip) to { app.prefs.haEnabled = false; ops.changed(); next() }
        step == Step.DONE -> tr(R.string.onb_go_home) to { onDone() }
        else -> tr(R.string.next) to { next() }
    }
    val restore = step == Step.HI && found != null
    val later = ask != null || restore

    Column(Modifier.fillMaxSize().background(c.bg).systemBarsPadding().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 16.dp)) {
        // In testa l'onda di Sordina: a ogni passo si calma un po', all'ultimo è quasi piatta
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettleWave(i, last, Modifier.weight(1f).height(26.dp))
            val inPart = steps.filter { it.part == step.part }
            if (step.part in 1..3) {
                Spacer(Modifier.width(16.dp))
                val name = tr(when (step.part) { 1 -> R.string.ob2_part1; 2 -> R.string.ob2_part2; else -> R.string.ob2_part3 })
                androidx.compose.material3.Text(tr(R.string.ob2_where, name, inPart.indexOf(step) + 1, inPart.size),
                    style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = c.soft))
            }
        }
        AnimatedContent(i, transitionSpec = { Motion.forward(targetState > initialState) }, label = "step", modifier = Modifier.weight(1f)) { s ->
            when (steps[s]) {
                Step.HI -> Welcome(found)
                Step.WAIT -> Waiting()
                Step.FIND -> FindThem()
                Step.BRAKE -> BrakeDemo()
                Step.ASK -> AskMe(app)
                Step.P_NOTIF -> PermStep(R.string.ob2_pn_title, R.string.ob2_pn_why, perms.listener,
                    R.string.ob2_pn_s1, R.string.ob2_pn_s2, R.string.ob2_pn_s3, R.string.ob2_back_here)
                // Xiaomi (anche Redmi e POCO) toglie le gesture di sistema con un launcher che non è il suo: meglio saperlo prima
                Step.P_HOME -> PermStep(R.string.ob2_ph_title, R.string.ob2_ph_why, perms.home,
                    R.string.ob2_ph_s1, R.string.ob2_ph_s2, R.string.ob2_ph_s3,
                    note = if (Build.MANUFACTURER.equals("xiaomi", ignoreCase = true)) R.string.ob2_ph_xiaomi else null)
                Step.P_BATTERY -> PermStep(R.string.ob2_pb_title, R.string.ob2_pb_why, perms.battery,
                    R.string.ob2_pb_s1, R.string.ob2_pb_s2)
                Step.P_SAMSUNG -> PermStep(R.string.ob2_ps_title, R.string.ob2_ps_why, samsungDone,
                    R.string.ob2_ps_s1, R.string.ob2_ps_s2, R.string.ob2_ps_s3, R.string.ob2_ps_s4, R.string.ob2_back_here)
                Step.P_POST -> PermStep(R.string.ob2_pp_title, R.string.ob2_pp_why, perms.post,
                    R.string.ob2_pp_s1, R.string.ob2_pp_s2)
                Step.C_IMPORTANT -> Importants(app, ops)
                Step.C_APPS -> AlwaysApps(app, apps, ops)
                Step.C_PEOPLE -> Faces(app, perms, ops)
                Step.C_BRAKE -> Distracting(app, apps, usage, perms, ops)
                Step.C_HOME -> HomeChoices(app, perms, ops)
                Step.C_HA -> HaStep(app, ops, haOpen) { haOpen = true }
                Step.DONE -> AllSet(app, apps, perms, ops)
            }
        }
        Spacer(Modifier.height(14.dp))
        StartPill(label, accent = step == Step.DONE) { action() }
        // "Più tardi" ai permessi, finché ne manca uno: un "no" ad Android non deve bloccare l'avvio
        if (later) androidx.compose.foundation.layout.Box(
            Modifier.fillMaxWidth().clickableNoRipple { if (restore) { Backup.forget(ctx); found = null } else next() }.padding(top = 14.dp, bottom = 4.dp),
            contentAlignment = Alignment.Center,
        ) { Serif(tr(if (restore) R.string.ob_restore_no else R.string.later), size = 14.sp, color = c.soft) }
    }
}

/** Il pulsante dell'avvio: pieno, alto come la barra di Casa. */
@Composable
private fun StartPill(text: String, accent: Boolean = false, onClick: () -> Unit) {
    val c = S.c
    val feel = LocalFeel.current
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxWidth().height(52.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(26.dp)).background(if (accent) c.accent else c.ink)
            .clickable { feel.confirm(); onClick() },
        contentAlignment = Alignment.Center,
    ) { androidx.compose.material3.Text(text, style = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, color = c.bg)) }
}

/**
 * L'onda smorzata che si calma passo dopo passo: al primo è mossa, all'ultimo è una linea quasi ferma
 * che finisce nel punto d'accento. Si muove piano, sempre meno.
 */
@Composable
private fun SettleWave(step: Int, last: Int, modifier: Modifier = Modifier) {
    val c = S.c
    val amp by androidx.compose.animation.core.animateFloatAsState(
        1f - 0.94f * step / last, androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 60f), label = "amp",
    )
    val phase by androidx.compose.animation.core.rememberInfiniteTransition(label = "wave").animateFloat(
        0f, (2 * Math.PI).toFloat(), androidx.compose.animation.core.infiniteRepeatable(tween(5200, easing = androidx.compose.animation.core.LinearEasing)), label = "phase",
    )
    androidx.compose.foundation.Canvas(modifier) {
        val dot = 4.dp.toPx()
        val w = size.width - dot * 3
        val mid = size.height / 2
        val maxA = size.height / 2 - 3.dp.toPx()
        val waveLen = 46.dp.toPx()
        val path = androidx.compose.ui.graphics.Path()
        var x = 0f
        while (x <= w) {
            // Smorzata verso destra: più ampia all'inizio, ferma vicino al punto
            val damp = kotlin.math.exp(-2.2f * x / w)
            val y = mid - maxA * amp * damp * kotlin.math.sin(x / waveLen * 2 * Math.PI.toFloat() - phase * amp)
            if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
            x += 2f
        }
        drawPath(path, c.ink, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
        drawCircle(c.accent, dot, androidx.compose.ui.geometry.Offset(size.width - dot, mid))
    }
}

/**
 * Schermata tipo: in alto l'etichetta in accento, il titolo e la frase; al centro la cosa da vedere.
 * [collapsed]: la lista sotto è stata fatta scorrere, il titolo si riduce e la frase sparisce per lasciarle posto.
 * [onSearch]: c'è una lista da filtrare; la lente accanto al titolo apre il campo al suo posto.
 */
@Composable
private fun Screen(
    label: String, title: String, text: String,
    collapsed: Boolean = false,
    query: String = "", onSearch: ((String?) -> Unit)? = null, searchHint: String = "",
    /** La sordina che parla: sta sopra il titolo, come se lo dicesse lei */
    mascot: Mood? = null,
    visual: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val c = S.c
    val size by androidx.compose.animation.core.animateFloatAsState(if (collapsed) 21f else 32f, tween(220), label = "title")
    var searching by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(top = 18.dp)) {
        if (mascot != null) { Mascot(mascot, Modifier.size(76.dp)); Spacer(Modifier.height(10.dp)) }
        if (label.isNotEmpty()) {
            androidx.compose.material3.Text(label.uppercase(), style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, letterSpacing = 1.2.sp, color = c.accent))
            Spacer(Modifier.height(6.dp))
        }
        if (searching && onSearch != null) {
            val focus = remember { androidx.compose.ui.focus.FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocus() }
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) androidx.compose.material3.Text(searchHint, style = androidx.compose.ui.text.TextStyle(fontSize = 21.sp, color = c.soft))
                    androidx.compose.foundation.text.BasicTextField(
                        query, { onSearch(it) }, singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 21.sp, color = c.ink),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent), modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                androidx.compose.material3.Icon(Icons.Rounded.Close, tr(R.string.clear), Modifier.clickableNoRipple { searching = false; onSearch(null) }.padding(start = 12.dp).size(24.dp), tint = c.soft)
            }
        } else Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Text(title, Modifier.weight(1f), style = androidx.compose.ui.text.TextStyle(fontSize = size.sp, lineHeight = (size * 1.2f).sp, color = c.ink))
            if (onSearch != null) androidx.compose.material3.Icon(Icons.Rounded.Search, searchHint, Modifier.clickableNoRipple { searching = true }.padding(start = 12.dp).size(26.dp), tint = c.ink)
        }
        androidx.compose.animation.AnimatedVisibility(!collapsed && !searching) {
            Column {
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.Text(text, style = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, lineHeight = 21.sp, color = c.soft))
            }
        }
        Spacer(Modifier.height(14.dp))
        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center, content = visual)
    }
}

private fun hm(m: Int): String = java.time.LocalTime.of((m / 60) % 24, m % 60)
    .format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))

/** L'icona di un'app, piccola e in memoria. */
@Composable
private fun appIcon(pkg: String, px: Int = 144): androidx.compose.ui.graphics.ImageBitmap? {
    val pm = androidx.compose.ui.platform.LocalContext.current.packageManager
    return remember(pkg) {
        app.sordina.data.AppIcons.forPackage(pm, pkg, px)
    }
}

// 0 · La sordina ti accoglie: dice lei cosa succede adesso. Se c'è una copia nel backup di Google, lo dice lei.

@Composable
private fun Welcome(found: java.time.LocalDate?) {
    val c = S.c
    val anim = LocalFeel.current.animations
    val text = found?.let { tr(R.string.ob_restore_found, it.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.LONG))) }
        ?: tr(R.string.ob2_hi_text)
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        // Il fumetto, con la punta verso di lei
        Column(
            Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(26.dp)).background(surface())
                .padding(horizontal = 22.dp, vertical = 20.dp),
        ) {
            androidx.compose.material3.Text(tr(R.string.ob_hi_title), style = androidx.compose.ui.text.TextStyle(fontSize = 28.sp, lineHeight = 34.sp, color = c.ink))
            Spacer(Modifier.height(10.dp))
            Typed(text, text.hashCode().toLong(), anim, androidx.compose.ui.text.style.TextAlign.Start)
        }
        androidx.compose.foundation.layout.Box(Modifier.size(width = 24.dp, height = 12.dp).background(surface(), DownTail))
        Spacer(Modifier.height(6.dp))
        Mascot(Mood.HELLO, Modifier.size(150.dp))
    }
}

/** La punta di un fumetto, verso il basso. */
private val DownTail = androidx.compose.foundation.shape.GenericShape { size, _ ->
    moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2f, size.height); close()
}

// Home Assistant, se ce l'hai: prima la domanda, il modulo solo se rispondi di sì.

@Composable
private fun HaStep(app: SordinaApp, ops: Ops, open: Boolean, onYes: () -> Unit) {
    Screen("", tr(R.string.ob2_ha_title), tr(R.string.ob2_ha_sub)) {
        if (!open) Column(Modifier.fillMaxSize(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
            Secondary(tr(R.string.ob2_ha_yes), Modifier.fillMaxWidth()) { app.prefs.haEnabled = true; ops.changed(); onYes() }
        } else Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {
            HaSettings(app, ops) {}
            Spacer(Modifier.height(8.dp))
        }
    }
}

// 1 · Le notifiche cadono in fila, aspettano, e all'ora del riepilogo arrivano insieme

@Composable
private fun Waiting() {
    val c = S.c
    // Esempi inventati: mai le notifiche vere di chi installa
    val rows = listOf(
        Icons.Rounded.MailOutline to tr(R.string.ob1_n1), Icons.Rounded.Groups to tr(R.string.ob1_n2),
        Icons.Rounded.FavoriteBorder to tr(R.string.ob1_n3), Icons.Rounded.LocalShipping to tr(R.string.ob1_n4),
    )
    val t by androidx.compose.animation.core.rememberInfiniteTransition(label = "fall").animateFloat(
        0f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(6500, easing = androidx.compose.animation.core.LinearEasing)), label = "t",
    )
    Screen("", tr(R.string.ob2_w_title), tr(R.string.ob2_w_sub)) {
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            rows.forEachIndexed { i, (glyph, text) ->
                // Entra dall'alto uno dopo l'altro, resta, poi scende e sparisce quando arriva il riepilogo
                val inAt = 0.04f + i * 0.06f
                val p = ((t - inAt) / 0.14f).coerceIn(0f, 1f)
                val out = ((t - 0.74f) / 0.12f).coerceIn(0f, 1f)
                val ease = 1f - (1f - p) * (1f - p) * (1f - p)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).height(48.dp)
                        .graphicsLayer { translationY = (1f - ease) * -120f + out * 60f; alpha = ease * (1f - out); scaleX = 1f - out * 0.06f; scaleY = scaleX }
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).background(surface()).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.foundation.layout.Box(Modifier.size(26.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).background(c.tile), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.Icon(glyph, null, Modifier.size(16.dp), tint = c.soft)
                    }
                    Spacer(Modifier.width(12.dp))
                    androidx.compose.material3.Text(text, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        style = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = c.ink))
                }
            }
            Spacer(Modifier.height(14.dp))
            val clock = (((t - 0.66f) / 0.06f).coerceIn(0f, 1f)) * (1f - ((t - 0.94f) / 0.06f).coerceIn(0f, 1f))
            androidx.compose.material3.Text(tr(R.string.ob2_w_at), Modifier.graphicsLayer { alpha = clock },
                style = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = c.accent))
        }
    }
}

/** Una cosa che manca, con il perché; tocca per sistemarla. Spuntata quando c'è. */
@Composable
private fun Need(ok: Boolean, label: String, why: String, onAsk: () -> Unit) {
    val c = S.c
    Row(Modifier.fillMaxWidth().let { if (!ok) it.clickableNoRipple(onAsk) else it }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(24.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .let { if (ok) it.background(c.accent) else it.border(1.5.dp, c.dot, androidx.compose.foundation.shape.CircleShape) },
            contentAlignment = Alignment.Center,
        ) { if (ok) androidx.compose.material3.Icon(Icons.Rounded.Check, null, Modifier.size(15.dp), tint = c.bg) }
        Spacer(Modifier.width(14.dp))
        Column {
            androidx.compose.material3.Text(label, style = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, color = c.ink))
            androidx.compose.material3.Text(why, style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = c.soft))
        }
    }
}

// 3 · I volti: i preferiti della rubrica, tocca per farli passare sempre

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Faces(app: SordinaApp, perms: PermState, ops: Ops) {
    val c = S.c
    var all by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var chosen by remember { mutableStateOf(app.prefs.importantNames) }
    LaunchedEffect(perms.contacts) { if (perms.contacts) all = withContext(Dispatchers.IO) { app.people.invalidate(); app.people.all() } }
    fun toggle(ct: Contact) {
        // Togliere qui non tocca i preferiti di Android: erano tuoi prima di Sordina
        if (ct.normalized in chosen) app.people.removeImportantName(ct.normalized, unstar = false)
        else app.people.setImportant(ct, true)
        chosen = app.prefs.importantNames
        ops.changed()
    }
    var q by remember { mutableStateOf("") }
    var small by remember { mutableStateOf(false) }
    Screen("", tr(R.string.ob2_people_title), tr(R.string.ob2_people_sub), collapsed = small,
        query = q, onSearch = if (perms.contacts) ({ q = it ?: "" }) else null, searchHint = tr(R.string.search_contact)) {
        if (!perms.contacts) {
            Secondary(tr(R.string.onb_allow_contacts), Modifier.fillMaxWidth()) { ops.request(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS) }
        } else {
            PickList(all.distinctBy { it.normalized }.sortedBy { it.name.lowercase() }, { it.normalized }, { it.name }, q,
                leading = { ct -> Initial(ct.name) }, selected = chosen, onScrolled = { small = it }) { toggle(it) }
        }
    }
}

// 4 · Le app che passano sempre: suonano subito, come prima di Sordina

@Composable
private fun AlwaysApps(app: SordinaApp, apps: List<AppEntry>, ops: Ops) {
    // Le frenate no: restano in sordina
    val installed = remember(apps) { apps.filter { !it.isWork && it.packageName !in app.prefs.braked }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() } }
    var chosen by remember { mutableStateOf(app.prefs.appLevels.filterValues { it == Level.SEMPRE }.keys) }
    fun toggle(pkg: String) {
        app.prefs.setAppLevel(pkg, if (pkg in chosen) null else Level.SEMPRE)
        chosen = app.prefs.appLevels.filterValues { it == Level.SEMPRE }.keys
        ops.changed()
        // Le sue notifiche già in sordina (o nella tendina) passano dalla regola nuova adesso, non alla prossima
        SordinaListener.reapply(app, pkg)
    }
    var q by remember { mutableStateOf("") }
    var small by remember { mutableStateOf(false) }
    Screen("", tr(R.string.ob2_apps_title), tr(R.string.ob2_apps_sub), collapsed = small,
        query = q, onSearch = { q = it ?: "" }, searchHint = tr(R.string.s_search_app)) {
        PickList(installed, { it.packageName }, { it.label }, q,
            leading = { a -> AppDot(a.packageName) }, selected = chosen, onScrolled = { small = it }) { toggle(it.packageName) }
    }
}

// 5 · Le icone delle app che distraggono; l'attesa è l'anello che si riempie

/** Video in streaming: la categoria dichiarata dalle app non basta (molte non la dichiarano). */
private val VIDEO = setOf(
    "com.netflix.mediaclient", "com.amazon.avod.thirdpartyclient", "com.disney.disneyplus", "com.google.android.youtube",
    "tv.twitch.android.app", "com.dazn", "it.mediaset.mediasetplay", "it.rai.raiplay", "com.hbo.hbonow", "com.apple.atve.androidtv.appletv",
)

/** Le app di chat non sono distrazioni: passano dalle regole delle persone. */
private val CHATS = setOf(
    "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.thoughtcrime.securesms", "com.facebook.orca",
    "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.discord", "com.Slack", "com.microsoft.teams",
)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Distracting(app: SordinaApp, apps: List<AppEntry>, usage: Map<String, Double>, perms: PermState, ops: Ops) {
    val installed = remember(apps) { apps.filter { !it.isWork && !it.isWeb }.distinctBy { it.packageName } }
    val candidates = remember(installed, usage) {
        installed.filter { a ->
            val cat = a.info?.applicationInfo?.category ?: -1
            a.packageName !in CHATS && (Categories.isSocial(a.packageName) || a.packageName in VIDEO ||
                cat == android.content.pm.ApplicationInfo.CATEGORY_VIDEO || cat == android.content.pm.ApplicationInfo.CATEGORY_GAME)
        }.sortedWith(compareByDescending<AppEntry> { usage[it.packageName] ?: 0.0 }.thenBy { it.label.lowercase() })
    }
    var braked by remember { mutableStateOf(app.prefs.braked) }
    // La prima volta le più usate sono già scelte: si toglie, più che aggiungere
    LaunchedEffect(candidates, perms.usage) {
        // Senza l'utilizzo delle app non so quali usi: aspetto il permesso (o il tuo "più tardi")
        if (!app.prefs.brakeSuggested && candidates.isNotEmpty() && perms.usage && usage.isNotEmpty()) {
            app.prefs.brakeSuggested = true
            if (app.prefs.braked.isEmpty()) {
                app.prefs.braked = candidates.filter { (usage[it.packageName] ?: 0.0) > 0 }.ifEmpty { candidates }.take(3).map { it.packageName }.toSet()
                braked = app.prefs.braked
                ops.changed()
            }
        }
    }
    fun toggle(pkg: String) {
        app.prefs.braked = if (pkg in braked) braked - pkg else braked + pkg
        braked = app.prefs.braked
        ops.changed()
    }
    var q by remember { mutableStateOf("") }
    var small by remember { mutableStateOf(false) }
    Screen("", tr(R.string.ob2_brake_title), tr(R.string.ob2_brake_sub), collapsed = small,
        query = q, onSearch = { q = it ?: "" }, searchHint = tr(R.string.s_add_app)) {
        // Senza l'utilizzo delle app si sceglie a mano; con il permesso le più usate sono già spuntate
        if (!perms.usage) Column(Modifier.fillMaxSize()) {
            Secondary(tr(R.string.ob2_brake_usage), Modifier.fillMaxWidth()) { ops.openUsageSettings() }
            Spacer(Modifier.height(10.dp))
            PickList(installed.sortedBy { it.label.lowercase() }, { it.packageName }, { it.label }, q,
                leading = { a -> AppDot(a.packageName) }, selected = braked, onScrolled = { small = it }) { toggle(it.packageName) }
        } else PickList(installed.sortedBy { it.label.lowercase() }, { it.packageName }, { it.label }, q,
            leading = { a -> AppDot(a.packageName) }, selected = braked, onScrolled = { small = it }) { toggle(it.packageName) }
    }
}

// 5 · La Home: cosa vedi quando prendi in mano il telefono. Meteo e impegni chiedono il loro permesso al tocco;
// sera, notte e auto la cambiano da sole. Tutto già acceso: si spegne quello che non serve.

@Composable
private fun HomeChoices(app: SordinaApp, perms: PermState, ops: Ops) {
    val prefs = app.prefs
    var weather by remember { mutableStateOf(prefs.homeWeather) }
    var agenda by remember { mutableStateOf(prefs.homeAgenda) }
    // Acceso per davvero solo con il permesso: finché Android non risponde resta spento
    val weatherOn = weather && perms.location
    val agendaOn = agenda && perms.calendar
    Screen("", tr(R.string.ob2_home_title), tr(R.string.ob2_home_sub)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            MomentCard(Icons.Rounded.WbSunny, tr(R.string.s_weather), tr(R.string.ob2_home_weather), weatherOn) {
                weather = !weatherOn; prefs.homeWeather = weather; ops.changed()
                if (weather && !perms.location) ops.request(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            MomentCard(Icons.Rounded.Schedule, tr(R.string.n_agenda), tr(R.string.ob2_home_agenda), agendaOn) {
                agenda = !agendaOn; prefs.homeAgenda = agenda; ops.changed()
                if (agenda && !perms.calendar) ops.request(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// 6 · Chiedimi: la barra di Casa scrive da sola frasi normali, e ognuna diventa la sua azione

private data class Ask(val q: Int, val title: Int, val sub: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
private fun AskMe(app: SordinaApp) {
    val c = S.c
    val asks = listOf(
        Ask(R.string.obt_q1, R.string.obt_r1, R.string.obt_s1, Icons.Rounded.Event),
        Ask(R.string.obt_q2, R.string.obt_r2, R.string.obt_s2, Icons.Rounded.Forum),
        Ask(R.string.obt_q3, R.string.obt_r3, R.string.obt_s3, Icons.Rounded.Timer),
        Ask(R.string.obt_q4, R.string.obt_r4, R.string.obt_s4, Icons.Rounded.Lightbulb),
    )
    // Per ogni frase: si scrive, appare la risposta, resta un attimo, si cancella
    var i by remember { mutableIntStateOf(0) }
    var typed by remember { mutableIntStateOf(0) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            val q = tr(asks[i].q)
            shown = false; typed = 0
            kotlinx.coroutines.delay(400)
            while (typed < q.length) { typed++; kotlinx.coroutines.delay(55) }
            kotlinx.coroutines.delay(350)
            shown = true
            kotlinx.coroutines.delay(2600)
            i = (i + 1) % asks.size
        }
    }
    val a = asks[i]
    Screen("", tr(R.string.obt_title), tr(R.string.ob2_ask_sub)) {
        Column(Modifier.fillMaxSize(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
            // La barra, con il testo che si scrive
            Row(
                Modifier.fillMaxWidth().height(56.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(28.dp)).background(surface()).padding(start = 20.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val q = tr(a.q)
                androidx.compose.material3.Text(if (typed == 0) tr(R.string.home_search) else q.take(typed), Modifier.weight(1f), maxLines = 1,
                    style = androidx.compose.ui.text.TextStyle(fontSize = 17.sp, color = if (typed == 0) c.soft else c.ink))
                androidx.compose.material3.Icon(Icons.Rounded.Mic, null, Modifier.size(22.dp), tint = c.accent)
            }
            // Sotto la barra, come in Casa: la sordina, e quando ha capito scivola a sinistra e risponde nel suo fumetto
            Spacer(Modifier.height(16.dp))
            val talk by androidx.compose.animation.core.animateFloatAsState(if (shown) 1f else 0f, tween(320), label = "talk")
            androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(96.dp)) {
                if (talk > 0f) Row(
                    Modifier.align(Alignment.CenterStart).padding(start = 52.dp).fillMaxWidth()
                        .graphicsLayer { alpha = talk; translationX = (1f - talk) * -24.dp.toPx() }
                        .clip(BubbleShape).background(surface())
                        .padding(start = 7.dp + 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        androidx.compose.material3.Text(tr(a.sub), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = c.soft))
                        androidx.compose.material3.Text(tr(a.title), maxLines = 2, style = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, lineHeight = 23.sp, color = c.ink))
                    }
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.foundation.layout.Box(Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.Icon(a.icon, null, Modifier.size(22.dp), tint = c.bg)
                    }
                }
                androidx.compose.foundation.layout.Box(Modifier.align(androidx.compose.ui.BiasAlignment(-talk, 0f))) {
                    MascotGlyph(Pose.AWAKE, if (talk > 0.5f) c.accent else c.soft, Modifier.size(40.dp))
                }
            }
        }
    }
}

@Composable
private fun MomentCard(icon: androidx.compose.ui.graphics.vector.ImageVector, name: String, note: String, on: Boolean, onToggle: () -> Unit) {
    val c = S.c
    val feel = LocalFeel.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp)).background(surface())
            .clickableNoRipple { feel.tick(); onToggle() }.padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (on) c.accent.copy(alpha = 0.16f) else c.tile),
            contentAlignment = Alignment.Center,
        ) { androidx.compose.material3.Icon(icon, null, Modifier.size(22.dp), tint = if (on) c.accent else c.soft) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            androidx.compose.material3.Text(name, style = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, color = if (on) c.ink else c.soft))
            androidx.compose.material3.Text(note, style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = c.soft))
        }
        androidx.compose.foundation.layout.Box(
            Modifier.size(24.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .let { if (on) it.background(c.accent) else it.border(1.5.dp, c.dot, androidx.compose.foundation.shape.CircleShape) },
            contentAlignment = Alignment.Center,
        ) { if (on) androidx.compose.material3.Icon(Icons.Rounded.Check, null, Modifier.size(15.dp), tint = c.bg) }
    }
}


@Composable
private fun MiniChip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    val c = S.c
    Row(
        modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).background(c.bg).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(icon, null, Modifier.size(16.dp), tint = c.soft)
        Spacer(Modifier.width(10.dp))
        androidx.compose.material3.Text(text, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            style = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = c.ink))
    }
}

// Le ritrovi: in fondo alla Home la sordina con il numero; sopra, quello che aspetta, tutto insieme.

@Composable
private fun FindThem() {
    val c = S.c
    val pulse by androidx.compose.animation.core.rememberInfiniteTransition(label = "find").animateFloat(
        0f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(2400), androidx.compose.animation.core.RepeatMode.Reverse), label = "p",
    )
    Screen("", tr(R.string.ob2_f_title), tr(R.string.ob2_f_sub)) {
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp)).background(surface()).padding(14.dp)) {
                androidx.compose.material3.Text(tr(R.string.ob2_f_today), Modifier.padding(start = 4.dp, bottom = 8.dp),
                    style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = c.soft))
                MiniChip(tr(R.string.ob1_n1), Icons.Rounded.MailOutline, Modifier.padding(bottom = 6.dp))
                MiniChip(tr(R.string.ob1_n2), Icons.Rounded.Groups, Modifier.padding(bottom = 6.dp))
                MiniChip(tr(R.string.ob1_n4), Icons.Rounded.LocalShipping)
            }
            Spacer(Modifier.height(18.dp))
            androidx.compose.material3.Icon(Icons.Rounded.KeyboardArrowUp, null, Modifier.size(22.dp).graphicsLayer { translationY = -pulse * 6.dp.toPx() }, tint = c.soft)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MascotGlyph(Pose.AWAKE, c.accent, Modifier.size(30.dp))
                Spacer(Modifier.width(8.dp))
                androidx.compose.material3.Text("3", style = androidx.compose.ui.text.TextStyle(fontSize = 20.sp, color = c.accent))
            }
        }
    }
}

// Il freno: tocchi un'app che ti distrae, il cerchio si riempie in cinque secondi, "Lascio stare" è il pulsante grande.

@Composable
private fun BrakeDemo() {
    val c = S.c
    val t by androidx.compose.animation.core.rememberInfiniteTransition(label = "brake").animateFloat(
        0f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(7000, easing = androidx.compose.animation.core.LinearEasing)), label = "t",
    )
    val prog = ((t - 0.05f) / 0.75f).coerceIn(0f, 1f)
    val left = 5 - (prog * 5).toInt()
    val breath = (kotlin.math.sin(t * 2 * Math.PI * 3).toFloat() + 1f) / 2f
    Screen("", tr(R.string.ob2_b_title), tr(R.string.ob2_b_sub)) {
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.foundation.layout.Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    val w = 5.dp.toPx()
                    drawCircle(c.line, size.minDimension / 2f - w, style = androidx.compose.ui.graphics.drawscope.Stroke(w))
                    drawArc(c.accent, -90f, 360f * prog, false,
                        androidx.compose.ui.geometry.Offset(w, w), androidx.compose.ui.geometry.Size(size.width - 2 * w, size.height - 2 * w),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                }
                androidx.compose.foundation.layout.Box(Modifier.size(90.dp).graphicsLayer { scaleX = 0.86f + 0.14f * breath; scaleY = scaleX }
                    .clip(androidx.compose.foundation.shape.CircleShape).background(c.accent.copy(alpha = 0.16f)))
                androidx.compose.material3.Text(if (left > 0) "$left" else "✓", style = androidx.compose.ui.text.TextStyle(fontSize = 32.sp, color = c.ink))
            }
            Spacer(Modifier.height(24.dp))
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxWidth(0.8f).height(44.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp)).background(c.accent),
                contentAlignment = Alignment.Center,
            ) { androidx.compose.material3.Text(tr(R.string.leave_it), style = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = c.bg)) }
            androidx.compose.material3.Text(if (left > 0) tr(R.string.open_in, left) else tr(R.string.open_anyway), Modifier.padding(top = 10.dp),
                style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = c.soft))
        }
    }
}

/**
 * Un permesso: perché serve, e passo per passo cosa succede in Android. Quando torni e c'è, al posto dei passi
 * la spunta grande.
 */
@Composable
private fun PermStep(title: Int, why: Int, done: Boolean, vararg how: Int, note: Int? = null) {
    val c = S.c
    Screen("", tr(title), tr(why)) {
        AnimatedContent(done, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) }, label = "perm") { ok ->
            if (ok) Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                androidx.compose.foundation.layout.Box(Modifier.size(72.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(Icons.Rounded.Check, null, Modifier.size(38.dp), tint = c.bg)
                }
                Spacer(Modifier.height(14.dp))
                androidx.compose.material3.Text(tr(R.string.ob2_perm_ok), style = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, color = c.ink))
            } else Column(Modifier.fillMaxSize(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                androidx.compose.material3.Text(tr(R.string.ob2_perm_how), Modifier.padding(bottom = 14.dp),
                    style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = c.soft))
                how.forEachIndexed { n, res ->
                    Row(Modifier.padding(bottom = 14.dp), verticalAlignment = Alignment.Top) {
                        androidx.compose.foundation.layout.Box(Modifier.size(28.dp).clip(androidx.compose.foundation.shape.CircleShape).background(surface()), contentAlignment = Alignment.Center) {
                            androidx.compose.material3.Text("${n + 1}", style = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = c.accent))
                        }
                        Spacer(Modifier.width(14.dp))
                        androidx.compose.material3.Text(tr(res), Modifier.padding(top = 4.dp), style = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, lineHeight = 22.sp, color = c.ink))
                    }
                }
                if (note != null) androidx.compose.material3.Text(tr(note), Modifier.padding(top = 6.dp),
                    style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 19.sp, color = c.soft))
            }
        }
    }
}

// Gli Importanti: passano già. Codici e sicurezza sempre; il resto si spegne con un tocco.

@Composable
private fun Importants(app: SordinaApp, ops: Ops) {
    val prefs = app.prefs
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var missed by remember { mutableStateOf(prefs.missedPass) }
    var remind by remember { mutableStateOf(prefs.remindPass) }
    var urgent by remember { mutableStateOf(prefs.urgentEnabled) }
    fun apply() { ops.changed(); SordinaListener.reapply(ctx) }
    Screen("", tr(R.string.ob2_imp_title), tr(R.string.ob2_imp_sub)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            MomentCard(Icons.Rounded.Key, tr(R.string.notif_security), tr(R.string.ob2_imp_security), true) {}
            MomentCard(Icons.Rounded.NotificationsActive, tr(R.string.notif_missed), tr(R.string.ob2_imp_missed), missed) {
                missed = !missed; prefs.missedPass = missed; apply()
            }
            MomentCard(Icons.Rounded.Event, tr(R.string.notif_remind), tr(R.string.ob2_imp_remind), remind) {
                remind = !remind; prefs.remindPass = remind; apply()
            }
            MomentCard(Icons.Rounded.Forum, tr(R.string.s_urgent), tr(R.string.ob2_imp_urgent), urgent) {
                urgent = !urgent; prefs.urgentEnabled = urgent; apply()
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** La fine: cosa hai scelto, in parole semplici, e cosa manca ancora (si sistema da qui). */
@Composable
private fun AllSet(app: SordinaApp, apps: List<AppEntry>, perms: PermState, ops: Ops) {
    val c = S.c
    val prefs = app.prefs
    val ctx = androidx.compose.ui.platform.LocalContext.current
    fun appName(pkg: String) = apps.firstOrNull { it.packageName == pkg }?.label
    fun names(list: List<String>) = when {
        list.size <= 3 -> list.joinToString(", ")
        else -> list.take(3).joinToString(", ") + " " + trn(R.plurals.ob2_and_more, list.size - 3)
    }
    val pass = remember {
        prefs.appLevels.filterValues { it == Level.SEMPRE }.keys.mapNotNull(::appName).sorted() +
            prefs.importantNames.sorted().map { n -> n.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } } }
    }
    val braked = remember { prefs.braked.mapNotNull(::appName).sorted() }
    val home = listOfNotNull(
        tr(R.string.s_weather).takeIf { prefs.homeWeather && perms.location },
        tr(R.string.n_agenda).takeIf { prefs.homeAgenda && perms.calendar },
    )
    Column(Modifier.fillMaxSize().padding(top = 18.dp).verticalScroll(rememberScrollState())) {
        Mascot(Mood.DONE, Modifier.size(76.dp))
        Spacer(Modifier.height(10.dp))
        androidx.compose.material3.Text(tr(R.string.ob_done_title), style = androidx.compose.ui.text.TextStyle(fontSize = 32.sp, lineHeight = 38.sp, color = c.ink))
        Spacer(Modifier.height(10.dp))
        androidx.compose.material3.Text(tr(R.string.ob2_done_sub), style = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, lineHeight = 21.sp, color = c.soft))
        Spacer(Modifier.height(16.dp))
        Summary(tr(R.string.ob2_sum_pass), if (pass.isEmpty()) tr(R.string.ob2_sum_pass_none) else tr(R.string.ob2_sum_pass_some, names(pass)))
        Summary(tr(R.string.ob2_sum_brake), if (braked.isEmpty()) tr(R.string.ob2_sum_none) else names(braked))
        Summary(tr(R.string.ob2_sum_home), if (home.isEmpty()) tr(R.string.ob2_sum_home_none) else home.joinToString(", "))
        // Quello che hai rimandato: si può fare ancora da qui
        val missing = !perms.listener || !perms.home || !perms.battery || (Build.VERSION.SDK_INT >= 33 && !perms.post)
        if (missing) {
            Spacer(Modifier.height(10.dp))
            androidx.compose.material3.Text(tr(R.string.ob2_sum_missing), Modifier.padding(start = 4.dp, bottom = 2.dp),
                style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = c.soft))
            if (!perms.listener) Need(false, tr(R.string.ob2_pn_title), tr(R.string.ob2_need_notif)) { ops.openListenerSettings() }
            if (!perms.home) Need(false, tr(R.string.ob_home_l), tr(R.string.ob2_need_home)) { ops.openHomeSettings() }
            if (!perms.battery) Need(false, tr(R.string.ob_battery_l), tr(R.string.ob2_need_battery)) { ops.openBatterySettings() }
            if (Build.VERSION.SDK_INT >= 33 && !perms.post) Need(false, tr(R.string.onb_alerts), tr(R.string.ob2_need_post)) { ops.request(Manifest.permission.POST_NOTIFICATIONS) }
        }
        androidx.compose.material3.Text(tr(R.string.ob_backup), Modifier.padding(top = 14.dp, start = 4.dp).clickableNoRipple { Backup.openSettings(ctx) },
            style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 19.sp, color = c.soft, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline))
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Summary(title: String, text: String) {
    val c = S.c
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp)).background(surface()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        androidx.compose.material3.Text(title, style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = c.soft))
        Spacer(Modifier.height(2.dp))
        androidx.compose.material3.Text(text, style = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, lineHeight = 22.sp, color = c.ink))
    }
}

@Composable
fun Done(ok: Boolean, label: String) {
    val c = S.c
    Row(Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Serif(if (ok) "✓" else "○", size = 16.sp, color = if (ok) c.accent else c.soft)
        Spacer(Modifier.width(10.dp))
        Serif(label, size = 15.sp, color = if (ok) c.ink else c.soft)
    }
}

/** Scelta delle persone importanti: suggerite dai preferiti, più ricerca. */
@Composable
fun ImportantPicker(app: SordinaApp, perms: PermState, ops: Ops) {
    val c = S.c
    if (!perms.contacts) {
        Secondary(tr(R.string.onb_allow_contacts), Modifier.fillMaxWidth()) {
            ops.request(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
        }
        return
    }
    var tick by remember { mutableIntStateOf(0) }
    var q by remember { mutableStateOf("") }
    var all by remember { mutableStateOf<List<Contact>>(emptyList()) }
    LaunchedEffect(perms.contacts) { all = withContext(Dispatchers.IO) { app.people.invalidate(); app.people.all() } }
    key(tick) {
        val names = app.prefs.importantNames
        val chosen = names.sorted()
        if (chosen.isNotEmpty()) {
            Group {
                chosen.forEach { n ->
                    val display = all.firstOrNull { it.normalized == n }?.name ?: n.replaceFirstChar { it.uppercase() }
                    Line(display, trailing = { SmallPill(tr(R.string.remove)) { app.people.removeImportantName(n); tick++ } })
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        Field(q, { q = it }, tr(R.string.search_contact), Modifier.padding(horizontal = 4.dp))
        val list = if (q.isBlank()) all.filter { it.starred && it.normalized !in names }.take(8)
        else app.people.search(q, 6).filter { it.normalized !in names }
        if (q.isBlank() && list.isNotEmpty()) Hint(tr(R.string.from_favorites), Modifier.padding(start = 4.dp, top = 10.dp))
        if (list.isNotEmpty()) Group {
            list.forEach { ct -> Line(ct.name, hint = ct.number, trailing = { SmallPill(tr(R.string.add_l).replaceFirstChar { it.uppercase() }) { app.people.setImportant(ct, true); q = ""; tick++ } }) }
        }
        if (q.isBlank() && list.isEmpty() && chosen.isEmpty()) Hint(tr(R.string.onb_search_people), Modifier.padding(start = 4.dp))
    }
}

@Composable
fun CalendarPicker(app: SordinaApp, ops: Ops) {
    var cals by remember { mutableStateOf(emptyList<app.sordina.data.Cal>()) }
    var sel by remember { mutableStateOf(app.prefs.calendarId) }
    LaunchedEffect(Unit) { cals = withContext(Dispatchers.IO) { app.calendars.list() } }
    if (cals.isEmpty()) { Hint(tr(R.string.cal_none_writable)); return }
    val current = cals.firstOrNull { it.id == sel } ?: cals.first()
    Hint(tr(R.string.cal_events_go))
    FlowPills(cals.map { it.name + if (cals.count { o -> o.name == it.name } > 1) " (${it.account})" else "" }, { cals[it].id == current.id }) {
        sel = cals[it].id; app.prefs.calendarId = sel; ops.changed()
    }
}



/**
 * Lista completa con scelta multipla, come "Tutte le app": lettere in accento, indice a destra,
 * filtro in basso. Tocca una riga per aggiungerla o toglierla.
 */
@Composable
internal fun <T> PickList(
    items: List<T>, key: (T) -> String, label: (T) -> String, q: String,
    leading: @Composable (T) -> Unit, selected: Set<String>, onScrolled: (Boolean) -> Unit, onToggle: (T) -> Unit,
) {
    val c = S.c
    val feel = LocalFeel.current
    var scrubbing by remember { mutableStateOf<String?>(null) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val nq = Search.normalize(q)
    val shown = remember(items, nq) { if (nq.isEmpty()) items else items.filter { Search.normalize(label(it)).contains(nq) } }
    // Righe: in cima le scelte di quando si è aperta la pagina, poi le altre per lettera.
    // Toccando una riga si riempie solo il cerchio: niente si sposta sotto il dito
    val opened = remember { selected }
    val rows = remember(shown) {
        val out = ArrayList<Any>()
        val (mine, rest) = shown.partition { key(it) in opened }
        if (mine.isNotEmpty()) { out += CHOSEN; mine.forEach { out += Pair(it, 0) } }
        var last = ""
        for (it in rest) { val l = app.sordina.ui.apps.letterOf(label(it)); if (l != last) { out += l; last = l }; out += Pair(it, 0) }
        out
    }
    val letters = remember(rows) { rows.filterIsInstance<String>().filter { it != CHOSEN } }
    LaunchedEffect(nq) { listState.scrollToItem(0) }
    // Appena la lista scorre, il titolo sopra si fa piccolo
    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 24 }.collect { onScrolled(it) }
    }
    Column(Modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Box(Modifier.weight(1f).fillMaxWidth()) {
            androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize().padding(end = 28.dp), state = listState) {
                items(rows.size, key = { i -> rows[i].let { r -> if (r is String) "h_$r" else key((r as Pair<*, *>).first as T) } }) { i ->
                    val r = rows[i]
                    if (r is String) androidx.compose.material3.Text(if (r == CHOSEN) tr(R.string.ob_chosen) else r, Modifier.padding(top = 14.dp, bottom = 2.dp),
                        style = androidx.compose.ui.text.TextStyle(fontSize = 26.sp, color = c.accent))
                    else {
                        @Suppress("UNCHECKED_CAST") val it = (r as Pair<*, *>).first as T
                        val on = key(it) in selected
                        Row(
                            Modifier.fillMaxWidth().clickableNoRipple { feel.tick(); onToggle(it) }.padding(vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            leading(it)
                            Spacer(Modifier.width(16.dp))
                            androidx.compose.material3.Text(label(it), Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                style = androidx.compose.ui.text.TextStyle(fontSize = 20.sp, color = c.ink))
                            androidx.compose.foundation.layout.Box(
                                Modifier.size(24.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                    .let { m -> if (on) m.background(c.accent) else m.border(1.5.dp, c.dot, androidx.compose.foundation.shape.CircleShape) },
                                contentAlignment = Alignment.Center,
                            ) { if (on) androidx.compose.material3.Icon(Icons.Rounded.Check, null, Modifier.size(15.dp), tint = c.bg) }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
            if (nq.isEmpty() && letters.size > 3) {
                app.sordina.ui.apps.LetterIndex(letters, scrubbing, Modifier.align(Alignment.CenterEnd)) { l ->
                    scrubbing = l
                    if (l != null) { val idx = rows.indexOf(l); if (idx >= 0) scope.launch { listState.scrollToItem(idx) } }
                }
            }
            // La lettera grande mentre scorri l'indice
            scrubbing?.let { l ->
                androidx.compose.foundation.layout.Box(
                    Modifier.align(Alignment.Center).size(96.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(28.dp)).background(c.card),
                    contentAlignment = Alignment.Center,
                ) { androidx.compose.material3.Text(l, style = androidx.compose.ui.text.TextStyle(fontSize = 48.sp, color = c.ink)) }
            }
        }
    }
}

@Composable
internal fun Initial(name: String) {
    val c = S.c
    androidx.compose.foundation.layout.Box(Modifier.size(30.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.tile), contentAlignment = Alignment.Center) {
        androidx.compose.material3.Text(name.trim().take(1).uppercase(), style = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = c.ink))
    }
}

@Composable
internal fun AppDot(pkg: String) {
    val icon = appIcon(pkg, 96)
    if (icon != null) androidx.compose.foundation.Image(icon, null, Modifier.size(30.dp))
    else androidx.compose.foundation.layout.Box(Modifier.size(30.dp).clip(androidx.compose.foundation.shape.CircleShape).background(S.c.tile))
}

/** Segnaposto della sezione "Scelti" in cima alla lista (non è una lettera). */
private const val CHOSEN = "\u0000chosen"


