package app.sordina.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRiskTest {
    private val names = listOf("Porta ingresso", "Garage", "Antifurto casa")
    private fun w(s: String) = s.split(' ')
    private fun auth(s: String, sensitive: Boolean = false) = HomeRisk.needsAuth(w(s), names, sensitive)

    @Test fun guardedEntitiesByDomainAndClass() {
        assertTrue(HomeRisk.guarded(HaEntity("lock.ingresso", "Ingresso", "locked", null)))
        assertTrue(HomeRisk.guarded(HaEntity("alarm_control_panel.casa", "Casa", "armed_away", null)))
        assertTrue(HomeRisk.guarded(HaEntity("cover.box", "Box", "closed", null, "garage")))
        assertFalse(HomeRisk.guarded(HaEntity("cover.salotto", "Tapparella", "open", null, "shutter")))
        assertFalse(HomeRisk.guarded(HaEntity("light.ingresso", "Luce ingresso", "on", null)))
    }

    @Test fun anInnocentNameStillNeedsAuth() {
        assertTrue(auth("apri ingresso"))
        assertTrue(auth("apri la porta ingresso"))
        assertTrue(auth("disattiva antifurto casa"))
        assertTrue(auth("Apri l ingresso"))
    }

    @Test fun otherThingsInTheSameRoomDoNot() {
        assertFalse(auth("accendi luce ingresso"))
        assertFalse(auth("spegni luci casa"))
        assertFalse(auth("apri"))
    }

    @Test fun securingIsImmediate() {
        assertFalse(auth("chiudi ingresso"))
        assertFalse(auth("chiudi il garage", sensitive = true))
        assertFalse(auth("lock front door", sensitive = true))
    }

    @Test fun sensitiveWordsAloneNeedAuth() {
        assertTrue(HomeRisk.needsAuth(w("sblocca cancello"), emptyList(), sensitive = true))
        assertTrue(HomeRisk.needsAuth(w("disarma allarme"), emptyList(), sensitive = true))
    }
}
