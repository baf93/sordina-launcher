package app.sordina.data.brain

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import app.sordina.R
import app.sordina.data.Act
import app.sordina.data.AppEntry
import app.sordina.data.Candidate
import app.sordina.data.Contact
import app.sordina.data.Kind
import app.sordina.data.NotifStore
import app.sordina.data.People
import app.sordina.data.Prefs
import app.sordina.data.Search
import app.sordina.data.Via
import app.sordina.tr
import java.io.File
import java.util.concurrent.Executors

/** Una proposta: la cosa da fare, quanto è probabile, e perché. */
data class Suggestion(val candidate: Candidate, val item: String, val score: Double, val why: String)

/**
 * Il cervello di Sordina: ricorda cosa fai e in che situazione, e prevede cosa ti serve adesso.
 *
 * - Memoria propria (120 giorni, in un file dell'app): la cronologia di Android dura pochi giorni.
 * - Impara dalle app che apri (cronologia d'uso), da quello che fai in Sordina (chiamate, messaggi,
 *   comandi di casa, timer…), dalle notifiche che tocchi (chi ti scrive e a chi rispondi)
 *   e da quello che scrivi nella barra (quali lettere portano a cosa).
 * - Niente esce dal telefono.
 */
class Brain(
    private val context: Context,
    private val prefs: Prefs,
    private val people: People,
    private val store: NotifStore,
) {
    private val file = File(context.filesDir, "brain.tsv")
    private val io = Executors.newSingleThreadExecutor()
    private val sp = context.getSharedPreferences("brain", Context.MODE_PRIVATE)
    @Volatile private var model = Model()
    @Volatile private var loaded = false

    /** I cambi di situazione (Wi-Fi, auto, cuffie, carica), per ricostruire com'era quando hai aperto un'app. */
    private val timeline = ArrayList<Triple<Long, String, String>>()

    // Stato della lettura della cronologia d'uso
    /** L'app in primo piano adesso ("" dopo la Home): per non contare due volte la stessa apertura */
    private var lastApp = ""
    /** L'ultima app vera aperta (la Home non la cancella): per "di solito dopo…" */
    private var prevApp = ""
    private var lastAppAt = 0L
    private var lastUnlock = 0L

    /** Il Wi-Fi attuale, da Contexts ("" = nessuno, null = non si sa). */
    @Volatile private var wifi: String? = null

    // Memoria

    @Synchronized
    private fun ensure() {
        if (loaded) return
        loaded = true
        val now = System.currentTimeMillis()
        val keep = now - KEEP_DAYS * Model.DAY
        val m = Model(now = now)
        var stale = 0
        runCatching {
            if (file.exists()) file.forEachLine { line ->
                val p = line.split('\t')
                val t = p.getOrNull(1)?.toLongOrNull() ?: return@forEachLine
                if (t < keep) { stale++; return@forEachLine }
                when (p[0]) {
                    "G" -> if (p.size >= 9) m.add(Gesture(t, p[2], situationOf(t, p).features(true)))
                    // Cercata perché non c'era tra le proposte: un gesto in più, nella stessa situazione
                    "M" -> if (p.size >= 9) { m.add(Gesture(t, p[2], situationOf(t, p).features(true), MISS_WEIGHT)); misses += t }
                    "P" -> hits += t
                    "N" -> if (p.size >= 3) m.notified(p[2], t)
                    "I" -> if (p.size >= 3) m.shown(t, p[2].split(',').filter { it.isNotEmpty() })
                    // Il fumetto "di solito": visto (U) e scorso via (X)
                    "U" -> if (p.size >= 3) m.shown(t, listOf(p[2]))
                    "X" -> if (p.size >= 9) m.refused(p[2], t, situationOf(t, p).features(false))
                    "C" -> if (p.size >= 4) m.addChoice(t, p[2], p[3])
                    "T" -> if (p.size >= 4) timeline += Triple(t, p[2], p[3])
                }
            }
        }
        m.tune = savedTune()
        model = m
        lastIngestAt = sp.getLong("ingest", 0L)
        if (stale > 200) compact(keep)
    }

    private fun situationOf(t: Long, p: List<String>) = Situation(
        t, p[3].unknown(), p[4].bool(), p[5].bool(), p[6].bool(), p[7].unknown(), p[8].bool(), device = p.getOrNull(9)?.unknown(),
    )

    private fun String.unknown() = if (this == "?") null else this
    private fun String.bool() = when (this) { "1" -> true; "0" -> false; else -> null }
    private fun Boolean?.enc() = when (this) { true -> "1"; false -> "0"; null -> "?" }
    private fun String?.enc() = this?.replace('\t', ' ')?.replace('\n', ' ') ?: "?"

    private fun append(line: String) = io.execute { runCatching { file.appendText(line + "\n") } }

    private fun compact(keep: Long) = io.execute {
        runCatching {
            val lines = file.readLines().filter { (it.split('\t').getOrNull(1)?.toLongOrNull() ?: 0L) >= keep }
            file.writeText(lines.joinToString("\n", postfix = "\n"))
        }
    }

    /** Quando hai preso una proposta o una scheda (P) e quando hai dovuto cercare (M): il numero da far scendere. */
    private val hits = ArrayList<Long>()
    private val misses = ArrayList<Long>()
    /** Le proposte in Casa adesso (cose del cervello). */
    @Volatile private var showing: Set<String> = emptySet()
    private val notifiedLog = HashMap<String, Long>()

    @Synchronized
    private fun remember(item: String, s: Situation) {
        ensure()
        model.add(Gesture(s.time, item, s.features(true)))
        append(listOf("G", s.time, item.enc(), s.wifi.enc(), s.car.enc(), s.headset.enc(), s.charging.enc(), s.previous.enc(), s.fresh.enc(), s.device.enc()).joinToString("\t"))
    }

    // Situazione

    /** Contexts: il Wi-Fi è cambiato ("" = scollegato). */
    fun onWifi(ssid: String?) {
        val v = ssid ?: ""
        if (v == wifi) return
        wifi = v
        mark("pl", v)
    }

    // Regolarsi da soli

    private val tuning = java.util.concurrent.atomic.AtomicBoolean(false)

    private fun savedTune() = Tune(
        sp.getFloat("t_r", Model.RECENT_GAIN.toFloat()).toDouble(),
        sp.getFloat("t_n", Model.NOTIFY_GAIN.toFloat()).toDouble(),
        sp.getFloat("t_i", Model.IGNORE_GAIN.toFloat()).toDouble(),
    )

    /**
     * In carica: una volta al giorno il cervello rigioca gli ultimi 60 giorni e regola i pesi dei segnali
     * su come usi tu il telefono. Su un filo a priorità minima, mai a batteria.
     */
    fun tuneIfDue(charging: Boolean) {
        if (!charging || System.currentTimeMillis() - sp.getLong("tuned", 0L) < 20 * 3_600_000L) return
        if (!tuning.compareAndSet(false, true)) return
        Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_LOWEST)
            runCatching {
                val from = System.currentTimeMillis() - 60 * Model.DAY
                val events = ArrayList<Tuner.Event>()
                if (file.exists()) file.forEachLine { line ->
                    val p = line.split('\t')
                    val t = p.getOrNull(1)?.toLongOrNull() ?: return@forEachLine
                    if (t < from) return@forEachLine
                    when (p[0]) {
                        "G", "M" -> if (p.size >= 9) events += Tuner.Event(p[0][0], t, p[2], situationOf(t, p))
                        "N", "I" -> if (p.size >= 3) events += Tuner.Event(p[0][0], t, p[2], null)
                    }
                }
                events.sortBy { it.time }
                Tuner.tune(events, savedTune(), MISS_WEIGHT)?.let { r ->
                    sp.edit().putFloat("t_r", r.tune.recent.toFloat()).putFloat("t_n", r.tune.notify.toFloat())
                        .putFloat("t_i", r.tune.ignore.toFloat()).apply()
                    synchronized(this) { model.tune = r.tune }
                }
            }
            sp.edit().putLong("tuned", System.currentTimeMillis()).apply()
            tuning.set(false)
        }.start()
    }

    /** Contexts: il dispositivo Bluetooth audio collegato ("" = nessuno). */
    fun onDevice(name: String) = mark("bt", name)

    /**
     * Contexts/Casa: dove sei quando non c'è Wi-Fi, da una posizione che il telefono aveva già (nessuna richiesta in più).
     * Una cella di qualche centinaio di metri: abbastanza per distinguere palestra, ufficio, supermercato.
     */
    fun onPlace(cell: String) { if (wifi.isNullOrEmpty()) mark("pl", cell) }

    /** Contexts: auto, cuffie, carica. */
    fun onState(group: String, on: Boolean) = mark(group, if (on) "1" else "0")

    @Synchronized
    private fun mark(group: String, value: String) {
        ensure()
        val t = System.currentTimeMillis()
        if (timeline.lastOrNull { it.second == group }?.third == value) return
        timeline += Triple(t, group, value)
        append("T\t$t\t$group\t${value.enc()}")
    }

    /** Com'era la situazione a [t], dai cambi registrati. */
    private fun stateAt(t: Long, group: String): String? = timeline.lastOrNull { it.second == group && it.first <= t }?.third

    /** Il luogo a [t]: il Wi-Fi, o la cella della posizione se è recente (una cella vecchia non dice dove sei adesso). */
    private fun placeAt(t: Long): String? {
        val e = timeline.lastOrNull { it.second == "pl" && it.first <= t } ?: return null
        return if (e.third.startsWith("@") && t - e.first > 45 * 60_000) "" else e.third
    }

    /**
     * Dove sei, senza Wi-Fi: l'ultima posizione che il telefono conosce già (Maps, meteo…), letta e mai chiesta.
     * Nessun GPS acceso da Sordina, nessuna batteria in più. Solo se è recente e abbastanza precisa.
     */
    fun samplePlace() {
        if (!wifi.isNullOrEmpty()) return
        if (context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val loc = runCatching {
            context.getSystemService(android.location.LocationManager::class.java).getLastKnownLocation(android.location.LocationManager.PASSIVE_PROVIDER)
        }.getOrNull() ?: return
        if (System.currentTimeMillis() - loc.time > 20 * 60_000 || (loc.hasAccuracy() && loc.accuracy > 400f)) return
        onPlace(cellOf(loc.latitude, loc.longitude))
    }

    /** Adesso. */
    fun now(): Situation {
        val t = System.currentTimeMillis()
        val am = context.getSystemService(AudioManager::class.java)
        val headset = runCatching {
            am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in HEADSETS }
        }.getOrNull()
        val charging = runCatching { context.getSystemService(BatteryManager::class.java).isCharging }.getOrNull()
        return Situation(
            time = t,
            wifi = wifi?.takeIf { it.isNotEmpty() } ?: placeAt(t) ?: wifi,
            car = stateAt(t, "car")?.let { it == "1" } ?: false,
            headset = headset,
            charging = charging,
            previous = if (prevApp.isNotEmpty() && t - lastAppAt < 10 * 60_000) "app:$prevApp" else "",
            fresh = lastUnlock > 0 && t - lastUnlock < 2 * 60_000,
            device = stateAt(t, "bt"),
        )
    }

    // Cronologia d'uso di Android → memoria

    @Volatile private var lastIngestAt = 0L

    /** Legge le app aperte dall'ultima volta e le ricorda con la loro situazione. Fuori dal thread principale. */
    @Synchronized
    fun ingest(launchable: Set<String>) {
        ensure()
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return
        val now = System.currentTimeMillis()
        val from = maxOf(lastIngestAt, now - 9 * Model.DAY)
        val skip = homePackages() + context.packageName
        // Si riparte un po' prima per ricostruire sblocco e app precedente; si ricorda solo il nuovo
        val events = runCatching { usm.queryEvents(from - 15 * 60_000, now) }.getOrNull() ?: return
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val ts = e.timeStamp
            when (e.eventType) {
                KEYGUARD_HIDDEN -> { lastUnlock = ts; lastApp = ""; prevApp = "" }
                FOREGROUND -> {
                    val pkg = e.packageName ?: continue
                    // La Home (anche Sordina) separa le aperture: la stessa app riaperta dopo è un'apertura nuova
                    if (pkg in skip) { lastApp = ""; continue }
                    if (pkg !in launchable) continue
                    if (pkg == lastApp) { lastAppAt = ts; continue }
                    if (ts > from) {
                        // L'app di prima: anche passando dalla Home in mezzo (Maps → Casa → Spotify)
                        val prev = if (prevApp.isNotEmpty() && prevApp != pkg && ts - lastAppAt < 10 * 60_000) "app:$prevApp" else ""
                        val s = Situation(
                            ts, placeAt(ts), stateAt(ts, "car")?.let { it == "1" }, stateAt(ts, "hs")?.let { it == "1" },
                            stateAt(ts, "chg")?.let { it == "1" }, prev, lastUnlock > 0 && ts - lastUnlock < 2 * 60_000, device = stateAt(ts, "bt"),
                        )
                        remember("app:$pkg", s)
                    }
                    lastApp = pkg; prevApp = pkg; lastAppAt = ts
                }
            }
        }
        lastIngestAt = now
        sp.edit().putLong("ingest", now).apply()
    }

    private fun homePackages(): Set<String> = runCatching {
        context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { it.activityInfo.packageName }.toSet()
    }.getOrDefault(emptySet())

    // Cosa hai fatto

    /** La cosa nel linguaggio del modello: "app:pacchetto", "call:nome", "msg:nome:VIA", "set:…", "cmd:frase". */
    fun itemOf(c: Candidate, query: String): String? = when (val a = c.act) {
        is Act.OpenApp -> "app:${a.app.packageName}"
        is Act.Call -> "call:${a.contact.normalized}"
        is Act.Message -> "msg:${a.contact.normalized}:${a.via.name}"
        Act.SordinaSettings -> "set:sordina"
        is Act.Setting -> "set:${a.action}"
        is Act.Timer, is Act.Alarm, Act.Torch, is Act.Media, is Act.Navigate, is Act.Music, is Act.Assist ->
            Search.normalize(query).takeIf { it.isNotEmpty() }?.let { "cmd:$it" }
        else -> null
    }

    /** Hai scelto [c] scrivendo [query] (vuota = da una proposta). */
    fun chosen(query: String, c: Candidate, usageAccess: Boolean) {
        val item = itemOf(c, query) ?: return
        val now = System.currentTimeMillis()
        val nq = Search.normalize(query)
        if (nq.isNotEmpty()) {
            synchronized(this) { ensure(); model.addChoice(now, nq, item) }
            append("C\t$now\t${nq.enc()}\t${item.enc()}")
        }
        // Le app le ricorda la cronologia d'uso (con la situazione giusta); senza accesso, si ricordano qui
        if (c.act !is Act.OpenApp || !usageAccess) remember(item, now())
        if (nq.isEmpty()) {
            // Presa da una proposta o da una scheda: Sordina aveva visto giusto
            synchronized(this) { ensure(); hits += now }
            append("P\t$now\t${item.enc()}")
        } else if (item !in showing) missed(item)
    }

    /**
     * Hai dovuto cercarla (nella barra o nell'elenco delle app): andava proposta. Se ricapita nella stessa
     * situazione, la prossima volta c'è. Una ricerca isolata (una cosa fuori dal solito) resta un caso.
     */
    fun missed(item: String) {
        if (item in showing) return
        val now = System.currentTimeMillis()
        run {
            val s = now()
            synchronized(this) { ensure(); model.add(Gesture(now, item, s.features(true), MISS_WEIGHT)); misses += now }
            append(listOf("M", now, item.enc(), s.wifi.enc(), s.car.enc(), s.headset.enc(), s.charging.enc(), s.previous.enc(), s.fresh.enc(), s.device.enc()).joinToString("\t"))
        }
    }

    /** Hai aperto qualcosa da una scheda (non dalla ricerca): conta come colpo giusto. */
    fun hit(item: String) {
        val now = System.currentTimeMillis()
        synchronized(this) { ensure(); hits += now }
        append("P\t$now\t${item.enc()}")
    }

    /** Le proposte che stai guardando in Casa. */
    fun shown(items: List<String>) {
        showing = items.toSet()
        val now = System.currentTimeMillis()
        synchronized(this) { ensure(); model.shown(now, items) }
        append("I\t$now\t${items.joinToString(",") { it.enc().replace(',', ' ') }}")
    }

    /** Un'app ti ha mandato una notifica che vedi (non quelle fisse): è probabile che la apri. */
    fun onNotified(pkg: String) {
        val now = System.currentTimeMillis()
        val item = "app:$pkg"
        // Fuori dal filo delle notifiche: la prima volta il cervello si carica dal file, e non deve rallentarle
        io.execute {
            synchronized(this) {
                ensure()
                model.notified(item, now)
                // Nella memoria al massimo una ogni 5 minuti per app
                if (now - (notifiedLog[item] ?: 0L) < 5 * 60_000) return@execute
                notifiedLog[item] = now
            }
            runCatching { file.appendText("N\t$now\t${item.enc()}\n") }
        }
    }

    /**
     * Quante volte, negli ultimi [days] giorni, hai dovuto cercare invece di trovare la cosa già lì:
     * (cercate, trovate). Il numero che Sordina deve far scendere.
     */
    @Synchronized
    fun missStats(days: Int = 7): Pair<Int, Int> {
        ensure()
        val from = System.currentTimeMillis() - days * Model.DAY
        return misses.count { it >= from } to hits.count { it >= from }
    }

    /** Hai toccato la notifica di un messaggio: con quella persona stai parlando, su quell'app. */
    fun onMessageOpened(pkg: String, person: String) {
        val via = Via.ofPackage(pkg) ?: return
        val n = Search.normalize(person)
        if (n.isEmpty()) return
        remember("msg:$n:${via.name}", now())
    }

    @Synchronized
    fun forgetItem(item: String) { ensure(); model.forget(item) }

    /** Dimentica quello che ha imparato dalle lettere scritte (Impostazioni → Dimentica le correzioni). Le abitudini restano. */
    @Synchronized
    fun forgetTyping() {
        ensure()
        model.clearTyped()
        io.execute {
            runCatching {
                if (file.exists()) file.writeText(file.readLines().filterNot { it.startsWith("C\t") }.joinToString("\n", postfix = "\n"))
            }
        }
    }

    /** Dimentica tutto (Azzera). */
    @Synchronized
    fun wipe() {
        model = Model(); timeline.clear(); lastIngestAt = 0
        sp.edit().clear().apply()
        io.execute { runCatching { file.delete() } }
    }

    // Previsioni

    /** Punteggio d'uso delle app (per ordinare la ricerca a parità di testo). */
    fun usage(): Map<String, Double> {
        ensure()
        val m = model
        return m.items.filter { it.startsWith("app:") }.associate { it.removePrefix("app:") to m.share(it) * 10 }
    }

    /** Messaggi e app che ti aspettano: chi ti ha scritto e non hai ancora letto. */
    private fun pending(): Map<String, String> {
        val out = HashMap<String, String>()
        for (h in store.held.value.values) {
            out["app:${h.pkg}"] = h.appLabel
            val via = Via.ofPackage(h.pkg)
            if (h.isMessage && h.conversation == null && h.person != null && via != null) {
                out["msg:${Search.normalize(h.person)}:${via.name}"] = h.person
            }
        }
        return out
    }

    /**
     * Le proposte per adesso, la più probabile per prima. [realize] trasforma una cosa in un'azione
     * (null se non esiste più: app disinstallata, contatto sparito).
     */
    fun suggest(
        count: Int, skip: (String) -> Boolean,
        /** Quello che succede adesso fuori dalle abitudini (una riunione tra poco): cosa → (spinta, perché) */
        boosts: Map<String, Pair<Double, String>> = emptyMap(),
        realize: (String) -> Candidate?,
    ): List<Suggestion> {
        ensure()
        val m = model
        val s = now()
        val f = s.features(learning = false)
        val waiting = pending()
        val pool = (m.items + waiting.keys.filter { it.startsWith("msg:") } + boosts.keys).distinct()
        return pool.asSequence()
            .filterNot(skip)
            .map { item ->
                var score = m.predict(item, f, s.time)
                // Chi ti ha scritto: rispondere è probabile, anche se non è la tua ora solita
                if (item in waiting) score = (if (score.isInfinite()) -6.0 else score) + if (item.startsWith("msg:")) 2.0 else 0.4
                boosts[item]?.let { score = (if (score.isInfinite()) -6.0 else score) + it.first }
                item to score
            }
            .filter { it.second.isFinite() }
            .sortedByDescending { it.second }
            .mapNotNull { (item, score) -> realize(item)?.let { Suggestion(it, item, score, boosts[item]?.second ?: why(m, item, f, waiting, s)) } }
            .distinctBy { it.candidate.id }
            .take(count)
            .toList()
    }

    /**
     * Il fumetto "di solito adesso": solo le cose di cui il cervello è davvero sicuro, e solo per un motivo
     * che viene dalla situazione (l'ora, il posto, l'auto, l'app di prima), mai "la usi spesso".
     * Niente di quello che hai fatto negli ultimi 30 minuti, niente di quello che ti ha appena scritto
     * (è già nel numero accanto alla sordina). Meglio una sordina zitta che un fumetto a caso.
     */
    fun usual(count: Int, skip: (String) -> Boolean, minP: Double = USUAL_P, realize: (String) -> Candidate?): List<Suggestion> {
        ensure()
        val m = model
        val s = now()
        val f = s.features(learning = false)
        val t = s.time
        val waiting = pending().keys
        val scored = m.items.filterNot(skip).map { it to m.habit(it, f, t) }.filter { it.second.isFinite() }
        if (scored.size < 3) return emptyList()
        val top = scored.maxOf { it.second }
        val z = scored.sumOf { kotlin.math.exp(it.second - top) }
        return scored.sortedByDescending { it.second }.asSequence()
            .map { (item, sc) -> item to kotlin.math.exp(sc - top) / z }
            .takeWhile { it.second >= minP }
            .filter { (item, _) ->
                item !in waiting && m.times(item, t) >= 3 && (m.lastUsed(item) ?: 0L) < t - 30 * 60_000L &&
                    m.notifiedBoost(item, t) < 0.3
            }
            .mapNotNull { (item, p) ->
                val r = m.reason(item, f)?.takeIf { it.group in SITUATIONAL || (it.value != "-" && it.value != "0") } ?: return@mapNotNull null
                realize(item)?.let { Suggestion(it, item, p, reasonText(r)) }
            }
            .distinctBy { it.candidate.id }
            .take(count)
            .toList()
    }

    /** Il fumetto ti sta mostrando [item]: se non lo prendi, conta come proposta lasciata lì. */
    fun shownUsual(item: String) {
        val now = System.currentTimeMillis()
        synchronized(this) { ensure(); model.shown(now, listOf(item)) }
        append("U\t$now\t${item.enc()}")
    }

    /** L'hai scorsa via dal fumetto: in una situazione come questa scende. */
    fun refusedUsual(item: String) {
        val s = now()
        synchronized(this) { ensure(); model.refused(item, s.time, s.features(learning = false)) }
        append(listOf("X", s.time, item.enc(), s.wifi.enc(), s.car.enc(), s.headset.enc(), s.charging.enc(), s.previous.enc(), s.fresh.enc(), s.device.enc()).joinToString("\t"))
    }

    /** Come va il fumetto negli ultimi [days] giorni: (visto, preso, scorso via). Per regolare [USUAL_P]. */
    fun usualStats(days: Int = 7): Triple<Int, Int, Int> {
        val from = System.currentTimeMillis() - days * Model.DAY
        var seen = 0; var refused = 0
        val shownAt = ArrayList<Pair<Long, String>>()
        val taken = HashSet<Pair<Long, String>>()
        runCatching {
            if (file.exists()) file.forEachLine { l ->
                val p = l.split('\t')
                val t = p.getOrNull(1)?.toLongOrNull() ?: return@forEachLine
                if (t < from) return@forEachLine
                when (p[0]) {
                    "U" -> { seen++; shownAt += t to p.getOrElse(2) { "" } }
                    "X" -> refused++
                    "P" -> shownAt.lastOrNull { it.second == p.getOrNull(2) && t - it.first in 0..10 * 60_000L }?.let { taken += it }
                }
            }
        }
        return Triple(seen, taken.size, refused)
    }


    private fun why(m: Model, item: String, f: List<Feature>, waiting: Map<String, String>, s: Situation): String {
        if (item.startsWith("msg:")) waiting[item]?.let { return tr(R.string.why_wrote, it) }
        if (m.notifiedBoost(item, s.time) >= 0.8) return tr(R.string.why_notified)
        // Usata pochi minuti fa: è questo che la porta su, non l'abitudine
        if (m.recency(item, s.time) >= 1.5 && (m.reason(item, f) == null || m.times(item) < 3)) return tr(R.string.why_recent)
        val r = m.reason(item, f) ?: return if (m.times(item) >= 5) tr(R.string.why_used) else tr(R.string.why_recent)
        return reasonText(r)
    }

    private fun reasonText(r: Reason): String {
        return when (r.group) {
            "h" -> tr(R.string.why_time)
            "pl" -> when { r.value == "-" -> tr(R.string.why_out); r.value.startsWith("@") -> tr(R.string.why_spot); else -> tr(R.string.why_place, r.value) }
            "bt" -> if (r.value == "-") tr(R.string.why_time) else tr(R.string.why_device, r.value)
            "car" -> if (r.value == "1") tr(R.string.why_car) else tr(R.string.why_time)
            "hs" -> if (r.value == "1") tr(R.string.why_headset) else tr(R.string.why_time)
            "chg" -> if (r.value == "1") tr(R.string.why_charging) else tr(R.string.why_time)
            "prev" -> labelOf(r.value)?.let { tr(R.string.why_after, it) } ?: tr(R.string.why_time)
            "fresh" -> if (r.value == "1") tr(R.string.why_unlock) else tr(R.string.why_time)
            else -> tr(R.string.why_day)
        }
    }

    private fun labelOf(item: String): String? = item.takeIf { it.startsWith("app:") }?.removePrefix("app:")?.let { pkg ->
        runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
    }

    // Mentre scrivi

    /**
     * Riordina i risultati della barra: il testo resta la base, poi contano quello che di solito scegli
     * con queste lettere e quanto è probabile adesso. Restituisce anche cosa aggiungere (le scelte imparate
     * che il testo da solo non trova). [realize] come in [suggest].
     */
    fun rerank(query: String, cands: List<Candidate>, realize: (String) -> Candidate?): List<Candidate> {
        ensure()
        val nq = Search.normalize(query)
        if (nq.isEmpty()) return cands
        val m = model
        val f = now().features(learning = false)
        val known = cands.associateBy { itemOf(it, query) }
        // Le scelte imparate per queste lettere, anche se il testo non le trova (es. "mam" → Scrivi a Mamma)
        val extra = m.typedItems(nq, 0.25).filter { it !in known.keys }.mapNotNull { item ->
            realize(item)?.let { it.copy(score = 55) }
        }
        val all = cands + extra
        // Il punteggio di situazione, rispetto alla media delle cose che conosci
        val ctx = all.associateWith { c -> itemOf(c, query)?.let { m.score(it, f) } }
        val finite = ctx.values.filterNotNull().filter { it.isFinite() }
        val mid = if (finite.isEmpty()) 0.0 else finite.sorted()[finite.size / 2]
        return all.map { c ->
            val item = itemOf(c, query) ?: return@map c
            val (p, times) = m.typed(nq, item)
            val learned = p * 32 * minOf(1.0, times / 2.0)
            val situational = ctx[c]?.takeIf { it.isFinite() }?.let { ((it - mid) * 4).coerceIn(-6.0, 10.0) } ?: 0.0
            c.copy(score = (c.score + learned + situational).toInt().coerceIn(0, 100))
        }
    }

    /** Quanto sei sicuro che [query] porti a [c]: le scelte ripetute contano come una conferma. */
    fun learnedFor(query: String, c: Candidate): Double {
        ensure()
        val item = itemOf(c, query) ?: return 0.0
        val (p, times) = model.typed(Search.normalize(query), item)
        return if (times >= 2) p else 0.0
    }

    /** Le persone che è più probabile che tu chiami o scriva adesso (per "chi…", "scr…"). */
    fun likelyPeople(prefix: String, limit: Int): List<String> {
        ensure()
        val m = model
        val f = now().features(learning = false)
        return m.items.filter { it.startsWith(prefix) }.sortedByDescending { m.predict(it, f, System.currentTimeMillis()) }.take(limit)
    }

    /** Il contatto con questo nome (normalizzato), se c'è in rubrica. */
    fun contact(norm: String): Contact? = people.all().firstOrNull { it.normalized == norm }

    companion object {
        private const val KEEP_DAYS = 120L
        /** Una cella di circa 280 × 200 metri (alle nostre latitudini): abbastanza per un posto, mai un indirizzo. */
        fun cellOf(lat: Double, lon: Double) = "@" + kotlin.math.round(lat * 400).toLong() + "_" + kotlin.math.round(lon * 400).toLong()
        const val MISS_WEIGHT = 1.0
        /** Il fumetto "di solito" parla solo da questa probabilità in su (fra tutte le cose che fai).
         * Misurata sulla memoria vera (UsualReplayTest, 15/11/2026): a 0.25 parla nel 6% dei momenti e quello che dice
         * lo apri entro mezz'ora quasi una volta su due; senza soglia, meno di una su cinque. */
        const val USUAL_P = 0.25
        /** Motivi che valgono anche col valore "no": l'ora e il giorno sono sempre un motivo. */
        private val SITUATIONAL = setOf("h", "dw", "we")
        private const val KEYGUARD_HIDDEN = 18
        /** MOVE_TO_FOREGROUND / ACTIVITY_RESUMED */
        private const val FOREGROUND = 1
        private val HEADSETS = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_USB_HEADSET,
        ) + if (Build.VERSION.SDK_INT >= 31) setOf(AudioDeviceInfo.TYPE_BLE_HEADSET) else emptySet()
    }
}
