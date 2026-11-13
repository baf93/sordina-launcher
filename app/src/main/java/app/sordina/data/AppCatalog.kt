package app.sordina.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
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

/** Un'app avviabile. La chiave distingue anche le app del profilo di lavoro. */
class AppEntry(
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    val isWork: Boolean,
    internal val info: LauncherActivityInfo,
) {
    val packageName: String get() = component.packageName
    val key: String = component.flattenToShortString() + "#" + user.hashCode()
    val normalized: String = Search.normalize(label)
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
            }
        }
            .filter { it.packageName != context.packageName }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    fun launch(app: AppEntry) {
        runCatching { launcherApps.startMainActivity(app.component, app.user, null, null) }
    }

    fun openInfo(app: AppEntry) {
        runCatching { launcherApps.startAppDetailsActivity(app.component, app.user, Rect(), null) }
    }

    fun uninstall(app: AppEntry) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** Icona piccola, caricata solo quando serve e tenuta in memoria. */
    fun icon(app: AppEntry, sizePx: Int): ImageBitmap? = synchronized(iconCache) {
        iconCache[app.key] ?: runCatching {
            app.info.getIcon(0).toBitmap(sizePx, sizePx).asImageBitmap()
        }.getOrNull()?.also { iconCache[app.key] = it }
    }

    fun registerChanges(onChange: () -> Unit): LauncherApps.Callback {
        val cb = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String?, user: UserHandle?) = onChange()
            override fun onPackageAdded(packageName: String?, user: UserHandle?) = onChange()
            override fun onPackageChanged(packageName: String?, user: UserHandle?) = onChange()
            override fun onPackagesAvailable(p: Array<out String>?, u: UserHandle?, r: Boolean) = onChange()
            override fun onPackagesUnavailable(p: Array<out String>?, u: UserHandle?, r: Boolean) = onChange()
        }
        launcherApps.registerCallback(cb)
        return cb
    }

    fun unregister(cb: LauncherApps.Callback) = launcherApps.unregisterCallback(cb)
}

/** In auto: le app che sanno portarti in un posto e quelle che suonano (musica, podcast, audiolibri). */
object CarApps {
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
