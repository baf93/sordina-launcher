package app.sordina.car

import android.content.Context
import app.sordina.data.Held
import app.sordina.data.HeldLine
import app.sordina.data.Level
import app.sordina.data.Upcoming

/**
 * Dati finti per provare lo schermo dell'auto: solo nella versione di prova (app.sordina.debug) e solo se acceso da
 * `adb shell setprop debug.sordina.demo 1`. Niente finisce nel calendario o tra le notifiche vere.
 */
internal object Demo {
    private fun on(cc: Context): Boolean = cc.packageName.endsWith(".debug") && runCatching {
        Class.forName("android.os.SystemProperties").getMethod("get", String::class.java, String::class.java)
            .invoke(null, "debug.sordina.demo", "") as String == "1"
    }.getOrDefault(false)

    /** Una notifica finta: "Rispondi" fa finta di mandare */
    fun isDemo(key: String) = key.startsWith("demo:")

    /** Le finte segnate lette dall'auto: spariscono finché non riavvii l'app */
    private val read = HashSet<String>()
    fun read(key: String) { read += key }

    fun events(cc: Context): List<Upcoming> {
        if (!on(cc)) return emptyList()
        val h = 3_600_000L
        val now = System.currentTimeMillis() / h * h
        return listOf(
            Upcoming(-1, "Dentista", now + 2 * h, now + 3 * h, "Via Roma 12, Torino", null, false),
            Upcoming(-2, "Riunione di progetto", now + 4 * h, now + 5 * h, null, "https://teams.microsoft.com/l/meetup-join/demo", false, "+390200000000,,123456789#"),
        )
    }

    fun held(cc: Context, since: Long): List<Held> {
        if (!on(cc)) return emptyList()
        val m = 60_000L
        fun chat(key: String, pkg: String, app: String, who: String, group: String?, at: Long, vararg lines: String) =
            Held("demo:$key", pkg, app, who, group, lines.last(), at, Level.RIEPILOGO, lines.size, true,
                lines = lines.mapIndexed { i, t -> HeldLine(at - (lines.size - 1 - i) * m, who, t) })
        return listOf<Held>(
            chat("anna", "com.whatsapp", "WhatsApp", "Anna", null, since + 2 * m, "Sei partito?", "Ricordati il pane"),
            chat("calcetto", "org.telegram.messenger", "Telegram", "Marco", "Calcetto giovedì", since + 5 * m, "Chi porta il pallone?"),
            Held("demo:mail", "com.google.android.gm", "Gmail", "Ufficio", null, "Fattura di novembre", since + 7 * m, Level.RIEPILOGO, 1, false, title = "Fattura di novembre"),
            chat("luca", "com.whatsapp", "WhatsApp", "Luca", null, since + 9 * m, "Stasera ci sei?"),
            chat("mamma", "com.whatsapp", "WhatsApp", "Mamma", null, since + 11 * m, "Chiamami quando arrivi"),
            chat("lavoro", "com.microsoft.teams", "Teams", "Giulia", "Team progetto", since + 13 * m, "Ho aggiornato il documento", "Ci sentiamo domani"),
            chat("sara", "org.telegram.messenger", "Telegram", "Sara", null, since + 15 * m, "Foto della gita 📷"),
            chat("condominio", "com.whatsapp", "WhatsApp", "Paolo", "Condominio", since + 17 * m, "Domani manca l'acqua"),
        ).filter { it.key !in read }
    }
}
