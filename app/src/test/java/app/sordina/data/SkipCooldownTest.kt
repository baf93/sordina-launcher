package app.sordina.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Aprire e lasciare stare di continuo non fa punti: una tentazione vale una volta. */
class SkipCooldownTest {
    private val t0 = 1_800_000_000_000L
    private val min = 60_000L

    @Test fun firstSkipEverIsRewarded() = assertTrue(Habits.rewarded(0L, t0))

    @Test fun repeatedSkipsInsideTheWindowAreNot() {
        assertFalse(Habits.rewarded(t0, t0))
        assertFalse(Habits.rewarded(t0, t0 + 1 * min))
        assertFalse(Habits.rewarded(t0, t0 + 19 * min))
    }

    @Test fun nextTemptationAfterTheWindowCountsAgain() = assertTrue(Habits.rewarded(t0, t0 + Habits.WIN_COOLDOWN))

    @Test fun clockMovedBackDoesNotLockForever() = assertTrue(Habits.rewarded(t0, t0 - 5 * min))

    /** Il giro di chi prova a farmare: apri, lascia stare, riapri, per un'ora. Premiati solo 3 su 60. */
    @Test fun farmingForAnHourEarnsOncePerWindow() {
        var lastWin = 0L
        var wins = 0
        for (i in 0 until 60) {
            val now = t0 + i * min
            if (Habits.rewarded(lastWin, now)) { wins++; lastWin = now }
        }
        assertEquals(3, wins)
    }
}
