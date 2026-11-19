package app.sordina.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Casa senza token (nomi delle serrature sconosciuti) e pulsanti delle notifiche di Home Assistant. */
class HomeRiskNoTokenTest {
    private fun w(s: String) = s.split(' ')
    private fun noToken(s: String) = HomeRisk.needsAuth(w(s), emptyList(), Intents.sensitive(w(s).map { Search.normalize(it) }), namesKnown = false)
    private fun button(q: String, b: String) = HomeRisk.buttonNeedsAuth(q, b, listOf("Ingresso")) { Intents.sensitive(it) }

    @Test fun senzaTokenOgniApriSiConferma() {
        assertTrue(noToken("apri ingresso"))
        assertTrue(noToken("sblocca box"))
        assertTrue(noToken("open front"))
    }

    @Test fun senzaTokenTapparelleETendeNo() {
        assertFalse(noToken("apri le tapparelle"))
        assertFalse(noToken("apri tenda salotto"))
        assertFalse(noToken("open the blinds"))
    }

    @Test fun senzaTokenLuciEChiudereNo() {
        assertFalse(noToken("accendi luce ingresso"))
        assertFalse(noToken("chiudi ingresso"))
    }

    @Test fun conTokenNonCambiaNiente() {
        assertFalse(HomeRisk.needsAuth(w("apri ingresso"), emptyList(), false, namesKnown = true))
    }

    @Test fun pulsantiCheApronoSiConfermano() {
        assertTrue(button("Qualcuno al citofono", "Apri cancello"))
        assertTrue(button("Apro il cancello?", "Sì"))
        assertTrue(button("Notifica", "Disarma"))
        assertTrue(button("Arrivo a casa", "Sblocca ingresso"))
    }

    @Test fun pulsantiInnocuiNo() {
        assertFalse(button("Apro il cancello?", "No"))
        assertFalse(button("Il cancello è aperto, lo chiudo?", "Sì"))
        assertFalse(button("Lavatrice finita", "Ok"))
        assertFalse(button("Cancello aperto da 10 minuti", "Chiudi cancello"))
        assertFalse(button("Apro il cancello?", "Più tardi"))
    }
}
