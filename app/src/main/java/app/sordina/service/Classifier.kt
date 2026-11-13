package app.sordina.service

import android.app.Notification
import android.os.Build
import android.service.notification.StatusBarNotification
import app.sordina.data.CardDetector
import app.sordina.data.Level
import app.sordina.data.Moment
import app.sordina.data.People
import app.sordina.data.Prefs
import app.sordina.data.Search

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
    /** Componente di sistema senza icona nel cassetto (auricolari, Bluetooth, Play Services…) */
    val system: Boolean = false,
    /** Il tipo di notifica (canale dell'app), per le regole per tipo */
    val channel: String? = null,
    /** Quanti messaggi di fila la stessa persona ha scritto negli ultimi minuti (insiste?) */
    val burst: Int = 1,
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

    /** Parole che indicano un codice da digitare (non "autorizzato", "sicurezza"…: compaiono nei pagamenti). */
    private val OTP_WORDS = Regex(
        "\\b(codice|code|otp|verifica|verification|password|passcode|pin|monouso|one[- ]time)\\b",
        RegexOption.IGNORE_CASE,
    )
    /** "Codice" che non è un codice di accesso. */
    private val NOT_OTP = Regex(
        "\\b(codice|code)\\s+(id|fiscale|cliente|pratica|ordine|order|sconto|promo|promozionale|coupon|tracking|spedizione|postale|iban|articolo|prodotto|protocollo|riferimento|reference|identificativo|segnalazione|partner)\\b",
        RegexOption.IGNORE_CASE,
    )
    /** Un numero da solo: niente pezzi di date, orari, importi, carte mascherate (XXXX1234) o codici alfanumerici. */
    private val OTP_NUMBER = Regex("(?<![\\w/.,:€$#+-])(\\d{4,8}|\\d{3}[- ]\\d{3})(?![\\w/,:%€$]|[.,]\\d)|(?<=\\bG-)\\d{6}\\b")
    private val YEAR = Regex("(19|20)\\d\\d")
    /** Il corpo di una mail (risposte, inoltri): lì "codice" e numeri sono quasi sempre altro. */
    private val MAIL_BODY = Regex("Messaggio originale|Original Message|-----|\\b(Da|From|Inviato|Sent|Oggetto|Subject):", RegexOption.IGNORE_CASE)

    /** Un codice di accesso: la parola e il numero vicini (entro poche parole), in un testo breve. */
    fun isOtp(text: String): Boolean {
        if (text.length > 300 || MAIL_BODY.containsMatchIn(text)) return false
        val clean = NOT_OTP.replace(text, " ")
        val words = OTP_WORDS.findAll(clean).map { it.range }.toList()
        if (words.isEmpty()) return false
        return OTP_NUMBER.findAll(clean).any { m ->
            !(m.value.length == 4 && YEAR.matches(m.value)) &&
                words.any { w -> m.range.first - w.last in 1..40 || w.first - m.range.last in 1..40 }
        }
    }

    /** Parole che rendono urgente un messaggio (di serie). */
    val URGENT_WORDS = listOf(
        "urgente", "urgenza", "emergenza", "chiamami", "richiamami", "chiamami subito", "rispondimi", "aiuto", "sos",
        "ospedale", "pronto soccorso", "incidente", "urgent", "emergency", "call me", "asap",
    )

    /** Un messaggio urgente: contiene una delle parole (intere, senza badare a maiuscole e accenti). */
    fun isUrgent(text: String, words: List<String> = URGENT_WORDS): Boolean {
        val t = " " + Search.normalize(text) + " "
        return words.map { Search.normalize(it) }.filter { it.isNotBlank() }.any { w -> Regex("(?<![\\p{L}\\d])" + Regex.escape(w) + "(?![\\p{L}\\d])").containsMatchIn(t) }
    }

    /** Messaggi di fila dalla stessa persona entro pochi minuti: oltre questa soglia "insiste". */
    const val BURST = 3
    const val BURST_MILLIS = 5 * 60_000L

    /** Caratteri invisibili che le mail usano per riempire l'anteprima (͏ ‌ ﻿ …) e spazi di troppo. */
    private val INVISIBLE = Regex("[\\u034F\\u00AD\\u180E\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u2064\\uFEFF]")
    fun clean(s: String): String = INVISIBLE.replace(s, "")
        .replace(Regex("[ \\t\\u00A0]+"), " ")
        .replace(Regex(" *\\n[ \\n]*"), "\n")
        .trim()

    /** Chiave di una regola per tipo di notifica (canale dell'app). */
    fun channelKey(pkg: String, channel: String?) = "$pkg|${channel.orEmpty()}"

    /** Chiave di una regola per persona o gruppo: il gruppo se c'è, altrimenti chi scrive. */
    fun personKey(n: NotifInfo): String? = if (n.isMessage) (n.conversation ?: n.person)?.let { Search.normalize(it) }?.ifBlank { null } else null

    /**
     * Le regole, dalla più specifica alla più generale:
     * tipo di notifica (canale) > "lascia passare per qualche ora" > persona o gruppo > app > le regole di Sordina.
     * Una modalità di Android accesa trattiene anche quello che passerebbe in silenzio.
     */
    fun classify(n: NotifInfo, self: String, prefs: Prefs, people: People, moment: Moment): Verdict {
        // 1. Mai toccate
        if (n.pkg == self) return Verdict(null, "Sordina")
        if (n.pkg in SYSTEM_PKGS) return Verdict(null, "sistema")
        val quiet = moment == Moment.QUIET
        fun ruled(level: Level, why: String) = when {
            level == Level.SUBITO && quiet -> Verdict(Level.RIEPILOGO, "modalità")
            else -> Verdict(level, why)
        }

        // 2. Regola per questo tipo di notifica: vale anche per quelle fisse (la notifica sempre accesa di un'app)
        prefs.channelRules[channelKey(n.pkg, n.channel)]?.let { return ruled(it, "regola del tipo") }

        // 3. Chiamate, sveglie, navigazione, musica, notifiche in corso: passano come sono
        if (n.system && prefs.systemPass) return Verdict(null, "sistema")
        if (n.category in PASS_CATEGORIES) return Verdict(null, "chiamata, sveglia o servizio")
        if (n.isMedia) return Verdict(null, "musica")
        if (n.ongoing) return Verdict(null, "in corso")

        val who = personKey(n)
        // 4. Lasciata passare per qualche ora dalla pagina Sordina: vince sulle tue regole per persona e app, per quelle ore
        val now = System.currentTimeMillis()
        val pass = prefs.passUntil
        if ((pass["a:" + n.pkg] ?: 0L) > now || (who != null && (pass["p:$who"] ?: 0L) > now)) return Verdict(null, "lasciata passare")

        // 5. Regola per persona o gruppo ("In sordina" vince anche su codici e persone importanti: l'hai scelto tu)
        if (who != null) prefs.personRules[who]?.let { return ruled(it, "regola della persona") }

        // 6. Codici di accesso
        if (isOtp(n.title + " " + n.text)) return Verdict(Level.SUBITO, "codice di accesso")

        // Conversazione in corso: hai appena letto o risposto a questa persona
        if (n.isMessage && Talking.active(n.pkg, n.conversation ?: n.person ?: "", now)) return Verdict(null, "conversazione in corso")

        // 7. Regola dell'app "Passa sempre"
        val appLevel = prefs.appLevels[n.pkg]
        if (appLevel == Level.SEMPRE) return Verdict(Level.SEMPRE, "regola dell'app")

        // 8. Persone importanti e urgenze
        if (n.isMessage && (people.isImportant(n.person) || (n.conversation == null && people.isImportant(n.title)))) {
            return Verdict(Level.SUBITO, "persona importante")
        }
        if (prefs.urgentEnabled && n.isMessage && n.conversation == null && n.pkg !in prefs.braked) {
            val words = prefs.urgentWords.split(',').map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { URGENT_WORDS }
            if (isUrgent(n.text, words)) return Verdict(Level.SUBITO, "urgente")
            if (n.burst >= BURST) return Verdict(Level.SUBITO, "insiste")
        }

        // 9. Regola dell'app
        if (appLevel != null) return ruled(appLevel, "regola dell'app")

        // 10. Le regole di Sordina
        // Pagamenti: li vedi nella scheda in Casa, la notifica resta in sordina
        if (!n.isMessage && CardDetector.paymentCents(n.title + " " + n.text) != null) return Verdict(Level.RIEPILOGO, "pagamento")
        if (n.pkg in prefs.braked) return Verdict(Level.RIEPILOGO, "app frenata")
        // Chiamate perse, promemoria ed eventi: ti servono adesso
        if (n.category == "missed_call") return ruled(Level.SUBITO, "chiamata persa")
        if (n.category == Notification.CATEGORY_REMINDER || n.category == Notification.CATEGORY_EVENT) return ruled(Level.SUBITO, "promemoria")
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
        var burst = 1
        fun senderOf(b: android.os.Bundle): String? = (if (Build.VERSION.SDK_INT >= 28) {
            @Suppress("DEPRECATION")
            (b.get("sender_person") as? android.app.Person)?.name?.toString()
        } else null) ?: b.getCharSequence("sender")?.toString()
        if (!messages.isNullOrEmpty()) {
            count = messages.size
            val last = messages.last() as? android.os.Bundle
            if (last != null) {
                text = last.getCharSequence("text")?.toString() ?: text
                person = senderOf(last)
                // Quanti messaggi di fila, della stessa persona, negli ultimi minuti
                val since = System.currentTimeMillis() - BURST_MILLIS
                burst = messages.toList().asReversed().mapNotNull { it as? android.os.Bundle }
                    .takeWhile { senderOf(it) == person }
                    .count { it.getLong("time") >= since }.coerceAtLeast(1)
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
            text = clean(text),
            title = clean(title),
            isMessage = isMessage,
            count = count,
            burst = burst,
            channel = n.channelId,
        )
    }

    private val MESSAGING_PKGS = setOf(
        "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.telegram.messenger.web",
        "org.thoughtcrime.securesms", "com.google.android.apps.messaging", "com.samsung.android.messaging",
        "com.facebook.orca", "com.instagram.android", "com.discord", "com.Slack", "com.microsoft.teams",
    )
}
