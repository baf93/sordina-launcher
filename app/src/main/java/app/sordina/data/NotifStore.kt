package app.sordina.data

import android.app.PendingIntent
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.concurrent.Executors

/** Una notifica che Sordina tiene da parte (o ha lasciato passare in silenzio). */
data class Held(
    val key: String,
    val pkg: String,
    val appLabel: String,
    /** Chi scrive: mittente o titolo */
    val person: String?,
    /** Gruppo o conversazione, se diverso dalla persona */
    val conversation: String?,
    val text: String,
    val time: Long,
    val level: Level,
    val count: Int,
    val isMessage: Boolean,
    /** Quando torna visibile da sola (posticipata fino al riepilogo); 0 se è già visibile */
    val releaseAt: Long = 0,
    /** postTime della notifica originale, per riconoscerla quando Android la ripubblica */
    val postTime: Long = 0,
) {
    /** Raggruppa per persona; se non c'è una persona, per app. */
    val groupKey: String
        get() = if (isMessage && (conversation ?: person) != null) "p:" + Search.normalize(conversation ?: person!!) else "a:$pkg"
    val groupTitle: String
        get() = if (isMessage) (conversation ?: person ?: appLabel) else appLabel
}

/** Decisione presa su una notifica, per la pagina "Oggi". */
data class Decision(val time: Long, val pkg: String, val appLabel: String, val level: Level?, val reason: String)

/**
 * Archivio locale delle notifiche trattenute. Tutto in un file nell'app, niente esce dal telefono.
 * Le azioni di apertura (PendingIntent) restano solo in memoria: dopo un riavvio si recuperano
 * dalle notifiche posticipate, che Android conserva.
 */
class NotifStore(context: Context) {
    private val file = File(context.filesDir, "held.json")
    private val learnFile = File(context.filesDir, "person_apps.json")
    private val io = Executors.newSingleThreadExecutor()

    private val _held = MutableStateFlow<Map<String, Held>>(emptyMap())
    val held: StateFlow<Map<String, Held>> = _held

    private val _today = MutableStateFlow<List<Decision>>(emptyList())
    val today: StateFlow<List<Decision>> = _today
    private var todayDate = LocalDate.now()

    private val intents = HashMap<String, PendingIntent>()
    private val dismissedFile = File(context.filesDir, "dismissed.txt")
    /** Notifiche segnate come lette mentre erano posticipate: quando tornano, si chiudono subito. */
    private val dismissed = LinkedHashSet<String>()
    /** Persona normalizzata → (pacchetto → quante notifiche) */
    private val personApps = HashMap<String, HashMap<String, Int>>()

    init {
        runCatching { load() }
        runCatching { loadLearn() }
        runCatching { if (dismissedFile.exists()) dismissed += dismissedFile.readLines().filter { it.isNotBlank() } }
    }

    fun get(key: String): Held? = _held.value[key]

    @Synchronized
    fun markDismissed(key: String) {
        dismissed += key
        while (dismissed.size > 500) dismissed.remove(dismissed.first())
        saveDismissed()
    }

    @Synchronized
    fun takeDismissed(key: String): Boolean {
        if (!dismissed.remove(key)) return false
        saveDismissed()
        return true
    }

    private fun saveDismissed() {
        val snap = dismissed.toList()
        io.execute { runCatching { dismissedFile.writeText(snap.joinToString("\n")) } }
    }

    @Synchronized
    fun put(h: Held, intent: PendingIntent?) {
        _held.value = _held.value + (h.key to h)
        if (intent != null) intents[h.key] = intent
        if (h.isMessage && h.person != null) learn(h.person, h.pkg)
        save()
    }

    @Synchronized
    fun remove(key: String) {
        if (key !in _held.value) return
        _held.value = _held.value - key
        intents.remove(key)
        save()
    }

    @Synchronized
    fun removeGroup(groupKey: String): List<String> {
        val keys = _held.value.values.filter { it.groupKey == groupKey }.map { it.key }
        _held.value = _held.value - keys.toSet()
        keys.forEach { intents.remove(it) }
        save()
        return keys
    }

    @Synchronized
    fun clear(): List<String> {
        val keys = _held.value.keys.toList()
        _held.value = emptyMap()
        intents.clear()
        save()
        return keys
    }

    fun intentFor(key: String): PendingIntent? = synchronized(this) { intents[key] }

    @Synchronized
    fun attachIntent(key: String, intent: PendingIntent?) {
        if (intent != null) intents[key] = intent
    }

    @Synchronized
    fun log(d: Decision) {
        val now = LocalDate.now()
        if (now != todayDate) { todayDate = now; _today.value = emptyList() }
        _today.value = (_today.value + d).takeLast(300)
    }

    /** Raggruppato per persona, il gruppo con il messaggio più recente per primo. */
    fun groups(): List<List<Held>> =
        _held.value.values.groupBy { it.groupKey }.values
            .map { g -> g.sortedByDescending { it.time } }
            .sortedByDescending { it.first().time }

    // App di messaggi abituale per persona
    @Synchronized
    private fun learn(person: String, pkg: String) {
        val p = Search.normalize(person)
        val m = personApps.getOrPut(p) { HashMap() }
        m[pkg] = (m[pkg] ?: 0) + 1
        saveLearn()
    }

    /** Le app con cui questa persona ti scrive, la più usata per prima. */
    @Synchronized
    fun appsForPerson(name: String): List<String> {
        val n = Search.normalize(name)
        val first = n.substringBefore(' ')
        val match = personApps.entries.filter { (k, _) -> k == n || k.startsWith("$first ") || k == first }
        return match.flatMap { it.value.entries }.groupBy { it.key }
            .mapValues { e -> e.value.sumOf { it.value } }
            .entries.sortedByDescending { it.value }.map { it.key }
    }

    // Persistenza
    private fun save() {
        val snapshot = _held.value.values.toList()
        io.execute {
            val arr = JSONArray()
            snapshot.forEach { h ->
                arr.put(JSONObject().apply {
                    put("key", h.key); put("pkg", h.pkg); put("app", h.appLabel)
                    put("person", h.person ?: JSONObject.NULL); put("conv", h.conversation ?: JSONObject.NULL)
                    put("text", h.text); put("time", h.time); put("level", h.level.name)
                    put("count", h.count); put("msg", h.isMessage)
                    put("release", h.releaseAt); put("post", h.postTime)
                })
            }
            runCatching { file.writeText(arr.toString()) }
        }
    }

    private fun load() {
        if (!file.exists()) return
        val arr = JSONArray(file.readText())
        val m = HashMap<String, Held>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val h = Held(
                key = o.getString("key"), pkg = o.getString("pkg"), appLabel = o.optString("app"),
                person = o.optString("person").takeIf { !o.isNull("person") && it.isNotEmpty() },
                conversation = o.optString("conv").takeIf { !o.isNull("conv") && it.isNotEmpty() },
                text = o.optString("text"), time = o.optLong("time"),
                level = runCatching { Level.valueOf(o.getString("level")) }.getOrDefault(Level.RIEPILOGO),
                count = o.optInt("count", 1), isMessage = o.optBoolean("msg"),
                releaseAt = o.optLong("release"), postTime = o.optLong("post"),
            )
            m[h.key] = h
        }
        _held.value = m
    }

    private fun saveLearn() {
        val snapshot = personApps.mapValues { HashMap(it.value) }
        io.execute {
            val o = JSONObject()
            snapshot.forEach { (p, apps) -> o.put(p, JSONObject(apps as Map<*, *>)) }
            runCatching { learnFile.writeText(o.toString()) }
        }
    }

    private fun loadLearn() {
        if (!learnFile.exists()) return
        val o = JSONObject(learnFile.readText())
        for (p in o.keys()) {
            val a = o.getJSONObject(p)
            val m = HashMap<String, Int>()
            for (k in a.keys()) m[k] = a.getInt(k)
            personApps[p] = m
        }
    }

    fun wipe() {
        clear()
        synchronized(this) { dismissed.clear(); _today.value = emptyList() }
        io.execute { runCatching { dismissedFile.delete() } }
        synchronized(this) { personApps.clear() }
        io.execute { runCatching { learnFile.delete() } }
    }
}
