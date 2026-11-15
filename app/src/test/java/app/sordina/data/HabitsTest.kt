package app.sordina.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HabitsTest {
    private val today = LocalDate.of(2026, 11, 14) // sabato
    private fun s(daysAgo: Long, minutes: Int) = Habits.Skip(today.minusDays(daysAgo), "app", minutes)

    @Test fun positiveDaysMakeTheSeries() {
        val st = Habits.summarize(listOf(s(2, 10), s(1, 5), s(0, 8)), today)
        assertEquals(3, st.days)
        assertEquals(8, st.balance)
    }

    @Test fun aNegativeDayBreaksIt() {
        val st = Habits.summarize(listOf(s(3, 10), s(2, 10), s(2, -15), s(1, 6)), today)
        // Ieri in positivo, l'altro ieri in negativo: la serie riparte da ieri
        assertEquals(1, st.days)
    }

    @Test fun neutralDaysDoNotBreakIt() {
        val st = Habits.summarize(listOf(s(3, 10), s(1, 6), s(1, -6), s(0, 4)), today)
        // Ieri a zero: neutra, non interrompe
        assertEquals(2, st.days)
    }

    @Test fun losingTodayZeroesTheSeries() {
        val st = Habits.summarize(listOf(s(2, 10), s(1, 10), s(0, 5), s(0, -9)), today)
        assertEquals(0, st.days)
        assertEquals(-4, st.balance)
        assertEquals(-1, st.week[today.dayOfWeek.value - 1])
    }

    @Test fun totalCanGoDownButNotBelowZero() {
        assertEquals(5, Habits.summarize(listOf(s(1, 20), s(0, -15)), today).totalMinutes)
        assertEquals(0, Habits.summarize(listOf(s(0, -15)), today).totalMinutes)
        assertEquals(25, Habits.summarize(listOf(s(0, -15)), today, bank = 40).totalMinutes)
    }
}
