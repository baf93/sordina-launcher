package app.sordina.data

import org.json.JSONArray
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.exp

/**
 * Le tue risposte veloci, imparate da te: cosa ti hanno scritto e cosa hai risposto ("A che ora arrivi?" → "Arrivo!").
 * Quando rispondi a qualcuno, Sordina propone le due risposte che di solito dai a un messaggio così.
 * Niente modello: parole in comune e quante volte l'hai detto, che sbiadiscono col tempo. Resta tutto sul telefono.
 */
class Replies(private val file: File?) {
    private class Pair(val words: Set<String>, val question: Boolean, val reply: String, val time: Long)

    private val pairs = ArrayList<Pair>()
    private val io = Executors.newSingleThreadExecutor()
    @Volatile private var loaded = false

    @Synchronized
    private fun ensure() {
        if (loaded) return
        loaded = true
        runCatching {
            val f = file ?: return
            if (!f.exists()) return
            val a = JSONArray(f.readText())
            for (i in 0 until a.length()) {
                val o = a.getJSONArray(i)
                pairs += Pair(o.getString(0).split(' ').filter { it.isNotEmpty() }.toSet(), o.getBoolean(1), o.getString(2), o.getLong(3))
            }
        }
    }

    /** Hai risposto [reply] a [incoming]. Solo risposte brevi: un messaggio lungo non si ripete. */
    @Synchronized
    fun learn(incoming: String, reply: String, time: Long = System.currentTimeMillis()) {
        val r = reply.trim()
        if (r.isEmpty() || r.length > 40 || r.count { it == ' ' } > 5) return
        ensure()
        if (pairs.any { it.time == time && it.reply == r }) return
        pairs += Pair(words(incoming), incoming.trim().endsWith("?"), r, time)
        while (pairs.size > 400) pairs.removeAt(0)
        save()
    }

    /**
     * Le risposte che di solito dai a un messaggio come [incoming], la più probabile per prima.
     * Una risposta conta se l'hai data almeno due volte.
     */
    @Synchronized
    fun suggest(incoming: String, limit: Int = 2, now: Long = System.currentTimeMillis()): List<String> {
        ensure()
        if (pairs.isEmpty()) return emptyList()
        val w = words(incoming)
        val q = incoming.trim().endsWith("?")
        val score = HashMap<String, Double>()
        val times = HashMap<String, Int>()
        val shown = HashMap<String, String>()
        for (p in pairs) {
            val k = Search.normalize(p.reply)
            val age = (now - p.time) / 86_400_000.0
            val overlap = if (w.isEmpty() || p.words.isEmpty()) 0.0 else (w intersect p.words).size.toDouble() / (w union p.words).size
            val s = exp(-age / 45.0) * (1.0 + 4.0 * overlap + if (q == p.question) 0.5 else 0.0)
            score.merge(k, s, Double::plus)
            times.merge(k, 1, Int::plus)
            shown[k] = p.reply
        }
        return score.entries.filter { (times[it.key] ?: 0) >= 2 }.sortedByDescending { it.value }.take(limit).mapNotNull { shown[it.key] }
    }

    private fun words(s: String) = Search.normalize(s).split(' ').filter { it.length >= 2 }.toSet()

    private fun save() {
        val f = file ?: return
        val snap = pairs.map { listOf(it.words.joinToString(" "), it.question, it.reply, it.time) }
        io.execute {
            runCatching {
                val a = JSONArray()
                snap.forEach { a.put(JSONArray().put(it[0]).put(it[1]).put(it[2]).put(it[3])) }
                f.writeSafely(a.toString())
            }
        }
    }
}
