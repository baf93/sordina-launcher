package app.sordina.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTalkTest {
    private fun s(p: String, learned: Set<String> = emptySet()) = HomeTalk.score(Search.normalize(p).split(' '), learned)

    @Test fun questionsAndCommandsAboutHome() {
        assertEquals(90, s("che luci sono accese"))
        assertEquals(90, s("quanti gradi ci sono in salotto?"))
        assertEquals(90, s("la porta del garage è chiusa"))
        assertEquals(90, s("il robot ha finito?"))
        assertEquals(90, s("quanto consuma la lavatrice adesso"))
        assertEquals(90, s("per favore abbassa le tapparelle in camera"))
        assertEquals(90, s("fai partire la lavatrice"))
    }

    @Test fun lists() {
        assertEquals(92, s("aggiungi il latte alla lista della spesa"))
        assertEquals(92, s("cosa c'è nella lista della spesa"))
    }

    @Test fun routines() {
        assertEquals(84, s("buonanotte"))
        assertEquals(84, s("sto uscendo"))
        assertEquals(84, s("modalità film"))
    }

    @Test fun homeThingsAlone() {
        assertEquals(76, s("luci cucina"))
    }

    @Test fun otherPhrasesStayOnTheWeb() {
        assertNull(s("che tempo fa domani"))
        assertNull(s("luci"))
        assertNull(s("ristoranti vicino a me"))
        assertNull(s("chi ha vinto la partita"))
    }

    private fun ch(p: String) = HomeTalk.channel(Search.normalize(p).split(' '), emptySet())

    @Test fun tvChannels() {
        assertTrue(ch("metti Rai 1"))
        assertTrue(ch("metti la7"))
        assertTrue(ch("guarda il 5"))
        assertTrue(ch("passa su canale 20"))
        assertTrue(ch("metti canale 5 in salotto"))
        assertTrue(ch("metti Sky TG24"))
        assertTrue(!ch("metti Vasco Rossi"))
        assertTrue(!ch("metti la sveglia alle 7"))
        assertTrue(!ch("vai a casa"))
        assertTrue(ch("put on BBC One"))
        assertTrue(ch("turn on CNN"))
        assertTrue(ch("mets TF1"))
        assertTrue(ch("pon Antena 3"))
        assertTrue(ch("pon La Sexta"))
        assertTrue(ch("schalte auf ZDF"))
        assertTrue(ch("metti la sette"))
        assertTrue(!ch("turn off the lights"))
        assertTrue(!ch("put on some music"))
    }

    @Test fun learnedNamesCount() {
        val learned = HomeTalk.learn("accendi la Govee del divano")
        assertTrue("govee" in learned && "divano" in learned)
        assertTrue("accendi" !in learned && "del" !in learned)
        assertEquals(90, s("la govee è accesa?", learned))
        assertNull(s("la govee è accesa?"))
    }
}
