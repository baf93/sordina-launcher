package app.sordina.data

import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/** Una data/ora capita da una frase. */
data class When(
    val start: LocalDateTime,
    val allDay: Boolean,
    /** "alle 17" era già passato: si intende domani */
    val movedToTomorrow: Boolean,
)

/**
 * Date e ore in italiano, con regole scritte apposta: "alle 17", "domani alle 17:30", "giovedì",
 * "tra mezz'ora", "tra 10 minuti", "stasera", "il 15", "15/11", "alle 9 e mezza".
 * Restituisce anche il testo rimasto, che diventa il titolo dell'impegno.
 */
object DateParser {

    data class Result(val whenAt: When?, val rest: String)

    private val NUMBERS = mapOf(
        "un" to 1, "uno" to 1, "una" to 1, "due" to 2, "tre" to 3, "quattro" to 4, "cinque" to 5, "sei" to 6,
        "sette" to 7, "otto" to 8, "nove" to 9, "dieci" to 10, "undici" to 11, "dodici" to 12, "tredici" to 13,
        "quattordici" to 14, "quindici" to 15, "venti" to 20, "venticinque" to 25, "trenta" to 30,
        "quaranta" to 40, "quarantacinque" to 45, "cinquanta" to 50, "sessanta" to 60, "novanta" to 90,
    )
    private val WEEKDAYS = mapOf(
        "lunedi" to DayOfWeek.MONDAY, "martedi" to DayOfWeek.TUESDAY, "mercoledi" to DayOfWeek.WEDNESDAY,
        "giovedi" to DayOfWeek.THURSDAY, "venerdi" to DayOfWeek.FRIDAY, "sabato" to DayOfWeek.SATURDAY,
        "domenica" to DayOfWeek.SUNDAY,
    )
    private val MONTHS = listOf(
        "gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno", "luglio", "agosto", "settembre",
        "ottobre", "novembre", "dicembre",
    )

    fun clean(s: String): String =
        Regex("\\p{Mn}+").replace(Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD), "")
            .replace('’', ' ').replace('\'', ' ')
            .replace(Regex("[^\\p{L}\\p{N}:./ %]"), " ")
            .replace(Regex("\\s+"), " ").trim()

    fun number(w: String): Int? = w.toIntOrNull() ?: NUMBERS[w]

    private const val NUM = "(\\d+|un|uno|una|due|tre|quattro|cinque|sei|sette|otto|nove|dieci|undici|dodici|tredici|quattordici|quindici|venti|venticinque|trenta|quaranta|quarantacinque|cinquanta|sessanta|novanta)"

    /** Durata in secondi: "10 minuti", "mezz ora", "1 ora e 20", "90 secondi". */
    fun duration(text: String): Pair<Int, String>? {
        var s = " " + clean(text) + " "
        var total = 0
        var found = false
        if (Regex(" (un )?mezz ?ora ").containsMatchIn(s)) {
            total += 1800; found = true; s = s.replace(Regex(" (un )?mezz ?ora "), " ")
        }
        if (Regex(" (un )?quarto d ?ora ").containsMatchIn(s)) {
            total += 900; found = true; s = s.replace(Regex(" (un )?quarto d ?ora "), " ")
        }
        val r = Regex(" $NUM ?(ore|ora|h|minuti|minuto|min|m|secondi|secondo|sec|s)(?= )")
        for (m in r.findAll(s).toList()) {
            val n = number(m.groupValues[1]) ?: continue
            total += when (m.groupValues[2]) {
                "ore", "ora", "h" -> n * 3600
                "minuti", "minuto", "min", "m" -> n * 60
                else -> n
            }
            found = true
        }
        s = r.replace(s, " ")
        // "1 ora e 20": i minuti dopo la "e"
        Regex(" e $NUM ").find(s)?.let { m -> if (found && total % 3600 == 0) { total += (number(m.groupValues[1]) ?: 0) * 60; s = s.replace(m.value, " ") } }
        if (!found) return null
        val rest = s.replace(Regex(" (di|da|per|un|una|il|lo|la) (?=\\s*$)"), " ").replace(Regex("\\s+"), " ").trim()
        return total to rest
    }

    fun parse(text: String, now: LocalDateTime = LocalDateTime.now(), eveningBias: Boolean = true): Result {
        var s = " " + clean(text) + " "
        var date: LocalDate? = null
        var time: LocalTime? = null
        var relative: LocalDateTime? = null
        var dayHint: Int? = null // ora di default per "stasera", "stamattina"
        fun cut(r: Regex): MatchResult? {
            val m = r.find(s) ?: return null
            s = s.replaceRange(m.range, " ")
            return m
        }

        // Relativo: "tra mezz ora", "tra 10 minuti", "fra 2 ore"
        cut(Regex(" (tra|fra) (un )?mezz ?ora "))?.let { relative = now.plusMinutes(30) }
        if (relative == null) cut(Regex(" (tra|fra) $NUM (minuti|minuto|min|ore|ora|giorni|giorno|settimane|settimana) "))?.let { m ->
            val n = number(m.groupValues[2])!!.toLong()
            relative = when (m.groupValues[3]) {
                "minuti", "minuto", "min" -> now.plusMinutes(n)
                "ore", "ora" -> now.plusHours(n)
                "giorni", "giorno" -> now.plusDays(n).withHour(9).withMinute(0)
                else -> now.plusWeeks(n).withHour(9).withMinute(0)
            }
        }

        // Giorno
        cut(Regex(" dopodomani "))?.let { date = now.toLocalDate().plusDays(2) }
        cut(Regex(" domani (mattina|sera|pomeriggio)? ?"))?.let { m ->
            date = now.toLocalDate().plusDays(1)
            dayHint = when (m.groupValues[1]) { "mattina" -> 9; "pomeriggio" -> 15; "sera" -> 20; else -> null }
        }
        cut(Regex(" (oggi|stamattina|stamani|stasera|stanotte|oggi pomeriggio) "))?.let { m ->
            date = now.toLocalDate()
            dayHint = when (m.groupValues[1]) { "stamattina", "stamani" -> 9; "stasera" -> 20; "stanotte" -> 23; "oggi pomeriggio" -> 15; else -> null }
        }
        cut(Regex(" (lunedi|martedi|mercoledi|giovedi|venerdi|sabato|domenica)( prossimo| prossima)? "))?.let { m ->
            val dow = WEEKDAYS[m.groupValues[1]]!!
            var d = now.toLocalDate().with(TemporalAdjusters.next(dow))
            if (m.groupValues[2].isNotBlank() && d.minusDays(7).isAfter(now.toLocalDate())) d = d.plusDays(7)
            date = d
        }
        cut(Regex(" (il )?(\\d{1,2})/(\\d{1,2})(/(\\d{2,4}))? "))?.let { m ->
            date = runCatching {
                val y = m.groupValues[5].toIntOrNull()?.let { if (it < 100) 2000 + it else it } ?: now.year
                val d = LocalDate.of(y, m.groupValues[3].toInt(), m.groupValues[2].toInt())
                if (m.groupValues[5].isEmpty() && d.isBefore(now.toLocalDate())) d.plusYears(1) else d
            }.getOrNull()
        }
        if (date == null) cut(Regex(" (il |l )?(\\d{1,2}|primo) (${MONTHS.joinToString("|")}) "))?.let { m ->
            val day = if (m.groupValues[2] == "primo") 1 else m.groupValues[2].toInt()
            val month = MONTHS.indexOf(m.groupValues[3]) + 1
            date = runCatching {
                val d = LocalDate.of(now.year, month, day)
                if (d.isBefore(now.toLocalDate())) d.plusYears(1) else d
            }.getOrNull()
        }
        if (date == null) cut(Regex(" il (\\d{1,2}) "))?.let { m ->
            val day = m.groupValues[1].toInt()
            date = runCatching {
                var d = now.toLocalDate().withDayOfMonth(day)
                if (d.isBefore(now.toLocalDate())) d = d.plusMonths(1)
                d
            }.getOrNull()
        }

        // Ora
        cut(Regex(" a mezzogiorno "))?.let { time = LocalTime.NOON }
        cut(Regex(" a mezzanotte "))?.let { time = LocalTime.MIDNIGHT }
        if (time == null) cut(Regex(" (alle|all|per le|verso le|dalle|ore|h) ?(\\d{1,2})(?:[:.](\\d{2}))?( e mezza| e mezzo| e un quarto| meno un quarto)?( di sera| del pomeriggio| di pomeriggio| di mattina| del mattino| di notte)? "))?.let { m ->
            time = buildTime(m.groupValues[2].toInt(), m.groupValues[3], m.groupValues[4], m.groupValues[5], eveningBias)
        }
        if (time == null) cut(Regex(" (all una|alle una)( e mezza)? "))?.let { m ->
            time = buildTime(1, "", m.groupValues[2], "", eveningBias)
        }
        if (time == null) cut(Regex(" (\\d{1,2})[:.](\\d{2}) "))?.let { m ->
            time = buildTime(m.groupValues[1].toInt(), m.groupValues[2], "", "", false)
        }

        val rest = s.replace(Regex("\\s+"), " ").trim()
        relative?.let { return Result(When(it, false, false), rest) }
        if (date == null && time == null && dayHint == null) return Result(null, rest)
        if (time == null && dayHint != null) time = LocalTime.of(dayHint!!, 0)
        // "stasera alle 9" = le 21
        if (time != null && dayHint != null && dayHint!! in 15..22 && time!!.hour < 12) time = time!!.plusHours(12)
        if (time == null) return Result(When(date!!.atStartOfDay(), true, false), rest)
        var start = (date ?: now.toLocalDate()).atTime(time)
        var moved = false
        if (date == null && !start.isAfter(now)) { start = start.plusDays(1); moved = true }
        return Result(When(start, false, moved), rest)
    }

    private fun buildTime(h0: Int, min: String, frac: String, part: String, eveningBias: Boolean): LocalTime? {
        var h = h0
        var m = min.toIntOrNull() ?: 0
        when (frac.trim()) {
            "e mezza", "e mezzo" -> m = 30
            "e un quarto" -> m = 15
            "meno un quarto" -> { h -= 1; m = 45 }
        }
        val p = part.trim()
        if (p.endsWith("sera") || p.endsWith("pomeriggio")) { if (h < 12) h += 12 }
        else if (p.endsWith("notte")) { if (h == 12) h = 0 }
        else if (p.isEmpty() && eveningBias && h in 1..7) h += 12 // "alle 5" per un impegno = le 17
        if (h !in 0..23 || m !in 0..59) return null
        return LocalTime.of(h, m)
    }

    /** Descrizione breve per la scheda: "domani alle 17:00", "giovedì 14 alle 9:30". */
    fun describe(w: When, now: LocalDateTime = LocalDateTime.now()): String {
        val d = w.start.toLocalDate()
        val today = now.toLocalDate()
        val day = when (d) {
            today -> "oggi"
            today.plusDays(1) -> "domani"
            today.plusDays(2) -> "dopodomani"
            else -> {
                val wd = d.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
                "$wd ${d.dayOfMonth}" + if (d.monthValue != today.monthValue) " " + d.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()) else ""
            }
        }
        return if (w.allDay) "$day, tutto il giorno" else "$day alle %d:%02d".format(w.start.hour, w.start.minute)
    }
}
