package app.sordina.car

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import app.sordina.R
import app.sordina.SordinaApp
import app.sordina.data.HomeRun
import app.sordina.service.AlarmCode
import app.sordina.service.HomeGuard
import app.sordina.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Impronta sul telefono" dallo schermo dell'auto: sul telefono compare solo la richiesta d'impronta (o del codice
 * a mano, se l'impronta non va). Poi si fa la cosa, l'esito torna all'auto con [CarBus] e questa schermata sparisce.
 * Annullata la richiesta, sparisce e basta.
 */
class CarAuthActivity : androidx.activity.ComponentActivity() {
    /** La richiesta è stata mostrata (la finestra ha perso il fuoco) */
    private var asked = false
    /** Il codice o l'impronta sono arrivati: si sta lavorando */
    private var working = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) { finish(); return }
        val app = SordinaApp.of(this)
        val prefs = app.prefs
        val entity = intent.getStringExtra(EXTRA_ENTITY).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val lost = { done(HomeRun.Done(tr(R.string.alarm_code_lost), false)) }
        when (intent.getStringExtra(EXTRA_OP)) {
            OP_ROUTINE -> {
                val r = prefs.haRoutines[entity] ?: return cancel()
                val alarm = r.alarm?.takeIf { prefs.haAlarms[it] != null }
                if (alarm != null) AlarmCode.use(this, prefs, alarm, onCode = { code -> work { HomeRun.routine(app, entity, code) } }, onLost = lost, title = title)
                else HomeGuard.ask(this, title) { work { HomeRun.routine(app, entity, null) } }
            }
            OP_ALARM_ON -> {
                val mode = intent.getStringExtra(EXTRA_TEXT)
                AlarmCode.use(this, prefs, entity, onCode = { code -> work { HomeRun.alarmOn(app, entity, mode, code) } }, onLost = lost, title = title)
            }
            OP_ALARM_OFF -> AlarmCode.use(this, prefs, entity, onCode = { code -> work { HomeRun.alarmOff(app, entity, code) } }, onLost = lost, title = title)
            OP_ASSIST -> {
                val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
                HomeGuard.ask(this, title) { work { HomeRun.Done(app.ha.assist(text) ?: tr(R.string.home_no_answer), true) } }
            }
            else -> cancel()
        }
    }

    // La richiesta d'impronta (o il codice a mano) è un'altra finestra: quando se ne va e torna qui senza un codice, hai annullato
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) asked = true else if (asked && !working) window.decorView.postDelayed({ if (!working && !isFinishing) cancel() }, 400)
    }

    private fun work(job: () -> HomeRun.Done) {
        working = true
        lifecycleScope.launch { done(withContext(Dispatchers.IO) { runCatching(job).getOrElse { HomeRun.Done(tr(R.string.home_no_answer), false) } }) }
    }

    private fun done(d: HomeRun.Done) { working = true; CarBus.post(d); finish() }

    private fun cancel() { if (!isFinishing) { CarBus.post(null); finish() } }

    companion object {
        private const val EXTRA_OP = "op"
        private const val EXTRA_ENTITY = "entity"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"
        const val OP_ROUTINE = "routine"
        const val OP_ALARM_OFF = "alarmOff"
        const val OP_ALARM_ON = "alarmOn"
        const val OP_ASSIST = "assist"

        /** Apre la richiesta sul telefono. Dall'auto: la chiama un pulsante "solo da fermi" */
        fun start(context: Context, op: String, entity: String, title: String, text: String? = null): Boolean = runCatching {
            context.startActivity(Intent(context, CarAuthActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra(EXTRA_OP, op).putExtra(EXTRA_ENTITY, entity).putExtra(EXTRA_TITLE, title).putExtra(EXTRA_TEXT, text))
        }.isSuccess
    }
}
