package app.sordina.service

import app.sordina.data.Search

/**
 * Conversazioni in corso: se hai appena letto o risposto a qualcuno (la sua notifica è stata aperta
 * o chiusa dall'app), i suoi messaggi passano per 15 minuti. Solo in memoria.
 */
object Talking {
    private const val WINDOW = 15 * 60_000L
    private val until = HashMap<String, Long>()

    private fun key(pkg: String, who: String) = pkg + "|" + Search.normalize(who)

    @Synchronized
    fun mark(pkg: String, who: String?, now: Long = System.currentTimeMillis()) {
        if (who.isNullOrBlank()) return
        until[key(pkg, who)] = now + WINDOW
        until.values.removeAll { it < now }
    }

    @Synchronized
    fun active(pkg: String, who: String?, now: Long = System.currentTimeMillis()): Boolean =
        who != null && (until[key(pkg, who)] ?: 0L) > now

    /** Le conversazioni in corso adesso (chiavi "pacchetto|persona"), per una fotografia delle regole. */
    @Synchronized
    fun activeKeys(now: Long = System.currentTimeMillis()): Set<String> = until.filterValues { it > now }.keys.toSet()

    /** C'è una conversazione in corso (qualunque) e quando finisce l'ultima. */
    @Synchronized
    fun lastUntil(now: Long = System.currentTimeMillis()): Long? = until.values.filter { it > now }.maxOrNull()

    /**
     * Dimentica le conversazioni in corso con [name] (nome normalizzato, anche solo il nome: "anna" vale per "anna rossi").
     * Serve quando togli una persona dalle importanti: i messaggi che aveva fatto passare non tengono aperta la porta.
     */
    @Synchronized
    fun forget(name: String) {
        val n = Search.normalize(name)
        if (n.isBlank()) return
        until.keys.removeAll { k -> val who = k.substringAfter('|'); who == n || n.startsWith("$who ") || who.startsWith("$n ") }
    }

    @Synchronized
    fun clear() = until.clear()
}
