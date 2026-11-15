package app.sordina.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sordina.ui.theme.S
import kotlinx.coroutines.delay

/**
 * Una frase di casa in corso: [text] null mentre la casa ci pensa, poi la risposta; [ok] com'è andata.
 * L'id distingue due risposte uguali di fila.
 */
data class HomeReply(val text: String?, val ok: Boolean = true, val id: Long = System.nanoTime())

/**
 * La sordina che risponde per la casa: sale a colori da dove sta quella piccola in fondo,
 * concentrata mentre aspetta (fumetto con i puntini), poi salta contenta e scrive la risposta, o sospira se non è andata.
 * Resta il tempo di leggerla, poi torna giù; un tocco la manda via prima.
 *
 * Batteria: si muove solo nei pochi secondi in cui è sullo schermo, poi non resta niente acceso.
 */
@Composable
fun BoxScope.HomeReplyView(reply: HomeReply?, onGone: () -> Unit) {
    val c = S.c
    val anim = LocalFeel.current.animations
    // Quella mostrata resta anche mentre esce, così il fumetto non si svuota durante la discesa
    var shown by remember { mutableStateOf(reply) }
    if (reply != null) shown = reply
    val r = shown ?: return

    LaunchedEffect(reply?.id) {
        val now = reply ?: return@LaunchedEffect
        // Mentre aspetta al massimo 20 secondi; una risposta resta giusto il tempo di leggerla: da 1,8 a 5 secondi
        delay(if (now.text == null) 20_000L else (1800L + now.text.length * 30L).coerceAtMost(5_000L))
        onGone()
    }

    AnimatedVisibility(
        reply != null,
        Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 196.dp, start = 24.dp, end = 24.dp),
        enter = if (anim) fadeIn(tween(160)) + slideInVertically(spring(dampingRatio = 0.62f, stiffness = 380f)) { it / 2 } else fadeIn(tween(0)),
        // Esce in fretta: se l'hai toccata vuoi fare altro, non guardarla andare via
        exit = if (anim) fadeOut(tween(120)) + slideOutVertically(tween(160)) { it / 4 } else fadeOut(tween(0)),
    ) {
        // Il tocco che la manda via lo ferma l'attività (dispatchTouchEvent): qui niente, così non ruba i tocchi dopo mentre esce
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Il fumetto spunta dalla sordina, con la punta verso di lei
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .graphicsLayer { transformOrigin = TransformOrigin(0.5f, 1f) }
                        .widthIn(min = 72.dp, max = 320.dp)
                        .clip(RoundedCornerShape(22.dp)).background(c.card)
                        .animateContentSize(spring(dampingRatio = 0.8f, stiffness = 500f))
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (r.text == null) Dots(anim) else Typed(r.text, r.id, anim)
                }
                Box(Modifier.size(width = 18.dp, height = 9.dp).background(c.card, Tail))
            }
            Spacer(Modifier.size(2.dp))
            // Concentrata e respira finché aspetta; poi salta tra le stelline, o un sospiro
            val mood = when {
                r.text == null -> Mood.WAIT
                r.ok -> Mood.DONE
                else -> Mood.SIGH
            }
            Mascot(mood, Modifier.size(104.dp))
        }
    }
}

/** La punta del fumetto, verso il basso. */
private val Tail = GenericShape { size, _ ->
    moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2f, size.height); close()
}

/** Tre puntini che salgono e scendono a turno: la casa sta pensando. */
@Composable
private fun Dots(anim: Boolean) {
    val c = S.c
    val t = if (anim) rememberInfiniteTransition(label = "dots").animateFloat(
        0f, 3f, infiniteRepeatable(tween(1100), RepeatMode.Restart), label = "t",
    ).value else 0f
    Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            // Ogni puntino ha il suo turno: sale e si accende, poi torna giù
            val d = ((t - i + 3f) % 3f).let { if (it < 1f) kotlin.math.sin(it * Math.PI).toFloat() else 0f }
            Box(
                Modifier.size(8.dp)
                    .graphicsLayer { translationY = -5.dp.toPx() * d }
                    .clip(CircleShape).background(c.ink.copy(alpha = 0.35f + 0.55f * d)),
            )
        }
    }
}

/** La risposta scritta lettera per lettera, come se la sordina la stesse dicendo (al massimo un secondo). */
@Composable
private fun Typed(text: String, id: Long, anim: Boolean) {
    val c = S.c
    var n by remember(id) { mutableIntStateOf(if (anim) 0 else text.length) }
    LaunchedEffect(id) {
        if (!anim) return@LaunchedEffect
        val step = (1000L / text.length.coerceAtLeast(1)).coerceIn(8L, 28L)
        while (n < text.length) { delay(step); n++ }
    }
    // Un solo testo: la parte ancora da dire è trasparente, così le righe sono già al loro posto e non saltano
    val shownText = buildAnnotatedString {
        append(text.take(n))
        withStyle(SpanStyle(color = c.ink.copy(alpha = 0f))) { append(text.drop(n)) }
    }
    Text(shownText, style = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, color = c.ink, textAlign = TextAlign.Center))
}
