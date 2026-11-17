package app.sordina.data

import app.sordina.R
import app.sordina.service.Classifier
import app.sordina.tr

/** A cosa vale una regola: un tipo di notifica (il canale dell'app), una persona o un gruppo, tutta l'app. */
enum class RuleScope(private val labelRes: Int) {
    TYPE(R.string.rule_scope_type),
    PERSON(R.string.rule_scope_person),
    APP(R.string.rule_scope_app),
    ;
    val label: String get() = tr(labelRes)
}

/** Una notifica (nella tendina o in sordina) vista come bersaglio di una regola. */
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
    /** Le portate possibili, dalla più stretta alla più larga. */
    val scopes: List<RuleScope>
        get() = listOfNotNull(RuleScope.TYPE.takeIf { channel != null }, RuleScope.PERSON.takeIf { person != null }, RuleScope.APP)

    /** Il nome da mostrare per una portata: "Websocket", "Anna", "Home Assistant". */
    fun name(s: RuleScope): String = when (s) {
        RuleScope.TYPE -> channelName?.takeIf { it.isNotBlank() } ?: channel.orEmpty()
        RuleScope.PERSON -> personTitle ?: person.orEmpty()
        RuleScope.APP -> appLabel
    }
}

/** Le regole scelte da te: una sola per bersaglio, la più stretta vince (vedi [Classifier.classify]). */
object Rules {
    fun get(prefs: Prefs, t: RuleTarget, s: RuleScope): Level? = when (s) {
        RuleScope.TYPE -> prefs.channelRules[Classifier.channelKey(t.pkg, t.channel)]
        RuleScope.PERSON -> t.person?.let { prefs.personRules[Classifier.personRuleKey(t.pkg, it)] ?: prefs.personRules[it] }
        RuleScope.APP -> prefs.appLevels[t.pkg]
    }

    /** La portata più stretta che ha già una regola, oppure quella proposta. */
    fun current(prefs: Prefs, t: RuleTarget): RuleScope =
        t.scopes.firstOrNull { get(prefs, t, it) != null }
            ?: if (t.person != null) RuleScope.PERSON else t.scopes.first()

    /** [level] null toglie la regola. Toglie anche "lascia passare per qualche ora", che la contraddirebbe. */
    fun set(prefs: Prefs, t: RuleTarget, s: RuleScope, level: Level?) {
        when (s) {
            RuleScope.TYPE -> {
                val k = Classifier.channelKey(t.pkg, t.channel)
                prefs.channelRules = if (level == null) prefs.channelRules - k else prefs.channelRules + (k to level)
                if (level != null) prefs.ruleNames = prefs.ruleNames + (k to t.appLabel + " · " + t.name(s))
            }
            RuleScope.PERSON -> t.person?.let { p ->
                // Una persona o un gruppo dentro questa app: "Famiglia" su WhatsApp non è "Famiglia" su Telegram.
                // La regola vecchia (solo il nome, valida per tutte le app) si toglie: da qui in poi decide quella nuova
                val k = Classifier.personRuleKey(t.pkg, p)
                prefs.personRules = (prefs.personRules - p - k).let { if (level == null) it else it + (k to level) }
                if (level != null) prefs.ruleNames = prefs.ruleNames + (k to t.appLabel + " · " + t.name(s))
                prefs.passUntil = prefs.passUntil - "p:$p"
            }
            RuleScope.APP -> {
                prefs.setAppLevel(t.pkg, level)
                prefs.passUntil = prefs.passUntil - "a:${t.pkg}"
            }
        }
    }

    /** Una regola salvata, per l'elenco in Impostazioni. */
    data class Saved(val scope: RuleScope, val key: String, val pkg: String?, val level: Level)

    fun all(prefs: Prefs): List<Saved> =
        prefs.channelRules.map { (k, v) -> Saved(RuleScope.TYPE, k, k.substringBefore('|'), v) } +
            prefs.personRules.map { (k, v) -> Saved(RuleScope.PERSON, k, k.substringBefore('|').takeIf { '|' in k }, v) } +
            prefs.appLevels.map { (k, v) -> Saved(RuleScope.APP, k, k, v) }

    fun remove(prefs: Prefs, r: Saved) = when (r.scope) {
        RuleScope.TYPE -> prefs.channelRules = prefs.channelRules - r.key
        RuleScope.PERSON -> prefs.personRules = prefs.personRules - r.key
        RuleScope.APP -> prefs.setAppLevel(r.key, null)
    }
}
