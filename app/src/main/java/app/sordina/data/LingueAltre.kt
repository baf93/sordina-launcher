package app.sordina.data

import java.text.Normalizer
import java.util.Locale
import java.util.regex.Pattern

/**
 * Le altre lingue dell'app, oltre a italiano, inglese, spagnolo, francese, tedesco e portoghese ([Lingue]).
 * Come lì, tutto diventa la frase italiana che il motore capisce già: "zavolej Petrovi" → "chiama Petrovi",
 * "明天早上7点叫醒我" → "svegliami domani mattina alle 7".
 *
 * Ogni lingua è un [Pack] di dati: parole del giorno, ore, durate, giorni, comandi, verbi di casa, parole sensibili.
 * Un pacchetto vale solo se il telefono parla quella lingua: "bel" in olandese è "chiama", in italiano no.
 *
 * I comandi sono modelli: "{x}" è chi o dove (un nome), "{y}" è il resto (la data, il testo del messaggio).
 * Senza segnaposto il modello è l'inizio della frase e il resto segue com'è. Lo spazio nel modello vuol dire spazio,
 * niente spazio vuol dire attaccato ("{x}에게 전화해": il nome e la particella sono una parola sola).
 * Per cinese, giapponese e thai, che non usano spazi, gli spazi del modello sono facoltativi.
 */
object LingueAltre {

    private class Pack(
        val langs: Set<String>,
        /** Senza spazi fra le parole (cinese, giapponese, thai) */
        val glued: Boolean = false,
        /** Parole del giorno → italiano: "zítra" → "domani". Le più lunghe vincono. */
        val words: List<Pair<String, String>> = emptyList(),
        /** Prima dell'ora, sempre un'ora: "v 7", "kl 7", "στις 7" */
        val at: List<String> = emptyList(),
        /** Prima dell'ora ma anche di una durata ("na 7" / "na 10 minut"): ora solo se dopo non c'è un'unità */
        val atWeak: List<String> = emptyList(),
        /** Dopo il numero dell'ora: "hodin", "giờ", "시", "時", "点", "बजे". Per le lingue senza spazi, espressioni. */
        val hour: List<String> = emptyList(),
        /** Dopo il numero dei minuti di un'ora: "phút", "분", "分" */
        val minute: List<String> = emptyList(),
        /** "e mezza" dopo l'ora: "rưỡi", "반", "半" */
        val half: List<String> = emptyList(),
        /** Particelle dopo l'ora da togliere: "에", "に" */
        val hourTail: List<String> = emptyList(),
        /** "tra" prima della durata: "za", "om", "через" */
        val inBefore: List<String> = emptyList(),
        /** "tra" dopo la durata: "päästä", "nữa", "후에", "後", "में" */
        val inAfter: List<String> = emptyList(),
        /** Unità: "minuti" → parole. Per le lingue senza spazi, espressioni ("日(?=後)"). */
        val units: Map<String, List<String>> = emptyMap(),
        /** Giorni da lunedì a domenica, ciascuno con le sue forme */
        val days: List<List<String>> = emptyList(),
        val dayPre: List<String> = emptyList(),
        val dayPost: List<String> = emptyList(),
        /** "prossimo" prima o dopo il giorno */
        val next: List<String> = emptyList(),
        val nextAfter: List<String> = emptyList(),
        /** Numeri scritti in lettere davanti a un'ora o un'unità (cinese, giapponese) */
        val numerals: List<Pair<String, String>> = emptyList(),
        /** I mesi da gennaio a dicembre, ciascuno con le sue forme ("března", "březen"): valgono dopo un numero ("15. března") */
        val months: List<List<String>> = emptyList(),
        /** Mese e giorno in cifre: espressione con i gruppi "m" e "d" ("3月15日", "15 tháng 3") */
        val monthDay: String? = null,
        /** Regole proprie della lingua, prima delle altre: espressione → frase italiana ("บ่าย 3 โมง" → "alle 15") */
        val extra: List<Pair<String, (MatchResult) -> String>> = emptyList(),
        /** Le desinenze dei nomi ("Petrovi", "Marka", "Маше"): si tolgono per trovare la persona in rubrica */
        val cases: List<String> = emptyList(),
        /** Tende, tapparelle, persiane: aprirle non è un rischio */
        val harmless: List<String> = emptyList(),
        /** Comandi: modello → frase italiana con {x} e {y} */
        val cmds: List<Pair<String, String>> = emptyList(),
        /** Cortesie in fondo o in testa da togliere prima dei comandi: "ください", "주세요", "หน่อย" */
        val tails: List<String> = emptyList(),
        val leads: List<String> = emptyList(),
        /** Da togliere in fondo al nome: "さん", la particella dopo l'apostrofo turco */
        val nameTail: Regex? = null,
        /** Verbi di casa in testa: parola → operazione */
        val homeFirst: Map<String, HaOp> = emptyMap(),
        /** Casa con il verbo in fondo o attaccato: modello con {x} → operazione */
        val home: List<Pair<String, HaOp>> = emptyList(),
        /** Porte, cancelli, garage, serrature, allarmi: comandi da confermare */
        val sensitive: List<String> = emptyList(),
        /** Verbi che aprono o sbloccano */
        val opening: List<String> = emptyList(),
    )

    // ── Normalizzazione

    private val MARKS = Regex("\\p{Mn}+")
    /** Come il testo che arriva da [DateParser.clean] e [Search.normalize]: minuscolo, senza accenti */
    private fun key(s: String) = Lingue.flat(Search.normalize(s))
    /** Lo stesso per le espressioni: niente accenti, ma i simboli restano */
    private fun keyRaw(s: String) = Lingue.flat(MARKS.replace(Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD), ""))
    private fun alt(ws: Collection<String>) = ws.map { Regex.escape(key(it)) }.distinct().sortedByDescending { it.length }.joinToString("|")
    private fun altRaw(ws: Collection<String>) = ws.map { keyRaw(it) }.distinct().sortedByDescending { it.length }.joinToString("|")

    /** La lingua che decide i pacchetti attivi: quella del telefono (nei test si cambia). */
    @Volatile var language: () -> String = { Locale.getDefault().language }

    private fun active(): List<Pack> {
        val l = when (val x = language()) { "in" -> "id"; "no" -> "nb"; else -> x }
        return PACKS.filter { l in it.langs }
    }

    // ── Date, ore, durate

    private class Rule(val re: Regex, val to: (MatchResult) -> String)

    private fun at(h: String, m: String?) = " alle $h${m?.takeIf { it.isNotEmpty() }?.let { ":" + it.padStart(2, '0') } ?: ""} "

    private val IT_UNITS = "minuti|ore|secondi|giorni|settimane"
    private val IT_MONTHS = listOf("gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno", "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre")

    /** Un numero in caratteri cinesi o giapponesi: "三" 3, "十五" 15, "二十" 20, "二十五" 25 */
    fun cjk(s: String): Int {
        val d = mapOf('零' to 0, '〇' to 0, '一' to 1, '二' to 2, '两' to 2, '兩' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9)
        val i = s.indexOf('十')
        if (i < 0) return s.fold(0) { a, c -> a * 10 + (d[c] ?: 0) }
        val tens = if (i == 0) 1 else d[s[i - 1]] ?: 1
        val ones = if (i == s.length - 1) 0 else d[s[i + 1]] ?: 0
        return tens * 10 + ones
    }

    private val SPACES = Regex("\\s{2,}")
    private val PART = Regex(" (di mattina|del pomeriggio|di sera|di notte) alle (\\d{1,2}(?::\\d{2})?) ")

    private val dateRules = java.util.concurrent.ConcurrentHashMap<Pack, List<Rule>>()

    private fun rulesOf(p: Pack): List<Rule> = dateRules.getOrPut(p) { synchronized(this) { buildRules(p) } }

    private fun buildRules(p: Pack): List<Rule> = buildList {
        val g = p.glued
        val ws = if (g) "\\s?" else " "
        val b = if (g) "" else " "  // confine di parola
        fun out(s: String) = " $s "
        // Numeri in caratteri davanti a un'ora, un'unità, un mese o un giorno (三点, 二十五分钟, 十一月)
        if (p.numerals.isNotEmpty()) {
            val markers = (p.hour + p.units.values.flatten() + listOf("月", "日", "号", "號")).joinToString("|") { keyRaw(it) }
            add(Rule(Regex("[零〇一二两兩三四五六七八九十]+(?=\\s?(?:$markers))")) { m -> cjk(m.value).toString() })
        }
        for ((re, to) in p.extra) add(Rule(Regex(keyRaw(re)), to))
        // Il giorno del mese: "15. března", "3月15日" → "15 marzo"
        p.monthDay?.let { md -> add(Rule(Regex(keyRaw(md))) { m ->
            val mo = m.groups["m"]!!.value.toInt(); val d = m.groups["d"]!!.value
            if (mo in 1..12) out("$d ${IT_MONTHS[mo - 1]}") else m.value
        }) }
        if (p.months.isNotEmpty()) {
            val all = p.months.flatMapIndexed { i, fs -> fs.map { (if (g) keyRaw(it) else key(it)) to IT_MONTHS[i] } }.toMap()
            val alts = all.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
            add(Rule(Regex("(?<=\\d|\\d\\.|\\d )\\.?\\s?($alts)(?=$b|$)")) { m -> " ${all[m.groupValues[1]]} " })
        }
        val unitWords = if (g) altRaw(p.units.values.flatten()) else alt(p.units.values.flatten())
        val hourW = if (p.hour.isEmpty()) null else if (g) altRaw(p.hour) else alt(p.hour)
        val minW = if (p.minute.isEmpty()) null else if (g) altRaw(p.minute) else alt(p.minute)
        val halfW = if (p.half.isEmpty()) null else if (g) altRaw(p.half) else alt(p.half)
        val tailW = if (p.hourTail.isEmpty()) null else altRaw(p.hourTail)
        // Ora con la parola davanti: "v 7", "kl 7:30", "στις 7"
        if (p.at.isNotEmpty()) {
            val hw = hourW?.let { "(?:$ws(?:$it))?" } ?: ""
            add(Rule(Regex("${if (g) "" else " "}(?:${if (g) altRaw(p.at) else alt(p.at)})\\.?$ws(\\d{1,2})(?:[:.](\\d{2}))?$hw(?:$ws(\\d{1,2})$ws(?:${minW ?: "(?!)"}))? ")) { m ->
                at(m.groupValues[1], m.groupValues[2].ifEmpty { m.groupValues[3] })
            })
        }
        if (p.atWeak.isNotEmpty()) {
            val stop = if (unitWords.isEmpty()) "" else "(?!(?:$unitWords))"
            add(Rule(Regex(" (?:${alt(p.atWeak)}) (\\d{1,2})(?:[:.](\\d{2}))? $stop")) { m -> at(m.groupValues[1], m.groupValues[2]) })
        }
        // Ora con la parola dopo: "7 giờ 30", "7시 반", "7点半", "7 बजे"
        if (hourW != null) {
            val notIn = if (p.inBefore.isEmpty()) "" else "(?<!(?:${alt(p.inBefore)}) )"
            val notAfter = if (p.inAfter.isEmpty()) "" else "(?!$ws(?:${if (g) altRaw(p.inAfter) else alt(p.inAfter)}))"
            val mins = buildList {
                if (minW != null) add("$ws(\\d{1,2})(?:$ws(?:$minW))?")
                if (halfW != null) add("$ws(?:$halfW)")
            }.joinToString("|")
            val tail = tailW?.let { "(?:$ws(?:$it))?" } ?: ""
            add(Rule(Regex("$notIn(?<![\\d:])(\\d{1,2})(?:[:.](\\d{2}))?$ws(?:$hourW)(?:$mins)?$notAfter$tail")) { m ->
                val min = m.groupValues[2].ifEmpty { m.groupValues.getOrNull(3).orEmpty() }
                    .ifEmpty { if (halfW != null && Regex(halfW).containsMatchIn(m.value.substringAfter(m.groupValues[1]))) "30" else "" }
                at(m.groupValues[1], min)
            })
        }
        // Parole del giorno
        for ((w, v) in p.words.sortedByDescending { it.first.length }) {
            val k = if (g) Regex.escape(keyRaw(w)) else Regex.escape(key(w))
            add(Rule(Regex("$b$k$b")) { out(v) })
        }
        // Durate
        for ((unit, list) in p.units) {
            val u = if (g) altRaw(list) else alt(list)
            add(Rule(Regex(if (g) "(\\d+|un)\\s?(?:$u)" else " (\\d+|un) (?:$u) ")) { m -> out("${m.groupValues[1]} $unit") })
        }
        if (p.inBefore.isNotEmpty())
            add(Rule(Regex(" (?:${alt(p.inBefore)}) (?=(?:\\d+|un) (?:$IT_UNITS) )")) { " tra " })
        if (p.inAfter.isNotEmpty()) {
            val a = if (g) altRaw(p.inAfter) else alt(p.inAfter)
            add(Rule(Regex(" ((?:\\d+|un) (?:$IT_UNITS)) ?(?:$a)$b")) { m -> out("tra ${m.groupValues[1]}") })
        }
        // Giorni della settimana
        val itDays = listOf("lunedi", "martedi", "mercoledi", "giovedi", "venerdi", "sabato", "domenica")
        val pre = if (p.dayPre.isEmpty()) "" else "(?:(?:${if (g) altRaw(p.dayPre) else alt(p.dayPre)})$ws)?"
        val nx = if (p.next.isEmpty()) "" else "((?:${if (g) altRaw(p.next) else alt(p.next)})$ws)?"
        val post = (p.dayPost + p.nextAfter).let { if (it.isEmpty()) "" else "($ws(?:${if (g) altRaw(it) else alt(it)}))?" }
        val nextAfter = p.nextAfter.map { if (g) keyRaw(it) else key(it) }
        p.days.forEachIndexed { i, forms ->
            val d = if (g) altRaw(forms) else alt(forms)
            add(Rule(Regex("$b$pre$nx(?:$d)$post$b")) { m ->
                val isNext = (nx.isNotEmpty() && m.groupValues[1].isNotBlank()) || nextAfter.any { n -> m.value.contains(n) }
                out(if (isNext) "${itDays[i]} prossimo" else itDays[i])
            })
        }
    }

    /** Testo già pulito ([DateParser.clean]); restituisce le stesse parole, le date in italiano. */
    fun dates(s0: String): String {
        val packs = active()
        if (packs.isEmpty()) return s0
        var s = " $s0 "
        for (p in packs) for (r in rulesOf(p)) s = SPACES.replace(r.re.replace(s) { r.to(it) }, " ")
        // "del pomeriggio alle 3" (coreano, giapponese, cinese, hindi) → "alle 3 del pomeriggio"
        s = " " + s.replace(Regex("\\s+"), " ").trim() + " "
        s = PART.replace(s) { m -> " alle ${m.groupValues[2]} ${m.groupValues[1]} " }
        return s.replace(Regex("\\s+"), " ").trim()
    }

    // ── Comandi

    private class Cmd(val strict: Regex, val loose: Regex, val to: String, val holes: List<Char>)

    private val cmdCache = java.util.concurrent.ConcurrentHashMap<Pack, List<Cmd>>()

    /** Un modello diventa un'espressione: i letterali come sono (con e senza accenti), {x} e {y} catturati. */
    private fun compile(template: String, glued: Boolean, normalize: (String) -> String): String {
        val ws = if (glued) "\\s*" else "\\s+"
        val parts = Regex("\\{[xy]\\}").split(template)
        val holes = Regex("\\{([xy])\\}").findAll(template).map { it.groupValues[1] }.toList()
        val sb = StringBuilder("^")
        parts.forEachIndexed { i, lit ->
            // Gli spazi del modello: spazio vero; attaccato al segnaposto senza spazio: facoltativo
            val words = lit.split(' ')
            words.forEachIndexed { j, w ->
                if (j > 0) sb.append(ws)
                if (w.isNotEmpty()) sb.append(Regex.escape(normalize(w)))
            }
            if (i < holes.size) {
                val last = i == holes.size - 1 && parts.getOrNull(i + 1).isNullOrEmpty()
                val glue = if (lit.isNotEmpty() && !lit.endsWith(' ')) "\\s*" else ""
                sb.append(glue)
                sb.append(if (holes[i] == "y" && last) "(.*?)" else if (holes[i] == "y") "(.*?)" else "(.+?)")
                if (!last && parts.getOrNull(i + 1)?.startsWith(' ') == false) sb.append("\\s*")
            }
        }
        sb.append("$")
        return sb.toString()
    }

    private fun cmdsOf(p: Pack): List<Cmd> = cmdCache.getOrPut(p) {
        synchronized(this) {
            p.cmds.flatMap { (tpls, to) -> tpls.split('/').map { it.trim() to to } }
                .sortedByDescending { (t, _) -> t.replace(Regex("\\{[xy]\\}"), "").length }
                .map { (t0, to) ->
                    val prefixOnly = !t0.contains('{')
                    val t = if (prefixOnly) "$t0 {y}" else t0
                    val flags = Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
                    // "{x}" a sinistra di un letterale attaccato ("{x}에게"): il nome può avere spazi
                    val strict = Pattern.compile(compile(t, p.glued) { Lingue.flat(it) }.let { if (prefixOnly) it.replaceFirst("\\s+(.*?)$", "(?:\\s+(.*?))?$") else it }, flags).toRegex()
                    val loose = Pattern.compile(compile(t, p.glued) { key(it).ifEmpty { it } }.let { if (prefixOnly) it.replaceFirst("\\s+(.*?)$", "(?:\\s+(.*?))?$") else it }, flags).toRegex()
                    Cmd(strict, loose, to, Regex("\\{([xy])\\}").findAll(t).map { it.groupValues[1][0] }.toList())
                }
        }
    }

    private fun clip(p: Pack, s: String): String {
        var t = s.trim()
        repeat(2) {
            for (x in p.tails.sortedByDescending { it.length }) if (t.lowercase().endsWith(x.lowercase())) t = t.dropLast(x.length).trim()
            for (x in p.leads.sortedByDescending { it.length }) if (t.lowercase().startsWith(x.lowercase() + (if (p.glued) "" else " ")) || (p.glued && t.startsWith(x))) t = t.drop(x.length).trim()
        }
        return t
    }

    /**
     * Le parole di un comando in una di queste lingue, riscritte in italiano; null se non è uno di questi.
     * Il testo catturato (nomi, messaggi) resta com'è scritto, maiuscole e accenti compresi.
     */
    fun command(words: List<String>): List<String>? {
        val packs = active()
        if (packs.isEmpty() || words.isEmpty()) return null
        val raw = words.joinToString(" ")
        for (p in packs) {
            val orig = clip(p, raw)
            val text = Lingue.flat(orig)  // stessa lunghezza: le parti catturate si riprendono da come le hai scritte
            val norm = key(text)
            // Prima com'è scritto, in tutti i modelli; poi senza accenti (in hindi "करो" e "करें" senza segni sono uguali)
            for ((c, loose) in cmdsOf(p).map { it to false } + cmdsOf(p).map { it to true }) {
                val strict = if (loose) null else c.strict.find(text)
                val m = strict ?: (if (loose) c.loose.find(norm) else null) ?: continue
                val g = if (strict != null) strict.groups.drop(1).map { gr -> gr?.range?.let { orig.substring(it) }.orEmpty() } else m.groupValues.drop(1)
                var x = ""
                var y = ""
                c.holes.forEachIndexed { i, h -> val v = g.getOrNull(i).orEmpty().trim(); if (h == 'x') x = v else y = v }
                if ('x' in c.holes && x.isEmpty()) continue
                p.nameTail?.let { t -> x = x.replace(t, "").trim() }
                val out = c.to.replace("{x}", x).replace("{y}", y).let { if (!c.to.contains("{y}") && 'y' in c.holes) "$it $y" else it }
                return out.split(Regex("\\s+")).filter { it.isNotEmpty() }
            }
        }
        return null
    }

    // ── Casa

    /** C'è un pacchetto per la lingua del telefono: si scrive in quella lingua, i verbi non sono italiano sbagliato */
    fun on(): Boolean = active().isNotEmpty()

    /** Il verbo di casa in testa, nella lingua del telefono ("zapni světlo", "bật đèn"). */
    fun homeFirst(): Map<String, HaOp> = active().flatMap { p -> p.homeFirst.entries.map { key(it.key) to it.value } }.toMap()

    /** Casa col verbo in fondo o attaccato ("salon ışığını aç", "ライトをつけて", "เปิดไฟ"): operazione e cosa. */
    fun home(words: List<String>): Pair<HaOp, String>? {
        val raw = words.joinToString(" ")
        for (p in active()) {
            val orig = clip(p, raw)
            val text = Lingue.flat(orig)
            for ((tpl, op) in p.home.sortedByDescending { it.first.length }) {
                val re = Pattern.compile(compile(tpl, p.glued) { Lingue.flat(it) }, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE).toRegex()
                val m = re.find(text) ?: continue
                val target = (if (m.groups.size > 1) m.groups[1] else null)?.range?.let { orig.substring(it) }.orEmpty().trim()
                if (target.isNotEmpty()) return op to target
            }
        }
        return null
    }

    /** Porte, cancelli, garage, serrature, allarmi, e i verbi che aprono: nella lingua del telefono ("lås" in danese, "las" in spagnolo) */
    private val sensCache = java.util.concurrent.ConcurrentHashMap<Pack, Pair<List<String>, List<String>>>()
    private fun sensOf(p: Pack) = sensCache.getOrPut(p) {
        fun k(w: String) = if (p.glued) keyRaw(w) else key(w)
        p.sensitive.map(::k).filter { it.isNotEmpty() }.distinct() to p.opening.map(::k).filter { it.isNotEmpty() }.distinct()
    }
    private val SENS get() = active().flatMap { sensOf(it).first }
    private val OPEN get() = active().flatMap { sensOf(it).second }

    /** [words] normalizzate. Le parole corte valgono intere o all'inizio; quelle lunghe anche dentro (parole composte). */
    fun sensitive(words: List<String>): Boolean {
        val joined = words.joinToString(" ")
        return SENS.any { s -> if (s.contains(' ') || s.length >= 4 || asian(s)) joined.contains(s) else words.any { w -> w == s || w.startsWith(s) } }
    }

    private val ASIAN = setOf(Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA,
        Character.UnicodeScript.HANGUL, Character.UnicodeScript.THAI)
    /** Scritture senza spazi (o con le parole attaccate): si cerca dentro la frase */
    private fun asian(s: String) = s.any { Character.UnicodeScript.of(it.code) in ASIAN }

    /** Un verbo che apre o sblocca, ovunque nella frase (in molte di queste lingue il verbo è in fondo). */
    fun opening(words: List<String>): Boolean {
        val joined = " " + words.joinToString(" ") + " "
        return OPEN.any { o -> if (asian(o)) joined.contains(o) else joined.contains(" $o ") }
    }

    /** Tende, tapparelle, persiane nella lingua del telefono ([words] normalizzate) */
    fun harmless(words: List<String>): Boolean {
        val joined = words.joinToString(" ")
        return active().any { p -> p.harmless.any { h -> val k = if (p.glued) keyRaw(h) else key(h); k.isNotEmpty() && (if (asian(k)) joined.contains(k) else words.any { it.startsWith(k) }) } }
    }

    /** La radice di un nome senza la desinenza ("marka" → "mark", "marek" → "mark", "petrovi" → "petr") */
    private fun stem(w: String, cases: List<String>): String {
        var r = w
        cases.map { key(it) }.filter { it.isNotEmpty() }.sortedByDescending { it.length }
            .firstOrNull { r.endsWith(it) && r.length - it.length >= 3 }?.let { r = r.dropLast(it.length) }
        // La "e" che cade: Marek → Marka, Pavel → Pavla, Павел → Павла
        if (r.length >= 4 && r[r.length - 2] == 'e' || r.length >= 4 && r[r.length - 2] == 'е') {
            val c1 = r[r.length - 3]; val c2 = r.last()
            if (c1 !in VOWELS && c2 !in VOWELS) r = r.dropLast(2) + c2
        }
        return r
    }
    private const val VOWELS = "aeiouyаеиоуыэюяіїє"

    /**
     * [query] (normalizzato, una o più parole) è il nome di [contact] (normalizzato) declinato:
     * "petrovi" per Petr, "marka" per Marek, "маше" per Маша. Solo nelle lingue con le declinazioni.
     */
    fun sameName(query: String, contact: String): Boolean {
        val cases = active().flatMap { it.cases }
        if (cases.isEmpty()) return false
        val q = query.split(' ').filter { it.isNotEmpty() }
        val c = contact.split(' ').filter { it.isNotEmpty() }
        if (q.isEmpty() || q.size > c.size) return false
        val qs = q.map { stem(it, cases) }
        val cs = c.map { stem(it, cases) }
        if (qs.any { it.length < 3 }) return false
        fun same(i: Int, j: Int) = qs[i] == cs[j] || qs[i] == c[j]
        return qs.indices.all { same(it, it) } || (qs.size == 1 && c.indices.any { same(0, it) })
    }

    /** Le prime parole dei comandi e i verbi di casa: non sono verbi italiani scritti male. */
    val VERBS: Set<String> by lazy {
        PACKS.flatMap { p -> p.cmds.flatMap { (t, _) -> t.split('/') }.mapNotNull { t -> t.trim().takeIf { !it.startsWith("{") }?.split(' ')?.firstOrNull()?.let { key(it) } } + p.homeFirst.keys.map { key(it) } }
            .filter { it.isNotEmpty() && !it.contains(' ') }.toSet()
    }

    // ── I pacchetti

    private val CALL = "chiama {x}"
    private val MSG = "manda un messaggio a {x} {y}"
    private val WA = "manda un whatsapp a {x} {y}"

    private val PACKS: List<Pack> by lazy { listOf(NL, PL, RU, TR, CS, DA, NB, SV, EL, FI, RO, UK, VI, ID, AR, HI, KO, JA, ZH, TH) }

    private val NL = Pack(
        langs = setOf("nl"),
        harmless = listOf("gordijn", "rolluik", "zonwering", "jaloezie", "screen", "lamel"),
        cmds = listOf(
            "herinner me eraan dat/herinner me eraan om/herinner me aan/herinner me" to "ricordami",
            "maak me wakker/wek me" to "svegliami",
            "zet een wekker/zet de wekker/zet wekker/zet een alarm/zet alarm/wekker/alarm" to "sveglia",
            "zet een timer van/zet een timer voor/zet een timer op/zet timer op/zet een timer/timer van/timer voor/timer" to "timer",
            "bel naar/bel" to "chiama",
            "stuur een bericht naar/stuur een bericht aan/stuur bericht naar/stuur een sms naar/stuur een sms aan/sms" to "manda un messaggio a",
            "stuur een whatsapp naar/stuur een whatsapp aan/app" to "manda un whatsapp a",
            "schrijf naar/schrijf aan" to "scrivi a",
            "zeg tegen/zeg" to "di a",
            "antwoord aan/antwoord" to "rispondi a",
            "navigeer naar/breng me naar/route naar/hoe kom ik bij/hoe kom ik naar/rijd naar" to "portami a",
            "voeg een afspraak toe/voeg een evenement toe/maak een afspraak/plan een afspraak/nieuwe afspraak/nieuw evenement" to "aggiungi",
        ),
        homeFirst = mapOf("zet" to HaOp.TOGGLE, "doe" to HaOp.TOGGLE, "schakel" to HaOp.TOGGLE, "sluit" to HaOp.OFF, "dim" to HaOp.LEVEL, "ontgrendel" to HaOp.ON),
        sensitive = listOf("deur", "voordeur", "achterdeur", "garagedeur", "poort", "slot", "alarm", "ontgrendel"),
        opening = listOf("open", "ontgrendel"),
    )

    private val PL = Pack(
        langs = setOf("pl"),
        cases = listOf("owi", "ego", "emu", "iem", "em", "ie", "a", "u", "e", "i", "y", "o", "ą", "ę"),
        harmless = listOf("rolet", "zasłon", "żaluzj", "markiz"),
        cmds = listOf(
            "przypomnij mi o/przypomnij mi że/przypomnij mi żeby/przypomnij mi/przypomnij" to "ricordami",
            "obudź mnie" to "svegliami",
            "ustaw budzik/ustaw alarm/nastaw budzik/budzik" to "sveglia",
            "ustaw minutnik na/ustaw timer na/nastaw minutnik na/minutnik na/timer na/odliczaj" to "timer",
            "zadzwoń do/zadzwoń/dzwoń do" to "chiama",
            "wyślij wiadomość do/wyślij sms do/wyślij esemesa do/napisz wiadomość do/wyślij wiadomość" to "manda un messaggio a",
            "wyślij whatsappa do/napisz na whatsappie do" to "manda un whatsapp a",
            "napisz do" to "scrivi a",
            "powiedz" to "di a",
            "odpowiedz" to "rispondi a",
            "nawiguj do/zaprowadź mnie do/zawieź mnie do/jak dojechać do/jak dojść do/trasa do/prowadź do" to "portami a",
            "dodaj wydarzenie/dodaj spotkanie/dodaj przypomnienie/nowe wydarzenie/utwórz wydarzenie" to "aggiungi",
        ),
        homeFirst = mapOf("włącz" to HaOp.ON, "wyłącz" to HaOp.OFF, "otwórz" to HaOp.ON, "zamknij" to HaOp.OFF, "przyciemnij" to HaOp.LEVEL, "zapal" to HaOp.ON, "zgaś" to HaOp.OFF),
        sensitive = listOf("drzwi", "brama", "bramę", "bramy", "garaż", "garażu", "zamek", "zamka", "alarm", "odblokuj"),
        opening = listOf("otwórz", "odblokuj", "rozbrój"),
    )

    private val RU = Pack(
        langs = setOf("ru"),
        cases = listOf("ому", "ему", "ого", "его", "ой", "ей", "ою", "ею", "ом", "ем", "у", "ю", "е", "а", "я", "и"),
        harmless = listOf("штор", "жалюзи", "рольстав", "занавес", "маркиз"),
        cmds = listOf(
            "напомни мне что/напомни мне чтобы/напомни мне о/напомни мне/напомни" to "ricordami",
            "разбуди меня" to "svegliami",
            "поставь будильник/установи будильник/заведи будильник/будильник" to "sveglia",
            "поставь таймер на/установи таймер на/таймер на/засеки" to "timer",
            "позвони/набери/вызови" to "chiama",
            "отправь сообщение/напиши сообщение/отправь смс/отправь эсэмэску" to "manda un messaggio a",
            "отправь whatsapp/напиши в whatsapp/напиши в ватсап/отправь в ватсап" to "manda un whatsapp a",
            "напиши" to "scrivi a",
            "скажи" to "di a",
            "ответь" to "rispondi a",
            "проложи маршрут до/построй маршрут до/маршрут до/отвези меня в/отвези меня на/отвези меня до/как доехать до/как добраться до/навигация до/поехали в/поехали на" to "portami a",
            "добавь событие/добавь встречу/создай событие/создай встречу/новое событие/добавь в календарь" to "aggiungi",
        ),
        homeFirst = mapOf("включи" to HaOp.ON, "выключи" to HaOp.OFF, "открой" to HaOp.ON, "закрой" to HaOp.OFF, "убавь" to HaOp.LEVEL, "приглуши" to HaOp.LEVEL),
        sensitive = listOf("дверь", "двери", "ворота", "гараж", "замок", "сигнализац", "домофон", "разблокируй"),
        opening = listOf("открой", "отопри", "разблокируй"),
    )

    private val TR = Pack(
        langs = setOf("tr"),
        harmless = listOf("perde", "panjur", "jaluzi", "stor", "tente"),
        cmds = listOf(
            "{x} {y} diye mesaj at/{x} {y} diye mesaj gönder/{x} {y} diye yaz" to MSG,
            "{x} mesaj gönder/{x} mesaj at/{x} mesaj yolla/{x} sms at/{x} sms gönder" to "manda un messaggio a {x}",
            "{x} whatsapp at/{x} whatsapp gönder/{x} whatsapptan yaz" to "manda un whatsapp a {x}",
            "{x} arar mısın/{x} ara" to CALL,
            "ara" to "chiama",
            "{x} yaz" to "scrivi a {x}",
            "{x} {y} söyle" to "di a {x} {y}",
            "bana {y} hatırlat/{y} hatırlat/hatırlat" to "ricordami {y}",
            "beni {y} uyandır/{y} beni uyandır/{y} uyandır" to "svegliami {y}",
            "{y} için alarm kur/{y} alarm kur/{y} alarmı kur/{y} alarm ayarla/alarm kur" to "sveglia {y}",
            "{y} zamanlayıcı kur/{y} zamanlayıcı ayarla/{y} sayaç kur/{y} geri sayım/{y} zamanlayıcı/zamanlayıcı kur" to "timer {y}",
            "beni {x} götür/{x} yol tarifi ver/{x} yol tarifi/{x} navigasyon başlat/{x} nasıl giderim" to "portami a {x}",
            "takvime {y} ekle/{y} takvime ekle/{y} etkinlik ekle/{y} randevu ekle" to "aggiungi {y}",
            "{x} cevap ver/{x} yanıtla" to "rispondi a {x}",
        ),
        tails = listOf("lütfen"), leads = listOf("lütfen"),
        nameTail = Regex("['’].*$"),
        home = listOf("{x} aç" to HaOp.ON, "{x} yak" to HaOp.ON, "{x} kapat" to HaOp.OFF, "{x} söndür" to HaOp.OFF),
        sensitive = listOf("kapı", "kapıyı", "kapısı", "garaj", "kilit", "kilidi", "alarm"),
        opening = listOf("aç", "kilidi aç"),
    )

    private val CS = Pack(
        langs = setOf("cs"),
        months = listOf(listOf("ledna", "leden"), listOf("února", "únor"), listOf("března", "březen"), listOf("dubna", "duben"), listOf("května", "květen"), listOf("června", "červen"), listOf("července", "červenec"), listOf("srpna", "srpen"), listOf("září"), listOf("října", "říjen"), listOf("listopadu", "listopad"), listOf("prosince", "prosinec")),
        cases = listOf("ovi", "ovou", "ou", "em", "u", "e", "ě", "a", "i", "y", "o"),
        harmless = listOf("žaluzi", "rolet", "závěs", "markýz"),
        words = listOf(
            "dnes večer" to "stasera", "dnes ráno" to "stamattina", "dnes odpoledne" to "oggi pomeriggio", "dnes v noci" to "stanotte",
            "zítra ráno" to "domani mattina", "zítra odpoledne" to "domani pomeriggio", "zítra večer" to "domani sera",
            "pozítří" to "dopodomani", "dnes" to "oggi", "zítra" to "domani",
            "ráno" to "di mattina", "odpoledne" to "del pomeriggio", "večer" to "di sera", "v noci" to "di notte",
        ),
        at = listOf("v", "ve"), atWeak = listOf("na"), hour = listOf("hodin", "hodiny", "hodinu"),
        inBefore = listOf("za"),
        units = mapOf(
            "minuti" to listOf("minut", "minuty", "minutu"), "ore" to listOf("hodin", "hodiny", "hodinu", "hodina"),
            "secondi" to listOf("sekund", "sekundy", "sekundu"), "giorni" to listOf("dní", "dny", "den", "dnů"),
            "settimane" to listOf("týdnů", "týdny", "týden"),
        ),
        days = listOf(listOf("pondělí"), listOf("úterý"), listOf("středa", "středu"), listOf("čtvrtek"), listOf("pátek"), listOf("sobota", "sobotu"), listOf("neděle", "neděli")),
        dayPre = listOf("v", "ve"), next = listOf("příští"),
        cmds = listOf(
            "připomeň mi že/připomeň mi abych/připomeň mi" to "ricordami",
            "vzbuď mě/probuď mě" to "svegliami",
            "nastav budík/nastav alarm/budík/budíček" to "sveglia",
            "nastav časovač na/nastav minutku na/časovač na/minutka na/nastav časovač/časovač/odpočet" to "timer",
            "zavolej na/zavolej/volej" to "chiama",
            "pošli zprávu/pošli sms/napiš zprávu" to "manda un messaggio a",
            "pošli whatsapp/napiš na whatsapp" to "manda un whatsapp a",
            "napiš pro/napiš" to "scrivi a",
            "řekni" to "di a",
            "odpověz" to "rispondi a",
            "naviguj do/naviguj na/naviguj k/vezmi mě do/vezmi mě na/jak se dostanu do/jak se dostanu na/trasa do" to "portami a",
            "přidej událost/přidej schůzku/přidej připomínku/nová událost/vytvoř událost" to "aggiungi",
        ),
        homeFirst = mapOf("zapni" to HaOp.ON, "vypni" to HaOp.OFF, "otevři" to HaOp.ON, "zavři" to HaOp.OFF, "ztlum" to HaOp.LEVEL, "rozsviť" to HaOp.ON, "zhasni" to HaOp.OFF),
        sensitive = listOf("dveře", "dveří", "vrata", "brána", "bránu", "garáž", "zámek", "alarm", "odemkni"),
        opening = listOf("otevři", "odemkni", "odjisti"),
    )

    private val DA = Pack(
        langs = setOf("da"),
        months = listOf(listOf("januar"), listOf("februar"), listOf("marts"), listOf("april"), listOf("maj"), listOf("juni"), listOf("juli"), listOf("august"), listOf("september"), listOf("oktober"), listOf("november"), listOf("december")),
        harmless = listOf("gardin", "persienne", "rullegardin", "markise"),
        words = listOf(
            "i aften" to "stasera", "i morges" to "stamattina", "i eftermiddag" to "oggi pomeriggio", "i nat" to "stanotte",
            "i morgen tidlig" to "domani mattina", "i morgen eftermiddag" to "domani pomeriggio", "i morgen aften" to "domani sera",
            "i overmorgen" to "dopodomani", "overmorgen" to "dopodomani", "i dag" to "oggi", "i morgen" to "domani",
            "om morgenen" to "di mattina", "om eftermiddagen" to "del pomeriggio", "om aftenen" to "di sera",
        ),
        at = listOf("kl", "klokken"), atWeak = listOf("til"), inBefore = listOf("om"),
        units = mapOf(
            "minuti" to listOf("minutter", "minut"), "ore" to listOf("timer", "time"), "secondi" to listOf("sekunder", "sekund"),
            "giorni" to listOf("dage", "dag"), "settimane" to listOf("uger", "uge"),
        ),
        days = listOf(listOf("mandag"), listOf("tirsdag"), listOf("onsdag"), listOf("torsdag"), listOf("fredag"), listOf("lørdag"), listOf("søndag")),
        dayPre = listOf("på", "om"), next = listOf("næste", "på"),
        cmds = listOf(
            "mind mig om at/mind mig om/husk mig på" to "ricordami",
            "væk mig/vågn mig" to "svegliami",
            "sæt en alarm/sæt alarm/sæt vækkeuret/stil vækkeuret/alarm" to "sveglia",
            "sæt en timer på/sæt timer på/start en timer på/timer på" to "timer",
            "ring til/ring" to "chiama",
            "send en besked til/send besked til/send en sms til/send sms til" to "manda un messaggio a",
            "send en whatsapp til" to "manda un whatsapp a",
            "skriv til" to "scrivi a",
            "sig til" to "di a",
            "svar" to "rispondi a",
            "naviger til/vis vej til/kør til/tag mig til" to "portami a",
            "tilføj en begivenhed/tilføj begivenhed/tilføj en aftale/opret en aftale/ny aftale" to "aggiungi",
        ),
        homeFirst = mapOf("tænd" to HaOp.ON, "sluk" to HaOp.OFF, "åbn" to HaOp.ON, "luk" to HaOp.OFF, "dæmp" to HaOp.LEVEL),
        sensitive = listOf("dør", "døren", "port", "porten", "garage", "lås", "låsen", "alarm"),
        opening = listOf("åbn", "lås op"),
    )

    private val NB = Pack(
        langs = setOf("nb"),
        months = listOf(listOf("januar"), listOf("februar"), listOf("mars"), listOf("april"), listOf("mai"), listOf("juni"), listOf("juli"), listOf("august"), listOf("september"), listOf("oktober"), listOf("november"), listOf("desember")),
        harmless = listOf("gardin", "persienne", "rullegardin", "markise"),
        words = listOf(
            "i kveld" to "stasera", "i morges" to "stamattina", "i ettermiddag" to "oggi pomeriggio", "i natt" to "stanotte",
            "i morgen tidlig" to "domani mattina", "i morgen ettermiddag" to "domani pomeriggio", "i morgen kveld" to "domani sera",
            "i overmorgen" to "dopodomani", "overmorgen" to "dopodomani", "i dag" to "oggi", "i morgen" to "domani",
            "om morgenen" to "di mattina", "om ettermiddagen" to "del pomeriggio", "om kvelden" to "di sera",
        ),
        at = listOf("kl", "klokka", "klokken"), atWeak = listOf("til"), inBefore = listOf("om"),
        units = mapOf(
            "minuti" to listOf("minutter", "minutt"), "ore" to listOf("timer", "time"), "secondi" to listOf("sekunder", "sekund"),
            "giorni" to listOf("dager", "dag", "døgn"), "settimane" to listOf("uker", "uke"),
        ),
        days = listOf(listOf("mandag"), listOf("tirsdag"), listOf("onsdag"), listOf("torsdag"), listOf("fredag"), listOf("lørdag"), listOf("søndag")),
        dayPre = listOf("på"), next = listOf("neste"),
        cmds = listOf(
            "minn meg på å/minn meg på/påminn meg om" to "ricordami",
            "vekk meg" to "svegliami",
            "sett en alarm/sett alarm/still vekkerklokka/alarm" to "sveglia",
            "sett en timer på/sett timer på/start en timer på/timer på" to "timer",
            "ring til/ring" to "chiama",
            "send en melding til/send melding til/send en sms til/send sms til" to "manda un messaggio a",
            "send en whatsapp til" to "manda un whatsapp a",
            "skriv til" to "scrivi a",
            "si til" to "di a",
            "svar" to "rispondi a",
            "naviger til/vis vei til/kjør til/ta meg til" to "portami a",
            "legg til en hendelse/legg til hendelse/legg til en avtale/ny avtale" to "aggiungi",
        ),
        homeFirst = mapOf("slå" to HaOp.TOGGLE, "skru" to HaOp.TOGGLE, "tenn" to HaOp.ON, "åpne" to HaOp.ON, "lukk" to HaOp.OFF, "demp" to HaOp.LEVEL),
        sensitive = listOf("dør", "døra", "døren", "port", "porten", "garasje", "lås", "låsen", "alarm"),
        opening = listOf("åpne", "lås opp"),
    )

    private val SV = Pack(
        langs = setOf("sv"),
        months = listOf(listOf("januari"), listOf("februari"), listOf("mars"), listOf("april"), listOf("maj"), listOf("juni"), listOf("juli"), listOf("augusti"), listOf("september"), listOf("oktober"), listOf("november"), listOf("december")),
        harmless = listOf("gardin", "persienn", "rullgardin", "markis"),
        words = listOf(
            "i kväll" to "stasera", "ikväll" to "stasera", "i morse" to "stamattina", "imorse" to "stamattina",
            "i eftermiddag" to "oggi pomeriggio", "i natt" to "stanotte",
            "i morgon bitti" to "domani mattina", "imorgon bitti" to "domani mattina", "i morgon förmiddag" to "domani mattina",
            "i morgon eftermiddag" to "domani pomeriggio", "i morgon kväll" to "domani sera", "imorgon kväll" to "domani sera",
            "i övermorgon" to "dopodomani", "övermorgon" to "dopodomani", "i dag" to "oggi", "idag" to "oggi",
            "i morgon" to "domani", "imorgon" to "domani",
            "på morgonen" to "di mattina", "på eftermiddagen" to "del pomeriggio", "på kvällen" to "di sera",
        ),
        at = listOf("kl", "klockan"), atWeak = listOf("till"), inBefore = listOf("om"),
        units = mapOf(
            "minuti" to listOf("minuter", "minut"), "ore" to listOf("timmar", "timme"), "secondi" to listOf("sekunder", "sekund"),
            "giorni" to listOf("dagar", "dag", "dygn"), "settimane" to listOf("veckor", "vecka"),
        ),
        days = listOf(listOf("måndag"), listOf("tisdag"), listOf("onsdag"), listOf("torsdag"), listOf("fredag"), listOf("lördag"), listOf("söndag")),
        dayPre = listOf("på"), next = listOf("nästa"),
        cmds = listOf(
            "påminn mig om att/påminn mig om/påminn mig att/påminn mig" to "ricordami",
            "väck mig" to "svegliami",
            "ställ ett alarm/ställ alarm/ställ väckarklockan/väckarklocka/alarm" to "sveglia",
            "ställ en timer på/ställ timer på/starta en timer på/timer på" to "timer",
            "ring till/ring" to "chiama",
            "skicka ett meddelande till/skicka meddelande till/skicka ett sms till/skicka sms till" to "manda un messaggio a",
            "skicka en whatsapp till" to "manda un whatsapp a",
            "skriv till" to "scrivi a",
            "säg till" to "di a",
            "svara" to "rispondi a",
            "navigera till/vägbeskrivning till/kör till/ta mig till" to "portami a",
            "lägg till en händelse/lägg till händelse/lägg till ett möte/nytt möte/ny händelse" to "aggiungi",
        ),
        homeFirst = mapOf("tänd" to HaOp.ON, "släck" to HaOp.OFF, "slå" to HaOp.TOGGLE, "öppna" to HaOp.ON, "stäng" to HaOp.OFF, "dimma" to HaOp.LEVEL),
        sensitive = listOf("dörr", "dörren", "port", "porten", "garage", "lås", "låset", "larm", "larmet"),
        opening = listOf("öppna", "lås upp"),
    )

    private val EL = Pack(
        langs = setOf("el"),
        months = listOf(listOf("ιανουαρίου", "ιανουάριος"), listOf("φεβρουαρίου", "φεβρουάριος"), listOf("μαρτίου", "μάρτιος"), listOf("απριλίου", "απρίλιος"), listOf("μαΐου", "μάιος"), listOf("ιουνίου", "ιούνιος"), listOf("ιουλίου", "ιούλιος"), listOf("αυγούστου", "αύγουστος"), listOf("σεπτεμβρίου", "σεπτέμβριος"), listOf("οκτωβρίου", "οκτώβριος"), listOf("νοεμβρίου", "νοέμβριος"), listOf("δεκεμβρίου", "δεκέμβριος")),
        harmless = listOf("κουρτίν", "ρολά", "ρολό", "παντζούρ", "τέντα", "περσίδ"),
        words = listOf(
            "απόψε" to "stasera", "σήμερα το πρωί" to "stamattina", "σήμερα το απόγευμα" to "oggi pomeriggio",
            "αύριο το πρωί" to "domani mattina", "αύριο το απόγευμα" to "domani pomeriggio", "αύριο το βράδυ" to "domani sera",
            "μεθαύριο" to "dopodomani", "σήμερα" to "oggi", "αύριο" to "domani",
            "το πρωί" to "di mattina", "το απόγευμα" to "del pomeriggio", "το βράδυ" to "di sera",
        ),
        at = listOf("στις"), inBefore = listOf("σε", "μέσα σε"),
        units = mapOf(
            "minuti" to listOf("λεπτά", "λεπτό"), "ore" to listOf("ώρες", "ώρα"), "secondi" to listOf("δευτερόλεπτα", "δευτερόλεπτο"),
            "giorni" to listOf("μέρες", "μέρα", "ημέρες"), "settimane" to listOf("εβδομάδες", "εβδομάδα"),
        ),
        days = listOf(listOf("δευτέρα"), listOf("τρίτη"), listOf("τετάρτη"), listOf("πέμπτη"), listOf("παρασκευή"), listOf("σάββατο"), listOf("κυριακή")),
        dayPre = listOf("την", "το"), next = listOf("επόμενη", "ερχόμενη", "επόμενο"),
        cmds = listOf(
            "θύμισέ μου ότι/θύμισέ μου να/θύμισέ μου/υπενθύμισέ μου" to "ricordami",
            "ξύπνα με/ξύπνησέ με" to "svegliami",
            "βάλε ξυπνητήρι/όρισε ξυπνητήρι/ξυπνητήρι" to "sveglia",
            "βάλε χρονόμετρο για/βάλε χρονόμετρο/χρονόμετρο για/αντίστροφη μέτρηση" to "timer",
            "πάρε τηλέφωνο τον/πάρε τηλέφωνο την/πάρε τηλέφωνο/τηλεφώνησε στον/τηλεφώνησε στην/τηλεφώνησε/κάλεσε" to "chiama",
            "στείλε μήνυμα στον/στείλε μήνυμα στην/στείλε μήνυμα σε/στείλε μήνυμα/στείλε sms" to "manda un messaggio a",
            "στείλε whatsapp στον/στείλε whatsapp στην/στείλε whatsapp" to "manda un whatsapp a",
            "γράψε στον/γράψε στην/γράψε σε" to "scrivi a",
            "πες στον/πες στην" to "di a",
            "απάντησε στον/απάντησε στην/απάντησε" to "rispondi a",
            "πήγαινέ με στο/πήγαινέ με στη/πλοήγηση προς/πλοήγηση στο/οδηγίες για" to "portami a",
            "πρόσθεσε εκδήλωση/πρόσθεσε ραντεβού/νέο ραντεβού/νέα εκδήλωση" to "aggiungi",
        ),
        homeFirst = mapOf("άναψε" to HaOp.ON, "σβήσε" to HaOp.OFF, "άνοιξε" to HaOp.ON, "κλείσε" to HaOp.OFF, "χαμήλωσε" to HaOp.LEVEL),
        sensitive = listOf("πόρτα", "πόρτας", "γκαράζ", "κλειδαριά", "συναγερμ", "καγκελόπορτα", "αυλόπορτα", "ξεκλείδωσε"),
        opening = listOf("άνοιξε", "ξεκλείδωσε"),
    )

    private val FI = Pack(
        langs = setOf("fi"),
        months = listOf(listOf("tammikuuta", "tammikuu"), listOf("helmikuuta", "helmikuu"), listOf("maaliskuuta", "maaliskuu"), listOf("huhtikuuta", "huhtikuu"), listOf("toukokuuta", "toukokuu"), listOf("kesäkuuta", "kesäkuu"), listOf("heinäkuuta", "heinäkuu"), listOf("elokuuta", "elokuu"), listOf("syyskuuta", "syyskuu"), listOf("lokakuuta", "lokakuu"), listOf("marraskuuta", "marraskuu"), listOf("joulukuuta", "joulukuu")),
        cases = listOf("lle", "lla", "lta", "sta", "ssa", "ksi", "n", "a", "ä", "ta", "tä"),
        harmless = listOf("verho", "kaihdin", "kaihtim", "rullaverho", "markiisi", "sälekaihtim"),
        words = listOf(
            "tänä iltana" to "stasera", "tänä aamuna" to "stamattina", "tänään iltapäivällä" to "oggi pomeriggio",
            "huomenna aamulla" to "domani mattina", "huomenna iltapäivällä" to "domani pomeriggio", "huomenna illalla" to "domani sera",
            "ylihuomenna" to "dopodomani", "tänään" to "oggi", "huomenna" to "domani",
            "aamulla" to "di mattina", "iltapäivällä" to "del pomeriggio", "illalla" to "di sera", "yöllä" to "di notte",
        ),
        at = listOf("kello", "klo"), inAfter = listOf("päästä", "kuluttua"),
        units = mapOf(
            "minuti" to listOf("minuuttia", "minuutti", "minuutin", "minuutiksi"), "ore" to listOf("tuntia", "tunti", "tunnin", "tunniksi"),
            "secondi" to listOf("sekuntia", "sekunti", "sekunnin"), "giorni" to listOf("päivää", "päivä", "päivän"),
            "settimane" to listOf("viikkoa", "viikko", "viikon"),
        ),
        days = listOf(listOf("maanantaina", "maanantai"), listOf("tiistaina", "tiistai"), listOf("keskiviikkona", "keskiviikko"),
            listOf("torstaina", "torstai"), listOf("perjantaina", "perjantai"), listOf("lauantaina", "lauantai"), listOf("sunnuntaina", "sunnuntai")),
        next = listOf("ensi"),
        cmds = listOf(
            "muistuta minua että/muistuta minua/muistuta" to "ricordami",
            "herätä minut" to "svegliami",
            "aseta herätys/laita herätys/herätys" to "sveglia",
            "aseta ajastin/laita ajastin/ajastin" to "timer",
            "soita henkilölle/soita" to "chiama",
            "lähetä viesti/lähetä tekstiviesti/lähetä sms" to "manda un messaggio a",
            "lähetä whatsapp" to "manda un whatsapp a",
            "kirjoita" to "scrivi a",
            "sano" to "di a",
            "vastaa" to "rispondi a",
            "navigoi/reitti/vie minut" to "portami a",
            "lisää tapahtuma/lisää kalenteriin/uusi tapahtuma/lisää muistutus" to "aggiungi",
        ),
        homeFirst = mapOf("sytytä" to HaOp.ON, "sammuta" to HaOp.OFF, "avaa" to HaOp.ON, "sulje" to HaOp.OFF, "himmennä" to HaOp.LEVEL),
        sensitive = listOf("ovi", "oven", "portti", "portin", "autotalli", "lukko", "lukon", "hälytin", "hälytys"),
        opening = listOf("avaa"),
    )

    private val RO = Pack(
        langs = setOf("ro"),
        months = listOf(listOf("ianuarie"), listOf("februarie"), listOf("martie"), listOf("aprilie"), listOf("mai"), listOf("iunie"), listOf("iulie"), listOf("august"), listOf("septembrie"), listOf("octombrie"), listOf("noiembrie"), listOf("decembrie")),
        harmless = listOf("jaluzel", "rulou", "perdea", "perdele", "draperi", "storur"),
        words = listOf(
            "în seara asta" to "stasera", "diseară" to "stasera", "azi dimineață" to "stamattina", "azi după-amiază" to "oggi pomeriggio",
            "mâine dimineață" to "domani mattina", "mâine după-amiază" to "domani pomeriggio", "mâine seară" to "domani sera",
            "poimâine" to "dopodomani", "astăzi" to "oggi", "azi" to "oggi", "mâine" to "domani",
            "dimineața" to "di mattina", "după-amiaza" to "del pomeriggio", "seara" to "di sera", "noaptea" to "di notte",
        ),
        at = listOf("la ora", "la orele"), atWeak = listOf("la"), inBefore = listOf("în", "peste"),
        units = mapOf(
            "minuti" to listOf("minute", "minut"), "ore" to listOf("ore", "oră"), "secondi" to listOf("secunde", "secundă"),
            "giorni" to listOf("zile", "zi"), "settimane" to listOf("săptămâni", "săptămână"),
        ),
        days = listOf(listOf("luni"), listOf("marți"), listOf("miercuri"), listOf("joi"), listOf("vineri"), listOf("sâmbătă"), listOf("duminică")),
        nextAfter = listOf("viitoare", "viitor"),
        cmds = listOf(
            "amintește-mi să/amintește-mi că/amintește-mi/adu-mi aminte" to "ricordami",
            "trezește-mă" to "svegliami",
            "setează o alarmă/pune o alarmă/alarmă" to "sveglia",
            "setează un temporizator de/pune un cronometru de/setează un timer de/temporizator de/cronometru de" to "timer",
            "sună-l pe/sună-o pe/sună pe/sună" to "chiama",
            "trimite un mesaj lui/trimite un mesaj către/trimite mesaj lui/trimite un sms lui/trimite un mesaj" to "manda un messaggio a",
            "trimite un whatsapp lui/trimite whatsapp lui" to "manda un whatsapp a",
            "scrie-i lui/scrie lui/scrie-i" to "scrivi a",
            "spune-i lui/spune-i" to "di a",
            "răspunde-i/răspunde" to "rispondi a",
            "navighează la/navighează spre/du-mă la/drumul spre" to "portami a",
            "adaugă un eveniment/adaugă eveniment/adaugă o întâlnire/eveniment nou" to "aggiungi",
        ),
        homeFirst = mapOf("aprinde" to HaOp.ON, "stinge" to HaOp.OFF, "deschide" to HaOp.ON, "închide" to HaOp.OFF, "pornește" to HaOp.ON, "oprește" to HaOp.OFF),
        sensitive = listOf("ușa", "ușă", "poarta", "poartă", "garaj", "garajul", "încuietoare", "yala", "alarma", "alarmă", "descuie"),
        opening = listOf("deschide", "descuie", "dezarmează"),
    )

    private val UK = Pack(
        langs = setOf("uk"),
        months = listOf(listOf("січня"), listOf("лютого"), listOf("березня"), listOf("квітня"), listOf("травня"), listOf("червня"), listOf("липня"), listOf("серпня"), listOf("вересня"), listOf("жовтня"), listOf("листопада"), listOf("грудня")),
        cases = listOf("ові", "еві", "ому", "ему", "ого", "ою", "ею", "ой", "ом", "ем", "у", "ю", "е", "і", "а", "я", "и"),
        harmless = listOf("штор", "жалюзі", "ролет", "завіс", "маркіз"),
        words = listOf(
            "сьогодні ввечері" to "stasera", "сьогодні вранці" to "stamattina", "сьогодні вдень" to "oggi pomeriggio", "сьогодні вночі" to "stanotte",
            "завтра вранці" to "domani mattina", "завтра вдень" to "domani pomeriggio", "завтра ввечері" to "domani sera",
            "післязавтра" to "dopodomani", "сьогодні" to "oggi", "завтра" to "domani",
            "вранці" to "di mattina", "вдень" to "del pomeriggio", "ввечері" to "di sera", "вночі" to "di notte",
        ),
        at = listOf("о", "об"), atWeak = listOf("на"), hour = listOf("годині"), inBefore = listOf("через"),
        units = mapOf(
            "minuti" to listOf("хвилин", "хвилини", "хвилину"), "ore" to listOf("годин", "години", "годину"),
            "secondi" to listOf("секунд", "секунди", "секунду"), "giorni" to listOf("днів", "дні", "день", "доби"),
            "settimane" to listOf("тижнів", "тижні", "тиждень"),
        ),
        days = listOf(listOf("понеділок"), listOf("вівторок"), listOf("середу", "середа"), listOf("четвер"),
            listOf("п'ятницю", "п'ятниця", "пʼятницю", "пʼятниця"), listOf("суботу", "субота"), listOf("неділю", "неділя")),
        dayPre = listOf("у", "в", "во"), next = listOf("наступного", "наступну", "наступної", "наступний"),
        cmds = listOf(
            "нагадай мені що/нагадай мені/нагадай" to "ricordami",
            "розбуди мене" to "svegliami",
            "постав будильник/встанови будильник/будильник" to "sveglia",
            "постав таймер на/встанови таймер на/таймер на" to "timer",
            "зателефонуй/подзвони/набери" to "chiama",
            "надішли повідомлення/надішли смс/напиши повідомлення" to "manda un messaggio a",
            "надішли whatsapp/напиши у whatsapp/напиши у вайбер" to "manda un whatsapp a",
            "напиши" to "scrivi a",
            "скажи" to "di a",
            "відповідай/дай відповідь" to "rispondi a",
            "прокладай маршрут до/маршрут до/відвези мене до/відвези мене в/як дістатися до/навігація до" to "portami a",
            "додай подію/додай зустріч/створи подію/нова подія" to "aggiungi",
        ),
        homeFirst = mapOf("увімкни" to HaOp.ON, "вимкни" to HaOp.OFF, "відкрий" to HaOp.ON, "закрий" to HaOp.OFF, "приглуш" to HaOp.LEVEL),
        sensitive = listOf("двері", "ворота", "гараж", "замок", "сигналізац", "розблокуй"),
        opening = listOf("відкрий", "розблокуй"),
    )

    private val VI = Pack(
        langs = setOf("vi"),
        monthDay = "(?:ngày )?(?<d>\\d{1,2})(?:/| tháng )(?<m>\\d{1,2})",
        harmless = listOf("rèm", "mành", "màn cửa"),
        words = listOf(
            "tối nay" to "stasera", "sáng nay" to "stamattina", "chiều nay" to "oggi pomeriggio", "đêm nay" to "stanotte",
            "sáng mai" to "domani mattina", "chiều mai" to "domani pomeriggio", "tối mai" to "domani sera",
            "ngày kia" to "dopodomani", "ngày mốt" to "dopodomani", "hôm nay" to "oggi", "ngày mai" to "domani",
            "buổi sáng" to "di mattina", "buổi chiều" to "del pomeriggio", "buổi tối" to "di sera",
        ),
        at = listOf("lúc", "vào lúc"), hour = listOf("giờ"), minute = listOf("phút"), half = listOf("rưỡi"),
        inBefore = listOf("sau", "trong"), inAfter = listOf("nữa"),
        units = mapOf(
            "minuti" to listOf("phút"), "ore" to listOf("giờ", "tiếng"), "secondi" to listOf("giây"),
            "giorni" to listOf("ngày"), "settimane" to listOf("tuần"),
        ),
        days = listOf(listOf("thứ hai"), listOf("thứ ba"), listOf("thứ tư"), listOf("thứ năm"), listOf("thứ sáu"), listOf("thứ bảy"), listOf("chủ nhật")),
        dayPre = listOf("vào"), nextAfter = listOf("tuần sau", "tới"),
        cmds = listOf(
            "nhắc nhở tôi/nhắc tôi/nhắc mình" to "ricordami",
            "đánh thức tôi/gọi tôi dậy" to "svegliami",
            "hẹn giờ báo thức/đặt báo thức/báo thức" to "sveglia",
            "đặt hẹn giờ/hẹn giờ/bấm giờ" to "timer",
            "gọi điện thoại cho/gọi điện cho/gọi cho/gọi" to "chiama",
            "gửi tin nhắn cho/gửi tin nhắn tới/nhắn tin cho/nhắn cho" to "manda un messaggio a",
            "gửi whatsapp cho/nhắn whatsapp cho" to "manda un whatsapp a",
            "viết cho" to "scrivi a",
            "nói với" to "di a",
            "trả lời" to "rispondi a",
            "chỉ đường đến/chỉ đường tới/dẫn đường đến/dẫn đường tới/đưa tôi đến/đưa tôi tới" to "portami a",
            "thêm sự kiện/thêm lịch hẹn/tạo sự kiện/thêm cuộc hẹn" to "aggiungi",
        ),
        homeFirst = mapOf("bật" to HaOp.ON, "tắt" to HaOp.OFF, "mở" to HaOp.ON, "đóng" to HaOp.OFF, "giảm" to HaOp.LEVEL),
        sensitive = listOf("cửa", "cổng", "gara", "khóa", "khoá", "báo động"),
        opening = listOf("mở", "mở khóa"),
    )

    private val ID = Pack(
        langs = setOf("id"),
        months = listOf(listOf("januari"), listOf("februari"), listOf("maret"), listOf("april"), listOf("mei"), listOf("juni"), listOf("juli"), listOf("agustus"), listOf("september"), listOf("oktober"), listOf("november"), listOf("desember")),
        harmless = listOf("tirai", "gorden", "gordin", "kerai"),
        words = listOf(
            "malam ini" to "stasera", "nanti malam" to "stasera", "pagi ini" to "stamattina", "sore ini" to "oggi pomeriggio", "siang ini" to "oggi pomeriggio",
            "besok pagi" to "domani mattina", "besok siang" to "domani pomeriggio", "besok sore" to "domani pomeriggio", "besok malam" to "domani sera",
            "lusa" to "dopodomani", "hari ini" to "oggi", "besok" to "domani",
            "pagi" to "di mattina", "siang" to "del pomeriggio", "sore" to "del pomeriggio", "malam" to "di sera",
        ),
        at = listOf("jam", "pukul", "pada jam", "pada pukul"), inBefore = listOf("dalam"), inAfter = listOf("lagi"),
        units = mapOf(
            "minuti" to listOf("menit"), "ore" to listOf("jam"), "secondi" to listOf("detik"), "giorni" to listOf("hari"), "settimane" to listOf("minggu", "pekan"),
        ),
        days = listOf(listOf("senin"), listOf("selasa"), listOf("rabu"), listOf("kamis"), listOf("jumat"), listOf("sabtu"), listOf("hari minggu", "ahad")),
        dayPre = listOf("hari", "pada hari"), nextAfter = listOf("depan"),
        cmds = listOf(
            "ingatkan saya untuk/ingatkan saya/ingatkan aku" to "ricordami",
            "bangunkan saya/bangunkan aku" to "svegliami",
            "pasang alarm/setel alarm/atur alarm/alarm" to "sveglia",
            "pasang timer/setel timer/atur timer" to "timer",
            "telepon/telpon/hubungi/panggil" to "chiama",
            "kirim pesan ke/kirim pesan untuk/kirim sms ke" to "manda un messaggio a",
            "kirim whatsapp ke/kirim wa ke" to "manda un whatsapp a",
            "tulis pesan ke/tulis ke" to "scrivi a",
            "bilang ke/katakan ke/bilang sama" to "di a",
            "balas" to "rispondi a",
            "navigasi ke/arahkan ke/antar saya ke/rute ke/petunjuk arah ke" to "portami a",
            "tambahkan acara/tambah acara/buat acara/tambah jadwal/buat janji" to "aggiungi",
        ),
        leads = listOf("tolong"),
        homeFirst = mapOf("nyalakan" to HaOp.ON, "hidupkan" to HaOp.ON, "matikan" to HaOp.OFF, "buka" to HaOp.ON, "tutup" to HaOp.OFF, "redupkan" to HaOp.LEVEL),
        sensitive = listOf("pintu", "gerbang", "garasi", "kunci", "alarm"),
        opening = listOf("buka", "buka kunci"),
    )

    private val AR = Pack(
        langs = setOf("ar"),
        months = listOf(listOf("يناير", "كانون الثاني"), listOf("فبراير", "شباط"), listOf("مارس", "آذار"), listOf("أبريل", "ابريل", "نيسان"), listOf("مايو", "أيار"), listOf("يونيو", "حزيران"), listOf("يوليو", "تموز"), listOf("أغسطس", "اغسطس", "آب"), listOf("سبتمبر", "أيلول"), listOf("أكتوبر", "اكتوبر", "تشرين الأول"), listOf("نوفمبر", "تشرين الثاني"), listOf("ديسمبر", "كانون الأول")),
        harmless = listOf("ستارة", "الستارة", "ستائر", "الستائر", "شيش", "الشيش", "الشمسية"),
        words = listOf(
            "بعد ساعة" to "tra un ore", "بعد دقيقة" to "tra un minuti", "دقيقتين" to "2 minuti", "ساعتين" to "2 ore", "يومين" to "2 giorni",
            "الليلة" to "stasera", "هذا المساء" to "stasera", "هذا الصباح" to "stamattina", "صباح اليوم" to "stamattina", "بعد ظهر اليوم" to "oggi pomeriggio",
            "غدا صباحا" to "domani mattina", "صباح الغد" to "domani mattina", "غدا مساء" to "domani sera", "مساء الغد" to "domani sera",
            "بعد غد" to "dopodomani", "بعد بكرة" to "dopodomani", "اليوم" to "oggi", "غدا" to "domani", "بكرة" to "domani", "بكره" to "domani",
            "في الصباح" to "di mattina", "صباحا" to "di mattina", "بعد الظهر" to "del pomeriggio", "العصر" to "del pomeriggio",
            "في المساء" to "di sera", "مساء" to "di sera",
        ),
        at = listOf("الساعة", "على الساعة", "في الساعة"), inBefore = listOf("بعد", "خلال"),
        units = mapOf(
            "minuti" to listOf("دقائق", "دقيقة"), "ore" to listOf("ساعات", "ساعة"), "secondi" to listOf("ثواني", "ثوان", "ثانية"),
            "giorni" to listOf("أيام", "يوم"), "settimane" to listOf("أسابيع", "أسبوع"),
        ),
        days = listOf(listOf("الاثنين", "الإثنين"), listOf("الثلاثاء"), listOf("الأربعاء"), listOf("الخميس"), listOf("الجمعة"), listOf("السبت"), listOf("الأحد")),
        dayPre = listOf("يوم"), nextAfter = listOf("القادم", "المقبل"),
        cmds = listOf(
            "ذكرني أن/ذكرني بأن/ذكرني ب/ذكرني" to "ricordami",
            "أيقظني/صحيني" to "svegliami",
            "اضبط المنبه/اضبط منبه/ضبط منبه/منبه" to "sveglia",
            "اضبط مؤقت لمدة/اضبط مؤقت/ضبط مؤقت/مؤقت لمدة/مؤقت" to "timer",
            "اتصل على {x}/اتصل ب{x}/اتصل {x}/كلم {x}" to CALL,
            "أرسل رسالة إلى/ارسل رسالة الى/أرسل رسالة ل{x}/ابعث رسالة إلى/راسل" to "manda un messaggio a {x}",
            "أرسل واتساب إلى/ارسل واتساب الى/أرسل واتس إلى" to "manda un whatsapp a",
            "اكتب إلى/اكتب الى" to "scrivi a",
            "قل ل{x}" to "di a {x}",
            "رد على" to "rispondi a",
            "خذني إلى/خذني الى/وجهني إلى/الطريق إلى/الطريق الى" to "portami a",
            "أضف حدثا/اضف حدث/أضف موعدا/أضف موعد/موعد جديد/حدث جديد" to "aggiungi",
        ),
        homeFirst = mapOf("شغل" to HaOp.ON, "أشعل" to HaOp.ON, "اشعل" to HaOp.ON, "أطفئ" to HaOp.OFF, "اطفئ" to HaOp.OFF, "طفي" to HaOp.OFF, "افتح" to HaOp.ON, "أغلق" to HaOp.OFF, "اغلق" to HaOp.OFF, "سكر" to HaOp.OFF),
        sensitive = listOf("باب", "الباب", "البوابة", "بوابة", "الكراج", "كراج", "المرآب", "قفل", "القفل", "إنذار", "الإنذار", "الانذار"),
        opening = listOf("افتح", "فك القفل"),
    )

    private val HI = Pack(
        langs = setOf("hi"),
        months = listOf(listOf("जनवरी"), listOf("फ़रवरी", "फरवरी"), listOf("मार्च"), listOf("अप्रैल"), listOf("मई"), listOf("जून"), listOf("जुलाई"), listOf("अगस्त"), listOf("सितंबर", "सितम्बर"), listOf("अक्टूबर"), listOf("नवंबर", "नवम्बर"), listOf("दिसंबर", "दिसम्बर")),
        harmless = listOf("पर्दा", "पर्दे", "परदा", "परदे", "ब्लाइंड"),
        words = listOf(
            "आज रात" to "stasera", "आज शाम" to "stasera", "आज सुबह" to "stamattina", "आज दोपहर" to "oggi pomeriggio",
            "कल सुबह" to "domani mattina", "कल दोपहर" to "domani pomeriggio", "कल शाम" to "domani sera", "कल रात" to "domani sera",
            "परसों" to "dopodomani", "आज" to "oggi", "कल" to "domani",
            "सुबह" to "di mattina", "दोपहर" to "del pomeriggio", "शाम" to "di sera", "रात" to "di notte",
        ),
        hour = listOf("बजे"), inAfter = listOf("में", "बाद", "के बाद"),
        units = mapOf(
            "minuti" to listOf("मिनट"), "ore" to listOf("घंटे", "घंटा", "घंटों"), "secondi" to listOf("सेकंड", "सेकेंड"),
            "giorni" to listOf("दिन", "दिनों"), "settimane" to listOf("हफ्ते", "हफ़्ते", "सप्ताह"),
        ),
        days = listOf(listOf("सोमवार"), listOf("मंगलवार"), listOf("बुधवार"), listOf("गुरुवार"), listOf("शुक्रवार"), listOf("शनिवार"), listOf("रविवार")),
        dayPost = listOf("को"), next = listOf("अगले", "अगला"),
        cmds = listOf(
            "{x} को कॉल करो/{x} को फोन करो/{x} को कॉल करें/{x} को फ़ोन करो/{x} को कॉल लगाओ/{x} को फोन लगाओ/कॉल करो/फोन करो" to CALL,
            "{x} को मैसेज भेजो कि {y}/{x} को मैसेज भेजो/{x} को मैसेज करो/{x} को संदेश भेजो" to MSG,
            "{x} को व्हाट्सएप भेजो/{x} को व्हाट्सऐप करो/{x} को व्हाट्सएप करो" to WA,
            "{x} को लिखो {y}" to "scrivi a {x} {y}",
            "{x} से कहो कि {y}/{x} से कहो {y}/{x} को बोलो {y}" to "di a {x} {y}",
            "{x} को जवाब दो {y}" to "rispondi a {x} {y}",
            "मुझे याद दिलाओ कि {y}/मुझे याद दिलाना {y}/मुझे {y} याद दिलाओ/{y} याद दिलाना/{y} याद दिलाओ" to "ricordami {y}",
            "मुझे {y} जगाओ/मुझे {y} जगा देना/{y} मुझे जगाओ/{y} जगा देना" to "svegliami {y}",
            "{y} का अलार्म लगाओ/{y} का अलार्म सेट करो/{y} अलार्म लगाओ/अलार्म लगाओ {y}/अलार्म सेट करो {y}" to "sveglia {y}",
            "{y} का टाइमर लगाओ/{y} का टाइमर सेट करो/{y} टाइमर लगाओ/टाइमर लगाओ {y}" to "timer {y}",
            "{x} तक का रास्ता दिखाओ/{x} का रास्ता दिखाओ/{x} तक नेविगेट करो/{x} ले चलो" to "portami a {x}",
            "कैलेंडर में {y} जोड़ो/{y} इवेंट जोड़ो/{y} कैलेंडर में जोड़ो" to "aggiungi {y}",
        ),
        leads = listOf("कृपया"), tails = listOf("प्लीज़", "प्लीज"),
        home = listOf("{x} चालू करो" to HaOp.ON, "{x} चालू कर दो" to HaOp.ON, "{x} जलाओ" to HaOp.ON, "{x} ऑन करो" to HaOp.ON,
            "{x} बंद करो" to HaOp.OFF, "{x} बंद कर दो" to HaOp.OFF, "{x} बुझाओ" to HaOp.OFF, "{x} ऑफ करो" to HaOp.OFF, "{x} खोलो" to HaOp.ON),
        sensitive = listOf("दरवाजा", "दरवाज़ा", "दरवाजे", "गेट", "गेराज", "गैराज", "ताला", "अलार्म"),
        opening = listOf("खोलो", "अनलॉक"),
    )

    private val KO = Pack(
        langs = setOf("ko"), glued = true,
        monthDay = "(?<m>\\d{1,2})\\s?월\\s?(?<d>\\d{1,2})\\s?일",
        harmless = listOf("커튼", "블라인드", "셔터"),
        words = listOf(
            "오늘 밤" to "stasera", "오늘밤" to "stasera", "오늘 저녁" to "stasera", "오늘 아침" to "stamattina", "오늘 오후" to "oggi pomeriggio",
            "내일 아침" to "domani mattina", "내일 오후" to "domani pomeriggio", "내일 저녁" to "domani sera", "내일 밤" to "domani sera",
            "모레" to "dopodomani", "오늘" to "oggi", "내일" to "domani",
            "아침" to "di mattina", "오전" to "di mattina", "오후" to "del pomeriggio", "저녁" to "di sera", "밤" to "di notte",
        ),
        hour = listOf("시(?!간)"), minute = listOf("분"), half = listOf("반"), hourTail = listOf("에", "까지"),
        inAfter = listOf("후에", "후", "뒤에", "뒤"),
        units = mapOf("minuti" to listOf("분"), "ore" to listOf("시간"), "secondi" to listOf("초"), "giorni" to listOf("일"), "settimane" to listOf("주")),
        days = listOf(listOf("월요일"), listOf("화요일"), listOf("수요일"), listOf("목요일"), listOf("금요일"), listOf("토요일"), listOf("일요일")),
        next = listOf("다음 주", "다음주"), dayPost = listOf("에"),
        cmds = listOf(
            "{x}에게 {y}라고 문자 보내줘/{x}에게 {y}라고 메시지 보내줘/{x}한테 {y}라고 문자 보내줘/{x}한테 {y}라고 보내줘" to MSG,
            "{x}에게 문자 보내줘/{x}에게 문자 보내/{x}한테 문자 보내줘/{x}에게 메시지 보내줘/{x}한테 메시지 보내/{x}에게 카톡 보내줘/{x}한테 카톡 보내줘" to "manda un messaggio a {x}",
            "{x}에게 전화 걸어줘/{x}한테 전화 걸어줘/{x}에게 전화해줘/{x}한테 전화해줘/{x}에게 전화해/{x}한테 전화해/{x}께 전화해" to CALL,
            "{x}에게 답장해줘/{x}한테 답장해줘" to "rispondi a {x}",
            "{y} 리마인드 해줘/{y} 상기시켜 줘/{y} 잊지 않게 알려줘" to "ricordami {y}",
            "{y} 깨워줘/{y} 깨워 줘" to "svegliami {y}",
            "{y} 알람 맞춰줘/{y} 알람 설정해줘/{y} 알람 맞춰/{y} 알람" to "sveglia {y}",
            "{y} 타이머 맞춰줘/{y} 타이머 설정해줘/{y} 타이머" to "timer {y}",
            "{x}까지 길 안내해줘/{x}까지 안내해줘/{x}로 안내해줘/{x}으로 안내해줘/{x}까지 가는 길" to "portami a {x}",
            "{y} 일정 추가해줘/{y} 일정 추가" to "aggiungi {y}",
        ),
        tails = listOf("주세요", "줄래요", "줄래"),
        home = listOf("{x} 켜줘" to HaOp.ON, "{x} 켜" to HaOp.ON, "{x} 꺼줘" to HaOp.OFF, "{x} 꺼" to HaOp.OFF, "{x} 열어줘" to HaOp.ON, "{x} 열어" to HaOp.ON, "{x} 닫아줘" to HaOp.OFF, "{x} 닫아" to HaOp.OFF),
        sensitive = listOf("현관", "대문", "차고", "도어락", "경보", "잠금", "문을", "문"),
        opening = listOf("열어줘", "열어", "해제해줘", "해제"),
    )

    private val JA = Pack(
        langs = setOf("ja"), glued = true,
        monthDay = "(?<m>\\d{1,2})\\s?月\\s?(?<d>\\d{1,2})\\s?日",
        harmless = listOf("カーテン", "ブラインド", "シャッター"),
        words = listOf(
            "今夜" to "stasera", "今晩" to "stasera", "今朝" to "stamattina", "今日の午後" to "oggi pomeriggio",
            "明日の朝" to "domani mattina", "明日の午後" to "domani pomeriggio", "明日の夜" to "domani sera", "明日の晩" to "domani sera",
            "明後日" to "dopodomani", "あさって" to "dopodomani", "今日" to "oggi", "きょう" to "oggi", "明日" to "domani", "あした" to "domani", "あす" to "domani",
            "午前" to "di mattina", "朝" to "di mattina", "午後" to "del pomeriggio", "夜" to "di sera", "晩" to "di sera",
        ),
        hour = listOf("時(?!間)"), minute = listOf("分(?!間|後)"), half = listOf("半"), hourTail = listOf("に", "まで"),
        inAfter = listOf("後に", "後", "経ったら"),
        units = mapOf("minuti" to listOf("分間", "分"), "ore" to listOf("時間"), "secondi" to listOf("秒間", "秒"), "giorni" to listOf("日間", "日(?=後)"), "settimane" to listOf("週間")),
        days = listOf(listOf("月曜日", "月曜"), listOf("火曜日", "火曜"), listOf("水曜日", "水曜"), listOf("木曜日", "木曜"), listOf("金曜日", "金曜"), listOf("土曜日", "土曜"), listOf("日曜日", "日曜")),
        next = listOf("来週の", "来週", "次の"), dayPost = listOf("に"),
        numerals = listOf("十二" to "12", "十一" to "11", "十" to "10", "九" to "9", "八" to "8", "七" to "7", "六" to "6", "五" to "5", "四" to "4", "三" to "3", "二" to "2", "一" to "1"),
        cmds = listOf(
            "{x}に{y}とメッセージを送って/{x}に{y}って送って/{x}に{y}と送って" to MSG,
            "{x}にメッセージを送って/{x}にメッセージ送って/{x}にメールして/{x}にLINEして/{x}にラインして/{x}にSMSを送って" to "manda un messaggio a {x}",
            "{x}に電話をかけて/{x}に電話して/{x}に電話する/{x}へ電話して/{x}に発信/{x}に電話" to CALL,
            "{x}に返信して" to "rispondi a {x}",
            "{y}をリマインドして/{y}とリマインドして/{y}を思い出させて" to "ricordami {y}",
            "{y}に起こして/{y}起こして" to "svegliami {y}",
            "{y}にアラームをセットして/{y}にアラームをかけて/{y}に目覚ましをかけて/{y}にアラーム/{y}のアラーム" to "sveglia {y}",
            "{y}のタイマーをセットして/{y}のタイマー/{y}タイマー/タイマー{y}" to "timer {y}",
            "{x}まで案内して/{x}までナビして/{x}へ案内して/{x}への道案内/{x}までの道" to "portami a {x}",
            "{y}の予定を追加して/{y}を予定に追加して/{y}を予定に追加" to "aggiungi {y}",
        ),
        tails = listOf("ください", "下さい", "ちょうだい", "お願い", "おねがい", "くれる", "くれ"),
        nameTail = Regex("(さん|ちゃん|くん|様)$"),
        home = listOf("{x}をつけて" to HaOp.ON, "{x}つけて" to HaOp.ON, "{x}を消して" to HaOp.OFF, "{x}消して" to HaOp.OFF,
            "{x}を開けて" to HaOp.ON, "{x}開けて" to HaOp.ON, "{x}を閉めて" to HaOp.OFF, "{x}閉めて" to HaOp.OFF),
        sensitive = listOf("ドア", "玄関", "門", "ガレージ", "車庫", "鍵", "ロック", "警報", "セキュリティ"),
        opening = listOf("開けて", "解錠", "解除"),
    )

    private val ZH = Pack(
        langs = setOf("zh"), glued = true,
        monthDay = "(?<m>\\d{1,2})\\s?月\\s?(?<d>\\d{1,2})\\s?[日号號]",
        harmless = listOf("窗帘", "窗簾", "百叶窗", "百葉窗", "卷帘", "捲簾"),
        words = listOf(
            "今天晚上" to "stasera", "今晚" to "stasera", "今天早上" to "stamattina", "今早" to "stamattina", "今天下午" to "oggi pomeriggio",
            "明天早上" to "domani mattina", "明早" to "domani mattina", "明天下午" to "domani pomeriggio", "明天晚上" to "domani sera", "明晚" to "domani sera",
            "后天" to "dopodomani", "後天" to "dopodomani", "今天" to "oggi", "明天" to "domani",
            "早上" to "di mattina", "上午" to "di mattina", "下午" to "del pomeriggio", "晚上" to "di sera",
        ),
        hour = listOf("点钟", "點鐘", "点", "點"), minute = listOf("分"), half = listOf("半"),
        inAfter = listOf("以后", "以後", "之后", "之後", "后", "後"),
        units = mapOf(
            "minuti" to listOf("分钟", "分鐘"), "ore" to listOf("个小时", "個小時", "小时", "小時"), "secondi" to listOf("秒钟", "秒鐘", "秒"),
            "giorni" to listOf("天"), "settimane" to listOf("个星期", "個星期", "星期(?![一二三四五六日天])", "周(?![一二三四五六日])", "週(?![一二三四五六日])"),
        ),
        days = listOf(
            listOf("星期一", "周一", "週一", "礼拜一", "禮拜一"), listOf("星期二", "周二", "週二", "礼拜二", "禮拜二"),
            listOf("星期三", "周三", "週三", "礼拜三", "禮拜三"), listOf("星期四", "周四", "週四", "礼拜四", "禮拜四"),
            listOf("星期五", "周五", "週五", "礼拜五", "禮拜五"), listOf("星期六", "周六", "週六", "礼拜六", "禮拜六"),
            listOf("星期日", "星期天", "周日", "週日", "礼拜天", "禮拜天"),
        ),
        next = listOf("下个", "下個", "下"),
        numerals = listOf("十二" to "12", "十一" to "11", "两" to "2", "兩" to "2", "十" to "10", "九" to "9", "八" to "8", "七" to "7", "六" to "6", "五" to "5", "四" to "4", "三" to "3", "二" to "2", "一" to "1"),
        cmds = listOf(
            "给{x}发消息说{y}/给{x}发短信说{y}/给{x}发微信说{y}/給{x}發訊息說{y}/給{x}傳訊息說{y}" to MSG,
            "给{x}发消息/给{x}发短信/给{x}发信息/发消息给{x}/发短信给{x}/給{x}發訊息/給{x}傳訊息/給{x}發簡訊/傳簡訊給{x}/發訊息給{x}" to "manda un messaggio a {x}",
            "给{x}打个电话/给{x}打电话/打电话给{x}/打给{x}/呼叫{x}/拨打{x}/給{x}打個電話/給{x}打電話/打電話給{x}/打給{x}/撥打{x}" to CALL,
            "回复{x}/回覆{x}" to "rispondi a {x}",
            "提醒我一下{y}/提醒我{y}" to "ricordami {y}",
            "{y}叫我起床/{y}叫醒我" to "svegliami {y}",
            "设置{y}的闹钟/设一个{y}的闹钟/定一个{y}的闹钟/设{y}的闹钟/設定{y}的鬧鐘/{y}的闹钟/{y}的鬧鐘/设闹钟{y}/闹钟{y}/鬧鐘{y}" to "sveglia {y}",
            "设置{y}的计时器/设{y}的计时器/設定{y}的計時器/{y}的计时器/{y}的計時器/{y}的定时器/{y}倒计时/{y}倒計時/倒计时{y}/计时{y}/計時{y}" to "timer {y}",
            "导航到{x}/导航去{x}/带我去{x}/去{x}怎么走/導航到{x}/帶我去{x}/去{x}怎麼走" to "portami a {x}",
            "添加日程{y}/新建日程{y}/新增行程{y}/添加{y}到日历/新增{y}到行事曆" to "aggiungi {y}",
        ),
        tails = listOf("一下", "吧", "好吗", "好嗎"), leads = listOf("请", "請", "帮我", "幫我"),
        home = listOf("打开{x}" to HaOp.ON, "打開{x}" to HaOp.ON, "开{x}" to HaOp.ON, "開{x}" to HaOp.ON,
            "关闭{x}" to HaOp.OFF, "關閉{x}" to HaOp.OFF, "关掉{x}" to HaOp.OFF, "關掉{x}" to HaOp.OFF, "关{x}" to HaOp.OFF, "關{x}" to HaOp.OFF),
        sensitive = listOf("门", "門", "车库", "車庫", "锁", "鎖", "警报", "警報", "报警", "安防"),
        opening = listOf("打开", "打開", "开门", "開門", "解锁", "解鎖", "撤防"),
    )

    private val TH = Pack(
        langs = setOf("th"), glued = true,
        months = listOf(listOf("มกราคม"), listOf("กุมภาพันธ์"), listOf("มีนาคม"), listOf("เมษายน"), listOf("พฤษภาคม"), listOf("มิถุนายน"), listOf("กรกฎาคม"), listOf("สิงหาคม"), listOf("กันยายน"), listOf("ตุลาคม"), listOf("พฤศจิกายน"), listOf("ธันวาคม")),
        harmless = listOf("ม่าน", "มู่ลี่"),
        extra = listOf(
            "เที่ยงคืน" to { _ -> " alle 0 " },
            "บ่ายโมง" to { _ -> " alle 13 " },
            "บ่าย\\s?(\\d{1,2})\\s?โมง(?:เย็น)?" to { m -> " alle ${m.groupValues[1].toInt().let { if (it < 12) it + 12 else it }} " },
            "(\\d{1,2})\\s?โมงเย็น" to { m -> " alle ${m.groupValues[1].toInt().let { if (it < 12) it + 12 else it }} " },
            "(\\d{1,2})\\s?โมงเช้า" to { m -> " alle ${m.groupValues[1]} " },
            "(?<![\\u0E00-\\u0E7F])ตี\\s?(\\d{1,2})" to { m -> " alle ${m.groupValues[1]} " },
            "(\\d{1,2})\\s?ทุ่ม" to { m -> " alle ${18 + m.groupValues[1].toInt()} " },
            "เที่ยง(?:วัน)?" to { _ -> " alle 12 " },
        ),
        words = listOf(
            "คืนนี้" to "stasera", "เย็นนี้" to "stasera", "เช้านี้" to "stamattina", "บ่ายนี้" to "oggi pomeriggio",
            "พรุ่งนี้เช้า" to "domani mattina", "พรุ่งนี้บ่าย" to "domani pomeriggio", "พรุ่งนี้เย็น" to "domani sera", "พรุ่งนี้ค่ำ" to "domani sera",
            "มะรืนนี้" to "dopodomani", "มะรืน" to "dopodomani", "วันนี้" to "oggi", "พรุ่งนี้" to "domani",
            "ตอนเช้า" to "di mattina", "ตอนบ่าย" to "del pomeriggio", "ตอนเย็น" to "di sera", "ตอนค่ำ" to "di sera",
        ),
        at = listOf("ตอน", "เวลา"), hour = listOf("นาฬิกา", "โมง"), minute = listOf("นาที"), half = listOf("ครึ่ง"),
        inBefore = listOf("อีก"),
        units = mapOf("minuti" to listOf("นาที"), "ore" to listOf("ชั่วโมง"), "secondi" to listOf("วินาที"), "giorni" to listOf("วัน(?!จันทร์|อังคาร|พุธ|พฤหัส|ศุกร์|เสาร์|อาทิตย์|นี้)"), "settimane" to listOf("สัปดาห์")),
        days = listOf(listOf("วันจันทร์", "จันทร์"), listOf("วันอังคาร", "อังคาร"), listOf("วันพุธ", "พุธ"), listOf("วันพฤหัสบดี", "วันพฤหัส", "พฤหัส"),
            listOf("วันศุกร์", "ศุกร์"), listOf("วันเสาร์", "เสาร์"), listOf("วันอาทิตย์", "อาทิตย์")),
        nextAfter = listOf("หน้า"),
        cmds = listOf(
            "ส่งข้อความหา{x}ว่า{y}/ส่งข้อความถึง{x}ว่า{y}" to MSG,
            "ส่งข้อความไปหา{x}/ส่งข้อความหา{x}/ส่งข้อความถึง{x}/ส่งไลน์หา{x}/ส่งsmsหา{x}" to "manda un messaggio a {x}",
            "โทรไปหา{x}/โทรหา{x}/โทรถึง{x}/โทร{x}" to CALL,
            "ตอบกลับ{x}" to "rispondi a {x}",
            "เตือนฉัน{y}/เตือนผม{y}/เตือนให้{y}/ช่วยเตือน{y}" to "ricordami {y}",
            "ตั้งนาฬิกาปลุก{y}/ตั้งเวลาปลุก{y}/ตั้งปลุก{y}/นาฬิกาปลุก{y}" to "sveglia {y}",
            "ปลุกฉัน{y}/ปลุกผม{y}/ปลุก{y}" to "svegliami {y}",
            "ตั้งตัวจับเวลา{y}/ตั้งเวลา{y}/จับเวลา{y}/นับถอยหลัง{y}" to "timer {y}",
            "นำทางไปที่{x}/นำทางไป{x}/เส้นทางไป{x}/พาไป{x}" to "portami a {x}",
            "เพิ่มตารางนัด{y}/เพิ่มนัด{y}/เพิ่มกิจกรรม{y}/สร้างนัด{y}" to "aggiungi {y}",
        ),
        tails = listOf("ให้หน่อย", "หน่อย", "ด้วย", "ครับ", "ค่ะ", "คะ", "นะ"), leads = listOf("ช่วย"),
        home = listOf("เปิด{x}" to HaOp.ON, "ปิด{x}" to HaOp.OFF),
        sensitive = listOf("ประตู", "โรงรถ", "กุญแจ", "ล็อค", "ล็อก", "กันขโมย", "รั้ว"),
        opening = listOf("เปิด", "ปลดล็อค", "ปลดล็อก"),
    )
}
