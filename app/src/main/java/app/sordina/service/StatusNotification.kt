package app.sordina.service

import app.sordina.tr

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.sordina.MainActivity
import app.sordina.R
import app.sordina.data.Held
import androidx.core.graphics.drawable.toBitmap

/**
 * Le notifiche di Sordina: i ri-avvisi del livello Sempre e l'avviso "un'altra Home".
 * Niente notifica fissa: il conteggio "in sordina" sta in Casa.
 */
class StatusNotification(private val context: Context) {
    private val nm = context.getSystemService(NotificationManager::class.java)

    fun createChannels() {
        val status = NotificationChannel(CH_STATUS, tr(R.string.ch_status), NotificationManager.IMPORTANCE_DEFAULT).apply {
            // Importanza normale ma muta: molti telefoni nascondono dal blocco le notifiche "silenziose"
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
            description = tr(R.string.ch_status_desc)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        val always = NotificationChannel(CH_ALWAYS, tr(R.string.ch_always), NotificationManager.IMPORTANCE_HIGH).apply {
            description = tr(R.string.ch_always_desc)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        runCatching { nm.deleteNotificationChannel("status") }
        nm.createNotificationChannels(listOf(status, always))
        // La vecchia scheda fissa, se è rimasta da una versione precedente: si toglie una volta, all'avvio
        runCatching { nm.cancel(ID_STATUS) }
    }

    /**
     * Ri-avviso con suono per il livello Sempre, quando Non disturbare zittisce l'originale.
     * Serve solo a suonare: dopo pochi secondi sparisce e in tendina resta la notifica dell'app.
     */
    fun alert(h: Held, intent: PendingIntent?) {
        val n = Notification.Builder(context, CH_ALWAYS)
            .setTimeoutAfter(ALERT_MILLIS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(h.person ?: h.appLabel)
            .setContentText(h.text)
            .setSubText(h.appLabel)
            // La piccola deve essere di Sordina (Android non ne accetta altre): l'icona dell'app, grande, dice da chi viene
            .apply { appIcon(h.pkg)?.let { setLargeIcon(it) } }
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setContentIntent(intent ?: openWaiting(context))
            .build()
        runCatching { nm.notify(h.key.hashCode(), n) }
        alerted += h.key.hashCode()
    }

    private fun appIcon(pkg: String): android.graphics.Bitmap? = runCatching {
        val px = context.resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width)
        context.packageManager.getApplicationIcon(pkg).toBitmap(px, px)
    }.getOrNull()

    /** I ri-avvisi ancora in giro: solo questi si cancellano (niente chiamate ad Android per gli altri). */
    private val alerted = java.util.Collections.synchronizedSet(HashSet<Int>())

    fun cancelAlert(key: String) {
        if (alerted.remove(key.hashCode())) runCatching { nm.cancel(key.hashCode()) }
    }

    /** Avviso unico: Android ha rimesso un'altra Home. */
    fun homeLost() {
        val i = PendingIntent.getActivity(
            context, 3, Intent(android.provider.Settings.ACTION_HOME_SETTINGS), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(context, CH_STATUS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(tr(R.string.home_lost))
            .setContentText(tr(R.string.home_lost_tap))
            .setAutoCancel(true)
            .setContentIntent(i)
            .build()
        runCatching { nm.notify(ID_HOME, n) }
    }

    companion object {
        const val CH_STATUS = "status2"
        const val CH_ALWAYS = "sempre"
        const val ID_STATUS = 1
        const val ID_HOME = 2
        /** Quanto resta il ri-avviso: il tempo del suono e del banner, poi resta solo l'originale */
        private const val ALERT_MILLIS = 8_000L

        fun openWaiting(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_WAITING)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
