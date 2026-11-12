package app.sordina.data

import app.sordina.tr

import app.sordina.R

import android.media.session.MediaController
import android.media.session.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Cosa fa un pulsante di una scheda. */
sealed interface CardOp {
    data class Copy(val text: String) : CardOp
    data class OpenNotif(val key: String) : CardOp
    data class Dial(val number: String) : CardOp
    data class Navigate(val place: String) : CardOp
    data class OpenUrl(val url: String) : CardOp
    data class OpenEvent(val id: Long) : CardOp
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
    /** Toglie solo la scheda */
    data object Dismiss : CardOp
    data object Weather : CardOp
}

data class CardAction(val label: String, val op: CardOp, val icon: String? = null)

enum class CardKind(val priority: Int) {
    OTP(0), ASK(1), CALL(2), RIDE(3), EVENT(4), TIMER(5), REMINDER(6), PAYMENT(7), MUSIC(8),
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
)

/**
 * Le schede che nascono dalle notifiche (OTP, chiamate, timer, biglietti, pacchi) e dalla musica.
 * Solo in memoria: dopo un riavvio si ricostruiscono dalle notifiche ancora presenti.
 */
class Signals {
    private val _cards = MutableStateFlow<Map<String, Card>>(emptyMap())
    val cards: StateFlow<Map<String, Card>> = _cards

    private val _music = MutableStateFlow<Card?>(null)
    val music: StateFlow<Card?> = _music

    @Volatile var controller: MediaController? = null
        private set
    private var pausedAt = 0L

    @Synchronized
    fun put(c: Card) {
        if (c.key in dismissed) return
        val old = _cards.value[c.key]
        // Lo stesso pagamento visto da due app (banca e portafoglio): una scheda, con entrambi i nomi
        val merged = if (old != null && old.kind == CardKind.PAYMENT && c.kind == CardKind.PAYMENT && c.label !in old.label.split(" · "))
            old.copy(label = old.label + " · " + c.label) else if (old != null && old.kind == CardKind.PAYMENT) old else c
        _cards.value = _cards.value + (c.key to merged)
    }

    @Synchronized
    fun remove(key: String) { if (key in _cards.value) _cards.value = _cards.value - key }

    private val dismissed = LinkedHashSet<String>()

    /** Tolta a mano: non torna se la notifica viene ripubblicata. */
    @Synchronized
    fun dismiss(key: String) {
        dismissed += key
        while (dismissed.size > 200) dismissed.remove(dismissed.first())
        remove(key)
    }

    /** La sessione multimediale più rilevante (quella che suona, o l'ultima messa in pausa da poco). */
    fun setMusic(c: MediaController?, label: String?) {
        controller = c
        val meta = c?.metadata
        val state = c?.playbackState?.state
        val playing = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
        if (c == null || meta == null || (!playing && state != PlaybackState.STATE_PAUSED)) { _music.value = null; pausedAt = 0; return }
        if (playing) pausedAt = 0 else if (pausedAt == 0L) {
            // Quando è stata messa in pausa davvero: lo dice il lettore. Così, se Sordina si riavvia,
            // una pausa vecchia non ricompare per altri 2 minuti. Se non si sa, la si considera vecchia.
            val since = c.playbackState?.lastPositionUpdateTime ?: 0L
            val er = android.os.SystemClock.elapsedRealtime()
            pausedAt = if (since in 1..er) System.currentTimeMillis() - (er - since) else 1L // 1: pausa vecchia, la scheda è già scaduta
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
    private val CLOCK_PKGS = setOf("com.sec.android.app.clockpackage", "com.google.android.deskclock", "com.android.deskclock")
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
    private val MISSED = Regex("(chiamata persa|chiamate perse|missed call)", RegexOption.IGNORE_CASE)
    private val PAY_WORDS = Regex("(pagament|pagato|addebit|autorizzat|transazion|acquisto|payment|paid|purchase|charged)", RegexOption.IGNORE_CASE)
    private val AMOUNT = Regex("(?:€|EUR)\\s?(\\d{1,3}(?:\\.\\d{3})*,\\d{2}|\\d+[.,]\\d{2})(?!\\d)|(?<![\\d.,])(\\d{1,3}(?:\\.\\d{3})*,\\d{2}|\\d+[.,]\\d{2})\\s?(?:€|EUR)")
    // A che punto è il pacco: dal più avanti al meno
    private val STEPS = listOf(
        4 to Regex("(consegnat|delivered|ritirat)", RegexOption.IGNORE_CASE),
        3 to Regex("(in consegna|out for delivery|arriva oggi|in arrivo oggi|arriving today)", RegexOption.IGNORE_CASE),
        2 to Regex("(in transito|in viaggio|in transit|on its way|in arrivo)", RegexOption.IGNORE_CASE),
        1 to Regex("(spedit|shipped|preso in carico|affidato)", RegexOption.IGNORE_CASE),
    )

    fun otpCode(text: String): String? {
        val all = Regex("(?<![\\d.,])\\d{4,8}(?![\\d.,])").findAll(text).map { it.value }.toList()
        return all.firstOrNull { it.length == 6 } ?: all.firstOrNull()
            ?: Regex("\\b(\\d{3})[- ](\\d{3})\\b").find(text)?.let { it.groupValues[1] + it.groupValues[2] }
    }

    /** Un pagamento: l'importo in centesimi, o null. */
    fun paymentCents(text: String): Long? {
        if (!PAY_WORDS.containsMatchIn(text)) return null
        val m = AMOUNT.find(text) ?: return null
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
    ): Card? {
        val now = System.currentTimeMillis()
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
            return Card(key, CardKind.CALL, tr(R.string.missed_call), who,
                actions = listOf(CardAction(tr(R.string.call_back), CardOp.Query("chiama $who")), CardAction(tr(R.string.write), CardOp.Query("scrivi a $who"))),
                expiresAt = now + 3 * 3600_000, avatar = true)
        }
        if (pkg in CLOCK_PKGS && (ongoing || category == "alarm")) {
            return Card(key, CardKind.TIMER, appLabel, title.ifBlank { tr(R.string.timer) }, text.ifBlank { null },
                actions = listOf(CardAction(tr(R.string.open), CardOp.OpenNotif(key))))
        }
        if (category == "reminder" && !ongoing) {
            return Card(key, CardKind.REMINDER, appLabel, title.ifBlank { text }, text.takeIf { title.isNotBlank() && it.isNotBlank() },
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
            val actions = buildList {
                if (w != null) add(CardAction(tr(R.string.add_calendar), CardOp.AddEvent(name, w)))
                add(CardAction(tr(R.string.open), CardOp.OpenNotif(key)))
            }
            return Card(key, CardKind.TICKET, appLabel, name, w?.let { DateParser.describe(it) } ?: text.take(60),
                actions = actions, expiresAt = now + 2 * 86_400_000, look = if (route != null) CardLook.ROUTE else CardLook.TITLE)
        }
        paymentCents(all)?.let { cents ->
            val rest = AMOUNT.replace(text.ifBlank { title }, "").replace(Regex("\\s+"), " ").trim(' ', ',', '.', ':', '-')
            // Stessa chiave per lo stesso importo: banca e portafoglio fanno una scheda sola
            return Card("pay:$cents", CardKind.PAYMENT, appLabel, money(cents), rest.take(70).ifBlank { null },
                actions = listOf(CardAction(tr(R.string.open), CardOp.OpenNotif(key)), CardAction(tr(R.string.ok), CardOp.Dismiss)),
                expiresAt = now + 30 * 60_000, look = CardLook.AMOUNT)
        }
        if (PARCEL.containsMatchIn(all) && !ongoing) {
            val step = STEPS.firstOrNull { it.second.containsMatchIn(all) }?.first
            return Card(key, CardKind.PARCEL, tr(R.string.delivery_x, appLabel), title.ifBlank { tr(R.string.parcel) }, text.take(70).ifBlank { null },
                actions = listOf(CardAction(tr(R.string.open), CardOp.OpenNotif(key))), expiresAt = now + 12 * 3600_000, step = step)
        }
        return null
    }
}
