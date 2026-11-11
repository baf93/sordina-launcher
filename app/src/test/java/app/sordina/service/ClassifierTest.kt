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
    }
}
