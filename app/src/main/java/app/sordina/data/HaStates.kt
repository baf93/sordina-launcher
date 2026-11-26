package app.sordina.data

import app.sordina.R
import app.sordina.tr
import java.text.NumberFormat

/**
 * Gli stati di casa da guardare in auto (scheda Casa): una temperatura, una porta, una luce. Si scelgono in
 * Impostazioni › Home Assistant › Stati in auto; qui quali entità si possono scegliere e come si dice il loro stato.
 */
object HaStates {
    /** Quanti stati al massimo nella scheda Casa: l'auto deve restare calma */
    const val MAX = 6

    private val DOMAINS = setOf("sensor", "binary_sensor", "cover", "lock", "light", "switch", "climate", "input_boolean", "fan", "person", "water_heater", "input_select", "select", "input_text", "input_number", "number")

    /** Si può guardare in auto: i sensori solo se hanno un'unità o un tipo (niente stringhe tecniche) */
    fun candidate(e: HaEntity): Boolean = e.domain in DOMAINS &&
        (e.domain != "sensor" || e.unit.isNotBlank() || e.deviceClass != null || e.state.length <= 24) &&
        e.state != "unavailable" && e.state != "unknown"

    /** Lo stato da leggere: "21,5 °C", "Aperta", "Accesa", "In casa" */
    fun label(e: HaEntity): String {
        val s = e.state
        if (s == "unavailable" || s == "unknown" || s.isBlank()) return tr(R.string.hs_unavailable)
        s.toDoubleOrNull()?.let { v ->
            val n = NumberFormat.getNumberInstance().apply { maximumFractionDigits = if (kotlin.math.abs(v) >= 100) 0 else 1 }.format(v)
            return when {
                e.unit.isBlank() -> n
                e.unit == "%" || e.unit.startsWith("°") -> n + e.unit
                else -> "$n ${e.unit}"
            }
        }
        val opening = e.deviceClass in setOf("door", "window", "garage_door", "opening", "garage", "gate")
        return when (e.domain to s) {
            "binary_sensor" to "on" -> when (e.deviceClass) {
                "motion", "occupancy", "presence" -> tr(R.string.hs_motion)
                "moisture" -> tr(R.string.hs_wet)
                "smoke", "gas", "problem", "safety" -> tr(R.string.hs_alert)
                else -> if (opening) tr(R.string.hs_open) else tr(R.string.hs_on)
            }
            "binary_sensor" to "off" -> when (e.deviceClass) {
                "motion", "occupancy", "presence" -> tr(R.string.hs_still)
                "moisture" -> tr(R.string.hs_dry)
                "smoke", "gas", "problem", "safety" -> tr(R.string.hs_ok)
                else -> if (opening) tr(R.string.hs_closed) else tr(R.string.hs_off)
            }
            "lock" to "locked" -> tr(R.string.hs_locked)
            "lock" to "unlocked" -> tr(R.string.hs_unlocked)
            "person" to "home" -> tr(R.string.hs_home)
            "person" to "not_home" -> tr(R.string.hs_away)
            else -> when (s) {
                "on" -> tr(R.string.hs_on)
                "off" -> tr(R.string.hs_off)
                "open", "opening" -> tr(R.string.hs_open)
                "closed", "closing" -> tr(R.string.hs_closed)
                else -> s.replace('_', ' ').replaceFirstChar { it.uppercase() }
            }
        }
    }

    /** Acceso, aperto, sbloccato, scattato: il riquadro lo dice anche col colore */
    fun active(e: HaEntity): Boolean = e.state in setOf("on", "open", "opening", "unlocked", "heat", "cool", "heat_cool", "home")
}
