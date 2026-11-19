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
    /** Il legame con la rubrica che dà l'app per chi scrive ("tel:…" o la chiave del contatto), se lo dà */
    val personUri: String? = null,
    /** I messaggi della conversazione (quando, chi, cosa), dal più vecchio; senza i tuoi */
    val lines: List<Triple<Long, String?, String>> = emptyList(),
    /** Il contatto (nome normalizzato) di [personUri]: chi è davvero, qualunque nome mostri l'app */
    val contactName: String? = null,
    /** La conversazione come la identifica l'app (shortcutId): stabile, non la sceglie chi scrive */
    val shortcut: String? = null,
)

/**
 * Le regole in un istante, immutabili: si costruiscono una volta per evento e rendono [Classifier.classify]
 * una funzione pura (si prova senza Android).
 */
data class RuleSnapshot(
    val self: String,
    val now: Long,
    val quiet: Boolean = false,
    /** Le app che entrano (sezione App) */
    val inApps: Set<String> = emptySet(),
    /** I tipi di notifica spenti: "pacchetto|canale" (eccezioni dentro un'app) */
    val offTypes: Set<String> = emptySet(),
    /** Le chat spente dentro un'app: "pacchetto|persona o gruppo" */
    val offChats: Set<String> = emptySet(),
    /** Le persone che entrano solo da un'app: "pacchetto|persona" (sezione Persone, "su WhatsApp") */
    val peopleIn: Set<String> = emptySet(),
    /** Le persone che entrano ovunque (sezione Persone) */
    val important: Set<String> = emptySet(),
    val importantInGroups: Set<String> = emptySet(),
    val systemPass: Boolean = true,
    val passUntil: Map<String, Long> = emptyMap(),
    val braked: Set<String> = emptySet(),
    val talkPass: Boolean = true,
    /** Conversazioni in corso adesso ([Classifier.talkKey]) */
    val talking: Set<String> = emptySet(),
    val urgentEnabled: Boolean = true,
    val urgentWords: List<String> = Classifier.URGENT_WORDS,
    val missedPass: Boolean = true,
    val remindPass: Boolean = true,
    /** I nomi della rubrica, normalizzati */
    val contacts: Set<String> = emptySet(),
    /** "pacchetto|nome visto" → persona importante: "Elisa" su Telegram è Elisa Rossi */
    val aliases: Map<String, String> = emptyMap(),
    /** Le app per cui non si indovinano codici e avvisi di pagamento */
    val noGuess: Set<String> = emptySet(),
) {
    companion object {
        fun of(self: String, prefs: Prefs, people: People, moment: Moment, now: Long = System.currentTimeMillis()) = RuleSnapshot(
            self = self, now = now, quiet = moment == Moment.QUIET,
            inApps = prefs.inApps, offTypes = prefs.offTypes, offChats = prefs.offChats, peopleIn = prefs.peopleIn,
            important = prefs.importantNames, importantInGroups = prefs.importantInGroups,
            systemPass = prefs.systemPass, passUntil = prefs.passUntil, braked = prefs.braked,
            talkPass = prefs.talkPass, talking = Talking.activeKeys(now),
            urgentEnabled = prefs.urgentEnabled,
            urgentWords = prefs.urgentWords.split(',').map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { Classifier.URGENT_WORDS },
            missedPass = prefs.missedPass, remindPass = prefs.remindPass,
            contacts = people.normalizedNames(),
            aliases = prefs.personAliases, noGuess = prefs.noGuess,
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
    /** Il telefono stesso (chiamate, sistema): passa sempre, nessuna regola lo tocca. */
    fun isCore(pkg: String) = pkg in SYSTEM_PKGS
    private val SYSTEM_PKGS = setOf("android", "com.android.systemui", "com.android.phone", "com.samsung.android.incallui", "com.google.android.dialer.incall")

    /** Parole che da sole indicano un codice da digitare (non "autorizzato", "sicurezza"…: compaiono nei pagamenti). */
    private val OTP_STRONG = Regex(
        "\\b(codice|code|otp|passcode|monouso|one[- ]time|2fa)\\b",
        RegexOption.IGNORE_CASE,
    )
    /** Parole ambigue ("il pin del portone", "la password del wifi"): valgono solo con un segno che è un accesso a un servizio. */
    private val OTP_WEAK = Regex("\\b(verifica|verification|password|pin)\\b", RegexOption.IGNORE_CASE)
    private val SERVICE = Regex(
        "(non condivider|do not share|don't share|never share|valid|scad|expire|minut|accedere|accesso|login|log in|sign[- ]?in|autentica|authenticat|identit|conferma|confirm|account)",
        RegexOption.IGNORE_CASE,
    )
    /** "Chiave" è un codice solo se il testo parla di un accesso ("la chiave della cassetta" no). */
    private val KEY_WORD = Regex("\\bchiave\\b", RegexOption.IGNORE_CASE)
    private val ACCESS = Regex(
        "(accedere|accesso|accedi|login|log in|sign[- ]?in|autentica|authenticat|non condivider|do not share|don't share|never share)",
        RegexOption.IGNORE_CASE,
    )
    /** Una promozione: lì "codice … valido" è uno sconto, non un accesso. */
    private val PROMO = Regex(
        "(\\bscont[oi]\\b|\\bscontat|\\bofferta|\\bofferte|\\bcoupon|\\bpromo|\\bpromozion|\\bsaldi\\b|\\bdiscount|\\brisparmi|\\d+\\s?%)",
        RegexOption.IGNORE_CASE,
    )
    /** Un codice promozionale scritto dopo "codice": lettere maiuscole e cifre insieme (SCONTO20, 20OFF). */
    private val PROMO_CODE = Regex("(?i:\\b(?:codice|code|coupon))\\s*:?\\s*(?=[A-Z0-9]*[A-Z])(?=[A-Z0-9]*\\d)[A-Z0-9]{4,}\\b")
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
        val head = if (long) text.take(MAIL_HEAD) else text
        if (PROMO.containsMatchIn(head) || PROMO_CODE.containsMatchIn(head)) return null
        val clean = NOT_OTP.replace(head, " ")
        val words = OTP_STRONG.findAll(clean).map { it.range }.toList() +
            (if (ACCESS.containsMatchIn(clean)) KEY_WORD.findAll(clean).map { it.range }.toList() else emptyList()) +
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
        "(\\bsospett\\p{L}*|\\bfrod[ei]\\b|\\bfraud\\p{L}*|\\bnon autorizzat[oaie]\\b|\\bunauthori[sz]ed\\b|\\brifiutat[oaie]\\b|\\bdeclined\\b|" +
            "\\bnegat[oaie]\\b|\\bbloccat[oaie]\\b|\\bblocked\\b|\\bsei stat[oa] tu\\b|\\bwas this you\\b|" +
            "\\bconferma(re)? (il |l'|questo |questa )?(pagament|acquist|transazion|operazion)|\\bapprova\\b|\\bautorizza (il|l'|questo|questa)\\b|" +
            "\\bconfirm (the |this )?(payment|purchase|transaction)|\\bapprove\\b|\\bentro \\d+ minut|\\bwithin \\d+ minut)",
        RegexOption.IGNORE_CASE,
    )
    /** Si parla davvero di soldi: senza questo "bloccata", "negativo", "approva" sono cronaca, meteo, pubblicità. */
    private val PAY_CONTEXT = Regex(
        "(pagament|payment|transazion|transaction|acquist|purchase|addebit|\\bcharge|bonific|\\btransfer|prelievo|prelevat|withdraw|" +
            "3d ?secure|\\b3ds\\b|\\bcart[ae]\\b|\\bcards?\\b|\\bcont[oi]\\b|\\bbank\\b|\\bbanca\\b|\\bpaypal\\b|€|\\$|\\beur\\b|\\busd\\b)",
        RegexOption.IGNORE_CASE,
    )

    fun isPaymentAlert(text: String): Boolean =
        PAY_ALERT.containsMatchIn(text) && (PAY_CONTEXT.containsMatchIn(text) || CardDetector.paymentCents(text) != null)

    /**
     * Un codice di accesso che conta come "Importante": in un gruppo no, i codici veri arrivano solo a te
     * (altrimenti chiunque in una chat spenta la riaprirebbe scrivendo "codice 1234").
     */
    fun securityOtp(n: NotifInfo): Boolean = (!n.isMessage || n.conversation == null) && isOtp(n.title + " " + n.text)

    /**
     * La chiave di un nome collegato a una persona importante: la conversazione (shortcutId) quando l'app la dà;
     * il nome visto solo nelle app che lo prendono dalla rubrica. Altrove il nome lo sceglie chi scrive: null.
     */
    fun aliasKey(pkg: String, shortcut: String?, person: String?): String? = when {
        !shortcut.isNullOrBlank() -> "$pkg|s:$shortcut"
        pkg in CONTACT_NAMED && !person.isNullOrBlank() -> personRuleKey(pkg, Search.normalize(person)).takeIf { !it.endsWith("|") }
        else -> null
    }

    /** I motivi che in una modalità di Android (QUIET) aspettano invece di passare in silenzio. */
    private val QUIET_HOLDS = setOf(REASON_PERSON, REASON_MISSED, REASON_REMINDER)

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
     * Tutto aspetta in Sordina, tranne quello che lasci entrare. Funzione pura: stesso ingresso, stesso verdetto.
     * Dal generico allo specifico, e il più specifico vince:
     *
     *  1. Sordina stessa e i componenti di sistema: mai toccati
     *  2. Un tipo di notifica spento (eccezione dentro un'app): aspetta, anche se è fissa. Mai un codice o un avviso di sicurezza
     *  3. Chiamate, sveglie, navigazione, musica, notifiche in corso: passano come sono
     *  4. "Lascia passare per qualche ora"
     *  5. Importanti, sempre: codici di accesso e avvisi di sicurezza
     *  6. Una chat spenta dentro un'app: aspetta
     *  7. Una persona che entra da quell'app (Persone, "su WhatsApp")
     *  8. App frenata o nascosta: aspetta, se non l'hai messa fra le app che entrano
     *  9. Chi ti sta rispondendo (Importanti)
     * 10. Le persone che entrano ovunque (Persone)
     * 11. Le app che entrano (App)
     * 12. Importanti: chi scrive "urgente" o insiste, chiamate perse, promemoria
     * 13. Tutto il resto aspetta
     *
     * Con una modalità di Android accesa ([RuleSnapshot.quiet]) aspettano anche persone per app, chiamate perse
     * e promemoria; sicurezza, persone di ovunque, app e urgenze restano visibili.
     */
    fun classify(n: NotifInfo, s: RuleSnapshot): Verdict {
        if (n.pkg == s.self) return Verdict(null, "Sordina")
        if (n.pkg in SYSTEM_PKGS) return Verdict(null, "sistema")
        fun ruled(level: Level, why: String) =
            if (level == Level.SUBITO && s.quiet && why in QUIET_HOLDS) Verdict(Level.RIEPILOGO, "modalità") else Verdict(level, why)

        val all = n.title + " " + n.text
        // "Non indovinare per questa app": niente scorciatoie di sicurezza, valgono le regole come per tutto il resto
        val guess = n.pkg !in s.noGuess
        val otp = guess && securityOtp(n)
        val payAlert = guess && !n.isMessage && isPaymentAlert(all)
        val security = otp || payAlert
        if (!security && channelKey(n.pkg, n.channel) in s.offTypes) return Verdict(Level.RIEPILOGO, REASON_CHANNEL)

        if (n.system && s.systemPass) return Verdict(null, "sistema")
        if (n.category in PASS_CATEGORIES) return Verdict(null, "chiamata, sveglia o servizio")
        if (n.isMedia) return Verdict(null, "musica")
        if (n.ongoing) return Verdict(null, "in corso")

        val who = personKey(n)
        if ((s.passUntil[Prefs.PASS_ALL] ?: 0L) > s.now || (s.passUntil["a:" + n.pkg] ?: 0L) > s.now || (who != null && (s.passUntil["p:$who"] ?: 0L) > s.now)) return Verdict(null, "lasciata passare")

        if (otp) return Verdict(Level.SUBITO, REASON_OTP)
        if (payAlert) return Verdict(Level.SUBITO, REASON_PAY_ALERT)

        // Un'app frenata resta sempre in sordina: più forte dell'app che passa, delle persone, della chat in corso.
        // Passano solo i codici di sicurezza, chiamate e sveglie (sopra) e il "lascia passare" chiesto per qualche ora.
        if (n.pkg in s.braked) return Verdict(Level.RIEPILOGO, REASON_BRAKED)

        if (who != null) {
            val k = personRuleKey(n.pkg, who)
            if (k in s.offChats) return Verdict(Level.RIEPILOGO, REASON_PERSON)
            if (k in s.peopleIn) return ruled(Level.SUBITO, REASON_PERSON)
        }

        val listed = n.pkg in s.inApps
        if (n.hidden && !listed) return Verdict(Level.RIEPILOGO, REASON_HIDDEN)

        if (n.isMessage && s.talkPass && n.peer != null && talkKey(n.pkg, n.peer) in s.talking) return Verdict(null, "conversazione in corso")

        if (n.isMessage) {
            val inGroup = n.conversation != null
            val exact = n.pkg !in CONTACT_NAMED
            // Prima chi è davvero (il contatto che dà l'app), poi un nome collegato a mano, poi il nome che si vede
            val imp = n.contactName?.takeIf { it in s.important }
                ?: (if (!inGroup) aliasKey(n.pkg, n.shortcut, n.person)?.let { s.aliases[it] } else null)?.takeIf { it in s.important }
                ?: importantKey(n.person, s.important, exact) ?: if (!inGroup) importantKey(n.title, s.important, exact) else null
            if (imp != null && (!inGroup || imp in s.importantInGroups)) return Verdict(Level.SUBITO, "persona importante")
        }

        if (listed) return Verdict(Level.SEMPRE, REASON_APP)

        // Urgenze: solo in una chat a due, solo da qualcuno che è nella tua rubrica (uno sconosciuto non decide quanto conta)
        // In rubrica davvero: il contatto che dà l'app, o il nome solo dove viene dalla rubrica (su Telegram lo sceglie chi scrive)
        val inBook = n.contactName != null || (n.pkg in CONTACT_NAMED && n.person != null && Search.normalize(n.person) in s.contacts)
        if (s.urgentEnabled && n.isMessage && n.conversation == null && inBook) {
            if (isUrgent(n.text, s.urgentWords)) return Verdict(Level.SUBITO, "urgente")
            if (n.burst >= BURST) return Verdict(Level.SUBITO, "insiste")
        }
        if (s.missedPass && n.category == "missed_call") return ruled(Level.SUBITO, REASON_MISSED)
        if (s.remindPass && (n.category == Notification.CATEGORY_REMINDER || n.category == Notification.CATEGORY_EVENT)) return ruled(Level.SUBITO, REASON_REMINDER)
        if (!n.isMessage && CardDetector.paymentCents(all) != null) return Verdict(Level.RIEPILOGO, "pagamento")
        return Verdict(Level.RIEPILOGO, REASON_DEFAULT)
    }

    /** Chiave di una persona o chat dentro un'app: "pacchetto|nome". */
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
        var personUri: String? = null
        fun uriOf(b: android.os.Bundle): String? = if (Build.VERSION.SDK_INT >= 28) {
            @Suppress("DEPRECATION")
            (b.get("sender_person") as? android.app.Person)?.uri
        } else null
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
                personUri = uriOf(last)
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
        // Una chat a due senza mittente nei messaggi: la persona della notifica
        if (personUri == null && isMessage && !isGroupConv && Build.VERSION.SDK_INT >= 28) personUri = runCatching {
            @Suppress("DEPRECATION")
            (e.getParcelableArrayList<android.app.Person>(Notification.EXTRA_PEOPLE_LIST))?.singleOrNull()?.uri
        }.getOrNull()
        val mine = lastIsMine(sbn)
        // Tutti i messaggi, non solo l'ultimo: la chat li mette in una notifica sola. I tuoi (risposte dalla notifica) no
        val me = if (Build.VERSION.SDK_INT >= 28) runCatching {
            @Suppress("DEPRECATION")
            (e.get(Notification.EXTRA_MESSAGING_PERSON) as? android.app.Person)?.name?.toString()
        }.getOrNull() else null
        val lines = messages.orEmpty().mapNotNull { it as? android.os.Bundle }.mapNotNull { b ->
            val s = senderOf(b)
            if (s == null || (me != null && s == me)) return@mapNotNull null
            val t = b.getCharSequence("text")?.toString()?.let { clean(it) }?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Triple(b.getLong("time"), s.trim().ifBlank { null }, t)
        }
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
            lines = lines,
            burst = burst,
            channel = n.channelId,
            personUri = personUri?.ifBlank { null },
            shortcut = if (isMessage && Build.VERSION.SDK_INT >= 26) n.shortcutId?.ifBlank { null } else null,
        )
    }
}
