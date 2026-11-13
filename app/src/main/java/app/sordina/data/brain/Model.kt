package app.sordina.data.brain

import kotlin.math.exp
import kotlin.math.ln

/**
 * Una caratteristica della situazione: un gruppo ("h" ora, "pl" luogo…) e un valore.
 * Il peso serve a spalmare un gesto sulle ore vicine: alle 8:40 conta anche un po' per le 9.
 */
data class Feature(val group: String, val value: String, val weight: Double = 1.0) {
    val key get() = "$group:$value"
}

/** Un gesto ricordato: cosa hai fatto, quando, in che situazione. */
data class Gesture(val time: Long, val item: String, val features: List<Feature>)

/** Perché una cosa è proposta: il gruppo che pesa di più e il suo valore (es. "pl" e il nome del Wi-Fi). */
data class Reason(val group: String, val value: String)

/**
 * Il modello che impara cosa fai: per ogni cosa, quante volte la fai in ogni situazione.
 * I conteggi sbiadiscono col tempo (dopo [halfLifeDays] valgono la metà), così le abitudini nuove
 * prendono il posto delle vecchie. È un Bayes ingenuo smussato: impara dalla prima volta,
 * ma una coincidenza sola non basta a spostare tutto.
 *
 * Nessuna dipendenza da Android: si prova con i test.
 */
class Model(private val halfLifeDays: Double = 21.0, now: Long = System.currentTimeMillis()) {
    /** Tempo di riferimento: i pesi crescono col tempo invece di far sbiadire i vecchi, che è lo stesso ma senza ricalcoli. */
    private val base = now - 150L * DAY

    private val itemMass = HashMap<String, Double>()
    private val itemFeature = HashMap<String, HashMap<String, Double>>()
    private val featureMass = HashMap<String, Double>()
    private val groupMass = HashMap<String, Double>()
    private var total = 0.0
    private val lastTime = HashMap<String, Long>()

    /** Lettere scritte → cosa hai scelto (con peso). */
    private val prefixes = HashMap<String, HashMap<String, Double>>()

    private fun w(t: Long): Double = exp(LN2 * (t - base) / (halfLifeDays * DAY))

    val items: Set<String> get() = itemMass.keys

    fun lastUsed(item: String): Long? = lastTime[item]

    /** Quanto conta adesso questa cosa, rispetto a tutte (0..1). */
    fun share(item: String): Double = if (total <= 0) 0.0 else (itemMass[item] ?: 0.0) / total

    @Synchronized
    fun add(g: Gesture) {
        val wt = w(g.time)
        itemMass.merge(g.item, wt, Double::plus)
        total += wt
        val per = itemFeature.getOrPut(g.item) { HashMap() }
        for (f in g.features) {
            val m = wt * f.weight
            per.merge(f.key, m, Double::plus)
            featureMass.merge(f.key, m, Double::plus)
        }
        // La massa di ogni gruppo, per sapere quanto è comune un valore (con le ore spalmate pesa di più di 1)
        g.features.groupBy { it.group }.forEach { (grp, fs) -> groupMass.merge(grp, wt * fs.sumOf { it.weight }, Double::plus) }
        lastTime.merge(g.item, g.time) { a, b -> maxOf(a, b) }
    }

    @Synchronized
    fun addChoice(time: Long, typed: String, item: String, weight: Double = 1.0) {
        if (typed.isEmpty()) return
        val wt = w(time) * weight
        // Ogni pezzo di quello che hai scritto: la prossima volta basterà la prima lettera
        for (n in 1..minOf(typed.length, 24)) {
            prefixes.getOrPut(typed.take(n)) { HashMap() }.merge(item, wt, Double::plus)
        }
    }

    @Synchronized
    fun clearTyped() = prefixes.clear()

    @Synchronized
    fun forget(item: String) {
        val m = itemMass.remove(item) ?: return
        total -= m
        itemFeature.remove(item)?.forEach { (k, v) -> featureMass.merge(k, -v, Double::plus) }
        lastTime.remove(item)
        prefixes.values.forEach { it.remove(item) }
    }

    /**
     * Quanto è probabile [item] in questa situazione, in logaritmo (più alto = più probabile).
     * Somma la frequenza di base e, per ogni caratteristica, quanto quella cosa la "preferisce" rispetto alla media.
     */
    @Synchronized
    fun score(item: String, situation: List<Feature>): Double {
        val n = itemMass[item] ?: return Double.NEGATIVE_INFINITY
        val k = itemMass.size.coerceAtLeast(1)
        var s = ln((n + PRIOR) / (total + PRIOR * k))
        val per = itemFeature[item].orEmpty()
        for (f in situation) s += GROUP_WEIGHT[f.group].orZero() * lift(n, per, f)
        return s
    }

    /** Il perché più forte: la caratteristica che alza di più questa cosa adesso. */
    @Synchronized
    fun reason(item: String, situation: List<Feature>): Reason? {
        val n = itemMass[item] ?: return null
        val per = itemFeature[item].orEmpty()
        return situation.map { it to GROUP_WEIGHT[it.group].orZero() * lift(n, per, it) }
            .filter { it.second > 0.4 }
            .maxByOrNull { it.second }
            ?.let { Reason(it.first.group, it.first.value) }
    }

    /** log(P(caratteristica | cosa) / P(caratteristica)), smussato e limitato. */
    private fun lift(n: Double, per: Map<String, Double>, f: Feature): Double {
        val g = groupMass[f.group] ?: return 0.0
        if (g <= 0) return 0.0
        // Quanto è comune questo valore in generale (con un pizzico di massa per i valori mai visti)
        val pf = ((featureMass[f.key] ?: 0.0) + 0.5) / (g + 1.0)
        // E per questa cosa: con pochi dati resta vicino alla media
        val nItemGroup = n * (g / total.coerceAtLeast(1e-9))
        val pfi = ((per[f.key] ?: 0.0) + SMOOTH * pf) / (nItemGroup + SMOOTH)
        return ln(pfi / pf).coerceIn(-MAX_LIFT, MAX_LIFT)
    }

    /**
     * Quanto quello che hai scritto porta di solito a [item]: (probabilità 0..1, peso accumulato).
     * Con poche lettere conta la storia; più scrivi, più conta il testo.
     */
    @Synchronized
    fun typed(typed: String, item: String, now: Long = System.currentTimeMillis()): Pair<Double, Double> {
        val m = prefixes[typed] ?: return 0.0 to 0.0
        val c = m[item] ?: return 0.0 to 0.0
        val unit = w(now)
        // In "volte di adesso": una scelta di tre settimane fa vale mezza
        return c / (m.values.sum() + 0.5 * unit) to c / unit
    }

    /** Le cose che di solito scegli scrivendo queste lettere, la più probabile per prima. */
    @Synchronized
    fun typedItems(typed: String, min: Double = 0.3, now: Long = System.currentTimeMillis()): List<String> {
        val m = prefixes[typed] ?: return emptyList()
        val sum = m.values.sum() + 0.5 * w(now)
        return m.entries.filter { it.value / sum >= min }.sortedByDescending { it.value }.map { it.key }
    }

    /** Volte (sbiadite) che hai fatto questa cosa: per decidere quando fidarsi. */
    fun times(item: String, now: Long = System.currentTimeMillis()): Double = (itemMass[item] ?: 0.0) / w(now)

    private fun Double?.orZero() = this ?: 0.0

    companion object {
        const val DAY = 86_400_000L
        private val LN2 = ln(2.0)
        private const val PRIOR = 0.5
        private const val SMOOTH = 3.0
        private const val MAX_LIFT = 2.5

        /** Quanto conta ogni tipo di situazione. L'ora e il luogo dicono molto; il giorno della settimana meno. */
        val GROUP_WEIGHT = mapOf(
            "h" to 1.0, "dw" to 0.35, "we" to 0.6, "pl" to 1.0, "car" to 1.2, "hs" to 0.8,
            "chg" to 0.4, "prev" to 0.7, "fresh" to 0.5,
        )
    }
}
