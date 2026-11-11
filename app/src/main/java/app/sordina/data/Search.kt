package app.sordina.data

import java.text.Normalizer

/**
 * Ricerca tollerante sui nomi. Prima versione: solo app.
 * Contatti, scorciatoie e azioni arriveranno nel motore delle intenzioni.
 */
object Search {
    private val marks = Regex("\\p{Mn}+")

    fun normalize(s: String): String =
        marks.replace(Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD), "")
            .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /** Punteggio 0..100 di quanto [query] corrisponde a [text] (entrambi normalizzati). */
    fun score(query: String, text: String): Int {
        if (query.isEmpty()) return 0
        if (text == query) return 100
        if (text.startsWith(query)) return 90
        val words = text.split(' ')
        if (words.any { it.startsWith(query) }) return 80
        // Iniziali: "ha" -> "Home Assistant"
        val initials = words.mapNotNull { it.firstOrNull() }.joinToString("")
        if (query.length >= 2 && initials.startsWith(query)) return 75
        if (text.contains(query)) return 65
        // Errori di battitura: confronto con l'inizio di ogni parola
        if (query.length >= 3) {
            val tolerance = if (query.length >= 6) 2 else 1
            for (w in words + text.replace(" ", "")) {
                val prefix = w.take(query.length)
                if (prefix.length >= query.length - 1 && levenshtein(query, prefix) <= tolerance) return 50
            }
        }
        // Lettere nell'ordine giusto: "wapp" -> "whatsapp"
        // (la prima lettera deve coincidere, altrimenti escono app che non c'entrano)
        if (query.length >= 3 && words.any { it.firstOrNull() == query[0] } &&
            isSubsequence(query, text.replace(" ", ""))
        ) return 35
        return 0
    }

    private fun isSubsequence(q: String, t: String): Boolean {
        var i = 0
        for (ch in t) if (i < q.length && q[i] == ch) i++
        return i == q.length
    }

    private fun levenshtein(a: String, b: String): Int {
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            cur.copyInto(prev)
        }
        return prev[b.length]
    }

    /** Le app migliori per la ricerca, a parità di testo vince quella più usata. */
    fun apps(query: String, apps: List<AppEntry>, usage: Map<String, Double>, limit: Int = 5): List<AppEntry> {
        val q = normalize(query)
        if (q.isEmpty()) return emptyList()
        return apps.asSequence()
            .map { it to score(q, it.normalized) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<AppEntry, Int>> { it.second }
                .thenByDescending { usage[it.first.packageName] ?: 0.0 })
            .take(limit)
            .map { it.first }
            .toList()
    }
}
