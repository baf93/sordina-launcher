package app.sordina.data

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * I calendari del telefono nel calendario di Sordina: leggere, creare, cambiare, cancellare. Si scrive subito, il
 * telefono sincronizza da solo con il suo account. Gli impegni di tutto il giorno Android li tiene a mezzanotte UTC:
 * qui diventano mezzanotte dell'ora di qui, come quelli di casa.
 */
class PhoneCalendar(private val context: Context, private val prefs: Prefs) {

    private fun can(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    val readable: Boolean get() = can(Manifest.permission.READ_CALENDAR)
    val writable: Boolean get() = readable && can(Manifest.permission.WRITE_CALENDAR)

    /** Gli id dei calendari del telefono scelti; null: nessuna scelta, tutti i visibili */
    private fun chosen(): Set<Long>? {
        val c = prefs.homeCalendars
        if (c.isEmpty()) return null
        return (c.filter { it.startsWith(CalBook.PHONE) }.mapNotNull { it.drop(2).toLongOrNull() } +
            listOfNotNull(prefs.calendarId.takeIf { it >= 0 && prefs.haCalendar.isBlank() })).toSet()
    }

    fun books(): List<CalBook> {
        if (!readable) return emptyList()
        val only = chosen()
        val out = ArrayList<CalBook>()
        runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.CALENDAR_COLOR, CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL),
                "${CalendarContract.Calendars.VISIBLE}=1", null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    if (only != null && id !in only) continue
                    val w = writable && c.getInt(3) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR
                    out += CalBook(CalBook.PHONE + id, c.getString(1).orEmpty(), c.getInt(2), w, w, w)
                }
            }
        }
        return out.sortedBy { it.name.lowercase() }
    }

    fun events(from: Long, to: Long): List<CalEvent> {
        if (!readable) return emptyList()
        val only = chosen()
        if (only != null && only.isEmpty()) return emptyList()
        val zone = ZoneId.systemDefault()
        val out = ArrayList<CalEvent>()
        runCatching {
            val b = Instances.CONTENT_URI.buildUpon()
            // Un giorno in più per parte: gli impegni di tutto il giorno sono in UTC
            ContentUris.appendId(b, from - 86_400_000L)
            ContentUris.appendId(b, to + 86_400_000L)
            val where = "${Instances.VISIBLE}=1" + (only?.let { " AND ${Instances.CALENDAR_ID} IN (${it.joinToString(",")})" } ?: "")
            context.contentResolver.query(
                b.build(),
                arrayOf(Instances.EVENT_ID, Instances.TITLE, Instances.BEGIN, Instances.END, Instances.ALL_DAY,
                    Instances.EVENT_LOCATION, Instances.DESCRIPTION, Instances.RRULE, Instances.CALENDAR_ID),
                where, null, "${Instances.BEGIN} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val allDay = c.getInt(4) == 1
                    val begin = c.getLong(2); val end = c.getLong(3)
                    val s = if (allDay) utcDay(begin, zone) else begin
                    val e = if (allDay) utcDay(end, zone).coerceAtLeast(s + 1) else end
                    if (e <= from || s >= to) continue
                    val rrule = c.getString(7)?.takeIf { it.isNotBlank() }
                    out += CalEvent(CalBook.PHONE + c.getLong(8), c.getLong(0).toString(), if (rrule != null) begin.toString() else null,
                        c.getString(1).orEmpty(), s, e, allDay, c.getString(5)?.takeIf { it.isNotBlank() },
                        c.getString(6)?.takeIf { it.isNotBlank() }, rrule)
                }
            }
        }
        return out
    }

    fun create(d: Draft): Boolean = writable && runCatching {
        val v = values(d, withRule = true).apply { put(Events.CALENDAR_ID, d.book.removePrefix(CalBook.PHONE).toLong()) }
        val uri = context.contentResolver.insert(Events.CONTENT_URI, v) ?: return false
        if (!d.allDay) remind(ContentUris.parseId(uri))
        true
    }.getOrDefault(false)

    /** Su un impegno che si ripete: [all] questa volta e le prossime (la serie si chiude qui e ne riparte una nuova), se no solo questa */
    fun update(e: CalEvent, d: Draft, all: Boolean): Boolean = writable && runCatching {
        val id = e.uid.toLong()
        val uri = ContentUris.withAppendedId(Events.CONTENT_URI, id)
        val instance = e.recurrenceId?.toLongOrNull()
        when {
            e.rrule == null || instance == null -> context.contentResolver.update(uri, values(d, withRule = true), null, null) > 0
            !all -> {
                val v = values(d, withRule = false).apply { put(Events.ORIGINAL_INSTANCE_TIME, instance) }
                context.contentResolver.insert(ContentUris.withAppendedId(Events.CONTENT_EXCEPTION_URI, id), v) != null
            }
            // La prima volta: cambia tutta la serie
            instance <= (series(id) ?: instance) -> context.contentResolver.update(uri, values(d, withRule = true), null, null) > 0
            else -> {
                // La serie di prima finisce prima di questa volta; da qui ne comincia una nuova, con le modifiche
                val until = until(instance - 1000L, e.allDay)
                val closed = ContentValues().apply { put(Events.RRULE, withUntil(e.rrule, until)) }
                if (context.contentResolver.update(uri, closed, null, null) <= 0) return false
                val cal = calendarOf(id) ?: return false
                val v = values(d.copy(rrule = d.rrule ?: e.rrule), withRule = true).apply { put(Events.CALENDAR_ID, cal) }
                context.contentResolver.insert(Events.CONTENT_URI, v) != null
            }
        }
    }.getOrDefault(false)

    private fun calendarOf(id: Long): Long? = context.contentResolver.query(
        ContentUris.withAppendedId(Events.CONTENT_URI, id), arrayOf(Events.CALENDAR_ID), null, null, null,
    )?.use { if (it.moveToFirst()) it.getLong(0) else null }

    fun delete(e: CalEvent, all: Boolean): Boolean = writable && runCatching {
        val id = e.uid.toLong()
        if (e.rrule != null && !all && e.recurrenceId != null) {
            val v = ContentValues().apply {
                put(Events.ORIGINAL_INSTANCE_TIME, e.recurrenceId.toLong())
                put(Events.STATUS, Events.STATUS_CANCELED)
            }
            context.contentResolver.insert(ContentUris.withAppendedId(Events.CONTENT_EXCEPTION_URI, id), v) != null
        } else {
            context.contentResolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, id), null, null) > 0
        }
    }.getOrDefault(false)

    /** L'inizio della prima volta di una serie, come è salvato */
    private fun series(id: Long): Long? = context.contentResolver.query(
        ContentUris.withAppendedId(Events.CONTENT_URI, id), arrayOf(Events.DTSTART), null, null, null,
    )?.use { if (it.moveToFirst()) it.getLong(0) else null }

    private fun values(d: Draft, withRule: Boolean) = ContentValues().apply {
        put(Events.TITLE, d.title)
        put(Events.EVENT_LOCATION, d.location.orEmpty())
        put(Events.DESCRIPTION, d.notes.orEmpty())
        val zone = ZoneId.systemDefault()
        val start = if (d.allDay) utcMidnight(d.start, zone) else d.start
        val end = if (d.allDay) utcMidnight(d.end, zone).let { if (it <= start) start + 86_400_000L else it } else maxOf(d.end, d.start + 60_000L)
        put(Events.DTSTART, start)
        put(Events.ALL_DAY, if (d.allDay) 1 else 0)
        put(Events.EVENT_TIMEZONE, if (d.allDay) "UTC" else zone.id)
        val rule = d.rrule?.removePrefix("RRULE:")?.takeIf { it.isNotBlank() }
        if (withRule && rule != null) {
            // Chi si ripete ha una durata, non una fine
            put(Events.RRULE, rule)
            put(Events.DURATION, if (d.allDay) "P${(end - start) / 86_400_000L}D" else "P${(end - start) / 1000}S")
            putNull(Events.DTEND)
        } else {
            put(Events.DTEND, end)
            if (withRule) { putNull(Events.RRULE); putNull(Events.DURATION) }
        }
    }

    private fun remind(id: Long) {
        runCatching {
            context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, id)
                put(CalendarContract.Reminders.MINUTES, 15)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            })
        }
    }

    companion object {
        /** La regola con una fine: senza UNTIL e COUNT di prima, con UNTIL=[until] */
        fun withUntil(rule: String, until: String): String =
            (rule.removePrefix("RRULE:").split(';').filter { p -> p.isNotBlank() && !p.startsWith("UNTIL=") && !p.startsWith("COUNT=") } + "UNTIL=$until").joinToString(";")

        /** UNTIL nel formato della regola: la data per il tutto il giorno, se no l'istante in UTC */
        fun until(t: Long, allDay: Boolean): String {
            val z = Instant.ofEpochMilli(t).atZone(ZoneOffset.UTC)
            return if (allDay) java.time.format.DateTimeFormatter.BASIC_ISO_DATE.format(z.toLocalDate())
            else java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").format(z)
        }

        /** Mezzanotte UTC (come Android salva il tutto il giorno) → mezzanotte di qui dello stesso giorno */
        fun utcDay(t: Long, zone: ZoneId): Long =
            Instant.ofEpochMilli(t).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

        /** Mezzanotte di qui → mezzanotte UTC dello stesso giorno */
        fun utcMidnight(t: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
            Instant.ofEpochMilli(t).atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
}
