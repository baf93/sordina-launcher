package app.sordina.data

import app.sordina.R
import app.sordina.SordinaApp
import app.sordina.service.AlarmCode
import app.sordina.tr

/**
 * Routine e allarmi di casa una volta che il codice c'è (o non serve): niente schermate, solo Home Assistant e la
 * frase da dire. Li usano Casa, dopo l'impronta, e lo schermo dell'auto, dopo il codice o l'impronta sul telefono.
 * Vanno chiamati fuori dal thread principale.
 */
object HomeRun {
    /** Com'è andata: la frase per te e se è andata bene */
    data class Done(val message: String, val ok: Boolean)

    /** Già inserito, o sta per esserlo: non si richiede */
    fun armed(state: String?) = state != null && (state.startsWith("armed_") || state == "arming" || state == "pending")

    /** Lo stato di un allarme, null se casa non risponde */
    fun state(app: SordinaApp, entity: String): String? = runCatching { app.ha.state(entity)?.state }.getOrNull()

    /**
     * Lancia una routine abilitata. Con un allarme da disattivare: prima si disattiva e, solo se è andata, parte la
     * routine ("Entra" non parte con la centrale ancora inserita). Con un allarme da inserire: parte la routine e, se è
     * andata, si inserisce l'allarme nella sua modalità ("Esco"). [code]: quello dell'allarme legato ("" se non lo
     * chiede), null senza allarme.
     */
    fun routine(app: SordinaApp, entity: String, code: String?): Done {
        val r = app.prefs.haRoutines[entity] ?: return Done(tr(R.string.routine_failed, entity), false)
        val a = r.alarm?.let { app.prefs.haAlarms[it] }
        val alarmName = a?.name ?: r.alarm.orEmpty()
        val alarm = r.alarm.takeIf { code != null && a != null }
        if (alarm != null && !r.arm && !app.ha.disarm(alarm, code!!)) return Done(tr(R.string.routine_alarm_failed, alarmName, r.name), false)
        if (!app.ha.runRoutine(entity)) return Done(tr(R.string.routine_failed, r.name), false)
        val armed = if (alarm != null && r.arm) armed(state(app, alarm)) || app.ha.arm(alarm, a!!.mode ?: "away", code!!) else null
        return when {
            alarm != null && !r.arm -> Done(tr(R.string.routine_done_alarm, r.name, alarmName), true)
            armed == true -> Done(tr(R.string.routine_done_armed, r.name, alarmName), true)
            armed == false -> Done(tr(R.string.routine_arm_failed, r.name, alarmName), false)
            else -> Done(tr(R.string.routine_done, r.name), true)
        }
    }

    /** Disattiva un allarme salvato con [code] */
    fun alarmOff(app: SordinaApp, entity: String, code: String): Done {
        val ok = app.ha.disarm(entity, code)
        return Done(if (ok) tr(R.string.alarm_off_done, app.prefs.haAlarms[entity]?.name ?: entity) else tr(R.string.alarm_off_failed), ok)
    }

    /** Inserisce un allarme salvato in [mode] (null: la sua predefinita) con [code] ("" se non lo chiede) */
    fun alarmOn(app: SordinaApp, entity: String, mode: String?, code: String): Done {
        val a = app.prefs.haAlarms[entity]
        val m = mode ?: a?.mode ?: "away"
        val ok = app.ha.arm(entity, m, code)
        return Done(if (ok) tr(R.string.alarm_on_done, a?.name ?: entity, AlarmCode.modeLabel(m)) else tr(R.string.alarm_on_failed), ok)
    }
}
