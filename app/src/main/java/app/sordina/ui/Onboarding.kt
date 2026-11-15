package app.sordina.ui

import app.sordina.data.Backup
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
import androidx.compose.ui.text.drawText
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/**
 * Primo avvio. Ogni schermata ha al centro una cosa che si vede (le notifiche che aspettano, i volti,
 * le icone con l'attesa, il quadrante del giorno, Casa in piccolo) e sotto una frase che dice cosa succede.
 * Ogni passo ha già una scelta sensata: si può andare avanti senza toccare nulla.
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
    onDone: () -> Unit,
) {
    var step by rememberSaveable(startStep) { mutableIntStateOf(startStep) }
    // Una copia ritrovata dal backup di Google (telefono nuovo o app reinstallata): si può rimettere tutto com'era
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var found by remember { mutableStateOf(Backup.found(ctx)) }
    val last = 6
    val c = S.c
    fun next() { if (step < last) step++ else onDone() }
    androidx.activity.compose.BackHandler(enabled = step > 0) { step-- }
    // L'ultima schermata: finita l'animazione, "Tutto pronto" e il pulsante grande al centro
    var finished by remember { mutableStateOf(false) }
    LaunchedEffect(step) { if (step != last) finished = false }
    val (label, action) = when {
        step == 0 && found != null -> tr(R.string.ob_restore) to { if (Backup.restore(ctx)) Backup.restart(ctx) }
        step == 0 -> tr(R.string.ob_begin) to { next() }
        step == 1 && !perms.listener -> tr(R.string.ob_allow) to { ops.openListenerSettings() }
        step == 1 && !perms.usage -> tr(R.string.ob_allow) to { ops.openUsageSettings() }
        step == 1 && !perms.battery -> tr(R.string.ob_allow) to { ops.openBatterySettings() }
        step == 1 && !perms.contacts -> tr(R.string.ob_allow) to { ops.request(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS) }
        step == 1 && !perms.calendar -> tr(R.string.ob_allow) to { ops.request(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR) }
        step == 1 && !perms.location -> tr(R.string.ob_allow) to { ops.request(Manifest.permission.ACCESS_COARSE_LOCATION) }
        step == 1 && Build.VERSION.SDK_INT >= 33 && !perms.post -> tr(R.string.ob_allow) to { ops.request(Manifest.permission.POST_NOTIFICATIONS) }
        step == last && !perms.home -> tr(R.string.ob_set_home) to { ops.openHomeSettings() }
        step == last -> tr(R.string.onb_go_home) to { onDone() }
        else -> tr(R.string.next) to { next() }
    }
    val ending = step == last && finished
    Column(Modifier.fillMaxSize().background(c.bg).systemBarsPadding().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 16.dp)) {
        // In testa l'onda di Sordina: a ogni passo si calma un po', all'ultimo è quasi piatta
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettleWave(step, last, Modifier.weight(1f).height(26.dp))
            Spacer(Modifier.width(16.dp))
            Dots(step, last)
        }
        AnimatedContent(step, transitionSpec = { Motion.forward(targetState > initialState) }, label = "step", modifier = Modifier.weight(1f)) { s ->
            when (s) {
                0 -> Waiting(app)
                1 -> Needs(perms, ops)
                2 -> Faces(app, perms, ops)
                3 -> AlwaysApps(app, apps, ops)
                4 -> Distracting(app, apps, usage, ops)
                5 -> Moments(app, ops)
                else -> AnimatedContent(finished, transitionSpec = { fadeIn(tween(500, delayMillis = 150)) togetherWith fadeOut(tween(300)) }, label = "end") { f ->
                    if (f) AllSet(perms, label, { action() }, onLater = onDone) else MiniHome(app, perms) { finished = true }
                }
            }
        }
        // Alla fine il pulsante lascia il fondo: ricompare grande, al centro
        androidx.compose.animation.AnimatedVisibility(!ending, enter = fadeIn(), exit = fadeOut(tween(250))) {
            Column {
                Spacer(Modifier.height(14.dp))
                found?.takeIf { step == 0 }?.let { d ->
                    androidx.compose.material3.Text(tr(R.string.ob_restore_found, d.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.LONG))),
                        Modifier.fillMaxWidth().padding(bottom = 14.dp), style = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, lineHeight = 21.sp, color = c.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
                }
                Pill(label) { action() }
                // "Più tardi" solo dove serve: altrove il pulsante sta in fondo e il contenuto ha più posto
                // Ai permessi sempre, finché ne manca uno: un "no" ad Android non deve bloccare l'avvio
                val later = (step == 1 && label == tr(R.string.ob_allow)) || (step == last && !perms.home) || (step == 0 && found != null)
                if (later) androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxWidth().clickableNoRipple { if (step == 0 && found != null) { Backup.forget(ctx); found = null } else if (step == last) onDone() else next() }.padding(top = 14.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center,
                ) { Serif(tr(if (step == 0 && found != null) R.string.ob_restore_no else R.string.later), size = 14.sp, color = c.soft) }
            }
        }
    }
}

/** Il pulsante dell'avvio: pieno, alto come la barra di Casa. */
@Composable
private fun Pill(text: String, onClick: () -> Unit) {
    val c = S.c
    val feel = LocalFeel.current
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxWidth().height(52.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(26.dp)).background(c.ink)
            .clickable { feel.confirm(); onClick() },
        contentAlignment = Alignment.Center,
    ) { androidx.compose.material3.Text(text, style = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, color = c.bg)) }
}

@Composable
private fun Dots(step: Int, last: Int) {
    val c = S.c
    Row {
        repeat(last + 1) { i ->
            androidx.compose.foundation.layout.Box(Modifier.padding(horizontal = 3.dp).size(5.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (i == step) c.ink else c.dot))
        }
    }
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
        androidx.compose.material3.Text(label.uppercase(), style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, letterSpacing = 1.2.sp, color = c.accent))
        Spacer(Modifier.height(6.dp))
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

// 1 · Le notifiche cadono in fila, aspettano, e all'ora del riepilogo arrivano insieme

@Composable
private fun Waiting(app: SordinaApp) {
    val c = S.c
    // Esempi inventati: mai le notifiche vere di chi installa
    val rows = listOf(
        Icons.Rounded.MailOutline to tr(R.string.ob1_n1), Icons.Rounded.Groups to tr(R.string.ob1_n2),
        Icons.Rounded.FavoriteBorder to tr(R.string.ob1_n3), Icons.Rounded.LocalShipping to tr(R.string.ob1_n4),
    )
    val t by androidx.compose.animation.core.rememberInfiniteTransition(label = "fall").animateFloat(
        0f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(6500, easing = androidx.compose.animation.core.LinearEasing)), label = "t",
    )
    Screen(tr(R.string.ob_l1), tr(R.string.ob1_title), tr(R.string.ob1_sub)) {
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
            androidx.compose.material3.Text(tr(R.string.ob1_at), Modifier.graphicsLayer { alpha = clock },
                style = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = c.accent))
        }
    }
}

// 2 · Il segno di Sordina e quello che le serve

@Composable
private fun Needs(perms: PermState, ops: Ops) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Screen(tr(R.string.ob_l2), tr(R.string.ob2_title), tr(R.string.ob2_sub), mascot = Mood.HELLO) {
        // Prima quelli senza cui Sordina non lavora, poi quelli che riempiono la Home
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Need(perms.listener, tr(R.string.ob_perm_notif_l), tr(R.string.ob_perm_notif)) { ops.openListenerSettings() }
            Need(perms.usage, tr(R.string.ob_perm_usage_l), tr(R.string.ob_perm_usage)) { ops.openUsageSettings() }
            Need(perms.battery, tr(R.string.s_battery), tr(R.string.ob_perm_battery)) { ops.openBatterySettings() }
            Need(perms.contacts, tr(R.string.perm_contacts), tr(R.string.ob_perm_contacts)) { ops.request(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS) }
            Need(perms.calendar, tr(R.string.s_calendar), tr(R.string.h_p_cal)) { ops.request(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR) }
            Need(perms.location, tr(R.string.s_weather), tr(R.string.h_weather)) { ops.request(Manifest.permission.ACCESS_COARSE_LOCATION) }
            if (Build.VERSION.SDK_INT >= 33) Need(perms.post, tr(R.string.onb_alerts), tr(R.string.ob_perm_post)) { ops.request(Manifest.permission.POST_NOTIFICATIONS) }
            // Facoltativa, in fondo: freno istantaneo e doppio tocco per spegnere
            Need(perms.access, tr(R.string.perm_access), tr(R.string.ob_perm_access)) { app.sordina.service.SordinaAccess.openSettings(ctx) }
        }
    }
}

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
        if (ct.normalized in chosen) app.prefs.importantNames = app.prefs.importantNames - ct.normalized
        else app.people.setImportant(ct, true)
        chosen = app.prefs.importantNames
        ops.changed()
    }
    var q by remember { mutableStateOf("") }
    var small by remember { mutableStateOf(false) }
    Screen(tr(R.string.ob_l3), tr(R.string.ob3_title), tr(R.string.ob_people_sub), collapsed = small,
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
    val installed = remember(apps) { apps.filter { !it.isWork }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() } }
    var chosen by remember { mutableStateOf(app.prefs.appLevels.filterValues { it == Level.SEMPRE }.keys) }
    fun toggle(pkg: String) {
        app.prefs.setAppLevel(pkg, if (pkg in chosen) null else Level.SEMPRE)
        chosen = app.prefs.appLevels.filterValues { it == Level.SEMPRE }.keys
        ops.changed()
    }
    var q by remember { mutableStateOf("") }
    var small by remember { mutableStateOf(false) }
    Screen(tr(R.string.ob_lapps), tr(R.string.ob_apps_title), tr(R.string.ob_apps_sub), collapsed = small,
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
private fun Distracting(app: SordinaApp, apps: List<AppEntry>, usage: Map<String, Double>, ops: Ops) {
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
    LaunchedEffect(candidates) {
        if (!app.prefs.brakeSuggested && candidates.isNotEmpty()) {
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
    Screen(tr(R.string.ob_l4), tr(R.string.ob4_title), tr(R.string.ob4_sub), collapsed = small,
        query = q, onSearch = { q = it ?: "" }, searchHint = tr(R.string.s_add_app)) {
        PickList(installed.sortedBy { it.label.lowercase() }, { it.packageName }, { it.label }, q,
            leading = { a -> AppDot(a.packageName) }, selected = braked, onScrolled = { small = it }) { toggle(it.packageName) }
    }
}

// 6 · Sera, notte, auto: la Home cambia da sola. Già accesi: si spegne quello che non serve.

@Composable
private fun Moments(app: SordinaApp, ops: Ops) {
    val prefs = app.prefs
    var evening by remember { mutableStateOf(prefs.homeEvening) }
    var night by remember { mutableStateOf(prefs.homeNight) }
    var car by remember { mutableStateOf(prefs.homeCar) }
    Screen(tr(R.string.ob_l5), tr(R.string.ob5_title), tr(R.string.ob5_sub)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            MomentCard(Icons.Rounded.WbTwilight, tr(R.string.s_scene_evening), tr(R.string.n_from, hm(prefs.eveningFrom)), evening) {
                evening = !evening; prefs.homeEvening = evening; ops.changed()
            }
            MomentCard(Icons.Rounded.Bedtime, tr(R.string.s_scene_night), tr(R.string.n_from, hm(prefs.nightFrom)), night) {
                night = !night; prefs.homeNight = night; ops.changed()
            }
            MomentCard(Icons.Rounded.DirectionsCar, tr(R.string.s_scene_car), tr(R.string.ob5_car), car) {
                car = !car; prefs.homeCar = car; ops.changed()
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

// 7 · Casa in piccolo: su le app, un'app che distrae chiede un respiro, giù la tendina, il resto aspetta in basso.

private fun seg(u: Float, a: Float, b: Float) = ((u - a) / (b - a)).coerceIn(0f, 1f)
private fun lerp(a: Float, b: Float, p: Float) = a + (b - a) * p

@Composable
private fun MiniHome(app: SordinaApp, perms: PermState, onFinished: () -> Unit) {
    val c = S.c
    val now = remember { java.time.LocalDate.now() }
    // Un giro solo: alla fine lascia il posto a "Tutto pronto"
    val anim = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { anim.animateTo(1f, tween(22000, easing = androidx.compose.animation.core.LinearEasing)); onFinished() }
    val t = anim.value.coerceAtMost(0.9999f)
    val scenes = 4
    val scene = (t * scenes).toInt().coerceAtMost(scenes - 1)
    val u = t * scenes - scene
    val captions = listOf(R.string.ob6_c1, R.string.ob6_c4, R.string.ob6_c2, R.string.ob6_c3)
    Screen(tr(R.string.ob_l6), tr(R.string.ob6_title), if (perms.home) tr(R.string.onb_home_done) else tr(R.string.ob6_sub)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.foundation.layout.BoxWithConstraints(
                Modifier.weight(1f).aspectRatio(0.5f, matchHeightConstraintsFirst = true)
                    .border(1.5.dp, c.line, androidx.compose.foundation.shape.RoundedCornerShape(30.dp))
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(30.dp)).background(c.bg),
            ) {
                val W = maxWidth; val H = maxHeight
                val counterY = H - 28.dp

                // ── La Casa ──
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 22.dp)) {
                    val loc = java.util.Locale.getDefault()
                    androidx.compose.material3.Text(now.format(java.time.format.DateTimeFormatter.ofPattern("EEEE", loc)).replaceFirstChar { it.uppercase() },
                        style = androidx.compose.ui.text.TextStyle(fontSize = 21.sp, color = c.ink))
                    androidx.compose.material3.Text(now.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM", loc)),
                        style = androidx.compose.ui.text.TextStyle(fontSize = 21.sp, color = c.soft))
                }
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 50.dp).fillMaxWidth().height(34.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(17.dp)).background(surface()).padding(start = 12.dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.Text(tr(R.string.home_search), Modifier.weight(1f), maxLines = 1, style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = c.soft))
                    androidx.compose.material3.Icon(Icons.Rounded.Mic, null, Modifier.size(14.dp), tint = c.accent)
                }
                // In basso quante aspettano: nell'ultima scena ne arriva una
                val fall = if (scene == 3) seg(u, 0.2f, 0.55f) else 0f
                val n = if (scene == 3 && fall >= 1f) 3 else 2
                val bump = if (scene == 3) seg(u, 0.55f, 0.6f) * (1f - seg(u, 0.6f, 0.75f)) else 0f
                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp).height(20.dp).graphicsLayer { scaleX = 1f + 0.12f * bump; scaleY = scaleX },
                    verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(Icons.Rounded.NotificationsOff, null, Modifier.size(11.dp), tint = if (bump > 0f) c.accent else c.soft)
                    Spacer(Modifier.width(4.dp))
                    androidx.compose.material3.Text(trn(R.plurals.n_waiting, n), style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = if (bump > 0f) c.accent else c.soft))
                }

                // 1 · In su: le app. Restano su: la seconda scena parte da qui e il respiro le copre
                val apps = when (scene) {
                    0 -> seg(u, 0.2f, 0.42f)
                    1 -> if (u < 0.3f) 1f else 0f
                    else -> 0f
                }
                if (apps > 0f) Column(
                    Modifier.fillMaxSize().graphicsLayer { translationY = (1f - apps) * H.toPx() }.background(c.bg).padding(horizontal = 16.dp, vertical = 22.dp),
                ) {
                    Row(Modifier.fillMaxWidth().height(34.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(17.dp)).background(surface()).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Icon(Icons.Rounded.Search, null, Modifier.size(14.dp), tint = c.accent)
                    }
                    Spacer(Modifier.height(14.dp))
                    listOf(R.string.ob6_a5 to Icons.Rounded.PlayArrow, R.string.ob6_a1 to Icons.Rounded.Map, R.string.ob6_a2 to Icons.Rounded.PhotoCamera, R.string.ob6_a3 to Icons.Rounded.MusicNote)
                        .forEach { (res, icon) ->
                            Row(Modifier.height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.foundation.layout.Box(Modifier.size(24.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.tile), contentAlignment = Alignment.Center) {
                                    androidx.compose.material3.Icon(icon, null, Modifier.size(13.dp), tint = c.ink)
                                }
                                Spacer(Modifier.width(10.dp))
                                androidx.compose.material3.Text(tr(res), style = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = c.ink))
                            }
                        }
                }
                // 2 · L'app che distrae: prima un respiro, e "Lascia stare" è il pulsante grande
                if (scene == 1) {
                    val b = seg(u, 0.24f, 0.3f) * (1f - seg(u, 0.84f, 0.9f))
                    if (b > 0f) {
                        val prog = seg(u, 0.3f, 0.8f)
                        val left = 5 - (prog * 5).toInt()
                        val breath = (kotlin.math.sin(u * 2 * Math.PI * 2.5).toFloat() + 1f) / 2f
                        val press = seg(u, 0.76f, 0.79f) * (1f - seg(u, 0.81f, 0.84f))
                        Column(
                            Modifier.fillMaxSize().graphicsLayer { alpha = b }.background(c.bg).padding(horizontal = 16.dp, vertical = 22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Spacer(Modifier.height(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.foundation.layout.Box(Modifier.size(18.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.tile), contentAlignment = Alignment.Center) {
                                    androidx.compose.material3.Icon(Icons.Rounded.PlayArrow, null, Modifier.size(11.dp), tint = c.ink)
                                }
                                Spacer(Modifier.width(6.dp))
                                androidx.compose.material3.Text(tr(R.string.ob6_a5), style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = c.ink))
                            }
                            Spacer(Modifier.weight(1f))
                            androidx.compose.foundation.layout.Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                                    val w = 4.dp.toPx()
                                    drawCircle(c.line, size.minDimension / 2f - w, style = androidx.compose.ui.graphics.drawscope.Stroke(w))
                                    drawArc(c.accent, -90f, 360f * prog, false,
                                        androidx.compose.ui.geometry.Offset(w, w), androidx.compose.ui.geometry.Size(size.width - 2 * w, size.height - 2 * w),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                                }
                                androidx.compose.foundation.layout.Box(Modifier.size(72.dp).graphicsLayer { scaleX = 0.86f + 0.14f * breath; scaleY = scaleX }
                                    .clip(androidx.compose.foundation.shape.CircleShape).background(c.accent.copy(alpha = 0.16f)))
                                androidx.compose.material3.Text(if (left > 0) "$left" else "✓", style = androidx.compose.ui.text.TextStyle(fontSize = 26.sp, color = c.ink))
                            }
                            Spacer(Modifier.weight(1f))
                            androidx.compose.foundation.layout.Box(
                                Modifier.fillMaxWidth().height(34.dp).graphicsLayer { scaleX = 1f - 0.05f * press; scaleY = scaleX }
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(17.dp)).background(c.accent),
                                contentAlignment = Alignment.Center,
                            ) { androidx.compose.material3.Text(tr(R.string.leave_it), style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = c.bg)) }
                            androidx.compose.material3.Text(if (left > 0) tr(R.string.open_in, left) else tr(R.string.open_anyway), Modifier.padding(top = 8.dp),
                                style = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, color = c.dot))
                        }
                    }
                }
                // 3 · In giù: la tendina di Android, con quello che è passato
                if (scene == 2) {
                    val p = seg(u, 0.2f, 0.42f) * (1f - seg(u, 0.86f, 1f))
                    if (p > 0f) Column(
                        Modifier.fillMaxWidth().height(H * 0.5f).graphicsLayer { translationY = -(1f - p) * (H * 0.5f).toPx() }
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp)).background(c.card).padding(14.dp),
                    ) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                            repeat(4) { androidx.compose.foundation.layout.Box(Modifier.size(26.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.tile)) }
                        }
                        Spacer(Modifier.height(14.dp))
                        MiniChip(tr(R.string.ob1_n4), Icons.Rounded.LocalShipping, Modifier.padding(bottom = 6.dp))
                    }
                }
                // 4 · Una notifica arriva e scende in basso, ad aspettare
                if (scene == 3 && fall > 0f && fall < 1f) MiniChip(tr(R.string.ob1_n1), Icons.Rounded.MailOutline,
                    Modifier.align(Alignment.TopCenter).padding(horizontal = 12.dp).graphicsLayer {
                        translationY = lerp(H.value * 0.2f, counterY.value - 14f, fall).dp.toPx()
                        alpha = if (fall < 0.15f) fall / 0.15f else 1f - seg(fall, 0.7f, 1f)
                        scaleX = 1f - 0.4f * seg(fall, 0.5f, 1f); scaleY = scaleX
                    })

                // ── Il dito ──
                val cx = W / 2
                val f = when (scene) {
                    0 -> { val m = seg(u, 0.15f, 0.42f); Finger(cx, H * lerp(0.7f, 0.3f, m), seg(u, 0.04f, 0.14f) * (1f - seg(u, 0.44f, 0.52f)), 1f) }
                    // Tocca l'app, poi "Lascia stare"
                    1 -> if (u < 0.5f) Finger(70.dp, 88.dp, seg(u, 0.04f, 0.12f) * (1f - seg(u, 0.24f, 0.3f)), 1f - 0.2f * seg(u, 0.16f, 0.2f))
                        else Finger(cx, H - 63.dp - 12.dp, seg(u, 0.66f, 0.72f) * (1f - seg(u, 0.84f, 0.9f)), 1f - 0.2f * seg(u, 0.76f, 0.79f))
                    2 -> { val m = seg(u, 0.15f, 0.42f); Finger(cx, H * lerp(0.06f, 0.46f, m), seg(u, 0.04f, 0.14f) * (1f - seg(u, 0.44f, 0.52f)), 1f) }
                    else -> null
                }
                if (f != null && f.alpha > 0f) androidx.compose.foundation.layout.Box(
                    Modifier.offset(f.x - 15.dp, f.y - 15.dp).size(30.dp).graphicsLayer { alpha = f.alpha; scaleX = f.scale; scaleY = f.scale }
                        .clip(androidx.compose.foundation.shape.CircleShape).background(c.accent.copy(alpha = 0.25f))
                        .border(2.dp, c.accent, androidx.compose.foundation.shape.CircleShape),
                )
            }
            Spacer(Modifier.height(14.dp))
            // Sempre due righe: il telefono non cambia misura da una scena all'altra
            AnimatedContent(scene, Modifier.fillMaxWidth(), transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(250)) }, label = "cap") { s ->
                androidx.compose.material3.Text(tr(captions[s]), Modifier.fillMaxWidth(), minLines = 2, maxLines = 2,
                    style = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, lineHeight = 22.sp, color = c.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
            }
        }
    }
}

private data class Finger(val x: androidx.compose.ui.unit.Dp, val y: androidx.compose.ui.unit.Dp, val alpha: Float, val scale: Float)

@Composable
private fun MiniChip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    val c = S.c
    Row(
        modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).background(c.card).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(icon, null, Modifier.size(13.dp), tint = c.soft)
        Spacer(Modifier.width(6.dp))
        androidx.compose.material3.Text(text, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = c.ink))
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
    // Righe: in cima le scelte, poi le altre per lettera
    val rows = remember(shown, selected) {
        val out = ArrayList<Any>()
        val (mine, rest) = shown.partition { key(it) in selected }
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
private fun Initial(name: String) {
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

/** La fine: il segno di Sordina, "Tutto pronto" e il pulsante grande in mezzo allo schermo. */
@Composable
private fun AllSet(perms: PermState, label: String, onGo: () -> Unit, onLater: () -> Unit) {
    val c = S.c
    val feel = LocalFeel.current
    val grow = remember { androidx.compose.animation.core.Animatable(0.85f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 260f)) }
    Column(Modifier.fillMaxSize().padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        Mascot(Mood.DONE, Modifier.size(150.dp))
        Spacer(Modifier.height(20.dp))
        androidx.compose.material3.Text(tr(R.string.ob_done_title), style = androidx.compose.ui.text.TextStyle(fontSize = 34.sp, lineHeight = 40.sp, color = c.ink))
        Spacer(Modifier.height(10.dp))
        if (!perms.home) androidx.compose.material3.Text(tr(R.string.ob_done_home), Modifier.padding(horizontal = 12.dp),
            style = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, lineHeight = 21.sp, color = c.soft, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
        val ctx = androidx.compose.ui.platform.LocalContext.current
        androidx.compose.material3.Text(tr(R.string.ob_backup), Modifier.padding(top = 14.dp, start = 12.dp, end = 12.dp).clickableNoRipple { Backup.openSettings(ctx) },
            style = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = c.soft, textAlign = androidx.compose.ui.text.style.TextAlign.Center, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline))
        Spacer(Modifier.height(40.dp))
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxWidth().height(68.dp).graphicsLayer { scaleX = grow.value; scaleY = grow.value }
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(34.dp)).background(c.accent)
                .clickable { feel.confirm(); onGo() },
            contentAlignment = Alignment.Center,
        ) { androidx.compose.material3.Text(label, style = androidx.compose.ui.text.TextStyle(fontSize = 20.sp, color = c.bg)) }
        if (!perms.home) androidx.compose.foundation.layout.Box(
            Modifier.fillMaxWidth().clickableNoRipple(onLater).padding(top = 16.dp, bottom = 4.dp),
            contentAlignment = Alignment.Center,
        ) { Serif(tr(R.string.later), size = 14.sp, color = c.soft) }
    }
}

