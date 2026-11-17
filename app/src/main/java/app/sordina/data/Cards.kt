package app.sordina.data

import app.sordina.tr

import app.sordina.R

import android.media.session.MediaController
import android.media.session.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDateTime
import kotlinx.coroutines.flow.StateFlow

/** Cosa fa un pulsante di una scheda. */
sealed interface CardOp {
    data class Copy(val text: String) : CardOp
    /** Sistema Sordina (accesso alle notifiche, motore fermo, batteria) */
    data object Fix : CardOp
    data class OpenNotif(val key: String) : CardOp
    data class Dial(val number: String) : CardOp
    data class Navigate(val place: String) : CardOp
    data class OpenUrl(val url: String) : CardOp
    data class OpenEvent(val id: Long) : CardOp
    /** Riunione online senza link nella notifica: apre l'evento nell'app (Outlook), dove c'è il suo "Partecipa" */
    data class Join(val key: String) : CardOp
    data class AddEvent(val title: String, val whenAt: When) : CardOp
    data class OpenPackage(val pkg: String) : CardOp
    data class Query(val query: String) : CardOp
    data object MusicPrev : CardOp
    data object MusicToggle : CardOp
    data object MusicNext : CardOp
    /** Preme un pulsante della notifica (es. le domande di Home Assistant) */
    data class Fire(val key: String, val index: Int) : CardOp
    /** Chiude la notifica: fatto */
    data class Done(val key: String) : CardOp
    /** La notifica torna fra un'ora */
    data class Later(val key: String) : CardOp
    /** "Ok": toglie la scheda e chiude le notifiche da cui nasce */
    data object Dismiss : CardOp
    /** Pressione lunga: toglie solo la scheda, per oggi */
    data object Hide : CardOp
    data object Weather : CardOp
}

data class CardAction(val label: String, val op: CardOp, val icon: String? = null)

enum class CardKind(val priority: Int) {
    HEALTH(-1), OTP(0), ASK(1), CALL(2), RIDE(3), EVENT(4), PLAN(4), TIMER(5), REMINDER(6), PAYMENT(7), MUSIC(8),
    TICKET(9), PARCEL(10), RAIN(11), BIRTHDAY(12), HABIT(13)
}

/** Come si legge il titolo: testo, codice da digitare, importo, tratta "A → B". */
enum class CardLook { TITLE, CODE, AMOUNT, ROUTE }

/** Una barra sottile: quanto manca (OTP che scade), quanto è passato (evento in corso) o un avanzamento dato. */
data class Bar(val from: Long = 0, val to: Long = 0, val value: Float? = null, val drain: Boolean = false) {
    fun at(now: Long): Float {
        val f = value ?: if (to > from) ((now - from).toFloat() / (to - from)) else 0f
        return (if (drain) 1f - f else f).coerceIn(0f, 1f)
    }
}

/** Una scheda della pila in Casa: una cosa utile adesso, al massimo due pulsanti. */
data class Card(
    val key: String,
    val kind: CardKind,
    /** Riga piccola in alto: "Intesa · codice", "Tra 20 min" */
    val label: String,
    val title: String,
    val subtitle: String? = null,
    val actions: List<CardAction> = emptyList(),
    val time: Long = System.currentTimeMillis(),
    /** Quando smette di servire */
    val expiresAt: Long = Long.MAX_VALUE,
    val look: CardLook = CardLook.TITLE,
    val bar: Bar? = null,
    /** Pacco: a che punto è, da 1 (spedito) a 4 (consegnato) */
    val step: Int? = null,
    /** Un cerchio con l'iniziale del titolo (persone) */
    val avatar: Boolean = false,
    /** Le notifiche da cui nasce (un pagamento può arrivare da due app): "Ok" le chiude tutte */
    val sources: List<String> = emptyList(),
    /** Riunione o impegno: quando comincia (l'etichetta "Tra 10 min" si ricalcola ogni minuto) */
    val startsAt: Long? = null,
    /** L'immagine della notifica (la telecamera di casa, una foto): solo in memoria, si vede allargando il fumetto */
    val image: android.graphics.Bitmap? = null,
) {
    /** Una cosa da vedere una volta: aperta o usata, la scheda ha fatto il suo lavoro. */
    val oneShot: Boolean get() = kind in ONE_SHOT

    companion object {
        val ONE_SHOT = setOf(CardKind.OTP, CardKind.ASK, CardKind.CALL, CardKind.REMINDER, CardKind.PAYMENT, CardKind.TICKET, CardKind.PARCEL, CardKind.BIRTHDAY, CardKind.HABIT)
    }
}

/**
 * Le schede che nascono dalle notifiche (OTP, chiamate, timer, biglietti, pacchi) e dalla musica.
 * Solo in memoria: dopo un riavvio si ricostruiscono dalle notifiche ancora presenti.
 */
class Signals(context: android.content.Context) {
    // Le schede tolte con "Ok" restano tolte anche se Sordina si riavvia: un giorno, poi si dimenticano
    private val dismissedFile = java.io.File(context.filesDir, "dismissed_cards.txt")
    private val _cards = MutableStateFlow<Map<String, Card>>(emptyMap())
    val cards: StateFlow<Map<String, Card>> = _cards

    private val _music = MutableStateFlow<Card?>(null)
    val music: StateFlow<Card?> = _music

    @Volatile var controller: MediaController? = null
        private set
    private var pausedAt = 0L
    /** L'app che Sordina ha sentito suonare per ultima (da quando è accesa) */
    private var heardPlaying: String? = null

    @Synchronized
    fun put(c: Card) {
        if (hidden(c, dismissed.keys)) return
        val old = _cards.value[c.key]
        // Lo stesso pagamento visto da due app (banca e portafoglio): una scheda, con entrambi i nomi
        val merged = if (old != null && old.kind == CardKind.PAYMENT && c.kind == CardKind.PAYMENT) old.copy(
            label = if (c.label in old.label.split(" · ")) old.label else old.label + " · " + c.label,
            sources = (old.sources + c.sources).distinct(),
        ) else c
        _cards.value = _cards.value + (c.key to merged)
    }

    @Synchronized
    fun remove(key: String) { if (key in _cards.value) _cards.value = _cards.value - key }

    /**
     * La notifica [key] non c'è più (chiusa, letta in Sordina): via anche le schede nate da lei,
     * comprese quelle con una chiave loro (il pagamento visto da banca e portafoglio).
     */
    @Synchronized
    fun removeSource(key: String) {
        val gone = _cards.value.values.filter { it.key == key || key in it.sources }.map { it.key }
        if (gone.isNotEmpty()) _cards.value = _cards.value - gone.toSet()
    }

    /** Chiave → quando è stata tolta. */
    private val dismissed = LinkedHashMap<String, Long>()
    private val _dismissedKeys = MutableStateFlow<Set<String>>(emptySet())
    /** Le schede tolte a mano nell'ultimo giorno: valgono anche per quelle che non nascono dalle notifiche (eventi, pioggia, abitudini). */
    val dismissedKeys: StateFlow<Set<String>> = _dismissedKeys

    init {
        runCatching {
            val since = System.currentTimeMillis() - 86_400_000L
            if (dismissedFile.exists()) {
                val raw = dismissedFile.readText()
                if (raw.trimStart().startsWith("{")) {
                    val o = org.json.JSONObject(raw)
                    o.keys().forEach { k -> o.optLong(k).takeIf { it > since }?.let { dismissed[k] = it } }
                } else KeyList.legacy(raw).forEach { l ->
                    // Vecchio formato "chiave\tora", con le chiavi che contengono un a capo ricucite
                    val k = l.substringBeforeLast('\t'); val t = l.substringAfterLast('\t', "").toLongOrNull()
                    if (t != null && t > since) dismissed[k] = t
                }
            }
            _dismissedKeys.value = dismissed.keys.toSet()
        }
    }

    fun isDismissed(key: String) = key in dismissed

    companion object {
        /**
         * Cosa ricorda "nascondi": le schede nate da una notifica si nascondono per quello che dicono, non per la
         * notifica. Molte app riusano la stessa notifica per ogni codice (o chiamata, o pagamento): nascosto un codice,
         * il prossimo deve comparire. Le altre (eventi, pioggia, abitudini) per la loro chiave, come sempre.
         */
        fun hideKey(c: Card): String = if ('|' in c.key) "${c.key}#${c.title.hashCode()}" else c.key

        fun hidden(c: Card, keys: Set<String>): Boolean = c.key in keys || hideKey(c) in keys
    }

    /** Tolta a mano: non torna se la notifica viene ripubblicata, né dopo un riavvio (per un giorno). */
    @Synchronized
    fun dismiss(c: Card) {
        dismissed[hideKey(c)] = System.currentTimeMillis()
        while (dismissed.size > 200) dismissed.remove(dismissed.keys.first())
        remove(c.key)
        _dismissedKeys.value = dismissed.keys.toSet()
        val snap = org.json.JSONObject(dismissed as Map<*, *>).toString()
        runCatching { dismissedFile.writeText(snap) }
    }

    /** La sessione multimediale più rilevante (quella che suona, o l'ultima messa in pausa da poco). */
    fun setMusic(c: MediaController?, label: String?) {
        controller = c
        val meta = c?.metadata
        val state = c?.playbackState?.state
        val playing = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
        if (c == null || meta == null || (!playing && state != PlaybackState.STATE_PAUSED)) { _music.value = null; pausedAt = 0; return }
        val pkg = c.packageName
        if (playing) { pausedAt = 0; heardPlaying = pkg }
        else if (pausedAt == 0L) {
            // In pausa conta solo se l'hai appena fermata: Sordina l'ha sentita suonare e poi fermarsi.
            // Spotify e altri tengono viva la sessione in pausa (anche senza notifica) e ne aggiornano l'ora da soli:
            // trovata già in pausa, è vecchia e non si mostra (1 = già scaduta).
            pausedAt = if (heardPlaying == pkg) System.currentTimeMillis() else 1L
        }
        val title = meta.getString(android.media.MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = meta.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        if (title.isBlank()) { _music.value = null; return }
        _music.value = Card(
            key = "music", kind = CardKind.MUSIC, label = label ?: tr(R.string.music), title = title, subtitle = artist,
            actions = listOf(
                CardAction(tr(R.string.prev), CardOp.MusicPrev, "prev"),
                CardAction(if (playing) tr(R.string.pause) else tr(R.string.resume), CardOp.MusicToggle, if (playing) "pause" else "play"),
                CardAction(tr(R.string.next_track), CardOp.MusicNext, "next"),
            ),
            // In pausa resta 2 minuti, poi sparisce
            expiresAt = if (playing) Long.MAX_VALUE else pausedAt + 2 * 60_000,
        )
    }

    fun musicPlaying(): Boolean = controller?.playbackState?.state == PlaybackState.STATE_PLAYING
}

/** Riconosce dalle notifiche le cose che meritano una scheda. Regole semplici, sul testo. */
object CardDetector {
    val CLOCK_PKGS =setOf("com.sec.android.app.clockpackage", "com.google.android.deskclock", "com.android.deskclock")
    private val DIALER_PKGS = setOf("com.samsung.android.dialer", "com.google.android.dialer", "com.android.dialer", "com.android.server.telecom")
    val HA_PKGS = setOf("io.homeassistant.companion.android", "io.homeassistant.companion.android.minimal")
    /** Corse e consegne di cibo: la notifica in corso dice quanto manca. */
    private val RIDE_PKGS = setOf(
        "com.ubercab", "com.ubercab.eats", "com.glovo", "com.deliveroo.orderapp", "it.justeat.app", "com.justeat.app.it",
        "com.takeaway.android", "ee.mtakso.client", "taxi.android.client", "com.itaxi.app", "com.wolt.android",
    )
    private const val MAPS = "com.google.android.apps.maps"
    private val TRAVEL = Regex("(\\bvolo\\b|flight|boarding|carta d.imbarco|check-in|\\btreno\\b|frecciarossa|italo|trenitalia|biglietto|e-ticket|prenotazione confermata|booking confirmed)", RegexOption.IGNORE_CASE)
    private val PARCEL = Regex("(\\bpacco\\b|consegn|spedizion|corriere|out for delivery|in arrivo oggi|arriva oggi|delivered|ritiro)", RegexOption.IGNORE_CASE)
    private val ROUTE = Regex("([A-ZÀ-Ý][\\p{L}. ]{1,20}?)\\s*(?:→|->|–|—| - | > )\\s*([A-ZÀ-Ý][\\p{L}. ]{1,20})")
    private val PHONE = Regex("""\+?[\d\s()./-]{6,}""")
    private val MISSED = Regex("(chiamata persa|chiamate perse|missed call)", RegexOption.IGNORE_CASE)
    private val PAY_WORDS = Regex("(pagament|pagato|addebit|autorizzat|transazion|acquisto|\\bspes[ao]\\b|payment|paid|purchase|charged|spent|\\bvisa\\b|mastercard|maestro|\\bamex\\b|contactless)", RegexOption.IGNORE_CASE)
    private val AMOUNT = Regex("(?:€|EUR)\\s?(\\d{1,3}(?:\\.\\d{3})*,\\d{2}|\\d+[.,]\\d{2})(?!\\d)|(?<![\\d.,])(\\d{1,3}(?:\\.\\d{3})*,\\d{2}|\\d+[.,]\\d{2})\\s?(?:€|EUR)")
    private val NOT_PAID = Regex("(alla consegna|al ritiro|on delivery|da pagare|in scadenza|to pay)", RegexOption.IGNORE_CASE)
    private const val PAY_NEAR = 60
    private val PICKUP = Regex("(ritiro|consegna|pick-?up|delivery)", RegexOption.IGNORE_CASE)
    // A che punto è il pacco: dal più avanti al meno
    private val STEPS = listOf(
        4 to Regex("(consegnat|delivered|ritirat)", RegexOption.IGNORE_CASE),
        3 to Regex("(in consegna|out for delivery|arriva oggi|in arrivo oggi|arriving today)", RegexOption.IGNORE_CASE),
        3 to Regex("(pronto per il ritiro|disponibile per il ritiro|è arrivato|arrivato in negozio|ready for pickup)", RegexOption.IGNORE_CASE),
        2 to Regex("(in transito|in viaggio|in transit|on its way|in arrivo)", RegexOption.IGNORE_CASE),
        1 to Regex("(spedit|shipped|preso in carico|affidato)", RegexOption.IGNORE_CASE),
    )

    /** Quando ritirare o ricevere: si cerca l'ora solo vicino a "ritiro" o "consegna", non in tutta la mail. */
    fun pickupWhen(text: String, now: LocalDateTime = LocalDateTime.now()): When? {
        val m = PICKUP.find(text) ?: return null
        val window = text.substring(m.range.first, minOf(text.length, m.range.first + 260))
            // Codici postali, numeri d'ordine e civici confondono le ore: restano solo le parole e gli orari
            .replace(Regex("\\b\\d{5,}\\b"), " ").replace(Regex("[\\n\\r]+"), " ")
        return DateParser.parse(window, now, eveningBias = false).whenAt?.takeIf { !it.allDay }
    }

    /** "15:30 - 16:00" o "15.30–16.00": l'orario di una riunione. */
    private val SPAN = Regex("(?<![\\d:.])([01]?\\d|2[0-3])[:.]([0-5]\\d)\\s*[-–—]\\s*([01]?\\d|2[0-3])[:.]([0-5]\\d)(?![\\d:.])")
    /** Il link per entrare in una riunione online (Teams, Zoom, Meet, Webex) */
    // Solo i domini veri o i loro sottodomini ("acme.zoom.us"), mai i sosia ("evilzoom.us", "fakewebex.com")
    val MEETING_LINK = Regex("https://(meet\\.google\\.com|(?:[\\w-]+\\.)*zoom\\.us|teams\\.microsoft\\.com|teams\\.live\\.com|(?:[\\w-]+\\.)*webex\\.com)/\\S+")
    /** "Microsoft Teams Meeting", "Riunione di Microsoft Teams", "Zoom", "Google Meet": si entra online */
    internal val ONLINE_MEETING = Regex("\\b(teams|zoom|google meet|webex)\\b", RegexOption.IGNORE_CASE)

    /** Inizio e fine (millisecondi) della riunione scritta nel testo, nel giorno [day]. */
    fun meetingSpan(text: String, day: java.time.LocalDate): Pair<Long, Long>? {
        val m = SPAN.find(text) ?: return null
        val (h1, m1, h2, m2) = m.destructured
        val zone = java.time.ZoneId.systemDefault()
        val from = day.atTime(h1.toInt(), m1.toInt()).atZone(zone).toInstant().toEpochMilli()
        var to = day.atTime(h2.toInt(), m2.toInt()).atZone(zone).toInstant().toEpochMilli()
        if (to <= from) to += 86_400_000
        return from to to
    }

    /** La prima riga che dice qualcosa (le mail ripetono il titolo e riempiono di spazi). */
    fun firstLine(text: String): String = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: ""

    fun otpCode(text: String): String? {
        // Il punto o la virgola contano solo dentro un numero (12.50, 1.234): "è 482913." resta un codice
        val all = Regex("(?<!\\d|\\d[.,])\\d{4,8}(?!\\d|[.,]\\d)").findAll(text).map { it.value }.toList()
        return all.firstOrNull { it.length == 6 } ?: all.firstOrNull()
            ?: Regex("\\b(\\d{3})[- ](\\d{3})\\b").find(text)?.let { it.groupValues[1] + it.groupValues[2] }
    }

    /** Un pagamento: l'importo in centesimi, o null. */
    fun paymentCents(text: String): Long? {
        // La parola del pagamento e l'importo devono stare vicini: in una mail d'ordine "pagamento" e il prezzo
        // del primo prodotto sono lontani e non dicono che hai pagato. "Pagamento alla consegna" non è ancora pagato.
        val words = PAY_WORDS.findAll(text).filterNot { w -> NOT_PAID.containsMatchIn(text.substring(w.range.first, minOf(text.length, w.range.last + 40))) }.toList()
        if (words.isEmpty()) return null
        val m = AMOUNT.findAll(text).firstOrNull { a -> words.any { w -> a.range.first - w.range.last in 1..PAY_NEAR || w.range.first - a.range.last in 1..PAY_NEAR } } ?: return null
        val raw = m.groupValues[1].ifEmpty { m.groupValues[2] }
        val digits = raw.replace(".", "").replace(",", "")
        // "12.50" (punto decimale) e "12,50" danno lo stesso numero: le ultime due cifre sono sempre i centesimi
        return digits.toLongOrNull()
    }

    private fun money(cents: Long): String = runCatching {
        val f = java.text.NumberFormat.getCurrencyInstance(java.util.Locale.getDefault())
        f.currency = java.util.Currency.getInstance("EUR")
        f.format(cents / 100.0)
    }.getOrDefault("€ %d,%02d".format(cents / 100, cents % 100))

    /**
     * @param buttons i pulsanti della notifica (senza quelli di risposta), nell'ordine
     * @param progress l'avanzamento dichiarato dalla notifica, 0..1
     */
    fun detect(
        key: String, pkg: String, appLabel: String, category: String?, title: String, text: String, ongoing: Boolean, otp: Boolean,
        buttons: List<String> = emptyList(), progress: Float? = null,
        /** Quando è arrivata la notifica: le scadenze contano da lì, non da quando Sordina la rilegge (dopo un riavvio, al riepilogo) */
        postTime: Long = System.currentTimeMillis(),
    ): Card? {
        val now = postTime.takeIf { it in 1..System.currentTimeMillis() } ?: System.currentTimeMillis()
        val all = "$title $text"
        if (otp) {
            val code = otpCode(all) ?: return null
            val pretty = if (code.length == 6) code.substring(0, 3) + " " + code.substring(3) else code
            return Card(key, CardKind.OTP, tr(R.string.code_x, appLabel), pretty, actions = listOf(CardAction(tr(R.string.copy), CardOp.Copy(code))),
                expiresAt = now + 10 * 60_000, look = CardLook.CODE, bar = Bar(now, now + 10 * 60_000, drain = true))
        }
        // Casa ti chiede qualcosa: i suoi pulsanti diventano quelli della scheda
        if (pkg in HA_PKGS && buttons.isNotEmpty() && !ongoing) {
            return Card(key, CardKind.ASK, appLabel, title.ifBlank { text }, text.takeIf { title.isNotBlank() && it.isNotBlank() },
                actions = buttons.take(3).mapIndexed { i, b -> CardAction(b, CardOp.Fire(key, i)) }, expiresAt = now + 3 * 3600_000)
        }
        if (category == "missed_call" || (pkg in DIALER_PKGS && MISSED.containsMatchIn(all))) {
            val who = title.replace(MISSED, "").trim(' ', ':', '-').ifBlank { text }.ifBlank { tr(R.string.missed_call) }
            // Un numero si richiama col compositore (mai a voce sbagliata); un nome passa dalla ricerca,
            // che chiede conferma se non è sicura
            val number = who.takeIf { PHONE.matches(it) }?.filter { it.isDigit() || it == '+' }
            val call = if (number != null) CardOp.Dial(number) else CardOp.Query("chiama $who")
            return Card(key, CardKind.CALL, tr(R.string.missed_call), who,
                actions = listOf(CardAction(tr(R.string.call_back), call), CardAction(tr(R.string.write), CardOp.Query("scrivi a $who"))),
                expiresAt = now + 3 * 3600_000, avatar = true)
        }
        if (pkg in CLOCK_PKGS && (ongoing || category == "alarm")) {
            // I pulsanti dell'Orologio ("Ignora", "Posticipa", "Stop", "Pausa") diventano quelli del fumetto: la sveglia
            // che sta per suonare si spegne da qui. Senza pulsanti, il tocco apre l'Orologio
            return Card(key, CardKind.TIMER, appLabel, title.ifBlank { tr(R.string.timer) }, text.ifBlank { null },
                actions = buttons.take(3).mapIndexed { i, b -> CardAction(b, CardOp.Fire(key, i)) }.ifEmpty { listOf(CardAction(tr(R.string.open), CardOp.OpenNotif(key))) })
        }
        // Riunione o impegno avvisato dall'app (Outlook, Teams, calendari): "15:30 - 16:00, Microsoft Teams Meeting"
        if (category == "event" && !ongoing) meetingSpan(text, java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate())?.let { (from, to) ->
            val rest = text.replace(SPAN, "").lines().map { it.trim(' ', ',', '-', '·', '\t') }.filter { it.isNotEmpty() }
                .joinToString(" · ").ifBlank { null }
            // Riunione online: col link si entra da qui; senza (Outlook non lo mette) il tondo apre l'evento, dove c'è "Partecipa"
            val join = MEETING_LINK.find(text)?.value?.let { CardAction(tr(R.string.join), CardOp.OpenUrl(it)) }
                ?: if (ONLINE_MEETING.containsMatchIn(all)) CardAction(tr(R.string.join), CardOp.Join(key)) else null
            return Card(key, CardKind.EVENT, appLabel, title.ifBlank { text }, rest,
                actions = listOfNotNull(join, CardAction(tr(R.string.open), CardOp.OpenNotif(key)), CardAction(tr(R.string.ok), CardOp.Done(key))),
                time = from, expiresAt = to, startsAt = from, bar = Bar(from, to), sources = listOf(key))
        }
        if ((category == "reminder" || category == "event") && !ongoing) {
            // Il riassunto del gruppo (Samsung Calendario) non ha né titolo né testo: niente scheda vuota
            if (title.isBlank() && text.isBlank()) return null
            return Card(key, CardKind.REMINDER, appLabel, title.ifBlank { text }, firstLine(text).takeIf { title.isNotBlank() && it.isNotBlank() },
                actions = listOf(CardAction(tr(R.string.done), CardOp.Done(key)), CardAction(tr(R.string.later), CardOp.Later(key))),
                expiresAt = now + 12 * 3600_000)
        }
        // Corsa, cibo in arrivo, navigazione: quello che dice la notifica in corso, con la sua barra
        val ride = pkg in RIDE_PKGS && (ongoing || category in setOf("progress", "transport", "navigation", "status"))
        if (ride || (pkg == MAPS && (category == "navigation" || ongoing))) {
            if (title.isBlank() && text.isBlank()) return null
            return Card(key, CardKind.RIDE, appLabel, title.ifBlank { text }, text.takeIf { title.isNotBlank() && it.isNotBlank() },
                actions = listOf(CardAction(tr(R.string.open), CardOp.OpenNotif(key))), bar = progress?.let { Bar(value = it) })
        }
        if (TRAVEL.containsMatchIn(all)) {
            val w = DateParser.parse(all).whenAt
            val route = ROUTE.find(all)?.let { "${it.groupValues[1].trim()} → ${it.groupValues[2].trim()}" }
            val name = route ?: title.ifBlank { tr(R.string.trip) }
            // Il tocco sulla scheda apre il biglietto; i pulsanti: in calendario (se c'è l'ora) e Ok
            val actions = buildList {
                if (w != null) add(CardAction(tr(R.string.add_calendar), CardOp.AddEvent(name, w)))
                else add(CardAction(tr(R.string.open), CardOp.OpenNotif(key)))
                add(CardAction(tr(R.string.ok), CardOp.Dismiss))
            }
            // Serve fino a poco dopo la partenza; senza ora, due giorni
            val until = w?.let { it.start.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() + 3 * 3_600_000 }
                ?.takeIf { it > now } ?: (now + 2 * 86_400_000)
            return Card(key, CardKind.TICKET, appLabel, name, w?.let { DateParser.describe(it) } ?: firstLine(text).take(60),
                actions = actions, expiresAt = until, look = if (route != null) CardLook.ROUTE else CardLook.TITLE, sources = listOf(key))
        }
        paymentCents(all)?.let { cents ->
            // Sotto l'importo: dove (il titolo, se è un negozio e non l'app o l'importo), altrimenti la prima riga senza cifre
            val shop = title.takeIf { it.isNotBlank() && !it.equals(appLabel, true) && AMOUNT.find(it) == null && !PAY_WORDS.containsMatchIn(it) }
            val rest = shop ?: AMOUNT.replace(firstLine(text.ifBlank { title }), "").replace(Regex("\\s+"), " ").trim(' ', ',', '.', ':', '-')
            // Stessa chiave per lo stesso importo: banca e portafoglio fanno una scheda sola
            return Card("pay:$cents", CardKind.PAYMENT, appLabel, money(cents), rest.take(70).ifBlank { null },
                actions = listOf(CardAction(tr(R.string.open), CardOp.OpenNotif(key)), CardAction(tr(R.string.ok), CardOp.Dismiss)),
                expiresAt = now + 30 * 60_000, look = CardLook.AMOUNT, sources = listOf(key))
        }
        if (PARCEL.containsMatchIn(all) && !ongoing) {
            val step = STEPS.firstOrNull { it.second.containsMatchIn(all) }?.first
            // Un ritiro o una consegna con l'ora (la spesa da ritirare alle 16): la scheda dice quando e la si mette in calendario
            val w = pickupWhen(all, java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime())
            val name = title.ifBlank { tr(R.string.parcel) }
            val actions = buildList {
                if (w != null) add(CardAction(tr(R.string.add_calendar), CardOp.AddEvent(name, w)))
                else add(CardAction(tr(R.string.open), CardOp.OpenNotif(key)))
                add(CardAction(tr(R.string.ok), CardOp.Dismiss))
            }
            val until = w?.let { it.start.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() + 3_600_000 }?.takeIf { it > now }
            // "Luogo e ora di ritiro": la vai a prendere tu
            val pickup = PICKUP.find(all)?.value?.lowercase()?.let { it.startsWith("ritiro") || it.startsWith("pick") } == true
            // Consegnato o ritirato: basta vederlo un'ora. Da ritirare: finché non dici Ok, al massimo tre giorni.
            val life = when {
                until != null -> until
                step == 4 -> now + 3_600_000
                pickup -> now + 3 * 86_400_000
                else -> now + 12 * 3_600_000
            }
            return Card(key, CardKind.PARCEL, tr(if (pickup) R.string.pickup_x else R.string.delivery_x, appLabel), name,
                w?.let { DateParser.describe(it) } ?: firstLine(text).take(70).ifBlank { null },
                actions = actions, expiresAt = life, step = if (w != null) null else step, sources = listOf(key))
        }
        return null
    }
}
