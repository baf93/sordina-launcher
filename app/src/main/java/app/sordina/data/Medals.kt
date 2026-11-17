package app.sordina.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Le imprese di Sordina: 100 adesivi della mascotte, tutti sul detox. Si vincono con le scelte del freno, le giornate
 * senza tentazioni, il tempo ripreso, le serie, il tempo al telefono, la guida, le notifiche e qualche scelta.
 * Si calcolano solo quando servono (aprendo Casa ogni tanto, dopo un "Lascia stare", nella pagina dei progressi).
 *
 * [tier]: 1 facile, 2 media, 3 difficile (il colore dell'adesivo). [n]: il traguardo (e il numero sull'adesivo), 0 nessuno.
 * I nomi delle medaglie già esistenti restano uguali: quelle già vinte si ritrovano.
 */
enum class Medal(val group: Group, val tier: Int, val n: Int = 0) {
    // Freno: lasciare stare
    FIRST(Group.BRAKE, 1), TEN(Group.BRAKE, 2, 10), SKIP_100(Group.BRAKE, 2, 100), SKIP_500(Group.BRAKE, 3, 500),
    SKIP_1000(Group.BRAKE, 3, 1000), PERFECT(Group.BRAKE, 2, 5), LOYAL(Group.BRAKE, 2, 25), SKIP_RUN(Group.BRAKE, 2, 5),
    MORNING(Group.BRAKE, 1), LUNCH(Group.BRAKE, 1), NIGHT(Group.BRAKE, 2), WEEKEND(Group.BRAKE, 2), ALL_OF_THEM(Group.BRAKE, 2),
    // Senza tentazioni: giornate in cui non provi neanche ad aprire un'app frenata
    CLEAN_1(Group.CLEAN, 1, 1), CLEAN_3(Group.CLEAN, 1, 3), CLEAN_7(Group.CLEAN, 2, 7), CLEAN_14(Group.CLEAN, 2, 14),
    CLEAN_30(Group.CLEAN, 3, 30), CLEAN_WEEKEND(Group.CLEAN, 2), CLEAN_10(Group.CLEAN, 1, 10), CLEAN_50(Group.CLEAN, 2, 50),
    CLEAN_100(Group.CLEAN, 3, 100), CLEAN_365(Group.CLEAN, 3, 365),
    // Tempo ripreso (in minuti)
    HOUR(Group.TIME, 2, 60), DAY_2H(Group.TIME, 2, 120), WEEK_3H(Group.TIME, 2, 180), MONTH_6H(Group.TIME, 2, 360),
    TOTAL_10H(Group.TIME, 2, 600), TOTAL_24H(Group.TIME, 3, 1440), TOTAL_50H(Group.TIME, 3, 3000), TOTAL_100H(Group.TIME, 3, 6000),
    // Serie: giornate non perse di fila
    SERIES_3(Group.SERIES, 1, 3), SERIES_7(Group.SERIES, 2, 7), SERIES_14(Group.SERIES, 2, 14), SERIES_30(Group.SERIES, 3, 30),
    SERIES_100(Group.SERIES, 3, 100), SERIES_365(Group.SERIES, 3, 365), WEEK(Group.SERIES, 2, 7), MONTH(Group.SERIES, 3, 30),
    COMEBACK(Group.SERIES, 1, 3),
    // Livelli
    LEVEL_2(Group.LEVEL, 1, 2), LEVEL_5(Group.LEVEL, 1, 5), LEVEL_10(Group.LEVEL, 2, 10), LEVEL_20(Group.LEVEL, 2, 20),
    LEVEL_30(Group.LEVEL, 3, 30), LEVEL_50(Group.LEVEL, 3, 50), LEVEL_75(Group.LEVEL, 3, 75), LEVEL_100(Group.LEVEL, 3, 100),
    // Tempo al telefono
    UNDER_3H(Group.PHONE, 1, 180), UNDER_2H(Group.PHONE, 2, 120), UNDER_1H(Group.PHONE, 3, 60),
    WEEK_UNDER_2H(Group.PHONE, 3, 7), MONTH_UNDER_3H(Group.PHONE, 3, 30), HALF_DAY(Group.PHONE, 2), DOWNHILL(Group.PHONE, 2),
    SLOW_SUNDAY(Group.PHONE, 2), LIGHT_WEEKEND(Group.PHONE, 2), FEW_UNLOCKS(Group.PHONE, 2, 30), UNLOCKS_15(Group.PHONE, 3, 15),
    FEW_UNLOCKS_7(Group.PHONE, 3, 7), SLOW_MORNING(Group.PHONE, 2), FRESH_AIR(Group.PHONE, 2, 3), FRESH_AIR_5(Group.PHONE, 2, 5),
    OFFLINE_6H(Group.PHONE, 3, 6), DINNER(Group.PHONE, 1), DINNER_7(Group.PHONE, 2, 7), SUNSET(Group.PHONE, 1),
    SUNSET_7(Group.PHONE, 3, 7), NIGHT_OFF(Group.PHONE, 1), NIGHT_OFF_7(Group.PHONE, 2, 7),
    // Alla guida
    ROAD_1(Group.DRIVE, 1, 1), ROAD_10(Group.DRIVE, 2, 10), ROAD_100(Group.DRIVE, 3, 100), HANDS_FREE(Group.DRIVE, 1, 1),
    HANDS_FREE_10(Group.DRIVE, 2, 10), HANDS_FREE_50(Group.DRIVE, 3, 50), LONG_DRIVE(Group.DRIVE, 2), NIGHT_DRIVE(Group.DRIVE, 2),
    ARRIVED(Group.DRIVE, 1),
    // Notifiche
    HELD_100(Group.NOTIF, 1, 100), HELD_1000(Group.NOTIF, 2, 1000), HELD_10000(Group.NOTIF, 3, 10000), READ_ALL(Group.NOTIF, 1),
    READ_ALL_10(Group.NOTIF, 2, 10), PEOPLE(Group.NOTIF, 1), FIRST_RULE(Group.NOTIF, 1), FOCUS(Group.NOTIF, 1),
    FOCUS_10(Group.NOTIF, 2, 10), DEEP_WORK(Group.NOTIF, 2, 3), QUIET_DAY(Group.NOTIF, 3, 8), REST_7(Group.NOTIF, 2, 7),
    REST_30(Group.NOTIF, 3, 30),
    // Scelte
    BRAKE_3(Group.CHOICE, 1, 3), HIDE(Group.CHOICE, 1), FAREWELL(Group.CHOICE, 2),
    // Anniversari (in giorni)
    WEEK_1(Group.YEARS, 1, 7), MONTH_1(Group.YEARS, 1, 30), MONTHS_6(Group.YEARS, 2, 182), YEAR_1(Group.YEARS, 3, 365),
    YEARS_2(Group.YEARS, 3, 730);

    enum class Group { BRAKE, CLEAN, TIME, SERIES, LEVEL, PHONE, DRIVE, NOTIF, CHOICE, YEARS }

    /** Una giornata al telefono, già finita: minuti in primo piano, sblocchi e qualche segno. */
    data class PhoneDay(
        val minutes: Int, val unlocks: Int,
        /** Sbloccato dopo le 23 */
        val late: Boolean = false,
        /** Nessuno sblocco nella mezz'ora dopo la prima accensione del mattino */
        val slowMorning: Boolean = false,
        /** Tre ore di fila senza sbloccare, tra le 8 e le 22 */
        val air: Boolean = false,
        /** Un'app frenata in primo piano prima delle 10 */
        val brakedMorning: Boolean = false,
        /** Un'app frenata in primo piano tra le 12 e le 14 */
        val brakedLunch: Boolean = false,
        /** Nessuno sblocco da mezzanotte alle 6 */
        val nightOff: Boolean = false,
        /** Nessuno sblocco dalle 18 alle 20 */
        val dinnerOff: Boolean = false,
        /** Sei ore di fila senza sbloccare, tra le 8 e le 22 */
        val offline6h: Boolean = false,
        /** Un'app frenata in primo piano, a qualunque ora (anche solo per vedere il freno) */
        val brakedAny: Boolean = false,
    )

    /** Tutto quello che non sta nello storico del freno. Vuoto di serie (per le prove). */
    data class Evidence(
        val skipsEver: Int = 0, val bestEver: Int = 0, val skipRunEver: Int = 0,
        /** Il giorno in cui hai frenato la prima app (null: nessuna app frenata) */
        val brakeSince: LocalDate? = null,
        /** L'ultima giornata persa, anche se è già uscita dallo storico */
        val lastLost: LocalDate? = null,
        val cleanTotal: Int = 0, val cleanRun: Int = 0, val cleanBest: Int = 0, val cleanWeekend: Boolean = false,
        val held: Int = 0, val readAllDays: Int = 0, val focus: Int = 0, val deep: Boolean = false, val quietDay: Boolean = false,
        val restNights: Int = 0,
        val important: Boolean = false, val rule: Boolean = false, val braked: Set<String> = emptySet(), val hidden: Boolean = false,
        val farewell: Boolean = false,
        val carTrips: Int = 0, val handsFree: Int = 0, val longDrive: Boolean = false, val nightDrive: Boolean = false, val arrived: Boolean = false,
        val daysWithSordina: Int = 0,
        val phone: Map<LocalDate, PhoneDay> = emptyMap(),
        /** I punti delle giornate col budget (oggi compreso), da [budgetFrom]; [ledgerBank]: quelli già usciti dal registro */
        val ledger: Map<LocalDate, Int> = emptyMap(), val budgetFrom: LocalDate? = null, val ledgerBank: Int = 0,
        /** Le giornate senza tentazioni che regalano minuti al livello (null: tutte, come prima del budget) */
        val bonusDays: Int? = null,
    )

    /** Dove sei arrivato: [now] su [goal]. Le serie tornano a zero quando si interrompono. */
    data class Progress(val now: Int, val goal: Int)

    /** Il calcolo di tutte le imprese su una fotografia dei dati: quali sono vinte e a che punto sei con le altre. */
    class Score(private val all: List<Habits.Skip>, private val today: LocalDate, bank: Int = 0, private val ev: Evidence = Evidence()) {
        private val st = Habits.summarize(all, today, bank, ev.brakeSince, ev.lastLost, cleanBonus(ev.bonusDays ?: ev.cleanTotal), ev.ledger, ev.budgetFrom, ev.ledgerBank)
        private val byDay = all.groupBy { it.day }
        /** Il saldo di ogni giornata: dalle scelte prima del budget, dal budget dopo */
        private val balance = Habits.balances(all, ev.ledger, ev.budgetFrom)
        /** Giornata perfetta: 5 "Lascia stare", e nessun "Apri comunque" (prima del budget) o dentro il budget (dopo) */
        private fun spotless(d: LocalDate, v: List<Habits.Skip>): Boolean =
            if (ev.budgetFrom == null || d.isBefore(ev.budgetFrom)) v.none { it.minutes < 0 } else bal(d) >= 0
        private fun perfect(d: LocalDate, v: List<Habits.Skip>): Boolean = v.count { it.minutes > 0 } >= Habits.PERFECT_SKIPS && spotless(d, v)
        private fun weekSum(mon: LocalDate) = (0L..6L).sumOf { bal(mon.plusDays(it)) }
        private fun monthSum(ym: YearMonth) = balance.filterKeys { YearMonth.from(it) == ym }.values.sum()
        private fun bal(d: LocalDate) = balance[d] ?: 0
        /** Una giornata non persa: con le app frenate, e senza finire in negativo */
        private fun kept(d: LocalDate) = ev.brakeSince != null && !d.isBefore(ev.brakeSince) && !d.isAfter(today) && bal(d) >= 0
        private val wins = all.count { it.minutes > 0 }
        private val skipsEver = maxOf(ev.skipsEver, wins)
        private val days = ev.phone.filterValues { it.minutes > 0 }
        private val yesterday = today.minusDays(1)

        /** Le "Lascia stare" di fila, senza un "Apri comunque" in mezzo: la migliore e quella di adesso */
        private val runs: Pair<Int, Int> = run {
            var cur = 0; var best = 0
            all.forEach { if (it.minutes > 0) { cur++; best = maxOf(best, cur) } else if (it.minutes < 0) cur = 0 }
            maxOf(best, ev.skipRunEver) to cur
        }

        /** Le giornate al telefono di fila, fino a ieri, che rispettano [ok]: la migliore e quella di adesso */
        private fun phoneRun(ok: (PhoneDay) -> Boolean): Pair<Int, Int> {
            var best = 0; var cur = 0; var prev: LocalDate? = null
            days.keys.sorted().forEach { d ->
                cur = if (!ok(days.getValue(d))) 0 else if (prev?.plusDays(1) == d) cur + 1 else 1
                prev = d; best = maxOf(best, cur)
            }
            val now = if (prev == yesterday) cur else 0
            return best to now
        }

        private val underTwo = phoneRun { it.minutes < 120 }
        private val underThree = phoneRun { it.minutes < 180 }
        private val fewUnlocks = phoneRun { it.unlocks in 1 until 30 }
        private val sunsets = phoneRun { it.unlocks > 0 && !it.late }
        private val nights = phoneRun { it.unlocks > 0 && it.nightOff }

        val earned: Set<Medal> by lazy { entries.filter { won(it) }.toSet() }

        fun progress(m: Medal): Progress? = when (m) {
            TEN -> Progress(byDay[today].orEmpty().count { it.minutes > 0 }, m.n)
            SKIP_100, SKIP_500, SKIP_1000 -> Progress(skipsEver, m.n)
            PERFECT -> Progress(byDay[today].orEmpty().let { v -> if (spotless(today, v)) v.count { it.minutes > 0 } else 0 }, m.n)
            LOYAL -> Progress(all.filter { it.minutes > 0 }.groupingBy { it.pkg }.eachCount().values.maxOrNull() ?: 0, m.n)
            SKIP_RUN -> Progress(runs.second, m.n)
            ALL_OF_THEM -> if (ev.braked.size < 2) null else Progress(byDay[today].orEmpty().filter { it.minutes > 0 && it.pkg in ev.braked }.map { it.pkg }.toSet().size, ev.braked.size)
            CLEAN_3, CLEAN_7, CLEAN_14, CLEAN_30 -> Progress(ev.cleanRun, m.n)
            CLEAN_10, CLEAN_50, CLEAN_100, CLEAN_365 -> Progress(ev.cleanTotal, m.n)
            HOUR, DAY_2H -> Progress(weekSum(monday(today)).coerceAtLeast(0), m.n)
            WEEK_3H, MONTH_6H -> Progress(monthSum(YearMonth.from(today)).coerceAtLeast(0), m.n)
            TOTAL_10H, TOTAL_24H, TOTAL_50H, TOTAL_100H -> Progress(st.totalMinutes, m.n)
            SERIES_3, SERIES_7, SERIES_14, SERIES_30, SERIES_100, SERIES_365 -> Progress(st.days, m.n)
            WEEK -> Progress(keptSince(monday(today)), 7)
            MONTH -> Progress(keptSince(today.withDayOfMonth(1)), today.lengthOfMonth())
            COMEBACK -> lastLostDay()?.let { l -> Progress((1L..3L).takeWhile { kept(l.plusDays(it)) }.size, 3) }
            LEVEL_2, LEVEL_5, LEVEL_10, LEVEL_20, LEVEL_30, LEVEL_50, LEVEL_75, LEVEL_100 -> Progress(st.level, m.n)
            WEEK_UNDER_2H -> Progress(underTwo.second, m.n)
            MONTH_UNDER_3H -> Progress(underThree.second, m.n)
            FEW_UNLOCKS_7 -> Progress(fewUnlocks.second, m.n)
            FRESH_AIR_5 -> Progress(days.values.count { it.unlocks > 0 && it.air }, m.n)
            DINNER_7 -> Progress(days.values.count { it.unlocks > 0 && it.dinnerOff }, m.n)
            SUNSET_7 -> Progress(sunsets.second, m.n)
            NIGHT_OFF_7 -> Progress(nights.second, m.n)
            ROAD_10, ROAD_100 -> Progress(ev.carTrips, m.n)
            HANDS_FREE_10, HANDS_FREE_50 -> Progress(ev.handsFree, m.n)
            HELD_100, HELD_1000, HELD_10000 -> Progress(ev.held, m.n)
            READ_ALL_10 -> Progress(ev.readAllDays, m.n)
            FOCUS_10 -> Progress(ev.focus, m.n)
            REST_7, REST_30 -> Progress(ev.restNights, m.n)
            BRAKE_3 -> Progress(ev.braked.size, m.n)
            WEEK_1, MONTH_1, MONTHS_6, YEAR_1, YEARS_2 -> Progress(ev.daysWithSordina, m.n)
            else -> null
        }?.let { Progress(it.now.coerceIn(0, it.goal), it.goal) }

        private fun monday(d: LocalDate) = d.minusDays(d.dayOfWeek.value - 1L)
        /** Le giornate non perse da [from] a oggi; 0 se una è persa */
        private fun keptSince(from: LocalDate): Int {
            val n = ChronoUnit.DAYS.between(from, today).toInt() + 1
            return if ((0 until n).all { kept(from.plusDays(it.toLong())) }) n else 0
        }
        private fun lastLostDay(): LocalDate? = listOfNotNull(balance.filterValues { it < 0 }.keys.maxOrNull(), ev.lastLost).maxOrNull()

        private fun won(m: Medal): Boolean = when (m) {
            // ── Freno
            FIRST -> skipsEver > 0
            TEN -> byDay.values.any { v -> v.count { it.minutes > 0 } >= m.n }
            SKIP_100, SKIP_500, SKIP_1000 -> skipsEver >= m.n
            PERFECT -> byDay.any { (d, v) -> perfect(d, v) }
            LOYAL -> all.filter { it.minutes > 0 }.groupingBy { it.pkg }.eachCount().values.any { it >= m.n }
            SKIP_RUN -> runs.first >= m.n
            MORNING -> ev.braked.isNotEmpty() && days.values.any { !it.brakedMorning }
            LUNCH -> ev.braked.isNotEmpty() && days.values.any { !it.brakedLunch }
            // Una sera è finita solo il giorno dopo
            NIGHT -> byDay.any { (d, v) -> d.isBefore(today) && v.any { it.minutes > 0 && it.hour >= 22 } && v.none { it.minutes < 0 && it.hour >= 22 } }
            WEEKEND -> (0L..400L).map { today.minusDays(it) }.filter { it.dayOfWeek == DayOfWeek.SUNDAY && ev.brakeSince?.let { s -> !it.minusDays(1).isBefore(s) } == true }
                .any { kept(it) && kept(it.minusDays(1)) }
            ALL_OF_THEM -> ev.braked.size >= 2 && byDay.values.any { v -> v.filter { it.minutes > 0 }.map { it.pkg }.toSet().containsAll(ev.braked) }
            // ── Senza tentazioni
            CLEAN_1, CLEAN_10, CLEAN_50, CLEAN_100, CLEAN_365 -> ev.cleanTotal >= m.n
            CLEAN_3, CLEAN_7, CLEAN_14, CLEAN_30 -> ev.cleanBest >= m.n
            CLEAN_WEEKEND -> ev.cleanWeekend
            // ── Tempo ripreso
            // Dal budget una giornata vale al massimo 20 minuti: un'ora e due ore in una settimana, tre e sei ore in un mese
            HOUR, DAY_2H -> balance.keys.map { monday(it) }.toSet().any { mon -> weekSum(mon) >= m.n }
            WEEK_3H, MONTH_6H -> balance.keys.map { YearMonth.from(it) }.toSet().any { ym -> monthSum(ym) >= m.n }
            TOTAL_10H, TOTAL_24H, TOTAL_50H, TOTAL_100H -> st.totalMinutes >= m.n
            // ── Serie
            SERIES_3, SERIES_7, SERIES_14, SERIES_30, SERIES_100, SERIES_365 -> maxOf(st.best, ev.bestEver) >= m.n
            // Da lunedì a domenica, già finita (o che finisce oggi)
            WEEK -> (0L..60L).map { monday(today).minusWeeks(it) }.any { mon -> !mon.plusDays(6).isAfter(today) && (0L..6L).all { kept(mon.plusDays(it)) } }
            MONTH -> (0L..3L).map { YearMonth.from(today).minusMonths(it) }.any { ym -> !ym.atEndOfMonth().isAfter(today) && (1..ym.lengthOfMonth()).all { kept(ym.atDay(it)) } }
            COMEBACK -> balance.filterValues { it < 0 }.keys.any { l -> !l.plusDays(3).isAfter(today) && (1L..3L).all { kept(l.plusDays(it)) } }
            // ── Livelli
            LEVEL_2, LEVEL_5, LEVEL_10, LEVEL_20, LEVEL_30, LEVEL_50, LEVEL_75, LEVEL_100 -> st.level >= m.n
            // ── Tempo al telefono: solo giornate finite, con dentro qualcosa
            UNDER_3H, UNDER_2H, UNDER_1H -> days.values.any { it.minutes < m.n }
            WEEK_UNDER_2H -> underTwo.first >= m.n
            MONTH_UNDER_3H -> underThree.first >= m.n
            HALF_DAY -> days.any { (d, p) ->
                val before = (1L..7L).mapNotNull { days[d.minusDays(it)]?.minutes }
                before.size == 7 && p.minutes * 2 < before.average()
            }
            // Una settimana (lunedì → domenica) con il 20% in meno di quella prima, tutte e due intere
            DOWNHILL -> days.keys.filter { it.dayOfWeek == DayOfWeek.MONDAY }.any { d ->
                val now = (0L..6L).mapNotNull { days[d.plusDays(it)]?.minutes }
                val before = (1L..7L).mapNotNull { days[d.minusDays(it)]?.minutes }
                now.size == 7 && before.size == 7 && before.sum() > 0 && now.sum() <= before.sum() * 0.8
            }
            SLOW_SUNDAY -> days.any { (d, p) -> d.dayOfWeek == DayOfWeek.SUNDAY && p.minutes < 60 }
            LIGHT_WEEKEND -> days.any { (d, p) -> d.dayOfWeek == DayOfWeek.SUNDAY && p.minutes < 120 && (days[d.minusDays(1)]?.minutes ?: Int.MAX_VALUE) < 120 }
            FEW_UNLOCKS, UNLOCKS_15 -> days.values.any { it.unlocks in 1 until m.n }
            FEW_UNLOCKS_7 -> fewUnlocks.first >= m.n
            SLOW_MORNING -> days.values.any { it.unlocks > 0 && it.slowMorning }
            FRESH_AIR -> days.values.any { it.unlocks > 0 && it.air }
            FRESH_AIR_5 -> days.values.count { it.unlocks > 0 && it.air } >= m.n
            OFFLINE_6H -> days.values.any { it.unlocks > 0 && it.offline6h }
            DINNER -> days.values.any { it.unlocks > 0 && it.dinnerOff }
            DINNER_7 -> days.values.count { it.unlocks > 0 && it.dinnerOff } >= m.n
            SUNSET -> days.values.any { it.unlocks > 0 && !it.late }
            SUNSET_7 -> sunsets.first >= m.n
            NIGHT_OFF -> days.values.any { it.unlocks > 0 && it.nightOff }
            NIGHT_OFF_7 -> nights.first >= m.n
            // ── Alla guida
            ROAD_1, ROAD_10, ROAD_100 -> ev.carTrips >= m.n
            HANDS_FREE, HANDS_FREE_10, HANDS_FREE_50 -> ev.handsFree >= m.n
            LONG_DRIVE -> ev.longDrive
            NIGHT_DRIVE -> ev.nightDrive
            ARRIVED -> ev.arrived
            // ── Notifiche
            HELD_100, HELD_1000, HELD_10000 -> ev.held >= m.n
            READ_ALL -> ev.readAllDays >= 1
            READ_ALL_10 -> ev.readAllDays >= m.n
            PEOPLE -> ev.important
            FIRST_RULE -> ev.rule
            FOCUS -> ev.focus >= 1
            FOCUS_10 -> ev.focus >= m.n
            DEEP_WORK -> ev.deep
            QUIET_DAY -> ev.quietDay
            REST_7, REST_30 -> ev.restNights >= m.n
            // ── Scelte e anniversari
            BRAKE_3 -> ev.braked.size >= m.n
            HIDE -> ev.hidden
            FAREWELL -> ev.farewell
            WEEK_1, MONTH_1, MONTHS_6, YEAR_1, YEARS_2 -> ev.daysWithSordina >= m.n
        }
    }

    companion object {
        /** I minuti regalati al livello per ogni giornata senza tentazioni. */
        const val CLEAN_BONUS = 30
        fun cleanBonus(days: Int) = days * CLEAN_BONUS

        fun earned(all: List<Habits.Skip>, today: LocalDate, bank: Int = 0, ev: Evidence = Evidence()): Set<Medal> =
            Score(all, today, bank, ev).earned
    }
}
