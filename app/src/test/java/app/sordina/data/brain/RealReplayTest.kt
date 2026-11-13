package app.sordina.data.brain

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Rigioca una memoria vera del telefono (brain.tsv, mai nel repository) con lo stesso [Tuner] che gira sul telefono:
 * quanto indovinano i pesi di partenza, quali sceglie la regolazione e quanto tempo ci mette.
 * Gira solo con BRAIN_TSV=/percorso/brain.tsv.
 */
class RealReplayTest {
    private fun events(): List<Tuner.Event> {
        val path = System.getenv("BRAIN_TSV") ?: ""
        assumeTrue(path.isNotEmpty() && File(path).exists())
        fun u(v: String) = if (v == "?") null else v
        fun b(v: String) = when (v) { "1" -> true; "0" -> false; else -> null }
        return File(path).readLines().mapNotNull { l ->
            val p = l.split('\t')
            val t = p.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
            when (p[0]) {
                "G", "M" -> if (p.size >= 9) Tuner.Event(p[0][0], t, p[2],
                    Situation(t, u(p[3]), b(p[4]), b(p[5]), b(p[6]), u(p[7]), b(p[8]), device = p.getOrNull(9)?.let(::u))) else null
                "N", "I" -> if (p.size >= 3) Tuner.Event(p[0][0], t, p[2], null) else null
                else -> null
            }
        }.sortedBy { it.time }
    }

    @Test fun replayAndTune() {
        val all = events()
        println("Eventi: ${all.size} (gesti ${all.count { it.kind == 'G' }}, cercate ${all.count { it.kind == 'M' }}, notifiche ${all.count { it.kind == 'N' }})")
        println("Solo abitudini:     punteggio %.3f".format(Tuner.evaluate(all, Tune(0.0, 0.0, 0.0)).first))
        println("Pesi di partenza:   punteggio %.3f".format(Tuner.evaluate(all, Tune()).first))
        val t0 = System.nanoTime()
        val r = Tuner.tune(all, Tune())
        println("Regolati: $r in ${(System.nanoTime() - t0) / 1_000_000} ms")
    }
}
