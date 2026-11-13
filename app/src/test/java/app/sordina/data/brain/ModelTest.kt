package app.sordina.data.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

class ModelTest {
    private val zone = ZoneId.of("Europe/Rome")
    private val start = LocalDate.of(2026, 10, 5) // lunedì

    private fun at(day: Int, h: Int, m: Int) = start.plusDays(day.toLong()).atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    /** Una persona inventata, con abitudini e rumore: 4 settimane di gesti. */
    private fun life(seed: Int = 7): List<Pair<Situation, String>> {
        val r = Random(seed)
        val out = ArrayList<Pair<Situation, String>>()
        fun add(day: Int, h: Int, m: Int, item: String, wifi: String, car: Boolean = false, hs: Boolean = false, chg: Boolean = false, prev: String = "") {
            val mm = (m + r.nextInt(-12, 13)).coerceIn(0, 59)
            out += Situation(at(day, h, mm), wifi, car, hs, chg, prev, false, zone) to item
        }
        for (d in 0 until 28) {
            val weekend = d % 7 >= 5
            if (!weekend) {
                add(d, 7, 20, "app:news", "casa")
                add(d, 7, 40, "app:whatsapp", "casa", prev = "app:news")
                add(d, 8, 15, "app:maps", "", car = true)
                if (r.nextDouble() < 0.8) add(d, 8, 20, "app:spotify", "", car = true, prev = "app:maps")
                add(d, 9, 10, "app:slack", "ufficio")
                add(d, 10, 30, "app:mail", "ufficio", prev = "app:slack")
                add(d, 13, 10, "app:instagram", "ufficio")
                add(d, 15, 0, "app:slack", "ufficio")
                add(d, 18, 10, "app:maps", "", car = true)
                add(d, 21, 15, "app:netflix", "casa", chg = r.nextDouble() < 0.5)
                if (d % 7 == 2) add(d, 19, 30, "msg:mamma:WHATSAPP", "casa")
            } else {
                add(d, 10, 0, "app:instagram", "casa")
                add(d, 11, 0, "app:whatsapp", "casa")
                add(d, 16, 0, "app:spotify", "", hs = true)
                add(d, 21, 30, "app:netflix", "casa")
            }
            // WhatsApp a caso durante il giorno: l'app più usata, ma non sempre quella giusta
            repeat(4) { add(d, r.nextInt(8, 22), r.nextInt(60), "app:whatsapp", if (r.nextBoolean()) "casa" else "ufficio") }
        }
        return out.sortedBy { it.first.time }
    }

    /** Si allena gesto dopo gesto e, nell'ultima settimana, prova a indovinare il gesto prima di vederlo. */
    private fun replay(rank: (Model, Situation) -> List<String>): Pair<Double, Double> {
        val all = life()
        val model = Model(now = all.last().first.time)
        val testFrom = at(21, 0, 0)
        var n = 0; var top1 = 0; var top6 = 0
        for ((s, item) in all) {
            if (s.time >= testFrom) {
                val guess = rank(model, s)
                n++
                if (guess.firstOrNull() == item) top1++
                if (item in guess.take(6)) top6++
            }
            model.add(Gesture(s.time, item, s.features(learning = true)))
        }
        return top1.toDouble() / n to top6.toDouble() / n
    }

    private fun brain(m: Model, s: Situation): List<String> {
        val f = s.features(learning = false)
        return m.items.sortedByDescending { m.score(it, f) }
    }

    @Test fun learnsBetterThanMostUsed() {
        val (b1, b6) = replay(::brain)
        val (f1, f6) = replay { m, _ -> m.items.sortedByDescending { m.share(it) } }
        println("Cervello: prima scelta %.0f%%, nelle sei %.0f%%".format(b1 * 100, b6 * 100))
        println("Le più usate: prima scelta %.0f%%, nelle sei %.0f%%".format(f1 * 100, f6 * 100))
        // Il motore di prima: ora del giorno (campana di un'ora), feriale/festivo, recenza
        val past = ArrayList<Pair<Situation, String>>()
        val all = life()
        val testFrom = at(21, 0, 0)
        var n = 0; var o1 = 0; var o6 = 0
        for ((s, item) in all) {
            if (s.time >= testFrom) {
                val t = java.time.Instant.ofEpochMilli(s.time).atZone(zone)
                val score = HashMap<String, Double>()
                for ((ps, pi) in past) {
                    val pt = java.time.Instant.ofEpochMilli(ps.time).atZone(zone)
                    val dh = kotlin.math.abs((pt.hour + pt.minute / 60.0) - (t.hour + t.minute / 60.0)).let { minOf(it, 24 - it) }
                    val we = { d: java.time.DayOfWeek -> d.value >= 6 }
                    val age = (s.time - ps.time) / 86_400_000.0
                    score.merge(pi, Math.exp(-dh * dh / 2) * (if (we(pt.dayOfWeek) == we(t.dayOfWeek)) 1.0 else 0.35) * Math.pow(0.5, age / 14), Double::plus)
                }
                val g = score.entries.sortedByDescending { it.value }.map { it.key }
                n++; if (g.firstOrNull() == item) o1++; if (item in g.take(6)) o6++
            }
            past += s to item
        }
        println("Motore di prima: prima scelta %.0f%%, nelle sei %.0f%%".format(o1 * 100.0 / n, o6 * 100.0 / n))
        assertTrue("prima scelta $b1 contro $f1", b1 > f1 + 0.25)
        assertTrue("nelle sei $b6", b6 >= 0.9)
    }

    @Test fun situationsWin() {
        val all = life()
        val m = Model(now = all.last().first.time)
        all.forEach { (s, i) -> m.add(Gesture(s.time, i, s.features(true))) }
        fun top(s: Situation) = brain(m, s).first()
        assertEquals("app:maps", top(Situation(at(29, 8, 15), "", car = true, headset = false, charging = false, previous = "", fresh = false, zone = zone)))
        assertEquals("app:netflix", top(Situation(at(29, 21, 15), "casa", false, false, true, "", false, zone)))
        assertEquals("app:slack", top(Situation(at(29, 9, 10), "ufficio", false, false, false, "", false, zone)))
        assertEquals("app:news", top(Situation(at(29, 7, 20), "casa", false, false, false, "", false, zone)))
        // E sa dire perché: in auto
        assertEquals("car", m.reason("app:maps", Situation(at(29, 12, 0), "", true, false, false, "", false, zone).features(false))?.group)
    }

    @Test fun lettersLearn() {
        val m = Model(now = at(0, 9, 0))
        m.addChoice(at(0, 9, 0), "wa", "app:wallet")
        m.addChoice(at(0, 10, 0), "wha", "app:whatsapp")
        m.addChoice(at(0, 11, 0), "w", "app:whatsapp")
        // "w": quasi sempre WhatsApp; "wa": quella volta era Wallet
        assertEquals("app:whatsapp", m.typedItems("w", now = at(0, 12, 0)).first())
        assertEquals("app:wallet", m.typedItems("wa", now = at(0, 12, 0)).first())
        assertTrue(m.typed("w", "app:whatsapp", at(0, 12, 0)).first > 0.5)
    }

    @Test fun oldHabitsFade() {
        val m = Model(halfLifeDays = 21.0, now = at(60, 9, 0))
        // Un mese fa ogni mattina Twitter; da due settimane ogni mattina Threads
        for (d in 0 until 20) m.add(Gesture(at(d, 8, 0), "app:twitter", Situation(at(d, 8, 0), "casa", zone = zone).features(true)))
        for (d in 40 until 54) m.add(Gesture(at(d, 8, 0), "app:threads", Situation(at(d, 8, 0), "casa", zone = zone).features(true)))
        assertEquals("app:threads", brain(m, Situation(at(60, 8, 0), "casa", zone = zone)).first())
    }
}
