package app.sordina.data

import app.sordina.R
import app.sordina.tr

import android.app.NotificationManager
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Il momento lo decide Android: se è accesa una modalità (Non disturbare, Riposo, Guida…)
 * passa solo l'importante. Sordina non ha orari suoi da tenere allineati.
 */
enum class Moment(private val labelRes: Int, private val phraseRes: Int?) {
    NONE(R.string.moment_none, null),
    QUIET(R.string.moment_quiet, R.string.moment_quiet_phrase),
    ;
    val label: String get() = tr(labelRes)
    val phrase: String get() = phraseRes?.let { tr(it) } ?: ""
}

/**
 * Come si presenta Casa adesso. Solo aspetto: cosa passa lo decide il [Moment].
 * Sera: dalle 20. Notte: una modalità di Android accesa, di sera o di notte. Auto: Android Auto o modalità auto.
 */
enum class Scene { DAY, EVENING, NIGHT, CAR }

class Moments(private val context: Context, private val prefs: Prefs) {
    private val nm = context.getSystemService(NotificationManager::class.java)

    /** In auto davvero (modalità auto di Android o Android Auto). */
    fun isCar(): Boolean {
        val ui = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        return ui.currentModeType == Configuration.UI_MODE_TYPE_CAR || androidAuto(context)
    }

    /** Una modalità di Android è accesa (qualunque: Non disturbare, Riposo, Guida, una tua). */
    fun androidQuiet(): Boolean = runCatching {
        val f = nm.currentInterruptionFilter
        f != NotificationManager.INTERRUPTION_FILTER_ALL && f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }.getOrDefault(false)

    fun current(): Moment = if (androidQuiet()) Moment.QUIET else Moment.NONE

    private fun lateHour(t: LocalDateTime) = t.hour >= 20 || t.hour < 7

    fun scene(t: LocalDateTime = LocalDateTime.now()): Scene {
        val quiet = androidQuiet()
        return when {
            prefs.homeCar && isCar() -> Scene.CAR
            prefs.homeNight && quiet && lateHour(t) -> Scene.NIGHT
            prefs.homeEvening && !quiet && t.hour >= 20 -> Scene.EVENING
            else -> Scene.DAY
        }
    }

    /** Sordina a tempo, accesa da te: fino a quando, o null. */
    fun focusUntil(now: Long = System.currentTimeMillis()): Long? =
        prefs.quietUntil.takeIf { prefs.quietOwned && !prefs.quietCar && it > now && androidQuiet() }

    /** "Solo l'importante fino alle 15:45 · tocca per finire" o "Modalità attiva · solo l'importante". */
    fun phrase(m: Moment = current()): String =
        focusUntil()?.let { tr(R.string.moment_focus_until, clock(it)) } ?: m.phrase

    private fun clock(ms: Long): String = java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))

    companion object {
        fun inWindow(min: Int, start: Int, end: Int): Boolean =
            if (start <= end) min in start until end else (min >= start || min < end)

        fun millis(t: LocalDateTime): Long = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        fun fmt(min: Int) = "%d:%02d".format(min / 60, min % 60)
    }
}

/**
 * Android Auto (anche senza "modalità auto"): lo stato che Android Auto pubblica per le app,
 * lo stesso letto da CarConnection di androidx. 0 = non collegato.
 */
@Volatile private var carCache = false
@Volatile private var carAt = 0L

fun androidAuto(context: Context, force: Boolean = false): Boolean {
    val now = System.currentTimeMillis()
    if (!force && now - carAt < 30_000) return carCache
    carCache = runCatching {
        context.contentResolver.query(android.net.Uri.parse("content://androidx.car.app.connection"),
            arrayOf("CarConnectionState"), null, null, null)?.use { c -> c.moveToFirst() && c.getInt(0) != 0 } ?: false
    }.getOrDefault(false)
    carAt = now
    return carCache
}

const val ACTION_CAR_CONNECTION = "androidx.car.app.connection.action.CAR_CONNECTION_UPDATED"
