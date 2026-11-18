package app.sordina.data

import android.content.Context
import android.content.Intent
import org.json.JSONObject
import java.io.File

/**
 * La copia di ogni giorno, per il backup di Google.
 *
 * Una volta al giorno (aprendo Sordina, mai in sottofondo) si scrive un file solo con le impostazioni, le imprese,
 * lo storico del freno, le routine e quello che il cervello ha imparato (le risposte veloci no: possono contenere
 * testo personale). Android lo porta nel
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
    private val FILES = listOf("brain.tsv")
    /** Restano su questo telefono: il token (cifrato con una chiave che non lascia il telefono) e le reti di casa */
    private val SECRET = setOf("haToken", "haTokenEnc", "haHomeNets", "haAlarms")
    /**
     * Restano su questo telefono anche i nomi delle chat e di chi ti scrive in ogni app: le chat spente, le persone che
     * entrano da un'app sola, i nomi collegati a una persona importante (e quelli rifiutati). Sul telefono nuovo si
     * rifanno con un tocco quando quelle notifiche arrivano. Le regole per tipo di notifica invece viaggiano.
     * Anche l'elenco delle app che ho fatto passare come codice (securityApps) resta qui; la scelta "non indovinare" viaggia.
     */
    // I nomi delle persone importanti (importantNames) invece viaggiano: sono la scelta principale, rifarla a mano
    // su un telefono nuovo sarebbe perdere la cosa più importante. Stanno nella copia sul telefono, come i contatti.
    private val LOCAL = setOf("personRules", "holdAlways", "personAliases", "aliasRefused", "securityApps", "haGuarded", "haGuardedAt", "haRoutines")
    /** I nomi da mostrare delle regole: viaggiano solo quelli dei tipi di notifica (canali), non di chat e persone */
    private const val RULE_NAMES = "ruleNames"

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
            // Solo i file di impostazioni che Sordina scrive: una copia manomessa non ne crea altri
            for (name in prefs.keys()) {
                if (name !in PREFS) continue
                val sp = ctx.getSharedPreferences(name, Context.MODE_PRIVATE)
                val keep = (SECRET + LOCAL).associateWith { sp.all[it] }.filterValues { it != null }
                val e = sp.edit().clear()
                @Suppress("UNCHECKED_CAST")
                for ((k, v) in decode(prefs.getJSONObject(name))) when (v) {
                    is Boolean -> e.putBoolean(k, v)
                    is Int -> e.putInt(k, v)
                    is Long -> e.putLong(k, v)
                    is Float -> e.putFloat(k, v)
                    is Set<*> -> e.putStringSet(k, v as Set<String>)
                    else -> e.putString(k, v.toString())
                }
                @Suppress("UNCHECKED_CAST")
                keep.forEach { (k, v) -> if (v is String) e.putString(k, v) else if (v is Set<*>) e.putStringSet(k, v as Set<String>) }
                e.commit()
            }
        }
        o.optJSONObject("files")?.let { files -> for (name in files.keys()) if (name in FILES) File(ctx.filesDir, name).writeSafely(files.getString(name)) }
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
        val all = ctx.getSharedPreferences(name, Context.MODE_PRIVATE).all
        val offTypes = if (name == "sordina") runCatching { Prefs(ctx).offTypes }.getOrDefault(emptySet()) else emptySet()
        return encode(all, offTypes)
    }

    /** Le impostazioni come escono dal telefono: senza i segreti né i nomi delle chat e delle persone. */
    internal fun encode(all: Map<String, *>, offTypes: Set<String>): JSONObject {
        val o = JSONObject()
        for ((k, raw) in all) {
            if (k in SECRET || k in LOCAL || raw == null) continue
            val v = if (k == RULE_NAMES && raw is String) onlyTypes(raw, offTypes) else raw
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

    /** Dei nomi delle regole tiene solo quelli dei tipi spenti ("pacchetto|canale"): niente nomi di chat o persone. */
    internal fun onlyTypes(json: String, offTypes: Set<String>): String = runCatching {
        val src = JSONObject(json)
        val out = JSONObject()
        for (k in src.keys()) if (k in offTypes) out.put(k, src.get(k))
        out.toString()
    }.getOrDefault("{}")

    /** Il contrario di [encode]: quello che si rimette. Le chiavi che restano sul telefono non si toccano, anche se la copia le avesse. */
    internal fun decode(vals: JSONObject): Map<String, Any> {
        val out = LinkedHashMap<String, Any>()
        for (k in vals.keys()) {
            if (k in SECRET || k in LOCAL) continue
            val v = vals.optJSONObject(k) ?: continue
            runCatching { when (v.getString("t")) {
                "b" -> v.getBoolean("v")
                "i" -> v.getInt("v")
                "l" -> v.getLong("v")
                "f" -> v.getDouble("v").toFloat()
                "s" -> v.getString("v")
                "set" -> v.getJSONArray("v").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
                else -> null
            } }.getOrNull()?.let { out[k] = it }
        }
        return out
    }
}
