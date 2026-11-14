package app.sordina.data

import app.sordina.R
import app.sordina.tr

import android.content.Context
import android.content.SharedPreferences
import app.sordina.ui.theme.ThemeFamily
import app.sordina.ui.theme.ThemeMode
import org.json.JSONObject

/** Come arriva una notifica. */
enum class Level(private val labelRes: Int, private val hintRes: Int) {
    SEMPRE(R.string.lv_always, R.string.lv_always_hint),
    SUBITO(R.string.lv_silent, R.string.lv_silent_hint),
    RIEPILOGO(R.string.lv_summary, R.string.lv_summary_hint),
    ;
    val label: String get() = tr(labelRes)
    val hint: String get() = tr(hintRes)
}

/** Quanto mostrare sulla schermata di blocco. */
enum class LockPrivacy(private val labelRes: Int, private val exampleRes: Int) {
    NUMBERS(R.string.lock_numbers, R.string.lock_numbers_ex),
    NAMES(R.string.lock_names, R.string.lock_names_ex),
    MESSAGES(R.string.lock_messages, R.string.lock_messages_ex),
    ;
    val label: String get() = tr(labelRes)
    val example: String get() = tr(exampleRes)
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
    /** Sfondo di Casa: 0 tinta del tema, 1 sfondo del telefono, 2 foto scelta, 3 astratto, 4 tinta scelta ([solidColor]) */
    var wallpaperMode: Int
        get() = sp.getInt("wallpaperMode", 0)
        set(v) = put { putInt("wallpaperMode", v) }
    /** Il colore della tinta unita scelta (wallpaperMode 4) */
    var solidColor: Int
        get() = sp.getInt("solidColor", 0xFF2F3A45.toInt())
        set(v) = put { putInt("solidColor", v) }
    /** Nel tema scuro, fondo nero vero: sugli schermi OLED i pixel neri sono spenti. Acceso di base: è l'aria di Sordina */
    var trueBlack: Boolean
        get() = sp.getBoolean("trueBlack", true)
        set(v) = put { putBoolean("trueBlack", v) }
    /** Quale degli sfondi astratti di Sordina (wallpaperMode 3) */
    var wallpaperPreset: Int
        get() = sp.getInt("wallpaperPreset", 0)
        set(v) = put { putInt("wallpaperPreset", v) }
    /** Animazioni morbide (spente: tutto scatta subito) */
    var animations: Boolean
        get() = sp.getBoolean("animations", true)
        set(v) = put { putBoolean("animations", v) }
    /** Vibrazioni leggere al tocco */
    var haptics: Boolean
        get() = sp.getBoolean("haptics", true)
        set(v) = put { putBoolean("haptics", v) }
    /** Icone accanto ai nomi delle app (spente di serie: le icone invitano ad aprire) */
    var appIcons: Boolean
        get() = sp.getBoolean("appIcons", true)
        set(v) = put { putBoolean("appIcons", v) }
    /** Schede e barra semitrasparenti sopra lo sfondo */
    var glass: Boolean
        get() = sp.getBoolean("glass", false)
        set(v) = put { putBoolean("glass", v) }
    /** Colore di rilievo: 0 predefinito, 1 dallo sfondo, altrimenti il colore */
    var accent: Int
        get() = sp.getInt("accent", 0)
        set(v) = put { putInt("accent", v) }
    var family: ThemeFamily
        get() = runCatching { ThemeFamily.valueOf(str("family", "CARTA")) }.getOrDefault(ThemeFamily.CARTA)
        set(v) = put { putString("family", v.name) }

    var mode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(str("mode", "AUTO")) }.getOrDefault(ThemeMode.AUTO)
        set(v) = put { putString("mode", v.name) }

    var keyboardOnOpen: Boolean
        get() = sp.getBoolean("keyboardOnOpen", true)
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

    /** "Lascia passare": contatto o app (chiave del gruppo, es. "p:anna" o "a:com.app") → fino a quando (millisecondi) */
    var passUntil: Map<String, Long>
        get() = str("passUntil", "").split('|').mapNotNull {
            val i = it.lastIndexOf('=')
            if (i > 0) it.substring(i + 1).toLongOrNull()?.let { t -> it.substring(0, i) to t } else null
        }.toMap()
        set(v) = put { putString("passUntil", v.filterValues { it > System.currentTimeMillis() }.entries.joinToString("|") { "${it.key}=${it.value}" }) }

    /** Categoria scelta da te per un contatto o un'app (chiave del gruppo → nome della categoria) */
    var categoryOverrides: Map<String, String>
        get() = str("categoryOverrides", "").split('|').mapNotNull {
            val i = it.lastIndexOf('=')
            if (i > 0) it.substring(0, i) to it.substring(i + 1) else null
        }.toMap()
        set(v) = put { putString("categoryOverrides", v.entries.joinToString("|") { "${it.key}=${it.value}" }) }

    /** Nomi delle persone importanti (normalizzati), per riconoscere i loro messaggi. */
    var importantNames: Set<String>
        get() = sp.getStringSet("importantNames", emptySet())!!.toSet()
        set(v) = put { putStringSet("importantNames", v) }

    /**
     * Le persone importanti che passano anche quando scrivono in un gruppo, non solo nella chat a due.
     * La prima volta: tutte quelle che c'erano già (prima passavano anche nei gruppi); le nuove, solo in chat.
     */
    var importantInGroups: Set<String>
        get() {
            if (!sp.contains("importantInGroups")) { val v = importantNames; put { putStringSet("importantInGroups", v) }; return v }
            return sp.getStringSet("importantInGroups", emptySet())!!.toSet()
        }
        set(v) = put { putStringSet("importantInGroups", v) }


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
    /** Casa cambia aspetto: in auto, la sera, di notte (quando Android è in una modalità). Ognuna si spegne da sola. */
    var homeCar: Boolean
        get() = sp.getBoolean("homeCar", true)
        set(v) = put { putBoolean("homeCar", v) }
    var homeEvening: Boolean
        get() = sp.getBoolean("homeEvening", true)
        set(v) = put { putBoolean("homeEvening", v) }
    /** Sera e Notte cominciano all'ora scelta (minuti dalla mezzanotte); finiscono alle 6 */
    var eveningFrom: Int
        get() = sp.getInt("eveningFrom", 20 * 60)
        set(v) = put { putInt("eveningFrom", v) }
    var nightFrom: Int
        get() = sp.getInt("nightFrom", 23 * 60)
        set(v) = put { putInt("nightFrom", v) }
    /** La prima sveglia di stamattina vista di notte (millis) e il giorno in cui la Notte è già finita (epoch day) */
    var nightAlarm: Long
        get() = sp.getLong("nightAlarm", 0L)
        set(v) = put { putLong("nightAlarm", v) }
    var nightDone: Long
        get() = sp.getLong("nightDone", -1L)
        set(v) = put { putLong("nightDone", v) }
    var homeNight: Boolean
        get() = sp.getBoolean("homeNight", true)
        set(v) = put { putBoolean("homeNight", v) }
    /** In auto: le app dei due pulsanti grandi (pacchetto); null = la prima che c'è */
    /** Cosa mostra la Home: meteo, impegni, schede intelligenti. Tutto acceso di serie. */
    var homeWeather: Boolean
        get() = sp.getBoolean("homeWeather", true)
        set(v) = put { putBoolean("homeWeather", v) }
    var homeAgenda: Boolean
        get() = sp.getBoolean("homeAgenda", true)
        set(v) = put { putBoolean("homeAgenda", v) }
    var homeCards: Boolean
        get() = sp.getBoolean("homeCards", true)
        set(v) = put { putBoolean("homeCards", v) }

    /** In auto: fino a tre app scelte da te (pacchetti), in fila sulla Home. null = mai scelte: Sordina propone navigazione, musica e casa. */
    var carApps: List<String>?
        get() = sp.getString("carApps", null)?.split('|')?.filter { it.isNotBlank() }
        set(v) = put { if (v == null) remove("carApps") else putString("carApps", v.take(3).joinToString("|")) }

    var carNavApp: String?
        get() = sp.getString("carNavApp", null)
        set(v) = put { putString("carNavApp", v) }
    var carMusicApp: String?
        get() = sp.getString("carMusicApp", null)
        set(v) = put { putString("carMusicApp", v) }
    /** In auto: persone da non mostrare (nomi normalizzati), tolte con una pressione lunga */
    var carHidden: Set<String>
        get() = sp.getStringSet("carHidden", emptySet())!!.toSet()
        set(v) = put { putStringSet("carHidden", v) }

    /** Sordina a tempo: fino a quando (millisecondi), 0 = spenta */
    var quietUntil: Long
        get() = sp.getLong("quietUntil", 0L)
        set(v) = put { putLong("quietUntil", v) }
    /** Non disturbare l'ha acceso Sordina (e quindi lo spegne lei, alla fine) */
    /** In auto: Non disturbare con Android Auto (acceso e spento da Sordina) */
    var carQuiet: Boolean
        get() = sp.getBoolean("carQuiet", true)
        set(v) = put { putBoolean("carQuiet", v) }
    /** Il silenzio acceso da Sordina è quello dell'auto */
    var quietCar: Boolean
        get() = sp.getBoolean("quietCar", false)
        set(v) = put { putBoolean("quietCar", v) }
    /** L'hai spento tu durante questo viaggio: non si riaccende fino al prossimo */
    var carDeclined: Boolean
        get() = sp.getBoolean("carDeclined", false)
        set(v) = put { putBoolean("carDeclined", v) }
    var quietOwned: Boolean
        get() = sp.getBoolean("quietOwned", false)
        set(v) = put { putBoolean("quietOwned", v) }

    // Urgenze
    /** Un messaggio urgente o insistente passa anche se non è di una persona importante. */
    /** Chi ti risponde passa: dopo che scrivi, rispondi o chiami qualcuno (anche in un gruppo), i suoi messaggi passano per un po'. */
    var talkPass: Boolean
        get() = sp.getBoolean("talkPass", true)
        set(v) = put { putBoolean("talkPass", v) }

    var urgentEnabled: Boolean
        get() = sp.getBoolean("urgentEnabled", true)
        set(v) = put { putBoolean("urgentEnabled", v) }
    /** Parole che rendono urgente un messaggio, separate da virgole (vuoto = quelle di serie) */
    var urgentWords: String
        get() = str("urgentWords", "")
        set(v) = put { putString("urgentWords", v.trim()) }

    /** Regole per tipo di notifica (canale): "pacchetto|canale" → livello. Vincono su tutto il resto. */
    var channelRules: Map<String, Level>
        get() = levelMap("channelRules")
        set(v) = putLevelMap("channelRules", v)
    /** Regole per persona o gruppo (nome normalizzato) → livello. "In sordina" vince anche sulle persone importanti. */
    var personRules: Map<String, Level>
        get() = levelMap("personRules").ifEmpty {
            // Prima c'era solo "sempre al riepilogo" per i gruppi
            sp.getStringSet("holdAlways", emptySet())!!.associate { it.removePrefix("p:") to Level.RIEPILOGO }
        }
        set(v) = put { putString("personRules", JSONObject(v.mapValues { it.value.name }).toString()); remove("holdAlways") }

    /** Il nome da mostrare per una regola (tipo di notifica o persona), per l'elenco in Impostazioni */
    var ruleNames: Map<String, String>
        get() = runCatching { JSONObject(str("ruleNames", "{}")).let { o -> o.keys().asSequence().associateWith { o.getString(it) } } }.getOrDefault(emptyMap())
        set(v) = put { putString("ruleNames", JSONObject(v).toString()) }

    private fun levelMap(k: String): Map<String, Level> = runCatching {
        val o = JSONObject(str(k, "{}"))
        o.keys().asSequence().mapNotNull { key -> runCatching { key to Level.valueOf(o.getString(key)) }.getOrNull() }.toMap()
    }.getOrDefault(emptyMap())
    private fun putLevelMap(k: String, v: Map<String, Level>) = put { putString(k, JSONObject(v.mapValues { it.value.name }).toString()) }

    /** Già chiesto di togliere Sordina dall'ottimizzazione della batteria */
    var batteryAsked: Boolean
        get() = sp.getBoolean("batteryAsked", false)
        set(v) = put { putBoolean("batteryAsked", v) }

    // Attrito
    /** Il freno vale solo in questa fascia (minuti dalla mezzanotte); -1 = sempre */
    var brakeFrom: Int
        get() = sp.getInt("brakeFrom", -1)
        set(v) = put { putInt("brakeFrom", v) }
    var brakeTo: Int
        get() = sp.getInt("brakeTo", -1)
        set(v) = put { putInt("brakeTo", v) }
    /** L'attesa cresce a ogni riapertura nella stessa ora */
    /** Il freno torna anche se l'app frenata si riapre dalle recenti */
    var brakeGuard: Boolean
        get() = sp.getBoolean("brakeGuard", true)
        set(v) = put { putBoolean("brakeGuard", v) }

    var brakeGrow: Boolean
        get() = sp.getBoolean("brakeGrow", true)
        set(v) = put { putBoolean("brakeGrow", v) }
    /** Le app da frenare sono già state proposte al primo avvio (una volta sola) */
    var brakeSuggested: Boolean
        get() = sp.getBoolean("brakeSuggested", false)
        set(v) = put { putBoolean("brakeSuggested", v) }
    var braked: Set<String>
        get() = sp.getStringSet("braked", emptySet())!!.toSet()
        set(v) = put { putStringSet("braked", v) }
    /** Le notifiche di sistema (auricolari, Bluetooth, Play Services…) passano senza sordina */
    var systemPass: Boolean
        get() = sp.getBoolean("systemPass", true)
        set(v) = put { putBoolean("systemPass", v) }
    /** Come ordinare la pagina Sordina: 0 automatico (per priorità), 1 per arrivo, 2 ordine tuo */
    var sordinaOrder: Int
        get() = sp.getInt("sordinaOrder", 0)
        set(v) = put { putInt("sordinaOrder", v) }
    /** Le categorie nell'ordine scelto da te (nomi dell'enum Category) */
    var categoryOrder: List<String>
        get() = sp.getString("categoryOrder", "")!!.split(",").filter { it.isNotBlank() }
        set(v) = put { putString("categoryOrder", v.joinToString(",")) }
    var brakeSeconds: Int
        get() = sp.getInt("brakeSeconds", 10)
        set(v) = put { putInt("brakeSeconds", v.coerceIn(3, 60)) }

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
    /** Calendari di Home Assistant da mostrare in Casa (entity_id) */
    var haCalendars: Set<String>
        get() = sp.getStringSet("haCalendars", emptySet())!!.toSet()
        set(v) = put { putStringSet("haCalendars", v) }
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

    /** Soprannomi imparati: parola che scrivi o dici ("mamma") → contatto (nome normalizzato). */
    var nicknames: Map<String, String>
        get() = runCatching {
            val o = JSONObject(str("nicknames", "{}"))
            o.keys().asSequence().associateWith { o.getString(it) }
        }.getOrDefault(emptyMap())
        set(v) = put { putString("nicknames", JSONObject(v.entries.toList().takeLast(100).associate { it.key to it.value }).toString()) }

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
                    is Int -> if (k == "calendarId" || k == "quietUntil") putLong(k, v.toLong()) else putInt(k, v)
                    is Long -> if (k == "calendarId" || k == "quietUntil") putLong(k, v) else putInt(k, v.toInt())
                    else -> putString(k, v.toString())
                }
            }
        }
    }

    fun clearAll() = sp.edit().clear().apply()

    companion object {
        const val MAX_PINNED = 4
        /** Una fissata che non è un'app ("scrivi a Elisa su Telegram"): davanti alla cosa del cervello */
        const val PIN_ACTION = "do:"
    }
}
