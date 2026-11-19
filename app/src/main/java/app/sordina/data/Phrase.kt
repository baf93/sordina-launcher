package app.sordina.data

/**
 * Le tante maniere di dire la stessa cosa, riportate alla frase che il motore capisce già.
 * "Puoi chiamare Elisa?" → "chiama Elisa"; "dì a Elisa che arrivo" → "scrivi a Elisa che arrivo";
 * "rispondi che arrivo" → "scrivi a <chi ti ha scritto> che arrivo"; "chiam elisa" → "chiama elisa".
 * Le parole originali restano (maiuscole comprese): il testo di un messaggio non si tocca.
 */
object Phrase {
    /** Quello che serve sapere del telefono, senza dipendere da Android (per i test). */
    interface Hints {
        /** Un nome di persona o di app: non è un verbo sbagliato ("Chiara" non è "chiama"). */
        fun isName(word: String): Boolean = false
        /** L'ultima persona che ti ha scritto e aspetta risposta. */
        fun lastWriter(): String? = null
        /** L'ultima chiamata persa. */
        fun lastCaller(): String? = null
        /** La persona di cui stavi parlando (l'ultima chiamata o scritta da Sordina, da poco). */
        fun lastPerson(): String? = null
    }

    object NoHints : Hints

    private class W(val o: String) { val n = Search.normalize(o) }

    /** Parole di cortesia e di attacco che non cambiano cosa vuoi. */
    private val LEAD = listOf(
        "ehi sordina", "hey sordina", "ok sordina", "ehi", "hey", "ok", "allora", "senti",
        "per favore", "per piacere", "puoi", "potresti", "riesci a", "mi puoi", "mi potresti", "vorrei", "voglio", "devo",
        "ho bisogno di", "fammi", "mi fai", "fai",
        "please", "can you", "could you", "would you", "will you", "i want to", "i need to", "i d like to", "i would like to", "let s",
        // Spagnolo, francese, tedesco, portoghese
        "oye", "por favor", "me puedes", "puedes", "podrias", "quiero", "necesito", "quisiera",
        "s il te plait", "s il vous plait", "peux tu", "pourrais tu", "tu peux", "je veux", "je voudrais", "j aimerais",
        "bitte", "kannst du", "konntest du", "ich mochte", "ich will",
        "voce pode", "podes", "pode", "consegues", "quero", "preciso", "gostaria de",
    )
    private val TAIL = listOf("per favore", "per piacere", "grazie", "please", "thanks", "thank you",
        "por favor", "gracias", "s il te plait", "s il vous plait", "merci", "bitte", "danke", "obrigado", "obrigada")

    private val CALL = setOf("chiama", "chiamare", "chiami", "telefona", "telefonare", "telefoni", "chiamata", "telefonata", "videochiama", "squilla")
    private val SAY = setOf("di", "dire", "dica", "dici", "rispondi", "rispondere", "scrivi", "scrivere", "scrivigli", "scrivile")
    private val SEND = setOf("manda", "mandare", "invia", "inviare", "mandagli", "mandale")
    private val VIAS = setOf("whatsapp", "telegram", "signal", "sms")
    private val CALL_HER = setOf("chiamala", "chiamalo", "telefonale", "telefonagli", "richiamala", "richiamalo", "chiamali")
    private val WRITE_HER = setOf("scrivile", "scrivigli", "dille", "digli", "mandale", "mandagli", "rispondile", "rispondigli", "scrivili")
    private val PLAY = setOf("ascolta", "ascoltare", "sentire", "riproduci", "suona")

    private val FORMS = mapOf(
        "aprire" to "apri", "apra" to "apri", "accendere" to "accendi", "accenda" to "accendi",
        "spegnere" to "spegni", "spenga" to "spegni", "mettere" to "metti", "metta" to "metti",
        "impostare" to "imposta", "chiudere" to "chiudi", "chiuda" to "chiudi", "abbassare" to "abbassa", "alzare" to "alza",
        "attivare" to "attiva", "disattivare" to "disattiva", "ricordarmi" to "ricordami", "svegliarmi" to "svegliami",
        "navigare" to "naviga", "portarmi" to "portami",
    )

    /** I comandi in inglese: non sono verbi italiani sbagliati ("call" non è "chiama" scritto male) */
    val ENGLISH = setOf("call", "phone", "ring", "text", "message", "write", "send", "tell", "reply", "answer", "remind", "wake",
        "alarm", "set", "add", "create", "schedule", "new", "turn", "switch", "open", "close", "play", "listen", "navigate", "take",
        "directions", "drive", "start", "pause", "stop", "resume", "next", "skip", "torch", "flashlight", "mute", "silence", "quiet")

    /** I verbi che si possono sbagliare a scrivere, per la prima parola. */
    private val VERBS = listOf(
        "chiama", "telefona", "scrivi", "manda", "invia", "messaggio", "rispondi", "richiama", "sveglia", "svegliami",
        "timer", "ricordami", "promemoria", "accendi", "spegni", "naviga", "portami", "ascolta", "riproduci", "torcia",
        "silenzio", "sordina", "appuntamento", "calendario",
    )

    fun canonical(text: String, hints: Hints = NoHints): String {
        var w = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map(::W).filter { it.n.isNotEmpty() }
        if (w.isEmpty()) return text.trim()

        fun starts(p: String): Int {
            val ps = p.split(' ')
            return if (w.size >= ps.size && w.take(ps.size).map { it.n } == ps) ps.size else 0
        }
        fun ow(from: Int = 0) = w.drop(from).joinToString(" ") { it.o }

        // Cortesia in fondo e in testa ("puoi chiamare Elisa per favore?"), più volte ("ehi, puoi…")
        for (t in TAIL) { val ts = t.split(' '); if (w.size > ts.size && w.takeLast(ts.size).map { it.n } == ts) w = w.dropLast(ts.size) }
        var again = true
        while (again && w.size > 1) {
            again = false
            for (l in LEAD) { val n = starts(l); if (n > 0 && w.size > n) { w = w.drop(n); again = true; break } }
        }
        // "mi" davanti al verbo: "mi chiami Elisa", "mi metti un timer"
        if (w.size > 2 && w[0].n == "mi") w = w.drop(1)
        // Spagnolo, francese, tedesco, portoghese: il comando in italiano, il resto com'è ("llama a Elisa" → "chiama Elisa")
        Lingue.command(w.map { it.o })?.let { t -> w = t.map(::W).filter { it.n.isNotEmpty() } }
        if (w.isEmpty()) return text.trim()

        // Il verbo scritto male, solo se non è un nome che conosci ("Chiara" resta Chiara)
        if (w.size >= 2 && w[0].n !in VERBS && w[0].n !in ENGLISH && w[0].n !in Lingue.VERBS && w[0].n !in LingueAltre.VERBS && !LingueAltre.on() && w[0].n !in CALL_HER && w[0].n !in WRITE_HER && w[0].n !in FORMS && w[0].n !in SAY && w[0].n !in SEND && !hints.isName(w[0].n)) {
            val fix = closestVerb(w[0].n)
            if (fix != null) w = listOf(W(fix)) + w.drop(1)
        }
        if (w.isEmpty()) return text.trim()
        // L'infinito e il lei diventano l'imperativo: "aprire WhatsApp", "accenda la luce"
        FORMS[w[0].n]?.let { w = listOf(W(it)) + w.drop(1) }
        val f = w[0].n

        // Con il pronome: la persona di cui stavi parlando ("chiamala", "scrivile che arrivo", "digli ok")
        if (f in CALL_HER) (hints.lastPerson() ?: if (f.startsWith("richiam")) hints.lastCaller() else null)?.let { return "chiama $it" }
        if (f in WRITE_HER) (hints.lastPerson() ?: hints.lastWriter())?.let { who ->
            var i = 1
            while (i < w.size && w[i].n in setOf("che", "di", "dicendo")) i++
            return "scrivi a $who" + (if (i < w.size) " " + ow(i) else "")
        }
        // Richiama: l'ultima chiamata persa
        if (f == "richiama") {
            val who = if (w.size > 1) ow(1) else hints.lastCaller()
            if (who != null) return "chiama $who"
        }
        // Chiamare: tutte le forme diventano "chiama"; "fai una telefonata a" è già senza "fai"
        if (f in CALL) {
            var i = 1
            while (i < w.size && w[i].n in setOf("a", "ad", "al", "alla", "una", "un", "chiamata", "telefonata", "videochiamata")) i++
            if (i < w.size) return "chiama " + ow(i)
        }
        if (f == "una" && w.size > 2 && w[1].n in setOf("chiamata", "telefonata") ) {
            var i = 2
            while (i < w.size && w[i].n in setOf("a", "ad", "al", "alla")) i++
            if (i < w.size) return "chiama " + ow(i)
        }

        // Rispondere senza dire a chi: a chi ti ha scritto per ultimo
        if (f in setOf("rispondi", "rispondile", "rispondigli", "rispondere")) {
            val toWho = w.size > 2 && w[1].n in setOf("a", "ad", "al", "alla")
            if (!toWho && w.size > 1 && hints.isName(w[1].n)) return "scrivi a " + ow(1)
            if (!toWho) {
                val who = hints.lastWriter() ?: return text.trim()
                return "scrivi a $who " + ow(1)
            }
        }
        // "dì a Elisa che…", "rispondi a Elisa…", "scrivere a Elisa"
        if (f in SAY && w.size > 2 && w[1].n in setOf("a", "ad", "al", "alla")) {
            val rest = w.drop(2)
            val via = rest.firstOrNull()?.n?.takeIf { it in VIAS }
            return if (via != null) "scrivi a " + rest.drop(1).joinToString(" ") { it.o } + " su $via" else "scrivi a " + ow(2)
        }
        // "manda un whatsapp a Elisa", "invia un messaggio a Elisa", "manda un vocale a Elisa"
        if (f in SEND || f == "un" && w.size > 1 && w[1].n in VIAS + "messaggio") {
            var i = if (f == "un") 0 else 1
            var via: String? = null
            while (i < w.size && w[i].n in setOf("un", "uno", "una", "il", "messaggio", "messaggino", "vocale", "audio", "a", "ad", "al", "alla") + VIAS) {
                if (w[i].n in VIAS) via = w[i].n
                i++
            }
            if (i < w.size && i > 1) return "scrivi a " + ow(i) + (via?.let { " su $it" } ?: "")
        }

        // La sveglia: "metti la sveglia alle 7", "imposta una sveglia per le 7", "punta la sveglia"
        val alarmAt = w.indexOfFirst { it.n == "sveglia" }
        if (alarmAt in 1..3 && w.take(alarmAt).all { it.n in setOf("metti", "imposta", "punta", "aggiungi", "crea", "la", "una", "un", "nuova") }) {
            return "sveglia " + ow(alarmAt + 1)
        }
        // "avvisami tra 10 minuti", "un timer di 5 minuti"
        if (f in setOf("avvisami", "avvertimi") && w.size > 2 && w[1].n in setOf("tra", "fra")) return "timer " + ow(2)
        // Musica: "fammi sentire Vasco", "ascoltare un po' di jazz"
        if (f in PLAY && w.size > 1) return "ascolta " + ow(1)
        // Strada: "come arrivo a Bologna", "come si arriva in stazione", "strada per Milano"
        for (p in listOf("come arrivo a", "come arrivo in", "come si arriva a", "come si arriva in", "strada per", "naviga fino a", "portami fino a", "andiamo in", "portami in")) {
            val n = starts(p)
            if (n > 0 && w.size > n) return "portami a " + ow(n)
        }
        return ow()
    }

    /** Il verbo più vicino a [word], con al massimo un errore (due nelle parole lunghe). */
    private fun closestVerb(word: String): String? {
        if (word.length < 4) return null
        val tol = if (word.length >= 7) 2 else 1
        return VERBS.map { it to lev(word, it) }.filter { it.second in 1..tol }.minByOrNull { it.second }?.first
    }

    /** Distanza di modifica, con due lettere scambiate ("scirvi") contate come un errore solo. */
    private fun lev(a: String, b: String): Int {
        val d = Array(a.length + 1) { i -> IntArray(b.length + 1) { j -> if (i == 0) j else if (j == 0) i else 0 } }
        for (i in 1..a.length) for (j in 1..b.length) {
            val c = if (a[i - 1] == b[j - 1]) 0 else 1
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + c)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
        }
        return d[a.length][b.length]
    }
}
