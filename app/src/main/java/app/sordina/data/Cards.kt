package app.sordina.data

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
}

data class CardAction(val label: String, val op: CardOp, val icon: String? = null)

enum class CardKind(val priority: Int) {
    OTP(0), CALL(1), EVENT(2), TIMER(3), MUSIC(4), TICKET(5), PARCEL(6), HABIT(7)
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
    fun put(c: Card) { _cards.value = _cards.value + (c.key to c) }

    @Synchronized
    fun remove(key: String) { if (key in _cards.value) _cards.value = _cards.value - key }

    @Synchronized
    fun dismiss(key: String) = remove(key)

    /** La sessione multimediale più rilevante (quella che suona, o l'ultima messa in pausa da poco). */
    fun setMusic(c: MediaController?, label: String?) {
        controller = c
        val meta = c?.metadata
        val state = c?.playbackState?.state
        val playing = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
        if (c == null || meta == null) { _music.value = null; return }
        if (playing) pausedAt = 0 else if (pausedAt == 0L) pausedAt = System.currentTimeMillis()
        val title = meta.getString(android.media.MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = meta.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        if (title.isBlank()) { _music.value = null; return }
        _music.value = Card(
            key = "music", kind = CardKind.MUSIC, label = label ?: "Musica", title = title, subtitle = artist,
            actions = listOf(
                CardAction("Indietro", CardOp.MusicPrev, "prev"),
                CardAction(if (playing) "Pausa" else "Riprendi", CardOp.MusicToggle, if (playing) "pause" else "play"),
                CardAction("Avanti", CardOp.MusicNext, "next"),
            ),
            // In pausa resta 15 minuti, poi sparisce
            expiresAt = if (playing) Long.MAX_VALUE else pausedAt + 15 * 60_000,
        )
    }

    fun musicPlaying(): Boolean = controller?.playbackState?.state == PlaybackState.STATE_PLAYING
}

/** Riconosce dalle notifiche le cose che meritano una scheda. Regole semplici, sul testo. */
object CardDetector {
    private val CLOCK_PKGS = setOf("com.sec.android.app.clockpackage", "com.google.android.deskclock", "com.android.deskclock")
    private val DIALER_PKGS = setOf("com.samsung.android.dialer", "com.google.android.dialer", "com.android.dialer", "com.android.server.telecom")
    private val TRAVEL = Regex("(\\bvolo\\b|flight|boarding|carta d.imbarco|check-in|\\btreno\\b|frecciarossa|italo|trenitalia|biglietto|e-ticket|prenotazione confermata|booking confirmed)", RegexOption.IGNORE_CASE)
    private val PARCEL = Regex("(\\bpacco\\b|consegn|spedizion|corriere|out for delivery|in arrivo oggi|arriva oggi|delivered|ritiro)", RegexOption.IGNORE_CASE)
    private val ROUTE = Regex("([A-ZÀ-Ý][\\p{L}. ]{1,20}?)\\s*(?:→|->|–|—| - | > )\\s*([A-ZÀ-Ý][\\p{L}. ]{1,20})")
    private val MISSED = Regex("(chiamata persa|chiamate perse|missed call)", RegexOption.IGNORE_CASE)

    fun otpCode(text: String): String? {
        val all = Regex("(?<![\\d.,])\\d{4,8}(?![\\d.,])").findAll(text).map { it.value }.toList()
        return all.firstOrNull { it.length == 6 } ?: all.firstOrNull()
            ?: Regex("\\b(\\d{3})[- ](\\d{3})\\b").find(text)?.let { it.groupValues[1] + it.groupValues[2] }
    }

    fun detect(key: String, pkg: String, appLabel: String, category: String?, title: String, text: String, ongoing: Boolean, otp: Boolean): Card? {
        val now = System.currentTimeMillis()
        val all = "$title $text"
        if (otp) {
            val code = otpCode(all) ?: return null
            val pretty = if (code.length == 6) code.substring(0, 3) + " " + code.substring(3) else code
            return Card(key, CardKind.OTP, "$appLabel · codice", pretty, actions = listOf(CardAction("Copia", CardOp.Copy(code))),
                expiresAt = now + 10 * 60_000)
        }
        if (category == "missed_call" || (pkg in DIALER_PKGS && MISSED.containsMatchIn(all))) {
            val who = title.replace(MISSED, "").trim(' ', ':', '-').ifBlank { text }
            return Card(key, CardKind.CALL, "Chiamata persa", who.ifBlank { "Chiamata persa" },
                actions = listOf(CardAction("Richiama", CardOp.Query("chiama $who")), CardAction("Apri", CardOp.OpenNotif(key))),
                expiresAt = now + 3 * 3600_000)
        }
        if (pkg in CLOCK_PKGS && (ongoing || category == "alarm")) {
            return Card(key, CardKind.TIMER, appLabel, title.ifBlank { "Timer" }, text.ifBlank { null },
                actions = listOf(CardAction("Apri", CardOp.OpenNotif(key))))
        }
        if (TRAVEL.containsMatchIn(all)) {
            val w = DateParser.parse(all).whenAt
            val route = ROUTE.find(all)?.let { "${it.groupValues[1].trim()} → ${it.groupValues[2].trim()}" }
            val name = route ?: title.ifBlank { "Viaggio" }
            val actions = buildList {
                if (w != null) add(CardAction("Aggiungi al calendario", CardOp.AddEvent(name, w)))
                add(CardAction("Apri", CardOp.OpenNotif(key)))
            }
            return Card(key, CardKind.TICKET, appLabel, name, w?.let { DateParser.describe(it) } ?: text.take(60),
                actions = actions, expiresAt = now + 2 * 86_400_000)
        }
        if (PARCEL.containsMatchIn(all) && !ongoing) {
            return Card(key, CardKind.PARCEL, "Consegna · $appLabel", title.ifBlank { "Pacco" }, text.take(70).ifBlank { null },
                actions = listOf(CardAction("Apri", CardOp.OpenNotif(key))), expiresAt = now + 12 * 3600_000)
        }
        return null
    }
}
