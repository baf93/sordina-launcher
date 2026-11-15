package app.sordina.data

/**
 * Quando una frase è per casa, anche senza il comando giusto all'inizio. Assist di Home Assistant capisce già stanze,
 * dispositivi, scene, liste e (se ci hai messo un'intelligenza) qualunque domanda: qui si decide solo quanto è
 * probabile che la frase sia per lui, così Assist sta sopra il web quando serve.
 *
 * Cosa conta, dal più forte:
 * - una lista ("aggiungi il latte alla spesa", "cosa c'è nella lista"): 92
 * - un comando o una domanda su una cosa di casa ("abbassa le tapparelle", "che luci sono accese",
 *   "quanti gradi ci sono in salotto", "il robot ha finito?"): 90
 * - una routine di casa ("buonanotte", "sto uscendo", "modalità film"): 84
 * - una cosa di casa nominata e basta ("luci cucina"): 76
 * Le altre frasi no: Assist senza un'intelligenza dietro risponde "non ho capito", meglio il web.
 *
 * Le cose di casa sono quelle comuni più i nomi che Sordina impara: le parole delle frasi che hai mandato ad Assist
 * (il nome della tua lampada, della tua stanza) e, col token, i nomi delle entità e delle aree. Solo parole, niente rete.
 */
object HomeTalk {
    /** Le cose di casa, già normalizzate (minuscole, senza accenti), singolari e plurali. */
    private val THINGS = setOf(
        "luce", "luci", "lampada", "lampade", "lampadina", "lampadine", "lampadario", "faretti", "faretto", "led", "striscia", "abat",
        "tapparella", "tapparelle", "tenda", "tende", "persiana", "persiane", "serranda", "serrande", "veneziane", "scuri",
        "termostato", "termostati", "riscaldamento", "caldaia", "termosifoni", "termosifone", "valvole", "pavimento",
        "condizionatore", "condizionatori", "clima", "climatizzatore", "deumidificatore", "ventilatore", "ventilatori", "stufa",
        "temperatura", "temperature", "gradi", "umidita", "aria", "co2",
        "porta", "porte", "portone", "cancello", "cancelletto", "garage", "box", "finestra", "finestre", "serratura", "lucchetto",
        "allarme", "antifurto", "telecamera", "telecamere", "campanello", "citofono", "sensore", "sensori", "fumo", "allagamento",
        "aspirapolvere", "robot", "lavatrice", "lavastoviglie", "asciugatrice", "forno", "frigo", "frigorifero", "boiler", "scaldabagno",
        "presa", "prese", "irrigazione", "irrigatore", "prato", "piscina", "fotovoltaico", "pannelli", "inverter", "consumi", "consumo",
        "energia", "corrente", "watt", "kwh", "bolletta",
        "tv", "televisione", "televisore", "chromecast", "soundbar", "stereo", "casse", "speaker",
        "cucina", "salotto", "soggiorno", "sala", "camera", "camere", "camerette", "cameretta", "bagno", "bagni", "studio", "corridoio",
        "ingresso", "giardino", "terrazzo", "terrazza", "balcone", "cantina", "taverna", "mansarda", "lavanderia", "scale", "esterno",
        "piano", "sopra", "sotto",
        // In inglese
        "light", "lights", "lamp", "lamps", "bulb", "bulbs", "strip", "blind", "blinds", "shutter", "shutters", "curtain", "curtains",
        "thermostat", "heating", "heater", "boiler", "radiator", "radiators", "ac", "aircon", "fan", "fans", "temperature", "degrees", "humidity",
        "door", "doors", "gate", "window", "windows", "lock", "alarm", "camera", "cameras", "doorbell", "sensor", "sensors", "smoke",
        "vacuum", "washer", "dishwasher", "dryer", "oven", "fridge", "plug", "socket", "sprinkler", "sprinklers", "pool", "solar",
        "power", "energy", "television", "speakers",
        "kitchen", "living", "lounge", "bedroom", "bedrooms", "bathroom", "office", "hallway", "hall", "entrance", "garden", "patio",
        "balcony", "basement", "attic", "laundry", "stairs", "outside", "upstairs", "downstairs",
    )

    /** I comandi su qualcosa di casa (quelli che iniziano la frase li conosce già Sordina, questi possono stare ovunque). */
    private val DO = setOf(
        "accendi", "spegni", "attiva", "disattiva", "apri", "chiudi", "alza", "abbassa", "imposta", "regola", "metti", "stacca",
        "aumenta", "diminuisci", "riduci", "alzare", "abbassare", "ferma", "fermare", "avvia", "avviare", "fai", "partire",
        "blocca", "sblocca", "inserisci", "disinserisci", "arma", "disarma", "pulisci", "aspira", "lava", "accendere", "spegnere",
        "aprire", "chiudere", "attivare", "disattivare", "scalda", "rinfresca", "raffredda", "dimmi", "controlla",
        "turn", "switch", "open", "close", "raise", "lower", "set", "dim", "brighten", "increase", "decrease", "start", "stop",
        "lock", "unlock", "arm", "disarm", "clean", "heat", "cool", "check", "tell",
    )

    /** Domande e stati: con una cosa di casa, la frase è per Assist. */
    private val ASK = setOf(
        "che", "quali", "quale", "quanti", "quante", "quanto", "come", "dove", "cosa", "chi", "quando", "perche", "stato",
        "acceso", "accesa", "accesi", "accese", "spento", "spenta", "spenti", "spente",
        "aperto", "aperta", "aperti", "aperte", "chiuso", "chiusa", "chiusi", "chiuse",
        "attivo", "attiva", "attivi", "attive", "inserito", "inserita", "finito", "finita", "pronto", "pronta",
        "sono", "ci", "c", "e", "ha", "hanno", "fa", "consuma", "consumato", "prodotto", "produce",
        "what", "which", "how", "many", "much", "where", "is", "are", "on", "off", "open", "closed", "locked", "unlocked", "running",
        "done", "finished", "status", "using",
    )

    /** Le liste di Home Assistant: spesa e cose da fare. */
    private val LIST = setOf("lista", "spesa", "liste", "todo", "list", "shopping", "groceries")
    private val LIST_DO = setOf("aggiungi", "aggiungere", "metti", "togli", "rimuovi", "cancella", "segna", "cosa", "che", "leggi", "dimmi", "add", "remove", "delete", "what", "read")

    /** Le routine di casa: frasi intere (normalizzate) o parole che le annunciano. */
    private val ROUTINES = setOf(
        "buonanotte", "buona notte", "buongiorno", "sto uscendo", "esco", "sono uscito", "sono uscita", "usciamo", "sono a casa",
        "sono tornato", "sono tornata", "sto arrivando", "arrivo a casa", "vado a letto", "vado a dormire", "sveglia casa",
        "good night", "goodnight", "good morning", "i m leaving", "leaving home", "i m home", "i m back", "going to bed", "bedtime",
    )
    private val MODES = setOf("modalita", "scena", "scenario", "routine", "automazione", "script", "mode", "scene", "automation")

    /** Parole che non dicono niente da imparare. */
    private val STOP = ASK + DO + LIST + LIST_DO + MODES + setOf(
        "della", "dello", "delle", "degli", "nella", "nello", "nelle", "negli", "sulla", "sullo", "sulle", "sugli", "alla",
        "allo", "alle", "agli", "dalla", "dalle", "questa", "questo", "quella", "quello", "tutte", "tutti", "tutto", "tutta",
        "anche", "ancora", "adesso", "subito", "casa", "percento", "cento", "minuti", "favore", "puoi", "potresti", "vorrei",
        "voglio", "grazie", "sempre", "fino", "dopo", "prima", "oggi", "domani", "stasera", "stanotte", "mattina", "sera",
        "the", "in", "of", "to", "please", "now", "all", "my", "a", "an", "and", "at", "it", "home", "percent", "minutes", "today", "tomorrow", "tonight",
    )

    /**
     * Quanto [nw] (parole normalizzate) è una frase per Assist, o null se non lo è affatto.
     * [learned]: le parole di casa imparate (nomi tuoi e, col token, delle entità).
     */
    fun score(nw: List<String>, learned: Set<String>): Int? {
        val words = nw.filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        val phrase = words.joinToString(" ")
        if (words.any { it in LIST } && words.any { it in LIST_DO }) return 92
        val thing = words.any { it in THINGS || it in learned }
        if (thing && words.size >= 2 && words.any { it in DO || it in ASK }) return 90
        if (phrase in ROUTINES || ROUTINES.any { phrase.startsWith("$it ") } || (words.size >= 2 && words.first() in MODES)) return 84
        if (thing && words.size >= 2) return 76
        return null
    }

    /** I canali TV: "metti Rai 1", "guarda il 5", "metti canale 20" vanno ad Assist (la TV la conosce casa). */
    private val CHANNELS = setOf(
        "rai", "rai 1", "rai 2", "rai 3", "rai 4", "rai 5", "rai uno", "rai due", "rai tre", "rai news", "rai news 24", "rainews",
        "rai sport", "rai movie", "rai premium", "rai storia", "rai scuola", "rai yoyo", "rai gulp", "rai italia", "raiplay",
        "rete 4", "rete quattro", "rete4", "canale 5", "canale cinque", "canale5", "italia 1", "italia uno", "italia1", "italia 2",
        "la7", "la 7", "la sette", "la7d", "tv8", "tv 8", "nove", "il nove", "20", "venti", "mediaset 20", "iris", "la5", "la 5",
        "focus", "cielo", "real time", "dmax", "giallo", "top crime", "boing", "k2", "frisbee", "cartoonito", "mediaset extra",
        "cine34", "cine 34", "twentyseven", "27", "spike", "hgtv", "food network", "motor trend", "warner tv", "tv2000", "tv 2000",
        "sky", "sky tg24", "sky tg 24", "tg24", "sky sport", "sky sport uno", "sky sport 24", "sky cinema", "sky uno", "sky arte",
        "euronews", "mtv", "super", "dazn", "eurosport", "tg1", "tg 1", "tg2", "tg3", "tg5", "tg 5", "telegiornale",
        // Nel mondo: le principali, scritte come si dicono
        "bbc", "bbc one", "bbc 1", "bbc two", "bbc 2", "bbc three", "bbc four", "bbc news", "bbc world", "cbbc", "cbeebies",
        "itv", "itv1", "itv 1", "itv2", "channel 4", "channel 5", "e4", "film4", "more4", "dave", "sky news", "sky atlantic", "sky one",
        "cnn", "cnn international", "fox", "fox news", "msnbc", "cnbc", "abc", "nbc", "cbs", "pbs", "espn", "espn 2", "hbo", "showtime",
        "comedy central", "cartoon network", "disney", "disney channel", "disney junior", "nickelodeon", "nick jr", "discovery",
        "discovery channel", "national geographic", "nat geo", "history", "history channel", "animal planet", "tlc", "amc", "fx",
        "bloomberg", "al jazeera", "aljazeera", "france 24", "dw", "deutsche welle", "rt", "nhk", "nhk world", "cgtn", "trt world",
        "tf1", "france 2", "france 3", "france 4", "france 5", "france info", "franceinfo", "m6", "arte", "canal plus", "canal+",
        "c8", "w9", "tmc", "bfm", "bfmtv", "lci", "cnews", "gulli",
        "ard", "das erste", "zdf", "rtl", "rtl2", "rtl zwei", "sat 1", "sat1", "pro7", "prosieben", "vox", "kabel eins", "kabel 1",
        "n tv", "ntv", "welt", "3sat", "kika", "super rtl", "orf", "orf 1", "orf 2", "srf", "srf 1", "srf zwei",
        "la 1", "la1", "la 2", "la2", "tve", "antena 3", "antena3", "telecinco", "la sexta", "lasexta", "cuatro", "tv3", "24h",
        "rtp", "rtp1", "sic", "tvi", "globo", "tv globo", "sbt", "record", "band", "televisa", "las estrellas", "azteca",
        "npo", "npo 1", "rtl 4", "een", "vtm", "rtbf", "la une", "dr1", "nrk", "nrk1", "svt", "svt1", "yle", "tvp", "tvp1", "polsat",
        "ert", "rai italia", "telemundo", "univision",
        // Dopo "la", "le", "el" (che si saltano): La Sexta, La Une, La Sette
        "sexta", "une", "sette", "sesta",
    )
    private val CHANNEL_WORDS = setOf("canale", "channel", "chaine", "canal", "kanal", "sender")
    private val TV_DO = setOf(
        "metti", "guarda", "cambia", "passa", "vai", "sintonizza", "accendi",
        "put", "watch", "switch", "change", "turn", "tune", "mets", "regarde", "passe", "allume", "pon", "ponme", "cambia", "sintoniza",
        "schalte", "schalt", "zeig", "zeige", "mach", "coloca", "liga", "zet", "kijk",
    )
    private val TV_FILL = setOf(
        "il", "lo", "la", "su", "sul", "a", "al", "canale", "programma", "tv", "televisione", "in",
        "on", "to", "the", "channel", "over", "sur", "le", "les", "chaine", "en", "el", "canal", "auf", "den", "das", "die", "kanal", "sender", "op",
    )

    /** "metti Rai 1", "guarda il 5", "passa su canale 20", "metti la7 in salotto": è un canale TV (Assist), non musica. */
    fun channel(nw: List<String>, learned: Set<String>): Boolean {
        val words = nw.filter { it.isNotEmpty() }
        if (words.size < 2 || words.first() !in TV_DO) return false
        val rest = words.drop(1).dropWhile { it in TV_FILL }
        // "in salotto", "sulla tv di sopra": la stanza dopo il canale non conta
        val what = rest.takeWhile { it !in setOf("in", "sulla", "sul", "nella", "nel", "di", "dans", "im", "auf") }.joinToString(" ")
        if (what.isEmpty()) return words.any { it in CHANNEL_WORDS }
        return what in CHANNELS || what.toIntOrNull()?.let { it in 1..999 } == true ||
            (words.any { it in CHANNEL_WORDS } && what.isNotEmpty()) || what in learned
    }

    /** Le parole da ricordare di una frase mandata ad Assist: i nomi (dispositivi, stanze), non i verbi né i "che". */
    fun learn(text: String): Set<String> = Search.normalize(text).split(' ')
        .filter { it.length >= 4 && it !in STOP && it !in THINGS && it.none(Char::isDigit) }.toSet()
}
