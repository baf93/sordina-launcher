package app.sordina.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sordina.R
import app.sordina.data.Medal
import app.sordina.tr
import app.sordina.ui.theme.S
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Un adesivo appena vinto, in cima a Casa: salta fuori, resta qualche secondo e se ne va da solo.
 * Toccato porta agli adesivi, con uno swipe in su se ne va; fuori dal riquadro i tocchi passano sotto e il telefono si usa come sempre.
 */
@Composable
fun Celebration(medals: List<Medal>, onOpen: () -> Unit, onDone: () -> Unit) {
    if (medals.isEmpty()) return
    val c = S.c
    val feel = LocalFeel.current
    val pop = remember(medals) { Animatable(if (feel.animations) 0f else 1f) }
    LaunchedEffect(medals) {
        feel.tick()
        if (feel.animations) pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f))
        delay(4200)
        if (feel.animations) pop.animateTo(0f, tween(260))
        onDone()
    }
    val names = medals.map { medalName(it) }
    // Uno swipe verso l'alto lo manda via prima del tempo
    val drag = remember(medals) { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = 12.dp, start = 16.dp, end = 16.dp), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
        Row(
            Modifier.graphicsLayer {
                val v = pop.value
                scaleX = 0.6f + 0.4f * v; scaleY = 0.6f + 0.4f * v; alpha = v.coerceIn(0f, 1f)
                translationY = (1f - v) * -40f + drag.floatValue
            }.pointerInput(medals) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (drag.floatValue < -36.dp.toPx()) scope.launch { if (feel.animations) pop.animateTo(0f, tween(180)); onDone() }
                        else drag.floatValue = 0f
                    },
                    onDragCancel = { drag.floatValue = 0f },
                ) { _, dy -> drag.floatValue = (drag.floatValue + dy).coerceAtMost(0f) }
            }.clip(RoundedCornerShape(30.dp)).background(c.card).clickableNoRipple(onOpen).padding(start = 10.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            medals.take(3).forEach { Sticker(it, true, 52.dp, Modifier.padding(end = 4.dp)) }
            Column(Modifier.padding(start = 8.dp)) {
                Text(
                    if (medals.size == 1) tr(R.string.ach_new) else tr(R.string.ach_new_many, medals.size),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.ink),
                )
                Text(names.joinToString(", "), maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 13.sp, color = c.soft))
            }
        }
    }
}
