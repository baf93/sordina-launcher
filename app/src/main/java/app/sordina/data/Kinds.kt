package app.sordina.data

import android.content.Context
import android.content.SharedPreferences

/**
 * I tipi di notifica che ogni app ha mandato, imparati man mano che arrivano: Android non dà a un launcher
 * l'elenco dei canali di un'app (solo alle app degli accessori collegati). Per le app di messaggi i "tipi" sono le chat.
 * Servono alla pagina di un'app in Impostazioni → Notifiche, dove si spengono le eccezioni.
 *
 * Chiavi: "pacchetto|c:canale" per un tipo, "pacchetto|p:persona o gruppo" (normalizzato) per una chat.
 * Valore: "ultima volta (ms)|nome da mostrare". Si scrive solo quando è nuovo o non si vedeva da un giorno.
 */
class Kinds(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("kinds", Context.MODE_PRIVATE)

    data class Kind(val pkg: String, val isChat: Boolean, val id: String, val name: String, val seen: Long) {
        /** La chiave della regola: "pacchetto|canale" per un tipo, "pacchetto|persona" per una chat */
        val ruleKey: String get() = "$pkg|$id"
    }

    fun channel(pkg: String, id: String?, name: String?, now: Long = System.currentTimeMillis()) {
        if (id.isNullOrBlank()) return
        note("$pkg|c:$id", name?.takeIf { it.isNotBlank() } ?: id, now)
    }

    fun chat(pkg: String, key: String?, name: String?, now: Long = System.currentTimeMillis()) {
        if (key.isNullOrBlank()) return
        note("$pkg|p:$key", name?.takeIf { it.isNotBlank() } ?: key, now)
    }

    private fun note(k: String, name: String, now: Long) {
        val old = sp.getString(k, null)
        val oldSeen = old?.substringBefore('|')?.toLongOrNull() ?: 0L
        if (old != null && old.substringAfter('|') == name && now - oldSeen < DAY) return
        sp.edit().putString(k, "$now|$name").apply()
        trim(k.substringBefore('|'))
    }

    /** Tutti i tipi e le chat di un'app, i più recenti prima. */
    fun of(pkg: String): List<Kind> = sp.all.mapNotNull { (k, v) ->
        if (!k.startsWith("$pkg|") || v !is String) return@mapNotNull null
        val rest = k.removePrefix("$pkg|")
        val chat = rest.startsWith("p:")
        if (!chat && !rest.startsWith("c:")) return@mapNotNull null
        Kind(pkg, chat, rest.substring(2), v.substringAfter('|'), v.substringBefore('|').toLongOrNull() ?: 0L)
    }.sortedByDescending { it.seen }

    /** Non più di [MAX] per app: le chat di un anno fa non servono. */
    private fun trim(pkg: String) {
        val all = of(pkg)
        if (all.size <= MAX) return
        sp.edit().apply { all.drop(MAX).forEach { remove("$pkg|${if (it.isChat) "p:" else "c:"}${it.id}") } }.apply()
    }

    companion object {
        private const val DAY = 86_400_000L
        const val MAX = 40
    }
}
