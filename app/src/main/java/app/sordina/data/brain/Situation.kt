package app.sordina.data.brain

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/**
 * Il momento, visto dal telefono. Tutto quello che il modello usa per capire "adesso".
 * null = non si sa (Sordina non stava ascoltando): quella caratteristica non conta.
 */
data class Situation(
    val time: Long,
    /** Il Wi-Fi a cui sei collegato ("" = nessuno) */
    val wifi: String? = null,
    val car: Boolean? = null,
    val headset: Boolean? = null,
    val charging: Boolean? = null,
    /** L'app aperta poco prima, negli ultimi 10 minuti ("" = nessuna) */
    val previous: String? = null,
    /** Hai appena sbloccato il telefono (2 minuti) */
    val fresh: Boolean? = null,
    val zone: ZoneId = ZoneId.systemDefault(),
    /** Il dispositivo Bluetooth audio collegato (auto, cuffie, cassa): "" = nessuno */
    val device: String? = null,
) {
    private val local get() = Instant.ofEpochMilli(time).atZone(zone)

    /**
     * Le caratteristiche. Per imparare ([learning]) l'ora si spalma sulle ore vicine,
     * così un'abitudine delle 8:50 vale anche per le 9; per chiedere si usa l'ora esatta.
     */
    fun features(learning: Boolean): List<Feature> {
        val t = local
        val out = ArrayList<Feature>(12)
        // Ora: mezz'ore, per distinguere 8:00 da 8:30
        val slot = t.hour * 2 + t.minute / 30
        if (learning) {
            out += Feature("h", "$slot", 1.0)
            out += Feature("h", "${(slot + 1) % 48}", 0.6)
            out += Feature("h", "${(slot + 47) % 48}", 0.6)
            out += Feature("h", "${(slot + 2) % 48}", 0.25)
            out += Feature("h", "${(slot + 46) % 48}", 0.25)
        } else out += Feature("h", "$slot")
        out += Feature("we", if (t.dayOfWeek == DayOfWeek.SATURDAY || t.dayOfWeek == DayOfWeek.SUNDAY) "1" else "0")
        wifi?.let { out += Feature("pl", it.ifEmpty { "-" }) }
        car?.let { out += Feature("car", if (it) "1" else "0") }
        headset?.let { out += Feature("hs", if (it) "1" else "0") }
        charging?.let { out += Feature("chg", if (it) "1" else "0") }
        previous?.let { out += Feature("prev", it.ifEmpty { "-" }) }
        fresh?.let { out += Feature("fresh", if (it) "1" else "0") }
        device?.let { out += Feature("bt", it.ifEmpty { "-" }) }
        return out
    }
}
