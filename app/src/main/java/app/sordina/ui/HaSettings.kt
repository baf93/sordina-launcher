package app.sordina.ui

import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sordina.R
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import app.sordina.SordinaApp
import app.sordina.data.HomeAssistant
import app.sordina.service.SordinaAccess
import app.sordina.service.AlarmCode
import app.sordina.data.HaEntity
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Shield
import app.sordina.data.SavedRoutine
import app.sordina.data.HomeRoutines
import app.sordina.tr
import app.sordina.ui.theme.S
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Home Assistant in Sordina, a due livelli uno sopra l'altro:
 * 1 · l'app — Assist si apre nell'app ufficiale, niente da configurare;
 * 2 · il token — in più, Sordina parla con casa da sola, risponde qui e mostra i calendari.
 * Il secondo livello resta chiuso finché non lo chiedi.
 */
@Composable
internal fun HaSettings(app: SordinaApp, ops: Ops, onAlarm: (String, String) -> Unit = { _, _ -> }, onRoutine: (String, String) -> Unit = { _, _ -> }, onChange: () -> Unit) {
    val prefs = app.prefs
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(prefs.haEnabled) }
    // Solo il resto di Sordina: ricostruire la pagina perderebbe quello che stai scrivendo e il risultato della prova
    fun changed() { ops.changed() }

    Spacer(Modifier.height(8.dp))
    Group {
        Toggle(tr(R.string.ha_use), on, icon = Icons.Rounded.Home) { v -> on = v; prefs.haEnabled = v; changed() }
    }
    if (!on) return

    // ── Livello 1: l'app
    Topic(tr(R.string.ha_level1), tr(R.string.ha_app_text))
    val access by SordinaAccess.on.collectAsState()
    Group {
        if (!app.ha.appInstalled) Line(tr(R.string.ha_app_missing), tr(R.string.ha_app_get), icon = Icons.Rounded.Download) {
            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + HomeAssistant.COMPANION_PKG)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        Line(tr(R.string.ha_write), if (access) tr(R.string.ha_write_on) else tr(R.string.ha_write_off), icon = Icons.Rounded.Accessibility) {
            SordinaAccess.openSettings(ctx)
        }
    }

    // ── Livello 2: il token
    Topic(tr(R.string.ha_level2), tr(R.string.ha_token_text))
    var connected by remember { mutableStateOf(app.ha.configured) }
    var open by remember { mutableStateOf(false) }

    if (connected) {
        var ask by remember { mutableStateOf(false) }
        Group {
            // Da dove sta parlando adesso con casa: in casa (rete di casa) o da fuori
            var via by remember { mutableStateOf(app.ha.local) }
            var away by remember { mutableStateOf(prefs.haUrl) }
            var home by remember { mutableStateOf(prefs.haLocalUrl) }
            // L'indirizzo di casa lo dice Home Assistant (passando da quello di fuori): si scrive a mano solo se non lo conosce
            var unknown by remember { mutableStateOf(false) }
            // Una prova all'apertura: dice la strada di adesso e, se mancava, impara l'indirizzo di casa
            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) { app.ha.test() }
                via = app.ha.local; away = prefs.haUrl; home = prefs.haLocalUrl; unknown = home.isBlank()
            }
            val where = when (via) { true -> tr(R.string.ha_via_local); false -> tr(R.string.ha_via_remote); null -> "" }
            Line(tr(R.string.ha_connected), where, icon = Icons.Rounded.CheckCircle)
            InputRow(Icons.Rounded.Link, tr(R.string.ha_address), away, "https://…ui.nabu.casa", KeyboardType.Uri, { away = it; prefs.haUrl = it })
            if (unknown) InputRow(Icons.Rounded.Home, tr(R.string.ha_address_local), home, "http://homeassistant.local:8123", KeyboardType.Uri, { home = it; prefs.haLocalUrl = it })
            Line(tr(R.string.disconnect), icon = Icons.Rounded.LinkOff) { ask = true }
        }
        if (ask) Confirm(tr(R.string.s_ha_disc_title), tr(R.string.s_ha_disc_text), tr(R.string.disconnect),
            onConfirm = { AlarmCode.forgetAll(prefs); app.ha.setToken(""); prefs.haUrl = ""; prefs.haLocalUrl = ""; prefs.haStatusEntity = ""; prefs.haCalendar = ""; prefs.haCalendarName = ""; connected = false; open = false; ask = false; changed() },
            onDismiss = { ask = false })
        AlarmCodes(app, onAlarm)
        RoutinePicks(app, onRoutine)
        return
    }

    if (!open) {
        Group { Line(tr(R.string.ha_add_token), icon = Icons.Rounded.Key) { open = true } }
        return
    }

    // Aperto: una scheda come quella del livello 1 (indirizzo, crea, token) e un solo tasto sotto
    var url by remember { mutableStateOf(prefs.haUrl) }
    var token by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    Group {
        InputRow(Icons.Rounded.Link, tr(R.string.ha_address), url, "https://…ui.nabu.casa", KeyboardType.Uri, { url = it; prefs.haUrl = it })
        Line(tr(R.string.ha_step2), icon = Icons.Rounded.Person) {
            // Nell'app di Home Assistant se c'è (già collegata), se no nel browser all'indirizzo scritto sopra
            val inApp = Intent(Intent.ACTION_VIEW, Uri.parse("homeassistant://navigate/profile/security")).setPackage(HomeAssistant.COMPANION_PKG)
            val web = url.trim().trimEnd('/').takeIf { it.startsWith("http") }?.let { Intent(Intent.ACTION_VIEW, Uri.parse("$it/profile/security")) }
            val ok = app.ha.appInstalled && runCatching { ctx.startActivity(inApp.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
            if (!ok && web != null) runCatching { ctx.startActivity(web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        // Il token: si scrive a mano, si incolla (creato sul telefono) o si inquadra il QR (creato sul PC)
        InputRow(Icons.Rounded.Key, tr(R.string.ha_token_label), token,
            if (prefs.haTokenEnc.isNotBlank()) tr(R.string.ha_token_saved) else tr(R.string.ha_token_ph), KeyboardType.Ascii,
            { token = it.trim(); result = null },
        ) {
            RoundIcon(Icons.Rounded.ContentPaste, tr(R.string.ha_paste)) {
                val clip = ctx.getSystemService(ClipboardManager::class.java)?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()?.trim()
                // Un token è una stringa lunga senza spazi (tre pezzi separati da punti)
                if (clip != null && clip.length > 40 && ' ' !in clip) { token = clip; result = null } else result = tr(R.string.ha_paste_empty)
            }
            Spacer(Modifier.width(8.dp))
            RoundIcon(Icons.Rounded.QrCodeScanner, tr(R.string.ha_scan)) {
                // Lo schermo del lettore è di Play Services: niente permesso fotocamera per Sordina
                val opts = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
                GmsBarcodeScanning.getClient(ctx, opts).startScan()
                    .addOnSuccessListener { b -> b.rawValue?.trim()?.takeIf { it.length > 40 && ' ' !in it }?.let { token = it; result = null } ?: run { result = tr(R.string.ha_scan_bad) } }
            }
        }
    }

    if (testing) LaunchedEffect(Unit) {
        prefs.haUrl = url
        if (token.isNotBlank()) { app.ha.setToken(token); token = "" }
        val r = withContext(Dispatchers.IO) { app.ha.test() }
        testing = false
        // Riuscito: la scheda si chiude e resta "Collegato"; se no, il motivo sotto il tasto
        if (r == tr(R.string.ha_unreachable) || r == tr(R.string.ha_missing)) result = r else connected = true
        changed()
    }
    Spacer(Modifier.height(14.dp))
    Primary(if (testing) tr(R.string.trying) else tr(R.string.save_test), Modifier.fillMaxWidth()) { if (!testing) testing = true }
    result?.let { Hint(it, Modifier.padding(start = 12.dp, top = 8.dp)) }
}

/** Una riga della scheda con il campo dentro: icona, nome, sotto il testo da scrivere; a destra eventuali tasti. */
@Composable
private fun InputRow(
    icon: ImageVector, label: String, value: String, placeholder: String, keyboard: KeyboardType,
    onChange: (String) -> Unit, actions: (@Composable () -> Unit)? = null,
) {
    val c = S.c
    Row(Modifier.fillMaxWidth().background(c.card).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(19.dp), tint = c.accent)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = TextStyle(fontSize = 17.sp, color = c.ink))
            Box(Modifier.padding(top = 2.dp)) {
                if (value.isEmpty()) Text(placeholder, maxLines = 1, style = TextStyle(fontSize = 15.sp, color = c.soft))
                BasicTextField(value, onChange, singleLine = true,
                    textStyle = TextStyle(fontSize = 15.sp, color = c.accent),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboard, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth())
            }
        }
        if (actions != null) { Spacer(Modifier.width(8.dp)); actions() }
    }
}

/** Tasto tondo con un'icona, per le azioni dentro una riga. */
@Composable
private fun RoundIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = S.c
    Box(Modifier.size(40.dp).clip(CircleShape).background(c.bg).clickable(onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, Modifier.size(20.dp), tint = c.ink)
    }
}

/**
 * Gli allarmi di Home Assistant da disattivare con l'impronta ("disattiva allarme"): li scegli tu, uno per uno,
 * ciascuno col suo codice. Ogni allarme ha la sua pagina ([HaAlarmPage]).
 */
@Composable
private fun AlarmCodes(app: SordinaApp, onAlarm: (String, String) -> Unit) {
    val prefs = app.prefs
    val ctx = LocalContext.current
    Topic(tr(R.string.alarm_topic), tr(R.string.alarm_topic_hint))
    if (!AlarmCode.available(ctx)) {
        Group { Line(tr(R.string.alarm_no_bio), icon = Icons.Rounded.Lock) }
        return
    }
    var alarms by remember { mutableStateOf<List<HaEntity>?>(null) }
    LaunchedEffect(Unit) { alarms = withContext(Dispatchers.IO) { runCatching { app.ha.alarms() }.getOrDefault(emptyList()) } }
    val found = alarms ?: return
    val saved = prefs.haAlarms
    // Anche quelli salvati che adesso Home Assistant non elenca (fuori casa, o rinominati): si possono togliere
    val ids = (found.map { it.id } + saved.keys).distinct()
    Group {
        if (ids.isEmpty()) Line(tr(R.string.alarm_none), icon = Icons.Rounded.Lock)
        ids.forEach { id ->
            val e = found.firstOrNull { it.id == id }
            val name = e?.name ?: saved[id]?.name ?: id
            val s = saved[id]
            // Sotto: se inserisce anche, e in che modalità
            val hint = when {
                s == null && e != null && e.codeFormat == null -> tr(R.string.alarm_no_code)
                s?.arm == true -> tr(R.string.alarm_hint_arm, AlarmCode.modeLabel(s.mode ?: "away"))
                else -> null
            }
            Line(name, if (s != null) tr(R.string.alarm_saved_short) else tr(R.string.alarm_add), hint,
                if (s != null) Icons.Rounded.Lock else Icons.Rounded.LockOpen) { onAlarm(id, name) }
        }
    }
}

/**
 * Un allarme: se non è salvato, il codice da scrivere (o niente, se non lo chiede); se è salvato, anche inserire,
 * la modalità predefinita e togliere il codice. Interruttori e scelte sulla pagina, niente fogli.
 */
@Composable
internal fun HaAlarmPage(app: SordinaApp, id: String, name: String, onGone: () -> Unit) {
    val prefs = app.prefs
    val activity = LocalContext.current as? android.app.Activity ?: return
    var e by remember { mutableStateOf<HaEntity?>(null) }
    LaunchedEffect(id) { e = withContext(Dispatchers.IO) { runCatching { app.ha.alarms() }.getOrDefault(emptyList()) }.firstOrNull { it.id == id } }
    var saved by remember { mutableStateOf(prefs.haAlarms[id]) }
    var removing by remember { mutableStateOf(false) }
    val s = saved
    if (s == null) {
        // Da salvare: il codice si scrive qui una volta, poi l'impronta lo chiude sul telefono
        val needsCode = e?.codeFormat != null
        var code by remember(id) { mutableStateOf("") }
        Hint(if (needsCode) tr(R.string.alarm_save_sub) else tr(R.string.alarm_no_code), Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp))
        Group {
            Column(Modifier.background(S.c.card).padding(horizontal = 16.dp, vertical = 8.dp)) {
                if (needsCode) Field(code, { code = it }, tr(R.string.alarm_code), password = true,
                    keyboard = if (e?.codeFormat == "number") KeyboardType.NumberPassword else KeyboardType.Password)
            }
        }
        Spacer(Modifier.height(16.dp))
        Primary(tr(R.string.alarm_add), Modifier.fillMaxWidth()) {
            val c = code.trim()
            if (needsCode && c.isEmpty()) return@Primary
            AlarmCode.save(activity, prefs, id, name, if (needsCode) c else "") {
                prefs.haAlarms[id]?.let { cur ->
                    prefs.haAlarms = prefs.haAlarms + (id to cur.copy(mode = cur.mode ?: e?.armModes?.firstOrNull(), codeArm = e?.codeArmRequired ?: true))
                }
                saved = prefs.haAlarms[id]
            }
        }
        return
    }
    fun update(a: app.sordina.data.SavedAlarm) { prefs.haAlarms = prefs.haAlarms + (id to a); saved = prefs.haAlarms[id] }
    val modes = e?.armModes?.takeIf { it.isNotEmpty() } ?: listOfNotNull(s.mode)
    Spacer(Modifier.height(8.dp))
    Group {
        Toggle(tr(R.string.alarm_can_arm), s.arm, if (s.arm) tr(R.string.alarm_hint_arm, AlarmCode.modeLabel(s.mode ?: modes.firstOrNull() ?: "away")) else null, Icons.Rounded.Shield) { v ->
            update(s.copy(arm = v, codeArm = e?.codeArmRequired ?: s.codeArm))
        }
    }
    if (s.arm && modes.isNotEmpty()) {
        Topic(tr(R.string.alarm_pick_mode))
        Group {
            val cur = s.mode ?: modes.first()
            modes.forEach { m ->
                Line(AlarmCode.modeLabel(m), trailing = {
                    if (m == cur) Icon(Icons.Rounded.Check, null, Modifier.size(22.dp), tint = S.c.accent) else Spacer(Modifier.size(22.dp))
                }) { update(s.copy(mode = m)) }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Group { Line(tr(R.string.alarm_forget_item), icon = Icons.Rounded.DeleteOutline) { removing = true } }
    if (removing) Confirm(tr(R.string.alarm_forget_title, s.name), tr(R.string.alarm_forget_text), tr(R.string.alarm_forget),
        onConfirm = { AlarmCode.forget(prefs, id); removing = false; onGone() },
        onDismiss = { removing = false })
}

/**
 * Le routine di Home Assistant (scene e script): ti mostro quelle che vedo; ognuna ha la sua pagina ([HaRoutinePage]).
 */
@Composable
private fun RoutinePicks(app: SordinaApp, onRoutine: (String, String) -> Unit) {
    val prefs = app.prefs
    Topic(tr(R.string.routine_topic), tr(R.string.routine_topic_hint))
    var found by remember { mutableStateOf<List<HaEntity>?>(null) }
    LaunchedEffect(Unit) { found = withContext(Dispatchers.IO) { runCatching { app.ha.routines() }.getOrDefault(emptyList()) } }
    val list = found ?: return
    val enabled = prefs.haRoutines
    // Anche quelle salvate che adesso Home Assistant non elenca: si possono spegnere
    val ids = (list.sortedBy { it.name.lowercase() }.map { it.id } + enabled.keys).distinct()
    Group {
        if (ids.isEmpty()) Line(tr(R.string.routine_none), icon = Icons.Rounded.PlayArrow)
        ids.forEach { id ->
            val name = list.firstOrNull { it.id == id }?.name ?: enabled[id]?.name ?: id
            val r = enabled[id]?.takeIf { it.on }
            val value = when { r == null -> tr(R.string.routine_value_off); r.auth || r.alarm != null -> tr(R.string.routine_auth); else -> tr(R.string.routine_free) }
            // Sotto: l'allarme legato e le tue frasi ("e disattiva Centrale · «sto entrando»")
            val hint = listOfNotNull(
                r?.alarm?.let { a -> prefs.haAlarms[a]?.name }?.let { tr(if (r.arm) R.string.routine_hint_arm else R.string.routine_hint_alarm, it) },
                r?.phrases?.takeIf { it.isNotEmpty() }?.joinToString(", ") { "«$it»" },
            ).joinToString(" · ").ifEmpty { null }
            Line(name, value, hint, if (r != null) Icons.Rounded.PlayArrow else null) { onRoutine(id, name) }
        }
    }
}

/**
 * Una routine: attiva, con impronta, e per ogni allarme salvato "Inserisci" (dopo la routine) o "Disattiva" (prima).
 * Le due cose sull'allarme si escludono, e con un allarme l'impronta c'è sempre. Sotto, le tue frasi.
 */
@Composable
internal fun HaRoutinePage(app: SordinaApp, id: String, name: String) {
    val prefs = app.prefs
    val c = S.c
    var r by remember(id) { mutableStateOf(prefs.haRoutines[id] ?: SavedRoutine(name, auth = true, on = false)) }
    fun update(n: SavedRoutine) { r = n; prefs.haRoutines = prefs.haRoutines + (id to n) }
    val alarms = prefs.haAlarms.entries.sortedBy { it.value.name.lowercase() }
    Spacer(Modifier.height(8.dp))
    Group {
        Toggle(tr(R.string.routine_use), r.on, null, Icons.Rounded.PlayArrow) { v -> update(r.copy(on = v)) }
        if (r.on) {
            val forced = r.alarm != null
            Toggle(tr(R.string.routine_auth), r.auth || forced, if (forced) tr(R.string.routine_auth_needed) else null, Icons.Rounded.Fingerprint) { v ->
                if (!forced) update(r.copy(auth = v))
            }
            Toggle(tr(R.string.routine_car), r.car, tr(R.string.routine_car_hint), Icons.Rounded.DirectionsCar) { v -> update(r.copy(car = v)) }
            alarms.forEach { (a, s) ->
                Toggle(tr(R.string.routine_arm_x, s.name), r.alarm == a && r.arm, null, Icons.Rounded.Shield) { v ->
                    update(if (v) r.copy(alarm = a, arm = true, auth = true) else r.copy(alarm = null, arm = false))
                }
                Toggle(tr(R.string.alarm_off_x, s.name), r.alarm == a && !r.arm, null, Icons.Rounded.LockOpen) { v ->
                    update(if (v) r.copy(alarm = a, arm = false, auth = true) else r.copy(alarm = null, arm = false))
                }
            }
        }
    }
    if (!r.on) return
    // Le tue frasi per lanciarla, oltre al nome: una per riga
    Topic(tr(R.string.routine_phrases), tr(R.string.routine_phrases_hint))
    var text by remember(id) { mutableStateOf(r.phrases.joinToString("\n")) }
    Group {
        Box(Modifier.fillMaxWidth().background(c.card).padding(horizontal = 16.dp, vertical = 14.dp)) {
            if (text.isEmpty()) Text(tr(R.string.routine_phrases_hint), style = TextStyle(fontSize = 16.sp, color = c.soft))
            BasicTextField(text, { t -> text = t; update(r.copy(phrases = HomeRoutines.parsePhrases(t))) }, minLines = 3,
                textStyle = TextStyle(fontSize = 16.sp, color = c.ink), cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth())
        }
    }
    Spacer(Modifier.height(26.dp))
}
