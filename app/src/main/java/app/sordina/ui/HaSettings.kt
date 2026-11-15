package app.sordina.ui

import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.sordina.R
import app.sordina.SordinaApp
import app.sordina.data.HomeAssistant
import app.sordina.service.SordinaAccess
import app.sordina.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Home Assistant in Sordina, tutto in una pagina: acceso o spento, come parla con casa (con l'app o con il token),
 * e per il token una procedura passo passo: indirizzo, profilo aperto dove si crea, incolla, prova.
 */
@Composable
internal fun HaSettings(app: SordinaApp, ops: Ops, onChange: () -> Unit) {
    val prefs = app.prefs
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(prefs.haEnabled) }
    var mode by remember { mutableStateOf(prefs.haMode) }
    // Solo il resto di Sordina: ricostruire la pagina perderebbe quello che stai scrivendo e il risultato della prova
    fun changed() { ops.changed() }

    Spacer(Modifier.height(8.dp))
    Group {
        Toggle(tr(R.string.ha_use), on, tr(R.string.ha_use_hint), Icons.Rounded.Home) { v -> on = v; prefs.haEnabled = v; changed() }
    }
    if (!on) return

    Choice(tr(R.string.ha_how), listOf(tr(R.string.ha_mode_app), tr(R.string.ha_mode_token)), if (mode == "app") 0 else 1) { i ->
        mode = if (i == 0) "app" else "token"; prefs.haMode = mode; changed()
    }

    if (mode == "app") {
        Hint(tr(R.string.ha_app_text), Modifier.padding(start = 4.dp, bottom = 12.dp))
        val access by SordinaAccess.on.collectAsState()
        Group {
            if (!app.ha.appInstalled) Line(tr(R.string.ha_app_missing), tr(R.string.ha_app_get), icon = Icons.Rounded.Download) {
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + HomeAssistant.COMPANION_PKG)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            Line(tr(R.string.ha_write), if (access) tr(R.string.ha_write_on) else tr(R.string.ha_write_off), tr(R.string.ha_write_hint), Icons.Rounded.Accessibility) {
                SordinaAccess.openSettings(ctx)
            }
        }
        return
    }

    // ── Con il token: i quattro passi
    Hint(tr(R.string.ha_token_text), Modifier.padding(start = 4.dp, bottom = 12.dp))
    var url by remember { mutableStateOf(prefs.haUrl) }
    var token by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(prefs.haTokenEnc.isNotBlank()) }
    var result by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    Topic(tr(R.string.ha_step1), tr(R.string.ha_step1_hint))
    Field(url, { url = it; prefs.haUrl = it }, "https://…ui.nabu.casa", keyboard = KeyboardType.Uri)

    Topic(tr(R.string.ha_step2), tr(R.string.ha_step2_hint))
    Secondary(tr(R.string.ha_open_profile), Modifier.fillMaxWidth()) {
        // Nell'app di Home Assistant se c'è (già collegata), se no nel browser all'indirizzo scritto sopra
        val inApp = Intent(Intent.ACTION_VIEW, Uri.parse("homeassistant://navigate/profile/security")).setPackage(HomeAssistant.COMPANION_PKG)
        val web = url.trim().trimEnd('/').takeIf { it.startsWith("http") }?.let { Intent(Intent.ACTION_VIEW, Uri.parse("$it/profile/security")) }
        val ok = app.ha.appInstalled && runCatching { ctx.startActivity(inApp.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        if (!ok && web != null) runCatching { ctx.startActivity(web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    Topic(tr(R.string.ha_step3))
    Field(token, { token = it }, if (saved) tr(R.string.ha_token_saved) else tr(R.string.ha_token), password = true)
    Secondary(tr(R.string.ha_paste), Modifier.fillMaxWidth()) {
        val clip = ctx.getSystemService(ClipboardManager::class.java)?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()?.trim()
        // Un token è una stringa lunga senza spazi (tre pezzi separati da punti)
        if (clip != null && clip.length > 40 && ' ' !in clip) { token = clip; result = tr(R.string.ha_pasted) } else result = tr(R.string.ha_paste_empty)
    }

    Topic(tr(R.string.ha_step4))
    if (testing) LaunchedEffect(Unit) {
        prefs.haUrl = url
        if (token.isNotBlank()) { app.ha.setToken(token); token = ""; saved = true }
        result = withContext(Dispatchers.IO) { app.ha.test() }
        testing = false
        changed()
    }
    Primary(if (testing) tr(R.string.trying) else tr(R.string.save_test), Modifier.fillMaxWidth()) { if (!testing) testing = true }
    result?.let { Hint(it, Modifier.padding(top = 8.dp)) }

    if (app.ha.configured) {
        HaCalendars(app, ops)
        Spacer(Modifier.height(18.dp))
        var ask by remember { mutableStateOf(false) }
        Group { Line(tr(R.string.disconnect), icon = Icons.Rounded.LinkOff) { ask = true } }
        if (ask) Confirm(tr(R.string.s_ha_disc_title), tr(R.string.s_ha_disc_text), tr(R.string.disconnect),
            onConfirm = { app.ha.setToken(""); prefs.haUrl = ""; prefs.haStatusEntity = ""; prefs.haCalendars = emptySet(); url = ""; saved = false; ask = false; changed() },
            onDismiss = { ask = false })
    }
}
