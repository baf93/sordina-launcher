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
import app.sordina.data.Routines
import app.sordina.data.Signals
import app.sordina.service.Scheduler
import app.sordina.service.Silence
import app.sordina.service.StatusNotification

/** Oggetti condivisi tra la Home, il servizio delle notifiche e i promemoria di sistema. */
class SordinaApp : Application() {
    val prefs by lazy { Prefs(this) }
    val catalog by lazy { AppCatalog(this) }
    val store by lazy { NotifStore(this) }
    val people by lazy { People(this, prefs) }
    val moments by lazy { Moments(this, prefs) }
    val silence by lazy { Silence(this, prefs) }
    val status by lazy { StatusNotification(this) }
    val ha by lazy { HomeAssistant(this, prefs) }
    val habits by lazy { Habits(this) }
    val calendars by lazy { Calendars(this, prefs) }
    val intents by lazy { Intents(this, prefs, people, store, ha) }
    val signals by lazy { Signals() }
    val routines by lazy { Routines(this) }
    val weather by lazy { app.sordina.data.Weather(this) }

    override fun attachBaseContext(base: Context) { super.attachBaseContext(Lang.wrap(base)) }

    override fun onCreate() {
        super.onCreate()
        Lang.init(this)
        Lang.applyLegacy(this)
        status.createChannels()
        Scheduler.refresh(this)
    }

    companion object {
        fun of(context: Context) = context.applicationContext as SordinaApp
    }
}
