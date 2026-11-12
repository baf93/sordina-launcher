package app.sordina.data

import app.sordina.tr

import app.sordina.R

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.time.ZoneId
import java.time.ZoneOffset

data class Cal(val id: Long, val name: String, val account: String, val primary: Boolean)

/** Scrittura diretta nel calendario scelto: nessuna app da aprire. */
class Calendars(private val context: Context, private val prefs: Prefs) {

    fun hasPermission() =
        context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Calendari visibili in cui si può scrivere. */
    fun list(): List<Cal> {
        if (!hasPermission()) return emptyList()
        val out = ArrayList<Cal>()
        runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.ACCOUNT_NAME,
                    CalendarContract.Calendars.IS_PRIMARY,
                ),
                "${CalendarContract.Calendars.VISIBLE}=1 AND ${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL}>=?",
                arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    out += Cal(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getInt(3) == 1)
                }
            }
        }
        return out.sortedWith(compareByDescending<Cal> { it.primary }.thenBy { it.name })
    }

    fun default(): Cal? {
        val all = list()
        return all.firstOrNull { it.id == prefs.calendarId } ?: all.firstOrNull { it.primary } ?: all.firstOrNull()
    }

    /** Inserisce un impegno; restituisce l'id per poterlo annullare. */
    fun insert(cal: Cal, title: String, w: When, minutes: Int = 60): Long? = runCatching {
        val v = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, cal.id)
            put(CalendarContract.Events.TITLE, title)
            if (w.allDay) {
                val start = w.start.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
                put(CalendarContract.Events.DTSTART, start)
                put(CalendarContract.Events.DTEND, start + 86_400_000L)
                put(CalendarContract.Events.ALL_DAY, 1)
                put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
            } else {
                val start = w.start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                put(CalendarContract.Events.DTSTART, start)
                put(CalendarContract.Events.DTEND, start + minutes * 60_000L)
                put(CalendarContract.Events.EVENT_TIMEZONE, ZoneId.systemDefault().id)
            }
        }
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, v) ?: return null
        val id = ContentUris.parseId(uri)
        if (!w.allDay) {
            val r = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, id)
                put(CalendarContract.Reminders.MINUTES, 15)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            runCatching { context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, r) }
        }
        id
    }.getOrNull()

    fun delete(eventId: Long) {
        runCatching {
            context.contentResolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), null, null)
        }
    }
}

data class Upcoming(val id: Long, val title: String, val begin: Long, val end: Long, val location: String?, val link: String?, val allDay: Boolean)

/** Eventi da 2 ore prima dell'inizio fino alla fine. Basta il permesso di lettura. */
fun Calendars.upcoming(context: Context, now: Long = System.currentTimeMillis()): List<Upcoming> {
    if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return emptyList()
    val out = ArrayList<Upcoming>()
    runCatching {
        val b = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(b, now - 12 * 3600_000L)
        ContentUris.appendId(b, now + 2 * 3600_000L)
        context.contentResolver.query(
            b.build(),
            arrayOf(
                CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
                CalendarContract.Instances.EVENT_LOCATION, CalendarContract.Instances.DESCRIPTION,
                CalendarContract.Instances.ALL_DAY,
            ),
            "${CalendarContract.Instances.VISIBLE}=1", null, "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { c ->
            val link = Regex("https://(meet\\.google\\.com|[\\w.-]*zoom\\.us|teams\\.microsoft\\.com|teams\\.live\\.com)/\\S+")
            while (c.moveToNext()) {
                if (c.getInt(6) == 1) continue
                val begin = c.getLong(2); val end = c.getLong(3)
                if (end <= now || begin - now > 2 * 3600_000L) continue
                val loc = c.getString(4)?.trim()?.takeIf { it.isNotBlank() }
                val desc = c.getString(5).orEmpty()
                val url = link.find(loc.orEmpty())?.value ?: link.find(desc)?.value
                out += Upcoming(c.getLong(0), c.getString(1).orEmpty().ifBlank { tr(R.string.event) }, begin, end,
                    loc?.takeIf { url == null || !it.startsWith("http") }, url, false)
            }
        }
    }
    return out
}

/** Il prossimo evento con orario nei prossimi 7 giorni (o quello in corso). */
fun Calendars.next(context: Context, now: Long = System.currentTimeMillis()): Upcoming? {
    if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return null
    return runCatching {
        val b = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(b, now)
        ContentUris.appendId(b, now + 7 * 86_400_000L)
        context.contentResolver.query(
            b.build(),
            arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END, CalendarContract.Instances.EVENT_LOCATION, CalendarContract.Instances.ALL_DAY),
            "${CalendarContract.Instances.VISIBLE}=1 AND ${CalendarContract.Instances.ALL_DAY}=0", null,
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getLong(3) <= now) continue
                return@runCatching Upcoming(c.getLong(0), c.getString(1).orEmpty().ifBlank { tr(R.string.event) }, c.getLong(2), c.getLong(3),
                    c.getString(4)?.takeIf { it.isNotBlank() }, null, false)
            }
            null
        }
    }.getOrNull()
}

/** Il programma: gli eventi con orario rimasti oggi (al massimo 3); se non ce ne sono, il prossimo nei 7 giorni. */
fun Calendars.agenda(context: Context, now: Long = System.currentTimeMillis()): List<Upcoming> {
    if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return emptyList()
    val endOfDay = java.time.LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val out = ArrayList<Upcoming>()
    runCatching {
        val b = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(b, now)
        ContentUris.appendId(b, endOfDay)
        context.contentResolver.query(
            b.build(),
            arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END, CalendarContract.Instances.EVENT_LOCATION),
            "${CalendarContract.Instances.VISIBLE}=1 AND ${CalendarContract.Instances.ALL_DAY}=0", null,
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { c ->
            while (c.moveToNext() && out.size < 3) {
                if (c.getLong(3) <= now) continue
                out += Upcoming(c.getLong(0), c.getString(1).orEmpty().ifBlank { tr(R.string.event) }, c.getLong(2), c.getLong(3),
                    c.getString(4)?.takeIf { it.isNotBlank() }, null, false)
            }
        }
    }
    return out.ifEmpty { listOfNotNull(next(context, now)) }
}
