package app.sordina

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import java.util.Locale

/**
 * Le lingue di Sordina. Di base quella del telefono; si può cambiare dalle impostazioni.
 * "Sordina" resta "Sordina" in ogni lingua.
 */
object Lang {
    /** Lingue con traduzione completa: tag BCP 47 e nome nella sua lingua. */
    val ALL = listOf(
        "en" to "English", "it" to "Italiano", "es" to "Español", "fr" to "Français", "de" to "Deutsch",
        "pt" to "Português", "nl" to "Nederlands", "pl" to "Polski", "sv" to "Svenska", "da" to "Dansk",
        "nb" to "Norsk", "fi" to "Suomi", "cs" to "Čeština", "ro" to "Română", "tr" to "Türkçe",
        "el" to "Ελληνικά", "ru" to "Русский", "uk" to "Українська", "ja" to "日本語", "ko" to "한국어",
        "zh-CN" to "中文（简体）", "zh-TW" to "中文（繁體）", "ar" to "العربية", "hi" to "हिन्दी",
        "id" to "Bahasa Indonesia", "vi" to "Tiếng Việt", "th" to "ไทย",
    )

    private lateinit var app: Context

    fun init(context: Context) { app = context.applicationContext }

    /** La lingua scelta; null = quella del telefono. */
    fun chosen(context: Context): String? =
        if (Build.VERSION.SDK_INT >= 33) context.getSystemService(LocaleManager::class.java).applicationLocales.takeIf { !it.isEmpty }?.get(0)?.toLanguageTag()
        else context.getSharedPreferences("sordina", Context.MODE_PRIVATE).getString("language", null)

    fun choose(context: Context, tag: String?) {
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            context.getSharedPreferences("sordina", Context.MODE_PRIVATE).edit().putString("language", tag).apply()
            if (::app.isInitialized) applyLegacy(app)
        }
    }

    /** Prima di Android 13 la lingua dell'app si applica a mano. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val tag = base.getSharedPreferences("sordina", Context.MODE_PRIVATE).getString("language", null) ?: return base
        val conf = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        return base.createConfigurationContext(conf)
    }

    @Suppress("DEPRECATION")
    fun applyLegacy(context: Context) {
        if (Build.VERSION.SDK_INT >= 33) return
        val tag = context.getSharedPreferences("sordina", Context.MODE_PRIVATE).getString("language", null)
        val locale = tag?.let { Locale.forLanguageTag(it) } ?: Resources_system()
        val conf = Configuration(context.resources.configuration).apply { setLocale(locale) }
        context.resources.updateConfiguration(conf, context.resources.displayMetrics)
    }

    private fun Resources_system(): Locale = android.content.res.Resources.getSystem().configuration.locales[0]

    /** La lingua scelta corrisponde a [tag] (anche "it-IT" per "it"). */
    fun matches(chosen: String?, tag: String) = chosen != null && (chosen.equals(tag, true) || chosen.startsWith("$tag-", true))

    /** Le lingue in ordine alfabetico, come le legge chi usa la lingua attuale. */
    fun sorted(context: Context): List<Pair<String, String>> {
        val c = java.text.Collator.getInstance(context.resources.configuration.locales[0])
        return ALL.sortedWith { a, b -> c.compare(a.second, b.second) }
    }

    /** Il nome di una lingua nella lingua che stai usando: "Deutsch" → "Tedesco". */
    fun localName(context: Context, tag: String): String {
        val now = context.resources.configuration.locales[0]
        val l = Locale.forLanguageTag(tag)
        val n = if (tag.contains('-')) l.getDisplayName(now) else l.getDisplayLanguage(now)
        return n.replaceFirstChar { it.titlecase(now) }
    }

    /** La lingua del telefono, col suo nome. */
    fun systemName(): String {
        val l = Resources_system()
        val t = l.toLanguageTag()
        return ALL.firstOrNull { matches(t, it.first) }?.second
            ?: ALL.firstOrNull { t.substringBefore('-') == it.first.substringBefore('-') }?.second
            ?: l.getDisplayName(l).replaceFirstChar { it.titlecase(l) }
    }

    /** Il nome della lingua scelta, per la riga delle impostazioni. */
    fun label(context: Context): String? = chosen(context)?.let { t -> ALL.firstOrNull { it.first.equals(t, true) || t.startsWith(it.first + "-", true) }?.second ?: Locale.forLanguageTag(t).displayName }

    fun s(@StringRes id: Int): String = app.getString(id)
    fun s(@StringRes id: Int, vararg args: Any): String = app.getString(id, *args)
    fun p(id: Int, n: Int): String = app.resources.getQuantityString(id, n, n)
}

/** Un testo nella lingua scelta, ovunque (schermate, servizi, notifiche). */
fun tr(@StringRes id: Int): String = Lang.s(id)
fun tr(@StringRes id: Int, vararg args: Any): String = Lang.s(id, *args)
/** Un testo con il numero giusto: "1 notifica", "3 notifiche". */
fun trn(id: Int, n: Int): String = Lang.p(id, n)
