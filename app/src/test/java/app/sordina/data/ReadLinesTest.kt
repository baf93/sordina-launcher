package app.sordina.data

import app.sordina.service.NotifInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ReadLinesTest {
    private val a = Triple(1_000L, "Anna", "ci sei?")
    private val b = Triple(2_000L, "Anna", "domani alle 9")
    private val c = Triple(90_000_000L, "Anna", "buongiorno")

    private fun chat(vararg lines: Triple<Long, String?, String>) =
        NotifInfo("com.whatsapp", "msg", false, false, false, "Anna", null, lines.last().third, "Anna", true, lines.size, lines = lines.toList())

    @Test fun nothingReadKeepsEverything() {
        val n = chat(a, b)
        assertSame(n, ReadLines.unread(n, emptySet()))
    }

    @Test fun theNextMorningOnlyTheNewMessage() {
        // Ieri letti in Sordina; stamattina WhatsApp li ripubblica con quello nuovo
        val seen = ReadLines.fps(chat(a, b)).toSet()
        val back = ReadLines.unread(chat(a, b, c), seen)!!
        assertEquals(listOf(c), back.lines)
        assertEquals(1, back.count)
        assertEquals("buongiorno", back.text)
    }

    @Test fun allReadCloses() {
        val seen = ReadLines.fps(chat(a, b)).toSet()
        assertNull(ReadLines.unread(chat(a, b), seen))
    }

    @Test fun sameTextAtAnotherTimeIsNew() {
        val seen = ReadLines.fps(chat(a)).toSet()
        val again = Triple(5_000L, "Anna", "ci sei?")
        assertEquals(listOf(again), ReadLines.unread(chat(a, again), seen)!!.lines)
    }

    @Test fun notAChatIsUntouched() {
        val mail = NotifInfo("com.microsoft.office.outlook", "email", false, false, false, null, null, "Fattura", "Fattura", false, 1)
        assertSame(mail, ReadLines.unread(mail, setOf(1, 2, 3)))
    }
}
