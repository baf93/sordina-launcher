package app.sordina.data

import app.sordina.R
import app.sordina.tr

/**
 * Categorie "intelligenti" per le notifiche in sordina: si capiscono da chi scrive, dall'app e dal testo.
 * Se una categoria non ti torna, la sposti: le prossime di quel contatto o di quell'app vanno dove hai detto tu.
 */
enum class Category(private val res: Int) {
    PERSONE(R.string.cat_people),
    GRUPPI(R.string.cat_groups),
    LAVORO(R.string.cat_work),
    EMAIL(R.string.cat_email),
    CASA(R.string.cat_home),
    SOLDI(R.string.cat_money),
    VIAGGI(R.string.cat_travel),
    SOCIAL(R.string.cat_social),
    NOTIZIE(R.string.cat_news),
    TELEFONO(R.string.cat_phone),
    APP(R.string.cat_apps),
    ;
    val label: String get() = tr(res)
}

object Categories {
    /** Ordine automatico: prima le persone, poi acquisti e soldi, casa, e il resto. */
    val AUTO = listOf(
        Category.PERSONE, Category.GRUPPI, Category.SOLDI, Category.CASA, Category.LAVORO, Category.EMAIL,
        Category.VIAGGI, Category.SOCIAL, Category.NOTIZIE, Category.TELEFONO, Category.APP,
    )

    /** L'ordine scelto da te; le categorie che mancano vanno in fondo nell'ordine automatico. */
    fun custom(names: List<String>): List<Category> {
        val mine = names.mapNotNull { n -> Category.entries.firstOrNull { it.name == n } }.distinct()
        return mine + AUTO.filter { it !in mine }
    }

    private val WORK = listOf(
        "com.microsoft.office.outlook", "com.microsoft.teams", "com.slack", "us.zoom.videomeetings",
        "com.atlassian", "com.google.android.apps.meetings", "com.microsoft.office", "com.microsoft.skydrive",
        "com.webex", "com.cisco.webex", "notion.id", "com.asana", "com.trello", "com.clickup",
    )
    private val MAIL = listOf(
        "com.google.android.gm", "com.samsung.android.email", "ch.protonmail", "me.proton.android.mail",
        "com.yahoo.mobile.client.android.mail", "it.libero.mail", "com.fsck.k9", "net.thunderbird", "com.readdle.spark",
    )
    private val HOME = listOf(
        "io.homeassistant", "com.samsung.android.oneconnect", "com.amazon.dee.app", "com.google.android.apps.chromecast.app",
        "com.philips.lighting", "com.ringapp", "com.netatmo", "com.ezviz", "com.mm.android", "com.dahua", "com.tuya",
        "com.xiaomi.smarthome", "com.tplink", "com.nest", "com.arlo", "com.wyze", "com.ajaxsystems", "com.hikvision",
    )
    private val MONEY = listOf(
        "com.amazon.mshop", "com.ebay", "it.poste", "posteitaliane", "com.dhl", "com.ups", "com.gls", "it.brt",
        "com.satispay", "com.paypal", "com.revolut", "com.n26", "it.widiba", "it.bancaditalia", "it.intesasanpaolo",
        "com.unicredit", "it.bnl", "it.fineco", "com.hype", "it.subito", "com.vinted", "com.zalando", "com.glovo",
        "com.deliveroo", "com.justeat", "com.shein", "com.alibaba", "com.einnovation.temu", "com.ikea",
        "com.google.android.apps.walletnfcrel", "com.samsung.android.spay",
    )
    private val TRAVEL = listOf(
        "com.airbnb", "com.booking", "com.ryanair", "com.easyjet", "it.trenitalia", "com.lynxspa.prontotreno",
        "it.italotreno", "com.ntvspa", "com.ita", "com.lufthansa", "com.uber", "com.flixbus", "com.google.android.apps.maps",
        "com.waze", "com.trainline", "com.skyscanner", "com.tripadvisor", "com.telepass", "it.autostrade",
    )
    fun isSocial(pkg: String) = SOCIAL.hit(pkg)
    fun isHome(pkg: String) = HOME.hit(pkg)

    private val SOCIAL = listOf(
        "com.instagram", "com.facebook.katana", "com.facebook.lite", "com.twitter.android", "com.zhiliaoapp.musically",
        "com.ss.android.ugc", "com.reddit", "com.linkedin", "com.snapchat", "com.pinterest", "com.bereal",
        "com.google.android.youtube", "com.strava", "tv.twitch", "com.discord", "com.tumblr", "com.bsky",
    )
    private val NEWS = listOf(
        "com.google.android.apps.magazines", "it.ansa", "it.gedi", "it.rcs", "it.ilpost", "flipboard", "com.medium",
        "com.substack", "it.sky.tg24", "it.ilsole24ore", "it.repubblica", "it.corriere", "com.feedly",
    )
    private val PHONE_PREFIX = listOf("com.google.android.gms", "com.android.", "com.samsung.", "com.sec.", "com.google.android.apps.wellbeing")

    private val PROMO = Regex("(offert|sconto|promo|saldi|coupon|newsletter|-\\d+ ?%|black friday|solo per te)", RegexOption.IGNORE_CASE)
    private val PARCEL = Regex("(pacco|consegn|spedizion|ordine|pagament|addebit|bonifico|ricevuto|rimborso)", RegexOption.IGNORE_CASE)

    /** Pacchetto uguale o "figlio" (com.google.android.gm non deve prendere com.google.android.gms). */
    private fun List<String>.hit(pkg: String) = any {
        if (it.endsWith(".")) pkg.startsWith(it, ignoreCase = true)
        else pkg.equals(it, ignoreCase = true) || pkg.startsWith("$it.", ignoreCase = true)
    }

    /** La categoria di un gruppo di notifiche: prima quella scelta da te, poi quella capita da Sordina. */
    fun of(h: Held, overrides: Map<String, String>): Category {
        overrides[h.groupKey]?.let { o -> Category.entries.firstOrNull { it.name == o }?.let { return it } }
        overrides["a:" + h.pkg]?.let { o -> Category.entries.firstOrNull { it.name == o }?.let { return it } }
        return guess(h)
    }

    fun guess(h: Held): Category {
        val p = h.pkg
        if (h.isMessage && h.conversation != null) return Category.GRUPPI
        if (h.isMessage && h.person != null && !WORK.hit(p) && !MAIL.hit(p)) return Category.PERSONE
        return when {
            WORK.hit(p) -> Category.LAVORO
            MAIL.hit(p) -> Category.EMAIL
            HOME.hit(p) -> Category.CASA
            MONEY.hit(p) -> Category.SOLDI
            TRAVEL.hit(p) -> Category.VIAGGI
            SOCIAL.hit(p) -> Category.SOCIAL
            NEWS.hit(p) || PROMO.containsMatchIn(h.text) -> Category.NOTIZIE
            PARCEL.containsMatchIn(h.text) -> Category.SOLDI
            PHONE_PREFIX.hit(p) -> Category.TELEFONO
            else -> Category.APP
        }
    }
}
