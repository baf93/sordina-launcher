package app.sordina.data

import app.sordina.R
import app.sordina.tr
import app.sordina.SordinaApp

import android.content.Context
import android.provider.Settings
import android.view.KeyEvent

/** Cosa può fare un'intenzione. Verso altri (chiamate, messaggi) serve sempre il tuo tocco. */
sealed interface Act {
    data class OpenApp(val app: AppEntry) : Act
    data class Call(val contact: Contact) : Act
    data class Message(val contact: Contact, val text: String, val via: Via) : Act
    data class Timer(val seconds: Int, val label: String) : Act
    data class Alarm(val hour: Int, val minute: Int, val label: String) : Act
    data class Event(val title: String, val whenAt: When) : Act
    data object Torch : Act
    data class Media(val keyCode: Int) : Act
    data class Navigate(val place: String) : Act
    data class Music(val query: String) : Act
    /** Frase passata ad Assist di Home Assistant */
    data class Assist(val text: String) : Act
    data class Setting(val action: String) : Act
    data object SordinaSettings : Act
    data class Web(val query: String) : Act
    data class Calc(val result: String) : Act
    /** Sordina a tempo fino a [until] (millisecondi); null = la togli */
    data class Quiet(val until: Long?) : Act
}

enum class Via(val label: String, val pkgs: List<String>) {
    WHATSAPP("WhatsApp", listOf("com.whatsapp", "com.whatsapp.w4b")),
    TELEGRAM("Telegram", listOf("org.telegram.messenger", "org.telegram.messenger.web")),
    SIGNAL("Signal", listOf("org.thoughtcrime.securesms")),
    SMS("SMS", emptyList());

    companion object {
        fun ofPackage(pkg: String) = entries.firstOrNull { pkg in it.pkgs }
    }
}

enum class HaOp { ON, OFF, TOGGLE, LEVEL }

enum class Kind(val label: String) {
    ACTION("Azione"), PERSON("Persona"), APP("App"), HOME("Casa"), SETTING("Impostazione"), CALC("Calcolo"), WEB("Web")
}

data class Candidate(
    /** Stabile, per imparare dalle correzioni */
    val id: String,
    val title: String,
    val subtitle: String?,
    val kind: Kind,
    val score: Int,
    val toOthers: Boolean,
    val act: Act,
    val app: AppEntry? = null,
)

data class Understanding(val candidates: List<Candidate>, val confident: Boolean) {
    val top: Candidate? get() = candidates.firstOrNull()
}

/**
 * Il motore delle intenzioni: sceglie tra cose che esistono sul tuo telefono.
 * Regole scritte per l'italiano (il "pacchetto di lingua"); la ricerca per nome funziona in ogni lingua.
 */
class Intents(
    private val context: Context,
    private val prefs: Prefs,
    private val people: People,
    private val store: NotifStore,
    private val ha: HomeAssistant,
    private val brain: app.sordina.data.brain.Brain,
) {
    /** Quello che il telefono sa per capire le frasi libere: nomi, chi ti ha scritto, chi ti ha chiamato. */
    private fun hints(apps: List<AppEntry>) = object : Phrase.Hints {
        override fun isName(word: String) = word.length >= 3 && (
            people.all().any { c -> c.normalized.split(' ').any { it == word } } || apps.any { a -> a.normalized.split(' ').any { it == word } })
        override fun lastWriter(): String? =
            store.held.value.values.filter { it.isMessage && it.conversation == null && !it.person.isNullOrBlank() }
                .maxByOrNull { it.time }?.person
                ?: runCatching { app.sordina.service.SordinaListener.tray() }.getOrDefault(emptyList())
                    .firstOrNull { !it.person.isNullOrBlank() && Via.ofPackage(it.pkg) != null }?.person
        override fun lastPerson(): String? = lastPerson?.takeIf { System.currentTimeMillis() - it.second < 15 * 60_000 }?.first?.name
        override fun lastCaller(): String? = runCatching {
            SordinaApp.of(context).signals.cards.value.values.filter { it.kind == CardKind.CALL }.maxByOrNull { it.time }?.title
        }.getOrNull()
    }

    fun understand(query: String, apps: List<AppEntry>, usage: Map<String, Double>): Understanding {
        val raw = query.trim()
        if (raw.isEmpty()) return Understanding(emptyList(), false)
        // Le frasi dette in mille modi diventano quella che il motore conosce ("puoi chiamare Elisa?" → "chiama Elisa")
        val q = reorder(runCatching { Phrase.canonical(raw, hints(apps)) }.getOrDefault(raw).ifBlank { raw })
        track(Search.normalize(raw))
        val ow = q.split(Regex("\\s+"))
        val nw = ow.map { Search.normalize(it) }
        val nq = nw.filter { it.isNotEmpty() }.joinToString(" ")
        val out = ArrayList<Candidate>()

        calc(q)?.let { out += it }
        structured(ow, nw, nq, apps, out)

        // Per nome: app, persone, casa, impostazioni
        if (nq.isNotEmpty()) {
            Search.apps(q, apps, usage, 5).forEach { a ->
                out += Candidate("app:${a.key}", a.label, if (a.isWork) tr(R.string.work_profile) else null, Kind.APP, Search.score(nq, a.normalized), false, Act.OpenApp(a), a)
            }
            // Un soprannome imparato ("mamma"): la persona in cima
            nickname(nq)?.let { c ->
                out += Candidate("call:${c.id}", tr(R.string.call_x, c.name), c.number, Kind.PERSON, 88, true, Act.Call(c))
                val via = viaFor(c, null)
                out += Candidate("msg:${c.id}", tr(R.string.write_to_x, c.name), via.label, Kind.PERSON, 86, true, Act.Message(c, "", via))
            }
            people.search(nq, 2).forEach { c ->
                val s = Search.score(nq, c.normalized)
                out += Candidate("call:${c.id}", tr(R.string.call_x, c.name), c.number, Kind.PERSON, s - 6, true, Act.Call(c))
                val via = viaFor(c, null)
                out += Candidate("msg:${c.id}", tr(R.string.write_to_x, c.name), via.label, Kind.PERSON, s - 9, true, Act.Message(c, "", via))
            }
            SETTINGS.forEach { (words, title, action) ->
                val s = words.maxOf { Search.score(nq, it) }
                if (s >= 75) out += Candidate("set:$action", title, tr(R.string.settings_kind), Kind.SETTING, s - 8, false, if (action == "sordina") Act.SordinaSettings else Act.Setting(action))
            }
        }

        // Il verbo a metà ("chi", "scr"): le persone che è più probabile tu chiami o scriva adesso
        if (nq.length >= 2 && ' ' !in nq) {
            val call = CALL_VERBS.any { it.startsWith(nq) }
            val msg = MSG_VERBS.any { it.startsWith(nq) }
            // Senza storia: le persone importanti e i preferiti
            fun fallback(prefix: String) = people.all().filter { it.starred || people.isImportant(it.name) }.take(3).map { c ->
                if (prefix == "call:") "call:${c.normalized}" else "msg:${c.normalized}:${viaFor(c, null).name}"
            }
            fun offer(prefix: String) = brain.likelyPeople(prefix, 3).ifEmpty { fallback(prefix) }
                .forEachIndexed { i, item -> realize(item, apps, usage)?.let { out += it.copy(score = 72 - i * 3) } }
            if (call) offer("call:")
            if (msg) offer("msg:")
        }

        // Quello che di solito scegli con queste lettere, e quanto è probabile adesso
        val reranked = brain.rerank(raw, out) { realize(it, apps, usage) }

        // Correzioni imparate (la frase intera scelta più in basso)
        val learned = prefs.aliases[Search.normalize(raw)] ?: prefs.aliases[nq]
        val ranked = reranked
            .map { if (it.id == learned) it.copy(score = (it.score + 30).coerceAtMost(100)) else it }
            .groupBy { it.id }.map { g -> g.value.maxBy { it.score } }
            .sortedWith(compareByDescending<Candidate> { it.score }
                .thenByDescending { it.app?.let { a -> usage[a.packageName] } ?: 0.0 })
            .take(5)
        val web = Candidate("web", tr(R.string.search_web_x, raw), null, Kind.WEB, 0, false, Act.Web(raw))
        val top = ranked.firstOrNull()
        val second = ranked.getOrNull(1)
        val confident = top != null && top.score >= 85 &&
            (second == null || top.score - second.score >= 10 || top.id == learned || brain.learnedFor(raw, top) >= 0.7 ||
                // Una frase di casa: va ad Assist anche se un'app o una persona somigliano, a meno che sia un'impostazione del telefono
                (top.kind == Kind.HOME && !(second.kind == Kind.SETTING && top.score - second.score < 10)))
        // Sicurezza bassa: "Cerca sul web" in cima
        val list = if (top == null || top.score < 45) listOf(web) + ranked else ranked + web
        return Understanding(list, confident)
    }

    /**
     * Una cosa del cervello ("app:…", "call:…", "msg:…", "set:…", "cmd:…") trasformata in un'azione da proporre.
     * null se non esiste più (app disinstallata, contatto sparito) o non si può rifare.
     */
    fun realize(item: String, apps: List<AppEntry>, usage: Map<String, Double>): Candidate? {
        val kind = item.substringBefore(':')
        val rest = item.substringAfter(':')
        return when (kind) {
            "app" -> (apps.firstOrNull { it.packageName == rest && !it.isWork } ?: apps.firstOrNull { it.packageName == rest })?.let { a ->
                Candidate("app:${a.key}", a.label, if (a.isWork) tr(R.string.work_profile) else null, Kind.APP, 60, false, Act.OpenApp(a), a)
            }
            "call" -> brain.contact(rest)?.let { c -> Candidate("call:${c.id}", tr(R.string.call_x, c.name), c.number, Kind.PERSON, 60, true, Act.Call(c)) }
            "msg" -> {
                val via = runCatching { Via.valueOf(rest.substringAfterLast(':')) }.getOrNull()?.takeIf { installed(it) } ?: return null
                brain.contact(rest.substringBeforeLast(':'))?.let { c ->
                    Candidate("msg:${c.id}:${via.name}", tr(R.string.write_to_x, c.name), via.label, Kind.PERSON, 60, true, Act.Message(c, "", via))
                }
            }
            "set" -> if (rest == "sordina") Candidate("set:sordina", tr(R.string.s_settings), null, Kind.SETTING, 60, false, Act.SordinaSettings)
                else SETTINGS.firstOrNull { it.third == rest }?.let { (_, title, action) -> Candidate("set:$action", title, tr(R.string.settings_kind), Kind.SETTING, 60, false, Act.Setting(action)) }
            // Un comando che ripeti: si ricapisce la frase (con le app e la casa di adesso)
            "cmd" -> understandRaw(rest, apps, usage).firstOrNull()?.takeIf { !it.toOthers && it.kind != Kind.WEB && it.kind != Kind.APP && it.kind != Kind.CALC }
                ?.copy(score = 60)
            else -> null
        }
    }

    /**
     * Prima chi, poi cosa: "elisa telegram", "mamma chiama", "elisa su whatsapp arrivo"
     * diventano "telegram elisa", "chiama mamma", "whatsapp elisa arrivo".
     * Solo se il resto comincia con una persona che conosci: le app e le altre frasi restano come sono.
     */
    private fun reorder(q: String): String {
        val ow = q.trim().split(Regex("\\s+"))
        val nw = ow.map { Search.normalize(it) }
        if (ow.size < 2 || nw[0] in LEAD_VERBS) return q
        val i = (1 until nw.size).firstOrNull { nw[it] in LEAD_VERBS } ?: return q
        // "su telegram", "via whatsapp": la preposizione se ne va con l'app
        val skip = setOf(i) + (if (nw[i] in VIA_WORDS && nw[i - 1] in setOf("su", "via", "con", "per")) setOf(i - 1) else emptySet())
        val rest = ow.indices.filter { it !in skip }
        if (rest.isEmpty() || personIn(rest.map { nw[it] }.filter { it.isNotEmpty() }) == null) return q
        return (listOf(ow[i]) + rest.map { ow[it] }).joinToString(" ")
    }

    /** Solo il testo, senza il cervello: per ricapire un comando ricordato. */
    private fun understandRaw(q: String, apps: List<AppEntry>, usage: Map<String, Double>): List<Candidate> {
        val ow = q.split(Regex("\\s+"))
        val nw = ow.map { Search.normalize(it) }
        val out = ArrayList<Candidate>()
        structured(ow, nw, nw.filter { it.isNotEmpty() }.joinToString(" "), apps, out)
        return out.sortedByDescending { it.score }
    }

    // Il filo del discorso

    /** L'ultima persona chiamata o scritta: "chiamala", "scrivile che arrivo" sono per lei (per 15 minuti). */
    @Volatile private var lastPerson: Pair<Contact, Long>? = null

    /** La frase più lunga scritta adesso, e quella di prima lasciata a metà (per imparare dalle riformulazioni). */
    private var peak = ""
    private var peakAt = 0L
    private var attempt: Pair<String, Long>? = null

    @Synchronized
    private fun track(nq: String) {
        if (nq.isEmpty() || peak.startsWith(nq)) return // stai cancellando
        val now = System.currentTimeMillis()
        if (nq.startsWith(peak)) { peak = nq; peakAt = now; return }
        // Una frase nuova che non continua la precedente: quella di prima non ha portato a niente
        if (peak.length >= 3) attempt = peak to peakAt
        peak = nq; peakAt = now
    }

    /**
     * Hai scritto "mamma", non è uscito niente, hai riscritto "maria" e l'hai scelta:
     * d'ora in poi "mamma" è lei (anche a voce). Solo se la frase di prima non era già il suo nome.
     */
    private fun learnNickname(c: Contact, now: Long) {
        val (text, at) = attempt ?: return
        if (now - at > 2 * 60_000) return
        val words = text.split(' ').filter { it.isNotEmpty() && it !in FILLER }
        val nick = words.joinToString(" ")
        if (nick.length < 3 || words.size > 2 || Search.score(nick, c.normalized) >= 75) return
        // Un nome che è già di un altro contatto non diventa un soprannome
        if (people.all().any { it.normalized != c.normalized && Search.score(nick, it.normalized) >= 75 }) return
        prefs.nicknames = prefs.nicknames + (nick to c.normalized)
    }

    /** L'ultimo messaggio di questa persona che aspetta risposta (in Sordina o nella tendina). */
    private fun lastFrom(c: Contact): String? {
        val n = c.normalized
        return store.held.value.values.filter { it.isMessage && it.conversation == null && it.person?.let(Search::normalize) == n }
            .maxByOrNull { it.time }?.text
            ?: runCatching { app.sordina.service.SordinaListener.tray() }.getOrDefault(emptyList())
                .firstOrNull { it.person?.let(Search::normalize) == n }?.text
    }

    /** Il contatto di un soprannome imparato. */
    private fun nickname(name: String): Contact? = prefs.nicknames[name]?.let { n -> people.all().firstOrNull { it.normalized == n } }

    /** Registra cosa è stato scelto: misura e correzioni. */
    fun chosen(query: String, c: Candidate, index: Int) {
        prefs.intentTotal = prefs.intentTotal + 1
        if (index == 0) prefs.intentFirst = prefs.intentFirst + 1
        val now = System.currentTimeMillis()
        val contact = when (val a = c.act) { is Act.Call -> a.contact; is Act.Message -> a.contact; else -> null }
        if (contact != null) {
            lastPerson = contact to now
            // Hai scritto qualcosa di breve: è una risposta da ricordare
            (c.act as? Act.Message)?.text?.takeIf { it.isNotBlank() }?.let { t -> lastFrom(contact)?.let { SordinaApp.of(context).replies.learn(it, t, now) } }
            learnNickname(contact, now)
        }
        peak = ""; attempt = null
        val nq = Search.normalize(query)
        if (nq.isEmpty() || c.kind == Kind.WEB) return
        if (index > 0) prefs.aliases = prefs.aliases + (nq to c.id)
    }

    // Frasi con struttura

    private fun structured(ow: List<String>, nw: List<String>, nq: String, apps: List<AppEntry>, out: MutableList<Candidate>) {
        val first = nw.firstOrNull() ?: return
        val restN = nw.drop(1).filter { it.isNotEmpty() }.joinToString(" ")

        // Torcia
        if (nw.any { it in TORCH } || nq == "luce telefono" || nq.contains("lampe torche") || nq.contains("lampe de poche")) {
            out += Candidate("torch", tr(R.string.torch), tr(R.string.torch_hint), Kind.ACTION, 97, false, Act.Torch); return
        }
        // Sordina a tempo: "sordina per 45 minuti", "silenzio fino alle 15", "non disturbare un'ora", "basta sordina"
        quiet(nw, nq)?.let { out += it; return }
        // Musica in riproduzione
        MEDIA[nq]?.let { (title, key) -> out += Candidate("media:$key", title, tr(R.string.music_playing), Kind.ACTION, 96, false, Act.Media(key)); return }

        // Timer
        if ("timer" in nw || nq.startsWith("conto alla rovescia")) {
            val d = DateParser.duration(nq)
            if (d != null) {
                val label = d.second.split(' ').filter { it !in setOf("timer", "metti", "un", "imposta", "per", "avvia", "conto", "alla", "rovescia", "di", "da", "set", "start", "a", "an", "for", "tra",
                    "de", "para", "pour", "fur", "auf", "von", "le", "el", "la", "o") }.joinToString(" ")
                val t = fmtDuration(d.first)
                out += Candidate("timer", tr(R.string.timer) + " $t" + if (label.isNotBlank()) " · $label" else "", tr(R.string.starts_now), Kind.ACTION, 96, false, Act.Timer(d.first, label))
            } else {
                out += Candidate("timer?", tr(R.string.timer), tr(R.string.timer_say_how_long), Kind.ACTION, 50, false, Act.Timer(0, ""))
            }
            return
        }
        // Sveglia
        val alarmEn = nq.startsWith("wake me") || first == "alarm" || Regex("^(set|create|add|new)( an| a| my)? alarm ").containsMatchIn(nq)
        if (first == "sveglia" || first == "svegliami" || alarmEn) {
            val r = DateParser.parse(if (alarmEn) nq.replace(Regex("^(wake me( up)?|((set|create|add|new)( an| a| my)? )?alarm)"), "") else nq.removePrefix(first), eveningBias = false)
            val w = r.whenAt
            if (w != null && !w.allDay) {
                out += Candidate("alarm", tr(R.string.alarm_at, java.time.LocalTime.of(w.start.hour, w.start.minute).format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))), tr(R.string.alarm_hint), Kind.ACTION, 96, false, Act.Alarm(w.start.hour, w.start.minute, ""))
                return
            }
        }
        // Chiama
        if (first in setOf("chiama", "telefona", "chiamare", "telefonare", "call", "phone", "ring", "dial")) {
            val rest = skip(nw.drop(1), setOf("a", "ad", "to"))
            personIn(rest)?.let { (c, s, _) ->
                out += Candidate("call:${c.id}", tr(R.string.call_x, c.name), c.number, Kind.PERSON, if (s >= 80) 95 else 80, true, Act.Call(c))
            }
            return
        }
        // Scrivi
        val msgVerbs = setOf("scrivi", "scrivere", "messaggio", "manda", "invia", "whatsapp", "sms", "telegram", "signal", "text", "message", "write", "send", "tell")
        if (first in msgVerbs) {
            var via: Via? = when (first) { "whatsapp" -> Via.WHATSAPP; "sms" -> Via.SMS; "telegram" -> Via.TELEGRAM; "signal" -> Via.SIGNAL; else -> null }
            // "su whatsapp", "via telegram", "con sms" ovunque nella frase
            val idx = ArrayList((1 until nw.size).toList())
            for (i in 1 until nw.size - 1) {
                if (nw[i] in setOf("su", "via", "con", "per", "on", "over", "by", "por", "par", "uber", "pelo", "no", "en")) {
                    val v = when (nw[i + 1]) { "whatsapp" -> Via.WHATSAPP; "telegram" -> Via.TELEGRAM; "signal" -> Via.SIGNAL; "sms", "messaggio" -> Via.SMS; else -> null }
                    if (v != null) { via = v; idx.remove(i); idx.remove(i + 1) }
                }
            }
            var k = 0
            val restIdx = idx.filter { nw[it].isNotEmpty() }.toMutableList()
            while (k < restIdx.size && nw[restIdx[k]] in setOf("un", "messaggio", "a", "ad", "al", "alla", "an", "message", "text", "to")) k++
            val tail = restIdx.drop(k)
            val found = personIn(tail.map { nw[it] }) ?: return
            val (c, s, used) = found
            val textWords = tail.drop(used).map { ow[it] }.toMutableList()
            while (textWords.isNotEmpty() && Search.normalize(textWords.first()) in setOf("che", "dicendo", "dicendogli", "dicendole", ":", "saying", "that",
                    "que", "qu", "diciendo", "disant", "dass", "dizendo")) textWords.removeAt(0)
            val text = textWords.joinToString(" ").replaceFirstChar { it.uppercase() }
            val v = viaFor(c, via)
            out += Candidate("msg:${c.id}:${v.name}", tr(R.string.write_to_x, c.name), (if (text.isNotBlank()) "«$text» · " else "") + v.label, Kind.PERSON, if (s >= 80) 95 else 80, true, Act.Message(c, text, v))
            // Senza testo: le risposte che dai di solito a un messaggio come il suo ("Arrivo!", "Ok a dopo"), un tocco più in basso
            if (text.isBlank()) lastFrom(c)?.let { incoming ->
                SordinaApp.of(context).replies.suggest(incoming).forEachIndexed { i, r ->
                    out += Candidate("msg:${c.id}:${v.name}:r$i", tr(R.string.write_to_x, c.name), "«$r» · " + v.label, Kind.PERSON, 79 - i, true, Act.Message(c, r, v))
                }
            }
            // Le altre destinazioni, un tocco più in basso
            Via.entries.filter { it != v && installed(it) }.take(2).forEach { o ->
                out += Candidate("msg:${c.id}:${o.name}", tr(R.string.write_to_x, c.name), tr(R.string.on_x, o.label), Kind.PERSON, 70, true, Act.Message(c, text, o))
            }
            return
        }
        // Impegni e promemoria
        val eventVerbs = setOf("aggiungi", "crea", "nuovo", "nuova", "fissa", "impegno", "evento", "appuntamento", "promemoria", "ricordami", "segna", "calendario", "metti",
            "add", "create", "new", "schedule", "event", "appointment", "reminder", "remind", "book", "plan")
        if (first in eventVerbs) {
            if (event(nw.drop(1), if (first == "metti") 80 else 94, out)) return
            val title = eventTitle(DateParser.parse(nw.drop(1).joinToString(" ")).rest)
            if (first !in setOf("metti", "aggiungi", "add", "book", "plan") && title.isNotBlank()) {
                val t = java.time.LocalDate.now().plusDays(1).atTime(9, 0)
                out += Candidate("event", title, tr(R.string.event_no_when), Kind.ACTION, 70, false, Act.Event(title, When(t, false, false)))
            }
        }
        // Navigazione
        val nav = listOf("naviga verso", "naviga a", "portami a", "portami", "indicazioni per", "vai a", "andiamo a", "verso",
            "navigate to", "take me to", "directions to", "drive to", "get me to", "navigate")
        nav.firstOrNull { nq.startsWith("$it ") }?.let { p ->
            val n = p.split(' ').size
            val place = ow.drop(n).joinToString(" ")
            if (place.isNotBlank()) out += Candidate("nav", tr(R.string.toward_x, place), tr(R.string.navigation), Kind.ACTION, 90, false, Act.Navigate(place))
            return
        }
        // Casa
        val haVerbs = mapOf("accendi" to HaOp.ON, "attiva" to HaOp.ON, "apri" to HaOp.ON, "alza" to HaOp.ON,
            "spegni" to HaOp.OFF, "disattiva" to HaOp.OFF, "chiudi" to HaOp.OFF, "abbassa" to HaOp.OFF, "stacca" to HaOp.OFF,
            "imposta" to HaOp.LEVEL, "regola" to HaOp.LEVEL,
            "turn" to HaOp.ON, "switch" to HaOp.ON, "open" to HaOp.ON, "close" to HaOp.OFF, "dim" to HaOp.LEVEL, "set" to HaOp.LEVEL, "lock" to HaOp.OFF, "unlock" to HaOp.ON) + HA_FOREIGN
        haVerbs[first]?.let { op0 ->
            val level = Regex("(?:al|a|su|to|auf|sur|para|em) (\\d{1,3}) ?(%|percento|per cento|gradi|percent|degrees|grados|degres|grad|graus|prozent|por ciento|pour cent)?").find(restN)?.groupValues?.get(1)?.toIntOrNull()
            val target = restN.replace(Regex("(?:al|a|su|to|auf|sur|para|em) \\d{1,3} ?(%|percento|per cento|gradi|percent|degrees|grados|degres|grad|graus|prozent|por ciento|pour cent)?"), "").split(' ').filter { it !in setOf("on", "off", "the", "an", "aus", "ein") }.joinToString(" ").trim()
            if (ha.canAssist && target.isNotBlank()) {
                // Casa: la frase va ad Assist, che conosce stanze, alias ed entità esposte (e la tua lingua: la frase resta com'è)
                out += Candidate("assist", ow.joinToString(" ").replaceFirstChar { it.uppercase() }, if (ha.configured) tr(R.string.home_kind) else tr(R.string.ha_in_app), Kind.HOME, if (first in OPEN) 80 else 90, false, Act.Assist(ow.joinToString(" ")))
            }
            if (first in OPEN) {
                Search.apps(restN, apps, emptyMap(), 2).forEach { a ->
                    out += Candidate("app:${a.key}", a.label, null, Kind.APP, Search.score(restN, a.normalized).coerceAtMost(92), false, Act.OpenApp(a), a)
                }
            }
            if (first in setOf("accendi", "spegni", "attiva", "disattiva", "turn", "switch",
                    "enciende", "apaga", "activa", "desactiva", "allume", "eteins", "active", "desactive", "schalte", "schalt", "mach", "liga", "desliga", "acende")) {
                SETTINGS.filter { (w, _, _) -> w.any { Search.score(target, it) >= 80 } }.forEach { (_, title, action) ->
                    out += Candidate("set:$action", title, tr(R.string.settings_kind), Kind.SETTING, 84, false, Act.Setting(action))
                }
            }
            return
        }
        // Un canale TV ("metti Rai 1", "guarda il 5"): a casa, non nella musica
        if (ha.canAssist && HomeTalk.channel(nw, prefs.homeWords + ha.names())) {
            out += Candidate("assist", ow.joinToString(" ").replaceFirstChar { it.uppercase() }, if (ha.configured) tr(R.string.home_kind) else tr(R.string.ha_in_app), Kind.HOME, 93, false, Act.Assist(ow.joinToString(" ")))
            return
        }
        // Casa senza comando: domande e stati ("che luci sono accese", "quanti gradi in salotto") vanno ad Assist, non sul web
        if (ha.canAssist) HomeTalk.score(nw, prefs.homeWords + ha.names())?.let { s ->
            out += Candidate("assist", ow.joinToString(" ").replaceFirstChar { it.uppercase() }, if (ha.configured) tr(R.string.home_kind) else tr(R.string.ha_in_app), Kind.HOME, s, false, Act.Assist(ow.joinToString(" ")))
        }
        // Musica
        if (first in PLAY && restN.isNotBlank()) {
            if (restN in setOf("musica", "la musica", "music", "some music", "to music", "algo de musica", "musique", "de la musique", "musik", "etwas musik", "uma musica")) {
                out += Candidate("media:${KeyEvent.KEYCODE_MEDIA_PLAY}", tr(R.string.resume_music), null, Kind.ACTION, 92, false, Act.Media(KeyEvent.KEYCODE_MEDIA_PLAY))
            } else {
                val what = ow.drop(if (first == "listen" && nw.getOrNull(1) == "to") 2 else 1).joinToString(" ")
                out += Candidate("music", tr(R.string.play_x, what), tr(R.string.in_music_app), Kind.ACTION, 82, false, Act.Music(what))
            }
        }
        // Un impegno detto come si parla, senza verbo: "pizza stasera alle 10", "dentista giovedì alle 15".
        // Solo frasi corte con un quando chiaro, e niente domande ("che tempo fa domani" resta una ricerca)
        val asks = setOf("che", "cosa", "come", "quando", "dove", "chi", "perche", "quanto", "quanti", "quale", "meteo", "tempo",
            "what", "whats", "how", "when", "where", "who", "why", "which", "weather", "is", "are", "does", "do", "can", "should",
            "que", "como", "cuando", "donde", "quien", "cual", "cuanto", "tiempo", "clima",
            "quel", "quelle", "comment", "quand", "ou", "qui", "pourquoi", "combien", "meteo", "est",
            "was", "wie", "wann", "wo", "wer", "warum", "welche", "welcher", "wetter", "ist",
            "quando", "onde", "quem", "qual", "porque", "quanto", "tempo")
        if (nw.size in 2..8 && nw.none { it in asks } && !nq.endsWith("?")) event(nw, 85, out)
    }

    private val eventFiller = setOf("di", "un", "una", "uno", "nuovo", "nuova", "l", "il", "lo", "la", "in", "nel", "al", "calendario", "impegno", "evento", "appuntamento", "promemoria", "che", "ho", "a", "me", "to", "an", "the", "at", "on", "for", "my", "event", "appointment", "reminder", "about",
        "de", "del", "el", "los", "las", "que", "evento", "cita", "du", "des", "le", "les", "d", "pour", "rendez", "vous", "rappel",
        "der", "die", "das", "dem", "den", "um", "am", "zu", "daran", "dass", "termin", "do", "da", "o", "os", "as", "para", "compromisso", "lembrete")

    private fun eventTitle(rest: String): String {
        val words = rest.split(' ').filter { it.isNotBlank() }.toMutableList()
        while (words.isNotEmpty() && words.first() in eventFiller) words.removeAt(0)
        while (words.isNotEmpty() && words.last() in eventFiller) words.removeAt(words.size - 1)
        return words.joinToString(" ").replaceFirstChar { it.uppercase() }
    }

    /** Un impegno con un quando e un titolo: lo propone e dice true. */
    private fun event(words: List<String>, score: Int, out: MutableList<Candidate>): Boolean {
        val r = DateParser.parse(words.joinToString(" "))
        val w = r.whenAt ?: return false
        val title = eventTitle(r.rest).takeIf { it.isNotBlank() } ?: return false
        val cal = if (ha.calendar != null) prefs.haCalendarName else Calendars(context, prefs).default()?.name
        val sub = DateParser.describe(w) + (if (w.movedToTomorrow) " " + tr(R.string.when_moved) else "") + (cal?.let { " · $it" } ?: "")
        out += Candidate("event", title, sub, Kind.ACTION, score, false, Act.Event(title, w))
        return true
    }

    private fun quiet(nw: List<String>, nq: String): Candidate? {
        val words = setOf("sordina", "silenzio", "concentrazione", "focus", "silenzia", "zitto", "quiet", "silence", "mute",
            "silencio", "concentracion", "concentration", "ruhe", "konzentration", "stumm", "concentracao")
        val dnd = listOf("non disturbare", "modalita silenziosa", "do not disturb", "no molestar", "ne pas deranger", "nicht storen", "nao perturbe", "nao incomodar")
            .any { nq.startsWith(it) }
        if (nw.none { it in words } && !dnd) return null
        val stop = nw.any { it in setOf("basta", "fine", "stop", "togli", "spegni", "esci", "finita", "via", "end", "off", "exit", "cancel",
            "quita", "desactiva", "apaga", "fin", "arrete", "enleve", "desactive", "beende", "aus", "beenden", "desliga", "tira", "desativa") }
        if (stop) return Candidate("quiet:off", tr(R.string.quiet_off), tr(R.string.quiet_off_hint), Kind.ACTION, 96, false, Act.Quiet(null))
        val now = System.currentTimeMillis()
        val d = DateParser.duration(nq)
        val until = when {
            d != null && d.first >= 60 -> now + d.first * 1000L
            nw.any { it in setOf("fino", "alle", "until", "till", "at", "hasta", "jusqu", "bis", "um", "ate", "as", "a") } ->DateParser.parse(nq, eveningBias = false).whenAt?.takeIf { !it.allDay }
                ?.start?.atZone(java.time.ZoneId.systemDefault())?.toInstant()?.toEpochMilli()?.takeIf { it > now }
            else -> null
        }
        // "sordina" da sola resta l'apertura delle impostazioni; con un tempo è la sordina a tempo
        if (until == null) return if (dnd || nw.size > 1) Candidate("quiet:1h", tr(R.string.quiet_for, fmtDuration(3600)), tr(R.string.quiet_hint), Kind.ACTION, 90, false, Act.Quiet(now + 3_600_000)) else null
        val at = java.time.Instant.ofEpochMilli(until).atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))
        return Candidate("quiet", tr(R.string.quiet_until, at), tr(R.string.quiet_hint), Kind.ACTION, 96, false, Act.Quiet(until))
    }


    /** La persona più lunga che corrisponde all'inizio delle parole. (contatto, punteggio, parole usate) */
    private fun personIn(words: List<String>): Triple<Contact, Int, Int>? {
        for (n in minOf(4, words.size) downTo 1) {
            val name = words.take(n).joinToString(" ")
            if (name.length < 2) continue
            nickname(name)?.let { return Triple(it, 95, n) }
            val best = people.all().map { it to Search.score(name, it.normalized) }
                .filter { it.second >= 75 }
                .maxWithOrNull(compareBy<Pair<Contact, Int>> { it.second }
                    .thenBy { if (people.isImportant(it.first.name)) 1 else 0 }
                    .thenBy { if (it.first.starred) 1 else 0 })
            if (best != null) return Triple(best.first, best.second, n)
        }
        return null
    }

    /** L'app con cui quella persona ti scrive di solito; altrimenti SMS. */
    fun viaFor(c: Contact, explicit: Via?): Via {
        if (explicit != null) return explicit
        prefs.personApp[c.normalized]?.let { p -> Via.ofPackage(p)?.let { if (installed(it)) return it } }
        store.appsForPerson(c.name).firstNotNullOfOrNull { Via.ofPackage(it) }?.let { if (installed(it)) return it }
        return Via.SMS
    }

    fun installed(v: Via): Boolean = v == Via.SMS || v.pkgs.any {
        runCatching { context.packageManager.getPackageInfo(it, 0) }.isSuccess
    }

    private fun skip(words: List<String>, set: Set<String>): List<String> {
        var i = 0
        while (i < words.size && (words[i] in set || words[i].isEmpty())) i++
        return words.drop(i)
    }

    /** Sinonimi minimi: "luci" e "luce" sono la stessa cosa. */
    private fun syn(s: String) = (" $s ").replace(" luci ", " luce ").replace(" lampade ", " lampada ")
        .replace(" tapparelle ", " tapparella ").replace(" tende ", " tenda ").trim()

    // Calcoli e unità

    private fun calc(q: String): Candidate? {
        val s = q.trim().lowercase().replace(',', '.')
        units(s)?.let { return Candidate("calc", it, tr(R.string.conversion), Kind.CALC, 99, false, Act.Calc(it)) }
        if (!Regex("^[\\d\\s+\\-*/x×:().^%]+$").matches(s) || !Regex("\\d\\s*[+\\-*/x×:^%]\\s*[\\d(]").containsMatchIn(s)) return null
        val v = runCatching { Expr(s.replace('x', '*').replace('×', '*').replace(':', '/')).parse() }.getOrNull() ?: return null
        if (v.isNaN() || v.isInfinite()) return null
        val r = fmtNumber(v)
        return Candidate("calc", "= $r", s, Kind.CALC, 99, false, Act.Calc(r))
    }

    private fun units(s: String): String? {
        val m = Regex("^(-?\\d+(?:\\.\\d+)?) ?([a-z°]+) (?:in|a) ([a-z°]+)$").find(s) ?: return null
        val v = m.groupValues[1].toDouble()
        val from = UNIT[m.groupValues[2]] ?: return null
        val to = UNIT[m.groupValues[3]] ?: return null
        val r = when {
            from == "c" && to == "f" -> v * 9 / 5 + 32
            from == "f" && to == "c" -> (v - 32) * 5 / 9
            else -> FACTOR[from to to]?.let { v * it } ?: FACTOR[to to from]?.let { v / it } ?: return null
        }
        return "${m.groupValues[1]} ${m.groupValues[2]} = ${fmtNumber(r)} ${m.groupValues[3]}"
    }

    private class Expr(val s: String) {
        var i = 0
        fun parse(): Double { val v = sum(); ws(); require(i == s.length); return v }
        fun ws() { while (i < s.length && s[i] == ' ') i++ }
        fun sum(): Double {
            var v = prod()
            while (true) { ws(); if (i >= s.length) return v
                when (s[i]) { '+' -> { i++; v += prod() }; '-' -> { i++; v -= prod() }; else -> return v } }
        }
        fun prod(): Double {
            var v = pow()
            while (true) { ws(); if (i >= s.length) return v
                when (s[i]) { '*' -> { i++; v *= pow() }; '/' -> { i++; v /= pow() }
                    '%' -> { i++; ws(); if (i < s.length && (s[i].isDigit() || s[i] == '(')) v %= pow() else v /= 100 }
                    else -> return v } }
        }
        fun pow(): Double { val b = unary(); ws(); return if (i < s.length && s[i] == '^') { i++; Math.pow(b, pow()) } else b }
        fun unary(): Double { ws(); return if (i < s.length && s[i] == '-') { i++; -unary() } else atom() }
        fun atom(): Double {
            ws()
            if (s[i] == '(') { i++; val v = sum(); ws(); require(s[i] == ')'); i++; return v }
            val st = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            return s.substring(st, i).toDouble()
        }
    }

    companion object {
        fun fmtNumber(v: Double): String =
            if (v == Math.rint(v) && kotlin.math.abs(v) < 1e15) v.toLong().toString()
            else "%.4f".format(java.util.Locale.ITALIAN, v).trimEnd('0').trimEnd(',')

        fun fmtDuration(sec: Int): String {
            val h = sec / 3600; val m = sec % 3600 / 60; val s = sec % 60
            return listOfNotNull(
                if (h > 0) "$h h" else null, if (m > 0) "$m min" else null, if (s > 0) "$s s" else null,
            ).joinToString(" ").ifEmpty { "0 s" }
        }

        /** Le parole che non sono un nome, in "chiama la mamma", "scrivi a papà". */
        private val FILLER = setOf("chiama", "telefona", "scrivi", "manda", "messaggio", "a", "ad", "al", "alla", "la", "il", "lo", "mia", "mio", "un", "una", "su", "whatsapp", "telegram", "sms", "signal")

        private val VIA_WORDS = setOf("whatsapp", "telegram", "sms", "signal")
        /** Le parole che reggono una frase verso una persona: possono stare anche dopo il nome. */
        private val LEAD_VERBS = VIA_WORDS + setOf("chiama", "telefona", "chiamare", "telefonare", "scrivi", "scrivere", "messaggio", "manda", "invia")

        private val TORCH = setOf("torcia", "flashlight", "torch", "linterna", "taschenlampe", "lanterna")
        /** Aprire: un'app o, per casa, una porta (la frase per Assist resta nella tua lingua). */
        private val OPEN = setOf("apri", "open", "abre", "ouvre", "offne", "oeffne", "öffne", "abra")
        private val PLAY = setOf("metti", "ascolta", "riproduci", "suona", "play", "listen",
            "pon", "reproduce", "escucha", "mets", "joue", "ecoute", "spiel", "spiele", "toca", "ouvir", "reproduz", "coloca")
        /** I verbi di casa nelle altre lingue: non si traducono, Assist parla già la tua lingua. */
        private val HA_FOREIGN = mapOf(
            "enciende" to HaOp.ON, "apaga" to HaOp.OFF, "abre" to HaOp.ON, "cierra" to HaOp.OFF, "sube" to HaOp.ON, "baja" to HaOp.OFF,
            "activa" to HaOp.ON, "desactiva" to HaOp.OFF,
            "allume" to HaOp.ON, "eteins" to HaOp.OFF, "ouvre" to HaOp.ON, "ferme" to HaOp.OFF, "baisse" to HaOp.OFF,
            "active" to HaOp.ON, "desactive" to HaOp.OFF, "regle" to HaOp.LEVEL,
            "schalte" to HaOp.TOGGLE, "schalt" to HaOp.TOGGLE, "offne" to HaOp.ON, "oeffne" to HaOp.ON, "öffne" to HaOp.ON,
            "schliesse" to HaOp.OFF, "schließe" to HaOp.OFF, "schliess" to HaOp.OFF, "dimme" to HaOp.LEVEL,
            "liga" to HaOp.ON, "acende" to HaOp.ON, "desliga" to HaOp.OFF, "abra" to HaOp.ON, "fecha" to HaOp.OFF,
        )

        private val CALL_VERBS = listOf("chiama", "telefona", "call")
        private val MSG_VERBS = listOf("scrivi", "messaggio", "manda", "write", "text")

        private val MEDIA get() = mapOf(
            "pausa" to (tr(R.string.media_pause) to KeyEvent.KEYCODE_MEDIA_PAUSE),
            "metti in pausa" to (tr(R.string.media_pause) to KeyEvent.KEYCODE_MEDIA_PAUSE),
            "stop" to (tr(R.string.media_pause) to KeyEvent.KEYCODE_MEDIA_PAUSE),
            "ferma la musica" to (tr(R.string.media_pause) to KeyEvent.KEYCODE_MEDIA_PAUSE),
            "play" to (tr(R.string.media_play) to KeyEvent.KEYCODE_MEDIA_PLAY),
            "riprendi" to (tr(R.string.media_play) to KeyEvent.KEYCODE_MEDIA_PLAY),
            "continua" to (tr(R.string.media_play) to KeyEvent.KEYCODE_MEDIA_PLAY),
            "avanti" to (tr(R.string.media_next) to KeyEvent.KEYCODE_MEDIA_NEXT),
            "prossima" to (tr(R.string.media_next) to KeyEvent.KEYCODE_MEDIA_NEXT),
            "prossima canzone" to (tr(R.string.media_next) to KeyEvent.KEYCODE_MEDIA_NEXT),
            "successiva" to (tr(R.string.media_next) to KeyEvent.KEYCODE_MEDIA_NEXT),
            "salta" to (tr(R.string.media_next) to KeyEvent.KEYCODE_MEDIA_NEXT),
            "indietro" to (tr(R.string.media_prev) to KeyEvent.KEYCODE_MEDIA_PREVIOUS),
            "precedente" to (tr(R.string.media_prev) to KeyEvent.KEYCODE_MEDIA_PREVIOUS),
            "canzone precedente" to (tr(R.string.media_prev) to KeyEvent.KEYCODE_MEDIA_PREVIOUS),
            "pause" to (tr(R.string.media_pause) to KeyEvent.KEYCODE_MEDIA_PAUSE),
            "pause music" to (tr(R.string.media_pause) to KeyEvent.KEYCODE_MEDIA_PAUSE),
            "stop music" to (tr(R.string.media_pause) to KeyEvent.KEYCODE_MEDIA_PAUSE),
            "resume" to (tr(R.string.media_play) to KeyEvent.KEYCODE_MEDIA_PLAY),
            "next" to (tr(R.string.media_next) to KeyEvent.KEYCODE_MEDIA_NEXT),
            "next song" to (tr(R.string.media_next) to KeyEvent.KEYCODE_MEDIA_NEXT),
            "skip" to (tr(R.string.media_next) to KeyEvent.KEYCODE_MEDIA_NEXT),
            "previous" to (tr(R.string.media_prev) to KeyEvent.KEYCODE_MEDIA_PREVIOUS),
            "previous song" to (tr(R.string.media_prev) to KeyEvent.KEYCODE_MEDIA_PREVIOUS),
            // Spagnolo, francese, tedesco, portoghese
            "siguiente" to (tr(R.string.media_next) to KeyEvent.KEYCODE_MEDIA_NEXT),
            "siguiente cancion" to (tr(R.string.media_next) to KeyEvent.KEYCODE_MEDIA_NEXT),
            "anterior" to (tr(R.string.media_prev) to KeyEvent.KEYCODE_MEDIA_PREVIOUS),
            "reanuda" to (tr(R.string.media_play) to KeyEvent.KEYCODE_MEDIA_PLAY),
            "para la musica" to (tr(R.string.media_pause) to KeyEvent.KEYCODE_MEDIA_PAUSE),
            "suivant" to (tr(R.string.media_next) to KeyEvent.KEYCODE_MEDIA_NEXT),
            "chanson suivante" to (tr(R.string.media_next) to KeyEvent.KEYCODE_MEDIA_NEXT),
            "precedent" to (tr(R.string.media_prev) to KeyEvent.KEYCODE_MEDIA_PREVIOUS),
            "reprends" to (tr(R.string.media_play) to KeyEvent.KEYCODE_MEDIA_PLAY),
            "arrete la musique" to (tr(R.string.media_pause) to KeyEvent.KEYCODE_MEDIA_PAUSE),
            "weiter" to (tr(R.string.media_next) to KeyEvent.KEYCODE_MEDIA_NEXT),
            "nachster titel" to (tr(R.string.media_next) to KeyEvent.KEYCODE_MEDIA_NEXT),
            "nachstes lied" to (tr(R.string.media_next) to KeyEvent.KEYCODE_MEDIA_NEXT),
            "zuruck" to (tr(R.string.media_prev) to KeyEvent.KEYCODE_MEDIA_PREVIOUS),
            "stopp" to (tr(R.string.media_pause) to KeyEvent.KEYCODE_MEDIA_PAUSE),
            "proxima musica" to (tr(R.string.media_next) to KeyEvent.KEYCODE_MEDIA_NEXT),
            "retoma" to (tr(R.string.media_play) to KeyEvent.KEYCODE_MEDIA_PLAY),
        )

        /** (parole, titolo, azione delle Impostazioni di Android) */
        private val SETTINGS: List<Triple<List<String>, String, String>> get() = listOf(
            Triple(listOf("wifi", "wi fi", "rete", "network"), "Wi-Fi", Settings.ACTION_WIFI_SETTINGS),
            Triple(listOf("bluetooth"), "Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS),
            Triple(listOf("dati mobili", "internet", "dati", "mobile data"), tr(R.string.set_internet), Settings.ACTION_WIRELESS_SETTINGS),
            Triple(listOf("modalita aereo", "aereo", "airplane mode", "flight mode", "modo avion", "mode avion", "flugmodus", "modo aviao"), tr(R.string.set_airplane), Settings.ACTION_AIRPLANE_MODE_SETTINGS),
            Triple(listOf("luminosita", "schermo", "display", "brightness", "screen", "brillo", "pantalla", "luminosite", "ecran", "helligkeit", "bildschirm", "brilho", "tela", "ecra"), tr(R.string.set_display), Settings.ACTION_DISPLAY_SETTINGS),
            Triple(listOf("suoni", "volume", "suoneria", "sound", "ringtone", "sonido", "volumen", "sonnerie", "lautstarke", "klingelton", "som", "toque"), tr(R.string.set_sound), Settings.ACTION_SOUND_SETTINGS),
            Triple(listOf("batteria", "risparmio energetico", "battery", "battery saver", "bateria", "batterie", "akku"), tr(R.string.s_battery), Settings.ACTION_BATTERY_SAVER_SETTINGS),
            Triple(listOf("posizione", "gps", "localizzazione", "location", "ubicacion", "localisation", "standort", "localizacao"), tr(R.string.set_location), Settings.ACTION_LOCATION_SOURCE_SETTINGS),
            Triple(listOf("nfc", "pagamenti contactless", "contactless"), "NFC", Settings.ACTION_NFC_SETTINGS),
            Triple(listOf("non disturbare", "do not disturb", "no molestar", "ne pas deranger", "nicht storen", "nao perturbe"), tr(R.string.set_dnd), "android.settings.ZEN_MODE_SETTINGS"),
            Triple(listOf("impostazioni", "settings", "ajustes", "configuracion", "reglages", "parametres", "einstellungen", "configuracoes", "definicoes"), tr(R.string.set_android), Settings.ACTION_SETTINGS),
            Triple(listOf("hotspot", "router", "tethering"), tr(R.string.set_hotspot), Settings.ACTION_WIRELESS_SETTINGS),
            Triple(listOf("lingua", "tastiera", "language", "keyboard", "idioma", "teclado", "langue", "clavier", "sprache", "tastatur"), tr(R.string.set_language), Settings.ACTION_LOCALE_SETTINGS),
            Triple(listOf("data", "ora", "fuso orario", "date", "time", "time zone"), tr(R.string.set_datetime), Settings.ACTION_DATE_SETTINGS),
            Triple(listOf("sordina", "impostazioni sordina", "sordina settings"), tr(R.string.set_sordina), "sordina"),
        )

        private val UNIT = mapOf(
            "km" to "km", "chilometri" to "km", "mi" to "mi", "miglia" to "mi", "kg" to "kg", "chili" to "kg",
            "lb" to "lb", "libbre" to "lb", "c" to "c", "°c" to "c", "celsius" to "c", "f" to "f", "°f" to "f",
            "fahrenheit" to "f", "cm" to "cm", "centimetri" to "cm", "in" to "in", "pollici" to "in",
            "m" to "m", "metri" to "m", "ft" to "ft", "piedi" to "ft", "l" to "l", "litri" to "l", "gal" to "gal", "galloni" to "gal",
            "g" to "g", "grammi" to "g", "oz" to "oz", "once" to "oz",
        )
        private val FACTOR = mapOf(
            ("km" to "mi") to 0.621371, ("kg" to "lb") to 2.20462, ("cm" to "in") to 0.393701,
            ("m" to "ft") to 3.28084, ("l" to "gal") to 0.264172, ("g" to "oz") to 0.035274,
            ("km" to "m") to 1000.0, ("m" to "cm") to 100.0, ("kg" to "g") to 1000.0,
        )
    }
}
