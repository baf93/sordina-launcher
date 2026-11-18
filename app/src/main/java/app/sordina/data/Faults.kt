package app.sordina.data

import java.io.File

/**
 * Gli errori che Sordina incontra, invece di sparire: gli ultimi [MAX], con quando, dove e cosa.
 * Restano sul telefono (in memoria e in un file dell'app) e si vedono in Impostazioni › Dati.
 * Niente esce dal telefono.
 */
object Faults {
    data class Fault(val time: Long, val where: String, val what: String)

    private const val MAX = 200
    private val list = ArrayDeque<Fault>()
    @Volatile private var file: File? = null

    /** Da chiamare una volta all'avvio: da lì gli errori si salvano anche su file. */
    fun init(dir: File) {
        val f = File(dir, "faults.tsv")
        file = f
        runCatching {
            if (f.exists()) synchronized(this) {
                f.readLines().takeLast(MAX).forEach { l ->
                    val p = l.split('\t', limit = 3)
                    if (p.size == 3) p[0].toLongOrNull()?.let { list.addLast(Fault(it, p[1], p[2])) }
                }
            }
        }
    }

    /** Esegue [block]; se fallisce, lo registra e restituisce null. Mai un crash, mai un errore muto. */
    inline fun <T> guard(where: String, block: () -> T): T? = try {
        block()
    } catch (t: Throwable) {
        record(where, t)
        null
    }

    fun record(where: String, t: Throwable) {
        val what = (t::class.java.simpleName + ": " + (t.message ?: "")).replace('\n', ' ').replace('\t', ' ').take(300)
        val f = Fault(System.currentTimeMillis(), where, what)
        synchronized(this) {
            list.addLast(f)
            while (list.size > MAX) list.removeFirst()
        }
        file?.let { runCatching { it.appendText("${f.time}\t${f.where}\t${f.what}\n") } }
        compactSometimes()
    }

    @Synchronized
    fun recent(): List<Fault> = list.toList().asReversed()

    fun clear() {
        synchronized(this) { list.clear() }
        file?.delete()
    }

    private var writes = 0
    private fun compactSometimes() {
        val f = file ?: return
        if (++writes % 50 != 0) return
        val snap = synchronized(this) { list.toList() }
        runCatching { f.writeSafely(snap.joinToString("") { "${it.time}\t${it.where}\t${it.what}\n" }) }
    }
}
