package app.sordina.data

import app.sordina.tr

import app.sordina.R

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
        is Act.Assist -> assist(act.text)
        // Lo gestisce la schermata (serve l'impronta): qui non arriva
        is Act.AlarmOff, is Act.AlarmOn, is Act.Routine -> Outcome(null, false)
        is Act.Setting -> start(Intent(act.action)).let { Outcome(null, it) }
        Act.SordinaSettings, Act.SordinaCalendar -> Outcome(null)
        is Act.Web -> web(act.query)
        is Act.Calc -> { copy(act.result); Outcome(tr(R.string.copied_x, act.result)) }
        is Act.Quiet -> quiet(act.until)
    }

    /** Sordina a tempo: da adesso fino a [until]; null la toglie e fa tornare subito ciò che aspettava lei. */
    private fun quiet(until: Long?): Outcome {
        val silence = app.sordina.SordinaApp.of(context).silence
        if (until == null) {
            silence.stop()
            app.sordina.service.Scheduler.refresh(context)
            return Outcome(tr(R.string.quiet_done))
        }
        // Serve il permesso di accendere Non disturbare: lo si chiede la prima volta
        if (!silence.hasAccess()) {
            start(Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return Outcome(tr(R.string.quiet_need_access), ok = false)
        }
        silence.start(until)
        app.sordina.service.Scheduler.refresh(context)
        val msg = tr(R.string.quiet_until, java.time.Instant.ofEpochMilli(until).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)))
        return Outcome(msg, undo = { silence.stop(); app.sordina.service.Scheduler.refresh(context) })
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
            // Telegram mette il testo nella casella da solo (parametro "text" dei link per numero)
            Via.TELEGRAM -> start(Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?phone=$num" + if (m.text.isNotBlank()) "&text=$enc" else "")))
            Via.SIGNAL -> {
                // Signal non accetta il testo da fuori: con l'accessibilità lo scrive Sordina nella casella (senza inviare), se no si incolla
                if (m.text.isNotBlank() && !app.sordina.service.SordinaAccess.fill("org.thoughtcrime.securesms", m.text, send = false)) copy(m.text)
                start(Intent(Intent.ACTION_VIEW, Uri.parse("https://signal.me/#p/+$num")).setPackage("org.thoughtcrime.securesms"))
            }
            Via.SMS -> start(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(m.contact.number))).putExtra("sms_body", m.text))
        }
        val note = if (m.via == Via.SIGNAL && m.text.isNotBlank() && !app.sordina.service.SordinaAccess.active()) tr(R.string.text_copied_paste) else null
        return Outcome(note, ok)
    }

    private fun timer(t: Act.Timer): Outcome {
        if (t.seconds <= 0) return Outcome(tr(R.string.timer_say_how_long), false)
        val i = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, t.seconds)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        if (t.label.isNotBlank()) i.putExtra(AlarmClock.EXTRA_MESSAGE, t.label.replaceFirstChar { it.uppercase() })
        val ok = start(i)
        return Outcome(if (ok) tr(R.string.timer_started, Intents.fmtDuration(t.seconds)) else tr(R.string.no_clock_app), ok)
    }

    private fun alarm(a: Act.Alarm): Outcome {
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, a.hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, a.minute)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        if (a.label.isNotBlank()) i.putExtra(AlarmClock.EXTRA_MESSAGE, a.label)
        val ok = start(i)
        // Android dice alle altre app una sola "prossima sveglia" (e l'Orologio Samsung ci mette i suoi promemoria):
        // quella che imposti da qui Sordina se la segna, così in Casa la vedi sotto "Domani"
        if (ok) prefs.addAlarm(nextAt(a.hour, a.minute), confirm = false)
        return Outcome(if (ok) tr(R.string.alarm_at, java.time.LocalTime.of(a.hour, a.minute).format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))) else tr(R.string.no_clock_app), ok)
    }

    private suspend fun event(e: Act.Event): Outcome {
        // Il calendario di casa, se l'hai scelto: l'impegno va lì (Home Assistant non lo lascia cancellare da qui, niente Annulla)
        if (ha.calendar != null) {
            val ok = withContext(Dispatchers.IO) { ha.createEvent(e.title, e.whenAt) }
            return if (ok) Outcome("«${e.title}» ${DateParser.describe(e.whenAt)} · ${prefs.haCalendarName}", true)
            else Outcome(tr(R.string.calendar_write_failed), false)
        }
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
            ?: return Outcome(tr(R.string.calendar_write_failed), false)
        return Outcome("«${e.title}» ${DateParser.describe(e.whenAt)} · ${cal.name}", true) {
            withContext(Dispatchers.IO) { cals.delete(id) }
        }
    }

    private fun torch(): Outcome {
        val cm = context.getSystemService(CameraManager::class.java) ?: return Outcome(tr(R.string.torch_unavailable), false)
        val id = runCatching {
            cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        }.getOrNull() ?: return Outcome(tr(R.string.torch_unavailable), false)
        ensureTorchCallback(cm)
        val on = !torchOn
        val ok = runCatching { cm.setTorchMode(id, on) }.isSuccess
        return Outcome(if (!ok) tr(R.string.torch_busy) else if (on) tr(R.string.torch_on) else tr(R.string.torch_off), ok)
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

    private suspend fun assist(text: String): Outcome {
        // I nomi della frase (dispositivi, stanze) diventano parole di casa: la prossima domanda che li nomina va ad Assist
        prefs.homeWords = (prefs.homeWords + HomeTalk.learn(text)).let { if (it.size > 150) it.toList().takeLast(150).toSet() else it }
        // Senza token: la frase va negli appunti e Assist si apre nell'app di Home Assistant, dove basta incollarla o ridirla
        if (!ha.configured) {
            // Con l'accessibilità Sordina la scrive e la invia da sola; senza, resta da incollare
            val typed = app.sordina.service.SordinaAccess.fill(HomeAssistant.COMPANION_PKG, text, send = true)
            if (!typed) copy(text)
            // La tastiera della ricerca sta ancora scendendo: aperto subito, Assist se la prenderebbe. Un attimo e non si vede mai.
            else kotlinx.coroutines.delay(250)
            val ok = ha.openAssist()
            return Outcome(if (ok && !typed) tr(R.string.ha_text_copied) else null, ok)
        }
        return assistHere(text)
    }

    private suspend fun assistHere(text: String): Outcome = withContext(Dispatchers.IO) {
        val r = ha.assist(text)
        // I nomi dei dispositivi per riconoscere le prossime frasi (una volta al minuto al massimo, solo dopo che hai usato Assist)
        runCatching { ha.entities() }
        Outcome(r ?: tr(R.string.home_no_answer), r != null)
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

/** La prossima volta che l'orologio segna [hour]:[minute] (oggi se non è ancora passata, se no domani), in millisecondi. */
internal fun nextAt(hour: Int, minute: Int, now: java.time.ZonedDateTime = java.time.ZonedDateTime.now()): Long {
    var t = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
    if (!t.isAfter(now)) t = t.plusDays(1)
    return t.toInstant().toEpochMilli()
}
