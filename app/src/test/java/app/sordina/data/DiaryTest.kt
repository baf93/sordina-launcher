package app.sordina.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DiaryTest {
    private val rome = ZoneId.of("Europe/Rome")
    private val book = CalBook.HA + "calendar.famiglia"

    @Test fun haTimedEvent() {
        val o = JSONObject("""{"start":{"dateTime":"2026-11-20T09:30:00+01:00"},"end":{"dateTime":"2026-11-20T10:15:00+01:00"},
            "summary":"Dentista","description":"porta la tessera","location":"Via Roma 3","uid":"abc","recurrence_id":null,"rrule":null}""")
        val e = CalEvent.fromHa(book, o, rome)!!
        assertEquals("Dentista", e.title); assertEquals("abc", e.uid); assertFalse(e.allDay)
        assertEquals(45 * 60_000L, e.end - e.start)
        assertEquals("Via Roma 3", e.location); assertNull(e.recurrenceId); assertFalse(e.repeats)
    }

    @Test fun haAllDayEndIsExclusive() {
        val o = JSONObject("""{"start":{"date":"2026-11-20"},"end":{"date":"2026-11-22"},"summary":"Gita","uid":"g","rrule":"FREQ=YEARLY","recurrence_id":"20261120"}""")
        val e = CalEvent.fromHa(book, o, rome)!!
        assertTrue(e.allDay); assertTrue(e.repeats)
        assertEquals(LocalDate.of(2026, 11, 20).atStartOfDay(rome).toInstant().toEpochMilli(), e.start)
        assertEquals(LocalDate.of(2026, 11, 22).atStartOfDay(rome).toInstant().toEpochMilli(), e.end)
        assertEquals("20261120", e.recurrenceId)
    }

    @Test fun badHaEventIsSkipped() { assertNull(CalEvent.fromHa(book, JSONObject("""{"summary":"x"}"""), rome)) }

    @Test fun draftForHomeAssistant() {
        val s = LocalDate.of(2026, 11, 20).atStartOfDay(rome).toInstant().toEpochMilli()
        val all = Draft(book, "Gita", s, s, true, rrule = "RRULE:FREQ=WEEKLY").haEvent(rome)
        assertEquals("2026-11-20", all.getString("dtstart")); assertEquals("2026-11-21", all.getString("dtend"))
        assertEquals("FREQ=WEEKLY", all.getString("rrule")); assertFalse(all.has("location"))
        val timed = Draft(book, "Cena", s + 20 * 3600_000L, s + 22 * 3600_000L, false, location = "Da Mario").haEvent(rome)
        assertEquals("2026-11-20T20:00+01:00", timed.getString("dtstart")); assertEquals("Da Mario", timed.getString("location"))
    }

    @Test fun commandsSayWhichTime() {
        val d = Draft(book, "Calcetto", 0, 3600_000, false)
        val one = Pending(Pending.UPDATE, book, "u1", "20261120T190000", false, d, 0).command()
        assertEquals("calendar/event/update", one.getString("type")); assertEquals("calendar.famiglia", one.getString("entity_id"))
        assertEquals("20261120T190000", one.getString("recurrence_id"))
        val every = Pending(Pending.DELETE, book, "u1", "20261120T190000", true, null, 0).command()
        assertEquals("calendar/event/delete", every.getString("type")); assertFalse(every.has("recurrence_id"))
        assertFalse(Pending(Pending.CREATE, book, "tmp:1", null, true, d, 0).command().has("uid"))
        // La serie, da questa volta in poi
        val following = Pending(Pending.UPDATE, book, "u1", "20261120T190000", true, d.copy(rrule = "FREQ=WEEKLY"), 0).command()
        assertEquals("THISANDFUTURE", following.getString("recurrence_range")); assertEquals("20261120T190000", following.getString("recurrence_id"))
        assertEquals("FREQ=WEEKLY", following.getJSONObject("event").getString("rrule"))
        assertFalse(Pending(Pending.UPDATE, book, "u1", "x", false, d.copy(rrule = "FREQ=WEEKLY"), 0).command().getJSONObject("event").has("rrule"))
    }

    @Test fun pendingRoundTrip() {
        val p = Pending(Pending.UPDATE, book, "u1", null, true, Draft(book, "A", 1, 2, false, notes = "n"), 7)
        assertEquals(p, Pending.fromJson(JSONObject(p.toJson().toString())))
        val e = CalEvent(book, "u", "r", "T", 1, 2, false, "l", null, "FREQ=DAILY")
        assertEquals(e, CalEvent.fromJson(JSONObject(e.toJson().toString())))
    }

    @Test fun pendingOverlay() {
        val a = CalEvent(book, "a", null, "A", 100, 200, false)
        val s1 = CalEvent(book, "s", "1", "Serie", 1000, 1100, false, rrule = "FREQ=DAILY")
        val s2 = CalEvent(book, "s", "2", "Serie", 2000, 2100, false, rrule = "FREQ=DAILY")
        val out = Pending.apply(listOf(a, s1, s2), listOf(
            Pending(Pending.CREATE, book, "tmp:9", null, true, Draft(book, "Nuovo", 300, 400, false), 0),
            Pending(Pending.DELETE, book, "s", "2", false, null, 0),
            Pending(Pending.UPDATE, book, "a", null, true, Draft(book, "A2", 150, 250, false), 0),
        ))
        assertEquals(listOf("A2", "Serie", "Nuovo"), out.map { it.title })
        assertTrue(out.first { it.uid == "tmp:9" }.pending)
        assertEquals(150L, out.first { it.uid == "a" }.start)
        // Dalla seconda volta in poi: rinominate e spostate di 10, la prima resta
        val moved = Pending.apply(listOf(s1, s2), listOf(Pending(Pending.UPDATE, book, "s", "2", true, Draft(book, "Corsa", 2010, 2150, false), 0, since = 2000)))
        assertEquals(listOf("Serie", "Corsa"), moved.map { it.title }); assertEquals(listOf(1000L, 2010L), moved.map { it.start })
        assertEquals(2150L, moved[1].end)
    }

    @Test fun phoneSeriesEndsBeforeThisTime() {
        assertEquals("FREQ=WEEKLY;BYDAY=MO;UNTIL=20261119T225959Z", PhoneCalendar.withUntil("FREQ=WEEKLY;COUNT=10;BYDAY=MO", "20261119T225959Z"))
        assertEquals("20261119T225959Z", PhoneCalendar.until(1795129199000L, false))
        assertEquals("20261119", PhoneCalendar.until(1795129199000L, true))
    }

    @Test fun phoneAllDayConversions() {
        val utc = LocalDate.of(2026, 11, 20).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        val local = PhoneCalendar.utcDay(utc, rome)
        assertEquals(LocalDate.of(2026, 11, 20).atStartOfDay(rome).toInstant().toEpochMilli(), local)
        assertEquals(utc, PhoneCalendar.utcMidnight(local, rome))
    }
}
