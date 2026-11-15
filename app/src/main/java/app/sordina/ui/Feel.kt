package app.sordina.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.animation.togetherWith

/**
 * Come risponde Sordina al tocco: animazioni e vibrazioni leggere.
 * Entrambe attive di serie, si spengono dalle impostazioni.
 */
class Feel(val animations: Boolean, val haptics: Boolean, private val view: View?) {
    private fun buzz(c: Int) { if (haptics) view?.performHapticFeedback(c) }

    /** Un tocco leggero: premi un pulsante, passi a un'altra scheda */
    fun tick() = buzz(HapticFeedbackConstants.CLOCK_TICK)

    /** Una conferma: segnata come letta, azione eseguita */
    fun confirm() = buzz(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)

    /** Superata una soglia (es. lo swipe che diventa "letta") */
    fun threshold() = buzz(if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE else HapticFeedbackConstants.CLOCK_TICK)

    /** L'animazione richiesta, oppure uno scatto immediato se le animazioni sono spente */
    fun <T> spec(a: AnimationSpec<T>): AnimationSpec<T> = if (animations) a else snap()
}

val LocalFeel = staticCompositionLocalOf { Feel(animations = true, haptics = true, view = null) }

/**
 * Un solo modo di muoversi per tutta l'app: stesse curve, stesse durate.
 * Ciò che entra parte deciso e si posa piano; ciò che esce se ne va in fretta, senza farsi notare.
 */
object Motion {
    val Ease = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Out = androidx.compose.animation.core.CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    const val FAST = 150
    const val NORMAL = 280

    fun <T> enter(ms: Int = NORMAL, delay: Int = 0) = androidx.compose.animation.core.tween<T>(ms, delay, Ease)
    fun <T> exit(ms: Int = FAST) = androidx.compose.animation.core.tween<T>(ms, 0, Out)

    /**
     * Tutte le transizioni sono fra pagine a schermo intero: senza sizeTransform = null Compose rimisurerebbe
     * la dimensione a ogni fotogramma per animarla, ma non cambia mai.
     */
    private fun page(enter: androidx.compose.animation.EnterTransition, exit: androidx.compose.animation.ExitTransition) =
        androidx.compose.animation.ContentTransform(enter, exit, sizeTransform = null)

    /** Una pagina più in fondo arriva da destra; tornando, quella prima rientra da sinistra. */
    fun forward(deeper: Boolean): androidx.compose.animation.ContentTransform {
        val d = if (deeper) 1 else -1
        return page(androidx.compose.animation.fadeIn(enter(220, 60)) + androidx.compose.animation.slideInHorizontally(enter(NORMAL + 40)) { it / 6 * d },
            androidx.compose.animation.fadeOut(exit()) + androidx.compose.animation.slideOutHorizontally(enter(NORMAL)) { -it / 12 * d })
    }

    /** Tra le schermate principali: Casa → ricerca → app si sale, tornando si scende. */
    fun vertical(up: Boolean): androidx.compose.animation.ContentTransform {
        val d = if (up) 1 else -1
        return page(androidx.compose.animation.fadeIn(enter(220, 60)) + androidx.compose.animation.slideInVertically(enter(NORMAL + 60)) { it / 14 * d },
            androidx.compose.animation.fadeOut(exit()) + androidx.compose.animation.slideOutVertically(enter(NORMAL)) { -it / 28 * d })
    }

    /** Un cambio sul posto: una dissolvenza e basta. */
    fun fade(): androidx.compose.animation.ContentTransform =
        page(androidx.compose.animation.fadeIn(enter(NORMAL, 60)), androidx.compose.animation.fadeOut(exit()))
}
