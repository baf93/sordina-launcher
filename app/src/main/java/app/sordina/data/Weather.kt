package app.sordina.data

import app.sordina.tr

import app.sordina.R

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Meteo da Open-Meteo: gratuito, senza account.
 * Riceve solo la posizione arrotondata (circa 10 km), al massimo una volta all'ora.
 */
class Weather(private val context: Context) {
    @Volatile private var cached: String? = null
    @Volatile private var at = 0L
    @Volatile private var failedAt = 0L

    /** Pioggia in arrivo nelle prossime ore: quando (inizio dell'ora) e con che probabilità. null se non piove o piove già. */
    @Volatile var rain: Pair<Long, Int>? = null
        private set

    fun hasPermission() =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** "14° nuvoloso", oppure null. Chiamata di rete: fuori dal thread principale. */
    fun now(): String? {
        if (!hasPermission()) return null
        if (System.currentTimeMillis() - at < 3600_000 && cached != null) return cached
        // Dopo un tentativo fallito (senza rete, senza posizione) si riprova fra 10 minuti, non a ogni ritorno a Casa
        if (System.currentTimeMillis() - failedAt < 600_000) return cached
        failedAt = System.currentTimeMillis()
        val lm = context.getSystemService(LocationManager::class.java) ?: return cached
        val loc = runCatching {
            listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
                .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
                .maxByOrNull { it.time }
        }.getOrNull() ?: return cached
        val lat = String.format(Locale.US, "%.1f", loc.latitude)
        val lon = String.format(Locale.US, "%.1f", loc.longitude)
        val text = runCatching {
            val c = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code&hourly=precipitation_probability,precipitation&forecast_hours=4&timezone=auto")
                .openConnection() as HttpURLConnection
            c.connectTimeout = 4000; c.readTimeout = 5000
            val body = c.inputStream.bufferedReader().use { it.readText() }
            c.disconnect()
            val root = JSONObject(body)
            val cur = root.getJSONObject("current")
            rain = runCatching { rainSoon(root, cur.getInt("weather_code")) }.getOrNull()
            "${Math.round(cur.getDouble("temperature_2m"))}° ${describe(cur.getInt("weather_code"))}"
        }.getOrNull() ?: return cached
        cached = text; at = System.currentTimeMillis(); failedAt = 0
        return text
    }

    private fun rainSoon(root: JSONObject, code: Int): Pair<Long, Int>? {
        if (code in 51..67 || code in 80..82 || code in 95..99) return null
        val h = root.getJSONObject("hourly")
        val times = h.getJSONArray("time"); val prob = h.getJSONArray("precipitation_probability"); val mm = h.getJSONArray("precipitation")
        val zone = java.time.ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        for (i in 0 until times.length()) {
            val t = java.time.LocalDateTime.parse(times.getString(i)).atZone(zone).toInstant().toEpochMilli()
            if (t + 3600_000 <= now) continue
            val p = prob.optInt(i, 0)
            if (p >= 60 && mm.optDouble(i, 0.0) >= 0.2) return maxOf(t, now) to p
        }
        return null
    }

    private fun describe(code: Int) = when (code) {
        0, 1 -> tr(R.string.wx_clear)
        2 -> tr(R.string.wx_partly)
        3 -> tr(R.string.wx_cloudy)
        45, 48 -> tr(R.string.wx_fog)
        in 51..67, in 80..82 -> tr(R.string.wx_rain)
        in 71..77, 85, 86 -> tr(R.string.wx_snow)
        in 95..99 -> tr(R.string.wx_storm)
        else -> ""
    }
}
