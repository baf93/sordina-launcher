package app.sordina.ui.calendar

import app.sordina.service.CalendarReminders
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sordina.R
import app.sordina.SordinaApp
import app.sordina.data.CalBook
import app.sordina.data.CalEvent
import app.sordina.data.Diary
import app.sordina.data.Draft
import app.sordina.data.Repeat
import app.sordina.tr
import app.sordina.trn
import app.sordina.ui.Confirm
import app.sordina.ui.Field
import app.sordina.ui.Group
import app.sordina.ui.Header
import app.sordina.ui.Line
import app.sordina.ui.Option
import app.sordina.ui.Pick
import app.sordina.ui.Pill
import app.sordina.ui.Primary
import app.sordina.ui.FlowPills
import app.sordina.ui.Secondary
import app.sordina.ui.Sheet
import app.sordina.ui.SheetRow
import app.sordina.ui.Toggle
import app.sordina.ui.Topic
import app.sordina.ui.clickableNoRipple
import app.sordina.ui.theme.S
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle as TextStyle_
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle as JTextStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/** Da dove si entra nel calendario: un giorno, un impegno da aprire (inizio e titolo, come lo mostra Casa), o un impegno nuovo */
data class CalStart(val day: LocalDate? = null, val begin: Long? = null, val title: String? = null, val create: Boolean = false)

private val zone: ZoneId get() = ZoneId.systemDefault()
private fun Long.day(): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
private fun Long.local(): LocalDateTime = Instant.ofEpochMilli(this).atZone(zone).toLocalDateTime()
private fun LocalDate.ms(): Long = atStartOfDay(zone).toInstant().toEpochMilli()
private fun LocalDateTime.ms(): Long = atZone(zone).toInstant().toEpochMilli()

/** I giorni in cui cade un impegno (quelli di più giorni compaiono in ognuno) */
private fun CalEvent.days(): List<LocalDate> {
    val first = start.day()
    val last = (end - 1).coerceAtLeast(start).day()
    return generateSequence(first) { it.plusDays(1) }.takeWhile { it <= last }.take(62).toList()
}

/**
 * Il calendario di Sordina: tutti i calendari scelti in un posto solo, con lo stile del resto. Un elenco che parte dal
 * giorno scelto (con la settimana sopra) o il mese; tocchi un impegno e lo vedi, "+" per uno nuovo. I calendari di casa
 * si aprono anche senza casa, dalla copia: quello che cambi parte appena Home Assistant risponde.
 */
@Composable
fun CalendarScreen(app: SordinaApp, start: CalStart, onBack: () -> Unit) {
    val c = S.c
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val scope = rememberCoroutineScope()
    val version by app.diary.version.collectAsState()

    var selectedEpoch by rememberSaveable { mutableStateOf((start.day ?: start.begin?.day() ?: LocalDate.now()).toEpochDay()) }
    val selected = LocalDate.ofEpochDay(selectedEpoch)
    var month by rememberSaveable { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var shown by remember { mutableStateOf<CalEvent?>(null) }
    var editing by remember { mutableStateOf<Pair<CalEvent?, Draft>?>(null) }

    val books by produceState(emptyList<CalBook>(), version) { value = withContext(Dispatchers.IO) { app.diary.books() } }
    val byKey = remember(books) { books.associateBy { it.key } }
    fun colorOf(e: CalEvent): Color = byKey[e.book]?.color?.let { Color(it) } ?: c.accent

    // All'apertura: manda quello che aspetta e rinfresca la copia di casa; se casa ha rifiutato qualcosa, lo si dice
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { app.diary.sync() }
        CalendarReminders.plan(context)
        app.diary.problem?.let { Toast.makeText(context, tr(R.string.cal_refused, it), Toast.LENGTH_LONG).show(); app.diary.problem = null }
    }

    fun newDraft(day: LocalDate): Draft? {
        val writable = books.filter { it.canCreate }
        val prefs = app.prefs
        val book = writable.firstOrNull { prefs.haCalendar.isNotBlank() && it.key == CalBook.HA + prefs.haCalendar }
            ?: writable.firstOrNull { it.key == CalBook.PHONE + prefs.calendarId } ?: writable.firstOrNull() ?: return null
        val now = LocalDateTime.now()
        val at = if (day == now.toLocalDate()) now.plusHours(1).withMinute(0).withSecond(0).withNano(0) else day.atTime(9, 0)
        return Draft(book.key, "", at.ms(), at.plusHours(1).ms(), false)
    }
    fun create(day: LocalDate) {
        val d = newDraft(day)
        if (d == null) Toast.makeText(context, tr(R.string.cal_no_books), Toast.LENGTH_LONG).show() else editing = null to d
    }

    // Da Casa: l'impegno toccato, oppure uno nuovo
    var started by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(books) {
        if (started || books.isEmpty() && !start.create) return@LaunchedEffect
        if (start.create) { started = true; create(selected); return@LaunchedEffect }
        val b = start.begin ?: run { started = true; return@LaunchedEffect }
        val day = b.day()
        val found = withContext(Dispatchers.IO) { app.diary.events(day.ms(), day.plusDays(1).ms()) }
        started = true
        shown = found.firstOrNull { it.start == b && it.title == start.title } ?: found.firstOrNull { it.start == b }
    }

    editing?.let { (original, draft) ->
        BackHandler { editing = null }
        Editor(app, books, original, draft, onClose = { editing = null })
        return
    }
    BackHandler(enabled = searching) { searching = false; query = "" }

    Box(Modifier.fillMaxSize().systemBarsPadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp)) {
            Header(tr(R.string.cal_title), onBack, status(app.diary, version))

            // Elenco o mese, oggi, cerca
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(tr(R.string.cal_week), !month && !searching) { month = false; searching = false }
                Pill(tr(R.string.cal_month), month && !searching) { month = true; searching = false }
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(44.dp).clip(CircleShape).clickableNoRipple { searching = !searching; query = "" }, contentAlignment = Alignment.Center) {
                    Icon(if (searching) Icons.Rounded.Close else Icons.Rounded.Search, tr(R.string.cal_search), Modifier.size(22.dp), tint = c.ink)
                }
            }

            when {
                searching -> SearchPart(app, query, { query = it }, version, ::colorOf, byKey, locale) { shown = it }
                // Uno swipe a destra o a sinistra: il mese (o la settimana) prima o dopo, come le frecce
                else -> Column(Modifier.swipeSides(selectedEpoch to month) { next ->
                    val d = when {
                        month -> monthOf(if (next) selected.plusMonths(1) else selected.minusMonths(1))
                        next -> selected.plusWeeks(1)
                        else -> selected.minusWeeks(1)
                    }
                    selectedEpoch = d.toEpochDay()
                }) {
                    if (month) MonthPart(app, selected, version, ::colorOf, byKey, locale, onDay = { selectedEpoch = it.toEpochDay() }) { shown = it }
                    else WeekPart(app, selected, version, ::colorOf, byKey, locale, onDay = { selectedEpoch = it.toEpochDay() }) { shown = it }
                }
            }
            Spacer(Modifier.height(96.dp))
        }

        // Nuovo impegno: il tondo in basso, sempre lì
        Box(
            Modifier.align(Alignment.BottomEnd).padding(20.dp).size(60.dp).clip(CircleShape)
                .background(c.ink).clickable { create(selected) },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Add, tr(R.string.cal_new), Modifier.size(28.dp), tint = c.bg) }
    }

    shown?.let { e ->
        Detail(app, e, byKey[e.book], locale, onDismiss = { shown = null },
            onEdit = { shown = null; editing = e to Draft.of(e) },
            onDone = { r -> shown = null; report(context, r) })
    }
}

/** Sotto il titolo: quando si è parlato con casa l'ultima volta, o cosa aspetta */
private fun status(diary: Diary, @Suppress("UNUSED_PARAMETER") version: Int): String? {
    val at = diary.syncedAt
    val n = diary.pendingCount()
    val time = if (at > 0) DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(at.local()) else null
    return when {
        n > 0 -> tr(R.string.cal_pending, n)
        !diary.reachable && time != null -> tr(R.string.cal_offline, time)
        time != null -> tr(R.string.cal_synced, time)
        else -> null
    }
}

private fun report(context: android.content.Context, r: Diary.Saved) {
    when (r) {
        Diary.Saved.Done -> Unit
        Diary.Saved.Queued -> Toast.makeText(context, tr(R.string.cal_queued), Toast.LENGTH_LONG).show()
        is Diary.Saved.Refused -> Toast.makeText(context, if (r.message.isBlank()) tr(R.string.cal_failed) else tr(R.string.cal_refused, r.message), Toast.LENGTH_LONG).show()
    }
}

// ── Settimana: la striscia dei sette giorni, sotto il giorno scelto (oggi, all'inizio)

@Composable
private fun WeekPart(
    app: SordinaApp, selected: LocalDate, version: Int, colorOf: (CalEvent) -> Color, books: Map<String, CalBook>, locale: Locale,
    onDay: (LocalDate) -> Unit, onOpen: (CalEvent) -> Unit,
) {
    val week = selected.with(TemporalAdjusters.previousOrSame(WeekFields.of(locale).firstDayOfWeek))
    val end = week.plusDays(7)
    val events by produceState(emptyList<CalEvent>(), week, version) { value = withContext(Dispatchers.IO) { app.diary.events(week.ms(), end.ms()) } }
    val perDay = remember(events) { HashMap<LocalDate, MutableList<CalEvent>>().apply { events.forEach { e -> e.days().forEach { getOrPut(it) { ArrayList() } += e } } } }

    // Frecce ai lati: la settimana prima e dopo. Un pallino nei giorni con qualcosa, oggi cerchiato
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Arrow(Icons.AutoMirrored.Rounded.KeyboardArrowLeft) { onDay(selected.minusWeeks(1)) }
        (0 until 7).forEach { i ->
            val d = week.plusDays(i.toLong())
            DayCell(d, d == selected, perDay[d].orEmpty().take(3).map(colorOf), Modifier.weight(1f), header = d.dayOfWeek.getDisplayName(JTextStyle.NARROW, locale)) { onDay(d) }
        }
        Arrow(Icons.AutoMirrored.Rounded.KeyboardArrowRight) { onDay(selected.plusWeeks(1)) }
    }
    DayTitle(selected, locale)
    val list = perDay[selected]
    if (list == null) FreeDay(150)
    else Group { list.forEach { e -> EventRow(e, selected, colorOf(e), books[e.book]?.name, locale) { onOpen(e) } } }
}

// ── Mese: la griglia, sotto il giorno scelto

@Composable
private fun MonthPart(
    app: SordinaApp, selected: LocalDate, version: Int, colorOf: (CalEvent) -> Color, books: Map<String, CalBook>, locale: Locale,
    onDay: (LocalDate) -> Unit, onOpen: (CalEvent) -> Unit,
) {
    val c = S.c
    val first = selected.withDayOfMonth(1)
    val firstDow = WeekFields.of(locale).firstDayOfWeek
    val gridStart = first.with(TemporalAdjusters.previousOrSame(firstDow))
    // Solo le settimane che servono al mese: di solito cinque
    val weeks = ((java.time.temporal.ChronoUnit.DAYS.between(gridStart, first) + first.lengthOfMonth() + 6) / 7).toInt()
    val gridEnd = gridStart.plusWeeks(weeks.toLong())
    val events by produceState(emptyList<CalEvent>(), gridStart, version) { value = withContext(Dispatchers.IO) { app.diary.events(gridStart.ms(), gridEnd.ms()) } }
    val perDay = remember(events) { HashMap<LocalDate, MutableList<CalEvent>>().apply { events.forEach { e -> e.days().forEach { getOrPut(it) { ArrayList() } += e } } } }

    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Arrow(Icons.AutoMirrored.Rounded.KeyboardArrowLeft) { onDay(monthOf(selected.minusMonths(1))) }
        Text(DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(first).replaceFirstChar { it.titlecase(locale) }, Modifier.weight(1f),
            textAlign = TextAlign.Center, style = TextStyle(fontSize = 19.sp, color = c.ink))
        Arrow(Icons.AutoMirrored.Rounded.KeyboardArrowRight) { onDay(monthOf(selected.plusMonths(1))) }
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.card).padding(horizontal = 8.dp, vertical = 6.dp)) {
        Row(Modifier.padding(bottom = 2.dp)) {
            (0 until 7).forEach { i ->
                Text(firstDow.plus(i.toLong()).getDisplayName(JTextStyle.NARROW, locale), Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = TextStyle(fontSize = 12.sp, color = c.soft))
            }
        }
        (0 until weeks).forEach { w ->
            Row {
                (0 until 7).forEach { i ->
                    val d = gridStart.plusDays((w * 7 + i).toLong())
                    DayCell(d, d == selected, perDay[d].orEmpty().take(3).map(colorOf), Modifier.weight(1f), faded = d.month != first.month, compact = true) { onDay(d) }
                }
            }
        }
    }
    DayTitle(selected, locale)
    val list = perDay[selected]
    if (list == null) FreeDay(96)
    else Group { list.forEach { e -> EventRow(e, selected, colorOf(e), books[e.book]?.name, locale) { onOpen(e) } } }
}

// ── Cerca

@Composable
private fun SearchPart(
    app: SordinaApp, query: String, onQuery: (String) -> Unit, version: Int, colorOf: (CalEvent) -> Color,
    books: Map<String, CalBook>, locale: Locale, onOpen: (CalEvent) -> Unit,
) {
    val c = S.c
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp).clip(RoundedCornerShape(22.dp)).background(c.card).padding(horizontal = 18.dp, vertical = 14.dp)) {
        if (query.isEmpty()) Text(tr(R.string.cal_search), style = TextStyle(fontSize = 17.sp, color = c.soft))
        BasicTextField(query, onQuery, singleLine = true, textStyle = TextStyle(fontSize = 17.sp, color = c.ink), cursorBrush = SolidColor(c.accent),
            modifier = Modifier.fillMaxWidth().focusRequester(focus))
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val found by produceState(emptyList<CalEvent>(), query, version) {
        value = if (query.isBlank()) emptyList() else withContext(Dispatchers.IO) { app.diary.search(query) }
    }
    if (query.isBlank()) return
    if (found.isEmpty()) { Empty(tr(R.string.cal_no_results)); return }
    found.groupBy { it.start.day() }.forEach { (d, list) ->
        DayTitle(d, locale)
        Group { list.forEach { e -> EventRow(e, d, colorOf(e), books[e.book]?.name, locale) { onOpen(e) } } }
    }
}

// ── Pezzi

@Composable
private fun Arrow(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(24.dp), tint = S.c.soft)
    }
}

/** Un giorno nella settimana o nel mese: il numero (nel tondo pieno se scelto, cerchiato se è oggi) e i pallini degli impegni */
@Composable
private fun DayCell(d: LocalDate, on: Boolean, dots: List<Color>, modifier: Modifier, header: String? = null, faded: Boolean = false, compact: Boolean = false, onClick: () -> Unit) {
    val c = S.c
    val today = d == LocalDate.now()
    Column(modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = if (compact) 1.dp else 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (header != null) Text(header, style = TextStyle(fontSize = 12.sp, color = c.soft))
        Box(
            Modifier.padding(top = if (compact) 0.dp else 2.dp).size(if (compact) 32.dp else 36.dp).clip(CircleShape)
                .background(if (on) c.ink else Color.Transparent)
                .let { if (today && !on) it.border(1.5.dp, c.accent, CircleShape) else it },
            contentAlignment = Alignment.Center,
        ) {
            Text(d.dayOfMonth.toString(), style = TextStyle(fontSize = if (compact) 15.sp else 16.sp, color = when { on -> c.bg; faded -> c.soft.copy(alpha = 0.5f); else -> c.ink }))
        }
        Row(Modifier.height(if (compact) 6.dp else 8.dp).padding(top = if (compact) 1.dp else 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            dots.forEach { Box(Modifier.size(5.dp).clip(CircleShape).background(if (faded) it.copy(alpha = 0.4f) else it)) }
        }
    }
}

@Composable
private fun DayTitle(d: LocalDate, locale: Locale) {
    val today = LocalDate.now()
    val name = when (d) {
        today -> tr(R.string.today_title)
        today.plusDays(1) -> tr(R.string.tomorrow_cap)
        else -> DateTimeFormatter.ofPattern("EEEE d MMMM", locale).format(d).replaceFirstChar { it.titlecase(locale) }
    }
    val hint = if (d == today || d == today.plusDays(1)) DateTimeFormatter.ofPattern("EEEE d MMMM", locale).format(d) else null
    Topic(name, hint)
}

/** Un giorno senza impegni: la sordina con gli occhiali da sole e il foglio del giorno bianco */
@Composable
private fun FreeDay(size: Int) {
    val c = S.c
    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        app.sordina.ui.Mascot(app.sordina.ui.Mood.FREE, Modifier.size(size.dp))
        Spacer(Modifier.height(14.dp))
        Text(tr(R.string.cal_free_title), style = TextStyle(fontSize = 22.sp, color = c.ink))
        Spacer(Modifier.height(4.dp))
        Text(tr(R.string.cal_empty_day), style = TextStyle(fontSize = 15.sp, color = c.soft))
    }
}

@Composable
private fun Empty(text: String) {
    Text(text, Modifier.padding(start = 12.dp, end = 12.dp, bottom = 4.dp), style = TextStyle(fontSize = 16.sp, lineHeight = 23.sp, color = S.c.soft))
}

private fun time(t: Long): String = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(t.local())

/** Un impegno in elenco: l'ora a sinistra, la striscia nel colore del suo calendario, il titolo e dove */
@Composable
private fun EventRow(e: CalEvent, day: LocalDate, color: Color, bookName: String?, locale: Locale, onClick: () -> Unit) {
    val c = S.c
    val startsToday = e.start.day() == day
    val endsToday = (e.end - 1).day() == day
    val top = when {
        e.allDay -> tr(R.string.cal_all_day_short)
        startsToday -> time(e.start)
        else -> "…"
    }
    val bottom = when {
        e.allDay -> null
        endsToday -> time(e.end)
        else -> "…"
    }
    Row(
        Modifier.fillMaxWidth().background(c.card).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp).heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(58.dp)) {
            Text(top, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 15.sp, color = c.ink))
            if (bottom != null) Text(bottom, maxLines = 1, style = TextStyle(fontSize = 13.sp, color = c.soft))
        }
        Box(Modifier.width(4.dp).height(38.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title.ifBlank { tr(R.string.event) }, maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 17.sp, color = c.ink))
            val sub = listOfNotNull(e.location, bookName).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 13.sp, color = c.soft))
        }
        if (e.pending) Text(tr(R.string.cal_waiting_badge), Modifier.padding(start = 8.dp), style = TextStyle(fontSize = 12.sp, color = c.accent))
    }
}

/** Quando: "giovedì 20 novembre · 9:30 – 10:15", o i giorni per il tutto il giorno */
private fun whenText(e: CalEvent, locale: Locale): String {
    val dayF = DateTimeFormatter.ofPattern("EEEE d MMMM", locale)
    val s = e.start.local(); val last = (e.end - 1).coerceAtLeast(e.start).local()
    return if (e.allDay) {
        if (s.toLocalDate() == last.toLocalDate()) dayF.format(s) + " · " + tr(R.string.cal_all_day)
        else dayF.format(s) + " – " + dayF.format(last)
    } else if (s.toLocalDate() == e.end.local().toLocalDate()) dayF.format(s) + " · " + time(e.start) + " – " + time(e.end)
    else dayF.format(s) + " " + time(e.start) + " – " + dayF.format(e.end.local()) + " " + time(e.end)
}

// ── Ripetizioni

/** Come si ripete, a parole: "Ogni 2 settimane, lun, gio, fino al 3 dic" */
private fun repeatText(rule: String?, start: LocalDate, locale: Locale): String {
    if (rule.isNullOrBlank()) return tr(R.string.cal_rep_none)
    val r = Repeat.parse(rule) ?: return tr(R.string.cal_rep_custom)
    if (!r.simple) return tr(R.string.cal_rep_custom)
    val parts = ArrayList<String>()
    parts += every(r.freq, r.interval)
    if (r.freq == Repeat.Freq.WEEKLY && r.days.isNotEmpty() && r.days != setOf(start.dayOfWeek))
        parts += weekOrder(locale).filter { it in r.days }.joinToString(", ") { it.getDisplayName(TextStyle_.SHORT, locale) }
    r.count?.let { parts += trn(R.plurals.cal_times, it) }
    r.until?.let { parts += tr(R.string.cal_until, DateTimeFormatter.ofPattern("d MMM yyyy", locale).format(it)) }
    return parts.joinToString(", ")
}

private val EVERY = mapOf(
    Repeat.Freq.DAILY to R.plurals.cal_every_day, Repeat.Freq.WEEKLY to R.plurals.cal_every_week,
    Repeat.Freq.MONTHLY to R.plurals.cal_every_month, Repeat.Freq.YEARLY to R.plurals.cal_every_year,
)

/** "Ogni settimana", "Ogni 2 settimane" */
private fun every(f: Repeat.Freq, n: Int): String = if (n > 1) trn(EVERY.getValue(f), n) else tr(when (f) {
    Repeat.Freq.DAILY -> R.string.cal_rep_daily; Repeat.Freq.WEEKLY -> R.string.cal_rep_weekly
    Repeat.Freq.MONTHLY -> R.string.cal_rep_monthly; Repeat.Freq.YEARLY -> R.string.cal_rep_yearly
})

/** I giorni della settimana a partire dal primo di qui (lunedì in Italia, domenica negli Stati Uniti) */
private fun weekOrder(locale: Locale): List<DayOfWeek> {
    val first = java.time.temporal.WeekFields.of(locale).firstDayOfWeek
    return (0L until 7L).map { first.plus(it) }
}

/**
 * Come si ripete, nel modulo stesso (come in Naboo): Ripeti sì o no; ogni quanti giorni, settimane, mesi o anni; per le
 * settimane in quali giorni; la fine se serve. La regola di prima resta tale e quale finché non si tocca.
 */
@Composable
private fun ColumnScope.RepeatRows(rule: String, start: LocalDate, allDay: Boolean, locale: Locale, onRule: (String) -> Unit) {
    val c = S.c
    var last by remember { mutableStateOf(false) }
    val r = Repeat.parse(rule)
    fun set(n: Repeat) {
        // Un giorno solo, ed è quello dell'impegno: non si scrive (così segue l'impegno se si sposta)
        val days = if (n.freq == Repeat.Freq.WEEKLY && n.days == setOf(start.dayOfWeek)) emptySet() else n.days
        onRule(n.copy(days = days).rule(allDay))
    }
    Toggle(tr(R.string.cal_repeat), rule.isNotBlank()) { on -> onRule(if (on) Repeat(Repeat.Freq.WEEKLY).rule(allDay) else "") }
    if (r == null) return
    Line(tr(R.string.cal_every), trailing = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton(Icons.Rounded.Remove, r.interval > 1) { set(r.copy(interval = r.interval - 1)) }
            Text("${r.interval}", Modifier.width(40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = TextStyle(fontSize = 18.sp, color = c.ink))
            StepButton(Icons.Rounded.Add, r.interval < 99) { set(r.copy(interval = r.interval + 1)) }
        }
    })
    Box(Modifier.fillMaxWidth().background(c.card).padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        val units = listOf(R.plurals.cal_unit_day, R.plurals.cal_unit_week, R.plurals.cal_unit_month, R.plurals.cal_unit_year)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.bg).padding(4.dp)) {
            Repeat.Freq.entries.forEachIndexed { i, f ->
                val on = r.freq == f
                Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (on) c.ink else Color.Transparent)
                    .clickableNoRipple { if (!on) set(Repeat(f, r.interval, until = r.until, count = r.count)) }.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                    Text(trn(units[i], r.interval).replaceFirstChar { it.titlecase(locale) }, maxLines = 1, softWrap = false, style = TextStyle(fontSize = 13.sp, color = if (on) c.bg else c.ink))
                }
            }
        }
    }
    if (r.freq == Repeat.Freq.WEEKLY) Box(Modifier.fillMaxWidth().background(c.card).padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
        val shown = r.days.ifEmpty { setOf(start.dayOfWeek) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            weekOrder(locale).forEach { d ->
                val on = d in shown
                Box(Modifier.size(36.dp).clip(CircleShape).background(if (on) c.ink else c.bg)
                    .clickableNoRipple {
                        val next = if (on) shown - d else shown + d
                        if (next.isNotEmpty()) set(r.copy(days = next)) // almeno un giorno resta scelto
                    }, contentAlignment = Alignment.Center) {
                    Text(d.getDisplayName(TextStyle_.NARROW, locale), style = TextStyle(fontSize = 15.sp, color = if (on) c.bg else c.ink))
                }
            }
        }
    }
    val ends = r.until != null || r.count != null
    Toggle(tr(R.string.cal_rep_end), ends) { on -> set(if (on) r.copy(until = start.plusDays(91), count = null) else r.copy(until = null, count = null)) }
    if (ends) {
        val value = r.until?.let { DateTimeFormatter.ofPattern("EEE d MMM yyyy", locale).format(it) } ?: trn(R.plurals.cal_times, r.count ?: 0)
        Line(tr(R.string.cal_rep_last), value) { last = true }
    }
    if (last) WhenSheet(tr(R.string.cal_rep_last), r.until ?: start.plusDays(91), null, locale, true, min = start, onDismiss = { last = false }) { d, _ ->
        set(r.copy(until = d, count = null)); last = false
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    val c = S.c
    Box(Modifier.size(36.dp).clip(CircleShape).background(c.bg).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(18.dp), tint = if (enabled) c.ink else c.dot)
    }
}

// ── Un impegno

@Composable
private fun Detail(
    app: SordinaApp, e: CalEvent, book: CalBook?, locale: Locale, onDismiss: () -> Unit, onEdit: () -> Unit, onDone: (Diary.Saved) -> Unit,
) {
    val c = S.c
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ask by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    fun delete(all: Boolean) { scope.launch { onDone(withContext(Dispatchers.IO) { app.diary.delete(e, all) }) } }

    Sheet(e.title.ifBlank { tr(R.string.event) }, onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Fact(Icons.Rounded.Schedule, whenText(e, locale).replaceFirstChar { it.titlecase(locale) })
            if (book != null) Fact(Icons.Rounded.CalendarMonth, book.name, dot = book.color?.let { Color(it) } ?: c.accent)
            e.location?.let { place ->
                Fact(Icons.Rounded.Place, place, link = true) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(place))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }
            if (e.repeats) Fact(Icons.Rounded.Repeat, repeatText(e.rrule, e.start.day(), locale))
            e.notes?.let { Fact(Icons.AutoMirrored.Rounded.Notes, it) }
            if (e.pending) Text(tr(R.string.cal_pending_one), style = TextStyle(fontSize = 14.sp, color = c.accent))
            if (book != null && !book.canEdit && !book.canDelete) Text(tr(R.string.cal_read_only), style = TextStyle(fontSize = 14.sp, color = c.soft))
        }
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (book?.canEdit == true) Primary(tr(R.string.cal_edit_btn), Modifier.fillMaxWidth(), onClick = onEdit)
            if (book?.canDelete == true) Secondary(tr(R.string.cal_delete_btn), Modifier.fillMaxWidth()) { if (e.repeats) ask = true else confirm = true }
        }
    }
    if (ask) Sheet(tr(R.string.cal_delete_q, e.title), tr(R.string.cal_repeats_hint), onDismiss = { ask = false }) {
        SheetRow(tr(R.string.cal_only_this)) { ask = false; delete(false) }
        SheetRow(tr(R.string.cal_all), accent = true) { ask = false; delete(true) }
    }
    if (confirm) Confirm(tr(R.string.cal_delete_q, e.title), tr(R.string.cal_delete_text, book?.name.orEmpty()), tr(R.string.cal_delete_btn),
        onConfirm = { delete(false) }, onDismiss = { confirm = false })
}

@Composable
private fun Fact(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, dot: Color? = null, link: Boolean = false, onClick: (() -> Unit)? = null) {
    val c = S.c
    Row(Modifier.fillMaxWidth().let { if (onClick != null) it.clickableNoRipple(onClick) else it }, verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.padding(top = 1.dp).size(20.dp), tint = c.soft)
        Spacer(Modifier.width(14.dp))
        if (dot != null) { Box(Modifier.padding(top = 7.dp, end = 8.dp).size(9.dp).clip(CircleShape).background(dot)) }
        Text(text, style = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, color = if (link) c.accent else c.ink))
    }
}

// ── Nuovo o da cambiare

@Composable
private fun Editor(app: SordinaApp, books: List<CalBook>, original: CalEvent?, initial: Draft, onClose: () -> Unit) {
    val c = S.c
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val scope = rememberCoroutineScope()
    var title by rememberSaveable { mutableStateOf(initial.title) }
    var allDay by rememberSaveable { mutableStateOf(initial.allDay) }
    var startMs by rememberSaveable { mutableStateOf(initial.start) }
    // Per il tutto il giorno la fine si mostra inclusa (l'ultimo giorno), si salva esclusa
    var endMs by rememberSaveable { mutableStateOf(if (initial.allDay) initial.end - 1 else initial.end) }
    var book by rememberSaveable { mutableStateOf(initial.book) }
    var rule by rememberSaveable { mutableStateOf(initial.rrule.orEmpty()) }
    var place by rememberSaveable { mutableStateOf(initial.location.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(initial.notes.orEmpty()) }
    var ask by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val is24 = android.text.format.DateFormat.is24HourFormat(context)
    // L'anno solo se non è quest'anno: così ci sta anche l'ora
    val dayF = object { fun format(t: LocalDateTime): String = DateTimeFormatter.ofPattern(if (t.year == LocalDate.now().year) "EEE d MMM" else "EEE d MMM yyyy", locale).format(t) }

    // Quale data si sta scegliendo nel foglio: l'inizio o la fine
    var picking by remember { mutableStateOf<Boolean?>(null) }
    // Spostare l'inizio sposta anche la fine: la durata resta
    fun setStart(new: Long) { val dur = endMs - startMs; startMs = new; endMs = new + dur.coerceAtLeast(0) }

    val creatable = books.filter { it.canCreate || it.key == book }
    val chosen = books.firstOrNull { it.key == book }
    val endsBefore = if (allDay) endMs.day() < startMs.day() else endMs <= startMs

    fun draft(): Draft {
        val s = if (allDay) startMs.day().ms() else startMs
        val e = if (allDay) endMs.day().plusDays(1).ms() else endMs
        // Se è cambiato "tutto il giorno", la fine della ripetizione cambia tipo con lui
        val r = if (allDay != initial.allDay) Repeat.parse(rule)?.takeIf { it.until != null }?.rule(allDay) ?: rule else rule
        return Draft(book, title.trim(), s, e, allDay, place.trim().ifBlank { null }, notes.trim().ifBlank { null }, r.ifBlank { null })
    }
    fun save(all: Boolean) {
        if (saving) return
        saving = true
        val d = draft()
        scope.launch {
            val r = withContext(Dispatchers.IO) { if (original == null) app.diary.create(d) else app.diary.update(original, d, all) }
            saving = false
            report(context, r)
            if (r !is Diary.Saved.Refused) onClose()
        }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp)) {
        Header(tr(if (original == null) R.string.cal_new else R.string.cal_edit), onClose)

        Group {
            Box(Modifier.fillMaxWidth()) {
                EditField(title, { title = it }, tr(R.string.cal_what), big = true)
            }
        }
        Spacer(Modifier.height(16.dp))
        Group {
            Toggle(tr(R.string.cal_all_day), allDay) { allDay = it }
            Line(tr(R.string.cal_starts), dayF.format(startMs.local()) + if (allDay) "" else " · " + time(startMs)) { picking = true }
            Line(tr(R.string.cal_ends), dayF.format(endMs.local()) + if (allDay) "" else " · " + time(endMs)) { picking = false }
        }
        if (endsBefore) Text(tr(R.string.cal_end_before), Modifier.padding(start = 12.dp, top = 8.dp), style = TextStyle(fontSize = 14.sp, color = c.accent))
        Spacer(Modifier.height(16.dp))
        Group { RepeatRows(rule, startMs.day(), allDay, locale) { rule = it } }
        if (rule.isNotBlank()) Text(repeatText(rule, startMs.day(), locale), Modifier.padding(start = 12.dp, top = 8.dp), style = TextStyle(fontSize = 14.sp, color = c.soft))
        Spacer(Modifier.height(16.dp))
        Group {
            // Il calendario si sceglie solo per un impegno nuovo: spostarlo da un calendario all'altro non lo fa nessuno dei due
            if (original == null && creatable.size > 1) Pick(tr(R.string.cal_calendar), chosen?.name.orEmpty(), creatable.map { Option(it.name, if (it.home) tr(R.string.cal_home_hint) else null) }, { creatable[it].key == book }) { book = creatable[it].key }
            else Line(tr(R.string.cal_calendar), chosen?.name.orEmpty())
            EditField(place, { place = it }, tr(R.string.cal_place))
            EditField(notes, { notes = it }, tr(R.string.cal_notes), lines = 6)
        }
        if (chosen?.home == true && !app.diary.reachable) Text(tr(R.string.cal_offline_save), Modifier.padding(start = 12.dp, top = 10.dp), style = TextStyle(fontSize = 14.sp, color = c.soft))

        Spacer(Modifier.height(24.dp))
        Primary(tr(R.string.cal_save), Modifier.fillMaxWidth()) {
            when {
                title.isBlank() -> Toast.makeText(context, tr(R.string.cal_what_missing), Toast.LENGTH_SHORT).show()
                endsBefore -> Toast.makeText(context, tr(R.string.cal_end_before), Toast.LENGTH_SHORT).show()
                original != null && original.repeats -> ask = true
                else -> save(true)
            }
        }
        Spacer(Modifier.height(48.dp))
    }
    picking?.let { start ->
        val at = (if (start) startMs else endMs).local()
        WhenSheet(tr(if (start) R.string.cal_starts else R.string.cal_ends), at.toLocalDate(), if (allDay) null else at.toLocalTime(), locale, is24,
            min = if (start) null else startMs.day(), onDismiss = { picking = null }) { d, t ->
            val ms = if (t == null) d.ms() else d.atTime(t).ms()
            if (start) setStart(ms) else endMs = ms
            picking = null
        }
    }
    if (ask) Sheet(tr(R.string.cal_edit), tr(R.string.cal_repeats_hint), onDismiss = { ask = false }) {
        SheetRow(tr(R.string.cal_only_this)) { ask = false; save(false) }
        SheetRow(tr(R.string.cal_following), accent = true) { ask = false; save(true) }
    }
}

/** Andando a un altro mese: oggi se è il mese di oggi, altrimenti il primo del mese. */
private fun monthOf(d: LocalDate): LocalDate = LocalDate.now().let { t -> if (t.year == d.year && t.month == d.month) t else d.withDayOfMonth(1) }

/** Uno swipe deciso a destra o a sinistra ([next]: verso sinistra, cioè avanti). Lo scorrimento in su e in giù resta alla pagina. */
private fun Modifier.swipeSides(key: Any, onSwipe: (next: Boolean) -> Unit): Modifier = pointerInput(key) {
    val min = 64.dp.toPx()
    var dx = 0f
    detectHorizontalDragGestures(
        onDragStart = { dx = 0f },
        onDragEnd = { if (kotlin.math.abs(dx) > min) onSwipe(dx < 0); dx = 0f },
        onDragCancel = { dx = 0f },
    ) { _, amount -> dx += amount }
}

/**
 * Un campo da scrivere che sta nelle righe del modulo: stessa altezza, stesso carattere, niente linea sotto.
 * [big]: il titolo dell'impegno, un po' più grande. [lines] > 1: va a capo (le note).
 */
@Composable
private fun EditField(value: String, onChange: (String) -> Unit, placeholder: String, big: Boolean = false, lines: Int = 1) {
    val c = S.c
    val style = TextStyle(fontSize = if (big) 20.sp else 17.sp, lineHeight = if (big) 26.sp else 23.sp, color = c.ink)
    Box(Modifier.fillMaxWidth().background(c.card).padding(horizontal = 16.dp, vertical = 14.dp).heightIn(min = 36.dp), contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty()) Text(placeholder, style = style.copy(color = c.soft))
        BasicTextField(
            value, onChange, singleLine = lines == 1, maxLines = lines, textStyle = style, cursorBrush = SolidColor(c.accent),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
