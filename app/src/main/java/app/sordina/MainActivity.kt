package app.sordina

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import app.sordina.data.AppCatalog
import app.sordina.data.AppEntry
import app.sordina.data.Prefs
import app.sordina.data.Proposals
import app.sordina.ui.SettingsScreen
import app.sordina.ui.apps.AllAppsScreen
import app.sordina.ui.home.HomeScreen
import app.sordina.ui.intent.IntentScreen
import app.sordina.ui.intent.ProposalActions
import app.sordina.ui.theme.S
import app.sordina.ui.theme.SordinaTheme
import app.sordina.ui.theme.paper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { HOME, INTENT, ALL_APPS, SETTINGS }

class MainActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private lateinit var catalog: AppCatalog
    private lateinit var proposalsEngine: Proposals
    private var appsCallback: LauncherApps.Callback? = null

    private var screen by mutableStateOf(Screen.HOME)
    private var apps by mutableStateOf<List<AppEntry>>(emptyList())
    private var proposals by mutableStateOf(Proposals.Result(emptyList(), emptyMap(), false, -1))
    private var family by mutableStateOf(app.sordina.ui.theme.ThemeFamily.CARTA)
    private var mode by mutableStateOf(app.sordina.ui.theme.ThemeMode.AUTO)
    private var hidden by mutableStateOf<Set<String>>(emptySet())
    private var prefsVersion by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        catalog = AppCatalog(this)
        proposalsEngine = Proposals(this)
        family = prefs.family
        mode = prefs.mode
        hidden = prefs.hidden

        appsCallback = catalog.registerChanges { reloadApps(forceProposals = true) }
        reloadApps(forceProposals = true)

        setContent {
            SordinaTheme(family, mode) {
                SystemBars()
                Root()
            }
        }
    }

    override fun onDestroy() {
        appsCallback?.let { catalog.unregister(it) }
        super.onDestroy()
    }

    /** Tasto Home: si torna sempre a Casa. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) screen = Screen.HOME
    }

    override fun onResume() {
        super.onResume()
        refreshProposals(force = false)
    }

    override fun onStop() {
        super.onStop()
        // Dopo aver aperto un'app, al ritorno si trova Casa
        screen = Screen.HOME
    }

    private fun reloadApps(forceProposals: Boolean) {
        lifecycleScope.launch {
            apps = withContext(Dispatchers.Default) { catalog.load() }
            refreshProposals(forceProposals)
        }
    }

    /** Le proposte cambiano solo al cambio di fascia (mezz'ora), mai mentre le guardi. */
    private fun refreshProposals(force: Boolean) {
        val slot = Proposals.slotOf(System.currentTimeMillis())
        val permissionChanged = proposals.needsPermission == proposalsEngine.hasPermission()
        if (!force && slot == proposals.slot && !permissionChanged) return
        val current = apps
        if (current.isEmpty()) return
        lifecycleScope.launch {
            proposals = withContext(Dispatchers.Default) { proposalsEngine.compute(current, prefs) }
        }
    }

    private fun launch(app: AppEntry) {
        catalog.launch(app)
    }

    private fun webSearch(q: String) {
        val search = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q)
        val ok = runCatching { startActivity(search) }.isSuccess
        if (!ok) runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://duckduckgo.com/?q=" + Uri.encode(q))))
        }
    }

    private fun openUsageSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
    }

    private fun openHomeSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
    }

    private fun isDefaultHome(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName == packageName
    }

    /** Scorri in giù da Casa: tendina delle notifiche di sistema. */
    @SuppressLint("WrongConstant")
    private fun expandNotifications() {
        runCatching {
            val sbs = getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sbs)
        }
    }

    @Composable
    private fun SystemBars() {
        val view = LocalView.current
        val dark = S.c.dark
        SideEffect {
            val c = WindowCompat.getInsetsController(window, view)
            c.isAppearanceLightStatusBars = !dark
            c.isAppearanceLightNavigationBars = !dark
        }
    }

    @Composable
    private fun Root() {
        BackHandler(enabled = screen != Screen.HOME) {
            screen = if (screen == Screen.ALL_APPS) Screen.INTENT else Screen.HOME
        }
        val actions = ProposalActions(
            isPinned = { a -> prefsVersion.let { prefs.pinned.contains(a.key) } },
            togglePin = { a ->
                val p = prefs.pinned
                prefs.pinned = if (a.key in p) p - a.key else (listOf(a.key) + p)
                prefsVersion++
                refreshProposals(force = true)
            },
            exclude = { a ->
                prefs.excluded = prefs.excluded + a.key
                prefs.pinned = prefs.pinned - a.key
                refreshProposals(force = true)
            },
            info = { a -> catalog.openInfo(a) },
        )
        Box(Modifier.fillMaxSize().paper()) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                label = "screen",
            ) { s ->
                when (s) {
                    Screen.HOME -> HomeScreen(
                        onSwipeUp = { screen = Screen.INTENT },
                        onSwipeDown = { expandNotifications() },
                        onLongPress = { screen = Screen.SETTINGS },
                    )
                    Screen.INTENT -> IntentScreen(
                        catalog = catalog,
                        apps = apps.filter { it.key !in hidden },
                        proposals = proposals.apps,
                        usage = proposals.usage,
                        needsUsagePermission = proposals.needsPermission,
                        actions = actions,
                        onLaunch = ::launch,
                        onWebSearch = ::webSearch,
                        onAllApps = { screen = Screen.ALL_APPS },
                        onGrantUsage = ::openUsageSettings,
                    )
                    Screen.ALL_APPS -> AllAppsScreen(
                        apps = apps,
                        hidden = hidden,
                        onLaunch = ::launch,
                        onInfo = { catalog.openInfo(it) },
                        onUninstall = { catalog.uninstall(it) },
                        onToggleHidden = { a ->
                            hidden = if (a.key in hidden) hidden - a.key else hidden + a.key
                            prefs.hidden = hidden
                        },
                    )
                    Screen.SETTINGS -> SettingsScreen(
                        family = family,
                        mode = mode,
                        usageGranted = !proposals.needsPermission,
                        isDefaultHome = isDefaultHome(),
                        version = BuildConfigVersion,
                        onFamily = { family = it; prefs.family = it },
                        onMode = { mode = it; prefs.mode = it },
                        onUsage = ::openUsageSettings,
                        onDefaultHome = ::openHomeSettings,
                    )
                }
            }
        }
        LaunchedEffect(screen) { if (screen == Screen.INTENT) refreshProposals(force = false) }
    }

    private val BuildConfigVersion: String
        get() = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"
}
