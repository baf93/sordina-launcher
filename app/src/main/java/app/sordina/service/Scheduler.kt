package app.sordina.service

import app.sordina.tr
import app.sordina.R

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.sordina.SordinaApp
import app.sordina.data.Moments
import java.time.LocalDateTime

/**
 * Un solo promemoria di sistema alla volta: la fine della sordina a tempo, o un giro ogni 6 ore
 * per rinnovare le notifiche in sordina e aggiornare la scheda.
 */
object Scheduler {
    const val ACTION_TICK = "app.sordina.TICK"

    fun refresh(context: Context) {
        val app = SordinaApp.of(context)
        app.silence.check()
        // Rinnova le notifiche in sordina (se il servizio è collegato)
        SordinaListener.sync()
        app.status.update()
        scheduleNext(context)
    }

    private fun scheduleNext(context: Context) {
        val app = SordinaApp.of(context)
        val now = LocalDateTime.now()
        // Fine della sordina a tempo, se c'è; comunque un giro ogni 6 ore (rinnovo delle posticipate, Casa)
        val focusEnd = app.moments.focusUntil()?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime() }
        val next = listOfNotNull(focusEnd, now.plusHours(6)).min()
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            context, 0, Intent(context, TickReceiver::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // Non serve la precisione al secondo: le notifiche tornano comunque all'ora giusta
        val at = Moments.millis(next) + 5_000
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) }
    }
}

/** Riceve il promemoria, l'avvio del telefono, gli aggiornamenti dell'app e i cambi d'ora. */
class TickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = SordinaApp.of(context)
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // Android potrebbe aver rimesso un'altra Home
                if (app.prefs.onboarded && !isDefaultHome(context)) app.status.homeLost()
            }
        }
        Scheduler.refresh(context)
    }

    private fun isDefaultHome(context: Context): Boolean {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager.resolveActivity(i, 0)?.activityInfo?.packageName == context.packageName
    }
}

/** Azioni della scheda: tr(R.string.read_all). */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_READ_ALL) SordinaListener.readAll(context)
    }

    companion object {
        const val ACTION_READ_ALL = "app.sordina.READ_ALL"

        fun readAll(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 2, Intent(context, ActionReceiver::class.java).setAction(ACTION_READ_ALL),
            PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
