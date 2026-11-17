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
import androidx.compose.material.icons.rounded.Close
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
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/** Le frasi del freno, in alto: una a caso ogni volta. Valgono per qualunque app. */
private val MOTTOS = listOf(
    R.string.brake_p1, R.string.brake_p2, R.string.brake_p3, R.string.brake_p4, R.string.brake_p5,
    R.string.brake_p6, R.string.brake_p7, R.string.brake_p8, R.string.brake_p9, R.string.brake_p10,
    R.string.brake_m1, R.string.brake_m2, R.string.brake_m3, R.string.brake_m4, R.string.brake_m5,
    R.string.brake_m6, R.string.brake_m7, R.string.brake_m8, R.string.brake_m9, R.string.brake_m10,
    R.string.brake_m11, R.string.brake_m12, R.string.brake_m13, R.string.brake_m14, R.string.brake_m15,
    R.string.brake_m16, R.string.brake_m17, R.string.brake_m18, R.string.brake_m19, R.string.brake_m20,
    R.string.brake_m21, R.string.brake_m22, R.string.brake_m23, R.string.brake_m24, R.string.brake_m25,
    R.string.brake_m26, R.string.brake_m27, R.string.brake_m28, R.string.brake_m29, R.string.brake_m30,
)

/** Il colore dei trofei: oro caldo, uguale in chiaro e in scuro. */
internal val GOLD = Color(0xFFE8A93A)
/** Il colore delle giornate perse e dei minuti in meno */
internal val LOSS = Color(0xFFE5484D)

/** Minuti col segno: "+12 min", "−8 min", "0 min". */
internal fun signed(m: Int): String = (if (m > 0) "+" else if (m < 0) "−" else "") + shortDuration(kotlin.math.abs(m))

/**
 * Attrito come una partita, con un saldo di giornata: "Lascia stare" ti fa riprendere i minuti di una visita,
 * "Apri comunque" (solo a fine attesa) te ne fa perdere di più. In positivo la giornata è tua, in negativo la perdi.
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
    /** Minuti persi aprendo comunque (vedi [Habits.openCost]) */
    cost: Int,
    onOpen: (habit: Boolean) -> Unit,
    /** Registra la rinuncia e dà il punteggio aggiornato */
    onSkip: () -> Pair<Habits.Streak, List<app.sordina.data.Medal>>,
    onDone: () -> Unit,
) {
    var after by remember { mutableStateOf<Pair<Habits.Streak, List<app.sordina.data.Medal>>?>(null) }
    val feel = LocalFeel.current
    run {
    AnimatedContent(after, transitionSpec = { fadeIn(tween(if (feel.animations) 300 else 0)) togetherWith fadeOut(tween(if (feel.animations) 200 else 0)) }, label = "brake") { a ->
        if (a == null) Playing(app, icon, today, seconds, streak, gain, cost, onOpen) {
            val s = onSkip()
            feel.confirm()
            after = s
        } else Won(gain, streak, a.first, a.second, onDone)
    }
    }
}


@Composable
private fun Playing(
    app: AppEntry, icon: ImageBitmap?, today: Pair<Int, Int>?, seconds: Int, streak: Habits.Streak, gain: Int, cost: Int,
    onOpen: (Boolean) -> Unit, onSkip: () -> Unit,
) {
    val c = S.c
    val feel = LocalFeel.current
    // La frase grande in alto: una a caso dalla raccolta
    val phrase = remember { tr(MOTTOS.random()) }
    // Un solo orologio per l'anello e per "Si apre tra N s": si chiudono insieme
    val total = seconds * 1000L
    var elapsed by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = android.os.SystemClock.uptimeMillis()
        while (elapsed < total) androidx.compose.runtime.withFrameMillis { elapsed = (android.os.SystemClock.uptimeMillis() - start).coerceAtMost(total) }
        feel.tick()
    }
    // Il numero cambia una volta al secondo; l'anello legge il tempo solo quando si disegna (niente ricomposizione a ogni fotogramma)
    val left by remember { androidx.compose.runtime.derivedStateOf { ((total - elapsed + 999) / 1000).toInt() } }
    val pulse = rememberInfiniteTransition(label = "pulse")
    val p by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
    // "Apri comunque": prima il sospiro della sordina, poi l'app
    var leaving by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun open() {
        if (leaving) return
        leaving = true
        if (feel.animations) scope.launch { delay(380); onOpen(false) } else onOpen(false)
    }
    val short = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 700
    val anim = feel.animations
    // Esercizio di respirazione: respiri lenti (circa 6 s l'uno) che finiscono, espirati, quando l'anello si chiude
    val breaths = maxOf(1, Math.round(seconds / 6f))
    val cycle = total / breaths
    /** A che punto del respiro: 0..1, prima metà inspira, seconda espira; -1 a esercizio finito */
    fun phaseAt(): Float = if (!anim || elapsed >= total || cycle <= 0) -1f else (elapsed % cycle).toFloat() / cycle
    fun breathAt(): Float = phaseAt().let { if (it < 0f) 0f else ((1 - cos(2 * Math.PI * it)) / 2).toFloat() }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // L'app e quanto l'hai già usata oggi
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            icon?.let { Image(it, null, Modifier.size(22.dp).clip(RoundedCornerShape(7.dp))); Spacer(Modifier.width(8.dp)) }
            Text(app.label, style = TextStyle(fontSize = 15.sp, color = c.ink))
            if (today != null) Text(
                "  ·  " + if (today.first == 0) tr(R.string.brake_not_today) else tr(R.string.brake_today, trn(R.plurals.n_times, today.first), shortDuration(today.second)),
                maxLines = 1, style = TextStyle(fontSize = 13.sp, color = c.soft),
            )
        }
        Spacer(Modifier.height(14.dp))
        Player(streak)
        // La frase subito sotto il pannello
        Text(
            phrase, Modifier.padding(top = 22.dp, start = 8.dp, end = 8.dp),
            textAlign = TextAlign.Center, style = TextStyle(fontSize = 24.sp, lineHeight = 31.sp, fontWeight = FontWeight.Medium, color = c.ink),
        )
        Spacer(Modifier.weight(1f))
        // Al centro la sordina, grande, dentro l'anello del tempo che manca ad "Apri"; subito sotto, il premio in palio
        Box(Modifier.size(if (short) 200.dp else 250.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 4.dp.toPx()
                // Il cerchio del respiro, dietro la sordina: si allarga inspirando, si stringe espirando
                val b = breathAt()
                drawCircle(c.accent.copy(alpha = 0.08f + 0.10f * b), radius = size.minDimension / 2 * (0.52f + 0.30f * b))
                drawCircle(c.line, radius = size.minDimension / 2 - w / 2, style = Stroke(w))
                drawArc(c.accent, -90f, 360f * (if (total > 0) elapsed.toFloat() / total else 1f), false,
                    topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w),
                    style = Stroke(w, cap = StrokeCap.Round))
            }
            Mascot(
                when { leaving -> Mood.SIGH; left > 0 -> Mood.WAIT; else -> Mood.READY },
                Modifier.fillMaxSize(0.76f),
                breath = if (left > 0 && !leaving) ::phaseAt else null,
            )
        }
        Row(
            Modifier.padding(top = 10.dp).clip(RoundedCornerShape(20.dp)).background(GOLD.copy(alpha = 0.16f)).padding(horizontal = 16.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Già premiato poco fa: niente "+0", si dice com'è
            if (gain > 0) {
                Text("+$gain ", style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.ink))
                Text(tr(R.string.brake_minutes_for_you), style = TextStyle(fontSize = 14.sp, color = c.soft))
            } else Text(tr(R.string.brake_recent), style = TextStyle(fontSize = 14.sp, color = c.soft))
        }
        Spacer(Modifier.weight(1f))
        // Il tasto che vince: grande, vivo, dice cosa incassi
        Box(
            Modifier.fillMaxWidth().scale(if (anim) 1f + 0.025f * p else 1f).height(68.dp).clip(RoundedCornerShape(34.dp))
                .background(c.accent).clickable { onSkip() },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(tr(R.string.leave_it), style = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = c.bg))
                // Se riporta la giornata in positivo, lo dice
                Text(
                    when {
                        gain == 0 -> tr(R.string.brake_recent)
                        streak.balance <= 0 && streak.balance + gain > 0 -> tr(R.string.brake_win_day, gain)
                        else -> tr(R.string.brake_win, gain)
                    },
                    style = TextStyle(fontSize = 12.sp, color = c.bg.copy(alpha = 0.8f)),
                )
            }
        }
        // Il costo si vede prima di scegliere
        Text(
            if (left > 0) tr(R.string.open_in, left) else tr(R.string.open_cost, cost),
            Modifier.padding(top = 6.dp).clip(RoundedCornerShape(16.dp))
                .then(if (left > 0) Modifier else Modifier.clickableNoRipple { open() })
                .padding(horizontal = 16.dp, vertical = 12.dp),
            style = TextStyle(fontSize = 14.sp, color = if (left > 0) c.dot else c.soft),
        )
    }
}

/** Il giocatore: livello con la barra verso il prossimo, la serie di giorni e la settimana. */
@Composable
internal fun Player(s: Habits.Streak, highlight: Boolean = false, before: Habits.Streak? = null) {
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
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr(R.string.brake_to_next, to - s.totalMinutes.coerceAtMost(to), s.level + 1),
                        Modifier.weight(1f), style = TextStyle(fontSize = 11.sp, color = c.soft))
                    // Il saldo di oggi: decide se la giornata è tua
                    Text(tr(R.string.brake_balance, signed(s.balance)), style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        color = if (s.balance > 0) c.accent else if (s.balance < 0) LOSS else c.soft))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Week(s.week)
    }
}

/** La settimana: ✓ le giornate in positivo, ✗ quelle in negativo, vuote le neutre e quelle da venire. */
@Composable
private fun Week(week: List<Int>) {
    val c = S.c
    val todayIdx = java.time.LocalDate.now().dayOfWeek.value - 1
    val letters = tr(R.string.week_letters).let { w -> if (w.contains(' ')) w.split(' ') else w.map { it.toString() } }.let { if (it.size == 7) it else List(7) { "" } }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        week.forEachIndexed { i, st ->
            Box(
                Modifier.size(30.dp).clip(CircleShape)
                    .background(if (st > 0) c.accent else if (st < 0) LOSS else if (i > todayIdx) c.bg.copy(alpha = 0.4f) else c.line)
                    .then(if (i == todayIdx && st == 0) Modifier.border(1.5.dp, c.accent, CircleShape) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                if (st > 0) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = c.bg)
                else if (st < 0) Icon(Icons.Rounded.Close, null, Modifier.size(16.dp), tint = c.bg)
                else Text(letters[i], style = TextStyle(fontSize = 11.sp, color = if (i == todayIdx) c.accent else c.soft))
            }
        }
    }
}

/** Hai vinto: coriandoli, quello che hai incassato, il livello che sale. Poi Casa. */
@Composable
private fun Won(gain: Int, before: Habits.Streak, s: Habits.Streak, medals: List<app.sordina.data.Medal>, onDone: () -> Unit) {
    val c = S.c
    val feel = LocalFeel.current
    val levelUp = s.level > before.level
    // Questa rinuncia ha riportato la giornata in positivo
    val dayWon = before.balance <= 0 && s.balance > 0
    val pop = remember { Animatable(if (feel.animations) 0f else 1f) }
    val burst = remember { Animatable(if (feel.animations) 0f else 1f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 260f)) }
    LaunchedEffect(Unit) { burst.animateTo(1f, tween(1600, easing = LinearEasing)) }
    LaunchedEffect(Unit) { if (levelUp || medals.isNotEmpty()) { delay(250); feel.confirm() }; delay(if (medals.isNotEmpty()) 4200 else if (levelUp) 3600 else 2800); onDone() }
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
            // La sordina salta e ride; accanto, il premio che hai incassato
            Mascot(Mood.WON, Modifier.size(160.dp))
            if (levelUp || gain > 0) Box(
                Modifier.align(Alignment.Center).padding(start = 116.dp, top = 84.dp).size(58.dp).scale(pop.value)
                    .clip(CircleShape).background(if (levelUp) GOLD else c.accent),
                contentAlignment = Alignment.Center,
            ) {
                if (levelUp) Icon(Icons.Rounded.EmojiEvents, null, Modifier.size(30.dp), tint = c.bg)
                else if (gain > 0) Text("+$gain", style = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold, color = c.bg))
            }
        }
        Text(
            if (levelUp) tr(R.string.brake_level_up, s.level) else tr(R.string.well_done),
            Modifier.padding(top = 8.dp), style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = c.ink),
        )
        Text(
            when {
                dayWon && s.days > 1 -> tr(R.string.brake_streak_up, "${s.days} ${trn(R.plurals.n_days_row, s.days)}")
                dayWon -> tr(R.string.brake_day_won)
                gain == 0 -> tr(R.string.brake_recent)
                else -> tr(R.string.time_back, shortDuration(gain))
            },
            Modifier.padding(top = 6.dp), textAlign = TextAlign.Center, style = TextStyle(fontSize = 16.sp, color = c.soft),
        )
        // Le medaglie appena vinte: arrivano con un piccolo salto
        if (medals.isNotEmpty()) Row(
            Modifier.padding(top = 14.dp).scale(pop.value).clip(RoundedCornerShape(20.dp)).background(GOLD.copy(alpha = 0.16f))
                .padding(start = 10.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            medals.take(3).forEach { Sticker(it, true, 44.dp, Modifier.padding(end = 4.dp)) }
            Text(
                tr(R.string.brake_new_medal, medals.map { medalName(it) }.joinToString(", ")),
                Modifier.padding(start = 6.dp), style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink),
            )
        }
        Spacer(Modifier.height(26.dp))
        Player(s, highlight = levelUp, before = before)
        Row(Modifier.padding(top = 10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat(signed(s.balance), tr(R.string.stat_today), Modifier.weight(1f))
            Stat(signed(s.weekMinutes), tr(R.string.stat_in_7_days), Modifier.weight(1f))
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

/** "45 min", "2 h", "7 h 20 min" */
internal fun shortDuration(min: Int): String = when {
    min < 1 -> tr(R.string.less_than_minute)
    min < 60 -> tr(R.string.minutes_short, min)
    min % 60 == 0 -> tr(R.string.hours_short, min / 60)
    else -> tr(R.string.hours_minutes_short, min / 60, min % 60)
}
