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
import app.sordina.ui.home.Usual
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

enum class Screen { HOME, INTENT, ALL_APPS, SETTINGS, BRAKE, ONBOARDING, SORDINA }

class MainActivity : ComponentActivity(), Ops {

    private val app by lazy { SordinaApp.of(this) }
    private val prefs get() = app.prefs
    private val catalog get() = app.catalog
    private lateinit var proposalsEngine: Proposals
    private lateinit var actions: Actions
    private var appsCallback: LauncherApps.Callback? = null

    private var screen by mutableStateOf(Screen.HOME)
    /** Si torna alla ricerca da Tutte le app: niente tastiera, anche se di solito si apre */
    private var backFromApps = false
    private var apps by mutableStateOf<List<AppEntry>>(emptyList())
    private var proposals by mutableStateOf(Proposals.Result(emptyList(), emptyMap(), emptyMap(), emptyMap(), false, -1))
    private var family by mutableStateOf(ThemeFamily.CARTA)
    private var mode by mutableStateOf(ThemeMode.AUTO)
    private var hidden by mutableStateOf<Set<String>>(emptySet())
    private var emptyPreview by mutableStateOf(false)
    private var prefsVersion by mutableIntStateOf(0)
    private var perms by mutableStateOf(PermState())
    private var query by mutableStateOf("")
    private var notice by mutableStateOf<Notice?>(null)
    private var voice by mutableStateOf(VoiceState(Build.VERSION.SDK_INT >= 31, false, ""))
    private var brakeTarget by mutableStateOf<AppEntry?>(null)
    /** Il browser dove si è aperto il sito dell'app frenata: passato il freno si torna lì */
    private var brakeVia: String? = null
    private var events by mutableStateOf<List<Upcoming>>(emptyList())
    /** Il fumetto "di solito" scorso via: non torna fino alla prossima fascia di proposte */
    private var usualHidden by mutableStateOf(-1L)
    private var onbStep by mutableIntStateOf(0)
    private var doneHabits by mutableStateOf<Set<String>>(emptySet())
    private var backdrop by mutableStateOf(Backdrop())
    private var accentPref by mutableIntStateOf(0)
    private var glassPref by mutableStateOf(false)
    private var blackPref by mutableStateOf(false)
    private var agendaEvents by mutableStateOf<List<Upcoming>>(emptyList())
    private var weatherNow by mutableStateOf<String?>(null)
    private var moment by mutableStateOf(Moment.NONE)
    private var scene by mutableStateOf(Scene.DAY)
    /** Cosa impedisce al motore di lavorare (null = tutto bene) */
    private var health by mutableStateOf<Health?>(null)
    private var tomorrowEvents by mutableStateOf<List<Upcoming>>(emptyList())
    private var tomorrowWeather by mutableStateOf<String?>(null)
    private var recap by mutableStateOf<String?>(null)
    private var carPeople by mutableStateOf<List<CarPerson>>(emptyList())
    /** Le foto delle persone in auto: si leggono dalla rubrica una volta sola */
    private val carPhotos = java.util.concurrent.ConcurrentHashMap<Long, java.util.Optional<androidx.compose.ui.graphics.ImageBitmap>>()
    /** Gli adesivi appena vinti, da far vedere in cima a Casa */
    private var celebrate by mutableStateOf<List<app.sordina.data.Medal>>(emptyList())
    private var medalsAt = 0L
    private var openStickers = false
    private var recognizer: SpeechRecognizer? = null

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshPerms()
        changed()
    }
    private val wallpaperLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { Backdrops.save(this@MainActivity, uri) }
            if (ok) { prefs.wallpaperMode = 2; backdropChanged() }
        }
    }
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            contentResolver.openOutputStream(uri)?.use { it.write(prefs.export().toByteArray()) }
            notice = Notice(tr(R.string.cfg_exported))
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
            notice = Notice(if (ok) tr(R.string.cfg_imported) else tr(R.string.file_invalid))
        }
    }

    override fun attachBaseContext(newBase: android.content.Context) { super.attachBaseContext(Lang.wrap(newBase)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Il telefono resta in verticale; sui tablet (lato corto ≥ 600 dp) si gira come il resto del sistema
        requestedOrientation = if (resources.configuration.smallestScreenWidthDp >= 600)
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        proposalsEngine = Proposals(this, app.brain, app.intents)
        actions = Actions(this, catalog, app.people, app.ha, prefs)
        family = prefs.family
        mode = prefs.mode
        hidden = prefs.hidden
        if (!prefs.onboarded) { screen = Screen.ONBOARDING; onbStep = prefs.onboardStep }
        handleIntent(intent)
        backdropChanged()

        appsCallback = catalog.registerChanges {
            // Dieci app aggiornate di fila: un ricaricamento solo, dopo l'ultima
            appsReload?.cancel()
            appsReload = lifecycleScope.launch { kotlinx.coroutines.delay(APPS_SETTLE); reloadApps(forceProposals = true) }
        }
        reloadApps(forceProposals = true)

        setContent {
            val accentColor = when (accentPref) {
                // Auto: segue lo sfondo (se ha un colore), se no quello del tema
                0, 1 -> autoAccent(backdrop.accent)?.let { androidx.compose.ui.graphics.Color(it) }
                else -> androidx.compose.ui.graphics.Color(accentPref)
            }
            SordinaTheme(family, mode, accentColor, blackPref) {
                val view = androidx.compose.ui.platform.LocalView.current
                val feel = remember(prefsVersion) { Feel(prefs.animations, prefs.haptics, view) }
                androidx.compose.runtime.CompositionLocalProvider(LocalFeel provides feel, LocalGlass provides (backdrop.active && glassPref)) {
                    SystemBars()
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                        Root()
                        Bubble(bubble) { bubble = null }
                        HomeReplyTimer(reply) { reply = null }
                        AccessConsent()
                    }
                }
            }
        }
    }

    /** Il gesto in corso è quello che ha mandato via la sordina: non arriva a nessuno */
    private var swallow = false

    /** Un tocco mentre la sordina risponde per la casa: la manda via e si ferma lì. Il tocco dopo arriva a quello che volevi toccare. */
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
            // Anche il fumetto con una risposta da leggere (o in attesa) conta come la sordina
            val up = reply != null || bubble?.let { it.long || it.wait } == true
            swallow = up
            if (up) { reply = null; bubble = null }
        }
        // Il primo tocco la manda via e basta, fino al rilascio: dietro non si preme niente per sbaglio. Dal secondo, tutto normale
        if (swallow) {
            if (ev.actionMasked == android.view.MotionEvent.ACTION_UP || ev.actionMasked == android.view.MotionEvent.ACTION_CANCEL) swallow = false
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onDestroy() {
        appsCallback?.let { catalog.unregister(it) }
        recognizer?.destroy()
        calendarObserver?.let { runCatching { contentResolver.unregisterContentObserver(it) } }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        // Solo nella versione di prova: riapre il primo avvio senza cancellare nulla
        if (intent?.getBooleanExtra("onboarding", false) == true && (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            onbStep = intent.getIntExtra("step", 0)
            screen = Screen.ONBOARDING; return
        }
        if (screen == Screen.ONBOARDING) return
        // Solo nella versione di prova: "bubble" = un fumetto finto in Casa (otp, ask, call, ride, timer, alarm, music, event, eventnow, plan, health); "off" lo toglie
        if (intent?.hasExtra("bubble") == true && (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            val k = intent.getStringExtra("bubble").orEmpty()
            intent.removeExtra("bubble")
            app.signals.cards.value.keys.filter { it.startsWith("demo:") }.forEach { app.signals.remove(it) }
            demoCard(k)?.let { app.signals.put(it) }
            screen = Screen.HOME
            return
        }
        // Solo nella versione di prova: "sordinaEmpty" = la pagina Sordina come se non ci fosse niente
        if (intent?.hasExtra("sordinaEmpty") == true && (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            emptyPreview = intent.getBooleanExtra("sordinaEmpty", false)
        }
        // Solo nella versione di prova: "homeReply" = la sordina che risponde per la casa, con questa risposta ("!" davanti: andata male)
        if (intent?.hasExtra("homeReply") == true && (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            val text = intent.getStringExtra("homeReply").orEmpty()
            intent.removeExtra("homeReply")
            screen = Screen.HOME; reply = HomeReply(null)
            lifecycleScope.launch { kotlinx.coroutines.delay(1800); reply = HomeReply(text.removePrefix("!"), !text.startsWith("!")) }
            return
        }
        // Solo nella versione di prova: "scene" = CAR, EVENING, NIGHT o DAY per vedere quella Casa ("" per tornare normale)
        if (intent?.hasExtra("scene") == true && (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            app.moments.preview = intent.getStringExtra("scene")?.let { s -> Scene.entries.firstOrNull { it.name == s } }
            // "carApps" = pacchetti separati da virgola ("" = quelle proposte)
            intent.getStringExtra("carApps")?.let { v -> prefs.carApps = v.split(',').filter { it.isNotBlank() }.ifEmpty { null }; prefsVersion++ }
            app.moments.previewQuiet = intent.getBooleanExtra("quiet", false); moment = app.moments.current()
            scene = app.moments.scene(); screen = Screen.HOME
            lifecycleScope.launch(Dispatchers.IO) { sceneExtras(scene) }
            return
        }
        // Un'app frenata è tornata davanti senza passare dal freno
        intent?.getStringExtra(BrakeGuard.EXTRA)?.let { pkg ->
            intent.removeExtra(BrakeGuard.EXTRA)
            brakeVia = intent.getStringExtra(BrakeGuard.VIA); intent.removeExtra(BrakeGuard.VIA)
            (apps.firstOrNull { it.packageName == pkg } ?: catalog.load().firstOrNull { it.packageName == pkg })?.let { brakeTarget = it; screen = Screen.BRAKE; return }
        }
        when {
            intent?.action == ACTION_WAITING -> { screen = Screen.SORDINA }
            intent?.hasCategory(Intent.CATEGORY_HOME) == true -> { screen = Screen.HOME; query = "" }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPerms()
        SordinaListener.rebind(this)
        BrakeGuard.atHome(this, prefs)
        // Le app frenate possono essere cambiate: l'accessibilità ascolta solo quelle
        SordinaAccess.refresh()
        SordinaListener.sync()
        moment = app.moments.current()
        scene = app.moments.scene()
        alarmFeedback()
        // Tornando da un'app la situazione è cambiata (l'app di prima, il luogo): si ricalcola, mai mentre guardi.
        // Un'occhiata a un'app e subito di nuovo Casa: le proposte di pochi secondi fa vanno ancora bene
        refreshProposals(force = System.currentTimeMillis() - proposalsAt > PROPOSALS_GAP)
        refreshExtras()
    }

    /** L'ultima sveglia di cui si è già detto "fatto": la conferma si dice una volta sola */
    private var alarmSaidAt = 0L

    /** Hai appena impostato una sveglia nell'Orologio: tornando a Casa la sordina conferma l'ora che ha capito. */
    private fun alarmFeedback() {
        val at = prefs.alarmHint
        val setAt = prefs.alarmHintAt
        val now = System.currentTimeMillis()
        if (at <= now || setAt == alarmSaidAt || now - setAt > 3 * 60_000L) return
        alarmSaidAt = setAt
        val time = java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))
        answer(tr(R.string.n_alarm_in, time, shortDuration(((at - now) / 60_000L).toInt())))
    }

    override fun onStop() {
        super.onStop()
        // Fuori da Casa: il freno guarda quale app va davanti (solo adesso, non mentre sei in Casa)
        BrakeGuard.leftHome(this, prefs)
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
            // Le icone si preparano in sottofondo, così la lista e la ricerca non devono caricarle mentre scorri
            if (prefs.appIcons) { val list = apps; launch(Dispatchers.Default) { catalog.warmIcons(list) } }
        }
    }

    /** Le proposte cambiano solo al cambio di fascia (mezz'ora) o di momento, mai mentre le guardi. */
    private fun refreshProposals(force: Boolean) {
        val slot = Proposals.slotOf(System.currentTimeMillis()) * 10 + moment.ordinal
        val permissionChanged = proposals.needsPermission == proposalsEngine.hasPermission()
        if (!force && slot == proposals.slot && !permissionChanged) return
        val current = apps
        if (current.isEmpty()) return
        proposalsAt = System.currentTimeMillis()
        lifecycleScope.launch {
            val r = withContext(Dispatchers.Default) { proposalsEngine.compute(current, prefs) }
            proposals = r.copy(slot = slot)
        }
    }

    /** Casa: eventi vicini e schede "di solito". */
    private var extrasAt = 0L
    /** L'ultima lettura del calendario: si rilegge quando cambia (osservatore) o al massimo ogni [CALENDAR_GAP] */
    @Volatile private var calendarAt = 0L
    private var calendarObserver: android.database.ContentObserver? = null
    private var proposalsAt = 0L
    private var appsReload: kotlinx.coroutines.Job? = null

    private fun refreshExtras() {
        // Tornando a Casa arrivano insieme onResume e il cambio di schermata: basta un giro solo
        val t = System.currentTimeMillis()
        if (t - extrasAt < 2_000) return
        extrasAt = t
        val current = apps
        watchCalendar()
        lifecycleScope.launch(Dispatchers.IO) {
            // La copia di ogni giorno per il backup di Google (una scrittura al giorno al massimo)
            Backup.daily(this@MainActivity, prefs.onboarded)
            // Il calendario cambia di rado: si rilegge se Android dice che è cambiato, o ogni qualche minuto (gli eventi passano)
            if (t - calendarAt > CALENDAR_GAP) {
                calendarAt = t
                // I calendari scelti per Casa, del telefono e di casa, tutti insieme: niente unioni, se un impegno è in due calendari si vede due volte
                val home = app.ha.calendarEvents()
                events = (app.calendars.upcoming(this@MainActivity) + home.filter { it.end > t && it.begin - t <= 2 * 3600_000L }).sortedBy { it.begin }
                val all = (app.calendars.agenda(this@MainActivity) + home).sortedBy { it.begin }
                // Sempre i prossimi tre, oggi o nei giorni dopo: mai di più
                agendaEvents = all.take(3)
            }
            weatherNow = app.weather.now()
            // Le imprese: al massimo ogni dieci minuti, e quelle nuove saltano fuori in Casa
            if (t - medalsAt > 10 * 60_000L) {
                medalsAt = t
                runCatching { app.habits.newMedals(fresh = true) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { celebrate = it }
            }
            sceneExtras(app.moments.scene())
        }
    }

    /** Quello che serve solo alle altre Case: domani per sera e notte, la giornata per la sera, le persone in auto. */
    private fun sceneExtras(s: Scene) {
        if (s == Scene.EVENING || s == Scene.NIGHT) {
            // Domani: anche dai calendari di casa (sono già in memoria per i prossimi 7 giorni)
            val z = java.time.ZoneId.systemDefault()
            val from = java.time.LocalDate.now().plusDays(1).atStartOfDay(z).toInstant().toEpochMilli()
            val to = java.time.LocalDate.now().plusDays(2).atStartOfDay(z).toInstant().toEpochMilli()
            tomorrowEvents = (app.calendars.tomorrow(this) + app.ha.calendarEvents().filter { it.begin in from until to }).sortedBy { it.begin }.take(3)
            tomorrowWeather = if (prefs.homeWeather) app.weather.tomorrow() else null
        }
        recap = if (s == Scene.EVENING) runCatching {
            val d = app.habits.today()
            val min = d.minutes.filterKeys { it != packageName }.values.sum()
            if (min <= 0) null else {
                val t = if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"
                d.unlocks?.let { tr(R.string.scene_recap, t, it) } ?: tr(R.string.scene_recap_time, t)
            }
        }.getOrNull() else null
        if (s == Scene.CAR) carPeople = runCatching {
            val chosen = carContacts()
            val px = (64 * resources.displayMetrics.density).toInt()
            // Due con lo stesso nome: "Francesco R." e "Francesco B."
            val first = chosen.groupingBy { it.name.substringBefore(' ') }.eachCount()
            chosen.map { k ->
                val f = k.name.substringBefore(' ')
                val rest = k.name.substringAfter(' ', "").trim()
                val photo = carPhotos.getOrPut(k.id) { java.util.Optional.ofNullable(app.people.photo(k.id, px)?.asImageBitmap()) }.orElse(null)
                CarPerson(if ((first[f] ?: 0) > 1 && rest.isNotEmpty()) "$f ${rest.first()}." else f, k.name, photo)
            }
        }.getOrDefault(emptyList())
    }

    /** Le persone in auto: solo quelle che hai scelto tu (fino a 4). */
    override fun carContacts(): List<Contact> = runCatching {
        val all = app.people.all()
        // Solo le persone che hai scelto tu: se non ne hai scelte, in auto non compare nessuno
        val list = prefs.carPeople?.mapNotNull { id -> all.firstOrNull { it.id == id } }.orEmpty()
        list.filter { it.number.isNotBlank() }.distinctBy { it.id }.take(4)
    }.getOrDefault(emptyList())

    /** Le scorciatoie in auto: le tue (fino a tre) o, se non ne hai scelte, navigazione, musica e casa che usi di più. */
    /** In auto, di base: Maps, Spotify e Home Assistant, o quelle che fanno lo stesso lavoro sul telefono. */
    override fun carApps(): List<String> = prefs.carApps ?: listOfNotNull(
        prefs.carNavApp ?: CarApps.preferredNav(apps) ?: favourite(CarApps.navigation(this, apps)),
        prefs.carMusicApp ?: CarApps.preferredMusic(apps) ?: favourite(CarApps.music(this, apps)),
        CarApps.preferredHome(apps),
    ).distinct().take(3)

    /** Fra le app candidate, quella che usi di più (per i pulsanti in auto quando non ne hai scelta una). */
    private fun favourite(list: List<AppEntry>): String? {
        if (list.isEmpty()) return null
        val use = runCatching { app.brain.usage() }.getOrDefault(emptyMap())
        return list.maxByOrNull { use[it.packageName] ?: 0.0 }?.packageName
    }

    private fun hm(min: Int): String = java.time.LocalTime.of(min / 60 % 24, min % 60)
        .format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))

    /**
     * La prossima sveglia di domani, se suona entro un giorno (quali valgono: [app.sordina.data.Moments.clockAlarm]).
     */
    private fun nextAlarm(): String? = nextAlarmAt()?.let {
        java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))
    }

    private fun nextAlarmAt(): Long? {
        val t = app.moments.clockAlarm() ?: return null
        if (t - System.currentTimeMillis() > 24 * 3_600_000L) return null
        val at = java.time.Instant.ofEpochMilli(t).atZone(java.time.ZoneId.systemDefault())
        // Sta sotto "Domani": una sveglia di stasera non è di domani (dopo mezzanotte invece sì)
        val now = java.time.LocalDateTime.now()
        if (at.toLocalDate() == now.toLocalDate() && now.hour >= 12) return null
        return t
    }

    private fun sceneLine(s: Scene): String? = when (s) {
        Scene.EVENING -> null
        Scene.NIGHT -> app.moments.phrase()
        Scene.CAR -> tr(if (moment == Moment.QUIET) R.string.car_line else R.string.car_line_open)
        Scene.DAY -> null
    }

    private fun onScene(key: String) {
        when {
            // Il fumetto "di solito": tocco fa, pressione lunga non la propone più, scorso via torna alla prossima fascia
            key.startsWith("usual:") -> key.removePrefix("usual:").let { id -> proposals.usual.firstOrNull { it.candidate.id == id }?.candidate ?: proposals.items.firstOrNull { it.id == id } }?.let { run(it, -1) }
            // Il fumetto ti mostra una cosa / l'hai scorsa via: il cervello impara anche da questo
            key.startsWith("usual-seen:") -> proposals.brainItem[key.removePrefix("usual-seen:")]?.let { item -> lifecycleScope.launch(Dispatchers.Default) { app.brain.shownUsual(item) } }
            key.startsWith("usual-no:") -> proposals.brainItem[key.removePrefix("usual-no:")]?.let { item -> lifecycleScope.launch(Dispatchers.Default) { app.brain.refusedUsual(item) } }
            key.startsWith("usual-drop:") -> {
                proposals.brainItem[key.removePrefix("usual-drop:")]?.let { prefs.excluded = prefs.excluded + "brain:$it" }
                refreshProposals(force = true)
            }
            key == "usual-hide" -> usualHidden = proposals.slot
            // Benessere digitale: Samsung, poi Google, se no le Impostazioni
            key == "usage" -> listOf(
                Intent().setClassName("com.samsung.android.forest", "com.samsung.android.forest.home.DefaultActivity"),
                Intent().setClassName("com.google.android.apps.wellbeing", "com.google.android.apps.wellbeing.settings.TopLevelSettingsActivity"),
                Intent(android.provider.Settings.ACTION_SETTINGS),
            ).firstOrNull { runCatching { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess }
            key == "alarm" -> start(Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            key.startsWith("nav:") -> key.removePrefix("nav:").let { place ->
                start(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(place))))
                    || start(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(place))))
            }
            key == "mic" -> voiceTap()
            key.startsWith("app:") -> key.removePrefix("app:").let { pkg ->
                // Se è l'app che sta suonando, si apre il lettore; altrimenti l'app
                if (app.signals.controller?.packageName == pkg) openMusic()
                else packageManager.getLaunchIntentForPackage(pkg)?.let { start(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            key == "ha" -> openHaCalendar()
            key == "nav" -> {
                // L'app scelta da te; se non l'hai scelta, Maps o Waze
                val i = listOfNotNull(prefs.carNavApp ?: favourite(CarApps.navigation(this, apps)), "com.google.android.apps.maps", "com.waze").firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }
                if (i == null || !start(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) start(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0")))
            }
            key == "music" -> {
                // Se suona già la tua app (o non ne hai scelta una) si apre il lettore; altrimenti la tua app
                val mine = prefs.carMusicApp ?: favourite(CarApps.music(this, apps))
                val playing = app.signals.controller
                if (playing != null && (prefs.carMusicApp == null || playing.packageName == mine)) openMusic()
                else if (mine != null && packageManager.getLaunchIntentForPackage(mine)?.let { start(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } == true) Unit
                else start(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            key.startsWith("hide:") -> {
                // Pressione lunga su una persona in auto: non la si mostra più lì (resta importante)
                val n = Search.normalize(key.removePrefix("hide:"))
                prefs.carHidden = prefs.carHidden + n
                // Se era tra le tue quattro, esce anche da lì (come "Rimuovi" nelle impostazioni)
                lifecycleScope.launch(Dispatchers.IO) {
                    prefs.carPeople?.let { ids -> val all = app.people.all(); prefs.carPeople = ids.filter { id -> all.firstOrNull { it.id == id }?.normalized != n } }
                    sceneExtras(Scene.CAR)
                }
                say(tr(R.string.car_hidden))
            }
            key.startsWith("call:") -> {
                val q = "chiama " + key.removePrefix("call:")
                cardAction(Card("scene:call", CardKind.CALL, "", ""), CardAction("", CardOp.Query(q)))
            }
            else -> onAgenda(key)
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
            location = g(Manifest.permission.ACCESS_COARSE_LOCATION),
            battery = SordinaListener.batteryFree(this),
            access = SordinaAccess.isEnabled(this),
        )
        if (perms.contacts && !was.contacts) app.people.invalidate()
        checkHealth()
    }

    private fun checkHealth() {
        if (!prefs.onboarded) { health = null; return }
        // La batteria la si chiede una volta: se dici di no, non si insiste in Casa (resta nelle Impostazioni)
        health = SordinaListener.health(this)?.takeIf { it != Health.BATTERY || !prefs.batteryAsked }
    }

    private fun isDefaultHome(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName == packageName
    }

    // Azioni

    private fun launch(a: AppEntry) {
        // Senza accesso all'utilizzo il cervello non vede le app aperte: le ricorda da qui
        if (!perms.usage) app.brain.chosen("", Candidate("app:${a.key}", a.label, null, Kind.APP, 0, false, Act.OpenApp(a), a), false)
        if (a.packageName in prefs.braked && brakeNow()) {
            brakeTarget = a
            brakeVia = null
            screen = Screen.BRAKE
        } else catalog.launch(a)
    }

    /** Il freno vale adesso? Sempre, o solo nella fascia scelta (es. 22–8). */
    private fun brakeNow(): Boolean = BrakeGuard.brakeNow(prefs)

    /** L'attesa: quella scelta, più 5 secondi per ogni volta che l'hai aperta comunque nell'ultima ora (al massimo un minuto). */
    private fun brakeWait(pkg: String): Int {
        val base = prefs.brakeSeconds
        if (!prefs.brakeGrow) return base
        return (base + 5 * app.habits.opensLastHour(pkg)).coerceAtMost(60)
    }

    private fun understand(q: String): Understanding = app.intents.understand(q, apps.filter { it.key !in hidden }, proposals.usage)

    private fun run(c: Candidate, index: Int) {
        // index < 0: toccata tra le proposte, non dalla ricerca (non conta per "giusta al primo colpo")
        if (index >= 0) app.intents.chosen(query, c, index)
        // Le app aperte le ricorda launch() (o la cronologia d'uso): qui solo le lettere scritte e le altre azioni
        app.brain.chosen(query, c, perms.usage || c.act is Act.OpenApp)
        when (val act = c.act) {
            is Act.OpenApp -> launch(act.app)
            Act.SordinaSettings -> { query = ""; screen = Screen.SETTINGS }
            else -> lifecycleScope.launch {
                // Si apre Assist: la tastiera della ricerca si chiude prima, così sopra non si vede mai una tastiera
                if (act is Act.Assist) runCatching { androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).hide(androidx.core.view.WindowInsetsCompat.Type.ime()) }
                // Col token la casa risponde qui: si torna subito a Casa e la sordina dice "Chiedo a casa…", poi la risposta
                val here = act is Act.Assist && app.ha.configured
                // Con le animazioni spente niente sordina che sale: basta il fumetto
                val asked = if (here && prefs.animations) HomeReply(null) else null
                if (here) { screen = Screen.HOME; query = ""; if (asked != null) reply = asked else bubble = Said(tr(R.string.ha_asking), wait = true) }
                val out = actions.run(act)
                if (here) {
                    val answer = out.message ?: tr(R.string.ha_no_answer)
                    // Se nel frattempo l'hai mandata via con un tocco, la risposta arriva nel fumetto piccolo
                    if (asked != null && reply?.id == asked.id) reply = HomeReply(answer, out.ok) else bubble = Said(answer, long = true)
                    if (out.ok) Feel(true, prefs.haptics, window.decorView).confirm()
                }
                if (act is Act.Event && out.ok) calendarAt = 0L
                val stays = act is Act.Timer || act is Act.Alarm || act is Act.Event || act is Act.Torch ||
                    act is Act.Media || act is Act.Calc
                if (stays && out.ok) query = ""
                val undo = out.undo
                if (out.message != null && !here) notice = Notice(out.message, undo?.let { u -> { lifecycleScope.launch { u(); notice = Notice(tr(R.string.undone)) } } })
                if (out.message != null && screen == Screen.HOME && !here) answer(out.message, out.ok)
                // Hai appena scritto o chiamato qualcuno: la sua risposta passa per i prossimi 15 minuti
                val talkTo = (act as? Act.Message)?.contact ?: (act as? Act.Call)?.contact
                if (out.ok && talkTo != null && prefs.talkPass) {
                    val k = "p:" + Search.normalize(talkTo.name)
                    prefs.passUntil = prefs.passUntil + (k to System.currentTimeMillis() + 15 * 60_000L)
                }
                // Si è aperta un'altra app: sotto torna Casa, anche se quella app sta sopra a metà schermo (Assist) e Sordina non si ferma
                val leaves = act is Act.Assist || act is Act.Message || act is Act.Call || act is Act.Navigate || act is Act.Music ||
                    act is Act.Web || act is Act.Setting
                if (leaves && !here && out.ok && screen != Screen.SETTINGS) { screen = Screen.HOME; query = "" }
            }
        }
    }

    // Schede di Casa

    /** "Adesso", "Tra 12 min", "Alle 16:00" */
    private fun startLabel(begin: Long, now: Long): String {
        val min = ((begin - now) / 60_000).toInt()
        return when {
            min <= 0 -> tr(R.string.now_cap)
            min < 60 -> tr(R.string.in_min, min)
            else -> tr(R.string.at_time, java.time.Instant.ofEpochMilli(begin).atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)))
        }
    }

    private fun eventCard(e: Upcoming, now: Long): Card {
        val label = startLabel(e.begin, now)
        val actions = buildList {
            e.link?.let { add(CardAction(tr(R.string.join), CardOp.OpenUrl(it))) }
            e.location?.let { add(CardAction(tr(R.string.navigate), CardOp.Navigate(it))) }
            if (isEmpty()) add(CardAction(tr(R.string.open), CardOp.OpenEvent(e.id)))
        }.let { a -> if (scene == Scene.CAR) a.sortedByDescending { it.op is CardOp.Navigate } else a }
        return Card("event:${e.id}:${e.begin}", CardKind.EVENT, label, e.title, e.location, actions, e.begin, e.end,
            bar = if (e.begin <= now) Bar(e.begin, e.end) else null)
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
            today -> if (e.begin <= System.currentTimeMillis()) "" else null
            today.plusDays(1) -> tr(R.string.tomorrow_cap)
            else -> t.format(java.time.format.DateTimeFormatter.ofPattern("EEE", Locale.getDefault())).replace(".", "").replaceFirstChar { it.uppercase() }
        }
        val hour = t.format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))
        return if (day == "") tr(R.string.now_dot, e.title) else if (day == null) tr(R.string.at_time, hour) + " · " + e.title else tr(R.string.day_hour_title, day, hour, e.title)
    }

    /** Il programma di Casa: meteo e momento adesso, poi eventi e ritorno delle notifiche in ordine di ora. */
    /** In auto: il prossimo impegno (o quello in corso). Con un indirizzo, un tocco ci porta. */
    private fun nextEvent(): AgendaRow? {
        val ms = System.currentTimeMillis()
        val e = agendaEvents.filter { it.end > ms && !it.allDay }.minByOrNull { it.begin } ?: return null
        // Gli impegni del calendario di casa hanno tutti la chiave "ha": si riconoscono anche dal titolo
        val k = if (e.link == "ha") "ha" else "event:${e.id}"
        val row = agendaRows(0).firstOrNull { it.key == k && it.text == e.title } ?: return null
        return row.copy(key = e.location?.takeIf { it.isNotBlank() }?.let { "nav:$it" } ?: row.key)
    }

    private fun agendaRows(waiting: Int): List<AgendaRow> {
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now()
        val hm = java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)
        val out = ArrayList<AgendaRow>()
        // Il motore è fermo o rischia di esserlo: lo si dice per primo, un tocco porta dove si sistema
        val timed = ArrayList<Pair<Long, AgendaRow>>()
        for (e in agendaEvents) {
            val key = if (e.link == "ha") "ha" else "event:${e.id}"
            val t = java.time.Instant.ofEpochMilli(e.begin).atZone(zone)
            val d = t.toLocalDate()
            val row = when {
                e.begin <= System.currentTimeMillis() -> AgendaRow(tr(R.string.now_l), e.title, key)
                d == today -> AgendaRow(t.format(hm), e.title, key)
                // Gli altri giorni: l'ora resta a sinistra come oggi, il giorno va sopra come titoletto
                d == today.plusDays(1) -> AgendaRow(t.format(hm), e.title, key, day = tr(R.string.tomorrow_cap))
                else -> AgendaRow(t.format(hm), e.title, key,
                    day = t.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d", Locale.getDefault())).replaceFirstChar { it.uppercase() })
            }
            timed += e.begin to row
        }
        // Il prossimo appuntamento in accento, gli altri neutri
        return out + timed.sortedBy { it.first }.mapIndexed { i, p -> if (i == 0) p.second.copy(accent = true) else p.second }
    }

    /** Un impegno di casa: il calendario di Home Assistant, nell'app se c'è, altrimenti nel browser */
    private fun openHaCalendar() {
        val inApp = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("homeassistant://navigate/calendar"))
            .setPackage(HomeAssistant.COMPANION_PKG).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { startActivity(inApp) }.isSuccess) return
        prefs.haUrl.takeIf { it.isNotBlank() }?.let { start(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(it.trimEnd('/') + "/calendar"))) }
    }

    private fun onAgenda(key: String) {
        when {
            key == "health" -> when (health) {
                Health.NO_ACCESS -> openListenerSettings()
                Health.STOPPED -> { SordinaListener.rebind(this); openListenerSettings() }
                Health.BATTERY -> openBatterySettings()
                null -> Unit
            }
            // Toccando "In sordina fino alle…" la si toglie
            key == "moment" && app.moments.focusUntil() != null -> lifecycleScope.launch {
                val out = actions.run(Act.Quiet(null))
                changed()
                out.message?.let { answer(it, out.ok) }
            }
            key == "weather" -> openWeather()
            key == "sordina" -> screen = Screen.SORDINA
            key == "ha" -> openHaCalendar()
            key.startsWith("event:") -> key.removePrefix("event:").toLongOrNull()?.let {
                start(Intent(Intent.ACTION_VIEW, android.content.ContentUris.withAppendedId(android.provider.CalendarContract.Events.CONTENT_URI, it)))
            }
        }
    }

    private fun openWeather() {
        val pkgs = listOf("com.sec.android.daemonapp", "com.google.android.apps.weather", "com.accuweather.android")
        val i = pkgs.firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }
        if (i != null) start(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        else openUrl("https://www.google.com/search?q=meteo")
    }

    private fun cardTap(card: Card) {
        // Le schede imparano da sé (CardSense); il cervello conta solo le sue proposte, così il suo numero resta vero
        app.cardSense.used(card)
        when {
            card.kind == CardKind.HEALTH -> onAgenda("health")
            // Un piano letto nei messaggi: il tocco fa la prima cosa (Naviga vicino all'ora, In calendario prima)
            card.key.startsWith("plan:") -> card.actions.firstOrNull()?.let { cardAction(card, it) }
            card.kind == CardKind.MUSIC -> openMusic()
            card.kind == CardKind.EVENT -> card.key.split(':').getOrNull(1)?.toLongOrNull()?.let { cardAction(card, CardAction("", CardOp.OpenEvent(it))) }
            card.kind == CardKind.HABIT -> card.actions.firstOrNull()?.let { cardAction(card, it) }
            card.kind == CardKind.RAIN -> openWeather()
            card.kind == CardKind.BIRTHDAY -> card.actions.firstOrNull()?.let { cardAction(card, it) }
            card.kind == CardKind.PAYMENT -> card.actions.firstOrNull()?.let { cardAction(card, it) }
            // Riunione da una notifica: si apre quella (la chiave della scheda è la sua)
            else -> SordinaListener.open(this, card.sources.firstOrNull() ?: card.key)
        }
        // Una cosa da vedere una volta (pacco, biglietto, chiamata persa…): aperta, ha fatto il suo lavoro
        if (card.oneShot) dismissCard(card)
    }

    /** Una scheda tolta: per un giorno non torna; un piano dei messaggi non torna più. */
    private fun dismissCard(card: Card) {
        app.signals.dismiss(card)
        if (card.key.startsWith("plan:")) app.plans.forget(card.key)
    }

    private fun cardAction(card: Card, a: CardAction) {
        val music = app.signals.controller?.transportControls
        // Le schede imparano: "nascondi" e "non ora" le scartano, i pulsanti veri le usano ("Ok" e "Più tardi" non dicono niente)
        when {
            a.op == CardOp.Hide -> app.cardSense.dropped(card)
            a.op == CardOp.Dismiss -> if (card.kind == CardKind.HABIT || card.kind == CardKind.BIRTHDAY) app.cardSense.dropped(card)
            a.op is CardOp.Later -> {}
            a.label.isNotEmpty() -> app.cardSense.used(card)
        }
        when (val op = a.op) {
            is CardOp.Copy -> {
                val cm = getSystemService(android.content.ClipboardManager::class.java)
                val clip = android.content.ClipData.newPlainText("codice", op.text)
                clip.description.extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
                cm.setPrimaryClip(clip)
                if (Build.VERSION.SDK_INT < 33) say(tr(R.string.copied))
                dismissCard(card)
            }
            is CardOp.OpenNotif -> { SordinaListener.open(this, op.key); if (card.oneShot) dismissCard(card) }
            CardOp.Fix -> onAgenda("health")
            CardOp.Hide -> {
                // La musica non si nasconde: sparisce da sola quando smette
                if (card.kind != CardKind.MUSIC) {
                    if (prefs.haptics) window.decorView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    if (card.kind == CardKind.HABIT || card.kind == CardKind.BIRTHDAY) doneHabits = doneHabits + card.key
                    dismissCard(card)
                    say(tr(R.string.card_hidden))
                }
            }
            is CardOp.Dial -> start(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(op.number))))
            is CardOp.Navigate -> start(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(op.place))))
                || start(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(op.place))))
            is CardOp.OpenUrl -> openUrl(op.url)
            is CardOp.Join -> SordinaListener.open(this, op.key)
            is CardOp.OpenEvent -> start(Intent(Intent.ACTION_VIEW, android.content.ContentUris.withAppendedId(android.provider.CalendarContract.Events.CONTENT_URI, op.id)))
            is CardOp.AddEvent -> {
                if (!app.calendars.hasPermission()) { request(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR); return }
                lifecycleScope.launch {
                    val id = withContext(Dispatchers.IO) { app.calendars.default()?.let { app.calendars.insert(it, op.title, op.whenAt) } }
                    say(if (id != null) tr(R.string.added) else tr(R.string.failed))
                    if (id != null) dismissCard(card)
                }
            }
            is CardOp.OpenPackage -> {
                doneHabits = doneHabits + card.key
                dismissCard(card)
                apps.firstOrNull { it.packageName == op.pkg && !it.isWork }?.let(::launch)
                    ?: packageManager.getLaunchIntentForPackage(op.pkg)?.let { start(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            is CardOp.Query -> {
                if (card.kind == CardKind.HABIT || card.kind == CardKind.BIRTHDAY) doneHabits = doneHabits + card.key
                // Richiamato o scritto: la chiamata persa e il compleanno sono fatti
                if (card.kind == CardKind.CALL || card.kind == CardKind.BIRTHDAY) dismissCard(card)
                query = op.query
                val u = understand(op.query)
                val top = u.top
                if (u.confident && top != null) run(top, 0) else screen = Screen.INTENT
            }
            CardOp.MusicPrev -> music?.skipToPrevious()
            CardOp.MusicNext -> music?.skipToNext()
            CardOp.MusicToggle -> if (app.signals.musicPlaying()) music?.pause() else music?.play()
            is CardOp.Fire -> { if (SordinaListener.fire(this, op.key, op.index)) dismissCard(card) else SordinaListener.open(this, op.key) }
            is CardOp.Done -> { SordinaListener.done(op.key); dismissCard(card) }
            is CardOp.Later -> { SordinaListener.later(op.key); app.signals.remove(card.key) }
            CardOp.Weather -> openWeather()
            CardOp.Dismiss -> {
                if (card.kind == CardKind.HABIT || card.kind == CardKind.BIRTHDAY) doneHabits = doneHabits + card.key
                // "Ok" toglie la scheda e chiude anche la notifica (o le notifiche) da cui è nata
                if (card.sources.isNotEmpty()) SordinaListener.readKeys(this, card.sources)
                dismissCard(card)
            }
        }
    }

    // Voce: solo il riconoscitore sul telefono, mai il cloud

    /** In auto: un tocco apre l'ascolto, si chiude da solo quando smetti di parlare (o con un altro tocco). */
    private fun voiceTap() {
        if (voice.listening) runCatching { recognizer?.stopListening() } else voiceDown(handsFree = true)
    }

    /** A mani libere: se dopo qualche secondo non hai detto nulla, si smette di ascoltare (in auto non serve un secondo tocco). */
    private val voiceHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var speechHeard = false
    private val noSpeech = Runnable {
        if (voice.listening && !speechHeard) {
            runCatching { recognizer?.cancel() }
            voice = voice.copy(listening = false, partial = "", level = 0f)
            if (screen == Screen.HOME) answer(tr(R.string.not_understood), ok = false)
        }
    }

    private fun voiceDown(handsFree: Boolean = false) {
        if (Build.VERSION.SDK_INT < 31) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            request(Manifest.permission.RECORD_AUDIO); return
        }
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            notice = Notice(tr(R.string.voice_offline))
            return
        }
        val r = recognizer ?: SpeechRecognizer.createOnDeviceSpeechRecognizer(this).also { recognizer = it }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() { speechHeard = true; voiceHandler.removeCallbacks(noSpeech) }
            override fun onRmsChanged(rmsdB: Float) {
                // Il cerchio del microfono respira con la voce: solo mentre ascolta, a passi visibili (niente ricomposizioni inutili)
                val l = (((rmsdB + 2f) / 12f).coerceIn(0f, 1f) * 8).toInt() / 8f
                if (voice.listening && l != voice.level) voice = voice.copy(level = l)
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partial: Bundle?) {
                val t = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (t.isNotBlank()) { speechHeard = true; voiceHandler.removeCallbacks(noSpeech) }
                voice = voice.copy(partial = t)
            }
            override fun onResults(results: Bundle?) {
                voiceHandler.removeCallbacks(noSpeech)
                val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty().filter { it.isNotBlank() }
                // Quello che hai detto resta nel fumetto della sordina mentre decide: la ricerca o la risposta prendono il suo posto
                voice = voice.copy(listening = false, partial = heard.firstOrNull().orEmpty(), level = 0f)
                if (heard.isEmpty()) { if (screen == Screen.HOME) answer(tr(R.string.not_understood), ok = false) else notice = Notice(tr(R.string.not_understood)); return }
                // Il riconoscitore propone più trascrizioni ("chiama Elisa" / "chiamala Lisa"): vince quella che Sordina capisce meglio,
                // a parità la prima (la più probabile per lui)
                val (t, u) = heard.take(4).mapIndexed { i, h -> Triple(h, understand(h), i) }
                    .maxWith(compareBy<Triple<String, Understanding, Int>> { it.second.confident }
                        .thenBy { (it.second.candidates.firstOrNull { c -> c.kind != Kind.WEB }?.score ?: 0) - it.third * 4 })
                    .let { it.first to it.second }
                query = t
                // Azioni verso te stesso, con sicurezza alta: fatte subito
                val top = u.top
                if (u.confident && top != null && !top.toOthers) run(top, 0) else screen = Screen.INTENT
                // Dopo che l'azione è partita (la casa che risponde mette già i suoi puntini): niente buco in mezzo
                lifecycleScope.launch { voice = voice.copy(partial = "") }
            }
            override fun onError(error: Int) {
                voiceHandler.removeCallbacks(noSpeech)
                voice = voice.copy(listening = false, partial = "", level = 0f)
                notice = when (error) {
                    12, 13 -> Notice(tr(R.string.voice_no_lang))
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Notice(tr(R.string.not_understood))
                    SpeechRecognizer.ERROR_CLIENT -> null
                    else -> Notice(tr(R.string.mic_unavailable))
                }
                if (screen == Screen.HOME) notice?.let { answer(it.text, ok = false) }
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            // Si ascolta finché tieni premuto: le pause non chiudono l'ascolto
            // A mani libere invece si chiude dopo un attimo di silenzio
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, if (handsFree) 1_500L else 60_000L)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, if (handsFree) 1_200L else 60_000L)
        if (Build.VERSION.SDK_INT >= 33) {
            // Nomi insoliti di persone, app e dispositivi di casa capiti meglio
            val bias = ArrayList<String>()
            bias += app.people.all().filter { it.starred || app.people.isImportant(it.name) }.map { it.name }
            // E le persone con cui parli davvero, anche se non sono tra i preferiti
            bias += (app.brain.likelyPeople("call:", 8) + app.brain.likelyPeople("msg:", 8))
                .mapNotNull { item -> app.brain.contact(item.substringAfter(':').substringBefore(':'))?.name }
            bias += proposals.apps.map { it.label }
            bias += app.ha.cached().filter { it.controllable }.map { it.name }
            i.putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(bias.distinct().take(100)))
        }
        speechHeard = false
        voiceHandler.removeCallbacks(noSpeech)
        if (handsFree) voiceHandler.postDelayed(noSpeech, NO_SPEECH_MILLIS)
        voiceDownAt = System.currentTimeMillis()
        voice = voice.copy(listening = true, partial = "")
        query = ""
        runCatching { r.startListening(i) }.onFailure { voice = voice.copy(listening = false) }
    }

    private var voiceDownAt = 0L

    // I messaggi brevi ("Copiato", "Aggiunto"…): una pillola nel tema, non il toast di Android
    private var bubble by mutableStateOf<Said?>(null)
    /** La sordina che risponde per la casa (frasi mandate ad Assist col token) */
    private var reply by mutableStateOf<HomeReply?>(null)
    private fun say(text: String) { bubble = Said(text) }
    /** Una risposta della sordina a quello che le hai chiesto: in Casa, con le animazioni, sale lei a dirla; se no il fumetto piccolo */
    private fun answer(text: String, ok: Boolean = true) {
        if (screen == Screen.HOME && prefs.animations) { bubble = null; reply = HomeReply(text, ok) } else say(text)
    }

    private fun hint(text: String) {
        if (screen == Screen.HOME || screen == Screen.SORDINA) say(text)
        else notice = Notice(text)
    }

    /** Rilasci: se era solo un tocco, niente ascolto e un suggerimento; altrimenti si chiude e si capisce. */
    private fun voiceUp() {
        if (!voice.listening) return
        if (System.currentTimeMillis() - voiceDownAt < 350) {
            runCatching { recognizer?.cancel() }
            voice = voice.copy(listening = false, partial = "")
            hint(tr(R.string.hold_speak))
            return
        }
        runCatching { recognizer?.stopListening() }
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

    /** Lo sfondo di Casa anche sulla schermata di blocco: disegnato a piena misura e dato ad Android. */
    override fun setLockWallpaper(paper: Int) {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val bmp = Backdrops.full(this@MainActivity, prefs.wallpaperMode, prefs.wallpaperPreset, prefs.solidColor, paper) ?: return@runCatching false
                    android.app.WallpaperManager.getInstance(this@MainActivity).setBitmap(bmp, null, true, android.app.WallpaperManager.FLAG_LOCK) != 0
                }.getOrDefault(false)
            }
            say(if (ok) tr(R.string.lock_done) else tr(R.string.lock_fail))
        }
    }

    @SuppressLint("BatteryLife")
    override fun openBatterySettings() {
        prefs.batteryAsked = true
        val direct = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
        if (!start(direct)) start(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    /** Sordina non funziona (o rischia): una scheda in cima, gentile, con un tasto solo che porta dove si sistema. */
    /** Solo nella versione di prova: un fumetto finto per vedere com'è, senza aspettare che succeda davvero. */
    private fun demoCard(k: String): Card? {
        val t = System.currentTimeMillis()
        val open = CardAction(tr(R.string.open), CardOp.Dismiss)
        return when (k) {
            "otp" -> Card("demo:otp", CardKind.OTP, tr(R.string.code_x, "Intesa"), "482 913", actions = listOf(CardAction(tr(R.string.copy), CardOp.Copy("482913"))),
                expiresAt = t + 8 * 60_000, look = CardLook.CODE, bar = Bar(t - 2 * 60_000, t + 8 * 60_000, drain = true))
            "ask" -> Card("demo:ask", CardKind.ASK, "Home Assistant", "Hai lasciato la finestra aperta. Accendo comunque il riscaldamento?",
                actions = listOf(CardAction("Sì", CardOp.Dismiss), CardAction("No", CardOp.Dismiss)))
            "ask3" -> Card("demo:ask3", CardKind.ASK, "Home Assistant", "La lavatrice ha finito. Cosa faccio?",
                actions = listOf(CardAction("Asciugatrice", CardOp.Dismiss), CardAction("Ricordamelo", CardOp.Dismiss), CardAction("Niente", CardOp.Dismiss)))
            "call" -> Card("demo:call", CardKind.CALL, tr(R.string.missed_call), "Marta",
                actions = listOf(CardAction(tr(R.string.call_back), CardOp.Query("chiama Marta")), CardAction(tr(R.string.write), CardOp.Query("scrivi a Marta"))), avatar = true)
            "ride" -> Card("demo:ride", CardKind.RIDE, "Glovo", "Il rider arriva tra 8 min", "Sushi Zen · 2 articoli", listOf(open), bar = Bar(value = 0.65f))
            "timer" -> Card("demo:timer", CardKind.TIMER, "Orologio", "Timer · 4:32", "Pasta", listOf(open))
            "alarm" -> Card("demo:alarm", CardKind.TIMER, "Orologio", "Sveglia in arrivo", "Lun 07:15", listOf(CardAction("Ignora", CardOp.Fire("demo:alarm", 0))))
            "music" -> Card("demo:music", CardKind.MUSIC, "Spotify", "Tutto bene", "Cosmo", listOf(
                CardAction(tr(R.string.prev), CardOp.MusicPrev, "prev"), CardAction(tr(R.string.pause), CardOp.MusicToggle, "pause"), CardAction(tr(R.string.next_track), CardOp.MusicNext, "next")))
            "event" -> Card("demo:event", CardKind.EVENT, "Teams", "Riunione di progetto", "Microsoft Teams", listOf(CardAction(tr(R.string.join), CardOp.OpenUrl("https://teams.microsoft.com"))),
                time = t + 10 * 60_000, expiresAt = t + 70 * 60_000, startsAt = t + 10 * 60_000)
            "eventnow" -> Card("demo:eventnow", CardKind.EVENT, "Teams", "Riunione di progetto", "Microsoft Teams", listOf(CardAction(tr(R.string.join), CardOp.OpenUrl("https://teams.microsoft.com"))),
                time = t - 20 * 60_000, expiresAt = t + 40 * 60_000, startsAt = t - 20 * 60_000, bar = Bar(t - 20 * 60_000, t + 40 * 60_000))
            "plan" -> Card("demo:plan", CardKind.PLAN, "WhatsApp", "Cena da Luca", "Via Roma 12, Torino", listOf(CardAction(tr(R.string.navigate), CardOp.Navigate("Via Roma 12, Torino"))),
                time = t, expiresAt = t + 3 * 3_600_000, startsAt = t + 14 * 60_000)
            "health" -> healthCard(Health.STOPPED).copy(key = "demo:health")
            else -> null
        }
    }

    private fun healthCard(h: Health): Card = when (h) {
        Health.NO_ACCESS -> Card("health:access", CardKind.HEALTH, tr(R.string.health_card_label), tr(R.string.health_access_title), tr(R.string.health_access_sub),
            listOf(CardAction(tr(R.string.health_access_fix), CardOp.Fix)))
        Health.STOPPED -> Card("health:stopped", CardKind.HEALTH, tr(R.string.health_card_label), tr(R.string.health_stopped_title), tr(R.string.health_stopped_sub),
            listOf(CardAction(tr(R.string.health_stopped_fix), CardOp.Fix)))
        Health.BATTERY -> Card("health:battery", CardKind.HEALTH, tr(R.string.health_card_label), tr(R.string.health_battery_title), tr(R.string.health_battery_sub),
            listOf(CardAction(tr(R.string.health_battery_fix), CardOp.Fix)))
    }
    override fun pickWallpaper() {
        runCatching { wallpaperLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }
    override fun backdropChanged() {
        accentPref = prefs.accent
        glassPref = prefs.glass
        blackPref = prefs.trueBlack
        val m = prefs.wallpaperMode
        // Lo sfondo della finestra (carta, dal tema) coprirebbe quello del telefono: con lo sfondo del telefono diventa trasparente.
        // Le schermate che non lo mostrano hanno comunque la loro carta (Modifier.paper).
        if (m == 1) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawableResource(R.color.paper)
        }
        lifecycleScope.launch { backdrop = withContext(Dispatchers.IO) { Backdrops.load(this@MainActivity, m, prefs.wallpaperPreset, prefs.solidColor) } }
    }
    private val notifIcons = HashMap<String, androidx.compose.ui.graphics.ImageBitmap?>()

    /** L'icona dell'app di una notifica (anche app di sistema senza voce nel cassetto). */
    private fun notifIcon(pkg: String): androidx.compose.ui.graphics.ImageBitmap? = notifIcons.getOrPut(pkg) {
        AppIcons.forPackage(packageManager, pkg, (40 * resources.displayMetrics.density).toInt())
    }

    /** Lascia passare un contatto o un'app: per qualche ora, oppure sempre (importante / livello Subito). */
    private fun letPass(h: Held, hours: Int?) {
        val k = h.groupKey
        if (hours != null) prefs.passUntil = prefs.passUntil + (k to System.currentTimeMillis() + hours * 3_600_000L)
        SordinaListener.releaseGroup(this, k)
        changed()
        hint(if (hours == null) tr(R.string.passes_always_x, h.groupTitle) else tr(R.string.passes_for_x, h.groupTitle, trn(R.plurals.n_hours, hours)))
    }

    /** Una regola dalla pagina Sordina: vale subito, anche per quello che è già nella tendina o in sordina. */
    private fun setRule(t: RuleTarget, s: RuleScope, level: Level?) {
        Rules.set(prefs, t, s, level)
        SordinaListener.reapply(this, t.pkg)
        changed()
        hint(if (level == null) tr(R.string.rule_removed) else tr(R.string.rule_set, t.name(s), level.label))
    }

    /** Telefoni prima di Android 10: la schermata di conferma del blocco, con il suo risultato */
    private var pendingUnlock: (() -> Unit)? = null
    private val credentialLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) pendingUnlock?.invoke()
        pendingUnlock = null
    }

    override fun unlock(title: String, onOk: () -> Unit) {
        val km = getSystemService(android.app.KeyguardManager::class.java)
        // Senza blocco schermo non c'è niente da chiedere
        if (km == null || !km.isDeviceSecure) { onOk(); return }
        // Se l'impronta non si può chiedere, la schermata del PIN di Android; mai un crash
        if (Build.VERSION.SDK_INT >= 29 && runCatching {
            val b = android.hardware.biometrics.BiometricPrompt.Builder(this).setTitle(title)
            if (Build.VERSION.SDK_INT >= 30) b.setAllowedAuthenticators(
                android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK or android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            ) else @Suppress("DEPRECATION") b.setDeviceCredentialAllowed(true)
            b.build().authenticate(android.os.CancellationSignal(), mainExecutor, object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult?) { onOk() }
            })
        }.isSuccess) return
        // Niente impronta (o Android 8–9): la conferma del blocco schermo. Se non si apre nemmeno quella, resta chiuso
        @Suppress("DEPRECATION")
        val i = km.createConfirmDeviceCredentialIntent(title, null) ?: return
        pendingUnlock = onOk
        runCatching { credentialLauncher.launch(i) }.onFailure { pendingUnlock = null }
    }

    override fun openHidden(a: AppEntry) { catalog.launch(a) }

    override fun changed() {
        calendarAt = 0L
        // Le nascoste si cambiano anche dalle Impostazioni
        hidden = prefs.hidden
        prefsVersion++
        moment = app.moments.current()
        scene = app.moments.scene()
        Scheduler.refresh(this)
        refreshProposals(force = true)
        refreshExtras()
    }

    private fun start(i: Intent): Boolean = try {
        startActivity(i); true
    } catch (_: ActivityNotFoundException) { false } catch (_: SecurityException) { false }

    private fun wipe() {
        app.brain.wipe()
        app.silence.stop()
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
        // Le Case di notte e in auto hanno il loro fondo: la barra segue quello
        val dark = S.c.dark || (screen == Screen.HOME && scene == Scene.NIGHT) || (screen == Screen.HOME && scene == Scene.CAR && S.c.dark)
        SideEffect {
            val c = WindowCompat.getInsetsController(window, view)
            c.isAppearanceLightStatusBars = !dark
            c.isAppearanceLightNavigationBars = !dark
        }
    }

    @Composable
    private fun Root() {
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
                                .filter { k -> scene == Scene.CAR || k.kind in BUBBLE_KINDS ||
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
                                // Niente in corso: quello di cui il cervello è sicuro in questa mezz'ora (spesso niente), di giorno e di sera
                                usual = remember(proposals, usualHidden, scene, prefsVersion) {
                                    if (!prefs.homeCards || (scene != Scene.DAY && scene != Scene.EVENING) || usualHidden == proposals.slot) emptyList()
                                    else proposals.usual.map { s -> val k = s.candidate; Usual(k.id, k.title, k.kind, k.app?.takeIf { prefs.appIcons }?.let { catalog.cachedIcon(it) }, s.why) }
                                },
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
                            onOpenHeld = { h -> SordinaListener.open(this@MainActivity, h.key) },
                            onReadAll = { SordinaListener.readAll(this@MainActivity) },
                            onReadGroup = { k -> SordinaListener.readGroup(this@MainActivity, k) },
                            onDoubleTap = if (remember(prefsVersion) { prefs.doubleTapLock }) ({
                                // Senza l'accessibilità di Sordina non si può spegnere: si dice come accenderla
                                if (!SordinaAccess.lock()) notice = Notice(tr(R.string.double_tap_needs))
                            }) else null,
                        )
                        Celebration(celebrate, onOpen = { celebrate = emptyList(); openStickers = true; screen = Screen.SETTINGS }) { celebrate = emptyList() } }
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
                                android.widget.Toast.makeText(this@MainActivity, tr(R.string.pins_full, Prefs.MAX_PINNED, a.label), android.widget.Toast.LENGTH_LONG).show()
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
                        ops = this@MainActivity,
                        onBack = { screen = Screen.HOME },
                        stickers = openStickers.also { androidx.compose.runtime.SideEffect { openStickers = false } },
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
                        val gain = remember(a.key) { app.habits.minutesPerVisit(a.packageName) }
                        val cost = remember(a.key) { app.habits.openCost(a.packageName, gain) }
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
                        SordinaScreen(
                        groups = if (emptyPreview) emptyList() else groups,
                        overrides = remember(prefsVersion) { prefs.categoryOverrides },
                        braked = remember(prefsVersion) { prefs.braked },
                        order = remember(prefsVersion) { when (prefs.sordinaOrder) { 1 -> null; 2 -> Categories.custom(prefs.categoryOrder); else -> Categories.AUTO } },
                        icon = ::notifIcon,
                        onOpen = { h -> SordinaListener.open(this@MainActivity, h.key) },
                        onRead = { k -> SordinaListener.readGroup(this@MainActivity, k) },
                        onReadOne = { h -> SordinaListener.readOne(this@MainActivity, h.key) },
                        onReadAll = { SordinaListener.readAll(this@MainActivity); screen = Screen.HOME },
                        onLetPass = ::letPass,
                        ruleOf = { t, s -> Rules.get(prefs, t, s) },
                        onRule = ::setRule,
                        onMove = { k, cat -> prefs.categoryOverrides = prefs.categoryOverrides + (k to cat.name); changed() },
                        onBack = { screen = Screen.HOME },
                    )
                    }

                    Screen.ONBOARDING -> Onboarding(
                        app = app,
                        perms = perms,
                        apps = apps,
                        usage = proposals.usage,
                        startStep = onbStep,
                        ops = this@MainActivity,
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

    private val versionName: String
        get() = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"

    companion object {
        const val ACTION_WAITING = "app.sordina.WAITING"
        /** In auto, dopo il tocco: quanto si aspetta che cominci a parlare */
        private const val NO_SPEECH_MILLIS = 6_000L
        /** La scheda "di solito" del cervello compare solo da questa probabilità (misurata sulla memoria vera). */
        /** Le schede che il fumetto dice sempre: in corso (musica, timer, corsa) o che non aspettano (codice, casa, chiamata) */
        val BUBBLE_KINDS = setOf(CardKind.HEALTH, CardKind.OTP, CardKind.ASK, CardKind.CALL, CardKind.RIDE, CardKind.TIMER, CardKind.MUSIC)
        /** Tornando a Casa entro così poco, le proposte di prima restano */
        private const val PROPOSALS_GAP = 20_000L
        /** Il calendario si rilegge al massimo così spesso (prima, se cambia) */
        private const val CALENDAR_GAP = 5 * 60_000L
        /** Le app che cambiano tutte insieme (aggiornamenti dal Play Store): un ricaricamento solo, dopo l'ultima */
        private const val APPS_SETTLE = 3_000L
    }

    /** Android avvisa quando il calendario cambia: la prossima volta in Casa si rilegge. */
    private fun watchCalendar() {
        if (calendarObserver != null || checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return
        val o = object : android.database.ContentObserver(null) {
            override fun onChange(selfChange: Boolean) { calendarAt = 0L }
        }
        if (runCatching { contentResolver.registerContentObserver(android.provider.CalendarContract.Events.CONTENT_URI, true, o) }.isSuccess) calendarObserver = o
    }
}
