package app.sordina.ui

import app.sordina.R
import app.sordina.tr
import app.sordina.trn

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sordina.data.AppEntry
import app.sordina.data.Habits
import app.sordina.ui.theme.S
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/** Frasi brevi, una a caso ogni volta. */
private val PHRASES = listOf(
    R.string.brake_p1, R.string.brake_p2, R.string.brake_p3, R.string.brake_p4, R.string.brake_p5,
    R.string.brake_p6, R.string.brake_p7, R.string.brake_p8, R.string.brake_p9, R.string.brake_p10,
)

/**
 * Attrito, solo per le app scelte. Un cerchio che si riempie mentre respiri;
 * "Lascia stare" è il tasto grande. "Apri comunque" arriva solo a fine attesa.
 * Se lasci stare: un piccolo premio e i tuoi numeri.
 */
@Composable
fun BrakeScreen(
    app: AppEntry,
    icon: ImageBitmap?,
    /** Aperture e minuti di oggi; null senza accesso all'utilizzo */
    today: Pair<Int, Int>?,
    seconds: Int,
    streak: Habits.Streak,
    onOpen: (habit: Boolean) -> Unit,
    /** Registra la rinuncia e dice quanti minuti tornano */
    onSkip: () -> Int,
    onDone: () -> Unit,
) {
    var reward by remember { mutableStateOf<Pair<Int, Habits.Streak>?>(null) }
    val feel = LocalFeel.current
    AnimatedContent(reward, transitionSpec = { fadeIn(tween(if (feel.animations) 300 else 0)) togetherWith fadeOut(tween(if (feel.animations) 200 else 0)) }, label = "brake") { r ->
        if (r == null) Waiting(app, icon, today, seconds, streak, onOpen) {
            val minutes = onSkip()
            feel.confirm()
            reward = minutes to streak.copy(
                today = streak.today + 1,
                days = if (streak.today == 0) streak.days + 1 else streak.days,
                weekMinutes = streak.weekMinutes + minutes,
            )
        } else Reward(r.first, r.second, onDone)
    }
}

@Composable
private fun Waiting(
    app: AppEntry, icon: ImageBitmap?, today: Pair<Int, Int>?, seconds: Int, streak: Habits.Streak,
    onOpen: (Boolean) -> Unit, onSkip: () -> Unit,
) {
    val c = S.c
    val feel = LocalFeel.current
    val phrase = remember { tr(PHRASES.random()) }
    var left by remember { mutableIntStateOf(seconds) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(seconds * 1000, easing = LinearEasing)) }
    LaunchedEffect(Unit) { while (left > 0) { delay(1000); left-- }; feel.tick() }
    // Il respiro: 4 secondi dentro, 4 fuori
    val breath = rememberInfiniteTransition(label = "breath")
    val b by breath.animateFloat(
        0f, 1f, infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b",
    )
    val inhale = remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { while (true) { delay(4000); inhale.value = !inhale.value } }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 22.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(30.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let { Image(it, null, Modifier.size(26.dp).clip(RoundedCornerShape(8.dp))); Spacer(Modifier.width(8.dp)) }
            Text(app.label, style = TextStyle(fontSize = 16.sp, color = c.ink))
        }
        Text(
            when {
                today == null -> " "
                today.first == 0 -> tr(R.string.brake_not_today)
                else -> tr(R.string.brake_today, trn(R.plurals.n_times, today.first), duration(today.second))
            },
            Modifier.padding(top = 4.dp), style = TextStyle(fontSize = 14.sp, color = c.soft),
        )
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 6.dp.toPx()
                drawCircle(c.line, style = Stroke(w))
                drawArc(c.accent, -90f, 360f * progress.value, false, style = Stroke(w, cap = StrokeCap.Round))
            }
            Box(
                Modifier.size(128.dp).scale(if (feel.animations) 0.86f + 0.14f * b else 1f).clip(CircleShape)
                    .background(c.accent.copy(alpha = 0.16f)),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (left > 0) "$left" else "✓", style = TextStyle(fontSize = 40.sp, lineHeight = 44.sp, color = c.ink))
                Text(if (inhale.value) tr(R.string.inhale) else tr(R.string.exhale), style = TextStyle(fontSize = 13.sp, color = c.soft))
            }
        }
        Text(
            phrase, Modifier.padding(top = 28.dp, start = 12.dp, end = 12.dp),
            textAlign = TextAlign.Center, style = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, color = c.ink),
        )
        Spacer(Modifier.weight(1f))
        Week(streak.week)
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(27.dp)).background(c.accent).clickable(onClick = onSkip),
            contentAlignment = Alignment.Center,
        ) { Text(tr(R.string.leave_it), style = TextStyle(fontSize = 17.sp, color = c.bg)) }
        Text(
            if (left > 0) tr(R.string.open_in, left) else tr(R.string.open_anyway),
            Modifier.padding(top = 6.dp).clip(RoundedCornerShape(16.dp))
                .then(if (left > 0) Modifier else Modifier.clickableNoRipple { onOpen(false) })
                .padding(horizontal = 16.dp, vertical = 12.dp),
            style = TextStyle(fontSize = 15.sp, color = if (left > 0) c.dot else c.soft),
        )
    }
}

/** La settimana: un segno pieno nei giorni in cui hai lasciato stare almeno una volta. */
@Composable
private fun Week(week: List<Boolean>) {
    val c = S.c
    val todayIdx = java.time.LocalDate.now().dayOfWeek.value - 1
    val letters = tr(R.string.week_letters).let { w -> if (w.contains(' ')) w.split(' ') else w.map { it.toString() } }.let { if (it.size == 7) it else List(7) { "" } }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        week.forEachIndexed { i, on ->
            Box(
                Modifier.size(28.dp).clip(CircleShape)
                    .background(if (on) c.accent else if (i > todayIdx) c.card else c.line)
                    .then(if (i == todayIdx && !on) Modifier.border(1.5.dp, c.accent, CircleShape) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                if (on) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = c.bg)
                else Text(letters[i], style = TextStyle(fontSize = 11.sp, color = if (i == todayIdx) c.accent else c.soft))
            }
        }
    }
}

/** Il premio: un segno di spunta che sboccia, due parole, i tuoi numeri. Poi Casa. */
@Composable
private fun Reward(minutes: Int, s: Habits.Streak, onDone: () -> Unit) {
    val c = S.c
    val feel = LocalFeel.current
    val pop = remember { Animatable(if (feel.animations) 0f else 1f) }
    val burst = remember { Animatable(if (feel.animations) 0f else 1f) }
    LaunchedEffect(Unit) {
        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f))
    }
    LaunchedEffect(Unit) { burst.animateTo(1f, tween(900)) }
    LaunchedEffect(Unit) { delay(2600); onDone() }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().clickableNoRipple(onDone).padding(horizontal = 22.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                val r0 = size.minDimension / 2
                // Anelli che si allargano e svaniscono
                drawCircle(c.accent.copy(alpha = 0.35f * (1 - burst.value)), radius = r0 * (0.5f + 0.5f * burst.value), style = Stroke(2.dp.toPx()))
                drawCircle(c.accent.copy(alpha = 0.2f * (1 - burst.value)), radius = r0 * (0.35f + 0.65f * burst.value), style = Stroke(2.dp.toPx()))
                // Puntini che volano via
                for (i in 0 until 10) {
                    val a = Math.toRadians(i * 36.0 + 12)
                    val d = r0 * (0.45f + 0.5f * burst.value)
                    drawCircle(
                        (if (i % 2 == 0) c.accent else c.ink).copy(alpha = 1 - burst.value * 0.7f),
                        radius = (if (i % 3 == 0) 4 else 3).dp.toPx(),
                        center = center + Offset((cos(a) * d).toFloat(), (sin(a) * d).toFloat()),
                    )
                }
            }
            Box(
                Modifier.size(104.dp).scale(pop.value).clip(CircleShape).background(c.accent),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Check, tr(R.string.done), Modifier.size(56.dp), tint = c.bg) }
        }
        Text(tr(R.string.well_done), Modifier.padding(top = 18.dp), style = TextStyle(fontSize = 30.sp, color = c.ink))
        Text(
            tr(R.string.time_back, duration(minutes)),
            Modifier.padding(top = 6.dp), textAlign = TextAlign.Center, style = TextStyle(fontSize = 16.sp, color = c.soft),
        )
        Row(Modifier.padding(top = 30.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("${s.today}", tr(R.string.stat_today))
            Stat("${s.days}", trn(R.plurals.n_days_row, s.days))
            Stat(duration(s.weekMinutes), tr(R.string.stat_in_7_days))
        }
        Spacer(Modifier.weight(1f))
        Text(tr(R.string.back_home), Modifier.padding(bottom = 12.dp), style = TextStyle(fontSize = 13.sp, color = c.soft))
    }
}

@Composable
private fun Stat(value: String, label: String) {
    val c = S.c
    Column(
        Modifier.clip(RoundedCornerShape(18.dp)).background(c.card).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = TextStyle(fontSize = 22.sp, color = c.accent))
        Text(label, style = TextStyle(fontSize = 11.sp, color = c.soft))
    }
}

private fun duration(min: Int): String = when {
    min < 1 -> tr(R.string.less_than_minute)
    min < 60 -> tr(R.string.minutes_short, min)
    min % 60 == 0 -> tr(R.string.hours_short, min / 60)
    else -> tr(R.string.hours_minutes_short, min / 60, min % 60)
}
