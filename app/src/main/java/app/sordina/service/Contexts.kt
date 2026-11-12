package app.sordina.service

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
import android.net.wifi.WifiInfo
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
    private var net: ConnectivityManager.NetworkCallback? = null
    private var audio: AudioDeviceCallback? = null
    private var sessions: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private val callbacks = HashMap<MediaController, MediaController.Callback>()
    private var lastSsid: String? = null

    fun start() {
        runCatching { startReceiver() }
        runCatching { startWifi() }
        runCatching { startAudio() }
        runCatching { startMusic() }
    }

    fun stop() {
        receiver?.let { runCatching { service.unregisterReceiver(it) } }
        carReceiver?.let { runCatching { service.unregisterReceiver(it) } }
        net?.let { runCatching { service.getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) } }
        audio?.let { runCatching { service.getSystemService(AudioManager::class.java).unregisterAudioDeviceCallback(it) } }
        sessions?.let { runCatching { service.getSystemService(MediaSessionManager::class.java).removeOnActiveSessionsChangedListener(it) } }
        callbacks.forEach { (c, cb) -> runCatching { c.unregisterCallback(cb) } }
        callbacks.clear()
    }

    private fun startReceiver() {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                when (i.action) {
                    UiModeManager.ACTION_ENTER_CAR_MODE -> { app.routines.onContext("car"); Scheduler.refresh(c) }
                    UiModeManager.ACTION_EXIT_CAR_MODE -> Scheduler.refresh(c)
                    Intent.ACTION_POWER_CONNECTED -> if (LocalTime.now().hour >= 20) app.routines.onContext("charge")
                }
            }
        }
        val f = IntentFilter().apply {
            addAction(UiModeManager.ACTION_ENTER_CAR_MODE)
            addAction(UiModeManager.ACTION_EXIT_CAR_MODE)
            addAction(Intent.ACTION_POWER_CONNECTED)
        }
        if (Build.VERSION.SDK_INT >= 33) service.registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED) else service.registerReceiver(r, f)
        receiver = r
        // Android Auto: lo annuncia un'altra app, serve un ricevitore esportato
        val car = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (androidAuto(c, force = true)) app.routines.onContext("car")
                Scheduler.refresh(c)
            }
        }
        val cf = IntentFilter(ACTION_CAR_CONNECTION)
        if (Build.VERSION.SDK_INT >= 33) service.registerReceiver(car, cf, Context.RECEIVER_EXPORTED) else service.registerReceiver(car, cf)
        carReceiver = car
    }

    /** Il nome del Wi-Fi si legge solo con il permesso di posizione: senza, niente "A casa". */
    private fun startWifi() {
        if (service.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val cm = service.getSystemService(ConnectivityManager::class.java)
        val req = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        val cb = if (Build.VERSION.SDK_INT >= 31) object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
            override fun onCapabilitiesChanged(n: Network, caps: NetworkCapabilities) = onWifi(caps)
            override fun onLost(n: Network) { lastSsid = null }
        } else object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(n: Network, caps: NetworkCapabilities) = onWifi(caps)
            override fun onLost(n: Network) { lastSsid = null }
        }
        cm.registerNetworkCallback(req, cb)
        net = cb
    }

    private fun onWifi(caps: NetworkCapabilities) {
        val info = caps.transportInfo as? WifiInfo ?: return
        val ssid = info.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" } ?: return
        if (ssid == lastSsid) return
        lastSsid = ssid
        app.routines.onContext("wifi:$ssid")
    }

    private fun startAudio() {
        val am = service.getSystemService(AudioManager::class.java)
        val heads = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_USB_HEADSET,
        ) + if (Build.VERSION.SDK_INT >= 31) setOf(AudioDeviceInfo.TYPE_BLE_HEADSET) else emptySet()
        var first = true
        val cb = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) {
                // La prima chiamata elenca quelli già collegati: non è un momento nuovo
                if (first) { first = false; return }
                if (added.any { it.isSink && it.type in heads }) app.routines.onContext("headset")
            }
        }
        am.registerAudioDeviceCallback(cb, main)
        audio = cb
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
        val label = c?.packageName?.let { pkg ->
            runCatching { service.packageManager.getApplicationLabel(service.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
        }
        app.signals.setMusic(c, label)
    }
}
