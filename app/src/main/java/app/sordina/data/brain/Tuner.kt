package app.sordina.data.brain

/**
 * Il cervello si regola da solo: rigioca la tua storia e prova pesi diversi per i segnali del momento
 * (usata da poco, notifica appena arrivata, proposta ignorata), tenendo quelli che indovinano di più.
 * È quello che si fa a mano su una memoria vera, fatto sul telefono, per te.
 *
 * Leggero: una volta al giorno, solo in carica, poche prove (un parametro alla volta) e solo con abbastanza storia.
 * Nessuna dipendenza da Android: si prova con i test.
 */
object Tuner {
    /** Un pezzo di storia: G gesto, M cercata, N notifica, I proposte viste (item = elenco separato da virgole). */
    class Event(val kind: Char, val time: Long, val item: String, situation: Situation?) {
        val learn = situation?.features(true).orEmpty()
        val ask = situation?.features(false).orEmpty()
    }

    data class Result(val tune: Tune, val score: Double, val base: Double, val tested: Int)

    private val STEPS = mapOf(
        "recent" to listOf(0.0, 1.5, 3.0, 4.5),
        "notify" to listOf(0.0, 1.5, 3.0),
        "ignore" to listOf(0.0, 1.5, 3.0),
    )

    /** Quanto indovina [tune] sull'ultimo 40% della storia: prima proposta giusta + metà del peso per "fra le sei". */
    fun evaluate(events: List<Event>, tune: Tune, missWeight: Double = 1.0): Pair<Double, Int> {
        val gestures = events.filter { it.kind == 'G' }
        if (gestures.size < 50) return 0.0 to 0
        val testFrom = gestures[(gestures.size * 0.6).toInt()].time
        val m = Model(now = events.last().time)
        m.tune = tune
        var n = 0; var top1 = 0; var top6 = 0
        for (e in events) {
            when (e.kind) {
                'N' -> { m.notified(e.item, e.time); continue }
                'I' -> { m.shown(e.time, e.item.split(',')); continue }
                'M' -> { m.add(Gesture(e.time, e.item, e.learn, missWeight)); continue }
            }
            if (e.time >= testFrom && e.item in m.items) {
                // Le sei migliori senza ordinare tutto: si tiene solo quanto serve
                val target = m.predict(e.item, e.ask, e.time)
                var better = 0
                for (other in m.items) if (other != e.item && m.predict(other, e.ask, e.time) > target) { better++; if (better >= 6) break }
                n++
                if (better == 0) top1++
                if (better < 6) top6++
            }
            m.add(Gesture(e.time, e.item, e.learn))
        }
        return (if (n == 0) 0.0 else (top1 + 0.5 * top6).toDouble() / n) to n
    }

    /**
     * Prova un parametro alla volta (due giri), partendo da [start]. Cambia i pesi solo se il guadagno è vero
     * (almeno un punto), per non inseguire il rumore. Null se la storia è troppo corta.
     */
    fun tune(events: List<Event>, start: Tune, missWeight: Double = 1.0): Result? {
        val (base, n) = evaluate(events, start, missWeight)
        if (n < 200) return null
        var best = start; var bestScore = base
        repeat(2) {
            for ((name, values) in STEPS) for (v in values) {
                val t = when (name) { "recent" -> best.copy(recent = v); "notify" -> best.copy(notify = v); else -> best.copy(ignore = v) }
                if (t == best) continue
                val s = evaluate(events, t, missWeight).first
                if (s > bestScore) { best = t; bestScore = s }
            }
        }
        return Result(if (bestScore - base >= 0.01) best else start, bestScore, base, n)
    }
}
