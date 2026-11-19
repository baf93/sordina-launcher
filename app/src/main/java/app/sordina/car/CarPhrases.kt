package app.sordina.car

import app.sordina.data.Held
import app.sordina.data.Lingue
import app.sordina.data.Search
import app.sordina.data.Upcoming

/**
 * Le frasi di Parla nelle lingue oltre all'italiano e all'inglese (quelle stanno in [CarTalk]).
 * Un pacchetto per lingua, e vale solo quella dell'app: "las" in svedese è "leggi", altrove no.
 *
 * - [Pack.waiting], [Pack.important], [Pack.next]: espressioni che bastano da sole, ovunque nella frase.
 * - "Fai passare tutti": un verbo di [Pack.pass] e una parola di [Pack.all]; la durata da [Pack.hours] e [Pack.nums],
 *   o una frase fissa di [Pack.fixed] ("mezz'ora" = 30). Di base un'ora.
 * - [Pack.read], [Pack.reply], [Pack.go]: modelli con {x} (chi, o dove) e {y} (il testo della risposta), provati in ordine.
 *   [Pack.fillers]: le parole intorno al nome da togliere (articoli, preposizioni, "a" di "rispondi a").
 * - [Pack.glued]: cinese, giapponese, coreano e thai: gli spazi non contano e la frase si confronta così com'è
 *   (togliere gli accenti romperebbe kana e hangul).
 *
 * I nomi delle lingue con i casi ("Petrovi", "Paololle", "Маше") si trovano anche declinati: basta l'inizio del nome.
 */
internal object CarPhrases {

    class Pack(
        val glued: Boolean = false,
        val waiting: List<String>,
        val important: List<String>,
        val pass: List<String>,
        val all: List<String>,
        val hours: List<String>,
        val nums: Map<String, Int>,
        val fixed: Map<String, Int>,
        val next: List<String>,
        val read: List<String>,
        val reply: List<String>,
        val go: List<String>,
        val fillers: List<String> = emptyList(),
        /** Cortesie attaccate al nome: "さん", "씨", "คุณ" */
        val honor: List<String> = emptyList(),
    )

    /** Quello che hai chiesto, se è una frase di Sordina in [lang]; null se no (allora decide il motore) */
    fun understand(said: String, lang: String, groups: List<List<Held>>, events: List<Upcoming>): CarAsk? {
        val p = PACKS[canonical(lang)] ?: return null
        val c = Compiled.of(p)
        val text = c.text(said)
        if (text.isBlank()) return null
        val flat = text.replace(" ", "")
        fun has(list: List<String>) = list.any { k -> if (p.glued) flat.contains(k) else " $text ".contains(" $k ") }

        if (has(c.important)) return CarAsk.PassImportant
        if (has(c.pass) && has(c.all)) return CarAsk.PassAll(c.minutes(text))
        if (has(c.waiting)) return CarAsk.Waiting
        if (has(c.next)) return CarAsk.NextEvent

        for (re in c.read) re.find(text)?.let { m -> c.group(groups, m.groupValues[1])?.let { return CarAsk.Read(it) } }
        for ((re, withText) in c.reply) re.find(text)?.let { m ->
            var who = m.groupValues[1]
            var y = if (withText) m.groupValues[2] else null
            // "{x} {y}": il nome si prende anche le parole di contorno che lo seguono ("Paolo'ya geliyorum": "ya" è del nome)
            if (y != null && !p.glued) {
                val ys = y.split(' ').toMutableList()
                while (ys.size > 1 && ys.first() in c.fillers) who += " " + ys.removeAt(0)
                y = ys.joinToString(" ")
            }
            c.group(groups, who)?.let { g -> return CarAsk.Reply(g, y?.let { c.original(said, it) }) }
        }
        for (re in c.go) re.find(text)?.let { m ->
            c.event(events, m.groupValues[1])?.let { return CarAsk.GoTo(it) }
        }
        return null
    }

    private fun canonical(lang: String): String {
        val l = lang.lowercase().replace('_', '-')
        return when {
            l.startsWith("zh") -> "zh"
            l.startsWith("in") || l.startsWith("id") -> "id"
            l.startsWith("no") || l.startsWith("nb") || l.startsWith("nn") -> "nb"
            else -> l.substringBefore('-')
        }
    }

    /** Un pacchetto pronto: modelli in espressioni, parole già nella forma del testo */
    private class Compiled(val p: Pack) {
        /** Il testo come si confronta: senza accenti e punteggiatura; per le lingue senza spazi solo minuscolo e niente punteggiatura */
        fun text(s: String): String =
            if (p.glued) s.lowercase().replace(Regex("[^\\p{L}\\p{M}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()
            else Lingue.flat(Search.normalize(s))

        private fun k(s: String) = text(s).let { if (p.glued) it.replace(" ", "") else it }
        val waiting = p.waiting.map(::k)
        val important = p.important.map(::k)
        val pass = p.pass.map(::k)
        val all = p.all.map(::k)
        val next = p.next.map(::k)
        val fillers = p.fillers.map(::k).toSet()
        private val honor = p.honor.map(::k)

        private fun compile(t: String): Regex {
            val ws = if (p.glued) "\\s*" else "\\s+"
            val out = StringBuilder("^")
            Regex("\\{([xy])\\}|([^{]+)").findAll(t).forEach { m ->
                when (m.groupValues[1]) {
                    "x" -> out.append("(.+?)")
                    "y" -> out.append("(.+)")
                    else -> {
                        val raw = m.groupValues[2]
                        val lead = raw.startsWith(" "); val trail = raw.endsWith(" ")
                        val words = text(raw).split(' ').filter { it.isNotEmpty() }
                        if (lead) out.append(ws)
                        out.append(words.joinToString(ws) { Regex.escape(it) })
                        if (trail && words.isNotEmpty()) out.append(ws)
                    }
                }
            }
            // Un {x} in fondo si prende tutto il resto; dopo l'ultima parola del modello può seguire altro ("읽어줘", "lütfen")
            val s = out.toString()
            return Regex(if (s.endsWith("(.+?)")) s.dropLast(5) + "(.+)$" else s)
        }

        val read = p.read.map(::compile)
        val reply = p.reply.map { compile(it) to it.contains("{y}") }
        val go = p.go.map(::compile)

        private val hourRe = Regex(
            "(\\d+|" + p.nums.keys.map(::k).sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) } + ")\\s*(" +
                p.hours.map(::k).sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) } + ")"
        )
        private val numK = p.nums.mapKeys { k(it.key) }
        private val fixedK = p.fixed.mapKeys { k(it.key) }.entries.sortedByDescending { it.key.length }

        fun minutes(text: String): Int {
            val t = if (p.glued) text.replace(" ", "") else " $text "
            fixedK.firstOrNull { (f, _) -> if (p.glued) t.contains(f) else t.contains(" $f ") }?.let { return it.value }
            val m = hourRe.find(t) ?: return 60
            val n = m.groupValues[1].toIntOrNull() ?: numK[m.groupValues[1]] ?: 1
            return (n * 60).coerceIn(30, 8 * 60)
        }

        /** Il nome detto senza contorno: le parole di [fillers] fuori, le cortesie via */
        private fun name(who: String): List<String> {
            var w = who.trim()
            if (p.glued) {
                w = w.replace(" ", "")
                for (h in honor) { if (w.endsWith(h)) w = w.removeSuffix(h); if (w.startsWith(h)) w = w.removePrefix(h) }
                return listOf(w).filter { it.isNotEmpty() }
            }
            return w.split(' ').filter { it.isNotEmpty() && it !in fillers }
        }

        /** Due parole che sono lo stesso nome, anche declinato: "petrovi" e "petr", "маше" e "маша", "paololle" e "paolo" */
        private fun same(said: String, title: String): Boolean {
            if (said == title) return true
            if (title.length < 3 || said.length > title.length + 3) return false
            val common = said.zip(title).takeWhile { (a, b) -> a == b }.size
            return common >= maxOf(3, title.length - 1)
        }

        fun group(all: List<List<Held>>, who: String): List<Held>? {
            val q = name(who)
            if (q.isEmpty()) return null
            if (p.glued) {
                val x = q.first()
                fun t(g: List<Held>) = text(g.first().groupTitle).replace(" ", "")
                return all.firstOrNull { t(it) == x } ?: all.firstOrNull { g -> t(g).let { it.isNotEmpty() && (it.startsWith(x) || x.startsWith(it)) } }
            }
            fun words(g: List<Held>) = text(g.first().groupTitle).split(' ').filter { it.isNotEmpty() }
            return all.firstOrNull { words(it) == q } ?: all.firstOrNull { g -> words(g).let { ws -> ws.isNotEmpty() && q.all { x -> ws.any { same(x, it) } } } }
        }

        fun event(events: List<Upcoming>, where: String): Upcoming? {
            val q = name(where)
            if (q.isEmpty()) return null
            return events.filter { it.location != null }.firstOrNull { e ->
                if (p.glued) text(e.title).replace(" ", "").let { it.isNotEmpty() && (it.contains(q.first()) || q.first().contains(it)) }
                else text(e.title).split(' ').let { ws -> q.all { x -> ws.any { same(x, it) } } }
            }
        }

        /** Il testo della risposta come l'hai detto tu: per le lingue con gli spazi, le parole originali che corrispondono a [y] */
        fun original(said: String, y: String): String? {
            if (p.glued) return y.trim().ifEmpty { null }
            val want = y.split(' ').filter { it.isNotEmpty() }
            if (want.isEmpty()) return null
            val tokens = said.trim().split(Regex("\\s+"))
            val flat = tokens.flatMapIndexed { i, tok -> text(tok).split(' ').filter { it.isNotEmpty() }.map { it to i } }
            for (start in flat.size - want.size downTo 0) {
                if ((0 until want.size).all { flat[start + it].first == want[it] }) {
                    val from = flat[start].second; val to = flat[start + want.size - 1].second
                    return tokens.subList(from, to + 1).joinToString(" ").trim().trim(',', '.', '!', '?', '¿', '¡')
                        .ifEmpty { null }?.replaceFirstChar { it.uppercase() }
                }
            }
            return y.replaceFirstChar { it.uppercase() }
        }

        companion object {
            private val cache = HashMap<Pack, Compiled>()
            @Synchronized fun of(p: Pack) = cache.getOrPut(p) { Compiled(p) }
        }
    }

    private val PACKS: Map<String, Pack> = mapOf(
        "es" to Pack(
            waiting = listOf("en sordina", "que me he perdido", "que me perdi", "quien me ha escrito", "quien me escribio", "que hay pendiente", "que tengo pendiente"),
            important = listOf("solo importantes", "solo los importantes", "solo lo importante", "solo las importantes"),
            pass = listOf("pasar", "pasen", "pase"), all = listOf("todos", "todo", "todas", "todo el mundo"),
            hours = listOf("hora", "horas"), nums = mapOf("una" to 1, "un" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4), fixed = mapOf("media hora" to 30),
            next = listOf("proxima cita", "siguiente cita", "proximo evento", "que tengo despues", "proximo compromiso"),
            read = listOf("leeme {x}", "lee los mensajes de {x}", "lee {x}"),
            reply = listOf("respondele {x} que {y}", "responde {x} que {y}", "responde {x} diciendo {y}", "contestale {x} que {y}", "contesta {x} que {y}",
                "respondele {x}", "responde {x}", "contestale {x}", "contesta {x}"),
            go = listOf("llevame {x}", "navega {x}", "vamos {x}", "ve {x}"),
            fillers = listOf("a", "al", "la", "el", "los", "las", "de", "del", "hacia", "hasta"),
        ),
        "fr" to Pack(
            waiting = listOf("en sourdine", "en sordina", "qu est ce que j ai rate", "qui m a ecrit", "qu est ce qui attend", "qu est ce que j ai manque"),
            important = listOf("seulement les importants", "que les importants", "juste les importants", "seulement l important", "que l important"),
            pass = listOf("passer", "passe", "passent"), all = listOf("tout", "tous", "toutes", "tout le monde"),
            hours = listOf("heure", "heures"), nums = mapOf("une" to 1, "un" to 1, "deux" to 2, "trois" to 3, "quatre" to 4), fixed = mapOf("demi heure" to 30),
            next = listOf("prochain rendez vous", "prochain evenement", "qu est ce que j ai apres", "quel est mon prochain rendez vous"),
            read = listOf("lis moi {x}", "lis les messages de {x}", "lis {x}", "lire {x}"),
            reply = listOf("reponds {x} que {y}", "reponds {x} en disant {y}", "repondre {x} que {y}", "reponds {x}", "repondre {x}"),
            go = listOf("emmene moi {x}", "amene moi {x}", "conduis moi {x}", "navigue {x}", "va {x}", "aller {x}"),
            fillers = listOf("a", "au", "aux", "la", "le", "les", "l", "de", "du", "des", "chez", "vers", "jusqu"),
        ),
        "de" to Pack(
            waiting = listOf("in sordina", "was habe ich verpasst", "wer hat mir geschrieben", "was wartet", "wer hat geschrieben"),
            important = listOf("nur wichtige", "nur die wichtigen", "nur wichtiges"),
            pass = listOf("durch", "durchlassen", "durchkommen"), all = listOf("alle", "alles", "jeden"),
            hours = listOf("stunde", "stunden"), nums = mapOf("eine" to 1, "ein" to 1, "zwei" to 2, "drei" to 3, "vier" to 4), fixed = mapOf("halbe stunde" to 30),
            next = listOf("nachster termin", "nachstes ereignis", "was habe ich als nachstes", "was steht als nachstes an"),
            read = listOf("lies mir {x} vor", "lies {x} vor", "lies {x}"),
            reply = listOf("antworte {x} dass {y}", "antworte {x} mit {y}", "schreib {x} zuruck dass {y}", "antworte {x}"),
            go = listOf("bring mich {x}", "navigiere {x}", "fahr {x}", "fahre {x}"),
            fillers = listOf("zu", "zum", "zur", "nach", "dem", "der", "den", "die", "das", "an", "von", "vom", "mir", "vor"),
        ),
        "pt" to Pack(
            waiting = listOf("em sordina", "o que eu perdi", "quem me escreveu", "o que esta esperando", "o que tem pendente"),
            important = listOf("so importantes", "so os importantes", "apenas importantes", "apenas os importantes", "so o importante"),
            pass = listOf("passar", "passe", "passem"), all = listOf("todos", "tudo", "todo mundo", "todas"),
            hours = listOf("hora", "horas"), nums = mapOf("uma" to 1, "um" to 1, "duas" to 2, "dois" to 2, "tres" to 3), fixed = mapOf("meia hora" to 30),
            next = listOf("proximo compromisso", "proximo evento", "o que tenho depois", "proxima reuniao"),
            read = listOf("leia as mensagens de {x}", "leia {x}", "le {x}"),
            reply = listOf("responda {x} que {y}", "responde {x} que {y}", "responda {x} dizendo {y}", "responda {x}", "responde {x}"),
            go = listOf("me leva {x}", "me leve {x}", "leve me {x}", "navegue {x}", "vai {x}", "va {x}"),
            fillers = listOf("a", "ao", "para", "pro", "pra", "o", "os", "as", "de", "do", "da", "no", "na", "ate"),
        ),
        "nl" to Pack(
            waiting = listOf("in sordina", "wat heb ik gemist", "wie heeft me geschreven", "wat wacht er", "wie schreef me"),
            important = listOf("alleen belangrijke", "alleen de belangrijke", "alleen belangrijk"),
            pass = listOf("door", "doorlaten"), all = listOf("iedereen", "alles", "alle"),
            hours = listOf("uur", "uren"), nums = mapOf("een" to 1, "twee" to 2, "drie" to 3), fixed = mapOf("half uur" to 30, "halfuur" to 30),
            next = listOf("volgende afspraak", "wat heb ik hierna", "volgende evenement"),
            read = listOf("lees {x}"),
            reply = listOf("antwoord {x} dat {y}", "antwoord {x} met {y}", "beantwoord {x}", "antwoord {x}"),
            go = listOf("breng me {x}", "navigeer {x}", "rij {x}", "ga {x}"),
            fillers = listOf("naar", "de", "het", "aan", "van", "me", "voor", "mij"),
        ),
        "sv" to Pack(
            waiting = listOf("i sordina", "vad har jag missat", "vem har skrivit", "vad vantar", "vem skrev"),
            important = listOf("bara viktiga", "endast viktiga", "bara de viktiga"),
            pass = listOf("igenom", "slapp", "slappa"), all = listOf("alla", "allt"),
            hours = listOf("timme", "timmar", "timma"), nums = mapOf("en" to 1, "ett" to 1, "tva" to 2, "tre" to 3), fixed = mapOf("halvtimme" to 30, "halv timme" to 30),
            next = listOf("nasta mote", "nasta handelse", "vad har jag sen", "vad har jag harnast"),
            read = listOf("las upp {x}", "las {x}"),
            reply = listOf("svara {x} att {y}", "svara {x} med {y}", "svara {x}"),
            go = listOf("ta mig {x}", "navigera {x}", "kor {x}", "aka {x}"),
            fillers = listOf("till", "pa", "fran", "upp", "for", "mig", "den", "det"),
        ),
        "da" to Pack(
            waiting = listOf("i sordina", "hvad har jeg misset", "hvem har skrevet", "hvad venter", "hvem skrev"),
            important = listOf("kun vigtige", "kun de vigtige", "bare vigtige"),
            pass = listOf("igennem", "slip"), all = listOf("alle", "alt"),
            hours = listOf("time", "timer"), nums = mapOf("en" to 1, "et" to 1, "to" to 2, "tre" to 3), fixed = mapOf("halv time" to 30, "halvtime" to 30),
            next = listOf("næste aftale", "næste begivenhed", "hvad skal jeg nu", "hvad har jeg nu"),
            read = listOf("læs op fra {x}", "læs {x}"),
            reply = listOf("svar {x} at {y}", "svar {x} med {y}", "svar {x}"),
            go = listOf("tag mig {x}", "kør mig {x}", "naviger {x}", "kør {x}"),
            fillers = listOf("til", "på", "fra", "op", "mig", "den", "det"),
        ),
        "nb" to Pack(
            waiting = listOf("i sordina", "hva har jeg gått glipp av", "hvem har skrevet", "hva venter", "hvem skrev"),
            important = listOf("bare viktige", "kun viktige", "bare de viktige"),
            pass = listOf("gjennom", "slipp"), all = listOf("alle", "alt"),
            hours = listOf("time", "timer"), nums = mapOf("en" to 1, "ei" to 1, "et" to 1, "to" to 2, "tre" to 3), fixed = mapOf("halvtime" to 30, "halv time" to 30),
            next = listOf("neste avtale", "neste hendelse", "hva har jeg nå", "hva skjer nå"),
            read = listOf("les opp {x}", "les {x}"),
            reply = listOf("svar {x} at {y}", "svar {x} med {y}", "svar {x}"),
            go = listOf("kjør meg {x}", "ta meg {x}", "naviger {x}", "kjør {x}"),
            fillers = listOf("til", "på", "fra", "opp", "meg", "høyt", "den", "det"),
        ),
        "fi" to Pack(
            waiting = listOf("sordinassa", "mita odottaa", "kuka kirjoitti", "mita jai valiin", "mita olen missannut"),
            important = listOf("vain tarkeat", "vain tarkeita", "ainoastaan tarkeat"),
            pass = listOf("lapi"), all = listOf("kaikki", "kaiken", "kaikkien"),
            hours = listOf("tunniksi", "tunnin", "tuntia", "tunti", "tunneiksi"),
            nums = mapOf("yhdeksi" to 1, "yhden" to 1, "yksi" to 1, "kahdeksi" to 2, "kahden" to 2, "kaksi" to 2, "kolmeksi" to 3, "kolme" to 3),
            fixed = mapOf("puoleksi tunniksi" to 30, "puoli tuntia" to 30, "puolen tunnin" to 30),
            next = listOf("seuraava tapaaminen", "seuraava meno", "mita seuraavaksi", "mita minulla on seuraavaksi"),
            read = listOf("lue {x} viestit", "lue {x}"),
            reply = listOf("vastaa {x} etta {y}", "vastaa {x}"),
            go = listOf("vie minut {x}", "navigoi {x}", "aja {x}"),
            fillers = listOf("minut", "minulle", "viestit"),
        ),
        "pl" to Pack(
            waiting = listOf("w sordinie", "co przegapilem", "co przegapilam", "kto do mnie pisal", "kto pisal", "co czeka"),
            important = listOf("tylko wazne", "tylko te wazne"),
            pass = listOf("przepusc", "przepuszczaj", "przepuszczac"), all = listOf("wszystko", "wszystkich", "wszyscy"),
            hours = listOf("godzine", "godziny", "godzin", "godzina"), nums = mapOf("jedna" to 1, "dwie" to 2, "trzy" to 3), fixed = mapOf("pol godziny" to 30),
            next = listOf("nastepne spotkanie", "najblizsze spotkanie", "co mam dalej", "nastepne wydarzenie"),
            read = listOf("przeczytaj wiadomosci od {x}", "przeczytaj {x}", "czytaj {x}"),
            reply = listOf("odpowiedz {x} ze {y}", "odpisz {x} ze {y}", "odpowiedz {x}", "odpisz {x}"),
            go = listOf("zawiez mnie {x}", "nawiguj {x}", "jedz {x}", "prowadz {x}"),
            fillers = listOf("do", "na", "od", "mnie", "z", "ze", "wiadomosci"),
        ),
        "cs" to Pack(
            waiting = listOf("v sordine", "co jsem zmeskal", "co jsem zmeskala", "kdo mi psal", "kdo psal", "co ceka"),
            important = listOf("jen dulezite", "pouze dulezite", "jenom dulezite"),
            pass = listOf("pust", "poustej", "propoustej"), all = listOf("vsechno", "vsechny", "vse"),
            hours = listOf("hodinu", "hodiny", "hodin", "hodina"), nums = mapOf("jednu" to 1, "dve" to 2, "tri" to 3), fixed = mapOf("pul hodiny" to 30),
            next = listOf("dalsi schuzka", "pristi schuzka", "co mam dal", "dalsi udalost"),
            read = listOf("precti zpravy od {x}", "precti {x}", "cti {x}"),
            reply = listOf("odpovez {x} ze {y}", "odepis {x} ze {y}", "odpovez {x}", "odepis {x}"),
            go = listOf("zaved me {x}", "vezmi me {x}", "naviguj {x}", "jed {x}"),
            fillers = listOf("do", "na", "od", "k", "ke", "me", "z", "zpravy"),
        ),
        "ro" to Pack(
            waiting = listOf("in sordina", "ce am pierdut", "cine mi a scris", "ce asteapta", "cine a scris"),
            important = listOf("doar importante", "doar cele importante", "numai importante"),
            pass = listOf("treaca", "trece"), all = listOf("toate", "tot", "toti", "toata lumea"),
            hours = listOf("ora", "ore"), nums = mapOf("o" to 1, "una" to 1, "doua" to 2, "trei" to 3), fixed = mapOf("jumatate de ora" to 30),
            next = listOf("urmatoarea intalnire", "urmatorul eveniment", "ce am dupa", "ce urmeaza"),
            read = listOf("citeste mesajele de la {x}", "citeste {x}"),
            reply = listOf("raspunde {x} ca {y}", "raspunde {x}"),
            go = listOf("du ma {x}", "condu ma {x}", "navigheaza {x}", "mergi {x}"),
            fillers = listOf("i", "lui", "ei", "la", "de", "mi", "spre", "ma", "mesajele"),
        ),
        "tr" to Pack(
            waiting = listOf("sessizde neler var", "ne kacirdim", "kim yazdi", "bana kim yazdi", "neler bekliyor", "bekleyenler"),
            important = listOf("sadece onemliler", "yalnizca onemliler", "sadece onemli"),
            pass = listOf("gecsin", "gecir", "gecmesine"), all = listOf("herkes", "herkesi", "her sey", "hepsi", "hersey"),
            hours = listOf("saatligine", "saatlik", "saat"), nums = mapOf("bir" to 1, "iki" to 2, "uc" to 3), fixed = mapOf("yarim saat" to 30, "yarim saatligine" to 30),
            next = listOf("sonraki randevu", "siradaki randevu", "siradaki etkinlik", "sonra ne var"),
            read = listOf("{x} mesajlarini oku", "{x} mesajini oku", "{x} oku", "oku {x}"),
            reply = listOf("{x} {y} diye cevap ver", "{x} {y} diye yanitla", "{x} cevap ver", "{x} yanitla", "{x} cevapla"),
            go = listOf("beni {x} gotur", "{x} gotur", "{x} git", "{x} yol tarifi"),
            fillers = listOf("ya", "ye", "a", "e", "yu", "yi", "u", "i", "nin", "in", "un", "na", "ne", "beni"),
        ),
        "el" to Pack(
            waiting = listOf("σε σουρντινα", "τι εχασα", "ποιος μου εγραψε", "ποιος εγραψε", "τι περιμενει"),
            important = listOf("μονο τα σημαντικα", "μονο σημαντικα"),
            pass = listOf("περνουν", "περασουν", "περνανε"), all = listOf("ολα", "ολους", "ολοι"),
            hours = listOf("ωρα", "ωρες"), nums = mapOf("μια" to 1, "δυο" to 2, "τρεις" to 3), fixed = mapOf("μιση ωρα" to 30),
            next = listOf("επομενο ραντεβου", "τι εχω μετα", "επομενη εκδηλωση"),
            read = listOf("διαβασε μου {x}", "διαβασε τα μηνυματα {x}", "διαβασε {x}"),
            reply = listOf("απαντησε {x} οτι {y}", "απαντησε {x} πως {y}", "απαντησε {x}"),
            go = listOf("πηγαινε με {x}", "οδηγησε με {x}", "πλοηγηση {x}", "παμε {x}"),
            fillers = listOf("στον", "στην", "στο", "στα", "σε", "του", "της", "τον", "την", "το", "μου", "τα", "με", "μηνυματα"),
        ),
        "ru" to Pack(
            waiting = listOf("в сурдине", "что я пропустил", "что я пропустила", "кто мне писал", "кто писал", "что ждет"),
            important = listOf("только важные", "лишь важные"),
            pass = listOf("пропускай", "пропусти", "пропускать"), all = listOf("всех", "все"),
            hours = listOf("часов", "часа", "час"), nums = mapOf("один" to 1, "два" to 2, "три" to 3), fixed = mapOf("полчаса" to 30, "пол часа" to 30),
            next = listOf("следующая встреча", "что у меня дальше", "следующее событие"),
            read = listOf("прочитай сообщения от {x}", "прочитай {x}", "прочти {x}", "читай {x}"),
            reply = listOf("ответь {x} что {y}", "напиши в ответ {x} что {y}", "ответь {x}"),
            go = listOf("отвези меня {x}", "проложи маршрут {x}", "поехали {x}", "едем {x}"),
            fillers = listOf("в", "во", "на", "к", "ко", "до", "от", "меня", "мне", "сообщения"),
        ),
        "uk" to Pack(
            waiting = listOf("у сурдині", "в сурдині", "що я пропустив", "що я пропустила", "хто мені писав", "хто писав", "що чекає"),
            important = listOf("лише важливі", "тільки важливі"),
            pass = listOf("пропускай", "пропусти", "пропускати"), all = listOf("всіх", "усіх", "все", "усе", "всі"),
            hours = listOf("годину", "години", "годин", "година"), nums = mapOf("одну" to 1, "дві" to 2, "три" to 3), fixed = mapOf("пів години" to 30, "півгодини" to 30),
            next = listOf("наступна зустріч", "що в мене далі", "наступна подія"),
            read = listOf("прочитай повідомлення від {x}", "прочитай {x}", "читай {x}"),
            reply = listOf("відповідай {x} що {y}", "відповісти {x} що {y}", "напиши {x} що {y}", "відповідай {x}", "відповісти {x}"),
            go = listOf("відвези мене {x}", "прокладай маршрут {x}", "поїхали {x}", "їдемо {x}"),
            fillers = listOf("в", "у", "на", "до", "від", "мене", "мені", "повідомлення"),
        ),
        "ja" to Pack(
            glued = true,
            waiting = listOf("待機中", "誰から", "見逃した", "何が来てる", "何か届いてる"),
            important = listOf("重要なものだけ", "重要なのだけ", "大事なものだけ", "重要だけ"),
            pass = listOf("通して", "通す"), all = listOf("すべて", "全部", "全員", "みんな"),
            hours = listOf("時間"), nums = mapOf("一" to 1, "二" to 2, "三" to 3), fixed = mapOf("30分" to 30, "三十分" to 30, "半時間" to 30),
            next = listOf("次の予定", "この後の予定", "次は何"),
            read = listOf("{x}のメッセージを読んで", "{x}を読み上げて", "{x}を読んで", "{x}読んで"),
            reply = listOf("{x}に{y}と返信して", "{x}に{y}って返信して", "{x}に{y}と返事して", "{x}に返信して", "{x}へ返信して", "{x}に返事して"),
            go = listOf("{x}に連れて行って", "{x}まで案内して", "{x}までナビ", "{x}へ行って", "{x}に行って"),
            honor = listOf("さん", "ちゃん", "くん", "様"),
        ),
        "ko" to Pack(
            glued = true,
            waiting = listOf("대기 중", "뭐가 왔어", "누가 메시지 보냈어", "놓친 거", "누가 연락했어"),
            important = listOf("중요한 것만", "중요한 거만"),
            pass = listOf("통과", "허용"), all = listOf("모두", "전부"),
            hours = listOf("시간"), nums = mapOf("한" to 1, "두" to 2, "세" to 3), fixed = mapOf("30분" to 30, "삼십 분" to 30, "반 시간" to 30),
            next = listOf("다음 일정", "다음 약속", "다음에 뭐 있어"),
            read = listOf("{x}의 메시지 읽어", "{x} 메시지 읽어", "{x} 읽어"),
            reply = listOf("{x}에게 {y}라고 답장", "{x}한테 {y}라고 답장", "{x}에게 {y}이라고 답장", "{x}한테 {y}이라고 답장", "{x}에게 답장", "{x}한테 답장"),
            go = listOf("{x}으로 안내", "{x}로 안내", "{x}까지 길 안내", "{x}으로 가", "{x}로 가"),
            honor = listOf("씨", "님"),
        ),
        "zh" to Pack(
            glued = true,
            waiting = listOf("等待中", "有什么消息", "谁给我发消息", "谁找我", "我错过了什么", "有什麼消息", "誰給我發消息", "誰找我", "我錯過了什麼"),
            important = listOf("只要重要的", "只放行重要的", "只有重要的"),
            pass = listOf("放行", "通过", "通過"), all = listOf("所有", "全部", "都"),
            hours = listOf("个小时", "個小時", "小时", "小時"), nums = mapOf("一" to 1, "两" to 2, "兩" to 2, "二" to 2, "三" to 3),
            fixed = mapOf("半小时" to 30, "半个小时" to 30, "半小時" to 30, "半個小時" to 30, "三十分钟" to 30, "三十分鐘" to 30),
            next = listOf("下一个日程", "下一个安排", "接下来有什么", "下一個行程", "接下來有什麼", "下一个会议"),
            read = listOf("读一下{x}的消息", "读{x}的消息", "念{x}的消息", "讀{x}的訊息", "唸{x}的訊息", "读{x}", "念{x}", "讀{x}", "唸{x}"),
            reply = listOf("回复{x}说{y}", "回覆{x}說{y}", "回复{x}", "回覆{x}"),
            go = listOf("带我去{x}", "帶我去{x}", "导航到{x}", "導航到{x}", "去{x}"),
        ),
        "th" to Pack(
            glued = true,
            waiting = listOf("มีอะไรรออยู่", "ใครส่งข้อความมา", "ใครทักมา", "พลาดอะไรไป"),
            important = listOf("เฉพาะเรื่องสำคัญ", "เฉพาะที่สำคัญ", "แค่เรื่องสำคัญ"),
            pass = listOf("ผ่าน"), all = listOf("ทุกอย่าง", "ทุกคน", "ทั้งหมด"),
            hours = listOf("ชั่วโมง"), nums = mapOf("หนึ่ง" to 1, "สอง" to 2, "สาม" to 3), fixed = mapOf("ครึ่งชั่วโมง" to 30, "30นาที" to 30, "สามสิบนาที" to 30),
            next = listOf("นัดต่อไป", "นัดถัดไป", "กำหนดการถัดไป"),
            read = listOf("อ่านข้อความของ{x}", "อ่านข้อความจาก{x}", "อ่าน{x}"),
            reply = listOf("ตอบกลับ{x}ว่า{y}", "ตอบ{x}ว่า{y}", "ตอบกลับ{x}", "ตอบ{x}"),
            go = listOf("นำทางไป{x}", "พาไป{x}", "ไป{x}"),
            honor = listOf("คุณ"),
        ),
        "ar" to Pack(
            waiting = listOf("ماذا ينتظر", "من راسلني", "من كتب لي", "ماذا فاتني", "ما الذي ينتظر"),
            important = listOf("المهمة فقط", "فقط المهمة", "المهم فقط"),
            pass = listOf("يمر", "يمرون", "مرر", "تمر"), all = listOf("الجميع", "كل شيء", "الكل"),
            hours = listOf("ساعة", "ساعات"), nums = mapOf("واحدة" to 1, "ثلاث" to 3), fixed = mapOf("نصف ساعة" to 30, "ساعتين" to 120),
            next = listOf("الموعد التالي", "ماذا لدي بعد ذلك", "الحدث التالي"),
            read = listOf("اقرأ لي {x}", "اقرأ رسائل {x}", "اقرأ {x}"),
            reply = listOf("رد على {x} بأن {y}", "رد على {x} قائلا {y}", "أجب {x} بأن {y}", "رد على {x}", "أجب {x}"),
            go = listOf("خذني {x}", "وجهني {x}", "اذهب {x}", "انتقل {x}"),
            fillers = listOf("إلى", "الى", "لي", "على", "من", "رسائل"),
        ),
        "hi" to Pack(
            waiting = listOf("क्या इंतज़ार में है", "किसने लिखा", "किसने मैसेज किया", "क्या छूटा", "इंतज़ार में क्या है"),
            important = listOf("सिर्फ़ ज़रूरी", "केवल ज़रूरी"),
            pass = listOf("आने दो", "आने दें", "आने दे", "जाने दो"), all = listOf("सब", "सबको", "सबकुछ", "सब कुछ"),
            hours = listOf("घंटे", "घंटा", "घंटों"), nums = mapOf("एक" to 1, "दो" to 2, "तीन" to 3), fixed = mapOf("आधा घंटा" to 30, "आधे घंटे" to 30),
            next = listOf("अगला अपॉइंटमेंट", "अगली मीटिंग", "आगे क्या है", "अगला कार्यक्रम"),
            read = listOf("{x} का मैसेज पढ़ो", "{x} के मैसेज पढ़ो", "{x} पढ़कर सुनाओ", "{x} पढ़ो", "पढ़ो {x}"),
            reply = listOf("{x} को जवाब दो कि {y}", "{x} को लिखो कि {y}", "{x} को जवाब दो", "{x} को जवाब दें", "{x} को रिप्लाई करो"),
            go = listOf("मुझे {x} ले चलो", "{x} ले चलो", "{x} चलो"),
            fillers = listOf("को", "का", "के", "की", "मैसेज", "पर", "मुझे", "तक"),
        ),
        "id" to Pack(
            waiting = listOf("apa yang menunggu", "siapa yang mengirim pesan", "siapa yang chat", "apa yang terlewat", "apa yang saya lewatkan"),
            important = listOf("hanya yang penting", "cuma yang penting"),
            pass = listOf("lewat", "lolos"), all = listOf("semua", "semuanya"),
            hours = listOf("jam"), nums = mapOf("satu" to 1, "dua" to 2, "tiga" to 3), fixed = mapOf("setengah jam" to 30, "sejam" to 60),
            next = listOf("acara berikutnya", "janji berikutnya", "jadwal berikutnya"),
            read = listOf("bacakan pesan {x}", "baca pesan dari {x}", "bacakan {x}", "baca {x}"),
            reply = listOf("balas {x} bahwa {y}", "balas {x} kalau {y}", "jawab {x} bahwa {y}", "balas {x}", "jawab {x}"),
            go = listOf("antar saya {x}", "bawa saya {x}", "navigasi {x}", "pergi {x}"),
            fillers = listOf("ke", "dari", "pesan", "saya", "untuk", "di"),
        ),
        "vi" to Pack(
            waiting = listOf("co gi đang cho", "ai nhan cho toi", "ai đa nhan", "toi đa bo lo gi"),
            important = listOf("chi tin quan trong", "chi quan trong", "chi nhung tin quan trong"),
            pass = listOf("cho qua"), all = listOf("tat ca", "moi nguoi", "het"),
            hours = listOf("gio", "tieng"), nums = mapOf("mot" to 1, "hai" to 2, "ba" to 3), fixed = mapOf("nua tieng" to 30, "nua gio" to 30),
            next = listOf("cuoc hen tiep theo", "lich tiep theo", "su kien tiep theo", "tiep theo la gi"),
            read = listOf("đoc tin nhan cua {x}", "đoc tin cua {x}", "đoc {x}"),
            reply = listOf("tra loi {x} la {y}", "tra loi {x} rang {y}", "nhan lai cho {x} la {y}", "tra loi {x}", "nhan lai cho {x}"),
            go = listOf("đua toi {x}", "chi đuong {x}", "đi {x}"),
            fillers = listOf("cho", "cua", "đen", "toi", "anh", "chi", "em", "ban"),
        ),
    )
}
