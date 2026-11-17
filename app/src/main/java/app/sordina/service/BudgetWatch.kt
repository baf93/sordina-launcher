package app.sordina.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import app.sordina.SordinaApp
import app.sordina.data.Tally
import java.time.LocalDate

/**
 * Il budget finisce mentre usi l'app: Sordina lo dice una volta, con una notifica in alto.
 *
 * Batteria: nessun controllo a ritmo fisso. Quando apri un'app frenata dal freno si fissa un solo promemoria per il
 * momento in cui il budget dovrebbe finire; allora si guarda (una lettura dell'utilizzo) se sei ancora lì e se il
 * budget è davvero finito. Se nel frattempo eri uscito non si fa niente: la prossima apertura passa di nuovo dal freno.
 */
object BudgetWatch {
    private val main = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    /** Hai aperto [pkg] dal freno. Se il budget era già finito lo sapevi: niente avviso. */
    fun opened(context: Context, pkg: String) {
        val left = runCatching { SordinaApp.of(context).habits.leftToday(pkg) }.getOrNull() ?: return
        if (left <= 0) return
        schedule(context.applicationContext, pkg, left)
    }

    private fun schedule(context: Context, pkg: String, minutes: Int) {
        pending?.let(main::removeCallbacks)
        val r = Runnable { runCatching { check(context, pkg) } }
        pending = r
        // Qualche secondo in più: i minuti dell'utilizzo si contano interi
        main.postDelayed(r, minutes * 60_000L + 20_000L)
    }

    private fun check(context: Context, pkg: String) {
        pending = null
        if (!foreground(context, pkg)) return
        val habits = SordinaApp.of(context).habits
        val used = habits.appTodayFresh(pkg)?.second ?: return
        val budget = habits.budget(pkg)
        // Eri uscito e rientrato dalle recenti: non è ancora finito, si riguarda quando dovrebbe
        if (used < budget) { schedule(context, pkg, budget - used); return }
        val today = LocalDate.now().toString()
        if (Tally.text("budgetTold_$pkg") == today) return
        Tally.putText("budgetTold_$pkg", today)
        StatusNotification(context).budgetDone(pkg, used)
    }

    /** [pkg] è davanti adesso, a schermo acceso? */
    private fun foreground(context: Context, pkg: String): Boolean {
        val on = runCatching { context.getSystemService(PowerManager::class.java).isInteractive }.getOrDefault(false)
        if (!on) return false
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return false
        val now = System.currentTimeMillis()
        val ev = usm.queryEvents(now - 3 * 3_600_000L, now)
        val e = UsageEvents.Event()
        var front: String? = null
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> front = e.packageName
                UsageEvents.Event.ACTIVITY_PAUSED -> if (e.packageName == front) front = null
            }
        }
        return front == pkg
    }
}
