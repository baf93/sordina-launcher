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

    fun compute(allApps: List<AppEntry>, prefs: Prefs, nowMs: Long = System.currentTimeMillis()): Result {
        // Le app nascoste non si propongono mai, nemmeno se fissate
        val hidden = prefs.hidden
        val apps = allApps.filter { it.key !in hidden }
        val slot = slotOf(nowMs)
        val permission = hasPermission()
        if (permission) runCatching { brain.ingest(apps.mapTo(HashSet()) { it.packageName }) }
        val usage = brain.usage()

        fun appCandidate(a: AppEntry) = Candidate("app:${a.key}", a.label, null, Kind.APP, 60, false, Act.OpenApp(a), a)
        val excluded = prefs.excluded
        val braked = prefs.braked
        // Le fissate: app (la loro chiave) oppure azioni ("do:" + la cosa del cervello, es. scrivi a Elisa su Telegram)
        val fixedItem = HashMap<String, String>()
        val fixedC = prefs.pinned.mapNotNull { k ->
            if (k.startsWith(Prefs.PIN_ACTION)) {
                val item = k.removePrefix(Prefs.PIN_ACTION)
                runCatching { intents.realize(item, apps.filter { it.key !in excluded }, usage) }.getOrNull()?.also { fixedItem[it.id] = item }
            } else apps.firstOrNull { it.key == k }?.let(::appCandidate)
        }.distinctBy { it.id }.take(Prefs.MAX_PINNED)
        val fixedPkgs = fixedC.mapNotNullTo(HashSet()) { it.app?.packageName }
        val fixedItems = fixedItem.values.toSet()

        val skip = { item: String ->
            val pkg = item.removePrefix("app:")
            when {
                item in fixedItems -> true
                item.startsWith("app:") -> pkg in braked || pkg in fixedPkgs || apps.none { it.packageName == pkg && it.key !in excluded }
                else -> "brain:$item" in excluded
            }
        }
        // Sempre COUNT blocchi in tutto: le fissate prima, le proposte riempiono il resto
        val count = (COUNT - fixedC.size).coerceAtLeast(0)
        val suggestions = if (count == 0) emptyList() else brain.suggest(count, skip, runCatching { boosts(apps, nowMs) }.getOrDefault(emptyMap())) { item ->
            intents.realize(item, apps.filter { it.key !in excluded }, usage)
        }
        val why = HashMap<String, String>()
        val brainItem = HashMap<String, String>()
        suggestions.forEach { why[it.candidate.id] = it.why; brainItem[it.candidate.id] = it.item }
        brainItem.putAll(fixedItem)
        var now = suggestions.map { it.candidate }

        // Poca storia: completa con le app più usate
        if (now.size < count) {
            val have = now.mapNotNullTo(HashSet()) { it.app?.packageName } + fixedPkgs
            val extra = usage.entries.sortedByDescending { it.value }.map { it.key }
                .filter { it !in have && it !in braked }
                .mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg && !it.isWork && it.key !in excluded } }
                .map(::appCandidate)
            now = (now + extra).take(count)
        }
        // Ancora pochi (telefono nuovo, niente accesso all'uso): le altre app, in ordine
        if (now.size < count) {
            val have = now.mapNotNullTo(HashSet()) { it.app?.packageName } + fixedPkgs
            now = now + apps.filter { !it.isWork && it.key !in excluded && it.packageName !in braked && it.packageName !in have }
                .distinctBy { it.packageName }.take(count - now.size).map(::appCandidate)
        }
        fixedC.forEach { why[it.id] = app.sordina.tr(app.sordina.R.string.why_pinned) }
        return Result((fixedC + now).take(COUNT), why, brainItem, usage, !permission, slot)
    }

    /**
     * Quello che sta per succedere spinge in alto le cose giuste, anche se non è la tua abitudine:
     * una riunione Teams tra 10 minuti porta su Teams, un impegno con un indirizzo porta su Maps.
     */
    private var cachedEvents: Pair<Long, List<Upcoming>>? = null

    private fun boosts(apps: List<AppEntry>, now: Long): Map<String, Pair<Double, String>> {
        val out = HashMap<String, Pair<Double, String>>()
        fun has(pkg: String) = apps.any { it.packageName == pkg }
        fun put(pkg: String, gain: Double, why: String) {
            if (has(pkg) && (out["app:$pkg"]?.first ?: 0.0) < gain) out["app:$pkg"] = gain to why
        }
        val sa = app.sordina.SordinaApp.of(context)
        // Il calendario si rilegge al massimo una volta al minuto (Casa lo legge già per le sue schede)
        val upcoming = cachedEvents?.takeIf { now - it.first < 60_000 }?.second
            ?: sa.calendars.upcoming(context, now).also { cachedEvents = now to it }
        for (e in upcoming) {
            val min = (e.begin - now) / 60_000
            if (min !in -10..60) continue
            val soon = min in -10..15
            MEETING_APPS.entries.firstOrNull { (host, _) -> e.link?.contains(host) == true }?.let { (_, pkgs) ->
                pkgs.forEach { put(it, if (soon) 4.0 else 1.5, app.sordina.tr(app.sordina.R.string.why_meeting, e.title)) }
            }
            if (e.location != null && e.link == null && min in 0..60) put(MAPS, if (min <= 30) 3.0 else 1.5, app.sordina.tr(app.sordina.R.string.why_event_place, e.title))
        }
        // Riunioni avvisate dalle app (Outlook, Teams): l'app che avvisa, e Teams se è una riunione Teams
        for (k in sa.signals.cards.value.values) {
            val start = k.startsAt ?: continue
            val min = (start - now) / 60_000
            if (min !in -10..15) continue
            val pkg = (k.sources.firstOrNull() ?: k.key).split('|').getOrNull(1) ?: continue
            val why = app.sordina.tr(app.sordina.R.string.why_meeting, k.title)
            put(pkg, 3.0, why)
            if ("teams" in "${k.title} ${k.subtitle}".lowercase()) MEETING_APPS["teams"]?.forEach { put(it, 4.0, why) }
        }
        return out
    }

    companion object {
        private const val MAPS = "com.google.android.apps.maps"
        private val MEETING_APPS = mapOf(
            "teams" to listOf("com.microsoft.teams"),
            "zoom.us" to listOf("us.zoom.videomeetings"),
            "meet.google" to listOf("com.google.android.apps.tachyon", "com.google.android.apps.meetings"),
        )
        const val COUNT = 6

        fun slotOf(ms: Long): Long = ms / (30 * 60_000L)
    }
}
