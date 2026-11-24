package app.sordina.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DialInTest {
    @Test fun teamsInvite() {
        val desc = """
            Partecipa solo con audio
            +39 02 3046 5432,,987654321#   Italy, Milano
            ID conferenza telefonica: 987 654 321#
        """.trimIndent()
        assertEquals("+390230465432,,987654321#", dialIn(desc))
    }

    @Test fun withDashes() {
        assertEquals("+12065550100,,123456#", dialIn("+1 206-555-0100,,123456# United States, Seattle"))
    }

    @Test fun noNumber() {
        assertNull(dialIn("Riunione di Microsoft Teams\nhttps://teams.microsoft.com/l/meetup-join/abc"))
    }
}
