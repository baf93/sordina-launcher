package app.sordina

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.LauncherApps
import android.os.Bundle

/**
 * "Aggiungi a schermata Home" da un browser (una web app come Push Mode) o da un'app: Android chiede alla Home
 * di accettare la scorciatoia. Sordina chiede prima a te: un'app non mette scorciatoie fra le tue app da sola.
 */
class PinShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val req = runCatching { getSystemService(LauncherApps::class.java).getPinItemRequest(intent) }.getOrNull()
        val info = req?.shortcutInfo
        if (req == null || req.requestType != LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT || !req.isValid || info == null) { finish(); return }
        val from = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(info.`package`, 0)).toString() }.getOrDefault(info.`package`)
        val label = (info.shortLabel ?: info.longLabel ?: "").toString()
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setMessage(getString(R.string.pin_shortcut_q, label, from))
            .setPositiveButton(R.string.ok) { _, _ -> runCatching { if (req.isValid) req.accept() } }
            .setNegativeButton(R.string.cancel, null)
            .setOnDismissListener { finish() }
            .show()
    }
}
