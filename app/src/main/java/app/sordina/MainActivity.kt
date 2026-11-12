package app.sordina

import app.sordina.data.Prefs
import app.sordina.data.Held
import app.sordina.R
import app.sordina.data.Categories
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.sordina.ui.sordina.SordinaScreen
import app.sordina.ui.Feel
import app.sordina.ui.LocalFeel
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
import app.sordina.data.Bar
import app.sordina.data.Birthday
import app.sordina.data.birthdaysToday
import app.sordina.data.CardAction
import app.sordina.data.CardKind
import app.sordina.data.CardOp
import app.sordina.data.Upcoming
import app.sordina.data.upcoming
import app.sordina.data.agenda
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
    private var backdrop by mutableStateOf(Backdrop())
    private var accentPref by mutableIntStateOf(0)
    private var glassPref by mutableStateOf(false)
    private var agendaEvents by mutableStateOf<List<Upcoming>>(emptyList())
    private var weatherNow by mutableStateOf<String?>(null)
    private var rainAt by mutableStateOf<Pair<Long, Int>?>(null)
    private var birthdays by mutableStateOf<List<Birthday>>(emptyList())
    private var moment by mutableStateOf(Moment.NONE)
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
        proposalsEngine = Proposals(this)
        actions = Actions(this, catalog, app.people, app.ha, prefs)
        family = prefs.family
        mode = prefs.mode
        hidden = prefs.hidden
        if (!prefs.onboarded) screen = Screen.ONBOARDING
        handleIntent(intent)
        backdropChanged()

        appsCallback = catalog.registerChanges { reloadApps(forceProposals = true) }
        reloadApps(forceProposals = true)

        setContent {
            val accentColor = when (accentPref) {
                0 -> null
                1 -> backdrop.accent?.let { androidx.compose.ui.graphics.Color(it) }
                else -> androidx.compose.ui.graphics.Color(accentPref)
            }
            SordinaTheme(family, mode, accentColor) {
                val view = androidx.compose.ui.platform.LocalView.current
                val feel = remember(prefsVersion) { Feel(prefs.animations, prefs.haptics, view) }
                androidx.compose.runtime.CompositionLocalProvider(LocalFeel provides feel, LocalGlass provides (backdrop.active && glassPref)) {
                    SystemBars()
                    Root()
                }
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
            intent?.action == ACTION_WAITING -> { screen = Screen.SORDINA }
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
            val endOfDay = java.time.LocalDate.now().plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            val all = (app.calendars.agenda(this@MainActivity) + app.ha.calendarEvents(prefs.haCalendars)).sortedBy { it.begin }
            agendaEvents = all.filter { it.begin < endOfDay }.take(3).ifEmpty { all.take(1) }
            weatherNow = app.weather.now()
            rainAt = app.weather.rain
            birthdays = birthdaysToday(this@MainActivity)
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
                if (out.message != null) notice = Notice(out.message, undo?.let { u -> { lifecycleScope.launch { u(); notice = Notice(tr(R.string.undone)) } } })
                if (out.message != null && screen == Screen.HOME) android.widget.Toast.makeText(this@MainActivity, out.message, android.widget.Toast.LENGTH_SHORT).show()
                if (out.ok) app.routines.onCommand(c.title, query.ifBlank { c.title })
            }
        }
    }

    // Schede di Casa

    private fun eventCard(e: Upcoming, now: Long): Card {
        val min = ((e.begin - now) / 60_000).toInt()
        val label = when {
            min <= 0 -> tr(R.string.now_cap)
            min < 60 -> tr(R.string.in_min, min)
            else -> tr(R.string.at_time, java.time.Instant.ofEpochMilli(e.begin).atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)))
        }
        val actions = buildList {
            e.link?.let { add(CardAction(tr(R.string.join), CardOp.OpenUrl(it))) }
            e.location?.let { add(CardAction(tr(R.string.navigate), CardOp.Navigate(it))) }
            if (isEmpty()) add(CardAction(tr(R.string.open), CardOp.OpenEvent(e.id)))
        }
        return Card("event:${e.id}:${e.begin}", CardKind.EVENT, label, e.title, e.location, actions, e.begin, e.end,
            bar = if (e.begin <= now) Bar(e.begin, e.end) else null)
    }

    /** Pioggia in arrivo: una scheda finché non comincia. */
    private fun rainCard(now: Long): Card? {
        val (at, p) = rainAt ?: return null
        val min = ((at - now) / 60_000).toInt()
        val title = when {
            min < 10 -> tr(R.string.rain_soon)
            min < 60 -> tr(R.string.rain_in, min)
            else -> tr(R.string.rain_around, java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)))
        }
        return Card("rain:$at", CardKind.RAIN, tr(R.string.weather), title, tr(R.string.rain_chance, p),
            actions = listOf(CardAction(tr(R.string.open), CardOp.Weather)), time = now, expiresAt = at + 30 * 60_000)
    }

    private fun birthdayCards(): List<Card> = birthdays.map { b ->
        Card("birthday:${b.name}", CardKind.BIRTHDAY, b.age?.let { tr(R.string.birthday_age, it) } ?: tr(R.string.birthday), b.name,
            actions = listOf(CardAction(tr(R.string.write), CardOp.Query("scrivi a ${b.name}")), CardAction(tr(R.string.call), CardOp.Query("chiama ${b.name}"))),
            avatar = true)
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
    private fun agendaRows(waiting: Int): List<AgendaRow> {
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now()
        val hm = java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)
        val out = ArrayList<AgendaRow>()
        if (moment != Moment.NONE) out += AgendaRow(tr(R.string.now_l), moment.phrase, "moment")
        val timed = ArrayList<Pair<Long, AgendaRow>>()
        for (e in agendaEvents) {
            val key = if (e.link == "ha") "ha" else "event:${e.id}"
            val t = java.time.Instant.ofEpochMilli(e.begin).atZone(zone)
            val d = t.toLocalDate()
            val row = when {
                e.begin <= System.currentTimeMillis() -> AgendaRow(tr(R.string.now_l), e.title, key)
                d == today -> AgendaRow(t.format(hm), e.title, key)
                d == today.plusDays(1) -> AgendaRow(tr(R.string.tomorrow_l), tr(R.string.title_time, e.title, t.format(hm)), key)
                else -> AgendaRow(t.format(java.time.format.DateTimeFormatter.ofPattern("EEE", Locale.getDefault())).replace(".", ""),
                    tr(R.string.title_time, e.title, t.format(hm)), key)
            }
            timed += e.begin to row
        }
        // Il prossimo appuntamento in accento, gli altri neutri
        return out + timed.sortedBy { it.first }.mapIndexed { i, p -> if (i == 0) p.second.copy(accent = true) else p.second }
    }

    private fun onAgenda(key: String) {
        when {
            key == "weather" -> openWeather()
            key == "sordina" -> screen = Screen.SORDINA
            key == "ha" -> packageManager.getLaunchIntentForPackage("io.homeassistant.companion.android")?.let { start(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
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
        when {
            card.kind == CardKind.MUSIC -> openMusic()
            card.kind == CardKind.EVENT -> card.key.split(':').getOrNull(1)?.toLongOrNull()?.let { cardAction(card, CardAction("", CardOp.OpenEvent(it))) }
            card.kind == CardKind.HABIT -> card.actions.firstOrNull()?.let { cardAction(card, it) }
            card.kind == CardKind.RAIN -> openWeather()
            card.kind == CardKind.BIRTHDAY -> card.actions.firstOrNull()?.let { cardAction(card, it) }
            card.kind == CardKind.PAYMENT -> card.actions.firstOrNull()?.let { cardAction(card, it) }
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
                if (Build.VERSION.SDK_INT < 33) android.widget.Toast.makeText(this, tr(R.string.copied), android.widget.Toast.LENGTH_SHORT).show()
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
                    android.widget.Toast.makeText(this@MainActivity, if (id != null) tr(R.string.added) else tr(R.string.failed), android.widget.Toast.LENGTH_SHORT).show()
                    if (id != null) app.signals.dismiss(card.key)
                }
            }
            is CardOp.OpenPackage -> {
                doneHabits = doneHabits + card.key
                apps.firstOrNull { it.packageName == op.pkg && !it.isWork }?.let(::launch)
                    ?: packageManager.getLaunchIntentForPackage(op.pkg)?.let { start(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            is CardOp.Query -> {
                if (card.kind == CardKind.HABIT || card.kind == CardKind.BIRTHDAY) doneHabits = doneHabits + card.key
                query = op.query
                val u = understand(op.query)
                val top = u.top
                if (u.confident && top != null) run(top, 0) else screen = Screen.INTENT
            }
            CardOp.MusicPrev -> music?.skipToPrevious()
            CardOp.MusicNext -> music?.skipToNext()
            CardOp.MusicToggle -> if (app.signals.musicPlaying()) music?.pause() else music?.play()
            is CardOp.Fire -> { if (SordinaListener.fire(this, op.key, op.index)) app.signals.dismiss(card.key) else SordinaListener.open(this, op.key) }
            is CardOp.Done -> { SordinaListener.done(op.key); app.signals.dismiss(card.key) }
            is CardOp.Later -> { SordinaListener.later(op.key); app.signals.remove(card.key) }
            CardOp.Weather -> openWeather()
            CardOp.Dismiss -> { if (card.kind == CardKind.HABIT || card.kind == CardKind.BIRTHDAY) doneHabits = doneHabits + card.key; app.signals.dismiss(card.key) }
        }
    }

    // Voce: solo il riconoscitore sul telefono, mai il cloud

    private fun voiceDown() {
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
                if (t.isBlank()) { notice = Notice(tr(R.string.not_understood)); return }
                query = t
                // Azioni verso te stesso, con sicurezza alta: fatte subito
                val u = understand(t)
                val top = u.top
                if (u.confident && top != null && !top.toOthers) run(top, 0) else screen = Screen.INTENT
            }
            override fun onError(error: Int) {
                voice = voice.copy(listening = false, partial = "")
                notice = when (error) {
                    12, 13 -> Notice(tr(R.string.voice_no_lang))
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Notice(tr(R.string.not_understood))
                    SpeechRecognizer.ERROR_CLIENT -> null
                    else -> Notice(tr(R.string.mic_unavailable))
                }
                if (screen == Screen.HOME) notice?.let { android.widget.Toast.makeText(this@MainActivity, it.text, android.widget.Toast.LENGTH_SHORT).show() }
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            // Si ascolta finché tieni premuto: le pause non chiudono l'ascolto
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 60_000L)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 60_000L)
        if (Build.VERSION.SDK_INT >= 33) {
            // Nomi insoliti di persone, app e dispositivi di casa capiti meglio
            val bias = ArrayList<String>()
            bias += app.people.all().filter { it.starred || app.people.isImportant(it.name) }.map { it.name }
            bias += proposals.apps.map { it.label }
            bias += app.ha.cached().filter { it.controllable }.map { it.name }
            i.putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(bias.distinct().take(100)))
        }
        voiceDownAt = System.currentTimeMillis()
        voice = voice.copy(listening = true, partial = "")
        query = ""
        runCatching { r.startListening(i) }.onFailure { voice = voice.copy(listening = false) }
    }

    private var voiceDownAt = 0L

    private fun hint(text: String) {
        if (screen == Screen.HOME || screen == Screen.SORDINA) android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_SHORT).show()
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
    override fun pickWallpaper() {
        runCatching { wallpaperLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }
    override fun backdropChanged() {
        accentPref = prefs.accent
        glassPref = prefs.glass
        val m = prefs.wallpaperMode
        if (m == 1) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        lifecycleScope.launch { backdrop = withContext(Dispatchers.IO) { Backdrops.load(this@MainActivity, m, prefs.wallpaperPreset) } }
    }
    private val notifIcons = HashMap<String, androidx.compose.ui.graphics.ImageBitmap?>()

    /** L'icona dell'app di una notifica (anche app di sistema senza voce nel cassetto). */
    private fun notifIcon(pkg: String): androidx.compose.ui.graphics.ImageBitmap? = notifIcons.getOrPut(pkg) {
        runCatching {
            val px = (40 * resources.displayMetrics.density).toInt()
            packageManager.getApplicationIcon(pkg).toBitmap(px, px).asImageBitmap()
        }.getOrNull()
    }

    /** Lascia passare un contatto o un'app: per qualche ora, oppure sempre (importante / livello Subito). */
    private fun letPass(h: Held, hours: Int?) {
        val k = h.groupKey
        when {
            hours != null -> prefs.passUntil = prefs.passUntil + (k to System.currentTimeMillis() + hours * 3_600_000L)
            k.startsWith("a:") -> prefs.setAppLevel(h.pkg, Level.SUBITO)
            h.conversation == null -> prefs.importantNames = prefs.importantNames + k.removePrefix("p:")
            else -> prefs.passUntil = prefs.passUntil + (k to Long.MAX_VALUE)
        }
        SordinaListener.releaseGroup(this, k)
        changed()
        hint(if (hours == null) tr(R.string.passes_always_x, h.groupTitle) else tr(R.string.passes_for_x, h.groupTitle, trn(R.plurals.n_hours, hours)))
    }

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
        // In Casa "indietro" non porta da nessuna parte: lo si ignora, così Android non mostra l'animazione di uscita
        BackHandler(enabled = screen == Screen.HOME) { }
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
                when {
                    a.key in p -> prefs.pinned = p - a.key
                    // Al massimo 4 fisse su 6: le altre due restano a chi impara dalle tue abitudini
                    p.size >= Prefs.MAX_PINNED -> {
                        notice = Notice(tr(R.string.pins_full, Prefs.MAX_PINNED, a.label))
                        if (prefs.haptics) window.decorView.performHapticFeedback(
                            if (Build.VERSION.SDK_INT >= 30) android.view.HapticFeedbackConstants.REJECT else android.view.HapticFeedbackConstants.LONG_PRESS,
                        )
                        return@ProposalActions
                    }
                    else -> prefs.pinned = p + a.key
                }
                prefsVersion++
                refreshProposals(force = true)
            },
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
            why = { a ->
                when {
                    a.key in prefs.pinned -> tr(R.string.why_pinned)
                    (proposals.usage[a.packageName] ?: 0.0) > 0.3 -> tr(R.string.why_time)
                    else -> tr(R.string.why_used)
                }
            },
        )
        LaunchedEffect(screen) {
            if (screen == Screen.INTENT) refreshProposals(force = false)
            if (screen == Screen.HOME) refreshExtras()
        }
        val feelNow = LocalFeel.current
        val bare = (screen == Screen.HOME || screen == Screen.INTENT) && backdrop.active
        Box(if (bare) Modifier.fillMaxSize() else Modifier.fillMaxSize().paper()) {
            if (bare) backdrop.image?.let { img ->
                androidx.compose.foundation.Image(img, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            }
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    // Verso l'alto si sale (Casa → ricerca → app), tornando si scende; spente le animazioni, si cambia e basta
                    val up = targetState.ordinal > initialState.ordinal
                    val d = if (up) 1 else -1
                    if (!feelNow.animations) androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                    else (fadeIn(tween(240, delayMillis = 40)) + androidx.compose.animation.slideInVertically(tween(320, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it / 14 * d }) togetherWith
                        (fadeOut(tween(140)) + androidx.compose.animation.slideOutVertically(tween(220)) { -it / 24 * d })
                },
                label = "screen",
            ) { s ->
                when (s) {
                    Screen.HOME -> {
                        val waiting = groups.sumOf { g -> g.sumOf { it.count } }
                        val ms = now.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                        val cards = remember(signalCards, music, events, habitCards, doneHabits, now, rainAt, birthdays) {
                            (signalCards.values + listOfNotNull(music, rainCard(ms)) + events.map { eventCard(it, ms) } +
                                (habitCards + birthdayCards()).filter { it.key !in doneHabits })
                                .filter { it.expiresAt > ms }
                                .sortedWith(compareBy<Card> { it.kind.priority }.thenByDescending { it.time })
                                .take(6)
                        }
                        val until = remember(now, prefsVersion) { tr(R.string.until_time, app.moments.nextSummary().format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))) }
                        HomeScreen(
                            state = HomeState(
                                waiting = waiting,
                                until = until,
                                moment = if (moment == Moment.NONE) null else moment.label,
                                cards = cards,
                                agenda = remember(agendaEvents, moment, now) { agendaRows(waiting) },
                                weather = weatherNow,
                                listening = voice.listening,
                                partial = voice.partial,
                                topDark = backdrop.topDark,
                                bottomDark = backdrop.bottomDark,
                                translucent = backdrop.active,
                                glass = backdrop.active && glassPref,
                            ),
                            groups = groups,
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
                        )
                    }
                    Screen.INTENT -> IntentScreen(
                        showIcons = remember(prefsVersion) { prefs.appIcons },
                        topDark = backdrop.topDark,
                        glass = backdrop.active && glassPref,
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
                        icon = if (remember(prefsVersion) { prefs.appIcons }) { a -> catalog.icon(a, (30 * resources.displayMetrics.density).toInt()) } else null,
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
                        backdrop = backdrop.mode,
                        backdropAccent = backdrop.accent,
                        accent = accentPref,
                        onFamily = { family = it; prefs.family = it },
                        onMode = { mode = it; prefs.mode = it },
                        onWipe = ::wipe,
                        ops = this@MainActivity,
                        onBack = { screen = Screen.HOME },
                    )
                    Screen.BRAKE -> brakeTarget?.let { a ->
                        BrakeScreen(
                            app = a,
                            icon = remember(a.key) { catalog.icon(a, (26 * resources.displayMetrics.density).toInt()) },
                            today = remember(a.key) { app.habits.appToday(a.packageName) },
                            seconds = prefs.brakeSeconds,
                            streak = remember(a.key) { app.habits.streak() },
                            onOpen = { habit ->
                                app.habits.logBrake(a.packageName, habit)
                                catalog.launch(a)
                            },
                            onSkip = { app.habits.minutesPerVisit(a.packageName).also { app.habits.logSkip(a.packageName, it) } },
                            onDone = { screen = Screen.HOME },
                        )
                    } ?: run { screen = Screen.HOME }
                    Screen.SORDINA -> SordinaScreen(
                        groups = groups,
                        until = remember(now, prefsVersion) { tr(R.string.until_time, app.moments.nextSummary().format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))) },
                        overrides = remember(prefsVersion) { prefs.categoryOverrides },
                        braked = remember(prefsVersion) { prefs.braked },
                        brakedStay = remember(prefsVersion) { prefs.brakedStay },
                        order = remember(prefsVersion) { when (prefs.sordinaOrder) { 1 -> null; 2 -> Categories.custom(prefs.categoryOrder); else -> Categories.AUTO } },
                        icon = ::notifIcon,
                        onOpen = { h -> SordinaListener.open(this@MainActivity, h.key) },
                        onRead = { k -> SordinaListener.readGroup(this@MainActivity, k) },
                        onReadAll = { SordinaListener.readAll(this@MainActivity); screen = Screen.HOME },
                        onLetPass = ::letPass,
                        onMove = { k, cat -> prefs.categoryOverrides = prefs.categoryOverrides + (k to cat.name); changed() },
                        onBack = { screen = Screen.HOME },
                    )

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
