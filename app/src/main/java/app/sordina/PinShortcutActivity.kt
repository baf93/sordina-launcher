package app.sordina

import android.app.Activity
import android.content.pm.LauncherApps
import android.os.Bundle

/**
 * "Aggiungi a schermata Home" da un browser (una web app come Push Mode) o da un'app: Android chiede alla Home
 * di accettare la scorciatoia. Sordina la accetta senza mostrare niente e la mette fra le app.
 */
class PinShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            val req = getSystemService(LauncherApps::class.java).getPinItemRequest(intent)
            if (req != null && req.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT && req.isValid) req.accept()
        }
        finish()
    }
}
