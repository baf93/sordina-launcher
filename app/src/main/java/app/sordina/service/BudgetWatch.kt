package app.sordina.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.SystemClock
import app.sordina.SordinaApp
import app.sordina.data.Tally
import java.time.LocalDate

/**
 * Il budget finisce mentre usi l'app: Sordina lo dice una volta, con una notifica in alto.
 *
 * Batteria: nessun controllo a ritmo fisso. Quando apri un'app frenata dal freno si fissa un solo promemoria per il
 * momento in cui il budget dovrebbe finire; allora si guarda (una lettura dell'utilizzo) se sei ancora lì e se il
 * budget è davvero finito. Se nel frattempo eri uscito non si fa niente: la prossima apertura passa di nuovo dal freno.
 * Il promemoria è un allarme di Android (non sveglia il telefono): regge anche se Android chiude Sordina nel frattempo.
 */
object BudgetWatch {
    private const val ACTION = "app.sordina.BUDGET_CHECK"
    private const val EXTRA_PKG = "pkg"
    /** Solo per non rileggere l'utilizzo a ogni cambio di schermata: se si perde, al peggio si rifissa lo stesso promemoria */
    @Volatile private var pendingPkg: String? = null

    /**
     * Hai aperto [pkg]. [sawBrake]: dal freno, che ti ha già detto quanto restava; se il budget era finito lo sapevi.
     * Senza freno (fuori dalla sua fascia oraria) nessuno te l'ha detto: se è già finito, l'avviso arriva subito.
     */
    fun opened(context: Context, pkg: String, sawBrake: Boolean = true) {
        val left = runCatching { SordinaApp.of(context).habits.leftToday(pkg) }.getOrNull() ?: return
        if (left <= 0) {
            if (!sawBrake) schedule(context.applicationContext, pkg, 0)
            return
        }
        schedule(context.applicationContext, pkg, left)
    }

    /** Un'app frenata è andata davanti fuori dalla fascia del freno (dall'accessibilità o da Casa). */
    fun outsideBrake(context: Context, pkg: String) {
        // Lo stesso promemoria già in attesa per quest'app basta: non si rilegge l'utilizzo a ogni cambio di schermata
        if (pendingPkg == pkg) return
        opened(context, pkg, sawBrake = false)
    }

    private fun intent(context: Context, pkg: String?): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, BudgetReceiver::class.java).setAction(ACTION).apply { if (pkg != null) putExtra(EXTRA_PKG, pkg) },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Un promemoria solo alla volta: l'ultima app aperta sostituisce la precedente */
    private fun schedule(context: Context, pkg: String, minutes: Int) {
        pendingPkg = pkg
        // Qualche secondo in più: i minuti dell'utilizzo si contano interi (subito, se è già finito: giusto il tempo di aprirsi)
        val delay = if (minutes <= 0) 5_000L else minutes * 60_000L + 20_000L
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        // Senza svegliare il telefono: a schermo spento non c'è niente da dire. Finestra di un minuto, Android raggruppa
        runCatching { am.setWindow(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + delay, 60_000L, intent(context, pkg)) }
    }

    internal fun fired(context: Context, intent: Intent) {
        val pkg = intent.getStringExtra(EXTRA_PKG) ?: return
        runCatching { check(context.applicationContext, pkg) }
    }

    private fun check(context: Context, pkg: String) {
        pendingPkg = null
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

/** Il promemoria del budget: vedi [BudgetWatch]. */
class BudgetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = BudgetWatch.fired(context, intent)
}
