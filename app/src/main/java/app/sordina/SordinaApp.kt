package app.sordina

import android.app.Application
import android.content.Context
import app.sordina.data.AppCatalog
import app.sordina.data.Calendars
import app.sordina.data.Habits
import app.sordina.data.HomeAssistant
import app.sordina.data.Intents
import app.sordina.data.Moments
import app.sordina.data.NotifStore
import app.sordina.data.People
import app.sordina.data.Prefs
import app.sordina.data.Signals
import app.sordina.service.Scheduler
import app.sordina.service.Silence
import app.sordina.service.StatusNotification

/** Oggetti condivisi tra la Home, il servizio delle notifiche e i promemoria di sistema. */
class SordinaApp : Application() {
    val prefs by lazy { Prefs(this) }
    val catalog by lazy { AppCatalog(this) }
    val store by lazy { NotifStore(this) }
    val today by lazy { app.sordina.data.Today(this) }
    val kinds by lazy { app.sordina.data.Kinds(this) }
    val people by lazy { People(this, prefs) }
    val moments by lazy { Moments(this, prefs) }
    val silence by lazy { Silence(this, prefs) }
    val status by lazy { StatusNotification(this) }
    val ha by lazy { HomeAssistant(this, prefs) }
    val habits by lazy { Habits(this) }
    val calendars by lazy { Calendars(this, prefs) }
    val diary by lazy { app.sordina.data.Diary(this, prefs, ha, app.sordina.data.PhoneCalendar(this, prefs)) }
    val brain by lazy { app.sordina.data.brain.Brain(this, prefs, people, store) }
    val intents by lazy { Intents(this, prefs, people, store, ha, brain) }
    val signals by lazy { Signals(this) }
    val cardSense by lazy { app.sordina.data.CardSense(this) }
    val plans by lazy { app.sordina.data.Plans(this) }
    val replies by lazy { app.sordina.data.Replies(java.io.File(filesDir, "replies.json")) }
    val weather by lazy { app.sordina.data.Weather(this) }

    override fun attachBaseContext(base: Context) { super.attachBaseContext(Lang.wrap(base)) }

    override fun onCreate() {
        super.onCreate()
        app.sordina.data.Faults.init(filesDir)
        // Un crash resta scritto (sul telefono): si vede in Impostazioni › Dati, poi Android fa il suo corso
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { app.sordina.data.Faults.record("crash:" + t.name, e) }
            previous?.uncaughtException(t, e)
        }
        Lang.init(this)
        app.sordina.data.Tally.init(this)
        app.sordina.data.Backup.onStart(this, prefs.onboarded)
        Lang.applyLegacy(this)
        // Il vecchio motore delle abitudini non c'è più: il suo file resta solo come spazio sprecato
        runCatching { java.io.File(filesDir, "routines.json").delete() }
        status.createChannels()
        Scheduler.refresh(this)
        app.sordina.service.CalendarReminders.plan(this)
    }

    companion object {
        fun of(context: Context) = context.applicationContext as SordinaApp
    }
}
