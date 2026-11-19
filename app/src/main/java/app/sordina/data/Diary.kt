package app.sordina.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Un calendario del calendario di Sordina: di casa ([HA] + entity_id) o del telefono ([PHONE] + id).
 * Cosa si può fare lo dice lui: Home Assistant con supported_features, Android con il livello d'accesso.
 */
data class CalBook(val key: String, val name: String, val color: Int?, val canCreate: Boolean, val canEdit: Boolean, val canDelete: Boolean) {
    val home: Boolean get() = key.startsWith(HA)
    /** entity_id per casa, id numerico per il telefono */
    val ref: String get() = key.drop(2)

    fun toJson(): JSONObject = JSONObject().put("k", key).put("n", name).put("c", color ?: JSONObject.NULL)
        .put("cc", canCreate).put("ce", canEdit).put("cd", canDelete)

    companion object {
        const val HA = "h:"
        const val PHONE = "p:"
        fun fromJson(o: JSONObject) = CalBook(o.getString("k"), o.optString("n"), if (o.isNull("c")) null else o.optInt("c"),
            o.optBoolean("cc"), o.optBoolean("ce"), o.optBoolean("cd"))
    }
}

/**
 * Un impegno, da qualunque calendario. [uid]: l'uid di Home Assistant o l'id dell'evento del telefono.
 * [recurrenceId]: quale volta di un impegno che si ripete (recurrence_id di casa, l'inizio originale sul telefono).
 * Gli impegni di tutto il giorno vanno da mezzanotte a mezzanotte, nell'ora di qui; [end] escluso.
 */
data class CalEvent(
    val book: String,
    val uid: String,
    val recurrenceId: String?,
    val title: String,
    val start: Long,
    val end: Long,
    val allDay: Boolean,
    val location: String? = null,
    val notes: String? = null,
    val rrule: String? = null,
    /** Fatto senza casa: aspetta di partire */
    val pending: Boolean = false,
) {
    val key: String get() = "$book|$uid|${recurrenceId.orEmpty()}|$start"
    val repeats: Boolean get() = !rrule.isNullOrBlank() || !recurrenceId.isNullOrBlank()

    fun toJson(): JSONObject = JSONObject().put("b", book).put("u", uid).put("r", recurrenceId ?: JSONObject.NULL)
        .put("t", title).put("s", start).put("e", end).put("a", allDay)
        .put("l", location ?: JSONObject.NULL).put("n", notes ?: JSONObject.NULL).put("rr", rrule ?: JSONObject.NULL)

    companion object {
        private fun JSONObject.str(k: String): String? = if (isNull(k)) null else optString(k).takeIf { it.isNotBlank() }

        fun fromJson(o: JSONObject) = CalEvent(o.getString("b"), o.getString("u"), o.str("r"), o.optString("t"), o.getLong("s"), o.getLong("e"),
            o.optBoolean("a"), o.str("l"), o.str("n"), o.str("rr"))

        /** Un impegno come lo dà /api/calendars/<id>: start e end con "dateTime" oppure "date" (tutto il giorno, fine esclusa) */
        fun fromHa(book: String, o: JSONObject, zone: ZoneId = ZoneId.systemDefault()): CalEvent? = runCatching {
            val s = o.getJSONObject("start"); val e = o.optJSONObject("end")
            val allDay = s.has("date") && !s.has("dateTime")
            val start: Long; val end: Long
            if (allDay) {
                val d = LocalDate.parse(s.getString("date"))
                start = d.atStartOfDay(zone).toInstant().toEpochMilli()
                end = (e?.str("date")?.let(LocalDate::parse) ?: d.plusDays(1)).atStartOfDay(zone).toInstant().toEpochMilli()
            } else {
                start = OffsetDateTime.parse(s.getString("dateTime")).toInstant().toEpochMilli()
                end = e?.str("dateTime")?.let { OffsetDateTime.parse(it).toInstant().toEpochMilli() } ?: (start + 3600_000L)
            }
            CalEvent(book, o.str("uid") ?: "${o.optString("summary")}@$start", o.str("recurrence_id"),
                o.str("summary") ?: "", start, end, allDay, o.str("location"), o.str("description"), o.str("rrule"))
        }.getOrNull()
    }
}

/** Un impegno da scrivere: nuovo o cambiato */
data class Draft(
    val book: String,
    val title: String,
    val start: Long,
    val end: Long,
    val allDay: Boolean,
    val location: String? = null,
    val notes: String? = null,
    val rrule: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().put("b", book).put("t", title).put("s", start).put("e", end).put("a", allDay)
        .put("l", location ?: JSONObject.NULL).put("n", notes ?: JSONObject.NULL).put("rr", rrule ?: JSONObject.NULL)

    /** L'impegno come lo vuole Home Assistant (calendar/event/create e update): date per tutto il giorno, se no data e ora con il fuso */
    fun haEvent(zone: ZoneId = ZoneId.systemDefault()): JSONObject {
        val o = JSONObject().put("summary", title)
        if (allDay) {
            val s = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
            val e = Instant.ofEpochMilli(end).atZone(zone).toLocalDate().let { if (it <= s) s.plusDays(1) else it }
            o.put("dtstart", s.toString()).put("dtend", e.toString())
        } else {
            o.put("dtstart", Instant.ofEpochMilli(start).atZone(zone).toOffsetDateTime().toString())
                .put("dtend", Instant.ofEpochMilli(maxOf(end, start + 60_000L)).atZone(zone).toOffsetDateTime().toString())
        }
        location?.takeIf { it.isNotBlank() }?.let { o.put("location", it) }
        notes?.takeIf { it.isNotBlank() }?.let { o.put("description", it) }
        rrule?.takeIf { it.isNotBlank() }?.let { o.put("rrule", it.removePrefix("RRULE:")) }
        return o
    }

    fun asEvent(uid: String, recurrenceId: String? = null, pending: Boolean = false) =
        CalEvent(book, uid, recurrenceId, title, start, end, allDay, location, notes, rrule, pending)

    companion object {
        private fun JSONObject.str(k: String): String? = if (isNull(k)) null else optString(k).takeIf { it.isNotBlank() }
        fun fromJson(o: JSONObject) = Draft(o.getString("b"), o.optString("t"), o.getLong("s"), o.getLong("e"), o.optBoolean("a"),
            o.str("l"), o.str("n"), o.str("rr"))
        fun of(e: CalEvent) = Draft(e.book, e.title, e.start, e.end, e.allDay, e.location, e.notes, e.rrule)
    }
}

/** Una modifica a un calendario di casa fatta senza casa: parte appena Home Assistant risponde, nell'ordine in cui l'hai fatta */
data class Pending(
    val op: String,
    val book: String,
    /** uid dell'impegno (per modifica e cancellazione); per una creazione l'id provvisorio "tmp:…" */
    val uid: String,
    val recurrenceId: String?,
    /** Su un impegno che si ripete: solo questa volta (false) o no. Modificare: questa e le prossime; cancellare: tutte */
    val all: Boolean,
    val draft: Draft?,
    val at: Long,
    /** L'inizio della volta toccata, com'era: da qui in poi cambiano le prossime */
    val since: Long = 0L,
) {
    fun toJson(): JSONObject = JSONObject().put("op", op).put("b", book).put("u", uid).put("r", recurrenceId ?: JSONObject.NULL)
        .put("all", all).put("d", draft?.toJson() ?: JSONObject.NULL).put("at", at).put("since", since)

    /** Il comando per Home Assistant */
    fun command(): JSONObject {
        val entity = book.removePrefix(CalBook.HA)
        val o = JSONObject().put("entity_id", entity)
        when (op) {
            CREATE -> o.put("type", "calendar/event/create").put("event", draft!!.haEvent())
            UPDATE -> o.put("type", "calendar/event/update").put("uid", uid)
                .put("event", draft!!.haEvent().apply { if (!all && recurrenceId != null) remove("rrule") })
            else -> o.put("type", "calendar/event/delete").put("uid", uid)
        }
        if (op != CREATE && recurrenceId != null) when {
            // Solo questa volta: si dice quale
            !all -> o.put("recurrence_id", recurrenceId)
            // Modificare la serie: da questa volta in poi (Home Assistant non dice quando è cominciata, e il passato resta com'era)
            op == UPDATE -> o.put("recurrence_id", recurrenceId).put("recurrence_range", "THISANDFUTURE")
            // Cancellare la serie: tutta, niente recurrence_id
        }
        return o
    }

    companion object {
        const val CREATE = "create"
        const val UPDATE = "update"
        const val DELETE = "delete"
        fun fromJson(o: JSONObject) = Pending(o.getString("op"), o.getString("b"), o.getString("u"),
            if (o.isNull("r")) null else o.optString("r"), o.optBoolean("all"),
            if (o.isNull("d")) null else Draft.fromJson(o.getJSONObject("d")), o.optLong("at"), o.optLong("since"))

        /** Le modifiche in attesa sopra la copia di casa: le nuove compaiono, le cancellate spariscono, le cambiate cambiano */
        fun apply(events: List<CalEvent>, pending: List<Pending>): List<CalEvent> {
            var out = events
            for (p in pending) {
                val series = p.all && p.recurrenceId != null
                fun hits(e: CalEvent) = e.book == p.book && e.uid == p.uid && when {
                    p.recurrenceId == null -> true
                    !p.all -> e.recurrenceId == p.recurrenceId
                    p.op == DELETE -> true
                    else -> e.start >= p.since
                }
                out = when (p.op) {
                    CREATE -> out + p.draft!!.asEvent(p.uid, pending = true)
                    DELETE -> out.filterNot(::hits)
                    else -> out.map { e ->
                        if (!hits(e)) e
                        else {
                            val d = p.draft!!
                            // Questa e le prossime: si spostano tutte di quanto hai spostato questa
                            val s = if (series) e.start + (d.start - p.since) else d.start
                            e.copy(title = d.title, start = s, end = s + (d.end - d.start), allDay = d.allDay,
                                location = d.location, notes = d.notes, pending = true)
                        }
                    }
                }
            }
            return out
        }
    }
}

/**
 * Il calendario di Sordina: i calendari di casa e quelli del telefono insieme.
 *
 * Di casa Sordina tiene una copia da un anno prima a un anno dopo il mese corrente, così il calendario si apre anche
 * senza Home Assistant. Quello che cambi senza casa resta in [pending] e parte appena casa risponde; se Home Assistant
 * dice di no (l'impegno non c'è più, il calendario non si modifica), la modifica si scarta e lo si dice ([problem]).
 * Il telefono non ha bisogno di copie: i suoi calendari sono già lì, e si scrive subito.
 */
class Diary(
    private val context: Context,
    private val prefs: Prefs,
    private val ha: HomeAssistant,
    private val phone: PhoneCalendar,
) {
    /** Cambia a ogni modifica o sincronizzazione: la pagina si ridisegna */
    val version = MutableStateFlow(0)

    private val cacheFile = File(context.filesDir, "calendar_home.json")
    private val pendingFile = File(context.filesDir, "calendar_pending.json")
    private val lock = Any()

    private class Copy(val at: Long, val books: List<CalBook>, val events: List<CalEvent>, val shown: Set<String> = emptySet())
    @Volatile private var copy: Copy? = null
    @Volatile private var pendingList: List<Pending>? = null

    /** Una modifica che casa ha rifiutato: da dire una volta, poi si cancella */
    @Volatile var problem: String? = null

    /** Com'è andata l'ultima volta che si è chiesto a casa: false se non ha risposto */
    @Volatile var reachable: Boolean = true

    /** Il momento dell'ultima copia riuscita (0: mai) */
    val syncedAt: Long get() = loadCopy().at

    // ── Lettura

    /** I calendari da mostrare: quelli di casa scelti (con quello dei nuovi impegni) e quelli del telefono scelti */
    fun books(): List<CalBook> {
        val home = homeShown(loadCopy().books)
        // Home Assistant non dà colori: ognuno il suo, sempre lo stesso per lo stesso calendario
        val haBooks = loadCopy().books.mapIndexed { i, b -> b.copy(color = b.color ?: HOME_COLORS[i % HOME_COLORS.size]) }.filter { it.ref in home }
        return haBooks + phone.books()
    }

    /** Gli impegni fra [from] e [to] di tutti i calendari mostrati, in ordine */
    fun events(from: Long, to: Long): List<CalEvent> {
        val home = homeShown(loadCopy().books).map { CalBook.HA + it }.toSet()
        val mine = Pending.apply(loadCopy().events.filter { it.book in home }, pending().filter { it.book in home })
            .filter { it.end > from && it.start < to }
        return (mine + phone.events(from, to)).sortedWith(compareBy<CalEvent> { !it.allDay }.thenBy { it.start }.thenBy { it.title })
            .sortedBy { it.start - if (it.allDay) 1 else 0 }
    }

    /** Cerca nel titolo, nel luogo e nelle note, da un anno prima a un anno dopo */
    fun search(q: String): List<CalEvent> {
        val n = Search.normalize(q)
        if (n.isBlank()) return emptyList()
        val (from, to) = window()
        return events(from, to).filter { e -> listOfNotNull(e.title, e.location, e.notes).any { Search.normalize(it).contains(n) } }
    }

    /** I calendari di casa del calendario di Sordina: quelli scelti per Casa; finché non ne scegli nessuno, tutti */
    private fun homeShown(books: List<CalBook>): Set<String> {
        if (!ha.configured) return emptySet()
        // Finché non scegli: quelli in cui si scrive (i calendari dei sensori, come "giorno lavorativo", riempirebbero ogni giorno)
        return ha.shownCalendars().ifEmpty { books.filter { it.home && it.canCreate }.map { it.ref }.toSet() }
    }

    fun pendingCount(): Int = pending().size

    // ── Scrittura

    sealed interface Saved {
        /** Fatto */
        data object Done : Saved
        /** Casa non risponde: parte appena c'è */
        data object Queued : Saved
        /** No: il motivo */
        data class Refused(val message: String) : Saved
    }

    fun create(d: Draft): Saved {
        if (!d.book.startsWith(CalBook.HA)) return if (phone.create(d)) done() else Saved.Refused("")
        return send(Pending(Pending.CREATE, d.book, "tmp:" + System.nanoTime(), null, true, d, System.currentTimeMillis()))
    }

    /** Cambia [e]; su un impegno che si ripete, [all] questa e le prossime, se no solo questa */
    fun update(e: CalEvent, d: Draft, all: Boolean): Saved {
        if (!e.book.startsWith(CalBook.HA)) return if (phone.update(e, d, all)) done() else Saved.Refused("")
        // Un impegno ancora in attesa di partire: si cambia la sua creazione, non si manda una modifica
        if (e.uid.startsWith("tmp:")) return rewritePending(e.uid) { it.copy(draft = d) }
        return send(Pending(Pending.UPDATE, e.book, e.uid, e.recurrenceId, all, d, System.currentTimeMillis(), e.start))
    }

    fun delete(e: CalEvent, all: Boolean): Saved {
        if (!e.book.startsWith(CalBook.HA)) return if (phone.delete(e, all)) done() else Saved.Refused("")
        if (e.uid.startsWith("tmp:")) return rewritePending(e.uid) { null }
        return send(Pending(Pending.DELETE, e.book, e.uid, e.recurrenceId, all, null, System.currentTimeMillis()))
    }

    private fun done(): Saved { bump(); return Saved.Done }

    /** Prima quello che aspetta (l'ordine conta), poi questa. Se casa non c'è, in coda */
    private fun send(p: Pending): Saved {
        synchronized(lock) {
            if (pending().isNotEmpty()) flush()
            if (pending().isNotEmpty() || !ha.configured) { savePending(pending() + p); bump(); return Saved.Queued }
            return when (val r = ha.ws(p.command())) {
                is HomeAssistant.Ws.Ok -> { reachable = true; refetchSoon(); Saved.Done }
                is HomeAssistant.Ws.Refused -> Saved.Refused(r.message)
                HomeAssistant.Ws.Unreachable -> { reachable = false; savePending(pending() + p); bump(); Saved.Queued }
            }
        }
    }

    private fun rewritePending(uid: String, change: (Pending) -> Pending?): Saved {
        synchronized(lock) {
            savePending(pending().mapNotNull { if (it.op == Pending.CREATE && it.uid == uid) change(it) else it })
        }
        bump()
        return Saved.Queued
    }

    // ── Sincronizzazione

    /**
     * Manda quello che aspetta e rifà la copia di casa. Da chiamare fuori dal thread principale: all'apertura del
     * calendario, quando casa torna raggiungibile, ogni tanto. Senza [force], la copia si rifà al massimo ogni 10 minuti.
     */
    fun sync(force: Boolean = false): Boolean {
        if (!ha.configured) return false
        synchronized(lock) {
            val sent = flush()
            val c = loadCopy()
            // Anche se è fresca: con altri calendari scelti la copia va rifatta subito
            if (!force && !sent && System.currentTimeMillis() - c.at < STALE && c.shown == homeShown(c.books)) return true
            return refetch().also { reachable = it; bump() }
        }
    }

    /** Manda le modifiche in attesa, nell'ordine. Vero se ne è partita almeno una */
    private fun flush(): Boolean {
        var list = pending()
        if (list.isEmpty()) return false
        var sent = false
        while (list.isNotEmpty()) {
            val p = list.first()
            when (val r = ha.ws(p.command())) {
                is HomeAssistant.Ws.Ok -> sent = true
                // Casa dice di no: si scarta e lo si dice, se no resterebbe in coda per sempre
                is HomeAssistant.Ws.Refused -> problem = (p.draft?.title ?: "") + (if (r.message.isNotBlank()) ": " + r.message else "")
                HomeAssistant.Ws.Unreachable -> { savePending(list); return sent }
            }
            list = list.drop(1)
        }
        savePending(emptyList())
        if (sent) bump()
        return sent
    }

    private fun refetch(): Boolean {
        val books = runCatching { ha.calendarBooks() }.getOrDefault(emptyList())
        if (books.isEmpty()) return false
        val (from, to) = window()
        val zone = ZoneId.systemDefault()
        val f = Instant.ofEpochMilli(from).atZone(zone).toOffsetDateTime()
        val t = Instant.ofEpochMilli(to).atZone(zone).toOffsetDateTime()
        val old = loadCopy()
        val wanted = homeShown(books)
        val out = ArrayList<CalEvent>()
        var any = false
        for (b in books) {
            if (b.ref !in wanted) continue
            val got = ha.calendarRange(b.ref, f, t)
            // Un calendario che stavolta non risponde: resta la sua copia di prima
            if (got == null) out += old.events.filter { it.book == b.key } else { out += got; any = true }
        }
        if (!any && wanted.isNotEmpty()) return false
        saveCopy(Copy(System.currentTimeMillis(), books, out, wanted))
        bump()
        return true
    }

    private fun refetchSoon() { Thread { runCatching { synchronized(lock) { refetch() } } }.start() }

    /** Da un anno prima a un anno dopo il mese corrente */
    fun window(today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
        val month = today.withDayOfMonth(1)
        return month.minusYears(1).atStartOfDay(zone).toInstant().toEpochMilli() to
            month.plusYears(1).plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    private fun bump() { version.value = version.value + 1 }

    // ── File

    private fun loadCopy(): Copy {
        copy?.let { return it }
        val c = runCatching {
            val o = JSONObject(cacheFile.readText())
            val b = o.getJSONArray("books"); val e = o.getJSONArray("events")
            val sh = o.optJSONArray("shown")
            Copy(o.optLong("at"), (0 until b.length()).map { CalBook.fromJson(b.getJSONObject(it)) }, (0 until e.length()).map { CalEvent.fromJson(e.getJSONObject(it)) },
                (0 until (sh?.length() ?: 0)).map { sh!!.getString(it) }.toSet())
        }.getOrElse { Copy(0L, emptyList(), emptyList()) }
        copy = c
        return c
    }

    private fun saveCopy(c: Copy) {
        copy = c
        runCatching {
            val o = JSONObject().put("at", c.at).put("shown", JSONArray(c.shown.toList()))
                .put("books", JSONArray().apply { c.books.forEach { put(it.toJson()) } })
                .put("events", JSONArray().apply { c.events.forEach { put(it.toJson()) } })
            val tmp = File(cacheFile.path + ".tmp")
            tmp.writeText(o.toString()); tmp.renameTo(cacheFile)
        }
    }

    private fun pending(): List<Pending> {
        pendingList?.let { return it }
        val l = runCatching { JSONArray(pendingFile.readText()).let { a -> (0 until a.length()).map { Pending.fromJson(a.getJSONObject(it)) } } }.getOrDefault(emptyList())
        pendingList = l
        return l
    }

    private fun savePending(l: List<Pending>) {
        pendingList = l
        runCatching {
            if (l.isEmpty()) pendingFile.delete()
            else pendingFile.writeText(JSONArray().apply { l.forEach { put(it.toJson()) } }.toString())
        }
    }

    private companion object {
        const val STALE = 10 * 60_000L
        /** I colori dei calendari di casa: verde acqua, ottone, corallo, lilla, verde, azzurro */
        val HOME_COLORS = listOf(0xFF5BBFB5, 0xFFD4A72C, 0xFFE07A5F, 0xFF9C89D9, 0xFF7FB069, 0xFF5B9BD5).map { it.toInt() }
    }
}
