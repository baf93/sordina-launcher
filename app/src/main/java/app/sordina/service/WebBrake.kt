package app.sordina.service

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Il freno vale anche per la versione web delle app principali: se freni YouTube, anche youtube.com nel browser.
 * Si riconosce il sito leggendo la barra dell'indirizzo del browser (serve l'accessibilità di Sordina).
 *
 * Batteria: i browser si ascoltano solo se almeno un'app frenata ha un sito qui sotto; il contenuto di una pagina
 * si guarda solo mentre un browser è davanti, al massimo una volta ogni [EVERY] ms, e si legge solo la barra dell'indirizzo.
 */
object WebBrake {
    const val EVERY = 1_000L

    /** App → i suoi siti (il dominio vale anche per i sottodomini: m.youtube.com, it.pinterest.com…) */
    private val SITES: Map<String, List<String>> = mapOf(
        "com.google.android.youtube" to listOf("youtube.com", "youtu.be"),
        "com.instagram.android" to listOf("instagram.com"),
        "com.instagram.barcelona" to listOf("threads.net", "threads.com"),
        "com.facebook.katana" to listOf("facebook.com", "fb.com"),
        "com.facebook.lite" to listOf("facebook.com", "fb.com"),
        "com.zhiliaoapp.musically" to listOf("tiktok.com"),
        "com.ss.android.ugc.trill" to listOf("tiktok.com"),
        "com.twitter.android" to listOf("x.com", "twitter.com"),
        "com.reddit.frontpage" to listOf("reddit.com"),
        "com.pinterest" to listOf("pinterest.com", "pinterest.it", "pin.it"),
        "com.linkedin.android" to listOf("linkedin.com"),
        "tv.twitch.android.app" to listOf("twitch.tv"),
        "com.netflix.mediaclient" to listOf("netflix.com"),
        "com.tumblr" to listOf("tumblr.com"),
        "com.snapchat.android" to listOf("snapchat.com"),
        "com.amazon.avod.thirdpartyclient" to listOf("primevideo.com"),
        "com.disney.disneyplus" to listOf("disneyplus.com"),
        "com.spotify.music" to listOf("open.spotify.com"),
        "com.vinted.android" to listOf("vinted.it", "vinted.com"),
        "bsky.app" to listOf("bsky.app"),
        "com.ninegag.android.app" to listOf("9gag.com"),
    )

    /** Browser → id della barra dell'indirizzo (il primo che c'è) */
    private val BARS: Map<String, List<String>> = mapOf(
        "com.android.chrome" to listOf("url_bar"),
        "com.chrome.beta" to listOf("url_bar"),
        "com.chrome.dev" to listOf("url_bar"),
        "com.brave.browser" to listOf("url_bar"),
        "com.microsoft.emmx" to listOf("url_bar"),
        "com.vivaldi.browser" to listOf("url_bar"),
        "com.kiwibrowser.browser" to listOf("url_bar"),
        "com.sec.android.app.sbrowser" to listOf("location_bar_edit_text", "custom_tab_toolbar_url_bar_text"),
        "com.sec.android.app.sbrowser.beta" to listOf("location_bar_edit_text"),
        "org.mozilla.firefox" to listOf("mozac_browser_toolbar_url_view", "url_bar_title"),
        "org.mozilla.firefox_beta" to listOf("mozac_browser_toolbar_url_view"),
        "org.mozilla.focus" to listOf("display_url", "mozac_browser_toolbar_url_view"),
        "com.opera.browser" to listOf("url_field"),
        "com.opera.mini.native" to listOf("url_field"),
        "com.duckduckgo.mobile.android" to listOf("omnibarTextInput"),
        "com.mi.globalbrowser" to listOf("url"),
        "com.huawei.browser" to listOf("url_bar"),
    )

    fun isBrowser(pkg: String) = pkg in BARS

    /** I browser da ascoltare: nessuno se nessuna app frenata ha un sito. */
    fun browsers(braked: Set<String>): Set<String> = if (braked.any { it in SITES }) BARS.keys else emptySet()

    /** L'app frenata a cui appartiene l'indirizzo [url], o null. */
    fun appFor(url: String, braked: Set<String>): String? {
        val h = host(url) ?: return null
        return braked.firstOrNull { p -> SITES[p]?.any { d -> h == d || h.endsWith(".$d") } == true }
    }

    /** "https://m.youtube.com/watch?v=…" → "m.youtube.com"; anche senza schema, come lo mostrano i browser. */
    internal fun host(url: String): String? {
        val s = url.trim().lowercase().substringAfter("://").substringBefore('/').substringBefore('?').substringBefore(':')
        if ('.' !in s || ' ' in s) return null
        return s.removePrefix("www.")
    }

    /** L'indirizzo mostrato dal browser [pkg] nella finestra [root], o null se non si trova. */
    fun url(pkg: String, root: AccessibilityNodeInfo): String? {
        for (id in BARS[pkg].orEmpty()) {
            val n = runCatching { root.findAccessibilityNodeInfosByViewId("$pkg:id/$id") }.getOrNull()?.firstOrNull() ?: continue
            // Mentre scrivi nella barra non è un indirizzo visitato: si aspetta che tu vada sulla pagina
            if (n.isFocused) return null
            n.text?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }
}
