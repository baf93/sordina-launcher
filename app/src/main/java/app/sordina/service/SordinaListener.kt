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
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val syncLoop = object : Runnable {
        override fun run() {
            // A schermo spento non serve: il primo controllo utile è quando lo riaccendi e apri Sordina
            val on = runCatching { getSystemService(android.os.PowerManager::class.java).isInteractive }.getOrDefault(true)
            if (on) runCatching { resync() }
            main.postDelayed(this, SYNC_MILLIS)
        }
    }

    override fun onListenerConnected() {
        instance = this
        runCatching { reconcile() }
        contexts = Contexts(this).also { it.start() }
        Scheduler.refresh(this)
        main.removeCallbacks(syncLoop)
        main.postDelayed(syncLoop, SYNC_MILLIS)
    }

    override fun onListenerDisconnected() {
        instance = null
        main.removeCallbacks(syncLoop)
        contexts?.stop()
        contexts = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        runCatching { handle(sbn) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        if (reason == REASON_SNOOZED) return
        val app = SordinaApp.of(this)
        // Aperta o letta nell'app (anche rispondendo): con quella persona stai parlando, i suoi prossimi messaggi passano
        if (reason == REASON_CLICK || reason == REASON_APP_CANCEL) runCatching {
            val info = Classifier.read(sbn)
            if (info.isMessage && !info.isGroupSummary) Talking.mark(info.pkg, info.conversation ?: info.person)
            // Toccata da te, in una chat a due: il cervello impara a chi rispondi e quando
            if (reason == REASON_CLICK && info.isMessage && !info.isGroupSummary && info.conversation == null) {
                info.person?.let { app.brain.onMessageOpened(info.pkg, it) }
            }
        }
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

        val info = Classifier.read(sbn).let { if (isSystemComponent(it.pkg)) it.copy(system = true) else it }
        card(sbn, info)
        val now = System.currentTimeMillis()
        val existing = store.get(key)
        // Torna dal riepilogo: adesso si vede, resta "in attesa" finché non la leggi.
        // Basta la stessa chiave dopo l'ora di rilascio: Android può cambiarle orario o testo quando la ripubblica.
        if (existing != null && existing.releaseAt > 0 && now >= existing.releaseAt - 60_000) {
            store.put(existing.copy(releaseAt = 0), sbn.notification.contentIntent)
            app.status.update()
            return
        }
        // Rete di sicurezza: arrivata prima del riepilogo appena passato e ripubblicata subito dopo
        // (es. Sordina riavviata nel frattempo): è una che torna, non va posticipata di nuovo.
        val returning = existing == null && justAfterSummary(now) && sbn.postTime < lastSummaryMillis(now)

        // Notifiche già silenziose (importanza bassa o minima): sono tecniche, non disturbano, restano dove sono
        val ranking = runCatching { Ranking().takeIf { currentRanking.getRanking(key, it) } }.getOrNull()
        val importance = ranking?.importance ?: NotificationManager.IMPORTANCE_DEFAULT
        if (importance in 1..NotificationManager.IMPORTANCE_LOW && existing == null) return

        val verdict = Classifier.classify(info, packageName, app.prefs, app.people, app.moments.current())
        val level = verdict.level ?: return
        val label = label(sbn.packageName)

        if (info.isGroupSummary) {
            // Il riassunto del gruppo segue i suoi messaggi: aspetta solo se tutti i suoi messaggi aspettano
            val mine = store.held.value.values.filter { it.pkg == sbn.packageName }
            val allWaiting = mine.isNotEmpty() && mine.all { it.level == Level.RIEPILOGO && it.releaseAt > now }
            if (level == Level.RIEPILOGO && allWaiting && existing == null && !returning) snoozeNotification(key, (if (sbn.packageName in app.prefs.braked && app.prefs.brakedStay) STAY_MILLIS else releaseAt() - now))
            return
        }
        if (info.text.isBlank() && info.title.isBlank()) return

        val stays = sbn.packageName in app.prefs.braked && app.prefs.brakedStay
        val release = when {
            level != Level.RIEPILOGO || returning -> 0L
            stays -> now + STAY_MILLIS
            else -> releaseAt()
        }
        val held = heldOf(sbn, info, label, level, release)
        if (release > 0) snoozeNotification(key, release - now)
        store.put(held, sbn.notification.contentIntent)
        // Il doppione serve solo se Non disturbare sta davvero zittendo l'originale (che resta comunque in tendina)
        val muted = ranking?.matchesInterruptionFilter() == false
        if (level == Level.SEMPRE && app.silence.isActive() && muted) app.status.alert(held, sbn.notification.contentIntent)
        store.log(Decision(now, sbn.packageName, label, level, verdict.reason))
        app.status.update()
    }

    private fun heldOf(sbn: StatusBarNotification, info: NotifInfo, label: String, level: Level, release: Long): Held {
        val text = if (info.isMessage) info.text.ifBlank { info.title }
        else listOf(info.title, info.text).filter { it.isNotBlank() }.distinct().joinToString(": ")
        return Held(
            key = sbn.key, pkg = sbn.packageName, appLabel = label,
            person = if (info.isMessage) info.person else null,
            conversation = if (info.isMessage) info.conversation else null,
            text = text, time = sbn.postTime, level = level, count = info.count,
            isMessage = info.isMessage, releaseAt = release, postTime = sbn.postTime,
            title = if (info.isMessage) null else info.title.takeIf { it.isNotBlank() && it != info.text },
        )
    }

    /**
     * Android non avvisa quando un'app aggiorna o chiude una notifica posticipata: l'aggiornamento resta
     * posticipato in silenzio. Così un codice o un messaggio importante arrivato nella stessa conversazione
     * aspetterebbe il riepilogo. Ogni tanto si confrontano le posticipate con quello che Sordina tiene:
     * se ora passerebbe, torna subito; altrimenti si aggiorna il testo; se l'app l'ha chiusa, si toglie.
     */
    private fun resync() {
        val app = SordinaApp.of(this)
        val store = app.store
        val now = System.currentTimeMillis()
        val waiting = store.held.value.values.filter { it.releaseAt > now }
        if (waiting.isEmpty()) return
        val snoozed = runCatching { snoozedNotifications }.getOrNull()?.associateBy { it.key } ?: return
        // Le attive si chiedono solo se serve: è una lista pesante (immagini comprese)
        val active by lazy { activeNotifications.orEmpty().mapTo(HashSet()) { it.key } }
        var changed = false
        for (h in waiting) {
            val sbn = snoozed[h.key]
            if (sbn == null) {
                if (h.key !in active) { store.remove(h.key); app.status.cancelAlert(h.key); changed = true }
                continue
            }
            val info = Classifier.read(sbn).let { if (isSystemComponent(it.pkg)) it.copy(system = true) else it }
            val fresh = heldOf(sbn, info, h.appLabel, h.level, h.releaseAt)
            if (fresh.postTime == h.postTime && fresh.text == h.text && fresh.count == h.count) continue
            card(sbn, info)
            val level = Classifier.classify(info, packageName, app.prefs, app.people, app.moments.current()).level
            if (level != Level.RIEPILOGO) {
                // Torna fra mezzo secondo e passa dalle regole come nuova
                store.remove(h.key)
                runCatching { snoozeNotification(h.key, 500) }
            } else store.put(fresh, sbn.notification.contentIntent)
            changed = true
        }
        if (changed) app.status.update()
    }

    /** Una scheda in Casa per le notifiche che la meritano (OTP, chiamate, timer, biglietti, pacchi). */
    private fun card(sbn: StatusBarNotification, info: NotifInfo = Classifier.read(sbn)) {
        if (sbn.packageName == packageName) return
        runCatching {
            CardDetector.detect(sbn.key, sbn.packageName, label(sbn.packageName), info.category, info.title, info.text,
                info.ongoing, Classifier.isOtp(info.title + " " + info.text), buttons(sbn), progress(sbn), sbn.postTime)?.let { SordinaApp.of(this).signals.put(it) }
        }
    }

    /** I pulsanti della notifica, senza quelli che chiedono di scrivere una risposta. */
    private fun buttons(sbn: StatusBarNotification): List<String> =
        sbn.notification.actions.orEmpty().filter { it.remoteInputs.isNullOrEmpty() }.mapNotNull { it.title?.toString()?.takeIf { t -> t.isNotBlank() } }

    private fun progress(sbn: StatusBarNotification): Float? {
        val e = sbn.notification.extras
        val max = e.getInt(android.app.Notification.EXTRA_PROGRESS_MAX, 0)
        if (max <= 0 || e.getBoolean(android.app.Notification.EXTRA_PROGRESS_INDETERMINATE)) return null
        return e.getInt(android.app.Notification.EXTRA_PROGRESS, 0).toFloat() / max
    }

    private val systemCache = HashMap<String, Boolean>()

    /**
     * Le notifiche di sistema passano: app preinstallate che non si aprono dal cassetto
     * (auricolari, Bluetooth, Play Services, batteria…). La shell resta fuori: serve per le prove.
     */
    private fun isSystemComponent(pkg: String): Boolean = systemCache.getOrPut(pkg) {
        if (pkg == "com.android.shell") return false
        runCatching {
            val ai = packageManager.getApplicationInfo(pkg, 0)
            val preinstalled = (ai.flags and (android.content.pm.ApplicationInfo.FLAG_SYSTEM or android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
            preinstalled && packageManager.getLaunchIntentForPackage(pkg) == null
        }.getOrElse { it is android.content.pm.PackageManager.NameNotFoundException }
    }

    private fun releaseAt(): Long = Moments.millis(SordinaApp.of(this).moments.nextSummary())

    /** L'ultimo riepilogo di oggi, in millisecondi (0 se non ce n'è ancora stato). */
    private fun lastSummaryMillis(now: Long): Long {
        val t = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
        val h = SordinaApp.of(this).moments.lastSummaryToday(t) ?: return 0
        return Moments.millis(t.toLocalDate().atTime(h / 60, h % 60))
    }

    /** Siamo nei 10 minuti dopo un riepilogo: è il momento in cui Android restituisce le posticipate. */
    private fun justAfterSummary(now: Long): Boolean {
        val last = lastSummaryMillis(now)
        return last > 0 && now - last in 0..10 * 60_000
    }

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
        // Anche quelle in sordina: dopo un aggiornamento o un riavvio le schede tornano, non solo alla prossima notifica
        for (sbn in snoozed.values) if (store.get(sbn.key) != null) card(sbn)
        for (sbn in active.values) if (store.get(sbn.key) == null) runCatching { handle(sbn) }
    }

    private fun find(key: String): StatusBarNotification? =
        activeNotifications.orEmpty().firstOrNull { it.key == key }

    private fun findIntent(key: String): PendingIntent? =
        (activeNotifications.orEmpty().firstOrNull { it.key == key }
            ?: runCatching { snoozedNotifications }.getOrNull()?.firstOrNull { it.key == key })
            ?.notification?.contentIntent

    companion object {
        /** Le frenate "restano qui": posticipate a lungo, escono solo se le leggi o le lasci passare. */
        const val STAY_MILLIS = 30L * 24 * 3_600_000
        private const val SYNC_MILLIS = 60_000L
        @Volatile var instance: SordinaListener? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
            return flat.split(':').any { ComponentName.unflattenFromString(it)?.packageName == context.packageName }
        }

        /** Ricontrolla subito le posticipate (all'apertura di Sordina). */
        fun sync() { instance?.let { l -> l.main.post { runCatching { l.resync() } } } }

        fun rebind(context: Context) {
            if (instance == null && isEnabled(context)) {
                runCatching { requestRebind(ComponentName(context, SordinaListener::class.java)) }
            }
        }

        /** Preme il pulsante n della notifica (lo stesso ordine di [buttons]). */
        fun fire(context: Context, key: String, index: Int): Boolean {
            val sbn = instance?.find(key) ?: return false
            val a = sbn.notification.actions.orEmpty().filter { it.remoteInputs.isNullOrEmpty() && !it.title.isNullOrBlank() }.getOrNull(index) ?: return false
            return runCatching { send(context, a.actionIntent) }.isSuccess
        }

        /** Promemoria fatto: la notifica si chiude. */
        fun done(key: String) { runCatching { instance?.cancelNotification(key) } }

        /** Promemoria: torna fra un'ora. */
        fun later(key: String) { runCatching { instance?.snoozeNotification(key, 3_600_000) } }

        private fun send(context: Context, pi: PendingIntent) {
            if (Build.VERSION.SDK_INT >= 34) {
                val opts = ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                pi.send(context, 0, null, null, null, null, opts.toBundle())
            } else pi.send()
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

        /**
         * Toglie dalla sordina un contatto o un'app: le sue notifiche posticipate tornano subito nella tendina
         * (non vengono lette né cancellate) e Sordina smette di tenerle.
         */
        fun releaseGroup(context: Context, groupKey: String) {
            val app = SordinaApp.of(context)
            val now = System.currentTimeMillis()
            for (h in app.store.held.value.values.filter { it.groupKey == groupKey }) {
                app.store.remove(h.key)
                app.status.cancelAlert(h.key)
                // Una posticipata torna fra mezzo secondo: al ritorno è "lasciata passare" e resta in tendina
                if (h.releaseAt > now) runCatching { instance?.snoozeNotification(h.key, 500) }
            }
            app.status.update()
        }

        fun readGroup(context: Context, groupKey: String) {
            val store = SordinaApp.of(context).store
            markRead(context, store.held.value.values.filter { it.groupKey == groupKey })
        }

        fun readAll(context: Context) {
            markRead(context, SordinaApp.of(context).store.held.value.values.toList())
        }

        /** Chiude queste notifiche come se le avessi lette: quelle in attesa non torneranno al riepilogo. */
        fun readKeys(context: Context, keys: List<String>) {
            val store = SordinaApp.of(context).store
            val held = keys.mapNotNull { store.get(it) }
            markRead(context, held)
            (keys - held.map { it.key }.toSet()).forEach { k -> runCatching { instance?.cancelNotification(k) } }
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
