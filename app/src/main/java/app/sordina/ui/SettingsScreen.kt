package app.sordina.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sordina.SordinaApp
import app.sordina.data.AppEntry
import app.sordina.data.Level
import app.sordina.data.LockPrivacy
import app.sordina.data.Moments
import app.sordina.data.Search
import app.sordina.ui.theme.S
import app.sordina.ui.theme.ThemeFamily
import app.sordina.ui.theme.ThemeMode

private enum class Sub { MAIN, PEOPLE, LEVELS, PROPOSALS, BRAKED, HA }

/** Impostazioni: una sola lista, nell'ordine di UX.md, con poche sottopagine. */
@Composable
fun SettingsScreen(
    app: SordinaApp,
    apps: List<AppEntry>,
    perms: PermState,
    family: ThemeFamily,
    mode: ThemeMode,
    version: String,
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
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp)) {
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
                Sub.MAIN -> Unit
            }
        }
        return
    }

    key(tick) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            Header("Impostazioni", onBack)

            Section("Persone importanti")
            val names = prefs.importantNames
            Line(if (names.isEmpty()) "Nessuna" else "${names.size} persone", "modifica") { sub = Sub.PEOPLE }

            Section("Notifiche")
            Line("Cosa passa subito", "${prefs.appLevels.size} app") { sub = Sub.LEVELS }
            Hint("Il resto aspetta il riepilogo:")
            val hours = prefs.summaryHours
            FlowPills((7..22).map { "$it" }, { (it + 7) in hours }) { i ->
                val h = i + 7
                change { prefs.summaryHours = if (h in hours) (hours - h).ifEmpty { hours } else hours + h }
            }
            Toggle("Notte", prefs.nightEnabled, "${Moments.fmt(prefs.nightStart)}–${Moments.fmt(prefs.nightEnd)}: solo persone importanti") { v -> change { prefs.nightEnabled = v } }
            if (prefs.nightEnabled) {
                val starts = listOf(22 * 60, 23 * 60, 0)
                FlowPills(starts.map { "dalle " + Moments.fmt(it) }, { starts[it] == prefs.nightStart }) { change { prefs.nightStart = starts[it] } }
                val ends = listOf(6 * 60, 7 * 60, 8 * 60)
                FlowPills(ends.map { "alle " + Moments.fmt(it) }, { ends[it] == prefs.nightEnd }) { change { prefs.nightEnd = ends[it] } }
            }
            Toggle("In auto", prefs.carEnabled, "Solo chiamate") { v -> change { prefs.carEnabled = v } }
            if (perms.policy) Toggle("Gestisci Non disturbare", prefs.silenceManaged) { v ->
                change { if (v) app.silence.enable() else app.silence.disable() }
            }

            Section("Attrito")
            Line("App da frenare", if (prefs.braked.isEmpty()) "nessuna" else "${prefs.braked.size} app", "Notifiche al riepilogo, attesa ${prefs.brakeSeconds} s") { sub = Sub.BRAKED }

            Section("Schede")
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val loc = ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val wx = ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            Line("Meteo", if (wx) "attivo" else "attiva", "Open-Meteo, posizione approssimativa") { if (!wx) ops.request(Manifest.permission.ACCESS_COARSE_LOCATION) }
            Line("Riconosci casa", if (loc) "attivo" else "attiva", "Dal nome del Wi-Fi") { if (!loc) ops.request(Manifest.permission.ACCESS_FINE_LOCATION) }
            if (!perms.calendar) Line("Eventi", "attiva") { ops.request(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR) }
            else CalendarPicker(app, ops)
            Line("Home Assistant", if (app.ha.configured) "collegato" else "collega") { sub = Sub.HA }

            Section("Aspetto")
            Choice("Tema", ThemeMode.entries.map { it.label }, mode.ordinal) { onMode(ThemeMode.entries[it]) }
            Line("Barra", "modifica") { sub = Sub.PROPOSALS }

            Section("Permessi")
            Line("Home", if (perms.home) "sì" else "imposta") { ops.openHomeSettings() }
            Line("Notifiche", if (perms.listener) "sì" else "concedi") { ops.openListenerSettings() }
            Line("Utilizzo app", if (perms.usage) "sì" else "concedi") { ops.openUsageSettings() }
            Line("Contatti", if (perms.contacts) "sì" else "concedi") { ops.request(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS) }
            Line("Microfono", if (perms.mic) "sì" else "concedi") { ops.request(Manifest.permission.RECORD_AUDIO) }
            Line("Chiamate", if (perms.call) "sì" else "concedi") { ops.request(Manifest.permission.CALL_PHONE) }

            Section("Dati")
            Line("Esporta", "file") { ops.exportSettings() }
            Line("Importa", "file") { ops.importSettings() }
            var confirm by remember { mutableStateOf(false) }
            Line("Cancella tutto", if (confirm) "sicuro?" else "cancella") {
                if (confirm) { app.routines.wipe(); onWipe() } else confirm = true
            }

            Section("Informazioni")
            Line("Versione", version)
            Line("Codice", "GitHub") { ops.openUrl("https://github.com/baf93/sordina-launcher") }
            Hint("GPL-3.0. Niente lascia il telefono.")
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
