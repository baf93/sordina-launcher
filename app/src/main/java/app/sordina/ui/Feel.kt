package app.sordina.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.runtime.staticCompositionLocalOf

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
