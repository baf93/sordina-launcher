package app.sordina.data

import app.sordina.service.NotifInfo

/**
 * I messaggi di una chat già letti, uno per uno. WhatsApp e Telegram mettono in una notifica sola tutto quello che
 * per loro è ancora da leggere: letti in Sordina (o tolti dalla tendina), restano "non letti" nell'app, che il giorno
 * dopo li ripubblica insieme al messaggio nuovo. Il testo della notifica è cambiato, ma i messaggi vecchi no: si
 * riconoscono dall'impronta (quando, chi, cosa) e in Sordina restano solo quelli nuovi.
 */
object ReadLines {
    /** L'impronta di un messaggio: l'ora che gli dà l'app, chi lo scrive e il testo. Uguale a ogni ripubblicazione. */
    fun fp(line: Triple<Long, String?, String>): Int = line.hashCode()

    fun fps(info: NotifInfo): List<Int> = if (info.isMessage) info.lines.map(::fp) else emptyList()

    /**
     * La notifica con solo i messaggi che non hai ancora letto. Uguale se non c'è niente di già letto;
     * null se li hai letti tutti (allora si chiude).
     */
    fun unread(info: NotifInfo, seen: Set<Int>): NotifInfo? {
        if (seen.isEmpty() || !info.isMessage || info.isGroupSummary || info.lines.isEmpty()) return info
        val fresh = info.lines.filter { fp(it) !in seen }
        if (fresh.isEmpty()) return null
        if (fresh.size == info.lines.size) return info
        return info.copy(lines = fresh, count = fresh.size)
    }
}
