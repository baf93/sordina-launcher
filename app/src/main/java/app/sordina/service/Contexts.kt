package app.sordina.service

import app.sordina.data.Tally
import android.Manifest
import android.app.UiModeManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import app.sordina.SordinaApp
import app.sordina.data.ACTION_CAR_CONNECTION
import app.sordina.data.androidAuto
import java.time.LocalTime

/**
 * Osserva i momenti (Wi-Fi, auto, cuffie, in carica la sera) e la musica.
 * Registra soltanto: non esegue niente.
 */
class Contexts(private val service: SordinaListener) {
    private val app = SordinaApp.of(service)
    private val main = Handler(Looper.getMainLooper())
    private var receiver: BroadcastReceiver? = null
    private var carReceiver: BroadcastReceiver? = null
    private var sessions: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private val callbacks = HashMap<MediaController, MediaController.Callback>()
    private val labels = HashMap<String, String?>()

    fun start() {
        // Com'è adesso, per il cervello: i cambi arriveranno dai ricevitori. Cuffie, carica e Bluetooth il cervello
        // li legge quando apri Casa; il luogo non più: niente ascolto in background solo per i suggerimenti
        runCatching { app.brain.onState("car", app.moments.isCar() || androidAuto(service)) }
        runCatching { startReceiver() }
        runCatching { startMusic() }
    }

    fun stop() {
        receiver?.let { runCatching { service.unregisterReceiver(it) } }
        carReceiver?.let { runCatching { service.unregisterReceiver(it) } }
        sessions?.let { runCatching { service.getSystemService(MediaSessionManager::class.java).removeOnActiveSessionsChangedListener(it) } }
        callbacks.forEach { (c, cb) -> runCatching { c.unregisterCallback(cb) } }
        callbacks.clear()
    }

    private fun startReceiver() {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                when (i.action) {
                    UiModeManager.ACTION_ENTER_CAR_MODE -> { Tally.onCar(); app.brain.onState("car", true); Scheduler.refresh(c) }
                    UiModeManager.ACTION_EXIT_CAR_MODE -> { Tally.onCarExit(c); app.brain.onState("car", false); Scheduler.refresh(c) }
                    // Schermo acceso: prima che tu guardi, le posticipate aggiornate (un messaggio urgente
                    // arrivato in una chat in sordina) tornano; e il freno ricomincia a guardare, se serve
                    Intent.ACTION_SCREEN_ON -> { SordinaListener.sync(); BrakeGuard.screenOn(c, app.prefs) }
                    // Sbloccato: quello che è scaduto mentre dormiva va rimesso a posto subito, non al prossimo giro
                    Intent.ACTION_USER_PRESENT -> SordinaListener.sync(force = true)
                }
            }
        }
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(UiModeManager.ACTION_ENTER_CAR_MODE)
            addAction(UiModeManager.ACTION_EXIT_CAR_MODE)
        }
        if (Build.VERSION.SDK_INT >= 33) service.registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED) else service.registerReceiver(r, f)
        receiver = r
        // Android Auto: lo annuncia un'altra app, serve un ricevitore esportato
        val car = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val on = androidAuto(c, force = true)
                app.brain.onState("car", on)
                if (on) Tally.onCar() else Tally.onCarExit(c)
                Scheduler.refresh(c)
            }
        }
        val cf = IntentFilter(ACTION_CAR_CONNECTION)
        // Il messaggio arriva da Android Auto (un'altra app): va dichiarato esportato
        androidx.core.content.ContextCompat.registerReceiver(service, car, cf, androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
        carReceiver = car
    }

    private fun startMusic() {
        val msm = service.getSystemService(MediaSessionManager::class.java)
        val me = ComponentName(service, SordinaListener::class.java)
        val l = MediaSessionManager.OnActiveSessionsChangedListener { list -> track(list.orEmpty()) }
        msm.addOnActiveSessionsChangedListener(l, me, main)
        sessions = l
        track(msm.getActiveSessions(me))
    }

    private fun track(list: List<MediaController>) {
        val keep = list.map { it.sessionToken }.toSet()
        callbacks.keys.filter { it.sessionToken !in keep }.forEach { c -> callbacks.remove(c)?.let { runCatching { c.unregisterCallback(it) } } }
        for (c in list) if (callbacks.keys.none { it.sessionToken == c.sessionToken }) {
            val cb = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = pick()
                override fun onMetadataChanged(metadata: MediaMetadata?) = pick()
                override fun onSessionDestroyed() {
                    callbacks.keys.firstOrNull { it.sessionToken == c.sessionToken }?.let { callbacks.remove(it) }
                    pick()
                }
            }
            c.registerCallback(cb, main)
            callbacks[c] = cb
        }
        pick()
    }

    private fun pick() {
        val all = callbacks.keys.toList()
        fun playing(c: MediaController) = c.playbackState?.state.let { it == PlaybackState.STATE_PLAYING || it == PlaybackState.STATE_BUFFERING }
        val c = all.firstOrNull { playing(it) } ?: all.firstOrNull { it.metadata != null && it.playbackState?.state == PlaybackState.STATE_PAUSED }
        if (c == null) { app.signals.setMusic(null, null); return }
        // Il nome dell'app si chiede ad Android una volta sola: lo stato della musica cambia spesso
        val label = c.packageName?.let { pkg ->
            labels.getOrPut(pkg) {
                runCatching { service.packageManager.getApplicationLabel(service.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
            }
        }
        app.signals.setMusic(c, label)
    }
}
