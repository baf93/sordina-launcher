package app.sordina.data

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
data class HaEntity(val id: String, val name: String, val state: String, val area: String?) {
    val domain: String get() = id.substringBefore('.')
    val normalized: String = Search.normalize(name)
    val isOn: Boolean get() = state == "on" || state == "open" || state == "playing" || state == "unlocked"
    val controllable: Boolean get() = domain in CONTROLLABLE

    companion object {
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

    val configured: Boolean get() = prefs.haUrl.isNotBlank() && prefs.haTokenEnc.isNotBlank()

    fun setToken(token: String) {
        prefs.haTokenEnc = if (token.isBlank()) "" else encrypt(token.trim())
        cache = emptyList()
    }

    private fun token(): String? = prefs.haTokenEnc.takeIf { it.isNotBlank() }?.let { runCatching { decrypt(it) }.getOrNull() }

    /** Chiamata HTTP sincrona: da usare solo fuori dal thread principale. */
    private fun request(path: String, body: JSONObject? = null): String? {
        val t = token() ?: return null
        val url = prefs.haUrl.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val c = URL(url + path).openConnection() as HttpURLConnection
            c.connectTimeout = 4000
            c.readTimeout = 6000
            c.setRequestProperty("Authorization", "Bearer $t")
            c.setRequestProperty("Content-Type", "application/json")
            if (body != null) {
                c.requestMethod = "POST"
                c.doOutput = true
                c.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }
            c.disconnect()
            if (code in 200..299) text else null
        }.getOrNull()
    }

    /** Verifica la connessione. Restituisce il messaggio da mostrare. */
    fun test(): String {
        if (!configured) return "Manca l'indirizzo o il token"
        val r = request("/api/") ?: return "Non risponde: controlla indirizzo e token"
        return runCatching { JSONObject(r).optString("message", "Collegato") }.getOrDefault("Collegato")
    }

    /** Entità (in cache per un minuto). */
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
            out += HaEntity(id, name, o.optString("state"), attrs?.optString("area")?.takeIf { it.isNotBlank() })
        }
        cache = out
        cacheAt = System.currentTimeMillis()
        return out
    }

    /** Passa la frase ad Assist: HA conosce stanze, alias ed entità esposte. Restituisce la risposta di HA, o null. */
    fun assist(text: String): String? {
        if (!configured) return null
        val body = JSONObject().put("text", text).put("language", java.util.Locale.getDefault().language)
        val r = request("/api/conversation/process", body) ?: return null
        return runCatching {
            JSONObject(r).getJSONObject("response").getJSONObject("speech").getJSONObject("plain").getString("speech")
        }.getOrDefault("Fatto")
    }

    /** I calendari di Home Assistant: (entity_id, nome). */
    fun calendars(): List<Pair<String, String>> {
        val r = request("/api/calendars") ?: return emptyList()
        return runCatching {
            val a = JSONArray(r)
            (0 until a.length()).map { a.getJSONObject(it).let { o -> o.getString("entity_id") to o.optString("name", o.getString("entity_id")) } }
        }.getOrDefault(emptyList())
    }

    @Volatile private var calCache: Pair<Long, List<Upcoming>> = 0L to emptyList()

    fun resetCalendarCache() { calCache = 0L to emptyList() }

    /** Gli eventi con orario dei calendari scelti, da adesso a [days] giorni. In cache 10 minuti. */
    fun calendarEvents(ids: Set<String>, days: Int = 7): List<Upcoming> {
        if (!configured || ids.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        if (now - calCache.first < 600_000) return calCache.second.filter { it.end > now }
        val zone = java.time.ZoneId.systemDefault()
        val start = java.time.OffsetDateTime.now()
        val end = start.plusDays(days.toLong())
        val out = ArrayList<Upcoming>()
        for (id in ids) {
            val r = request("/api/calendars/$id?start=${java.net.URLEncoder.encode(start.toString(), "UTF-8")}&end=${java.net.URLEncoder.encode(end.toString(), "UTF-8")}") ?: continue
            runCatching {
                val a = JSONArray(r)
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    val s = o.optJSONObject("start")?.optString("dateTime").orEmpty()
                    val e = o.optJSONObject("end")?.optString("dateTime").orEmpty()
                    if (s.isBlank()) continue // giornata intera
                    val b = java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli()
                    val f = if (e.isBlank()) b + 3600_000 else java.time.OffsetDateTime.parse(e).toInstant().toEpochMilli()
                    out += Upcoming((id + s).hashCode().toLong(), o.optString("summary").ifBlank { "Evento" }, b, f,
                        o.optString("location").takeIf { it.isNotBlank() }, "ha", false)
                }
            }
        }
        calCache = now to out
        return out.filter { it.end > now }
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
    }
}
