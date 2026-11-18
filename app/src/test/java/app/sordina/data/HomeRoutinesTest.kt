package app.sordina.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeRoutinesTest {
    private fun w(s: String) = s.split(' ')
    private val on = mapOf("scene.buonanotte" to "Buonanotte", "scene.esci" to "Scena Esci", "script.cinema_inizio" to "Cinema Inizio")

    @Test fun byNameWithOrWithoutLeadWords() {
        assertEquals("scene.buonanotte", HomeRoutines.match(w("buonanotte"), on))
        assertEquals("scene.buonanotte", HomeRoutines.match(w("attiva la scena buonanotte"), on))
        assertEquals("scene.esci", HomeRoutines.match(w("esci"), on))
        assertEquals("script.cinema_inizio", HomeRoutines.match(w("lancia cinema inizio"), on))
    }

    @Test fun onlyTheWholeName() {
        assertNull(HomeRoutines.match(w("cinema"), on))
        assertNull(HomeRoutines.match(w("buonanotte a tutti"), on))
        assertNull(HomeRoutines.match(w("attiva"), on))
        assertNull(HomeRoutines.match(w("buonanotte"), emptyMap()))
    }

    @Test fun yourPhrasesStartIt() {
        val f = mapOf("scene.entra" to listOf("sto entrando", "Sono a casa"))
        val names = mapOf("scene.entra" to "Entra")
        assertEquals("scene.entra", HomeRoutines.match(w("sto entrando"), names, f))
        assertEquals("scene.entra", HomeRoutines.match(w("sono a casa"), names, f))
        assertEquals("scene.entra", HomeRoutines.match(w("entra"), names, f))
        assertNull(HomeRoutines.match(w("sto entrando in macchina"), names, f))
    }

    @Test fun phrasesFromSettings() {
        assertEquals(listOf("sto entrando", "sono a casa"), HomeRoutines.parsePhrases("sto entrando\n\n sono a casa , sto entrando"))
    }
}
