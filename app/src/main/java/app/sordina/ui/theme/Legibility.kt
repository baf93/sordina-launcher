package app.sordina.ui.theme

import kotlin.math.pow

/**
 * Quale testo si legge su uno sfondo, e quanto velo serve sotto. Niente Android: si prova con i test.
 *
 * Si guarda il caso peggiore, non la media: il testo bianco deve leggersi anche sul punto più chiaro della zona,
 * quello scuro anche sul più scuro. Se nessuno dei due basta, sotto le scritte scende un velo sfumato
 * (scuro per il testo bianco, chiaro per quello scuro) quanto basta, mai più di [MAX_SCRIM].
 */
object Legibility {
    /** Luminanza relativa (0..1) di un colore sRGB 0..255. */
    fun luminance(r: Int, g: Int, b: Int): Double {
        fun lin(c: Int): Double { val s = c / 255.0; return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
    }

    fun contrast(a: Double, b: Double): Double = (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)

    /** Il testo chiaro (bianco) e quello scuro di Sordina. */
    private const val LIGHT_TEXT = 1.0
    private const val DARK_TEXT = 0.007

    /** Contrasto da raggiungere: quello del testo normale (le righe dell'agenda sono piccole). */
    const val TARGET = 4.5
    const val MAX_SCRIM = 0.55f

    /** Testo scuro? e velo (0..[MAX_SCRIM]) per una zona, dalle luminanze dei suoi punti. [floor]: velo minimo (sfondo che non si vede). */
    fun pick(samples: DoubleArray, floor: Float = 0f): Pair<Boolean, Float> {
        if (samples.isEmpty()) return false to floor
        val s = samples.sorted()
        // I punti più chiari e più scuri (il 10% agli estremi), non il singolo pixel
        val bright = s[(s.size * 0.9).toInt().coerceAtMost(s.lastIndex)]
        val dark = s[(s.size * 0.1).toInt()]
        val whiteWorst = contrast(LIGHT_TEXT, bright)
        val darkWorst = contrast(DARK_TEXT, dark)
        val useDark = darkWorst > whiteWorst
        val scrim = if (useDark) {
            // Velo chiaro: L' = L + a(1 - L) deve arrivare a (TARGET * 0.057 - 0.05)
            val need = TARGET * (DARK_TEXT + 0.05) - 0.05
            if (dark >= need) 0f else ((need - dark) / (1 - dark)).toFloat()
        } else {
            // Velo scuro: L' = L (1 - a) deve scendere a (1.05 / TARGET - 0.05)
            val need = (LIGHT_TEXT + 0.05) / TARGET - 0.05
            if (bright <= need) 0f else (1 - need / bright).toFloat()
        }
        return useDark to scrim.coerceIn(floor, MAX_SCRIM)
    }
}
