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
}
