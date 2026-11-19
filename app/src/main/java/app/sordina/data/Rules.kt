package app.sordina.data

import app.sordina.service.Classifier

/** Una notifica (nella tendina o in sordina) vista come bersaglio di una scelta. */
data class RuleTarget(
    val pkg: String,
    val appLabel: String,
    /** Il canale della notifica: il suo "tipo" (es. "Websocket" di Home Assistant) */
    val channel: String?,
    val channelName: String?,
    /** Chiave normalizzata della persona o del gruppo, solo per i messaggi */
    val person: String?,
    val personTitle: String?,
    val title: String,
    val text: String = "",
    val ongoing: Boolean = false,
    val key: String? = null,
) {
    /** Il nome del tipo o della chat: "Websocket", "Gruppo Casa". */
    val kindName: String get() = personTitle ?: person ?: channelName?.takeIf { it.isNotBlank() } ?: channel.orEmpty()
}

/**
 * Le scelte possibili su una notifica, nel modello "tutto aspetta in Sordina, tranne":
 * far entrare la persona (da quell'app), far entrare tutta l'app, spegnere quel tipo o quella chat dentro un'app che entra.
 */
enum class RuleOp { PERSON_IN, APP_IN, OFF }

object Rules {
    /** Le scelte che hanno senso per questa notifica, adesso. */
    fun ops(prefs: Prefs, t: RuleTarget): List<RuleOp> = listOfNotNull(
        RuleOp.PERSON_IN.takeIf { t.person != null },
        RuleOp.APP_IN,
        RuleOp.OFF.takeIf { t.pkg in prefs.inApps && (t.person != null || !t.channel.isNullOrBlank()) },
    )

    private fun offKey(t: RuleTarget): String? = when {
        t.person != null -> Classifier.personRuleKey(t.pkg, t.person)
        !t.channel.isNullOrBlank() -> Classifier.channelKey(t.pkg, t.channel)
        else -> null
    }

    /**
     * La persona entra già ovunque (sezione Persone): "Fai entrare" vale già e da qui non si toglie
     * (toglierla da tutte le app con un tocco su una notifica sarebbe troppo).
     */
    fun everywhere(prefs: Prefs, t: RuleTarget): Boolean = t.person != null &&
        Classifier.importantKey(t.personTitle ?: t.person, prefs.importantNames, exact = t.pkg !in Classifier.CONTACT_NAMED) != null

    /** L'app è frenata: resta in sordina, "Fai entrare" si legge ma non si tocca. */
    fun braked(prefs: Prefs, t: RuleTarget): Boolean = t.pkg in prefs.braked

    fun isOn(prefs: Prefs, t: RuleTarget, op: RuleOp): Boolean = when (op) {
        RuleOp.PERSON_IN -> t.person != null && (Classifier.personRuleKey(t.pkg, t.person) in prefs.peopleIn || everywhere(prefs, t))
        RuleOp.APP_IN -> t.pkg in prefs.inApps
        RuleOp.OFF -> offKey(t)?.let { it in prefs.offChats || it in prefs.offTypes } == true
    }

    /** Accende o spegne una scelta. Toglie anche "lascia passare per qualche ora", che la contraddirebbe. */
    fun toggle(prefs: Prefs, t: RuleTarget, op: RuleOp) {
        if (op != RuleOp.OFF && braked(prefs, t)) return
        val on = !isOn(prefs, t, op)
        when (op) {
            RuleOp.PERSON_IN -> if (!everywhere(prefs, t)) t.person?.let { p ->
                val k = Classifier.personRuleKey(t.pkg, p)
                if (on) prefs.offChats = prefs.offChats - k
                prefs.peopleIn = if (on) prefs.peopleIn + k else prefs.peopleIn - k
                prefs.passUntil = prefs.passUntil - "p:$p"
            }
            RuleOp.APP_IN -> setApp(prefs, t.pkg, on)
            RuleOp.OFF -> {
                val k = offKey(t) ?: return
                if (t.person != null) {
                    if (on) prefs.peopleIn = prefs.peopleIn - k
                    prefs.offChats = if (on) prefs.offChats + k else prefs.offChats - k
                } else prefs.offTypes = if (on) prefs.offTypes + k else prefs.offTypes - k
            }
        }
    }

    /** Un'app entra o no. Togliendola si tolgono anche le sue eccezioni: rimettendola, entra tutto. */
    fun setApp(prefs: Prefs, pkg: String, on: Boolean) {
        if (on && pkg in prefs.braked) return
        prefs.inApps = if (on) prefs.inApps + pkg else prefs.inApps - pkg
        if (!on) {
            prefs.offTypes = prefs.offTypes.filterTo(HashSet()) { it.substringBefore('|') != pkg }
            prefs.offChats = prefs.offChats.filterTo(HashSet()) { it.substringBefore('|') != pkg }
        }
        prefs.passUntil = prefs.passUntil - "a:$pkg"
    }

    /** Dopo la scelta: la notifica entra (true) o aspetta in Sordina. */
    fun enters(prefs: Prefs, t: RuleTarget, op: RuleOp): Boolean = if (op == RuleOp.OFF) !isOn(prefs, t, op) else isOn(prefs, t, op)

    /**
     * I tipi messi in sordina dalla tendina (Impostazioni → Notifiche → Dalla tendina): quelli delle app che non entrano.
     * Quelli delle app che entrano sono le loro eccezioni e stanno nella pagina dell'app.
     */
    fun trayOff(prefs: Prefs): List<String> = prefs.offTypes.filter { it.substringBefore('|') !in prefs.inApps }

    /** Quante eccezioni (tipi e chat spenti) ha un'app. */
    fun exceptions(prefs: Prefs, pkg: String): Int =
        prefs.offTypes.count { it.substringBefore('|') == pkg } + prefs.offChats.count { it.substringBefore('|') == pkg }
}
