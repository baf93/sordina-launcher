package app.sordina.data

import app.sordina.service.Classifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Casi visti sul telefono vero il 13 novembre 2026. */
class RealPhoneTest {
    @Test fun walletAndRevolutPaymentsAreRecognized() {
        // Google Wallet: titolo il negozio, testo "1,58 € con Revolut Visa ••8230"
        assertEquals(158L, CardDetector.paymentCents("SAPORE DI MARE COLLEGNO 1,58 € con Revolut Visa ••8230"))
        // Revolut: "Spesa di 1,58 €" e poi il saldo: conta la spesa, non il saldo
        assertEquals(158L, CardDetector.paymentCents("Spesa di 1,58 €\nSaldo del Pocket EUR: 276,23 €"))
    }

    @Test fun invisibleMailPaddingIsRemoved() {
        val raw = "Il tuo ordine è arrivato!\n͏‌ ﻿ ͏‌ ﻿ ͏‌ ﻿ \nSe non visualizzi correttamente questa mail"
        val clean = Classifier.clean(raw)
        assertEquals("Il tuo ordine è arrivato!\nSe non visualizzi correttamente questa mail", clean)
        assertEquals("Il tuo ordine è arrivato!", CardDetector.firstLine(clean))
    }

    @Test fun meetingSpanFromOutlook() {
        val day = LocalDate.of(2026, 11, 13)
        val (from, to) = CardDetector.meetingSpan("15:30 - 16:00, Microsoft Teams Meeting", day)!!
        val z = ZoneId.systemDefault()
        assertEquals(day.atTime(15, 30).atZone(z).toInstant().toEpochMilli(), from)
        assertEquals(day.atTime(16, 0).atZone(z).toInstant().toEpochMilli(), to)
        assertNull(CardDetector.meetingSpan("Ci vediamo dopo", day))
        // Oltre la mezzanotte: finisce il giorno dopo
        val (f2, t2) = CardDetector.meetingSpan("23:30–00:30 rilascio", day)!!
        assertTrue(t2 > f2)
    }

    @Test fun whatsappKeysWithNewlinesSurviveTheOldFile() {
        val old = "0|com.google.android.gm|885083296|gig:-1172567875:^sq_ig_i_personal|10293\n" +
            "0|com.whatsapp|1|KG6eikBoMV5+KdJQvvyVSH1JCrcRuxyaK7UtzNcBhRA=\n|10453\n" +
            "0|org.telegram.messenger|59356164|null|10449"
        val keys = KeyList.legacy(old)
        assertEquals(3, keys.size)
        assertEquals("0|com.whatsapp|1|KG6eikBoMV5+KdJQvvyVSH1JCrcRuxyaK7UtzNcBhRA=\n|10453", keys[1])
    }

    @Test fun urgentMessages() {
        assertTrue(Classifier.isUrgent("Chiamami appena puoi"))
        assertTrue(Classifier.isUrgent("È URGENTE, rispondi"))
        assertTrue(Classifier.isUrgent("siamo al pronto soccorso"))
        assertFalse(Classifier.isUrgent("Ci vediamo domani al calcetto"))
        // Parole intere: "sosta" non è "sos"
        assertFalse(Classifier.isUrgent("Dove hai lasciato la sosta?"))
        assertTrue(Classifier.isUrgent("ciao", listOf("ciao")))
        assertNotNull(Classifier.URGENT_WORDS.firstOrNull())
    }
}
