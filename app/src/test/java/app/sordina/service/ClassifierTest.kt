package app.sordina.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassifierTest {
    @Test fun otp() {
        assertTrue(Classifier.isOtp("Il tuo codice di verifica è 482913"))
        assertTrue(Classifier.isOtp("Your verification code: 123-456"))
        assertTrue(Classifier.isOtp("Codice OTP 5521 per autorizzare il pagamento"))
        assertFalse(Classifier.isOtp("Ci vediamo alle 17 al campo"))
        assertFalse(Classifier.isOtp("Codice sconto PROMO20"))
        assertFalse(Classifier.isOtp("Hai speso 12,50 euro"))
        assertFalse(Classifier.isOtp("Notifica Sella: SellaMemoShop Esito Autorizzato: 42.00 EUR presso NEGOZIO 01/02/2026 ora 10:15 - 400000XXXXXX1234 Per blocco carta: 800123456"))
        assertFalse(Classifier.isOtp("NEGOZIO: 1,65 € con Debit Mastercard ••1234"))
        assertFalse(Classifier.isOtp("Rossi: I: Ente - PORTALE - Partner: 518230; Rilevazione: ABCDE; Categoria del messaggio"))
        assertFalse(Classifier.isOtp("Il tuo codice ordine 48291377 è stato spedito"))
        assertFalse(Classifier.isOtp("Riunione 2026: inserisci il pin della sala alle 15:30"))
        assertTrue(Classifier.isOtp("G-482913 è il tuo codice di verifica Google"))
        assertFalse(Classifier.isOtp("Rossi Mario I: Ente Esempio - PORTALE - Partner: 518230; Rilevazione: ABCDE; Categoria del messaggio: notifica di protocollo • Ciao a tutti, in allegato il protocollo di invio della segnalazione riferita al codice ID 772615 Mario Rossi -----Messaggio originale----- Da:"))
        assertFalse(Classifier.isOtp("Mario: il codice 552190 della pratica è pronto -----Messaggio originale-----"))
        assertTrue(Classifier.isOtp("Microsoft: Il codice di sicurezza è 482913"))
        assertTrue(Classifier.isOtp("Use 7731 as your one-time password."))
    }
}
