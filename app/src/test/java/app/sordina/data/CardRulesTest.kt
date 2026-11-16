package app.sordina.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

/** Le schede che sbagliavano sul telefono vero. */
class CardRulesTest {
    private val order = """Servizio Clienti CosiCom.: Conferma ordine N° 0039984430
Ciao Fabio,
grazie per aver acquistato da MERCATO' DRIVE (punto vendita gestito da DIMAR ) .
Il tuo ordine è stato inoltrato correttamente.
Luogo e ora di ritiro della tua spesa:
MERCATO' - PIANEZZA
via Piave - S.S. 24 Monginevro - 10044
Pianezza (TO)
venerdì 13 novembre
Fascia oraria di consegna:
ore 16:00 - 16:30
Conserva il tuo N° ordine, ti verrà richiesto al momento del ritiro della spesa.
La modalità di pagamento da te selezionata è pagamento alla consegna con bancomat, carta di credito, carta di debito o prepagata
Dettagli ordine
Ordine N° 0039984430
Effettuato il 13-11-2026
PRODOTTI ACQUISTATI
Selex Natura Chiama Pomodoro Ciliegino 500 g
Prezzo unitario: 3,99 €
1
€ 3,99"""

    @Test fun orderMailIsNotAPayment() {
        assertNull(CardDetector.paymentCents(order))
    }

    @Test fun realPaymentsStillWork() {
        assertEquals(1250L, CardDetector.paymentCents("Pagamento di 12,50 € autorizzato presso ESSELUNGA"))
        assertEquals(399L, CardDetector.paymentCents("Hai pagato € 3,99 con Google Pay"))
        assertEquals(4500L, CardDetector.paymentCents("Addebito carta *1234: EUR 45,00 AMAZON"))
    }

    @Test fun pickupTime() {
        val now = LocalDateTime.of(2026, 11, 13, 9, 30)
        val w = CardDetector.pickupWhen(order, now)
        assertEquals(LocalDateTime.of(2026, 11, 13, 16, 0), w?.start)
    }

    @Test fun onlineMeetingsCanBeJoined() {
        // Outlook: niente link, solo il nome del servizio
        org.junit.Assert.assertTrue(CardDetector.ONLINE_MEETING.containsMatchIn("10:00 - 10:30, Microsoft Teams Meeting"))
        org.junit.Assert.assertTrue(CardDetector.ONLINE_MEETING.containsMatchIn("Riunione di Microsoft Teams"))
        org.junit.Assert.assertFalse(CardDetector.ONLINE_MEETING.containsMatchIn("10:00 - 10:30, Sala Steamboat"))
        assertEquals("https://teams.microsoft.com/l/meetup-join/19%3ameeting_abc/0",
            CardDetector.MEETING_LINK.find("Partecipa: https://teams.microsoft.com/l/meetup-join/19%3ameeting_abc/0 ID riunione")?.value)
        assertEquals("https://us02web.zoom.us/j/123", CardDetector.MEETING_LINK.find("Zoom https://us02web.zoom.us/j/123")?.value)
    }

    @Test fun hidingACodeDoesNotHideTheNextOne() {
        // Stessa notifica riusata dall'app per ogni codice: nascosto il primo, il secondo deve comparire
        val key = "0|com.bank|7|null|10123"
        val first = Card(key, CardKind.OTP, "Banca · codice", "482 913")
        val next = first.copy(title = "739 104")
        val hidden = setOf(Signals.hideKey(first))
        org.junit.Assert.assertTrue(Signals.hidden(first, hidden))
        org.junit.Assert.assertFalse(Signals.hidden(next, hidden))
        // Le schede non nate da notifiche restano nascoste per chiave
        val rain = Card("rain:today", CardKind.RAIN, "Meteo", "Pioggia alle 16")
        org.junit.Assert.assertTrue(Signals.hidden(rain.copy(title = "Pioggia alle 17"), setOf(Signals.hideKey(rain))))
    }
}
