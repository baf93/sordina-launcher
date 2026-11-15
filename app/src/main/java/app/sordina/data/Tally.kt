package app.sordina.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.SharedPreferences

/**
 * I piccoli conteggi delle imprese (notifiche tenute, viaggi in auto, concentrazioni…). Solo numeri, solo sul telefono.
 * Una scrittura per evento, mai in un giro: niente batteria.
 */
object Tally {
    private var sp: SharedPreferences? = null

    fun init(context: Context) { if (sp == null) sp = context.getSharedPreferences("tally", Context.MODE_PRIVATE) }

    fun get(key: String): Int = sp?.getInt(key, 0) ?: 0
    fun add(key: String, n: Int = 1) { sp?.let { it.edit().putInt(key, it.getInt(key, 0) + n).apply() } }
    fun putInt(key: String, v: Int) { sp?.edit()?.putInt(key, v)?.apply() }
    fun raise(key: String, v: Int) { sp?.let { if (v > it.getInt(key, 0)) it.edit().putInt(key, v).apply() } }
    fun flag(key: String) { sp?.let { if (!it.getBoolean(key, false)) it.edit().putBoolean(key, true).apply() } }
    fun has(key: String): Boolean = sp?.getBoolean(key, false) ?: false
    fun long(key: String): Long = sp?.getLong(key, 0L) ?: 0L
    fun putLong(key: String, v: Long) { sp?.edit()?.putLong(key, v)?.apply() }
    fun text(key: String): String = sp?.getString(key, "").orEmpty()
    fun putText(key: String, v: String) { sp?.edit()?.putString(key, v)?.apply() }

    /** Una volta al giorno al massimo: conta i giorni diversi in cui succede [key] (in "<key>Days"). */
    fun daily(key: String) {
        val today = java.time.LocalDate.now().toString()
        if (text(key + "Last") == today) return
        putText(key + "Last", today); add(key + "Days")
    }

    /** Si cominciano o si smettono di frenare app: la serie conta da quando ce n'è almeno una. */
    fun onBraked(any: Boolean) {
        if (!any) putText("brakeSince", "")
        else if (text("brakeSince").isEmpty()) putText("brakeSince", java.time.LocalDate.now().toString())
    }

    /** Una modalità di Android si accende o si spegne: un'ora di giorno è concentrazione, tre ore lavoro profondo, la notte riposo. */
    fun onMode(quiet: Boolean, now: Long = System.currentTimeMillis()) {
        val since = long("modeSince")
        if (quiet) { if (since == 0L) putLong("modeSince", now); return }
        if (since == 0L) return
        putLong("modeSince", 0L)
        val minutes = (now - since) / 60_000
        val zone = java.time.ZoneId.systemDefault()
        val start = java.time.Instant.ofEpochMilli(since).atZone(zone)
        val end = java.time.Instant.ofEpochMilli(now).atZone(zone)
        // Una notte di riposo: almeno quattro ore che passano per le 3 del mattino
        val three = (if (start.hour < 3) start else start.plusDays(1)).toLocalDate().atTime(3, 0).atZone(zone)
        val night = !three.isBefore(start) && !three.isAfter(end)
        if (start.hour in 7..20 && minutes >= 60) { flag("focus"); add("focusCount") }
        if (start.hour in 7..20 && minutes >= 180) flag("deep")
        if (start.hour in 7..14 && minutes >= 480 && !night) flag("quietDay")
        if (minutes >= 240 && night) {
            val day = three.toLocalDate().toString()
            if (text("restLast") != day) { putText("restLast", day); add("restNights") }
        }
    }

    /** Si entra in auto: un viaggio (non più di uno ogni dieci minuti) e l'ora di partenza. */
    fun onCar(now: Long = System.currentTimeMillis()) {
        if (long("tripStart") == 0L) putLong("tripStart", now)
        if (now - long("carAt") < 10 * 60_000) return
        putLong("carAt", now); add("car")
    }

    /**
     * Si scende dall'auto: una lettura sola dell'utilizzo, per sapere se durante il viaggio il telefono è stato sbloccato.
     * Almeno dieci minuti per contare; un'ora è un viaggio lungo; dopo le 20 (o prima delle 5) è guida notturna.
     */
    fun onCarExit(context: Context, now: Long = System.currentTimeMillis()) {
        val start = long("tripStart")
        if (start == 0L) return
        putLong("tripStart", 0L)
        val minutes = (now - start) / 60_000
        if (minutes < 10) return
        putLong("tripEnd", now)
        if (unlocks(context, start, now) != 0) return
        add("handsFree")
        if (minutes >= 60) flag("longDrive")
        val hour = java.time.Instant.ofEpochMilli(start).atZone(java.time.ZoneId.systemDefault()).hour
        if (hour >= 20 || hour < 5) flag("nightDrive")
    }

    /** Arrivato con calma: nei cinque minuti dopo l'ultimo viaggio, nessuno sblocco. Si guarda una volta, dopo. */
    fun checkArrival(context: Context, now: Long = System.currentTimeMillis()) {
        val end = long("tripEnd")
        if (end == 0L || now - end < 5 * 60_000) return
        putLong("tripEnd", 0L)
        if (unlocks(context, end, end + 5 * 60_000) == 0) flag("arrived")
    }

    /** Quante volte è stato sbloccato il telefono in un intervallo. -1 se non si può sapere. */
    private fun unlocks(context: Context, from: Long, to: Long): Int = runCatching {
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return -1
        val ev = usm.queryEvents(from, to)
        val e = UsageEvents.Event()
        var n = 0
        while (ev.hasNextEvent()) { ev.getNextEvent(e); if (e.eventType == KEYGUARD_HIDDEN) n++ }
        n
    }.getOrDefault(-1)

    private const val KEYGUARD_HIDDEN = 18
}
