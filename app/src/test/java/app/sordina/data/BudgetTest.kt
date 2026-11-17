package app.sordina.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Il budget del freno: i minuti di una giornata, la proposta della domenica, il saldo e le imprese. */
class BudgetTest {
    private val today = LocalDate.of(2026, 11, 14) // sabato
    private fun s(daysAgo: Long, minutes: Int, hour: Int = 12) = Habits.Skip(today.minusDays(daysAgo), "app", minutes, hour)
    private fun u(used: Int, budget: Int = 30) = Habits.Use("app", used, budget)

    @Test fun pointsArePercentOfBudget() {
        // Il caso da cui è nato tutto: 4 minuti su 30 non può essere negativo
        assertEquals(17, Habits.points(4, 30))
        assertEquals(20, Habits.points(0, 30))
        assertEquals(0, Habits.points(30, 30))
        // 30% oltre = −6; il doppio o più = −20
        assertEquals(-6, Habits.points(39, 30))
        assertEquals(-20, Habits.points(90, 30))
        // Alzare il budget non fa più punti: conta la percentuale
        assertEquals(Habits.points(5, 30), Habits.points(20, 120))
        // Budget 0: niente uso = tutto avanzato, ogni minuto conta come su 5
        assertEquals(20, Habits.points(0, 0))
        assertEquals(-4, Habits.points(1, 0))
    }

    @Test fun dayIsJudgedOnTheTotal() {
        // Una sfora di 2, l'altra avanza 20: la giornata è positiva
        val day = listOf(Habits.Use("a", 32, 30), Habits.Use("b", 0, 20))
        assertTrue(Habits.dayPoints(day) > 0)
    }

    @Test fun sundayProposal() {
        // Sempre sotto l'80%: il budget scende a quanto è bastato (a 5 minuti)
        assertEquals(15, Habits.weekly(List(7) { u(12) }, 30))
        // Sforato spesso: sale alla media vera (circa 38 → 40)
        assertEquals(40, Habits.weekly(List(5) { u(45) } + List(2) { u(20) }, 30))
        // Già giusto, o troppo pochi giorni: niente
        assertNull(Habits.weekly(List(7) { u(27) }, 30))
        assertNull(Habits.weekly(List(4) { u(5) }, 30))
        // Mai sotto 5
        assertEquals(5, Habits.weekly(List(7) { u(0) }, 30))
    }

    @Test fun budgetReplacesChoicesFromItsFirstDay() {
        val from = today.minusDays(1)
        // Prima del budget conta il saldo delle scelte; da lì solo il budget (il −7 di ieri non c'è più)
        val all = listOf(s(3, 10), s(1, -7), s(0, 1))
        val ledger = mapOf(today.minusDays(1) to 17, today to 12)
        val b = Habits.balances(all, ledger, from)
        assertEquals(10, b[today.minusDays(3)])
        assertEquals(17, b[today.minusDays(1)])
        assertEquals(12, b[today])
        val st = Habits.summarize(all, today, ledger = ledger, from = from, ledgerBank = 3)
        assertEquals(12, st.balance)
        assertEquals(10 + 17 + 12 + 3, st.totalMinutes)
    }

    @Test fun overBudgetLosesTheDay() {
        val from = today.minusDays(5)
        val st = Habits.summarize(emptyList(), today, since = from, ledger = mapOf(today.minusDays(1) to -6, today to 8), from = from)
        assertEquals(1, st.days)
        assertEquals(2, st.totalMinutes)
    }

    @Test fun perfectDayAddsFive() {
        val from = today.minusDays(2)
        val five = List(5) { s(0, 1) }
        // Dentro il budget: +5 al livello
        assertEquals(10 + 5, Habits.summarize(five, today, ledger = mapOf(today to 10), from = from).totalMinutes)
        // Fuori budget: niente bonus
        assertEquals(0, Habits.summarize(five, today, ledger = mapOf(today to -3), from = from).totalMinutes)
        val ev = Medal.Evidence(ledger = mapOf(today to 10), budgetFrom = from)
        assertTrue(Medal.PERFECT in Medal.earned(five, today, ev = ev))
        assertFalse(Medal.PERFECT in Medal.earned(five, today, ev = ev.copy(ledger = mapOf(today to -3))))
    }

    @Test fun timeMedalsCountWeeksAndMonths() {
        val from = today.minusDays(40)
        // Lunedì 9 → sabato 14: 3 giornate da 20 = un'ora nella stessa settimana
        val week = mapOf(today to 20, today.minusDays(1) to 20, today.minusDays(2) to 20)
        assertTrue(Medal.HOUR in Medal.earned(emptyList(), today, ev = Medal.Evidence(ledger = week, budgetFrom = from)))
        assertFalse(Medal.DAY_2H in Medal.earned(emptyList(), today, ev = Medal.Evidence(ledger = week, budgetFrom = from)))
        // Una giornata sola da 20 non fa più l'ora
        assertFalse(Medal.HOUR in Medal.earned(emptyList(), today, ev = Medal.Evidence(ledger = mapOf(today to 20), budgetFrom = from)))
        // Tre ore nello stesso mese (novembre)
        val month = (0L..8L).associate { today.minusDays(it) to 20 }
        assertTrue(Medal.WEEK_3H in Medal.earned(emptyList(), today, ev = Medal.Evidence(ledger = month, budgetFrom = from)))
    }
}
