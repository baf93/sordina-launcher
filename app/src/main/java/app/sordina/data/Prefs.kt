package app.sordina.data

import android.content.Context
import app.sordina.ui.theme.ThemeFamily
import app.sordina.ui.theme.ThemeMode

/** Impostazioni locali. Niente cloud, niente account. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("sordina", Context.MODE_PRIVATE)

    var family: ThemeFamily
        get() = runCatching { ThemeFamily.valueOf(sp.getString("family", null)!!) }.getOrDefault(ThemeFamily.CARTA)
        set(v) = sp.edit().putString("family", v.name).apply()

    var mode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(sp.getString("mode", null)!!) }.getOrDefault(ThemeMode.AUTO)
        set(v) = sp.edit().putString("mode", v.name).apply()

    /** Proposte fisse (al massimo due), in ordine. Chiave = AppEntry.key */
    var pinned: List<String>
        get() = sp.getString("pinned", "")!!.split('|').filter { it.isNotBlank() }
        set(v) = sp.edit().putString("pinned", v.take(MAX_PINNED).joinToString("|")).apply()

    /** App nascoste dall'elenco Tutte le app */
    var hidden: Set<String>
        get() = sp.getStringSet("hidden", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("hidden", v).apply()

    /** Proposte escluse ("non propormela più") */
    var excluded: Set<String>
        get() = sp.getStringSet("excluded", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("excluded", v).apply()

    companion object {
        const val MAX_PINNED = 2
    }
}
