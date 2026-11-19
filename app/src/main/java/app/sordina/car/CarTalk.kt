package app.sordina.car

import app.sordina.Lang
import app.sordina.SordinaApp
import app.sordina.data.Act
import app.sordina.data.Held
import app.sordina.data.Kind
import app.sordina.data.Search
import app.sordina.data.Upcoming
import app.sordina.service.Classifier
import java.util.Locale

/** Quello che hai chiesto a Parla */
sealed interface CarAsk {
    /** "cosa c'è in sordina?": solo chi, mai cosa */
    data object Waiting : CarAsk
    /** "leggi Paolo" */
    data class Read(val group: List<Held>) : CarAsk
    /** "rispondi a Paolo che arrivo": [text] null, la risposta si chiede dopo */
    data class Reply(val group: List<Held>, val text: String?) : CarAsk
    /** "fai passare tutti per un'ora" */
    data class PassAll(val minutes: Int) : CarAsk
    /** "solo importanti" */
    data object PassImportant : CarAsk
    /** "il prossimo impegno?" */
    data object NextEvent : CarAsk
    /** "portami al calcetto": un impegno di oggi con l'indirizzo */
    data class GoTo(val event: Upcoming) : CarAsk
    /** Routine, allarmi, Assist, chiamate: il motore della barra di Casa */
    data class Do(val act: Act) : CarAsk
    /** App, timer, musica, navigatore, messaggi nuovi: lì c'è Google */
    data object Google : CarAsk
    data object Unknown : CarAsk
}

/**
 * Parla capisce prima le cose di Sordina (quello che aspetta, cosa passa, l'agenda), poi passa la frase al motore della
 * barra di Casa (routine, allarmi, chiamate) e infine ad Assist di Home Assistant: non dice "non ho capito" se c'è casa.
 * Le frasi di Sordina in italiano e in inglese qui; le altre lingue in [CarPhrases], solo quella dell'app.
 */
object CarTalk {
    private val READ = setOf("leggi", "leggimi", "read")
    private val REPLY = setOf("rispondi", "replica", "reply", "answer")
    private val SAYING = setOf("che", "dicendo", "dicendogli", "dicendole", "that", "saying")
    private val GO = listOf("portami", "vai", "naviga", "take me", "navigate", "go to", "drive to")
    private val FILLER = setOf("a", "al", "allo", "alla", "ai", "agli", "alle", "da", "dal", "dalla", "di", "del", "della", "il", "lo", "la",
        "i", "gli", "le", "l", "to", "the", "my", "mi", "me", "per", "for", "verso", "su", "with", "con")

    /** Le chat e le app che aspettano adesso, senza le app nascoste: le loro le dice solo il telefono sbloccato */
    fun groups(app: SordinaApp): List<List<Held>> = app.store.groups().filter { it.first().reason != Classifier.REASON_HIDDEN }

    fun understand(app: SordinaApp, said: String, events: List<Upcoming>): CarAsk =
        understand(said, groups(app), events, Lang.chosen(app) ?: Locale.getDefault().toLanguageTag()) { engine(app, said) }

    /** Il motore della barra: routine, allarmi, chiamate; il resto di casa ad Assist */
    private fun engine(app: SordinaApp, said: String): CarAsk {
        val u = runCatching { app.intents.understand(said, emptyList(), emptyMap(), learn = false) }.getOrNull()
        val top = u?.top
        return when (val act = top?.act) {
            is Act.Routine, is Act.AlarmOn, is Act.AlarmOff, is Act.Assist, is Act.Call -> CarAsk.Do(act)
            null, is Act.Web -> if (app.ha.configured) CarAsk.Do(Act.Assist(said)) else CarAsk.Unknown
            else -> if (top.kind == Kind.WEB && app.ha.configured) CarAsk.Do(Act.Assist(said)) else CarAsk.Google
        }
    }

    /** Le frasi di Sordina su [groups] (chi aspetta) ed [events] (oggi); il resto a [engine] */
    fun understand(said: String, groups: List<List<Held>>, events: List<Upcoming>, lang: String = "it", engine: () -> CarAsk): CarAsk {
        val n = Search.normalize(said)
        val w = n.split(' ').filter { it.isNotEmpty() }
        if (w.isEmpty()) return CarAsk.Unknown
        fun has(vararg p: String) = p.any { " $n ".contains(" $it ") }

        // Cosa passa
        if (has("solo importanti", "solo gli importanti", "solo le importanti", "only important", "only the important")) return CarAsk.PassImportant
        if ((has("passare", "passino", "let", "through") && has("tutti", "tutto", "tutte", "everyone", "everything", "all")))
            return CarAsk.PassAll(minutes(n))

        // Quello che aspetta
        if (has("in sordina", "chi mi ha scritto", "chi ha scritto", "cosa mi sono perso", "what s waiting", "whats waiting", "who wrote", "what did i miss"))
            return CarAsk.Waiting
        if (w.first() in READ) group(groups, w.drop(1))?.let { return CarAsk.Read(it) }
        if (w.first() in REPLY) {
            val rest = w.drop(1).dropWhile { it in FILLER }
            val cut = rest.indexOfFirst { it in SAYING }
            val who = if (cut >= 0) rest.take(cut) else rest
            val g = group(groups, who) ?: (1 until who.size).reversed().firstNotNullOfOrNull { k -> group(groups, who.take(k))?.let { it to k } }?.let { (g, k) ->
                // "rispondi a Paolo arrivo tra dieci minuti": il nome è l'inizio, il resto è la risposta
                return CarAsk.Reply(g, original(said, who.drop(k)))
            }
            if (g != null) return CarAsk.Reply(g, if (cut >= 0) original(said, rest.drop(cut + 1)) else null)
        }

        // L'agenda
        if (has("prossimo impegno", "prossimo appuntamento", "cosa ho dopo", "che impegni ho", "next appointment", "next event", "what s next", "whats next"))
            return CarAsk.NextEvent
        GO.firstOrNull { n.startsWith("$it ") }?.let { lead ->
            val target = n.removePrefix(lead).split(' ').filter { it.isNotEmpty() && it !in FILLER }
            events.filter { it.location != null }.firstOrNull { e -> Search.normalize(e.title).split(' ').let { t -> target.isNotEmpty() && target.all { it in t } } }
                ?.let { return CarAsk.GoTo(it) }
        }

        // Le altre lingue: le stesse cose, con le loro frasi
        CarPhrases.understand(said, lang, groups, events)?.let { return it }

        return engine()
    }

    /** "per due ore", "per mezz'ora", "for 2 hours": quanto far passare tutti. Di base un'ora */
    private fun minutes(n: String): Int {
        val w = n.split(' ')
        val i = w.indexOfFirst { it in setOf("ora", "ore", "hour", "hours") }
        if (" $n ".contains(" mezz ora ") || " $n ".contains(" mezzora ") || " $n ".contains(" half an hour ")) return 30
        if (i <= 0) return 60
        val k = when (val q = w[i - 1]) {
            "un", "una", "an", "a", "one" -> 1
            "due", "two" -> 2; "tre", "three" -> 3; "quattro", "four" -> 4
            else -> q.toIntOrNull() ?: 1
        }
        return (k * 60).coerceIn(30, 8 * 60)
    }

    /** La chat o l'app in attesa che i nomi [who] indicano: tutto il nome o la prima parola ("Paolo" per "Paolo Rossi") */
    fun group(all: List<List<Held>>, who: List<String>): List<Held>? {
        val q = who.filter { it !in FILLER }
        if (q.isEmpty()) return null
        fun words(g: List<Held>) = Search.normalize(g.first().groupTitle).split(' ').filter { it.isNotEmpty() }
        return all.firstOrNull { words(it) == q } ?: all.firstOrNull { g -> words(g).let { it.isNotEmpty() && q.all { x -> x in it } } }
    }

    /** Le ultime parole [words] della frase (normalizzate) come le hai dette tu, con maiuscole e accenti: la risposta parte così com'è */
    private fun original(said: String, words: List<String>): String? {
        if (words.isEmpty()) return null
        val skip = Search.normalize(said).split(' ').count { it.isNotEmpty() } - words.size
        val parts = said.trim().split(Regex("\\s+"))
        var seen = 0
        var start = 0
        while (start < parts.size && seen < skip) { seen += Search.normalize(parts[start]).split(' ').count { it.isNotEmpty() }; start++ }
        return parts.drop(start).joinToString(" ").trim().ifEmpty { null }?.replaceFirstChar { it.uppercase() }
    }
}
