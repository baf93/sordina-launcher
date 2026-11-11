package app.sordina.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.Executors

/**
 * "Di solito": Sordina ricorda i momenti (arrivo a casa, in auto, cuffie, in carica la sera)
 * e cosa fai subito dopo. Poi lo propone con una scheda. Non fa mai niente da sola.
 */
class Routines(private val context: Context) {
    private val file = File(context.filesDir, "routines.json")
    private val io = Executors.newSingleThreadExecutor()
    private val episodes = ArrayList<Pair<String, Long>>()
    private val commands = ArrayList<Triple<String, String, Long>>() // (etichetta, frase, quando)

    init { runCatching { load() } }

    @Synchronized
    fun onContext(ctx: String, t: Long = System.currentTimeMillis()) {
        // Stesso momento entro mezz'ora: è lo stesso episodio (es. Wi-Fi che va e viene)
        if (episodes.any { it.first == ctx && t - it.second < 30 * 60_000 }) return
        episodes += ctx to t
        prune(t)
        save()
    }

    @Synchronized
    fun onCommand(label: String, query: String, t: Long = System.currentTimeMillis()) {
        if (query.isBlank()) return
        commands += Triple(label, query, t)
        prune(t)
        save()
    }

    private fun prune(now: Long) {
        val limit = now - 45L * 86_400_000
        episodes.removeAll { it.second < limit }
        commands.removeAll { it.third < limit }
    }

    /** Nome breve del momento, per la scheda. */
    @Synchronized
    fun label(ctx: String): String = when {
        ctx == "car" -> "In auto"
        ctx == "headset" -> "Cuffie"
        ctx == "charge" -> "In carica"
        ctx.startsWith("wifi:") -> if (ctx == homeWifi()) "A casa" else "Wi-Fi " + ctx.removePrefix("wifi:")
        else -> ctx
    }

    /** Il Wi-Fi dove arrivi più spesso la sera: è casa. */
    private fun homeWifi(): String? = episodes.filter { it.first.startsWith("wifi:") }
        .filter { val h = Instant.ofEpochMilli(it.second).atZone(ZoneId.systemDefault()).hour; h >= 17 || h < 9 }
        .groupingBy { it.first }.eachCount().maxByOrNull { it.value }?.key

    /**
     * Le schede "di solito" per i momenti iniziati negli ultimi 20 minuti.
     * Serve l'accesso all'utilizzo per sapere quali app apri.
     */
    fun suggestions(apps: List<AppEntry>, now: Long = System.currentTimeMillis()): List<Card> {
        val (active, past, cmds) = synchronized(this) {
            Triple(
                episodes.filter { now - it.second in 0..20 * 60_000 }.groupBy { it.first }.mapValues { e -> e.value.maxOf { it.second } },
                episodes.toList(),
                commands.toList(),
            )
        }
        if (active.isEmpty()) return emptyList()
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val labels = apps.associate { it.packageName to it.label }
        val skip = setOf(context.packageName, "com.android.systemui", "com.sec.android.app.launcher", "com.google.android.apps.nexuslauncher")
        val out = ArrayList<Card>()
        for ((ctx, start) in active) {
            val before = past.filter { it.first == ctx && it.second < start - 60 * 60_000 }
            if (before.size < 3) continue
            val counts = HashMap<String, Int>()
            for ((_, t) in before) {
                val seen = HashSet<String>()
                opened(usm, t, t + 10 * 60_000).filter { it !in skip && it in labels }.forEach { seen += "app:$it" }
                cmds.filter { it.third in t..(t + 10 * 60_000) }.forEach { seen += "cmd:${it.second}" }
                seen.forEach { counts[it] = (counts[it] ?: 0) + 1 }
            }
            val already = opened(usm, start, now).map { "app:$it" }.toSet() +
                cmds.filter { it.third >= start }.map { "cmd:${it.second}" }
            val best = counts.entries.filter { it.value >= 3 && it.value * 2 >= before.size && it.key !in already }
                .sortedByDescending { it.value }.take(2)
            for ((item, _) in best) {
                val name = label(ctx)
                out += if (item.startsWith("app:")) {
                    val pkg = item.removePrefix("app:")
                    Card("habit:$ctx:$item", CardKind.HABIT, "$name · di solito", labels[pkg] ?: pkg,
                        actions = listOf(CardAction("Apri", CardOp.OpenPackage(pkg))), time = start)
                } else {
                    val q = item.removePrefix("cmd:")
                    val l = cmds.lastOrNull { it.second == q }?.first ?: q
                    Card("habit:$ctx:$item", CardKind.HABIT, "$name · di solito", l,
                        actions = listOf(CardAction("Fallo", CardOp.Query(q))), time = start)
                }
            }
        }
        return out
    }

    private fun opened(usm: UsageStatsManager, from: Long, to: Long): List<String> = runCatching {
        val ev = usm.queryEvents(from, to)
        val e = UsageEvents.Event()
        val out = ArrayList<String>()
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) out += e.packageName
        }
        out.distinct()
    }.getOrDefault(emptyList())

    private fun save() {
        val ep = episodes.toList()
        val cm = commands.toList()
        io.execute {
            val o = JSONObject()
            o.put("e", JSONArray().apply { ep.forEach { put(JSONArray().put(it.first).put(it.second)) } })
            o.put("c", JSONArray().apply { cm.forEach { put(JSONArray().put(it.first).put(it.second).put(it.third)) } })
            runCatching { file.writeText(o.toString()) }
        }
    }

    private fun load() {
        if (!file.exists()) return
        val o = JSONObject(file.readText())
        o.optJSONArray("e")?.let { a -> for (i in 0 until a.length()) a.getJSONArray(i).let { episodes += it.getString(0) to it.getLong(1) } }
        o.optJSONArray("c")?.let { a -> for (i in 0 until a.length()) a.getJSONArray(i).let { commands += Triple(it.getString(0), it.getString(1), it.getLong(2)) } }
    }

    @Synchronized
    fun wipe() {
        episodes.clear(); commands.clear()
        io.execute { runCatching { file.delete() } }
    }
}
