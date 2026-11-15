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
