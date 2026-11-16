package app.sordina.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class AlarmToastTest {
    private fun ms(h: Int, m: Int, s: Int = 0, day: Int = 15) = LocalDateTime.of(2026, 11, day, h, m, s).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test fun italian() {
        // Alle 22:30:20 una sveglia per le 7:15: l'Orologio dice "tra 8 ore e 44 minuti"
        assertEquals(ms(7, 15, day = 16), AlarmToast.parse("Sveglia impostata tra 8 ore e 44 minuti.", ms(22, 30, 20)))
        assertEquals(ms(23, 31), AlarmToast.parse("Sveglia impostata tra 1 ora", ms(22, 30, 20)))
        assertEquals(ms(22, 46), AlarmToast.parse("Sveglia impostata tra 15 minuti", ms(22, 30, 20)))
        assertEquals(ms(1, 31, day = 17), AlarmToast.parse("Sveglia impostata tra 1 giorno e 3 ore", ms(22, 30, 20)))
    }

    @Test fun english() {
        assertEquals(ms(7, 15, day = 16), AlarmToast.parse("Alarm set for 8 hours and 44 minutes from now.", ms(22, 30, 20)))
        assertEquals(ms(7, 15, day = 16), AlarmToast.parse("Alarm set for 8 hr 44 min from now", ms(22, 30, 20)))
    }

    @Test fun notAnAlarm() {
        assertNull(AlarmToast.parse("Sveglia eliminata", ms(22, 30)))
    }
}
