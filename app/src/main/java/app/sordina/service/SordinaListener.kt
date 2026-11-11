package app.sordina.service

import android.app.ActivityOptions
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.sordina.SordinaApp
import app.sordina.data.CardDetector
import app.sordina.data.Decision
import app.sordina.data.Held
import app.sordina.data.Level
import app.sordina.data.Moments

/**
 * Ascolta le notifiche e decide: passano, passano in silenzio o aspettano il riepilogo.
 * Quelle che aspettano vengono posticipate (non cancellate): se Sordina si ferma, tornano da sole.
 */
class SordinaListener : NotificationListenerService() {
    private val labels = HashMap<String, String>()
    private var contexts: Contexts? = null

    override fun onListenerConnected() {
        instance = this
        runCatching { reconcile() }
        contexts = Contexts(this).also { it.start() }
        Scheduler.refresh(this)
    }

    override fun onListenerDisconnected() {
        instance = null
        contexts?.stop()
        contexts = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        runCatching { handle(sbn) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        if (reason == REASON_SNOOZED) return
        val app = SordinaApp.of(this)
        app.signals.remove(sbn.key)
        if (app.store.get(sbn.key) != null) {
            app.store.remove(sbn.key)
            app.status.cancelAlert(sbn.key)
            app.status.update()
        }
    }

    private fun handle(sbn: StatusBarNotification) {
        val app = SordinaApp.of(this)
        val store = app.store
        val key = sbn.key
        // Letta in Sordina mentre era posticipata: quando torna, si chiude
        if (store.takeDismissed(key)) { cancelNotification(key); return }

        val info = Classifier.read(sbn)
        card(sbn, info)
        val now = System.currentTimeMillis()
        val existing = store.get(key)
        // Torna dal riepilogo: adesso si vede, resta "in attesa" finché non la leggi
        if (existing != null && existing.releaseAt > 0 && now >= existing.releaseAt - 60_000 &&
            (existing.postTime == sbn.postTime || existing.text == info.text)
        ) {
            store.put(existing.copy(releaseAt = 0), sbn.notification.contentIntent)
            app.status.update()
            return
        }

        // Notifiche già silenziose (importanza bassa o minima): sono tecniche, non disturbano, restano dove sono
        val importance = runCatching {
            val r = Ranking()
            if (currentRanking.getRanking(key, r)) r.importance else NotificationManager.IMPORTANCE_DEFAULT
        }.getOrDefault(NotificationManager.IMPORTANCE_DEFAULT)
        if (importance in 1..NotificationManager.IMPORTANCE_LOW && existing == null) return

        val verdict = Classifier.classify(info, packageName, app.prefs, app.people, app.moments.current())
        val level = verdict.level ?: return
        val label = label(sbn.packageName)

        if (info.isGroupSummary) {
            // Il riassunto del gruppo segue i suoi messaggi: aspetta solo se tutti i suoi messaggi aspettano
            val mine = store.held.value.values.filter { it.pkg == sbn.packageName }
            val allWaiting = mine.isNotEmpty() && mine.all { it.level == Level.RIEPILOGO && it.releaseAt > now }
            if (level == Level.RIEPILOGO && allWaiting && existing == null) snoozeNotification(key, releaseAt() - now)
            return
        }
        if (info.text.isBlank() && info.title.isBlank()) return

        val text = if (info.isMessage) info.text.ifBlank { info.title }
        else listOf(info.title, info.text).filter { it.isNotBlank() }.distinct().joinToString(": ")
        val release = if (level == Level.RIEPILOGO) releaseAt() else 0L
        val held = Held(
            key = key, pkg = sbn.packageName, appLabel = label,
            person = if (info.isMessage) info.person else null,
            conversation = if (info.isMessage) info.conversation else null,
            text = text, time = sbn.postTime, level = level, count = info.count,
            isMessage = info.isMessage, releaseAt = release, postTime = sbn.postTime,
        )
        if (level == Level.RIEPILOGO) snoozeNotification(key, release - now)
        store.put(held, sbn.notification.contentIntent)
        if (level == Level.SEMPRE && app.silence.isActive()) app.status.alert(held, sbn.notification.contentIntent)
        store.log(Decision(now, sbn.packageName, label, level, verdict.reason))
        app.status.update()
    }

    /** Una scheda in Casa per le notifiche che la meritano (OTP, chiamate, timer, biglietti, pacchi). */
    private fun card(sbn: StatusBarNotification, info: NotifInfo = Classifier.read(sbn)) {
        if (sbn.packageName == packageName) return
        runCatching {
            CardDetector.detect(sbn.key, sbn.packageName, label(sbn.packageName), info.category, info.title, info.text,
                info.ongoing, Classifier.isOtp(info.title + " " + info.text))?.let { SordinaApp.of(this).signals.put(it) }
        }
    }

    private fun releaseAt(): Long = Moments.millis(SordinaApp.of(this).moments.nextSummary())

    private fun label(pkg: String): String = labels.getOrPut(pkg) {
        runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }
            .getOrDefault(pkg)
    }

    /** Dopo un riavvio: ritrova le azioni di apertura e toglie ciò che non esiste più. */
    private fun reconcile() {
        val store = SordinaApp.of(this).store
        val active = activeNotifications.orEmpty().associateBy { it.key }
        val snoozed = runCatching { snoozedNotifications }.getOrNull().orEmpty().associateBy { it.key }
        for (h in store.held.value.values) {
            val sbn = active[h.key] ?: snoozed[h.key]
            if (sbn == null) store.remove(h.key) else store.attachIntent(h.key, sbn.notification.contentIntent)
        }
        // Quello che è già nella tendina passa dalle stesse regole
        for (sbn in active.values) if (store.get(sbn.key) != null) card(sbn)
        for (sbn in active.values) if (store.get(sbn.key) == null) runCatching { handle(sbn) }
    }

    private fun findIntent(key: String): PendingIntent? =
        (activeNotifications.orEmpty().firstOrNull { it.key == key }
            ?: runCatching { snoozedNotifications }.getOrNull()?.firstOrNull { it.key == key })
            ?.notification?.contentIntent

    companion object {
        @Volatile var instance: SordinaListener? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
            return flat.split(':').any { ComponentName.unflattenFromString(it)?.packageName == context.packageName }
        }

        fun rebind(context: Context) {
            if (instance == null && isEnabled(context)) {
                runCatching { requestRebind(ComponentName(context, SordinaListener::class.java)) }
            }
        }

        /** Apre il messaggio nell'app giusta. Se era in attesa del riepilogo, conta come letto. */
        fun open(context: Context, key: String): Boolean {
            val app = SordinaApp.of(context)
            val h = app.store.get(key)
            val pi = app.store.intentFor(key) ?: instance?.findIntent(key)
            val ok = pi != null && runCatching {
                if (Build.VERSION.SDK_INT >= 34) {
                    val opts = ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    pi.send(context, 0, null, null, null, null, opts.toBundle())
                } else pi.send()
            }.isSuccess
            if (!ok && h != null) {
                // Senza azione: si apre almeno l'app
                context.packageManager.getLaunchIntentForPackage(h.pkg)?.let {
                    runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }
            if (h != null) markRead(context, listOf(h))
            return ok
        }

        fun readGroup(context: Context, groupKey: String) {
            val store = SordinaApp.of(context).store
            markRead(context, store.held.value.values.filter { it.groupKey == groupKey })
        }

        fun readAll(context: Context) {
            markRead(context, SordinaApp.of(context).store.held.value.values.toList())
        }

        private fun markRead(context: Context, items: List<Held>) {
            val app = SordinaApp.of(context)
            for (h in items) {
                app.store.remove(h.key)
                app.status.cancelAlert(h.key)
                if (h.releaseAt > System.currentTimeMillis()) app.store.markDismissed(h.key)
                else runCatching { instance?.cancelNotification(h.key) }
            }
            app.status.update()
        }
    }
}
