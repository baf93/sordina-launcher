package app.sordina.data

import android.content.Context
import android.content.SharedPreferences
import app.sordina.ui.theme.ThemeFamily
import app.sordina.ui.theme.ThemeMode
import org.json.JSONObject

/** Come arriva una notifica. */
enum class Level(val label: String, val hint: String) {
    SEMPRE("Sempre", "subito, con suono, anche di notte"),
    SUBITO("Subito in silenzio", "subito, senza suono"),
    RIEPILOGO("Riepilogo", "agli orari dei riepiloghi"),
}

/** Quanto mostrare sulla schermata di blocco. */
enum class LockPrivacy(val label: String, val example: String) {
    NUMBERS("Solo numeri", "3 in attesa"),
    NAMES("Nomi", "Anna, Paolo, Calcetto"),
    MESSAGES("Messaggi", "Anna: passi tu a prendere il pane?"),
}

/** Impostazioni locali. Niente cloud, niente account. */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("sordina", Context.MODE_PRIVATE)

    private fun str(k: String, d: String) = sp.getString(k, d) ?: d
    private fun put(block: SharedPreferences.Editor.() -> Unit) = sp.edit().apply(block).apply()

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) = put { putBoolean("onboarded", v) }

    // Aspetto
    var family: ThemeFamily
        get() = runCatching { ThemeFamily.valueOf(str("family", "CARTA")) }.getOrDefault(ThemeFamily.CARTA)
        set(v) = put { putString("family", v.name) }

    var mode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(str("mode", "AUTO")) }.getOrDefault(ThemeMode.AUTO)
        set(v) = put { putString("mode", v.name) }

    var keyboardOnOpen: Boolean
        get() = sp.getBoolean("keyboardOnOpen", false)
        set(v) = put { putBoolean("keyboardOnOpen", v) }

    // Proposte
    /** Proposte fisse (al massimo due), in ordine. Chiave = AppEntry.key */
    var pinned: List<String>
        get() = str("pinned", "").split('|').filter { it.isNotBlank() }
        set(v) = put { putString("pinned", v.take(MAX_PINNED).joinToString("|")) }

    var hidden: Set<String>
        get() = sp.getStringSet("hidden", emptySet())!!.toSet()
        set(v) = put { putStringSet("hidden", v) }

    var excluded: Set<String>
        get() = sp.getStringSet("excluded", emptySet())!!.toSet()
        set(v) = put { putStringSet("excluded", v) }

    // Notifiche
    var summaryHours: List<Int>
        get() = str("summaryHours", "8,13,19").split(',').mapNotNull { it.trim().toIntOrNull() }.sorted()
            .ifEmpty { listOf(8, 13, 19) }
        set(v) = put { putString("summaryHours", v.distinct().sorted().joinToString(",")) }

    /** Livello scelto dall'utente per app (pacchetto → livello). */
    var appLevels: Map<String, Level>
        get() = str("appLevels", "").split('|').mapNotNull {
            val p = it.split('=')
            if (p.size == 2) runCatching { p[0] to Level.valueOf(p[1]) }.getOrNull() else null
        }.toMap()
        set(v) = put { putString("appLevels", v.entries.joinToString("|") { "${it.key}=${it.value.name}" }) }

    fun setAppLevel(pkg: String, level: Level?) {
        appLevels = if (level == null) appLevels - pkg else appLevels + (pkg to level)
    }

    /** Nomi delle persone importanti (normalizzati), per riconoscere i loro messaggi. */
    var importantNames: Set<String>
        get() = sp.getStringSet("importantNames", emptySet())!!.toSet()
        set(v) = put { putStringSet("importantNames", v) }

    /** Sordina gestisce Non disturbare (scelta esplicita al primo avvio). */
    var silenceManaged: Boolean
        get() = sp.getBoolean("silenceManaged", false)
        set(v) = put { putBoolean("silenceManaged", v) }

    var savedInterruptionFilter: Int
        get() = sp.getInt("savedFilter", 1)
        set(v) = put { putInt("savedFilter", v) }

    var lockRecap: Boolean
        get() = sp.getBoolean("lockRecap", true)
        set(v) = put { putBoolean("lockRecap", v) }

    var lockPrivacy: LockPrivacy
        get() = runCatching { LockPrivacy.valueOf(str("lockPrivacy", "NAMES")) }.getOrDefault(LockPrivacy.NAMES)
        set(v) = put { putString("lockPrivacy", v.name) }

    // Momenti
    var nightEnabled: Boolean
        get() = sp.getBoolean("nightEnabled", true)
        set(v) = put { putBoolean("nightEnabled", v) }
    var nightStart: Int
        get() = sp.getInt("nightStart", 23 * 60)
        set(v) = put { putInt("nightStart", v) }
    var nightEnd: Int
        get() = sp.getInt("nightEnd", 7 * 60)
        set(v) = put { putInt("nightEnd", v) }
    var carEnabled: Boolean
        get() = sp.getBoolean("carEnabled", true)
        set(v) = put { putBoolean("carEnabled", v) }
    var momentOverride: String?
        get() = sp.getString("momentOverride", null)
        set(v) = put { putString("momentOverride", v) }
    var momentOverrideBase: String?
        get() = sp.getString("momentOverrideBase", null)
        set(v) = put { putString("momentOverrideBase", v) }

    // Attrito
    var braked: Set<String>
        get() = sp.getStringSet("braked", emptySet())!!.toSet()
        set(v) = put { putStringSet("braked", v) }
    var brakeSeconds: Int
        get() = sp.getInt("brakeSeconds", 5)
        set(v) = put { putInt("brakeSeconds", v.coerceIn(0, 15)) }

    var mirrorEnabled: Boolean
        get() = sp.getBoolean("mirrorEnabled", true)
        set(v) = put { putBoolean("mirrorEnabled", v) }

    // Calendario
    var calendarId: Long
        get() = sp.getLong("calendarId", -1)
        set(v) = put { putLong("calendarId", v) }

    // Home Assistant
    var haUrl: String
        get() = str("haUrl", "")
        set(v) = put { putString("haUrl", v.trim().trimEnd('/')) }
    var haTokenEnc: String
        get() = str("haToken", "")
        set(v) = put { putString("haToken", v) }
    var haStatusEntity: String
        get() = str("haStatusEntity", "")
        set(v) = put { putString("haStatusEntity", v.trim()) }

    /** App di messaggi scelta a mano per una persona (nome normalizzato → pacchetto). */
    var personApp: Map<String, String>
        get() = str("personApp", "").split('|').mapNotNull {
            val p = it.split('='); if (p.size == 2) p[0] to p[1] else null
        }.toMap()
        set(v) = put { putString("personApp", v.entries.joinToString("|") { "${it.key}=${it.value}" }) }

    /** Il motore impara dalle correzioni: frase normalizzata → id della scelta. */
    var aliases: Map<String, String>
        get() = runCatching {
            val o = JSONObject(str("aliases", "{}"))
            o.keys().asSequence().associateWith { o.getString(it) }
        }.getOrDefault(emptyMap())
        set(v) = put { putString("aliases", JSONObject(v.entries.toList().takeLast(300).associate { it.key to it.value }).toString()) }

    /** Quante volte la prima proposta era quella giusta (misura del motore). */
    var intentFirst: Int
        get() = sp.getInt("intentFirst", 0)
        set(v) = put { putInt("intentFirst", v) }
    var intentTotal: Int
        get() = sp.getInt("intentTotal", 0)
        set(v) = put { putInt("intentTotal", v) }

    /** Ora dello specchio serale. */
    var mirrorHour: Int
        get() = sp.getInt("mirrorHour", 21)
        set(v) = put { putInt("mirrorHour", v.coerceIn(17, 23)) }

    /** Esporta tutte le impostazioni (tranne il token di Home Assistant). */
    fun export(): String {
        val o = JSONObject()
        for ((k, v) in sp.all) {
            if (k == "haToken") continue
            when (v) {
                is Set<*> -> o.put(k, org.json.JSONArray(v.toList()))
                else -> o.put(k, v)
            }
        }
        return o.toString(2)
    }

    fun import(json: String) {
        val o = JSONObject(json)
        put {
            for (k in o.keys()) {
                if (k == "haToken") continue
                when (val v = o.get(k)) {
                    is org.json.JSONArray -> putStringSet(k, (0 until v.length()).map { v.getString(it) }.toSet())
                    is Boolean -> putBoolean(k, v)
                    is Int -> if (k == "calendarId") putLong(k, v.toLong()) else putInt(k, v)
                    is Long -> if (k == "calendarId") putLong(k, v) else putInt(k, v.toInt())
                    else -> putString(k, v.toString())
                }
            }
        }
    }

    fun clearAll() = sp.edit().clear().apply()

    companion object {
        const val MAX_PINNED = 2
    }
}
