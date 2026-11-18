package app.sordina.data

import app.sordina.data.Foreground.Ev
import app.sordina.data.Foreground.KEYGUARD_SHOWN
import app.sordina.data.Foreground.PAUSED
import app.sordina.data.Foreground.RESUMED
import app.sordina.data.Foreground.SCREEN_OFF
import app.sordina.data.Foreground.STOPPED
import org.junit.Assert.assertEquals
import org.junit.Test

/** Il tempo al telefono: deve tornare come Benessere digitale, non 19 ore invece di un'ora e mezza. */
class ForegroundTest {
    private fun r(pkg: String, cls: String, t: Long) = Ev(RESUMED, pkg, cls, t)
    private fun p(pkg: String, cls: String, t: Long) = Ev(PAUSED, pkg, cls, t)

    @Test fun dueSchermateDellaStessaAppNonLascianoIlContoAperto() {
        val evs = listOf(r("x", "A", 0), r("x", "B", 10), p("x", "A", 12), p("x", "B", 30))
        val res = Foreground.fold(evs, 0, 1000)
        assertEquals(30L, res.perPkg["x"]); assertEquals(30L, res.total)
    }

    @Test fun loSchermoSpentoChiudeTutto() {
        val evs = listOf(r("x", "A", 0), Ev(SCREEN_OFF, "android", null, 10))
        assertEquals(10L, Foreground.fold(evs, 0, 1000).total)
    }

    @Test fun ilBloccoChiudeTutto() {
        val evs = listOf(r("x", "A", 0), Ev(KEYGUARD_SHOWN, "android", null, 7), p("x", "A", 50))
        assertEquals(7L, Foreground.fold(evs, 0, 1000).total)
    }

    @Test fun dueAppInsiemeContanoUnaVoltaSola() {
        val evs = listOf(r("x", "A", 0), r("y", "B", 5), p("y", "B", 10), p("x", "A", 20))
        val res = Foreground.fold(evs, 0, 1000)
        assertEquals(20L, res.total); assertEquals(20L, res.perPkg["x"]); assertEquals(5L, res.perPkg["y"])
    }

    @Test fun sordinaNonContaNelTotale() {
        val evs = listOf(r("sordina", "M", 0), p("sordina", "M", 10), r("x", "A", 10), Ev(STOPPED, "x", "A", 15))
        assertEquals(5L, Foreground.fold(evs, 0, 1000, exclude = setOf("sordina")).total)
    }

    @Test fun unAppAncoraApertaContaFinoAdAdesso() {
        assertEquals(40L, Foreground.fold(listOf(r("x", "A", 60)), 0, 100).total)
    }

    @Test fun unaChiusuraSenzaAperturaNonConta() {
        assertEquals(0L, Foreground.fold(listOf(p("x", "A", 30)), 0, 100).total)
    }
}
