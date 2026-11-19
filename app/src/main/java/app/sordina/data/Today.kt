package app.sordina.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.concurrent.Executors

/** Una notifica arrivata oggi: chi, se è passata o aspetta, e la regola che l'ha deciso. Il testo no, solo la sua impronta. */
data class TodayItem(
    val time: Long,
    val key: String,
    val pkg: String,
    val app: String,
    val who: String,
    /** Impronta del testo: riconosce un aggiornamento identico della stessa notifica */
    val sig: Int,
    val passed: Boolean,
    val reason: String,
)

/**
 * Le notifiche di oggi, per Impostazioni → Notifiche → Oggi: serve a capire perché una cosa è passata
 * (o non è passata). Solo oggi: a mezzanotte si ricomincia. Resta sul telefono,
 * fuori dal backup.
 */
class Today(context: Context) {
    private val file = File(context.filesDir, "today.json")
    private val io = Executors.newSingleThreadExecutor()
    private val _items = MutableStateFlow<List<TodayItem>>(emptyList())
    val items: StateFlow<List<TodayItem>> = _items

    init { io.execute { runCatching { load() } } }

    /** Aggiunge una notifica; un aggiornamento identico della stessa (stesso testo, stesso esito) non conta due volte. */
    fun add(item: TodayItem) {
        synchronized(this) {
            val day = startOfDay(item.time)
            val kept = _items.value.filter { it.time >= day }
            val last = kept.lastOrNull { it.key == item.key }
            if (last != null && last.sig == item.sig && last.passed == item.passed) return
            _items.value = (kept + item).takeLast(MAX)
        }
        save()
    }

    /** Solo quelle di oggi, anche se da mezzanotte non è arrivato niente. */
    fun current(now: Long = System.currentTimeMillis()): List<TodayItem> {
        val day = startOfDay(now)
        return _items.value.filter { it.time >= day }
    }

    fun wipe() {
        synchronized(this) { _items.value = emptyList() }
        io.execute { runCatching { file.delete() } }
    }

    private fun save() {
        val snapshot = _items.value
        io.execute {
            runCatching {
                val a = JSONArray()
                snapshot.forEach { i ->
                    a.put(JSONObject().put("t", i.time).put("k", i.key).put("p", i.pkg).put("a", i.app)
                        .put("w", i.who).put("x", i.sig).put("ok", i.passed).put("r", i.reason))
                }
                file.writeSafely(a.toString())
            }
        }
    }

    private fun load() {
        if (!file.exists()) return
        val a = JSONArray(file.readText())
        val day = startOfDay(System.currentTimeMillis())
        val list = (0 until a.length()).mapNotNull { n ->
            val o = a.optJSONObject(n) ?: return@mapNotNull null
            TodayItem(o.optLong("t"), o.optString("k"), o.optString("p"), o.optString("a"), o.optString("w"),
                o.optInt("x"), o.optBoolean("ok"), o.optString("r"))
        }.filter { it.time >= day }
        synchronized(this) { if (_items.value.isEmpty()) _items.value = list }
    }

    companion object {
        const val MAX = 500
        fun startOfDay(t: Long): Long = Calendar.getInstance().apply {
            timeInMillis = t
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
