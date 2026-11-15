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
 * Un solo promemoria di sistema alla volta, e solo se serve: la fine della sordina a tempo, la fine di una
 * conversazione in auto, o poco prima che scada la sospensione della prima notifica in sordina (di solito
 * la rinnova prima lo sblocco del telefono, e il promemoria non suona mai). Niente in sordina, niente promemoria.
 */
object Scheduler {
    const val ACTION_TICK = "app.sordina.TICK"
    /** Il rinnovo di riserva: due ore prima che Android restituisca la notifica */
    private const val RENEW_MARGIN = 2 * 3_600_000L
    /** Il promemoria impostato adesso (0 = nessuno), per non chiederlo di nuovo ad Android se non cambia */
    @Volatile private var scheduledAt = 0L

    fun refresh(context: Context) {
        val app = SordinaApp.of(context)
        app.silence.check()
        // Rinnova le notifiche in sordina (se il servizio è collegato)
        SordinaListener.sync()
        plan(context)
    }

    /** Rimette il promemoria al momento giusto; non tocca Android se è già lì. */
    fun plan(context: Context) {
        val app = SordinaApp.of(context)
        val now = System.currentTimeMillis()
        val focusEnd = app.moments.focusUntil()
        // In auto: quando finisce la conversazione in corso, Non disturbare torna ai soli preferiti
        val talkEnd = if (app.prefs.quietCar) Talking.lastUntil()?.let { it + 1_000 } else null
        val renew = app.store.held.value.values.filter { it.releaseAt > now }.minOfOrNull { it.releaseAt }
            ?.let { maxOf(now + 60_000, it - RENEW_MARGIN) }
        val next = listOfNotNull(focusEnd, talkEnd, renew).filter { it > now }.minOrNull()
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            context, 0, Intent(context, TickReceiver::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (next == null) {
            if (scheduledAt != 0L) { runCatching { am.cancel(pi) }; scheduledAt = 0L }
            return
        }
        // Non serve la precisione al secondo: le notifiche tornano comunque all'ora giusta
        val at = next + 5_000
        if (at == scheduledAt) return
        scheduledAt = at
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
