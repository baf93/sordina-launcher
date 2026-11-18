package app.sordina.service

import android.app.NotificationManager
import android.app.NotificationManager.Policy
import android.content.Context
import android.os.Build
import app.sordina.SordinaApp
import app.sordina.data.Prefs

/**
 * Il silenzio è quello di Android. Sordina lo accende solo in due casi e lo spegne da sola alla fine:
 * quando glielo chiedi ("sordina per 45 minuti") e in auto con Android Auto (se lo lasci acceso).
 * Non tocca mai il silenzio acceso da te o da una tua modalità.
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
            prefs.quietCar = false
            policy(messagesFromAnyone = false)
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            prefs.quietUntil = until
            prefs.quietOwned = true
        }.isSuccess
    }

    /** Spegne il silenzio acceso da Sordina (quello acceso da te o da una modalità resta). */
    fun stop() {
        val owned = prefs.quietOwned
        prefs.quietOwned = false
        prefs.quietCar = false
        prefs.quietUntil = 0L
        if (owned && hasAccess()) runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL) }
    }

    /**
     * Le regole del silenzio di Sordina (solo da Android 15: prima cambierebbero il tuo Non disturbare).
     * Passano chiamate e messaggi dei preferiti (le tue persone importanti), chiamate ripetute,
     * conversazioni prioritarie, sveglie e musica. [messagesFromAnyone]: mentre stai scrivendo a qualcuno.
     */
    private fun policy(messagesFromAnyone: Boolean) {
        if (Build.VERSION.SDK_INT < 35) return
        // Chiamate: le persone importanti (i preferiti), in auto tutti i contatti, e chi richiama entro pochi minuti. Sveglie e musica sempre.
        // Messaggi: solo i preferiti, o tutti mentre stai scrivendo a qualcuno. Anche in auto: Android Auto annuncia
        // una notifica appena arriva, prima che Sordina la metta in sordina, quindi è Non disturbare a doverla fermare.
        val car = prefs.quietCar
        val messages = if (messagesFromAnyone && prefs.talkPass) Policy.PRIORITY_SENDERS_ANY else Policy.PRIORITY_SENDERS_STARRED
        val categories = Policy.PRIORITY_CATEGORY_CALLS or Policy.PRIORITY_CATEGORY_REPEAT_CALLERS or Policy.PRIORITY_CATEGORY_MESSAGES or
            Policy.PRIORITY_CATEGORY_CONVERSATIONS or Policy.PRIORITY_CATEGORY_ALARMS or Policy.PRIORITY_CATEGORY_MEDIA
        val conversations = Policy.CONVERSATION_SENDERS_IMPORTANT
        // Chiamate in auto: anche tutti i tuoi contatti (con le mani libere una chiamata si prende); zitti solo i numeri sconosciuti
        val calls = if (car) Policy.PRIORITY_SENDERS_CONTACTS else Policy.PRIORITY_SENDERS_STARRED
        val wanted = Policy(categories, calls, messages, 0, conversations)
        val now = nm.notificationPolicy
        if (now.priorityCategories != wanted.priorityCategories || now.priorityMessageSenders != wanted.priorityMessageSenders ||
            now.priorityCallSenders != wanted.priorityCallSenders || now.priorityConversationSenders != wanted.priorityConversationSenders) nm.notificationPolicy = wanted
    }

    /** Ai cambi e allo scadere: finito il tempo, si spegne; spento a mano, Sordina se ne dimentica. In auto, si accende. */
    fun check(now: Long = System.currentTimeMillis()) {
        // Prima Sordina poteva gestire Non disturbare tutto il giorno: lo restituisce com'era
        if (prefs.silenceManaged) {
            prefs.silenceManaged = false
            if (hasAccess()) runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL) }
        }
        val inCar = runCatching { SordinaApp.of(context).moments.isCar() }.getOrDefault(false)
        if (!inCar) prefs.carDeclined = false
        if (prefs.quietCar) {
            when {
                // Sceso dall'auto (o hai spento l'opzione): si spegne
                !inCar || !prefs.carQuiet -> stop()
                // Spento a mano durante il viaggio: resta spento fino al prossimo
                !isActive() -> { prefs.carDeclined = true; prefs.quietOwned = false; prefs.quietCar = false }
                // Mentre scrivi a qualcuno passano i messaggi di tutti; finita la conversazione, solo i preferiti
                else -> runCatching { policy(messagesFromAnyone = Talking.lastUntil(now) != null) }
            }
            return
        }
        if (inCar && prefs.carQuiet && !prefs.carDeclined && !isActive() && hasAccess()) {
            runCatching {
                // Prima segna che è il silenzio dell'auto: così valgono le tue scelte per l'auto
                prefs.quietCar = true
                policy(messagesFromAnyone = Talking.lastUntil(now) != null)
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                prefs.quietOwned = true
                prefs.quietUntil = Long.MAX_VALUE
            }
            return
        }
        if (!prefs.quietOwned) return
        if (prefs.quietUntil <= now || !isActive()) stop()
    }
}
