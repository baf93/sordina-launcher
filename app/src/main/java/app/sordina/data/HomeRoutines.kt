package app.sordina.data

/**
 * Le routine di Home Assistant (scene e script) che hai abilitato in Sordina: si lanciano per nome ("buonanotte",
 * "esco"), con l'impronta se l'hai chiesta, e possono disattivare prima un allarme salvato ("Entra" + centrale).
 */
object HomeRoutines {
    val DOMAINS = setOf("scene", "script")

    /** Le parole che stanno davanti al nome e non lo cambiano: "attiva la scena buonanotte", "lancia routine esco" */
    private val LEAD = setOf(
        "attiva", "lancia", "avvia", "esegui", "fai", "metti", "scena", "routine", "modalita", "script",
        "run", "start", "activate", "scene", "mode",
        "activa", "ejecuta", "lanza", "escena", "modo", "lance", "active", "scene", "starte", "aktiviere", "szene", "modus", "ative", "executa", "cena",
    )
    private val FILLER = setOf("la", "il", "lo", "le", "l", "the", "my", "el", "de", "di", "della", "del", "die", "der", "das", "a", "o")

    private fun words(s: String) = s.split(' ').map { Search.normalize(it) }.filter { it.isNotEmpty() && it !in FILLER }

    /** Quale routine abilitata nomina la frase ([enabled]: entità → nome): il nome intero, niente di più ("Scena Esco" vale "esco"). */
    fun match(phrase: List<String>, enabled: Map<String, String>, phrases: Map<String, List<String>> = emptyMap()): String? {
        val all = phrase.flatMap { words(it) }
        if (all.isEmpty()) return null
        // Le tue frasi valgono così come le hai scritte ("sto entrando")
        phrases.entries.firstOrNull { (_, fs) -> fs.any { f -> words(f).let { it.isNotEmpty() && it == all } } }?.let { return it.key }
        val w = all.dropWhile { it in LEAD }
        if (w.isEmpty()) return null
        return enabled.entries.firstOrNull { (_, n) -> words(n).dropWhile { it in LEAD }.let { it.isNotEmpty() && it == w } }?.key
    }

    /** Le frasi scritte in Impostazioni: una per riga (o separate da virgole), al massimo dieci */
    fun parsePhrases(text: String): List<String> =
        text.split('\n', ',', ';').map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(10)
}
