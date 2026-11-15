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

/** Quanto contano i segnali del momento. I valori di partenza sono quelli misurati su una memoria vera. */
data class Tune(
    val recent: Double = Model.RECENT_GAIN,
    val notify: Double = Model.NOTIFY_GAIN,
    val ignore: Double = Model.IGNORE_GAIN,
)

/** Un gesto ricordato: cosa hai fatto, quando, in che situazione. */
/** [weight]: quanto conta. Una cosa cercata perché Sordina non la proponeva conta di più (era da proporre). */
data class Gesture(val time: Long, val item: String, val features: List<Feature>, val weight: Double = 1.0)

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
    /** I pesi dei segnali di adesso: si regolano da soli sulla tua storia (vedi [Tuner]). */
    @Volatile var tune = Tune()

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
        val wt = w(g.time) * g.weight
        uses.getOrPut(g.item) { ArrayList() }.let { u ->
            // In ordine di tempo (quasi sempre arriva in coda); al massimo gli ultimi 300
            var i = u.size
            while (i > 0 && u[i - 1] > g.time) i--
            u.add(i, g.time)
            if (u.size > 300) u.removeAt(0)
        }
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
        uses.remove(item); notifiedAt.remove(item); seen.remove(item); refusals.remove(item)
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

    /**
     * Quanto conta l'averla usata da poco: un'app di dieci minuti fa è probabile che la riapri,
     * una di ieri non dice niente in più delle abitudini. In logaritmo, da sommare a [score].
     */
    @Synchronized
    fun recency(item: String, now: Long): Double {
        val t = lastTime[item] ?: return 0.0
        val dt = (now - t).coerceAtLeast(0L) / 60_000.0
        return tune.recent * exp(-dt / RECENT_MINUTES)
    }

    /** La previsione completa per adesso: abitudini e situazione, più quello che hai usato da poco. */
    fun predict(item: String, situation: List<Feature>, now: Long): Double =
        score(item, situation) + recency(item, now) + notifiedBoost(item, now) + ignoredPenalty(item, now)

    // Notifiche: un'app che ti ha appena scritto è probabile che la apri

    private val notifiedAt = HashMap<String, Long>()

    @Synchronized
    fun notified(item: String, t: Long) { notifiedAt[item] = t }

    /** Ti ha mandato una notifica da poco e non l'hai ancora aperta. */
    @Synchronized
    fun notifiedBoost(item: String, now: Long): Double {
        val t = notifiedAt[item] ?: return 0.0
        if ((lastTime[item] ?: 0L) >= t || now < t) return 0.0
        return tune.notify * exp(-(now - t) / 60_000.0 / NOTIFY_MINUTES)
    }

    // Proposte viste e lasciate lì

    /** Quando hai usato ogni cosa (per sapere se una proposta vista è stata presa). */
    private val uses = HashMap<String, ArrayList<Long>>()
    /** Le occhiate non ancora decise (si decide dopo 10 minuti: l'hai presa o no). */
    private val pending = ArrayDeque<Pair<Long, List<String>>>()
    /** L'ultima volta che si è vista ogni schermata (le proposte della ricerca, il fumetto): la stessa rivista entro 10 minuti è la stessa occhiata */
    private val lastShown = HashMap<List<String>, Long>()
    /** Per ogni cosa: [viste, lasciate lì]. Si contano una volta sola, poi basta guardarle. */
    private val seen = HashMap<String, DoubleArray>()

    /** Le proposte che hai visto in Casa a [t]. */
    @Synchronized
    fun shown(t: Long, items: List<String>) {
        // Lo stesso schermo rivisto entro 10 minuti è la stessa occhiata
        if (t - (lastShown[items] ?: Long.MIN_VALUE / 2) in 0 until 10 * 60_000L) return
        lastShown[items] = t
        if (lastShown.size > 30) lastShown.entries.removeAll { t - it.value > 10 * 60_000L }
        pending.addLast(t to items)
        if (pending.size > 500) pending.removeFirst()
    }

    /** Decide le occhiate di più di 10 minuti fa: per ogni proposta, l'hai usata subito dopo o no. */
    private fun resolve(now: Long) {
        while (pending.isNotEmpty() && pending.first().first <= now - 10 * 60_000) {
            val (t, items) = pending.removeFirst()
            for (item in items) {
                val c = seen.getOrPut(item) { DoubleArray(2) }
                // Conta di più il presente: oltre 40 occhiate, le vecchie valgono la metà
                if (c[0] > 40) { c[0] *= 0.5; c[1] *= 0.5 }
                c[0] += 1.0
                if (!usedBetween(item, t, t + 10 * 60_000)) c[1] += 1.0
            }
        }
    }

    /** Usata fra [from] e [to]? Ricerca binaria sugli usi (in ordine di tempo). */
    private fun usedBetween(item: String, from: Long, to: Long): Boolean {
        val u = uses[item] ?: return false
        var lo = 0; var hi = u.size
        while (lo < hi) { val mid = (lo + hi) ushr 1; if (u[mid] < from) lo = mid + 1 else hi = mid }
        return lo < u.size && u[lo] <= to
    }

    /**
     * Una proposta che vedi spesso e non prendi mai sta occupando il posto di qualcos'altro: scende.
     * Solo dopo almeno 5 occhiate; con una frazione, così le app che usi spesso non ne soffrono.
     */
    @Synchronized
    fun ignoredPenalty(item: String, now: Long): Double {
        resolve(now)
        val c = seen[item] ?: return 0.0
        if (c[0] < 5) return 0.0
        return -tune.ignore * c[1] / (c[0] + 2.0)
    }

    // Scorsa via dal fumetto: un "no" detto apposta

    /** Per ogni cosa: quando l'hai scorsa via e com'era la situazione (le ultime 30). */
    private val refusals = HashMap<String, ArrayList<Pair<Long, Set<String>>>>()

    @Synchronized
    fun refused(item: String, t: Long, situation: List<Feature>) {
        val l = refusals.getOrPut(item) { ArrayList() }
        l += t to situation.mapTo(HashSet()) { it.key }
        if (l.size > 30) l.removeAt(0)
    }

    /**
     * Scorsa via in una situazione come questa: qui scende, altrove no (in auto sì, a casa la sera no).
     * Quanto somiglia la situazione pesa come i segnali del modello; il "no" sbiadisce come le abitudini.
     */
    @Synchronized
    fun refusedPenalty(item: String, situation: List<Feature>, now: Long): Double {
        val l = refusals[item] ?: return 0.0
        val all = situation.sumOf { GROUP_WEIGHT[it.group].orZero() }
        if (all <= 0) return 0.0
        var sum = 0.0
        for ((t, keys) in l) {
            val sim = situation.filter { it.key in keys }.sumOf { GROUP_WEIGHT[it.group].orZero() } / all
            sum += sim * sim * exp(-LN2 * (now - t).coerceAtLeast(0L) / (halfLifeDays * DAY))
        }
        return -(REFUSE_GAIN * sum).coerceAtMost(MAX_REFUSE)
    }

    /**
     * L'abitudine e basta: la situazione, le proposte lasciate lì e quelle scorse via.
     * Senza "usata da poco" e senza notifiche: è quello che serve al fumetto "di solito".
     */
    fun habit(item: String, situation: List<Feature>, now: Long): Double =
        score(item, situation) + ignoredPenalty(item, now) + refusedPenalty(item, situation, now)

    /** Volte (sbiadite) che hai fatto questa cosa: per decidere quando fidarsi. */
    fun times(item: String, now: Long = System.currentTimeMillis()): Double = (itemMass[item] ?: 0.0) / w(now)

    private fun Double?.orZero() = this ?: 0.0

    companion object {
        const val DAY = 86_400_000L
        private val LN2 = ln(2.0)
        private const val PRIOR = 0.5
        private const val SMOOTH = 3.0
        private const val MAX_LIFT = 2.5
        /** Misurati rigiocando una memoria vera: +5 punti sulla prima proposta giusta. */
        const val RECENT_GAIN = 3.0
        const val RECENT_MINUTES = 15.0
        const val NOTIFY_GAIN = 1.5
        const val NOTIFY_MINUTES = 40.0
        const val IGNORE_GAIN = 1.5
        /** Un "no" nella stessa identica situazione: circa tre volte meno probabile. Al massimo [MAX_REFUSE]. */
        const val REFUSE_GAIN = 1.2
        const val MAX_REFUSE = 4.0

        /** Quanto conta ogni tipo di situazione. L'ora e il luogo dicono molto; il giorno della settimana meno. */
        val GROUP_WEIGHT = mapOf(
            "h" to 1.0, "dw" to 0.35, "we" to 0.6, "pl" to 1.0, "car" to 1.2, "hs" to 0.8,
            "chg" to 0.4, "prev" to 0.7, "fresh" to 0.5, "bt" to 1.0,
        )
    }
}
