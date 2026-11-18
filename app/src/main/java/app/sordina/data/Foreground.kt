package app.sordina.data

import android.app.usage.UsageEvents

/**
 * Il tempo in primo piano, contato come lo conta Android (Benessere digitale), dagli eventi di utilizzo.
 *
 * Tre trappole del conto "primo piano → background" per pacchetto, che davano 19 ore invece di un'ora e mezza:
 * - un'app con due schermate: la seconda sovrascriveva la prima e il conto restava aperto;
 * - a schermo spento un'app resta "in primo piano" fino al prossimo evento: va chiusa allo spegnimento;
 * - il totale non è la somma delle app: due insieme (una sopra l'altra) contano una volta sola.
 */
object Foreground {
    data class Ev(val type: Int, val pkg: String, val cls: String?, val t: Long)

    /** [perPkg]: millisecondi per app. [total]: millisecondi con almeno un'app non esclusa davanti. */
    data class Result(val perPkg: Map<String, Long>, val total: Long)

    const val RESUMED = 1              // UsageEvents.Event.ACTIVITY_RESUMED (MOVE_TO_FOREGROUND)
    const val PAUSED = 2               // UsageEvents.Event.ACTIVITY_PAUSED (MOVE_TO_BACKGROUND)
    const val STOPPED = 23             // UsageEvents.Event.ACTIVITY_STOPPED
    const val SCREEN_OFF = 16          // UsageEvents.Event.SCREEN_NON_INTERACTIVE
    const val KEYGUARD_SHOWN = 17      // UsageEvents.Event.KEYGUARD_SHOWN
    const val SHUTDOWN = 26            // UsageEvents.Event.DEVICE_SHUTDOWN

    /** Gli eventi in ordine di tempo, tra [start] e [end]. [exclude] non conta nel totale (Sordina stessa). */
    fun fold(events: Iterable<Ev>, start: Long, end: Long, exclude: Set<String> = emptySet()): Result {
        val open = LinkedHashMap<Pair<String, String?>, Long>()
        val per = HashMap<String, Long>()
        var total = 0L
        var last = start
        fun advance(t: Long) {
            val to = t.coerceIn(start, end)
            if (to > last && open.isNotEmpty()) {
                val d = to - last
                val pkgs = open.keys.mapTo(HashSet()) { it.first }
                pkgs.forEach { per[it] = (per[it] ?: 0) + d }
                if (pkgs.any { it !in exclude }) total += d
            }
            if (to > last) last = to
        }
        for (e in events) {
            advance(e.t)
            when (e.type) {
                RESUMED -> open[e.pkg to e.cls] = e.t
                PAUSED, STOPPED -> open.remove(e.pkg to e.cls)
                SCREEN_OFF, KEYGUARD_SHOWN, SHUTDOWN -> open.clear()
            }
        }
        advance(end)
        return Result(per, total)
    }

    /** Legge tutti gli eventi di una finestra. [only]: se c'è, tiene solo quei pacchetti (gli eventi di schermo restano). */
    fun read(events: UsageEvents, only: Set<String>? = null, onEvent: (UsageEvents.Event) -> Unit = {}): List<Ev> {
        val out = ArrayList<Ev>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            onEvent(e)
            val screen = e.eventType == SCREEN_OFF || e.eventType == KEYGUARD_SHOWN || e.eventType == SHUTDOWN
            val act = e.eventType == RESUMED || e.eventType == PAUSED || e.eventType == STOPPED
            if (screen || act && (only == null || e.packageName in only)) out += Ev(e.eventType, e.packageName.orEmpty(), e.className, e.timeStamp)
        }
        return out
    }
}
