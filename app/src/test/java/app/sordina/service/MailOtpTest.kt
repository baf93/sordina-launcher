package app.sordina.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** I codici nelle mail: testo lungo (oggetto + corpo), separatori, firme. */
class MailOtpTest {
    private val firma = "\n\nGrazie,\nIl team\n-----\nQuesta è una mail automatica, non rispondere. Informativa privacy: " + "x ".repeat(200)

    @Test fun codiceInMailLunga() {
        val t = "Amazon Il tuo codice monouso\nCiao, per accedere al tuo account usa questo codice:\n\n482913\n\nIl codice scade tra 10 minuti. Non condividerlo con nessuno." + firma
        assertEquals("482913", Classifier.otpNumber(t))
    }

    @Test fun codiceDopoAltriNumeri() {
        val t = "Banca Codice di verifica\nOrdine 2026 del 17/11. Il tuo codice di verifica è 551 204, valido 5 minuti." + firma
        assertEquals("551204", Classifier.otpNumber(t))
    }

    @Test fun englishMail() {
        val t = "Microsoft account Your single-use code\nHi, we received a request to sign in. Your one-time code is: 7731902. If you didn't request this code, you can safely ignore this email." + firma
        assertEquals("7731902", Classifier.otpNumber(t))
    }

    @Test fun newsletterNo() {
        val t = "Negozio Offerte di novembre\nUsa il codice 2580 alla cassa per lo sconto, solo nei negozi aderenti. Scopri le novità della stagione." + firma
        assertNull(Classifier.otpNumber(t))
    }

    @Test fun rispostaNo() {
        assertFalse(Classifier.isOtp("Mario Re: pratica\nVa bene.\n\nDa: Luigi <l@x.it>\nInviato: lunedì\nOggetto: codice 552190 della pratica, accesso"))
    }
}
