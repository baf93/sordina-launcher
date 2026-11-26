package app.sordina.data

import app.sordina.tr

import app.sordina.R

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Un'entità di Home Assistant che si può comandare o leggere. */
data class HaEntity(val id: String, val name: String, val state: String, val area: String?, val deviceClass: String? = null,
                    /** Per gli allarmi: "number", "text" o null (non chiede codice) */
                    val codeFormat: String? = null,
                    /** Per gli allarmi: le modalità che sa inserire (bit di Home Assistant) e se vuole il codice anche per inserire */
                    val features: Int = 0, val codeArmRequired: Boolean = true,
                    /** L'unità dei sensori ("°C", "%"), vuota per il resto */
                    val unit: String = "") {
    /** Le modalità di inserimento che l'allarme supporta, nell'ordine in cui si propongono */
    val armModes: List<String> get() = ARM_MODES.filter { (_, bit) -> (features and bit) != 0 }.map { it.first }
    val domain: String get() = id.substringBefore('.')
    val normalized: String = Search.normalize(name)
    val isOn: Boolean get() = state == "on" || state == "open" || state == "playing" || state == "unlocked"
    val controllable: Boolean get() = domain in CONTROLLABLE

    companion object {
        /** Modalità di inserimento (suffisso del servizio alarm_arm_…) e il loro bit in supported_features */
        val ARM_MODES = listOf("away" to 2, "home" to 1, "night" to 4, "vacation" to 32, "custom_bypass" to 16)
        val CONTROLLABLE = setOf("light", "switch", "fan", "cover", "script", "scene", "input_boolean", "lock", "media_player", "climate", "automation")
    }
}


/**
 * Home Assistant minimo: solo REST, solo quando serve. Nessuna connessione aperta in background.
 * Gli allarmi passano dall'app ufficiale di Home Assistant con il livello "Sempre".
 */
class HomeAssistant(private val context: Context, private val prefs: Prefs) {
    @Volatile private var cache: List<HaEntity> = emptyList()
    @Volatile private var cacheAt = 0L

    /** Livello 2: col token Sordina parla con casa da sola. Senza, resta il livello 1 (l'app). */
    val configured: Boolean get() = prefs.haEnabled && prefs.haUrl.isNotBlank() && prefs.haTokenEnc.isNotBlank()

    /** L'app ufficiale di Home Assistant: senza token, Assist si apre lì (con il suo collegamento, anche fuori casa). */
    val appInstalled: Boolean get() = runCatching { context.packageManager.getPackageInfo(COMPANION_PKG, 0) }.isSuccess

    /** Si può parlare con casa: col token da qui, senza token passando dall'app. */
    val canAssist: Boolean get() = prefs.haEnabled && (configured || appInstalled)

    /** Apre Assist nell'app di Home Assistant (microfono e tastiera). */
    fun openAssist(): Boolean = runCatching {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_ASSIST).setPackage(COMPANION_PKG).putExtra("start_listening", false).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess

    fun setToken(token: String) {
        prefs.haTokenEnc = if (token.isBlank()) "" else encrypt(token.trim())
        cache = emptyList()
    }

    private fun token(): String? = prefs.haTokenEnc.takeIf { it.isNotBlank() }?.let { runCatching { decrypt(it) }.getOrNull() }

    /**
     * Quale indirizzo usare, come fa l'app di Home Assistant: su Wi-Fi prima quello di casa, se no quello di fuori.
     * Si ricorda quale ha funzionato su questa rete, così non riprova ogni volta quello che non risponde.
     */
    @Volatile private var route: Pair<android.net.Network?, String>? = null

    /** L'ultima strada usata: true in casa, false da fuori, null se non si è ancora parlato con casa. */
    val local: Boolean? get() = route?.second?.let { it == prefs.haLocalUrl && it != prefs.haUrl }

    /**
     * L'impronta della rete di adesso: gateway, sottorete, server DHCP e dominio. Non è una prova
     * (una rete ostile può imitarla), ma basta perché il token non parta in chiaro verso
     * un 192.168.1.10 qualunque di un bar o di un albergo.
     */
    private fun netPrint(): String? = runCatching {
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java) ?: return null
        val lp = cm.getLinkProperties(cm.activeNetwork ?: return null) ?: return null
        val gw = lp.routes.firstOrNull { it.isDefaultRoute && it.gateway != null }?.gateway?.hostAddress ?: return null
        val sub = lp.linkAddresses.firstOrNull { it.address is java.net.Inet4Address }?.let { "${it.address.hostAddress}".substringBeforeLast('.') + "/" + it.prefixLength }
        val dhcp = if (android.os.Build.VERSION.SDK_INT >= 30) lp.dhcpServerAddress?.hostAddress else null
        listOf(gw, sub, dhcp, lp.domains).joinToString("|") { it ?: "" }
    }.getOrNull()

    /** Siamo su una rete di casa già riconosciuta (imparata quando la prova di collegamento è riuscita in chiaro). */
    private fun atHome(): Boolean = netPrint()?.let { it in prefs.haHomeNets } == true

    /**
     * Durante la prova chiesta da te nelle impostazioni si può parlare in chiaro: è così che casa si impara.
     * Vale solo per il thread della prova: una richiesta in parallelo (il calendario) resta sulle regole di sempre.
     */
    private val trusting = ThreadLocal.withInitial { false }

    private fun bases(): List<Pair<String, Int>> {
        // In chiaro (http) solo su una rete di casa riconosciuta; fuori serve https
        val safe = trusting.get() || atHome()
        fun ok(u: String) = u.isNotBlank() && (u.startsWith("https://") || safe)
        val out = prefs.haUrl
        val home = prefs.haLocalUrl
        if (home.isBlank() || home == out) return listOfNotNull(out.takeIf(::ok)?.let { it to 4000 })
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
        val net = cm?.activeNetwork
        val caps = net?.let { cm.getNetworkCapabilities(it) }
        val lan = caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true ||
            caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) == true
        // Sulla rete mobile casa non si raggiunge: dritti fuori
        if (!lan) return listOfNotNull(out.takeIf(::ok)?.let { it to 4000 })
        // Quello di casa ha un'attesa breve: se non risponde in un attimo non siamo a casa
        val both = listOf(home to 1500, out to 4000).filter { ok(it.first) }
        val known = route?.takeIf { it.first == net }?.second ?: return both
        return both.sortedBy { if (it.first == known) 0 else 1 }
    }

    /** Chiamata HTTP sincrona: da usare solo fuori dal thread principale. */
    private fun request(path: String, body: JSONObject? = null): String? {
        val t = token() ?: return null
        val net = context.getSystemService(android.net.ConnectivityManager::class.java)?.activeNetwork
        for ((base, wait) in bases()) {
            val c = runCatching { URL(base + path).openConnection() as HttpURLConnection }.getOrNull() ?: continue
            try {
                c.connectTimeout = wait
                c.readTimeout = 6000
                // Il token non segue i reindirizzamenti verso altri indirizzi
                c.instanceFollowRedirects = false
                c.setRequestProperty("Authorization", "Bearer $t")
                c.setRequestProperty("Content-Type", "application/json")
                if (body != null) { c.requestMethod = "POST"; c.doOutput = true }
                c.connect()
            } catch (_: java.io.IOException) {
                // Non raggiungibile da qui: si prova l'altro indirizzo
                c.disconnect(); continue
            }
            val code = try {
                if (body != null) c.outputStream.use { it.write(body.toString().toByteArray()) }
                c.responseCode
            } catch (_: java.io.IOException) {
                c.disconnect()
                // Un comando (apri, disattiva, una routine) può essere già arrivato: non si rimanda dall'altro indirizzo,
                // se no partirebbe due volte. Una lettura invece si può riprovare.
                if (body != null) return null else continue
            }
            // Ha risposto: questa è la strada giusta su questa rete, anche se la risposta è un errore
            route = net to base
            val text = runCatching { (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            c.disconnect()
            return if (code in 200..299) text else null
        }
        return null
    }

    /** Verifica la connessione e impara da Home Assistant i suoi due indirizzi. Restituisce il messaggio da mostrare. */
    fun test(): String {
        if (!configured) return tr(R.string.ha_missing)
        route = null
        trusting.set(true)
        try {
            val r = request("/api/") ?: return tr(R.string.ha_unreachable)
            // Gli indirizzi che Home Assistant conosce (Impostazioni › Sistema › Rete): quello di casa, e quello di fuori se manca
            runCatching {
                val cfg = JSONObject(request("/api/config") ?: return@runCatching)
                val inside = cfg.optString("internal_url").takeIf { it.startsWith("http") && cfg.isNull("internal_url").not() }
                // Fuori casa il token viaggia solo cifrato: un indirizzo esterno in chiaro non si adotta
                val outside = cfg.optString("external_url").takeIf { it.startsWith("https://") && cfg.isNull("external_url").not() }
                // Hai scritto quello di casa: fuori si passa da quello esterno, se c'è
                if (inside != null && prefs.haUrl.trimEnd('/') == inside.trimEnd('/') && outside != null) prefs.haUrl = outside
                if (inside != null && prefs.haLocalUrl.isBlank()) {
                    prefs.haLocalUrl = inside
                    // Appena imparato: si riprova, così la strada di adesso è già quella giusta (in casa, se ci sei)
                    route = null
                    request("/api/")
                }
            }
            // Ha risposto in chiaro: questa è una rete di casa
            if (route?.second?.startsWith("http://") == true) netPrint()?.let { prefs.haHomeNets = prefs.haHomeNets + it }
            return runCatching { JSONObject(r).optString("message", tr(R.string.ha_connected)) }.getOrDefault(tr(R.string.ha_connected))
        } finally {
            trusting.set(false)
        }
    }

    fun entities(force: Boolean = false): List<HaEntity> {
        if (!configured) return emptyList()
        if (!force && System.currentTimeMillis() - cacheAt < 60_000 && cache.isNotEmpty()) return cache
        val r = request("/api/states") ?: return cache
        val arr = runCatching { JSONArray(r) }.getOrNull() ?: return cache
        val out = ArrayList<HaEntity>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = o.optString("entity_id")
            val attrs = o.optJSONObject("attributes")
            val name = attrs?.optString("friendly_name")?.takeIf { it.isNotBlank() } ?: id
            out += HaEntity(id, name, o.optString("state"), attrs?.optString("area")?.takeIf { it.isNotBlank() },
                attrs?.optString("device_class")?.takeIf { it.isNotBlank() },
                attrs?.takeIf { !it.isNull("code_format") }?.optString("code_format")?.takeIf { it.isNotBlank() },
                attrs?.optInt("supported_features", 0) ?: 0, attrs?.optBoolean("code_arm_required", true) ?: true,
                attrs?.optString("unit_of_measurement").orEmpty())
        }
        cache = out
        cacheAt = System.currentTimeMillis()
        // I nomi di serrature, allarmi, garage e cancelli restano anche da Sordina chiusa: servono a riconoscere "apri ingresso"
        prefs.haGuarded = out.filter(HomeRisk::guarded).map { it.name }.toSet()
        prefs.haGuardedAt = cacheAt
        return out
    }

    /** I nomi di entità e aree già scaricati (senza rete): parole di casa per riconoscere le frasi. */
    fun names(): Set<String> {
        val at = cacheAt
        namesMemo.takeIf { it.first == at }?.let { return it.second }
        return cache.flatMap { HomeTalk.learn(it.name + " " + (it.area ?: "")) }.toSet().also { namesMemo = at to it }
    }
    @Volatile private var namesMemo: Pair<Long, Set<String>> = -1L to emptySet()

    /** Passa la frase ad Assist: HA conosce stanze, alias ed entità esposte. Restituisce la risposta di HA, o null. */
    fun assist(text: String): String? {
        if (!configured) return null
        val body = JSONObject().put("text", text).put("language", java.util.Locale.getDefault().language)
        val r = request("/api/conversation/process", body) ?: return null
        return runCatching {
            JSONObject(r).getJSONObject("response").getJSONObject("speech").getJSONObject("plain").getString("speech")
        }.getOrDefault(tr(R.string.done))
    }

    /** I calendari di Home Assistant: (entity_id, nome). */
    fun calendars(): List<Pair<String, String>> {
        val r = request("/api/calendars") ?: return emptyList()
        return runCatching {
            val a = JSONArray(r)
            (0 until a.length()).map { a.getJSONObject(it).let { o -> o.getString("entity_id") to o.optString("name", o.getString("entity_id")) } }
        }.getOrDefault(emptyList())
    }

    /** Il calendario di casa scelto per gli impegni, se c'è (e se c'è il token). */
    val calendar: String? get() = prefs.haCalendar.takeIf { configured && it.isNotBlank() }

    /** I calendari di casa da mostrare in Casa: quelli scelti, più quello dei nuovi impegni se è di casa. */
    fun shownCalendars(): Set<String> {
        if (!configured) return emptySet()
        return prefs.homeCalendars.filter { it.startsWith("h:") }.map { it.drop(2) }.toSet() + listOfNotNull(calendar)
    }

    @Volatile private var calCache: Triple<Long, Set<String>, List<Upcoming>> = Triple(0L, emptySet(), emptyList())

    fun resetCalendarCache() { calCache = Triple(0L, emptySet(), emptyList()) }

    /** Gli eventi con orario dei calendari di casa da mostrare, da adesso a [days] giorni. In cache 10 minuti. */
    fun calendarEvents(days: Int = 7): List<Upcoming> {
        val ids = shownCalendars()
        if (ids.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        if (now - calCache.first < 600_000 && calCache.second == ids) return calCache.third.filter { it.end > now }
        val start = java.time.OffsetDateTime.now()
        val end = start.plusDays(days.toLong())
        val range = "start=${java.net.URLEncoder.encode(start.toString(), "UTF-8")}&end=${java.net.URLEncoder.encode(end.toString(), "UTF-8")}"
        val out = ArrayList<Upcoming>()
        var reached = false
        for (id in ids) {
            val r = request("/api/calendars/$id?$range") ?: continue
            reached = true
            runCatching {
                val a = JSONArray(r)
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    val s = o.optJSONObject("start")?.optString("dateTime").orEmpty()
                    val e = o.optJSONObject("end")?.optString("dateTime").orEmpty()
                    if (s.isBlank()) continue // giornata intera
                    val b = java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli()
                    val f = if (e.isBlank()) b + 3600_000 else java.time.OffsetDateTime.parse(e).toInstant().toEpochMilli()
                    out += Upcoming((id + s).hashCode().toLong(), o.optString("summary").ifBlank { tr(R.string.event) }, b, f,
                        o.optString("location").takeIf { it.isNotBlank() && !o.isNull("location") }, "ha", false)
                }
            }
        }
        // Casa irraggiungibile: restano quelli di prima, meglio di niente
        if (!reached) return calCache.third.filter { it.end > now }
        calCache = Triple(now, ids, out)
        return out.filter { it.end > now }
    }

    // ── Il calendario di Sordina: i calendari di casa per intero, con cosa permettono

    /** I calendari di casa con quello che si può fare: Home Assistant lo dice in supported_features (1 crea, 2 cancella, 4 modifica) */
    fun calendarBooks(): List<CalBook> {
        val ids = calendars()
        return ids.map { (id, name) ->
            val f = runCatching { JSONObject(request("/api/states/$id") ?: "{}").optJSONObject("attributes")?.optInt("supported_features", 0) ?: 0 }.getOrDefault(0)
            CalBook(CalBook.HA + id, name, null, f and 1 != 0, f and 4 != 0, f and 2 != 0)
        }
    }

    /** Gli impegni di un calendario di casa fra [from] e [to], ripetizioni già espanse. null: casa non raggiungibile */
    fun calendarRange(id: String, from: java.time.OffsetDateTime, to: java.time.OffsetDateTime): List<CalEvent>? {
        val range = "start=${java.net.URLEncoder.encode(from.toString(), "UTF-8")}&end=${java.net.URLEncoder.encode(to.toString(), "UTF-8")}"
        val r = request("/api/calendars/$id?$range") ?: return null
        return runCatching {
            val a = JSONArray(r)
            (0 until a.length()).mapNotNull { CalEvent.fromHa(CalBook.HA + id, a.getJSONObject(it)) }
        }.getOrNull()
    }

    /** Com'è andato un comando sul canale WebSocket */
    sealed interface Ws {
        data class Ok(val result: JSONObject?) : Ws
        /** Home Assistant ha risposto di no: il motivo, nelle sue parole */
        data class Refused(val message: String) : Ws
        /** Casa non raggiungibile: si riprova dopo */
        data object Unreachable : Ws
    }

    private val http by lazy {
        okhttp3.OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS).build()
    }

    /**
     * Un comando sul canale WebSocket di Home Assistant (crea, modifica, cancella un impegno): quello che l'API REST non fa.
     * Stessi indirizzi e stessa prudenza delle richieste normali: in chiaro solo a casa, il token mai altrove.
     */
    fun ws(cmd: JSONObject): Ws {
        val t = token() ?: return Ws.Unreachable
        val net = context.getSystemService(android.net.ConnectivityManager::class.java)?.activeNetwork
        for ((base, wait) in bases()) {
            val url = base.trimEnd('/').replaceFirst("http", "ws") + "/api/websocket"
            val q = java.util.concurrent.LinkedBlockingQueue<String>()
            val client = http.newBuilder().connectTimeout(wait.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS).build()
            val sock = runCatching {
                client.newWebSocket(okhttp3.Request.Builder().url(url).build(), object : okhttp3.WebSocketListener() {
                    override fun onMessage(webSocket: okhttp3.WebSocket, text: String) { q.offer(text) }
                    override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: okhttp3.Response?) { q.offer(FAIL) }
                    override fun onClosed(webSocket: okhttp3.WebSocket, code: Int, reason: String) { q.offer(FAIL) }
                })
            }.getOrNull() ?: continue
            try {
                fun next(): JSONObject? = q.poll(8, java.util.concurrent.TimeUnit.SECONDS)?.takeIf { it != FAIL }?.let { runCatching { JSONObject(it) }.getOrNull() }
                // Prima "auth_required": se non arriva, da qui casa non si raggiunge
                val hello = next() ?: continue
                if (hello.optString("type") != "auth_required") continue
                sock.send(JSONObject().put("type", "auth").put("access_token", t).toString())
                val auth = next() ?: continue
                if (auth.optString("type") != "auth_ok") return Ws.Refused(auth.optString("message", "auth"))
                route = net to base
                sock.send(JSONObject(cmd.toString()).put("id", 1).toString())
                while (true) {
                    // Il comando è partito: senza risposta non si rimanda dall'altro indirizzo (partirebbe due volte)
                    val m = next() ?: return Ws.Unreachable
                    if (m.optInt("id") != 1 || m.optString("type") != "result") continue
                    return if (m.optBoolean("success")) Ws.Ok(m.optJSONObject("result"))
                    else Ws.Refused(m.optJSONObject("error")?.optString("message").orEmpty())
                }
            } finally {
                runCatching { sock.close(1000, null) }
            }
        }
        return Ws.Unreachable
    }

    /** Scrive un impegno nel calendario di casa scelto. Home Assistant non permette di cancellarlo da qui: niente "Annulla". */
    fun createEvent(title: String, w: When, minutes: Int = 60): Boolean {
        val id = calendar ?: return false
        val body = JSONObject().put("entity_id", id).put("summary", title)
        if (w.allDay) {
            val d = w.start.toLocalDate()
            body.put("start_date", d.toString()).put("end_date", d.plusDays(1).toString())
        } else {
            val s = w.start.atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime()
            body.put("start_date_time", s.toString()).put("end_date_time", s.plusMinutes(minutes.toLong()).toString())
        }
        val ok = request("/api/services/calendar/create_event", body) != null
        if (ok) resetCalendarCache()
        return ok
    }

    fun cached(): List<HaEntity> = cache

    fun state(entityId: String): HaEntity? {
        val r = request("/api/states/$entityId") ?: return null
        return runCatching {
            val o = JSONObject(r)
            val attrs = o.optJSONObject("attributes")
            val unit = attrs?.optString("unit_of_measurement").orEmpty()
            HaEntity(entityId, attrs?.optString("friendly_name") ?: entityId, (o.optString("state") + " " + unit).trim(), null)
        }.getOrNull()
    }

    /** Chiama un servizio, es. light.turn_off. */
    fun call(domain: String, service: String, entityId: String, extra: JSONObject? = null): Boolean {
        val body = (extra ?: JSONObject()).put("entity_id", entityId)
        return request("/api/services/$domain/$service", body) != null
    }

    /** Disattiva un allarme ([code] vuoto: non lo chiede). Il codice va solo in questa chiamata, mai altrove. */
    fun disarm(entity: String, code: String): Boolean =
        call("alarm_control_panel", "alarm_disarm", entity, if (code.isEmpty()) null else JSONObject().put("code", code))

    /** Inserisce un allarme in [mode] (away, home, night…); [code] vuoto se non lo chiede. */
    fun arm(entity: String, mode: String, code: String): Boolean =
        call("alarm_control_panel", "alarm_arm_$mode", entity, if (code.isEmpty()) null else JSONObject().put("code", code))

    /** Le scene e gli script della casa, per sceglierli in Impostazioni */
    fun routines(): List<HaEntity> = entities().filter { it.domain in HomeRoutines.DOMAINS }

    /** Lancia una scena o uno script */
    fun runRoutine(entity: String): Boolean = call(entity.substringBefore('.'), "turn_on", entity)

    /** Gli allarmi della casa, per sceglierli in Impostazioni */
    fun alarms(): List<HaEntity> = entities().filter { it.domain == "alarm_control_panel" }

    fun turn(e: HaEntity, on: Boolean): Boolean = when (e.domain) {
        "cover" -> call("cover", if (on) "open_cover" else "close_cover", e.id)
        "lock" -> call("lock", if (on) "unlock" else "lock", e.id)
        "script", "scene" -> call(e.domain, "turn_on", e.id)
        "automation" -> call("automation", if (on) "trigger" else "turn_off", e.id)
        "media_player" -> call("media_player", if (on) "media_play" else "media_pause", e.id)
        else -> call(if (e.domain in setOf("light", "switch", "fan", "input_boolean", "climate")) e.domain else "homeassistant", if (on) "turn_on" else "turn_off", e.id)
    }

    fun toggle(e: HaEntity): Boolean = when (e.domain) {
        "script", "scene" -> call(e.domain, "turn_on", e.id)
        else -> turn(e, !e.isOn)
    }

    fun setLevel(e: HaEntity, percent: Int): Boolean = when (e.domain) {
        "light" -> call("light", "turn_on", e.id, JSONObject().put("brightness_pct", percent.coerceIn(0, 100)))
        "cover" -> call("cover", "set_cover_position", e.id, JSONObject().put("position", percent.coerceIn(0, 100)))
        "fan" -> call("fan", "set_percentage", e.id, JSONObject().put("percentage", percent.coerceIn(0, 100)))
        "climate" -> call("climate", "set_temperature", e.id, JSONObject().put("temperature", percent))
        else -> false
    }

    // Token cifrato con una chiave del Keystore di Android, che non lascia mai il telefono.

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val enc = c.doFinal(plain.toByteArray())
        return Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(enc, Base64.NO_WRAP)
    }

    private fun decrypt(s: String): String {
        val (iv, data) = s.split(':').let { Base64.decode(it[0], Base64.NO_WRAP) to Base64.decode(it[1], Base64.NO_WRAP) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(c.doFinal(data))
    }

    companion object {
        private const val ALIAS = "sordina_ha"
        const val COMPANION_PKG = "io.homeassistant.companion.android"
        private const val FAIL = "\u0000"
    }
}
