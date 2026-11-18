package app.sordina.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmOnTest {
    private fun w(s: String) = s.split(' ')
    private val one = mapOf("alarm_control_panel.casa" to "Antifurto casa")
    private val two = one + ("alarm_control_panel.garage" to "Garage")

    @Test fun defaultOrSaidMode() {
        assertEquals(Triple("alarm_control_panel.casa", "Antifurto casa", null), HomeRisk.alarmOn(w("inserisci allarme"), one))
        assertEquals("night", HomeRisk.alarmOn(w("attiva allarme notte"), one)?.third)
        assertEquals("away", HomeRisk.alarmOn(w("inserisci allarme fuori"), one)?.third)
        // "casa" fa parte del nome, non è la modalità In casa
        assertNull(HomeRisk.alarmOn(w("inserisci antifurto casa"), one)?.third)
        assertEquals("night", HomeRisk.alarmOn(w("inserisci antifurto casa notte"), one)?.third)
    }

    @Test fun nameChoosesOrAsk() {
        assertEquals("alarm_control_panel.garage", HomeRisk.alarmOn(w("arma garage"), two)?.first)
        assertNull(HomeRisk.alarmOn(w("inserisci allarme"), two)!!.first)
    }

    @Test fun notArming() {
        assertNull(HomeRisk.alarmOn(w("disattiva allarme"), one))
        assertNull(HomeRisk.alarmOn(w("accendi luce sala"), one))
        assertNull(HomeRisk.alarmOn(w("inserisci allarme"), emptyMap()))
    }
}
