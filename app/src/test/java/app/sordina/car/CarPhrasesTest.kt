package app.sordina.car

import app.sordina.data.Held
import app.sordina.data.Level
import app.sordina.data.Upcoming
import org.junit.Assert.assertEquals
import org.junit.Test

/** Parla nelle altre lingue: le stesse cose di Sordina, con le frasi di ciascuna */
class CarPhrasesTest {
    private fun held(person: String, text: String = "ciao", time: Long = 1L) =
        Held("k:$person:$time", "com.whatsapp", "WhatsApp", person, null, text, time, Level.RIEPILOGO, 1, isMessage = true)

    private val paolo = listOf(held("Paolo Rossi"))
    private val petr = listOf(held("Petr Novák"))
    private val masha = listOf(held("Маша"))
    private val jp = listOf(held("パオロ"))
    private val ko = listOf(held("파올로"))
    private val zh = listOf(held("张伟"))
    private val groups = listOf(paolo, petr, masha, jp, ko, zh)
    private val calcetto = Upcoming(1, "Calcetto", 2_000L, 3_000L, "Centro sportivo", null, false)
    private val events = listOf(calcetto)

    private fun ask(lang: String, said: String): CarAsk = CarTalk.understand(said, groups, events, lang) { CarAsk.Unknown }

    @Test fun waiting() {
        assertEquals(CarAsk.Waiting, ask("es", "¿Quién me ha escrito?"))
        assertEquals(CarAsk.Waiting, ask("de", "Was habe ich verpasst?"))
        assertEquals(CarAsk.Waiting, ask("ja", "誰からメッセージ来てる？"))
        assertEquals(CarAsk.Waiting, ask("ru", "Кто мне писал?"))
    }

    @Test fun passing() {
        assertEquals(CarAsk.PassAll(60), ask("fr", "Laisse passer tout le monde"))
        assertEquals(CarAsk.PassAll(120), ask("es", "Deja pasar a todos durante dos horas"))
        assertEquals(CarAsk.PassAll(30), ask("de", "Lass alle für eine halbe Stunde durch"))
        assertEquals(CarAsk.PassAll(120), ask("zh-CN", "两个小时内全部放行"))
        assertEquals(CarAsk.PassAll(60), ask("pl", "Przepuść wszystkich na godzinę"))
        assertEquals(CarAsk.PassImportant, ask("pt-BR", "Só os importantes"))
        assertEquals(CarAsk.PassImportant, ask("ko", "중요한 것만 알려줘"))
    }

    @Test fun agenda() {
        assertEquals(CarAsk.NextEvent, ask("nl", "Wat is mijn volgende afspraak?"))
        assertEquals(CarAsk.GoTo(calcetto), ask("es", "Llévame al calcetto"))
        assertEquals(CarAsk.GoTo(calcetto), ask("de", "Bring mich zum Calcetto"))
    }

    @Test fun read() {
        assertEquals(CarAsk.Read(paolo), ask("es", "Lee a Paolo"))
        assertEquals(CarAsk.Read(jp), ask("ja", "パオロさんのメッセージを読んで"))
        assertEquals(CarAsk.Read(zh), ask("zh-TW", "读张伟的消息"))
    }

    @Test fun reply() {
        assertEquals(CarAsk.Reply(paolo, "Llego en diez minutos"), ask("es", "Responde a Paolo que llego en diez minutos"))
        assertEquals(CarAsk.Reply(paolo, "J'arrive"), ask("fr", "Réponds à Paolo que j'arrive"))
        assertEquals(CarAsk.Reply(paolo, null), ask("de", "Antworte Paolo"))
        assertEquals(CarAsk.Reply(paolo, "Geliyorum"), ask("tr", "Paolo'ya geliyorum diye cevap ver"))
        assertEquals(CarAsk.Reply(ko, "곧 도착해"), ask("ko", "파올로에게 곧 도착해라고 답장해줘"))
        assertEquals(CarAsk.Reply(jp, "今行く"), ask("ja", "パオロに今行くと返信して"))
    }

    @Test fun declinedNames() {
        // Il nome con la desinenza del caso: "Petrovi" è Petr, "Маше" è Маша
        assertEquals(CarAsk.Reply(petr, "Už jedu"), ask("cs", "Odpověz Petrovi, že už jedu"))
        assertEquals(CarAsk.Reply(masha, null), ask("ru", "Ответь Маше"))
    }

    @Test fun onlyTheAppLanguage() {
        // "las Paolo" è "leggi" solo in svedese
        assertEquals(CarAsk.Read(paolo), ask("sv", "Läs Paolo"))
        assertEquals(CarAsk.Unknown, ask("it", "Läs Paolo"))
    }
}
