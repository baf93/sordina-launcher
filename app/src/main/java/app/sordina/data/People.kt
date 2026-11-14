package app.sordina.data

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.telephony.TelephonyManager

/** Un contatto con almeno un numero. */
data class Contact(
    val id: Long,
    val name: String,
    val number: String,
    val starred: Boolean,
    val timesContacted: Int,
) {
    val normalized: String = Search.normalize(name)
}

/** Rubrica (solo lettura, tranne i preferiti) e persone importanti. */
class People(private val context: Context, private val prefs: Prefs) {
    @Volatile private var cache: List<Contact>? = null

    fun hasPermission() =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun invalidate() { cache = null }

    fun all(): List<Contact> {
        cache?.let { return it }
        if (!hasPermission()) return emptyList()
        val out = LinkedHashMap<Long, Contact>()
        runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.STARRED,
                    ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY,
                ),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val name = c.getString(1) ?: continue
                    val number = c.getString(2) ?: continue
                    val primary = c.getInt(4) == 1
                    if (id in out && !primary) continue
                    out[id] = Contact(id, name, number, c.getInt(3) == 1, 0)
                }
            }
        }
        return out.values.toList().also { cache = it }
    }

    fun byId(id: Long): Contact? = all().firstOrNull { it.id == id }

    /** Contatti che corrispondono alla ricerca, i preferiti per primi. */
    fun search(query: String, limit: Int = 3): List<Contact> {
        val q = Search.normalize(query)
        if (q.length < 2) return emptyList()
        return all().asSequence()
            .map { it to Search.score(q, it.normalized) }
            .filter { it.second >= 50 }
            .sortedWith(compareByDescending<Pair<Contact, Int>> { it.second }
                .thenByDescending { if (it.first.starred) 1 else 0 }
                .thenByDescending { if (isImportant(it.first.name)) 1 else 0 })
            .take(limit).map { it.first }.toList()
    }

    /** Persone importanti: scelte a mano (nomi normalizzati). */
    fun isImportant(name: String?): Boolean = importantKey(name) != null

    /** Quale persona importante è [name] (il suo nome salvato), oppure null. */
    fun importantKey(name: String?): String? {
        if (name.isNullOrBlank()) return null
        val n = Search.normalize(name)
        val set = prefs.importantNames
        if (n in set) return n
        // "Anna Rossi" importante riconosce anche la notifica che dice solo "Anna"... ma non il contrario
        // (Un importante salvato solo come "Marco" non fa passare ogni "Marco Bianchi")
        return set.firstOrNull { it.startsWith("$n ") }
    }

    fun importantList(): List<String> = prefs.importantNames.sorted()

    /** Rende importante una persona e, se possibile, la segna tra i preferiti di Android (le sue chiamate suonano anche con Non disturbare). */
    fun setImportant(contact: Contact, important: Boolean) {
        val n = contact.normalized
        prefs.importantNames = if (important) prefs.importantNames + n else prefs.importantNames - n
        setStarred(contact.id, important)
    }

    fun removeImportantName(n: String) {
        prefs.importantNames = prefs.importantNames - n
        prefs.importantInGroups = prefs.importantInGroups - n
        all().firstOrNull { it.normalized == n }?.let { setStarred(it.id, false) }
    }

    private fun setStarred(id: Long, starred: Boolean) {
        if (context.checkSelfPermission(Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) return
        runCatching {
            val v = ContentValues().apply { put(ContactsContract.Contacts.STARRED, if (starred) 1 else 0) }
            context.contentResolver.update(
                ContactsContract.Contacts.CONTENT_URI, v, "${ContactsContract.Contacts._ID}=?", arrayOf(id.toString()),
            )
        }
        invalidate()
    }

    /** Proposte per le persone importanti: i preferiti di Android. */
    fun suggestions(): List<Contact> = all().filter { it.starred }.take(12)

    /** Numero in formato internazionale, solo cifre (per wa.me e simili). */
    fun international(number: String): String {
        var n = number.filter { it.isDigit() || it == '+' }
        if (n.startsWith("+")) return n.drop(1)
        if (n.startsWith("00")) return n.drop(2)
        val iso = runCatching {
            (context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).simCountryIso
        }.getOrNull()?.lowercase().orEmpty().ifEmpty { java.util.Locale.getDefault().country.lowercase() }
        val cc = COUNTRY_CODES[iso] ?: return n
        if (cc == "1" || iso == "it" || iso == "sm" || iso == "va") return cc + n
        n = n.removePrefix("0")
        return cc + n
    }

    companion object {
        private val COUNTRY_CODES = mapOf(
            "it" to "39", "sm" to "378", "va" to "39", "ch" to "41", "fr" to "33", "de" to "49", "at" to "43",
            "es" to "34", "pt" to "351", "gb" to "44", "ie" to "353", "nl" to "31", "be" to "32", "lu" to "352",
            "us" to "1", "ca" to "1", "br" to "55", "ar" to "54", "mx" to "52", "pl" to "48", "ro" to "40",
            "gr" to "30", "se" to "46", "no" to "47", "dk" to "45", "fi" to "358", "cz" to "420", "hu" to "36",
        )
    }
}
