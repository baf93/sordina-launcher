package app.sordina.ui

import android.Manifest
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import app.sordina.data.Level
import app.sordina.data.LockPrivacy
import app.sordina.data.Moments
import app.sordina.data.Search
import app.sordina.ui.theme.Abstracts
import app.sordina.ui.theme.S
import app.sordina.ui.theme.ThemeFamily
import app.sordina.ui.theme.ThemeMode

private enum class Sub { MAIN, PEOPLE, LEVELS, PROPOSALS, BRAKED, HA, SUMMARY, NIGHT, CALENDAR, PERMS, HACAL, LOOK }

/** Impostazioni: una sola lista, nell'ordine di UX.md, con poche sottopagine. */
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
    fun change(block: () -> Unit) { block(); tick++; ops.changed() }
    BackHandler(sub != Sub.MAIN) { sub = Sub.MAIN }
    val prefs = app.prefs
    val c = S.c

    if (sub != Sub.MAIN) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 12.dp, vertical = 16.dp)) {
            when (sub) {
                Sub.PEOPLE -> {
                    Header("Persone importanti") { sub = Sub.MAIN }
                    Hint("Passano sempre, anche di notte.")
                    Column(Modifier.verticalScroll(rememberScrollState())) { ImportantPicker(app, perms, ops) }
                }
                Sub.LEVELS -> LevelsPage(app, apps) { sub = Sub.MAIN }
                Sub.PROPOSALS -> key(tick) {
                    Header("Barra") { sub = Sub.MAIN }
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Toggle("Tastiera subito aperta", prefs.keyboardOnOpen, "Scorrendo in su si scrive subito") { v -> change { prefs.keyboardOnOpen = v } }
                        Section("Fisse")
                        val byKey = apps.associateBy { it.key }
                        if (prefs.pinned.isEmpty()) Hint("Nessuna. Pressione lunga su una proposta: «Tieni qui».")
                        prefs.pinned.forEach { k -> Line(byKey[k]?.label ?: k, "togli") { change { prefs.pinned = prefs.pinned - k } } }
                        Section("Escluse")
                        if (prefs.excluded.isEmpty()) Hint("Nessuna.")
                        prefs.excluded.forEach { k -> Line(byKey[k]?.label ?: k, "rimetti") { change { prefs.excluded = prefs.excluded - k } } }
                        Section("Quanto ci prende")
                        val t = prefs.intentTotal
                        Hint(if (t == 0) "Ancora nessuna ricerca." else "La prima proposta della barra era quella giusta ${prefs.intentFirst} volte su $t (${prefs.intentFirst * 100 / t}%).")
                        Line("Dimentica le correzioni imparate", "azzera") { change { prefs.aliases = emptyMap(); prefs.intentFirst = 0; prefs.intentTotal = 0 } }
                    }
                }
                Sub.BRAKED -> BrakedPage(app, apps, onChange = { ops.changed() }) { sub = Sub.MAIN }
                Sub.HA -> {
                    Header("Home Assistant") { sub = Sub.MAIN }
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        HaForm(app, ops)
                        Spacer(Modifier.height(12.dp))
                        Line("Scollega Home Assistant", "scollega") { change { app.ha.setToken(""); prefs.haUrl = ""; prefs.haStatusEntity = "" } }
                    }
                }
                Sub.SUMMARY -> key(tick) {
                    Header("Riepiloghi") { sub = Sub.MAIN }
                    Hint("Il non urgente torna a quest'ore.")
                    val hours = prefs.summaryHours
                    FlowPills((7..22).map { "$it" }, { (it + 7) in hours }) { i ->
                        val h = i + 7
                        change { prefs.summaryHours = if (h in hours) (hours - h).ifEmpty { hours } else hours + h }
                    }
                }
                Sub.NIGHT -> key(tick) {
                    Header("Notte") { sub = Sub.MAIN }
                    Group { Toggle("Notte", prefs.nightEnabled, "Solo persone importanti", Icons.Rounded.Bedtime) { v -> change { prefs.nightEnabled = v } } }
                    if (prefs.nightEnabled) {
                        Section("Inizio")
                        val starts = listOf(22 * 60, 23 * 60, 0)
                        FlowPills(starts.map { Moments.fmt(it) }, { starts[it] == prefs.nightStart }) { change { prefs.nightStart = starts[it] } }
                        Section("Fine")
                        val ends = listOf(6 * 60, 7 * 60, 8 * 60)
                        FlowPills(ends.map { Moments.fmt(it) }, { ends[it] == prefs.nightEnd }) { change { prefs.nightEnd = ends[it] } }
                    }
                }
                Sub.CALENDAR -> {
                    Header("Calendario") { sub = Sub.MAIN }
                    CalendarPicker(app, ops)
                }
                Sub.PERMS -> key(tick) {
                    Header("Permessi") { sub = Sub.MAIN }
                    Group {
                        Line("Home", if (perms.home) "sì" else "imposta", icon = Icons.Rounded.Home) { ops.openHomeSettings() }
                        Line("Notifiche", if (perms.listener) "sì" else "concedi", icon = Icons.Rounded.Notifications) { ops.openListenerSettings() }
                        Line("Utilizzo app", if (perms.usage) "sì" else "concedi", icon = Icons.Rounded.BarChart) { ops.openUsageSettings() }
                        Line("Contatti", if (perms.contacts) "sì" else "concedi", icon = Icons.Rounded.Contacts) { ops.request(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS) }
                        Line("Microfono", if (perms.mic) "sì" else "concedi", icon = Icons.Rounded.Mic) { ops.request(Manifest.permission.RECORD_AUDIO) }
                        Line("Chiamate", if (perms.call) "sì" else "concedi", icon = Icons.Rounded.Call) { ops.request(Manifest.permission.CALL_PHONE) }
                    }
                }
                Sub.LOOK -> {
                    Header("Sfondo e colore") { sub = Sub.MAIN }
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                    Section("Sfondo")
                    FlowPills(listOf("Tinta unita", "Del telefono", "Una foto"), { it == backdrop }) { i ->
                        if (i == 2) ops.pickWallpaper() else { prefs.wallpaperMode = i; ops.backdropChanged() }
                    }
                    Hint(if (backdrop == 0) "Il colore del tema." else "Le scritte diventano chiare o scure secondo lo sfondo.")
                    Abstracts.Kind.entries.forEach { kind ->
                        Section(kind.label)
                        PresetPicker(kind, if (backdrop == 3) prefs.wallpaperPreset else -1) { i -> prefs.wallpaperPreset = i; prefs.wallpaperMode = 3; ops.backdropChanged() }
                    }
                    if (backdrop != 0) Group {
                        var g by remember { mutableStateOf(prefs.glass) }
                        Toggle("Schede trasparenti", g, "Schede e barra lasciano vedere lo sfondo") { v -> g = v; prefs.glass = v; ops.backdropChanged() }
                    }
                    Section("Colore di rilievo")
                    AccentPicker(accent, backdropAccent) { v -> prefs.accent = v; ops.backdropChanged() }
                    Spacer(Modifier.height(40.dp))
                    }
                }
                Sub.HACAL -> {
                    Header("Calendari di casa") { sub = Sub.MAIN }
                    var list by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
                    var chosen by remember { mutableStateOf(prefs.haCalendars) }
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        list = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.ha.calendars() }
                    }
                    Hint("Da Home Assistant, nel programma di Casa.")
                    when {
                        list == null -> Hint("Carico…")
                        list!!.isEmpty() -> Hint("Nessun calendario trovato.")
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
                Sub.MAIN -> Unit
            }
        }
        return
    }

    key(tick) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 16.dp),
        ) {
            Header("Impostazioni", onBack)
            val ctx = androidx.compose.ui.platform.LocalContext.current
            fun granted(p: String) = ctx.checkSelfPermission(p) == android.content.pm.PackageManager.PERMISSION_GRANTED

            Section("Notifiche")
            Group {
                val names = prefs.importantNames
                Line("Persone importanti", "${names.size}", icon = Icons.Rounded.FavoriteBorder) { sub = Sub.PEOPLE }
                Line("Passano subito", "${prefs.appLevels.size} app", icon = Icons.Rounded.Bolt) { sub = Sub.LEVELS }
                Line("Riepiloghi", prefs.summaryHours.sorted().joinToString(" · "), icon = Icons.Rounded.Schedule) { sub = Sub.SUMMARY }
                Line("Notte", if (prefs.nightEnabled) "${Moments.fmt(prefs.nightStart)}–${Moments.fmt(prefs.nightEnd)}" else "no", icon = Icons.Rounded.Bedtime) { sub = Sub.NIGHT }
                Toggle("In auto", prefs.carEnabled, "Solo chiamate", Icons.Rounded.DirectionsCar) { v -> change { prefs.carEnabled = v } }
                if (perms.policy) Toggle("Non disturbare", prefs.silenceManaged, "Gestito da Sordina", Icons.Rounded.DoNotDisturbOn) { v ->
                    change { if (v) app.silence.enable() else app.silence.disable() }
                }
            }

            Section("Attrito")
            Group {
                Line("App da frenare", if (prefs.braked.isEmpty()) "nessuna" else "${prefs.braked.size} · ${prefs.brakeSeconds} s", icon = Icons.Rounded.PanTool) { sub = Sub.BRAKED }
            }

            Section("Casa")
            Group {
                val wx = granted(Manifest.permission.ACCESS_COARSE_LOCATION)
                Line("Meteo", if (wx) "sì" else "attiva", icon = Icons.Rounded.Cloud, onClick = if (wx) null else ({ ops.request(Manifest.permission.ACCESS_COARSE_LOCATION) }))
                if (!perms.calendar) Line("Calendario", "attiva", icon = Icons.Rounded.CalendarToday) { ops.request(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR) }
                else Line("Calendario", app.calendars.default()?.name.orEmpty(), icon = Icons.Rounded.CalendarToday) { sub = Sub.CALENDAR }
                val loc = granted(Manifest.permission.ACCESS_FINE_LOCATION)
                Line("Riconosci casa", if (loc) "sì" else "attiva", "Dal nome del Wi-Fi", Icons.Rounded.Wifi, onClick = if (loc) null else ({ ops.request(Manifest.permission.ACCESS_FINE_LOCATION) }))
                Line("Home Assistant", if (app.ha.configured) "sì" else "collega", icon = Icons.Rounded.Home) { sub = Sub.HA }
                if (app.ha.configured) Line("Calendari di casa", if (prefs.haCalendars.isEmpty()) "nessuno" else "${prefs.haCalendars.size}", icon = Icons.Rounded.EventNote) { sub = Sub.HACAL }
            }

            Section("Aspetto")
            Group {
                Line("Tema", mode.label, icon = Icons.Rounded.Contrast) { onMode(ThemeMode.entries[(mode.ordinal + 1) % ThemeMode.entries.size]) }
                Line("Sfondo e colore", listOf("tinta unita", "del telefono", "foto", "astratto")[backdrop.coerceIn(0, 3)], icon = Icons.Rounded.Wallpaper) { sub = Sub.LOOK }
                Line("Barra", icon = Icons.Rounded.Search) { sub = Sub.PROPOSALS }
                Toggle("Icone delle app", prefs.appIcons, "Senza icone viene meno voglia di aprire", Icons.Rounded.Apps) { v -> change { prefs.appIcons = v } }
                Toggle("Animazioni", prefs.animations, "Movimenti morbidi", Icons.Rounded.Animation) { v -> change { prefs.animations = v } }
                Toggle("Vibrazioni", prefs.haptics, "Un tocco leggero quando premi", Icons.Rounded.Vibration) { v -> change { prefs.haptics = v } }
            }

            Section("Altro")
            Group {
                Line("Permessi", icon = Icons.Rounded.Lock) { sub = Sub.PERMS }
                Line("Esporta", icon = Icons.Rounded.Upload) { ops.exportSettings() }
                Line("Importa", icon = Icons.Rounded.Download) { ops.importSettings() }
                var confirm by remember { mutableStateOf(false) }
                Line("Cancella tutto", if (confirm) "sicuro?" else "", icon = Icons.Rounded.DeleteOutline) {
                    if (confirm) { app.routines.wipe(); onWipe() } else confirm = true
                }
            }

            Section("Informazioni")
            Group {
                Line("Versione", version, icon = Icons.Rounded.Info)
                Line("Codice sorgente", "GitHub", icon = Icons.Rounded.Code) { ops.openUrl("https://github.com/baf93/sordina-launcher") }
            }
            Hint("Open source, GPL-3.0.")
            Spacer(Modifier.height(40.dp))
        }
    }
}

/** Livello per app: tocco per cambiare. In cima le app che hanno scritto oggi. */
@Composable
private fun LevelsPage(app: SordinaApp, apps: List<AppEntry>, onBack: () -> Unit) {
    val prefs = app.prefs
    var levels by remember { mutableStateOf(prefs.appLevels) }
    var q by remember { mutableStateOf("") }
    val todayPkgs = remember { app.store.today.value.groupBy { it.pkg }.mapValues { it.value.size } }
    val pkgs = remember(apps) { apps.distinctBy { it.packageName } }
    Header("Cosa passa subito", onBack)
    Hint("Tocca per cambiare.")
    Field(q, { q = it }, "Cerca un'app…")
    val nq = Search.normalize(q)
    val list = pkgs.filter { nq.isEmpty() || it.normalized.contains(nq) }
        .sortedWith(compareByDescending<AppEntry> { todayPkgs[it.packageName] ?: 0 }.thenByDescending { if (it.packageName in levels) 1 else 0 })
    LazyColumn(Modifier.fillMaxWidth()) {
        items(list, key = { it.key }) { a ->
            val lv = levels[a.packageName]
            val n = todayPkgs[a.packageName]
            Line(a.label, lv?.label ?: "Automatico", n?.let { "$it notifiche oggi" }) {
                val next = when (lv) { null -> Level.SEMPRE; Level.SEMPRE -> Level.SUBITO; Level.SUBITO -> Level.RIEPILOGO; Level.RIEPILOGO -> null }
                prefs.setAppLevel(a.packageName, next)
                levels = prefs.appLevels
            }
        }
    }
}

@Composable
private fun BrakedPage(app: SordinaApp, apps: List<AppEntry>, onChange: () -> Unit, onBack: () -> Unit) {
    val prefs = app.prefs
    var braked by remember { mutableStateOf(prefs.braked) }
    var secs by remember { mutableIntStateOf(prefs.brakeSeconds) }
    var q by remember { mutableStateOf("") }
    Header("App da frenare", onBack)
    Hint("Notifiche al riepilogo. Prima di aprirle, un'attesa:")
    val opts = listOf(5, 10, 20, 30, 60)
    FlowPills(opts.map { "$it s" }, { opts[it] == secs }) { secs = opts[it]; prefs.brakeSeconds = secs }
    Field(q, { q = it }, "Aggiungi un'app…")
    val nq = Search.normalize(q)
    val list = if (nq.isEmpty()) apps.filter { it.packageName in braked }.distinctBy { it.packageName }
    else Search.apps(q, apps, emptyMap(), 8)
    LazyColumn(Modifier.fillMaxWidth()) {
        items(list, key = { it.key }) { a ->
            val on = a.packageName in braked
            Line(a.label, if (on) "togli" else "frena") {
                braked = if (on) braked - a.packageName else braked + a.packageName
                prefs.braked = braked
                onChange()
            }
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
        Swatch(null, "Tema", current == 0) { onPick(0) }
        if (fromBackdrop != null) Swatch(androidx.compose.ui.graphics.Color(fromBackdrop), "Sfondo", current == 1) { onPick(1) }
        ACCENTS.forEach { a -> Swatch(androidx.compose.ui.graphics.Color(a), null, current == a) { onPick(a) } }
    }
    Hint(if (fromBackdrop == null && current == 1) "Lo sfondo non suggerisce un colore." else "Per i numeri in sordina, il microfono, le cose da notare.")
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
