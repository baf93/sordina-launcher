package app.sordina.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import app.sordina.MainActivity
import app.sordina.R
import app.sordina.SordinaApp
import app.sordina.data.Held
import app.sordina.data.LockPrivacy
import app.sordina.data.Moment
import java.time.Duration
import java.time.LocalDateTime

/**
 * La scheda di Sordina: una sola notifica silenziosa e fissa, che si vede anche sulla schermata di blocco.
 * "Niente di urgente fino alle 19 · Anna, Paolo e altri 4". Più i ri-avvisi del livello Sempre.
 */
class StatusNotification(private val context: Context) {
    private val nm = context.getSystemService(NotificationManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val pending = Runnable { postNow() }

    fun createChannels() {
        val status = NotificationChannel(CH_STATUS, "Scheda di Sordina", NotificationManager.IMPORTANCE_DEFAULT).apply {
            // Importanza normale ma muta: molti telefoni nascondono dal blocco le notifiche "silenziose"
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
            description = "Lo stato e cosa ti aspetta, anche sulla schermata di blocco"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        val always = NotificationChannel(CH_ALWAYS, "Passa sempre", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Le notifiche delle app che passano sempre, anche con Non disturbare"
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        runCatching { nm.deleteNotificationChannel("status") }
        nm.createNotificationChannels(listOf(status, always))
    }

    /** Aggiorna la scheda (raggruppa gli aggiornamenti ravvicinati). */
    fun update() {
        handler.removeCallbacks(pending)
        handler.postDelayed(pending, 400)
    }

    private fun postNow() {
        val app = SordinaApp.of(context)
        val prefs = app.prefs
        // Niente notifica fissa: il conteggio "in sordina" sta in Casa
        nm.cancel(ID_STATUS)
    }

    private fun build(app: SordinaApp): Notification {
        val prefs = app.prefs
        val groups = app.store.groups()
        val total = groups.sumOf { g -> g.sumOf { it.count } }
        val title = headline(app)
        val names = groups.map { it.first().groupTitle }

        fun namesLine(): String = when {
            names.isEmpty() -> "Niente in attesa"
            names.size <= 3 -> joinNames(names)
            else -> names.take(2).joinToString(", ") + " e altri ${names.size - 2}"
        }
        fun countLine(): String = when (total) {
            0 -> "Niente in attesa"
            1 -> "1 in attesa"
            else -> "$total in attesa"
        }

        val privacy = prefs.lockPrivacy
        val b = base(title)
        when (privacy) {
            LockPrivacy.NUMBERS -> b.setContentText(countLine())
            LockPrivacy.NAMES -> {
                b.setContentText(namesLine())
                if (groups.isNotEmpty()) b.setStyle(Notification.InboxStyle().also { s ->
                    groups.take(6).forEach { g -> s.addLine(g.first().groupTitle + countSuffix(g)) }
                    if (groups.size > 6) s.setSummaryText("e altri ${groups.size - 6}")
                })
            }
            LockPrivacy.MESSAGES -> {
                b.setContentText(namesLine())
                if (groups.isNotEmpty()) b.setStyle(Notification.InboxStyle().also { s ->
                    groups.take(6).forEach { g -> s.addLine(g.first().groupTitle + ": " + g.first().text.take(80)) }
                    if (groups.size > 6) s.setSummaryText("e altri ${groups.size - 6}")
                })
            }
        }
        // Bloccato: al massimo i nomi (o i numeri, se si è scelto così). Rispetta "nascondi contenuti sensibili".
        val public = base(title).setContentText(if (privacy == LockPrivacy.NUMBERS) countLine() else namesLine())
        b.setPublicVersion(public.build())
        b.setVisibility(Notification.VISIBILITY_PRIVATE)

        // Azioni: Letto tutto e le due persone più recenti
        if (groups.isNotEmpty()) {
            b.addAction(Notification.Action.Builder(null, "Letto tutto", ActionReceiver.readAll(context)).build())
            groups.take(2).forEach { g ->
                val h = g.first()
                val pi = app.store.intentFor(h.key)
                if (pi != null) b.addAction(Notification.Action.Builder(null, h.groupTitle.take(18), pi).build())
            }
        }
        return b.build()
    }

    private fun base(title: String) = Notification.Builder(context, CH_STATUS)
        .setSmallIcon(R.drawable.ic_stat)
        .setContentTitle(title)
        .setOngoing(true)
        .setShowWhen(false)
        .setOnlyAlertOnce(true)
        .setCategory(Notification.CATEGORY_STATUS)
        .setColor(0xFFB4523A.toInt())
        .setContentIntent(openWaiting(context))

    /** Ri-avviso con suono per il livello Sempre, quando c'è Non disturbare. */
    fun alert(h: Held, intent: PendingIntent?) {
        val n = Notification.Builder(context, CH_ALWAYS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(h.person ?: h.appLabel)
            .setContentText(h.text)
            .setSubText(h.appLabel)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setContentIntent(intent ?: openWaiting(context))
            .build()
        runCatching { nm.notify(h.key.hashCode(), n) }
    }

    fun cancelAlert(key: String) = nm.cancel(key.hashCode())

    /** Avviso unico: Android ha rimesso un'altra Home. */
    fun homeLost() {
        val i = PendingIntent.getActivity(
            context, 3, Intent(android.provider.Settings.ACTION_HOME_SETTINGS), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(context, CH_STATUS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Sordina non è più la Home")
            .setContentText("Tocca per rimetterla")
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

        fun openWaiting(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_WAITING)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        fun joinNames(n: List<String>) = when (n.size) {
            0 -> ""
            1 -> n[0]
            else -> n.dropLast(1).joinToString(", ") + " e " + n.last()
        }

        private fun countSuffix(g: List<Held>): String {
            val c = g.sumOf { it.count }
            return if (c > 1) " · $c" else ""
        }

        /** La frase di stato, uguale in Casa e sulla scheda. */
        fun headline(app: SordinaApp, now: LocalDateTime = LocalDateTime.now()): String {
            val moment = app.moments.current()
            if (moment != Moment.NONE) return moment.phrase
            val waiting = app.store.groups().sumOf { g -> g.sumOf { it.count } }
            val last = app.moments.lastSummaryToday(now)
            if (last != null && waiting > 0) {
                val since = Duration.between(now.toLocalDate().atTime(last, 0), now).toMinutes()
                if (since in 0..59) return "Riepilogo delle $last · " + if (waiting == 1) "1 cosa" else "$waiting cose"
            }
            val next = app.moments.nextSummary(now)
            val day = if (next.toLocalDate() == now.toLocalDate()) "" else if (next.toLocalDate() == now.toLocalDate().plusDays(1)) " di domani" else ""
            return "Niente di urgente fino alle ${next.hour}$day"
        }
    }
}
