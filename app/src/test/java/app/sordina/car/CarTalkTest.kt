package app.sordina.car

import app.sordina.data.Held
import app.sordina.data.Level
import app.sordina.data.Upcoming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parla in auto: le frasi di Sordina (chi aspetta, cosa passa, l'agenda); il resto va al motore della barra */
class CarTalkTest {
    private fun held(person: String, text: String = "ciao", conversation: String? = null, time: Long = 1L) =
        Held("k:$person:$time", "com.whatsapp", "WhatsApp", person, conversation, text, time, Level.RIEPILOGO, 1, isMessage = true)

    private val paolo = listOf(held("Paolo Rossi", "dove sei?"))
    private val calcetto = listOf(held("Luca", "stasera?", conversation = "Calcetto"))
    private val groups = listOf(paolo, calcetto)
    private val events = listOf(
        Upcoming(1, "Calcetto", 2_000L, 3_000L, "Centro sportivo, via dei Pini", null, false),
        Upcoming(2, "Spesa", 4_000L, 5_000L, null, null, false),
    )

    private fun ask(said: String): CarAsk = CarTalk.understand(said, groups, events) { CarAsk.Unknown }

    @Test fun waiting() {
        assertEquals(CarAsk.Waiting, ask("Cosa c'è in sordina?"))
        assertEquals(CarAsk.Waiting, ask("chi mi ha scritto"))
        assertEquals(CarAsk.Waiting, ask("What's waiting?"))
    }

    @Test fun readByFirstName() {
        assertEquals(CarAsk.Read(paolo), ask("leggi Paolo"))
        assertEquals(CarAsk.Read(calcetto), ask("leggimi il calcetto"))
    }

    @Test fun replyWithText() {
        assertEquals(CarAsk.Reply(paolo, "Arrivo tra dieci minuti"), ask("rispondi a Paolo che arrivo tra dieci minuti"))
        // Senza "che": il nome è l'inizio, il resto è la risposta, con le parole come le hai dette
        assertEquals(CarAsk.Reply(paolo, "Sono in macchina, dopo ti chiamo"), ask("rispondi a Paolo sono in macchina, dopo ti chiamo"))
        assertEquals(CarAsk.Reply(paolo, "I'm driving"), ask("reply to Paolo saying I'm driving"))
    }

    @Test fun replyWithoutText() {
        assertEquals(CarAsk.Reply(paolo, null), ask("rispondi a Paolo"))
    }

    @Test fun passing() {
        assertEquals(CarAsk.PassAll(60), ask("fai passare tutti per un'ora"))
        assertEquals(CarAsk.PassAll(120), ask("lascia passare tutto per due ore"))
        assertEquals(CarAsk.PassAll(30), ask("fai passare tutti per mezz'ora"))
        assertEquals(CarAsk.PassAll(60), ask("let everyone through"))
        assertEquals(CarAsk.PassImportant, ask("solo importanti"))
    }

    @Test fun agenda() {
        assertEquals(CarAsk.NextEvent, ask("il prossimo impegno?"))
        assertEquals(CarAsk.GoTo(events[0]), ask("portami al calcetto"))
        // Un impegno senza indirizzo non porta da nessuna parte: decide il motore
        assertEquals(CarAsk.Unknown, ask("portami alla spesa"))
    }

    @Test fun everythingElseGoesToTheEngine() {
        var asked = false
        val r = CarTalk.understand("esco", groups, events) { asked = true; CarAsk.Google }
        assertTrue(asked)
        assertEquals(CarAsk.Google, r)
        // "leggi" senza nessuno in attesa con quel nome: non è una cosa di Sordina
        assertEquals(CarAsk.Unknown, ask("leggi Giovanni"))
    }

    @Test fun groupMatch() {
        assertEquals(paolo, CarTalk.group(groups, listOf("paolo", "rossi")))
        assertNull(CarTalk.group(groups, listOf("giovanni")))
    }
}
