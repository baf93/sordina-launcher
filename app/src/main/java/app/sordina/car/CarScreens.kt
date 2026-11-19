package app.sordina.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.InputCallback
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.OnClickListener
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.ParkedOnlyOnClickListener
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.model.signin.InputSignInMethod
import androidx.car.app.model.signin.SignInTemplate
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import app.sordina.R
import app.sordina.data.Held
import app.sordina.data.HomeRun
import app.sordina.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Un'icona di Sordina per l'auto; [tint]: nel colore del testo dell'auto (le icone a una tinta, non la mascotte) */
internal fun CarContext.icon(res: Int, tint: Boolean = true): CarIcon =
    CarIcon.Builder(IconCompat.createWithResource(this, res)).apply { if (tint) setTint(CarColor.DEFAULT) }.build()

/** Un pulsante. [primary]: quello evidenziato (Car API 4). [parked]: solo da fermi, se no Android Auto lo dice lui */
internal fun CarContext.button(title: String, primary: Boolean = false, parked: Boolean = false, onClick: () -> Unit): Action =
    Action.Builder().setTitle(title)
        .apply { if (primary && carAppApiLevel >= 4) setFlags(Action.FLAG_PRIMARY) }
        .setOnClickListener(if (parked) ParkedOnlyOnClickListener.create { onClick() } else OnClickListener { onClick() })
        .build()

/**
 * Una frase con la sordina e al massimo due pulsanti: conferme ("Chiamo Anna?"), "fermati", l'esito di una routine.
 * [autoClose]: torna indietro da sola dopo un po' (l'esito: l'hai già sentito).
 */
internal class SayScreen(
    cc: CarContext,
    private val title: String?,
    private val text: String,
    private val iconRes: Int? = R.drawable.car_mascot,
    private val autoClose: Boolean = false,
    private val actions: (SayScreen) -> List<Action> = { s -> listOf(cc.button(tr(R.string.ok)) { s.finish() }) },
) : Screen(cc) {
    init {
        if (autoClose) lifecycleScope.launch { delay(AUTO_CLOSE_MILLIS); finish() }
    }

    override fun onGetTemplate(): Template = MessageTemplate.Builder(text)
        .setHeaderAction(Action.BACK)
        .apply {
            title?.let { setTitle(it) }
            iconRes?.let { setIcon(carContext.icon(it, tint = it != R.drawable.car_mascot && it != R.drawable.car_mascot_happy)) }
            actions(this@SayScreen).take(2).forEach { addAction(it) }
        }
        .build()

    private companion object { const val AUTO_CLOSE_MILLIS = 5_000L }
}

/** Cosa fa il codice scritto (o l'impronta sul telefono) */
internal sealed interface CodeOp {
    data class Routine(val entity: String) : CodeOp
    data class AlarmOff(val entity: String) : CodeOp
    data class AlarmOn(val entity: String, val mode: String?) : CodeOp
}

/**
 * Il codice dell'allarme, da fermi: subito il tastierino (Android Auto lo mostra solo ad auto ferma), e sotto
 * "Impronta sul telefono", che usa il codice salvato. Il codice scritto qui vale una volta e non si salva.
 */
internal class CodeScreen(
    cc: CarContext,
    private val home: CarHome,
    private val title: String,
    private val instructions: String,
    private val op: CodeOp,
) : Screen(cc) {
    private var error: String? = null
    private var busy = false
    private var onPhone = false
    private var wait: Job? = null

    override fun onGetTemplate(): Template {
        if (onPhone) return MessageTemplate.Builder(tr(R.string.auto_on_phone_sub))
            .setTitle(tr(R.string.auto_on_phone)).setHeaderAction(Action.BACK)
            .setIcon(carContext.icon(R.drawable.car_mascot, tint = false))
            .addAction(carContext.button(tr(R.string.cancel)) { wait?.cancel(); onPhone = false; invalidate() })
            .build()
        val input = InputSignInMethod.Builder(object : InputCallback {
            override fun onInputSubmitted(text: String) { submit(text) }
        })
            .setInputType(InputSignInMethod.INPUT_TYPE_PASSWORD)
            .setKeyboardType(InputSignInMethod.KEYBOARD_NUMBER)
            .setHint(tr(R.string.auto_code_hint))
            .setShowKeyboardByDefault(true)
            .apply { error?.let { setErrorMessage(it) } }
            .build()
        return SignInTemplate.Builder(input)
            .setTitle(title)
            .setInstructions(instructions)
            .setHeaderAction(Action.BACK)
            .setLoading(busy)
            .apply { if (!busy) addAction(carContext.button(tr(R.string.auto_fingerprint), parked = true) { phone() }) }
            .setAdditionalText(tr(R.string.auto_code_or))
            .build()
    }

    private fun submit(code: String) {
        if (code.isBlank() || busy) return
        busy = true; error = null; invalidate()
        lifecycleScope.launch {
            val app = home.app
            val d = withContext(Dispatchers.IO) {
                runCatching {
                    when (op) {
                        is CodeOp.Routine -> HomeRun.routine(app, op.entity, code)
                        is CodeOp.AlarmOff -> HomeRun.alarmOff(app, op.entity, code)
                        is CodeOp.AlarmOn -> HomeRun.alarmOn(app, op.entity, op.mode, code)
                    }
                }.getOrElse { HomeRun.Done(tr(R.string.home_no_answer), false) }
            }
            busy = false
            // Codice sbagliato: si resta qui, con il motivo sotto la casella
            if (!d.ok) { error = d.message; invalidate(); return@launch }
            finish()
            home.done(d)
        }
    }

    /** Sul telefono: impronta, poi il codice salvato. L'esito torna qui da [CarBus] */
    private fun phone() {
        val (what, entity, extra) = when (op) {
            is CodeOp.Routine -> Triple(CarAuthActivity.OP_ROUTINE, op.entity, null)
            is CodeOp.AlarmOff -> Triple(CarAuthActivity.OP_ALARM_OFF, op.entity, null)
            is CodeOp.AlarmOn -> Triple(CarAuthActivity.OP_ALARM_ON, op.entity, op.mode)
        }
        CarBus.take()
        if (!CarAuthActivity.start(carContext, what, entity, title, extra)) return
        onPhone = true; invalidate()
        wait?.cancel()
        wait = lifecycleScope.launch {
            val out = CarBus.outcome.filterNotNull().first()
            CarBus.take()
            onPhone = false
            val d = out.done
            if (d == null) { invalidate(); return@launch }
            finish()
            home.done(d)
        }
    }
}

/** Una chat o un'app in attesa: chi e quanti, mai il testo sullo schermo. "Ascolta" lo legge, "Rispondi a voce" risponde */
internal class GroupScreen(cc: CarContext, private val home: CarHome, private val group: List<Held>) : Screen(cc) {
    override fun onGetTemplate(): Template {
        val first = group.first()
        val count = group.sumOf { it.count }
        val pane = Pane.Builder()
            .addRow(Row.Builder().setTitle(tr(R.string.auto_n_waiting, count)).addText(first.appLabel).build())
            .addAction(carContext.button(tr(R.string.auto_listen), primary = true) { home.say(CarHome.readAloud(group)) })
            .apply { if (home.replyKey(group) != null) addAction(carContext.button(tr(R.string.auto_reply)) { home.replyByVoice(group) }) }
            .build()
        return PaneTemplate.Builder(pane).setTitle(first.groupTitle).setHeaderAction(Action.BACK).build()
    }
}

/** Più allarmi salvati e non hai detto quale: si sceglie qui */
internal class PickScreen(cc: CarContext, private val title: String, private val items: List<Pair<String, String>>, private val onPick: (String) -> Unit) : Screen(cc) {
    override fun onGetTemplate(): Template {
        val list = ItemList.Builder()
        items.forEach { (id, name) -> list.addItem(Row.Builder().setTitle(name).setOnClickListener { finish(); onPick(id) }.build()) }
        return ListTemplate.Builder().setTitle(title).setHeaderAction(Action.BACK).setSingleList(list.build()).build()
    }
}
