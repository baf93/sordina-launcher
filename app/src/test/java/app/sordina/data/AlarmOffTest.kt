package app.sordina.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmOffTest {
    private fun w(s: String) = s.split(' ')
    private val one = mapOf("alarm_control_panel.centrale_naboo" to "Centrale Naboo")
    private val two = one + ("alarm_control_panel.garage" to "Antifurto garage")

    @Test fun oneAlarmAnswersToTheWord() {
        assertEquals("alarm_control_panel.centrale_naboo", HomeRisk.alarmOff(w("disattiva allarme"), one)?.first)
        assertEquals("alarm_control_panel.centrale_naboo", HomeRisk.alarmOff(w("disarma l allarme"), one)?.first)
        assertEquals("alarm_control_panel.centrale_naboo", HomeRisk.alarmOff(w("spegni centrale naboo"), one)?.first)
    }

    @Test fun withTwoTheNameChooses() {
        assertEquals("alarm_control_panel.garage", HomeRisk.alarmOff(w("disattiva antifurto garage"), two)?.first)
        val ask = HomeRisk.alarmOff(w("disattiva allarme"), two)
        assertNull(ask!!.first)
    }

    @Test fun otherPhrasesAreNotDisarm() {
        assertNull(HomeRisk.alarmOff(w("inserisci allarme"), one))
        assertNull(HomeRisk.alarmOff(w("spegni luce sala"), one))
        assertNull(HomeRisk.alarmOff(w("disattiva allarme"), emptyMap()))
    }
}
