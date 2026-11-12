package app.sordina.data

import app.sordina.R
import app.sordina.tr

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** I momenti della 0.1: Notte (a orario) e In auto (modalità auto di Android / Android Auto). */
enum class Moment(private val labelRes: Int, private val phraseRes: Int?) {
    NONE(R.string.moment_none, null),
    NIGHT(R.string.moment_night, R.string.moment_night_phrase),
    CAR(R.string.moment_car, R.string.moment_car_phrase),
    ;
    val label: String get() = tr(labelRes)
    val phrase: String get() = phraseRes?.let { tr(it) } ?: ""
}

class Moments(private val context: Context, private val prefs: Prefs) {

    fun isCar(): Boolean {
        if (!prefs.carEnabled) return false
        val ui = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        return ui.currentModeType == Configuration.UI_MODE_TYPE_CAR || androidAuto(context)
    }

    fun isNight(t: LocalDateTime = LocalDateTime.now()): Boolean =
        prefs.nightEnabled && inWindow(t.hour * 60 + t.minute, prefs.nightStart, prefs.nightEnd)

    /** Il momento riconosciuto da solo. Priorità: In auto > Notte. */
    fun automatic(): Moment = when {
        isCar() -> Moment.CAR
        isNight() -> Moment.NIGHT
        else -> Moment.NONE
    }

    /** Il momento in vigore: la scelta a mano vale fino al prossimo cambio automatico. */
    fun current(): Moment {
        val auto = automatic()
        val override = prefs.momentOverride ?: return auto
        if (prefs.momentOverrideBase != auto.name) {
            prefs.momentOverride = null
            prefs.momentOverrideBase = null
            return auto
        }
        return runCatching { Moment.valueOf(override) }.getOrDefault(auto)
    }

    fun setManual(m: Moment?) {
        prefs.momentOverride = m?.name
        prefs.momentOverrideBase = if (m == null) null else automatic().name
    }

    // Orari

    /** Il prossimo riepilogo dopo [now], saltando quelli che cadono di notte. */
    fun nextSummary(now: LocalDateTime = LocalDateTime.now()): LocalDateTime {
        val times = prefs.summaryTimes
        for (d in 0..2) {
            val day = now.toLocalDate().plusDays(d.toLong())
            for (m in times) {
                val t = day.atTime(m / 60, m % 60)
                if (!t.isAfter(now)) continue
                if (prefs.nightEnabled && inWindow(m, prefs.nightStart, prefs.nightEnd)) continue
                return t
            }
        }
        return now.plusHours(6)
    }

    /** L'ultimo riepilogo passato oggi, se c'è (in minuti dalla mezzanotte). */
    fun lastSummaryToday(now: LocalDateTime = LocalDateTime.now()): Int? =
        prefs.summaryTimes.lastOrNull { it <= now.hour * 60 + now.minute }

    /** Prossimo confine di notte (inizio o fine) dopo [now]. */
    fun nextNightEdge(now: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
        if (!prefs.nightEnabled) return null
        val today: LocalDate = now.toLocalDate()
        return listOf(prefs.nightStart, prefs.nightEnd)
            .flatMap { m -> (0..1).map { today.plusDays(it.toLong()).atTime(m / 60, m % 60) } }
            .filter { it.isAfter(now) }
            .minOrNull()
    }

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
