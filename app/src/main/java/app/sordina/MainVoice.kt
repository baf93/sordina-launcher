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

/* La voce: microfono premuto e lasciato, e le risposte dette nel fumetto. */

/** In auto: un tocco apre l'ascolto, si chiude da solo quando smetti di parlare (o con un altro tocco). */
internal fun MainActivity.voiceTap() {
    if (voice.listening) runCatching { recognizer?.stopListening() } else voiceDown(handsFree = true)
}

internal fun MainActivity.voiceDown(handsFree: Boolean = false) {
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
            // Il riconoscitore propone più trascrizioni, la prima è la sua più probabile: si tiene quella,
            // e si passa alla successiva solo se la prima non porta a niente fuori dal web
            fun useful(x: Understanding) = x.candidates.any { c -> c.kind != Kind.WEB }
            // Le prove non insegnano niente; solo la frase scelta conta come tua
            val tries = heard.take(4).asSequence().mapIndexed { i, h -> Triple(h, understand(h, learn = false), i) }
            val (t, _, index) = tries.firstOrNull { useful(it.second) } ?: tries.first()
            val u = understand(t)
            query = t
            // Azioni verso te stesso, con sicurezza alta: fatte subito
            // (solo dalla trascrizione più probabile: una seconda scelta non fa mai le cose da sola)
            val top = u.top
            if (index == 0 && u.confident && top != null && !top.toOthers) run(top, 0) else screen = Screen.INTENT
            // Dopo che l'azione è partita (la casa che risponde mette già i suoi puntini): niente buco in mezzo
            lifecycleScope.launch { voice = voice.copy(partial = "") }
        }
        override fun onError(error: Int) {
            voiceHandler.removeCallbacks(noSpeech)
            voice = voice.copy(listening = false, partial = "", level = 0f)
            val n = when (error) {
                12, 13 -> Notice(tr(R.string.voice_no_lang))
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Notice(tr(R.string.not_understood))
                SpeechRecognizer.ERROR_CLIENT -> null
                else -> Notice(tr(R.string.mic_unavailable))
            }
            // In Casa lo dice il fumetto; l'avviso della ricerca serve solo se sei nella ricerca (se no resterebbe lì ad aspettarti)
            if (screen == Screen.HOME) n?.let { answer(it.text, ok = false) } else notice = n
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
            .mapNotNull { item -> app.brain.personOf(item)?.name }
        bias += proposals.apps.map { it.label }
        bias += app.ha.cached().filter { it.controllable }.map { it.name }
        i.putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(bias.distinct().take(100)))
    }
    speechHeard = false
    voiceHandler.removeCallbacks(noSpeech)
    if (handsFree) voiceHandler.postDelayed(noSpeech, MainActivity.NO_SPEECH_MILLIS)
    voiceDownAt = System.currentTimeMillis()
    voice = voice.copy(listening = true, partial = "")
    query = ""
    runCatching { r.startListening(i) }.onFailure { voice = voice.copy(listening = false) }
}

internal fun MainActivity.say(text: String) { bubble = Said(text) }

/** Una risposta della sordina a quello che le hai chiesto: in Casa, con le animazioni, sale lei a dirla; se no il fumetto piccolo */
internal fun MainActivity.answer(text: String, ok: Boolean = true) {
    if (screen == Screen.HOME && prefs.animations) { bubble = null; reply = HomeReply(text, ok) } else say(text)
}

internal fun MainActivity.hint(text: String) {
    if (screen == Screen.HOME || screen == Screen.SORDINA) say(text)
    else notice = Notice(text)
}

/** Rilasci: se era solo un tocco, niente ascolto e un suggerimento; altrimenti si chiude e si capisce. */
internal fun MainActivity.voiceUp() {
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
