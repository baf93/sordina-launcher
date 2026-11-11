package app.sordina.service

import android.app.NotificationManager
import android.app.NotificationManager.Policy
import android.content.Context
import android.os.Build
import app.sordina.data.Moment
import app.sordina.data.Prefs

/**
 * Non disturbare gestito da Sordina, solo se l'utente lo sceglie.
 * Di giorno suonano le chiamate di tutti; di notte e in auto solo i preferiti (le persone importanti).
 * Sempre: chiamate ripetute, sveglie, musica. Le notifiche restano visibili, solo senza suono.
 */
class Silence(private val context: Context, private val prefs: Prefs) {
    private val nm = context.getSystemService(NotificationManager::class.java)

    fun hasAccess(): Boolean = nm.isNotificationPolicyAccessGranted

    /** Silenzio attivo in questo momento (da Sordina o da altri). */
    fun isActive(): Boolean = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL &&
        nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN

    fun enable() {
        if (!hasAccess()) return
        if (!prefs.silenceManaged) {
            prefs.savedInterruptionFilter = nm.currentInterruptionFilter
            prefs.silenceManaged = true
        }
    }

    fun disable() {
        val was = prefs.silenceManaged
        prefs.silenceManaged = false
        if (!was || !hasAccess()) return
        runCatching {
            val f = prefs.savedInterruptionFilter
            nm.setInterruptionFilter(if (f == NotificationManager.INTERRUPTION_FILTER_UNKNOWN) NotificationManager.INTERRUPTION_FILTER_ALL else f)
        }
    }

    /** Applica la regola giusta per il momento. Chiamata all'avvio, ai cambi di momento e dallo scheduler. */
    fun apply(moment: Moment) {
        if (!prefs.silenceManaged || !hasAccess()) return
        runCatching {
            var categories = Policy.PRIORITY_CATEGORY_CALLS or Policy.PRIORITY_CATEGORY_REPEAT_CALLERS or
                Policy.PRIORITY_CATEGORY_MESSAGES
            if (Build.VERSION.SDK_INT >= 28) {
                categories = categories or Policy.PRIORITY_CATEGORY_ALARMS or Policy.PRIORITY_CATEGORY_MEDIA
            }
            val calls = if (moment == Moment.NONE) Policy.PRIORITY_SENDERS_ANY else Policy.PRIORITY_SENDERS_STARRED
            val policy = if (Build.VERSION.SDK_INT >= 28) {
                // 0 = nessun effetto visivo soppresso: le notifiche si vedono, solo senza suono
                Policy(categories, calls, Policy.PRIORITY_SENDERS_STARRED, 0)
            } else {
                @Suppress("DEPRECATION")
                Policy(categories, calls, Policy.PRIORITY_SENDERS_STARRED)
            }
            nm.notificationPolicy = policy
            if (nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            }
        }
    }
}
