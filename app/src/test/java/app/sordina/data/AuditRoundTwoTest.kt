package app.sordina.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le correzioni del secondo giro di audit che si possono provare senza telefono. */
class AuditRoundTwoTest {
    private fun link(s: String) = CardDetector.MEETING_LINK.find(s)?.value

    @Test fun meetingLinksOnlyFromRealDomains() {
        assertTrue(link("Entra: https://us02web.zoom.us/j/123456") != null)
        assertTrue(link("https://zoom.us/j/1") != null)
        assertTrue(link("https://acme.webex.com/meet/x") != null)
        assertTrue(link("https://meet.google.com/abc-defg-hij") != null)
        // I sosia non diventano un "Partecipa"
        assertTrue(link("https://evilzoom.us/j/1") == null)
        assertTrue(link("https://fakewebex.com/meet/x") == null)
        assertTrue(link("https://zoom.us.evil.com/j/1") == null)
    }

    private fun sensitive(s: String) = Intents.sensitive(s.split(' ').map(Search::normalize))

    @Test fun homeCommandsThatOpenOrDisarmNeedATap() {
        assertTrue(sensitive("apri il cancello"))
        assertTrue(sensitive("sblocca la porta"))
        assertTrue(sensitive("disattiva allarme"))
        assertTrue(sensitive("öffne die Haustür"))
        assertTrue(sensitive("öffne das Garagentor"))
        assertTrue(sensitive("abre a garagem"))
        assertTrue(sensitive("schalte die Alarmanlage aus"))
        assertTrue(sensitive("unlock the front door"))
    }

    @Test fun ordinaryHomeCommandsStayOneTouch() {
        assertFalse(sensitive("turn on the living room light"))
        assertFalse(sensitive("accendi la luce in sala"))
        assertFalse(sensitive("stell die Temperatur auf 21"))
        assertFalse(sensitive("cancella il timer"))
        assertFalse(sensitive("spegni la tv"))
    }

    @Test fun importNeverTouchesTheHomeConnection() {
        assertTrue("haUrl" in Prefs.NOT_IMPORTED)
        assertTrue("haLocalUrl" in Prefs.NOT_IMPORTED)
        assertTrue("haTokenEnc" in Prefs.NOT_IMPORTED)
        assertTrue("nightAlarm" in Prefs.LONG_KEYS)
    }
}
