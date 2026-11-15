package app.sordina.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MedalsTest {
    private val today = LocalDate.of(2026, 11, 14) // sabato
    private fun s(daysAgo: Long, minutes: Int, hour: Int = 12, pkg: String = "app") = Habits.Skip(today.minusDays(daysAgo), pkg, minutes, hour)
    /** Con un'app frenata da un mese */
    private val braking = Medal.Evidence(brakeSince = today.minusDays(30), braked = setOf("app"))

    @Test fun firstSkip() {
        assertEquals(setOf(Medal.FIRST), Medal.earned(listOf(s(0, 5)), today))
        assertTrue(Medal.earned(listOf(s(0, -5)), today).isEmpty())
    }

    @Test fun perfectNeedsNoOpens() {
        val five = List(5) { s(0, 3) }
        assertTrue(Medal.PERFECT in Medal.earned(five, today))
        assertFalse(Medal.PERFECT in Medal.earned(five + s(0, -1), today))
    }

    @Test fun eveningOnlyWhenOverAndClean() {
        assertTrue(Medal.NIGHT in Medal.earned(listOf(s(1, 5, 23)), today))
        assertFalse(Medal.NIGHT in Medal.earned(listOf(s(0, 5, 23)), today))
        assertFalse(Medal.NIGHT in Medal.earned(listOf(s(1, 5, 23), s(1, -10, 22)), today))
    }

    @Test fun oldSeriesWithoutBrake() {
        // Senza app frenate: contano solo le giornate in positivo, una persa azzera
        val all = listOf(s(5, 5), s(4, 5), s(3, 5), s(2, -5), s(1, 5))
        val st = Habits.summarize(all, today)
        assertEquals(1, st.days)
        assertEquals(3, st.best)
    }

    @Test fun seriesCountsDaysWithoutTrying() {
        // Freni da 10 giorni, hai perso 4 giorni fa e poi non hai più provato: la serie è 4 (ieri, l'altroieri… e oggi)
        val all = listOf(s(4, -5))
        val st = Habits.summarize(all, today, since = today.minusDays(10))
        assertEquals(4, st.days)
        assertEquals(6, st.best)
        // Una giornata persa uscita dallo storico conta lo stesso
        assertEquals(2, Habits.summarize(emptyList(), today, since = today.minusDays(100), lastLost = today.minusDays(2)).days)
        val ev = braking.copy(brakeSince = today.minusDays(10))
        assertTrue(Medal.SERIES_3 in Medal.earned(all, today, ev = ev))
        assertFalse(Medal.SERIES_7 in Medal.earned(all, today, ev = ev))
    }

    @Test fun fullWeekMondayToSunday() {
        // Da lunedì 2 a domenica 8 novembre: anche senza tentativi la settimana è piena, una giornata persa no
        assertTrue(Medal.WEEK in Medal.earned(emptyList(), today, ev = braking))
        assertFalse(Medal.WEEK in Medal.earned(listOf(s(8, -5)), today, ev = braking.copy(brakeSince = today.minusDays(12))))
        assertFalse(Medal.WEEK in Medal.earned(emptyList(), today))
    }

    @Test fun levelsFromTotalAndCleanBonus() {
        assertTrue(Medal.LEVEL_2 in Medal.earned(listOf(s(0, 15)), today))
        assertFalse(Medal.LEVEL_5 in Medal.earned(listOf(s(0, 15)), today))
        // Cinque giornate senza tentazioni: 150 minuti di bonus, livello 5
        assertTrue(Medal.LEVEL_5 in Medal.earned(emptyList(), today, ev = Medal.Evidence(cleanTotal = 5)))
    }

    @Test fun comeback() {
        val all = listOf(s(4, -5), s(3, 5), s(2, 5), s(1, 5))
        assertTrue(Medal.COMEBACK in Medal.earned(all, today, ev = braking))
        assertFalse(Medal.COMEBACK in Medal.earned(all + s(2, -20), today, ev = braking))
    }

    @Test fun everyBrakedApp() {
        val ev = Medal.Evidence(braked = setOf("a", "b"))
        assertTrue(Medal.ALL_OF_THEM in Medal.earned(listOf(s(0, 5, pkg = "a"), s(0, 5, pkg = "b")), today, ev = ev))
        assertFalse(Medal.ALL_OF_THEM in Medal.earned(listOf(s(0, 5, pkg = "a")), today, ev = ev))
    }

    @Test fun skipRun() {
        val five = List(5) { s(0, 3) }
        assertTrue(Medal.SKIP_RUN in Medal.earned(five, today))
        assertFalse(Medal.SKIP_RUN in Medal.earned(five.take(2) + s(0, -3) + five.take(3), today))
    }

    @Test fun phoneDays() {
        fun d(ago: Long) = today.minusDays(ago)
        val calm = (1L..7L).associate { d(it) to Medal.PhoneDay(50, 20, nightOff = true, dinnerOff = true) }
        val got = Medal.earned(emptyList(), today, ev = Medal.Evidence(phone = calm))
        listOf(Medal.UNDER_1H, Medal.SUNSET_7, Medal.FEW_UNLOCKS, Medal.WEEK_UNDER_2H, Medal.FEW_UNLOCKS_7, Medal.NIGHT_OFF_7, Medal.DINNER_7)
            .forEach { assertTrue(it.name, it in got) }
        assertFalse(Medal.UNLOCKS_15 in got)
        // Senza app frenate la mattina libera non conta
        assertFalse(Medal.MORNING in got)
        assertTrue(Medal.MORNING in Medal.earned(emptyList(), today, ev = Medal.Evidence(phone = calm, braked = setOf("a"))))
        val late = calm + (d(3) to Medal.PhoneDay(50, 20, late = true))
        assertFalse(Medal.SUNSET_7 in Medal.earned(emptyList(), today, ev = Medal.Evidence(phone = late)))
    }

    @Test fun halfDayAndDownhill() {
        val mon = today.minusDays(today.dayOfWeek.value - 1L + 14)
        val days = (0L..13L).associate { mon.plusDays(it) to Medal.PhoneDay(if (it < 7) 200 else 150, 40) }
        val got = Medal.earned(emptyList(), today, ev = Medal.Evidence(phone = days))
        assertTrue(Medal.DOWNHILL in got)
        assertFalse(Medal.HALF_DAY in got)
        val half = days + (mon.plusDays(13) to Medal.PhoneDay(60, 40))
        assertTrue(Medal.HALF_DAY in Medal.earned(emptyList(), today, ev = Medal.Evidence(phone = half)))
    }

    @Test fun counters() {
        val ev = Medal.Evidence(skipsEver = 120, held = 150, daysWithSordina = 40, carTrips = 10, handsFree = 1, cleanTotal = 10, cleanBest = 3)
        val got = Medal.earned(emptyList(), today, ev = ev)
        listOf(Medal.SKIP_100, Medal.HELD_100, Medal.WEEK_1, Medal.MONTH_1, Medal.ROAD_10, Medal.HANDS_FREE, Medal.CLEAN_10, Medal.CLEAN_3)
            .forEach { assertTrue(it.name, it in got) }
        assertFalse(Medal.SKIP_500 in got); assertFalse(Medal.CLEAN_7 in got)
    }

    @Test fun progressResetsWithTheSeries() {
        val ev = braking.copy(brakeSince = today.minusDays(10), cleanRun = 4)
        val score = Medal.Score(listOf(s(4, -5)), today, ev = ev)
        assertEquals(Medal.Progress(4, 7), score.progress(Medal.SERIES_7))
        assertEquals(Medal.Progress(4, 7), score.progress(Medal.CLEAN_7))
        assertEquals(null, score.progress(Medal.FIRST))
        assertEquals(Medal.Progress(0, 7), Medal.Score(listOf(s(0, -5)), today, ev = ev).progress(Medal.SERIES_7))
    }

    @Test fun hundred() { assertEquals(100, Medal.entries.size) }
}
