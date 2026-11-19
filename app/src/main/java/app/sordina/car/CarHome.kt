package app.sordina.car

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Tab
import androidx.car.app.model.TabContents
import androidx.car.app.model.TabTemplate
import androidx.car.app.model.Template
import androidx.car.app.model.Toggle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import app.sordina.R
import app.sordina.SordinaApp
import app.sordina.data.Act
import app.sordina.data.Contact
import app.sordina.data.Held
import app.sordina.data.HeldLine
import app.sordina.data.HomeRun
import app.sordina.data.Prefs
import app.sordina.data.Upcoming
import app.sordina.data.today
import app.sordina.service.AlarmCode
import app.sordina.service.Classifier
import app.sordina.service.Scheduler
import app.sordina.service.SordinaListener
import app.sordina.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Sordina sullo schermo dell'auto. In alto la sordina (l'icona dell'app) e le schede che hai acceso, nel tuo ordine:
 * Casa (le routine "Anche in auto"), In sordina (cosa passa e chi aspetta), Agenda (oggi) e, per ultima, Parla.
 * Con una scheda sola (o un'auto senza schede, prima della Car API 6) resta una schermata sola, col microfono in alto.
 */
class CarHome(cc: CarContext, private val motion: CarMotion) : Screen(cc) {
    val app: SordinaApp = SordinaApp.of(cc)
    private val prefs get() = app.prefs
    private val main = Handler(Looper.getMainLooper())

    /** La scheda aperta, e quella da cui sei arrivato a Parla (ci si torna dopo) */
    private var active: String? = null
    private var back: String? = null
    /** Parla: sta ascoltando, e cosa ha sentito finora */
    private var listening = false
    private var heard: String? = null
    /** Parla fuori dalle schede: la sua schermata */
    private var talkScreen: SayScreen? = null
    /** Dopo "rispondi a Paolo": la prossima frase è la risposta */
    private var replyTo: List<Held>? = null
    private var events: List<Upcoming> = emptyList()
    private var redraw = false

    init {
        // Arriva o se ne va qualcosa in sordina: la scheda si aggiorna (al massimo ogni paio di secondi)
        lifecycleScope.launch { app.store.held.drop(1).collect { if (shown(Prefs.CAR_TAB_SORDINA)) invalidateSoon() } }
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { loadEvents() }
            override fun onStop(owner: LifecycleOwner) { if (listening) endTalk(null) }
        })
    }

    // Le schede

    private fun tabs(): List<String> = prefs.carTabs.filter { it !in prefs.carTabsOff }.ifEmpty { listOf(Prefs.CAR_TAB_HOME) }
    private fun ids(): List<String> = tabs() + if (prefs.carVoice) listOf(TALK) else emptyList()
    private fun withTabs(): Boolean = carContext.carAppApiLevel >= 6 && ids().size >= 2
    private fun shown(id: String) = (if (withTabs()) active ?: ids().first() else tabs().first()) == id

    override fun onGetTemplate(): Template {
        redraw = false
        val ids = ids()
        if (!withTabs()) return single(tabs().first())
        val cur = active?.takeIf { it in ids } ?: ids.first()
        active = cur
        return TabTemplate.Builder(object : TabTemplate.TabCallback {
            override fun onTabSelected(tabContentId: String) { select(tabContentId) }
        })
            .setHeaderAction(Action.APP_ICON)
            .apply { ids.forEach { addTab(tab(it)) } }
            .setTabContents(TabContents.Builder(content(cur)).build())
            .setActiveTabContentId(cur)
            .build()
    }

    private fun tab(id: String): Tab = Tab.Builder().setContentId(id).setTitle(title(id)).setIcon(carContext.icon(when (id) {
        Prefs.CAR_TAB_HOME -> R.drawable.car_home
        Prefs.CAR_TAB_SORDINA -> R.drawable.car_bell
        Prefs.CAR_TAB_AGENDA -> R.drawable.car_calendar
        else -> R.drawable.car_mic
    })).build()

    private fun title(id: String): String = when (id) {
        Prefs.CAR_TAB_HOME -> tr(R.string.auto_tab_home)
        Prefs.CAR_TAB_SORDINA -> CarTalk.groups(app).sumOf { g -> g.sumOf { it.count } }.let { n -> if (n > 0) tr(R.string.auto_tab_sordina_n, n) else tr(R.string.auto_tab_sordina) }
        Prefs.CAR_TAB_AGENDA -> tr(R.string.auto_tab_agenda)
        else -> tr(R.string.auto_tab_talk)
    }

    private fun select(id: String) {
        if (id == active) return
        if (id == TALK) { back = active; active = TALK; invalidate(); talk(); return }
        if (listening) endTalk(null, stay = true)
        active = id
        if (id == Prefs.CAR_TAB_AGENDA) loadEvents()
        invalidate()
    }

    /** Una scheda sola: la sordina e il nome in alto, il microfono a destra se Parla è acceso */
    private fun single(id: String): Template {
        val strip = if (prefs.carVoice) ActionStrip.Builder().addAction(Action.Builder().setIcon(carContext.icon(R.drawable.car_mic))
            .setOnClickListener { talkAlone() }.build()).build() else null
        return when (val t = content(id)) {
            is GridTemplate -> GridTemplate.Builder().setTitle(tr(R.string.app_name)).setHeaderAction(Action.APP_ICON)
                .setSingleList(t.singleList!!).apply { strip?.let { setActionStrip(it) } }.build()
            is ListTemplate -> ListTemplate.Builder().setTitle(tr(R.string.app_name)).setHeaderAction(Action.APP_ICON)
                .setSingleList(t.singleList!!).apply { strip?.let { setActionStrip(it) } }.build()
            else -> t
        }
    }

    private fun content(id: String): Template = when (id) {
        Prefs.CAR_TAB_HOME -> homeGrid()
        Prefs.CAR_TAB_SORDINA -> sordinaList()
        Prefs.CAR_TAB_AGENDA -> agendaList()
        else -> talkMessage()
    }

    private fun limit(type: Int, fallback: Int) =
        runCatching { carContext.getCarService(ConstraintManager::class.java).getContentLimit(type) }.getOrDefault(fallback).coerceAtLeast(1)

    // Casa: le routine "Anche in auto", nell'ordine in cui le hai abilitate

    private fun carRoutines() = prefs.haRoutines.filter { (_, r) -> r.on && r.car }

    private fun homeGrid(): Template {
        val list = ItemList.Builder()
        val routines = carRoutines().entries.take(limit(ConstraintManager.CONTENT_LIMIT_TYPE_GRID, 6))
        routines.forEach { (id, r) ->
            val alarm = r.alarm?.let { prefs.haAlarms[it]?.name }
            // Un riquadro vero, con il bordo e il disegno della routine; l'allarme è il bollino, non una riga di testo
            list.addItem(GridItem.Builder().setTitle(r.name)
                .setImage(CarTiles.tile(carContext, CarTiles.glyph(r.name, alarm != null, r.arm), alarm != null), GridItem.IMAGE_TYPE_LARGE)
                .setOnClickListener { routine(id) }
                .build())
        }
        if (routines.isEmpty()) list.setNoItemsMessage(tr(if (app.ha.configured) R.string.auto_no_routines else R.string.auto_no_ha))
        return GridTemplate.Builder().setSingleList(list.build()).build()
    }

    // In sordina: cosa passa, poi chi aspetta

    private fun passingAll(): Long? = prefs.passUntil[Prefs.PASS_ALL]?.takeIf { it > System.currentTimeMillis() }

    private fun sordinaList(): Template {
        val list = ItemList.Builder()
        val all = passingAll()
        list.addItem(Row.Builder()
            .setTitle(if (all == null) tr(R.string.auto_pass_important) else tr(R.string.auto_pass_all_until, time(all)))
            .addText(tr(if (all == null) R.string.auto_pass_important_sub else R.string.auto_pass_all_sub))
            .setToggle(Toggle.Builder { on -> if (on) passImportant() else passAll(60) }.setChecked(all == null).build())
            .build())
        val groups = CarTalk.groups(app)
        val room = limit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST, 6) - 1
        groups.take(room).forEach { g ->
            val h = g.first()
            list.addItem(Row.Builder().setTitle(h.groupTitle)
                .addText(tr(R.string.auto_n_waiting, g.sumOf { it.count }) + " · " + h.appLabel)
                .setOnClickListener { screenManager.push(GroupScreen(carContext, this, g)) }
                .setBrowsable(true)
                .build())
        }
        // Le app nascoste: quante, mai chi
        val hidden = app.store.groups().filter { it.first().reason == Classifier.REASON_HIDDEN }.sumOf { g -> g.sumOf { it.count } }
        if (hidden > 0 && groups.size < room) list.addItem(Row.Builder().setTitle(tr(R.string.hidden_apps)).addText(tr(R.string.auto_n_waiting, hidden)).build())
        if (groups.isEmpty() && hidden == 0) list.addItem(Row.Builder().setTitle(tr(R.string.auto_nothing_waiting)).build())
        return ListTemplate.Builder().setSingleList(list.build()).build()
    }

    // Agenda: oggi e i prossimi giorni, con "Vai" dove c'è un indirizzo

    private fun loadEvents() {
        events = runCatching { app.calendars.today(carContext, days = AGENDA_DAYS) }.getOrDefault(emptyList())
        lifecycleScope.launch {
            val home = withContext(Dispatchers.IO) { runCatching { app.ha.calendarEvents(AGENDA_DAYS) }.getOrDefault(emptyList()) }
            if (home.isEmpty()) return@launch
            val now = System.currentTimeMillis()
            events = (events + home.filter { it.end > now }).distinctBy { it.title to it.begin }.sortedBy { it.begin }
            if (shown(Prefs.CAR_TAB_AGENDA)) invalidateSoon()
        }
    }

    private fun agendaList(): Template {
        val list = ItemList.Builder()
        events.take(limit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST, 6)).forEach { e ->
            list.addItem(Row.Builder().setTitle(e.title)
                .addText(day(e.begin) + " · " + time(e.begin))
                .apply { e.location?.let { loc -> addText(tr(R.string.auto_go_to, loc)); setOnClickListener { navigate(loc) } } }
                .build())
        }
        if (events.isEmpty()) list.setNoItemsMessage(tr(R.string.auto_no_events_week))
        return ListTemplate.Builder().setSingleList(list.build()).build()
    }

    private fun time(at: Long): String = DateFormat.getTimeFormat(carContext).format(java.util.Date(at))

    /** "Oggi", "Domani", "Sabato 21" */
    private fun day(at: Long): String {
        val zone = java.time.ZoneId.systemDefault()
        val d = java.time.Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        val today = java.time.LocalDate.now(zone)
        return when (d) {
            today -> tr(R.string.today_title)
            today.plusDays(1) -> tr(R.string.tomorrow_cap)
            else -> java.time.format.DateTimeFormatter.ofPattern("EEEE d", java.util.Locale.getDefault()).format(d).replaceFirstChar { it.uppercase() }
        }
    }

    // Parla

    private fun talkMessage(): Template = MessageTemplate.Builder(
        if (listening) heard?.let { "“$it”" } ?: tr(R.string.auto_listening_hint) else heard?.let { "“$it”" } ?: tr(R.string.auto_listening_hint))
        .setTitle(tr(if (replyTo != null) R.string.auto_reply_what_title else R.string.auto_listening))
        .setIcon(carContext.icon(R.drawable.car_mic))
        .addAction(carContext.button(tr(R.string.cancel)) { endTalk(null) })
        .build()

    /** Parla senza schede: una schermata sua sopra Casa */
    private fun talkAlone() {
        val s = SayScreen(carContext, tr(R.string.auto_listening), tr(R.string.auto_listening_hint), R.drawable.car_mic) { s ->
            listOf(carContext.button(tr(R.string.cancel)) { endTalk(null); s.finish() })
        }
        talkScreen = s
        screenManager.push(s)
        talk()
    }

    private fun talk() {
        if (!CarVoice.allowed(carContext)) {
            carContext.requestPermissions(listOf(Manifest.permission.RECORD_AUDIO)) { granted, _ ->
                if (Manifest.permission.RECORD_AUDIO in granted) talk() else endTalk(tr(R.string.auto_mic_denied))
            }
            return
        }
        listening = true
        heard = null
        invalidate()
        var shownAt = 0L
        CarVoice.listen(carContext,
            onPartial = { t ->
                heard = t
                // Le parole compaiono mentre parli, senza ridisegnare a ogni sillaba
                val now = System.currentTimeMillis()
                if (now - shownAt > 700) { shownAt = now; if (active == TALK) invalidate() }
            },
            onHeard = { all ->
                listening = false
                val t = all.firstOrNull()
                if (t == null) { endTalk(tr(R.string.not_understood)); return@listen }
                heard = t
                val to = replyTo
                replyTo = null
                endTalk(null)
                if (to != null) confirmReply(to, t) else handle(t)
            },
            onError = { msg -> listening = false; endTalk(msg) },
        )
    }

    /** Fine dell'ascolto: si torna alla scheda di prima (o si chiude la schermata di Parla). [stay]: hai cambiato scheda tu */
    private fun endTalk(say: String?, stay: Boolean = false) {
        if (listening) CarVoice.cancel(carContext)
        listening = false
        if (!stay && active == TALK) active = back?.takeIf { it != TALK }
        talkScreen?.let { s -> talkScreen = null; runCatching { if (screenManager.top == s) s.finish() } }
        invalidate()
        say?.let(::say)
    }

    /** Quello che hai detto a Parla */
    private fun handle(said: String) {
        when (val ask = CarTalk.understand(app, said, events)) {
            CarAsk.Waiting -> {
                val groups = CarTalk.groups(app)
                if (groups.isEmpty()) say(tr(R.string.auto_nothing_waiting))
                else say(tr(R.string.auto_waiting_say, groups.sumOf { g -> g.sumOf { it.count } },
                    groups.take(4).joinToString("; ") { g -> g.first().groupTitle + ", " + g.sumOf { it.count } }))
            }
            is CarAsk.Read -> say(readAloud(ask.group))
            is CarAsk.Reply -> if (ask.text == null) replyByVoice(ask.group) else confirmReply(ask.group, ask.text)
            is CarAsk.PassAll -> passAll(ask.minutes)
            CarAsk.PassImportant -> passImportant()
            CarAsk.NextEvent -> {
                val now = System.currentTimeMillis()
                val e = events.firstOrNull { it.begin > now } ?: events.firstOrNull()
                say(if (e == null) tr(R.string.auto_no_events) else tr(R.string.auto_next_event, e.title, time(e.begin)))
            }
            is CarAsk.GoTo -> ask.event.location?.let(::navigate)
            is CarAsk.Do -> act(ask.act)
            CarAsk.Google -> say(tr(R.string.auto_ask_google))
            CarAsk.Unknown -> say(tr(R.string.not_understood))
        }
    }

    private fun act(a: Act) {
        when (a) {
            is Act.Routine -> routine(a.entity)
            is Act.AlarmOff -> pickAlarm(a.entity, prefs.haAlarms.keys) { alarmOff(it) }
            is Act.AlarmOn -> pickAlarm(a.entity, prefs.haAlarms.filterValues { it.arm }.keys) { alarmOn(it, a.mode) }
            is Act.Assist -> assist(a.text, a.guarded)
            is Act.Call -> call(a.contact)
            else -> say(tr(R.string.auto_ask_google))
        }
    }

    // Routine e allarmi

    /**
     * Un riquadro di Casa (o "esco" detto a Parla). Disattivare l'allarme: solo da fermi, col codice o l'impronta sul
     * telefono. Inserirlo: una conferma (il codice solo se l'allarme lo vuole anche per inserire). Una routine con
     * l'impronta e senza allarme: l'impronta sul telefono, da fermi. Le altre partono subito.
     */
    fun routine(entity: String) {
        val r = prefs.haRoutines[entity]?.takeIf { it.on } ?: return
        val a = r.alarm?.let { prefs.haAlarms[it] }
        when {
            a != null && !r.arm -> unlessOff(r.alarm) {
                if (!stopFirst(R.string.auto_stop_alarm, r.name)) screenManager.push(CodeScreen(carContext, this,
                    tr(R.string.auto_code_title), tr(R.string.auto_code_disarm, r.name, a.name), CodeOp.Routine(entity)))
            }
            a != null && a.codeArm && a.enc.isNotEmpty() -> if (!stopFirst(R.string.auto_stop_code, r.name)) screenManager.push(CodeScreen(carContext, this,
                tr(R.string.auto_code_title), tr(R.string.auto_code_arm, r.name, a.name), CodeOp.Routine(entity)))
            a != null -> screenManager.push(SayScreen(carContext, r.name, tr(R.string.auto_confirm_arm, a.name, AlarmCode.modeLabel(a.mode ?: "away"))) { s ->
                listOf(carContext.button(tr(R.string.cancel)) { s.finish() }, carContext.button(tr(R.string.auto_run), primary = true) { s.finish(); home { HomeRun.routine(app, entity, "") } })
            })
            r.auth -> if (!stopFirst(R.string.auto_stop_guard, r.name)) onPhone(r.name) { CarAuthActivity.start(carContext, CarAuthActivity.OP_ROUTINE, entity, r.name) }
            else -> home { HomeRun.routine(app, entity, null) }
        }
    }

    private fun alarmOff(entity: String) {
        val a = prefs.haAlarms[entity] ?: return
        unlessOff(entity) {
            if (!stopFirst(R.string.auto_stop_alarm, tr(R.string.alarm_off_x, a.name))) screenManager.push(CodeScreen(carContext, this,
                tr(R.string.auto_code_title), tr(R.string.alarm_off_x, a.name), CodeOp.AlarmOff(entity)))
        }
    }

    private fun alarmOn(entity: String, mode: String?) {
        val a = prefs.haAlarms[entity] ?: return
        lifecycleScope.launch {
            val st = withContext(Dispatchers.IO) { HomeRun.state(app, entity) }
            if (HomeRun.armed(st) || st == "triggered") { say(tr(R.string.alarm_already_on, a.name)); return@launch }
            if (a.codeArm && a.enc.isNotEmpty()) {
                if (!stopFirst(R.string.auto_stop_code, a.name)) screenManager.push(CodeScreen(carContext, this@CarHome,
                    tr(R.string.auto_code_title), a.name, CodeOp.AlarmOn(entity, mode)))
            } else home { HomeRun.alarmOn(app, entity, mode, "") }
        }
    }

    /** Già disattivato: lo dico e basta, niente codice e niente routine */
    private fun unlessOff(alarm: String, go: () -> Unit) {
        lifecycleScope.launch {
            val st = withContext(Dispatchers.IO) { HomeRun.state(app, alarm) }
            if (st == "disarmed") say(tr(R.string.alarm_already_off, prefs.haAlarms[alarm]?.name ?: alarm)) else go()
        }
    }

    /** Detto senza nome e con più allarmi salvati: si sceglie quale */
    private fun pickAlarm(entity: String?, saved: Set<String>, go: (String) -> Unit) {
        val e = entity ?: saved.singleOrNull()
        if (e != null) { go(e); return }
        if (saved.isEmpty()) { say(tr(R.string.not_understood)); return }
        screenManager.push(PickScreen(carContext, tr(R.string.alarm_pick), saved.map { it to (prefs.haAlarms[it]?.name ?: it) }, go))
    }

    /** In movimento (se l'auto lo dice): "fermati", e la cosa aspetta in Casa */
    private fun stopFirst(why: Int, what: String): Boolean {
        if (motion.moving != true) return false
        screenManager.push(SayScreen(carContext, tr(R.string.auto_stop_title), tr(why) + "\n" + tr(R.string.auto_stop_wait, what)))
        return true
    }

    /** L'impronta sul telefono, senza codice (routine con l'impronta, "apri il cancello"): solo da fermi */
    private fun onPhone(title: String, start: () -> Boolean) {
        screenManager.push(SayScreen(carContext, title, tr(R.string.auto_guard_sub)) { s ->
            listOf(carContext.button(tr(R.string.cancel)) { s.finish() }, carContext.button(tr(R.string.auto_fingerprint), primary = true, parked = true) {
                CarBus.take()
                if (!start()) return@button
                s.finish()
                val wait = SayScreen(carContext, tr(R.string.auto_on_phone), tr(R.string.auto_on_phone_sub))
                screenManager.push(wait)
                lifecycleScope.launch {
                    val out = CarBus.outcome.filterNotNull().first()
                    CarBus.take()
                    runCatching { wait.finish() }
                    out.done?.let(::done)
                }
            })
        })
    }

    /** Fa una cosa di casa fuori dal thread principale, poi l'esito */
    private fun home(job: () -> HomeRun.Done) {
        CarToast.makeText(carContext, tr(R.string.ha_asking), CarToast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            done(withContext(Dispatchers.IO) { runCatching(job).getOrElse { HomeRun.Done(tr(R.string.home_no_answer), false) } })
        }
    }

    /** L'esito di una routine o di un allarme: la sordina (contenta se è andata), la frase detta, e si torna a Casa da soli */
    fun done(d: HomeRun.Done) {
        say(d.message)
        screenManager.push(SayScreen(carContext, null, d.message, if (d.ok) R.drawable.car_mascot_happy else R.drawable.car_mascot, autoClose = true))
    }

    // Casa, persone, strade

    private fun assist(text: String, guarded: Boolean) {
        if (guarded) {
            if (!stopFirst(R.string.auto_stop_guard, text)) onPhone(text) { CarAuthActivity.start(carContext, CarAuthActivity.OP_ASSIST, "", text, text) }
            return
        }
        CarToast.makeText(carContext, tr(R.string.ha_asking), CarToast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val answer = withContext(Dispatchers.IO) { runCatching { app.ha.assist(text) }.getOrNull() } ?: tr(R.string.home_no_answer)
            say(answer)
            CarToast.makeText(carContext, answer, CarToast.LENGTH_LONG).show()
        }
    }

    /** "Chiama Anna": verso le persone serve il tuo tocco */
    private fun call(c: Contact) {
        screenManager.push(SayScreen(carContext, null, tr(R.string.auto_call_q, c.name)) { s ->
            listOf(carContext.button(tr(R.string.cancel)) { s.finish() }, carContext.button(tr(R.string.auto_call), primary = true) {
                s.finish()
                runCatching { carContext.startCarApp(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", c.number, null))) }
            })
        })
    }

    private fun navigate(place: String) {
        runCatching { carContext.startCarApp(Intent(CarContext.ACTION_NAVIGATE, Uri.parse("geo:0,0?q=" + Uri.encode(place)))) }
            .onFailure { say(tr(R.string.auto_ask_google)) }
    }

    // Cosa passa

    fun passAll(minutes: Int) {
        val until = System.currentTimeMillis() + minutes * 60_000L
        prefs.passUntil = prefs.passUntil + (Prefs.PASS_ALL to until)
        rules()
        say(tr(R.string.auto_passing_all, time(until)))
    }

    fun passImportant() {
        prefs.passUntil = prefs.passUntil - Prefs.PASS_ALL
        rules()
        say(tr(R.string.auto_passing_important))
    }

    private fun rules() {
        runCatching { app.silence.check() }
        Scheduler.refresh(carContext)
        invalidate()
    }

    // Rispondere

    /** La chiave della notifica più recente del gruppo a cui si può rispondere, se c'è */
    fun replyKey(group: List<Held>): String? = group.firstOrNull { SordinaListener.replyAction(it.key) != null }?.key

    /** "Rispondi a voce": la prossima frase detta è la risposta */
    fun replyByVoice(group: List<Held>) {
        val name = group.first().groupTitle
        if (replyKey(group) == null) { say(tr(R.string.auto_cant_reply, name)); return }
        replyTo = group
        say(tr(R.string.auto_reply_what, name))
        // Prima la domanda, poi il microfono
        main.postDelayed({
            if (withTabs()) { back = active?.takeIf { it != TALK } ?: back; active = TALK; invalidate(); talk() } else talkAlone()
        }, REPLY_PAUSE_MILLIS)
    }

    private fun confirmReply(group: List<Held>, text: String) {
        val name = group.first().groupTitle
        val key = replyKey(group) ?: run { say(tr(R.string.auto_cant_reply, name)); return }
        screenManager.push(SayScreen(carContext, tr(R.string.auto_reply_to, name), "“$text”", null) { s ->
            listOf(carContext.button(tr(R.string.cancel)) { s.finish() }, carContext.button(tr(R.string.auto_send), primary = true) {
                s.finish()
                say(if (SordinaListener.reply(key, text)) tr(R.string.auto_sent, name) else tr(R.string.auto_send_failed))
            })
        })
    }

    // Voce e ridisegno

    fun say(text: String) = CarVoice.say(carContext, text)

    private fun invalidateSoon() {
        if (redraw) return
        redraw = true
        main.postDelayed({ if (redraw) invalidate() }, REDRAW_MILLIS)
    }

    companion object {
        const val TALK = "talk"
        private const val AGENDA_DAYS = 7
        private const val REDRAW_MILLIS = 2_000L
        private const val REPLY_PAUSE_MILLIS = 1_800L

        /** "Ascolta": chi, da quale app, e gli ultimi messaggi */
        fun readAloud(group: List<Held>): String {
            val h = group.first()
            val lines = group.flatMap { g -> g.lines.ifEmpty { listOf(HeldLine(g.time, null, g.text)) } }
                .sortedBy { it.time }.takeLast(5)
                .joinToString(". ") { l -> (l.sender?.takeIf { it != h.groupTitle }?.let { "$it: " } ?: "") + l.text }
            return tr(R.string.auto_read, h.groupTitle, h.appLabel, lines)
        }
    }
}
