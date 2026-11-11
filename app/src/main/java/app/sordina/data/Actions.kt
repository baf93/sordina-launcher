package app.sordina.data

import android.Manifest
import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.provider.AlarmClock
import android.provider.MediaStore
import android.view.KeyEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Esito di un'azione: un messaggio breve e, per le azioni verso te stesso, Annulla. */
data class Outcome(val message: String?, val ok: Boolean = true, val undo: (suspend () -> Unit)? = null)

/** Esegue le intenzioni. Nessun servizio di accessibilità: ciò che le app non permettono, non si fa. */
class Actions(
    private val context: Context,
    private val catalog: AppCatalog,
    private val people: People,
    private val ha: HomeAssistant,
    private val prefs: Prefs,
) {
    init {
        // Lo stato della torcia arriva in modo asincrono: meglio saperlo prima del primo tocco
        context.getSystemService(CameraManager::class.java)?.let { runCatching { ensureTorchCallback(it) } }
    }

    suspend fun run(act: Act): Outcome = when (act) {
        is Act.OpenApp -> { catalog.launch(act.app); Outcome(null) }
        is Act.Call -> call(act.contact)
        is Act.Message -> message(act)
        is Act.Timer -> timer(act)
        is Act.Alarm -> alarm(act)
        is Act.Event -> event(act)
        Act.Torch -> torch()
        is Act.Media -> media(act.keyCode)
        is Act.Navigate -> navigate(act.place)
        is Act.Music -> music(act.query)
        is Act.Ha -> home(act)
        is Act.Assist -> assist(act.text)
        is Act.Setting -> start(Intent(act.action)).let { Outcome(null, it) }
        Act.SordinaSettings -> Outcome(null)
        is Act.Web -> web(act.query)
        is Act.Calc -> { copy(act.result); Outcome("Copiato: ${act.result}") }
    }

    private fun start(i: Intent): Boolean =
        runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess

    private fun call(c: Contact): Outcome {
        val granted = context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val uri = Uri.fromParts("tel", c.number, null)
        val ok = start(Intent(if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri))
        return Outcome(null, ok)
    }

    private fun message(m: Act.Message): Outcome {
        val num = people.international(m.contact.number)
        val enc = Uri.encode(m.text)
        // Ricorda la scelta per questa persona
        if (m.via != Via.SMS) {
            val pkg = m.via.pkgs.firstOrNull { installed(it) }
            if (pkg != null) prefs.personApp = prefs.personApp + (m.contact.normalized to pkg)
        }
        val ok = when (m.via) {
            Via.WHATSAPP -> {
                val pkg = m.via.pkgs.firstOrNull { installed(it) }
                start(Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$num&text=$enc")).apply { pkg?.let { setPackage(it) } })
            }
            Via.TELEGRAM -> {
                if (m.text.isNotBlank()) copy(m.text)
                start(Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?phone=$num")))
            }
            Via.SIGNAL -> {
                if (m.text.isNotBlank()) copy(m.text)
                start(Intent(Intent.ACTION_VIEW, Uri.parse("https://signal.me/#p/+$num")).setPackage("org.thoughtcrime.securesms"))
            }
            Via.SMS -> start(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(m.contact.number))).putExtra("sms_body", m.text))
        }
        val note = if ((m.via == Via.TELEGRAM || m.via == Via.SIGNAL) && m.text.isNotBlank()) "Testo copiato: incollalo nella chat" else null
        return Outcome(note, ok)
    }

    private fun timer(t: Act.Timer): Outcome {
        if (t.seconds <= 0) return Outcome("Di' anche quanto: «timer 10 minuti»", false)
        val i = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, t.seconds)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        if (t.label.isNotBlank()) i.putExtra(AlarmClock.EXTRA_MESSAGE, t.label.replaceFirstChar { it.uppercase() })
        val ok = start(i)
        return Outcome(if (ok) "Timer di ${Intents.fmtDuration(t.seconds)} avviato" else "Nessuna app orologio risponde", ok)
    }

    private fun alarm(a: Act.Alarm): Outcome {
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, a.hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, a.minute)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        if (a.label.isNotBlank()) i.putExtra(AlarmClock.EXTRA_MESSAGE, a.label)
        val ok = start(i)
        return Outcome(if (ok) "Sveglia alle %d:%02d".format(a.hour, a.minute) else "Nessuna app orologio risponde", ok)
    }

    private suspend fun event(e: Act.Event): Outcome {
        val cals = Calendars(context, prefs)
        val cal = withContext(Dispatchers.IO) { cals.default() }
        if (cal == null) {
            // Senza permesso o senza calendari: si apre il calendario con i dati già scritti
            val start = Moments.millis(e.whenAt.start)
            val ok = start(Intent(Intent.ACTION_INSERT).setData(android.provider.CalendarContract.Events.CONTENT_URI)
                .putExtra(android.provider.CalendarContract.Events.TITLE, e.title)
                .putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                .putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, start + 3_600_000)
                .putExtra(android.provider.CalendarContract.EXTRA_EVENT_ALL_DAY, e.whenAt.allDay))
            return Outcome(null, ok)
        }
        val id = withContext(Dispatchers.IO) { cals.insert(cal, e.title, e.whenAt) }
            ?: return Outcome("Non sono riuscito a scrivere nel calendario", false)
        return Outcome("«${e.title}» ${DateParser.describe(e.whenAt)} · ${cal.name}", true) {
            withContext(Dispatchers.IO) { cals.delete(id) }
        }
    }

    private fun torch(): Outcome {
        val cm = context.getSystemService(CameraManager::class.java) ?: return Outcome("Torcia non disponibile", false)
        val id = runCatching {
            cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        }.getOrNull() ?: return Outcome("Torcia non disponibile", false)
        ensureTorchCallback(cm)
        val on = !torchOn
        val ok = runCatching { cm.setTorchMode(id, on) }.isSuccess
        return Outcome(if (!ok) "Torcia occupata" else if (on) "Torcia accesa" else "Torcia spenta", ok)
    }

    private fun media(key: Int): Outcome {
        val am = context.getSystemService(AudioManager::class.java)
        val t = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, key, 0))
        am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, key, 0))
        return Outcome(null)
    }

    private fun navigate(place: String): Outcome {
        val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(place)))
        val ok = (installed("com.google.android.apps.maps") && start(nav)) ||
            start(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(place))))
        return Outcome(null, ok)
    }

    private fun music(q: String): Outcome {
        val i = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .putExtra(SearchManager.QUERY, q)
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
        if (installed("com.spotify.music")) i.setPackage("com.spotify.music")
        val ok = start(i) || start(Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:" + Uri.encode(q))))
        return Outcome(null, ok)
    }

    private suspend fun assist(text: String): Outcome = withContext(Dispatchers.IO) {
        val r = ha.assist(text)
        Outcome(r ?: "Casa non risponde", r != null)
    }

    private suspend fun home(a: Act.Ha): Outcome = withContext(Dispatchers.IO) {
        val e = a.entity
        val ok = when (a.op) {
            HaOp.ON -> ha.turn(e, true)
            HaOp.OFF -> ha.turn(e, false)
            HaOp.TOGGLE -> ha.toggle(e)
            HaOp.LEVEL -> a.value?.let { ha.setLevel(e, it) } ?: ha.turn(e, true)
        }
        ha.entities(force = true)
        Outcome(if (ok) "Fatto: ${e.name}" else "Home Assistant non risponde", ok)
    }

    private fun web(q: String): Outcome {
        val ok = start(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q)) ||
            start(Intent(Intent.ACTION_VIEW, Uri.parse("https://duckduckgo.com/?q=" + Uri.encode(q))))
        return Outcome(null, ok)
    }

    private fun copy(text: String) {
        val cm = context.getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("Sordina", text))
    }

    private fun installed(pkg: String) = runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess

    companion object {
        @Volatile private var torchOn = false
        private var torchCb: CameraManager.TorchCallback? = null

        private fun ensureTorchCallback(cm: CameraManager) {
            if (torchCb != null) return
            val cb = object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) { torchOn = enabled }
            }
            torchCb = cb
            cm.registerTorchCallback(cb, android.os.Handler(android.os.Looper.getMainLooper()))
        }
    }
}
