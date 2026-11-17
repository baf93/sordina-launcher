package app.sordina.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import java.time.LocalDate
import java.time.ZoneId

/** Quello che serve all'attrito e allo specchio serale. Solo conteggi di oggi, sul telefono. */
class Habits(private val context: Context) {
    private val sp = context.getSharedPreferences("habits", Context.MODE_PRIVATE)

    private fun todayKey() = "brake_" + LocalDate.now()

    /** Un'apertura di un'app da frenare: con un motivo o per abitudine. */
    fun logBrake(pkg: String, habit: Boolean) {
        val k = todayKey()
        val v = sp.getString(k, "").orEmpty()
        val entry = "$pkg:${if (habit) 1 else 0}"
        // Tieni solo oggi
        val e = sp.edit()
        sp.all.keys.filter { it.startsWith("brake_") && it != k }.forEach { e.remove(it) }
        e.putString(k, if (v.isEmpty()) entry else "$v|$entry")
        // Le aperture dell'ultima ora, con l'ora: l'attesa cresce se ci torni subito
        val now = System.currentTimeMillis()
        val recent = (recentOpens(pkg, now) + now).joinToString(",")
        e.putString("recent_$pkg", recent).apply()
    }

    private fun recentOpens(pkg: String, now: Long): List<Long> =
        sp.getString("recent_$pkg", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }.filter { now - it < 3_600_000 }

    /** Quante volte hai aperto comunque quest'app nell'ultima ora. */
    fun opensLastHour(pkg: String): Int = recentOpens(pkg, System.currentTimeMillis()).size

    /** (pacchetto, per abitudine) di oggi. */
    fun brakesToday(): List<Pair<String, Boolean>> =
        sp.getString(todayKey(), "").orEmpty().split('|').filter { it.contains(':') }
            .map { it.substringBefore(':') to (it.substringAfter(':') == "1") }

    fun opensToday(pkg: String) = brakesToday().count { it.first == pkg }

    data class Day(val unlocks: Int?, val minutes: Map<String, Int>)

    @Volatile private var todayCache: Pair<Long, Day>? = null
    private val appTodayCache = HashMap<String, Pair<Long, Pair<Int, Int>?>>()

    /**
     * Sblocchi e minuti in primo piano da mezzanotte (serve l'accesso all'utilizzo). Legge tutta la giornata:
     * il risultato vale qualche minuto, non si rilegge a ogni ritorno a Casa.
     */
    fun today(): Day {
        val now = System.currentTimeMillis()
        todayCache?.let { (at, d) -> if (now - at in 0 until 5 * 60_000) return d }
        return readToday().also { todayCache = now to it }
    }

    private fun readToday(): Day {
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return Day(null, emptyMap())
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        var unlocks = 0
        val fg = HashMap<String, Long>()
        val open = HashMap<String, Long>()
        val ok = runCatching {
            val ev = usm.queryEvents(start, now)
            val e = UsageEvents.Event()
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e)
                when (e.eventType) {
                    KEYGUARD_HIDDEN -> unlocks++
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> open[e.packageName] = e.timeStamp
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> open.remove(e.packageName)?.let { s ->
                        fg[e.packageName] = (fg[e.packageName] ?: 0) + (e.timeStamp - s)
                    }
                }
            }
            open.forEach { (p, s) -> fg[p] = (fg[p] ?: 0) + (now - s) }
        }.isSuccess
        if (!ok) return Day(null, emptyMap())
        return Day(if (Build.VERSION.SDK_INT >= 28) unlocks else null, fg.mapValues { (it.value / 60_000).toInt() })
    }

    /** Come [appToday], ma letto adesso (senza la copia di un minuto fa): per l'avviso di budget finito. */
    fun appTodayFresh(pkg: String): Pair<Int, Int>? = readAppToday(pkg)

    /** Aperture e minuti di oggi per una sola app (tutte le aperture, non solo da Sordina). null senza accesso all'utilizzo. */
    fun appToday(pkg: String): Pair<Int, Int>? {
        val now = System.currentTimeMillis()
        synchronized(appTodayCache) { appTodayCache[pkg]?.let { (at, v) -> if (now - at in 0 until 60_000) return v } }
        return readAppToday(pkg).also { v -> synchronized(appTodayCache) { appTodayCache.clear(); appTodayCache[pkg] = now to v } }
    }

    private fun readAppToday(pkg: String): Pair<Int, Int>? {
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        var opens = 0
        var fg = 0L
        var since: Long? = null
        var lastOpen = 0L
        return runCatching {
            val ev = usm.queryEvents(start, now)
            val e = UsageEvents.Event()
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e)
                if (e.packageName != pkg) continue
                when (e.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        // Un'apertura sola se rientri entro un minuto
                        if (e.timeStamp - lastOpen > 60_000) opens++
                        lastOpen = e.timeStamp
                        if (since == null) since = e.timeStamp
                    }
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        since?.let { fg += e.timeStamp - it }
                        since = null
                        lastOpen = e.timeStamp
                    }
                }
            }
            since?.let { fg += now - it }
            opens to (fg / 60_000).toInt()
        }.getOrNull()
    }

    // Le rinunce: ogni "Lascia stare" davanti a un'app frenata. Restano 60 giorni, solo qui.

    /** [hour]: l'ora della scelta (0..23), -1 per le voci salvate prima che si ricordasse */
    data class Skip(val day: LocalDate, val pkg: String, val minutes: Int, val hour: Int = -1)

    private fun skips(): List<Skip> = sp.getString("skips", "").orEmpty().split('|').mapNotNull { s ->
        val p = s.split(',')
        if (p.size !in 3..4) null else runCatching {
            Skip(LocalDate.parse(p[0]), p[1], p[2].toInt(), p.getOrNull(3)?.toInt() ?: -1)
        }.getOrNull()
    }

    /**
     * Vale adesso lasciare stare? 1 sì, 0 no: una volta sola per ogni tentazione. Entro [WIN_COOLDOWN] da un
     * "Lascia stare" contato (qualunque app frenata) vale 0: aprire e lasciare stare di continuo non fa imprese.
     * Dal budget le scelte non danno più minuti: contano per le imprese del freno, i minuti li dà la giornata.
     */
    fun skipGain(visit: Int = 1, now: Long = System.currentTimeMillis()): Int =
        if (rewarded(sp.getLong("lastWin", 0L), now)) visit else 0

    fun logSkip(pkg: String, minutes: Int) {
        // Una rinuncia che non vale niente (appena dopo un'altra) non entra nello storico: non conta per le imprese
        if (minutes == 0) return
        if (minutes > 0) { Tally.add("skips"); sp.edit().putLong("lastWin", System.currentTimeMillis()).apply() }
        val from = budgetFrom()
        val keep = LocalDate.now().minusDays(60)
        val (old, recent) = skips().partition { !it.day.isAfter(keep) }
        val e = sp.edit()
        // Le voci più vecchie di 60 giorni escono dall'elenco, ma i loro minuti restano nel totale (il livello non scende da solo).
        // Dal budget in poi le scelte non hanno minuti: solo il segno, per le imprese
        // Le giornate perfette che escono dall'elenco tengono il loro premio
        val led = ledgerRaw()
        val perfectGone = old.filter { !it.day.isBefore(from) }.groupBy { it.day }
            .count { (d, v) -> v.count { it.minutes > 0 } >= PERFECT_SKIPS && (led[d]?.let(::dayPoints) ?: 0) >= 0 }
        val gone = old.filter { it.day.isBefore(from) }.sumOf { it.minutes } + perfectGone * PERFECT_BONUS
        if (gone != 0) e.putInt("bank", sp.getInt("bank", 0) + gone)
        val list = recent + Skip(LocalDate.now(), pkg, minutes, java.time.LocalTime.now().hour)
        e.putString("skips", list.joinToString("|") { if (it.hour < 0) "${it.day},${it.pkg},${it.minutes}" else "${it.day},${it.pkg},${it.minutes},${it.hour}" }).apply()
    }

    /** Aperta dal freno: una voce negativa, solo per le imprese (interrompe le "Lascia stare" di fila). Non toglie minuti. */
    fun logOpen(pkg: String) = logSkip(pkg, -1)

    // ── Il budget: minuti al giorno per ogni app frenata. La giornata si giudica sul totale delle app frenate

    private val prefs get() = app.sordina.SordinaApp.of(context).prefs

    /**
     * Il giorno da cui la giornata si misura col budget (prima: col saldo delle scelte nel freno). Si fissa la prima
     * volta; da lì le giornate senza tentazioni smettono di regalare minuti al livello (li dà già il budget).
     */
    fun budgetFrom(): LocalDate {
        Tally.text("budgetFrom").takeIf { it.isNotEmpty() }?.let { s -> runCatching { LocalDate.parse(s) }.getOrNull()?.let { return it } }
        val today = LocalDate.now()
        Tally.putInt("cleanAtBudget", Tally.get("cleanDays"))
        Tally.putText("budgetFrom", today.toString())
        return today
    }

    /** Le giornate senza tentazioni che regalano minuti al livello: solo quelle di prima del budget. */
    private fun bonusDays(): Int { budgetFrom(); return minOf(Tally.get("cleanDays"), Tally.get("cleanAtBudget")) }

    /**
     * Il budget di [pkg] nel giorno [day], in minuti. Un cambio in attesa vale dal suo giorno (e da lì diventa il
     * budget di sempre). Il primo budget lo propone l'utilizzo degli ultimi 7 giorni.
     */
    fun budget(pkg: String, day: LocalDate = LocalDate.now()): Int {
        val p = prefs
        p.budgetsNext[pkg]?.let { s ->
            val m = s.substringBefore('@').toIntOrNull()
            val d = runCatching { LocalDate.parse(s.substringAfter('@')) }.getOrNull()
            if (m == null || d == null) p.budgetsNext = p.budgetsNext - pkg
            else if (!d.isAfter(LocalDate.now())) { p.budgets = p.budgets + (pkg to m); p.budgetsNext = p.budgetsNext - pkg }
            else if (!day.isBefore(d)) return m
        }
        p.budgets[pkg]?.let { return it }
        val m = suggestedBudget(pkg)
        p.budgets = p.budgets + (pkg to m)
        return m
    }

    /** Il budget in arrivo, se c'è: minuti e giorno da cui vale. */
    fun nextBudget(pkg: String): Pair<Int, LocalDate>? {
        budget(pkg)
        val s = prefs.budgetsNext[pkg] ?: return null
        val m = s.substringBefore('@').toIntOrNull() ?: return null
        val d = runCatching { LocalDate.parse(s.substringAfter('@')) }.getOrNull() ?: return null
        return m to d
    }

    /** Cambiato a mano: se scende vale subito, se sale da domani (stasera non lo allarghi per rientrarci). */
    fun setBudget(pkg: String, minutes: Int) {
        val m = minutes.coerceIn(0, MAX_BUDGET)
        val now = budget(pkg)
        val p = prefs
        if (m <= now) { p.budgets = p.budgets + (pkg to m); p.budgetsNext = p.budgetsNext - pkg }
        else p.budgetsNext = p.budgetsNext + (pkg to "$m@${LocalDate.now().plusDays(1)}")
    }

    /** Il primo budget: tre quarti dell'uso medio degli ultimi 7 giorni, a 5 minuti. Senza dati, [DEFAULT_BUDGET]. */
    private fun suggestedBudget(pkg: String): Int {
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return DEFAULT_BUDGET
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now().minusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
        val ms = runCatching { usm.queryAndAggregateUsageStats(start, end)[pkg]?.totalTimeInForeground }.getOrNull() ?: return DEFAULT_BUDGET
        return suggest(ms / 60_000.0 / 7)
    }

    /** Quanto resta oggi a [pkg] (negativo: quanto sei oltre). null senza accesso all'utilizzo. */
    fun leftToday(pkg: String): Int? = appToday(pkg)?.let { budget(pkg) - it.second }

    /** Un'app frenata in una giornata: minuti usati e budget di quel giorno. */
    data class Use(val pkg: String, val used: Int, val budget: Int)

    /** Le giornate finite col budget, app per app (60 giorni). */
    private fun ledgerRaw(): Map<LocalDate, List<Use>> = sp.getString("ledger", "").orEmpty().split('|').mapNotNull { e ->
        val d = runCatching { LocalDate.parse(e.substringBefore('=')) }.getOrNull() ?: return@mapNotNull null
        d to e.substringAfter('=', "").split(';').mapNotNull { u ->
            val p = u.split(':')
            if (p.size != 3) null else runCatching { Use(p[0], p[1].toInt(), p[2].toInt()) }.getOrNull()
        }
    }.toMap()

    /**
     * Chiude le giornate finite: una lettura dell'utilizzo per giornata, una volta al giorno (fino a 7 giorni indietro,
     * quanto tiene Android). Legge l'utilizzo: meglio non sul thread principale.
     */
    fun closeDays() {
        val today = LocalDate.now()
        if (Tally.text("ledgerAt") == today.toString()) return
        val from = budgetFrom()
        val braked = prefs.braked
        val led = ledgerRaw().toMutableMap()
        var d = maxOf(from, today.minusDays(7))
        while (d.isBefore(today)) {
            if (d !in led && braked.isNotEmpty()) readDayMinutes(d, braked)?.let { m -> led[d] = braked.sorted().map { Use(it, m[it] ?: 0, budget(it, d)) } }
            d = d.plusDays(1)
        }
        val keep = today.minusDays(60)
        val (old, recent) = led.entries.partition { !it.key.isAfter(keep) }
        val e = sp.edit()
        if (old.isNotEmpty()) e.putInt("ledgerBank", sp.getInt("ledgerBank", 0) + old.sumOf { dayPoints(it.value) })
        e.putString("ledger", recent.sortedBy { it.key }.joinToString("|") { (day, v) -> "$day=" + v.joinToString(";") { "${it.pkg}:${it.used}:${it.budget}" } }).apply()
        Tally.putText("ledgerAt", today.toString())
    }

    /** Oggi, finora: le app frenate con i minuti usati e il loro budget. null senza accesso all'utilizzo. */
    private fun todayUse(): List<Use>? {
        val braked = prefs.braked
        if (braked.isEmpty()) return emptyList()
        val m = today().minutes
        if (m.isEmpty()) return null
        return braked.sorted().map { Use(it, m[it] ?: 0, budget(it)) }
    }

    /** I punti di ogni giornata col budget, oggi compreso (finora). */
    fun ledger(): Map<LocalDate, Int> {
        val m = ledgerRaw().mapValues { (_, v) -> dayPoints(v) }.toMutableMap()
        todayUse()?.takeIf { it.isNotEmpty() }?.let { m[LocalDate.now()] = dayPoints(it) }
        return m
    }

    /** I minuti in primo piano di alcune app in una giornata intera. null se non c'è niente (senza accesso o telefono spento). */
    private fun readDayMinutes(day: LocalDate, pkgs: Set<String>): Map<String, Int>? {
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val zone = ZoneId.systemDefault()
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return runCatching {
            val ev = usm.queryEvents(start, end)
            val e = UsageEvents.Event()
            var events = 0
            val fg = HashMap<String, Long>()
            val open = HashMap<String, Long>()
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e)
                events++
                if (e.packageName !in pkgs) continue
                when (e.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> open[e.packageName] = e.timeStamp
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> open.remove(e.packageName)?.let { s -> fg[e.packageName] = (fg[e.packageName] ?: 0) + (e.timeStamp - s) }
                }
            }
            open.forEach { (p, s) -> fg[p] = (fg[p] ?: 0) + (end - s) }
            if (events == 0) null else fg.mapValues { (it.value / 60_000).toInt() }
        }.getOrNull()
    }

    /** La proposta della domenica: abbassare (o rendere realistico) il budget di un'app, da lunedì. */
    data class Proposal(val pkg: String, val from: Int, val to: Int) { val lower: Boolean get() = to < from }

    /** Solo la domenica, un'app alla volta, guardando la settimana appena passata. Legge solo il registro. */
    fun proposal(today: LocalDate = LocalDate.now()): Proposal? {
        if (today.dayOfWeek != java.time.DayOfWeek.SUNDAY) return null
        val led = ledgerRaw()
        for (pkg in prefs.braked.sorted()) {
            if (nextBudget(pkg) != null) continue
            val snooze = runCatching { LocalDate.parse(Tally.text("budgetSnooze_$pkg")) }.getOrNull()
            if (snooze != null && snooze.isAfter(today)) continue
            val week = (1L..7L).mapNotNull { led[today.minusDays(it)]?.firstOrNull { u -> u.pkg == pkg } }
            val now = budget(pkg)
            weekly(week, now)?.let { return Proposal(pkg, now, it) }
        }
        return null
    }

    /** "Sì": il nuovo budget vale da domani (lunedì). */
    fun acceptProposal(pkg: String, minutes: Int) {
        budget(pkg)
        prefs.budgetsNext = prefs.budgetsNext + (pkg to "${minutes.coerceIn(0, MAX_BUDGET)}@${LocalDate.now().plusDays(1)}")
    }

    /** "Lascia così": per due settimane non si chiede più per quest'app. */
    fun keepBudget(pkg: String) = Tally.putText("budgetSnooze_$pkg", LocalDate.now().plusDays(14).toString())

    /**
     * Il punteggio. Dal budget: ogni giornata vale fino a +20 minuti se resti nel budget (la parte che avanza) e fino a
     * −20 se lo sfori (la parte in più). Prima del budget: ogni "Lascia stare" valeva +minuti di una visita, ogni
     * "Apri comunque" li toglieva. In positivo la giornata è tua, in negativo la perdi e la serie si azzera.
     */
    data class Streak(
        /** Quante volte hai lasciato stare oggi */
        val today: Int,
        /** Il saldo di oggi, in minuti: quelli ripresi meno quelli persi */
        val balance: Int,
        /** Giornate in positivo di fila (le neutre non interrompono). Con oggi in negativo è 0 */
        val days: Int,
        /** Il saldo degli ultimi 7 giorni (anche negativo) */
        val weekMinutes: Int,
        /** Lunedì → domenica: 1 in positivo, -1 in negativo, 0 neutra o ancora da venire */
        val week: List<Int>,
        /** Il saldo di sempre: fa salire (e scendere) il livello */
        val totalMinutes: Int = 0,
        /** La serie più lunga di sempre (negli ultimi 60 giorni) */
        val best: Int = 0,
    ) {
        /** Il livello: sale con i minuti ripresi (15, 45, 90, 150… per i livelli 2, 3, 4, 5…). */
        val level: Int get() = levelOf(totalMinutes)
    }

    fun streak(): Streak {
        closeDays()
        val list = skips()
        val from = budgetFrom()
        val ledger = ledger()
        return summarize(list, LocalDate.now(), sp.getInt("bank", 0), brakeSince(list), lastLost(balances(list, ledger, from)),
            Medal.cleanBonus(bonusDays()), ledger, from, sp.getInt("ledgerBank", 0))
    }

    /** Il giorno della prima app frenata (per chi c'era già: la prima scelta nello storico). null senza app frenate. */
    private fun brakeSince(list: List<Skip>): LocalDate? {
        if (app.sordina.SordinaApp.of(context).prefs.braked.isEmpty()) return null
        Tally.text("brakeSince").takeIf { it.isNotEmpty() }?.let { return runCatching { LocalDate.parse(it) }.getOrNull() }
        val first = list.minOfOrNull { it.day } ?: LocalDate.now()
        Tally.putText("brakeSince", first.toString())
        return first
    }

    /** L'ultima giornata finita in negativo: si ricorda anche quando esce dallo storico (oggi non è ancora finita). */
    private fun lastLost(byDay: Map<LocalDate, Int>): LocalDate? {
        val today = LocalDate.now()
        val seen = byDay.filter { (d, v) -> d.isBefore(today) && v < 0 }.keys.maxOrNull()
        val stored = Tally.text("lastLost").takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (seen != null && (stored == null || seen.isAfter(stored))) { Tally.putText("lastLost", seen.toString()); return seen }
        return stored
    }

    /** Il saldo di ogni giornata, negli ultimi 60 giorni: col budget dal suo primo giorno, prima dalle scelte nel freno. */
    fun days(): Map<LocalDate, Int> = balances(skips(), ledger(), budgetFrom())

    /**
     * Le imprese vinte, con il giorno. Una volta vinta, un'impresa resta: anche quando lo storico la dimentica.
     * [fresh]: rilegge anche le giornate al telefono non ancora salvate (legge l'utilizzo: mai sul thread principale).
     */
    fun medals(fresh: Boolean = false): Map<Medal, LocalDate> = score(fresh).first

    /** Le imprese vinte e il calcolo completo (per vedere a che punto sono le altre). */
    fun score(fresh: Boolean = false): Pair<Map<Medal, LocalDate>, Medal.Score> {
        val have = storedMedals()
        closeDays()
        val score = Medal.Score(skips(), LocalDate.now(), sp.getInt("bank", 0), evidence(fresh))
        val now = score.earned - have.keys
        if (now.isEmpty()) return have to score
        val all = have + now.associateWith { LocalDate.now() }
        sp.edit().putString("medals", all.entries.joinToString(",") { "${it.key.name}:${it.value}" }).apply()
        return all to score
    }

    /** Le imprese appena vinte (e da ora ricordate). */
    fun newMedals(fresh: Boolean = false): List<Medal> {
        val before = storedMedals().keys
        return (medals(fresh).keys - before).sortedBy { it.ordinal }
    }

    /** Quello che serve alle imprese oltre allo storico del freno. */
    private fun evidence(fresh: Boolean): Medal.Evidence {
        val prefs = app.sordina.SordinaApp.of(context).prefs
        val list = skips()
        val since = brakeSince(list)
        val from = budgetFrom()
        val ledger = ledger()
        val lost = lastLost(balances(list, ledger, from))
        val today = LocalDate.now()
        // I conteggi di sempre partono da quello che c'è nello storico (per chi c'era già)
        Tally.raise("skips", list.count { it.minutes > 0 })
        Tally.raise("best", summarize(list, today, sp.getInt("bank", 0), since, lost, 0, ledger, from).best)
        var run = 0
        list.forEach { if (it.minutes > 0) { run++; Tally.raise("skipRun", run) } else if (it.minutes < 0) run = 0 }
        val installed = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime }.getOrDefault(System.currentTimeMillis())
        val rules = prefs.appLevels.values + prefs.personRules.values + prefs.channelRules.values
        val phone = phoneDays(fresh, prefs.braked, installed)
        if (fresh) { countClean(list, phone, since); Tally.checkArrival(context) }
        return Medal.Evidence(
            skipsEver = Tally.get("skips"), bestEver = Tally.get("best"), skipRunEver = Tally.get("skipRun"),
            brakeSince = since, lastLost = lost,
            cleanTotal = Tally.get("cleanDays"), cleanRun = Tally.get("cleanRun"), cleanBest = Tally.get("cleanBest"), cleanWeekend = Tally.has("cleanWeekend"),
            held = Tally.get("held"), readAllDays = maxOf(Tally.get("readAllDays"), if (Tally.has("readAll")) 1 else 0),
            focus = maxOf(Tally.get("focusCount"), if (Tally.has("focus")) 1 else 0), deep = Tally.has("deep"), quietDay = Tally.has("quietDay"),
            restNights = Tally.get("restNights"),
            important = prefs.importantNames.isNotEmpty(), rule = Level.RIEPILOGO in rules,
            braked = prefs.braked, hidden = prefs.hidden.isNotEmpty(), farewell = Tally.has("farewell"),
            carTrips = Tally.get("car"), handsFree = Tally.get("handsFree"), longDrive = Tally.has("longDrive"),
            nightDrive = Tally.has("nightDrive"), arrived = Tally.has("arrived"),
            daysWithSordina = ((System.currentTimeMillis() - installed) / 86_400_000L).toInt(),
            phone = phone,
            ledger = ledger, budgetFrom = from, ledgerBank = sp.getInt("ledgerBank", 0), bonusDays = bonusDays(),
        )
    }

    /**
     * Le giornate senza tentazioni: finite, con le app frenate (dal giorno dopo la prima), senza nessuna scelta nel
     * freno e, se c'è l'utilizzo, senza nessuna app frenata in primo piano. Si contano una volta sola, in ordine.
     */
    private fun countClean(list: List<Skip>, phone: Map<LocalDate, Medal.PhoneDay>, since: LocalDate?) {
        if (since == null) { Tally.putInt("cleanRun", 0); return }
        val today = LocalDate.now()
        val tried = list.map { it.day }.toSet()
        val done = Tally.text("cleanAt").takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        var d = listOfNotNull(done?.plusDays(1), since.plusDays(1), today.minusDays(59)).maxOrNull()!!
        if (done != null && done.plusDays(1).isBefore(d)) Tally.putInt("cleanRun", 0)
        while (d.isBefore(today)) {
            val clean = d !in tried && phone[d]?.brakedAny != true
            if (clean) {
                Tally.add("cleanDays")
                Tally.add("cleanRun")
                Tally.raise("cleanBest", Tally.get("cleanRun"))
                if (d.dayOfWeek == java.time.DayOfWeek.SUNDAY && Tally.get("cleanRun") >= 2) Tally.flag("cleanWeekend")
            } else Tally.putInt("cleanRun", 0)
            Tally.putText("cleanAt", d.toString())
            d = d.plusDays(1)
        }
    }

    // Le giornate al telefono: una lettura dell'utilizzo per giornata finita, una volta sola, e poi restano qui (60 giorni)

    private fun phoneDays(fresh: Boolean, braked: Set<String>, installed: Long): Map<LocalDate, Medal.PhoneDay> {
        val zone = ZoneId.systemDefault()
        val stored = Tally.text("phone2").split('|').mapNotNull { s ->
            val p = s.split(',')
            if (p.size != 3) null else runCatching {
                val f = p[2].toInt()
                LocalDate.parse(p[0]) to p[1].split(':').let { (m, u) ->
                    Medal.PhoneDay(m.toInt(), u.toInt(), f and 1 != 0, f and 2 != 0, f and 4 != 0, f and 8 != 0, f and 16 != 0,
                        f and 32 != 0, f and 64 != 0, f and 128 != 0, f and 256 != 0)
                }
            }.getOrNull()
        }.toMap().toMutableMap()
        val today = LocalDate.now()
        if (!fresh || Tally.text("phoneAt2") == today.toString()) return stored
        // Android tiene l'utilizzo per circa una settimana: si leggono i giorni mancanti, dopo l'installazione
        val first = maxOf(today.minusDays(7), java.time.Instant.ofEpochMilli(installed).atZone(zone).toLocalDate().plusDays(1))
        var d = first
        while (d.isBefore(today)) {
            if (d !in stored) readPhoneDay(d, braked)?.let { stored[d] = it }
            d = d.plusDays(1)
        }
        val keep = today.minusDays(60)
        Tally.putText("phone2", stored.filterKeys { it.isAfter(keep) }.entries.joinToString("|") { (day, p) ->
            val f = (if (p.late) 1 else 0) or (if (p.slowMorning) 2 else 0) or (if (p.air) 4 else 0) or (if (p.brakedMorning) 8 else 0) or (if (p.brakedLunch) 16 else 0) or
                (if (p.nightOff) 32 else 0) or (if (p.dinnerOff) 64 else 0) or (if (p.offline6h) 128 else 0) or (if (p.brakedAny) 256 else 0)
            "$day,${p.minutes}:${p.unlocks},$f"
        })
        Tally.putText("phoneAt2", today.toString())
        return stored
    }

    /** Una giornata intera dall'utilizzo di Android. null se non c'è niente (manca l'accesso o il telefono era spento). */
    private fun readPhoneDay(day: LocalDate, braked: Set<String>): Medal.PhoneDay? {
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val zone = ZoneId.systemDefault()
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        fun at(h: Int) = start + h * 3_600_000L
        return runCatching {
            val ev = usm.queryEvents(start, end)
            val e = UsageEvents.Event()
            var events = 0
            var fg = 0L
            val open = HashMap<String, Long>()
            val unlocks = ArrayList<Long>()
            var wake = 0L
            var brakedMorning = false
            var brakedLunch = false
            var brakedAny = false
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e)
                events++
                val t = e.timeStamp
                when (e.eventType) {
                    KEYGUARD_HIDDEN -> unlocks += t
                    SCREEN_INTERACTIVE -> if (wake == 0L && t >= at(4)) wake = t
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        if (e.packageName != context.packageName) open[e.packageName] = t
                        if (e.packageName in braked) {
                            brakedAny = true
                            if (t < at(10)) brakedMorning = true
                            if (t in at(12) until at(14)) brakedLunch = true
                        }
                    }
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> open.remove(e.packageName)?.let { fg += t - it }
                }
            }
            open.values.forEach { fg += end - it }
            if (events == 0) return@runCatching null
            val day8 = unlocks.filter { it in at(8)..at(22) }
            val gap = day8.zipWithNext().maxOfOrNull { (a, b) -> b - a } ?: 0L
            Medal.PhoneDay(
                minutes = (fg / 60_000).toInt(), unlocks = unlocks.size,
                late = unlocks.any { it >= at(23) },
                slowMorning = wake > 0 && unlocks.none { it > wake && it <= wake + 30 * 60_000L } && unlocks.any { it > wake },
                air = gap >= 3 * 3_600_000L, brakedMorning = brakedMorning, brakedLunch = brakedLunch,
                nightOff = unlocks.none { it < at(6) }, dinnerOff = unlocks.none { it in at(18) until at(20) },
                offline6h = gap >= 6 * 3_600_000L, brakedAny = brakedAny,
            )
        }.getOrNull()
    }

    /** Gli adesivi già vinti, con il giorno: solo una lettura, niente calcoli. */
    fun wonMedals(): Map<Medal, LocalDate> = storedMedals()

    private fun storedMedals(): Map<Medal, LocalDate> = sp.getString("medals", "").orEmpty().split(',').mapNotNull { e ->
        runCatching { Medal.valueOf(e.substringBefore(':')) to LocalDate.parse(e.substringAfter(':')) }.getOrNull()
    }.toMap()

    companion object {
        /** Dopo un "Lascia stare" premiato, per così tanto gli altri non valgono niente */
        const val WIN_COOLDOWN = 20 * 60_000L

        /** Premiato se l'ultimo premio è più vecchio di [WIN_COOLDOWN] (o nel futuro: orologio spostato indietro). */
        fun rewarded(lastWin: Long, now: Long): Boolean = now - lastWin !in 0 until WIN_COOLDOWN

        /** Il budget più alto che si può dare a un'app (4 ore) */
        const val MAX_BUDGET = 240
        /** Il budget di un'app frenata quando l'utilizzo non dice niente */
        const val DEFAULT_BUDGET = 15
        /** I minuti che una giornata può dare (o togliere) al massimo */
        const val MAX_POINTS = 20
        /** Giornata perfetta (almeno [PERFECT_SKIPS] "Lascio stare" e dentro il budget): minuti in più al livello */
        const val PERFECT_BONUS = 5
        const val PERFECT_SKIPS = 5

        /** Le giornate perfette col budget: almeno 5 "Lascio stare" e la giornata dentro il budget. */
        fun perfectDays(all: List<Skip>, byDay: Map<LocalDate, Int>, from: LocalDate?): Int =
            if (from == null) 0
            else all.filter { !it.day.isBefore(from) }.groupBy { it.day }
                .count { (d, v) -> v.count { it.minutes > 0 } >= PERFECT_SKIPS && (byDay[d] ?: 0) >= 0 }

        private fun roundUp5(m: Double): Int = (kotlin.math.ceil(m / 5) * 5).toInt()

        /** Il budget proposto per un'app usata [perDay] minuti al giorno: tre quarti, a 5 minuti, almeno 5. */
        fun suggest(perDay: Double): Int = if (perDay < 1) DEFAULT_BUDGET else roundUp5(perDay * 0.75).coerceIn(5, MAX_BUDGET)

        /**
         * I minuti di una giornata: nel budget la parte che avanza (tutto avanzato = +20), fuori la parte in più
         * (30% oltre = −6, il doppio o più = −20). Una percentuale: alzare il budget non fa più punti.
         * Con budget 0 ogni minuto conta come su un budget di 5.
         */
        fun points(used: Int, budget: Int): Int =
            if (used <= budget) { if (budget == 0) MAX_POINTS else Math.round(MAX_POINTS * (budget - used) / budget.toDouble()).toInt() }
            else -minOf(MAX_POINTS, kotlin.math.ceil(MAX_POINTS * (used - budget) / maxOf(budget, 5).toDouble()).toInt())

        /** Una giornata si giudica sul totale delle app frenate: se una sfora un po' e le altre avanzano, va bene. */
        fun dayPoints(v: List<Use>): Int = points(v.sumOf { it.used }, v.sumOf { it.budget })

        /**
         * La proposta su una settimana di un'app ([week]: le giornate registrate, ne servono almeno 5). Quasi sempre
         * sotto l'80% → il budget scende a quanto ti è bastato; sforato spesso → sale a quanto la usi davvero.
         */
        fun weekly(week: List<Use>, current: Int): Int? {
            if (week.size < 5) return null
            val avg = week.map { it.used }.average()
            if (week.count { it.used <= it.budget * 0.8 } >= 5) return maxOf(5, roundUp5(avg)).takeIf { it < current }
            if (week.count { it.used > it.budget } >= 4) return roundUp5(avg).coerceAtMost(MAX_BUDGET).takeIf { it > current }
            return null
        }

        /** Il saldo di ogni giornata: prima di [from] dalle scelte nel freno, da [from] dal budget ([ledger]). */
        fun balances(all: List<Skip>, ledger: Map<LocalDate, Int>, from: LocalDate?): Map<LocalDate, Int> {
            val old = all.filter { from == null || it.day.isBefore(from) }.groupBy { it.day }.mapValues { (_, v) -> v.sumOf { it.minutes } }
            return if (from == null) old else old + ledger.filterKeys { !it.isBefore(from) }
        }

        /** Minuti ripresi per arrivare al livello [l]. */
        fun levelStart(l: Int): Int = 15 * (l - 1) * l / 2
        fun levelOf(minutes: Int): Int { var l = 1; while (levelStart(l + 1) <= minutes) l++; return l }

        /**
         * Il punteggio da un elenco di scelte (vedi [Streak]). [bank]: i minuti delle voci già uscite dall'elenco.
         * [since]: il giorno della prima app frenata. Da lì ogni giornata non persa conta nella serie, anche senza
         * tentativi (non provarci neanche è il risultato migliore). [lastLost]: l'ultima giornata persa, anche se è già
         * uscita dall'elenco. [bonus]: i minuti regalati al livello (le giornate senza tentazioni).
         * [ledger], [from], [ledgerBank]: i punti delle giornate col budget, da quando c'è, e quelli già usciti dal registro.
         */
        fun summarize(
            all: List<Skip>, today: LocalDate, bank: Int = 0, since: LocalDate? = null, lastLost: LocalDate? = null, bonus: Int = 0,
            ledger: Map<LocalDate, Int> = emptyMap(), from: LocalDate? = null, ledgerBank: Int = 0,
        ): Streak {
            val byDay = balances(all, ledger, from)
            fun state(d: LocalDate) = Integer.signum(byDay[d] ?: 0)
            var run = 0
            var best = 0
            if (since != null) {
                val lost = listOfNotNull(byDay.filterValues { it < 0 }.keys.maxOrNull(), lastLost).maxOrNull()
                val from = listOfNotNull(since, lost?.plusDays(1)).maxOrNull()!!
                run = if (from.isAfter(today)) 0 else java.time.temporal.ChronoUnit.DAYS.between(from, today).toInt() + 1
                // La migliore negli ultimi 60 giorni (quelle prima le ricorda il contatore delle imprese)
                var d = maxOf(since, today.minusDays(60)); var cur = 0
                while (!d.isAfter(today)) { cur = if (state(d) < 0) 0 else cur + 1; best = maxOf(best, cur); d = d.plusDays(1) }
            } else if (state(today) >= 0) {
                if (state(today) > 0) run++
                val first = byDay.keys.minOrNull()
                var d = today.minusDays(1)
                while (first != null && !d.isBefore(first)) {
                    val s = state(d)
                    if (s < 0) break
                    if (s > 0) run++
                    d = d.minusDays(1)
                }
            }
            val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
            return Streak(
                best = maxOf(run, best, if (since == null) bestRun(byDay, today) else 0),
                today = all.count { it.day == today && it.minutes > 0 },
                balance = byDay[today] ?: 0,
                days = run,
                weekMinutes = byDay.filterKeys { !it.isBefore(today.minusDays(6)) && !it.isAfter(today) }.values.sum(),
                week = (0..6).map { weekStart.plusDays(it.toLong()).let { d -> if (d.isAfter(today)) 0 else state(d) } },
                totalMinutes = (bank + bonus + ledgerBank + byDay.values.sum() + PERFECT_BONUS * perfectDays(all, byDay, from)).coerceAtLeast(0),
            )
        }

        /** La serie più lunga: giornate in positivo di fila, le neutre non interrompono, una negativa sì. */
        fun bestRun(byDay: Map<LocalDate, Int>, today: LocalDate): Int {
            var d = byDay.keys.minOrNull() ?: return 0
            var run = 0; var best = 0
            while (!d.isAfter(today)) {
                val v = byDay[d] ?: 0
                if (v < 0) run = 0 else if (v > 0) run++
                best = maxOf(best, run)
                d = d.plusDays(1)
            }
            return best
        }

        private const val KEYGUARD_HIDDEN = 18
        private const val SCREEN_INTERACTIVE = 15
    }
}
