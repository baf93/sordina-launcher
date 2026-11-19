package app.sordina

import app.sordina.ui.calendar.CalStart
import app.sordina.ui.home.DayRecap
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

/* Le scene di Casa e l'agenda: cosa mostrare adesso, sveglia, meteo, impegni. */

internal fun MainActivity.refreshExtras() {
    // Tornando a Casa arrivano insieme onResume e il cambio di schermata: basta un giro solo
    val t = System.currentTimeMillis()
    if (t - extrasAt < 2_000) return
    extrasAt = t
    val current = apps
    watchCalendar()
    lifecycleScope.launch(Dispatchers.IO) {
        // La copia di ogni giorno per il backup di Google (una scrittura al giorno al massimo)
        Backup.daily(this@refreshExtras, prefs.onboarded)
        // Il calendario cambia di rado: si rilegge se Android dice che è cambiato, o ogni qualche minuto (gli eventi passano)
        if (t - calendarAt > MainActivity.CALENDAR_GAP) {
            calendarAt = t
            // I calendari scelti per Casa, del telefono e di casa, tutti insieme: niente unioni, se un impegno è in due calendari si vede due volte
            val home = app.ha.calendarEvents()
            events = (app.calendars.upcoming(this@refreshExtras) + home.filter { it.end > t && it.begin - t <= 2 * 3600_000L }).sortedBy { it.begin }
            val all = (app.calendars.agenda(this@refreshExtras) + home).sortedBy { it.begin }
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
internal fun MainActivity.sceneExtras(s: Scene) {
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
        val min = d.screen
        if (min <= 0) null else {
            // A schermo solo icone e numeri; la frase intera resta per chi usa TalkBack
            val t = if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"
            val short = if (min >= 60) "${min / 60}h ${min % 60}m" else "${min}m"
            DayRecap(short, d.unlocks, d.unlocks?.let { tr(R.string.scene_recap, t, it) } ?: tr(R.string.scene_recap_time, t))
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

/** Fra le app candidate, quella che usi di più (per i pulsanti in auto quando non ne hai scelta una). */
internal fun MainActivity.favourite(list: List<AppEntry>): String? {
    if (list.isEmpty()) return null
    val use = runCatching { app.brain.usage() }.getOrDefault(emptyMap())
    return list.maxByOrNull { use[it.packageName] ?: 0.0 }?.packageName
}

internal fun MainActivity.hm(min: Int): String = java.time.LocalTime.of(min / 60 % 24, min % 60)
    .format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))

/**
 * La prossima sveglia di domani, se suona entro un giorno (quali valgono: [app.sordina.data.Moments.clockAlarm]).
 */
internal fun MainActivity.nextAlarm(): String? = nextAlarmAt()?.let {
    java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))
}

internal fun MainActivity.nextAlarmAt(): Long? {
    val t = app.moments.clockAlarm() ?: return null
    if (t - System.currentTimeMillis() > 24 * 3_600_000L) return null
    val at = java.time.Instant.ofEpochMilli(t).atZone(java.time.ZoneId.systemDefault())
    // Sta sotto "Domani": una sveglia di stasera non è di domani (dopo mezzanotte invece sì)
    val now = java.time.LocalDateTime.now()
    if (at.toLocalDate() == now.toLocalDate() && now.hour >= 12) return null
    return t
}

internal fun MainActivity.sceneLine(s: Scene): String? = when (s) {
    Scene.EVENING -> null
    // La modalità attiva la dice già la sordina (in basso, con la sua faccia): qui solo l'orario del silenzio che hai chiesto tu
    Scene.NIGHT -> app.moments.focusUntil()?.let { tr(R.string.moment_focus_until, app.moments.clock(it)) }
    // In auto basta l'ora: che sei in auto lo vedi, cosa passa l'hai scelto tu
    Scene.CAR -> null
    Scene.DAY -> null
}

internal fun MainActivity.onScene(key: String) {
    when {
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

/** "Adesso", "Tra 12 min", "Alle 16:00" */
internal fun MainActivity.startLabel(begin: Long, now: Long): String {
    val min = ((begin - now) / 60_000).toInt()
    return when {
        min <= 0 -> tr(R.string.now_cap)
        min < 60 -> tr(R.string.in_min, min)
        else -> tr(R.string.at_time, java.time.Instant.ofEpochMilli(begin).atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)))
    }
}

/** Apre il lettore: prima la sua schermata "in riproduzione", altrimenti l'app. */
internal fun MainActivity.openMusic() {
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
internal fun MainActivity.nextLabel(e: Upcoming): String {
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
internal fun MainActivity.nextEvent(): AgendaRow? {
    val ms = System.currentTimeMillis()
    val e = agendaEvents.filter { it.end > ms && !it.allDay }.minByOrNull { it.begin } ?: return null
    // Gli impegni del calendario di casa hanno tutti la chiave "ha": si riconoscono anche dal titolo
    val k = agendaKey(e)
    val row = agendaRows(0).firstOrNull { it.key == k && it.text == e.title } ?: return null
    return row.copy(key = e.location?.takeIf { it.isNotBlank() }?.let { "nav:$it" } ?: row.key)
}

/** Cosa apre un impegno di Casa: il calendario di Sordina (sull'impegno, riconosciuto da inizio e titolo), o l'app del suo calendario */
internal fun MainActivity.agendaKey(e: Upcoming): String = when {
    prefs.sordinaCalendar -> "cal:${e.begin}:${e.title}"
    e.link == "ha" -> "ha"
    else -> "event:${e.id}"
}

internal fun MainActivity.agendaRows(waiting: Int): List<AgendaRow> {
    val zone = java.time.ZoneId.systemDefault()
    val today = java.time.LocalDate.now()
    val hm = java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)
    val out = ArrayList<AgendaRow>()
    // Il motore è fermo o rischia di esserlo: lo si dice per primo, un tocco porta dove si sistema
    val timed = ArrayList<Pair<Long, AgendaRow>>()
    for (e in agendaEvents) {
        val key = agendaKey(e)
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
internal fun MainActivity.openHaCalendar() {
    val inApp = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("homeassistant://navigate/calendar"))
        .setPackage(HomeAssistant.COMPANION_PKG).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { startActivity(inApp) }.isSuccess) return
    prefs.haUrl.takeIf { it.isNotBlank() }?.let { start(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(it.trimEnd('/') + "/calendar"))) }
}

internal fun MainActivity.onAgenda(key: String) {
    when {
        key == "health" -> when (health) {
            Health.NO_ACCESS -> openListenerSettings()
            Health.STOPPED -> { SordinaListener.rebind(this); openListenerSettings() }
            Health.BATTERY -> openBatterySettings()
            Health.SAMSUNG -> SamsungSleep.ask()
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
        key == "calendar" -> { calStart = CalStart(); screen = Screen.CALENDAR }
        key.startsWith("cal:") -> key.split(":", limit = 3).let { p ->
            calStart = CalStart(begin = p.getOrNull(1)?.toLongOrNull(), title = p.getOrNull(2)); screen = Screen.CALENDAR
        }
        key.startsWith("event:") -> key.removePrefix("event:").toLongOrNull()?.let {
            start(Intent(Intent.ACTION_VIEW, android.content.ContentUris.withAppendedId(android.provider.CalendarContract.Events.CONTENT_URI, it)))
        }
    }
}

internal fun MainActivity.openWeather() {
    val pkgs = listOf("com.sec.android.daemonapp", "com.google.android.apps.weather", "com.accuweather.android")
    val i = pkgs.firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }
    if (i != null) start(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    else openUrl("https://www.google.com/search?q=meteo")
}
