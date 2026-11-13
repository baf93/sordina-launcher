package app.sordina.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import app.sordina.MainActivity
import app.sordina.data.Moments
import app.sordina.data.Prefs

/**
 * Il freno vale anche quando un'app frenata torna davanti senza passare da Casa
 * (dalle app recenti, da una notifica, da un collegamento). Android non lascia chiudere le app degli altri:
 * per mezz'ora dopo il ritorno a Casa, a schermo acceso, si guarda quale app è davanti e, se è frenata
 * e non l'hai aperta dal freno, il freno torna sopra di lei.
 */
object BrakeGuard {
    private const val WINDOW = 30 * 60_000L
    private const val EVERY = 1_500L

    /** L'app aperta passando dal freno: vale fino al ritorno a Casa. */
    @Volatile private var allowed: String? = null
    @Volatile private var until = 0L
    @Volatile private var last = 0L
    private var running = false
    private val main = Handler(Looper.getMainLooper())

    /** Il freno vale adesso? Sempre, o solo nella fascia scelta (es. 22–8). */
    fun brakeNow(prefs: Prefs): Boolean {
        val from = prefs.brakeFrom; val to = prefs.brakeTo
        if (from < 0 || to < 0 || from == to) return true
        val t = java.time.LocalTime.now()
        return Moments.inWindow(t.hour * 60 + t.minute, from, to)
    }

    /** Hai aspettato e aperto l'app dal freno. */
    fun passed(pkg: String) { allowed = pkg }

    /** Sei tornato a Casa: la sessione è finita, da qui l'app frenata ripassa dal freno. */
    fun atHome(context: Context, prefs: Prefs) {
        allowed = null
        val now = System.currentTimeMillis()
        last = now
        if (!prefs.brakeGuard || prefs.braked.isEmpty()) { until = 0; return }
        until = now + WINDOW
        if (!running) { running = true; main.postDelayed({ tick(context.applicationContext, prefs) }, EVERY) }
    }

    private fun tick(context: Context, prefs: Prefs) {
        val now = System.currentTimeMillis()
        if (now > until || !prefs.brakeGuard) { running = false; return }
        runCatching {
            val screenOn = context.getSystemService(PowerManager::class.java).isInteractive
            if (screenOn) check(context, prefs, now) else last = now
        }
        main.postDelayed({ tick(context, prefs) }, EVERY)
    }

    private fun check(context: Context, prefs: Prefs, now: Long) {
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return
        val events = usm.queryEvents(last, now)
        last = now
        var front: String? = null
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) front = e.packageName
        }
        val pkg = front ?: return
        if (pkg == context.packageName || pkg == allowed || pkg !in prefs.braked || !brakeNow(prefs)) return
        context.startActivity(
            Intent(context, MainActivity::class.java).putExtra(EXTRA, pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
        )
    }

    const val EXTRA = "brake"
}
