package app.sordina.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow

/**
 * Le sei proposte: ciò che di solito apri a quest'ora, in questo tipo di giorno.
 * Tutto calcolato sul telefono dalla cronologia di utilizzo di Android.
 * Il luogo (Wi-Fi, auto, Home Assistant) arriverà in una versione successiva.
 */
class Proposals(private val context: Context) {

    data class Result(
        val apps: List<AppEntry>,
        /** Punteggio d'uso complessivo per pacchetto, usato anche per ordinare la ricerca */
        val usage: Map<String, Double>,
        val needsPermission: Boolean,
        /** Fascia di mezz'ora: le proposte non cambiano finché non cambia la fascia */
        val slot: Long,
    )

    fun hasPermission(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private class Open(val pkg: String, val time: Long)

    private fun homePackages(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }.toSet()
    }

    private fun readOpens(now: Long): List<Open> {
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val events = usm.queryEvents(now - DAYS * DAY_MS, now) ?: return emptyList()
        val skip = homePackages() + context.packageName
        val last = HashMap<String, Long>()
        val opens = ArrayList<Open>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            @Suppress("DEPRECATION")
            if (e.eventType != UsageEvents.Event.MOVE_TO_FOREGROUND) continue
            val pkg = e.packageName ?: continue
            if (pkg in skip) continue
            // Un'apertura sola se rientri nella stessa app entro un minuto
            val prev = last[pkg]
            last[pkg] = e.timeStamp
            if (prev != null && e.timeStamp - prev < 60_000) continue
            opens += Open(pkg, e.timeStamp)
        }
        return opens
    }

    fun compute(apps: List<AppEntry>, prefs: Prefs, nowMs: Long = System.currentTimeMillis()): Result {
        val slot = slotOf(nowMs)
        if (!hasPermission()) return Result(fallback(apps, prefs), emptyMap(), true, slot)

        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), zone)
        val nowHour = now.hour + now.minute / 60.0
        val nowWeekend = now.dayOfWeek.isWeekend()

        val contextual = HashMap<String, Double>()
        val overall = HashMap<String, Double>()
        val opens = readOpens(nowMs)
        // Appena usata e chiusa (ultimi 2 minuti): non la riproporre subito
        val justUsed = opens.filter { nowMs - it.time < 2 * 60_000 }.map { it.pkg }.toSet()
        for (o in opens) {
            val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(o.time), zone)
            val h = t.hour + t.minute / 60.0
            val dh = abs(h - nowHour).let { min(it, 24 - it) }
            val ageDays = (nowMs - o.time) / DAY_MS.toDouble()
            val recency = 0.5.pow(ageDays / HALF_LIFE_DAYS)
            val sameDayType = t.dayOfWeek.isWeekend() == nowWeekend
            val timeWeight = exp(-(dh * dh) / (2 * SIGMA_H * SIGMA_H))
            val dayWeight = if (sameDayType) 1.0 else 0.35
            contextual.merge(o.pkg, timeWeight * dayWeight * recency, Double::plus)
            overall.merge(o.pkg, recency, Double::plus)
        }

        val byPackage = apps.filter { !it.isWork }.groupBy { it.packageName }.mapValues { it.value.first() }
        val pinned = prefs.pinned.mapNotNull { k -> apps.firstOrNull { it.key == k } }
        val excluded = prefs.excluded
        val braked = prefs.braked
        val learned = contextual.entries
            .filter { it.value >= MIN_SCORE }
            .sortedByDescending { it.value }
            .mapNotNull { byPackage[it.key] }
            .filter { it.key !in excluded && it.packageName !in braked && it.packageName !in justUsed && pinned.none { p -> p.key == it.key } }

        val result = (pinned + learned).distinctBy { it.key }.take(COUNT)
        val filled = if (result.size < COUNT) {
            // Poca storia: completa con le più usate in assoluto
            val extra = overall.entries.sortedByDescending { it.value }
                .mapNotNull { byPackage[it.key] }
                .filter { e -> result.none { it.key == e.key } && e.key !in excluded && e.packageName !in braked && e.packageName !in justUsed }
            (result + extra).take(COUNT)
        } else result
        return Result(filled, overall, false, slot)
    }

    private fun fallback(apps: List<AppEntry>, prefs: Prefs): List<AppEntry> =
        prefs.pinned.mapNotNull { k -> apps.firstOrNull { it.key == k } }

    private fun DayOfWeek.isWeekend() = this == DayOfWeek.SATURDAY || this == DayOfWeek.SUNDAY

    companion object {
        const val COUNT = 6
        private const val DAYS = 28L
        private const val DAY_MS = 86_400_000L
        private const val HALF_LIFE_DAYS = 14.0
        private const val SIGMA_H = 1.0
        private const val MIN_SCORE = 0.3

        fun slotOf(ms: Long): Long = ms / (30 * 60_000L)
    }
}
