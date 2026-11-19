package app.sordina.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** Le altre lingue: ogni pacchetto vale solo con il telefono in quella lingua. */
class LingueAltreTest {
    // Mercoledì 11 novembre 2026, ore 10:00
    private val now = LocalDateTime.of(2026, 11, 11, 10, 0)
    private fun t(d: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, d, h, m)

    private var lang = "it"
    init { LingueAltre.language = { lang } }
    @After fun reset() { LingueAltre.language = { "it" } }

    private fun at(l: String, s: String): LocalDateTime? { lang = l; return DateParser.parse(s, now, false).whenAt?.start }
    private fun dur(l: String, s: String): Int? { lang = l; return DateParser.duration(s)?.first }
    private fun cmd(l: String, s: String): String? { lang = l; return Lingue.command(s.split(' '))?.joinToString(" ") }

    @Test fun soloNellaLinguaDelTelefono() {
        assertNull(cmd("it", "bel Marco"))
        assertEquals("chiama Marco", cmd("nl", "bel Marco"))
        assertNull(cmd("it", "zavolej Petra"))
    }

    @Test fun olandese() {
        assertEquals("chiama Marco", cmd("nl", "bel Marco"))
        assertEquals("manda un messaggio a Marco ik kom eraan", cmd("nl", "stuur een bericht naar Marco ik kom eraan"))
        assertEquals("ricordami morgen om 9 uur melk", cmd("nl", "herinner me morgen om 9 uur melk"))
        assertEquals("portami a Amsterdam Centraal", cmd("nl", "navigeer naar Amsterdam Centraal"))
    }

    @Test fun polacco() {
        assertEquals("chiama Marka", cmd("pl", "zadzwoń do Marka"))
        assertEquals("svegliami jutro o 7", cmd("pl", "obudź mnie jutro o 7"))
        assertEquals("timer 10 minut", cmd("pl", "ustaw minutnik na 10 minut"))
    }

    @Test fun russo() {
        assertEquals("chiama Маше", cmd("ru", "позвони Маше"))
        assertEquals("ricordami завтра в 9 купить хлеб", cmd("ru", "напомни мне завтра в 9 купить хлеб"))
    }

    @Test fun turco() {
        assertEquals("chiama Ahmet", cmd("tr", "Ahmet'i ara"))
        assertEquals("ricordami yarın saat 9 da süt", cmd("tr", "yarın saat 9 da süt hatırlat"))
        assertEquals("manda un messaggio a Ayşe geliyorum", cmd("tr", "Ayşe'ye geliyorum diye mesaj at"))
    }

    @Test fun ceco() {
        assertEquals(t(12, 7), at("cs", "zítra v 7"))
        assertEquals(t(11, 10, 20), at("cs", "za 20 minut"))
        assertEquals(t(13, 0), at("cs", "v pátek"))
        assertEquals(600, dur("cs", "časovač 10 minut"))
        assertEquals("chiama Petrovi", cmd("cs", "zavolej Petrovi"))
        assertEquals("svegliami zítra v 7", cmd("cs", "vzbuď mě zítra v 7"))
    }

    @Test fun scandinave() {
        assertEquals(t(12, 7), at("da", "i morgen kl. 7"))
        assertEquals(t(11, 10, 15), at("da", "om 15 minutter"))
        assertEquals(t(12, 7, 30), at("nb", "i morgen kl 7:30"))
        assertEquals(t(11, 12), at("sv", "om 2 timmar"))
        assertEquals(t(13, 0), at("sv", "på fredag"))
        assertEquals("chiama Erik", cmd("sv", "ring Erik"))
        assertEquals("chiama Erik", cmd("da", "ring til Erik"))
        assertEquals("ricordami i morgen at købe mælk", cmd("da", "mind mig om i morgen at købe mælk"))
    }

    @Test fun greco() {
        assertEquals(t(12, 7), at("el", "αύριο στις 7"))
        assertEquals(t(11, 10, 10), at("el", "σε 10 λεπτά"))
        assertEquals("chiama Νίκο", cmd("el", "πάρε τηλέφωνο τον Νίκο"))
    }

    @Test fun finlandese() {
        assertEquals(t(12, 7), at("fi", "huomenna kello 7"))
        assertEquals(t(11, 10, 10), at("fi", "10 minuutin päästä"))
        assertEquals("chiama Mikalle", cmd("fi", "soita Mikalle"))
    }

    @Test fun rumeno() {
        assertEquals(t(12, 7), at("ro", "mâine la ora 7"))
        assertEquals(t(11, 10, 10), at("ro", "peste 10 minute"))
        assertEquals("chiama Ana", cmd("ro", "sună-o pe Ana"))
    }

    @Test fun ucraino() {
        assertEquals(t(12, 7), at("uk", "завтра о 7"))
        assertEquals(t(11, 10, 10), at("uk", "через 10 хвилин"))
        assertEquals("chiama Олені", cmd("uk", "зателефонуй Олені"))
    }

    @Test fun vietnamita() {
        assertEquals(t(12, 7, 30), at("vi", "ngày mai lúc 7 giờ 30"))
        assertEquals(t(11, 10, 10), at("vi", "10 phút nữa"))
        assertEquals(t(11, 12), at("vi", "2 giờ nữa"))
        assertEquals("chiama Lan", cmd("vi", "gọi cho Lan"))
    }

    @Test fun indonesiano() {
        assertEquals(t(12, 7), at("id", "besok jam 7"))
        assertEquals(t(11, 10, 10), at("id", "10 menit lagi"))
        assertEquals("chiama Budi", cmd("in", "telepon Budi"))
    }

    @Test fun arabo() {
        assertEquals(t(12, 7), at("ar", "غدا الساعة 7"))
        assertEquals(t(11, 10, 10), at("ar", "بعد 10 دقائق"))
        assertEquals("chiama أحمد", cmd("ar", "اتصل ب أحمد"))
    }

    @Test fun hindi() {
        assertEquals(t(12, 7), at("hi", "कल सुबह 7 बजे"))
        assertEquals(t(11, 10, 10), at("hi", "10 मिनट में"))
        assertEquals("chiama राहुल", cmd("hi", "राहुल को कॉल करो"))
        assertEquals("svegliami कल सुबह 7 बजे", cmd("hi", "मुझे कल सुबह 7 बजे जगाओ"))
    }

    @Test fun coreano() {
        assertEquals(t(12, 7), at("ko", "내일 아침 7시"))
        assertEquals(t(11, 15, 30), at("ko", "오후 3시 반에"))
        assertEquals(t(11, 10, 10), at("ko", "10분 후에"))
        assertEquals("chiama 엄마", cmd("ko", "엄마에게 전화해줘"))
        assertEquals("svegliami 내일 7시에", cmd("ko", "내일 7시에 깨워줘"))
    }

    @Test fun giapponese() {
        assertEquals(t(12, 7), at("ja", "明日の朝7時に"))
        assertEquals(t(11, 10, 10), at("ja", "10分後"))
        assertEquals(t(11, 15), at("ja", "午後三時"))
        assertEquals("chiama 田中", cmd("ja", "田中さんに電話して"))
        assertEquals("timer 5分", cmd("ja", "5分のタイマー"))
    }

    @Test fun cinese() {
        assertEquals(t(12, 7), at("zh", "明天早上7点"))
        assertEquals(t(11, 15, 30), at("zh", "下午三点半"))
        assertEquals(t(11, 10, 10), at("zh", "10分钟后"))
        assertEquals("chiama 妈妈", cmd("zh", "给妈妈打电话"))
        assertEquals("svegliami 明天早上7点", cmd("zh", "明天早上7点叫醒我"))
    }

    @Test fun thai() {
        assertEquals(t(11, 10, 10), at("th", "อีก 10 นาที"))
        assertEquals("chiama แม่", cmd("th", "โทรหาแม่"))
    }

    @Test fun casaVerboInFondo() {
        lang = "tr"; assertEquals(HaOp.ON to "salon ışığını", LingueAltre.home(listOf("salon", "ışığını", "aç")))
        lang = "ja"; assertEquals(HaOp.OFF to "電気", LingueAltre.home(listOf("電気を消して")))
        lang = "th"; assertEquals(HaOp.ON to "ไฟ", LingueAltre.home(listOf("เปิดไฟ")))
        lang = "it"; assertNull(LingueAltre.home(listOf("salon", "aç")))
    }

    @Test fun casaSensibile() {
        lang = "cs"
        assertTrue(HomeRisk.needsAuth(listOf("otevři", "garáž").map { Search.normalize(it) }, emptyList(), Intents.sensitive(listOf("otevri", "garaz")), namesKnown = true))
        lang = "da"
        assertTrue(Intents.sensitive(listOf("abn", "doren")) || Intents.sensitive(listOf("åbn", "døren").map { Search.normalize(it) }))
        lang = "es"
        assertFalse(Intents.sensitive(listOf("enciende", "las", "luces")))
        lang = "tr"
        assertTrue(HomeRisk.needsAuth(listOf("bahçe", "kapısını", "aç").map { Search.normalize(it) }, emptyList(), false, namesKnown = false))
    }

    @Test fun mesi() {
        assertEquals(LocalDateTime.of(2026, 12, 15, 0, 0), at("cs", "15. prosince"))
        assertEquals(LocalDateTime.of(2026, 12, 15, 0, 0), at("da", "den 15. december"))
        assertEquals(LocalDateTime.of(2026, 12, 15, 0, 0), at("fi", "15. joulukuuta"))
        assertEquals(LocalDateTime.of(2026, 12, 15, 0, 0), at("uk", "15 грудня"))
        assertEquals(LocalDateTime.of(2026, 12, 15, 0, 0), at("el", "15 δεκεμβρίου"))
        assertEquals(LocalDateTime.of(2026, 12, 15, 0, 0), at("ar", "15 ديسمبر"))
        assertEquals(LocalDateTime.of(2026, 12, 15, 0, 0), at("hi", "15 दिसंबर"))
        assertEquals(LocalDateTime.of(2026, 12, 15, 0, 0), at("th", "15 ธันวาคม"))
        assertEquals(LocalDateTime.of(2026, 12, 15, 0, 0), at("id", "15 desember"))
        assertEquals(LocalDateTime.of(2026, 12, 15, 0, 0), at("vi", "ngày 15 tháng 12"))
        assertEquals(LocalDateTime.of(2026, 12, 15, 0, 0), at("ko", "12월 15일"))
        assertEquals(LocalDateTime.of(2026, 12, 15, 0, 0), at("ja", "12月15日"))
        assertEquals(LocalDateTime.of(2026, 12, 15, 0, 0), at("zh", "十二月十五日"))
        assertEquals(LocalDateTime.of(2026, 12, 15, 9, 0), at("ro", "15 decembrie la ora 9"))
    }

    @Test fun numeriCinesi() {
        assertEquals(25, LingueAltre.cjk("二十五"))
        assertEquals(20, LingueAltre.cjk("二十"))
        assertEquals(15, LingueAltre.cjk("十五"))
        assertEquals(t(11, 10, 20), at("zh", "二十分钟后"))
        assertEquals(t(11, 10, 25), at("ja", "二十五分後"))
        assertEquals(t(11, 23), at("zh", "晚上十一点"))
    }

    @Test fun oreThai() {
        assertEquals(t(11, 15), at("th", "บ่าย 3 โมง"))
        assertEquals(t(11, 13), at("th", "บ่ายโมง"))
        assertEquals(t(11, 17), at("th", "5 โมงเย็น"))
        assertEquals(t(11, 20), at("th", "2 ทุ่ม"))
        assertEquals(t(12, 2), at("th", "พรุ่งนี้ ตี 2"))
        assertEquals(t(11, 12), at("th", "เที่ยง"))
    }

    @Test fun nomiDeclinati() {
        lang = "cs"; assertTrue(LingueAltre.sameName("petrovi", "petr novak"))
        lang = "pl"; assertTrue(LingueAltre.sameName("marka", "marek"))
        assertTrue(LingueAltre.sameName("pawla", "pawel"))
        lang = "ru"; assertTrue(LingueAltre.sameName("маше", "маша"))
        lang = "fi"; assertTrue(LingueAltre.sameName("mikalle", "mika"))
        lang = "uk"; assertTrue(LingueAltre.sameName("олені", "олена"))
        lang = "pl"; assertFalse(LingueAltre.sameName("marka", "maria"))
        lang = "it"; assertFalse(LingueAltre.sameName("marka", "marek"))
    }

    @Test fun tendeNelleAltreLingue() {
        lang = "tr"
        assertFalse(HomeRisk.needsAuth(listOf("salon", "perdesini", "aç").map { Search.normalize(it) }, emptyList(), false, namesKnown = false))
        lang = "ja"
        assertFalse(HomeRisk.needsAuth(listOf("カーテンを開けて"), emptyList(), false, namesKnown = false))
        assertTrue(HomeRisk.needsAuth(listOf("ドアを開けて"), emptyList(), Intents.sensitive(listOf("ドアを開けて")), namesKnown = false))
        lang = "cs"
        assertFalse(HomeRisk.needsAuth(listOf("otevri", "zaluzie"), emptyList(), false, namesKnown = false))
    }
}
