package app.sordina.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import java.time.LocalDate
import java.time.ZoneId

/** Quello che serve all'attrito e allo specchio serale. Solo conteggi di oggi, sul telefono. */
class Habits(private val context: Context) {
    private val sp = context.getSharedPreferences("habits", Context.MODE_PRIVATE)

    private fun todayKey() = "brake_" + LocalDate.now()

    /** Un'apertura di un'app da frenare: con un motivo o per abitudine. */
    fun logBrake(pkg: String, habit: Boolean) {
        val k = todayKey()
        val v = sp.getString(k, "").orEmpty()
        val entry = "$pkg:${if (habit) 1 else 0}"
        // Tieni solo oggi
        val e = sp.edit()
        sp.all.keys.filter { it.startsWith("brake_") && it != k }.forEach { e.remove(it) }
        e.putString(k, if (v.isEmpty()) entry else "$v|$entry").apply()
    }

    /** (pacchetto, per abitudine) di oggi. */
    fun brakesToday(): List<Pair<String, Boolean>> =
        sp.getString(todayKey(), "").orEmpty().split('|').filter { it.contains(':') }
            .map { it.substringBefore(':') to (it.substringAfter(':') == "1") }

    fun opensToday(pkg: String) = brakesToday().count { it.first == pkg }

    data class Day(val unlocks: Int?, val minutes: Map<String, Int>)

    /** Sblocchi e minuti in primo piano da mezzanotte (serve l'accesso all'utilizzo). */
    fun today(): Day {
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return Day(null, emptyMap())
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        var unlocks = 0
        val fg = HashMap<String, Long>()
        val open = HashMap<String, Long>()
        val ok = runCatching {
            val ev = usm.queryEvents(start, now)
            val e = UsageEvents.Event()
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e)
                when (e.eventType) {
                    KEYGUARD_HIDDEN -> unlocks++
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> open[e.packageName] = e.timeStamp
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> open.remove(e.packageName)?.let { s ->
                        fg[e.packageName] = (fg[e.packageName] ?: 0) + (e.timeStamp - s)
                    }
                }
            }
            open.forEach { (p, s) -> fg[p] = (fg[p] ?: 0) + (now - s) }
        }.isSuccess
        if (!ok) return Day(null, emptyMap())
        return Day(if (Build.VERSION.SDK_INT >= 28) unlocks else null, fg.mapValues { (it.value / 60_000).toInt() })
    }

    companion object {
        private const val KEYGUARD_HIDDEN = 18
    }
}
