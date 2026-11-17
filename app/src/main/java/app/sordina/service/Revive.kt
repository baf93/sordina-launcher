package app.sordina.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.SystemClock
import app.sordina.SordinaApp
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Risveglia un servizio di Sordina (ascolto delle notifiche, accessibilità) che Android non ha ricollegato.
 *
 * Quando la memoria è poca, alcuni telefoni (Samsung) uccidono il processo e non riavviano più i servizi collegati:
 * restano accesi nelle impostazioni ma nessuno li chiama. requestRebind lì non basta, perché per Android sono ancora "accesi".
 * Spegnere e riaccendere il componente nel manifest è un cambio di pacchetto: Android lo ricollega da capo,
 * senza toccare i permessi dati dall'utente.
 */
object Revive {
    /** Dopo l'avvio del processo Android collega i servizi in qualche secondo: prima non è un guasto. */
    private const val GRACE = 20_000L
    private const val GAP = 60_000L
    private val last = HashMap<String, Long>()

    fun startedLongAgo(): Boolean = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime() > GRACE

    /** [waited]: si è già aspettato che Android li collegasse (il processo appena svegliato da un promemoria). */
    @Synchronized
    fun kick(context: Context, cls: Class<*>, waited: Boolean = false): Boolean {
        if (!waited && !startedLongAgo()) return false
        val now = SystemClock.elapsedRealtime()
        if (now - (last[cls.name] ?: 0L) < GAP) return false
        last[cls.name] = now
        val cn = ComponentName(context, cls)
        val pm = context.packageManager
        return runCatching {
            pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP)
            // Ricordato: su Samsung, in Casa, si propone l'elenco "App mai in sospensione"
            SordinaApp.of(context).prefs.revivedAt = System.currentTimeMillis()
        }.isSuccess
    }
}

/**
 * Samsung: l'elenco "App mai in sospensione" è quello che il telefono guarda per ultimo quando libera memoria.
 * Non c'è un collegamento diretto (né un modo per sapere se Sordina c'è già): si spiega la strada e si apre la Batteria.
 */
object SamsungSleep {
    val isSamsung: Boolean get() = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    /** Il foglio con la strada è aperto */
    val asking = MutableStateFlow(false)

    fun ask() { asking.value = true }

    fun openBattery(context: Context) {
        SordinaApp.of(context).prefs.samsungAsked = System.currentTimeMillis()
        val tries = listOf(
            Intent("com.samsung.android.sm.ACTION_BATTERY"),
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)),
        )
        for (i in tries) if (runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
    }
}
