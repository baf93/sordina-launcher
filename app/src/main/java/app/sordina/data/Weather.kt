package app.sordina.data

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

    fun hasPermission() =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** "14° nuvoloso", oppure null. Chiamata di rete: fuori dal thread principale. */
    fun now(): String? {
        if (!hasPermission()) return null
        if (System.currentTimeMillis() - at < 3600_000 && cached != null) return cached
        val lm = context.getSystemService(LocationManager::class.java) ?: return cached
        val loc = runCatching {
            listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
                .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
                .maxByOrNull { it.time }
        }.getOrNull() ?: return cached
        val lat = String.format(Locale.US, "%.1f", loc.latitude)
        val lon = String.format(Locale.US, "%.1f", loc.longitude)
        val text = runCatching {
            val c = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code&timezone=auto")
                .openConnection() as HttpURLConnection
            c.connectTimeout = 4000; c.readTimeout = 5000
            val body = c.inputStream.bufferedReader().use { it.readText() }
            c.disconnect()
            val cur = JSONObject(body).getJSONObject("current")
            "${Math.round(cur.getDouble("temperature_2m"))}° ${describe(cur.getInt("weather_code"))}"
        }.getOrNull() ?: return cached
        cached = text; at = System.currentTimeMillis()
        return text
    }

    private fun describe(code: Int) = when (code) {
        0, 1 -> "sereno"
        2 -> "poco nuvoloso"
        3 -> "nuvoloso"
        45, 48 -> "nebbia"
        in 51..67, in 80..82 -> "pioggia"
        in 71..77, 85, 86 -> "neve"
        in 95..99 -> "temporale"
        else -> ""
    }
}
