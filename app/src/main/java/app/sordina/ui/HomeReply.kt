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
 * La sordina che risponde per la casa, nel suo fumetto in fondo a Casa (lo disegna [app.sordina.ui.home.SordinaZone]):
 * qui solo quanto resta. Mentre aspetta al massimo 20 secondi; una risposta il tempo di leggerla, da 1,8 a 5 secondi.
 * Un tocco la manda via prima (lo ferma l'attività).
 */
@Composable
fun HomeReplyTimer(reply: HomeReply?, onGone: () -> Unit) {
    LaunchedEffect(reply?.id) {
        val now = reply ?: return@LaunchedEffect
        delay(if (now.text == null) 20_000L else (1800L + now.text.length * 30L).coerceAtMost(5_000L))
        onGone()
    }
}

/** Tre puntini che salgono e scendono a turno: la casa sta pensando. */
@Composable
internal fun Dots(anim: Boolean) {
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
internal fun Typed(text: String, id: Long, anim: Boolean, align: TextAlign = TextAlign.Center, maxLines: Int = Int.MAX_VALUE) {
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
    Text(shownText, maxLines = maxLines, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, color = c.ink, textAlign = align))
}
