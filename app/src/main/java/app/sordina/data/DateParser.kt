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
        var s = " " + english(clean(text)) + " "
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
        var s = " " + english(clean(text)).split(' ').joinToString(" ") { typo(it) } + " "
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
        // "venerdì 13 novembre": conta la data, il giorno della settimana è solo un nome (di venerdì, non è il prossimo)
        cut(Regex(" (lunedi|martedi|mercoledi|giovedi|venerdi|sabato|domenica) (?=(il )?\\d{1,2}[ /])"))
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
        if (time == null) cut(Regex(" (alle|all|per le|verso le|dalle|ore|h) ?(\\d{1,2})(?:(?:[:.]| e | )(\\d{2}))?( e mezza| e mezzo| e un quarto| meno un quarto)?( di sera| del pomeriggio| di pomeriggio| di mattina| del mattino| di notte)? "))?.let { m ->
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

    /**
     * L'inglese riportato alla forma che le regole capiscono già: "tonight at 10" → "stasera alle 10",
     * "7pm" → "alle 7 di sera", "in 10 minutes" → "tra 10 minuti", "next friday" → "venerdi prossimo".
     * Solo forme che non si confondono con l'italiano: "in" diventa "tra" solo davanti a una durata,
     * i mesi solo accanto a un numero ("may" da solo resta "may").
     */
    fun english(s0: String): String {
        var s = " $s0 "
        fun r(from: String, to: String) { s = s.replace(Regex(from), to) }
        // Numeri a parole
        for ((w, n) in EN_NUMBERS) r(" $w (?=(minutes?|mins?|hours?|hrs?|seconds?|secs?|days?|weeks?|am|pm|o clock|oclock)\\b)", " $n ")
        // Durate
        r(" half (an|a) hour ", " mezz ora ")
        r(" (a )?quarter of an hour ", " quarto d ora ")
        r(" (a|an|one) (?=(minute|hour|second|day|week)\\b)", " un ")
        r(" (\\d+) ?(minutes|minute|mins|min) ", " $1 minuti ")
        r(" (\\d+|un) ?(hours|hour|hrs|hr) ", " $1 ore ")
        r(" (\\d+) ?(seconds|second|secs|sec) ", " $1 secondi ")
        r(" (\\d+|un) (days|day) ", " $1 giorni ")
        r(" (\\d+|un) (weeks|week) ", " $1 settimane ")
        r(" un minuti ", " un minuto ")
        r(" in (?=(mezz ora|quarto d ora|\\d+ (minuti|ore|secondi|giorni|settimane)|un (minuto|ore|giorni|settimane)) )", " tra ")
        r(" and (?=\\d+ (minuti|secondi) )", " e ")
        // Giorni
        r(" (the )?day after tomorrow ", " dopodomani ")
        r(" tomorrow (morning) ", " domani mattina ")
        r(" tomorrow (afternoon) ", " domani pomeriggio ")
        r(" tomorrow (evening|night) ", " domani sera ")
        r(" tomorrow ", " domani ")
        r(" (tonight|this evening) ", " stasera ")
        r(" this morning ", " stamattina ")
        r(" this afternoon ", " oggi pomeriggio ")
        r(" today ", " oggi ")
        for ((en, it) in EN_DAYS) {
            r(" (next|this coming) $en ", " $it prossimo ")
            r(" (on |this )?$en ", " $it ")
        }
        // "november 20th", "20th of november", "the 15th"
        for ((i, en) in EN_MONTHS.withIndex()) {
            r(" (on )?$en (\\d{1,2})(st|nd|rd|th)? ", " $2 ${MONTHS[i]} ")
            r(" (on )?(the )?(\\d{1,2})(st|nd|rd|th)? (of )?$en ", " $3 ${MONTHS[i]} ")
        }
        r(" (on )?the (\\d{1,2})(st|nd|rd|th) ", " il $2 ")
        // Ore: "at noon", "7pm", "at 7:30 am", "at 10", "10 o clock"
        r(" (at )?(noon|midday) ", " a mezzogiorno ")
        r(" (at )?midnight ", " a mezzanotte ")
        r(" (at )?12 ?am ", " a mezzanotte ")
        r(" (at |by |around )?(\\d{1,2})(?::(\\d{2}))? ?pm ", " alle $2:$3 di sera ")
        r(" (at |by |around )?(\\d{1,2})(?::(\\d{2}))? ?am ", " alle $2:$3 di mattina ")
        r(" alle (\\d{1,2}): di ", " alle $1 di ")
        r(" (\\d{1,2}) ?(o clock|oclock) ", " alle $1 ")
        r(" (at|by|around) (?=\\d{1,2}([:.]\\d{2})? )", " alle ")
        r(" in the (morning) ", " di mattina ")
        r(" in the (afternoon|evening) ", " di sera ")
        r(" at night ", " di notte ")
        return s.replace(Regex("\\s+"), " ").trim()
    }

    private val EN_NUMBERS = listOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9,
        "ten" to 10, "eleven" to 11, "twelve" to 12, "fifteen" to 15, "twenty" to 20, "thirty" to 30, "forty" to 40,
        "forty five" to 45, "fifty" to 50, "sixty" to 60, "ninety" to 90,
    ).sortedByDescending { it.first.length }
    private val EN_DAYS = listOf(
        "monday" to "lunedi", "tuesday" to "martedi", "wednesday" to "mercoledi", "thursday" to "giovedi",
        "friday" to "venerdi", "saturday" to "sabato", "sunday" to "domenica",
    )
    private val EN_MONTHS = listOf(
        "january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december",
    )

    /** Le parole del quando, anche con un errore di battitura ("staswera", "domnai"). Solo parole lunghe e con la stessa iniziale. */
    private val DAY_WORDS = listOf("stasera", "stamattina", "stamani", "stanotte", "domani", "dopodomani", "pomeriggio", "mattina",
        "mezzogiorno", "mezzanotte") + WEEKDAYS.keys + MONTHS.filter { it.length >= 6 && it != "aprile" }

    private fun typo(w: String): String {
        if (w.length < 6 || w in DAY_WORDS) return w
        return DAY_WORDS.firstOrNull { it[0] == w[0] && kotlin.math.abs(it.length - w.length) <= 1 && near(it, w) } ?: w
    }

    /** Distanza di una sola modifica: una lettera in più, in meno, cambiata o due scambiate. */
    private fun near(a: String, b: String): Boolean {
        var i = 0
        while (i < minOf(a.length, b.length) && a[i] == b[i]) i++
        return when {
            a.length == b.length -> a.substring(i + 1) == b.substring(i + 1) ||
                (i + 1 < a.length && a[i] == b[i + 1] && a[i + 1] == b[i] && a.substring(i + 2) == b.substring(i + 2))
            a.length > b.length -> a.substring(i + 1) == b.substring(i)
            else -> a.substring(i) == b.substring(i + 1)
        }
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
            today -> app.sordina.tr(app.sordina.R.string.when_today)
            today.plusDays(1) -> app.sordina.tr(app.sordina.R.string.when_tomorrow)
            today.plusDays(2) -> app.sordina.tr(app.sordina.R.string.when_after)
            else -> {
                val wd = d.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
                "$wd ${d.dayOfMonth}" + if (d.monthValue != today.monthValue) " " + d.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()) else ""
            }
        }
        val time = w.start.toLocalTime().format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))
        return if (w.allDay) app.sordina.tr(app.sordina.R.string.when_all_day, day) else app.sordina.tr(app.sordina.R.string.when_at, day, time)
    }
}
