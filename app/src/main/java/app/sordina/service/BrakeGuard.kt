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
 * per un'ora dopo che lasci Casa, a schermo acceso, si guarda quale app è davanti e, se è frenata
 * e non l'hai aperta dal freno, il freno torna sopra di lei.
 *
 * Batteria: si guarda solo fuori da Casa (mentre sei in Casa non può esserci nessuna app davanti),
 * solo a schermo acceso (a schermo spento si ferma e riparte all'accensione), solo nella fascia del freno,
 * e con un ritmo che rallenta: [FAST] nei primi minuti dopo che lasci Casa o accendi lo schermo
 * (quando è più probabile che cambi app), poi [SLOW].
 */
object BrakeGuard {
    private const val WINDOW = 60 * 60_000L
    private const val FAST = 2_000L
    private const val SLOW = 5_000L
    private const val FAST_FOR = 2 * 60_000L
    /** Fino a quando si guarda al ritmo veloce */
    @Volatile private var fastUntil = 0L

    /** L'app aperta passando dal freno: vale fino al ritorno a Casa. */
    @Volatile private var allowed: String? = null
    @Volatile private var until = 0L
    @Volatile private var last = 0L
    private var running = false
    private var ctx: Context? = null
    private var prefs: Prefs? = null
    private val main = Handler(Looper.getMainLooper())
    private val loop = object : Runnable { override fun run() = tick() }

    /** Il freno vale adesso? Sempre, o solo nella fascia scelta (es. 22–8). */
    fun brakeNow(prefs: Prefs): Boolean {
        val from = prefs.brakeFrom; val to = prefs.brakeTo
        if (from < 0 || to < 0 || from == to) return true
        val t = java.time.LocalTime.now()
        return Moments.inWindow(t.hour * 60 + t.minute, from, to)
    }

    /** Hai aspettato e aperto l'app dal freno. */
    fun passed(pkg: String) { allowed = pkg }

    /** Sei in Casa: la sessione è finita, da qui l'app frenata ripassa dal freno. Niente da guardare. */
    fun atHome(context: Context, prefs: Prefs) {
        allowed = null
        until = 0
        stop()
    }

    /** Hai lasciato Casa (un'app, le recenti, lo schermo spento): per un'ora si guarda chi va davanti. */
    fun leftHome(context: Context, prefs: Prefs) {
        if (!prefs.brakeGuard || prefs.braked.isEmpty()) { until = 0; return }
        until = System.currentTimeMillis() + WINDOW
        start(context, prefs)
    }

    /** Hai toccato la notifica di un'app frenata: anche oltre l'ora, si guarda (e l'ora riparte). */
    fun opening(context: Context, prefs: Prefs) = leftHome(context, prefs)

    /** Schermo riacceso: se l'ora non è finita, si torna a guardare. */
    fun screenOn(context: Context, prefs: Prefs) {
        if (System.currentTimeMillis() < until) start(context, prefs)
    }

    private fun start(context: Context, p: Prefs) {
        // Con l'accessibilità accesa è Android ad avvisare: nessun controllo a ritmo fisso
        if (SordinaAccess.active() && p.instantBrake) { stop(); return }
        // Fuori dalla fascia del freno (es. 22–8) non c'è niente da fermare: non si guarda proprio
        if (!brakeNow(p)) return
        ctx = context.applicationContext; prefs = p
        // Un attimo prima: l'app che ti ha portato fuori da Casa è andata davanti poco fa
        val now = System.currentTimeMillis()
        last = now - 5_000
        fastUntil = now + FAST_FOR
        // Già in giro: si riparte subito al ritmo veloce
        main.removeCallbacks(loop)
        running = true
        main.postDelayed(loop, FAST)
    }

    private fun stop() {
        running = false
        main.removeCallbacks(loop)
    }

    private fun tick() {
        val context = ctx ?: return stop()
        val p = prefs ?: return stop()
        val now = System.currentTimeMillis()
        if (now > until || !p.brakeGuard || !brakeNow(p)) { stop(); return }
        val screenOn = runCatching { context.getSystemService(PowerManager::class.java).isInteractive }.getOrDefault(false)
        // A schermo spento non si gira: riparte con l'accensione
        if (!screenOn) { stop(); return }
        runCatching { check(context, p, now) }
        main.postDelayed(loop, if (now < fastUntil) FAST else SLOW)
    }

    /** Dall'accessibilità: [pkg] è appena andata davanti (arrivano solo le app frenate). Vale sempre, non solo per un'ora. */
    fun onForeground(context: Context, pkg: String) {
        val prefs = app.sordina.SordinaApp.of(context).prefs
        if (!prefs.brakeGuard || !prefs.instantBrake) return
        brake(context, prefs, pkg)
    }

    private fun brake(context: Context, prefs: Prefs, pkg: String) {
        if (pkg == context.packageName || pkg == allowed || pkg !in prefs.braked || !brakeNow(prefs)) return
        context.startActivity(
            Intent(context, MainActivity::class.java).putExtra(EXTRA, pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
        )
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
        brake(context, prefs, pkg)
    }

    const val EXTRA = "brake"
}
