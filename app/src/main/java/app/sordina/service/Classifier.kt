package app.sordina.service

import android.app.Notification
import android.os.Build
import android.service.notification.StatusBarNotification
import app.sordina.data.Level
import app.sordina.data.Moment
import app.sordina.data.People
import app.sordina.data.Prefs

/** Quello che si legge di una notifica, senza dipendere da Android (si può provare a parte). */
data class NotifInfo(
    val pkg: String,
    val category: String?,
    val ongoing: Boolean,
    val isMedia: Boolean,
    val isGroupSummary: Boolean,
    val person: String?,
    val conversation: String?,
    val text: String,
    val title: String,
    val isMessage: Boolean,
    val count: Int,
)

/** null = la notifica passa intatta, senza che Sordina la tocchi. */
data class Verdict(val level: Level?, val reason: String)

object Classifier {

    private val PASS_CATEGORIES = setOf(
        Notification.CATEGORY_CALL, Notification.CATEGORY_ALARM, Notification.CATEGORY_NAVIGATION,
        Notification.CATEGORY_TRANSPORT, Notification.CATEGORY_PROGRESS, Notification.CATEGORY_SERVICE,
        Notification.CATEGORY_SYSTEM, "stopwatch", "location_sharing", "car_emergency", "car_warning",
    )
    private val SYSTEM_PKGS = setOf("android", "com.android.systemui", "com.android.phone", "com.samsung.android.incallui", "com.google.android.dialer.incall")

    private val OTP_WORDS = Regex(
        "(codice|code|otp|verifica|verification|password|passcode|pin|conferma|autorizza|authoriz|sicurezza|security|accesso|login|token|3d ?secure)",
        RegexOption.IGNORE_CASE,
    )
    private val OTP_NUMBER = Regex("(?<![\\d.,])\\d{4,8}(?![\\d.,])|\\b\\d{3}[- ]\\d{3}\\b")

    fun isOtp(text: String): Boolean = OTP_WORDS.containsMatchIn(text) && OTP_NUMBER.containsMatchIn(text)

    fun classify(n: NotifInfo, self: String, prefs: Prefs, people: People, moment: Moment): Verdict {
        // 1. Sempre intatte
        if (n.pkg == self) return Verdict(null, "Sordina")
        if (n.pkg in SYSTEM_PKGS) return Verdict(null, "sistema")
        if (n.category in PASS_CATEGORIES) return Verdict(null, "chiamata, sveglia o servizio")
        if (n.isMedia) return Verdict(null, "musica")
        if (n.ongoing) return Verdict(null, "in corso")

        // 2. Codici di accesso
        if (isOtp(n.title + " " + n.text)) return Verdict(Level.SUBITO, "codice di accesso")

        // 3. Livello scelto per l'app (gli allarmi di Home Assistant passano dall'app con "Sempre")
        val appLevel = prefs.appLevels[n.pkg]
        if (appLevel == Level.SEMPRE) return Verdict(Level.SEMPRE, "l'app passa sempre")

        // 4. Persone importanti
        if (n.isMessage && (people.isImportant(n.person) || (n.conversation == null && people.isImportant(n.title)))) {
            return Verdict(Level.SUBITO, "persona importante")
        }
        // Chiamate perse, promemoria ed eventi: ti servono adesso
        if (n.category == "missed_call") return Verdict(Level.SUBITO, "chiamata persa")
        if (n.category == Notification.CATEGORY_REMINDER || n.category == Notification.CATEGORY_EVENT) {
            return Verdict(Level.SUBITO, "promemoria")
        }

        // 5. Livello dell'app, ma di notte e in auto aspetta anche "Subito"
        if (appLevel == Level.SUBITO) {
            return if (moment == Moment.NONE) Verdict(Level.SUBITO, "scelta per l'app")
            else Verdict(Level.RIEPILOGO, moment.label.lowercase())
        }
        if (appLevel == Level.RIEPILOGO) return Verdict(Level.RIEPILOGO, "scelta per l'app")

        // 6. Predefinito
        return Verdict(Level.RIEPILOGO, "predefinito")
    }

    /** Estrae le informazioni utili da una notifica. */
    fun read(sbn: StatusBarNotification): NotifInfo {
        val n = sbn.notification
        val e = n.extras
        val title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val bigText = e.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        var text = bigText ?: e.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val convTitle = e.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
        var person: String? = null
        var count = 1
        val messages = runCatching {
            @Suppress("DEPRECATION")
            e.getParcelableArray(Notification.EXTRA_MESSAGES)
        }.getOrNull()
        val isGroupConv = if (Build.VERSION.SDK_INT >= 28) e.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION) else convTitle != null
        if (!messages.isNullOrEmpty()) {
            count = messages.size
            val last = messages.last() as? android.os.Bundle
            if (last != null) {
                text = last.getCharSequence("text")?.toString() ?: text
                val sender = if (Build.VERSION.SDK_INT >= 28) {
                    @Suppress("DEPRECATION")
                    (last.get("sender_person") as? android.app.Person)?.name?.toString()
                } else null
                person = sender ?: last.getCharSequence("sender")?.toString()
            }
        }
        val isMessage = n.category == Notification.CATEGORY_MESSAGE || !messages.isNullOrEmpty() ||
            convTitle != null || sbn.packageName in MESSAGING_PKGS
        if (person == null && isMessage) {
            // "Gruppo: Persona" (stile WhatsApp) o solo "Persona"
            person = if (convTitle == null && title.contains(": ")) title.substringAfter(": ") else title.ifBlank { null }
        }
        var conversation: String? = convTitle?.takeIf { isGroupConv || it != person }
        if (conversation == null && isMessage && title.contains(": ") && isGroupConv) conversation = title.substringBefore(": ")
        // Riassunti tipo "3 nuovi messaggi" non sono messaggi veri
        conversation = conversation?.replace(Regex("\\s*\\(\\d+ (messaggi|messages)\\)$"), "")
        return NotifInfo(
            pkg = sbn.packageName,
            category = n.category,
            ongoing = sbn.isOngoing || (n.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0,
            isMedia = e.containsKey(Notification.EXTRA_MEDIA_SESSION),
            isGroupSummary = (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0,
            person = person?.trim()?.ifBlank { null },
            conversation = conversation?.trim()?.ifBlank { null },
            text = text.trim(),
            title = title.trim(),
            isMessage = isMessage,
            count = count,
        )
    }

    private val MESSAGING_PKGS = setOf(
        "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.telegram.messenger.web",
        "org.thoughtcrime.securesms", "com.google.android.apps.messaging", "com.samsung.android.messaging",
        "com.facebook.orca", "com.instagram.android", "com.discord", "com.Slack", "com.microsoft.teams",
    )
}
