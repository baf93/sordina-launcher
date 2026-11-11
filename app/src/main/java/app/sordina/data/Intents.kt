package app.sordina.data

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
    data class Ha(val entity: HaEntity, val op: HaOp, val value: Int? = null) : Act
    /** Frase passata ad Assist di Home Assistant */
    data class Assist(val text: String) : Act
    data class Setting(val action: String) : Act
    data object SordinaSettings : Act
    data class Web(val query: String) : Act
    data class Calc(val result: String) : Act
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
) {
    fun understand(query: String, apps: List<AppEntry>, usage: Map<String, Double>): Understanding {
        val q = query.trim()
        if (q.isEmpty()) return Understanding(emptyList(), false)
        val ow = q.split(Regex("\\s+"))
        val nw = ow.map { Search.normalize(it) }
        val nq = nw.filter { it.isNotEmpty() }.joinToString(" ")
        val out = ArrayList<Candidate>()

        calc(q)?.let { out += it }
        structured(ow, nw, nq, apps, out)

        // Per nome: app, persone, casa, impostazioni
        if (nq.isNotEmpty()) {
            Search.apps(q, apps, usage, 5).forEach { a ->
                out += Candidate("app:${a.key}", a.label, if (a.isWork) "Lavoro" else null, Kind.APP, Search.score(nq, a.normalized), false, Act.OpenApp(a), a)
            }
            people.search(nq, 2).forEach { c ->
                val s = Search.score(nq, c.normalized)
                out += Candidate("call:${c.id}", "Chiama ${c.name}", c.number, Kind.PERSON, s - 6, true, Act.Call(c))
                val via = viaFor(c, null)
                out += Candidate("msg:${c.id}", "Scrivi a ${c.name}", via.label, Kind.PERSON, s - 9, true, Act.Message(c, "", via))
            }
            SETTINGS.forEach { (words, title, action) ->
                val s = words.maxOf { Search.score(nq, it) }
                if (s >= 75) out += Candidate("set:$action", title, "Impostazioni", Kind.SETTING, s - 8, false, if (action == "sordina") Act.SordinaSettings else Act.Setting(action))
            }
        }

        // Correzioni imparate
        val learned = prefs.aliases[nq]
        val ranked = out
            .map { if (it.id == learned) it.copy(score = (it.score + 30).coerceAtMost(100)) else it }
            .groupBy { it.id }.map { g -> g.value.maxBy { it.score } }
            .sortedWith(compareByDescending<Candidate> { it.score }
                .thenByDescending { it.app?.let { a -> usage[a.packageName] } ?: 0.0 })
            .take(5)
        val web = Candidate("web", "Cerca «$q» sul web", null, Kind.WEB, 0, false, Act.Web(q))
        val top = ranked.firstOrNull()
        val second = ranked.getOrNull(1)
        val confident = top != null && top.score >= 85 && (second == null || top.score - second.score >= 10 || top.id == learned)
        // Sicurezza bassa: "Cerca sul web" in cima
        val list = if (top == null || top.score < 45) listOf(web) + ranked else ranked + web
        return Understanding(list, confident)
    }

    /** Registra cosa è stato scelto: misura e correzioni. */
    fun chosen(query: String, c: Candidate, index: Int) {
        prefs.intentTotal = prefs.intentTotal + 1
        if (index == 0) prefs.intentFirst = prefs.intentFirst + 1
        val nq = Search.normalize(query)
        if (nq.isEmpty() || c.kind == Kind.WEB) return
        if (index > 0) prefs.aliases = prefs.aliases + (nq to c.id)
    }

    // Frasi con struttura

    private fun structured(ow: List<String>, nw: List<String>, nq: String, apps: List<AppEntry>, out: MutableList<Candidate>) {
        val first = nw.firstOrNull() ?: return
        val restN = nw.drop(1).filter { it.isNotEmpty() }.joinToString(" ")

        // Torcia
        if ("torcia" in nw || nq == "luce telefono") {
            out += Candidate("torch", "Torcia", "accendi o spegni", Kind.ACTION, 97, false, Act.Torch); return
        }
        // Musica in riproduzione
        MEDIA[nq]?.let { (title, key) -> out += Candidate("media:$key", title, "musica in riproduzione", Kind.ACTION, 96, false, Act.Media(key)); return }

        // Timer
        if ("timer" in nw || nq.startsWith("conto alla rovescia")) {
            val d = DateParser.duration(nq)
            if (d != null) {
                val label = d.second.split(' ').filter { it !in setOf("timer", "metti", "un", "imposta", "per", "avvia", "conto", "alla", "rovescia", "di", "da") }.joinToString(" ")
                val t = fmtDuration(d.first)
                out += Candidate("timer", "Timer $t" + if (label.isNotBlank()) " · $label" else "", "parte subito", Kind.ACTION, 96, false, Act.Timer(d.first, label))
            } else {
                out += Candidate("timer?", "Timer", "di' anche quanto: «timer 10 minuti»", Kind.ACTION, 50, false, Act.Timer(0, ""))
            }
            return
        }
        // Sveglia
        if (first == "sveglia" || first == "svegliami") {
            val r = DateParser.parse(nq.removePrefix(first), eveningBias = false)
            val w = r.whenAt
            if (w != null && !w.allDay) {
                out += Candidate("alarm", "Sveglia alle %d:%02d".format(w.start.hour, w.start.minute), "impostata senza aprire l'orologio", Kind.ACTION, 96, false, Act.Alarm(w.start.hour, w.start.minute, ""))
                return
            }
        }
        // Chiama
        if (first in setOf("chiama", "telefona", "chiamare", "telefonare")) {
            val rest = skip(nw.drop(1), setOf("a", "ad"))
            personIn(rest)?.let { (c, s, _) ->
                out += Candidate("call:${c.id}", "Chiama ${c.name}", c.number, Kind.PERSON, if (s >= 80) 95 else 80, true, Act.Call(c))
            }
            return
        }
        // Scrivi
        val msgVerbs = setOf("scrivi", "scrivere", "messaggio", "manda", "invia", "whatsapp", "sms", "telegram", "signal")
        if (first in msgVerbs) {
            var via: Via? = when (first) { "whatsapp" -> Via.WHATSAPP; "sms" -> Via.SMS; "telegram" -> Via.TELEGRAM; "signal" -> Via.SIGNAL; else -> null }
            // "su whatsapp", "via telegram", "con sms" ovunque nella frase
            val idx = ArrayList((1 until nw.size).toList())
            for (i in 1 until nw.size - 1) {
                if (nw[i] in setOf("su", "via", "con", "per") ) {
                    val v = when (nw[i + 1]) { "whatsapp" -> Via.WHATSAPP; "telegram" -> Via.TELEGRAM; "signal" -> Via.SIGNAL; "sms", "messaggio" -> Via.SMS; else -> null }
                    if (v != null) { via = v; idx.remove(i); idx.remove(i + 1) }
                }
            }
            var k = 0
            val restIdx = idx.filter { nw[it].isNotEmpty() }.toMutableList()
            while (k < restIdx.size && nw[restIdx[k]] in setOf("un", "messaggio", "a", "ad", "al", "alla")) k++
            val tail = restIdx.drop(k)
            val found = personIn(tail.map { nw[it] }) ?: return
            val (c, s, used) = found
            val textWords = tail.drop(used).map { ow[it] }.toMutableList()
            while (textWords.isNotEmpty() && Search.normalize(textWords.first()) in setOf("che", "dicendo", "dicendogli", "dicendole", ":")) textWords.removeAt(0)
            val text = textWords.joinToString(" ").replaceFirstChar { it.uppercase() }
            val v = viaFor(c, via)
            out += Candidate("msg:${c.id}:${v.name}", "Scrivi a ${c.name}", (if (text.isNotBlank()) "«$text» · " else "") + v.label, Kind.PERSON, if (s >= 80) 95 else 80, true, Act.Message(c, text, v))
            // Le altre destinazioni, un tocco più in basso
            Via.entries.filter { it != v && installed(it) }.take(2).forEach { o ->
                out += Candidate("msg:${c.id}:${o.name}", "Scrivi a ${c.name}", "su ${o.label}", Kind.PERSON, 70, true, Act.Message(c, text, o))
            }
            return
        }
        // Impegni e promemoria
        val eventVerbs = setOf("aggiungi", "impegno", "evento", "appuntamento", "promemoria", "ricordami", "segna", "calendario", "metti")
        if (first in eventVerbs) {
            val r = DateParser.parse(nw.drop(1).joinToString(" "))
            val w = r.whenAt
            val filler = setOf("di", "un", "una", "uno", "l", "il", "lo", "la", "in", "nel", "al", "calendario", "impegno", "evento", "appuntamento", "promemoria", "che", "ho", "a")
            val words = r.rest.split(' ').filter { it.isNotBlank() }.toMutableList()
            while (words.isNotEmpty() && words.first() in filler) words.removeAt(0)
            while (words.isNotEmpty() && words.last() in filler) words.removeAt(words.size - 1)
            val title = words.joinToString(" ").replaceFirstChar { it.uppercase() }
            if (w != null && title.isNotBlank()) {
                val cal = Calendars(context, prefs).default()
                val sub = DateParser.describe(w) + (if (w.movedToTomorrow) " (oggi era già passato)" else "") + (cal?.let { " · ${it.name}" } ?: "")
                out += Candidate("event", title, sub, Kind.ACTION, if (first == "metti") 80 else 94, false, Act.Event(title, w))
                return
            }
            if (first != "metti" && first != "aggiungi" && title.isNotBlank()) {
                val t = java.time.LocalDate.now().plusDays(1).atTime(9, 0)
                out += Candidate("event", title, "domani alle 9:00 (non hai detto quando)", Kind.ACTION, 70, false, Act.Event(title, When(t, false, false)))
            }
        }
        // Navigazione
        val nav = listOf("naviga verso", "naviga a", "portami a", "portami", "indicazioni per", "vai a", "andiamo a", "verso")
        nav.firstOrNull { nq.startsWith("$it ") }?.let { p ->
            val n = p.split(' ').size
            val place = ow.drop(n).joinToString(" ")
            if (place.isNotBlank()) out += Candidate("nav", "Verso $place", "navigazione", Kind.ACTION, 90, false, Act.Navigate(place))
            return
        }
        // Casa
        val haVerbs = mapOf("accendi" to HaOp.ON, "attiva" to HaOp.ON, "apri" to HaOp.ON, "alza" to HaOp.ON,
            "spegni" to HaOp.OFF, "disattiva" to HaOp.OFF, "chiudi" to HaOp.OFF, "abbassa" to HaOp.OFF, "stacca" to HaOp.OFF,
            "imposta" to HaOp.LEVEL, "regola" to HaOp.LEVEL)
        haVerbs[first]?.let { op0 ->
            val level = Regex("(?:al|a|su) (\\d{1,3}) ?(%|percento|per cento|gradi)?").find(restN)?.groupValues?.get(1)?.toIntOrNull()
            val target = restN.replace(Regex("(?:al|a|su) \\d{1,3} ?(%|percento|per cento|gradi)?"), "").trim()
            if (ha.configured && target.isNotBlank()) {
                // Casa: la frase va ad Assist, che conosce stanze, alias ed entità esposte
                out += Candidate("assist", ow.joinToString(" ").replaceFirstChar { it.uppercase() }, "Casa", Kind.HOME, if (first == "apri") 80 else 90, false, Act.Assist(ow.joinToString(" ")))
            }
            if (first == "apri") {
                Search.apps(restN, apps, emptyMap(), 2).forEach { a ->
                    out += Candidate("app:${a.key}", a.label, null, Kind.APP, Search.score(restN, a.normalized).coerceAtMost(92), false, Act.OpenApp(a), a)
                }
            }
            if (first == "accendi" || first == "spegni" || first == "attiva" || first == "disattiva") {
                SETTINGS.filter { (w, _, _) -> w.any { Search.score(target, it) >= 80 } }.forEach { (_, title, action) ->
                    out += Candidate("set:$action", title, "Impostazioni", Kind.SETTING, 84, false, Act.Setting(action))
                }
            }
            return
        }
        // Musica
        if (first in setOf("metti", "ascolta", "riproduci", "suona") && restN.isNotBlank()) {
            if (restN == "musica" || restN == "la musica") {
                out += Candidate("media:${KeyEvent.KEYCODE_MEDIA_PLAY}", "Riprendi la musica", null, Kind.ACTION, 92, false, Act.Media(KeyEvent.KEYCODE_MEDIA_PLAY))
            } else {
                val what = ow.drop(1).joinToString(" ")
                out += Candidate("music", "Metti $what", "nell'app di musica", Kind.ACTION, 82, false, Act.Music(what))
            }
        }
    }

    private fun haCandidate(e: HaEntity, op: HaOp, level: Int?, score: Int): Candidate {
        val verb = when (op) {
            HaOp.ON -> if (e.domain == "cover") "Apri" else if (e.domain in setOf("script", "scene")) "Esegui" else "Accendi"
            HaOp.OFF -> if (e.domain == "cover") "Chiudi" else "Spegni"
            HaOp.TOGGLE -> if (e.domain == "cover") (if (e.isOn) "Chiudi" else "Apri") else if (e.domain in setOf("script", "scene")) "Esegui" else if (e.isOn) "Spegni" else "Accendi"
            HaOp.LEVEL -> "Imposta"
        }
        val title = "$verb ${e.name}" + if (op == HaOp.LEVEL && level != null) " a $level" + (if (e.domain == "climate") "°" else "%") else ""
        return Candidate("ha:${e.id}:$op", title, "Casa · ${e.state}", Kind.HOME, score, false, Act.Ha(e, op, level))
    }

    /** La persona più lunga che corrisponde all'inizio delle parole. (contatto, punteggio, parole usate) */
    private fun personIn(words: List<String>): Triple<Contact, Int, Int>? {
        for (n in minOf(4, words.size) downTo 1) {
            val name = words.take(n).joinToString(" ")
            if (name.length < 2) continue
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
        units(s)?.let { return Candidate("calc", it, "conversione", Kind.CALC, 99, false, Act.Calc(it)) }
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

        private val MEDIA = mapOf(
            "pausa" to ("Pausa" to KeyEvent.KEYCODE_MEDIA_PAUSE),
            "metti in pausa" to ("Pausa" to KeyEvent.KEYCODE_MEDIA_PAUSE),
            "stop" to ("Pausa" to KeyEvent.KEYCODE_MEDIA_PAUSE),
            "ferma la musica" to ("Pausa" to KeyEvent.KEYCODE_MEDIA_PAUSE),
            "play" to ("Riprendi" to KeyEvent.KEYCODE_MEDIA_PLAY),
            "riprendi" to ("Riprendi" to KeyEvent.KEYCODE_MEDIA_PLAY),
            "continua" to ("Riprendi" to KeyEvent.KEYCODE_MEDIA_PLAY),
            "avanti" to ("Prossima canzone" to KeyEvent.KEYCODE_MEDIA_NEXT),
            "prossima" to ("Prossima canzone" to KeyEvent.KEYCODE_MEDIA_NEXT),
            "prossima canzone" to ("Prossima canzone" to KeyEvent.KEYCODE_MEDIA_NEXT),
            "successiva" to ("Prossima canzone" to KeyEvent.KEYCODE_MEDIA_NEXT),
            "salta" to ("Prossima canzone" to KeyEvent.KEYCODE_MEDIA_NEXT),
            "indietro" to ("Canzone precedente" to KeyEvent.KEYCODE_MEDIA_PREVIOUS),
            "precedente" to ("Canzone precedente" to KeyEvent.KEYCODE_MEDIA_PREVIOUS),
            "canzone precedente" to ("Canzone precedente" to KeyEvent.KEYCODE_MEDIA_PREVIOUS),
        )

        /** (parole, titolo, azione delle Impostazioni di Android) */
        private val SETTINGS: List<Triple<List<String>, String, String>> = listOf(
            Triple(listOf("wifi", "wi fi", "rete"), "Wi-Fi", Settings.ACTION_WIFI_SETTINGS),
            Triple(listOf("bluetooth"), "Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS),
            Triple(listOf("dati mobili", "internet", "dati"), "Internet", Settings.ACTION_WIRELESS_SETTINGS),
            Triple(listOf("modalita aereo", "aereo"), "Modalità aereo", Settings.ACTION_AIRPLANE_MODE_SETTINGS),
            Triple(listOf("luminosita", "schermo", "display"), "Schermo e luminosità", Settings.ACTION_DISPLAY_SETTINGS),
            Triple(listOf("suoni", "volume", "suoneria"), "Suoni e volume", Settings.ACTION_SOUND_SETTINGS),
            Triple(listOf("batteria", "risparmio energetico"), "Batteria", Settings.ACTION_BATTERY_SAVER_SETTINGS),
            Triple(listOf("posizione", "gps", "localizzazione"), "Posizione", Settings.ACTION_LOCATION_SOURCE_SETTINGS),
            Triple(listOf("nfc", "pagamenti contactless"), "NFC", Settings.ACTION_NFC_SETTINGS),
            Triple(listOf("non disturbare"), "Non disturbare", "android.settings.ZEN_MODE_SETTINGS"),
            Triple(listOf("impostazioni", "settings"), "Impostazioni di Android", Settings.ACTION_SETTINGS),
            Triple(listOf("hotspot", "router"), "Hotspot e connessioni", Settings.ACTION_WIRELESS_SETTINGS),
            Triple(listOf("lingua", "tastiera"), "Lingua e tastiera", Settings.ACTION_LOCALE_SETTINGS),
            Triple(listOf("data", "ora", "fuso orario"), "Data e ora", Settings.ACTION_DATE_SETTINGS),
            Triple(listOf("sordina", "impostazioni sordina"), "Impostazioni di Sordina", "sordina"),
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
