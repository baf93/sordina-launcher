package app.sordina.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.sordina.MainActivity
import app.sordina.R
import app.sordina.SordinaApp
import app.sordina.data.CalBook
import app.sordina.data.CalEvent
import app.sordina.tr
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * I promemoria di Sordina per gli impegni di casa, 15 minuti prima: facoltativi e spenti all'inizio, perché spesso li fa
 * già Home Assistant. Solo i calendari di casa: quelli del telefono hanno già i promemoria della loro app, e si
 * sentirebbero due volte. Una sveglia sola alla volta, per il prossimo; se nei prossimi giorni non c'è niente, si
 * ripassa fra qualche ora (e intanto si rinfresca la copia di casa).
 */
object CalendarReminders {
    const val CH_CAL = "cal"
    private const val BEFORE = 15 * 60_000L
    private const val IDLE = 6 * 3_600_000L
    private const val ACTION = "app.sordina.CAL_REMIND"

    fun channel(): NotificationChannel = NotificationChannel(CH_CAL, tr(R.string.ch_cal), NotificationManager.IMPORTANCE_HIGH).apply {
        setShowBadge(false)
        lockscreenVisibility = Notification.VISIBILITY_PRIVATE
    }

    private fun on(app: SordinaApp) = app.prefs.sordinaCalendar && app.prefs.calendarReminders && app.ha.configured

    private fun pending(context: Context) = PendingIntent.getBroadcast(
        context, 7, Intent(context, CalendarReminderReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun upcoming(app: SordinaApp, from: Long, to: Long): List<CalEvent> =
        app.diary.events(from, to).filter { it.book.startsWith(CalBook.HA) && !it.allDay }

    /** Rimette la sveglia per il prossimo promemoria (o la toglie). Da un thread qualsiasi: legge solo la copia di casa */
    fun plan(context: Context) {
        val app = SordinaApp.of(context)
        val am = context.getSystemService(AlarmManager::class.java)
        if (!on(app)) { runCatching { am.cancel(pending(context)) }; return }
        Thread {
            val now = System.currentTimeMillis()
            val next = upcoming(app, now, now + 2 * 86_400_000L).map { it.start - BEFORE }.filter { it > now }.minOrNull()
            val at = next ?: (now + IDLE)
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context)) }
        }.start()
    }

    /** È l'ora: avvisa degli impegni che cominciano fra un quarto d'ora (una volta sola ciascuno), rinfresca, ripianifica */
    internal fun fire(context: Context) {
        val app = SordinaApp.of(context)
        if (!on(app)) return
        runCatching { app.diary.sync() }
        val now = System.currentTimeMillis()
        val due = upcoming(app, now, now + BEFORE + 2 * 60_000L).filter { it.start >= now - 60_000L }
        val doneFile = File(context.filesDir, "calendar_reminded.txt")
        val done = runCatching { doneFile.readLines().toMutableSet() }.getOrDefault(mutableSetOf())
        val nm = context.getSystemService(NotificationManager::class.java)
        val hm = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        for (e in due) {
            val key = "${e.book}|${e.uid}|${e.start}"
            if (key in done) continue
            done += key
            val open = Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_CALENDAR)
                .putExtra(MainActivity.EXTRA_BEGIN, e.start).putExtra(MainActivity.EXTRA_TITLE, e.title)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val time = hm.format(Instant.ofEpochMilli(e.start).atZone(ZoneId.systemDefault()))
            val n = Notification.Builder(context, CH_CAL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(e.title.ifBlank { tr(R.string.event) })
                .setContentText(listOfNotNull(tr(R.string.cal_remind_text, time), e.location).joinToString(" · "))
                .setWhen(e.start).setShowWhen(true)
                .setCategory(Notification.CATEGORY_EVENT)
                .setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(context, key.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                .build()
            runCatching { nm.notify(key.hashCode(), n) }
        }
        // Si ricordano solo quelli degli ultimi giorni
        runCatching { doneFile.writeText(done.filter { (it.substringAfterLast('|').toLongOrNull() ?: 0) > now - 3 * 86_400_000L }.joinToString("\n")) }
        plan(context)
    }
}

class CalendarReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        Thread { try { CalendarReminders.fire(context) } finally { result.finish() } }.start()
    }
}
