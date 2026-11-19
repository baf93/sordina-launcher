package app.sordina.data

/**
 * Spagnolo, francese, tedesco e portoghese riportati alle forme che il motore capisce già, come fa
 * [DateParser.english] per l'inglese. Due parti:
 * - [dates]: date, ore e durate ("mañana a las 5" → "domani alle 5", "dans 10 minutes" → "tra 10 minuti");
 * - [command]: il verbo all'inizio ("llama a Elisa" → "chiama Elisa", "ruf Elisa an" → "chiama Elisa");
 *   il resto (il testo di un messaggio, il nome) resta com'è, maiuscole comprese.
 * I comandi per casa non si traducono: Assist di Home Assistant parla già la tua lingua.
 *
 * Solo forme che non si confondono con l'italiano: "a" da sola non diventa "alle", le ordinali portoghesi
 * ("quinta") sono un giorno solo con "feira" o "na", i mesi solo accanto a un numero.
 * Le regole si compilano una volta sola: per ogni lettera scritta è solo qualche confronto su una frase corta.
 */
object Lingue {

    private class Rule(val re: Regex, val to: (MatchResult) -> String)
    private fun rule(p: String, to: String) = Rule(Regex(p)) { m ->
        var out = to
        for (i in m.groupValues.indices.reversed()) out = out.replace("$$i", m.groupValues[i])
        out
    }
    private fun rule(p: String, to: (MatchResult) -> String) = Rule(Regex(p), to)

    /** La ı turca e la ł polacca non hanno accento da togliere: diventano i e l, così le regole valgono comunque si scriva */
    fun flat(s: String) = s.replace('ı', 'i').replace('ł', 'l')

    // ── Date, ore, durate

    /** Testo già pulito ([DateParser.clean]: minuscolo, senza accenti, apostrofi come spazi). */
    fun dates(s0: String): String {
        var s = " ${flat(LingueAltre.dates(flat(s0)))} "
        for (r in DATE_RULES) s = r.re.replace(s) { r.to(it) }
        return s.replace(Regex("\\s+"), " ").trim()
    }

    private val UNIT = "(minutos?|minutes?|minuten|mn|horas?|heures?|stunden?|segundos?|secondes?|sekunden?|dias?|jours?|tagen?|semanas?|semaines?|wochen?" +
        "|minuut|uur|uren|seconden?|dagen|weken|minuty?|godzin[aey]?|sekundy?|dni|tygodni[ea]?|минут[уы]?|час(а|ов)?|секунд[уы]?|дн(я|еи)|недел[юиь]|dakika|dakikalik|saat|saniye|gun|hafta)"

    private val NUMBERS = listOf(
        "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5, "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9, "diez" to 10,
        "once" to 11, "doce" to 12, "quince" to 15, "veinte" to 20, "treinta" to 30, "cuarenta" to 40, "cincuenta" to 50,
        "deux" to 2, "trois" to 3, "quatre" to 4, "cinq" to 5, "six" to 6, "sept" to 7, "huit" to 8, "neuf" to 9, "dix" to 10,
        "douze" to 12, "quinze" to 15, "vingt" to 20, "trente" to 30, "quarante" to 40, "cinquante" to 50,
        "zwei" to 2, "drei" to 3, "vier" to 4, "funf" to 5, "sechs" to 6, "sieben" to 7, "acht" to 8, "neun" to 9, "zehn" to 10,
        "zwolf" to 12, "funfzehn" to 15, "zwanzig" to 20, "dreissig" to 30, "vierzig" to 40, "funfzig" to 50,
        "dois" to 2, "duas" to 2, "quatro" to 4, "sete" to 7, "oito" to 8, "dez" to 10, "doze" to 12, "vinte" to 20, "trinta" to 30,
        "quarenta" to 40,
        "twee" to 2, "drie" to 3, "vier" to 4, "vijf" to 5, "zes" to 6, "zeven" to 7, "negen" to 9, "tien" to 10, "twaalf" to 12,
        "vijftien" to 15, "twintig" to 20, "dertig" to 30, "veertig" to 40, "vijftig" to 50,
        "dwie" to 2, "trzy" to 3, "cztery" to 4, "piec" to 5, "szesc" to 6, "siedem" to 7, "osiem" to 8, "dziewiec" to 9, "dziesiec" to 10,
        "pietnascie" to 15, "dwadziescia" to 20, "trzydziesci" to 30, "czterdziesci" to 40, "piecdziesiat" to 50,
        "два" to 2, "две" to 2, "три" to 3, "четыре" to 4, "пять" to 5, "шесть" to 6, "семь" to 7, "восемь" to 8, "девять" to 9,
        "десять" to 10, "пятнадцать" to 15, "двадцать" to 20, "тридцать" to 30, "сорок" to 40, "пятьдесят" to 50,
        "iki" to 2, "uc" to 3, "dort" to 4, "bes" to 5, "alti" to 6, "yedi" to 7, "sekiz" to 8, "dokuz" to 9, "on" to 10,
        "onbes" to 15, "yirmi" to 20, "otuz" to 30, "kirk" to 40, "elli" to 50,
    )

    /** Giorni della settimana: parola → italiano. Il portoghese dal lunedì al venerdì a parte, vedi sotto. */
    private val DAYS = listOf(
        "lunes" to "lunedi", "martes" to "martedi", "miercoles" to "mercoledi", "jueves" to "giovedi", "viernes" to "venerdi",
        "lundi" to "lunedi", "mardi" to "martedi", "mercredi" to "mercoledi", "jeudi" to "giovedi", "vendredi" to "venerdi",
        "samedi" to "sabato", "dimanche" to "domenica",
        "montag" to "lunedi", "dienstag" to "martedi", "mittwoch" to "mercoledi", "donnerstag" to "giovedi", "freitag" to "venerdi",
        "samstag" to "sabato", "sonnabend" to "sabato", "sonntag" to "domenica",
        "sabado" to "sabato", "domingo" to "domenica",
        "maandag" to "lunedi", "dinsdag" to "martedi", "woensdag" to "mercoledi", "donderdag" to "giovedi", "vrijdag" to "venerdi",
        "zaterdag" to "sabato", "zondag" to "domenica",
        "poniedzialek" to "lunedi", "wtorek" to "martedi", "sroda" to "mercoledi", "srode" to "mercoledi", "czwartek" to "giovedi",
        "piatek" to "venerdi", "sobota" to "sabato", "sobote" to "sabato", "niedziela" to "domenica", "niedziele" to "domenica",
        "понедельник" to "lunedi", "вторник" to "martedi", "среда" to "mercoledi", "среду" to "mercoledi", "четверг" to "giovedi",
        "пятница" to "venerdi", "пятницу" to "venerdi", "суббота" to "sabato", "субботу" to "sabato", "воскресенье" to "domenica",
        "pazartesi" to "lunedi", "sali" to "martedi", "carsamba" to "mercoledi", "persembe" to "giovedi", "cuma" to "venerdi",
        "cumartesi" to "sabato", "pazar" to "domenica",
    )
    private val PT_DAYS = listOf("segunda" to "lunedi", "terca" to "martedi", "quarta" to "mercoledi", "quinta" to "giovedi", "sexta" to "venerdi")
    private val NEXT = Regex("proxim|siguiente|que viene|que vem|prochain|nachsten|kommenden|volgende|aanstaande|przysz|nastepn|следующ|ближаиш|gelecek|onumuzdeki|haftaya")

    private val IT_MONTHS = listOf("gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno", "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre")
    private val MONTHS = listOf(
        listOf("enero", "janvier", "januar", "janeiro", "januari", "stycznia", "января", "ocak"),
        listOf("febrero", "fevrier", "februar", "fevereiro", "februari", "lutego", "февраля", "subat"),
        listOf("marzo", "mars", "marz", "maart", "marca", "марта", "mart"), listOf("abril", "avril", "april", "kwietnia", "апреля", "nisan"),
        listOf("mayo", "mai", "maio", "mei", "maja", "мая", "mayis"),
        listOf("junio", "juin", "juni", "junho", "czerwca", "июня", "haziran"), listOf("julio", "juillet", "juli", "julho", "lipca", "июля", "temmuz"),
        listOf("agosto", "aout", "august", "augustus", "sierpnia", "августа", "agustos"),
        listOf("septiembre", "setiembre", "septembre", "september", "setembro", "wrzesnia", "сентября", "eylul"),
        listOf("octubre", "octobre", "oktober", "outubro", "pazdziernika", "октября", "ekim"),
        listOf("noviembre", "novembre", "november", "novembro", "listopada", "ноября", "kasim"),
        listOf("diciembre", "decembre", "dezember", "dezembro", "december", "grudnia", "декабря", "aralik"),
    )
    private val MONTH_OF = MONTHS.flatMapIndexed { i, ws -> ws.map { it to IT_MONTHS[i] } }.toMap()

    /** "alle 7", "alle 7:30", più "e mezza" e simili se c'erano. */
    private fun at(h: String, m: String?, frac: String? = null): String {
        val min = m?.takeIf { it.isNotEmpty() }?.let { ":$it" } ?: ""
        val f = when (frac?.trim()) {
            "y media", "et demie", "e meia" -> " e mezza"
            "y cuarto", "et quart", "e quinze" -> " e un quarto"
            "menos cuarto", "moins le quart", "moins quart" -> " meno un quarto"
            else -> ""
        }
        return " alle $h$min$f "
    }

    /** "в 7 вечера": la parte del giorno dopo l'ora */
    private fun partRu(p: String) = when (p.trim()) { "утра" -> " di mattina"; "вечера" -> " di sera"; "дня" -> " del pomeriggio"; "ночи" -> " di notte"; else -> "" }

    private val DATE_RULES: List<Rule> = buildList {
        // Prima delle durate: "à 5 heures", "às 5 horas" sono un'ora, non cinque ore
        add(rule(" (a|vers|pour|jusqu a|jusqu|avant|des) (\\d{1,2}) ?(?:h|heures?)(?: ?(\\d{2}))?( et demie| et quart| moins le quart| moins quart)? ") { m -> at(m.groupValues[2], m.groupValues[3], m.groupValues[4]) })
        add(rule(" (as|ate as|pelas|para as|antes das) (\\d{1,2})(?:[:h.](\\d{2}))?( horas| h)?( e meia| e quinze)? ") { m -> at(m.groupValues[2], m.groupValues[3], m.groupValues[5]) })
        // Olandese, polacco, russo, turco: l'ora prima delle durate ("om 7 uur" non sono sette ore)
        val monthsLook = MONTH_OF.keys.sortedByDescending { it.length }.joinToString("|")
        add(rule(" (om|rond|tegen) (\\d{1,2})(?:[:.](\\d{2}))?( uur)? ") { m -> at(m.groupValues[2], m.groupValues[3]) })
        add(rule(" o (?:godzinie )?(\\d{1,2})(?:[:.](\\d{2}))? (?!($monthsLook) )") { m -> at(m.groupValues[1], m.groupValues[2]) })
        add(rule(" na (?:godzine )?(\\d{1,2})(?:[:.](\\d{2}))? (?!(minut|godzin|sekund|dni|tygodni)\\S* |($monthsLook) )") { m -> at(m.groupValues[1], m.groupValues[2]) })
        add(rule(" в (\\d{1,2})(?:[:.](\\d{2}))?(?: часов| часа| час)?( утра| вечера| дня| ночи)? (?!(минут|секунд)\\S* |($monthsLook) )") { m -> at(m.groupValues[1], m.groupValues[2]).trimEnd() + partRu(m.groupValues[3]) + " " })
        add(rule(" (на|к) (\\d{1,2})(?:[:.](\\d{2}))?( утра| вечера| дня| ночи)? (?!(минут|час|секунд|дн|недел)\\S* |($monthsLook) )") { m -> at(m.groupValues[2], m.groupValues[3]).trimEnd() + partRu(m.groupValues[4]) + " " })
        add(rule(" saat (\\d{1,2})(?:[:.](\\d{2}))?(?: de| da| te| ta| ye| ya| e| a)? ") { m -> at(m.groupValues[1], m.groupValues[2]) })
        add(rule(" (\\d{1,2})[:.](\\d{2}) (de|da|te|ta|ye|ya) ") { m -> at(m.groupValues[1], m.groupValues[2]) })
        add(rule(" (\\d+) dakikalik ", " $1 dakika "))
        // Numeri a parole davanti a una durata; "un/une/ein/um" come "un"
        for ((w, n) in NUMBERS.sortedByDescending { it.first.length }) add(rule(" $w (?=$UNIT )", " $n "))
        add(rule(" (una|une|ein|eine|einen|einer|um|uma|een|bir) (?=$UNIT )", " un "))
        // "через минуту", "za minutę": un minuto
        add(rule(" (через|za) (минуту|час|minute|godzine) ") { m -> if (m.groupValues[2] in setOf("минуту", "minute")) " tra 1 minuti " else " tra 1 ore " })
        // Mezz'ora e quarto d'ora
        add(rule(" (una |un )?media hora ", " mezz ora "))
        add(rule(" (une )?demie? heure ", " mezz ora "))
        add(rule(" (einer |eine )?halben? stunde ", " mezz ora "))
        add(rule(" (uma )?meia hora ", " mezz ora "))
        add(rule(" (un )?cuarto de hora ", " quarto d ora "))
        add(rule(" (un )?quart d heure ", " quarto d ora "))
        add(rule(" (eine |einer )?viertelstunde ", " quarto d ora "))
        add(rule(" (um )?quarto de hora ", " quarto d ora "))
        add(rule(" (een )?half ?uur ", " mezz ora "))
        add(rule(" (een )?kwartier ", " quarto d ora "))
        add(rule(" pol godziny ", " mezz ora "))
        add(rule(" kwadrans ", " quarto d ora "))
        add(rule(" (полчаса|пол часа) ", " mezz ora "))
        add(rule(" четверть часа ", " quarto d ora "))
        add(rule(" yarim saat ", " mezz ora "))
        add(rule(" ceyrek saat ", " quarto d ora "))
        // Durate
        add(rule(" (\\d+|un) ?(minutos|minuto|minutes|minute|minuten|mn|minuut|minuty|minut|минуты|минуту|минут|dakika) ", " $1 minuti "))
        add(rule(" (\\d+|un) (horas|hora|heures|heure|stunden|stunde|uur|uren|godziny|godzine|godzina|godzin|часов|часа|час|saat) ", " $1 ore "))
        add(rule(" (\\d+|un) (segundos|segundo|secondes|seconde|sekunden|sekunde|seconden|sekundy|sekund|секунды|секунду|секунд|saniye) ", " $1 secondi "))
        add(rule(" (\\d+|un) (dias|dia|jours|jour|tagen|tage|tag|dagen|dag|dni|дней|дня|день|gun) ", " $1 giorni "))
        add(rule(" (\\d+|un) (semanas|semana|semaines|semaine|wochen|woche|weken|week|tygodnie|tygodni|tygodnia|недели|неделю|недель|hafta) ", " $1 settimane "))
        add(rule(" (\\d+|un) ore (y|et|und|e) (media|demie|halb|meia) ", " $1 ore e 30 "))
        // "en 10 minutos", "dans 10 minutes", "in 10 Minuten", "daqui a 10 minutos"
        add(rule(" (en|dentro de|dans|in|em|daqui a|daqui|d ici|over|binnen|za|через) (?=(mezz ora|quarto d ora|(\\d+|un) (minuti|ore|secondi|giorni|settimane)) )", " tra "))
        // Turco: "10 dakika sonra", "yarim saat içinde"
        add(rule(" (mezz ora|quarto d ora|(\\d+|un) (minuti|ore|secondi|giorni|settimane)) (sonra|icinde) ", " tra $1 "))

        // Giorni: prima le coppie ("mañana por la mañana"), poi le parole sole
        add(rule(" pasado manana ", " dopodomani "))
        add(rule(" manana (por la|en la) manana ", " domani mattina "))
        add(rule(" manana (por la|en la) tarde ", " domani pomeriggio "))
        add(rule(" manana (por la|en la) noche ", " domani sera "))
        add(rule(" (esta manana|hoy por la manana) ", " stamattina "))
        add(rule(" (esta tarde|hoy por la tarde) ", " oggi pomeriggio "))
        add(rule(" (esta noche|hoy por la noche) ", " stasera "))
        add(rule(" (de|por) la manana ", " di mattina "))
        add(rule(" (de|por) la tarde ", " del pomeriggio "))
        add(rule(" (de|por) la noche ", " di sera "))
        add(rule(" manana ", " domani "))
        add(rule(" hoy ", " oggi "))

        add(rule(" apres demain ", " dopodomani "))
        add(rule(" demain matin ", " domani mattina "))
        add(rule(" demain apres midi ", " domani pomeriggio "))
        add(rule(" demain soir ", " domani sera "))
        add(rule(" demain ", " domani "))
        add(rule(" aujourd hui ", " oggi "))
        add(rule(" ce soir ", " stasera "))
        add(rule(" ce matin ", " stamattina "))
        add(rule(" cet apres midi ", " oggi pomeriggio "))
        add(rule(" cette nuit ", " stanotte "))
        add(rule(" du matin ", " di mattina "))
        add(rule(" de l apres midi ", " del pomeriggio "))
        add(rule(" du soir ", " di sera "))

        add(rule(" ubermorgen ", " dopodomani "))
        add(rule(" morgen (fruh|vormittag) ", " domani mattina "))
        add(rule(" morgen nachmittag ", " domani pomeriggio "))
        add(rule(" morgen abend ", " domani sera "))
        add(rule(" heute (morgen|fruh|vormittag) ", " stamattina "))
        add(rule(" heute nachmittag ", " oggi pomeriggio "))
        add(rule(" heute abend ", " stasera "))
        add(rule(" heute nacht ", " stanotte "))
        add(rule(" heute ", " oggi "))
        add(rule(" morgen ", " domani "))
        add(rule(" (abends|am abend) ", " di sera "))
        add(rule(" (morgens|am morgen|vormittags) ", " di mattina "))
        add(rule(" (nachmittags|am nachmittag) ", " del pomeriggio "))
        add(rule(" nachts ", " di notte "))

        add(rule(" overmorgen ", " dopodomani "))
        add(rule(" morgen ?(ochtend|vroeg) ", " domani mattina "))
        add(rule(" morgen ?middag ", " domani pomeriggio "))
        add(rule(" morgen ?avond ", " domani sera "))
        add(rule(" (vanochtend|vanmorgen) ", " stamattina "))
        add(rule(" vanmiddag ", " oggi pomeriggio "))
        add(rule(" vanavond ", " stasera "))
        add(rule(" vannacht ", " stanotte "))
        add(rule(" vandaag ", " oggi "))
        add(rule(" ('s ochtends|s ochtends|s morgens) ", " di mattina "))
        add(rule(" s middags ", " del pomeriggio "))
        add(rule(" s avonds ", " di sera "))

        add(rule(" pojutrze ", " dopodomani "))
        add(rule(" jutro rano ", " domani mattina "))
        add(rule(" jutro po poludniu ", " domani pomeriggio "))
        add(rule(" jutro wieczorem ", " domani sera "))
        add(rule(" (dzis|dzisiaj) rano ", " stamattina "))
        add(rule(" (dzis|dzisiaj) po poludniu ", " oggi pomeriggio "))
        add(rule(" (dzis|dzisiaj) wieczorem ", " stasera "))
        add(rule(" (dzis|dzisiaj) ", " oggi "))
        add(rule(" jutro ", " domani "))
        add(rule(" rano ", " di mattina "))
        add(rule(" po poludniu ", " del pomeriggio "))
        add(rule(" wieczorem ", " di sera "))

        add(rule(" послезавтра ", " dopodomani "))
        add(rule(" завтра утром ", " domani mattina "))
        add(rule(" завтра днем ", " domani pomeriggio "))
        add(rule(" завтра вечером ", " domani sera "))
        add(rule(" сегодня утром ", " stamattina "))
        add(rule(" сегодня днем ", " oggi pomeriggio "))
        add(rule(" сегодня вечером ", " stasera "))
        add(rule(" сегодня ночью ", " stanotte "))
        add(rule(" сегодня ", " oggi "))
        add(rule(" завтра ", " domani "))
        add(rule(" утром ", " di mattina "))
        add(rule(" днем ", " del pomeriggio "))
        add(rule(" вечером ", " di sera "))
        add(rule(" ночью ", " di notte "))

        add(rule(" (yarindan sonra|obur gun) ", " dopodomani "))
        add(rule(" yarin sabah ", " domani mattina "))
        add(rule(" yarin ogleden sonra ", " domani pomeriggio "))
        add(rule(" yarin aksam ", " domani sera "))
        add(rule(" bu sabah ", " stamattina "))
        add(rule(" bu ogleden sonra ", " oggi pomeriggio "))
        add(rule(" bu aksam ", " stasera "))
        add(rule(" bu gece ", " stanotte "))
        add(rule(" bugun ", " oggi "))
        add(rule(" yarin ", " domani "))
        add(rule(" sabah ", " di mattina "))
        add(rule(" ogleden sonra ", " del pomeriggio "))
        add(rule(" aksam ", " di sera "))

        add(rule(" depois de amanha ", " dopodomani "))
        add(rule(" amanha (de|pela) manha ", " domani mattina "))
        add(rule(" amanha (a|de|pela) tarde ", " domani pomeriggio "))
        add(rule(" amanha (a|de|pela) noite ", " domani sera "))
        add(rule(" (hoje de manha|hoje pela manha|esta manha) ", " stamattina "))
        add(rule(" hoje (a|de|pela) tarde ", " oggi pomeriggio "))
        add(rule(" (hoje (a|de|pela) noite|esta noite) ", " stasera "))
        add(rule(" amanha ", " domani "))
        add(rule(" hoje ", " oggi "))
        add(rule(" (da|de) manha ", " di mattina "))
        add(rule(" da tarde ", " del pomeriggio "))
        add(rule(" da noite ", " di sera "))

        // Giorni della settimana: "el próximo viernes", "vendredi prochain", "nächsten Freitag", "na próxima sexta"
        for ((w, it) in DAYS) {
            add(rule(" (el |le |la |am |no |na |ce |este |diesen |op |w |we |в |во )?(proximo |proxima |siguiente |prochain |nachsten |kommenden |volgende |aanstaande |przyszly |przyszla |przyszlą |nastepny |nastepna |следующии |следующую |следующее |ближаишии |ближаишую |gelecek |onumuzdeki |haftaya )?$w( que viene| que vem| prochain| proximo| gunu)? ") { m ->
                if (NEXT.containsMatchIn(m.value)) " $it prossimo " else " $it "
            })
        }
        for ((w, it) in PT_DAYS) {
            add(rule(" (na |a )?(proxima )?$w feira( que vem)? ") { m -> if (NEXT.containsMatchIn(m.value)) " $it prossimo " else " $it " })
            add(rule(" (na |proxima |na proxima )$w( que vem)? ") { m -> if (NEXT.containsMatchIn(m.value)) " $it prossimo " else " $it " })
        }

        // Date: "15 de noviembre", "le 1er novembre", "am 15. November", "dia 15 de novembro"
        val months = MONTH_OF.keys.sortedByDescending { it.length }.joinToString("|")
        add(rule(" (el |le |am |o |dia |no dia )?(\\d{1,2})(er|\\.)?( de| du)? ($months)( de \\d{4}| \\d{4})? ") { m -> " ${m.groupValues[2]} ${MONTH_OF[m.groupValues[5]]} " })
        // "março" solo con "de": senza, è Marco
        add(rule(" (dia |no dia |o )?(\\d{1,2}) de marco( de \\d{4})? ", " $2 marzo "))
        add(rule(" (el|am|no dia|dia) (\\d{1,2})\\.? ", " il $2 "))

        // Ore
        add(rule(" (al |a )?mediodia ", " a mezzogiorno "))
        add(rule(" (a )?(la )?medianoche ", " a mezzanotte "))
        add(rule(" (a|hasta|para|desde|sobre|antes de) (las|la) (\\d{1,2})(?:[:.](\\d{2}))?( y media| y cuarto| menos cuarto)?( en punto)? ") { m -> at(m.groupValues[3], m.groupValues[4], m.groupValues[5]) })
        add(rule(" (a la|a las) una( y media)? ") { m -> at("1", null, m.groupValues[2]) })
        add(rule(" (a )?midi ", " a mezzogiorno "))
        add(rule(" (a )?minuit ", " a mezzanotte "))
        // "22h30" da solo; "1h" da solo resta una durata ("timer 1h")
        add(rule(" (\\d{1,2})h(\\d{2}) ") { m -> at(m.groupValues[1], m.groupValues[2]) })
        add(rule(" (um |zu )?mittag ", " a mezzogiorno "))
        add(rule(" (um )?mitternacht ", " a mezzanotte "))
        add(rule(" (um|gegen|bis|ab) (\\d{1,2})(?:[:.](\\d{2}))?(?: uhr(?: (\\d{2}))?)? ") { m -> at(m.groupValues[2], m.groupValues[3].ifEmpty { m.groupValues[4] }) })
        add(rule(" (\\d{1,2})(?:[:.](\\d{2}))? uhr(?: (\\d{2}))? ") { m -> at(m.groupValues[1], m.groupValues[2].ifEmpty { m.groupValues[3] }) })
        add(rule(" (ao )?meio dia ", " a mezzogiorno "))
        add(rule(" (a )?meia noite ", " a mezzanotte "))
    }

    // ── Comandi

    /** Le prime parole di un comando, e la frase italiana che le sostituisce. */
    private class Cmd(val re: Regex, val to: (MatchResult) -> String)
    private fun cmd(p: String, to: String) = Cmd(Regex("^(?:$p)(?= |$)")) { to }
    private fun cmd(p: String, to: (MatchResult) -> String) = Cmd(Regex("^(?:$p)(?= |$)"), to)

    private fun via(w: String) = when (w) { "whatsapp" -> "whatsapp"; "telegram" -> "telegram"; "sms" -> "sms"; else -> "messaggio" }

    private val CMDS: List<Cmd> = listOf(
        // Ricordami (prima di "rappelle" = richiama)
        cmd("(recuerdame|recordarme|acuerdame)( que| de)?", "ricordami"),
        cmd("(rappelle moi|rappelle toi)( de| d| que)?", "ricordami"),
        cmd("erinnere mich( daran| an)?( dass)?", "ricordami"),
        cmd("(lembra me|me lembra|lembre me|me lembre|lembrar me)( de| que)?", "ricordami"),
        // Sveglia
        cmd("(despiertame|despertarme)", "svegliami"),
        cmd("(reveille moi|reveiller moi)", "svegliami"),
        cmd("(weck|wecke) mich", "svegliami"),
        cmd("(me acorda|acorda me|acorde me|me acorde|desperta me|me desperta)", "svegliami"),
        cmd("(pon|ponme|programa|configura) (una |la )?(alarma|despertador)", "sveglia"),
        cmd("(alarma|despertador)", "sveglia"),
        cmd("(mets|regle|programme) (une |un |l )?(alarme|reveil)", "sveglia"),
        cmd("(alarme|reveil)", "sveglia"),
        cmd("(stell|stelle) (einen |den )?wecker", "sveglia"),
        cmd("wecker", "sveglia"),
        cmd("(poe|coloca|cria|define) (um |o )?(alarme|despertador)", "sveglia"),
        // Timer
        cmd("(pon|ponme|inicia|activa) (un |el )?(temporizador|cronometro)( de| para)?", "timer"),
        cmd("(temporizador|cronometro)( de| para)?", "timer"),
        cmd("(mets|lance|demarre|regle) (un |le )?(minuteur|minuterie)( de| pour)?", "timer"),
        cmd("(minuteur|minuterie)( de| pour)?", "timer"),
        cmd("(stell|stelle|starte) (einen |den )?timer( auf| fur| von)?", "timer"),
        cmd("(poe|coloca|inicia) (um |o )?(temporizador|cronometro)( de| para)?", "timer"),
        cmd("(avisame|avisa me|me avisa|previens moi) (en|dentro de|dans|em)", "timer"),
        // Chiama
        cmd("(haz|hazme) una llamada (a|al)", "chiama"),
        cmd("(llama|llamar|telefonea|telefonear)( a| al)?", "chiama"),
        cmd("(passe|passer) un (coup de fil|appel) (a|au)", "chiama"),
        cmd("(appelle|appeler|telephone|telephoner|rappelle)( a| au)?", "chiama"),
        cmd("(anrufen|telefoniere mit|telefonier mit)", "chiama"),
        cmd("faz (uma )?(ligacao|chamada) (para|pra)( o| a)?", "chiama"),
        cmd("(liga|ligar|telefona|telefonar|liga me) (para|pra)( o| a)?", "chiama"),
        // Scrivi: "manda un whatsapp a" lo sistema già Phrase, con la app giusta
        cmd("(envia|enviale|manda|mandale) (un |una )?(mensaje|texto|sms|whatsapp|telegram)( a| al)?") { m -> "manda un ${via(m.groupValues[3])} a" },
        cmd("(envoie|envoyer|envoie lui) (un |une )?(message|texto|sms|whatsapp|telegram)( a| au)?") { m -> "manda un ${via(m.groupValues[3])} a" },
        cmd("(schick|schicke|sende) (eine |ein )?(nachricht|sms|whatsapp|telegram)( an)?") { m -> "manda un ${via(m.groupValues[3])} a" },
        cmd("(manda|envia|mande|envie) (uma |um )?(mensagem|sms|whatsapp|telegram)( para| pra| a| ao)?( o| a)?") { m -> "manda un ${via(m.groupValues[3])} a" },
        cmd("(escribe|escribele|escribir)( a| al)?", "scrivi a"),
        cmd("(ecris|ecrire|ecris lui)( a| au)?", "scrivi a"),
        cmd("(schreib|schreibe)( an)?", "scrivi a"),
        cmd("(escreve|escreva|escrever)( para| pra| a| ao)( o| a)?", "scrivi a"),
        cmd("(dile|di|decile) (a|al)", "di a"),
        cmd("(dis|dites)( a| au)", "di a"),
        cmd("(sag|sage)( an)?", "di a"),
        cmd("(diz|diga|fala|fale)( para| pra| a| ao)( o| a)?", "di a"),
        cmd("(responde|contesta|responder|reponds|repondre|antworte|responda)( a| al| au| para)", "rispondi a"),
        cmd("(responde|contesta|reponds|antworte|responda)", "rispondi"),
        // Strada
        cmd("(llevame|llevarme) (a|al|hasta)", "portami a"),
        cmd("como (llego|llegar|voy) (a|al|hasta)", "portami a"),
        cmd("(navega|navegar|ruta) (a|al|hasta)", "portami a"),
        cmd("(emmene|amene|conduis) moi (a|au|chez|jusqu a)", "portami a"),
        cmd("(itineraire|navigue|naviguer) (vers|a|au|pour|jusqu a)", "portami a"),
        cmd("comment aller (a|au)", "portami a"),
        cmd("(navigiere|navigieren|route|fahre|fahr|bring mich) (nach|zu|zum|zur)", "portami a"),
        cmd("wie komme ich (nach|zu|zum|zur)", "portami a"),
        cmd("(leva me|me leva|me leve|leve me) (para|a|ao|ate)", "portami a"),
        cmd("como (chego|chegar) (a|ao|para|ate)", "portami a"),
        cmd("(navegar|navega|rota) (para|ate)", "portami a"),
        // Impegni
        cmd("(anade|agrega|pon) (un |una )?(evento|cita|recordatorio)( para)?", "aggiungi"),
        cmd("(anade|agrega)( al calendario)?", "aggiungi"),
        cmd("(nuevo evento|nueva cita)", "aggiungi"),
        cmd("(ajoute|cree|ajouter) (un |une )?(evenement|rendez vous|rappel|rdv)( pour)?", "aggiungi"),
        cmd("(nouvel evenement|nouveau rendez vous)", "aggiungi"),
        cmd("(neuer|neuen) termin|(erstelle|trage) (einen )?termin( ein)?|termin", "aggiungi"),
        cmd("(cria|crie|adiciona|marca|marque) (um |uma )?(evento|compromisso|lembrete|consulta)( para)?", "aggiungi"),
        cmd("(novo evento|novo compromisso)", "aggiungi"),
    )

    /** Le parole d'attacco di questi comandi e i verbi di casa: non sono verbi italiani scritti male. */
    val VERBS = setOf(
        "recuerdame", "rappelle", "erinnere", "lembra", "lembre", "despiertame", "reveille", "weck", "wecke", "acorda", "acorde",
        "pon", "ponme", "alarma", "despertador", "mets", "regle", "alarme", "reveil", "stell", "stelle", "wecker", "poe", "coloca",
        "temporizador", "cronometro", "minuteur", "minuterie", "starte", "avisame", "llama", "llamar", "appelle", "appeler",
        "telephone", "anrufen", "ruf", "rufe", "liga", "ligar", "envia", "manda", "envoie", "schick", "schicke", "sende", "mande",
        "envie", "escribe", "escribele", "ecris", "schreib", "schreibe", "escreve", "escreva", "dile", "dis", "sag", "sage", "diz",
        "diga", "fala", "responde", "contesta", "reponds", "antworte", "responda", "llevame", "navega", "emmene", "itineraire",
        "navigiere", "bring", "leva", "anade", "agrega", "ajoute", "cree", "termin", "cria", "adiciona", "marca",
        // Casa: restano come sono, per Assist
        "enciende", "apaga", "abre", "cierra", "sube", "baja", "activa", "desactiva", "allume", "eteins", "ouvre", "ferme",
        "baisse", "active", "desactive", "schalte", "schalt", "mach", "offne", "oeffne", "schliesse", "schließe", "dimme",
        "desliga", "acende", "fecha", "reproduce", "escucha", "joue", "ecoute", "spiel", "spiele", "toca", "ouvir",
    )

    /**
     * Le prime parole di un comando in un'altra lingua, riscritte in italiano; null se non è uno di questi.
     * [words] sono le parole originali (non vuote una volta normalizzate): quelle dopo il comando restano uguali.
     */
    fun command(words: List<String>): List<String>? {
        if (words.isEmpty()) return null
        val n = words.map { flat(Search.normalize(it)) }
        // Tedesco, il verbo in due pezzi: "ruf Elisa an"
        if (n.size >= 3 && n[0] in setOf("ruf", "rufe") && n.last() == "an") return listOf("chiama") + words.subList(1, words.size - 1)
        if (n.size >= 2 && n[0] in setOf("ruf", "rufe")) return listOf("chiama") + words.drop(1)
        val joined = n.joinToString(" ")
        for (c in CMDS) {
            val m = c.re.find(joined) ?: continue
            // Parole normalizzate usate → parole scritte: "réveille-moi" è una parola sola ma due normalizzate
            var tokens = m.value.trim().split(' ').size
            var used = 0
            while (tokens > 0 && used < words.size) { tokens -= n[used].split(' ').size; used++ }
            return c.to(m).split(' ').filter { it.isNotEmpty() } + words.drop(used)
        }
        return LingueAltre.command(words)
    }
}
