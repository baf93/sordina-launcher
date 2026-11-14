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


    /** Solo nella versione di prova: mostra una Casa a scelta, per vederla senza guidare o aspettare la sera */
    var preview: Scene? = null

    fun scene(t: LocalDateTime = LocalDateTime.now()): Scene {
        // Cominciano all'ora che hai scelto. La Sera finisce alle 6; la Notte alla prima sveglia (vedi [night])
        val min = t.hour * 60 + t.minute
        preview?.let { return it }
        val morningOver = min < NOON && prefs.nightDone == t.toLocalDate().toEpochDay()
        return when {
            prefs.homeCar && isCar() -> Scene.CAR
            prefs.homeNight && night(t, min) -> Scene.NIGHT
            prefs.homeEvening && !morningOver && inWindow(min, prefs.eveningFrom, MORNING) -> Scene.EVENING
            else -> Scene.DAY
        }
    }

    /**
     * La Notte finisce quando suona la prima sveglia (anche dopo le 6), alle 6 se non c'è una sveglia,
     * oppure quando togli la sveglia prima che suoni, se manca meno di un'ora o sono già le 6.
     * Finita, per quella mattina non torna (neanche con la sveglia rimandata).
     */
    private fun night(t: LocalDateTime, min: Int): Boolean {
        // La sera: dall'ora scelta a mezzanotte
        if (min >= NOON) return inWindow(min, prefs.nightFrom, MORNING)
        val today = t.toLocalDate().toEpochDay()
        if (prefs.nightDone == today) return false
        val now = millis(t)
        val alarm = morningAlarm(t)
        val seen = prefs.nightAlarm.takeIf { it > 0 && java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay() == today }
        if (alarm != null && alarm != prefs.nightAlarm) prefs.nightAlarm = alarm
        val done = when {
            seen != null && now >= seen -> true                      // la prima sveglia ha suonato
            alarm == null && seen != null && seen - now <= HOUR -> true // tolta a meno di un'ora
            alarm == null && min >= MORNING -> true                  // senza sveglia: alle 6
            else -> false
        }
        if (done) { prefs.nightDone = today; return false }
        return (alarm != null && min >= MORNING) || inWindow(min, prefs.nightFrom, MORNING)
    }

    /** La prossima sveglia di un'app orologio, se suona oggi prima di mezzogiorno. */
    private fun morningAlarm(t: LocalDateTime): Long? = runCatching {
        val info = context.getSystemService(android.app.AlarmManager::class.java)?.nextAlarmClock ?: return@runCatching null
        val owner = info.showIntent?.creatorPackage
        val clock = context.packageManager.resolveActivity(android.content.Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS), 0)?.activityInfo?.packageName
        if (owner != null && owner !in CardDetector.CLOCK_PKGS && owner != clock) return@runCatching null
        val at = java.time.Instant.ofEpochMilli(info.triggerTime).atZone(ZoneId.systemDefault()).toLocalDateTime()
        info.triggerTime.takeIf { at.toLocalDate() == t.toLocalDate() && at.hour < 12 }
    }.getOrNull()

    /** Sordina a tempo, accesa da te: fino a quando, o null. */
    fun focusUntil(now: Long = System.currentTimeMillis()): Long? =
        prefs.quietUntil.takeIf { prefs.quietOwned && !prefs.quietCar && it > now && androidQuiet() }

    /** "Solo l'importante fino alle 15:45 · tocca per finire" o "Modalità attiva · solo l'importante". */
    fun phrase(m: Moment = current()): String =
        focusUntil()?.let { tr(R.string.moment_focus_until, clock(it)) } ?: m.phrase

    private fun clock(ms: Long): String = java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))

    companion object {
        /** Le 6: finiscono Sera e Notte */
        const val MORNING = 6 * 60
        private const val NOON = 12 * 60
        private const val HOUR = 3_600_000L
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
