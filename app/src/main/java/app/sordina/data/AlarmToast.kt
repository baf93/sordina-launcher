package app.sordina.data

/**
 * La conferma che l'Orologio mostra quando imposti una sveglia: "Sveglia impostata tra 8 ore e 44 minuti",
 * "Alarm set for 8 hours and 44 minutes from now". Da lì si ricava l'ora della sveglia.
 *
 * Serve perché Android dice alle altre app una sola "prossima sveglia", e l'Orologio Samsung ci mette anche i suoi
 * promemoria (l'ora di andare a letto, alle 23:30): finché non passano, la sveglia vera resta nascosta.
 */
object AlarmToast {
    private val PAIR = Regex("""(\d{1,3})\s*([\p{L}]+)""")
    private val DAY = listOf("giorn", "day", "tag", "jour", "día", "dia", "dag", "dzie", "день", "дн", "дня", "päiv", "zi", "gün", "ngày", "日", "일", "天")
    private val HOUR = listOf("or", "hour", "hr", "stund", "std", "heure", "h", "hora", "uur", "time", "timm", "godz", "час", "tunti", "ore", "saat", "giờ", "時間", "시간", "小时", "小時", "ώρ", "hodin")
    private val MIN = listOf("min", "мин", "minut", "dakika", "phút", "分", "분", "λεπ")

    /** L'ora della sveglia, arrotondata al minuto, o null se il testo non dice "tra quanto". */
    fun parse(text: String, now: Long): Long? {
        var d = 0L; var h = 0L; var m = 0L; var any = false
        for (p in PAIR.findAll(text.lowercase())) {
            val n = p.groupValues[1].toLong()
            val w = p.groupValues[2]
            when {
                MIN.any { w.startsWith(it) } -> { m = n; any = true }
                DAY.any { w.startsWith(it) } -> { d = n; any = true }
                HOUR.any { w.startsWith(it) } -> { h = n; any = true }
            }
        }
        if (!any) return null
        val at = now + ((d * 24 + h) * 60 + m) * 60_000L
        // L'Orologio tronca ai minuti: la sveglia è al minuto pieno dopo
        val r = at % 60_000L
        return if (r == 0L) at else at - r + 60_000L
    }
}
