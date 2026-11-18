package app.sordina.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.net.Uri
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.text.Collator
import java.util.Locale

/**
 * Un'app avviabile. La chiave distingue anche le app del profilo di lavoro.
 * Oppure una web app aggiunta dal browser (Brave, Samsung Internet, Firefox…): per Android è una scorciatoia
 * fissata sulla Home, [shortcut]; il pacchetto è quello del browser.
 */
class AppEntry(
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    val isWork: Boolean,
    internal val info: LauncherActivityInfo?,
    internal val shortcut: ShortcutInfo? = null,
) {
    val packageName: String get() = component.packageName
    /** Senza "/" per le web app: le regole per pacchetto (app nascoste) non devono toccare il browser */
    val key: String = (shortcut?.let { "${it.`package`}:web:${it.id}" } ?: component.flattenToShortString()) + "#" + user.hashCode()
    val isWeb: Boolean get() = shortcut != null
    val normalized: String = Search.normalize(label)
    /** La lettera dell'elenco ("#" se non comincia con una lettera): calcolata una volta, non a ogni apertura */
    val letter: String by lazy { normalized.firstOrNull()?.takeIf { it.isLetter() }?.uppercaseChar()?.toString() ?: "#" }
}

/** Catalogo delle app installate, letto da LauncherApps (funziona per ogni profilo). */
class AppCatalog(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val iconCache = HashMap<String, ImageBitmap>()

    fun load(): List<AppEntry> {
        val me = Process.myUserHandle()
        val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
        return userManager.userProfiles.flatMap { user ->
            runCatching { launcherApps.getActivityList(null, user) }.getOrDefault(emptyList()).map { info ->
                AppEntry(
                    label = info.label?.toString()?.trim().orEmpty().ifEmpty { info.componentName.packageName },
                    component = info.componentName,
                    user = user,
                    isWork = user != me,
                    info = info,
                )
            } + webApps(user, user != me)
        }
            .filter { it.packageName != context.packageName }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    /**
     * Le web app fissate su Sordina (vedi [app.sordina.PinShortcutActivity]). Android le mostra solo alla Home
     * predefinita, e solo quelle fissate su di lei: quelle aggiunte prima, quando c'era un'altra Home, restano là.
     */
    private fun webApps(user: UserHandle, work: Boolean): List<AppEntry> = runCatching {
        if (!launcherApps.hasShortcutHostPermission()) return@runCatching emptyList()
        val q = LauncherApps.ShortcutQuery().setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
        launcherApps.getShortcuts(q, user).orEmpty().filter { it.isEnabled }.map { s ->
            AppEntry(
                label = (s.shortLabel ?: s.longLabel)?.toString()?.trim().orEmpty().ifEmpty { s.id },
                component = s.activity ?: ComponentName(s.`package`, ""),
                user = user, isWork = work, info = null, shortcut = s,
            )
        }
    }.getOrDefault(emptyList())

    fun launch(app: AppEntry) {
        val s = app.shortcut
        if (s != null) runCatching { launcherApps.startShortcut(s, null, null) }
        else runCatching { launcherApps.startMainActivity(app.component, app.user, null, null) }
    }

    fun openInfo(app: AppEntry) {
        runCatching { launcherApps.startAppDetailsActivity(app.component, app.user, Rect(), null) }
    }

    fun uninstall(app: AppEntry) {
        // Una web app si toglie e basta: restano fissate le altre dello stesso browser
        app.shortcut?.let { s ->
            runCatching {
                val q = LauncherApps.ShortcutQuery().setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED).setPackage(s.`package`)
                val keep = launcherApps.getShortcuts(q, app.user).orEmpty().map { it.id }.filter { it != s.id }
                launcherApps.pinShortcuts(s.`package`, keep, app.user)
            }
            return
        }
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** Una sola misura per tutte le icone: lista, ricerca e freno la rimpiccioliscono disegnandola. */
    private val iconPx = (36 * context.resources.displayMetrics.density).toInt()

    /** L'icona se è già pronta, senza mai caricarla: si disegna subito senza bloccare lo scorrimento. */
    fun cachedIcon(app: AppEntry): ImageBitmap? = synchronized(iconCache) { iconCache[app.key] }

    /** Icona piccola, caricata solo quando serve e tenuta in memoria. Meglio chiamarla fuori dal thread principale. */
    fun icon(app: AppEntry): ImageBitmap? {
        cachedIcon(app)?.let { return it }
        val bmp = runCatching {
            val d = app.shortcut?.let { launcherApps.getShortcutIconDrawable(it, context.resources.displayMetrics.densityDpi) } ?: app.info!!.getIcon(0)
            AppIcons.render(d, iconPx).asImageBitmap()
        }.getOrNull() ?: return null
        return synchronized(iconCache) { iconCache.getOrPut(app.key) { bmp } }
    }

    /** L'icona dell'app che esegue un'azione (vedi [ActHandler]); null se la fa Sordina da sola. */
    fun actionIcon(act: Act): ImageBitmap? {
        val pkg = ActHandler.packageOf(context, act) ?: return null
        val key = "act:$pkg"
        synchronized(iconCache) { iconCache[key] }?.let { return it }
        val bmp = runCatching {
            val d = launcherApps.getActivityList(pkg, Process.myUserHandle()).firstOrNull()?.getIcon(0)
                ?: context.packageManager.getApplicationIcon(pkg)
            AppIcons.render(d, iconPx).asImageBitmap()
        }.getOrNull() ?: return null
        return synchronized(iconCache) { iconCache.getOrPut(key) { bmp } }
    }

    /** Prepara in sottofondo le icone di tutte le app, così la lista scorre liscia. */
    fun warmIcons(apps: List<AppEntry>) { apps.forEach { icon(it) } }

    fun registerChanges(onChange: () -> Unit): LauncherApps.Callback {
        val cb = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String?, user: UserHandle?) {
                // Hai tolto un'app che avevi frenato: un'impresa
                val prefs = app.sordina.SordinaApp.of(context).prefs
                if (packageName != null && packageName in prefs.braked) app.sordina.data.Tally.flag("farewell")
                // Rimossa davvero (non un aggiornamento: lì arriva onPackageChanged)
                if (packageName != null) runCatching { prefs.forgetApp(packageName) }
                onChange()
            }
            override fun onPackageAdded(packageName: String?, user: UserHandle?) = onChange()
            override fun onPackageChanged(packageName: String?, user: UserHandle?) = onChange()
            override fun onPackagesAvailable(p: Array<out String>?, u: UserHandle?, r: Boolean) = onChange()
            override fun onPackagesUnavailable(p: Array<out String>?, u: UserHandle?, r: Boolean) = onChange()
            // Una web app fissata o tolta. Le app cambiano spesso le loro scorciatoie (WhatsApp con ogni chat):
            // si ricarica solo se cambiano quelle fissate qui.
            private val pinned = HashMap<String, Set<String>>()
            override fun onShortcutsChanged(p: String, s: MutableList<ShortcutInfo>, u: UserHandle) {
                val now = s.filter { it.isPinned }.mapTo(HashSet()) { it.id }
                val k = "$p#${u.hashCode()}"
                if ((pinned.put(k, now) ?: emptySet<String>()) != now) onChange()
            }
        }
        launcherApps.registerCallback(cb)
        return cb
    }

    fun unregister(cb: LauncherApps.Callback) = launcherApps.unregisterCallback(cb)
}

/** In auto: le app che sanno portarti in un posto e quelle che suonano (musica, podcast, audiolibri). */
object CarApps {
    /** Le scelte di base, in ordine: la prima installata vince. */
    private val NAV_FIRST = listOf("com.google.android.apps.maps", "com.waze", "net.osmand.plus", "net.osmand", "com.here.app.maps", "com.sygic.aura")
    private val MUSIC_FIRST = listOf("com.spotify.music", "com.google.android.apps.youtube.music", "deezer.android.app", "com.amazon.mp3", "com.apple.android.music", "com.aspiro.tidal")
    /** La casa: Home Assistant, se no Google Home, SmartThings o Alexa. */
    private val HOME_FIRST = CardDetector.HA_PKGS.toList() + listOf("com.google.android.apps.chromecast.app", "com.samsung.android.oneconnect", "com.amazon.dee.app")

    private fun first(order: List<String>, apps: List<AppEntry>): String? =
        order.firstOrNull { p -> apps.any { it.packageName == p && !it.isWork } }

    fun preferredNav(apps: List<AppEntry>) = first(NAV_FIRST, apps)
    fun preferredMusic(apps: List<AppEntry>) = first(MUSIC_FIRST, apps)
    fun preferredHome(apps: List<AppEntry>) = first(HOME_FIRST, apps)

    fun navigation(context: android.content.Context, apps: List<AppEntry>): List<AppEntry> {
        val pm = context.packageManager
        val pkgs = runCatching {
            pm.queryIntentActivities(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=Roma")), 0)
                .map { it.activityInfo.packageName }.toSet()
        }.getOrDefault(emptySet())
        return apps.filter { !it.isWork && it.packageName in pkgs }.distinctBy { it.packageName }.sortedBy { it.normalized }
    }

    fun music(context: android.content.Context, apps: List<AppEntry>): List<AppEntry> {
        val pm = context.packageManager
        val pkgs = runCatching {
            pm.queryIntentServices(android.content.Intent("android.media.browse.MediaBrowserService"), 0).map { it.serviceInfo.packageName }.toSet()
        }.getOrDefault(emptySet())
        return apps.filter { !it.isWork && it.packageName in pkgs }.distinctBy { it.packageName }.sortedBy { it.normalized }
    }
}

/**
 * Tutte le icone con la stessa forma: le adattive prendono la maschera del telefono;
 * le vecchie icone quadrate a pieno campo vanno nella stessa maschera, così niente spigoli vivi.
 * Le vecchie icone che hanno già una forma loro (tonde, ritagliate) restano come sono.
 */
object AppIcons {
    fun render(d: android.graphics.drawable.Drawable, px: Int): android.graphics.Bitmap {
        val shaped = if (d is android.graphics.drawable.AdaptiveIconDrawable || !fullBleed(d)) d
        else android.graphics.drawable.AdaptiveIconDrawable(
            android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE),
            // Il livello davanti è 1,5 volte la parte visibile: con 1/6 per lato l'icona riempie la maschera
            android.graphics.drawable.InsetDrawable(d, 0.16f),
        )
        return shaped.toBitmap(px, px)
    }

    /** L'icona di un pacchetto qualsiasi (anche app di sistema senza voce nel cassetto), già nella forma giusta. */
    fun forPackage(pm: android.content.pm.PackageManager, pkg: String, px: Int): ImageBitmap? =
        runCatching { render(pm.getApplicationIcon(pkg), px).asImageBitmap() }.getOrNull()

    /** Una vecchia icona che riempie tutto il quadrato: angoli pieni, quindi spigoli vivi. */
    private fun fullBleed(d: android.graphics.drawable.Drawable): Boolean {
        val s = 48
        val b = runCatching { d.toBitmap(s, s) }.getOrNull() ?: return false
        val m = 2
        val corners = intArrayOf(b.getPixel(m, m), b.getPixel(s - 1 - m, m), b.getPixel(m, s - 1 - m), b.getPixel(s - 1 - m, s - 1 - m))
        return corners.all { android.graphics.Color.alpha(it) > 200 }
    }
}
