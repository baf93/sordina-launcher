package app.sordina.ui

import app.sordina.data.Backup
import app.sordina.ui.theme.autoAccent
import app.sordina.data.Prefs
import androidx.compose.ui.text.style.TextOverflow
import app.sordina.data.AppIcons
import app.sordina.service.Talking
import androidx.compose.material.icons.rounded.Forum

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
import app.sordina.data.RuleScope
import app.sordina.data.RuleTarget
import app.sordina.data.Rules
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
    PEOPLE("NOTIF"), ALWAYS("NOTIF"), RULES("NOTIF"), ORDER("HOME"), LEVELS("RULES"),
    BRAKED("BRAKE"), HIDDEN("BRAKE"), MASCOTS("HOME"), PINNED("SEARCH"), EXCLUDED("SEARCH"), HA("MAIN"), CAR_APPS("HOME"), PERMS("GENERAL"), CREDITS("GENERAL"), LANG("GENERAL"),
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
    /** Si apre già sugli adesivi (dal riquadro di un adesivo appena vinto) */
    stickers: Boolean = false,
    hiddenApps: @Composable () -> Unit = {},
) {
    var sub by rememberSaveable { mutableStateOf(if (stickers) Sub.PROGRESS else Sub.MAIN) }
    // Solo all'apertura: poi la pagina si comporta come sempre
    val jumpToStickers = remember { stickers }
    var tick by remember { mutableIntStateOf(0) }
    // Ogni pagina ricorda dove eri: tornando indietro, sei lì
    val scrolls = remember { HashMap<Sub, ScrollState>() }
    fun scrollOf(s: Sub) = scrolls.getOrPut(s) { ScrollState(0) }
    fun change(block: () -> Unit) { block(); tick++; ops.changed() }
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
                    TopicCard(tr(R.string.perm_home), tr(R.string.t_home_d), Icons.Rounded.Home) { sub = Sub.HOME }
                    TopicCard(tr(R.string.s_brake), tr(R.string.t_brake_d), Icons.Rounded.PanTool) { sub = Sub.BRAKE }
                    TopicCard(tr(R.string.s_search), tr(R.string.t_search_d), Icons.Rounded.Search) { sub = Sub.SEARCH }
                    TopicCard(tr(R.string.s_ha), tr(R.string.t_ha_d), Icons.Rounded.Lightbulb) { sub = Sub.HA }
                    TopicCard(tr(R.string.t_themes), tr(R.string.t_themes_d), Icons.Rounded.Palette) { sub = Sub.THEMES }
                    TopicCard(tr(R.string.t_general), tr(R.string.t_general_d), Icons.Rounded.Tune) { sub = Sub.GENERAL }
                }

                // ── Notifiche: cosa passa subito e cosa aspetta
                Sub.NOTIF -> Page(tr(R.string.perm_notifications), null, back, scrollOf(page)) {
                    // Persone e app, divise per come vanno: chi passa sempre e chi resta in sordina. Tocchi un gettone e cambi.
                    val refresh = { tick++; ops.changed(); SordinaListener.reapply(ctx) }
                    Topic(tr(R.string.cat_people))
                    Choices(app, apps, Kind.PEOPLE, { sub = Sub.PEOPLE }, null, refresh)
                    Topic(tr(R.string.kind_apps))
                    Choices(app, apps, Kind.APPS, { sub = Sub.ALWAYS }, { sub = Sub.RULES }, refresh)
                    // Le scorciatoie intelligenti: valgono per tutti
                    Topic(tr(R.string.n_also))
                    Group {
                        Toggle(tr(R.string.s_urgent), prefs.urgentEnabled, null, Icons.Rounded.PriorityHigh) { v -> change { prefs.urgentEnabled = v } }
                        Toggle(tr(R.string.s_talk), prefs.talkPass, null, Icons.Rounded.Forum) { v -> change { prefs.talkPass = v; if (!v) Talking.clear() } }
                        Toggle(tr(R.string.s_system), prefs.systemPass, null, Icons.Rounded.Settings) { v -> change { prefs.systemPass = v } }
                    }
                }

                // ── Home: come cambia durante il giorno e cosa sa
                Sub.HOME -> Page(tr(R.string.perm_home), null, back, scrollOf(page)) {
                    // Cosa c'è in Home: ognuna si spegne con un tocco
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
                        if (prefs.homeAgenda && perms.calendar) CalendarPick(app, ops)
                        Toggle(tr(R.string.n_cards), prefs.homeCards, null, Icons.Rounded.AutoAwesome) { v -> change { prefs.homeCards = v } }
                        // Solo nella versione di prova: tutti i disegni, uno sotto l'altro
                        if ((ctx.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) Line("Tutte le sordine", "", null, Icons.Rounded.Collections) { sub = Sub.MASCOTS }
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
                    // I momenti: cosa sono, detto in una riga
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
                        Toggle(tr(R.string.s_scene_car), prefs.homeCar, null, Icons.Rounded.DirectionsCar) { v -> change { prefs.homeCar = v } }
                        if (prefs.homeCar) Toggle(tr(R.string.n_car_calls_only), prefs.carQuiet && perms.policy, if (perms.policy) null else tr(R.string.s_car_quiet_access), Icons.Rounded.Call) { v ->
                            if (v && !perms.policy) runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            change { prefs.carQuiet = v }
                        }
                    }
                    if (prefs.homeCar) {
                        // Fino a tre app, in una riga sola: con meno app i pulsanti si allargano
                        val car = ops.carApps()
                        Topic(tr(R.string.n_car_shortcuts), tr(R.string.n_car_shortcuts_hint))
                        AppKeys(car, apps, byPackage = true, sorted = false, onRemove = { k -> change { prefs.carApps = car - k } },
                            onMove = { i, j -> change { val l = car.toMutableList(); l[i] = l[j].also { l[j] = l[i] }; prefs.carApps = l } },
                            add = if (car.size < 3) tr(R.string.n_add_app) else null) { sub = Sub.CAR_APPS }
                    }
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
                            onConfirm = { app.routines.wipe(); onWipe() }, onDismiss = { ask = false })
                    }
                    Topic(tr(R.string.s_info))
                    Group {
                        Line(tr(R.string.s_version), version, icon = Icons.Rounded.Info)
                        Line(tr(R.string.s_source), "GitHub", null, Icons.Rounded.Code) { ops.openUrl(REPO) }
                        Line(tr(R.string.n_credits), icon = Icons.Rounded.Favorite) { sub = Sub.CREDITS }
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
                Sub.PEOPLE -> Page(tr(R.string.s_important), tr(R.string.ob_people_sub), back, scrollOf(page)) {
                    Spacer(Modifier.height(8.dp))
                    ImportantPicker(app, perms, ops)
                }
                Sub.ALWAYS -> AppChooser(tr(R.string.s_always_apps), null, apps,
                    selected = { prefs.appLevels.filterValues { it == Level.SEMPRE }.keys }, back) { pkg, on ->
                    prefs.setAppLevel(pkg, if (on) Level.SEMPRE else null); ops.changed()
                }
                Sub.BRAKED -> AppChooser(tr(R.string.s_braked_apps), null, apps, selected = { prefs.braked }, back) { pkg, on ->
                    prefs.braked = if (on) prefs.braked + pkg else prefs.braked - pkg; ops.changed()
                }
                Sub.CAR_APPS -> AppChooser(tr(R.string.n_car_shortcuts), tr(R.string.n_car_shortcuts_hint), apps, selected = { ops.carApps().toSet() }, back) { pkg, on ->
                    val now = ops.carApps()
                    prefs.carApps = if (on) (if (now.size < 3) now + pkg else now) else now - pkg
                    ops.changed()
                    // Arrivati a tre si torna indietro da soli
                    if (on && now.size >= 2) back()
                }
                Sub.LEVELS -> LevelsPage(app, apps, back)
                Sub.RULES -> RulesPage(app, apps, onChange = { ops.changed() }, onApps = { sub = Sub.LEVELS }, onBack = back)
                Sub.ORDER -> Page(tr(R.string.s_your_order), null, back, scrollOf(page)) {
                    val order = Categories.custom(prefs.categoryOrder)
                    Spacer(Modifier.height(8.dp))
                    Group {
                        order.forEachIndexed { i, cat ->
                            fun move(d: Int) = change {
                                val l = order.toMutableList(); val j = i + d
                                if (j in l.indices) { l[i] = l[j].also { l[j] = l[i] }; prefs.categoryOrder = l.map { it.name } }
                            }
                            Line("${i + 1}.  ${cat.label}", trailing = {
                                Row {
                                    RoundIcon(Icons.Rounded.KeyboardArrowUp, tr(R.string.up), i > 0) { move(-1) }
                                    Spacer(Modifier.width(8.dp))
                                    RoundIcon(Icons.Rounded.KeyboardArrowDown, tr(R.string.down), i < order.lastIndex) { move(1) }
                                }
                            })
                        }
                    }
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
                Sub.PROGRESS -> Page(tr(R.string.t_progress), null, back, scrollOf(page)) { ProgressPage(app.habits, jump = jumpToStickers) }
                Sub.HA -> Page(tr(R.string.s_ha), null, back, scrollOf(page)) {
                    HaSettings(app, ops) { tick++ }
                }
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
                        PermLine(tr(R.string.perm_location), tr(R.string.h_p_location), granted(Manifest.permission.ACCESS_FINE_LOCATION), Icons.Rounded.Wifi) { ops.request(Manifest.permission.ACCESS_FINE_LOCATION) }
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

private enum class Kind { PEOPLE, APPS }

/** Una scelta salvata: una persona (importante o con una regola), un'app, un tipo di notifica di un'app. */
private class Choice(val name: String, val pkg: String?, val passes: Boolean, val important: Boolean, val key: String, val set: (Level?) -> Unit)

/**
 * Persone o app, divise per come vanno: chi passa sempre e chi resta in sordina. Ogni voce è un gettone;
 * lo tocchi e scegli fra le due, o lo togli. "Silenziosa", di chi l'aveva già, sta con chi passa.
 * I tipi di notifica di un'app stanno con le app, col nome del tipo accanto.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Choices(app: SordinaApp, apps: List<AppEntry>, kind: Kind, onAdd: () -> Unit, onAddFromNotif: (() -> Unit)?, onChange: () -> Unit) {
    val prefs = app.prefs
    val names = prefs.ruleNames
    fun appName(pkg: String) = apps.firstOrNull { it.packageName == pkg }?.label ?: pkg
    val list = buildList {
        when (kind) {
            Kind.PEOPLE -> {
                prefs.importantNames.forEach { n ->
                    add(Choice(n.replaceFirstChar { it.uppercase() }, null, true, true, n) { lv ->
                        if (lv == null) app.people.removeImportantName(n)
                        else if (lv == Level.RIEPILOGO) { app.people.removeImportantName(n); prefs.personRules = prefs.personRules + (n to lv) }
                    })
                }
                prefs.personRules.forEach { (k, v) ->
                    add(Choice(names[k] ?: k.replaceFirstChar { it.uppercase() }, null, v != Level.RIEPILOGO, false, k) { lv ->
                        prefs.personRules = if (lv == null) prefs.personRules - k else prefs.personRules + (k to lv)
                    })
                }
            }
            Kind.APPS -> {
                prefs.appLevels.forEach { (k, v) -> add(Choice(appName(k), k, v != Level.RIEPILOGO, false, k) { lv -> prefs.setAppLevel(k, lv) }) }
                prefs.channelRules.forEach { (k, v) ->
                    val pkg = k.substringBefore('|')
                    add(Choice(names[k] ?: (appName(pkg) + " · " + k.substringAfter('|')), pkg, v != Level.RIEPILOGO, false, k) { lv ->
                        prefs.channelRules = if (lv == null) prefs.channelRules - k else prefs.channelRules + (k to lv)
                    })
                }
            }
        }
    }.sortedBy { it.name.lowercase() }
    var editing by remember { mutableStateOf<Choice?>(null) }
    val inGroups = prefs.importantInGroups
    for (pass in listOf(true, false)) {
        val mine = list.filter { it.passes == pass }
        ChipBox(if (pass) tr(R.string.r_pass_title) else tr(R.string.lv_summary)) {
            if (mine.isEmpty()) Text(tr(R.string.r_empty), Modifier.padding(vertical = 6.dp), style = TextStyle(fontSize = 15.sp, color = S.c.soft))
            mine.forEach { w -> RuleChip(w.name, w.pkg, grouped = w.important && w.key in inGroups) { editing = w } }
            // Si aggiunge sempre da chi passa: per la sordina c'è già tutto il resto
            if (pass) {
                AddChip(if (kind == Kind.PEOPLE) tr(R.string.n_add_person) else tr(R.string.n_add_app), onAdd)
                onAddFromNotif?.let { AddChip(tr(R.string.n_add_from_notif), it) }
            }
        }
    }
    editing?.let { w ->
        Sheet(w.name, onDismiss = { editing = null }) {
            SheetRow(tr(R.string.lv_always), if (w.passes) Icons.Rounded.Check else null, accent = w.passes) {
                if (!w.passes) w.set(Level.SEMPRE)
                editing = null; onChange()
            }
            SheetRow(tr(R.string.lv_summary), if (!w.passes) Icons.Rounded.Check else null, accent = !w.passes) {
                if (w.passes) w.set(Level.RIEPILOGO)
                editing = null; onChange()
            }
            if (w.important) {
                val on = w.key in prefs.importantInGroups
                Toggle(tr(R.string.n_in_groups), on, null, Icons.Rounded.Groups) { v ->
                    prefs.importantInGroups = if (v) prefs.importantInGroups + w.key else prefs.importantInGroups - w.key
                    editing = null; onChange()
                }
            }
            SheetRow(tr(R.string.remove), Icons.Rounded.DeleteOutline) { w.set(null); editing = null; onChange() }
        }
    }
}

/** Un riquadro con un titolo piccolo e i gettoni che vanno a capo. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ChipBox(title: String, content: @Composable () -> Unit) {
    val c = S.c
    Column(Modifier.padding(vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.card).padding(14.dp)) {
        Text(title, Modifier.padding(start = 4.dp, bottom = 10.dp), style = TextStyle(fontSize = 14.sp, color = c.soft))
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }
}

/** Un gettone: l'icona dell'app o l'iniziale della persona, e il nome. */
@Composable
private fun RuleChip(name: String, pkg: String?, grouped: Boolean, onClick: () -> Unit) {
    val c = S.c
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(c.line).clickable(onClick = onClick).padding(start = 5.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pkg != null) Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { AppDot(pkg) }
        else Box(Modifier.size(28.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Text(name.take(1).uppercase(), style = TextStyle(fontSize = 13.sp, color = c.accent))
        }
        Spacer(Modifier.width(8.dp))
        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 15.sp, color = c.ink))
        if (grouped) Icon(Icons.Rounded.Groups, tr(R.string.n_in_groups), Modifier.padding(start = 6.dp).size(16.dp), tint = c.soft)
    }
}

@Composable
private fun AddChip(label: String, onClick: () -> Unit) {
    val c = S.c
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).border(1.dp, c.dot, RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Add, null, Modifier.size(16.dp), tint = c.accent)
        Spacer(Modifier.width(6.dp))
        Text(label, style = TextStyle(fontSize = 14.sp, color = c.accent))
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

/** Quanto è in fondo una pagina: serve a capire se stai entrando o tornando. */
private fun depth(s: Sub): Int = s.parent?.let { 1 + depth(Sub.valueOf(it)) } ?: 0

/** Una pagina: la testata uguale al resto dell'app (freccia, titolo grande, la frase che dice a cosa serve) e sotto il contenuto che scorre. */
@Composable
private fun Page(title: String, subtitle: String?, onBack: () -> Unit, scroll: ScrollState, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 16.dp)) {
        Header(title, onBack, subtitle = subtitle)
        content()
        Spacer(Modifier.height(48.dp))
    }
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
        Header(title, onBack, subtitle = about)
        Field(q, { q = it }, tr(R.string.s_search_app), Modifier.padding(horizontal = 4.dp))
        Box(Modifier.weight(1f).padding(start = 4.dp)) {
            PickList(installed, { it.packageName }, { it.label }, q, leading = { a -> AppDot(a.packageName) }, selected = chosen, onScrolled = {}) { a ->
                onToggle(a.packageName, a.packageName !in chosen)
                chosen = selected()
            }
        }
    }
}

@Composable
private fun PermLine(label: String, why: String, ok: Boolean, icon: ImageVector, onAsk: () -> Unit) {
    if (ok) Line(label, tr(R.string.yes), why, icon) else Line(label, tr(R.string.grant), why, icon, onClick = onAsk)
}

/** Il calendario in cui scrivere gli impegni, da un foglio. */
@Composable
private fun CalendarPick(app: SordinaApp, ops: Ops) {
    var cals by remember { mutableStateOf(emptyList<app.sordina.data.Cal>()) }
    var sel by remember { mutableStateOf(app.prefs.calendarId) }
    LaunchedEffect(Unit) { cals = withContext(Dispatchers.IO) { app.calendars.list() } }
    if (cals.isEmpty()) { Line(tr(R.string.s_events_go), tr(R.string.none_m), null, Icons.Rounded.CalendarToday); return }
    val current = cals.firstOrNull { it.id == sel } ?: cals.first()
    val dup = { n: String -> cals.count { it.name == n } > 1 }
    Pick(
        tr(R.string.s_events_go), current.name,
        cals.map { Option(it.name, if (dup(it.name)) it.account else null) }, { cals[it].id == current.id },
        icon = Icons.Rounded.CalendarToday, title = tr(R.string.s_events_go),
    ) { sel = cals[it].id; app.prefs.calendarId = sel; ops.changed() }
}

/** I calendari di Home Assistant da mostrare in Casa, insieme a quello del telefono. */
@Composable
internal fun HaCalendars(app: SordinaApp, ops: Ops) {
    val prefs = app.prefs
    var list by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var chosen by remember { mutableStateOf(prefs.haCalendars) }
    LaunchedEffect(Unit) { list = withContext(Dispatchers.IO) { app.ha.calendars() } }
    Topic(tr(R.string.s_ha_calendars))
    when {
        list == null -> Hint(tr(R.string.loading), Modifier.padding(start = 12.dp))
        list!!.isEmpty() -> Hint(tr(R.string.s_no_calendar), Modifier.padding(start = 12.dp))
        else -> Group {
            list!!.forEach { (id, name) ->
                Toggle(name, id in chosen, icon = Icons.Rounded.CalendarToday) { v ->
                    chosen = if (v) chosen + id else chosen - id
                    prefs.haCalendars = chosen
                    app.ha.resetCalendarCache()
                    ops.changed()
                }
            }
        }
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

/** Cosa passa subito, app per app. Tocco: un foglio con i livelli. In cima le app che hanno scritto oggi. */
@Composable
private fun LevelsPage(app: SordinaApp, apps: List<AppEntry>, onBack: () -> Unit) {
    val prefs = app.prefs
    var levels by remember { mutableStateOf(prefs.appLevels) }
    var q by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<AppEntry?>(null) }
    val todayPkgs = remember { app.store.today.value.groupBy { it.pkg }.mapValues { it.value.size } }
    val pkgs = remember(apps) { apps.distinctBy { it.packageName } }
    val nq = Search.normalize(q)
    val list = pkgs.filter { nq.isEmpty() || it.normalized.contains(nq) }
        .sortedWith(compareByDescending<AppEntry> { todayPkgs[it.packageName] ?: 0 }.thenByDescending { if (it.packageName in levels) 1 else 0 })
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 16.dp, bottom = 48.dp)) {
        item(key = "head") {
            Header(tr(R.string.s_apps_pass), onBack)
            Field(q, { q = it }, tr(R.string.s_search_app), Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
        }
        itemsIndexed(list, key = { _, a -> a.key }) { i, a ->
            ListCell(i, list.size) {
                Line(a.label, levels[a.packageName]?.label ?: tr(R.string.s_order_auto), todayPkgs[a.packageName]?.let { trn(R.plurals.n_today, it) }) { editing = a }
            }
        }
    }
    editing?.let { a ->
        val opts = listOf<Pair<Level?, Option>>(
            null to Option(tr(R.string.s_order_auto), tr(R.string.lv_auto_hint)),
            Level.SEMPRE to Option(tr(R.string.lv_always), tr(R.string.lv_always_hint)),
            Level.RIEPILOGO to Option(tr(R.string.lv_summary), tr(R.string.lv_summary_hint)),
        )
        Sheet(a.label, onDismiss = { editing = null }) {
            opts.forEach { (lv, o) ->
                val on = levels[a.packageName] == lv
                Row(
                    Modifier.fillMaxWidth().clickable {
                        prefs.setAppLevel(a.packageName, lv); levels = prefs.appLevels; editing = null
                    }.padding(horizontal = 24.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(o.label, style = TextStyle(fontSize = 17.sp, color = if (on) S.c.accent else S.c.ink))
                        Text(o.hint.orEmpty(), style = TextStyle(fontSize = 13.sp, color = S.c.soft))
                    }
                    if (on) Icon(Icons.Rounded.Check, null, tint = S.c.accent)
                }
            }
        }
    }
}

/** Le regole scelte da te: per tipo di notifica, persona o gruppo, app. Si aggiungono dalla pagina Sordina o dalla tendina qui sotto. */
@Composable
private fun RulesPage(app: SordinaApp, apps: List<AppEntry>, onChange: () -> Unit, onApps: () -> Unit, onBack: () -> Unit) {
    val prefs = app.prefs
    val ctx = LocalContext.current
    // Quello che c'è adesso nella tendina: si tocca per dargli una regola (es. la notifica fissa di un'app)
    var tray by remember { mutableStateOf(SordinaListener.tray()) }
    var ruleFor by remember { mutableStateOf<RuleTarget?>(null) }
    // Niente giri: la lista si rilegge quando arriva o sparisce una notifica
    val trayVersion by SordinaListener.trayChanges.collectAsState()
    LaunchedEffect(trayVersion) { tray = SordinaListener.tray() }
    val icons = remember { HashMap<String, ImageBitmap?>() }
    fun icon(pkg: String) = icons.getOrPut(pkg) {
        AppIcons.forPackage(ctx.packageManager, pkg, 96)
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 16.dp, bottom = 48.dp)) {
        item(key = "head") {
            // Le notifiche di adesso: ne tocchi una e scegli come deve avvisarti (lei, chi l'ha mandata o tutta l'app)
            Header(tr(R.string.n_add_from_notif), onBack)
        }
        item(key = "tray_h") { Topic(tr(R.string.tray), if (tray.isEmpty()) tr(R.string.tray_none) else null) }
        itemsIndexed(tray, key = { _, t -> "t_" + (t.key ?: t.title) }) { _, t ->
            TrayRow(t, t.scopes.firstNotNullOfOrNull { Rules.get(prefs, t, it) }, icon(t.pkg)) { ruleFor = t }
        }
    }
    ruleFor?.let { t ->
        RuleSheet(t, { x, s -> Rules.get(prefs, x, s) }, { x, s, lv ->
            Rules.set(prefs, x, s, lv)
            SordinaListener.reapply(ctx, x.pkg)
            onChange()
        }, onDismiss = { ruleFor = null })
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
        // Auto: il colore dello sfondo; mostra quello che si otterrebbe
        val isAuto = accent == 0 || accent == 1
        val autoColor = autoAccent(backdropAccent)?.let { Color(it) } ?: if (isAuto) c.accent else null
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
