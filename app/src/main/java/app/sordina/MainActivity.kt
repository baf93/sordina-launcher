package app.sordina
import app.sordina.ui.Celebration

import app.sordina.data.Backup
import app.sordina.data.HomeRun
import app.sordina.data.Signals
import app.sordina.data.Tally
import app.sordina.ui.theme.autoAccent
import app.sordina.data.AppIcons
import app.sordina.ui.Motion

import app.sordina.data.Prefs
import app.sordina.data.Faults
import app.sordina.data.Held
import app.sordina.data.Habits
import app.sordina.data.RuleOp
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
import app.sordina.service.Classifier
import app.sordina.service.SordinaListener
import app.sordina.service.BrakeGuard
import app.sordina.service.BudgetWatch
import app.sordina.service.HomeGuard
import app.sordina.service.AlarmCode
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

enum class Screen { HOME, INTENT, ALL_APPS, SETTINGS, BRAKE, ONBOARDING, SORDINA }

class MainActivity : ComponentActivity(), Ops {

    internal val app by lazy { SordinaApp.of(this) }
    internal val prefs get() = app.prefs
    internal val catalog get() = app.catalog
    internal lateinit var proposalsEngine: Proposals
    internal lateinit var actions: Actions
    internal var appsCallback: LauncherApps.Callback? = null

    internal var screen by mutableStateOf(Screen.HOME)
    /** Le app nascoste nella pagina Sordina: sbloccate fino a quando si lascia la pagina */
    internal var hiddenRevealed by mutableStateOf(false)
    /** L'ultimo codice copiato da una scheda e quando: si toglie dagli appunti dopo [COPY_TTL] */
    private var guardTry = 0L
    internal var copiedCode: String? = null
    internal var copiedAt = 0L
    /** Si torna alla ricerca da Tutte le app: niente tastiera, anche se di solito si apre */
    internal var backFromApps = false
    internal var apps by mutableStateOf<List<AppEntry>>(emptyList())
    internal var proposals by mutableStateOf(Proposals.Result(emptyList(), emptyMap(), emptyMap(), emptyMap(), false, -1))
    internal var family by mutableStateOf(ThemeFamily.CARTA)
    internal var mode by mutableStateOf(ThemeMode.AUTO)
    internal var hidden by mutableStateOf<Set<String>>(emptySet())
    internal var emptyPreview by mutableStateOf(false)
    internal var prefsVersion by mutableIntStateOf(0)
    internal var perms by mutableStateOf(PermState())
    internal var query by mutableStateOf("")
    internal var notice by mutableStateOf<Notice?>(null)
    internal var voice by mutableStateOf(VoiceState(Build.VERSION.SDK_INT >= 31, false, ""))
    internal var brakeTarget by mutableStateOf<AppEntry?>(null)
    /** Il browser dove si è aperto il sito dell'app frenata: passato il freno si torna lì */
    internal var brakeVia: String? = null
    internal var events by mutableStateOf<List<Upcoming>>(emptyList())
    /** Il fumetto "di solito" scorso via: non torna fino alla prossima fascia di proposte */
    internal var onbStep by mutableIntStateOf(0)
    internal var doneHabits by mutableStateOf<Set<String>>(emptySet())
    internal var backdrop by mutableStateOf(Backdrop())
    internal var accentPref by mutableIntStateOf(0)
    internal var glassPref by mutableStateOf(false)
    internal var blackPref by mutableStateOf(false)
    internal var agendaEvents by mutableStateOf<List<Upcoming>>(emptyList())
    internal var weatherNow by mutableStateOf<String?>(null)
    internal var moment by mutableStateOf(Moment.NONE)
    internal var scene by mutableStateOf(Scene.DAY)
    /** Cosa impedisce al motore di lavorare (null = tutto bene) */
    internal var health by mutableStateOf<Health?>(null)
    internal var tomorrowEvents by mutableStateOf<List<Upcoming>>(emptyList())
    internal var tomorrowWeather by mutableStateOf<String?>(null)
    internal var recap by mutableStateOf<app.sordina.ui.home.DayRecap?>(null)
    internal var carPeople by mutableStateOf<List<CarPerson>>(emptyList())
    /** Le foto delle persone in auto: si leggono dalla rubrica una volta sola */
    internal val carPhotos = java.util.concurrent.ConcurrentHashMap<Long, java.util.Optional<androidx.compose.ui.graphics.ImageBitmap>>()
    /** Gli adesivi appena vinti, da far vedere in cima a Casa */
    internal var celebrate by mutableStateOf<List<app.sordina.data.Medal>>(emptyList())
    internal var medalsAt = 0L
    /** L'adesivo da aprire nella pagina adesivi (dal riquadro in Casa) */
    internal var openSticker: app.sordina.data.Medal? = null
    internal var recognizer: SpeechRecognizer? = null

    internal val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshPerms()
        changed()
    }
    internal val wallpaperLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { Backdrops.save(this@MainActivity, uri) }
            if (ok) { prefs.wallpaperMode = 2; backdropChanged() }
        }
    }
    internal val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            contentResolver.openOutputStream(uri)?.use { it.write(prefs.export().toByteArray()) }
            notice = Notice(tr(R.string.cfg_exported))
        }
    }
    internal val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
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
                        SamsungSleepSheet { checkHealth() }
                    }
                }
            }
        }
    }

    /** Il gesto in corso è quello che ha mandato via la sordina: non arriva a nessuno */
    internal var swallow = false

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

    internal fun handleIntent(intent: Intent?) {
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
            val genuine = BrakeGuard.genuine(intent.getStringExtra(BrakeGuard.TOKEN)); intent.removeExtra(BrakeGuard.TOKEN)
            val via = intent.getStringExtra(BrakeGuard.VIA); intent.removeExtra(BrakeGuard.VIA)
            // Solo il freno chiesto da Sordina, per un'app che hai frenato tu; si torna solo in un browser
            if (!genuine || pkg !in prefs.braked) return@let
            brakeVia = via?.takeIf { v -> packageManager.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).setPackage(v), 0).isNotEmpty() }
            (apps.firstOrNull { it.packageName == pkg } ?: catalog.load().firstOrNull { it.packageName == pkg })?.let { brakeTarget = it; screen = Screen.BRAKE; return }
        }
        when {
            intent?.action == ACTION_WAITING -> { screen = Screen.SORDINA }
            intent?.hasCategory(Intent.CATEGORY_HOME) == true -> { screen = Screen.HOME; query = "" }
        }
    }

    override fun onResume() {
        super.onResume()
        clearCopiedCode()
        refreshPerms()
        SordinaListener.rebind(this)
        SordinaAccess.revive(this)
        BrakeGuard.atHome(this, prefs)
        // Le serrature e gli allarmi di casa, per chiedere l'impronta: si riguardano ogni sei ore (e dopo ogni comando)
        // Fuori casa senza https la casa non risponde: si riprova al massimo ogni dieci minuti
        val t = System.currentTimeMillis()
        if (app.ha.configured && t - prefs.haGuardedAt > 6 * 3_600_000L && t - guardTry > 10 * 60_000L) {
            guardTry = t
            lifecycleScope.launch(Dispatchers.IO) { runCatching { app.ha.entities(force = true) } }
        }
        // Le app frenate possono essere cambiate: l'accessibilità ascolta solo quelle
        SordinaAccess.refresh()
        SordinaListener.sync()
        SordinaListener.returnedHome()
        moment =app.moments.current()
        scene = app.moments.scene()
        alarmFeedback()
        // Tornando da un'app la situazione è cambiata (l'app di prima, il luogo): si ricalcola, mai mentre guardi.
        // Un'occhiata a un'app e subito di nuovo Casa: le proposte di pochi secondi fa vanno ancora bene
        refreshProposals(force = System.currentTimeMillis() - proposalsAt > PROPOSALS_GAP)
        refreshExtras()
    }

    /** L'ultima sveglia di cui si è già detto "fatto": la conferma si dice una volta sola */
    internal var alarmSaidAt = 0L

    /** Hai appena impostato una sveglia nell'Orologio: tornando a Casa la sordina conferma l'ora che ha capito. */
    internal fun alarmFeedback() {
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
        hiddenRevealed = false
        // Fuori da Casa: il freno guarda quale app va davanti (solo adesso, non mentre sei in Casa)
        BrakeGuard.leftHome(this, prefs)
        // Dopo aver aperto un'app, al ritorno si trova Casa (le impostazioni restano dove erano)
        if (screen != Screen.SETTINGS && screen != Screen.ONBOARDING) screen = Screen.HOME
        query = ""
        notice = null
    }

    // Dati

    internal fun reloadApps(forceProposals: Boolean) {
        lifecycleScope.launch {
            apps = withContext(Dispatchers.Default) { catalog.load() }
            refreshProposals(forceProposals)
            // Le icone si preparano in sottofondo, così la lista e la ricerca non devono caricarle mentre scorri
            if (prefs.appIcons) { val list = apps; launch(Dispatchers.Default) { catalog.warmIcons(list) } }
        }
    }

    /** Le proposte cambiano solo al cambio di fascia (mezz'ora) o di momento, mai mentre le guardi. */
    internal fun refreshProposals(force: Boolean) {
        val slot = Proposals.slotOf(System.currentTimeMillis()) * 10 + moment.ordinal
        val permissionChanged = proposals.needsPermission == proposalsEngine.hasPermission()
        if (!force && slot == proposals.slot && !permissionChanged) return
        val current = apps
        if (current.isEmpty()) return
        proposalsAt = System.currentTimeMillis()
        lifecycleScope.launch {
            // Se il cervello sbaglia, restano le proposte di prima e l'errore finisce nel registro: Casa non si chiude mai per questo
            val r = withContext(Dispatchers.Default) { Faults.guard("proposals") { proposalsEngine.compute(current, prefs) } } ?: return@launch
            proposals = r.copy(slot = slot)
        }
    }

    /** Casa: eventi vicini e schede "di solito". */
    internal var extrasAt = 0L
    /** L'ultima lettura del calendario: si rilegge quando cambia (osservatore) o al massimo ogni [CALENDAR_GAP] */
    @Volatile internal var calendarAt = 0L
    internal var calendarObserver: android.database.ContentObserver? = null
    internal var proposalsAt = 0L
    internal var appsReload: kotlinx.coroutines.Job? = null

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

    internal fun refreshPerms() {
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

    internal fun checkHealth() {
        if (!prefs.onboarded) { health = null; return }
        // La batteria la si chiede una volta: se dici di no, non si insiste in Casa (resta nelle Impostazioni)
        health = SordinaListener.health(this)?.takeIf { it != Health.BATTERY || !prefs.batteryAsked }
    }

    internal fun isDefaultHome(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName == packageName
    }

    // Azioni

    internal fun launch(a: AppEntry) {
        // Senza accesso all'utilizzo il cervello non vede le app aperte: le ricorda da qui
        if (!perms.usage) app.brain.chosen("", Candidate("app:${a.key}", a.label, null, Kind.APP, 0, false, Act.OpenApp(a), a), false)
        if (a.packageName in prefs.braked && brakeNow()) {
            brakeTarget = a
            brakeVia = null
            screen = Screen.BRAKE
        } else {
            // Frenata ma fuori dalla fascia del freno: niente freno, però l'avviso del budget sì
            if (a.packageName in prefs.braked) BudgetWatch.opened(this, a.packageName, sawBrake = false)
            catalog.launch(a)
        }
    }

    /** Il freno vale adesso? Sempre, o solo nella fascia scelta (es. 22–8). */
    internal fun brakeNow(): Boolean = BrakeGuard.brakeNow(prefs)

    /** L'attesa: quella scelta, più 5 secondi per ogni volta che l'hai aperta comunque nell'ultima ora (al massimo un minuto). */
    internal fun brakeWait(pkg: String): Int {
        // Budget di oggi finito: l'attesa raddoppia
        val base = prefs.brakeSeconds * (if ((app.habits.leftToday(pkg) ?: 1) <= 0) 2 else 1)
        if (!prefs.brakeGrow) return base.coerceAtMost(60)
        return (base + 5 * app.habits.opensLastHour(pkg)).coerceAtMost(60)
    }

    internal fun understand(q: String, learn: Boolean = true): Understanding = app.intents.understand(q, apps.filter { it.key !in hidden }, proposals.usage, learn)

    internal fun run(c: Candidate, index: Int) {
        // Un allarme salvato: impronta, poi il codice va a Home Assistant. Non si impara né si propone
        (c.act as? Act.AlarmOff)?.let { a -> alarmOff(a.entity); return }
        (c.act as? Act.AlarmOn)?.let { a -> alarmOn(a.entity, a.mode); return }
        // Apre o disarma qualcosa in casa: prima impronta o PIN, poi parte come sempre (e solo allora si impara)
        (c.act as? Act.Assist)?.takeIf { it.guarded }?.let { a ->
            HomeGuard.ask(this, c.title) { run(c.copy(act = a.copy(guarded = false)), index) }
            return
        }
        // index < 0: toccata tra le proposte, non dalla ricerca (non conta per "giusta al primo colpo")
        if (index >= 0) app.intents.chosen(query, c, index)
        // Le app aperte le ricorda launch() (o la cronologia d'uso): qui solo le lettere scritte e le altre azioni
        app.brain.chosen(query, c, perms.usage || c.act is Act.OpenApp)
        // Una routine di casa: impronta se l'hai chiesta (e il codice dell'allarme legato), poi parte
        (c.act as? Act.Routine)?.let { r -> routine(r.entity); return }
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
                // Fatto (sveglia, timer, evento, torcia…): si torna a Casa e risponde la sordina nel suo fumetto, come a voce.
                // Restano nella ricerca il conto (lo stai leggendo) e quello che si può annullare
                val home = stays && out.ok && undo == null && !here && act !is Act.Calc && out.message != null
                if (home) screen = Screen.HOME
                if (out.message != null && !here && !home) notice = Notice(out.message, undo?.let { u -> { lifecycleScope.launch { u(); notice = Notice(tr(R.string.undone)) } } })
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

    /**
     * Lancia una routine abilitata. Con un allarme da disattivare: una sola impronta apre il codice, si disattiva e,
     * solo se è andata, parte la routine ("Entra" non parte con la centrale ancora inserita). Con un allarme da
     * inserire: impronta, parte la routine e, se è andata, si inserisce l'allarme nella sua modalità ("Esco").
     */
    internal fun routine(entity: String) {
        val r = prefs.haRoutines[entity]?.takeIf { it.on } ?: return
        val a = r.alarm?.let { prefs.haAlarms[it] }
        val alarmName = a?.name ?: r.alarm.orEmpty()
        /** [code]: quello dell'allarme legato ("" se non lo chiede), null senza allarme */
        fun start(code: String?) {
            screen = Screen.HOME; query = ""
            asking()
            lifecycleScope.launch {
                val done = withContext(Dispatchers.IO) { HomeRun.routine(app, entity, code) }
                answer(done.message, ok = done.ok)
                if (done.ok) Feel(true, prefs.haptics, window.decorView).confirm()
            }
        }
        val lost = { answer(tr(R.string.alarm_code_lost), ok = false) }
        when {
            a != null && !r.arm -> unlessOff(r.alarm!!) {
                AlarmCode.use(this, prefs, r.alarm, onCode = { code -> start(code) }, onLost = lost,
                    title = tr(R.string.routine_with_alarm_title, r.name, alarmName))
            }
            a != null -> {
                val title = tr(R.string.routine_with_arm_title, r.name, alarmName)
                if (a.codeArm && a.enc.isNotEmpty()) AlarmCode.use(this, prefs, r.alarm!!, onCode = { code -> start(code) }, onLost = lost, title = title)
                else HomeGuard.ask(this, title) { start("") }
            }
            r.auth || r.alarm != null -> HomeGuard.ask(this, r.name) { start(null) }
            else -> start(null)
        }
    }

    /** Disattiva un allarme salvato ([entity] null: se ne hai più d'uno, prima si sceglie quale). */
    internal fun alarmOff(entity: String?) {
        val saved = prefs.haAlarms
        if (entity == null) {
            val list = saved.entries.sortedBy { it.value.name.lowercase() }
            android.app.AlertDialog.Builder(this).setTitle(tr(R.string.alarm_pick))
                .setItems(list.map { it.value.name }.toTypedArray()) { _, i -> alarmOff(list[i].key) }
                .setNegativeButton(tr(R.string.cancel), null).show()
            return
        }
        val name = saved[entity]?.name ?: return
        unlessOff(entity) { AlarmCode.use(this, prefs, entity, onCode = { code ->
            screen = Screen.HOME; query = ""
            asking()
            lifecycleScope.launch {
                val done = withContext(Dispatchers.IO) { HomeRun.alarmOff(app, entity, code) }
                answer(done.message, done.ok)
                if (done.ok) Feel(true, prefs.haptics, window.decorView).confirm()
            }
        }, onLost = { answer(tr(R.string.alarm_code_lost), ok = false) }) }
    }

    /**
     * Inserisce un allarme salvato in [mode] (null: la predefinita). Se vuole il codice anche per inserire, impronta;
     * se no si inserisce subito (mettere in sicurezza non ha attrito). Già inserito: lo dico e basta.
     */
    internal fun alarmOn(entity: String?, mode: String?) {
        val saved = prefs.haAlarms.filterValues { it.arm }
        if (entity == null) {
            val list = saved.entries.sortedBy { it.value.name.lowercase() }
            android.app.AlertDialog.Builder(this).setTitle(tr(R.string.alarm_pick))
                .setItems(list.map { it.value.name }.toTypedArray()) { _, i -> alarmOn(list[i].key, mode) }
                .setNegativeButton(tr(R.string.cancel), null).show()
            return
        }
        val a = saved[entity] ?: return
        val m = mode ?: a.mode ?: "away"
        val label = AlarmCode.modeLabel(m)
        fun arm(code: String) {
            screen = Screen.HOME; query = ""
            asking()
            lifecycleScope.launch {
                val done = withContext(Dispatchers.IO) { HomeRun.alarmOn(app, entity, m, code) }
                answer(done.message, done.ok)
                if (done.ok) Feel(true, prefs.haptics, window.decorView).confirm()
            }
        }
        lifecycleScope.launch {
            val st = withContext(Dispatchers.IO) { runCatching { app.ha.state(entity)?.state }.getOrNull() }
            if (st != null && (st.startsWith("armed_") || st == "arming" || st == "pending" || st == "triggered")) {
                screen = Screen.HOME; query = ""
                answer(tr(R.string.alarm_already_on, a.name))
                return@launch
            }
            if (a.codeArm && a.enc.isNotEmpty())
                AlarmCode.use(this@MainActivity, prefs, entity, onCode = ::arm, onLost = { answer(tr(R.string.alarm_code_lost), ok = false) },
                    title = tr(R.string.alarm_on_mode_x, a.name, label))
            else arm("")
        }
    }

    /**
     * Se l'allarme è già disattivato lo dico e basta: niente impronta, niente codice, niente routine.
     * Se casa non risponde si va avanti come sempre (sarà il disarmo a dire com'è andata).
     */
    private fun unlessOff(entity: String, go: () -> Unit) {
        lifecycleScope.launch {
            val st = withContext(Dispatchers.IO) { runCatching { app.ha.state(entity)?.state }.getOrNull() }
            if (st == "disarmed") {
                screen = Screen.HOME; query = ""
                answer(tr(R.string.alarm_already_off, prefs.haAlarms[entity]?.name ?: entity))
            } else go()
        }
    }

    /** Casa sta rispondendo: la sordina sale e aspetta (con le animazioni), se no il fumetto piccolo "Chiedo a casa…" */
    private fun asking() {
        if (screen == Screen.HOME && prefs.animations) { bubble = null; reply = HomeReply(null) } else bubble = Said(tr(R.string.ha_asking), wait = true)
    }

    // Schede di Casa

    /** A mani libere: se dopo qualche secondo non hai detto nulla, si smette di ascoltare (in auto non serve un secondo tocco). */
    internal val voiceHandler = android.os.Handler(android.os.Looper.getMainLooper())
    internal var speechHeard = false
    internal val noSpeech = Runnable {
        if (voice.listening && !speechHeard) {
            runCatching { recognizer?.cancel() }
            voice = voice.copy(listening = false, partial = "", level = 0f)
            if (screen == Screen.HOME) answer(tr(R.string.not_understood), ok = false)
        }
    }

    internal var voiceDownAt = 0L

    // I messaggi brevi ("Copiato", "Aggiunto"…): una pillola nel tema, non il toast di Android
    internal var bubble by mutableStateOf<Said?>(null)
    /** La sordina che risponde per la casa (frasi mandate ad Assist col token) */
    internal var reply by mutableStateOf<HomeReply?>(null)
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
    internal val notifIcons = HashMap<String, androidx.compose.ui.graphics.ImageBitmap?>()

    /** L'icona dell'app di una notifica (anche app di sistema senza voce nel cassetto). */
    internal fun notifIcon(pkg: String): androidx.compose.ui.graphics.ImageBitmap? = notifIcons.getOrPut(pkg) {
        AppIcons.forPackage(packageManager, pkg, (40 * resources.displayMetrics.density).toInt())
    }

    /** Lascia passare un contatto o un'app: per qualche ora, oppure sempre (importante / livello Subito). */
    /**
     * Chi scrive ha un nome che somiglia a una sola persona importante ("Elisa" e Elisa Rossi), ma non è stata riconosciuta:
     * la persona da proporre (chiave, nome in rubrica). Solo nelle chat a due, e una volta sola per nome e app.
     */
    internal fun aliasFor(h: Held): Pair<String, String>? {
        if (!h.isMessage || h.conversation != null) return null
        val seen = Search.normalize(h.person ?: return null).ifBlank { return null }
        // Solo con un legame che chi scrive non può falsificare: la conversazione dell'app, o un nome che viene dalla rubrica
        val k = Classifier.aliasKey(h.pkg, h.shortcut, h.person) ?: return null
        if (k in prefs.personAliases || k in prefs.aliasRefused) return null
        val first = seen.substringBefore(' ')
        val match = prefs.importantNames.filter { it != seen && (it.substringBefore(' ') == first || seen.startsWith("$it ") ) }.singleOrNull() ?: return null
        val name = runCatching { app.people.all().firstOrNull { it.normalized == match }?.name }.getOrNull()
            ?: match.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
        return match to name
    }

    internal fun setAlias(h: Held, important: String, yes: Boolean) {
        val k = Classifier.aliasKey(h.pkg, h.shortcut, h.person) ?: return
        if (yes) {
            prefs.personAliases = prefs.personAliases + (k to important)
            // Il nome visto, per l'elenco in Impostazioni (la chiave della conversazione non si legge)
            h.person?.let { prefs.ruleNames = prefs.ruleNames + (k to "${h.appLabel} · $it") }
            SordinaListener.reapply(this, h.pkg)
        } else prefs.aliasRefused = prefs.aliasRefused + k
        changed()
    }

    internal fun letPass(h: Held, hours: Int) {
        val k = h.groupKey
        prefs.passUntil = prefs.passUntil + (k to System.currentTimeMillis() + hours * 3_600_000L)
        SordinaListener.releaseGroup(this, k)
        changed()
        hint(tr(R.string.passes_for_x, h.groupTitle, trn(R.plurals.n_hours, hours)))
    }

    /** Una regola dalla pagina Sordina: vale subito, anche per quello che è già nella tendina o in sordina. */
    internal fun setRule(t: RuleTarget, op: RuleOp) {
        Rules.toggle(prefs, t, op)
        SordinaListener.reapply(this, t.pkg)
        changed()
        val who = when (op) { RuleOp.PERSON_IN -> t.personTitle ?: t.person.orEmpty(); RuleOp.APP_IN -> t.appLabel; RuleOp.OFF -> t.kindName }
        hint(tr(if (Rules.enters(prefs, t, op)) R.string.rule_enters else R.string.rule_waits, who))
    }

    /** Telefoni prima di Android 10: la schermata di conferma del blocco, con il suo risultato */
    internal var pendingUnlock: (() -> Unit)? = null
    internal val credentialLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
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

    internal fun start(i: Intent): Boolean = try {
        startActivity(i); true
    } catch (_: ActivityNotFoundException) { false } catch (_: SecurityException) { false }

    internal fun wipe() {
        app.brain.wipe()
        app.silence.stop()
        SordinaListener.readAll(this)
        app.store.wipe()
        app.today.wipe()
        app.ha.setToken("")
        prefs.clearAll()
        family = prefs.family; mode = prefs.mode; hidden = prefs.hidden
        changed()
        screen = Screen.ONBOARDING
    }

    /** Scorri in giù da Casa: tendina delle notifiche di sistema. */
    @SuppressLint("WrongConstant")
    internal fun expandNotifications() {
        runCatching {
            val sbs = getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sbs)
        }
    }

    // Interfaccia

    internal val versionName: String
        get() = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"

    companion object {
        const val ACTION_WAITING = "app.sordina.WAITING"
        /** In auto, dopo il tocco: quanto si aspetta che cominci a parlare */
        internal const val NO_SPEECH_MILLIS = 6_000L
        /** La scheda "di solito" del cervello compare solo da questa probabilità (misurata sulla memoria vera). */
        /** Le schede che il fumetto dice sempre: in corso (musica, timer, corsa) o che non aspettano (codice, casa, chiamata) */
        val BUBBLE_KINDS = setOf(CardKind.HEALTH, CardKind.OTP, CardKind.ASK, CardKind.CALL, CardKind.RIDE, CardKind.TIMER, CardKind.MUSIC)
        /** Tornando a Casa entro così poco, le proposte di prima restano */
        internal const val PROPOSALS_GAP = 20_000L
        /** Il calendario si rilegge al massimo così spesso (prima, se cambia) */
        internal const val CALENDAR_GAP = 5 * 60_000L
        /** Le app che cambiano tutte insieme (aggiornamenti dal Play Store): un ricaricamento solo, dopo l'ultima */
        internal const val APPS_SETTLE = 3_000L
    }

    /** Android avvisa quando il calendario cambia: la prossima volta in Casa si rilegge. */
    internal fun watchCalendar() {
        if (calendarObserver != null || checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return
        val o = object : android.database.ContentObserver(null) {
            override fun onChange(selfChange: Boolean) { calendarAt = 0L }
        }
        if (runCatching { contentResolver.registerContentObserver(android.provider.CalendarContract.Events.CONTENT_URI, true, o) }.isSuccess) calendarObserver = o
    }
}
