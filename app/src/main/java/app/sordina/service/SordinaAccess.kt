package app.sordina.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import app.sordina.SordinaApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * L'accessibilità di Sordina: facoltativa, serve a due cose sole.
 * - Freno istantaneo: Android avvisa nell'istante in cui un'app frenata va davanti (dalle recenti, da un link…),
 *   e i controlli a ritmo fisso del freno non servono più.
 * - Doppio tocco in Casa per spegnere lo schermo (come fa la Home del telefono), senza "amministratore del
 *   dispositivo", che dopo ogni spegnimento chiederebbe il PIN al posto dell'impronta.
 *
 * - Scrivere una frase in un'app che non la accetta da fuori (Assist di Home Assistant): per pochi secondi, solo in
 *   quell'app, cerca la casella, ci scrive e invia.
 *
 * Batteria: ascolta solo il cambio di finestra delle app frenate (le altre app non lo svegliano proprio), e guarda il
 * contenuto di una finestra solo durante una [fill], che dura al massimo qualche secondo. Senza app frenate non riceve niente.
 */
class SordinaAccess : AccessibilityService() {
    /** Le app ascoltate adesso: si chiede ad Android di cambiarle solo se cambiano davvero */
    private var watching: Set<String>? = null

    override fun onServiceConnected() {
        instance = this
        _on.value = true
        watch()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        gone()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        gone()
        super.onDestroy()
    }

    private fun gone() {
        if (instance === this) instance = null
        _on.value = false
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == pending?.pkg) { tryFill(); return }
        runCatching { BrakeGuard.onForeground(this, pkg) }
    }

    override fun onInterrupt() {}

    /** Ascolta solo le app frenate (o nessuna: solo Sordina stessa, che non manda mai niente di utile qui). */
    private fun watch() {
        val prefs = SordinaApp.of(this).prefs
        val want = if (prefs.brakeGuard && prefs.instantBrake && prefs.braked.isNotEmpty()) prefs.braked else setOf(packageName)
            .let { w -> pending?.let { w + it.pkg } ?: w }
        if (want == watching) return
        val info = serviceInfo ?: return
        // Un elenco vuoto vorrebbe dire "tutte le app": non succede mai, al minimo c'è Sordina
        info.packageNames = want.toTypedArray()
        runCatching { serviceInfo = info }.onSuccess { watching = want }
    }

    /** Una frase da scrivere (e inviare) nella casella di [pkg], entro [until]. */
    private class Fill(val pkg: String, val text: String, val send: Boolean, val until: Long) {
        /** Quando è comparsa la casella (0: non ancora): si aspetta che la tastiera dell'app finisca di partire */
        var seenAt = 0L
        /** Quante volte si è scritto: si controlla che il testo sia rimasto quello giusto prima di inviare */
        var writes = 0
    }
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /** Cerca la casella nella finestra di davanti: se c'è, scrive e invia; se no riprova fra poco, fino alla scadenza. */
    private fun tryFill() {
        val f = pending ?: return
        if (android.os.SystemClock.uptimeMillis() > f.until) { done(); return }
        // La finestra dell'app (anche se Android considera ancora attiva quella di Sordina): la prima con una casella
        val roots = runCatching { windows.mapNotNull { it.root } }.getOrDefault(emptyList()) + listOfNotNull(runCatching { rootInActiveWindow }.getOrNull())
        val box = roots.filter { it.packageName?.toString() == f.pkg }.firstNotNullOfOrNull { editable(it) }
        val now = android.os.SystemClock.uptimeMillis()
        main.removeCallbacksAndMessages(null)
        if (box == null) { main.postDelayed({ tryFill() }, 200); return }
        // Appena comparsa: la tastiera dell'app sta ancora partendo e si mangerebbe le prime lettere. Un attimo.
        if (f.seenAt == 0L) { f.seenAt = now; main.postDelayed({ tryFill() }, 450); return }
        // Già scritto: se il testo è rimasto quello giusto si invia, se no si riscrive (al massimo tre volte)
        if (f.writes > 0 && box.text?.toString() == f.text) {
            if (f.send && Build.VERSION.SDK_INT >= 30) {
                box.performAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
                // La casella perde il fuoco: ridata la tastiera, non torna su da sola sopra la risposta
                box.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLEAR_FOCUS)
            }
            done(); return
        }
        if (f.writes >= 3) { done(); return }
        val args = android.os.Bundle().apply { putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, f.text) }
        if (box.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, args)) f.writes++
        main.postDelayed({ tryFill() }, 250)
    }

    /** Per le frasi da inviare la tastiera non serve: resta nascosta da quando si apre l'app finché non è inviata. */
    private val keys = android.os.Handler(android.os.Looper.getMainLooper())
    private fun keyboard(show: Boolean) {
        keys.removeCallbacksAndMessages(null)
        val kb = softKeyboardController
        if (!show) runCatching { kb.showMode = SHOW_MODE_HIDDEN }
        // Ridata un attimo dopo l'invio: si vede la risposta, e alla prossima casella toccata torna
        else keys.postDelayed({ runCatching { kb.showMode = SHOW_MODE_AUTO } }, 700)
    }

    private fun done() {
        pending = null
        keyboard(show = true)
        main.removeCallbacksAndMessages(null)
        watch()
    }

    private fun editable(n: android.view.accessibility.AccessibilityNodeInfo): android.view.accessibility.AccessibilityNodeInfo? {
        if (n.isEditable && n.isVisibleToUser) return n
        for (i in 0 until n.childCount) n.getChild(i)?.let { c -> editable(c)?.let { return it } }
        return null
    }

    companion object {
        @Volatile private var instance: SordinaAccess? = null
        @Volatile private var pending: Fill? = null

        /**
         * Scrive [text] nella prima casella di [pkg] appena si apre; con [send] la invia anche (mai per i messaggi alle persone). Va chiamata subito prima di aprire l'app.
         * false se l'accessibilità non è accesa (allora resta l'incolla a mano).
         */
        fun fill(pkg: String, text: String, send: Boolean): Boolean {
            val s = instance ?: return false
            pending = Fill(pkg, text, send, android.os.SystemClock.uptimeMillis() + 5000)
            if (send) s.keyboard(show = false)
            s.main.post { runCatching { s.watch(); s.tryFill() } }
            return true
        }
        private val _on = MutableStateFlow(false)
        /** L'accessibilità di Sordina è accesa e collegata */
        val on: StateFlow<Boolean> = _on

        fun active(): Boolean = instance != null

        /** Accesa nelle impostazioni di Android (anche se il servizio non si è ancora collegato). */
        fun isEnabled(context: Context): Boolean {
            if (instance != null) return true
            val flat = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            return flat.split(':').any { android.content.ComponentName.unflattenFromString(it)?.packageName == context.packageName }
        }

        /** Le app frenate (o l'opzione) sono cambiate: si aggiorna cosa ascoltare. */
        fun refresh() { instance?.let { s -> runCatching { s.watch() } } }

        /** Spegne lo schermo come il tasto laterale. false se l'accessibilità non c'è. */
        fun lock(): Boolean {
            val s = instance ?: return false
            if (Build.VERSION.SDK_INT < 28) return false
            return s.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        }

        /** Le impostazioni di Android dove si accende. */
        fun openSettings(context: Context) {
            runCatching {
                context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}
