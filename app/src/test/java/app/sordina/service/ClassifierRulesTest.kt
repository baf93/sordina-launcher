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

    private fun app(pkg: String = "com.shop", title: String = "Avviso", text: String = "Novità per te", category: String? = null, channel: String? = null) =
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

    @Test fun mediaStaysEvenWhenItsTypeIsOff() {
        // Spotify: la notifica coi controlli passa anche se il suo canale è stato spento; le altre di quel canale no
        val s = base.copy(offTypes = setOf("com.spotify.music|playback"))
        val player = NotifInfo("com.spotify.music", Notification.CATEGORY_TRANSPORT, true, true, false, null, null, "Artista", "Brano", false, 1, channel = "playback")
        assertNull(level(player, s))
        assertEquals(Classifier.REASON_CHANNEL, reason(app(pkg = "com.spotify.music", channel = "playback"), s))
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
        // Il freno vince su tutto quello che fa passare: l'app che entra, la persona fatta entrare, la chat in corso
        assertEquals(Classifier.REASON_BRAKED, reason(msg(person = "Marco"), s.copy(inApps = setOf("com.whatsapp"))))
        assertEquals(Classifier.REASON_BRAKED, reason(msg(), s.copy(peopleIn = setOf("com.whatsapp|anna rossi"))))
        assertEquals(Classifier.REASON_BRAKED, reason(msg(), s.copy(talking = setOf(Classifier.talkKey("com.whatsapp", "Anna Rossi")))))
        // Passano solo il "lascia passare" chiesto per qualche ora, chiamate e codici
        assertNull(level(msg(), s.copy(passUntil = mapOf("a:com.whatsapp" to now + 1000))))
        assertNull(level(msg().copy(category = Notification.CATEGORY_CALL), s))
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

    @Test fun importantByContactWhateverTheName() {
        val s = base.copy(important = setOf("anna rossi"))
        val tg = msg(pkg = "org.telegram.messenger", person = "Annina")
        assertEquals(Classifier.REASON_DEFAULT, reason(tg, s))
        // L'app dice chi è davvero (il numero in rubrica): entra, qualunque nome mostri
        assertEquals("persona importante", reason(tg.copy(contactName = "anna rossi"), s))
        // Un contatto che non è importante non conta
        assertEquals(Classifier.REASON_DEFAULT, reason(tg.copy(contactName = "marco verdi"), s))
    }

    @Test fun importantByLinkedConversation() {
        val s = base.copy(important = setOf("anna rossi"))
        val tg = msg(pkg = "org.telegram.messenger", person = "Anna").copy(shortcut = "chat-42")
        assertEquals(Classifier.REASON_DEFAULT, reason(tg, s))
        val linked = s.copy(aliases = mapOf("org.telegram.messenger|s:chat-42" to "anna rossi"))
        assertEquals("persona importante", reason(tg, linked))
        // Un'altra conversazione con lo stesso nome visualizzato non entra: il nome su Telegram lo sceglie chi scrive
        assertEquals(Classifier.REASON_DEFAULT, reason(tg.copy(shortcut = "chat-99"), linked))
        // Il collegamento vale solo su quell'app
        assertEquals(Classifier.REASON_DEFAULT, reason(tg.copy(pkg = "com.instagram.android"), linked))
        // Tolta dalle importanti, il collegamento non fa entrare più nessuno
        assertEquals(Classifier.REASON_DEFAULT, reason(tg, linked.copy(important = emptySet())))
    }

    @Test fun nameAliasOnlyWhereNamesComeFromContacts() {
        val s = base.copy(important = setOf("anna rossi"))
        // Un vecchio collegamento per nome su Telegram non vale più: chiunque potrebbe chiamarsi "Anna"
        val tgByName = s.copy(aliases = mapOf("org.telegram.messenger|anna" to "anna rossi"))
        assertEquals(Classifier.REASON_DEFAULT, reason(msg(pkg = "org.telegram.messenger", person = "Anna"), tgByName))
        assertNull(Classifier.aliasKey("org.telegram.messenger", null, "Anna"))
        assertEquals("com.whatsapp|annina", Classifier.aliasKey("com.whatsapp", null, "Annina"))
        assertEquals("org.telegram.messenger|s:x", Classifier.aliasKey("org.telegram.messenger", "x", "Anna"))
    }

    @Test fun urgencyNeedsARealContact() {
        // Su Telegram chi si chiama come un contatto non basta
        assertEquals(Classifier.REASON_DEFAULT, reason(msg(pkg = "org.telegram.messenger", person = "Anna Rossi", text = "URGENTE chiamami")))
        assertEquals(Classifier.REASON_DEFAULT, reason(msg(pkg = "org.telegram.messenger", person = "Marco", burst = 9)))
        // Se l'app dice chi è davvero (numero in rubrica), sì
        assertEquals("urgente", reason(msg(pkg = "org.telegram.messenger", person = "Anna Rossi", text = "URGENTE chiamami").copy(contactName = "anna rossi")))
        // WhatsApp e SMS prendono il nome dalla rubrica
        assertEquals("urgente", reason(msg(text = "URGENTE chiamami")))
    }

    @Test fun codesInGroupsDoNotBreakThrough() {
        val s = base.copy(offChats = setOf("com.whatsapp|gruppo calcetto"))
        val group = msg(person = "Luca", conversation = "Gruppo calcetto", text = "il codice del campo è 4821, accesso dalle 20")
        assertEquals(Classifier.REASON_PERSON, reason(group, s))
        // In una chat a due un codice vero passa ancora
        assertEquals(Classifier.REASON_OTP, reason(msg(person = "Banca", text = "Il tuo codice di accesso è 482913")))
    }

    @Test fun newsIsNotAPaymentAlert() {
        val off = base.copy(inApps = setOf("it.news"), offTypes = setOf("it.news|news"))
        listOf(
            "Frode fiscale" to "Arrestati tre imprenditori",
            "Test" to "Il tampone è negativo per tutti",
            "Senato" to "Il Senato approva la manovra",
            "Allerta" to "Strade bloccate per neve",
            "Ordine" to "Il tuo carrello è bloccato? Sconti fino al 50%",
        ).forEach { (t, x) -> assertEquals(Classifier.REASON_CHANNEL, reason(app(pkg = "it.news", title = t, text = x, channel = "news"), off)) }
        assertFalse(Classifier.isPaymentAlert("Test negativo per tutti"))
        assertTrue(Classifier.isPaymentAlert("Carta bloccata: pagamento sospetto di 89,00 €"))
        assertTrue(Classifier.isPaymentAlert("Conferma il pagamento entro 5 minuti nell'app"))
    }

    @Test fun noGuessAppFollowsTheRules() {
        val code = app(pkg = "com.shop", title = "Il tuo codice", text = "Il codice di accesso è 482913", channel = "promo")
        val pay = app(pkg = "com.shop", title = "Carta bloccata", text = "Pagamento sospetto di 89,00 €", channel = "promo")
        assertEquals(Classifier.REASON_OTP, reason(code))
        assertEquals(Classifier.REASON_PAY_ALERT, reason(pay))
        val s = base.copy(inApps = setOf("com.shop"), offTypes = setOf("com.shop|promo"), noGuess = setOf("com.shop"))
        assertEquals(Classifier.REASON_CHANNEL, reason(code, s))
        assertEquals(Classifier.REASON_CHANNEL, reason(pay, s))
        // Le altre app restano come prima
        assertEquals(Classifier.REASON_OTP, reason(code.copy(pkg = "com.bank"), s))
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
        // Promozioni e chiavi di casa non sono codici di accesso
        assertFalse(Classifier.isOtp("Usa il codice SCONTO20: 1234 valido 10 minuti"))
        assertFalse(Classifier.isOtp("Codice 482913: 20% di sconto valido 10 minuti"))
        assertFalse(Classifier.isOtp("La chiave della cassetta è 4821, valida fino a domenica"))
        assertTrue(Classifier.isOtp("Code: 482913. Do not share it."))
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
