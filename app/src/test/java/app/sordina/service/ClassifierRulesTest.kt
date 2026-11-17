package app.sordina.service

import android.app.Notification
import app.sordina.data.CardDetector
import app.sordina.data.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** La catena delle regole, caso per caso: stesso ingresso, stesso verdetto. */
class ClassifierRulesTest {
    private val now = 1_800_000_000_000L
    private val base = RuleSnapshot(self = "app.sordina", now = now, contacts = setOf("anna rossi", "marco"))

    private fun msg(pkg: String = "com.whatsapp", person: String? = "Anna Rossi", text: String = "ciao", conversation: String? = null, burst: Int = 1) =
        NotifInfo(pkg, Notification.CATEGORY_MESSAGE, false, false, false, person, conversation, text, person ?: "", true, 1,
            burst = burst, peer = conversation ?: person)

    private fun app(pkg: String = "com.shop", title: String = "Offerta", text: String = "Sconti", category: String? = null, channel: String? = null) =
        NotifInfo(pkg, category, false, false, false, null, null, text, title, false, 1, channel = channel)

    private fun level(n: NotifInfo, s: RuleSnapshot = base) = Classifier.classify(n, s).level
    private fun reason(n: NotifInfo, s: RuleSnapshot = base) = Classifier.classify(n, s).reason

    @Test fun defaultWaits() = assertEquals(Level.RIEPILOGO, level(app()))

    @Test fun selfAndCallsPassUntouched() {
        assertNull(level(app(pkg = "app.sordina")))
        assertNull(level(app(category = Notification.CATEGORY_CALL)))
        assertNull(level(app(category = Notification.CATEGORY_ALARM)))
    }

    @Test fun appInLetsEverythingThrough() {
        val s = base.copy(inApps = setOf("com.shop"))
        assertEquals(Level.SEMPRE, level(app(), s))
        assertEquals(Classifier.REASON_DEFAULT, reason(app(pkg = "com.other"), s))
    }

    @Test fun offTypeIsAnExceptionEvenForStickyButNeverForSecurity() {
        val s = base.copy(inApps = setOf("com.bank"), offTypes = setOf("com.bank|alerts"))
        assertEquals(Classifier.REASON_CHANNEL, reason(app(pkg = "com.bank", channel = "alerts"), s))
        assertEquals(Classifier.REASON_CHANNEL, reason(app(pkg = "com.bank", channel = "alerts", category = Notification.CATEGORY_CALL), s))
        assertEquals(Level.SEMPRE, level(app(pkg = "com.bank", channel = "other"), s))
        assertEquals(Classifier.REASON_OTP, reason(app(pkg = "com.bank", channel = "alerts", text = "Il tuo codice di verifica è 482913"), s))
    }

    @Test fun offChatIsPerApp() {
        val s = base.copy(inApps = setOf("com.whatsapp", "org.telegram.messenger"), offChats = setOf("com.whatsapp|famiglia"))
        assertEquals(Classifier.REASON_PERSON, reason(msg(conversation = "Famiglia"), s))
        // Lo stesso nome su Telegram non è toccato dall'eccezione di WhatsApp
        assertEquals(Level.SEMPRE, level(msg(pkg = "org.telegram.messenger", conversation = "Famiglia"), s))
    }

    @Test fun personInOnlyFromThatApp() {
        val s = base.copy(peopleIn = setOf("com.whatsapp|anna rossi"))
        assertEquals(Classifier.REASON_PERSON, reason(msg(), s))
        assertEquals(Level.SUBITO, level(msg(), s))
        assertEquals(Classifier.REASON_DEFAULT, reason(msg(pkg = "org.telegram.messenger"), s))
    }

    @Test fun offChatBeatsPersonEverywhere() {
        val s = base.copy(important = setOf("anna rossi"), importantInGroups = setOf("anna rossi"), offChats = setOf("com.whatsapp|calcetto"))
        assertEquals(Classifier.REASON_PERSON, reason(msg(conversation = "Calcetto"), s))
        assertEquals(Level.RIEPILOGO, level(msg(conversation = "Calcetto"), s))
    }

    @Test fun brakedWaitsEvenForImportant() {
        val s = base.copy(braked = setOf("com.whatsapp"), important = setOf("anna rossi"))
        assertEquals(Classifier.REASON_BRAKED, reason(msg(), s))
        // Messa fra le app che entrano, il freno non la trattiene
        assertEquals("persona importante", reason(msg(), s.copy(inApps = setOf("com.whatsapp"))))
        assertEquals(Level.SEMPRE, level(msg(person = "Marco"), s.copy(inApps = setOf("com.whatsapp"))))
    }

    @Test fun missedAndRemindersCanBeTurnedOff() {
        assertEquals(Classifier.REASON_MISSED, reason(app(category = "missed_call")))
        assertEquals(Classifier.REASON_DEFAULT, reason(app(category = "missed_call"), base.copy(missedPass = false)))
        assertEquals(Classifier.REASON_REMINDER, reason(app(category = Notification.CATEGORY_REMINDER)))
        assertEquals(Classifier.REASON_DEFAULT, reason(app(category = Notification.CATEGORY_REMINDER), base.copy(remindPass = false)))
    }

    @Test fun otpAndPaymentAlertsPass() {
        assertEquals(Classifier.REASON_OTP, reason(app(pkg = "com.bank", title = "Banca", text = "Il tuo codice di verifica è 482913")))
        assertEquals(Classifier.REASON_PAY_ALERT, reason(app(pkg = "com.bank", title = "Banca", text = "Transazione sospetta di 450,00 € con la tua carta. Sei stato tu?")))
        assertEquals(Classifier.REASON_PAY_ALERT, reason(app(pkg = "com.bank", title = "Banca", text = "Conferma il pagamento di 49,90 € su Amazon nell'app entro 5 minuti")))
        assertEquals(Classifier.REASON_PAY_ALERT, reason(app(pkg = "com.bank", title = "Banca", text = "Pagamento rifiutato: 120,00 € presso HOTEL")))
        // Un pagamento eseguito aspetta (si vede nella scheda)
        assertEquals("pagamento", reason(app(pkg = "com.bank", title = "Banca", text = "Pagamento di 12,50 € presso BAR")))
    }

    @Test fun securityAlwaysGetsThrough() {
        val s = base.copy(braked = setOf("com.bank"))
        assertEquals(Classifier.REASON_OTP, reason(app(pkg = "com.bank", text = "Codice 482913 per accedere"), s))
    }

    @Test fun hiddenAppWaitsButCodesPass() {
        val hidden = app(pkg = "com.secret").copy(hidden = true)
        assertEquals(Classifier.REASON_HIDDEN, reason(hidden))
        assertEquals(Classifier.REASON_OTP, reason(hidden.copy(text = "Il tuo codice è 482913")))
        assertNull(level(hidden.copy(category = Notification.CATEGORY_CALL)))
    }

    @Test fun talkingPasses() {
        val s = base.copy(talking = setOf(Classifier.talkKey("com.whatsapp", "Anna Rossi")))
        assertNull(level(msg(), s))
        assertEquals(Classifier.REASON_DEFAULT, reason(msg(), s.copy(talkPass = false)))
    }

    @Test fun importantFullNameAndUniqueFirstName() {
        val s = base.copy(important = setOf("anna rossi"))
        assertEquals("persona importante", reason(msg(person = "Anna Rossi"), s))
        assertEquals("persona importante", reason(msg(person = "Anna"), s))
        // Su Telegram il nome lo sceglie chi scrive: serve quello completo
        assertEquals(Classifier.REASON_DEFAULT, reason(msg(pkg = "org.telegram.messenger", person = "Anna"), s))
        // Due Anna importanti: "Anna" da solo non dice quale
        assertEquals(Classifier.REASON_DEFAULT, reason(msg(person = "Anna"), s.copy(important = setOf("anna rossi", "anna bianchi"))))
    }

    @Test fun importantInGroupsOnlyIfChosen() {
        val s = base.copy(important = setOf("anna rossi"))
        assertEquals(Classifier.REASON_DEFAULT, reason(msg(conversation = "Calcetto"), s))
        assertEquals("persona importante", reason(msg(conversation = "Calcetto"), s.copy(importantInGroups = setOf("anna rossi"))))
    }

    @Test fun urgentOnlyFromContacts() {
        assertEquals("urgente", reason(msg(person = "Marco", text = "urgente chiamami")))
        // Lo smishing da un numero sconosciuto non decide quanto conta
        assertEquals(Classifier.REASON_DEFAULT, reason(msg(pkg = "com.google.android.apps.messaging", person = "+39 333 1234567",
            text = "URGENTE: il tuo pacco è in giacenza, paga 1,99 €")))
        assertEquals("insiste", reason(msg(person = "Marco", burst = 8)))
        assertEquals(Classifier.REASON_DEFAULT, reason(msg(person = "Sconosciuto", burst = 8)))
    }

    @Test fun quietHoldsOnlyRuleBasedPasses() {
        val q = base.copy(quiet = true)
        assertEquals("modalità", reason(app(category = "missed_call"), q))
        assertEquals("modalità", reason(msg(), q.copy(peopleIn = setOf("com.whatsapp|anna rossi"))))
        assertEquals(Classifier.REASON_OTP, reason(app(text = "Il tuo codice è 482913"), q))
        assertEquals("persona importante", reason(msg(), q.copy(important = setOf("anna rossi"))))
        assertEquals(Level.SEMPRE, level(app(pkg = "com.x"), q.copy(inApps = setOf("com.x"))))
    }

    @Test fun passUntilWins() {
        val s = base.copy(passUntil = mapOf("a:com.shop" to now + 1000), offChats = setOf("com.shop|x"))
        assertNull(level(app(), s))
        assertEquals(Classifier.REASON_DEFAULT, reason(app(), s.copy(passUntil = mapOf("a:com.shop" to now - 1))))
    }

    @Test fun deterministic() {
        val n = msg(text = "domani alle 20 da me")
        val first = Classifier.classify(n, base)
        repeat(100) { assertEquals(first, Classifier.classify(n, base)) }
    }

    @Test fun otpWords() {
        assertTrue(Classifier.isOtp("Il tuo codice è 482 913"))
        assertTrue(Classifier.isOtp("La tua chiave di accesso: 739102"))
        assertTrue(Classifier.isOtp("Password monouso 552190, valida 5 minuti"))
        assertTrue(Classifier.isOtp("Verification: 482913. Do not share it."))
        assertFalse(Classifier.isOtp("Ti ho lasciato il pin del portone: 2580"))
        assertFalse(Classifier.isOtp("La password del wifi è 12345678"))
        assertFalse(Classifier.isOtp("Verifica lo stato del tuo ordine 55823901"))
        assertFalse(Classifier.isOtp("Riunione 2026: inserisci il pin della sala alle 15:30"))
    }

    @Test fun paymentAlertWords() {
        assertTrue(Classifier.isPaymentAlert("Tentativo di pagamento non autorizzato"))
        assertTrue(Classifier.isPaymentAlert("Was this you? Card declined"))
        assertFalse(Classifier.isPaymentAlert("Pagamento di 12,50 € presso BAR"))
        assertTrue(CardDetector.paymentCents("Pagamento di 12,50 € presso BAR") != null)
    }

    @Test fun importantKeyRules() {
        val set = setOf("anna rossi", "marco")
        assertEquals("anna rossi", Classifier.importantKey("Anna", set, exact = false))
        assertNull(Classifier.importantKey("Anna", set, exact = true))
        assertNull(Classifier.importantKey("Marco Bianchi", set, exact = false))
        assertEquals("marco", Classifier.importantKey("marco", set, exact = true))
    }
}
