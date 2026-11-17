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

/* Le schede del fumetto: tocco, azioni, chiusura, e le schede di prova. */

internal fun MainActivity.eventCard(e: Upcoming, now: Long): Card {
    val label = startLabel(e.begin, now)
    val actions = buildList {
        e.link?.let { add(CardAction(tr(R.string.join), CardOp.OpenUrl(it))) }
        e.location?.let { add(CardAction(tr(R.string.navigate), CardOp.Navigate(it))) }
        if (isEmpty()) add(CardAction(tr(R.string.open), CardOp.OpenEvent(e.id)))
    }.let { a -> if (scene == Scene.CAR) a.sortedByDescending { it.op is CardOp.Navigate } else a }
    return Card("event:${e.id}:${e.begin}", CardKind.EVENT, label, e.title, e.location, actions, e.begin, e.end,
        bar = if (e.begin <= now) Bar(e.begin, e.end) else null)
}

internal fun MainActivity.cardTap(card: Card) {
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
internal fun MainActivity.dismissCard(card: Card) {
    app.signals.dismiss(card)
    if (card.key.startsWith("plan:")) app.plans.forget(card.key)
}

internal fun MainActivity.cardAction(card: Card, a: CardAction) {
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
            // Un minuto dopo il codice sparisce dagli appunti: subito se Sordina è davanti, se no appena torni a Casa
            // (Android non lascia leggere gli appunti alle app dietro, e non si cancella quello che non è nostro)
            copiedCode = op.text; copiedAt = System.currentTimeMillis()
            window.decorView.postDelayed({ clearCopiedCode() }, COPY_TTL)
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
            // Chi riceve qualcosa (chiamata, messaggio) lo sceglie sempre l'utente: da una scheda mai da soli
            if (u.confident && top != null && !top.toOthers) run(top, 0) else screen = Screen.INTENT
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

/** Sordina non funziona (o rischia): una scheda in cima, gentile, con un tasto solo che porta dove si sistema. */
/** Solo nella versione di prova: un fumetto finto per vedere com'è, senza aspettare che succeda davvero. */
internal fun MainActivity.demoCard(k: String): Card? {
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

internal fun MainActivity.healthCard(h: Health): Card = when (h) {
    Health.NO_ACCESS -> Card("health:access", CardKind.HEALTH, tr(R.string.health_card_label), tr(R.string.health_access_title), tr(R.string.health_access_sub),
        listOf(CardAction(tr(R.string.health_access_fix), CardOp.Fix)))
    Health.STOPPED -> Card("health:stopped", CardKind.HEALTH, tr(R.string.health_card_label), tr(R.string.health_stopped_title), tr(R.string.health_stopped_sub),
        listOf(CardAction(tr(R.string.health_stopped_fix), CardOp.Fix)))
    Health.SAMSUNG -> Card("health:samsung", CardKind.HEALTH, tr(R.string.health_card_label), tr(R.string.health_samsung_title), tr(R.string.health_samsung_sub),
        listOf(CardAction(tr(R.string.health_samsung_fix), CardOp.Fix)))
    Health.BATTERY -> Card("health:battery", CardKind.HEALTH, tr(R.string.health_card_label), tr(R.string.health_battery_title), tr(R.string.health_battery_sub),
        listOf(CardAction(tr(R.string.health_battery_fix), CardOp.Fix)))
}

/** Toglie il codice copiato dagli appunti se è passato il minuto e ci sono ancora loro. */
internal fun MainActivity.clearCopiedCode() {
    val code = copiedCode ?: return
    if (System.currentTimeMillis() - copiedAt < COPY_TTL) return
    runCatching {
        val cm = getSystemService(android.content.ClipboardManager::class.java)
        if (cm.primaryClip?.getItemAt(0)?.text?.toString() == code) {
            if (Build.VERSION.SDK_INT >= 28) cm.clearPrimaryClip() else cm.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
        }
        copiedCode = null
    }
}

/** Quanto resta negli appunti un codice copiato da una scheda */
internal const val COPY_TTL = 60_000L
