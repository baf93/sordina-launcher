package app.sordina.data

import android.app.PendingIntent
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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
    /** Titolo della notifica (per le app: l'oggetto), separato dal testo */
    val title: String? = null,
    /** Perché aspetta (o perché è passata): "predefinito", "app frenata", "lavoro, fuori orario"… */
    val reason: String? = null,
    /** Il canale (il "tipo" di notifica) e il suo nome, per le regole */
    val channel: String? = null,
    val channelName: String? = null,
    /** L'immagine della notifica, copiata in [NotifImages] (nome del file) */
    val image: String? = null,
    /** La conversazione come la identifica l'app (shortcutId), per i nomi collegati a una persona */
    val shortcut: String? = null,
    /** I messaggi della conversazione, dal più vecchio (una chat mette più messaggi in una notifica sola); vuoto = solo [text] */
    val lines: List<HeldLine> = emptyList(),
) {
    /** Raggruppa per persona; se non c'è una persona, per app. */
    val groupKey: String
        get() = if (isMessage && (conversation ?: person) != null) "p:" + Search.normalize(conversation ?: person!!) else "a:$pkg"
    val groupTitle: String
        get() = if (isMessage) (conversation ?: person ?: appLabel) else appLabel
}

/** Un messaggio dentro una notifica di chat: quando, chi (nei gruppi) e cosa. */
data class HeldLine(val time: Long, val sender: String?, val text: String)

/**
 * Elenchi di chiavi di notifica su file. Le chiavi possono contenere un a capo (WhatsApp, Revolut):
 * per questo si scrivono in JSON. Il vecchio formato a righe si rilegge ricucendo le chiavi spezzate.
 */
object KeyList {
    fun read(f: File): List<String> {
        if (!f.exists()) return emptyList()
        val raw = f.readText()
        if (raw.trimStart().startsWith("[")) {
            val a = JSONArray(raw)
            return (0 until a.length()).map { a.getString(it) }
        }
        return legacy(raw)
    }

    /** Righe del vecchio formato: una riga che comincia con "|" è il seguito della chiave di prima. */
    fun legacy(raw: String): List<String> {
        val out = ArrayList<String>()
        for (l in raw.split('\n')) {
            if (l.isEmpty()) continue
            if (l.startsWith("|") && out.isNotEmpty()) out[out.size - 1] = out.last() + "\n" + l else out += l
        }
        return out
    }

    fun write(f: File, keys: Collection<String>) = f.writeSafely(JSONArray(keys.toList()).toString())
}


/**
 * Archivio locale delle notifiche trattenute. Tutto in un file nell'app, niente esce dal telefono.
 * Le azioni di apertura (PendingIntent) restano solo in memoria: dopo un riavvio si recuperano
 * dalle notifiche posticipate, che Android conserva.
 */
class NotifStore(private val context: Context) {
    private val file = File(context.filesDir, "held.json")
    private val learnFile = File(context.filesDir, "person_apps.json")
    private val io = Executors.newSingleThreadScheduledExecutor()
    /** Una raffica di messaggi diventa una scrittura sola: si salva un attimo dopo l'ultima modifica */
    private val heldDirty = java.util.concurrent.atomic.AtomicBoolean(false)
    private val learnDirty = java.util.concurrent.atomic.AtomicBoolean(false)

    private val _held = MutableStateFlow<Map<String, Held>>(emptyMap())
    val held: StateFlow<Map<String, Held>> = _held


    private val intents = HashMap<String, PendingIntent>()
    private val dismissedFile = File(context.filesDir, "dismissed.txt")
    /**
     * Le notifiche segnate come lette, con il loro testo (un'impronta): se tornano uguali (Android che le restituisce
     * dopo la sospensione, o l'app che le ripubblica il giorno dopo) si chiudono subito, ogni volta.
     * Se sulla stessa notifica arriva un testo nuovo, è una notifica nuova e passa dalle regole.
     */
    private val read = LinkedHashMap<String, Pair<Int, Long>>()
    /** Persona normalizzata → (pacchetto → quante notifiche) */
    private val personApps = HashMap<String, HashMap<String, Int>>()

    init {
        runCatching { load() }
        runCatching { loadLearn() }
        runCatching {
            // "impronta<TAB>quando<TAB>chiave". I formati vecchi (senza quando, o senza impronta) si lasciano cadere:
            // al peggio una notifica già letta torna una volta, mai il contrario
            val now = System.currentTimeMillis()
            KeyList.read(dismissedFile).forEach { line ->
                val p = line.split('\t', limit = 3)
                val hash = p.getOrNull(0)?.toIntOrNull()
                val at = p.getOrNull(1)?.toLongOrNull()
                if (p.size == 3 && hash != null && at != null && now - at < READ_TTL) read[p[2]] = hash to at
            }
        }
    }

    fun get(key: String): Held? = _held.value[key]

    /** Segnata come letta: [text] è il testo che avevi davanti. */
    @Synchronized
    fun markRead(key: String, text: String) {
        read.remove(key)
        read[key] = text.hashCode() to System.currentTimeMillis()
        while (read.size > 1000) read.remove(read.keys.first())
        saveRead()
    }

    /**
     * La notifica [key] con questo [text] l'hai già letta? Se sì va chiusa. Un testo diverso è una notifica nuova:
     * si dimentica la lettura e passa.
     */
    @Synchronized
    fun alreadyRead(key: String, text: () -> String): Boolean {
        val (hash, at) = read[key] ?: return false
        if (System.currentTimeMillis() - at < READ_TTL && hash == text().hashCode()) return true
        read.remove(key); saveRead()
        return false
    }

    private fun saveRead() {
        val snap = read.map { (k, v) -> "${v.first}\t${v.second}\t$k" }
        io.execute { runCatching { KeyList.write(dismissedFile, snap) } }
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

    /** Raggruppato per persona, il gruppo con il messaggio più recente per primo. */
    fun groups(): List<List<Held>> {
        // Solo quello che è ancora trattenuto: ciò che è passato (subito o al riepilogo) sta nella tendina, non qui
        val now = System.currentTimeMillis()
        val waiting = _held.value.values.filter { it.releaseAt > now }.sortedBy { it.time }
        // Lo stesso pagamento da due app (banca e portafoglio, entro mezz'ora): se ne tiene uno
        val seen = HashMap<Long, Held>()
        val unique = waiting.filter { h ->
            val cents = if (h.isMessage) null else CardDetector.paymentCents((h.title ?: "") + " " + h.text)
            if (cents == null) return@filter true
            val first = seen[cents]
            if (first != null && first.pkg != h.pkg && h.time - first.time < 30 * 60_000) false else { seen[cents] = h; true }
        }
        return unique.groupBy { it.groupKey }.values
            .map { g -> g.sortedByDescending { it.time } }
            .sortedByDescending { it.first().time }
    }

    // App di messaggi abituale per persona
    @Synchronized
    private fun learn(person: String, pkg: String) {
        val p = Search.normalize(person)
        val m = personApps.getOrPut(p) { HashMap() }
        m[pkg] = (m[pkg] ?: 0) + 1
        // Limitato: chi ti ha scritto una volta sola tanto tempo fa non serve tenerlo per sempre
        if (personApps.size > MAX_PEOPLE) personApps.entries.minByOrNull { e -> e.value.values.sum() }?.let { personApps.remove(it.key) }
        saveLearn()
    }

    private companion object {
        const val SAVE_DELAY = 1_500L
        const val MAX_PEOPLE = 400
        /** Quanto vale una lettura: oltre, una notifica uguale è una notifica nuova (Android ne restituisce una posticipata entro un giorno) */
        const val READ_TTL = 48L * 3_600_000
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
        if (heldDirty.compareAndSet(false, true)) io.schedule({ heldDirty.set(false); writeHeld() }, SAVE_DELAY, java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    private fun writeHeld() {
        val snapshot = _held.value.values.toList()
        run {
            val arr = JSONArray()
            snapshot.forEach { h ->
                arr.put(JSONObject().apply {
                    put("key", h.key); put("pkg", h.pkg); put("app", h.appLabel)
                    put("person", h.person ?: JSONObject.NULL); put("conv", h.conversation ?: JSONObject.NULL)
                    put("text", h.text); put("time", h.time); put("level", h.level.name)
                    put("count", h.count); put("msg", h.isMessage)
                    put("release", h.releaseAt); put("post", h.postTime); put("title", h.title ?: JSONObject.NULL); put("why", h.reason ?: JSONObject.NULL); put("ch", h.channel ?: JSONObject.NULL); put("chn", h.channelName ?: JSONObject.NULL); put("img", h.image ?: JSONObject.NULL); put("sc", h.shortcut ?: JSONObject.NULL)
                    if (h.lines.isNotEmpty()) put("lines", JSONArray().apply {
                        h.lines.forEach { l -> put(JSONObject().apply { put("t", l.time); put("s", l.sender ?: JSONObject.NULL); put("x", l.text) }) }
                    })
                })
            }
            runCatching { file.writeSafely(arr.toString()) }
            // Le immagini delle notifiche lette se ne vanno con loro
            runCatching { NotifImages.prune(context, snapshot.mapNotNull { it.image }.toSet()) }
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
                title = o.optString("title").takeIf { !o.isNull("title") && it.isNotEmpty() },
                reason = o.optString("why").takeIf { !o.isNull("why") && it.isNotEmpty() },
                channel = o.optString("ch").takeIf { !o.isNull("ch") && it.isNotEmpty() },
                channelName = o.optString("chn").takeIf { !o.isNull("chn") && it.isNotEmpty() },
                image = o.optString("img").takeIf { !o.isNull("img") && it.isNotEmpty() },
                shortcut = o.optString("sc").takeIf { !o.isNull("sc") && it.isNotEmpty() },
                lines = o.optJSONArray("lines")?.let { a -> (0 until a.length()).map { j -> a.getJSONObject(j) }.map { l ->
                    HeldLine(l.optLong("t"), l.optString("s").takeIf { !l.isNull("s") && it.isNotEmpty() }, l.optString("x"))
                } } ?: emptyList(),
            )
            m[h.key] = h
        }
        _held.value = m
    }

    private fun saveLearn() {
        if (learnDirty.compareAndSet(false, true)) io.schedule({ learnDirty.set(false); writeLearn() }, SAVE_DELAY, java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    private fun writeLearn() {
        val snapshot = synchronized(this) { personApps.mapValues { HashMap(it.value) } }
        run {
            val o = JSONObject()
            snapshot.forEach { (p, apps) -> o.put(p, JSONObject(apps as Map<*, *>)) }
            runCatching { learnFile.writeSafely(o.toString()) }
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
        io.execute { runCatching { NotifImages.wipe(context) } }
        synchronized(this) { read.clear() }
        io.execute { runCatching { dismissedFile.delete() } }
        synchronized(this) { personApps.clear() }
        io.execute { runCatching { learnFile.delete() } }
    }
}
