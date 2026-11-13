package app.sordina.data

import android.content.Context
import org.json.JSONObject
import kotlin.math.exp
import kotlin.math.ln

/**
 * Le schede imparano da quello che ne fai. Per ogni genere di scheda (il tipo e l'app da cui nasce:
 * "pagamenti di Revolut", "pacchi di Amazon", "di solito in auto: Spotify") ricorda quante volte l'hai usata
 * e quante l'hai scartata. Quelle che scarti sempre spariscono; quelle che usi salgono in cima.
 * I ricordi sbiadiscono (un mese per dimezzarsi): se cambi abitudini, le schede tornano.
 */
class CardSense(context: Context) {
    private val sp = context.getSharedPreferences("card_sense", Context.MODE_PRIVATE)
    private val mem = HashMap<String, DoubleArray>() // genere → [usate, scartate, ultimo aggiornamento]

    init {
        runCatching {
            val o = JSONObject(sp.getString("m", "{}") ?: "{}")
            o.keys().forEach { k -> o.optJSONArray(k)?.let { a -> mem[k] = doubleArrayOf(a.optDouble(0), a.optDouble(1), a.optDouble(2)) } }
        }
    }

    @Synchronized fun used(c: Card) = bump(genre(c), 0)
    @Synchronized fun dropped(c: Card) = bump(genre(c), 1)

    /** Da non mostrare più: scartata tante volte e quasi mai usata. Le schede che servono sempre non si spengono. */
    @Synchronized
    fun muted(c: Card, now: Long = System.currentTimeMillis()): Boolean {
        if (c.kind in ALWAYS) return false
        val (u, d) = faded(genre(c), now)
        return d >= 3.0 && u * 3 < d
    }

    /** Quanto è utile questo genere di scheda per te, da -1 (la scarti) a +1 (la usi), 0 se non si sa. */
    @Synchronized
    fun usefulness(c: Card, now: Long = System.currentTimeMillis()): Double {
        val (u, d) = faded(genre(c), now)
        return (u - d) / (u + d + 2.0)
    }

    /**
     * L'ordine della pila: prima quello che non può aspettare (Sordina ferma, codici, domande, chiamate),
     * poi quello che sta per succedere, poi quello che di solito usi. Più basso = più in alto.
     */
    fun rank(c: Card, now: Long): Double {
        if (c.kind.priority <= CardKind.CALL.priority) return c.kind.priority.toDouble() - 10
        var r = c.kind.priority.toDouble()
        // Sta per cominciare o finire: sale
        val soon = c.startsAt ?: c.expiresAt.takeIf { it != Long.MAX_VALUE && c.kind != CardKind.MUSIC }
        if (soon != null) {
            val min = (soon - now) / 60_000.0
            if (min in -5.0..20.0) r -= 6.0 else if (min in 20.0..60.0) r -= 2.5
        }
        // Quello che usi sale, quello che scarti scende
        r -= 4.0 * usefulness(c, now)
        // Una scheda vecchia di ore dice meno di una appena arrivata
        val ageH = (now - c.time) / 3_600_000.0
        if (c.startsAt == null && ageH > 3) r += minOf(3.0, ageH / 4)
        return r
    }

    /** Il genere: il tipo e l'app (dalla chiave della notifica, "0|pacchetto|…"); le abitudini una per una. */
    private fun genre(c: Card): String {
        if (c.kind == CardKind.HABIT) return c.key
        val pkg = (c.sources.firstOrNull() ?: c.key).split('|').getOrNull(1)
        return if (pkg != null) "${c.kind}:$pkg" else c.kind.name
    }

    private fun faded(g: String, now: Long): Pair<Double, Double> {
        val a = mem[g] ?: return 0.0 to 0.0
        val k = decay(now - a[2].toLong())
        return a[0] * k to a[1] * k
    }

    private fun bump(g: String, i: Int) {
        val now = System.currentTimeMillis()
        val (u, d) = faded(g, now)
        val a = doubleArrayOf(u, d, now.toDouble())
        a[i] += 1.0
        mem[g] = a
        // Non più di 300 generi: si dimenticano i più vecchi
        if (mem.size > 300) mem.entries.minByOrNull { it.value[2] }?.let { mem.remove(it.key) }
        val o = JSONObject()
        mem.forEach { (k, v) -> o.put(k, org.json.JSONArray().put(v[0]).put(v[1]).put(v[2].toLong())) }
        sp.edit().putString("m", o.toString()).apply()
    }

    private fun decay(ms: Long): Double = exp(-ln(2.0) * ms.coerceAtLeast(0) / (30 * 86_400_000.0))

    companion object {
        /** Mai spente: avvisano di cose che non puoi perdere. */
        val ALWAYS = setOf(CardKind.HEALTH, CardKind.OTP, CardKind.ASK, CardKind.CALL, CardKind.TIMER, CardKind.EVENT, CardKind.RIDE, CardKind.MUSIC)
    }
}
