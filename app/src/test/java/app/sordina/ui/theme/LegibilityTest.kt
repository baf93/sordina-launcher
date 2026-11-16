package app.sordina.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegibilityTest {
    private fun l(v: Int) = Legibility.luminance(v, v, v)
    private fun zone(vararg grays: Int) = grays.map { l(it) }.toDoubleArray()

    @Test fun darkUniformBackgroundNeedsNoVeil() {
        val (dark, scrim) = Legibility.pick(zone(*IntArray(100) { 30 }))
        assertFalse(dark); assertEquals(0f, scrim, 0.001f)
    }

    @Test fun lightUniformBackgroundUsesDarkText() {
        val (dark, scrim) = Legibility.pick(zone(*IntArray(100) { 235 }))
        assertTrue(dark); assertEquals(0f, scrim, 0.001f)
    }

    /** Mezzo cielo chiaro e mezzo scuro: la media direbbe "va bene", il caso peggiore chiede un velo. */
    @Test fun mixedBackgroundGetsAVeil() {
        val (_, scrim) = Legibility.pick(zone(*(IntArray(50) { 20 } + IntArray(50) { 240 })))
        assertTrue("velo atteso, era $scrim", scrim > 0.2f)
    }

    /** Grigio medio: il testo scuro si legge già, niente velo. */
    @Test fun midGrayPrefersDarkText() {
        val (dark, scrim) = Legibility.pick(zone(*IntArray(100) { 140 }))
        assertTrue(dark); assertEquals(0f, scrim, 0.001f)
    }

    /** Il velo non supera mai il massimo, anche sul bianco e nero più netto. */
    @Test fun veilIsCapped() {
        val (_, scrim) = Legibility.pick(zone(*(IntArray(50) { 0 } + IntArray(50) { 255 })))
        assertTrue(scrim <= Legibility.MAX_SCRIM)
    }

    @Test fun phoneWallpaperKeepsTheFloor() {
        val (_, scrim) = Legibility.pick(zone(20, 25, 30), floor = 0.22f)
        assertEquals(0.22f, scrim, 0.001f)
    }
}
