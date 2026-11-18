package app.sordina.service

import app.sordina.data.Tally
import app.sordina.data.HeldLine
import app.sordina.data.NotifImages
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
import app.sordina.data.CardKind
import app.sordina.data.Held
import app.sordina.data.Level
import app.sordina.data.Moments
import app.sordina.data.Faults

/**
 * Ascolta le notifiche e decide: passano, passano in silenzio o aspettano il riepilogo.
 * Quelle che aspettano vengono posticipate (non cancellate): se Sordina si ferma, tornano da sole.
 */
class SordinaListener : NotificationListenerService() {
    private val labels = java.util.concurrent.ConcurrentHashMap<String, String>()
    private var contexts: Contexts? = null
    /** Tutto il lavoro sulle notifiche gira qui, mai sul thread di Casa: un filo solo, quindi in ordine e senza gare. */
    private val thread = android.os.HandlerThread("sordina-notifiche").apply { start() }
    private val main = android.os.Handler(thread.looper)
    /** Ricontrollo leggero delle posticipate, solo a schermo acceso e solo se qualcosa è in sordina. */
    private val poll = Runnable { pollTick() }
    private var polling = false
    /*
     * Nessun ciclo: le posticipate si ricontrollano solo agli eventi (una notifica nuova della stessa app,
     * lo schermo che si accende, il ritorno a Casa, una modalità che cambia) e al promemoria di [Scheduler],
     * che c'è solo se qualcosa è in sordina e scatta poco prima che Android la restituisca.
     */
    /** Le app con una notifica nuova mentre ne avevano altre in sordina: si ricontrollano solo quelle. */
    private val dirty = HashSet<String>()
    private val dirtyRun = Runnable {
        val pkgs = HashSet(dirty); dirty.clear()
        runCatching { resync(pkgs) }
    }
    private var lastResync = 0L

    /** Una regola è cambiata (da qualunque schermata): fra un attimo si ricontrolla tutto, una volta sola anche se ne cambiano tante */
    private val rulesRun = Runnable { reapply(this, all = true) }
    private var rulesWatch: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null

    override fun onListenerConnected() {
        instance = this
        rulesWatch = SordinaApp.of(this).prefs.watchRules { main.removeCallbacks(rulesRun); main.postDelayed(rulesRun, 400) }
        main.post { Faults.guard("reconcile") { reconcile() }; startPolling() }
        contexts = Contexts(this).also { it.start() }
        Scheduler.refresh(this)
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        thread.quitSafely()
        super.onDestroy()
    }

    /** Una modalità di Android si accende o si spegne: Casa e regole si adeguano subito. */
    override fun onInterruptionFilterChanged(interruptionFilter: Int) {
        Faults.guard("mode") { Tally.onMode(interruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL && interruptionFilter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN) }
        Faults.guard("mode.refresh") { Scheduler.refresh(this) }
        reapply(this)
    }

    override fun onListenerDisconnected() {
        instance = null
        rulesWatch?.let { SordinaApp.of(this).prefs.stopWatching(it) }
        rulesWatch = null
        main.removeCallbacks(rulesRun)
        main.removeCallbacks(dirtyRun)
        main.removeCallbacks(poll); polling = false
        contexts?.stop()
        contexts = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        main.post {
            Faults.guard("posted") { handle(sbn, fresh = true) }
            trayChanges.value++
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        if (reason == REASON_SNOOZED) return
        main.post { Faults.guard("removed") { removed(sbn, reason) } }
    }

    private fun removed(sbn: StatusBarNotification, reason: Int) {
        trayChanges.value++
        val app = SordinaApp.of(this)
        // Toccata la notifica di un'app frenata: sta per andare davanti, il freno guarda subito (è un evento, non un giro)
        if (reason == REASON_CLICK && sbn.packageName in app.prefs.braked) BrakeGuard.opening(this, app.prefs)
        // Aperta o letta nell'app (anche rispondendo): con quella persona stai parlando, i suoi prossimi messaggi passano
        if (reason == REASON_CLICK || reason == REASON_APP_CANCEL) Faults.guard("removed.read") {
            val info = Classifier.read(sbn)
            if (info.isMessage && !info.isGroupSummary && app.prefs.talkPass) Talking.mark(info.pkg, info.peer)
            // Aperta toccando la notifica: quell'apertura non è un'abitudine (il cervello non la impara)
            if (reason == REASON_CLICK) app.brain.onNotificationClick(info.pkg)
            // In auto: mentre scrivi a qualcuno, Non disturbare lascia passare i messaggi
            if (info.isMessage && SordinaApp.of(this).prefs.quietCar) Scheduler.refresh(this)
            // Toccata da te, in una chat a due: il cervello impara a chi rispondi e quando
            if (reason == REASON_CLICK && info.isMessage && !info.isGroupSummary && info.conversation == null) {
                info.person?.let { app.brain.onMessageOpened(info.pkg, it) }
            }
        }
        app.signals.removeSource(sbn.key)
        if (app.store.get(sbn.key) != null) {
            app.store.remove(sbn.key)
            app.status.cancelAlert(sbn.key)
        }
    }

    /** Le regole di adesso, una volta per evento. */
    private fun snapshot(app: SordinaApp): RuleSnapshot = RuleSnapshot.of(packageName, app.prefs, app.people, app.moments.current())

    /** Legge la notifica e aggiunge quello che sa solo il telefono: componente di sistema, app nascosta. */
    private fun info(sbn: StatusBarNotification, app: SordinaApp): NotifInfo = Classifier.read(sbn).let {
        it.copy(system = isSystemComponent(it.pkg), hidden = "${it.pkg}#${it.user}" in hiddenSet(app.prefs.hidden),
            contactName = it.personUri?.let { u -> runCatching { app.people.nameForUri(u) }.getOrNull() })
    }

    /**
     * Una notifica arrivata ([fresh]) o da ricontrollare perché una regola è cambiata (non [fresh]: niente effetti
     * collaterali come conversazioni, cervello, conteggi, schede nuove).
     */
    private fun handle(sbn: StatusBarNotification, fresh: Boolean) {
        val app = SordinaApp.of(this)
        val store = app.store
        val key = sbn.key
        val info = info(sbn, app)
        val verdict = Classifier.classify(info, snapshot(app))
        val level = verdict.level
        // Già letta in Sordina mentre era in sordina, e tornata identica (dalla sospensione, o ripubblicata): si chiude.
        // Solo per quello che aspetterebbe di nuovo: ciò che passa (un allarme, un codice) non si chiude mai da solo.
        if (level == Level.RIEPILOGO && store.alreadyRead(key) { textOf(info) }) { Faults.guard("cancel.read") { cancelNotification(key) }; return }

        // Un'app che ha altre notifiche in sordina: Android non avvisa quando aggiorna quelle posticipate
        // (un nuovo messaggio nella stessa chat), ma di solito ripubblica qualcosa (il riassunto del gruppo).
        if (fresh && store.held.value.values.any { it.pkg == sbn.packageName && it.releaseAt > 0 && it.key != key }) {
            dirty += sbn.packageName
            main.removeCallbacks(dirtyRun)
            main.postDelayed(dirtyRun, 1_500)
        }
        // Hai appena risposto dalla notifica (l'ultimo messaggio è tuo): la chat, o il gruppo, passa per un po'
        if (fresh && info.isMessage && !info.isGroupSummary && app.prefs.talkPass && Classifier.lastIsMine(sbn)) Talking.mark(info.pkg, info.peer)

        val now = System.currentTimeMillis()
        val existing = store.get(key)
        val ranking = Faults.guard("ranking") { Ranking().takeIf { currentRanking.getRanking(key, it) } }
        val importance = ranking?.importance ?: NotificationManager.IMPORTANCE_DEFAULT
        // I tipi e le chat di ogni app, per spegnerli dalla sua pagina in Impostazioni → Notifiche
        if (fresh && !info.system && !info.isGroupSummary && sbn.packageName != packageName) Faults.guard("kinds") {
            val who = Classifier.personKey(info)
            if (who != null) app.kinds.chat(info.pkg, who, info.conversation ?: info.person)
            else app.kinds.channel(info.pkg, info.channel, ranking?.channel?.name?.toString())
        }
        // Le schede in Casa seguono il verdetto: niente da ciò che hai messo in sordina tu (app, persona, tipo, frenata, nascosta)
        val mutedByYou = level == Level.RIEPILOGO && verdict.reason in Classifier.MUTED_BY_YOU
        if (mutedByYou) app.signals.remove(key) else card(sbn, info, scanPlans = fresh)

        // Una notifica fissa o silenziosa (la "connessione attiva" di un'app) messa in sordina con una regola per tipo:
        // sparisce dalla tendina ma non entra nell'elenco, non c'è niente da leggere. Quando Android la rimette, si riposticipa.
        val yours = level == Level.RIEPILOGO && verdict.reason in YOUR_RULES
        val technical = info.ongoing || (importance in 1..NotificationManager.IMPORTANCE_LOW && !yours)
        // Un'app che ti ha appena scritto qualcosa che vedi: è probabile che la apri, il cervello la porta su.
        // Quello che aspetta in sordina no: Sordina non riporta davanti ciò che ha messo da parte.
        if (fresh && level != Level.RIEPILOGO && !technical && !info.system && !info.isGroupSummary && existing == null && sbn.packageName != packageName) {
            Faults.guard("brain.notified") { app.brain.onNotified(sbn.packageName) }
        }
        if (technical) {
            if (level == Level.RIEPILOGO && verdict.reason == Classifier.REASON_CHANNEL) Faults.guard("snooze.tech") { snoozeNotification(key, HOLD_MILLIS) }
            if (existing == null) return
        }
        if (level == null) {
            // Prima aspettava, adesso una regola la lascia passare: resta nella tendina, Sordina la dimentica
            if (existing != null) store.remove(key)
            return
        }
        val label = label(sbn.packageName)

        if (info.isGroupSummary) {
            // Il riassunto del gruppo segue i suoi messaggi: sparisce solo se tutti i suoi messaggi sono in sordina
            val mine = store.held.value.values.filter { it.pkg == sbn.packageName }
            val allWaiting = mine.isNotEmpty() && mine.all { it.releaseAt > 0 }
            if (level == Level.RIEPILOGO && allWaiting) Faults.guard("snooze.summary") { snoozeNotification(key, HOLD_MILLIS) }
            return
        }
        if (info.text.isBlank() && info.title.isBlank()) return

        // In sordina: resta lì finché non la leggi. Android la tiene posticipata per un giorno e Sordina rinnova
        // finché è viva: se si fermasse, entro un giorno tutto torna da solo nella tendina. Nulla si perde.
        val until = if (level == Level.RIEPILOGO) now + HOLD_MILLIS else 0L
        val held = heldOf(sbn, info, label, level, until, verdict.reason, ranking?.channel?.name?.toString())
        if (until > 0) Faults.guard("snooze") { snoozeNotification(key, HOLD_MILLIS) }
        if (fresh && until > 0 && existing == null) Tally.add("held")
        store.put(held, sbn.notification.contentIntent)
        if (until > 0) { Scheduler.plan(this); startPolling() }
        // Il doppione serve solo se Non disturbare sta davvero zittendo l'originale (che resta comunque in tendina)
        // In auto no: mentre guidi non lo leggeresti, e l'originale lo ritrovi quando ti fermi
        val muted = ranking?.matchesInterruptionFilter() == false
        if (fresh && level == Level.SEMPRE && app.silence.isActive() && muted && !app.prefs.quietCar) app.status.alert(held, sbn.notification.contentIntent)
        if (fresh && (verdict.reason == Classifier.REASON_OTP || verdict.reason == Classifier.REASON_PAY_ALERT)) {
            val seen = app.prefs.securityApps
            if (seen[sbn.packageName] != label) app.prefs.securityApps = (seen - sbn.packageName + (sbn.packageName to label)).entries.toList().takeLast(40).associate { it.key to it.value }
        }
    }

    /** Il testo con cui una notifica sta in Sordina: serve anche a riconoscerla se torna uguale dopo che l'hai letta. */
    private fun textOf(info: NotifInfo): String = if (info.isMessage) info.text.ifBlank { info.title }
        else listOf(info.title, info.text).filter { it.isNotBlank() }.distinct().joinToString(": ")

    private fun heldOf(sbn: StatusBarNotification, info: NotifInfo, label: String, level: Level, release: Long, reason: String? = null, chName: String? = null): Held {
        val text = textOf(info)
        return Held(
            key = sbn.key, pkg = sbn.packageName, appLabel = label,
            person = if (info.isMessage) info.person else null,
            conversation = if (info.isMessage) info.conversation else null,
            text = text, time = sbn.postTime, level = level, count = info.count,
            isMessage = info.isMessage, releaseAt = release, postTime = sbn.postTime,
            title = if (info.isMessage) null else info.title.takeIf { it.isNotBlank() && it != info.text },
            shortcut = if (info.isMessage) info.shortcut else null,
            // Più messaggi nella stessa notifica: si tengono tutti, così in Sordina si leggono uno per uno
            lines = if (info.isMessage && info.lines.size > 1) info.lines.takeLast(MAX_LINES).map { (t, s, x) -> HeldLine(t, s, x) } else emptyList(),
            reason = reason,
            channel = info.channel, channelName = chName ?: channelName(sbn.key),
            // Solo per quelle che restano in Sordina: le altre l'immagine la mostrano già nella tendina
            image = if (release > 0) NotifImages.save(this, sbn) else null,
        )
    }

    /** Il nome del canale come lo mostra Android ("Websocket", "Messaggi"…). */
    private fun channelName(key: String): String? = runCatching {
        val r = Ranking()
        if (currentRanking.getRanking(key, r)) r.channel?.name?.toString() else null
    }.getOrNull()

    /**
     * Android non avvisa quando un'app aggiorna o chiude una notifica posticipata: l'aggiornamento resta
     * posticipato in silenzio. Così un codice o un messaggio importante arrivato nella stessa conversazione
     * aspetterebbe il riepilogo. Ogni tanto si confrontano le posticipate con quello che Sordina tiene:
     * se ora passerebbe, torna subito; altrimenti si aggiorna il testo; se l'app l'ha chiusa, si toglie.
     */
    private fun resync(only: Set<String>? = null) {
        val app = SordinaApp.of(this)
        val store = app.store
        val now = System.currentTimeMillis()
        if (only == null) lastResync = now
        val waiting = store.held.value.values.filter { it.releaseAt > 0 && (only == null || it.pkg in only) }
        if (waiting.isEmpty()) return
        val snoozed = Faults.guard("resync.snoozed") { snoozedNotifications }?.associateBy { it.key } ?: return
        val snap = snapshot(app)
        // Rinnova la sospensione prima che Android la restituisca: finché Sordina vive, restano in sordina
        for (h in waiting) if (h.releaseAt - now < RENEW_BELOW && h.key in snoozed) {
            if (runCatching { snoozeNotification(h.key, HOLD_MILLIS) }.isSuccess) store.put(h.copy(releaseAt = now + HOLD_MILLIS), null)
        }
        // Le attive si chiedono solo se serve: è una lista pesante (immagini comprese)
        val active by lazy { activeNotifications.orEmpty().mapTo(HashSet()) { it.key } }
        for (h in waiting) {
            val sbn = snoozed[h.key]
            if (sbn == null) {
                if (h.key !in active) { store.remove(h.key); app.status.cancelAlert(h.key) }
                continue
            }
            val info = info(sbn, app)
            val fresh = heldOf(sbn, info, h.appLabel, h.level, store.get(h.key)?.releaseAt ?: h.releaseAt, h.reason, h.channelName)
            if (fresh.postTime == h.postTime && fresh.text == h.text && fresh.count == h.count && fresh.lines == h.lines) continue
            val verdict = Classifier.classify(info, snap)
            val level = verdict.level
            if (!(level == Level.RIEPILOGO && verdict.reason in Classifier.MUTED_BY_YOU)) card(sbn, info, scanPlans = true)
            if (level != Level.RIEPILOGO) {
                // Torna fra mezzo secondo e passa dalle regole come nuova
                store.remove(h.key)
                runCatching { snoozeNotification(h.key, 500) }
            } else store.put(fresh, sbn.notification.contentIntent)
        }
        // Rinnovate o tolte: il promemoria si sposta (o sparisce se non resta niente)
        Scheduler.plan(this)
    }

    /** Una scheda in Casa per le notifiche che la meritano (OTP, chiamate, timer, biglietti, pacchi). */
    private fun card(sbn: StatusBarNotification, info: NotifInfo = Classifier.read(sbn), scanPlans: Boolean = true) {
        if (sbn.packageName == packageName) return
        // App nascosta: in Casa solo il codice che è passato, senza il nome dell'app né l'immagine; niente piani, niente risposte imparate
        if (info.hidden) {
            Faults.guard("card.hidden") {
                // "Non indovinare per questa app" vale anche qui: niente scheda del codice
                val otp = info.pkg !in SordinaApp.of(this).prefs.noGuess && Classifier.securityOtp(info)
                val c = if (!otp) null else CardDetector.detect(sbn.key, sbn.packageName, app.sordina.tr(app.sordina.R.string.hidden_apps), info.category,
                    info.title, info.text, info.ongoing, true, emptyList(), null, sbn.postTime)?.takeIf { it.kind == CardKind.OTP }
                val signals = SordinaApp.of(this).signals
                if (c == null) signals.remove(sbn.key) else signals.put(c)
            }
            return
        }
        Faults.guard("card") {
            CardDetector.detect(sbn.key, sbn.packageName, label(sbn.packageName), info.category, info.title, info.text,
                info.ongoing, info.pkg !in SordinaApp.of(this).prefs.noGuess && Classifier.securityOtp(info), buttons(sbn), progress(sbn), sbn.postTime)
                ?.let { k -> if (k.kind == CardKind.MUSIC) k else NotifImages.bitmap(this, sbn)?.let { k.copy(image = it) } ?: k }
                ?.copy(pkg = sbn.packageName).let { c ->
                val signals = SordinaApp.of(this).signals
                // Aggiornata e non serve più una scheda (la corsa è finita, il codice è stato sostituito): via quella di prima
                if (c == null) signals.remove(sbn.key) else signals.put(c)
                // Un messaggio con un piano ("domani alle 20 da me", un indirizzo, un codice di spedizione): una scheda per quel momento
                // Le tue risposte nella conversazione: Sordina impara cosa rispondi di solito
                if (scanPlans && info.isMessage && !info.isGroupSummary) Classifier.replyPairs(sbn).forEach { (t, a, b) -> SordinaApp.of(this).replies.learn(a, b, t) }
                val mail = info.category == android.app.Notification.CATEGORY_EMAIL
                if (scanPlans && c == null && (info.isMessage || mail) && !info.ongoing && !info.isGroupSummary && !info.system) {
                    SordinaApp.of(this).plans.scan(sbn.key, label(sbn.packageName), if (mail) null else info.conversation ?: info.person,
                        if (mail) "${info.title}\n${info.text}" else info.text, sbn.postTime, mail)
                }
            }
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

    private val systemCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    /**
     * Le notifiche di sistema passano: app preinstallate che non si aprono dal cassetto
     * (auricolari, Bluetooth, Play Services, batteria…). La shell resta fuori: serve per le prove.
     */
    /** I pacchetti delle app nascoste (la chiave è "pacchetto/attività#utente") */
    private fun hiddenSet(keys: Set<String>): Set<String> = hiddenApps(keys)

    private fun isSystemComponent(pkg: String): Boolean = systemCache.getOrPut(pkg) {
        if (pkg == "com.android.shell") return false
        runCatching {
            val ai = packageManager.getApplicationInfo(pkg, 0)
            val preinstalled = (ai.flags and (android.content.pm.ApplicationInfo.FLAG_SYSTEM or android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
            preinstalled && packageManager.getLaunchIntentForPackage(pkg) == null
        }.getOrElse { it is android.content.pm.PackageManager.NameNotFoundException }
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
        val mutedKeys = store.held.value.values.filter { it.releaseAt > 0 && it.reason in Classifier.MUTED_BY_YOU }.mapTo(HashSet()) { it.key }
        for (sbn in active.values) if (store.get(sbn.key) != null && sbn.key !in mutedKeys) card(sbn, scanPlans = false)
        // Anche quelle in sordina: dopo un aggiornamento o un riavvio le schede tornano, non solo alla prossima notifica
        for (sbn in snoozed.values) if (store.get(sbn.key) != null && sbn.key !in mutedKeys) card(sbn, scanPlans = false)
        for (sbn in active.values) if (store.get(sbn.key) == null) Faults.guard("reconcile.handle") { handle(sbn, fresh = false) }
    }

    private fun find(key: String): StatusBarNotification? =
        activeNotifications.orEmpty().firstOrNull { it.key == key }

    private fun findIntent(key: String): PendingIntent? =
        (activeNotifications.orEmpty().firstOrNull { it.key == key }
            ?: runCatching { snoozedNotifications }.getOrNull()?.firstOrNull { it.key == key })
            ?.notification?.contentIntent

    /**
     * Android non avvisa quando aggiorna una notifica posticipata: un codice arrivato nella stessa chat resterebbe nascosto.
     * Finché qualcosa è in sordina e lo schermo è acceso, si ricontrolla ogni [POLL_MILLIS]. A schermo spento si ferma
     * (riparte con l'accensione): niente lavoro quando non guardi.
     */
    private fun startPolling() {
        if (polling) return
        polling = true
        main.postDelayed(poll, POLL_MILLIS)
    }

    private fun pollTick() {
        polling = false
        val waiting = SordinaApp.of(this).store.held.value.values.any { it.releaseAt > 0 }
        val screenOn = Faults.guard("poll.screen") { getSystemService(android.os.PowerManager::class.java).isInteractive } ?: false
        if (!waiting || !screenOn) return
        Faults.guard("poll.resync") { resync() }
        startPolling()
    }

    companion object {
        /** Ogni quanto si ricontrollano le posticipate a schermo acceso */
        private const val POLL_MILLIS = 60_000L
        /** Le app nascoste come "pacchetto#utente" (la chiave è "pacchetto/attività#utente"; le web app non hanno notifiche proprie) */
        fun hiddenApps(keys: Set<String>): Set<String> = keys.filter { ":web:" !in it.substringBefore('#') }
            .mapTo(HashSet()) { it.substringBefore('/') + "#" + it.substringAfterLast('#') }
        /** Quanto Android tiene posticipata una notifica in sordina; Sordina rinnova prima della fine. */
        const val HOLD_MILLIS = 24L * 3_600_000
        /** Quanti messaggi di una chat tenere in una notifica (gli ultimi) */
        const val MAX_LINES = 30
        /** I motivi del [Classifier] che vengono da una scelta tua, non da un'ipotesi di Sordina */
        private val YOUR_RULES = setOf("regola dell'app", "regola della persona", "regola del tipo")
        /** Si rinnova quando ne mancano meno di queste */
        private const val RENEW_BELOW = 18L * 3_600_000
        /** Un giro completo chiesto da fuori (sblocco, ritorno a Casa) non si ripete prima di così */
        private const val SYNC_GAP = 30_000L
        /** Cresce a ogni notifica arrivata o tolta: chi mostra la tendina si aggiorna solo allora */
        val trayChanges = kotlinx.coroutines.flow.MutableStateFlow(0)
        @Volatile var instance: SordinaListener? = null
            private set

        /**
         * Il motore funziona? Accesso alle notifiche dato, servizio collegato e vivo.
         * null = tutto bene; altrimenti cosa non va.
         */
        fun health(context: Context): Health? = when {
            !isEnabled(context) -> Health.NO_ACCESS
            // Appena avviata, Android collega il servizio dopo qualche secondo: non è un guasto
            instance == null && android.os.SystemClock.elapsedRealtime() - android.os.Process.getStartElapsedRealtime() > 20_000 -> Health.STOPPED
            // Samsung l'ha spenta e lei si è dovuta risvegliare (dopo l'ultima volta che hai visto la strada)
            SamsungSleep.isSamsung && SordinaApp.of(context).prefs.let { it.revivedAt > it.samsungAsked } -> Health.SAMSUNG
            !batteryFree(context) -> Health.BATTERY
            else -> null
        }

        /** Sordina è fuori dall'ottimizzazione della batteria (Android non la addormenta). */
        fun batteryFree(context: Context): Boolean = runCatching {
            context.getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
        }.getOrDefault(true)

        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
            return flat.split(':').any { ComponentName.unflattenFromString(it)?.packageName == context.packageName }
        }

        /**
         * Quello che c'è adesso nella tendina, per dargli una regola dalla pagina Sordina.
         * Anche le notifiche fisse (es. quella sempre accesa di Home Assistant); non i riassunti dei gruppi.
         */
        fun tray(postedBefore: Long = Long.MAX_VALUE): List<app.sordina.data.RuleTarget> {
            val l = instance ?: return emptyList()
            val sbns = runCatching { l.activeNotifications?.toList() }.getOrNull().orEmpty()
            return sbns.filter { it.packageName != l.packageName && it.postTime < postedBefore && !Classifier.read(it).isGroupSummary }
                .sortedByDescending { it.postTime }
                .map { sbn ->
                    val info = Classifier.read(sbn)
                    val who = Classifier.personKey(info)
                    app.sordina.data.RuleTarget(
                        pkg = sbn.packageName, appLabel = l.label(sbn.packageName),
                        channel = info.channel, channelName = l.channelName(sbn.key),
                        person = who, personTitle = if (who != null) info.conversation ?: info.person else null,
                        title = info.title.ifBlank { info.text }, text = if (info.title.isBlank()) "" else info.text,
                        ongoing = info.ongoing, key = sbn.key,
                    )
                }
                .distinctBy { Triple(it.pkg, it.channel, it.person ?: it.key) }
        }

        /** Ricontrolla le posticipate (sblocco, ritorno a Casa). Un giro fatto da poco basta, salvo [force]. */
        fun sync(force: Boolean = false) {
            instance?.let { l ->
                l.main.post {
                    if (force || System.currentTimeMillis() - l.lastResync > SYNC_GAP) Faults.guard("sync") { l.resync() }
                    l.startPolling()
                }
            }
        }

        /** Acceso nelle impostazioni ma scollegato: si chiede ad Android di ricollegarlo e, se non basta, lo si risveglia ([Revive]). */
        fun rebind(context: Context, waited: Boolean = false) {
            if (instance == null && isEnabled(context)) {
                runCatching { requestRebind(ComponentName(context, SordinaListener::class.java)) }
                Revive.kick(context, SordinaListener::class.java, waited)
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
        }

        /**
         * Le regole o la modalità sono cambiate: quello che è in sordina e adesso passerebbe torna nella tendina;
         * con [tray], anche quello che è nella tendina e adesso andrebbe in sordina ci va subito.
         */
        /** [all]: anche le notifiche già nella tendina di tutte le app (una regola cambiata può farle aspettare). */
        fun reapply(context: Context, pkg: String? = null, all: Boolean = false) {
            val l = instance ?: return
            l.main.post {
                Faults.guard("reapply") {
                    val app = SordinaApp.of(context)
                    val snap = l.snapshot(app)
                    val snoozed = Faults.guard("reapply.snoozed") { l.snoozedNotifications }.orEmpty().associateBy { it.key }
                    for (h in app.store.held.value.values.filter { it.releaseAt > 0 }) {
                        val sbn = snoozed[h.key] ?: continue
                        val info = l.info(sbn, app)
                        if (Classifier.classify(info, snap).level != Level.RIEPILOGO) {
                            // Torna fra mezzo secondo e passa dalle regole come nuova
                            app.store.remove(h.key)
                            runCatching { l.snoozeNotification(h.key, 500) }
                        }
                    }
                    // Solo l'app della regola: le altre notifiche nella tendina restano come sono
                    if (pkg != null || all) l.activeNotifications.orEmpty()
                        .filter { (all || it.packageName == pkg) && it.packageName != l.packageName }
                        .forEach { Faults.guard("reapply.handle") { l.handle(it, fresh = false) } }
                }
            }
        }

        fun readGroup(context: Context, groupKey: String) {
            val store = SordinaApp.of(context).store
            markRead(context, store.held.value.values.filter { it.groupKey == groupKey })
        }

        /** Una notifica sola, letta scorrendo nella pagina Sordina. */
        fun readOne(context: Context, key: String) {
            markRead(context, listOfNotNull(SordinaApp.of(context).store.get(key)))
        }

        /** "Tutto letto": solo quello che è in sordina. Ciò che è passato nella tendina non si tocca da qui. */
        fun readAll(context: Context) {
            val now = System.currentTimeMillis()
            markRead(context, SordinaApp.of(context).store.held.value.values.filter { it.releaseAt > now })
        }

        /** Chiude queste notifiche come se le avessi lette: quelle in attesa non torneranno al riepilogo. */
        fun readKeys(context: Context, keys: List<String>) {
            val store = SordinaApp.of(context).store
            val held = keys.mapNotNull { store.get(it) }
            markRead(context, held)
            (keys - held.map { it.key }.toSet()).forEach { k -> runCatching { instance?.cancelNotification(k) } }
        }

        /** Sul thread del motore quando c'è: una notifica letta non può ricomparire per un "arrivata" a metà strada. */
        private fun markRead(context: Context, items: List<Held>) {
            if (items.isEmpty()) return
            val l = instance
            if (l != null) l.main.post { Faults.guard("markRead") { doMarkRead(context, items) } } else doMarkRead(context, items)
        }

        private fun doMarkRead(context: Context, items: List<Held>) {
            val app = SordinaApp.of(context)
            for (h in items) {
                app.store.remove(h.key)
                app.status.cancelAlert(h.key)
                app.signals.removeSource(h.key)
                // Ricordata con il suo testo, solo se aspettava: se l'app la ripubblica uguale si chiude da sola
                if (h.releaseAt > 0) app.store.markRead(h.key, h.text)
                // Chiusa anche se è posticipata (Android chiude anche quelle): altrimenti tornerebbe nella tendina
                // entro un giorno, già letta, ogni volta che l'app l'ha aggiornata nel frattempo
                runCatching { instance?.cancelNotification(h.key) }
            }
        }
    }
}

/** Cosa impedisce al motore di Sordina di lavorare. */
enum class Health { NO_ACCESS, STOPPED, SAMSUNG, BATTERY }
