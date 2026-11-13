package app.sordina.ui

import app.sordina.R
import app.sordina.tr
import app.sordina.trn
import app.sordina.Lang

import android.Manifest
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sordina.SordinaApp
import app.sordina.data.AppEntry
import app.sordina.data.CarApps
import app.sordina.data.Level
import app.sordina.data.LockPrivacy
import app.sordina.data.Moments
import app.sordina.data.Search
import app.sordina.ui.theme.Abstracts
import app.sordina.ui.theme.S
import app.sordina.data.Categories
import androidx.compose.material3.Icon
import app.sordina.ui.theme.ThemeFamily
import app.sordina.ui.theme.ThemeMode


/** Le sottopagine: solo per le liste vere (persone, app, sfondi, Home Assistant). */
private enum class Sub { MAIN, HOURS, PEOPLE, LEVELS, BRAKED, ORDER, PINNED, EXCLUDED, HA, PERMS, LOOK }

private fun hm(min: Int) = Moments.fmt(min)
/** "1 h", "1 h 30" */
private fun span(min: Int) = if (min % 60 == 0) "${min / 60} h" else "${min / 60} h ${min % 60}"

/**
 * Impostazioni. Tre regole: un interruttore accende o spegne; una riga con › apre una lista;
 * una riga con un valore apre un foglio con le opzioni, e niente cambia finché non scegli.
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
) {
    var sub by rememberSaveable { mutableStateOf(Sub.MAIN) }
    var tick by remember { mutableIntStateOf(0) }
    // Fuori da key(tick): un interruttore ricostruisce la pagina ma non deve riportarla in cima.
    // Resta viva anche nelle sottopagine, così tornando indietro sei dove eri.
    val mainScroll = rememberScrollState()
    val haScroll = rememberScrollState()
    fun change(block: () -> Unit) { block(); tick++; ops.changed() }
    BackHandler(sub != Sub.MAIN) { sub = Sub.MAIN }
    val prefs = app.prefs
    val back = { sub = Sub.MAIN }

    if (sub != Sub.MAIN) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp, vertical = 16.dp)) {
            when (sub) {
                Sub.HOURS -> {
                    Header(tr(R.string.s_summaries), back)
                    Hint(tr(R.string.ob5_sub))
                    SummaryHours(app, ops, Modifier.weight(1f).padding(top = 8.dp))
                }
                Sub.PEOPLE -> {
                    Header(tr(R.string.s_important), back)
                    Hint(tr(R.string.s_important_hint))
                    Column(Modifier.verticalScroll(rememberScrollState())) { ImportantPicker(app, perms, ops) }
                }
                Sub.LEVELS -> LevelsPage(app, apps, back)
                Sub.BRAKED -> BrakedPage(app, apps, onChange = { ops.changed() }, onBack = back)
                Sub.ORDER -> key(tick) {
                    Header(tr(R.string.s_your_order), back)
                    Hint(tr(R.string.s_use_arrows))
                    val order = Categories.custom(prefs.categoryOrder)
                    Group {
                        order.forEachIndexed { i, cat ->
                            fun move(d: Int) = change {
                                val l = order.toMutableList(); val j = i + d
                                if (j in l.indices) { l[i] = l[j].also { l[j] = l[i] }; prefs.categoryOrder = l.map { it.name } }
                            }
                            Line("${i + 1}.  ${cat.label}", trailing = {
                                androidx.compose.foundation.layout.Row {
                                    RoundIcon(Icons.Rounded.KeyboardArrowUp, tr(R.string.up), i > 0) { move(-1) }
                                    Spacer(Modifier.width(8.dp))
                                    RoundIcon(Icons.Rounded.KeyboardArrowDown, tr(R.string.down), i < order.lastIndex) { move(1) }
                                }
                            })
                        }
                    }
                }
                Sub.PINNED, Sub.EXCLUDED -> key(tick) {
                    val pinned = sub == Sub.PINNED
                    Header(if (pinned) tr(R.string.s_pinned) else tr(R.string.s_excluded), back)
                    Hint(if (pinned) tr(R.string.s_always_suggested) else tr(R.string.s_never_suggested))
                    val byKey = apps.associateBy { it.key }
                    val keys = if (pinned) prefs.pinned else prefs.excluded.toList()
                    Group {
                        keys.forEach { k ->
                            Line(byKey[k]?.label ?: k, trailing = {
                                SmallPill(tr(R.string.remove)) {
                                    change { if (pinned) prefs.pinned = prefs.pinned - k else prefs.excluded = prefs.excluded - k }
                                    if (keys.size == 1) sub = Sub.MAIN
                                }
                            })
                        }
                    }
                }
                Sub.HA -> key(tick) {
                    Header(tr(R.string.s_ha), back)
                    Column(Modifier.verticalScroll(haScroll)) {
                        HaForm(app, ops)
                        if (app.ha.configured) {
                            HaCalendars(app, ops)
                            Section(" ")
                            var ask by remember { mutableStateOf(false) }
                            Group { Line(tr(R.string.disconnect), icon = Icons.Rounded.LinkOff) { ask = true } }
                            if (ask) Confirm(tr(R.string.s_ha_disc_title), tr(R.string.s_ha_disc_text), tr(R.string.disconnect),
                                onConfirm = { change { app.ha.setToken(""); prefs.haUrl = ""; prefs.haStatusEntity = ""; prefs.haCalendars = emptySet() } },
                                onDismiss = { ask = false })
                        }
                        Spacer(Modifier.height(40.dp))
                    }
                }
                Sub.PERMS -> key(tick) {
                    Header(tr(R.string.s_perms), back)
                    Hint(tr(R.string.s_tap_grant))
                    Group {
                        PermLine(tr(R.string.perm_home), perms.home, Icons.Rounded.Home) { ops.openHomeSettings() }
                        PermLine(tr(R.string.perm_notifications), perms.listener, Icons.Rounded.Notifications) { ops.openListenerSettings() }
                        PermLine(tr(R.string.perm_usage), perms.usage, Icons.Rounded.BarChart) { ops.openUsageSettings() }
                        PermLine(tr(R.string.perm_contacts), perms.contacts, Icons.Rounded.Contacts) { ops.request(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS) }
                        PermLine(tr(R.string.perm_mic), perms.mic, Icons.Rounded.Mic) { ops.request(Manifest.permission.RECORD_AUDIO) }
                        PermLine(tr(R.string.perm_calls), perms.call, Icons.Rounded.Call) { ops.request(Manifest.permission.CALL_PHONE) }
                        PermLine(tr(R.string.s_calendar), perms.calendar, Icons.Rounded.CalendarToday) { ops.request(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR) }
                    }
                }
                Sub.LOOK -> {
                    Header(tr(R.string.s_look), back)
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Section(tr(R.string.s_wallpaper))
                        FlowPills(listOf(tr(R.string.wp_solid), tr(R.string.wp_phone), tr(R.string.wp_photo)), { it == backdrop }) { i ->
                            if (i == 2) ops.pickWallpaper() else { prefs.wallpaperMode = i; ops.backdropChanged() }
                        }
                        Abstracts.Kind.entries.forEach { kind ->
                            Section(kind.label)
                            PresetPicker(kind, if (backdrop == 3) prefs.wallpaperPreset else -1) { i -> prefs.wallpaperPreset = i; prefs.wallpaperMode = 3; ops.backdropChanged() }
                        }
                        if (backdrop != 0) {
                            Section(" ")
                            Group {
                                var g by remember { mutableStateOf(prefs.glass) }
                                Toggle(tr(R.string.s_glass), g, tr(R.string.s_glass_hint)) { v -> g = v; prefs.glass = v; ops.backdropChanged() }
                            }
                        }
                        Section(tr(R.string.s_color))
                        AccentPicker(accent, backdropAccent) { v -> prefs.accent = v; ops.backdropChanged() }
                        Spacer(Modifier.height(40.dp))
                    }
                }
                Sub.MAIN -> Unit
            }
        }
        return
    }

    key(tick) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(mainScroll)
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Header(tr(R.string.s_settings), onBack)
            val ctx = androidx.compose.ui.platform.LocalContext.current
            fun granted(p: String) = ctx.checkSelfPermission(p) == android.content.pm.PackageManager.PERMISSION_GRANTED

            // ── Sordina
            Section(tr(R.string.s_sordina))
            Group {
                Line(tr(R.string.s_important), "${prefs.importantNames.size}", icon = Icons.Rounded.FavoriteBorder) { sub = Sub.PEOPLE }
                Line(tr(R.string.s_apps_pass), "${prefs.appLevels.size}", icon = Icons.Rounded.Bolt) { sub = Sub.LEVELS }
                // Riepiloghi e notte si scelgono sul quadrante, come nella prima configurazione
                val times = prefs.summaryTimes.joinToString(" · ") { if (it % 60 == 0) "${it / 60}" else hm(it) }
                Line(tr(R.string.s_summaries), times, if (prefs.nightEnabled) "${tr(R.string.s_night)} ${hm(prefs.nightStart)} – ${hm(prefs.nightEnd)}" else null, Icons.Rounded.Schedule) { sub = Sub.HOURS }
                Toggle(tr(R.string.s_car), prefs.carEnabled, tr(R.string.s_calls_only), Icons.Rounded.DirectionsCar) { v -> change { prefs.carEnabled = v } }
                if (perms.policy) Toggle(tr(R.string.s_dnd), prefs.silenceManaged, tr(R.string.s_dnd_hint), Icons.Rounded.DoNotDisturbOn) { v ->
                    change { if (v) app.silence.enable() else app.silence.disable() }
                }
                Toggle(tr(R.string.s_system), prefs.systemPass, if (prefs.systemPass) tr(R.string.s_system_on) else tr(R.string.s_system_off), Icons.Rounded.Settings) { v -> change { prefs.systemPass = v } }
                val orders = listOf(
                    Option(tr(R.string.s_order_auto), tr(R.string.s_order_auto_hint)),
                    Option(tr(R.string.s_order_arrival), tr(R.string.s_order_arrival_hint)),
                    Option(tr(R.string.s_order_yours), tr(R.string.s_order_yours_hint)),
                )
                Pick(tr(R.string.s_order), orders[prefs.sordinaOrder.coerceIn(0, 2)].label, orders, { it == prefs.sordinaOrder }, icon = Icons.Rounded.Sort, title = tr(R.string.s_order_title)) { i ->
                    change { prefs.sordinaOrder = i }
                    if (i == 2) sub = Sub.ORDER
                }
                if (prefs.sordinaOrder == 2) Line(tr(R.string.s_your_order), icon = Icons.Rounded.Reorder) { sub = Sub.ORDER }
            }

            // ── Casa cambia: sera, notte, auto
            Section(tr(R.string.s_scenes))
            Group {
                val evening = app.moments.eveningStart()
                Toggle(tr(R.string.s_scene_evening), prefs.homeEvening,
                    if (prefs.nightEnabled) tr(R.string.s_scene_evening_hint, hm(evening)) else tr(R.string.s_scene_evening_off),
                    Icons.Rounded.WbTwilight) { v -> change { prefs.homeEvening = v } }
                if (prefs.homeEvening && prefs.nightEnabled) {
                    val lengths = listOf(60, 90, 120, 180)
                    Pick(tr(R.string.s_scene_evening_len), span(prefs.eveningMinutes), lengths.map { Option(span(it)) },
                        { lengths[it] == prefs.eveningMinutes }, icon = Icons.Rounded.Schedule, title = tr(R.string.s_scene_evening_len)) { change { prefs.eveningMinutes = lengths[it] } }
                }
                Toggle(tr(R.string.s_scene_night), prefs.homeNight,
                    if (prefs.nightEnabled) tr(R.string.s_scene_night_hint) else tr(R.string.s_scene_evening_off),
                    Icons.Rounded.Bedtime) { v -> change { prefs.homeNight = v } }
                Toggle(tr(R.string.s_scene_car), prefs.homeCar, tr(R.string.s_scene_car_hint), Icons.Rounded.DirectionsCar) { v -> change { prefs.homeCar = v } }
                if (prefs.homeCar) {
                    // I due pulsanti grandi in auto: scegli tu le app
                    val navs = remember(apps) { CarApps.navigation(ctx, apps) }
                    val music = remember(apps) { CarApps.music(ctx, apps) }
                    fun name(list: List<AppEntry>, pkg: String?) = list.firstOrNull { it.packageName == pkg }?.label ?: tr(R.string.s_car_auto)
                    Pick(tr(R.string.s_car_nav_app), name(navs, prefs.carNavApp), listOf(Option(tr(R.string.s_car_auto))) + navs.map { Option(it.label) },
                        { i -> if (i == 0) prefs.carNavApp == null else navs[i - 1].packageName == prefs.carNavApp }, icon = Icons.Rounded.Navigation) { i ->
                        change { prefs.carNavApp = if (i == 0) null else navs[i - 1].packageName }
                    }
                    Pick(tr(R.string.s_car_music_app), name(music, prefs.carMusicApp), listOf(Option(tr(R.string.s_car_auto))) + music.map { Option(it.label) },
                        { i -> if (i == 0) prefs.carMusicApp == null else music[i - 1].packageName == prefs.carMusicApp }, icon = Icons.Rounded.MusicNote) { i ->
                        change { prefs.carMusicApp = if (i == 0) null else music[i - 1].packageName }
                    }
                }
            }

            // ── Freno
            Section(tr(R.string.s_brake))
            Group {
                Line(tr(R.string.s_braked_apps), if (prefs.braked.isEmpty()) tr(R.string.none_f) else "${prefs.braked.size}", icon = Icons.Rounded.PanTool) { sub = Sub.BRAKED }
                val secs = listOf(5, 10, 20, 30, 60)
                Pick(tr(R.string.s_wait), "${prefs.brakeSeconds} s", secs.map { Option(trn(R.plurals.n_seconds, it)) }, { secs[it] == prefs.brakeSeconds }, icon = Icons.Rounded.HourglassEmpty, title = tr(R.string.s_wait_title)) { change { prefs.brakeSeconds = secs[it] } }
                Toggle(tr(R.string.s_stay), prefs.brakedStay, tr(R.string.s_stay_hint), Icons.Rounded.NotificationsOff) { v -> change { prefs.brakedStay = v } }
            }

            // ── Casa
            Section(tr(R.string.s_home))
            Group {
                val wx = granted(Manifest.permission.ACCESS_COARSE_LOCATION)
                if (wx) Line(tr(R.string.s_weather), tr(R.string.on_m), icon = Icons.Rounded.Cloud)
                else Line(tr(R.string.s_weather), tr(R.string.turn_on), icon = Icons.Rounded.Cloud) { ops.request(Manifest.permission.ACCESS_COARSE_LOCATION) }
                if (!perms.calendar) Line(tr(R.string.s_calendar), tr(R.string.turn_on), icon = Icons.Rounded.CalendarToday) { ops.request(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR) }
                else CalendarPick(app, ops)
                val loc = granted(Manifest.permission.ACCESS_FINE_LOCATION)
                if (loc) Line(tr(R.string.s_recognize_home), tr(R.string.on_m), tr(R.string.s_wifi_hint), Icons.Rounded.Wifi)
                else Line(tr(R.string.s_recognize_home), tr(R.string.turn_on), tr(R.string.s_wifi_hint), Icons.Rounded.Wifi) { ops.request(Manifest.permission.ACCESS_FINE_LOCATION) }
                Line(tr(R.string.s_ha), if (app.ha.configured) tr(R.string.connected) else tr(R.string.connect), icon = Icons.Rounded.Home) { sub = Sub.HA }
            }

            // ── Aspetto
            Section(tr(R.string.s_appearance))
            Group {
                val chosenLang = Lang.chosen(ctx)
                val langs = listOf(Option(tr(R.string.lang_phone))) + Lang.ALL.map { Option(it.second) }
                Pick(
                    tr(R.string.s_language), Lang.label(ctx) ?: tr(R.string.lang_phone), langs,
                    { i -> if (i == 0) chosenLang == null else chosenLang != null && (chosenLang.equals(Lang.ALL[i - 1].first, true) || chosenLang.startsWith(Lang.ALL[i - 1].first + "-", true)) },
                    icon = Icons.Rounded.Language,
                ) { i ->
                    Lang.choose(ctx, if (i == 0) null else Lang.ALL[i - 1].first)
                    if (android.os.Build.VERSION.SDK_INT < 33) (ctx as? android.app.Activity)?.recreate()
                }
                val themes = ThemeMode.entries.map { Option(it.label, if (it == ThemeMode.AUTO) tr(R.string.s_like_phone) else null) }
                Pick(tr(R.string.s_theme), mode.label, themes, { it == mode.ordinal }, icon = Icons.Rounded.Contrast) { onMode(ThemeMode.entries[it]) }
                Line(tr(R.string.s_look), listOf(tr(R.string.wp_solid_l), tr(R.string.wp_phone_l), tr(R.string.wp_photo_l), tr(R.string.wp_abstract_l))[backdrop.coerceIn(0, 3)], icon = Icons.Rounded.Wallpaper) { sub = Sub.LOOK }
                Toggle(tr(R.string.s_icons), prefs.appIcons, tr(R.string.s_icons_hint), Icons.Rounded.Apps) { v -> change { prefs.appIcons = v } }
                Toggle(tr(R.string.s_animations), prefs.animations, icon = Icons.Rounded.Animation) { v -> change { prefs.animations = v } }
                Toggle(tr(R.string.s_haptics), prefs.haptics, icon = Icons.Rounded.Vibration) { v -> change { prefs.haptics = v } }
            }

            // ── Ricerca
            Section(tr(R.string.s_search))
            Group {
                Toggle(tr(R.string.s_keyboard), prefs.keyboardOnOpen, icon = Icons.Rounded.Keyboard) { v -> change { prefs.keyboardOnOpen = v } }
                if (prefs.pinned.isEmpty()) Line(tr(R.string.s_pinned), tr(R.string.none_f), tr(R.string.s_hold_suggestion), Icons.Rounded.PushPin)
                else Line(tr(R.string.s_pinned), "${prefs.pinned.size}", icon = Icons.Rounded.PushPin) { sub = Sub.PINNED }
                if (prefs.excluded.isEmpty()) Line(tr(R.string.s_excluded), tr(R.string.none_f), tr(R.string.s_hold_suggestion), Icons.Rounded.Block)
                else Line(tr(R.string.s_excluded), "${prefs.excluded.size}", icon = Icons.Rounded.Block) { sub = Sub.EXCLUDED }
                val t = prefs.intentTotal
                var ask by remember { mutableStateOf(false) }
                Line(tr(R.string.s_first_hit), if (t == 0) "–" else "${prefs.intentFirst * 100 / t}%", icon = Icons.Rounded.TrackChanges)
                if (prefs.aliases.isNotEmpty() || t > 0) Line(tr(R.string.s_forget), icon = Icons.Rounded.Restore, trailing = {}) { ask = true }
                if (ask) Confirm(tr(R.string.s_forget_title), tr(R.string.s_forget_text), tr(R.string.forget),
                    onConfirm = { change { prefs.aliases = emptyMap(); prefs.intentFirst = 0; prefs.intentTotal = 0; app.brain.forgetTyping() } },
                    onDismiss = { ask = false })
            }

            // ── Altro
            Section(tr(R.string.s_other))
            Group {
                Line(tr(R.string.s_perms), icon = Icons.Rounded.Lock) { sub = Sub.PERMS }
                Line(tr(R.string.s_export), icon = Icons.Rounded.Upload, trailing = {}) { ops.exportSettings() }
                Line(tr(R.string.s_import), icon = Icons.Rounded.Download, trailing = {}) { ops.importSettings() }
                var ask by remember { mutableStateOf(false) }
                Line(tr(R.string.s_wipe), icon = Icons.Rounded.DeleteOutline, trailing = {}) { ask = true }
                if (ask) Confirm(tr(R.string.s_wipe_title), tr(R.string.s_wipe_text), tr(R.string.s_wipe),
                    onConfirm = { app.routines.wipe(); onWipe() }, onDismiss = { ask = false })
            }

            Section(tr(R.string.s_info))
            Group {
                Line(tr(R.string.s_version), version, icon = Icons.Rounded.Info)
                Line(tr(R.string.s_source), "GitHub", icon = Icons.Rounded.Code) { ops.openUrl("https://github.com/baf93/sordina-launcher") }
            }
            Hint(tr(R.string.s_license))
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun PermLine(label: String, ok: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, onAsk: () -> Unit) {
    if (ok) Line(label, tr(R.string.yes), icon = icon) else Line(label, tr(R.string.grant), icon = icon, onClick = onAsk)
}

/** Il calendario in cui scrivere gli impegni, da un foglio. */
@Composable
private fun CalendarPick(app: SordinaApp, ops: Ops) {
    var cals by remember { mutableStateOf(emptyList<app.sordina.data.Cal>()) }
    var sel by remember { mutableStateOf(app.prefs.calendarId) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        cals = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.calendars.list() }
    }
    if (cals.isEmpty()) { Line(tr(R.string.s_calendar), tr(R.string.none_m), icon = Icons.Rounded.CalendarToday); return }
    val current = cals.firstOrNull { it.id == sel } ?: cals.first()
    val dup = { n: String -> cals.count { it.name == n } > 1 }
    Pick(
        tr(R.string.s_calendar), current.name,
        cals.map { Option(it.name, if (dup(it.name)) it.account else null) }, { cals[it].id == current.id },
        icon = Icons.Rounded.CalendarToday, title = tr(R.string.s_events_go),
    ) { sel = cals[it].id; app.prefs.calendarId = sel; ops.changed() }
}

/** I calendari di Home Assistant da mostrare in Casa, dentro la pagina di Home Assistant. */
@Composable
private fun HaCalendars(app: SordinaApp, ops: Ops) {
    val prefs = app.prefs
    var list by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var chosen by remember { mutableStateOf(prefs.haCalendars) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        list = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.ha.calendars() }
    }
    Section(tr(R.string.s_ha_calendars))
    when {
        list == null -> Hint(tr(R.string.loading))
        list!!.isEmpty() -> Hint(tr(R.string.s_no_calendar))
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

/** Cosa passa subito, app per app. Tocco: un foglio con i livelli. In cima le app che hanno scritto oggi. */
@Composable
private fun LevelsPage(app: SordinaApp, apps: List<AppEntry>, onBack: () -> Unit) {
    val prefs = app.prefs
    var levels by remember { mutableStateOf(prefs.appLevels) }
    var q by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<AppEntry?>(null) }
    val todayPkgs = remember { app.store.today.value.groupBy { it.pkg }.mapValues { it.value.size } }
    val pkgs = remember(apps) { apps.distinctBy { it.packageName } }
    Header(tr(R.string.s_apps_pass), onBack)
    Hint(tr(R.string.s_tap_app))
    Field(q, { q = it }, tr(R.string.s_search_app))
    val nq = Search.normalize(q)
    val list = pkgs.filter { nq.isEmpty() || it.normalized.contains(nq) }
        .sortedWith(compareByDescending<AppEntry> { todayPkgs[it.packageName] ?: 0 }.thenByDescending { if (it.packageName in levels) 1 else 0 })
    LazyColumn(Modifier.fillMaxWidth()) {
        items(list, key = { it.key }) { a ->
            val n = todayPkgs[a.packageName]
            Line(a.label, levels[a.packageName]?.label ?: tr(R.string.s_order_auto), n?.let { trn(R.plurals.n_today, it) }) { editing = a }
        }
    }
    editing?.let { a ->
        val opts = listOf<Pair<Level?, Option>>(
            null to Option(tr(R.string.s_order_auto), tr(R.string.lv_auto_hint)),
            Level.SEMPRE to Option(tr(R.string.lv_always), tr(R.string.lv_always_hint)),
            Level.SUBITO to Option(tr(R.string.lv_silent), tr(R.string.lv_silent_hint)),
            Level.RIEPILOGO to Option(tr(R.string.lv_summary), tr(R.string.lv_summary_hint)),
        )
        Sheet(a.label, onDismiss = { editing = null }) {
            opts.forEach { (lv, o) ->
                val on = levels[a.packageName] == lv
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth().clickable {
                        prefs.setAppLevel(a.packageName, lv); levels = prefs.appLevels; editing = null
                    }.padding(horizontal = 24.dp, vertical = 13.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(o.label, style = androidx.compose.ui.text.TextStyle(fontSize = 17.sp, color = if (on) S.c.accent else S.c.ink))
                        Text(o.hint.orEmpty(), style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = S.c.soft))
                    }
                    if (on) Icon(Icons.Rounded.Check, null, tint = S.c.accent)
                }
            }
        }
    }
}

/** Le app frenate: cerca per aggiungere, tocca tr(R.string.remove) per liberarla. */
@Composable
private fun BrakedPage(app: SordinaApp, apps: List<AppEntry>, onChange: () -> Unit, onBack: () -> Unit) {
    val prefs = app.prefs
    var braked by remember { mutableStateOf(prefs.braked) }
    var q by remember { mutableStateOf("") }
    Header(tr(R.string.s_braked_apps), onBack)
    Hint(tr(R.string.s_braked_hint))
    Field(q, { q = it }, tr(R.string.s_add_app))
    val nq = Search.normalize(q)
    val list = if (nq.isEmpty()) apps.filter { it.packageName in braked }.distinctBy { it.packageName }
    else Search.apps(q, apps, emptyMap(), 8)
    if (nq.isEmpty() && list.isEmpty()) Hint(tr(R.string.s_braked_none))
    LazyColumn(Modifier.fillMaxWidth()) {
        items(list, key = { it.key }) { a ->
            val on = a.packageName in braked
            Line(a.label, trailing = {
                SmallPill(if (on) tr(R.string.remove) else tr(R.string.brake)) {
                    braked = if (on) braked - a.packageName else braked + a.packageName
                    prefs.braked = braked
                    onChange()
                }
            })
        }
    }
}

/** I colori di rilievo: quello del tema, quello dello sfondo, e qualche tinta calma. */
private val ACCENTS = listOf(0xFFC4623A, 0xFF4F7A5A, 0xFF3D6FA8, 0xFF7A5BA6, 0xFFC98B1E, 0xFFB5476B).map { it.toInt() }

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AccentPicker(current: Int, fromBackdrop: Int?, onPick: (Int) -> Unit) {
    val c = S.c
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Swatch(null, tr(R.string.s_theme), current == 0) { onPick(0) }
        if (fromBackdrop != null) Swatch(androidx.compose.ui.graphics.Color(fromBackdrop), tr(R.string.s_wallpaper), current == 1) { onPick(1) }
        ACCENTS.forEach { a -> Swatch(androidx.compose.ui.graphics.Color(a), null, current == a) { onPick(a) } }
    }
    Hint(if (fromBackdrop == null && current == 1) tr(R.string.s_no_bg_color) else tr(R.string.s_accent_hint))
}

@Composable
private fun Swatch(color: androidx.compose.ui.graphics.Color?, label: String?, on: Boolean, onClick: () -> Unit) {
    val c = S.c
    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .background(color ?: c.accent)
                .border(if (on) 3.dp else 0.dp, c.ink, androidx.compose.foundation.shape.CircleShape)
                .clickableNoRipple(onClick),
        )
        if (label != null) androidx.compose.material3.Text(label, style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = c.soft))
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PresetPicker(kind: Abstracts.Kind, current: Int, onPick: (Int) -> Unit) {
    val c = S.c
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Abstracts.all.forEachIndexed { i, p ->
            if (p.kind != kind) return@forEachIndexed
            val img = remember(i) { Abstracts.render(i, 104, 192, grain = false).asImageBitmap() }
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                androidx.compose.foundation.Image(
                    img, p.name,
                    Modifier.size(width = 52.dp, height = 96.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .border(if (i == current) 3.dp else 0.5.dp, if (i == current) c.ink else c.dot, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .clickableNoRipple { onPick(i) },
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
                androidx.compose.material3.Text(p.name, style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = c.soft))
            }
        }
    }
}
