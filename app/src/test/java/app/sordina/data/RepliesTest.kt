package app.sordina.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepliesTest {
    private val day = 86_400_000L
    private val now = 1_800_000_000_000L

    @Test fun learnsYourUsualReplies() {
        val r = Replies(null)
        r.learn("A che ora arrivi?", "Arrivo tra 10 minuti", now - 5 * day)
        r.learn("Quando arrivi?", "Arrivo tra 10 minuti", now - 2 * day)
        r.learn("Ci sei stasera?", "Sì!", now - 3 * day)
        r.learn("Vieni stasera?", "Sì!", now - 1 * day)
        r.learn("Mi mandi il documento?", "Te lo mando dopo, un attimo di pazienza perché sono fuori casa adesso", now)
        // Un messaggio come "a che ora arrivi?" porta alla risposta che dai sempre a quella domanda
        assertEquals("Arrivo tra 10 minuti", r.suggest("Ehi a che ora arrivi?", now = now).first())
        assertEquals("Sì!", r.suggest("Ci vediamo stasera?", now = now).first())
        // Le risposte lunghe non si imparano; quelle dette una volta sola non si propongono
        assertTrue(r.suggest("documento?", now = now).none { it.startsWith("Te lo mando") })
    }
}
