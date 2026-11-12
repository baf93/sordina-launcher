package app.sordina.data

import org.junit.Assert.assertEquals
import org.junit.Test

class OtpCodeTest {
    @Test fun codeAtSentenceEnd() {
        assertEquals("482913", CardDetector.otpCode("Banca Il tuo codice di verifica è 482913. Non condividerlo."))
        assertEquals("482913", CardDetector.otpCode("Codice: 482913, valido 5 minuti"))
        assertEquals("5521", CardDetector.otpCode("Codice OTP 5521 per autorizzare 12.50 EUR"))
        assertEquals("123456", CardDetector.otpCode("Your verification code: 123-456"))
    }
}
