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
        e.putString(k, if (v.isEmpty()) entry else "$v|$entry")
        // Le aperture dell'ultima ora, con l'ora: l'attesa cresce se ci torni subito
        val now = System.currentTimeMillis()
        val recent = (recentOpens(pkg, now) + now).joinToString(",")
        e.putString("recent_$pkg", recent).apply()
    }

    private fun recentOpens(pkg: String, now: Long): List<Long> =
        sp.getString("recent_$pkg", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }.filter { now - it < 3_600_000 }

    /** Quante volte hai aperto comunque quest'app nell'ultima ora. */
    fun opensLastHour(pkg: String): Int = recentOpens(pkg, System.currentTimeMillis()).size

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

    /** Aperture e minuti di oggi per una sola app (tutte le aperture, non solo da Sordina). null senza accesso all'utilizzo. */
    fun appToday(pkg: String): Pair<Int, Int>? {
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        var opens = 0
        var fg = 0L
        var since: Long? = null
        var lastOpen = 0L
        return runCatching {
            val ev = usm.queryEvents(start, now)
            val e = UsageEvents.Event()
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e)
                if (e.packageName != pkg) continue
                when (e.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        // Un'apertura sola se rientri entro un minuto
                        if (e.timeStamp - lastOpen > 60_000) opens++
                        lastOpen = e.timeStamp
                        if (since == null) since = e.timeStamp
                    }
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        since?.let { fg += e.timeStamp - it }
                        since = null
                        lastOpen = e.timeStamp
                    }
                }
            }
            since?.let { fg += now - it }
            opens to (fg / 60_000).toInt()
        }.getOrNull()
    }

    // Le rinunce: ogni "Lascia stare" davanti a un'app frenata. Restano 60 giorni, solo qui.

    data class Skip(val day: LocalDate, val pkg: String, val minutes: Int)

    private fun skips(): List<Skip> = sp.getString("skips", "").orEmpty().split('|').mapNotNull { s ->
        val p = s.split(',')
        if (p.size != 3) null else runCatching { Skip(LocalDate.parse(p[0]), p[1], p[2].toInt()) }.getOrNull()
    }

    /** Minuti che una rinuncia fa tornare: la durata media di una visita a quell'app oggi (tra 2 e 30). */
    fun minutesPerVisit(pkg: String): Int {
        val (opens, minutes) = appToday(pkg) ?: return 5
        return if (opens == 0) 5 else (minutes / opens).coerceIn(2, 30)
    }

    fun logSkip(pkg: String, minutes: Int) {
        val keep = LocalDate.now().minusDays(60)
        val list = skips().filter { it.day.isAfter(keep) } + Skip(LocalDate.now(), pkg, minutes)
        sp.edit().putString("skips", list.joinToString("|") { "${it.day},${it.pkg},${it.minutes}" }).apply()
    }

    data class Streak(val today: Int, val days: Int, val weekMinutes: Int, val week: List<Boolean>)

    /** Rinunce di oggi, giorni di fila con almeno una rinuncia, minuti ripresi negli ultimi 7 giorni, la settimana (lunedì → domenica). */
    fun streak(): Streak {
        val all = skips()
        val today = LocalDate.now()
        val days = all.map { it.day }.toSet()
        var d = if (today in days) today else today.minusDays(1)
        var run = 0
        while (d in days) { run++; d = d.minusDays(1) }
        val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
        return Streak(
            today = all.count { it.day == today },
            days = run,
            weekMinutes = all.filter { !it.day.isBefore(today.minusDays(6)) }.sumOf { it.minutes },
            week = (0..6).map { weekStart.plusDays(it.toLong()) in days },
        )
    }

    companion object {
        private const val KEYGUARD_HIDDEN = 18
    }
}
