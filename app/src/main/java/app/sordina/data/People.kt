package app.sordina.data

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import app.sordina.service.Talking

/** Un contatto con almeno un numero. */
data class Contact(
    val id: Long,
    val name: String,
    val number: String,
    val starred: Boolean,
    val timesContacted: Int,
    /** La chiave stabile di Android (LOOKUP_KEY), già ripulita: resta la stessa se rinomini il contatto */
    val lookup: String = "",
) {
    val normalized: String = Search.normalize(name)
    /** Come il cervello e le regole chiamano questa persona: la chiave stabile se c'è, se no il nome */
    val token: String get() = if (lookup.isNotEmpty()) "@$lookup" else normalized
}

/** Rubrica (solo lettura, tranne i preferiti) e persone importanti. */
/** La chiave di Android ripulita: solo lettere, cifre, punto e trattino (finisce in file separati da tab e virgole). */
internal fun cleanLookup(k: String?): String = k.orEmpty().filter { it.isLetterOrDigit() || it == '.' || it == '-' }

class People(private val context: Context, private val prefs: Prefs) {
    @Volatile private var cache: List<Contact>? = null

    fun hasPermission() =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun invalidate() { cache = null; names = null; nameWords = null; byNumber = null }

    @Volatile private var names: Set<String>? = null
    @Volatile private var nameWords: Set<String>? = null

    /** I nomi della rubrica, normalizzati (per riconoscere chi ti scrive). */
    fun normalizedNames(): Set<String> = names ?: all().mapTo(HashSet()) { it.normalized }.also { names = it }

    /** Le singole parole dei nomi della rubrica ("anna", "rossi"): per capire se una parola è un nome. */
    fun nameWords(): Set<String> = nameWords ?: all().flatMapTo(HashSet()) { it.normalized.split(' ') }.also { nameWords = it }

    /** La foto piccola del contatto (quella della rubrica), già ridotta a [px]; null se non ce l'ha. */
    fun photo(id: Long, px: Int): android.graphics.Bitmap? = runCatching {
        if (!hasPermission()) return null
        val uri = android.content.ContentUris.withAppendedId(android.provider.ContactsContract.Contacts.CONTENT_URI, id)
        android.provider.ContactsContract.Contacts.openContactPhotoInputStream(context.contentResolver, uri, false)?.use { s ->
            android.graphics.BitmapFactory.decodeStream(s)?.let { b -> android.graphics.Bitmap.createScaledBitmap(b, px, px, true) }
        }
    }.getOrNull()

    /** La rubrica cambia (un contatto rinominato, aggiunto, tolto): si rilegge alla prossima richiesta. */
    @Volatile private var watching = false
    private fun watch() {
        if (watching) return
        watching = true
        runCatching {
            context.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true,
                object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) { invalidate() }
                })
        }.onFailure { watching = false }
    }

    fun all(): List<Contact> {
        cache?.let { return it }
        if (!hasPermission()) return emptyList()
        watch()
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
                    ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                ),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val name = c.getString(1) ?: continue
                    val number = c.getString(2) ?: continue
                    val primary = c.getInt(4) == 1
                    if (id in out && !primary) continue
                    out[id] = Contact(id, name, number, c.getInt(3) == 1, 0, cleanLookup(c.getString(5)))
                }
            }
        }
        // Ordine fisso (per id): stessa rubrica, stesse risposte, qualunque ordine dia Android
        val list = out.values.sortedBy { it.id }
        cache = list
        followRenames(list)
        return list
    }

    @Volatile private var byNumber: Map<String, String>? = null

    /** Le ultime 9 cifre: "+39 333 1234567" e "3331234567" sono lo stesso numero. */
    private fun tail(number: String) = number.filter { it.isDigit() }.takeLast(9)

    /**
     * Chi è davvero chi scrive, dal legame con la rubrica che dà l'app ("tel:…", la chiave del contatto):
     * il nome normalizzato del contatto, o null. Il nome che si vede nella notifica non c'entra.
     */
    fun nameForUri(uri: String?): String? {
        if (uri.isNullOrBlank()) return null
        val u = runCatching { android.net.Uri.parse(uri) }.getOrNull() ?: return null
        return when {
            u.scheme == "tel" -> {
                val t = tail(u.schemeSpecificPart.orEmpty()).takeIf { it.length >= 6 } ?: return null
                val map = byNumber ?: all().filter { it.number.isNotBlank() }.groupBy { tail(it.number) }
                    .mapNotNull { (k, cs) -> cs.map { it.normalized }.distinct().singleOrNull()?.let { k to it } }.toMap().also { byNumber = it }
                map[t]
            }
            u.scheme == "content" && u.authority == ContactsContract.AUTHORITY -> {
                val segs = u.pathSegments
                val i = segs.indexOf("lookup")
                val key = if (i >= 0) segs.getOrNull(i + 1) else null
                val id = segs.lastOrNull()?.toLongOrNull()
                (key?.let { k -> all().firstOrNull { it.lookup == cleanLookup(k) } } ?: id?.let { x -> all().firstOrNull { it.id == x } })?.normalized
            }
            else -> null
        }
    }

    /** Il contatto di un token ("@chiave" o nome normalizzato); per nome solo se è uno solo. */
    fun byToken(token: String): Contact? =
        if (token.startsWith("@")) all().firstOrNull { it.lookup == token.substring(1) }
        else all().filter { it.normalized == token }.singleOrNull()

    /** Il token di chi ha [name] (normalizzato): la chiave stabile se il nome è di un contatto solo, se no il nome. */
    fun tokenOf(name: String): String {
        if (name.startsWith("@")) return name
        return all().filter { it.normalized == name }.singleOrNull()?.token ?: name
    }

    /**
     * Le persone che segui per nome (importanti, regole per persona, soprannomi) restano loro anche se le rinomini
     * in rubrica: Sordina ricorda la chiave stabile di ognuna e, se il nome è cambiato, sposta tutto sul nome nuovo.
     */
    @Synchronized
    private fun followRenames(list: List<Contact>) {
        if (list.isEmpty()) return
        val byLookup = list.filter { it.lookup.isNotEmpty() }.associateBy { it.lookup }
        val known = prefs.personLookups.toMutableMap()
        val renames = HashMap<String, String>()
        // Le chiavi già note: se il nome è cambiato, si sposta
        for ((lookup, old) in known.toMap()) {
            val c = byLookup[lookup] ?: continue
            if (c.normalized != old) { renames[old] = c.normalized; known[lookup] = c.normalized }
        }
        // I nomi seguiti ma senza chiave ancora: la si impara (solo se il nome è di un contatto solo)
        val followed = prefs.importantNames + prefs.importantInGroups + prefs.nicknames.values +
            prefs.personRules.keys.filter { '|' !in it || it.substringBefore('|') in app.sordina.service.Classifier.CONTACT_NAMED }.map { it.substringAfter('|') }
        val counts = list.groupingBy { it.normalized }.eachCount()
        for (n in followed) {
            if (n in known.values || n in renames.values || counts[n] != 1) continue
            list.firstOrNull { it.normalized == n && it.lookup.isNotEmpty() }?.let { known[it.lookup] = n }
        }
        if (renames.isNotEmpty()) applyRenames(renames)
        if (known != prefs.personLookups) prefs.personLookups = known
    }

    private fun applyRenames(r: Map<String, String>) {
        fun n(x: String) = r[x] ?: x
        prefs.importantNames = prefs.importantNames.mapTo(HashSet(), ::n)
        prefs.importantInGroups = prefs.importantInGroups.mapTo(HashSet(), ::n)
        prefs.nicknames = prefs.nicknames.mapValues { n(it.value) }
        // Solo dove il nome viene dalla rubrica (WhatsApp, SMS, telefono…): su Telegram il nome lo sceglie chi scrive,
        // e rinominarlo qui non cambia quello che arriva
        val named = app.sordina.service.Classifier.CONTACT_NAMED
        prefs.personRules = prefs.personRules.mapKeys { (k, _) ->
            if ('|' in k) { val pkg = k.substringBefore('|'); if (pkg in named) pkg + "|" + n(k.substringAfter('|')) else k } else n(k)
        }
        prefs.passUntil = prefs.passUntil.mapKeys { (k, _) -> if (k.startsWith("p:")) "p:" + n(k.removePrefix("p:")) else k }
        prefs.carHidden = prefs.carHidden.mapTo(HashSet(), ::n)
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
    fun importantKey(name: String?): String? = app.sordina.service.Classifier.importantKey(name, prefs.importantNames, exact = false)

    fun importantList(): List<String> = prefs.importantNames.sorted()

    /** Rende importante una persona e, se possibile, la segna tra i preferiti di Android (le sue chiamate suonano anche con Non disturbare). */
    fun setImportant(contact: Contact, important: Boolean) {
        val n = contact.normalized
        // Riaggiunta dopo averla messa "In sordina": quella regola vincerebbe sull'importante e i suoi messaggi resterebbero fermi
        if (important) prefs.personRules.keys.filter { it == n || it.endsWith("|$n") }.takeIf { it.isNotEmpty() }?.let { ks -> prefs.personRules = prefs.personRules - ks.toSet() }
        // Entra ovunque: le stesse persone "solo su un'app" sarebbero doppioni
        if (important) prefs.peopleIn = prefs.peopleIn.filterTo(HashSet()) { !it.endsWith("|$n") }
        prefs.importantNames = if (important) prefs.importantNames + n else prefs.importantNames - n
        setStarred(contact.id, important, wasStarred = contact.starred)
    }

    /**
     * Toglie una persona dalle importanti: anche dai gruppi, da "lascia passare" e dalle conversazioni in corso
     * (i messaggi che passavano perché era importante, letti, la facevano passare ancora per un quarto d'ora alla volta).
     * [unstar]: toglie anche la stella di Android (non dall'onboarding: lì le stelle erano tue da prima).
     */
    fun removeImportantName(n: String, unstar: Boolean = true) {
        prefs.importantNames = prefs.importantNames - n
        prefs.importantInGroups = prefs.importantInGroups - n
        prefs.passUntil = prefs.passUntil - "p:$n"
        val gone = prefs.personAliases.filterValues { it == n }.keys
        prefs.personAliases = prefs.personAliases - gone
        prefs.ruleNames = prefs.ruleNames - gone
        Talking.forget(n)
        if (unstar) all().firstOrNull { it.normalized == n }?.let { setStarred(it.id, false, wasStarred = it.starred) }
    }

    /**
     * La stella di Android: Sordina la mette solo se mancava, e toglie solo quelle che ha messo lei
     * (si ricorda quali in [Prefs.starredBySordina]). Le stelle che avevi già restano tue.
     */
    private fun setStarred(id: Long, starred: Boolean, wasStarred: Boolean) {
        if (context.checkSelfPermission(Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) return
        val mine = id.toString() in prefs.starredBySordina
        if (starred && wasStarred) return
        if (!starred && !mine) return
        val ok = runCatching {
            val v = ContentValues().apply { put(ContactsContract.Contacts.STARRED, if (starred) 1 else 0) }
            context.contentResolver.update(
                ContactsContract.Contacts.CONTENT_URI, v, "${ContactsContract.Contacts._ID}=?", arrayOf(id.toString()),
            ) > 0
        }.getOrDefault(false)
        if (ok) prefs.starredBySordina = if (starred) prefs.starredBySordina + id.toString() else prefs.starredBySordina - id.toString()
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
