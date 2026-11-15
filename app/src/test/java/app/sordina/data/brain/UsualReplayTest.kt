package app.sordina.data.brain

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.exp

/**
 * Il fumetto "di solito" rigiocato su una memoria vera (brain.tsv, mai nel repository): a ogni apertura,
 * cosa avrebbe detto il fumetto con le regole di [Brain.usual] e quante volte ci avrebbe preso, per ogni soglia.
 * Gira solo con BRAIN_TSV=/percorso/brain.tsv.
 */
class UsualReplayTest {
    private data class Ev(val t: Long, val item: String, val s: Situation, val weight: Double)

    private fun events(): List<Ev> {
        val path = System.getenv("BRAIN_TSV") ?: ""
        assumeTrue(path.isNotEmpty() && File(path).exists())
        fun u(v: String) = if (v == "?") null else v
        fun b(v: String) = when (v) { "1" -> true; "0" -> false; else -> null }
        return File(path).readLines().mapNotNull { l ->
            val p = l.split('\t')
            val t = p.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
            if ((p[0] == "G" || p[0] == "M") && p.size >= 9)
                Ev(t, p[2], Situation(t, u(p[3]), b(p[4]), b(p[5]), b(p[6]), u(p[7]), b(p[8]), device = p.getOrNull(9)?.let(::u)), if (p[0] == "M") 1.0 else 1.0)
            else null
        }.sortedBy { it.t }
    }

    @Test fun thresholds() {
        val all = events()
        val start = all.first().t + 4 * Model.DAY
        val ths = listOf(0.0, 0.1, 0.15, 0.2, 0.25, 0.3, 0.35, 0.4, 0.5)
        val spoke = IntArray(ths.size); val right = IntArray(ths.size); val rightAny = IntArray(ths.size)
        var moments = 0
        val m = Model(now = all.first().t)
        // Giusto se lo apri nei 30 minuti dopo: il fumetto resta lì per la mezz'ora
        fun soon(i: Int, item: String): Boolean { var j = i; while (j < all.size && all[j].t - all[i].t <= 30 * 60_000L) { if (all[j].item == item) return true; j++ }; return false }
        for ((idx, e) in all.withIndex()) {
            if (e.t >= start) {
                moments++
                val f = e.s.features(learning = false)
                val scored = m.items.map { it to m.habit(it, f, e.t) }.filter { it.second.isFinite() }
                if (scored.size >= 3) {
                    val top = scored.maxOf { it.second }
                    val z = scored.sumOf { exp(it.second - top) }
                    val ranked = scored.sortedByDescending { it.second }.map { (i, sc) -> i to exp(sc - top) / z }
                    for ((k, th) in ths.withIndex()) {
                        val ok = ranked.takeWhile { it.second >= th }.filter { (i, _) ->
                            m.times(i, e.t) >= 3 && (m.lastUsed(i) ?: 0L) < e.t - 30 * 60_000L &&
                                m.reason(i, f)?.let { r -> r.group in setOf("h", "dw", "we") || (r.value != "-" && r.value != "0") } == true
                        }.take(3).map { it.first }
                        if (ok.isNotEmpty()) {
                            spoke[k]++
                            if (soon(idx, ok.first())) right[k]++
                            if (ok.any { soon(idx, it) }) rightAny[k]++
                        }
                    }
                }
            }
            m.add(Gesture(e.t, e.item, e.s.features(true), e.weight))
        }
        println("Aperture valutate: $moments")
        for ((k, th) in ths.withIndex()) {
            val s = spoke[k].coerceAtLeast(1)
            println("soglia %.2f  parla %4.1f%%  primo aperto entro 30 min %4.1f%%  uno dei 3 %4.1f%%".format(
                th, 100.0 * spoke[k] / moments, 100.0 * right[k] / s, 100.0 * rightAny[k] / s))
        }
    }
}
