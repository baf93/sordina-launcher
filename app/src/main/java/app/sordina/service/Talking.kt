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

    @Synchronized
    fun clear() = until.clear()
}
