package app.sordina.data

import android.content.Context
import android.content.Intent
import org.json.JSONObject
import java.io.File

/**
 * La copia di ogni giorno, per il backup di Google.
 *
 * Una volta al giorno (aprendo Sordina, mai in sottofondo) si scrive un file solo con le impostazioni, le imprese,
 * lo storico del freno, le routine, le risposte veloci e quello che il cervello ha imparato. Android lo porta nel
 * backup di Google del telefono quando lo fa per tutte le app (in carica e col Wi-Fi), ed è l'unico file che porta:
 * le notifiche tenute, i piani e lo sfondo restano qui. Il token di Home Assistant non esce mai.
 *
 * Su un telefono nuovo (o reinstallando) Android rimette il file prima del primo avvio: l'avvio lo trova e chiede
 * se rimettere tutto com'era.
 */
object Backup {
    private const val DIR = "backup"
    private const val COPY = "sordina.json"
    private const val FOUND = "restored.json"
    private val PREFS = listOf("sordina", "habits", "tally", "brain", "card_sense")
    private val FILES = listOf("replies.json", "brain.tsv")
    private val SECRET = setOf("haToken", "haTokenEnc")

    private fun dir(ctx: Context) = File(ctx.filesDir, DIR).apply { mkdirs() }

    /** All'avvio: se la configurazione non è ancora fatta ma c'è una copia, è arrivata da Google. La si mette da parte. */
    fun onStart(ctx: Context, onboarded: Boolean) {
        val copy = File(dir(ctx), COPY)
        if (!onboarded && copy.exists()) copy.renameTo(File(dir(ctx), FOUND))
    }

    /** La copia ritrovata da rimettere, con il giorno in cui è stata fatta. null se non c'è. */
    fun found(ctx: Context): java.time.LocalDate? = File(dir(ctx), FOUND).takeIf { it.exists() }?.let { f ->
        runCatching { java.time.LocalDate.parse(JSONObject(f.readText()).getString("day")) }.getOrNull()
    }

    fun forget(ctx: Context) { File(dir(ctx), FOUND).delete() }

    /** Il giorno dell'ultima copia fatta qui. */
    fun last(ctx: Context): java.time.LocalDate? = File(dir(ctx), COPY).takeIf { it.exists() }?.let {
        java.time.Instant.ofEpochMilli(it.lastModified()).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    }

    /** Una volta al giorno al massimo, e solo a configurazione fatta (prima non c'è niente da salvare). Fuori dal thread principale. */
    fun daily(ctx: Context, onboarded: Boolean) {
        if (!onboarded || last(ctx) == java.time.LocalDate.now()) return
        runCatching {
            val o = JSONObject().put("v", 1).put("day", java.time.LocalDate.now().toString())
            val prefs = JSONObject()
            PREFS.forEach { name -> prefs.put(name, dump(ctx, name)) }
            o.put("prefs", prefs)
            val files = JSONObject()
            FILES.forEach { name -> File(ctx.filesDir, name).takeIf { it.exists() }?.let { files.put(name, it.readText()) } }
            o.put("files", files)
            val tmp = File(dir(ctx), "$COPY.tmp")
            tmp.writeText(o.toString())
            tmp.renameTo(File(dir(ctx), COPY))
        }
    }

    /** Rimette la copia ritrovata e riapre Sordina da capo (tutto quello in memoria va riletto). */
    fun restore(ctx: Context): Boolean {
        val f = File(dir(ctx), FOUND)
        val o = runCatching { JSONObject(f.readText()) }.getOrNull() ?: return false
        o.optJSONObject("prefs")?.let { prefs ->
            for (name in prefs.keys()) {
                val sp = ctx.getSharedPreferences(name, Context.MODE_PRIVATE)
                val keep = SECRET.associateWith { sp.all[it] }.filterValues { it != null }
                val e = sp.edit().clear()
                val vals = prefs.getJSONObject(name)
                for (k in vals.keys()) {
                    val v = vals.getJSONObject(k)
                    when (v.getString("t")) {
                        "b" -> e.putBoolean(k, v.getBoolean("v"))
                        "i" -> e.putInt(k, v.getInt("v"))
                        "l" -> e.putLong(k, v.getLong("v"))
                        "f" -> e.putFloat(k, v.getDouble("v").toFloat())
                        "s" -> e.putString(k, v.getString("v"))
                        "set" -> v.getJSONArray("v").let { a -> e.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet()) }
                    }
                }
                keep.forEach { (k, v) -> if (v is String) e.putString(k, v) }
                e.commit()
            }
        }
        o.optJSONObject("files")?.let { files -> for (name in files.keys()) if (name in FILES) File(ctx.filesDir, name).writeText(files.getString(name)) }
        f.delete()
        return true
    }

    /** Riapre l'app da capo: nuovo processo, così ogni parte rilegge quello che è stato rimesso. */
    fun restart(ctx: Context) {
        ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.let {
            ctx.startActivity(Intent.makeRestartActivityTask(it.component))
        }
        Runtime.getRuntime().exit(0)
    }

    /** La pagina del backup di Google sul telefono (o le impostazioni, se non c'è). */
    fun openSettings(ctx: Context) {
        val google = Intent("com.google.android.gms.backup.ACTION_BACKUP_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(google) }.onFailure {
            runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    private fun dump(ctx: Context, name: String): JSONObject {
        val o = JSONObject()
        for ((k, v) in ctx.getSharedPreferences(name, Context.MODE_PRIVATE).all) {
            if (k in SECRET || v == null) continue
            o.put(k, when (v) {
                is Boolean -> JSONObject().put("t", "b").put("v", v)
                is Int -> JSONObject().put("t", "i").put("v", v)
                is Long -> JSONObject().put("t", "l").put("v", v)
                is Float -> JSONObject().put("t", "f").put("v", v.toDouble())
                is Set<*> -> JSONObject().put("t", "set").put("v", org.json.JSONArray(v.map { it.toString() }))
                else -> JSONObject().put("t", "s").put("v", v.toString())
            })
        }
        return o
    }
}
