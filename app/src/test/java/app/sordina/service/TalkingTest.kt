package app.sordina.service

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TalkingTest {
    @After fun reset() = Talking.clear()

    @Test fun forgetClosesTheConversationOfARemovedPerson() {
        val now = 1_000L
        Talking.mark("wa", "Anna Rossi", now)
        Talking.mark("tg", "Anna", now)
        Talking.mark("wa", "Marco", now)
        Talking.forget("anna rossi")
        assertFalse(Talking.active("wa", "Anna Rossi", now + 1))
        assertFalse(Talking.active("tg", "Anna", now + 1))
        assertTrue(Talking.active("wa", "Marco", now + 1))
    }

    @Test fun forgetDoesNotTouchOtherPeopleWithTheSameFirstName() {
        val now = 1_000L
        Talking.mark("wa", "Anna Bianchi", now)
        Talking.forget("anna rossi")
        assertTrue(Talking.active("wa", "Anna Bianchi", now + 1))
    }
}
