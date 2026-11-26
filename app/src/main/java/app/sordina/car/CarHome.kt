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
import androidx.car.app.model.CarColor
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridSection
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.SectionedItemTemplate
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
import app.sordina.data.HaStates
import app.sordina.data.HaEntity
import app.sordina.data.Prefs
import app.sordina.data.ReadLines
import app.sordina.data.Search
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
 * Casa (allarmi e routine "Anche in auto", a riquadri grandi), In sordina (cosa è arrivato da quando sei in auto),
 * Agenda (i prossimi impegni). Su ogni scheda il pulsante Cerca, come in Spotify: la barra col microfono di Android
 * Auto (la tastiera la spegne lui mentre guidi); quello che dici o scrivi e non trova lo capisce Parla.
 * Con una scheda sola (o un'auto senza schede, prima della Car API 6) resta una schermata sola. Prima della Car API 7
 * (niente pulsanti tondi) al posto di Cerca c'è il microfono in alto.
 */
class CarHome(cc: CarContext, private val motion: CarMotion) : Screen(cc) {
    val app: SordinaApp = SordinaApp.of(cc)
    private val prefs get() = app.prefs
    private val main = Handler(Looper.getMainLooper())

    /** La scheda aperta */
    private var active: String? = null
    /** Parla sta ascoltando */
    private var listening = false
    /** La schermata di Parla mentre ascolta */
    private var talkScreen: SayScreen? = null
    /** Dopo "rispondi a Paolo": la prossima frase è la risposta */
    private var replyTo: List<Held>? = null
    private var events: List<Upcoming> = emptyList()
    private var redraw = false

    init {
        // Arriva o se ne va qualcosa in sordina: la scheda si aggiorna (al massimo ogni paio di secondi)
        lifecycleScope.launch { app.store.held.drop(1).collect { if (shown(Prefs.CAR_TAB_SORDINA)) invalidateSoon() } }
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { loadEvents(); loadAlarmStates() }
            override fun onStop(owner: LifecycleOwner) { if (listening) endTalk(null) }
        })
    }

    // Le schede

    private fun tabs(): List<String> = prefs.carTabs.filter { it !in prefs.carTabsOff }.ifEmpty { listOf(Prefs.CAR_TAB_HOME) }
    private fun withTabs(): Boolean = carContext.carAppApiLevel >= 6 && tabs().size >= 2
    private fun shown(id: String) = (if (withTabs()) active ?: tabs().first() else tabs().first()) == id

    override fun onGetTemplate(): Template {
        redraw = false
        val ids = tabs()
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
        else -> R.drawable.car_calendar
    })).build()

    private fun title(id: String): String = when (id) {
        Prefs.CAR_TAB_HOME -> tr(R.string.auto_tab_home)
        Prefs.CAR_TAB_SORDINA -> sinceCar().size.let { n -> if (n > 0) tr(R.string.auto_tab_sordina_n, n) else tr(R.string.auto_tab_sordina) }
        else -> tr(R.string.auto_tab_agenda)
    }

    private fun select(id: String) {
        if (id == active) return
        active = id
        if (id == Prefs.CAR_TAB_AGENDA) loadEvents()
        invalidate()
    }

    /** Una scheda sola: la sordina e il nome in alto; il microfono a destra se l'auto non ha il pulsante Cerca */
    private fun single(id: String): Template {
        val strip = if (searchFab() == null) ActionStrip.Builder().addAction(Action.Builder().setIcon(carContext.icon(R.drawable.car_mic))
            .setOnClickListener { talkAlone() }.build()).build() else null
        return when (val t = content(id)) {
            is SectionedItemTemplate -> SectionedItemTemplate.Builder(t).setHeader(Header.Builder().setTitle(tr(R.string.app_name))
                .setStartHeaderAction(Action.APP_ICON)
                .build()).build()
            is GridTemplate -> GridTemplate.Builder().setTitle(tr(R.string.app_name)).setHeaderAction(Action.APP_ICON)
                .setSingleList(t.singleList!!).large().apply { strip?.let { setActionStrip(it) }; searchFab()?.let(::addAction) }.build()
            is ListTemplate -> ListTemplate.Builder().setTitle(tr(R.string.app_name)).setHeaderAction(Action.APP_ICON)
                .apply {
                    t.singleList?.let(::setSingleList)
                    t.sectionedLists.forEach(::addSectionedList)
                    strip?.let { setActionStrip(it) }
                    searchFab()?.let(::addAction)
                }.build()
            else -> t
        }
    }

    private fun content(id: String): Template = when (id) {
        Prefs.CAR_TAB_HOME -> homeGrid()
        Prefs.CAR_TAB_SORDINA -> sordinaList()
        else -> agendaList()
    }

    /** Il pulsante Cerca su ogni scheda (Car API 7: i pulsanti tondi della schermata) */
    private fun searchFab(): Action? = if (carContext.carAppApiLevel < 7) null else Action.Builder()
        .setIcon(CarTiles.glyph(carContext, R.drawable.car_search))
        .setBackgroundColor(CarTiles.accent(prefs).let { CarColor.createCustom(it, it) })
        .setOnClickListener { openSearch() }
        .build()

    private fun limit(type: Int, fallback: Int) =
        runCatching { carContext.getCarService(ConstraintManager::class.java).getContentLimit(type) }.getOrDefault(fallback).coerceAtLeast(1)

    // Casa: le routine "Anche in auto", nell'ordine in cui le hai abilitate

    private fun carRoutines() = prefs.haRoutines.filter { (_, r) -> r.on && r.car }
    private fun carAlarms() = prefs.haAlarms.filter { (_, a) -> a.car }

    /** Lo stato degli allarmi in Casa ("armed_away", "disarmed"…), letto quando la schermata si apre e dopo ogni comando */
    private var alarmStates: Map<String, String?> = emptyMap()

    /** Gli stati di casa scelti per l'auto (temperature, porte, luci), letti insieme agli allarmi */
    private var homeStates: Map<String, HaEntity> = emptyMap()

    /**
     * Si sta leggendo casa: allarmi e stati sono riquadri "in caricamento". Android Auto, quando la scheda si aggiorna,
     * non ridisegna un'immagine cambiata nello stesso riquadro (lo stato non comparirebbe): da "in caricamento" a
     * immagine invece sì.
     */
    private var reading = true

    private fun loadAlarmStates() {
        val ids = carAlarms().keys
        val states = prefs.haCarStates.keys
        if (ids.isEmpty() && states.isEmpty()) { reading = false; return }
        if (!reading) { reading = true; if (shown(Prefs.CAR_TAB_HOME)) invalidate() }
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                // Tutto in una richiesta sola (ricca, ma una): allarmi e stati
                val all = runCatching { app.ha.entities(force = true) }.getOrDefault(emptyList()).associateBy { it.id }
                alarmStates = ids.associateWith { all[it]?.state ?: HomeRun.state(app, it) }
                homeStates = states.mapNotNull { id -> all[id]?.let { id to it } }.toMap()
            }
            reading = false
            if (shown(Prefs.CAR_TAB_HOME)) invalidate()
        }
    }

    /**
     * Casa: prima gli allarmi (un tocco inserisce o disinserisce), poi le routine. Dalla Car API 8 riquadri grandi
     * che scorrono (il nome sotto, su due righe; lo stato dentro); prima la griglia normale, coi riquadri da 64dp.
     */
    private fun homeGrid(): Template {
        // I nomi scelti da te per l'auto (Impostazioni › Home Assistant › Riquadri in auto)
        val names = prefs.carNames
        val room = limit(ConstraintManager.CONTENT_LIMIT_TYPE_GRID, 6)
        // Con le sezioni la griglia scorre: tutti; prima, quanti ne entrano
        val big = carContext.carAppApiLevel >= 8
        val accent = CarTiles.accent(prefs)
        // Grande: nome e stato dentro il riquadro (sotto, l'auto li taglierebbe); piccolo: sotto, come sempre
        fun item(name: String, caption: String?, glyph: Int, state: CarTiles.State, inner: String? = null, onClick: () -> Unit): GridItem {
            // Grande: nome e stato dentro il riquadro, centrati; sotto un titolo vuoto (l'auto allinea a sinistra e taglia,
            // ma senza titolo Android Auto si chiude: "A title must be set on the row")
            if (big) return GridItem.Builder().setTitle(CarTiles.NO_TITLE)
                .setImage(CarTiles.tile(carContext, glyph, state, accent, caption, inner ?: name), GridItem.IMAGE_TYPE_LARGE)
                .setOnClickListener(onClick)
                .build()
            return GridItem.Builder().setTitle(name)
                .apply { caption?.let { setText(it) } }
                .setImage(CarTiles.tile(carContext, glyph, state, accent), GridItem.IMAGE_TYPE_LARGE)
                .setOnClickListener(onClick)
                .build()
        }
        val alarms = carAlarms().entries.let { if (big) it else it.take(room) }.map { (id, a) ->
            val st = alarmStates[id]
            val state = when {
                running == id -> CarTiles.State.RUNNING
                st == "triggered" -> CarTiles.State.TRIGGERED
                st == "arming" || st == "pending" -> CarTiles.State.ARMING
                HomeRun.armed(st) -> CarTiles.State.ARMED
                else -> CarTiles.State.DISARMED
            }
            val text = when {
                running == id -> tr(R.string.auto_running)
                st == null -> null
                st == "triggered" -> tr(R.string.auto_alarm_triggered)
                st == "arming" || st == "pending" -> tr(R.string.auto_alarm_arming)
                // Grande: dentro il riquadro solo il modo ("Notte"); piccolo: "Inserito · Notte"
                HomeRun.armed(st) -> AlarmCode.modeLabel(st.removePrefix("armed_")).let { if (big) it else tr(R.string.auto_alarm_on, it) }
                else -> tr(R.string.auto_alarm_off)
            }
            // Grande: sotto il riquadro "Allarme", dentro il nome corto ("Centrale Naboo" → "Naboo") e lo stato
            if (big && (reading || running == id)) GridItem.Builder().setTitle(CarTiles.NO_TITLE).setLoading(true).build()
            else if (big) item(a.name, text, R.drawable.car_shield, state, names[id] ?: CarTiles.shortName(a.name) ?: a.name) { alarmTile(id) }
            else item(names[id] ?: a.name, text, R.drawable.car_shield, state) { alarmTile(id) }
        }
        val routines = carRoutines().entries.let { if (big) it else it.take((room - alarms.size).coerceAtLeast(0)) }.map { (id, r) ->
            val alarm = r.alarm?.let { prefs.haAlarms[it]?.name }
            // Sotto il nome: in corso, oppure cosa fa all'allarme
            val text = when {
                running == id -> tr(R.string.auto_running)
                alarm != null -> tr(if (r.arm) R.string.auto_routine_arm else R.string.auto_routine_disarm, alarm)
                else -> null
            }
            // In corso: "in caricamento" (un'immagine nuova nello stesso riquadro Android Auto non la ridisegna)
            if (big && running == id) GridItem.Builder().setTitle(CarTiles.NO_TITLE).setLoading(true).build()
            else item(names[id] ?: r.name, text, R.drawable.car_play, if (running == id) CarTiles.State.RUNNING else CarTiles.State.IDLE) { routine(id) }
        }
        // Gli stati di casa: dentro il riquadro il valore grande ("21,5°C", "Aperto") e sotto il nome; un tocco li rilegge
        val states = prefs.haCarStates.entries.let { if (big) it else it.take((room - alarms.size - routines.size).coerceAtLeast(0)) }.map { (id, ha) ->
            val name = names[id] ?: ha
            val e = homeStates[id]
            val value = e?.let(HaStates::label) ?: "…"
            val st = if (e != null && HaStates.active(e)) CarTiles.State.ON else CarTiles.State.IDLE
            if (big && reading) GridItem.Builder().setTitle(CarTiles.NO_TITLE).setLoading(true).build()
            else if (big) GridItem.Builder().setTitle(CarTiles.NO_TITLE)
                .setImage(CarTiles.tile(carContext, CarTiles.glyphFor(e, id), st, accent, name, value), GridItem.IMAGE_TYPE_LARGE)
                .setOnClickListener { loadAlarmStates() }
                .build()
            else GridItem.Builder().setTitle(name).setText(value)
                .setImage(CarTiles.tile(carContext, CarTiles.glyphFor(e, id), st, accent), GridItem.IMAGE_TYPE_LARGE)
                .setOnClickListener { loadAlarmStates() }
                .build()
        }
        // L'ordine è il tuo (Impostazioni › Home Assistant › Ordine dei riquadri); quelli nuovi vanno in fondo
        val order = prefs.carTileOrder
        val ids = carAlarms().keys.toList().take(alarms.size) + carRoutines().keys.toList().take(routines.size) + prefs.haCarStates.keys.toList().take(states.size)
        val tiles = (ids zip (alarms + routines + states)).sortedBy { (id, _) -> order.indexOf(id).let { if (it < 0) Int.MAX_VALUE else it } }.map { it.second }
        if (big && tiles.isNotEmpty()) {
            // Una griglia sola, prima gli allarmi: i titoli delle sezioni l'auto li fa grigi e quasi invisibili
            return SectionedItemTemplate.Builder()
                .addSection(GridSection.Builder().setItems(tiles).setItemSize(GridSection.ITEM_SIZE_LARGE).build())
                .apply { searchFab()?.let(::addAction) }
                .build()
        }
        val list = ItemList.Builder()
        tiles.forEach(list::addItem)
        if (alarms.isEmpty() && routines.isEmpty() && states.isEmpty()) list.setNoItemsMessage(tr(if (app.ha.configured) R.string.auto_no_routines else R.string.auto_no_ha))
        return GridTemplate.Builder().setSingleList(list.build()).large().apply { searchFab()?.let(::addAction) }.build()
    }

    /** Riquadri grandi, come le copertine di Spotify (dove l'auto li sa fare) */
    private fun GridTemplate.Builder.large() = apply { if (carContext.carAppApiLevel >= 7) setItemSize(GridTemplate.ITEM_SIZE_LARGE) }

    /** Il riquadro di un allarme: inserito (o scattato) lo disinserisce, disinserito lo inserisce se gliel'hai permesso */
    private fun alarmTile(entity: String) {
        val a = prefs.haAlarms[entity] ?: return
        lifecycleScope.launch {
            val st = withContext(Dispatchers.IO) { HomeRun.state(app, entity) }
            alarmStates = alarmStates + (entity to st)
            when {
                st == null -> say(tr(R.string.home_no_answer))
                HomeRun.armed(st) || st == "triggered" -> alarmOff(entity)
                !a.arm -> say(tr(R.string.alarm_already_off, a.name))
                a.codeArm && a.enc.isNotEmpty() -> alarmOn(entity, null)
                else -> screenManager.push(SayScreen(carContext, a.name, tr(R.string.alarm_on_mode_x, a.name, AlarmCode.modeLabel(a.mode ?: "away")), R.drawable.car_shield) { s ->
                    listOf(carContext.button(tr(R.string.cancel)) { s.finish() }, carContext.button(tr(R.string.auto_arm), primary = true) { s.finish(); alarmOn(entity, null) })
                })
            }
            invalidate()
        }
    }

    // Cerca: allarmi, routine, chat in sordina e impegni che contengono quello che scrivi; Invio lo fa capire a Parla

    private var query = ""

    /** Cerca, sopra la scheda: la barra con la tastiera (da fermi) e il microfono */
    private fun openSearch() {
        query = ""
        screenManager.push(object : Screen(carContext) {
            override fun onGetTemplate(): Template = searchTemplate(this)
        })
    }

    private fun searchTemplate(screen: Screen): Template {
        val results = found(query)
        val list = ItemList.Builder()
        results.take(limit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST, 6)).forEach { (go, row) ->
            list.addItem(row.setOnClickListener { screen.finish(); go() }.build())
        }
        if (query.isNotBlank() && results.isEmpty()) list.setNoItemsMessage(tr(R.string.auto_search_none))
        return SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(searchText: String) {
                if (searchText == query) return
                query = searchText
                screen.invalidate()
            }
            override fun onSearchSubmitted(searchText: String) {
                query = searchText
                val hits = found(searchText)
                // Una cosa sola: la si fa; niente: la frase va a Parla ("esco", "fai passare tutti per un'ora"…)
                when {
                    hits.size == 1 && searchText.isNotBlank() -> { screen.finish(); hits.first().first.invoke() }
                    hits.isEmpty() && searchText.isNotBlank() -> { screen.finish(); handle(searchText) }
                    else -> screen.invalidate()
                }
            }
        })
            .setHeaderAction(Action.BACK)
            .setSearchHint(tr(R.string.auto_search_hint))
            .setInitialSearchText(query)
            .setShowKeyboardByDefault(motion.moving != true)
            .setItemList(list.build())
            .build()
    }

    /**
     * Quello che corrisponde a [q]: cosa fare col tocco, e la riga da mostrare. Barra vuota: allarmi e routine.
     * Il microfono è quello della barra (lo mette Android Auto): la frase detta arriva come se l'avessi scritta.
     */
    private fun found(q: String): List<Pair<() -> Unit, Row.Builder>> {
        val n = Search.normalize(q)
        fun hit(s: String?) = n.isEmpty() || s != null && n in Search.normalize(s)
        val out = ArrayList<Pair<() -> Unit, Row.Builder>>()
        val accent = CarTiles.accent(prefs)
        carAlarms().filter { hit(it.value.name) }.forEach { (id, a) ->
            out += { alarmTile(id) } to Row.Builder().setTitle(a.name).addText(tr(R.string.auto_section_alarms))
                .setImage(CarTiles.tile(carContext, R.drawable.car_shield, CarTiles.State.IDLE, accent), Row.IMAGE_TYPE_LARGE)
        }
        carRoutines().filter { hit(it.value.name) }.forEach { (id, r) ->
            out += { routine(id) } to Row.Builder().setTitle(r.name).addText(tr(R.string.auto_section_routines))
                .setImage(CarTiles.tile(carContext, R.drawable.car_play, CarTiles.State.IDLE, accent), Row.IMAGE_TYPE_LARGE)
        }
        if (n.isEmpty()) return out
        CarTalk.groups(app).filter { g -> hit(g.first().groupTitle) || hit(g.first().appLabel) }.forEach { g ->
            val h = g.first()
            val open = { screenManager.push(GroupScreen(carContext, this, g)) }
            out += open to Row.Builder().setTitle(h.groupTitle).addText(waiting(g))
                .apply { CarTiles.framed(carContext, h.pkg, CarTiles.accent(prefs))?.let { setImage(it, Row.IMAGE_TYPE_LARGE) } }
                .setBrowsable(true)
        }
        events.filter { hit(it.title) || hit(it.location) }.forEach { e ->
            val go = { e.location?.let(::navigate) ?: Unit }
            out += go to Row.Builder().setTitle(e.title).addText(day(e.begin) + " · " + time(e.begin))
                .apply { e.location?.let { loc -> addText(tr(R.string.auto_go_to, loc)) } }
        }
        return out
    }

    /** "5 in attesa", con l'app solo se il nome del gruppo non è già quello dell'app */
    private fun waiting(g: List<Held>): String {
        val h = g.first()
        val n = tr(R.string.auto_n_waiting, g.sumOf { it.count })
        return if (h.groupTitle == h.appLabel) n else n + " · " + h.appLabel
    }

    // In sordina: cosa passa, poi chi aspetta

    private fun passingAll(): Long? = prefs.passUntil[Prefs.PASS_ALL]?.takeIf { it > System.currentTimeMillis() }

    /** Quando è partita Sordina sull'auto: In sordina mostra solo quello arrivato da allora (il resto è sul telefono) */
    private val since = System.currentTimeMillis()

    /** Le notifiche in sordina arrivate da quando sei in auto, una per una, dalla più recente */
    private fun sinceCar(): List<Held> = (CarTalk.groups(app).flatten() + Demo.held(carContext, since)).filter { it.time >= since }.sortedByDescending { it.time }

    private fun sordinaList(): Template {
        val list = ItemList.Builder()
        val all = passingAll()
        // Una riga sola: cosa fa l'interruttore
        list.addItem(Row.Builder()
            .setTitle(if (all == null) tr(R.string.auto_pass_important_sub) else tr(R.string.auto_pass_all_until, time(all)))
            .setToggle(Toggle.Builder { on -> if (on) passImportant() else passAll(60) }.setChecked(all == null).build())
            .build())
        val room = limit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST, 6) - 2
        val fresh = sinceCar()
        // Chi scrive, da quale app e quando: il testo mai. Un tocco sulla riga la legge (solo i messaggi che non hai
        // sentito, o tutta se non ce ne sono di nuovi); il pulsante risponde a voce, o la segna letta se non si può
        // rispondere. Android Auto nelle liste
        // mostra un pulsante solo. Prima della Car API 6 (niente pulsanti) la riga apre la sua scheda.
        val inRow = carContext.carAppApiLevel >= 6
        fresh.take(room).forEach { h ->
            val one = listOf(h)
            val canReply = replyKey(one) != null
            list.addItem(Row.Builder().setTitle(h.groupTitle)
                .addText((if (h.groupTitle == h.appLabel) "" else h.appLabel + " · ") + time(h.time) +
                    (if (h.count > 1) " · " + tr(R.string.auto_n_waiting, h.count) else ""))
                .apply { CarTiles.framed(carContext, h.pkg, CarTiles.accent(prefs))?.let { setImage(it, Row.IMAGE_TYPE_LARGE) } }
                .apply {
                    if (!inRow) { setOnClickListener { screenManager.push(GroupScreen(carContext, this@CarHome, one)) }; setBrowsable(true); return@apply }
                    setOnClickListener { listen(h) }
                    // Sempre lo stesso pulsante: ascoltare non ridisegna la lista (l'auto la rifarebbe tutta, con l'animazione)
                    if (canReply) addAction(Action.Builder().setIcon(CarTiles.glyph(carContext, R.drawable.car_reply)).setOnClickListener { replyByVoice(one) }.build())
                    else addAction(Action.Builder().setIcon(CarTiles.glyph(carContext, R.drawable.car_check)).setOnClickListener { markRead(h) }.build())
                }
                .build())
        }
        list.addItem(Row.Builder().setTitle(tr(if (fresh.isEmpty()) R.string.auto_since_car_none else R.string.auto_older_on_phone))
            .apply { if (fresh.isEmpty()) addText(tr(R.string.auto_older_on_phone)) }
            .build())
        return ListTemplate.Builder().setSingleList(list.build()).apply { searchFab()?.let(::addAction) }.build()
    }

    /** I messaggi già ascoltati in auto, per notifica: "Ascolta" legge solo i nuovi */
    private val heard = HashMap<String, Set<Int>>()

    private fun lineFps(h: Held): List<Int> =
        h.lines.ifEmpty { listOf(HeldLine(h.time, null, h.text)) }.map { ReadLines.fp(Triple(it.time, it.sender, it.text)) }

    /** Legge quello che non hai ancora sentito (tutto, se l'avevi già sentito tutto) */
    private fun listen(h: Held) {
        val old = heard[h.key].orEmpty()
        val lines = h.lines.ifEmpty { listOf(HeldLine(h.time, null, h.text)) }
        val fresh = lines.filter { ReadLines.fp(Triple(it.time, it.sender, it.text)) !in old }.ifEmpty { lines }
        say(readAloud(listOf(h.copy(lines = fresh))))
        heard[h.key] = old + lineFps(h)
    }

    /** La spunta: letta, come in Sordina sul telefono (i suoi messaggi non tornano) */
    private fun markRead(h: Held) {
        if (Demo.isDemo(h.key)) Demo.read(h.key) else SordinaListener.readOne(carContext, h.key)
        heard.remove(h.key)
        invalidateSoon()
    }

    // Agenda: oggi e i prossimi giorni, con "Vai" dove c'è un indirizzo

    private fun loadEvents() {
        events = (runCatching { app.calendars.today(carContext, days = AGENDA_DAYS) }.getOrDefault(emptyList()) + Demo.events(carContext)).sortedBy { it.begin }
        lifecycleScope.launch {
            val home = withContext(Dispatchers.IO) { runCatching { app.ha.calendarEvents(AGENDA_DAYS) }.getOrDefault(emptyList()) }
            if (home.isEmpty()) return@launch
            val now = System.currentTimeMillis()
            events = (events + home.filter { it.end > now }).distinctBy { it.title to it.begin }.sortedBy { it.begin }
            if (shown(Prefs.CAR_TAB_AGENDA)) invalidateSoon()
        }
    }

    /**
     * Agenda come quella di Teams: una riga per impegno, divise da una linea; sotto il titolo il giorno e l'orario
     * ("Oggi · 19:00 – 20:00", con "Teams" se è una riunione online) e il posto. Dove c'è un indirizzo, il pulsante
     * tondo per andarci, come quelli di In sordina.
     */
    private fun agendaList(): Template {
        val list = ItemList.Builder()
        events.take(limit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST, 6)).forEach { e ->
            val hours = if (e.allDay) tr(R.string.cal_all_day) else time(e.begin) + " – " + time(e.end)
            list.addItem(Row.Builder().setTitle(e.title)
                .addText(day(e.begin) + " · " + hours + (online(e.link)?.let { " · $it" } ?: ""))
                .apply {
                    e.location?.let(::addText)
                    // Un pulsante solo (l'auto ne mostra uno). Partecipa: Android Auto non lascia aprire Teams (o Meet,
                    // Zoom) da un'altra app, ma lascia telefonare: si entra solo audio, col numero dell'invito.
                    when {
                        carContext.carAppApiLevel < 6 -> e.location?.let { loc -> setOnClickListener { navigate(loc) } }
                        e.dialIn != null -> addAction(Action.Builder().setIcon(CarTiles.glyph(carContext, R.drawable.car_join)).setOnClickListener { joinByPhone(e.dialIn) }.build())
                        e.location != null -> addAction(Action.Builder().setIcon(CarTiles.glyph(carContext, R.drawable.car_navigate)).setOnClickListener { navigate(e.location) }.build())
                    }
                }
                .build())
        }
        if (events.isEmpty()) list.setNoItemsMessage(tr(R.string.auto_no_events_week))
        return ListTemplate.Builder().setSingleList(list.build()).apply { searchFab()?.let(::addAction) }.build()
    }

    /** Partecipa solo audio: il numero della riunione, con il codice dopo la pausa */
    private fun joinByPhone(number: String) {
        runCatching { carContext.startCarApp(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)))) }
            .onFailure { say(tr(R.string.auto_ask_google)) }
    }

    /** Una riunione online: il servizio dal link ("Teams", "Meet", "Zoom"); null se non c'è un link */
    private fun online(link: String?): String? {
        val host = link?.let { runCatching { Uri.parse(it).host }.getOrNull() } ?: return null
        return when {
            "teams" in host -> "Teams"
            "meet.google" in host -> "Meet"
            "zoom" in host -> "Zoom"
            else -> host.removePrefix("www.")
        }
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

    /** Parla: una schermata sua sopra le schede, finché ascolta */
    private fun talkAlone() {
        val title = tr(if (replyTo != null) R.string.auto_reply_what_title else R.string.auto_listening)
        val s = SayScreen(carContext, title, tr(R.string.auto_listening_hint), R.drawable.car_mic) { s ->
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
        invalidate()
        CarVoice.listen(carContext,
            onPartial = { },
            onHeard = { all ->
                listening = false
                val t = all.firstOrNull()
                if (t == null) { endTalk(tr(R.string.not_understood)); return@listen }
                val to = replyTo
                replyTo = null
                endTalk(null)
                if (to != null) confirmReply(to, t) else handle(t)
            },
            onError = { msg -> listening = false; endTalk(msg) },
        )
    }

    /** Fine dell'ascolto: si chiude la schermata di Parla */
    private fun endTalk(say: String?) {
        if (listening) CarVoice.cancel(carContext)
        listening = false
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
                listOf(carContext.button(tr(R.string.cancel)) { s.finish() }, carContext.button(tr(R.string.auto_run), primary = true) { s.finish(); home(entity) { HomeRun.routine(app, entity, "") } })
            })
            r.auth -> if (!stopFirst(R.string.auto_stop_guard, r.name)) onPhone(r.name) { CarAuthActivity.start(carContext, CarAuthActivity.OP_ROUTINE, entity, r.name) }
            else -> home(entity) { HomeRun.routine(app, entity, null) }
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
            } else home(entity) { HomeRun.alarmOn(app, entity, mode, "") }
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
    /** La routine (o l'allarme) che sta andando adesso: il suo riquadro si riempie del colore d'accento */
    private var running: String? = null

    private fun home(entity: String? = null, job: () -> HomeRun.Done) {
        if (entity != null) { running = entity; if (shown(Prefs.CAR_TAB_HOME)) invalidate() }
        CarToast.makeText(carContext, tr(R.string.ha_asking), CarToast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            done(withContext(Dispatchers.IO) { runCatching(job).getOrElse { HomeRun.Done(tr(R.string.home_no_answer), false) } })
        }
    }

    /** L'esito di una routine o di un allarme: la sordina (contenta se è andata), la frase detta, e si torna a Casa da soli */
    fun done(d: HomeRun.Done) {
        running = null
        say(d.message)
        loadAlarmStates()
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
    fun replyKey(group: List<Held>): String? = group.firstOrNull { Demo.isDemo(it.key) || SordinaListener.replyAction(it.key) != null }?.key

    /** "Rispondi a voce": la prossima frase detta è la risposta */
    fun replyByVoice(group: List<Held>) {
        val name = group.first().groupTitle
        if (replyKey(group) == null) { say(tr(R.string.auto_cant_reply, name)); return }
        replyTo = group
        say(tr(R.string.auto_reply_what, name))
        // Prima la domanda, poi il microfono
        main.postDelayed({
            talkAlone()
        }, REPLY_PAUSE_MILLIS)
    }

    private fun confirmReply(group: List<Held>, text: String) {
        val name = group.first().groupTitle
        val key = replyKey(group) ?: run { say(tr(R.string.auto_cant_reply, name)); return }
        screenManager.push(SayScreen(carContext, tr(R.string.auto_reply_to, name), "“$text”", null) { s ->
            listOf(carContext.button(tr(R.string.cancel)) { s.finish() }, carContext.button(tr(R.string.auto_send), primary = true) {
                s.finish()
                val sent = Demo.isDemo(key) || SordinaListener.reply(key, text)
                say(if (sent) tr(R.string.auto_sent, name) else tr(R.string.auto_send_failed))
                // Risposto: la chat è letta, esce da In sordina (i suoi messaggi non tornano)
                if (sent) group.forEach(::markRead)
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
