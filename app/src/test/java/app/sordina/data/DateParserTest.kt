package app.sordina.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** Frasi di prova del pacchetto italiano: date, ore, durate. */
class DateParserTest {
    // Martedì 11 novembre 2026, ore 10:00
    private val now = LocalDateTime.of(2026, 11, 11, 10, 0)

    private fun p(s: String, bias: Boolean = true) = DateParser.parse(s, now, bias)

    @Test fun alle17() {
        val r = p("calcetto alle 17")
        assertEquals(LocalDateTime.of(2026, 11, 11, 17, 0), r.whenAt!!.start)
        assertEquals("calcetto", r.rest)
    }

    @Test fun oraConLoSpazio() {
        assertEquals(LocalDateTime.of(2026, 11, 11, 13, 30), p("Alle 13 30").whenAt!!.start)
        assertEquals(LocalDateTime.of(2026, 11, 11, 13, 30), p("ci vediamo alle 13 e 30").whenAt!!.start)
    }

    @Test fun domaniAlle1730() {
        val r = p("domani alle 17:30 dentista")
        assertEquals(LocalDateTime.of(2026, 11, 12, 17, 30), r.whenAt!!.start)
        assertEquals("dentista", r.rest)
    }

    @Test fun giaPassatoVaADomani() {
        val r = p("riunione alle 9")
        assertEquals(LocalDateTime.of(2026, 11, 12, 9, 0), r.whenAt!!.start)
        assertTrue(r.whenAt!!.movedToTomorrow)
    }

    @Test fun alle5DiventaPomeriggio() {
        assertEquals(17, p("aperitivo alle 5").whenAt!!.start.hour)
        assertEquals(5, p("alle 5", bias = false).whenAt!!.start.hour)
    }

    @Test fun giovedi() {
        val r = p("giovedì cena da Paolo")
        assertEquals(LocalDateTime.of(2026, 11, 12, 0, 0), r.whenAt!!.start)
        assertTrue(r.whenAt!!.allDay)
        assertEquals("cena da paolo", r.rest)
    }

    @Test fun traMezzora() {
        assertEquals(now.plusMinutes(30), p("chiamare mamma tra mezz'ora").whenAt!!.start)
        assertEquals(now.plusMinutes(10), p("tra 10 minuti esco").whenAt!!.start)
    }

    @Test fun stasera() {
        assertEquals(LocalDateTime.of(2026, 11, 11, 20, 0), p("stasera pizza").whenAt!!.start)
        assertEquals(LocalDateTime.of(2026, 11, 11, 21, 0), p("stasera alle 9 pizza").whenAt!!.start)
    }

    @Test fun eMezza() {
        assertEquals(LocalDateTime.of(2026, 11, 11, 18, 30), p("alle 6 e mezza palestra").whenAt!!.start)
    }

    @Test fun dataEsplicita() {
        assertEquals(LocalDateTime.of(2026, 12, 15, 9, 0), p("il 15 dicembre alle 9 visita").whenAt!!.start)
        assertEquals(LocalDateTime.of(2026, 11, 20, 0, 0), p("20/11 compleanno").whenAt!!.start)
    }

    @Test fun nienteData() {
        assertNull(p("comprare il pane").whenAt)
    }

    @Test fun durate() {
        assertEquals(600, DateParser.duration("timer pasta 10 minuti")!!.first)
        assertEquals(1800, DateParser.duration("timer mezz'ora")!!.first)
        assertEquals(4800, DateParser.duration("timer 1 ora e 20")!!.first)
        assertEquals(90, DateParser.duration("timer 90 secondi")!!.first)
        assertEquals(300, DateParser.duration("timer cinque minuti")!!.first)
        assertNull(DateParser.duration("timer pasta"))
    }

    @Test fun pizzaStasera() {
        val r = p("pizza stasera alle 10")
        assertEquals(LocalDateTime.of(2026, 11, 11, 22, 0), r.whenAt!!.start)
        assertEquals("pizza", r.rest)
    }

    @Test fun erroriDiBattitura() {
        assertEquals(LocalDateTime.of(2026, 11, 11, 22, 0), p("pizza staswera alle 10").whenAt!!.start)
        assertEquals(LocalDateTime.of(2026, 11, 12, 9, 0), p("dentista domnai mattina").whenAt?.start?.toLocalDate()?.atTime(9, 0))
        assertEquals(LocalDateTime.of(2026, 11, 13, 18, 0), p("calcetto venedri alle 18").whenAt!!.start)
        // Parole vere vicine a quelle del quando restano come sono
        assertNull(p("pizza dai romani").whenAt)
        assertNull(p("aprire la porta").whenAt)
    }

    @Test fun inglese() {
        // Martedì 11 novembre 2026, ore 10:00
        assertEquals(LocalDateTime.of(2026, 11, 11, 22, 0), p("pizza tonight at 10").whenAt!!.start)
        assertEquals("pizza", p("pizza tonight at 10").rest)
        assertEquals(LocalDateTime.of(2026, 11, 12, 17, 0), p("dentist tomorrow at 5pm").whenAt!!.start)
        assertEquals("dentist", p("dentist tomorrow at 5pm").rest)
        assertEquals(LocalDateTime.of(2026, 11, 12, 7, 30), p("gym tomorrow 7:30am").whenAt!!.start)
        assertEquals(LocalDateTime.of(2026, 11, 13, 20, 0), p("dinner with mark on friday at 8pm").whenAt!!.start)
        assertEquals("dinner with mark", p("dinner with mark on friday at 8").rest)
        assertEquals(LocalDateTime.of(2026, 11, 11, 10, 30), p("call back in 30 minutes").whenAt!!.start)
        assertEquals(LocalDateTime.of(2026, 11, 20, 0, 0), p("party november 20th").whenAt!!.start)
        assertEquals(LocalDateTime.of(2026, 11, 15, 0, 0), p("rent on the 15th").whenAt!!.start)
        assertEquals(LocalDateTime.of(2026, 11, 11, 12, 0), p("lunch at noon").whenAt!!.start)
        assertEquals(LocalDateTime.of(2026, 11, 12, 0, 0), p("deploy at midnight").whenAt!!.start)
        assertEquals(LocalDateTime.of(2026, 11, 11, 18, 0), p("drinks at 6").whenAt!!.start)
        // "may" da solo non è maggio, "in" da solo non è "tra"
        assertNull(p("may the force").whenAt)
        assertNull(p("dinner in town").whenAt)
        assertEquals(600, DateParser.duration("timer 10 minutes")!!.first)
        assertEquals(1800, DateParser.duration("half an hour")!!.first)
        assertEquals(4800, DateParser.duration("1 hour and 20 minutes")!!.first)
        assertEquals(300, DateParser.duration("five minutes")!!.first)
    }
}
