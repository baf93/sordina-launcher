package app.sordina.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteTest {
    @Test fun aBareTimeIsNotAnInvite() {
        assertFalse(isInvite("Alle 13 30"))
        assertFalse(isInvite("ok alle 5"))
        assertFalse(isInvite("sono arrivato alle 9"))
    }

    @Test fun realInvites() {
        assertTrue(isInvite("Ci vediamo domani alle 13:30?"))
        assertTrue(isInvite("cena da noi sabato alle 20"))
        assertTrue(isInvite("Dentista giovedì alle 10"))
        assertTrue(isInvite("Dinner on Friday at 8?"))
        assertTrue(isInvite("On se voit demain à 14h"))
        assertTrue(isInvite("¿Quedamos mañana a las 9?"))
        assertTrue(isInvite("Treffen morgen um 10"))
    }
}

class EventMailTest {
    @org.junit.Test fun concerto() {
        org.junit.Assert.assertTrue(isEvent("TicketOne\nI tuoi biglietti per Coldplay - Stadio San Siro, sabato 13 giugno 2027 ore 21:00"))
        org.junit.Assert.assertTrue(isInvite("TicketOne\nI tuoi biglietti per Coldplay"))
        org.junit.Assert.assertTrue(isEvent("Your e-ticket for the concert"))
        org.junit.Assert.assertFalse(isEvent("Il tuo estratto conto di novembre"))
    }
}
