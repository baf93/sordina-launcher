package app.sordina.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

/** Spagnolo, francese, tedesco, portoghese: date, durate e comandi riportati all'italiano. */
class LingueTest {
    // Mercoledì 11 novembre 2026, ore 10:00
    private val now = LocalDateTime.of(2026, 11, 11, 10, 0)
    private fun at(s: String, bias: Boolean = true) = DateParser.parse(s, now, bias).whenAt?.start
    private fun rest(s: String) = DateParser.parse(s, now).rest
    private fun dur(s: String) = DateParser.duration(s)?.first
    private fun t(d: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, d, h, m)

    private val hints = object : Phrase.Hints {
        override fun isName(word: String) = word in setOf("elisa", "marco", "ana")
        override fun lastWriter() = "Elisa"
    }
    private fun c(s: String) = Phrase.canonical(s, hints)

    @Test fun spagnolo() {
        assertEquals(t(12, 17), at("dentista mañana a las 5"))
        assertEquals("dentista", rest("dentista mañana a las 5"))
        assertEquals(t(11, 22), at("pizza esta noche a las 10"))
        assertEquals(t(11, 22, 30), at("cena a las 10 y media de la noche"))
        assertEquals(t(13, 9), at("reunión el viernes a las 9"))
        assertEquals(t(13, 0), at("viernes"))
        assertEquals(t(15, 0), at("el 15 de noviembre"))
        assertEquals(t(11, 10, 20), at("en 20 minutos"))
        assertEquals(t(11, 10, 30), at("dentro de media hora"))
        assertEquals(t(13, 0), at("pasado mañana"))
        assertEquals(t(11, 12), at("a mediodía"))
        assertEquals(600, dur("temporizador 10 minutos"))
        assertEquals(5400, dur("una hora y media"))
    }

    @Test fun francese() {
        assertEquals(t(12, 17), at("dentiste demain à 17h"))
        assertEquals(t(11, 22), at("pizza ce soir à 22h"))
        assertEquals(t(12, 8, 30), at("demain matin à 8h30"))
        assertEquals(t(18, 9), at("mercredi prochain à 9 heures"))
        assertEquals(t(11, 10, 10), at("dans 10 minutes"))
        assertEquals(t(11, 10, 30), at("dans une demi heure"))
        assertEquals(LocalDateTime.of(2026, 12, 1, 0, 0), at("le 1er décembre"))
        assertEquals(t(11, 19, 30), at("à 7h et demie du soir"))
        assertEquals(7200, dur("minuteur deux heures"))
    }

    @Test fun tedesco() {
        assertEquals(t(12, 17), at("Zahnarzt morgen um 17 Uhr"))
        assertEquals("zahnarzt", rest("Zahnarzt morgen um 17 Uhr"))
        assertEquals(t(11, 20), at("heute Abend"))
        assertEquals(t(12, 9), at("morgen früh"))
        assertEquals(t(13, 18, 30), at("Freitag um 18:30"))
        // Come "venerdì prossimo": il primo venerdì che viene
        assertEquals(t(13, 0), at("nächsten Freitag"))
        assertEquals(t(15, 0), at("am 15. November"))
        assertEquals(t(11, 10, 30), at("in einer halben Stunde"))
        assertEquals(t(11, 12), at("in 2 Stunden"))
        assertEquals(900, dur("Timer fünfzehn Minuten"))
    }

    @Test fun portoghese() {
        assertEquals(t(12, 17), at("dentista amanhã às 5"))
        assertEquals(t(11, 22), at("pizza hoje à noite às 10"))
        assertEquals(t(13, 9), at("na sexta às 9"))
        assertEquals(t(12, 0), at("quinta feira"))
        assertEquals(t(15, 0), at("dia 15 de novembro"))
        assertEquals(t(11, 10, 15), at("daqui a 15 minutos"))
        assertEquals(t(11, 10, 30), at("em meia hora"))
        assertEquals(LocalDateTime.of(2027, 3, 3, 0, 0), at("3 de março"))
        assertEquals(1800, dur("temporizador trinta minutos"))
    }

    /** Le parole italiane non cambiano: niente "quinta" giorno, niente "Marco" mese, "1h" resta una durata. */
    @Test fun litalianoResta() {
        assertEquals(t(11, 17), at("calcetto alle 17"))
        assertNull(at("la quinta strada"))
        assertNull(at("scrivi a Marco 5 minuti fa"))
        assertEquals(3600, dur("timer 1h"))
        assertEquals(t(11, 15), at("per le 15"))
        assertEquals(t(11, 10, 10), at("tra 10 minuti"))
    }

    @Test fun comandi() {
        assertEquals("chiama Elisa", c("llama a Elisa"))
        assertEquals("chiama Elisa", c("¿Puedes llamar a Elisa por favor?"))
        assertEquals("chiama Elisa", c("appelle Elisa"))
        assertEquals("chiama Elisa", c("ruf Elisa an"))
        assertEquals("chiama Elisa", c("liga para a Elisa"))
        assertEquals("scrivi a Elisa que llego en 5", c("escribe a Elisa que llego en 5"))
        assertEquals("scrivi a Elisa que llego tarde", c("dile a Elisa que llego tarde"))
        assertEquals("scrivi a Elisa su whatsapp", c("envía un whatsapp a Elisa"))
        assertEquals("scrivi a Elisa j'arrive", c("écris à Elisa j'arrive"))
        assertEquals("scrivi a Elisa dass ich komme", c("sag Elisa dass ich komme"))
        assertEquals("scrivi a Elisa chego em 5", c("escreve para a Elisa chego em 5"))
        assertEquals("scrivi a Elisa ok", c("responde ok"))
        assertEquals("sveglia a las 7", c("pon una alarma a las 7"))
        assertEquals("svegliami um 7", c("weck mich um 7"))
        assertEquals("svegliami à 7h", c("réveille-moi à 7h"))
        assertEquals("timer 10 minutos pasta", c("temporizador de 10 minutos pasta"))
        assertEquals("timer pâtes 10 minutes", c("minuteur pâtes 10 minutes"))
        assertEquals("portami a la estación", c("llévame a la estación"))
        assertEquals("portami a Berlin", c("navigiere nach Berlin"))
        assertEquals("ricordami mañana llamar al médico", c("recuérdame mañana llamar al médico"))
        assertEquals("ricordami demain d'appeler", c("rappelle-moi demain d'appeler"))
        // Casa: resta nella tua lingua, per Assist
        assertEquals("enciende la luz del salón", c("enciende la luz del salón"))
        assertEquals("allume la lumière", c("allume la lumière"))
        // L'italiano non si tocca
        assertEquals("chiama Elisa", c("chiama Elisa"))
        assertEquals("scrivi a Elisa su whatsapp", c("manda un whatsapp a Elisa"))
    }

    @Test fun casa() {
        assertEquals(90, HomeTalk.score(Search.normalize("qué luces están encendidas").split(' '), emptySet()))
        assertEquals(90, HomeTalk.score(Search.normalize("mach das Licht im Wohnzimmer an").split(' '), emptySet()))
        assertEquals(84, HomeTalk.score(Search.normalize("bonne nuit").split(' '), emptySet()))
        assertNull(HomeTalk.score(Search.normalize("timer quarto d ora").split(' '), emptySet()))
    }
}
