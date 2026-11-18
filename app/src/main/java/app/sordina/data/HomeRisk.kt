package app.sordina.data

/**
 * I comandi di casa che aprono o disarmano (porte, serrature, cancelli, garage, allarme) chiedono impronta o PIN.
 * Si riconoscono dalle parole ("apri il cancello") o dal nome vero dell'entità in Home Assistant: una serratura che si
 * chiama "Ingresso" fa di "apri ingresso" un comando da confermare, anche se nessuna parola lo dice.
 * Mettere in sicurezza (chiudi, blocca, inserisci l'allarme) resta immediato.
 */
object HomeRisk {
    /** Le entità che contano: serrature, allarmi, e tapparelle che in realtà sono garage, cancelli o porte */
    fun guarded(e: HaEntity): Boolean = e.domain == "lock" || e.domain == "alarm_control_panel" ||
        (e.domain == "cover" && e.deviceClass in setOf("garage", "gate", "door"))

    /** I verbi che mettono in sicurezza: non serve conferma */
    private val SECURING = setOf(
        "chiudi", "blocca", "inserisci", "arma",
        "close", "lock", "arm",
        "cierra", "bloquea", "ferme", "verrouille", "schliesse", "schliess", "verriegle", "sperre", "fecha", "tranca",
    )
    /** Parole che non dicono quale entità: articoli e preposizioni */
    private val FILLER = setOf(
        "il", "lo", "la", "i", "gli", "le", "l", "un", "una", "di", "del", "della", "dello", "dei", "delle", "a", "al", "alla", "in", "nel", "nella",
        "the", "of", "my", "el", "los", "las", "de", "les", "du", "des", "der", "die", "das", "den", "o", "os", "as", "da", "do",
    )

    private fun words(s: String) = s.split(' ').map { Search.normalize(it) }.filter { it.isNotEmpty() && it !in FILLER }

    /** La frase nomina per intero un'entità protetta, o la frase (senza il verbo) è una parte del suo nome. */
    fun namesGuarded(phrase: List<String>, names: Collection<String>): Boolean {
        val w = phrase.flatMap { words(it) }
        if (w.size < 2) return false
        val target = w.drop(1).toSet()
        return names.any { n ->
            val name = words(n).toSet()
            name.isNotEmpty() && (w.containsAll(name) || name.containsAll(target))
        }
    }

    /** I verbi per disattivare un allarme, nelle lingue dell'app */
    private val ALARM_OFF = setOf(
        "spegni", "disattiva", "disinserisci", "disarma", "togli", "stacca",
        "disarm", "disable", "deactivate",
        "apaga", "desactiva", "desarma", "eteins", "desactive", "desarme",
        "deaktiviere", "entscharfe", "desliga", "desative", "desarme",
    )
    private val ALARM_WORDS = setOf("allarme", "antifurto", "alarm", "alarma", "alarme", "alarmanlage", "antirrobo", "sirena")

    /**
     * "disattiva allarme": quale allarme salvato ([saved]: entità → nome). Col nome si sceglie ("spegni antifurto garage");
     * con la sola parola "allarme" vale se ce n'è uno, se no (null, nome) e si chiede quale. null se la frase non è questa.
     */
    fun alarmOff(phrase: List<String>, saved: Map<String, String>): Pair<String?, String>? {
        if (saved.isEmpty()) return null
        val first = phrase.firstOrNull()?.let { Search.normalize(it) } ?: return null
        if (first !in ALARM_OFF) return null
        val w = phrase.flatMap { words(it) }
        saved.entries.firstOrNull { (_, n) -> namesGuarded(phrase, listOf(n)) }?.let { return it.key to it.value }
        if (w.drop(1).none { it in ALARM_WORDS }) return null
        return if (saved.size == 1) saved.entries.first().let { it.key to it.value } else null to (w.drop(1).joinToString(" "))
    }

    /** I verbi per inserire un allarme */
    private val ALARM_ON = setOf(
        "inserisci", "attiva", "arma", "accendi", "metti",
        "arm", "activate",
        "activa", "arma", "conecta", "active", "enclenche", "aktiviere", "scharfe", "ative", "liga", "arme",
    )
    /** Le parole delle modalità, nelle lingue dell'app */
    private val MODE_WORDS = mapOf(
        "night" to setOf("notte", "notturno", "night", "noche", "nuit", "nacht", "noite"),
        "home" to setOf("casa", "home", "presenza", "hogar", "maison", "hause", "zuhause"),
        "away" to setOf("fuori", "totale", "uscita", "away", "full", "ausente", "total", "absence", "abwesend", "fora"),
        "vacation" to setOf("vacanza", "vacanze", "vacation", "holiday", "vacaciones", "vacances", "urlaub", "ferias"),
    )

    /**
     * "inserisci allarme", "attiva antifurto notte": quale allarme ([saved]: entità → nome, solo quelli che si possono
     * inserire) e in quale modalità (null: la predefinita). Entità null: più allarmi e nessun nome, si chiede quale.
     */
    fun alarmOn(phrase: List<String>, saved: Map<String, String>): Triple<String?, String, String?>? {
        if (saved.isEmpty()) return null
        val first = phrase.firstOrNull()?.let { Search.normalize(it) } ?: return null
        if (first !in ALARM_ON) return null
        val w = phrase.flatMap { words(it) }
        val named = saved.entries.firstOrNull { (_, n) -> words(n).toSet().let { it.isNotEmpty() && w.containsAll(it) } }
        if (named == null && w.drop(1).none { it in ALARM_WORDS }) return null
        // La modalità dalle parole che non fanno parte del nome ("antifurto casa notte": notte)
        val rest = w.drop(1).toSet() - (named?.value?.let { words(it).toSet() } ?: emptySet()) - ALARM_WORDS
        val mode = MODE_WORDS.entries.firstOrNull { (_, ws) -> rest.any { it in ws } }?.key
        return when {
            named != null -> Triple(named.key, named.value, mode)
            saved.size == 1 -> saved.entries.first().let { Triple(it.key, it.value, mode) }
            else -> Triple(null, w.drop(1).joinToString(" "), mode)
        }
    }

    /** Da confermare con impronta o PIN: parole sensibili o entità protetta, e non un verbo che mette in sicurezza. */
    fun needsAuth(phrase: List<String>, guardedNames: Collection<String>, sensitive: Boolean): Boolean {
        val first = phrase.firstOrNull()?.let { Search.normalize(it) } ?: return false
        if (first in SECURING) return false
        return sensitive || namesGuarded(phrase, guardedNames)
    }
}
