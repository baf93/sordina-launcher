package app.sordina.data

import android.content.Context
import app.sordina.R
import app.sordina.tr
import com.google.mlkit.nl.entityextraction.DateTimeEntity
import com.google.mlkit.nl.entityextraction.Entity
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractionParams
import com.google.mlkit.nl.entityextraction.EntityExtractor
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors

/**
 * I piani nascosti nei messaggi e nelle mail: "ci vediamo domani alle 20 in via Roma 5", un volo, un codice di spedizione.
 * Un modello piccolo di Google (ML Kit) legge il testo sul telefono, senza mandarlo da nessuna parte.
 *
 * Al momento giusto: la scheda compare quando arriva il messaggio (per metterlo in calendario) e torna
 * il giorno stesso, un'ora e mezza prima, con Naviga per primo. I piani si ricordano (anche se leggi il messaggio
 * o il telefono si riavvia) fino a quando sono passati.
 *
 * Leggero: il modello gira solo sui testi che possono contenere un piano (un numero, "domani", "via"…),
 * e il file si riscrive solo quando cambia qualcosa.
 */
class Plans(context: Context) {
    /** Un piano: cosa, quando, dove. [at] null per spedizioni senza data. */
    data class Plan(
        val key: String, val label: String, val who: String?, val snippet: String,
        val at: Long?, val dayOnly: Boolean, val place: String?, val tracking: String?, val flight: String?, val posted: Long,
        /** Un evento con biglietto letto in una mail (concerto, teatro) */
        val event: Boolean = false,
    )

    private val file = File(context.filesDir, "plans.json")
    private val io = Executors.newSingleThreadExecutor()
    private val _plans = MutableStateFlow<Map<String, Plan>>(emptyMap())
    val plans: StateFlow<Map<String, Plan>> = _plans

    private val extractor: EntityExtractor? = runCatching {
        val lang = EntityExtractorOptions.fromLanguageTag(Locale.getDefault().toLanguageTag())
            ?: EntityExtractorOptions.fromLanguageTag(Locale.getDefault().language) ?: EntityExtractorOptions.ENGLISH
        EntityExtraction.getClient(EntityExtractorOptions.Builder(lang).build())
    }.getOrNull()
    @Volatile private var ready = false
    @Volatile private var preparing = false
    /** I testi già letti (impronta), per non far girare il modello due volte sullo stesso messaggio. */
    private val scanned = LinkedHashSet<Int>()

    init {
        io.execute { runCatching { load() } }
        prepare()
    }

    private fun prepare() {
        if (preparing || ready) return
        preparing = true
        extractor?.downloadModelIfNeeded()?.addOnSuccessListener { ready = true }?.addOnCompleteListener { preparing = false }
    }

    /**
     * Legge un messaggio ([mail] = una mail) e, se c'è un piano, lo ricorda.
     * [who]: chi scrive (o il gruppo). Il testo va al modello solo se può contenere un piano.
     */
    fun scan(key: String, appLabel: String, who: String?, text: String, postTime: Long, mail: Boolean = false) {
        if (text.length < 8) return
        // Lo stesso testo già letto (la notifica viene ripubblicata): niente seconda lettura
        val h = key.hashCode() * 31 + text.hashCode()
        synchronized(scanned) { if (!scanned.add(h)) return; if (scanned.size > 300) scanned.remove(scanned.first()) }
        val id = "$key#${text.hashCode()}"
        val low = text.lowercase()
        if (!MAYBE.containsMatchIn(low)) return
        if (mail && !MAIL_WORTH.containsMatchIn(low)) return
        val ex = extractor
        if (ex == null || !ready) {
            prepare()
            if (!mail || isEvent(text)) fallback(id, appLabel, who, text, postTime, mail)?.let(::keep)
            return
        }
        val params = EntityExtractionParams.Builder(text.take(2000))
            .setReferenceTime(postTime).setReferenceTimeZone(TimeZone.getDefault()).build()
        // Un'ora precisa scritta all'italiana ("alle 13 30", "stasera alle 9"): valgono le regole di Sordina, il modello
        // generico sbaglia (13:00 del giorno dopo). Il modello serve per tutto il resto: indirizzi, voli, spedizioni, date in altre lingue.
        val mine = DateParser.parse(text, Instant.ofEpochMilli(postTime).atZone(ZoneId.systemDefault()).toLocalDateTime()).whenAt
            ?.takeIf { !it.allDay }?.start?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
            // La data dell'ordine o dell'invio ("ordine del 15 novembre") non è l'appuntamento
            ?.takeIf { it > postTime }
        val event = mail && isEvent(text)
        ex.annotate(params).addOnSuccessListener { found ->
            var at: Long? = null; var dayOnly = false; var place: String? = null; var tracking: String? = null; var flight: String? = null
            for (a in found) for (e in a.entities) when (e.type) {
                Entity.TYPE_DATE_TIME -> {
                    val d = e.asDateTimeEntity() ?: continue
                    val g = d.dateTimeGranularity
                    if (g < DateTimeEntity.GRANULARITY_DAY) continue
                    // La prima data che viene dopo l'arrivo: in una mail di conferma la prima è spesso quella dell'ordine
                    val day = g == DateTimeEntity.GRANULARITY_DAY
                    if (at == null || (at!! <= postTime && d.timestampMillis > postTime)) { at = d.timestampMillis; dayOnly = day }
                }
                Entity.TYPE_ADDRESS -> if (place == null) place = a.annotatedText
                Entity.TYPE_TRACKING_NUMBER -> if (tracking == null) tracking = a.annotatedText
                Entity.TYPE_FLIGHT_NUMBER -> if (flight == null) flight = a.annotatedText
            }
            if (mine != null && flight == null) { at = mine; dayOnly = false }
            // Nelle mail le date sono ovunque (offerte, scadenze): conta solo un volo, una spedizione, un appuntamento con
            // l'indirizzo, o un evento con giorno e ora (concerto, teatro: il luogo spesso non è un indirizzo, "Unipol Forum")
            if (mail && flight == null && tracking == null && (at == null || dayOnly || (place == null && !event))) return@addOnSuccessListener
            plan(id, appLabel, who, text, postTime, at, dayOnly, place, tracking, flight, event)?.let(::keep)
        }.addOnFailureListener { if (!mail || event) fallback(id, appLabel, who, text, postTime, event)?.let(::keep) }
    }

    /** Senza il modello: le regole di Sordina per le date (in italiano), niente indirizzi. */
    private fun fallback(key: String, appLabel: String, who: String?, text: String, postTime: Long, far: Boolean = false): Plan? {
        val w = DateParser.parse(text, Instant.ofEpochMilli(postTime).atZone(ZoneId.systemDefault()).toLocalDateTime()).whenAt ?: return null
        if (w.allDay) return null
        return plan(key, appLabel, who, text, postTime, w.start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), false, null, null, null, far)
    }

    private fun plan(
        key: String, appLabel: String, who: String?, text: String, postTime: Long,
        at: Long?, dayOnly: Boolean, place: String?, tracking: String?, flight: String?,
        /** Un evento comprato (concerto, teatro): si prenota con mesi d'anticipo */
        far: Boolean = false,
    ): Plan? {
        // Un'ora da sola ("alle 13 30") è una risposta, non un invito: senza un indirizzo, un volo o una spedizione serve una
        // parola da appuntamento (ci vediamo, cena, riunione, dentista…)
        if (place == null && flight == null && tracking == null && !isInvite(text)) return null
        val now = System.currentTimeMillis()
        val future = at != null && at > now && at - now < (if (far) 400 else 30) * 86_400_000L
        val usable = (future && (!dayOnly || place != null || flight != null)) || tracking != null || (flight != null && (at == null || future))
        if (!usable) return null
        // Una mail arriva come "mittente\noggetto": quello che dice è l'oggetto ("Coldplay · Stadio San Siro")
        val what = if (who == null && '\n' in text) text.substringAfter('\n').ifBlank { text } else text
        return Plan(key, listOfNotNull(who, appLabel).distinct().joinToString(" · "), who, CardDetector.firstLine(what).take(80),
            at?.takeIf { future }, dayOnly, place, tracking, flight, postTime, far)
    }

    @Synchronized
    private fun keep(p: Plan) {
        // Lo stesso piano da due messaggi (o letto due volte): resta quello con più dettagli
        val same = _plans.value.values.firstOrNull { it.who == p.who && it.at != null && it.at == p.at }
        if (same != null && (same.place != null || p.place == null)) return
        _plans.value = prune(_plans.value - (same?.key ?: "") + (p.key to p))
        save()
    }

    /** Tolto a mano o messo in calendario: il piano non serve più. */
    @Synchronized
    fun forget(cardKey: String) {
        val k = cardKey.removePrefix("plan:")
        if (k !in _plans.value) return
        _plans.value = _plans.value - k
        save()
    }

    private fun prune(m: Map<String, Plan>, now: Long = System.currentTimeMillis()): Map<String, Plan> =
        m.filterValues { end(it) > now }.entries.sortedByDescending { it.value.posted }.take(50).associate { it.key to it.value }

    /** Quando smette di servire: un'ora dopo l'inizio (il giorno intero per le date senza ora), tre giorni per le spedizioni. */
    private fun end(p: Plan): Long = when {
        p.at != null -> p.at + if (p.dayOnly) 86_400_000L else 3_600_000L
        else -> p.posted + 3 * 86_400_000L
    }

    /**
     * Le schede di adesso. Un piano si vede quando arriva (6 ore, per metterlo in calendario),
     * poi sparisce e torna un'ora e mezza prima (Naviga per primo) fino a un'ora dopo.
     * Le spedizioni restano finché non le togli (tre giorni al massimo).
     */
    fun cards(now: Long = System.currentTimeMillis()): List<Card> = _plans.value.values.mapNotNull { p ->
        val fresh = now - p.posted < 6 * 3_600_000L
        val soon = p.at != null && !p.dayOnly && p.at - now in -3_600_000L..90 * 60_000L
        val today = p.at != null && p.dayOnly && Instant.ofEpochMilli(p.at).atZone(ZoneId.systemDefault()).toLocalDate() == java.time.LocalDate.now()
        if (end(p) <= now || !(fresh || soon || today || p.at == null)) return@mapNotNull null
        card(p, near = soon || today)
    }

    private fun card(p: Plan, near: Boolean): Card {
        val zone = ZoneId.systemDefault()
        if (p.at == null && p.tracking != null) {
            return Card("plan:${p.key}", CardKind.PARCEL, tr(R.string.delivery_x, p.label), p.tracking, p.snippet,
                listOf(CardAction(tr(R.string.copy), CardOp.Copy(p.tracking)), CardAction(tr(R.string.ok), CardOp.Dismiss)),
                time = p.posted, expiresAt = end(p), look = CardLook.CODE)
        }
        val w = p.at?.let { When(Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime().let { t -> if (p.dayOnly) t.toLocalDate().atStartOfDay() else t }, p.dayOnly, false) }
        val name = p.flight?.let { tr(R.string.flight_x, it) } ?: p.who?.let { tr(R.string.plan_with, it) } ?: p.snippet
        val actions = buildList {
            p.place?.let { add(CardAction(tr(R.string.navigate), CardOp.Navigate(it))) }
            if (w != null) add(CardAction(tr(R.string.add_calendar), CardOp.AddEvent(name, w)))
        }.let { a -> if (near) a else a.sortedByDescending { it.op is CardOp.AddEvent } }
            // Un piano letto in una chat potrebbe non riguardarti: lo togli senza metterlo in calendario
            .let { a -> if (a.isEmpty()) a else a + CardAction(tr(R.string.ignore), CardOp.Dismiss) }
        return Card("plan:${p.key}", if (p.flight != null) CardKind.TICKET else CardKind.PLAN, p.label,
            if (p.flight != null) name else w?.let { DateParser.describe(it).replaceFirstChar { c -> c.uppercase() } } ?: name,
            if (p.flight != null) w?.let { DateParser.describe(it) } else p.place ?: p.snippet,
            actions.ifEmpty { listOf(CardAction(tr(R.string.ok), CardOp.Dismiss)) },
            time = p.posted, expiresAt = end(p), startsAt = p.at?.takeIf { !p.dayOnly })
    }

    private fun save() {
        val snap = _plans.value.values.toList()
        io.execute {
            runCatching {
                val a = JSONArray()
                snap.forEach { p ->
                    a.put(JSONObject().put("k", p.key).put("l", p.label).put("w", p.who).put("s", p.snippet).put("at", p.at ?: 0L)
                        .put("d", p.dayOnly).put("pl", p.place).put("tr", p.tracking).put("fl", p.flight).put("p", p.posted).put("ev", p.event))
                }
                file.writeText(a.toString())
            }
        }
    }

    private fun load() {
        if (!file.exists()) return
        val a = JSONArray(file.readText())
        val m = HashMap<String, Plan>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            fun s(n: String) = if (!o.has(n) || o.isNull(n)) null else o.optString(n).ifEmpty { null }
            val p = Plan(o.getString("k"), o.optString("l"), s("w"), o.optString("s"), o.optLong("at").takeIf { it > 0 },
                o.optBoolean("d"), s("pl"), s("tr"), s("fl"), o.optLong("p"), o.optBoolean("ev"))
            // Quelli letti con la regola vecchia (un'ora sola, senza invito) non tornano
            if (p.place == null && p.flight == null && p.tracking == null && !p.event && !isInvite(p.snippet)) continue
            m[p.key] = p
        }
        synchronized(this) { _plans.value = prune(m + _plans.value) }
    }

    companion object {
        /** Può contenere un piano: un numero, un giorno, un indirizzo. Gli altri testi non arrivano al modello. */
        private val MAYBE = Regex("\\d|oggi|domani|stasera|stamattina|stanotte|dopodomani|luned|marted|mercoled|gioved|venerd|sabato|domenica|mezzogiorno|\\bvia\\b|piazza|corso|viale|tomorrow|tonight|monday|tuesday|wednesday|thursday|friday|saturday|sunday|street")
        /** Una mail che può valere una scheda (le newsletter no). */
        private val MAIL_WORTH = Regex("volo|flight|imbarco|boarding|prenotazion|booking|reservation|check-in|spedizion|tracking|consegna|delivery|appuntamento|appointment|bigliett|ticket|concert|spettacol|teatro|evento|event|ingress|festival")
    }
}

/** Le parole da appuntamento, in più lingue (testo normalizzato: minuscolo, senza accenti). */
private val INVITE = Regex(
    "\\b(ci vediamo|vediamoci|ci becchiamo|ci sentiamo|appuntamento|incontr\\w*|cena|cenare|pranzo|pranzare|colazione|aperitiv\\w*|apericena|caffe|" +
    "riunione|call|meeting|festa|compleanno|visita|dentista|medico|dottore|parrucchiere|partita|calcetto|palestra|cinema|concert\\w*|teatro|spettacol\\w*|festival|bigliett\\w*|tickets?|" +
    "prenotat\\w*|prenotazione|passo a prender\\w*|ti passo a|vieni|venite|veniamo|andiamo|usciamo|sei liber\\w|siete liberi|ti va|vi va|ritrovo|" +
    "meet|meeting|see you|dinner|lunch|breakfast|brunch|drinks|coffee|party|appointment|booked|booking|reservation|pick you up|let s|are you free|" +
    "quedamos|nos vemos|cena|comida|almuerzo|cita|reunion|fiesta|reserva|" +
    "rendez vous|rdv|on se voit|diner|dejeuner|reunion|soiree|reservation|" +
    "treffen|termin|abendessen|mittagessen|fruhstuck|besprechung|party|reserviert)\\b"
)

/** Un evento a cui vai (con un biglietto): concerto, spettacolo, partita, festival. */
private val EVENT = Regex("\\b(concert\\w*|spettacol\\w*|teatro|festival|partita|bigliett\\w*|tickets?|e ticket|evento|event|apertura porte|doors open|ingresso|admission)\\b")

internal fun isEvent(text: String): Boolean = EVENT.containsMatchIn(Search.normalize(text))

/** Il messaggio parla di vedersi o di un impegno? */
internal fun isInvite(text: String): Boolean = INVITE.containsMatchIn(Search.normalize(text))
