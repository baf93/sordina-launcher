package app.sordina.ui

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
import app.sordina.ui.theme.ThemeFamily
import app.sordina.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Primo avvio: quattro passi. L'app funziona anche se si salta tutto. */
@Composable
fun Onboarding(
    app: SordinaApp,
    perms: PermState,
    family: ThemeFamily,
    mode: ThemeMode,
    onFamily: (ThemeFamily) -> Unit,
    onMode: (ThemeMode) -> Unit,
    ops: Ops,
    onDone: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val last = 3
    fun next() { if (step < last) step++ else onDone() }
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Cap("${step + 1} di ${last + 1}")
            Spacer(Modifier.weight(1f))
            if (step in 1 until last) Serif("Salta", Modifier.clickableNoRipple { next() }.padding(8.dp), size = 14.sp, color = S.c.soft)
        }
        Spacer(Modifier.height(24.dp))
        AnimatedContent(step, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) }, label = "step", modifier = Modifier.weight(1f)) { s ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                when (s) {
                    0 -> Page("Sordina come Home", "Il telefono, più calmo.") {
                        Done(perms.home, "Sordina è la Home")
                        if (!perms.home) Primary("Imposta", Modifier.fillMaxWidth()) { ops.openHomeSettings() }
                        else Primary("Avanti", Modifier.fillMaxWidth()) { next() }
                    }
                    1 -> Page("Notifiche", "Il non urgente aspetta il riepilogo.") {
                        Done(perms.listener, "Accesso alle notifiche")
                        Done(perms.usage, "Utilizzo app")
                        if (Build.VERSION.SDK_INT >= 33) Done(perms.post, "Avvisi di Sordina")
                        when {
                            !perms.listener -> Primary("Consenti notifiche", Modifier.fillMaxWidth()) { ops.openListenerSettings() }
                            !perms.usage -> Primary("Consenti utilizzo", Modifier.fillMaxWidth()) { ops.openUsageSettings() }
                            Build.VERSION.SDK_INT >= 33 && !perms.post -> Primary("Consenti avvisi", Modifier.fillMaxWidth()) { ops.request(Manifest.permission.POST_NOTIFICATIONS) }
                            else -> Primary("Avanti", Modifier.fillMaxWidth()) { next() }
                        }
                    }
                    2 -> Page("Persone importanti", "Passano sempre.") {
                        ImportantPicker(app, perms, ops)
                        Spacer(Modifier.height(12.dp))
                        Primary("Avanti", Modifier.fillMaxWidth()) { next() }
                    }
                    else -> Page("Fatto", "Su: fai. Giù: notifiche.\nTieni premuto: impostazioni.") {
                        Primary("Vai a Casa", Modifier.fillMaxWidth()) { onDone() }
                    }
                }
            }
        }
    }
}

@Composable
private fun Page(title: String, text: String, content: @Composable () -> Unit) {
    Serif(title, size = 28.sp)
    Spacer(Modifier.height(14.dp))
    Serif(text, size = 17.sp, color = S.c.soft)
    Spacer(Modifier.height(28.dp))
    content()
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
        Secondary("Consenti l'accesso ai contatti", Modifier.fillMaxWidth()) {
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
            Cap("Importanti")
            Spacer(Modifier.height(4.dp))
            chosen.forEach { n ->
                val display = all.firstOrNull { it.normalized == n }?.name ?: n.replaceFirstChar { it.uppercase() }
                Line(display, "togli") { app.people.removeImportantName(n); tick++ }
            }
            Spacer(Modifier.height(10.dp))
        }
        Field(q, { q = it }, "Cerca un contatto…")
        val list = if (q.isBlank()) all.filter { it.starred && it.normalized !in names }.take(8)
        else app.people.search(q, 6).filter { it.normalized !in names }
        if (q.isBlank() && list.isNotEmpty()) Hint("Dai tuoi preferiti:")
        list.forEach { ct -> Line(ct.name, "aggiungi", ct.number) { app.people.setImportant(ct, true); q = ""; tick++ } }
        if (q.isBlank() && list.isEmpty() && chosen.isEmpty()) Serif("Cerca le persone che devono passare sempre.", size = 14.sp, color = c.soft, italic = true)
    }
}

@Composable
fun CalendarPicker(app: SordinaApp, ops: Ops) {
    var cals by remember { mutableStateOf(emptyList<app.sordina.data.Cal>()) }
    var sel by remember { mutableStateOf(app.prefs.calendarId) }
    LaunchedEffect(Unit) { cals = withContext(Dispatchers.IO) { app.calendars.list() } }
    if (cals.isEmpty()) { Hint("Nessun calendario in cui scrivere."); return }
    val current = cals.firstOrNull { it.id == sel } ?: cals.first()
    Hint("Gli impegni vanno in:")
    FlowPills(cals.map { it.name + if (cals.count { o -> o.name == it.name } > 1) " (${it.account})" else "" }, { cals[it].id == current.id }) {
        sel = cals[it].id; app.prefs.calendarId = sel; ops.changed()
    }
}

/** Indirizzo, token e prova di Home Assistant. */
@Composable
fun HaForm(app: SordinaApp, ops: Ops) {
    val prefs = app.prefs
    var url by remember { mutableStateOf(prefs.haUrl) }
    var token by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var alarms by remember { mutableStateOf(prefs.appLevels[HomeAssistant.COMPANION_PKG] == Level.SEMPRE) }
    Field(url, { url = it }, "http://homeassistant.local:8123", keyboard = KeyboardType.Uri)
    Field(token, { token = it }, if (prefs.haTokenEnc.isNotBlank()) "Token salvato (scrivi per cambiarlo)" else "Token di accesso a lunga durata", password = true)
    Hint("Comandi tramite Assist: solo le entità esposte. Token cifrato sul telefono.")
    if (testing) {
        LaunchedEffect(Unit) {
            prefs.haUrl = url
            if (token.isNotBlank()) app.ha.setToken(token)
            result = withContext(Dispatchers.IO) { app.ha.test() }
            testing = false
            ops.changed()
        }
    }
    Secondary(if (testing) "Provo…" else "Salva e prova", Modifier.fillMaxWidth()) { if (!testing) testing = true }
    result?.let { Hint(it) }
    Toggle("Allarmi di casa: sempre", alarms) { v ->
        alarms = v
        prefs.setAppLevel(HomeAssistant.COMPANION_PKG, if (v) Level.SEMPRE else null)
    }
}
