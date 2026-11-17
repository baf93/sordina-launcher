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
    /** L'utente Android (profilo personale o di lavoro) */
    val user: Int = 0,
    /** L'app è fra quelle nascoste (per questo utente) */
    val hidden: Boolean = false,
    /** Con chi è la conversazione: il gruppo, o l'altra persona (anche quando l'ultimo messaggio è tuo) */
    val peer: String? = null,
)

/**
 * Le regole in un istante, immutabili: si costruiscono una volta per evento e rendono [Classifier.classify]
 * una funzione pura (si prova senza Android).
 */
data class RuleSnapshot(
    val self: String,
    val now: Long,
    val quiet: Boolean = false,
    val channelRules: Map<String, Level> = emptyMap(),
    val systemPass: Boolean = true,
    val passUntil: Map<String, Long> = emptyMap(),
    val personRules: Map<String, Level> = emptyMap(),
    val braked: Set<String> = emptySet(),
    val appLevels: Map<String, Level> = emptyMap(),
    val talkPass: Boolean = true,
    /** Conversazioni in corso adesso ([Classifier.talkKey]) */
    val talking: Set<String> = emptySet(),
    val important: Set<String> = emptySet(),
    val importantInGroups: Set<String> = emptySet(),
    val urgentEnabled: Boolean = true,
    val urgentWords: List<String> = Classifier.URGENT_WORDS,
    /** I nomi della rubrica, normalizzati */
    val contacts: Set<String> = emptySet(),
) {
    companion object {
        fun of(self: String, prefs: Prefs, people: People, moment: Moment, now: Long = System.currentTimeMillis()) = RuleSnapshot(
            self = self, now = now, quiet = moment == Moment.QUIET,
            channelRules = prefs.channelRules, systemPass = prefs.systemPass, passUntil = prefs.passUntil,
            personRules = prefs.personRules, braked = prefs.braked, appLevels = prefs.appLevels,
            talkPass = prefs.talkPass, talking = Talking.activeKeys(now),
            important = prefs.importantNames, importantInGroups = prefs.importantInGroups,
            urgentEnabled = prefs.urgentEnabled,
            urgentWords = prefs.urgentWords.split(',').map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { Classifier.URGENT_WORDS },
            contacts = people.normalizedNames(),
        )
    }
}

/** null = la notifica passa intatta, senza che Sordina la tocchi. */
data class Verdict(val level: Level?, val reason: String)

object Classifier {

    private val PASS_CATEGORIES = setOf(
        Notification.CATEGORY_CALL, Notification.CATEGORY_ALARM, Notification.CATEGORY_NAVIGATION,
        Notification.CATEGORY_TRANSPORT, Notification.CATEGORY_PROGRESS, Notification.CATEGORY_SERVICE,
        Notification.CATEGORY_SYSTEM, "stopwatch", "location_sharing", "car_emergency", "car_warning",
    )
    private val SYSTEM_PKGS = setOf("android", "com.android.systemui", "com.android.phone", "com.samsung.android.incallui", "com.google.android.dialer.incall")

    /** Parole che da sole indicano un codice da digitare (non "autorizzato", "sicurezza"…: compaiono nei pagamenti). */
    private val OTP_STRONG = Regex(
        "\\b(codice|code|otp|passcode|monouso|one[- ]time|chiave|2fa)\\b",
        RegexOption.IGNORE_CASE,
    )
    /** Parole ambigue ("il pin del portone", "la password del wifi"): valgono solo con un segno che è un accesso a un servizio. */
    private val OTP_WEAK = Regex("\\b(verifica|verification|password|pin)\\b", RegexOption.IGNORE_CASE)
    private val SERVICE = Regex(
        "(non condivider|do not share|don't share|never share|valid|scad|expire|minut|accedere|accesso|login|log in|sign[- ]?in|autentica|authenticat|identit|conferma|confirm|account)",
        RegexOption.IGNORE_CASE,
    )
    /** "Codice" che non è un codice di accesso. */
    private val NOT_OTP = Regex(
        "\\b(codice|code)\\s+(id|fiscale|cliente|pratica|ordine|order|sconto|promo|promozionale|coupon|tracking|spedizione|postale|iban|articolo|prodotto|protocollo|riferimento|reference|identificativo|segnalazione|partner)\\b",
        RegexOption.IGNORE_CASE,
    )
    /** Un numero da solo: niente pezzi di date, orari, importi, carte mascherate (XXXX1234) o codici alfanumerici. */
    private val OTP_NUMBER = Regex("(?<![\\w/.,:€$#+-])(\\d{4,8}|\\d{3}[- ]\\d{3})(?![\\w/:%€$]|[.,]\\d)|(?<=\\bG-)\\d{6}\\b")
    private val YEAR = Regex("(19|20)\\d\\d")
    /**
     * Una risposta o un inoltro (il messaggio citato sotto): lì "codice" e numeri sono quasi sempre altro.
     * Non basta una riga di trattini o un "Da:": i modelli delle mail con il codice li usano come separatori.
     */
    private val MAIL_BODY = Regex(
        "Messaggio originale|Original Message|Forwarded message|Messaggio inoltrato|\\b(Da|From):[\\s\\S]{0,200}?\\b(Inviato|Sent|Data|Date|Oggetto|Subject):",
        RegexOption.IGNORE_CASE,
    )
    /** Una mail lunga: il codice sta in cima (oggetto e prime righe), il resto è firma, privacy e pubblicità. */
    private const val SHORT = 300
    private const val MAIL_HEAD = 600

    /**
     * Un codice di accesso: una parola del codice e il numero vicini (entro 40 caratteri).
     * Le parole ambigue (pin, password, verifica) contano solo se il testo parla di un accesso a un servizio.
     * Un testo lungo (una mail) si legge solo in cima e solo con una parola forte (codice, OTP, monouso…).
     */
    fun isOtp(text: String): Boolean = otpNumber(text) != null

    /** Il numero del codice di accesso (solo cifre), quello vicino alla parola: in una mail non è il primo numero che capita. */
    fun otpNumber(text: String): String? {
        if (MAIL_BODY.containsMatchIn(text)) return null
        val long = text.length > SHORT
        val clean = NOT_OTP.replace(if (long) text.take(MAIL_HEAD) else text, " ")
        val words = OTP_STRONG.findAll(clean).map { it.range }.toList() +
            (if (!long && SERVICE.containsMatchIn(clean)) OTP_WEAK.findAll(clean).map { it.range }.toList() else emptyList())
        if (words.isEmpty()) return null
        // Una mail lunga deve anche dire che è un accesso (scadenza, non condividere, accedi…): le newsletter no
        if (long && !SERVICE.containsMatchIn(clean)) return null
        return OTP_NUMBER.findAll(clean).firstOrNull { m ->
            !(m.value.length == 4 && YEAR.matches(m.value)) &&
                words.any { w -> m.range.first - w.last in 1..40 || w.first - m.range.last in 1..40 }
        }?.value?.filter { it.isDigit() }
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

    /**
     * "Insiste": almeno 8 messaggi di fila della stessa persona, senza che tu abbia risposto, nell'ultima mezz'ora,
     * sparsi su almeno 10 minuti. Una chiacchierata scritta di getto non è insistere: continuare a cercarti sì.
     * Chi scrive "urgente" passa comunque, subito.
     */
    const val BURST = 8
    const val BURST_MILLIS = 30 * 60_000L
    const val BURST_SPAN = 10 * 60_000L

    /** Caratteri invisibili che le mail usano per riempire l'anteprima (U+034F, U+200C, U+FEFF…) e spazi di troppo. */
    private val INVISIBLE = Regex("[\\u034F\\u00AD\\u180E\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u2064\\uFEFF]")
    fun clean(s: String): String = INVISIBLE.replace(s, "")
        .replace(Regex("[ \\t\\u00A0]+"), " ")
        .replace(Regex(" *\\n[ \\n]*"), "\n")
        .trim()

    /** Chiave di una regola per tipo di notifica (canale dell'app). */
    fun channelKey(pkg: String, channel: String?) = "$pkg|${channel.orEmpty()}"

    /** Chiave di una regola per persona o gruppo: il gruppo se c'è, altrimenti chi scrive. */
    fun personKey(n: NotifInfo): String? = if (n.isMessage) (n.conversation ?: n.person)?.let { Search.normalize(it) }?.ifBlank { null } else null

    /** Le app i cui nomi vengono dalla rubrica: lì "Anna" basta per "Anna Rossi". Altrove (Telegram, social) il nome lo sceglie chi scrive. */
    val CONTACT_NAMED = setOf(
        "com.whatsapp", "com.whatsapp.w4b", "org.thoughtcrime.securesms", "com.google.android.apps.messaging",
        "com.samsung.android.messaging", "com.android.mms", "com.google.android.dialer", "com.samsung.android.dialer",
    )

    /** Avvisi di sicurezza su pagamenti: frodi, autorizzazioni da confermare, rifiuti. Non aspettano mai. */
    private val PAY_ALERT = Regex(
        "(sospett|frod|fraud|non autorizzat|unauthori[sz]ed|rifiutat|declined|negat|bloccat|blocked|sei stato tu|was this you|" +
            "conferma(re)? (il |l'|questo |questa )?(pagament|acquist|transazion|operazion)|approva|autorizza (il|l'|questo|questa)|" +
            "confirm (the |this )?(payment|purchase|transaction)|approve|entro \\d+ minut|within \\d+ minut)",
        RegexOption.IGNORE_CASE,
    )

    fun isPaymentAlert(text: String): Boolean = PAY_ALERT.containsMatchIn(text)

    /** I motivi che in una modalità di Android (QUIET) aspettano invece di passare in silenzio. */
    private val QUIET_HOLDS = setOf(REASON_CHANNEL, REASON_PERSON, REASON_APP, REASON_MISSED, REASON_REMINDER)

    const val REASON_CHANNEL = "regola del tipo"
    const val REASON_PERSON = "regola della persona"
    const val REASON_APP = "regola dell'app"
    const val REASON_BRAKED = "app frenata"
    const val REASON_HIDDEN = "app nascosta"
    const val REASON_OTP = "codice di accesso"
    const val REASON_PAY_ALERT = "avviso di pagamento"
    const val REASON_MISSED = "chiamata persa"
    const val REASON_REMINDER = "promemoria"
    const val REASON_DEFAULT = "predefinito"
    /** Le scelte tue che mettono in sordina: lì Sordina non deve riportare niente davanti (schede, proposte). */
    val MUTED_BY_YOU = setOf(REASON_CHANNEL, REASON_PERSON, REASON_APP, REASON_BRAKED, REASON_HIDDEN)

    /**
     * Le regole, dalla più specifica alla più generale. Funzione pura: stesso ingresso, stesso verdetto.
     *
     *  1. Sordina stessa e i componenti di sistema: mai toccati
     *  2. Regola per tipo di notifica (canale)
     *  3. Chiamate, sveglie, navigazione, musica, notifiche in corso: passano come sono
     *  4. "Lascia passare per qualche ora"
     *  5. Regola per persona o gruppo (in questa app)
     *  6. App frenata: aspetta (vince solo "Passa sempre" per lei)
     *  7. Sicurezza: codici di accesso e avvisi di pagamento passano
     *  8. App nascosta: aspetta, senza mostrarsi
     *  9. Conversazione in corso: passa
     * 10. "Passa sempre" per l'app
     * 11. Persone importanti; urgenze e insistenza solo da chi è in rubrica
     * 12. Regola dell'app
     * 13. Le regole di Sordina: pagamenti eseguiti aspettano; chiamate perse e promemoria passano; il resto aspetta
     *
     * Con una modalità di Android accesa ([RuleSnapshot.quiet]) aspettano anche le notifiche che passerebbero
     * per i motori in [QUIET_HOLDS]; sicurezza, persone importanti e urgenze restano visibili.
     */
    fun classify(n: NotifInfo, s: RuleSnapshot): Verdict {
        if (n.pkg == s.self) return Verdict(null, "Sordina")
        if (n.pkg in SYSTEM_PKGS) return Verdict(null, "sistema")
        fun ruled(level: Level, why: String) =
            if (level == Level.SUBITO && s.quiet && why in QUIET_HOLDS) Verdict(Level.RIEPILOGO, "modalità") else Verdict(level, why)

        s.channelRules[channelKey(n.pkg, n.channel)]?.let { return ruled(it, REASON_CHANNEL) }

        if (n.system && s.systemPass) return Verdict(null, "sistema")
        if (n.category in PASS_CATEGORIES) return Verdict(null, "chiamata, sveglia o servizio")
        if (n.isMedia) return Verdict(null, "musica")
        if (n.ongoing) return Verdict(null, "in corso")

        val who = personKey(n)
        if ((s.passUntil["a:" + n.pkg] ?: 0L) > s.now || (who != null && (s.passUntil["p:$who"] ?: 0L) > s.now)) return Verdict(null, "lasciata passare")

        if (who != null) (s.personRules[personRuleKey(n.pkg, who)] ?: s.personRules[who])?.let { return ruled(it, REASON_PERSON) }

        val appLevel = s.appLevels[n.pkg]
        if (n.pkg in s.braked && appLevel != Level.SEMPRE) return Verdict(Level.RIEPILOGO, REASON_BRAKED)

        val all = n.title + " " + n.text
        if (isOtp(all)) return Verdict(Level.SUBITO, REASON_OTP)
        if (!n.isMessage && isPaymentAlert(all)) return Verdict(Level.SUBITO, REASON_PAY_ALERT)

        if (n.hidden && appLevel != Level.SEMPRE) return Verdict(Level.RIEPILOGO, REASON_HIDDEN)

        if (n.isMessage && s.talkPass && n.peer != null && talkKey(n.pkg, n.peer) in s.talking) return Verdict(null, "conversazione in corso")

        if (appLevel == Level.SEMPRE) return Verdict(Level.SEMPRE, REASON_APP)

        if (n.isMessage) {
            val inGroup = n.conversation != null
            val exact = n.pkg !in CONTACT_NAMED
            val imp = importantKey(n.person, s.important, exact) ?: if (!inGroup) importantKey(n.title, s.important, exact) else null
            if (imp != null && (!inGroup || imp in s.importantInGroups)) return Verdict(Level.SUBITO, "persona importante")
        }
        // Urgenze: solo in una chat a due, solo da qualcuno che è nella tua rubrica (uno sconosciuto non decide quanto conta)
        if (s.urgentEnabled && n.isMessage && n.conversation == null && n.person != null && Search.normalize(n.person) in s.contacts) {
            if (isUrgent(n.text, s.urgentWords)) return Verdict(Level.SUBITO, "urgente")
            if (n.burst >= BURST) return Verdict(Level.SUBITO, "insiste")
        }

        if (appLevel != null) return ruled(appLevel, REASON_APP)

        if (!n.isMessage && CardDetector.paymentCents(all) != null) return Verdict(Level.RIEPILOGO, "pagamento")
        if (n.category == "missed_call") return ruled(Level.SUBITO, REASON_MISSED)
        if (n.category == Notification.CATEGORY_REMINDER || n.category == Notification.CATEGORY_EVENT) return ruled(Level.SUBITO, REASON_REMINDER)
        return Verdict(Level.RIEPILOGO, REASON_DEFAULT)
    }

    /** Regola per persona dentro un'app: "pacchetto|nome". Le regole vecchie (solo il nome) valgono ancora per tutte le app. */
    fun personRuleKey(pkg: String, who: String) = "$pkg|$who"

    /** La chiave di una conversazione in corso. */
    fun talkKey(pkg: String, peer: String) = pkg + "|" + Search.normalize(peer)

    /**
     * Quale persona importante è [name]. [exact]: solo il nome completo (app dove il nome lo sceglie chi scrive).
     * Altrimenti "Anna" vale per "Anna Rossi" solo se c'è una sola Anna fra le importanti.
     */
    fun importantKey(name: String?, important: Set<String>, exact: Boolean): String? {
        if (name.isNullOrBlank()) return null
        val n = Search.normalize(name)
        if (n.isEmpty()) return null
        if (n in important) return n
        if (exact || ' ' in n) return null
        return important.filter { it.startsWith("$n ") }.singleOrNull()
    }

    /**
     * Le tue risposte dentro una conversazione (quando rispondi dalla notifica, l'app ci mette anche il tuo messaggio):
     * (quando, cosa ti avevano scritto, cosa hai risposto). Servono a imparare le tue risposte veloci.
     */
    fun replyPairs(sbn: StatusBarNotification): List<Triple<Long, String, String>> = runCatching {
        val e = sbn.notification.extras
        @Suppress("DEPRECATION")
        val msgs = e.getParcelableArray(Notification.EXTRA_MESSAGES)?.mapNotNull { it as? android.os.Bundle } ?: return emptyList()
        if (msgs.size < 2) return emptyList()
        val me = if (Build.VERSION.SDK_INT >= 28) {
            @Suppress("DEPRECATION")
            (e.get(Notification.EXTRA_MESSAGING_PERSON) as? android.app.Person)?.name?.toString()
        } else null
        fun mine(b: android.os.Bundle): Boolean {
            @Suppress("DEPRECATION")
            val p = if (Build.VERSION.SDK_INT >= 28) (b.get("sender_person") as? android.app.Person)?.name?.toString() else null
            val s = p ?: b.getCharSequence("sender")?.toString()
            return s == null || (me != null && s == me)
        }
        val out = ArrayList<Triple<Long, String, String>>()
        for (i in 1 until msgs.size) {
            val a = msgs[i - 1]; val b = msgs[i]
            if (!mine(a) && mine(b)) {
                val incoming = a.getCharSequence("text")?.toString() ?: continue
                val reply = b.getCharSequence("text")?.toString() ?: continue
                out += Triple(b.getLong("time"), incoming, reply)
            }
        }
        out
    }.getOrDefault(emptyList())

    /** L'ultimo messaggio della conversazione l'hai scritto tu (risposta dalla notifica). */
    fun lastIsMine(sbn: StatusBarNotification): Boolean = runCatching {
        val e = sbn.notification.extras
        @Suppress("DEPRECATION")
        val last = e.getParcelableArray(Notification.EXTRA_MESSAGES)?.lastOrNull() as? android.os.Bundle ?: return false
        val me = if (Build.VERSION.SDK_INT >= 28) {
            @Suppress("DEPRECATION")
            (e.get(Notification.EXTRA_MESSAGING_PERSON) as? android.app.Person)?.name?.toString()
        } else null
        @Suppress("DEPRECATION")
        val p = if (Build.VERSION.SDK_INT >= 28) (last.get("sender_person") as? android.app.Person)?.name?.toString() else null
        val s = p ?: last.getCharSequence("sender")?.toString()
        s == null || (me != null && s == me)
    }.getOrDefault(false)

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
                val times = messages.toList().asReversed().mapNotNull { it as? android.os.Bundle }
                    .takeWhile { senderOf(it) == person }
                    .map { it.getLong("time") }.filter { it >= since }
                // Tutti in un attimo: è un messaggio spezzato in più righe, non conta come insistere
                burst = if (times.size >= 2 && times.max() - times.min() >= BURST_SPAN) times.size else 1
            }
        }
        // Un messaggio lo dice la notifica stessa (categoria o stile conversazione), non il nome dell'app:
        // un "mi piace" di Instagram o una riunione di Teams non sono messaggi di una persona
        val isMessage = n.category == Notification.CATEGORY_MESSAGE || !messages.isNullOrEmpty() || convTitle != null
        if (person == null && isMessage) {
            // "Gruppo: Persona" (stile WhatsApp) o solo "Persona"
            person = if (convTitle == null && title.contains(": ")) title.substringAfter(": ") else title.ifBlank { null }
        }
        var conversation: String? = convTitle?.takeIf { isGroupConv || it != person }
        if (conversation == null && isMessage && title.contains(": ") && isGroupConv) conversation = title.substringBefore(": ")
        // Riassunti tipo "3 nuovi messaggi" non sono messaggi veri
        conversation = conversation?.replace(Regex("\\s*\\(\\d+ (messaggi|messages)\\)$"), "")
        val mine = lastIsMine(sbn)
        val peerName = conversation?.trim()?.ifBlank { null }
            ?: (if (mine) title.substringBefore(": ") else person ?: title).trim().ifBlank { null }
        return NotifInfo(
            pkg = sbn.packageName,
            user = sbn.user.hashCode(),
            peer = if (isMessage) peerName?.let { clean(it) } else null,
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
}
