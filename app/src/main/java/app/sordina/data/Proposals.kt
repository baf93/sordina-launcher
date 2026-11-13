package app.sordina.data

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process
import app.sordina.data.brain.Brain

/**
 * Le proposte: le cose che è più probabile ti servano adesso (app, persone, comandi, casa).
 * Le decide il cervello ([Brain]); qui si aggiungono le fissate e si toglie quello che hai escluso.
 */
class Proposals(private val context: Context, private val brain: Brain, private val intents: Intents) {

    data class Result(
        /** Prima le fissate, poi quelle di adesso */
        val items: List<Candidate>,
        /** Perché c'è (per id della proposta) */
        val why: Map<String, String>,
        /** La cosa del cervello dietro ogni proposta (per id), per poterla escludere */
        val brainItem: Map<String, String>,
        /** Punteggio d'uso per pacchetto, usato anche per ordinare la ricerca */
        val usage: Map<String, Double>,
        val needsPermission: Boolean,
        /** Fascia di mezz'ora: le proposte non cambiano finché non cambia la fascia */
        val slot: Long,
    ) {
        val apps: List<AppEntry> get() = items.mapNotNull { it.app }
    }

    fun hasPermission(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun compute(apps: List<AppEntry>, prefs: Prefs, nowMs: Long = System.currentTimeMillis()): Result {
        val slot = slotOf(nowMs)
        val permission = hasPermission()
        if (permission) runCatching { brain.ingest(apps.mapTo(HashSet()) { it.packageName }) }
        val usage = brain.usage()

        fun appCandidate(a: AppEntry) = Candidate("app:${a.key}", a.label, null, Kind.APP, 60, false, Act.OpenApp(a), a)
        val fixed = prefs.pinned.mapNotNull { k -> apps.firstOrNull { it.key == k } }.distinctBy { it.key }.take(Prefs.MAX_PINNED)
        val fixedPkgs = fixed.mapTo(HashSet()) { it.packageName }
        val excluded = prefs.excluded
        val braked = prefs.braked

        val skip = { item: String ->
            val pkg = item.removePrefix("app:")
            when {
                item.startsWith("app:") -> pkg in braked || pkg in fixedPkgs || apps.none { it.packageName == pkg && it.key !in excluded }
                else -> "brain:$item" in excluded
            }
        }
        val suggestions = brain.suggest(COUNT, skip) { item ->
            intents.realize(item, apps.filter { it.key !in excluded }, usage)
        }
        val why = HashMap<String, String>()
        val brainItem = HashMap<String, String>()
        suggestions.forEach { why[it.candidate.id] = it.why; brainItem[it.candidate.id] = it.item }
        var now = suggestions.map { it.candidate }

        // Poca storia: completa con le app più usate
        if (now.size < COUNT) {
            val have = now.mapNotNullTo(HashSet()) { it.app?.packageName } + fixedPkgs
            val extra = usage.entries.sortedByDescending { it.value }.map { it.key }
                .filter { it !in have && it !in braked }
                .mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg && !it.isWork && it.key !in excluded } }
                .map(::appCandidate)
            now = (now + extra).take(COUNT)
        }
        fixed.forEach { why["app:${it.key}"] = app.sordina.tr(app.sordina.R.string.why_pinned) }
        return Result(fixed.map(::appCandidate) + now, why, brainItem, usage, !permission, slot)
    }

    companion object {
        const val COUNT = 6

        fun slotOf(ms: Long): Long = ms / (30 * 60_000L)
    }
}
