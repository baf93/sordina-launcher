package app.sordina.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Come si ripete un impegno, nella parte di RRULE che il calendario di Sordina sa cambiare: ogni quanti giorni,
 * settimane, mesi o anni; per le settimane, in quali giorni; fino a quando o quante volte. Tutto il resto della regola
 * (es. "il secondo martedì": BYDAY=2TU, BYSETPOS…) resta in [extra] e si riscrive com'era.
 */
data class Repeat(
    val freq: Freq,
    val interval: Int = 1,
    /** Solo per le settimane: i giorni scelti (vuoto: quello dell'inizio) */
    val days: Set<DayOfWeek> = emptySet(),
    val until: LocalDate? = null,
    val count: Int? = null,
    /** Le parti della regola che qui non si toccano, così come sono */
    val extra: List<String> = emptyList(),
) {
    enum class Freq { DAILY, WEEKLY, MONTHLY, YEARLY }

    /** Lo dice il calendario di Sordina per intero (niente parti che non sa mostrare) */
    val simple: Boolean get() = extra.isEmpty()

    /**
     * La regola da salvare. UNTIL ha lo stesso tipo dell'inizio: la data per il tutto il giorno, se no la fine di quel
     * giorno qui, in UTC.
     */
    fun rule(allDay: Boolean, zone: ZoneId = ZoneId.systemDefault()): String {
        val p = ArrayList<String>()
        p += "FREQ=" + freq.name
        if (interval > 1) p += "INTERVAL=$interval"
        if (freq == Freq.WEEKLY && days.isNotEmpty()) p += "BYDAY=" + DayOfWeek.entries.filter { it in days }.joinToString(",") { CODES.getValue(it) }
        p += extra
        when {
            count != null -> p += "COUNT=$count"
            until != null -> p += "UNTIL=" + if (allDay) BASIC.format(until)
                else STAMP.format(until.plusDays(1).atStartOfDay(zone).minusSeconds(1).withZoneSameInstant(ZoneOffset.UTC))
        }
        return p.joinToString(";")
    }

    companion object {
        private val CODES = mapOf(
            DayOfWeek.MONDAY to "MO", DayOfWeek.TUESDAY to "TU", DayOfWeek.WEDNESDAY to "WE", DayOfWeek.THURSDAY to "TH",
            DayOfWeek.FRIDAY to "FR", DayOfWeek.SATURDAY to "SA", DayOfWeek.SUNDAY to "SU",
        )
        private val DAYS = CODES.entries.associate { (k, v) -> v to k }
        private val BASIC = DateTimeFormatter.BASIC_ISO_DATE
        private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

        /** La regola com'è scritta (con o senza "RRULE:"); null se non si ripete o non si capisce la frequenza */
        fun parse(rule: String?, zone: ZoneId = ZoneId.systemDefault()): Repeat? {
            val r = rule?.trim()?.removePrefix("RRULE:")?.takeIf { it.isNotBlank() } ?: return null
            var freq: Freq? = null; var interval = 1; var days = emptySet<DayOfWeek>(); var until: LocalDate? = null; var count: Int? = null
            val extra = ArrayList<String>()
            for (part in r.split(';').map { it.trim() }.filter { it.isNotEmpty() }) {
                val k = part.substringBefore('=').uppercase(); val v = part.substringAfter('=', "")
                when (k) {
                    "FREQ" -> freq = runCatching { Freq.valueOf(v.uppercase()) }.getOrNull() ?: return null
                    "INTERVAL" -> interval = v.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    // Giorni semplici ("MO,TH"): si cambiano qui; con un numero davanti ("2TU") restano com'erano
                    "BYDAY" -> {
                        val d = v.split(',').map { it.trim().uppercase() }
                        if (d.all { it in DAYS }) days = d.map { DAYS.getValue(it) }.toSet() else extra += part
                    }
                    "COUNT" -> count = v.toIntOrNull()
                    "UNTIL" -> until = untilDate(v, zone)
                    else -> extra += part
                }
            }
            val f = freq ?: return null
            // BYDAY fuori dalle settimane ("ogni giorno feriale" con FREQ=DAILY): si tiene così com'è
            if (f != Freq.WEEKLY && days.isNotEmpty()) { extra += "BYDAY=" + DayOfWeek.entries.filter { it in days }.joinToString(",") { CODES.getValue(it) }; days = emptySet() }
            return Repeat(f, interval, days, until, count, extra)
        }

        /** UNTIL come giorno di qui: "20261203" o "20261203T225959Z" (o con un fuso locale, senza Z) */
        private fun untilDate(v: String, zone: ZoneId): LocalDate? = runCatching {
            when {
                v.length == 8 -> LocalDate.parse(v, BASIC)
                v.endsWith("Z") -> Instant.from(STAMP.withZone(ZoneOffset.UTC).parse(v)).atZone(zone).toLocalDate()
                else -> LocalDate.parse(v.take(8), BASIC)
            }
        }.getOrNull()
    }
}
