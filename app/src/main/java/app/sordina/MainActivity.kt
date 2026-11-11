package app.sordina

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
import app.sordina.data.Actions
import app.sordina.data.AppEntry
import app.sordina.data.Candidate
import app.sordina.data.Card
import app.sordina.data.CardAction
import app.sordina.data.CardKind
import app.sordina.data.CardOp
import app.sordina.data.Upcoming
import app.sordina.data.upcoming
import app.sordina.data.next
import app.sordina.data.Level
import app.sordina.data.Moment
import app.sordina.data.Proposals
import app.sordina.data.Understanding
import app.sordina.service.Scheduler
import app.sordina.service.SordinaListener
import app.sordina.service.StatusNotification
import app.sordina.ui.BrakeScreen
import app.sordina.ui.Onboarding
import app.sordina.ui.Ops
import app.sordina.ui.PermState
import app.sordina.ui.SettingsScreen
import app.sordina.ui.apps.AllAppsScreen
import app.sordina.ui.home.HomeScreen
import app.sordina.ui.home.HomeState
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
import app.sordina.ui.waiting.WaitingScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.util.Locale

enum class Screen { HOME, INTENT, ALL_APPS, SETTINGS, WAITING, BRAKE, ONBOARDING }

class MainActivity : ComponentActivity(), Ops {

    private val app by lazy { SordinaApp.of(this) }
    private val prefs get() = app.prefs
    private val catalog get() = app.catalog
    private lateinit var proposalsEngine: Proposals
    private lateinit var actions: Actions
    private var appsCallback: LauncherApps.Callback? = null

    private var screen by mutableStateOf(Screen.HOME)
    private var apps by mutableStateOf<List<AppEntry>>(emptyList())
    private var proposals by mutableStateOf(Proposals.Result(emptyList(), emptyMap(), false, -1))
    private var family by mutableStateOf(ThemeFamily.CARTA)
    private var mode by mutableStateOf(ThemeMode.AUTO)
    private var hidden by mutableStateOf<Set<String>>(emptySet())
    private var prefsVersion by mutableIntStateOf(0)
    private var perms by mutableStateOf(PermState())
    private var query by mutableStateOf("")
    private var notice by mutableStateOf<Notice?>(null)
    private var voice by mutableStateOf(VoiceState(Build.VERSION.SDK_INT >= 31, false, ""))
    private var brakeTarget by mutableStateOf<AppEntry?>(null)
    private var events by mutableStateOf<List<Upcoming>>(emptyList())
    private var habitCards by mutableStateOf<List<Card>>(emptyList())
    private var doneHabits by mutableStateOf<Set<String>>(emptySet())
    private var nextEvent by mutableStateOf<Upcoming?>(null)
    private var weatherNow by mutableStateOf<String?>(null)
    private var moment by mutableStateOf(Moment.NONE)
    private var recognizer: SpeechRecognizer? = null

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshPerms()
        changed()
    }
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            contentResolver.openOutputStream(uri)?.use { it.write(prefs.export().toByteArray()) }
            notice = Notice("Configurazione esportata")
        }
    }
    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val ok = runCatching {
                val text = contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: return@runCatching
                prefs.import(text)
            }.isSuccess
            family = prefs.family; mode = prefs.mode; hidden = prefs.hidden
            prefsVersion++
            changed()
            notice = Notice(if (ok) "Configurazione importata" else "File non valido")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        proposalsEngine = Proposals(this)
        actions = Actions(this, catalog, app.people, app.ha, prefs)
        family = prefs.family
        mode = prefs.mode
        hidden = prefs.hidden
        if (!prefs.onboarded) screen = Screen.ONBOARDING
        handleIntent(intent)

        appsCallback = catalog.registerChanges { reloadApps(forceProposals = true) }
        reloadApps(forceProposals = true)

        setContent {
            SordinaTheme(family, mode) {
                SystemBars()
                Root()
            }
        }
    }

    override fun onDestroy() {
        appsCallback?.let { catalog.unregister(it) }
        recognizer?.destroy()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (screen == Screen.ONBOARDING) return
        when {
            intent?.action == ACTION_WAITING -> screen = Screen.WAITING
            intent?.hasCategory(Intent.CATEGORY_HOME) == true -> { screen = Screen.HOME; query = "" }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPerms()
        SordinaListener.rebind(this)
        moment = app.moments.current()
        refreshProposals(force = false)
        refreshExtras()
    }

    override fun onStop() {
        super.onStop()
        // Dopo aver aperto un'app, al ritorno si trova Casa (le impostazioni restano dove erano)
        if (screen != Screen.SETTINGS && screen != Screen.ONBOARDING) screen = Screen.HOME
        query = ""
        notice = null
    }

    // Dati

    private fun reloadApps(forceProposals: Boolean) {
        lifecycleScope.launch {
            apps = withContext(Dispatchers.Default) { catalog.load() }
            refreshProposals(forceProposals)
        }
    }

    /** Le proposte cambiano solo al cambio di fascia (mezz'ora) o di momento, mai mentre le guardi. */
    private fun refreshProposals(force: Boolean) {
        val slot = Proposals.slotOf(System.currentTimeMillis()) * 10 + moment.ordinal
        val permissionChanged = proposals.needsPermission == proposalsEngine.hasPermission()
        if (!force && slot == proposals.slot && !permissionChanged) return
        val current = apps
        if (current.isEmpty()) return
        lifecycleScope.launch {
            val r = withContext(Dispatchers.Default) { proposalsEngine.compute(current, prefs) }
            proposals = r.copy(slot = slot)
        }
    }

    /** Casa: eventi vicini e schede "di solito". */
    private fun refreshExtras() {
        val current = apps
        lifecycleScope.launch(Dispatchers.IO) {
            events = app.calendars.upcoming(this@MainActivity)
            nextEvent = app.calendars.next(this@MainActivity)
            weatherNow = app.weather.now()
            if (current.isNotEmpty()) habitCards = app.routines.suggestions(current)
        }
    }

    private fun refreshPerms() {
        val was = perms
        fun g(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
        perms = PermState(
            home = isDefaultHome(),
            listener = SordinaListener.isEnabled(this),
            policy = app.silence.hasAccess(),
            usage = proposalsEngine.hasPermission(),
            post = Build.VERSION.SDK_INT < 33 || g(Manifest.permission.POST_NOTIFICATIONS),
            contacts = g(Manifest.permission.READ_CONTACTS),
            calendar = g(Manifest.permission.WRITE_CALENDAR),
            mic = g(Manifest.permission.RECORD_AUDIO),
            call = g(Manifest.permission.CALL_PHONE),
        )
        if (perms.contacts && !was.contacts) app.people.invalidate()
    }

    private fun isDefaultHome(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName == packageName
    }

    // Azioni

    private fun launch(a: AppEntry) {
        if (a.packageName in prefs.braked) {
            brakeTarget = a
            screen = Screen.BRAKE
        } else catalog.launch(a)
    }

    private fun understand(q: String): Understanding = app.intents.understand(q, apps.filter { it.key !in hidden || q.length > 2 }, proposals.usage)

    private fun run(c: Candidate, index: Int) {
        app.intents.chosen(query, c, index)
        when (val act = c.act) {
            is Act.OpenApp -> launch(act.app)
            Act.SordinaSettings -> { query = ""; screen = Screen.SETTINGS }
            else -> lifecycleScope.launch {
                val out = actions.run(act)
                val stays = act is Act.Timer || act is Act.Alarm || act is Act.Event || act is Act.Torch ||
                    act is Act.Media || act is Act.Ha || act is Act.Calc
                if (stays && out.ok) query = ""
                val undo = out.undo
                if (out.message != null) notice = Notice(out.message, undo?.let { u -> { lifecycleScope.launch { u(); notice = Notice("Annullato") } } })
                if (out.message != null && screen == Screen.HOME) android.widget.Toast.makeText(this@MainActivity, out.message, android.widget.Toast.LENGTH_SHORT).show()
                if (out.ok) app.routines.onCommand(c.title, query.ifBlank { c.title })
            }
        }
    }

    // Schede di Casa

    private fun eventCard(e: Upcoming, now: Long): Card {
        val min = ((e.begin - now) / 60_000).toInt()
        val label = when {
            min <= 0 -> "Adesso"
            min < 60 -> "Tra $min min"
            else -> "Alle " + java.time.Instant.ofEpochMilli(e.begin).atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("H:mm"))
        }
        val actions = buildList {
            e.link?.let { add(CardAction("Partecipa", CardOp.OpenUrl(it))) }
            e.location?.let { add(CardAction("Naviga", CardOp.Navigate(it))) }
            if (isEmpty()) add(CardAction("Apri", CardOp.OpenEvent(e.id)))
        }
        return Card("event:${e.id}:${e.begin}", CardKind.EVENT, label, e.title, e.location, actions, e.begin, e.end)
    }

    /** Apre il lettore: prima la sua schermata "in riproduzione", altrimenti l'app. */
    private fun openMusic() {
        val c = app.signals.controller ?: return
        val ok = c.sessionActivity?.let { pi ->
            runCatching {
                if (Build.VERSION.SDK_INT >= 34) {
                    val o = android.app.ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    pi.send(this, 0, null, null, null, null, o.toBundle())
                } else pi.send()
            }.isSuccess
        } ?: false
        if (!ok) packageManager.getLaunchIntentForPackage(c.packageName)?.let { start(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** "Oggi 15:30 Dentista", "Domani 9:00 Riunione", "Ven 9:00 Volo" */
    private fun nextLabel(e: Upcoming): String {
        val t = java.time.Instant.ofEpochMilli(e.begin).atZone(java.time.ZoneId.systemDefault())
        val today = java.time.LocalDate.now()
        val day = when (t.toLocalDate()) {
            today -> if (e.begin <= System.currentTimeMillis()) "Ora" else "Alle"
            today.plusDays(1) -> "Domani"
            else -> t.format(java.time.format.DateTimeFormatter.ofPattern("EEE", Locale.getDefault())).replace(".", "").replaceFirstChar { it.uppercase() }
        }
        val hour = t.format(java.time.format.DateTimeFormatter.ofPattern("H:mm"))
        return if (day == "Ora") "Ora: ${e.title}" else "$day $hour ${e.title}"
    }

    private fun openWeather() {
        val pkgs = listOf("com.sec.android.daemonapp", "com.google.android.apps.weather", "com.accuweather.android")
        val i = pkgs.firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }
        if (i != null) start(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        else openUrl("https://www.google.com/search?q=meteo")
    }

    private fun cardTap(card: Card) {
        when {
            card.kind == CardKind.MUSIC -> openMusic()
            card.kind == CardKind.EVENT -> card.key.split(':').getOrNull(1)?.toLongOrNull()?.let { cardAction(card, CardAction("", CardOp.OpenEvent(it))) }
            card.kind == CardKind.HABIT -> card.actions.firstOrNull()?.let { cardAction(card, it) }
            else -> SordinaListener.open(this, card.key)
        }
    }

    private fun cardAction(card: Card, a: CardAction) {
        val music = app.signals.controller?.transportControls
        when (val op = a.op) {
            is CardOp.Copy -> {
                val cm = getSystemService(android.content.ClipboardManager::class.java)
                val clip = android.content.ClipData.newPlainText("codice", op.text)
                clip.description.extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
                cm.setPrimaryClip(clip)
                if (Build.VERSION.SDK_INT < 33) android.widget.Toast.makeText(this, "Copiato", android.widget.Toast.LENGTH_SHORT).show()
                app.signals.dismiss(card.key)
            }
            is CardOp.OpenNotif -> SordinaListener.open(this, op.key)
            is CardOp.Dial -> start(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(op.number))))
            is CardOp.Navigate -> start(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(op.place))))
                || start(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(op.place))))
            is CardOp.OpenUrl -> openUrl(op.url)
            is CardOp.OpenEvent -> start(Intent(Intent.ACTION_VIEW, android.content.ContentUris.withAppendedId(android.provider.CalendarContract.Events.CONTENT_URI, op.id)))
            is CardOp.AddEvent -> {
                if (!app.calendars.hasPermission()) { request(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR); return }
                lifecycleScope.launch {
                    val id = withContext(Dispatchers.IO) { app.calendars.default()?.let { app.calendars.insert(it, op.title, op.whenAt) } }
                    android.widget.Toast.makeText(this@MainActivity, if (id != null) "Aggiunto" else "Non riuscito", android.widget.Toast.LENGTH_SHORT).show()
                    if (id != null) app.signals.dismiss(card.key)
                }
            }
            is CardOp.OpenPackage -> {
                doneHabits = doneHabits + card.key
                apps.firstOrNull { it.packageName == op.pkg && !it.isWork }?.let(::launch)
                    ?: packageManager.getLaunchIntentForPackage(op.pkg)?.let { start(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            is CardOp.Query -> {
                if (card.kind == CardKind.HABIT) doneHabits = doneHabits + card.key
                query = op.query
                val u = understand(op.query)
                val top = u.top
                if (u.confident && top != null) run(top, 0) else screen = Screen.INTENT
            }
            CardOp.MusicPrev -> music?.skipToPrevious()
            CardOp.MusicNext -> music?.skipToNext()
            CardOp.MusicToggle -> if (app.signals.musicPlaying()) music?.pause() else music?.play()
        }
    }

    // Voce: solo il riconoscitore sul telefono, mai il cloud

    private fun voiceDown() {
        if (Build.VERSION.SDK_INT < 31) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            request(Manifest.permission.RECORD_AUDIO); return
        }
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            notice = Notice("Il riconoscimento vocale sul telefono non è disponibile. Scarica la lingua nelle impostazioni della dettatura.")
            return
        }
        val r = recognizer ?: SpeechRecognizer.createOnDeviceSpeechRecognizer(this).also { recognizer = it }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partial: Bundle?) {
                val t = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                voice = voice.copy(partial = t)
            }
            override fun onResults(results: Bundle?) {
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                voice = voice.copy(listening = false, partial = "")
                if (t.isBlank()) { notice = Notice("Non ho capito"); return }
                query = t
                // Azioni verso te stesso, con sicurezza alta: fatte subito
                val u = understand(t)
                val top = u.top
                if (u.confident && top != null && !top.toOthers) run(top, 0)
            }
            override fun onError(error: Int) {
                voice = voice.copy(listening = false, partial = "")
                notice = when (error) {
                    12, 13 -> Notice("Manca la lingua per il riconoscimento sul telefono: scaricala nelle impostazioni della dettatura.")
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Notice("Non ho capito")
                    SpeechRecognizer.ERROR_CLIENT -> null
                    else -> Notice("Microfono non disponibile")
                }
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        if (Build.VERSION.SDK_INT >= 33) {
            // Nomi insoliti di persone, app e dispositivi di casa capiti meglio
            val bias = ArrayList<String>()
            bias += app.people.all().filter { it.starred || app.people.isImportant(it.name) }.map { it.name }
            bias += proposals.apps.map { it.label }
            bias += app.ha.cached().filter { it.controllable }.map { it.name }
            i.putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(bias.distinct().take(100)))
        }
        voice = voice.copy(listening = true, partial = "")
        query = ""
        runCatching { r.startListening(i) }.onFailure { voice = voice.copy(listening = false) }
    }

    private fun voiceUp() {
        if (voice.listening) runCatching { recognizer?.stopListening() }
    }

    // Ops

    override fun openHomeSettings() { start(Intent(Settings.ACTION_HOME_SETTINGS)) }
    override fun openListenerSettings() {
        if (Build.VERSION.SDK_INT >= 30) {
            val ok = start(Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    android.content.ComponentName(this, SordinaListener::class.java).flattenToString()))
            if (ok) return
        }
        start(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }
    override fun openPolicySettings() { start(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
    override fun openUsageSettings() {
        if (!start(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:$packageName")))) {
            start(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }
    override fun request(vararg permissions: String) { permLauncher.launch(arrayOf(*permissions)) }
    override fun exportSettings() { runCatching { exportLauncher.launch("sordina.json") } }
    override fun importSettings() { runCatching { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) } }
    override fun openUrl(url: String) { start(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    override fun changed() {
        prefsVersion++
        moment = app.moments.current()
        Scheduler.refresh(this)
        refreshProposals(force = true)
        refreshExtras()
    }

    private fun start(i: Intent): Boolean = try {
        startActivity(i); true
    } catch (_: ActivityNotFoundException) { false } catch (_: SecurityException) { false }

    private fun wipe() {
        app.silence.disable()
        SordinaListener.readAll(this)
        app.store.wipe()
        app.ha.setToken("")
        prefs.clearAll()
        family = prefs.family; mode = prefs.mode; hidden = prefs.hidden
        changed()
        screen = Screen.ONBOARDING
    }

    /** Scorri in giù da Casa: tendina delle notifiche di sistema. */
    @SuppressLint("WrongConstant")
    private fun expandNotifications() {
        runCatching {
            val sbs = getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sbs)
        }
    }

    // Interfaccia

    @Composable
    private fun SystemBars() {
        val view = LocalView.current
        val dark = S.c.dark
        SideEffect {
            val c = WindowCompat.getInsetsController(window, view)
            c.isAppearanceLightStatusBars = !dark
            c.isAppearanceLightNavigationBars = !dark
        }
    }

    @Composable
    private fun Root() {
        BackHandler(enabled = screen != Screen.HOME && screen != Screen.ONBOARDING) {
            screen = when (screen) {
                Screen.ALL_APPS -> Screen.INTENT
                else -> Screen.HOME
            }
        }
        val held by app.store.held.collectAsState()
        val today by app.store.today.collectAsState()
        val now = rememberNow()
        val groups = remember(held) { app.store.groups() }
        val signalCards by app.signals.cards.collectAsState()
        val music by app.signals.music.collectAsState()

        val actionsP = ProposalActions(
            isPinned = { a -> prefsVersion.let { prefs.pinned.contains(a.key) } },
            togglePin = { a ->
                val p = prefs.pinned
                prefs.pinned = if (a.key in p) p - a.key else (listOf(a.key) + p)
                prefsVersion++
                refreshProposals(force = true)
            },
            exclude = { a ->
                prefs.excluded = prefs.excluded + a.key
                prefs.pinned = prefs.pinned - a.key
                refreshProposals(force = true)
            },
            info = { a -> catalog.openInfo(a) },
            why = { a ->
                when {
                    a.key in prefs.pinned -> "La tieni qui tu."
                    (proposals.usage[a.packageName] ?: 0.0) > 0.3 -> "La apri spesso attorno a quest'ora, nelle ultime quattro settimane."
                    else -> "È tra le app che usi di più."
                }
            },
        )
        LaunchedEffect(screen) {
            if (screen == Screen.INTENT) refreshProposals(force = false)
            if (screen == Screen.HOME) refreshExtras()
        }
        Box(Modifier.fillMaxSize().paper()) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                label = "screen",
            ) { s ->
                when (s) {
                    Screen.HOME -> {
                        val waiting = groups.sumOf { g -> g.sumOf { it.count } }
                        val line = when {
                            moment != Moment.NONE -> moment.label.lowercase()
                            waiting > 0 -> "$waiting in attesa"
                            else -> "tutto calmo"
                        }
                        val ms = now.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                        val cards = remember(signalCards, music, events, habitCards, doneHabits, now) {
                            (signalCards.values + listOfNotNull(music) + events.map { eventCard(it, ms) } +
                                habitCards.filter { it.key !in doneHabits })
                                .filter { it.expiresAt > ms }
                                .sortedWith(compareBy<Card> { it.kind.priority }.thenByDescending { it.time })
                                .take(6)
                        }
                        HomeScreen(
                            state = HomeState(line, waiting, cards, weatherNow, remember(nextEvent, now) { nextEvent?.let { nextLabel(it) } }),
                            onWeather = ::openWeather,
                            onNext = { nextEvent?.let { cardAction(eventCard(it, ms), CardAction("", CardOp.OpenEvent(it.id))) } },
                            onSwipeUp = { screen = Screen.INTENT },
                            onSwipeDown = { expandNotifications() },
                            onLongPress = { screen = Screen.SETTINGS },
                            onLine = { if (waiting > 0) screen = Screen.WAITING },
                            onAction = ::cardAction,
                            onCard = ::cardTap,
                        )
                    }
                    Screen.INTENT -> IntentScreen(
                        catalog = catalog,
                        query = query,
                        onQuery = { query = it },
                        understand = ::understand,
                        proposals = proposals.apps,
                        needsUsagePermission = proposals.needsPermission,
                        actions = actionsP,
                        keyboardOnOpen = remember(prefsVersion) { prefs.keyboardOnOpen },
                        voice = voice,
                        notice = notice,
                        onRun = ::run,
                        onLaunch = ::launch,
                        onAllApps = { screen = Screen.ALL_APPS },
                        onGrantUsage = ::openUsageSettings,
                        onVoiceDown = ::voiceDown,
                        onVoiceUp = ::voiceUp,
                    )
                    Screen.ALL_APPS -> AllAppsScreen(
                        apps = apps,
                        hidden = hidden,
                        onLaunch = ::launch,
                        onInfo = { catalog.openInfo(it) },
                        onUninstall = { catalog.uninstall(it) },
                        onToggleHidden = { a ->
                            hidden = if (a.key in hidden) hidden - a.key else hidden + a.key
                            prefs.hidden = hidden
                        },
                        isBraked = { a -> prefsVersion.let { a.packageName in prefs.braked } },
                        onToggleBrake = { a ->
                            val b = prefs.braked
                            prefs.braked = if (a.packageName in b) b - a.packageName else b + a.packageName
                            changed()
                        },
                    )
                    Screen.SETTINGS -> SettingsScreen(
                        app = app,
                        apps = apps,
                        perms = perms,
                        family = family,
                        mode = mode,
                        version = versionName,
                        onFamily = { family = it; prefs.family = it },
                        onMode = { mode = it; prefs.mode = it },
                        onWipe = ::wipe,
                        ops = this@MainActivity,
                        onBack = { screen = Screen.HOME },
                    )
                    Screen.WAITING -> WaitingScreen(
                        groups = groups,
                        listenerEnabled = perms.listener,
                        onEnableListener = ::openListenerSettings,
                        onOpen = { h -> SordinaListener.open(this@MainActivity, h.key) },
                        onReadGroup = { g -> SordinaListener.readGroup(this@MainActivity, g) },
                        onReadAll = { SordinaListener.readAll(this@MainActivity) },
                        onBack = { screen = Screen.HOME },
                    )
                    Screen.BRAKE -> brakeTarget?.let { a ->
                        BrakeScreen(
                            app = a,
                            opensToday = remember(a.key) { app.habits.opensToday(a.packageName) },
                            seconds = prefs.brakeSeconds,
                            onOpen = { habit ->
                                app.habits.logBrake(a.packageName, habit)
                                catalog.launch(a)
                            },
                            onCancel = { screen = Screen.HOME },
                        )
                    } ?: run { screen = Screen.HOME }
                    Screen.ONBOARDING -> Onboarding(
                        app = app,
                        perms = perms,
                        family = family,
                        mode = mode,
                        onFamily = { family = it; prefs.family = it },
                        onMode = { mode = it; prefs.mode = it },
                        ops = this@MainActivity,
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

    private val versionName: String
        get() = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"

    companion object {
        const val ACTION_WAITING = "app.sordina.WAITING"
    }
}
