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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocalFireDepartment
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sordina.data.AppEntry
import app.sordina.data.Habits
import app.sordina.ui.theme.S
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/** Frasi di riserva, quando i tuoi numeri non hanno niente di meglio da dire. Valgono per qualunque app. */
private val PHRASES = listOf(
    R.string.brake_p1, R.string.brake_p2, R.string.brake_p3, R.string.brake_p4, R.string.brake_p5,
    R.string.brake_p6, R.string.brake_p7, R.string.brake_p8, R.string.brake_p9, R.string.brake_p10,
)

/** Il colore dei trofei: oro caldo, uguale in chiaro e in scuro. */
private val GOLD = Color(0xFFE8A93A)

/**
 * Attrito come una partita. In palio c'è un premio (i minuti che ti riprendi), il livello e la serie di giorni:
 * "Lascia stare" li incassa, "Apri" arriva solo a fine attesa e non vale niente.
 */
@Composable
fun BrakeScreen(
    app: AppEntry,
    icon: ImageBitmap?,
    /** Aperture e minuti di oggi; null senza accesso all'utilizzo */
    today: Pair<Int, Int>?,
    seconds: Int,
    streak: Habits.Streak,
    /** Minuti in palio: quanto dura di solito una visita */
    gain: Int,
    onOpen: (habit: Boolean) -> Unit,
    /** Registra la rinuncia */
    onSkip: () -> Unit,
    onDone: () -> Unit,
) {
    var after by remember { mutableStateOf<Habits.Streak?>(null) }
    val feel = LocalFeel.current
    AnimatedContent(after, transitionSpec = { fadeIn(tween(if (feel.animations) 300 else 0)) togetherWith fadeOut(tween(if (feel.animations) 200 else 0)) }, label = "brake") { a ->
        if (a == null) Playing(app, icon, today, seconds, streak, gain, onOpen) {
            onSkip()
            feel.confirm()
            after = streak.copy(
                today = streak.today + 1,
                days = if (streak.today == 0) streak.days + 1 else streak.days,
                weekMinutes = streak.weekMinutes + gain,
                totalMinutes = streak.totalMinutes + gain,
                week = streak.week.toMutableList().also { w -> (java.time.LocalDate.now().dayOfWeek.value - 1).let { if (it in w.indices) w[it] = true } },
            )
        } else Won(gain, streak, a, onDone)
    }
}

/** La frase in alto: prima i tuoi numeri (livello vicino, serie da salvare, tempo già speso), poi una frase qualunque. */
private fun hook(s: Habits.Streak, today: Pair<Int, Int>?, gain: Int): String = when {
    Habits.levelOf(s.totalMinutes + gain) > s.level -> tr(R.string.brake_hook_levelup, s.level + 1)
    s.today == 0 && s.days > 1 -> tr(R.string.brake_hook_keep, "${s.days} ${trn(R.plurals.n_days_row, s.days)}")
    s.today == 0 -> tr(R.string.brake_hook_first)
    today != null && today.second >= 30 -> tr(R.string.brake_hook_minutes, duration(today.second))
    today != null && today.first >= 3 -> tr(R.string.brake_hook_opens, today.first)
    else -> tr(PHRASES.random())
}

@Composable
private fun Playing(
    app: AppEntry, icon: ImageBitmap?, today: Pair<Int, Int>?, seconds: Int, streak: Habits.Streak, gain: Int,
    onOpen: (Boolean) -> Unit, onSkip: () -> Unit,
) {
    val c = S.c
    val feel = LocalFeel.current
    val phrase = remember { hook(streak, today, gain) }
    // Aprire costa: metà del premio, ma non ti fa mai scendere di livello
    val cost = remember { Habits.penalty(streak, gain) }
    var left by remember { mutableIntStateOf(seconds) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(seconds * 1000, easing = LinearEasing)) }
    LaunchedEffect(Unit) { while (left > 0) { delay(1000); left-- }; feel.tick() }
    val pulse = rememberInfiniteTransition(label = "pulse")
    val p by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
    val spin by pulse.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "spin")
    val anim = feel.animations

    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // L'app e quanto l'hai già usata oggi
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            icon?.let { Image(it, null, Modifier.size(22.dp).clip(RoundedCornerShape(7.dp))); Spacer(Modifier.width(8.dp)) }
            Text(app.label, style = TextStyle(fontSize = 15.sp, color = c.ink))
            if (today != null) Text(
                "  ·  " + if (today.first == 0) tr(R.string.brake_not_today) else tr(R.string.brake_today, trn(R.plurals.n_times, today.first), duration(today.second)),
                maxLines = 1, style = TextStyle(fontSize = 13.sp, color = c.soft),
            )
        }
        Spacer(Modifier.height(14.dp))
        Player(streak)
        Spacer(Modifier.weight(1f))
        Text(
            phrase, Modifier.padding(horizontal = 8.dp),
            textAlign = TextAlign.Center, style = TextStyle(fontSize = 24.sp, lineHeight = 31.sp, fontWeight = FontWeight.Medium, color = c.ink),
        )
        Spacer(Modifier.height(22.dp))
        // Il premio in palio: brilla e gira finché è lì. L'anello grigio intorno è il tempo che manca ad "Apri"
        Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2
                drawCircle(c.line, radius = r - 3.dp.toPx(), style = Stroke(3.dp.toPx()))
                drawArc(c.soft.copy(alpha = 0.6f), -90f, 360f * progress.value, false,
                    topLeft = Offset(3.dp.toPx(), 3.dp.toPx()), size = Size(size.width - 6.dp.toPx(), size.height - 6.dp.toPx()),
                    style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                // Scintille che orbitano
                rotate(if (anim) spin else 0f) {
                    for (i in 0 until 8) {
                        val a = Math.toRadians(i * 45.0)
                        val d = r * 0.78f
                        drawCircle(
                            (if (i % 2 == 0) GOLD else c.accent).copy(alpha = 0.55f + 0.45f * (if (i % 2 == 0) p else 1 - p)),
                            radius = (if (i % 2 == 0) 3.5f else 2.5f).dp.toPx(),
                            center = center + Offset((cos(a) * d).toFloat(), (sin(a) * d).toFloat()),
                        )
                    }
                }
            }
            Box(
                Modifier.size(128.dp).scale(if (anim) 0.94f + 0.06f * p else 1f).clip(CircleShape)
                    .background(c.accent.copy(alpha = 0.14f + 0.08f * p)).border(2.dp, c.accent.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("+$gain", style = TextStyle(fontSize = 46.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold, color = c.accent))
                    Text(tr(R.string.brake_minutes_for_you), style = TextStyle(fontSize = 12.sp, color = c.soft))
                }
            }
        }
        Spacer(Modifier.weight(1f))
        // Il tasto che vince: grande, vivo, dice cosa incassi
        Box(
            Modifier.fillMaxWidth().scale(if (anim) 1f + 0.025f * p else 1f).height(68.dp).clip(RoundedCornerShape(34.dp))
                .background(c.accent).clickable(onClick = onSkip),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(tr(R.string.leave_it), style = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = c.bg))
                Text(
                    if (streak.today == 0) tr(R.string.brake_win_streak, gain) else tr(R.string.brake_win, gain),
                    style = TextStyle(fontSize = 12.sp, color = c.bg.copy(alpha = 0.8f)),
                )
            }
        }
        Text(
            if (left > 0) tr(R.string.open_in, left) else if (cost > 0) tr(R.string.open_cost, cost) else tr(R.string.open_no_points),
            Modifier.padding(top = 6.dp).clip(RoundedCornerShape(16.dp))
                .then(if (left > 0) Modifier else Modifier.clickableNoRipple { onOpen(false) })
                .padding(horizontal = 16.dp, vertical = 12.dp),
            style = TextStyle(fontSize = 14.sp, color = if (left > 0) c.dot else c.soft),
        )
    }
}

/** Il giocatore: livello con la barra verso il prossimo, la serie di giorni e la settimana. */
@Composable
private fun Player(s: Habits.Streak, highlight: Boolean = false, before: Habits.Streak? = null) {
    val c = S.c
    val from = Habits.levelStart(s.level)
    val to = Habits.levelStart(s.level + 1)
    val frac = ((s.totalMinutes - from).toFloat() / (to - from)).coerceIn(0f, 1f)
    // Dopo una vittoria la barra parte da dove era e si riempie (da zero se sei salito di livello)
    val startFrac = before?.let { b -> if (b.level < s.level) 0f else ((b.totalMinutes - from).toFloat() / (to - from)).coerceIn(0f, 1f) } ?: frac
    val bar = remember { Animatable(startFrac) }
    LaunchedEffect(frac) { delay(350); bar.animateTo(frac, tween(1100, easing = FastOutSlowInEasing)) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.card).padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(if (highlight) GOLD else c.accent),
                contentAlignment = Alignment.Center,
            ) { Text("${s.level}", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.bg)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr(R.string.brake_level, s.level), Modifier.weight(1f), style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.ink))
                    Icon(Icons.Rounded.LocalFireDepartment, null, Modifier.size(18.dp), tint = if (s.days > 0) GOLD else c.dot)
                    Text("${s.days}", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = if (s.days > 0) GOLD else c.soft))
                }
                Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(8.dp).clip(CircleShape).background(c.line)) {
                    Box(Modifier.fillMaxWidth(bar.value).fillMaxHeight().clip(CircleShape).background(if (highlight) GOLD else c.accent))
                }
                Text(tr(R.string.brake_to_next, to - s.totalMinutes.coerceAtMost(to), s.level + 1),
                    Modifier.padding(top = 4.dp), style = TextStyle(fontSize = 11.sp, color = c.soft))
            }
        }
        Spacer(Modifier.height(12.dp))
        Week(s.week)
    }
}

/** La settimana: un segno pieno nei giorni in cui hai lasciato stare almeno una volta. */
@Composable
private fun Week(week: List<Boolean>) {
    val c = S.c
    val todayIdx = java.time.LocalDate.now().dayOfWeek.value - 1
    val letters = tr(R.string.week_letters).let { w -> if (w.contains(' ')) w.split(' ') else w.map { it.toString() } }.let { if (it.size == 7) it else List(7) { "" } }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        week.forEachIndexed { i, on ->
            Box(
                Modifier.size(30.dp).clip(CircleShape)
                    .background(if (on) c.accent else if (i > todayIdx) c.bg.copy(alpha = 0.4f) else c.line)
                    .then(if (i == todayIdx && !on) Modifier.border(1.5.dp, c.accent, CircleShape) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                if (on) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = c.bg)
                else Text(letters[i], style = TextStyle(fontSize = 11.sp, color = if (i == todayIdx) c.accent else c.soft))
            }
        }
    }
}

/** Hai vinto: coriandoli, quello che hai incassato, il livello che sale. Poi Casa. */
@Composable
private fun Won(gain: Int, before: Habits.Streak, s: Habits.Streak, onDone: () -> Unit) {
    val c = S.c
    val feel = LocalFeel.current
    val levelUp = s.level > before.level
    val newDay = before.today == 0
    val pop = remember { Animatable(if (feel.animations) 0f else 1f) }
    val burst = remember { Animatable(if (feel.animations) 0f else 1f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 260f)) }
    LaunchedEffect(Unit) { burst.animateTo(1f, tween(1600, easing = LinearEasing)) }
    LaunchedEffect(Unit) { if (levelUp) { delay(250); feel.confirm() }; delay(if (levelUp) 3600 else 2800); onDone() }
    // Coriandoli: direzioni e colori fissi per questa vittoria
    val bits = remember {
        val colors = listOf(GOLD, c.accent, c.ink, c.accent.copy(alpha = 0.6f))
        List(if (levelUp) 40 else 26) { i ->
            val a = Math.toRadians(-90.0 + (Math.random() - 0.5) * 300)
            Triple(a, 0.55f + Math.random().toFloat() * 0.6f, colors[i % colors.size])
        }
    }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().clickableNoRipple(onDone).padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r0 = size.minDimension / 2
                val t = burst.value
                drawCircle((if (levelUp) GOLD else c.accent).copy(alpha = 0.4f * (1 - t)), radius = r0 * (0.4f + 0.6f * t), style = Stroke(3.dp.toPx()))
                bits.forEachIndexed { i, (a, speed, color) ->
                    // Volano fuori e poi cadono
                    val d = r0 * speed * t
                    val pos = center + Offset((cos(a) * d).toFloat(), (sin(a) * d).toFloat() + r0 * 0.9f * t * t)
                    val alpha = (1 - t * t * t).coerceIn(0f, 1f)
                    rotate(t * 540f * (if (i % 2 == 0) 1 else -1), pos) {
                        if (i % 3 == 0) drawCircle(color.copy(alpha = alpha), 3.5.dp.toPx(), pos)
                        else drawRect(color.copy(alpha = alpha), pos - Offset(4.dp.toPx(), 2.dp.toPx()), Size(8.dp.toPx(), 4.dp.toPx()))
                    }
                }
            }
            Box(
                Modifier.size(112.dp).scale(pop.value).clip(CircleShape).background(if (levelUp) GOLD else c.accent),
                contentAlignment = Alignment.Center,
            ) {
                if (levelUp) Icon(Icons.Rounded.EmojiEvents, null, Modifier.size(58.dp), tint = c.bg)
                else Text("+$gain", style = TextStyle(fontSize = 38.sp, fontWeight = FontWeight.Bold, color = c.bg))
            }
        }
        Text(
            if (levelUp) tr(R.string.brake_level_up, s.level) else tr(R.string.well_done),
            Modifier.padding(top = 8.dp), style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = c.ink),
        )
        Text(
            when {
                newDay && s.days > 1 -> tr(R.string.brake_streak_up, "${s.days} ${trn(R.plurals.n_days_row, s.days)}")
                else -> tr(R.string.time_back, duration(gain))
            },
            Modifier.padding(top = 6.dp), textAlign = TextAlign.Center, style = TextStyle(fontSize = 16.sp, color = c.soft),
        )
        Spacer(Modifier.height(26.dp))
        Player(s, highlight = levelUp, before = before)
        Row(Modifier.padding(top = 10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("${s.today}", tr(R.string.stat_today), Modifier.weight(1f))
            Stat(duration(s.weekMinutes), tr(R.string.stat_in_7_days), Modifier.weight(1f))
        }
        Spacer(Modifier.weight(1f))
        Text(tr(R.string.back_home), Modifier.padding(bottom = 8.dp), style = TextStyle(fontSize = 13.sp, color = c.soft))
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    val c = S.c
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(c.card).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = c.accent))
        Text(label, style = TextStyle(fontSize = 11.sp, color = c.soft))
    }
}

private fun duration(min: Int): String = when {
    min < 1 -> tr(R.string.less_than_minute)
    min < 60 -> tr(R.string.minutes_short, min)
    min % 60 == 0 -> tr(R.string.hours_short, min / 60)
    else -> tr(R.string.hours_minutes_short, min / 60, min % 60)
}
