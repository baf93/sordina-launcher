package app.sordina.ui

import app.sordina.data.Backup
import app.sordina.ui.theme.autoAccent
import app.sordina.ui.theme.themeAccent
import app.sordina.data.Prefs
import androidx.compose.ui.text.style.TextOverflow
import app.sordina.data.AppIcons
import app.sordina.service.Talking
import app.sordina.service.Classifier
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.BugReport
import app.sordina.data.Faults

import app.sordina.R
import app.sordina.tr
import app.sordina.trn
import app.sordina.Lang

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Rule
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import app.sordina.SordinaApp
import app.sordina.data.AppEntry
import app.sordina.data.CarApps
import app.sordina.data.Categories
import app.sordina.data.Level
import app.sordina.data.Moments
import app.sordina.data.RuleTarget
import app.sordina.data.Rules
import app.sordina.data.Kinds.Kind as KindEntry
import app.sordina.data.Search
import app.sordina.service.SordinaListener
import app.sordina.service.SordinaAccess
import app.sordina.ui.theme.Abstracts
import app.sordina.ui.theme.S
import app.sordina.ui.theme.ThemeFamily
import app.sordina.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Le pagine delle impostazioni. In cima sei argomenti; dentro ogni argomento le sue voci;
 * le liste vere (persone, app, regole) hanno una pagina loro. Indietro torna sempre al livello sopra.
 */
private const val REPO = "https://github.com/baf93/sordina-launcher"

private enum class Sub(val parent: String?) {
    MAIN(null),
    NOTIF("MAIN"), HOME("MAIN"), BRAKE("MAIN"), SEARCH("MAIN"), THEMES("MAIN"), GENERAL("MAIN"), PROGRESS("MAIN"),
    PEOPLE("NOTIF"), TODAY("NOTIF"), ALWAYS("NOTIF"), TRAY("NOTIF"), KINDS("NOTIF"), ORDER("NOTIF"), SECURITY("NOTIF"), CAR("HOME"),
    BRAKED("BRAKE"), HIDDEN("BRAKE"), MASCOTS("HOME"), PINNED("SEARCH"), EXCLUDED("SEARCH"), HA("MAIN"), HA_ALARM("HA"), HA_ROUTINE("HA"), CAR_APPS("CAR"), CAR_PEOPLE("CAR"), PERMS("GENERAL"), CREDITS("GENERAL"), LANG("GENERAL"), FAULTS("GENERAL"), WHY("GENERAL"),
}

private fun hm(min: Int) = Moments.fmt(min)

/**
 * Impostazioni. Le regole di sempre: un interruttore accende o spegne; una riga con › apre una pagina;
 * una riga con un valore apre un foglio con le opzioni, e niente cambia finché non scegli.
 * In più: ogni voce dice, sotto, cosa fa.
 */
@Composable
fun SettingsScreen(
    app: SordinaApp,
    apps: List<AppEntry>,
    perms: PermState,
    family: ThemeFamily,
    mode: ThemeMode,
    version: String,
    backdrop: Int = 0,
    backdropAccent: Int? = null,
    accent: Int = 0,
    onFamily: (ThemeFamily) -> Unit,
    onMode: (ThemeMode) -> Unit,
    onWipe: () -> Unit,
    ops: Ops,
    onBack: () -> Unit,
    /** La lista delle app nascoste (si apre dopo l'impronta) */
    /** Si apre già su questo adesivo, con la sua scheda aperta (dal riquadro di un adesivo appena vinto) */
    sticker: app.sordina.data.Medal? = null,
    hiddenApps: @Composable () -> Unit = {},
) {
    var sub by rememberSaveable { mutableStateOf(if (sticker != null) Sub.PROGRESS else Sub.MAIN) }
    // L'app di cui si guardano i tipi di notifica (pagina Sub.KINDS)
    var kindsOf by rememberSaveable { mutableStateOf<String?>(null) }
    // L'allarme o la routine di Home Assistant aperti (pagine Sub.HA_ALARM e Sub.HA_ROUTINE): entità e nome
    var haItem by rememberSaveable { mutableStateOf<String?>(null) }
    var haName by rememberSaveable { mutableStateOf("") }
    // Solo all'apertura: poi la pagina si comporta come sempre
    var jumpToSticker by remember { mutableStateOf(sticker) }
    // Usato una volta sola: uscendo da Progressi si dimentica, così rientrando non riapre più la scheda
    LaunchedEffect(sub) { if (sub != Sub.PROGRESS) jumpToSticker = null }
    var tick by remember { mutableIntStateOf(0) }
    // Ogni pagina ricorda dove eri: tornando indietro, sei lì
    val scrolls = remember { HashMap<Sub, ScrollState>() }
    fun scrollOf(s: Sub) = scrolls.getOrPut(s) { ScrollState(0) }
    // Ogni modifica ricostruisce la pagina: per un attimo è più corta e lo scorrimento salterebbe. Si torna dov'eri
    var keep by remember { mutableStateOf<Pair<Sub, Int>?>(null) }
    fun change(block: () -> Unit) { keep = sub to scrollOf(sub).value; block(); tick++; ops.changed() }
    LaunchedEffect(tick) {
        val (s, v) = keep ?: return@LaunchedEffect
        keep = null
        // Qualche fotogramma al massimo: appena la pagina è di nuovo lunga abbastanza
        val st = scrollOf(s)
        var frames = 0
        do {
            if (st.value != v) st.scrollTo(v)
            androidx.compose.runtime.withFrameNanos { }
        } while (st.value != v && ++frames < 12)
    }
    fun up() { sub = sub.parent?.let { Sub.valueOf(it) } ?: Sub.MAIN }
    BackHandler(sub != Sub.MAIN) { up() }
    val back = { up() }
    val prefs = app.prefs
    val ctx = LocalContext.current
    fun granted(p: String) = ctx.checkSelfPermission(p) == android.content.pm.PackageManager.PERMISSION_GRANTED

    val toggles = remember { HashMap<String, Boolean>() }
    Box(Modifier.fillMaxSize().systemBarsPadding()) {
    androidx.compose.runtime.CompositionLocalProvider(LocalToggleMemory provides toggles) {
        // Entrando in una sottopagina arriva da destra, tornando indietro da sinistra: come nel resto di Android
        val feel = LocalFeel.current
        androidx.compose.animation.AnimatedContent(
            sub,
            transitionSpec = {
                if (!feel.animations) androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                else {
                    val deeper = depth(targetState) > depth(initialState)
                    Motion.forward(deeper)
                }
            },
            label = "settings",
        ) { page ->
        key(tick) {
            when (page) {
                // ── In cima: cosa manca, poi i sei argomenti
                Sub.MAIN -> Page(tr(R.string.s_settings), null, onBack, scrollOf(Sub.MAIN)) {
                    val free = SordinaListener.batteryFree(ctx)
                    if (!perms.listener || !perms.home || !free) {
                        Topic(tr(R.string.s_fix))
                        Group {
                            if (!perms.listener) Line(tr(R.string.perm_notifications), tr(R.string.grant), tr(R.string.ob_perm_notif), Icons.Rounded.Notifications) { ops.openListenerSettings() }
                            if (!perms.home) Line(tr(R.string.perm_home), tr(R.string.grant), null, Icons.Rounded.Home) { ops.openHomeSettings() }
                            if (!free) Line(tr(R.string.s_battery), tr(R.string.s_battery_limited), tr(R.string.s_battery_hint), Icons.Rounded.BatteryChargingFull) { ops.openBatterySettings() }
                        }
                        Spacer(Modifier.height(18.dp))
                    } else Spacer(Modifier.height(10.dp))
                    TopicCard(tr(R.string.t_progress), tr(R.string.t_progress_d), Icons.Rounded.EmojiEvents) { sub = Sub.PROGRESS }
                    TopicCard(tr(R.string.perm_notifications), tr(R.string.t_notif_d), Icons.Rounded.NotificationsActive) { sub = Sub.NOTIF }
                    TopicCard(tr(R.string.s_brake), tr(R.string.t_brake_d), Icons.Rounded.PanTool) { sub = Sub.BRAKE }
                    TopicCard(tr(R.string.perm_home), tr(R.string.t_home_d), Icons.Rounded.Home) { sub = Sub.HOME }
                    TopicCard(tr(R.string.t_themes), tr(R.string.t_themes_d), Icons.Rounded.Palette) { sub = Sub.THEMES }
                    TopicCard(tr(R.string.s_search), tr(R.string.t_search_d), Icons.Rounded.Search) { sub = Sub.SEARCH }
                    TopicCard(tr(R.string.s_ha), tr(R.string.t_ha_d), Icons.Rounded.Lightbulb) { sub = Sub.HA }
                    TopicCard(tr(R.string.t_general), tr(R.string.t_general_d), Icons.Rounded.Tune) { sub = Sub.GENERAL }
                }

                // ── Notifiche: tutto aspetta in Sordina, tranne. Dal generico allo specifico
                Sub.NOTIF -> Page(tr(R.string.perm_notifications), null, back, scrollOf(page)) {
                    NotifPage(app, apps, change = ::change, onAddApp = { sub = Sub.ALWAYS }, onAddPerson = { sub = Sub.PEOPLE },
                        onApp = { pkg -> kindsOf = pkg; sub = Sub.KINDS }, onSecurity = { sub = Sub.SECURITY }, onAddTray = { sub = Sub.TRAY },
                        onToday = { sub = Sub.TODAY })
                    // La pagina Sordina si apre da Casa: il suo ordine si sceglie qui
                    Topic(tr(R.string.sec_page))
                    Group {
                        val orders = listOf(
                            Option(tr(R.string.s_order_auto), tr(R.string.s_order_auto_hint)),
                            Option(tr(R.string.s_order_arrival)),
                            Option(tr(R.string.s_order_yours)),
                        )
                        val oi = prefs.sordinaOrder.coerceIn(0, 2)
                        Pick(tr(R.string.s_order), orders[oi].label, orders, { it == prefs.sordinaOrder }, icon = Icons.Rounded.Sort, title = tr(R.string.s_order_title)) { i ->
                            change { prefs.sordinaOrder = i }
                            if (i == 2) sub = Sub.ORDER
                        }
                        if (prefs.sordinaOrder == 2) Line(tr(R.string.s_your_order), icon = Icons.Rounded.Reorder) { sub = Sub.ORDER }
                    }
                }

                // ── Home: come cambia durante il giorno e cosa sa
                Sub.HOME -> Page(tr(R.string.perm_home), null, back, scrollOf(page)) {
                    // Tre blocchi: cosa c'è in Home, come cambia nei momenti, i gesti. Una riga per cosa
                    Topic(tr(R.string.home_in_home))
                    Group {
                        val loc = granted(Manifest.permission.ACCESS_COARSE_LOCATION)
                        Toggle(tr(R.string.s_weather), prefs.homeWeather && loc, null, Icons.Rounded.Cloud) { v ->
                            change { prefs.homeWeather = v }
                            if (v && !loc) ops.request(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                        Toggle(tr(R.string.n_agenda), prefs.homeAgenda && perms.calendar, null, Icons.Rounded.CalendarToday) { v ->
                            change { prefs.homeAgenda = v }
                            if (v && !perms.calendar) ops.request(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                        }
                        if (prefs.homeAgenda && perms.calendar) { HomeCalendarsPick(app, ops); CalendarPick(app, ops) }
                        Toggle(tr(R.string.n_cards), prefs.homeCards, null, Icons.Rounded.AutoAwesome) { v -> change { prefs.homeCards = v } }
                        // Solo nella versione di prova: tutti i disegni, uno sotto l'altro
                        if ((ctx.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) Line("Tutte le sordine", "", null, Icons.Rounded.Collections) { sub = Sub.MASCOTS }
                    }
                    Topic(tr(R.string.n_moments))
                    Group {
                        // Una riga sola per accenderla e scegliere l'ora
                        @Composable fun Hour(label: String, on: Boolean, from: Int, hours: List<Int>, icon: androidx.compose.ui.graphics.vector.ImageVector, set: (Boolean, Int) -> Unit) {
                            val labels = listOf(tr(R.string.n_off)) + hours.map { tr(R.string.n_from, hm(it * 60)) }
                            Pick(label, if (on) tr(R.string.n_from, hm(from)) else labels[0], labels.map { Option(it) },
                                { i -> if (i == 0) !on else on && hours[i - 1] * 60 == from }, icon = icon) { i ->
                                change { if (i == 0) set(false, from) else set(true, hours[i - 1] * 60) }
                            }
                        }
                        Hour(tr(R.string.s_scene_evening), prefs.homeEvening, prefs.eveningFrom, listOf(17, 18, 19, 20, 21, 22), Icons.Rounded.WbTwilight) { on, m -> prefs.homeEvening = on; prefs.eveningFrom = m }
                        Hour(tr(R.string.s_scene_night), prefs.homeNight, prefs.nightFrom, listOf(21, 22, 23, 0, 1), Icons.Rounded.Bedtime) { on, m -> prefs.homeNight = on; prefs.nightFrom = m }
                        // Solo quello che c'è: "2 app", "2 app · 3 persone"; acceso e vuoto dice "Acceso"
                        val carSummary = if (!prefs.homeCar) tr(R.string.car_off) else listOfNotNull(
                            ops.carApps().size.takeIf { it > 0 }?.let { trn(R.plurals.car_n_apps, it) },
                            ops.carContacts().size.takeIf { it > 0 }?.let { trn(R.plurals.car_n_people, it) },
                        ).joinToString(" · ").ifEmpty { tr(R.string.car_on) }
                        Line(tr(R.string.s_scene_car), carSummary, null, Icons.Rounded.DirectionsCar) { sub = Sub.CAR }
                    }
                    // Doppio tocco sul fondo di Casa: spegne lo schermo. Serve l'accessibilità di Sordina
                    Topic(tr(R.string.n_gestures))
                    Group {
                        val access by SordinaAccess.on.collectAsState()
                        // Come il freno istantaneo: senza accessibilità resta spento, e accenderlo porta al permesso
                        Toggle(tr(R.string.s_double_tap), access && prefs.doubleTapLock, if (access) null else tr(R.string.s_double_tap_hint), Icons.Rounded.TouchApp) { v ->
                            change { prefs.doubleTapLock = v }
                            if (v && !access) SordinaAccess.openSettings(ctx)
                        }
                    }
                }

                // ── In auto: acceso, solo chiamate, le app e le persone. Si scelgono con le liste di sempre, l'ordine si trascina
                Sub.CAR -> Page(tr(R.string.s_scene_car), tr(R.string.car_sub), back, scrollOf(page)) {
                    Spacer(Modifier.height(8.dp))
                    Group {
                        Toggle(tr(R.string.car_on), prefs.homeCar, null, Icons.Rounded.DirectionsCar) { v -> change { prefs.homeCar = v } }
                        if (prefs.homeCar) Toggle(tr(R.string.n_car_calls_only), prefs.carQuiet && perms.policy, if (perms.policy) null else tr(R.string.s_car_quiet_access), Icons.Rounded.Call) { v ->
                            if (v && !perms.policy) runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            change { prefs.carQuiet = v }
                        }
                    }
                    if (prefs.homeCar) {
                        val car = ops.carApps()
                        Topic(tr(R.string.n_car_shortcuts), tr(R.string.n_car_shortcuts_hint))
                        fun appLabel(k: String) = apps.firstOrNull { it.packageName == k }?.label ?: k
                        DragList(car.map(::appLabel), add = tr(R.string.car_pick_apps), onAdd = { sub = Sub.CAR_APPS }) { order -> change { prefs.carApps = order.map { car[it] } } }
                        Topic(tr(R.string.n_car_people), tr(R.string.n_car_people_hint))
                        val people = remember(tick) { ops.carContacts() }
                        DragList(people.map { it.name }, add = tr(R.string.car_pick_people), onAdd = { sub = Sub.CAR_PEOPLE }) { order -> change { prefs.carPeople = order.map { people[it].id } } }
                        if (car.size > 1 || people.size > 1) Hint(tr(R.string.drag_hint), Modifier.padding(start = 12.dp, top = 10.dp))
                    }
                }

                // ── Freno: le opzioni compaiono solo quando c'è almeno un'app frenata
                Sub.BRAKE -> Page(tr(R.string.s_brake), null, back, scrollOf(page)) {
                    // Come nelle notifiche: l'elenco qui, in fondo "Aggiungi"
                    Topic(tr(R.string.s_braked_apps))
                    AppKeys(prefs.braked.toList(), apps, byPackage = true, onRemove = { k -> change { prefs.braked = prefs.braked - k } }, add = tr(R.string.n_add_app)) { sub = Sub.BRAKED }
                    if (prefs.braked.isNotEmpty()) {
                        Topic(tr(R.string.sec_how))
                        Group {
                            val secs = listOf(5, 10, 20, 30, 60)
                            Pick(tr(R.string.s_wait), trn(R.plurals.n_seconds, prefs.brakeSeconds), secs.map { Option(trn(R.plurals.n_seconds, it)) }, { secs[it] == prefs.brakeSeconds },
                                icon = Icons.Rounded.HourglassEmpty, title = tr(R.string.s_wait_title)) { change { prefs.brakeSeconds = secs[it] } }
                            val windows = listOf(-1 to -1, 20 * 60 to 8 * 60, 22 * 60 to 7 * 60)
                            val labels = listOf(tr(R.string.s_brake_always), "${hm(20 * 60)} – ${hm(8 * 60)}", "${hm(22 * 60)} – ${hm(7 * 60)}")
                            val wi = windows.indexOfFirst { it.first == prefs.brakeFrom && it.second == prefs.brakeTo }.let { if (it < 0) 0 else it }
                            Pick(tr(R.string.s_brake_when), labels[wi], labels.map { Option(it) }, { it == wi }, icon = Icons.Rounded.Schedule) { i ->
                                change { prefs.brakeFrom = windows[i].first; prefs.brakeTo = windows[i].second }
                            }
                            Toggle(tr(R.string.s_brake_grow), prefs.brakeGrow, tr(R.string.s_brake_grow_hint), Icons.AutoMirrored.Rounded.TrendingUp) { v -> change { prefs.brakeGrow = v } }
                            Toggle(tr(R.string.s_brake_guard), prefs.brakeGuard, tr(R.string.h_guard), Icons.Rounded.History) { v ->
                                change { prefs.brakeGuard = v }
                                SordinaAccess.refresh()
                            }
                            // Con l'accessibilità il freno arriva subito, e senza controlli continui (meno batteria)
                            if (prefs.brakeGuard) {
                                val access by SordinaAccess.on.collectAsState()
                                // Acceso da solo con l'accessibilità; senza, resta spento e accenderlo porta al permesso
                                Toggle(tr(R.string.s_instant_brake), access && prefs.instantBrake, tr(R.string.s_instant_brake_hint), Icons.Rounded.Bolt) { v ->
                                    change { prefs.instantBrake = v }
                                    SordinaAccess.refresh()
                                    if (v && !access) SordinaAccess.openSettings(ctx)
                                }
                            }
                        }
                    }
                    // In fondo, senza dire quante sono: le app nascoste, dietro impronta o PIN
                    Spacer(Modifier.height(26.dp))
                    Group {
                        Line(tr(R.string.hidden_apps), hint = tr(R.string.hidden_apps_hint), icon = Icons.Rounded.Lock) {
                            ops.unlock(tr(R.string.hidden_apps)) { sub = Sub.HIDDEN }
                        }
                    }
                }

                // ── App nascoste: si aprono da qui o si rimettono nell'elenco. Uscendo da Sordina si richiude
                // La stessa lista di Tutte le app, con solo le nascoste
                Sub.HIDDEN -> {
                    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
                    androidx.compose.runtime.DisposableEffect(owner) {
                        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_STOP) up() }
                        owner.lifecycle.addObserver(obs)
                        onDispose { owner.lifecycle.removeObserver(obs) }
                    }
                    hiddenApps()
                }

                // ── Ricerca: le proposte quando scorri in su
                Sub.SEARCH -> Page(tr(R.string.s_search), null, back, scrollOf(page)) {
                    Spacer(Modifier.height(8.dp))
                    Group {
                        Toggle(tr(R.string.s_keyboard), prefs.keyboardOnOpen, null, Icons.Rounded.Keyboard) { v -> change { prefs.keyboardOnOpen = v } }
                    }
                    // Le app fissate: in cima, nell'ordine che scegli (fino a 4). Si aggiungono da qui o tenendo premuta una proposta.
                    Topic(tr(R.string.s_pinned), tr(R.string.n_pinned_hint, Prefs.MAX_PINNED))
                    AppKeys(prefs.pinned, apps, sorted = false, onRemove = { k -> change { prefs.pinned = prefs.pinned - k } },
                        onMove = { i, j -> change { val l = prefs.pinned.toMutableList(); l[i] = l[j].also { l[j] = l[i] }; prefs.pinned = l } },
                        add = if (prefs.pinned.size < Prefs.MAX_PINNED) tr(R.string.n_add_app) else null) { sub = Sub.PINNED }
                    // Le app escluse: mai proposte
                    Topic(tr(R.string.s_excluded))
                    AppKeys(prefs.excluded.toList(), apps, onRemove = { k -> change { prefs.excluded = prefs.excluded - k } }, add = tr(R.string.n_add_app)) { sub = Sub.EXCLUDED }
                }

                Sub.THEMES -> Page(tr(R.string.t_themes), null, back, scrollOf(page)) {
                    ThemesPage(app, mode, backdrop, backdropAccent, accent, onMode, ops) { tick++ }
                }

                // ── Generali: lingua, permessi, batteria, i tuoi dati
                Sub.GENERAL -> Page(tr(R.string.t_general), null, back, scrollOf(page)) {
                    Spacer(Modifier.height(8.dp))
                    Group {
                        Line(tr(R.string.s_language), Lang.label(ctx) ?: tr(R.string.lang_phone), null, Icons.Rounded.Language) { sub = Sub.LANG }
                        Line(tr(R.string.s_perms), icon = Icons.Rounded.Lock) { sub = Sub.PERMS }
                    }
                    Topic(tr(R.string.sec_backup))
                    Group {
                        // La copia di ogni giorno nel backup di Google, e (dopo una reinstallazione) quella ritrovata da rimettere
                        val day = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                        val found = remember { Backup.found(ctx) }
                        var restore by remember { mutableStateOf(false) }
                        if (found != null) Line(tr(R.string.s_restore, found.format(day)), icon = Icons.Rounded.Restore, trailing = {}) { restore = true }
                        if (restore) Confirm(tr(R.string.s_restore_title), tr(R.string.s_restore_text), tr(R.string.s_restore_do),
                            onConfirm = { if (Backup.restore(ctx)) Backup.restart(ctx) }, onDismiss = { restore = false })
                        var how by remember { mutableStateOf(false) }
                        val last = remember { Backup.last(ctx) }
                        Line(tr(R.string.s_gbackup), hint = last?.let { tr(R.string.s_gbackup_last, it.format(day)) } ?: tr(R.string.s_gbackup_none),
                            icon = Icons.Rounded.CloudUpload) { how = true }
                        if (how) Sheet(tr(R.string.s_gbackup), onDismiss = { how = false }) {
                            Column(Modifier.padding(horizontal = 24.dp)) {
                                Text(tr(R.string.s_gbackup_how), style = TextStyle(fontSize = 16.sp, lineHeight = 23.sp, color = S.c.ink))
                                Spacer(Modifier.height(18.dp))
                                Primary(tr(R.string.s_gbackup_open), Modifier.fillMaxWidth()) { Backup.openSettings(ctx) }
                                Spacer(Modifier.height(12.dp))
                            }
                        }
                        Line(tr(R.string.s_export), icon = Icons.Rounded.Upload, trailing = {}) { ops.exportSettings() }
                        Line(tr(R.string.s_import), icon = Icons.Rounded.Download, trailing = {}) { ops.importSettings() }
                        var ask by remember { mutableStateOf(false) }
                        Line(tr(R.string.s_wipe), icon = Icons.Rounded.DeleteOutline, trailing = {}) { ask = true }
                        if (ask) Confirm(tr(R.string.s_wipe_title), tr(R.string.s_wipe_text), tr(R.string.s_wipe),
                            onConfirm = { onWipe() }, onDismiss = { ask = false })
                    }
                    Topic(tr(R.string.s_info))
                    Group {
                        Line(tr(R.string.s_version), version, icon = Icons.Rounded.Info)
                        Line(tr(R.string.s_source), "GitHub", null, Icons.Rounded.Code) { ops.openUrl(REPO) }
                        Line(tr(R.string.why_title), icon = Icons.Rounded.MusicNote) { sub = Sub.WHY }
                        Line(tr(R.string.n_credits), icon = Icons.Rounded.Favorite) { sub = Sub.CREDITS }
                        val faults = remember { Faults.recent().size }
                        Line(tr(R.string.faults_title), if (faults == 0) "" else "$faults", null, Icons.Rounded.BugReport) { sub = Sub.FAULTS }
                    }
                }

                // ── Perché Sordina: la parola, l'idea, come la pensa. Solo da leggere
                Sub.WHY -> Page(tr(R.string.why_title), null, back, scrollOf(page)) { WhySordina() }

                // ── Gli errori recenti: quando, dove, cosa. Restano sul telefono
                Sub.FAULTS -> Page(tr(R.string.faults_title), tr(R.string.faults_hint), back, scrollOf(page)) {
                    var list by remember { mutableStateOf(Faults.recent()) }
                    Spacer(Modifier.height(8.dp))
                    if (list.isEmpty()) Group { Line(tr(R.string.faults_none)) }
                    else {
                        val fmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                        Group {
                            list.take(100).forEach { f ->
                                val at = java.time.Instant.ofEpochMilli(f.time).atZone(java.time.ZoneId.systemDefault()).format(fmt)
                                Line(f.where, at, f.what)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Group { Line(tr(R.string.clear), icon = Icons.Rounded.DeleteOutline, trailing = {}) { Faults.clear(); list = emptyList() } }
                    }
                }

                // ── Lingua: quella del telefono in cima, poi tutte in ordine, ognuna col suo nome e, sotto, nella lingua che stai usando
                Sub.LANG -> Page(tr(R.string.s_language), null, back, scrollOf(page)) {
                    val chosen = Lang.chosen(ctx)
                    fun pick(tag: String?) {
                        Lang.choose(ctx, tag)
                        if (android.os.Build.VERSION.SDK_INT < 33) (ctx as? android.app.Activity)?.recreate() else tick++
                    }
                    @Composable fun Mark(on: Boolean) {
                        if (on) Icon(Icons.Rounded.Check, null, Modifier.size(22.dp), tint = S.c.accent) else Spacer(Modifier.size(22.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Group {
                        Line(tr(R.string.lang_phone), hint = Lang.systemName(), icon = Icons.Rounded.PhoneAndroid, trailing = { Mark(chosen == null) }) { pick(null) }
                    }
                    Spacer(Modifier.height(18.dp))
                    Group {
                        Lang.sorted(ctx).forEach { (tag, name) ->
                            val local = Lang.localName(ctx, tag).takeIf { !it.equals(name, true) }
                            Line(name, hint = local, trailing = { Mark(Lang.matches(chosen, tag)) }) { pick(tag) }
                        }
                    }
                }

                // ── Crediti: la licenza di Sordina e quelle di ciò che usa. Ogni voce apre la sua pagina.
                Sub.CREDITS -> Page(tr(R.string.n_credits), null, back, scrollOf(page)) {
                    Spacer(Modifier.height(8.dp))
                    Group {
                        Line("Sordina", "GPL-3.0") { ops.openUrl("$REPO/blob/main/LICENSE") }
                    }
                    Topic(tr(R.string.n_services))
                    Group {
                        Line("Open-Meteo", "CC BY 4.0", tr(R.string.n_weather_data)) { ops.openUrl("https://open-meteo.com") }
                    }
                    Topic(tr(R.string.n_libraries))
                    Group {
                        Line("Jetpack Compose", "Apache 2.0") { ops.openUrl("https://developer.android.com/jetpack/compose") }
                        Line("AndroidX", "Apache 2.0") { ops.openUrl("https://developer.android.com/jetpack/androidx") }
                        Line("Material Icons", "Apache 2.0") { ops.openUrl("https://fonts.google.com/icons") }
                        Line("ML Kit", "Google") { ops.openUrl("https://developers.google.com/ml-kit/terms") }
                    }
                }

                // ── Le pagine con le liste
                // Come "Aggiungi un'app": la rubrica per lettera, le scelte in cima, un tocco per aggiungere o togliere
                Sub.PEOPLE -> if (!perms.contacts) Page(tr(R.string.n_add_person), null, back, scrollOf(page)) {
                    Spacer(Modifier.height(8.dp))
                    ImportantPicker(app, perms, ops)
                } else PersonChooser(app, tr(R.string.n_add_person), back) { ops.changed(); SordinaListener.reapply(ctx) }
                Sub.ALWAYS -> AppChooser(tr(R.string.n_add_app), null, apps.filter { it.packageName !in prefs.braked }, selected = { prefs.inApps }, back) { pkg, on ->
                    Rules.setApp(prefs, pkg, on); ops.changed(); SordinaListener.reapply(ctx, pkg)
                }
                Sub.BRAKED -> AppChooser(tr(R.string.s_braked_apps), null, apps, selected = { prefs.braked }, back) { pkg, on ->
                    prefs.braked = if (on) prefs.braked + pkg else prefs.braked - pkg; ops.changed()
                }
                Sub.CAR_PEOPLE -> ContactChooser(app, tr(R.string.n_car_people), tr(R.string.car_people_sub), selected = { ops.carContacts().map { it.id }.toSet() }, back) { id, on ->
                    val now = ops.carContacts().map { it.id }
                    prefs.carPeople = if (on) (if (now.size < 4) now + id else now) else now - id
                    ops.changed()
                    // Arrivati a quattro si torna indietro da soli
                    if (on && now.size >= 3) back()
                }
                Sub.CAR_APPS -> AppChooser(tr(R.string.n_car_shortcuts), tr(R.string.car_apps_sub), apps, selected = { ops.carApps().toSet() }, back) { pkg, on ->
                    val now = ops.carApps()
                    prefs.carApps = if (on) (if (now.size < 3) now + pkg else now) else now - pkg
                    ops.changed()
                    // Arrivati a tre si torna indietro da soli
                    if (on && now.size >= 2) back()
                }
                // Oggi: cosa è passato e cosa aspetta, e perché. Solo da leggere: le regole si cambiano in Notifiche
                Sub.TODAY -> Page(tr(R.string.today_title), null, back, scrollOf(page)) { TodayPage(app) }
                Sub.SECURITY -> Page(tr(R.string.notif_security), null, back, scrollOf(page)) { SecurityPage(app, apps, change = ::change) }
                Sub.TRAY -> TrayChooser(app, tr(R.string.n_add_notif), tr(R.string.tray_pick_sub), back) { pkg -> ops.changed(); SordinaListener.reapply(ctx, pkg) }
                Sub.KINDS -> kindsOf?.let { pkg -> AppKindsPage(app, pkg, apps.firstOrNull { it.packageName == pkg }?.label ?: pkg, scrollOf(page), back, onChange = { tick++; ops.changed(); SordinaListener.reapply(ctx, pkg) }) }
                Sub.ORDER -> Page(tr(R.string.s_your_order), tr(R.string.order_sub), back, scrollOf(page)) {
                    val order = Categories.custom(prefs.categoryOrder)
                    Spacer(Modifier.height(8.dp))
                    DragList(order.map { it.label }) { o -> change { prefs.categoryOrder = o.map { order[it].name } } }
                }
                Sub.PINNED, Sub.EXCLUDED -> {
                    // Si sceglie per app; si salva per chiave (l'app di lavoro resta distinta)
                    val pinned = sub == Sub.PINNED
                    fun keyOf(pkg: String) = apps.firstOrNull { it.packageName == pkg && !it.isWork }?.key ?: pkg
                    fun chosen() = (if (pinned) prefs.pinned.toSet() else prefs.excluded).mapNotNull { k -> apps.firstOrNull { it.key == k }?.packageName }.toSet()
                    AppChooser(if (pinned) tr(R.string.s_pinned) else tr(R.string.s_excluded), null, apps, selected = { chosen() }, back) { pkg, on ->
                        val k = keyOf(pkg)
                        if (pinned) {
                            val now = prefs.pinned
                            prefs.pinned = if (on) (if (now.size < Prefs.MAX_PINNED) now + k else now) else now - k
                            if (on) prefs.excluded = prefs.excluded - k
                            ops.changed()
                            // Arrivati al massimo si torna indietro da soli
                            if (on && now.size >= Prefs.MAX_PINNED - 1) back()
                        } else {
                            prefs.excluded = if (on) prefs.excluded + k else prefs.excluded - k
                            if (on) prefs.pinned = prefs.pinned - k
                            ops.changed()
                        }
                    }
                }
                // Solo nella versione di prova: ogni disegno della mascotte, grande, con dove compare
                Sub.MASCOTS -> Page("Tutte le sordine", null, back, scrollOf(page)) { MascotGallery() }
                Sub.PROGRESS -> Page(tr(R.string.t_progress), null, back, scrollOf(page)) { ProgressPage(app.habits, jump = jumpToSticker) }
                Sub.HA -> Page(tr(R.string.s_ha), null, back, scrollOf(page)) {
                    HaSettings(app, ops,
                        onAlarm = { id, n -> haItem = id; haName = n; sub = Sub.HA_ALARM },
                        onRoutine = { id, n -> haItem = id; haName = n; sub = Sub.HA_ROUTINE }) { tick++ }
                }
                Sub.HA_ALARM -> haItem?.let { id -> Page(haName, null, back, scrollOf(page)) { HaAlarmPage(app, id, haName, onGone = back) } }
                Sub.HA_ROUTINE -> haItem?.let { id -> Page(haName, null, back, scrollOf(page)) { HaRoutinePage(app, id, haName) } }
                Sub.PERMS -> Page(tr(R.string.s_perms), null, back, scrollOf(page)) {
                    Spacer(Modifier.height(8.dp))
                    Group {
                        PermLine(tr(R.string.perm_home), tr(R.string.h_p_home), perms.home, Icons.Rounded.Home) { ops.openHomeSettings() }
                        PermLine(tr(R.string.perm_notifications), tr(R.string.ob_perm_notif), perms.listener, Icons.Rounded.Notifications) { ops.openListenerSettings() }
                        PermLine(tr(R.string.perm_usage), tr(R.string.ob_perm_usage), perms.usage, Icons.Rounded.BarChart) { ops.openUsageSettings() }
                        PermLine(tr(R.string.perm_contacts), tr(R.string.ob_perm_contacts), perms.contacts, Icons.Rounded.Contacts) { ops.request(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS) }
                        PermLine(tr(R.string.perm_mic), tr(R.string.h_p_mic), perms.mic, Icons.Rounded.Mic) { ops.request(Manifest.permission.RECORD_AUDIO) }
                        PermLine(tr(R.string.perm_calls), tr(R.string.h_p_calls), perms.call, Icons.Rounded.Call) { ops.request(Manifest.permission.CALL_PHONE) }
                        PermLine(tr(R.string.s_calendar), tr(R.string.h_p_cal), perms.calendar, Icons.Rounded.CalendarToday) { ops.request(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR) }
                        PermLine(tr(R.string.perm_location), tr(R.string.h_p_location), granted(Manifest.permission.ACCESS_COARSE_LOCATION), Icons.Rounded.WbSunny) { ops.request(Manifest.permission.ACCESS_COARSE_LOCATION) }
                        // Se Android addormenta Sordina, le notifiche non vengono più smistate
                        PermLine(tr(R.string.s_battery), tr(R.string.s_battery_hint), SordinaListener.batteryFree(ctx), Icons.Rounded.BatteryChargingFull) { ops.openBatterySettings() }
                        // Facoltativa: freno istantaneo e doppio tocco per spegnere
                        PermLine(tr(R.string.perm_access), tr(R.string.ob_perm_access), perms.access, Icons.Rounded.Accessibility) { SordinaAccess.openSettings(ctx) }
                    }
                }
            }
        }
        }
    }
    }
}

/** Un elenco di app con "Togli" accanto a ognuna e, se serve, "Aggiungi" in fondo. [byPackage]: le chiavi sono pacchetti, non voci del cassetto. */
@Composable
private fun AppKeys(keys: List<String>, apps: List<AppEntry>, byPackage: Boolean = false, sorted: Boolean = true, onRemove: (String) -> Unit,
                    onMove: ((Int, Int) -> Unit)? = null, add: String? = null, onAdd: () -> Unit = {}) {
    val intents = app.sordina.SordinaApp.of(androidx.compose.ui.platform.LocalContext.current).intents
    // Un'azione fissata ("do:…") si mostra con il suo nome, come nella ricerca
    fun label(k: String) = (if (byPackage) apps.firstOrNull { it.packageName == k } else apps.firstOrNull { it.key == k })?.label
        ?: k.takeIf { it.startsWith(app.sordina.data.Prefs.PIN_ACTION) }?.let { runCatching { intents.realize(it.removePrefix(app.sordina.data.Prefs.PIN_ACTION), apps, emptyMap())?.title }.getOrNull() }
        ?: k
    Group {
        val list = if (sorted) keys.sortedBy { label(it).lowercase() } else keys
        list.forEachIndexed { i, k ->
            Line(label(k), trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Con un ordine che conta: su e giù accanto a Rimuovi
                    if (onMove != null && list.size > 1) {
                        RoundIcon(Icons.Rounded.KeyboardArrowUp, tr(R.string.up), i > 0) { onMove(i, i - 1) }
                        Spacer(Modifier.width(6.dp))
                        RoundIcon(Icons.Rounded.KeyboardArrowDown, tr(R.string.down), i < list.lastIndex) { onMove(i, i + 1) }
                        Spacer(Modifier.width(10.dp))
                    }
                    SmallPill(tr(R.string.remove)) { onRemove(k) }
                }
            })
        }
        if (add != null) Line(add, icon = Icons.Rounded.Add, trailing = {}, onClick = onAdd)
    }
}

/**
 * Un elenco corto in cui l'ordine conta: tieni premuto una riga e trascinala. Niente frecce, niente "Rimuovi":
 * si toglie dalla lista da cui si sceglie ([add] in fondo). L'ordine nuovo si salva quando lasci il dito.
 */
@Composable
private fun DragList(labels: List<String>, add: String? = null, onAdd: () -> Unit = {}, onOrder: (List<Int>) -> Unit) {
    val c = S.c
    val feel = LocalFeel.current
    val rowH = 58.dp
    val rowPx = with(androidx.compose.ui.platform.LocalDensity.current) { rowH.toPx() }
    // Durante il trascinamento l'ordine vive qui: la pagina non si ricostruisce sotto il dito
    var order by remember(labels) { mutableStateOf(labels.indices.toList()) }
    var dragging by remember { mutableIntStateOf(-1) }
    var offset by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val commit by androidx.compose.runtime.rememberUpdatedState(onOrder)
    val n = labels.size
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.line)) {
        order.forEachIndexed { slot, item ->
            val lifted = slot == dragging
            Row(
                Modifier.padding(top = if (slot == 0) 0.dp else 1.dp)
                    .zIndex(if (lifted) 1f else 0f)
                    .graphicsLayer { translationY = if (lifted) offset else 0f; scaleX = if (lifted) 1.02f else 1f; scaleY = if (lifted) 1.02f else 1f }
                    .fillMaxWidth().height(rowH).background(if (lifted) c.tile else c.card)
                    .then(if (n < 2) Modifier else Modifier.pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { dragging = slot; offset = 0f; feel.threshold() },
                            onDrag = { ch, d ->
                                ch.consume()
                                offset += d.y
                                val target = (dragging + (offset / rowPx).let { if (it > 0) (it + 0.5f).toInt() else (it - 0.5f).toInt() }).coerceIn(0, n - 1)
                                if (target != dragging) {
                                    order = order.toMutableList().also { l -> l.add(target, l.removeAt(dragging)) }
                                    offset -= (target - dragging) * rowPx
                                    dragging = target
                                    feel.tick()
                                }
                            },
                            onDragEnd = { dragging = -1; offset = 0f; if (order != labels.indices.toList()) commit(order) },
                            onDragCancel = { dragging = -1; offset = 0f },
                        )
                    })
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(labels[item], Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 17.sp, color = c.ink))
                if (n > 1) Icon(Icons.Rounded.DragHandle, null, Modifier.size(22.dp), tint = c.soft)
            }
        }
        if (add != null) Box(Modifier.padding(top = if (n == 0) 0.dp else 1.dp)) { Line(add, icon = Icons.Rounded.Add, trailing = {}, onClick = onAdd) }
    }
}

/** Quanto è in fondo una pagina: serve a capire se stai entrando o tornando. */
private fun depth(s: Sub): Int = s.parent?.let { 1 + depth(Sub.valueOf(it)) } ?: 0

/** Una pagina: la testata uguale al resto dell'app (freccia, titolo grande, la frase che dice a cosa serve) e sotto il contenuto che scorre. */
@Composable
private fun Page(title: String, subtitle: String?, onBack: () -> Unit, scroll: ScrollState, content: @Composable ColumnScope.() -> Unit) {
    // La tastiera restringe la pagina: il campo in cui scrivi scorre sopra e non resta coperto
    Column(Modifier.fillMaxSize().imeJump().verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 16.dp)) {
        Header(title, onBack)
        subtitle?.let { Intro(it, Modifier.padding(bottom = 8.dp)) }
        content()
        Spacer(Modifier.height(48.dp))
    }
}

/** Cosa fa la pagina, detto una volta: sotto il titolo, a filo con i blocchi. */
@Composable
private fun Intro(text: String, modifier: Modifier = Modifier, start: Int = 12) {
    Text(text, modifier.padding(start = start.dp, end = 12.dp, top = 4.dp),
        style = TextStyle(fontSize = 16.sp, lineHeight = 23.sp, color = S.c.soft))
}

/** Un argomento in cima alle impostazioni: un blocco come quelli della pagina Sordina, con il nome, a cosa serve e com'è adesso. */
@Composable
private fun TopicCard(title: String, about: String, icon: ImageVector, onClick: () -> Unit) {
    val c = S.c
    Row(
        Modifier.padding(vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.card)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = c.accent)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = TextStyle(fontSize = 18.sp, color = c.ink))
            // Sempre una riga: tutte le schede hanno la stessa altezza
            Text(about, Modifier.padding(top = 2.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, color = c.soft))
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.padding(start = 6.dp).size(22.dp), tint = c.soft)
    }
}


/**
 * Scegliere più app da un elenco, come "Tutte le app" e il primo avvio: in cima le scelte,
 * poi le altre per lettera, la lente per filtrare. Tocca una riga per aggiungerla o toglierla.
 */
@Composable
private fun AppChooser(title: String, about: String?, apps: List<AppEntry>, selected: () -> Set<String>, onBack: () -> Unit, onToggle: (String, Boolean) -> Unit) {
    val installed = remember(apps) { apps.filter { !it.isWork }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() } }
    var chosen by remember { mutableStateOf(selected()) }
    var q by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 16.dp)) {
        Header(title, onBack)
        about?.let { Intro(it, Modifier.padding(bottom = 12.dp), start = 4) }
        Field(q, { q = it }, tr(R.string.s_search_app), Modifier.padding(horizontal = 4.dp))
        Box(Modifier.weight(1f).padding(start = 4.dp)) {
            PickList(installed, { it.packageName }, { it.label }, q, leading = { a -> AppDot(a.packageName) }, selected = chosen, onScrolled = {}) { a ->
                onToggle(a.packageName, a.packageName !in chosen)
                chosen = selected()
            }
        }
    }
}

/**
 * Aggiungi una notifica: quello che c'è adesso nella tendina, un tipo per riga ("Meteo · Previsioni"), scelto come le app.
 * In cima i tipi già in sordina, così si tolgono anche quando non sono più nella tendina.
 * Niente chat (le persone hanno la loro sezione), niente app che entrano (i loro tipi si spengono nella loro pagina).
 */
@Composable
private fun TrayChooser(app: SordinaApp, title: String, about: String, onBack: () -> Unit, onChange: (String) -> Unit) {
    val prefs = app.prefs
    var items by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    LaunchedEffect(Unit) {
        items = withContext(Dispatchers.IO) {
            val now = SordinaListener.tray()
                .filter { it.person == null && !it.channel.isNullOrBlank() && it.pkg !in prefs.inApps && !Classifier.isCore(it.pkg) }
                .map { Classifier.channelKey(it.pkg, it.channel) to "${it.appLabel} · ${it.kindName}" }
            val names = now.toMap()
            val muted = Rules.trayOff(prefs).map { it to (prefs.ruleNames[it] ?: names[it] ?: it.substringAfter('|')) }
            (muted + now).distinctBy { it.first }.sortedBy { it.second.lowercase() }
        }
    }
    var chosen by remember { mutableStateOf(prefs.offTypes) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 16.dp)) {
        Header(title, onBack)
        Intro(about, Modifier.padding(bottom = 12.dp), start = 4)
        val list = items
        if (list != null && list.isEmpty()) Text(tr(R.string.tray_none), Modifier.padding(horizontal = 8.dp, vertical = 12.dp), style = TextStyle(fontSize = 15.sp, color = S.c.soft))
        else if (list != null) Box(Modifier.weight(1f).padding(start = 4.dp)) {
            PickList(list, { it.first }, { it.second }, "", leading = { e -> AppDot(e.first.substringBefore('|')) }, selected = chosen, onScrolled = {}) { e ->
                val k = e.first
                val on = k !in chosen
                prefs.offTypes = if (on) prefs.offTypes + k else prefs.offTypes - k
                if (on) prefs.ruleNames = prefs.ruleNames + (k to e.second)
                chosen = prefs.offTypes
                onChange(k.substringBefore('|'))
            }
        }
    }
}

/** Come [AppChooser], per le persone: in cima le scelte, poi la rubrica per lettera (solo chi ha un numero), la lente per filtrare. */
@Composable
private fun ContactChooser(app: SordinaApp, title: String, about: String?, selected: () -> Set<Long>, onBack: () -> Unit, onToggle: (Long, Boolean) -> Unit) {
    var all by remember { mutableStateOf(emptyList<app.sordina.data.Contact>()) }
    LaunchedEffect(Unit) {
        all = withContext(Dispatchers.IO) { app.people.all().filter { it.number.isNotBlank() }.distinctBy { it.id }.sortedBy { it.name.lowercase() } }
    }
    var chosen by remember { mutableStateOf(selected()) }
    var q by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 16.dp)) {
        Header(title, onBack)
        about?.let { Intro(it, Modifier.padding(bottom = 12.dp), start = 4) }
        Field(q, { q = it }, tr(R.string.search_contact), Modifier.padding(horizontal = 4.dp))
        Box(Modifier.weight(1f).padding(start = 4.dp)) {
            PickList(all, { it.id.toString() }, { it.name }, q, leading = { k -> Initial(k.name) }, selected = chosen.mapTo(HashSet()) { it.toString() }, onScrolled = {}) { k ->
                onToggle(k.id, k.id !in chosen)
                chosen = selected()
            }
        }
    }
}

/**
 * Le persone che entrano ovunque, scelte come le app: in cima quelle già dentro, poi la rubrica per lettera, la lente per filtrare.
 * Una persona è il suo nome: due voci con lo stesso nome sono una riga sola.
 */
@Composable
private fun PersonChooser(app: SordinaApp, title: String, onBack: () -> Unit, onChange: () -> Unit) {
    var all by remember { mutableStateOf(emptyList<app.sordina.data.Contact>()) }
    LaunchedEffect(Unit) {
        all = withContext(Dispatchers.IO) { app.people.invalidate(); app.people.all().filter { it.normalized.isNotBlank() }.distinctBy { it.normalized }.sortedBy { it.name.lowercase() } }
    }
    var chosen by remember { mutableStateOf(app.prefs.importantNames) }
    var q by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 16.dp)) {
        Header(title, onBack)
        Field(q, { q = it }, tr(R.string.search_contact), Modifier.padding(horizontal = 4.dp))
        Box(Modifier.weight(1f).padding(start = 4.dp)) {
            PickList(all, { it.normalized }, { it.name }, q, leading = { k -> Initial(k.name) }, selected = chosen, onScrolled = {}) { k ->
                if (k.normalized in chosen) app.people.removeImportantName(k.normalized) else app.people.setImportant(k, true)
                chosen = app.prefs.importantNames
                onChange()
            }
        }
    }
}

@Composable
private fun PermLine(label: String, why: String, ok: Boolean, icon: ImageVector, onAsk: () -> Unit) {
    if (ok) Line(label, tr(R.string.yes), why, icon) else Line(label, tr(R.string.grant), why, icon, onClick = onAsk)
}

/**
 * I calendari che si vedono in Casa, quanti vuoi: quelli del telefono (anche in sola lettura) e, se c'è il token, quelli di Home Assistant.
 * Quello dei nuovi impegni c'è sempre: non avrebbe senso creare impegni che poi non vedi.
 * Niente unioni: se un impegno è in due calendari, si vede due volte.
 */
@Composable
internal fun HomeCalendarsPick(app: SordinaApp, ops: Ops) {
    val prefs = app.prefs
    var cals by remember { mutableStateOf(emptyList<app.sordina.data.Cal>()) }
    var home by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
    var chosen by remember { mutableStateOf(prefs.homeCalendars) }
    LaunchedEffect(Unit) {
        cals = withContext(Dispatchers.IO) { app.calendars.list(writable = false) }
        if (app.ha.configured) home = withContext(Dispatchers.IO) { app.ha.calendars() }
    }
    if (cals.isEmpty() && home.isEmpty()) return
    val keys = cals.map { "p:${it.id}" } + home.map { "h:${it.first}" }
    // Quello dei nuovi impegni: sempre acceso
    val fixed = if (app.ha.calendar != null) "h:${prefs.haCalendar}" else "p:${prefs.calendarId.takeIf { id -> cals.any { it.id == id } } ?: cals.firstOrNull { it.primary }?.id ?: cals.firstOrNull()?.id}"
    // Finché non scegli: tutti quelli del telefono, come prima
    val on = (chosen.ifEmpty { cals.map { "p:${it.id}" }.toSet() } + fixed).filter { it in keys }.toSet()
    val dup = { n: String -> cals.count { it.name == n } > 1 }
    val options = cals.map { Option(it.name, if (dup(it.name)) it.account else null) } + home.map { Option(it.second, tr(R.string.s_ha)) }
    Pick(tr(R.string.s_home_cals), "", options, { keys[it] in on }, icon = Icons.Rounded.CalendarMonth, title = tr(R.string.s_home_cals), multi = true, arrow = true) { i ->
        val k = keys[i]
        if (k == fixed) return@Pick
        chosen = if (k in on) on - k else on + k
        prefs.homeCalendars = chosen
        app.ha.resetCalendarCache()
        ops.changed()
    }
}

/**
 * Il calendario dei nuovi impegni, da un foglio: quelli del telefono e, se c'è il token, quelli di Home Assistant.
 * Uno solo: è quello in cui finiscono gli impegni che detti.
 */
@Composable
internal fun CalendarPick(app: SordinaApp, ops: Ops) {
    val prefs = app.prefs
    var cals by remember { mutableStateOf(emptyList<app.sordina.data.Cal>()) }
    var home by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
    var sel by remember { mutableStateOf(prefs.calendarId) }
    var selHome by remember { mutableStateOf(if (app.ha.configured) prefs.haCalendar else "") }
    LaunchedEffect(Unit) {
        cals = withContext(Dispatchers.IO) { app.calendars.list() }
        if (app.ha.configured) home = withContext(Dispatchers.IO) { app.ha.calendars() }
    }
    if (cals.isEmpty() && home.isEmpty()) { Line(tr(R.string.s_events_go), tr(R.string.none_m), null, Icons.Rounded.CalendarToday); return }
    val phone = cals.firstOrNull { it.id == sel } ?: cals.firstOrNull()
    val current = home.firstOrNull { it.first == selHome }?.second ?: phone?.name ?: home.first().second
    val dup = { n: String -> cals.count { it.name == n } > 1 }
    val options = cals.map { Option(it.name, if (dup(it.name)) it.account else null) } +
        home.map { Option(it.second, tr(R.string.s_ha)) }
    val isOn = { i: Int ->
        if (i < cals.size) selHome.isBlank() && cals[i].id == phone?.id else home[i - cals.size].first == selHome
    }
    Pick(tr(R.string.s_events_go), current, options, isOn, icon = Icons.Rounded.CalendarToday, title = tr(R.string.s_events_go), arrow = true) { i ->
        if (i < cals.size) {
            sel = cals[i].id; selHome = ""
            prefs.calendarId = sel; prefs.haCalendar = ""; prefs.haCalendarName = ""
        } else {
            val (id, name) = home[i - cals.size]
            selHome = id
            prefs.haCalendar = id; prefs.haCalendarName = name
        }
        app.ha.resetCalendarCache()
        ops.changed()
    }
}


/** Una riga di un elenco lungo: i blocchi si toccano, il primo e l'ultimo arrotondati, come un [Group]. */
@Composable
private fun ListCell(i: Int, n: Int, content: @Composable () -> Unit) {
    val r = 22.dp
    val shape = RoundedCornerShape(topStart = if (i == 0) r else 0.dp, topEnd = if (i == 0) r else 0.dp,
        bottomStart = if (i == n - 1) r else 0.dp, bottomEnd = if (i == n - 1) r else 0.dp)
    Box(Modifier.fillMaxWidth().clip(shape).background(S.c.line).padding(top = if (i == 0) 0.dp else 1.dp)) { content() }
}

// ── Notifiche ─────────────────────────────────────────────────────────────────────────────────────

/**
 * Tutto aspetta in Sordina, tranne: gli Importanti (codici e sicurezza sempre, il resto si spegne),
 * le App che entrano (con le eventuali eccezioni dentro), le Persone che entrano ovunque o da un'app sola.
 * Il sottotitolo c'è solo quando dice qualcosa: "2 eccezioni", "su WhatsApp".
 */
@Composable
private fun NotifPage(app: SordinaApp, apps: List<AppEntry>, change: (() -> Unit) -> Unit, onAddApp: () -> Unit, onAddPerson: () -> Unit, onApp: (String) -> Unit, onSecurity: () -> Unit, onAddTray: () -> Unit, onToday: () -> Unit) {
    val prefs = app.prefs
    val ctx = LocalContext.current
    fun appName(pkg: String) = apps.firstOrNull { it.packageName == pkg }?.label ?: pkg
    fun apply(block: () -> Unit) = change { block(); SordinaListener.reapply(ctx) }

    Intro(tr(R.string.notif_intro))
    // Oggi: per capire perché una cosa è passata o ha aspettato, prima delle regole
    val today by app.today.items.collectAsState()
    Spacer(Modifier.height(16.dp))
    Group { Line(tr(R.string.today_title), hint = todaySummary(remember(today) { app.today.current() }), onClick = onToday) }
    Topic(tr(R.string.notif_important))
    Group {
        val skipped = prefs.noGuess.size
        Line(tr(R.string.notif_security), if (skipped == 0) tr(R.string.pass_always) else trn(R.plurals.security_except_n, skipped),
            tr(R.string.notif_security_hint), Icons.Rounded.Lock) { onSecurity() }
        Toggle(tr(R.string.notif_missed), prefs.missedPass, null, Icons.Rounded.Call) { v -> apply { prefs.missedPass = v } }
        Toggle(tr(R.string.notif_remind), prefs.remindPass, null, Icons.Rounded.Schedule) { v -> apply { prefs.remindPass = v } }
        Toggle(tr(R.string.s_urgent), prefs.urgentEnabled, tr(R.string.s_urgent_hint), Icons.Rounded.PriorityHigh) { v -> apply { prefs.urgentEnabled = v } }
        Toggle(tr(R.string.s_talk), prefs.talkPass, tr(R.string.h_talk), Icons.Rounded.Forum) { v -> apply { prefs.talkPass = v; if (!v) Talking.clear() } }
        Toggle(tr(R.string.s_system), prefs.systemPass, tr(R.string.h_system), Icons.Rounded.Settings) { v -> apply { prefs.systemPass = v } }
    }

    Topic(tr(R.string.kind_apps))
    Group {
        prefs.inApps.sortedBy { appName(it).lowercase() }.forEach { pkg ->
            val n = Rules.exceptions(prefs, pkg)
            Line(appName(pkg), hint = if (n > 0) trn(R.plurals.n_exceptions, n) else null,
                leading = if (prefs.appIcons) ({ AppDot(pkg) }) else null) { onApp(pkg) }
        }
        Line(tr(R.string.n_add_app), icon = Icons.Rounded.Add, trailing = {}, onClick = onAddApp)
    }

    Topic(tr(R.string.cat_people))
    var editing by remember { mutableStateOf<String?>(null) }
    val names = prefs.ruleNames
    // Il nome come in rubrica ("Elisa Rossi"), non la chiave ("elisa rossi")
    var book by remember { mutableStateOf(emptyMap<String, String>()) }
    LaunchedEffect(Unit) { book = withContext(Dispatchers.IO) { runCatching { app.people.all().associate { it.normalized to it.name } }.getOrDefault(emptyMap()) } }
    fun pretty(n: String) = book[n] ?: n.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
    fun personName(k: String) = names[k]?.substringAfter(" · ") ?: pretty(k.substringAfter('|'))
    Group {
        val everywhere = prefs.importantNames.map { it to null as String? }
        val oneApp = prefs.peopleIn.map { it to it.substringBefore('|') }
        (everywhere + oneApp).sortedBy { (k, pkg) -> (if (pkg == null) k else personName(k)).lowercase() }.forEach { (k, pkg) ->
            if (pkg == null) Line(pretty(k), hint = if (k in prefs.importantInGroups) tr(R.string.n_in_groups) else null) { editing = k }
            else Line(personName(k), hint = tr(R.string.notif_on_app, appName(pkg))) { editing = k }
        }
        Line(tr(R.string.n_add_person), icon = Icons.Rounded.Add, trailing = {}, onClick = onAddPerson)
    }
    editing?.let { k ->
        val everywhere = '|' !in k
        Sheet(if (everywhere) pretty(k) else personName(k), onDismiss = { editing = null }) {
            if (everywhere) Toggle(tr(R.string.n_in_groups), k in prefs.importantInGroups, null, Icons.Rounded.Groups) { v ->
                apply { prefs.importantInGroups = if (v) prefs.importantInGroups + k else prefs.importantInGroups - k }
                editing = null
            }
            // I nomi collegati a lei ("Elisa" su Telegram): si vedono e si tolgono uno per uno
            if (everywhere) prefs.personAliases.filterValues { it == k }.keys.sorted().forEach { a ->
                val seen = names[a]?.substringAfter(" · ")
                    ?: a.substringAfter('|').removePrefix("s:").split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
                SheetRow(tr(R.string.alias_row, seen, appName(a.substringBefore('|'))), Icons.Rounded.Link, hint = tr(R.string.remove)) {
                    apply { prefs.personAliases = prefs.personAliases - a; prefs.ruleNames = prefs.ruleNames - a }
                    editing = null
                }
            }
            SheetRow(tr(R.string.remove), Icons.Rounded.DeleteOutline) {
                apply { if (everywhere) app.people.removeImportantName(k) else prefs.peopleIn = prefs.peopleIn - k }
                editing = null
            }
        }
    }

    // Dalla tendina: un tipo di notifica che passerebbe (il meteo, i promemoria di un'app, una notifica di sistema)
    // va in sordina. Solo quel tipo, non l'app.
    Topic(tr(R.string.notif_tray))
    var muting by remember { mutableStateOf<String?>(null) }
    fun trayName(k: String) = prefs.ruleNames[k] ?: (appName(k.substringBefore('|')) + " · " + k.substringAfter('|'))
    Group {
        Rules.trayOff(prefs).sortedBy { trayName(it).lowercase() }.forEach { k ->
            Line(trayName(k), leading = if (prefs.appIcons) ({ AppDot(k.substringBefore('|')) }) else null) { muting = k }
        }
        Line(tr(R.string.n_add_notif), icon = Icons.Rounded.Add, trailing = {}, onClick = onAddTray)
    }
    muting?.let { k ->
        Sheet(trayName(k), onDismiss = { muting = null }) {
            SheetRow(tr(R.string.remove), Icons.Rounded.DeleteOutline) {
                change { prefs.offTypes = prefs.offTypes - k; SordinaListener.reapply(ctx, k.substringBefore('|')) }
                muting = null
            }
        }
    }
}

/**
 * Un'app che entra: entra tutto, tranne i tipi (o le chat) che spegni qui. I tipi si imparano man mano che
 * arrivano (Android non ne dà l'elenco a un launcher); quelli nuovi entrano.
 */
/**
 * Codici e sicurezza: le app che ho fatto passare come codice o avviso di pagamento, ciascuna si può spegnere.
 * Spenta, le sue notifiche seguono le regole come tutte le altre (un'app di offerte che scrive "codice SCONTO20").
 */
@Composable
private fun SecurityPage(app: SordinaApp, apps: List<AppEntry>, change: (() -> Unit) -> Unit) {
    val prefs = app.prefs
    val ctx = LocalContext.current
    var off by remember { mutableStateOf(prefs.noGuess) }
    // Il nome visto quando il codice è passato: anche le app senza icona (Play Services manda codici)
    val seen = prefs.securityApps
    fun appName(pkg: String) = apps.firstOrNull { it.packageName == pkg }?.label ?: seen[pkg] ?: pkg
    val list = (seen.keys + off).sortedBy { appName(it).lowercase() }
    Intro(tr(R.string.security_intro))
    if (list.isEmpty()) Text(tr(R.string.security_none), Modifier.padding(horizontal = 8.dp, vertical = 12.dp), style = TextStyle(fontSize = 15.sp, color = S.c.soft))
    else Group {
        list.forEach { pkg ->
            Toggle(appName(pkg), pkg !in off, leading = if (prefs.appIcons) ({ AppDot(pkg) }) else null) { on ->
                change { prefs.noGuess = if (on) prefs.noGuess - pkg else prefs.noGuess + pkg; SordinaListener.reapply(ctx, pkg) }
                off = prefs.noGuess
            }
        }
    }
    Spacer(Modifier.height(26.dp))
}

@Composable
private fun AppKindsPage(app: SordinaApp, pkg: String, label: String, scroll: ScrollState, onBack: () -> Unit, onChange: () -> Unit) {
    val prefs = app.prefs
    // Quello che c'è adesso nella tendina o in sordina conta già: la lista non parte vuota
    // Subito quello che si sa già; la tendina (una chiamata ad Android che legge tutte le notifiche) fuori dal thread dell'interfaccia
    var kinds by remember(pkg) { mutableStateOf(app.kinds.of(pkg)) }
    LaunchedEffect(pkg) {
        kinds = withContext(Dispatchers.IO) {
            SordinaListener.tray().filter { it.pkg == pkg }.forEach { t ->
                if (t.person != null) app.kinds.chat(pkg, t.person, t.personTitle) else app.kinds.channel(pkg, t.channel, t.channelName)
            }
            app.store.held.value.values.filter { it.pkg == pkg && !it.groupKey.startsWith("p:") }.forEach { h -> app.kinds.channel(pkg, h.channel, h.channelName) }
            app.kinds.of(pkg)
        }
    }
    var off by remember { mutableStateOf(prefs.offTypes + prefs.offChats) }
    // Spente ma non (più) fra quelle viste: restano in lista, si possono riaccendere
    val known = kinds.map { it.ruleKey }.toSet()
    val orphans = off.filter { it.substringBefore('|') == pkg && it !in known }.map { k ->
        KindEntry(pkg, k in prefs.offChats, k.substringAfter('|'), prefs.ruleNames[k]?.substringAfter(" · ") ?: k.substringAfter('|'), 0L)
    }
    val all = kinds + orphans
    fun flip(k: KindEntry, on: Boolean) {
        val key = k.ruleKey
        if (k.isChat) prefs.offChats = if (on) prefs.offChats - key else prefs.offChats + key
        else prefs.offTypes = if (on) prefs.offTypes - key else prefs.offTypes + key
        if (!on) prefs.ruleNames = prefs.ruleNames + (key to "$label · ${k.name}")
        off = prefs.offTypes + prefs.offChats
        onChange()
    }
    Page(label, tr(R.string.app_kinds_sub), onBack, scroll) {
        val (chats, types) = all.partition { it.isChat }
        if (all.isEmpty()) Text(tr(R.string.app_kinds_none), Modifier.padding(horizontal = 8.dp, vertical = 12.dp), style = TextStyle(fontSize = 15.sp, color = S.c.soft))
        if (chats.isNotEmpty()) {
            if (types.isNotEmpty()) Topic(tr(R.string.app_kinds_chats))
            Group { chats.forEach { k -> Toggle(k.name, k.ruleKey !in off) { v -> flip(k, v) } } }
        }
        if (types.isNotEmpty()) {
            if (chats.isNotEmpty()) Topic(tr(R.string.app_kinds_types))
            Group { types.forEach { k -> Toggle(k.name, k.ruleKey !in off) { v -> flip(k, v) } } }
        }
        Spacer(Modifier.height(26.dp))
        Group {
            SheetRow(tr(R.string.app_remove, label), Icons.Rounded.DeleteOutline) {
                Rules.setApp(prefs, pkg, false); onChange(); onBack()
            }
        }
    }
}

// ── Temi ──────────────────────────────────────────────────────────────────────────────────────────

/** I colori di rilievo: quello del tema, quello dello sfondo, e qualche tinta calma. */
private val ACCENTS = listOf(0xFF2E9C93, 0xFFC4623A, 0xFF4F7A5A, 0xFF3D6FA8, 0xFF7A5BA6, 0xFFC98B1E, 0xFFB5476B).map { it.toInt() }

/** Le tinte unite pronte: scure e chiare, calme. Il nero è per gli OLED. */
private val SOLIDS = listOf(
    0xFF000000, 0xFF1C1C1E, 0xFF2F3A45, 0xFF34433A, 0xFF4A2F2A, 0xFF3B2F4F, 0xFF0F2A33,
    0xFFF4F1EC, 0xFFE9E1D6, 0xFFDCE3E8, 0xFFE8DCE3, 0xFFF2E3C6, 0xFFD9E4D3,
).map { it.toInt() }

@Composable
private fun ThemesPage(app: SordinaApp, mode: ThemeMode, backdrop: Int, backdropAccent: Int?, accent: Int, onMode: (ThemeMode) -> Unit, ops: Ops, refresh: () -> Unit) {
    val prefs = app.prefs
    val c = S.c
    fun apply(block: () -> Unit) { block(); ops.backdropChanged(); refresh() }
    var picking by remember { mutableStateOf<String?>(null) }

    // 1. Chiaro o scuro
    Spacer(Modifier.height(8.dp))
    Segmented(ThemeMode.entries.map { it.label }, mode.ordinal) { onMode(ThemeMode.entries[it]); refresh() }

    // 2. Lo sfondo della Home: un tipo, poi la scelta dentro quel tipo
    Topic(tr(R.string.n_home_bg))
    val kind = when (backdrop) { 0, 4 -> 0; 3 -> 1; 1 -> 2; else -> 3 }
    Segmented(listOf(tr(R.string.n_bg_color), tr(R.string.n_bg_drawn), tr(R.string.n_bg_phone), tr(R.string.wp_photo)), kind) { i ->
        when (i) {
            0 -> if (backdrop != 0 && backdrop != 4) apply { prefs.wallpaperMode = 0 }
            1 -> apply { prefs.wallpaperMode = 3 }
            2 -> apply { prefs.wallpaperMode = 1 }
            else -> ops.pickWallpaper()
        }
    }
    Spacer(Modifier.height(14.dp))
    when (kind) {
        // Tinta unita: quella del tema, una pronta, o una scelta con il selettore
        // Colore: prima il nero puro (se può essere scuro); acceso, lo sfondo è nero e i colori non servono
        0 -> {
            val black = prefs.trueBlack && mode != ThemeMode.LIGHT
            if (mode != ThemeMode.LIGHT) {
                Group { Toggle(tr(R.string.s_black), prefs.trueBlack, null, Icons.Rounded.DarkMode) { v -> apply { prefs.trueBlack = v; if (v) prefs.wallpaperMode = 0 } } }
                if (!black) Spacer(Modifier.height(10.dp))
            }
            if (!black) Swatches {
            Swatch(c.bg, tr(R.string.n_auto), backdrop == 0, ring = true) { apply { prefs.wallpaperMode = 0 } }
            // Il colore uguale ad "Auto" non si ripete
            SOLIDS.filter { Color(it).toArgb() != c.bg.toArgb() }.forEach { s -> Swatch(Color(s), null, backdrop == 4 && prefs.solidColor == s, ring = true) { apply { prefs.solidColor = s; prefs.wallpaperMode = 4 } } }
            val custom = backdrop == 4 && prefs.solidColor !in SOLIDS
            Swatch(if (custom) Color(prefs.solidColor) else null, null, custom, ring = true, plus = true) { picking = "solid" }
        }
        }
        // Disegnati da Sordina: una fila per tipo, scorre di lato
        1 -> Abstracts.Kind.entries.forEach { k ->
            Text(k.label, Modifier.padding(start = 12.dp, top = 6.dp, bottom = 8.dp), style = TextStyle(fontSize = 15.sp, color = c.soft))
            PresetRow(k, if (backdrop == 3) prefs.wallpaperPreset else -1) { i -> apply { prefs.wallpaperPreset = i; prefs.wallpaperMode = 3 } }
            Spacer(Modifier.height(10.dp))
        }
        3 -> Group { Line(tr(R.string.wp_change_photo), icon = Icons.Rounded.Image) { ops.pickWallpaper() } }
        // Telefono: un riquadro che spiega da dove viene lo sfondo
        else -> Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.card).padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Info, null, Modifier.size(22.dp), tint = c.accent)
            Spacer(Modifier.width(14.dp))
            Text(tr(R.string.n_phone_bg_info), style = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, color = c.soft))
        }
    }
    // Lo sfondo del telefono è già lì: niente da copiare. È un'azione, non un interruttore: "Applica"
    if (backdrop != 1) {
        Spacer(Modifier.height(10.dp))
        Group {
            Line(tr(R.string.n_lock_too), icon = Icons.Rounded.ScreenLockPortrait, trailing = {
                SmallPill(tr(R.string.n_apply)) { ops.setLockWallpaper(if (backdrop == 0) c.bg.toArgb() else 0) }
            }) { ops.setLockWallpaper(if (backdrop == 0) c.bg.toArgb() else 0) }
        }
    }

    // 3. Il colore principale: pulsanti, interruttori, evidenze
    Topic(tr(R.string.n_accent))
    Swatches {
        // Auto: il colore dello sfondo (o quello del tema); mostra quello che si otterrebbe,
        // lo stesso qualunque altro colore sia scelto: cambia solo se cambia lo sfondo
        val isAuto = accent == 0 || accent == 1
        val autoColor = autoAccent(backdropAccent)?.let { Color(it) } ?: themeAccent(c.dark)
        Swatch(autoColor, tr(R.string.n_auto), isAuto) { apply { prefs.accent = 0 } }
        ACCENTS.forEach { a -> Swatch(Color(a), null, accent == a) { apply { prefs.accent = a } } }
        val custom = accent != 0 && accent != 1 && accent !in ACCENTS
        Swatch(if (custom) Color(accent) else null, null, custom, plus = true) { picking = "accent" }
    }

    // 4. Il resto
    Topic(tr(R.string.n_other))
    Group {
        // Schede trasparenti: solo se dietro c'è un'immagine
        if (backdrop != 0) {
            var g by remember { mutableStateOf(prefs.glass) }
            Toggle(tr(R.string.s_glass), g, null, Icons.Rounded.Layers) { v -> g = v; prefs.glass = v; ops.backdropChanged() }
        }
        Toggle(tr(R.string.s_icons), prefs.appIcons, null, Icons.Rounded.Apps) { v -> prefs.appIcons = v; ops.changed(); refresh() }
        Toggle(tr(R.string.s_animations), prefs.animations, null, Icons.Rounded.Animation) { v -> prefs.animations = v; ops.changed(); refresh() }
        Toggle(tr(R.string.s_haptics), prefs.haptics, null, Icons.Rounded.Vibration) { v -> prefs.haptics = v; ops.changed(); refresh() }
    }

    picking?.let { what ->
        val start = if (what == "solid") prefs.solidColor else if (accent != 0 && accent != 1) accent else c.accent.toArgb()
        ColorSheet(start, onDismiss = { picking = null }) { v ->
            picking = null
            apply { if (what == "solid") { prefs.solidColor = v; prefs.wallpaperMode = 4 } else prefs.accent = v }
        }
    }
}

/** Una scelta fra poche opzioni, a pillole affiancate dentro un blocco: come un interruttore a più posizioni. */
@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = S.c
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.card).padding(5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Box(
                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(18.dp)).background(if (on) c.ink else Color.Transparent)
                    .clickableNoRipple { onSelect(i) }.padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Su due righe se serve ("Tinta unita", "Del telefono"): mai troncate
                Text(o, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 14.sp, lineHeight = 16.sp, color = if (on) c.bg else c.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Swatches(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(S.c.card).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

/** Un tondo di colore. [ring]: un bordo sottile, per i colori vicini al fondo; [plus]: apre il selettore. */
@Composable
private fun Swatch(color: Color?, label: String?, on: Boolean, ring: Boolean = false, plus: Boolean = false, onClick: () -> Unit) {
    val c = S.c
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(42.dp).clip(CircleShape)
                .background(color ?: if (plus) c.line else c.accent)
                .border(if (on) 3.dp else if (ring) 1.dp else 0.dp, if (on) c.ink else c.dot, CircleShape)
                .clickableNoRipple(onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (plus && color == null) Icon(Icons.Rounded.Colorize, tr(R.string.pick_color), Modifier.size(20.dp), tint = c.ink)
        }
        if (label != null) Text(label, Modifier.padding(top = 3.dp), style = TextStyle(fontSize = 12.sp, color = c.soft))
    }
}

/** Le anteprime degli sfondi, disegnate una volta sola (sono piccole: pochi KB l'una). */
private val previews = HashMap<Int, androidx.compose.ui.graphics.ImageBitmap>()
private fun presetPreview(i: Int) = previews.getOrPut(i) { Abstracts.render(i, 120, 216, grain = false).asImageBitmap() }

/** Gli sfondi di un tipo, in fila: si scorre di lato. Le anteprime si disegnano solo quando compaiono. */
@Composable
private fun PresetRow(kind: Abstracts.Kind, current: Int, onPick: (Int) -> Unit) {
    val c = S.c
    val items = remember(kind) { Abstracts.all.withIndex().filter { it.value.kind == kind }.map { it.index } }
    // Dopo una scelta la pagina si ricostruisce: la fila riparte da quello scelto, non dall'inizio
    val state = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = (items.indexOf(current) - 1).coerceAtLeast(0))
    LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) {
        items(items.size, key = { items[it] }) { k ->
            val i = items[k]
            val img = remember(i) { presetPreview(i) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    img, Abstracts.all[i].name,
                    Modifier.size(width = 64.dp, height = 116.dp).clip(RoundedCornerShape(16.dp))
                        .border(if (i == current) 3.dp else 1.dp, if (i == current) c.ink else c.line, RoundedCornerShape(16.dp))
                        .clickableNoRipple { onPick(i) },
                    contentScale = ContentScale.Crop,
                )
                Text(Abstracts.all[i].name, Modifier.padding(top = 4.dp), style = TextStyle(fontSize = 12.sp, color = if (i == current) c.ink else c.soft))
            }
        }
    }
}

/**
 * Il selettore di colore: tinta in una barra, chiarezza e intensità in un riquadro. Sopra, l'anteprima.
 * Niente cambia finché non tocchi "Fatto".
 */
@Composable
private fun ColorSheet(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val c = S.c
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var h by remember { mutableFloatStateOf(hsv[0]) }
    var s by remember { mutableFloatStateOf(hsv[1]) }
    var v by remember { mutableFloatStateOf(hsv[2]) }
    val color = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))
    val hue = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, 1f, 1f)))
    Sheet(tr(R.string.pick_color), onDismiss = onDismiss, leading = {
        Box(Modifier.size(28.dp).clip(CircleShape).background(color).border(1.dp, c.dot, CircleShape))
    }) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            // Intensità da sinistra a destra, chiarezza dal basso in alto
            Canvas(
                Modifier.fillMaxWidth().aspectRatio(1.5f).clip(RoundedCornerShape(18.dp)).pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(); down.consume()
                        fun at(o: Offset) { s = (o.x / size.width).coerceIn(0f, 1f); v = 1f - (o.y / size.height).coerceIn(0f, 1f) }
                        at(down.position)
                        drag(down.id) { it.consume(); at(it.position) }
                    }
                },
            ) {
                drawRect(Brush.horizontalGradient(listOf(Color.White, hue)))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                val p = Offset(s * size.width, (1f - v) * size.height)
                drawCircle(Color.White, 11.dp.toPx(), p, style = Stroke(3.dp.toPx()))
                drawCircle(Color.Black.copy(alpha = 0.3f), 13.dp.toPx(), p, style = Stroke(1.dp.toPx()))
            }
            Spacer(Modifier.height(16.dp))
            val hues = remember { (0..6).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f))) } }
            Canvas(
                Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(15.dp)).pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(); down.consume()
                        fun at(o: Offset) { h = (o.x / size.width).coerceIn(0f, 1f) * 360f }
                        at(down.position)
                        drag(down.id) { it.consume(); at(it.position) }
                    }
                },
            ) {
                drawRect(Brush.horizontalGradient(hues))
                val x = h / 360f * size.width
                drawCircle(Color.White, size.height / 2 - 2.dp.toPx(), Offset(x.coerceIn(size.height / 2, size.width - size.height / 2), size.height / 2), style = Stroke(3.dp.toPx()))
            }
            Spacer(Modifier.height(20.dp))
            Primary(tr(R.string.done), Modifier.fillMaxWidth()) { onPick(color.toArgb()) }
        }
    }
}

/** Solo per la prova: ogni sordina, grande, con il posto in cui compare. */
@Composable
private fun MascotGallery() {
    val c = S.c
    val full = listOf(
        Mood.HELLO to "Permessi (primo avvio)", Mood.WAIT to "Freno · attesa", Mood.READY to "Freno · puoi aprire",
        Mood.WON to "Freno · lascia", Mood.SIGH to "Freno · apri comunque", Mood.REST to "Pagina Sordina vuota",
        Mood.DONE to "Fine del primo avvio", Mood.FIX to "Impostazioni",
    )
    val lines = listOf(
        Pose.AWAKE to "Home", Pose.QUIET to "Home · modalità di Android", Pose.NIGHT to "Home · notte",
        Pose.DRIVE to "Home · auto",
    )
    run {
        // Tutti gli adesivi, vinti, per controllarli a colpo d'occhio
        app.sordina.data.Medal.entries.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                row.forEach { m ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Sticker(m, true, 104.dp)
                        Text(medalName(m), maxLines = 1, style = TextStyle(fontSize = 12.sp, color = c.soft))
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        full.forEachIndexed { i, (m, where) ->
            Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // Si ritocca per rivedere l'animazione
                var k by remember { mutableStateOf(0) }
                androidx.compose.runtime.key(k) { Mascot(m, Modifier.size(220.dp).clickableNoRipple { k++ }) }
                Text("${i + 1} · $where", style = TextStyle(fontSize = 16.sp, color = c.ink))
                Text(m.name, style = TextStyle(fontSize = 12.sp, color = c.soft))
            }
        }
        lines.forEachIndexed { i, (p, where) ->
            Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                MascotGlyph(p, c.soft, Modifier.size(200.dp))
                Text("${full.size + i + 1} · $where", style = TextStyle(fontSize = 16.sp, color = c.ink))
                Text(p.name, style = TextStyle(fontSize = 12.sp, color = c.soft))
            }
        }
    }
}

/** La pagina sul nome: la sordina del violino, l'idea che ne viene, i principi detti in una riga. */
@Composable
private fun WhySordina() {
    val c = S.c
    val serif = S.style.serif
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Mascot(Mood.REST, Modifier.size(160.dp))
        Text(
            tr(R.string.why_tagline), Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            style = TextStyle(fontFamily = serif, fontSize = 21.sp, lineHeight = 28.sp, color = c.ink, textAlign = TextAlign.Center),
        )
    }
    // La parola, come in un dizionario
    WhyCard(tr(R.string.why_word_t)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("sordina", style = TextStyle(fontFamily = serif, fontSize = 34.sp, color = c.ink))
            Spacer(Modifier.width(10.dp))
            Text("pp", Modifier.padding(bottom = 5.dp),
                style = TextStyle(fontFamily = serif, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = c.accent))
        }
        Text(tr(R.string.why_word_pron), Modifier.padding(top = 2.dp, bottom = 12.dp),
            style = TextStyle(fontFamily = serif, fontStyle = FontStyle.Italic, fontSize = 14.sp, color = c.soft))
        WhyBody(tr(R.string.why_word))
    }
    WhyCard(tr(R.string.why_idea_t)) { WhyBody(tr(R.string.why_idea)) }
    Topic(tr(R.string.why_rules_t))
    Group {
        listOf(
            R.string.why_r1_t to R.string.why_r1, R.string.why_r2_t to R.string.why_r2, R.string.why_r3_t to R.string.why_r3,
            R.string.why_r4_t to R.string.why_r4, R.string.why_r5_t to R.string.why_r5, R.string.why_r6_t to R.string.why_r6,
        ).forEachIndexed { i, (t, b) ->
            Row(Modifier.fillMaxWidth().background(c.card).padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text("${i + 1}", Modifier.width(28.dp), style = TextStyle(fontFamily = serif, fontSize = 22.sp, color = c.accent))
                Column {
                    Text(tr(t), style = TextStyle(fontSize = 16.sp, color = c.ink))
                    Text(tr(b), Modifier.padding(top = 2.dp), style = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, color = c.soft))
                }
            }
        }
    }
    Text(
        tr(R.string.why_end), Modifier.fillMaxWidth().padding(top = 32.dp),
        style = TextStyle(fontFamily = serif, fontStyle = FontStyle.Italic, fontSize = 18.sp, color = c.soft, textAlign = TextAlign.Center),
    )
}

@Composable
private fun WhyCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(S.c.card).padding(20.dp)) {
        Text(title.uppercase(), Modifier.padding(bottom = 10.dp), style = TextStyle(fontSize = 12.sp, letterSpacing = 1.5.sp, color = S.c.accent))
        content()
    }
}

@Composable
private fun WhyBody(text: String) =
    Text(text, style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, color = S.c.ink))
