package app.sordina.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class RepeatTest {
    private val rome = ZoneId.of("Europe/Rome")

    @Test fun simpleRules() {
        assertNull(Repeat.parse(null)); assertNull(Repeat.parse("")); assertNull(Repeat.parse("FREQ=HOURLY"))
        val r = Repeat.parse("RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=TH,MO;COUNT=10", rome)!!
        assertEquals(Repeat.Freq.WEEKLY, r.freq); assertEquals(2, r.interval)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), r.days); assertEquals(10, r.count); assertTrue(r.simple)
        assertEquals("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,TH;COUNT=10", r.rule(false, rome))
    }

    @Test fun untilKeepsTheKindOfStart() {
        val r = Repeat(Repeat.Freq.DAILY, until = LocalDate.of(2026, 12, 3))
        assertEquals("FREQ=DAILY;UNTIL=20261203", r.rule(true, rome))
        // Fine del 3 dicembre a Roma = 22:59:59 UTC
        assertEquals("FREQ=DAILY;UNTIL=20261203T225959Z", r.rule(false, rome))
        assertEquals(LocalDate.of(2026, 12, 3), Repeat.parse("FREQ=DAILY;UNTIL=20261203T225959Z", rome)!!.until)
        assertEquals(LocalDate.of(2026, 12, 3), Repeat.parse("FREQ=DAILY;UNTIL=20261203", rome)!!.until)
    }

    @Test fun unknownPartsStay() {
        val r = Repeat.parse("FREQ=MONTHLY;BYDAY=2TU;WKST=MO", rome)!!
        assertFalse(r.simple)
        assertEquals("FREQ=MONTHLY;INTERVAL=3;BYDAY=2TU;WKST=MO", r.copy(interval = 3).rule(false, rome))
        // Ogni giorno feriale: BYDAY con FREQ=DAILY resta com'è
        val w = Repeat.parse("FREQ=DAILY;BYDAY=MO,TU,WE,TH,FR", rome)!!
        assertFalse(w.simple); assertTrue(w.days.isEmpty())
        assertEquals("FREQ=DAILY;BYDAY=MO,TU,WE,TH,FR", w.rule(false, rome))
    }
}
