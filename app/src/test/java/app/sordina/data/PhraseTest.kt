package app.sordina.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PhraseTest {
    private val hints = object : Phrase.Hints {
        override fun isName(word: String) = word in setOf("elisa", "chiara", "marco", "spotify", "whatsapp")
        override fun lastWriter() = "Elisa"
        override fun lastCaller() = "Marco"
    }

    private fun c(s: String) = Phrase.canonical(s, hints)

    @Test fun politeAndSpoken() {
        assertEquals("chiama Elisa", c("Puoi chiamare Elisa per favore?"))
        assertEquals("chiama Elisa", c("ehi sordina, mi chiami Elisa"))
        assertEquals("chiama Elisa", c("fai una telefonata a Elisa"))
        assertEquals("chiama Elisa", c("telefona ad Elisa"))
        assertEquals("apri WhatsApp", c("potresti aprire WhatsApp"))
        assertEquals("un timer di 5 minuti", c("fammi un timer di 5 minuti"))
    }

    @Test fun messages() {
        assertEquals("scrivi a Elisa che arrivo tra 10 minuti", c("dì a Elisa che arrivo tra 10 minuti"))
        assertEquals("scrivi a Elisa che arrivo", c("rispondi che arrivo"))
        assertEquals("scrivi a Elisa ok", c("rispondi ok"))
        assertEquals("scrivi a Marco ciao", c("rispondi a Marco ciao"))
        assertEquals("scrivi a Elisa su whatsapp", c("manda un whatsapp a Elisa"))
        assertEquals("scrivi a Elisa", c("invia un messaggio a Elisa"))
    }

    @Test fun pronouns() {
        val talking = object : Phrase.Hints by hints { override fun lastPerson() = "Chiara" }
        assertEquals("chiama Chiara", Phrase.canonical("chiamala", talking))
        assertEquals("scrivi a Chiara arrivo tra 5 minuti", Phrase.canonical("scrivile che arrivo tra 5 minuti", talking))
        assertEquals("scrivi a Chiara ok", Phrase.canonical("digli ok", talking))
        // Senza una persona di cui stavi parlando: chi ti ha scritto, chi ti ha chiamato
        assertEquals("scrivi a Elisa ok", c("dille ok"))
        assertEquals("chiama Marco", c("richiamalo"))
    }

    @Test fun replyAndCallBack() {
        assertEquals("chiama Marco", c("richiama"))
        assertEquals("chiama Chiara", c("richiama Chiara"))
    }

    @Test fun typosButNotNames() {
        assertEquals("chiama elisa", c("chiam elisa"))
        assertEquals("scrivi a elisa", c("scirvi a elisa"))
        // Chiara è una persona, non "chiama" scritto male
        assertEquals("Chiara bianchi", c("Chiara bianchi"))
        // Una parola sola resta com'è (potrebbe essere un'app)
        assertEquals("chiam", c("chiam"))
    }

    @Test fun alarmsMusicRoutes() {
        assertEquals("sveglia alle 7", c("metti la sveglia alle 7"))
        assertEquals("sveglia per le 7 e mezza", c("imposta una sveglia per le 7 e mezza"))
        assertEquals("timer 10 minuti", c("avvisami tra 10 minuti"))
        assertEquals("ascolta Vasco Rossi", c("fammi sentire Vasco Rossi"))
        assertEquals("portami a Bologna", c("come arrivo a Bologna"))
        // Quello che il motore capiva già non cambia
        assertEquals("sordina per 45 minuti", c("sordina per 45 minuti"))
        assertEquals("accendi la luce in cucina", c("accendi la luce in cucina"))
        assertEquals("spotify", c("spotify"))
    }
}
