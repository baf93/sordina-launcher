package app.sordina

import app.sordina.ui.Celebration
import app.sordina.data.Backup
import app.sordina.data.Signals
import app.sordina.data.Tally
import app.sordina.ui.theme.autoAccent
import app.sordina.data.AppIcons
import app.sordina.ui.Motion
import app.sordina.data.Prefs
import app.sordina.data.Held
import app.sordina.data.Habits
import app.sordina.data.RuleScope
import app.sordina.data.RuleTarget
import app.sordina.data.Rules
import app.sordina.R
import app.sordina.data.Categories
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.asImageBitmap
import app.sordina.ui.Said
import app.sordina.ui.Bubble
import app.sordina.ui.sordina.SordinaScreen
import app.sordina.ui.Feel
import app.sordina.ui.LocalFeel
import app.sordina.ui.HomeReply
import app.sordina.ui.HomeReplyTimer
import app.sordina.ui.shortDuration
import app.sordina.data.Contact
import app.sordina.ui.LocalGlass
import app.sordina.ui.theme.Backdrop
import app.sordina.ui.theme.Backdrops
import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import app.sordina.data.Act
import app.sordina.ui.AccessConsent
import app.sordina.ui.SamsungSleepSheet
import app.sordina.service.SamsungSleep
import app.sordina.data.Actions
import app.sordina.data.AppEntry
import app.sordina.data.Candidate
import app.sordina.data.Kind
import app.sordina.data.CardLook
import app.sordina.data.Card
import app.sordina.data.Bar
import app.sordina.data.Birthday
import app.sordina.data.CardAction
import app.sordina.data.CardKind
import app.sordina.data.CardOp
import app.sordina.data.Upcoming
import app.sordina.data.upcoming
import app.sordina.data.agenda
import app.sordina.data.Level
import app.sordina.data.Moment
import app.sordina.data.Moments
import app.sordina.data.Scene
import app.sordina.data.CardDetector
import app.sordina.data.HomeAssistant
import app.sordina.data.Search
import app.sordina.data.CarApps
import app.sordina.data.tomorrow
import app.sordina.data.Proposals
import app.sordina.data.Understanding
import app.sordina.service.Scheduler
import app.sordina.service.SordinaListener
import app.sordina.service.BrakeGuard
import app.sordina.service.SordinaAccess
import app.sordina.service.Health
import app.sordina.service.StatusNotification
import app.sordina.ui.BrakeScreen
import app.sordina.ui.Onboarding
import app.sordina.ui.Ops
import app.sordina.ui.PermState
import app.sordina.ui.SettingsScreen
import app.sordina.ui.apps.AllAppsScreen
import app.sordina.ui.home.HomeScreen
import app.sordina.ui.home.HomeState
import app.sordina.ui.home.CarShortcut
import app.sordina.ui.home.CarPerson
import app.sordina.ui.home.AgendaRow
import app.sordina.ui.intent.IntentScreen
import app.sordina.ui.intent.Notice
import app.sordina.ui.intent.ProposalActions
import app.sordina.ui.intent.VoiceState
import app.sordina.ui.rememberNow
import app.sordina.ui.theme.S
import app.sordina.ui.theme.SordinaTheme
import app.sordina.ui.theme.ThemeFamily
import app.sordina.ui.theme.ThemeMode
import app.sordina.ui.theme.paper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.util.Locale

/* L'interfaccia di MainActivity: le barre di sistema e la radice con tutte le schermate. */

@Composable
internal fun MainActivity.SystemBars() {
    val view = LocalView.current
    // Le Case di notte e in auto hanno il loro fondo: la barra segue quello
    val dark = S.c.dark || (screen == Screen.HOME && scene == Scene.NIGHT) || (screen == Screen.HOME && scene == Scene.CAR && S.c.dark)
    SideEffect {
        val c = WindowCompat.getInsetsController(window, view)
        c.isAppearanceLightStatusBars = !dark
        c.isAppearanceLightNavigationBars = !dark
    }
}

@Composable
internal fun MainActivity.Root() {
    // In Casa "indietro" non porta da nessuna parte: lo si ignora, così Android non mostra l'animazione di uscita
    BackHandler(enabled = screen == Screen.HOME) { }
    BackHandler(enabled = screen != Screen.HOME && screen != Screen.ONBOARDING) {
        screen = when (screen) {
            Screen.ALL_APPS -> { backFromApps = true; Screen.INTENT }
            else -> Screen.HOME
        }
    }
    val held by app.store.held.collectAsState()
    val today by app.store.today.collectAsState()
    val now = rememberNow()
    val groups = remember(held) { app.store.groups() }
    val signalCards by app.signals.cards.collectAsState()
    val planMap by app.plans.plans.collectAsState()
    val dismissedCards by app.signals.dismissedKeys.collectAsState()
    val music by app.signals.music.collectAsState()

    // Fissa o togli una chiave (un'app, oppure "do:" + un'azione); al massimo 4, dentro i 6 blocchi
    fun togglePinKey(key: String, label: String) {
        val p = prefs.pinned
        when {
            key in p -> prefs.pinned = p - key
            p.size >= Prefs.MAX_PINNED -> {
                notice = Notice(tr(R.string.pins_full, Prefs.MAX_PINNED, label))
                if (prefs.haptics) window.decorView.performHapticFeedback(
                    if (Build.VERSION.SDK_INT >= 30) android.view.HapticFeedbackConstants.REJECT else android.view.HapticFeedbackConstants.LONG_PRESS,
                )
                return
            }
            else -> prefs.pinned = p + key
        }
        prefsVersion++
        refreshProposals(force = true)
    }
    val actionsP = ProposalActions(
        isPinned = { a -> prefsVersion.let { prefs.pinned.contains(a.key) } },
        togglePin = { a -> togglePinKey(a.key, a.label) },
        isPinnedAction = { c -> prefsVersion.let { proposals.brainItem[c.id]?.let { Prefs.PIN_ACTION + it in prefs.pinned } == true } },
        togglePinAction = { c -> proposals.brainItem[c.id]?.let { togglePinKey(Prefs.PIN_ACTION + it, c.title) } },
        exclude = { a ->
            prefs.excluded = prefs.excluded + a.key
            prefs.pinned = prefs.pinned - a.key
            refreshProposals(force = true)
        },
        info = { a -> catalog.openInfo(a) },
        brake = { a ->
            prefs.braked = prefs.braked + a.packageName
            changed()
        },
        why = { a -> proposals.why["app:${a.key}"] ?: if (a.key in prefs.pinned) tr(R.string.why_pinned) else tr(R.string.why_used) },
        whyOf = { c -> proposals.why[c.id] ?: tr(R.string.why_used) },
        hide = { c ->
            proposals.brainItem[c.id]?.let { prefs.excluded = prefs.excluded + "brain:$it" }
            refreshProposals(force = true)
        },
    )
    // Le proposte che stai guardando: se poi cerchi altro, Sordina sa che non erano quelle giuste
    LaunchedEffect(screen, proposals) {
        if (screen == Screen.INTENT) app.brain.shown(proposals.items.mapNotNull { app.brain.itemOf(it, "") })
    }
    LaunchedEffect(screen) {
        if (screen == Screen.INTENT) refreshProposals(force = false)
        if (screen == Screen.HOME) refreshExtras()
    }
    val feelNow = LocalFeel.current
    // Di notte e in auto Casa ha un fondo pieno: la foto non serve
    val bare = (screen == Screen.HOME && scene != Scene.NIGHT && scene != Scene.CAR || screen == Screen.INTENT) && backdrop.active
    // Il momento e la Casa si ricontrollano ogni minuto: la sera e la notte arrivano anche a schermo acceso
    LaunchedEffect(now) { moment = app.moments.current(); scene = app.moments.scene(); checkHealth() }
    LaunchedEffect(scene) { if (scene != Scene.DAY) lifecycleScope.launch(Dispatchers.IO) { sceneExtras(scene) } }
    Box(if (bare) Modifier.fillMaxSize() else Modifier.fillMaxSize().paper()) {
        if (bare) backdrop.image?.let { img ->
            androidx.compose.foundation.Image(img, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        }
        // Sotto le scritte in alto un velo sfumato, solo se lo sfondo lo chiede: scuro col testo chiaro, chiaro col testo scuro
        if (bare && backdrop.topScrim > 0f) {
            val veil = if (backdrop.topDark == true) androidx.compose.ui.graphics.Color(0xFFF6F4EF) else androidx.compose.ui.graphics.Color.Black
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to veil.copy(alpha = backdrop.topScrim), 0.42f to veil.copy(alpha = backdrop.topScrim * 0.75f), 0.62f to androidx.compose.ui.graphics.Color.Transparent,
            )))
        }
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                // Verso l'alto si sale (Casa → ricerca → app), tornando si scende; spente le animazioni, si cambia e basta
                val up = targetState.ordinal > initialState.ordinal
                if (!feelNow.animations) androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                else Motion.vertical(up)
            },
            label = "screen",
            // Sui tablet una colonna da telefono al centro: il fondo resta a tutto schermo, le cose non si stirano
            modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter)
                .widthIn(max = 560.dp).fillMaxHeight(),
        ) { s ->
            when (s) {
                Screen.HOME -> {
                    val waiting = groups.sumOf { g -> g.sumOf { it.count } }
                    val ms = now.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val cards = remember(health, signalCards, planMap, music, events, now, scene, dismissedCards) {
                        val calendar = events.map { eventCard(it, ms) }
                        val calendarTitles = calendar.map { Search.normalize(it.title) }.toSet()
                        // Riunioni avvisate da un'app: l'etichetta "Tra 10 min" si aggiorna; se il calendario ce l'ha già, basta quella
                        // I piani dei messaggi compaiono quando arrivano e tornano poco prima, con Naviga
                        val fromNotifs = (signalCards.values + app.plans.cards(ms))
                            .filterNot { it.startsAt != null && Search.normalize(it.title) in calendarTitles }
                            .map { k -> k.startsAt?.takeIf { s -> s - ms < 3 * 3_600_000L }?.let { s -> k.copy(label = startLabel(s, ms) + " · " + k.label) } ?: k } // "Tra 10 min" solo quando manca poco: domani resta "domani alle 20"
                            // Lo stesso piano letto due volte (dal modello e dalle regole, o da due notifiche): una scheda, la più ricca
                            .filter { !Signals.hidden(it, dismissedCards) }
                            .sortedByDescending { it.actions.size }.distinctBy { if (it.kind == CardKind.PLAN) "plan|${it.label}|${it.title}" else it.key }
                        (listOfNotNull(health?.let(::healthCard)) + fromNotifs + listOfNotNull(music) + calendar)
                            // Una scheda senza titolo non dice niente: mai in Casa
                            .filter { it.expiresAt > ms && !Signals.hidden(it, dismissedCards) && it.title.isNotBlank() }
                            // Ogni Casa tiene solo le schede che le servono: in auto e di notte quasi nulla
                            // Sordina che non funziona si dice sempre, in ogni Casa
                            .filter { k -> k.kind == CardKind.HEALTH || when (scene) {
                                Scene.CAR -> k.kind in setOf(CardKind.CALL, CardKind.MUSIC, CardKind.TIMER, CardKind.RIDE) ||
                                    // In auto: l'impegno con un indirizzo, per andarci
                                    (k.kind == CardKind.PLAN && k.actions.any { it.op is CardOp.Navigate })
                                Scene.NIGHT -> k.kind in setOf(CardKind.CALL, CardKind.MUSIC, CardKind.TIMER, CardKind.OTP, CardKind.ASK)
                                Scene.EVENING -> k.kind != CardKind.HABIT
                                Scene.DAY -> true
                            } }
                            // Il fumetto dice solo cose in corso o che non aspettano: gli avvisi restano nelle notifiche,
                            // gli impegni nell'agenda finché non mancano 15 minuti
                            .filter { k -> scene == Scene.CAR || k.kind in MainActivity.BUBBLE_KINDS ||
                                (k.kind == CardKind.EVENT && (k.startsAt ?: k.time) - ms <= 15 * 60_000L) ||
                                (k.kind == CardKind.PLAN && k.startsAt?.let { s -> s - ms <= 15 * 60_000L } == true) }
                            // Quelle che scarti sempre spariscono; l'ordine segue l'urgenza e quello che usi davvero
                            .filterNot { app.cardSense.muted(it, ms) }
                            .sortedWith(compareBy<Card> { app.cardSense.rank(it, ms) }.thenByDescending { it.time })
                            .take(6)
                    }
                    // Solo nella versione di prova: le schede in Casa finiscono nel log, per controllarle senza scorrere la pila
                    if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) LaunchedEffect(cards) {
                        cards.forEach { k -> android.util.Log.d("SordinaCards", "${k.kind} | ${k.label} | ${k.title} | ${k.subtitle} | ${k.actions.joinToString { it.label }}") }
                    }
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) { HomeScreen(
                        state = HomeState(
                            waiting = waiting,
                            reply = reply,
                            until = null,
                            moment = if (moment == Moment.NONE) null else moment.label,
                            // Senza schede resta solo quella che dice che Sordina non funziona
                            cards = if (prefs.homeCards) cards else cards.filter { it.kind == CardKind.HEALTH },
                            agenda = remember(agendaEvents, moment, now, scene, health) { agendaRows(waiting) }
                                // Senza impegni in Casa non resta nulla
                                .let { r -> if (prefs.homeAgenda) r else emptyList() },
                            weather = if (prefs.homeWeather) weatherNow else null,
                            listening = voice.listening,
                            partial = voice.partial,
                            level = voice.level,
                            topDark = backdrop.topDark,
                            bottomDark = backdrop.bottomDark,
                            translucent = backdrop.active,
                            glass = backdrop.active && glassPref,
                            scene = scene,
                            alarm = remember(now, scene) { if (scene == Scene.EVENING || scene == Scene.NIGHT) nextAlarm() else null },
                            alarmAt = remember(now, scene) { if (scene == Scene.NIGHT) nextAlarmAt() else null },
                            tomorrow = remember(tomorrowEvents) { tomorrowEvents.map { e -> AgendaRow(hm(java.time.Instant.ofEpochMilli(e.begin).atZone(java.time.ZoneId.systemDefault()).let { it.hour * 60 + it.minute }), e.title, "event:${e.id}") } },
                            tomorrowWeather = if (prefs.homeWeather) tomorrowWeather else null,
                            sceneLine = remember(scene, moment, prefsVersion) { sceneLine(scene) },
                            recap = recap,
                            people = carPeople,
                            next = remember(agendaEvents, scene, now) { if (scene == Scene.CAR) nextEvent() else null },
                            carApps = remember(apps, prefsVersion, scene) {
                                if (scene != Scene.CAR) emptyList()
                                else carApps().mapNotNull { p -> apps.firstOrNull { it.packageName == p }?.let { CarShortcut(p, it.label, catalog.icon(it)) } }
                            },
                        ),
                        groups = groups,
                        onScene = ::onScene,
                        onSwipeUp = { screen = Screen.INTENT },
                        onSwipeDown = { expandNotifications() },
                        onLongPress = { screen = Screen.SETTINGS },
                        onSearch = { screen = Screen.INTENT },
                        onMicDown = ::voiceDown,
                        onMicUp = ::voiceUp,
                        onAction = ::cardAction,
                        onCard = ::cardTap,
                        onAgenda = ::onAgenda,
                        onSheet = { if (it) screen = Screen.SORDINA },
                        onOpenHeld = { h -> SordinaListener.open(this@Root, h.key) },
                        onReadAll = { SordinaListener.readAll(this@Root) },
                        onReadGroup = { k -> SordinaListener.readGroup(this@Root, k) },
                        onDoubleTap = if (remember(prefsVersion) { prefs.doubleTapLock }) ({
                            // Senza l'accessibilità di Sordina non si può spegnere: si dice come accenderla
                            if (!SordinaAccess.lock()) notice = Notice(tr(R.string.double_tap_needs))
                        }) else null,
                    )
                    Celebration(celebrate, onOpen = { openSticker = celebrate.firstOrNull(); celebrate = emptyList(); screen = Screen.SETTINGS }) { celebrate = emptyList() } }
                }
                Screen.INTENT -> IntentScreen(
                    showIcons = remember(prefsVersion) { prefs.appIcons },
                    topDark = backdrop.topDark,
                    glass = backdrop.active && glassPref,
                    catalog = catalog,
                    query = query,
                    onQuery = { query = it },
                    understand = ::understand,
                    proposals = proposals.items,
                    needsUsagePermission = proposals.needsPermission,
                    actions = actionsP,
                    // Tornando indietro da Tutte le app la tastiera resta chiusa: stavi guardando, non scrivendo
                    keyboardOnOpen = remember(prefsVersion) { prefs.keyboardOnOpen && !backFromApps }.also { androidx.compose.runtime.SideEffect { backFromApps = false } },
                    voice = voice,
                    notice = notice,
                    onRun = ::run,
                    // Una proposta presa: Sordina aveva visto giusto
                    onLaunch = { a -> if (query.isBlank()) app.brain.hit("app:${a.packageName}"); launch(a) },
                    onAllApps = { screen = Screen.ALL_APPS },
                    onHome = { screen = Screen.HOME },
                    onGrantUsage = ::openUsageSettings,
                    onVoiceDown = ::voiceDown,
                    onVoiceUp = ::voiceUp,
                )
                Screen.ALL_APPS -> AllAppsScreen(
                    icon = if (remember(prefsVersion) { prefs.appIcons }) { a -> catalog.icon(a) } else null,
                    cachedIcon = { a -> catalog.cachedIcon(a) },
                    apps = apps,
                    hidden = hidden,
                    // Cercata nell'elenco: andava proposta
                    onLaunch = { a -> app.brain.missed("app:${a.packageName}"); launch(a) },
                    onInfo = { catalog.openInfo(it) },
                    onUninstall = { catalog.uninstall(it) },
                    onToggleHidden = { a ->
                        hidden = if (a.key in hidden) hidden - a.key else hidden + a.key
                        prefs.hidden = hidden
                        // Sparisce subito anche dalle proposte
                        refreshProposals(force = true)
                    },
                    isBraked = { a -> prefsVersion.let { a.packageName in prefs.braked } },
                    onToggleBrake = { a ->
                        val b = prefs.braked
                        prefs.braked = if (a.packageName in b) b - a.packageName else b + a.packageName
                        changed()
                    },
                    isPinned = actionsP.isPinned,
                    onTogglePin = { a ->
                        // Qui l'avviso della ricerca non si vede: se sono già tutte occupate lo dice un toast
                        if (a.key !in prefs.pinned && prefs.pinned.size >= Prefs.MAX_PINNED)
                            android.widget.Toast.makeText(this@Root, tr(R.string.pins_full, Prefs.MAX_PINNED, a.label), android.widget.Toast.LENGTH_LONG).show()
                        else actionsP.togglePin(a)
                    },
                )
                Screen.SETTINGS -> SettingsScreen(
                    app = app,
                    apps = apps,
                    perms = perms,
                    family = family,
                    mode = mode,
                    version = versionName,
                    backdrop = backdrop.mode,
                    backdropAccent = backdrop.accent,
                    accent = accentPref,
                    onFamily = { family = it; prefs.family = it },
                    onMode = { mode = it; prefs.mode = it },
                    onWipe = ::wipe,
                    ops = this@Root,
                    onBack = { screen = Screen.HOME },
                    sticker = openSticker.also { androidx.compose.runtime.SideEffect { openSticker = null } },
                    hiddenApps = {
                        AllAppsScreen(
                            showHidden = true,
                            icon = if (remember(prefsVersion) { prefs.appIcons }) { a -> catalog.icon(a) } else null,
                            cachedIcon = { a -> catalog.cachedIcon(a) },
                            apps = apps,
                            hidden = hidden,
                            // Aperta da qui: niente freno, e il cervello non la impara
                            onLaunch = { a -> openHidden(a) },
                            onInfo = { catalog.openInfo(it) },
                            onUninstall = { catalog.uninstall(it) },
                            onToggleHidden = { a ->
                                hidden = hidden - a.key
                                prefs.hidden = hidden
                                changed()
                            },
                            isBraked = { a -> prefsVersion.let { a.packageName in prefs.braked } },
                            onToggleBrake = { a ->
                                val b = prefs.braked
                                prefs.braked = if (a.packageName in b) b - a.packageName else b + a.packageName
                                changed()
                            },
                        )
                    },
                )
                Screen.BRAKE -> brakeTarget?.let { a ->
                    // Quanto vale lasciare stare e quanto costa aprire: fissati all'arrivo, uguali a quelli mostrati
                    // Lasciare stare vale una volta per tentazione (subito dopo un altro premio vale 0); aprire costa sempre
                    val visit = remember(a.key) { app.habits.minutesPerVisit(a.packageName) }
                    val gain = remember(a.key) { app.habits.skipGain(visit) }
                    val cost = remember(a.key) { app.habits.openCost(a.packageName, visit) }
                    BrakeScreen(
                        app = a,
                        icon = remember(a.key) { catalog.icon(a) },
                        today = remember(a.key) { app.habits.appToday(a.packageName) },
                        seconds = remember(a.key) { brakeWait(a.packageName) },
                        streak = remember(a.key) { app.habits.streak() },
                        gain = gain,
                        cost = cost,
                        onOpen = { habit ->
                            // Prima il costo (conta le aperture precedenti), poi si registra questa
                            app.habits.logOpen(a.packageName, cost)
                            app.habits.logBrake(a.packageName, habit)
                            BrakeGuard.passed(a.packageName)
                            val via = brakeVia?.let { packageManager.getLaunchIntentForPackage(it) }
                            if (via != null) runCatching { startActivity(via.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { catalog.launch(a) } else catalog.launch(a)
                        },
                        onSkip = { app.habits.logSkip(a.packageName, gain); app.habits.streak() to app.habits.newMedals() },
                        onDone = { screen = Screen.HOME },
                    )
                } ?: run { screen = Screen.HOME }
                Screen.SORDINA -> {
                    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { hiddenRevealed = false } }
                    SordinaScreen(
                    groups = if (emptyPreview) emptyList() else groups,
                    overrides = remember(prefsVersion) { prefs.categoryOverrides },
                    braked = remember(prefsVersion) { prefs.braked },
                    order = remember(prefsVersion) { when (prefs.sordinaOrder) { 1 -> null; 2 -> Categories.custom(prefs.categoryOrder); else -> Categories.AUTO } },
                    icon = ::notifIcon,
                    onOpen = { h -> SordinaListener.open(this@Root, h.key) },
                    onReadKeys = { keys -> SordinaListener.readKeys(this@Root, keys) },
                    onReadAll = { SordinaListener.readAll(this@Root); screen = Screen.HOME },
                    onLetPass = ::letPass,
                    ruleOf = { t, s -> Rules.get(prefs, t, s) },
                    onRule = ::setRule,
                    onMove = { k, cat -> prefs.categoryOverrides = prefs.categoryOverrides + (k to cat.name); changed() },
                    onBack = { screen = Screen.HOME },
                    hiddenRevealed = hiddenRevealed,
                    onRevealHidden = { unlock(getString(R.string.hidden_apps)) { hiddenRevealed = true } },
                    holdHint = remember(prefsVersion) { !prefs.sordinaHoldLearned },
                    onHoldLearned = { if (!prefs.sordinaHoldLearned) { prefs.sordinaHoldLearned = true; changed() } },
                )
                }

                Screen.ONBOARDING -> Onboarding(
                    app = app,
                    perms = perms,
                    apps = apps,
                    usage = proposals.usage,
                    startStep = onbStep,
                    ops = this@Root,
                    onStep = { prefs.onboardStep = it },
                    onDone = {
                        prefs.onboarded = true
                        changed()
                        screen = Screen.HOME
                    },
                )
            }
        }
    }
}
