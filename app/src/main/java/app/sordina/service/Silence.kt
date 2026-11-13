package app.sordina.service

import android.app.NotificationManager
import android.app.NotificationManager.Policy
import android.content.Context
import android.os.Build
import app.sordina.data.Prefs

/**
 * Il silenzio è quello di Android. Sordina lo accende solo quando glielo chiedi ("sordina per 45 minuti")
 * e lo spegne alla fine; non lo tocca mai da sola e non cambia le tue modalità.
 */
class Silence(private val context: Context, private val prefs: Prefs) {
    private val nm = context.getSystemService(NotificationManager::class.java)

    fun hasAccess(): Boolean = nm.isNotificationPolicyAccessGranted

    /** Una modalità è accesa (da Sordina o da altri). */
    fun isActive(): Boolean = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL &&
        nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN

    /** Non disturbare fino a [until]. Da Android 15 diventa la modalità "Sordina", con le sue regole, accanto alle tue. */
    fun start(until: Long): Boolean {
        if (!hasAccess()) return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= 35) {
                // Solo la modalità di Sordina: chiamate e messaggi dei preferiti, chiamate ripetute, sveglie, musica
                nm.notificationPolicy = Policy(
                    Policy.PRIORITY_CATEGORY_CALLS or Policy.PRIORITY_CATEGORY_REPEAT_CALLERS or Policy.PRIORITY_CATEGORY_MESSAGES or
                        Policy.PRIORITY_CATEGORY_ALARMS or Policy.PRIORITY_CATEGORY_MEDIA,
                    Policy.PRIORITY_SENDERS_STARRED, Policy.PRIORITY_SENDERS_STARRED, 0,
                )
            }
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            prefs.quietUntil = until
            prefs.quietOwned = true
        }.isSuccess
    }

    /** Spegne il silenzio acceso da Sordina (quello acceso da te o da una modalità resta). */
    fun stop() {
        val owned = prefs.quietOwned
        prefs.quietOwned = false
        prefs.quietUntil = 0L
        if (owned && hasAccess()) runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL) }
    }

    /** Ai cambi e allo scadere: finito il tempo, si spegne; spento a mano, Sordina se ne dimentica. */
    fun check(now: Long = System.currentTimeMillis()) {
        // Prima Sordina poteva gestire Non disturbare tutto il giorno: lo restituisce com'era
        if (prefs.silenceManaged) {
            prefs.silenceManaged = false
            if (hasAccess()) runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL) }
        }
        if (!prefs.quietOwned) return
        if (prefs.quietUntil <= now || !isActive()) stop()
    }
}
