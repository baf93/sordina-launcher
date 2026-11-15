package app.sordina.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import app.sordina.data.Medal
import app.sordina.data.Medal.Group

/*
 * Gli adesivi della mascotte: uno per impresa, ognuno diverso. La sordina con la sua espressione, a volte un
 * cappello, e accanto l'oggetto dell'impresa (le orme del primo passo, la clessidra, il libro, la torta…).
 * Il tondo ha il colore del gruppo, l'anello quello della difficoltà (bronzo, argento, oro), il bordo bianco
 * da adesivo. Bloccato è grigio e trasparente. Fermo: niente batteria.
 * Lo stesso disegno diventa un'immagine 512×512 da mandare su WhatsApp o Telegram.
 */

private val RING = listOf(Color(0xFFD39A62), Color(0xFFBFC6D1), Color(0xFFF2C94C))
private val RED = Color(0xFFD9483B)
private val FLAME = Color(0xFFF08A24)
private val SKY = Color(0xFF6FA8E8)
private val NAVY = Color(0xFF34406B)
private val GREEN = Color(0xFF4CAF6E)
private val AMBER = Color(0xFFF2C94C)
private val PINK = Color(0xFFE8697A)
private val WOOD = Color(0xFF9C6B3F)
private val SKIN = Color(0xFFF6C9A0)
private val INK = Color(0xFF2B2F36)
private val WHITE = Color(0xFFFFFFFF)

private fun bg(g: Group) = when (g) {
    Group.BRAKE -> Color(0xFFFFDCD2); Group.CLEAN -> Color(0xFFD6ECFA); Group.TIME -> Color(0xFFD4F0DC)
    Group.SERIES -> Color(0xFFFFE3C2); Group.LEVEL -> Color(0xFFE6DDF7); Group.PHONE -> Color(0xFFF2E6D8)
    Group.NOTIF -> Color(0xFFFCE1EA); Group.CHOICE -> Color(0xFFE3F1D1); Group.YEARS -> Color(0xFFFFF0B8)
    Group.DRIVE -> Color(0xFFDDE5EE)
}

private enum class Face { HAPPY, LAUGH, CALM, WOW, UP, FOCUS, GLASSES, BREATH, COOL, STARS, WINK, SLEEP, LOVE }
private enum class Hat { NONE, CROWN, PARTY, NIGHTCAP, HALO, HEADPHONES }
private enum class Prop {
    STEPS, HAND, STOP, STAR, SHIELD, SUNRISE, PLATE, MOON, UMBRELLA, CHECKS, LEAF, WAVES, NIGHTSKY,
    CLOCK, KITE, BOOK, HOURGLASS, SUNMOON, TROPHY, FLAME, CALENDAR, CALENDAR_FULL, ARROW_UP, MEDAL, LADDER, ROCKET,
    MOUNTAIN, DIAMOND, SPARKLES, PHONE_DOWN, PHONE_ZZZ, FLOWER, LOCK, ALARM, SUNSET, STARMOON, TREE, ARROW_DOWN, CUP,
    BELL, ENVELOPES, CHECK, PEOPLE, SCROLL, TARGET, BULB, ZZZ, HEART, BOX, WAVE_HAND, WHEEL, GIFT, BALLOONS, CAKE,
    DICE, BUBBLES, RAINBOW, BUTTERFLY, TENT, SEEDLING, DANDELION, LOTUS, CASTLE, PALETTE, GUITAR, PLANET, PODIUM, FIREWORKS,
    CONFETTI, HAMMOCK, SNAIL, HALF, BIKE, SAILBOAT, KEY, FEATHER, SUN, PIZZA, TOAST, BED, CAR, ROAD, TRAFFIC_LIGHT, FLAG_RACE,
    SIGNPOST, STREETLAMP, HOUSE, MAP_PIN, MAILBOX, TRAY, PENCIL, STONES, OWL,
}

private class Look(val face: Face, val prop: Prop, val hat: Hat = Hat.NONE)

private fun look(m: Medal): Look = when (m) {
    Medal.FIRST -> Look(Face.HAPPY, Prop.STEPS)
    Medal.TEN -> Look(Face.LAUGH, Prop.HAND)
    Medal.SKIP_100 -> Look(Face.COOL, Prop.STOP)
    Medal.SKIP_500 -> Look(Face.WOW, Prop.STOP, Hat.PARTY)
    Medal.SKIP_1000 -> Look(Face.STARS, Prop.STOP, Hat.CROWN)
    Medal.PERFECT -> Look(Face.STARS, Prop.STAR)
    Medal.LOYAL -> Look(Face.HAPPY, Prop.SHIELD)
    Medal.SKIP_RUN -> Look(Face.COOL, Prop.DICE)
    Medal.MORNING -> Look(Face.HAPPY, Prop.SUNRISE)
    Medal.LUNCH -> Look(Face.LAUGH, Prop.PLATE)
    Medal.NIGHT -> Look(Face.CALM, Prop.MOON, Hat.NIGHTCAP)
    Medal.WEEKEND -> Look(Face.COOL, Prop.UMBRELLA)
    Medal.ALL_OF_THEM -> Look(Face.LAUGH, Prop.CHECKS)
    Medal.CLEAN_1 -> Look(Face.HAPPY, Prop.BUBBLES)
    Medal.CLEAN_3 -> Look(Face.BREATH, Prop.LEAF)
    Medal.CLEAN_7 -> Look(Face.STARS, Prop.RAINBOW)
    Medal.CLEAN_14 -> Look(Face.LOVE, Prop.BUTTERFLY)
    Medal.CLEAN_30 -> Look(Face.CALM, Prop.WAVES, Hat.HALO)
    Medal.CLEAN_WEEKEND -> Look(Face.COOL, Prop.TENT)
    Medal.CLEAN_10 -> Look(Face.WINK, Prop.SEEDLING)
    Medal.CLEAN_50 -> Look(Face.UP, Prop.DANDELION)
    Medal.CLEAN_100 -> Look(Face.CALM, Prop.LOTUS)
    Medal.CLEAN_365 -> Look(Face.STARS, Prop.CASTLE, Hat.CROWN)
    Medal.HOUR -> Look(Face.WOW, Prop.CLOCK)
    Medal.DAY_2H -> Look(Face.LAUGH, Prop.PALETTE)
    Medal.WEEK_3H -> Look(Face.HAPPY, Prop.KITE)
    Medal.MONTH_6H -> Look(Face.GLASSES, Prop.BOOK)
    Medal.TOTAL_10H -> Look(Face.HAPPY, Prop.HOURGLASS)
    Medal.TOTAL_24H -> Look(Face.LAUGH, Prop.SUNMOON)
    Medal.TOTAL_50H -> Look(Face.LOVE, Prop.GUITAR)
    Medal.TOTAL_100H -> Look(Face.STARS, Prop.TROPHY, Hat.CROWN)
    Medal.SERIES_3 -> Look(Face.HAPPY, Prop.FLAME)
    Medal.SERIES_7 -> Look(Face.LAUGH, Prop.FLAME)
    Medal.SERIES_14 -> Look(Face.COOL, Prop.FLAME)
    Medal.SERIES_30 -> Look(Face.STARS, Prop.FLAME)
    Medal.SERIES_100 -> Look(Face.STARS, Prop.FLAME, Hat.CROWN)
    Medal.SERIES_365 -> Look(Face.LOVE, Prop.FLAME, Hat.HALO)
    Medal.WEEK -> Look(Face.HAPPY, Prop.CALENDAR)
    Medal.MONTH -> Look(Face.LAUGH, Prop.CALENDAR_FULL)
    Medal.COMEBACK -> Look(Face.WINK, Prop.ARROW_UP)
    Medal.LEVEL_2 -> Look(Face.HAPPY, Prop.MEDAL)
    Medal.LEVEL_5 -> Look(Face.WOW, Prop.LADDER)
    Medal.LEVEL_10 -> Look(Face.LAUGH, Prop.ROCKET)
    Medal.LEVEL_20 -> Look(Face.COOL, Prop.MOUNTAIN)
    Medal.LEVEL_30 -> Look(Face.LOVE, Prop.SPARKLES, Hat.CROWN)
    Medal.LEVEL_50 -> Look(Face.STARS, Prop.DIAMOND, Hat.CROWN)
    Medal.LEVEL_75 -> Look(Face.WOW, Prop.PLANET, Hat.CROWN)
    Medal.LEVEL_100 -> Look(Face.LOVE, Prop.PODIUM, Hat.CROWN)
    Medal.UNDER_3H -> Look(Face.HAPPY, Prop.PHONE_DOWN)
    Medal.UNDER_2H -> Look(Face.CALM, Prop.PHONE_ZZZ)
    Medal.UNDER_1H -> Look(Face.COOL, Prop.FLOWER)
    Medal.WEEK_UNDER_2H -> Look(Face.COOL, Prop.HAMMOCK)
    Medal.MONTH_UNDER_3H -> Look(Face.HAPPY, Prop.SNAIL)
    Medal.HALF_DAY -> Look(Face.WOW, Prop.HALF)
    Medal.DOWNHILL -> Look(Face.HAPPY, Prop.ARROW_DOWN)
    Medal.SLOW_SUNDAY -> Look(Face.CALM, Prop.CUP)
    Medal.LIGHT_WEEKEND -> Look(Face.LAUGH, Prop.BIKE)
    Medal.FEW_UNLOCKS -> Look(Face.WINK, Prop.LOCK)
    Medal.UNLOCKS_15 -> Look(Face.WINK, Prop.KEY)
    Medal.FEW_UNLOCKS_7 -> Look(Face.CALM, Prop.FEATHER)
    Medal.SLOW_MORNING -> Look(Face.SLEEP, Prop.ALARM, Hat.NIGHTCAP)
    Medal.FRESH_AIR -> Look(Face.LAUGH, Prop.TREE)
    Medal.FRESH_AIR_5 -> Look(Face.LAUGH, Prop.SUN)
    Medal.OFFLINE_6H -> Look(Face.HAPPY, Prop.SAILBOAT)
    Medal.DINNER -> Look(Face.LAUGH, Prop.PIZZA)
    Medal.DINNER_7 -> Look(Face.LOVE, Prop.TOAST)
    Medal.SUNSET -> Look(Face.CALM, Prop.SUNSET)
    Medal.SUNSET_7 -> Look(Face.SLEEP, Prop.STARMOON)
    Medal.NIGHT_OFF -> Look(Face.SLEEP, Prop.NIGHTSKY)
    Medal.NIGHT_OFF_7 -> Look(Face.SLEEP, Prop.BED, Hat.NIGHTCAP)
    Medal.ROAD_1 -> Look(Face.HAPPY, Prop.CAR)
    Medal.ROAD_10 -> Look(Face.WOW, Prop.WHEEL)
    Medal.ROAD_100 -> Look(Face.LOVE, Prop.MAP_PIN, Hat.CROWN)
    Medal.HANDS_FREE -> Look(Face.FOCUS, Prop.ROAD)
    Medal.HANDS_FREE_10 -> Look(Face.COOL, Prop.TRAFFIC_LIGHT)
    Medal.HANDS_FREE_50 -> Look(Face.STARS, Prop.FLAG_RACE, Hat.CROWN)
    Medal.LONG_DRIVE -> Look(Face.WOW, Prop.SIGNPOST)
    Medal.NIGHT_DRIVE -> Look(Face.CALM, Prop.STREETLAMP)
    Medal.ARRIVED -> Look(Face.LAUGH, Prop.HOUSE)
    Medal.HELD_100 -> Look(Face.HAPPY, Prop.BELL)
    Medal.HELD_1000 -> Look(Face.COOL, Prop.ENVELOPES)
    Medal.HELD_10000 -> Look(Face.STARS, Prop.MAILBOX, Hat.CROWN)
    Medal.READ_ALL -> Look(Face.LAUGH, Prop.CHECK)
    Medal.READ_ALL_10 -> Look(Face.HAPPY, Prop.TRAY)
    Medal.PEOPLE -> Look(Face.LOVE, Prop.PEOPLE)
    Medal.FIRST_RULE -> Look(Face.GLASSES, Prop.SCROLL)
    Medal.FOCUS -> Look(Face.FOCUS, Prop.TARGET)
    Medal.FOCUS_10 -> Look(Face.FOCUS, Prop.PENCIL)
    Medal.DEEP_WORK -> Look(Face.FOCUS, Prop.BULB, Hat.HEADPHONES)
    Medal.QUIET_DAY -> Look(Face.CALM, Prop.STONES)
    Medal.REST_7 -> Look(Face.SLEEP, Prop.ZZZ, Hat.NIGHTCAP)
    Medal.REST_30 -> Look(Face.SLEEP, Prop.OWL)
    Medal.BRAKE_3 -> Look(Face.HAPPY, Prop.HEART)
    Medal.HIDE -> Look(Face.WINK, Prop.BOX)
    Medal.FAREWELL -> Look(Face.HAPPY, Prop.WAVE_HAND)
    Medal.WEEK_1 -> Look(Face.UP, Prop.CONFETTI, Hat.PARTY)
    Medal.MONTH_1 -> Look(Face.LAUGH, Prop.GIFT, Hat.PARTY)
    Medal.MONTHS_6 -> Look(Face.STARS, Prop.BALLOONS, Hat.PARTY)
    Medal.YEAR_1 -> Look(Face.LOVE, Prop.CAKE, Hat.PARTY)
    Medal.YEARS_2 -> Look(Face.LOVE, Prop.FIREWORKS, Hat.PARTY)
}

/**
 * L'adesivo come immagine: si disegna una volta sola, fuori dal thread principale, e poi resta in memoria (poca: le
 * più vecchie si buttano). Scorrendo la pagina si copia un'immagine invece di ridisegnare ogni forma a ogni fotogramma.
 * Quelli bloccati sono la stessa immagine, grigia e quasi trasparente.
 */
@Composable
fun Sticker(medal: Medal, unlocked: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { size.roundToPx() }.coerceAtLeast(1)
    val key = "${medal.name}:$px"
    val img by androidx.compose.runtime.produceState(drawn.get(key), key) {
        if (value == null) value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            stickerBitmap(medal, px).asImageBitmap().also { drawn.put(key, it) }
        }
    }
    androidx.compose.foundation.layout.Box(modifier.size(size)) {
        img?.let {
            androidx.compose.foundation.Image(
                it, null, Modifier.size(size),
                alpha = if (unlocked) 1f else 0.32f, colorFilter = if (unlocked) null else GREY,
            )
        }
    }
}

private val GREY = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/** Le immagini già disegnate: al massimo un ottavo della memoria dell'app e mai più di 16 MB. */
private val drawn = object : android.util.LruCache<String, ImageBitmap>(
    minOf(16 * 1024 * 1024L, Runtime.getRuntime().maxMemory() / 8).toInt(),
) {
    override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
}

private fun DrawScope.art(m: Medal) {
    val l = look(m)
    // Il bordo bianco da adesivo, l'anello della difficoltà, il tondo del gruppo
    drawCircle(Color.Black.copy(alpha = 0.12f), 48.5f, Offset(0f, 1.5f))
    drawCircle(PAPER, 48f, Offset.Zero)
    drawCircle(RING[(m.tier - 1).coerceIn(0, 2)], 44.5f, Offset.Zero)
    drawCircle(bg(m.group), 41.5f, Offset.Zero)
    withTransform({ translate(-9f, 8f); scale(0.58f, 0.58f, Offset.Zero) }) {
        mascot(l.face)
        hat(l.hat)
    }
    // L'oggetto un po' più grande: è lui che rende ogni adesivo diverso
    withTransform({ scale(1.22f, 1.22f, Offset(30f, -10f)) }) { prop(l.prop, m) }
    label(m)?.let { badge(it) }
}

private val thin = Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun star(cx: Float, cy: Float, r: Float, inner: Float = 0.45f) = path {
    for (i in 0 until 10) {
        val a = Math.toRadians(-90.0 + i * 36.0)
        val rr = if (i % 2 == 0) r else r * inner
        val x = cx + rr * kotlin.math.cos(a).toFloat(); val y = cy + rr * kotlin.math.sin(a).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun heart(cx: Float, cy: Float, s: Float) = path {
    moveTo(cx, cy + 8f * s)
    cubicTo(cx - 13f * s, cy, cx - 9f * s, cy - 12f * s, cx, cy - 5f * s)
    cubicTo(cx + 9f * s, cy - 12f * s, cx + 13f * s, cy, cx, cy + 8f * s); close()
}

// ── La sordina (nello spazio della mascotte)

private fun DrawScope.mascot(f: Face) {
    when (f) {
        Face.HAPPY -> return body(Mood.DONE)
        Face.LAUGH -> return body(Mood.WON)
        Face.CALM -> return body(Mood.REST)
        Face.WOW -> return body(Mood.READY)
        Face.UP -> return body(Mood.HELLO)
        Face.FOCUS -> return body(Mood.WAIT)
        Face.GLASSES -> return body(Mood.FIX)
        Face.BREATH -> return body(Mood.WAIT, 0.75f)
        else -> {}
    }
    drawRoundRect(CAP, Offset(-9f, -54f), Size(18f, 14f), CornerRadius(6f))
    drawPath(bodyPath(), BODY)
    drawPath(path { moveTo(-30f, 24f); quadraticTo(0f, 34f, 30f, 24f); lineTo(32f, 30f); quadraticTo(0f, 40f, -32f, 30f); close() }, BAND)
    drawOval(CHEEK, Offset(-23f, 9f), Size(12f, 7f)); drawOval(CHEEK, Offset(11f, 9f), Size(12f, 7f))
    val s = Stroke(2.5f, cap = StrokeCap.Round)
    val smile = path { moveTo(-6f, 12f); quadraticTo(0f, 18f, 6f, 12f) }
    when (f) {
        // Occhiali da sole
        Face.COOL -> {
            drawRoundRect(FACE, Offset(-16f, -2f), Size(13f, 8f), CornerRadius(3f))
            drawRoundRect(FACE, Offset(3f, -2f), Size(13f, 8f), CornerRadius(3f))
            drawLine(FACE, Offset(-4f, 0f), Offset(4f, 0f), 2f)
            drawLine(WHITE.copy(alpha = 0.6f), Offset(-13f, 0f), Offset(-10f, 0f), 1.4f, StrokeCap.Round)
            drawPath(path { moveTo(-5f, 12f); quadraticTo(2f, 17f, 7f, 10f) }, FACE, style = s)
        }
        // Occhi a stella e la bocca aperta
        Face.STARS -> {
            drawPath(star(-8f, 2f, 5.5f), AMBER); drawPath(star(-8f, 2f, 5.5f), FACE, style = Stroke(1.2f))
            drawPath(star(8f, 2f, 5.5f), AMBER); drawPath(star(8f, 2f, 5.5f), FACE, style = Stroke(1.2f))
            drawPath(path { moveTo(-7f, 10f); lineTo(7f, 10f); quadraticTo(6f, 19f, 0f, 19f); quadraticTo(-6f, 19f, -7f, 10f); close() }, FACE)
        }
        // Un occhiolino
        Face.WINK -> {
            drawPath(path { moveTo(-12f, 3f); quadraticTo(-8f, -2f, -4f, 3f) }, FACE, style = s)
            drawCircle(FACE, 3.2f, Offset(8f, 2f))
            drawPath(path { moveTo(-6f, 11f); quadraticTo(0f, 18f, 7f, 11f) }, FACE, style = s)
            drawOval(PINK, Offset(-1f, 14f), Size(5f, 4f))
        }
        // Dorme: occhi chiusi all'ingiù, la bocca piccola
        Face.SLEEP -> {
            drawPath(path { moveTo(-12f, 2f); quadraticTo(-8f, 6f, -4f, 2f); moveTo(4f, 2f); quadraticTo(8f, 6f, 12f, 2f) }, FACE, style = s)
            drawOval(FACE, Offset(-2f, 12f), Size(4f, 4.5f))
        }
        // Occhi a cuore
        Face.LOVE -> {
            drawPath(heart(-8f, 1f, 0.55f), RED); drawPath(heart(8f, 1f, 0.55f), RED)
            drawPath(smile, FACE, style = s)
        }
        else -> {}
    }
}

private fun DrawScope.hat(h: Hat) {
    when (h) {
        Hat.NONE -> {}
        Hat.CROWN -> {
            val c = path { moveTo(-16f, -56f); lineTo(-16f, -72f); lineTo(-8f, -63f); lineTo(0f, -76f); lineTo(8f, -63f); lineTo(16f, -72f); lineTo(16f, -56f); close() }
            drawPath(c, AMBER); drawPath(c, BAND, style = Stroke(2f, join = StrokeJoin.Round))
            drawCircle(RED, 2.6f, Offset(0f, -62f))
        }
        Hat.PARTY -> {
            val p = path { moveTo(-14f, -54f); lineTo(4f, -84f); lineTo(14f, -54f); close() }
            drawPath(p, SKY)
            drawPath(path { moveTo(-8f, -64f); lineTo(10f, -64f); moveTo(-3f, -74f); lineTo(7f, -74f) }, PAPER, style = Stroke(3f))
            drawCircle(RED, 4f, Offset(4f, -85f))
        }
        Hat.NIGHTCAP -> {
            val p = path { moveTo(-15f, -50f); quadraticTo(-10f, -78f, 10f, -80f); quadraticTo(22f, -78f, 26f, -64f); quadraticTo(14f, -70f, 15f, -50f); close() }
            drawPath(p, NAVY)
            drawRoundRect(PAPER, Offset(-17f, -55f), Size(34f, 7f), CornerRadius(3.5f))
            drawCircle(PAPER, 5f, Offset(26f, -62f))
        }
        Hat.HALO -> drawOval(AMBER, Offset(-17f, -72f), Size(34f, 9f), style = Stroke(3.5f))
        Hat.HEADPHONES -> {
            drawPath(path { moveTo(-27f, 4f); quadraticTo(-30f, -66f, 0f, -64f); quadraticTo(30f, -66f, 27f, 4f) }, INK, style = Stroke(4f, cap = StrokeCap.Round))
            drawRoundRect(RED, Offset(-33f, -6f), Size(10f, 18f), CornerRadius(4f))
            drawRoundRect(RED, Offset(23f, -6f), Size(10f, 18f), CornerRadius(4f))
        }
    }
}

// ── L'oggetto dell'impresa, a destra in alto (nello spazio dell'adesivo, intorno a 30,-14)

private fun DrawScope.prop(p: Prop, m: Medal) {
    when (p) {
        Prop.STEPS -> {
            fun foot(x: Float, y: Float, a: Float) = rotate(a, Offset(x, y)) {
                drawOval(INK, Offset(x - 3.5f, y - 6f), Size(7f, 11f)); drawCircle(INK, 2.6f, Offset(x, y + 7.5f))
            }
            foot(25f, -6f, -12f); foot(36f, -22f, -12f)
        }
        Prop.HAND, Prop.WAVE_HAND -> rotate(if (p == Prop.WAVE_HAND) 18f else 0f, Offset(31f, -12f)) {
            for (i in 0 until 4) drawRoundRect(SKIN, Offset(22.5f + i * 4.6f, -30f + (if (i == 1 || i == 2) -2f else 1f)), Size(4.2f, 14f), CornerRadius(2.1f))
            drawRoundRect(SKIN, Offset(22f, -19f), Size(19f, 15f), CornerRadius(5f))
            drawRoundRect(SKIN, Offset(16f, -15f), Size(9f, 4.5f), CornerRadius(2.2f))
            if (p == Prop.WAVE_HAND) drawPath(path { moveTo(44f, -30f); quadraticTo(47f, -24f, 44f, -18f); moveTo(16f, -32f); quadraticTo(13f, -26f, 16f, -20f) }, BAND, style = thin)
        }
        Prop.STOP -> {
            drawLine(INK, Offset(30f, -8f), Offset(30f, 14f), 2.6f, StrokeCap.Round)
            val oct = path {
                for (i in 0 until 8) {
                    val a = Math.toRadians(22.5 + i * 45.0)
                    val x = 30f + 11f * kotlin.math.cos(a).toFloat(); val y = -16f + 11f * kotlin.math.sin(a).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            drawPath(oct, RED); drawPath(oct, PAPER, style = Stroke(1.6f))
            drawLine(PAPER, Offset(24.5f, -16f), Offset(35.5f, -16f), 3f, StrokeCap.Round)
        }
        Prop.STAR -> { drawPath(star(30f, -16f, 13f), AMBER); drawPath(star(30f, -16f, 13f), BAND, style = Stroke(1.8f, join = StrokeJoin.Round)) }
        Prop.SHIELD -> {
            val s = path { moveTo(30f, -30f); lineTo(42f, -25f); quadraticTo(42f, -6f, 30f, 0f); quadraticTo(18f, -6f, 18f, -25f); close() }
            drawPath(s, SKY); drawPath(s, NAVY, style = Stroke(1.8f))
            drawPath(path { moveTo(24f, -15f); lineTo(29f, -10f); lineTo(36f, -20f) }, WHITE, style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Prop.SUNRISE, Prop.SUNSET -> {
            val c = if (p == Prop.SUNSET) FLAME else AMBER
            drawArc(c, 180f, 180f, true, Offset(20f, -22f), Size(22f, 22f))
            for (i in 0 until 5) {
                val a = Math.toRadians(180.0 + 22.5 + i * 33.75)
                val cx = 31f; val cy = -11f
                drawLine(c, Offset(cx + 14f * kotlin.math.cos(a).toFloat(), cy + 14f * kotlin.math.sin(a).toFloat()), Offset(cx + 18f * kotlin.math.cos(a).toFloat(), cy + 18f * kotlin.math.sin(a).toFloat()), 2.2f, StrokeCap.Round)
            }
            drawLine(if (p == Prop.SUNSET) Color(0xFF8E6BC4) else SKY, Offset(17f, -10f), Offset(45f, -10f), 2.6f, StrokeCap.Round)
            if (p == Prop.SUNSET) drawLine(Color(0xFF8E6BC4), Offset(22f, -5f), Offset(40f, -5f), 2.2f, StrokeCap.Round)
        }
        Prop.PLATE -> {
            drawCircle(WHITE, 11f, Offset(32f, -12f)); drawCircle(Color(0xFFE6E1D8), 7f, Offset(32f, -12f), style = Stroke(1.4f))
            drawCircle(GREEN, 3.2f, Offset(30f, -13f)); drawCircle(RED, 2.4f, Offset(34.5f, -10f))
            drawLine(INK, Offset(18f, -24f), Offset(18f, 0f), 2f, StrokeCap.Round)
            drawPath(path { moveTo(15.5f, -24f); lineTo(15.5f, -17f); moveTo(20.5f, -24f); lineTo(20.5f, -17f); moveTo(15.5f, -17f); lineTo(20.5f, -17f) }, INK, style = Stroke(1.6f, cap = StrokeCap.Round))
        }
        Prop.MOON, Prop.NIGHTSKY, Prop.STARMOON -> {
            val r = if (p == Prop.NIGHTSKY) 7f else 10f
            drawCircle(AMBER, r, Offset(31f, -18f)); drawCircle(bg(m.group), r * 0.9f, Offset(31f + r * 0.45f, -18f - r * 0.35f))
            sparkle(21f, -28f, 2.5f, BAND); sparkle(41f, -6f, 2f, BAND)
            if (p != Prop.MOON) { drawPath(star(40f, -26f, 3.5f), AMBER); drawPath(star(22f, -6f, 3f), AMBER) }
            if (p == Prop.STARMOON) { drawPath(star(16f, -18f, 2.6f), AMBER); drawPath(star(44f, -16f, 2.6f), AMBER); drawPath(star(32f, -2f, 2.6f), AMBER) }
            if (p == Prop.NIGHTSKY) puffs()
        }
        Prop.UMBRELLA -> {
            drawLine(WOOD, Offset(30f, -20f), Offset(26f, 8f), 2f, StrokeCap.Round)
            drawArc(RED, 180f, 180f, true, Offset(16f, -32f), Size(28f, 22f))
            drawArc(WHITE, 225f, 45f, true, Offset(16f, -32f), Size(28f, 22f))
            drawArc(WHITE, 315f, 45f, true, Offset(16f, -32f), Size(28f, 22f))
        }
        Prop.CHECKS -> for (i in 0 until 3) drawPath(path { val y = -28f + i * 10f; moveTo(22f, y); lineTo(27f, y + 4.5f); lineTo(37f, y - 4f) }, GREEN, style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        Prop.LEAF -> {
            rotate(-30f, Offset(34f, -18f)) {
                drawOval(GREEN, Offset(28f, -24f), Size(14f, 9f)); drawLine(Color(0xFF2E7D4A), Offset(28f, -19.5f), Offset(42f, -19.5f), 1.2f)
            }
            puffs(0.7f)
        }
        Prop.WAVES -> for (i in 0 until 3) {
            val y = -26f + i * 8f
            drawPath(path { moveTo(18f, y); quadraticTo(22f, y - 4f, 26f, y); quadraticTo(30f, y + 4f, 34f, y); quadraticTo(38f, y - 4f, 42f, y) }, SKY, style = thin)
        }
        Prop.CLOCK, Prop.ALARM -> {
            val c = Offset(31f, -14f)
            if (p == Prop.ALARM) {
                drawCircle(RED, 4.5f, Offset(23f, -25f)); drawCircle(RED, 4.5f, Offset(39f, -25f))
                drawLine(INK, Offset(24f, -2f), Offset(22f, 1f), 2f, StrokeCap.Round); drawLine(INK, Offset(38f, -2f), Offset(40f, 1f), 2f, StrokeCap.Round)
            }
            drawCircle(if (p == Prop.ALARM) RED else INK, 12f, c); drawCircle(WHITE, 10f, c)
            drawLine(INK, c, Offset(31f, -21f), 2f, StrokeCap.Round); drawLine(INK, c, Offset(36f, -12f), 2f, StrokeCap.Round)
            drawCircle(INK, 1.5f, c)
        }
        Prop.KITE -> {
            val k = path { moveTo(32f, -34f); lineTo(42f, -22f); lineTo(32f, -8f); lineTo(22f, -22f); close() }
            drawPath(k, PINK); drawPath(path { moveTo(32f, -34f); lineTo(32f, -8f); moveTo(22f, -22f); lineTo(42f, -22f) }, WHITE, style = Stroke(1.4f))
            drawPath(path { moveTo(32f, -8f); quadraticTo(28f, -2f, 32f, 2f); quadraticTo(36f, 6f, 30f, 10f) }, INK, style = Stroke(1.2f))
            drawCircle(AMBER, 1.8f, Offset(30f, -1f)); drawCircle(SKY, 1.8f, Offset(33f, 5f))
        }
        Prop.BOOK -> {
            drawPath(path { moveTo(31f, -22f); quadraticTo(24f, -26f, 17f, -24f); lineTo(17f, -6f); quadraticTo(24f, -8f, 31f, -4f); close() }, WHITE)
            drawPath(path { moveTo(31f, -22f); quadraticTo(38f, -26f, 45f, -24f); lineTo(45f, -6f); quadraticTo(38f, -8f, 31f, -4f); close() }, WHITE)
            drawPath(path { moveTo(17f, -24f); lineTo(17f, -6f); quadraticTo(24f, -8f, 31f, -4f); quadraticTo(38f, -8f, 45f, -6f); lineTo(45f, -24f); moveTo(31f, -22f); lineTo(31f, -4f) }, RED, style = Stroke(1.8f, join = StrokeJoin.Round))
            drawPath(path { moveTo(20f, -18f); lineTo(28f, -16f); moveTo(20f, -13f); lineTo(28f, -11f); moveTo(34f, -16f); lineTo(42f, -18f); moveTo(34f, -11f); lineTo(42f, -13f) }, Color(0xFFB9B2A6), style = Stroke(1.2f, cap = StrokeCap.Round))
        }
        Prop.HOURGLASS -> {
            val g = path { moveTo(23f, -27f); lineTo(39f, -27f); lineTo(31f, -15f); lineTo(39f, -3f); lineTo(23f, -3f); lineTo(31f, -15f); close() }
            drawPath(g, WHITE.copy(alpha = 0.7f))
            drawPath(path { moveTo(26f, -7f); lineTo(36f, -7f); lineTo(39f, -3f); lineTo(23f, -3f); close() }, BODY)
            drawPath(path { moveTo(28f, -23f); lineTo(34f, -23f); lineTo(31f, -19f); close() }, BODY)
            drawPath(g, BAND, style = thin)
            drawLine(WOOD, Offset(21f, -28f), Offset(41f, -28f), 3f, StrokeCap.Round); drawLine(WOOD, Offset(21f, -2f), Offset(41f, -2f), 3f, StrokeCap.Round)
        }
        Prop.SUNMOON -> {
            drawArc(AMBER, 90f, 180f, true, Offset(19f, -26f), Size(24f, 24f))
            drawArc(NAVY, -90f, 180f, true, Offset(19f, -26f), Size(24f, 24f))
            drawPath(star(36f, -16f, 3f), AMBER)
        }
        Prop.TROPHY -> {
            drawPath(path { moveTo(22f, -30f); lineTo(40f, -30f); quadraticTo(40f, -14f, 31f, -12f); quadraticTo(22f, -14f, 22f, -30f); close() }, AMBER)
            drawPath(path { moveTo(22f, -27f); quadraticTo(15f, -26f, 19f, -18f); moveTo(40f, -27f); quadraticTo(47f, -26f, 43f, -18f) }, AMBER, style = Stroke(2.4f))
            drawRect(AMBER, Offset(29f, -12f), Size(4f, 6f)); drawRoundRect(WOOD, Offset(24f, -6f), Size(14f, 6f), CornerRadius(1.5f))
            drawPath(star(31f, -22f, 4f), WHITE.copy(alpha = 0.8f))
        }
        Prop.FLAME -> {
            val s = when (m) { Medal.SERIES_3 -> 0.7f; Medal.SERIES_7 -> 0.85f; Medal.SERIES_14 -> 1f; Medal.SERIES_30 -> 1.12f; else -> 1.22f }
            withTransform({ scale(s, s, Offset(30f, -2f)) }) {
                drawPath(path { moveTo(30f, -32f); cubicTo(42f, -20f, 42f, -6f, 30f, -2f); cubicTo(18f, -6f, 18f, -18f, 26f, -24f); cubicTo(26f, -18f, 28f, -16f, 30f, -18f); cubicTo(32f, -22f, 30f, -28f, 30f, -32f); close() }, if (m == Medal.SERIES_100) RED else FLAME)
                drawPath(path { moveTo(30f, -16f); cubicTo(36f, -10f, 34f, -4f, 30f, -3f); cubicTo(26f, -4f, 25f, -9f, 30f, -16f); close() }, AMBER)
            }
        }
        Prop.CALENDAR, Prop.CALENDAR_FULL -> {
            drawRoundRect(WHITE, Offset(19f, -28f), Size(24f, 24f), CornerRadius(3f))
            drawRoundRect(RED, Offset(19f, -28f), Size(24f, 7f), CornerRadius(3f))
            drawLine(INK, Offset(24f, -31f), Offset(24f, -26f), 2f, StrokeCap.Round); drawLine(INK, Offset(38f, -31f), Offset(38f, -26f), 2f, StrokeCap.Round)
            for (r in 0 until 3) for (c in 0 until 4) {
                val o = Offset(23f + c * 5.4f, -17f + r * 5f)
                if (p == Prop.CALENDAR_FULL || r == 1) drawCircle(GREEN, 1.9f, o) else drawCircle(Color(0xFFD8D3CB), 1.4f, o)
            }
        }
        Prop.ARROW_UP -> {
            drawPath(path { moveTo(17f, -2f); lineTo(25f, -12f); lineTo(30f, -7f); lineTo(41f, -24f) }, GREEN, style = Stroke(3.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path { moveTo(43f, -28f); lineTo(43f, -19f); lineTo(35f, -25f); close() }, GREEN)
        }
        Prop.ARROW_DOWN -> {
            drawPath(path { moveTo(17f, -28f); lineTo(25f, -18f); lineTo(30f, -22f); lineTo(41f, -6f) }, SKY, style = Stroke(3.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path { moveTo(43f, -2f); lineTo(43f, -11f); lineTo(35f, -5f); close() }, SKY)
        }
        Prop.MEDAL -> {
            drawPath(path { moveTo(24f, -34f); lineTo(29f, -34f); lineTo(33f, -22f); lineTo(28f, -22f); close() }, RED)
            drawPath(path { moveTo(38f, -34f); lineTo(33f, -34f); lineTo(29f, -22f); lineTo(34f, -22f); close() }, SKY)
            drawCircle(AMBER, 9f, Offset(31f, -14f)); drawCircle(BAND, 6.5f, Offset(31f, -14f), style = Stroke(1.4f))
        }
        Prop.LADDER -> {
            drawLine(WOOD, Offset(24f, 4f), Offset(28f, -32f), 2.6f, StrokeCap.Round); drawLine(WOOD, Offset(36f, 4f), Offset(40f, -32f), 2.6f, StrokeCap.Round)
            for (i in 0 until 5) { val t = i / 4f; drawLine(WOOD, Offset(24.5f + 3.5f * t, 0f - 28f * t), Offset(36.5f + 3.5f * t, 0f - 28f * t), 2f) }
            drawPath(star(42f, -34f, 4f), AMBER)
        }
        Prop.ROCKET -> rotate(35f, Offset(31f, -16f)) {
            drawPath(path { moveTo(31f, -34f); quadraticTo(39f, -24f, 37f, -6f); lineTo(25f, -6f); quadraticTo(23f, -24f, 31f, -34f); close() }, WHITE)
            drawCircle(SKY, 3.5f, Offset(31f, -20f)); drawCircle(NAVY, 3.5f, Offset(31f, -20f), style = Stroke(1.2f))
            drawPath(path { moveTo(25f, -12f); lineTo(20f, -3f); lineTo(25f, -6f); close(); moveTo(37f, -12f); lineTo(42f, -3f); lineTo(37f, -6f); close() }, RED)
            drawPath(path { moveTo(27f, -6f); quadraticTo(31f, 6f, 35f, -6f); close() }, FLAME)
        }
        Prop.MOUNTAIN -> {
            drawPath(path { moveTo(15f, 0f); lineTo(31f, -26f); lineTo(46f, 0f); close() }, Color(0xFF8C93A3))
            drawPath(path { moveTo(25.5f, -17f); lineTo(31f, -26f); lineTo(36.5f, -17f); lineTo(33f, -19f); lineTo(31f, -16f); lineTo(28.5f, -19f); close() }, WHITE)
            drawLine(INK, Offset(31f, -26f), Offset(31f, -36f), 1.6f); drawPath(path { moveTo(31f, -36f); lineTo(38f, -33.5f); lineTo(31f, -31f); close() }, RED)
        }
        Prop.DIAMOND -> {
            val d = path { moveTo(22f, -24f); lineTo(27f, -30f); lineTo(35f, -30f); lineTo(40f, -24f); lineTo(31f, -6f); close() }
            drawPath(d, Color(0xFF7FD6E8)); drawPath(path { moveTo(22f, -24f); lineTo(40f, -24f); moveTo(27f, -30f); lineTo(31f, -24f); lineTo(35f, -30f); moveTo(31f, -24f); lineTo(31f, -6f) }, WHITE, style = Stroke(1.2f))
            drawPath(d, Color(0xFF3A9DB5), style = Stroke(1.4f, join = StrokeJoin.Round))
            sparkle(43f, -32f, 3f, AMBER)
        }
        Prop.SPARKLES -> { drawPath(star(30f, -18f, 9f), AMBER); sparkle(42f, -28f, 4f, BAND); sparkle(20f, -30f, 3f, PINK); sparkle(41f, -6f, 3f, SKY) }
        Prop.PHONE_DOWN -> {
            drawRoundRect(INK, Offset(19f, -8f), Size(24f, 12f), CornerRadius(3f))
            drawRoundRect(Color(0xFF555B66), Offset(21f, -6f), Size(20f, 8f), CornerRadius(2f))
            drawCircle(Color(0xFF8C93A3), 1.6f, Offset(37f, -2f))
            heartAt(31f, -22f, 0.6f)
        }
        Prop.PHONE_ZZZ -> {
            drawRoundRect(INK, Offset(20f, -22f), Size(13f, 24f), CornerRadius(3f))
            drawRoundRect(NAVY, Offset(21.5f, -19.5f), Size(10f, 18f), CornerRadius(1.5f))
            zees(36f, -26f, 0.8f)
        }
        Prop.FLOWER -> {
            drawLine(GREEN, Offset(31f, -14f), Offset(31f, 4f), 2.2f, StrokeCap.Round)
            drawOval(GREEN, Offset(31f, -6f), Size(9f, 5f))
            for (i in 0 until 6) { val a = Math.toRadians(i * 60.0); drawCircle(PINK, 5f, Offset(31f + 6.5f * kotlin.math.cos(a).toFloat(), -18f + 6.5f * kotlin.math.sin(a).toFloat())) }
            drawCircle(AMBER, 4f, Offset(31f, -18f))
        }
        Prop.LOCK -> {
            drawPath(path { moveTo(25f, -16f); lineTo(25f, -22f); quadraticTo(25f, -30f, 31f, -30f); quadraticTo(37f, -30f, 37f, -22f); lineTo(37f, -16f) }, Color(0xFF8C93A3), style = Stroke(3f))
            drawRoundRect(AMBER, Offset(21f, -17f), Size(20f, 16f), CornerRadius(3f))
            drawCircle(BAND, 2.4f, Offset(31f, -10f)); drawLine(BAND, Offset(31f, -10f), Offset(31f, -5f), 2f, StrokeCap.Round)
        }
        Prop.TREE -> {
            drawRoundRect(WOOD, Offset(29f, -10f), Size(4f, 14f), CornerRadius(1.5f))
            drawCircle(GREEN, 9f, Offset(31f, -20f)); drawCircle(GREEN, 7f, Offset(24f, -14f)); drawCircle(GREEN, 7f, Offset(38f, -14f))
            drawCircle(RED, 1.8f, Offset(27f, -20f)); drawCircle(RED, 1.8f, Offset(35f, -16f))
        }
        Prop.CUP -> {
            val cup = path { moveTo(22f, -16f); lineTo(38f, -16f); quadraticTo(38f, 0f, 30f, 0f); quadraticTo(22f, 0f, 22f, -16f); close() }
            drawPath(cup, WHITE); drawPath(cup, BAND, style = thin)
            drawArc(BAND, -90f, 180f, false, Offset(34f, -13f), Size(9f, 9f), style = Stroke(2f))
            drawPath(path { moveTo(27f, -20f); quadraticTo(24f, -24f, 27f, -28f); moveTo(33f, -20f); quadraticTo(30f, -24f, 33f, -28f) }, Color(0xFFB9B2A6), style = thin)
        }
        Prop.BELL -> {
            drawPath(path { moveTo(21f, -6f); quadraticTo(23f, -8f, 23f, -14f); quadraticTo(23f, -25f, 31f, -25f); quadraticTo(39f, -25f, 39f, -14f); quadraticTo(39f, -8f, 41f, -6f); close() }, AMBER)
            drawCircle(BAND, 2.6f, Offset(31f, -3.5f))
            drawLine(RED, Offset(19f, -27f), Offset(43f, -1f), 3f, StrokeCap.Round)
        }
        Prop.ENVELOPES -> for (i in 1 downTo 0) {
            val o = Offset(18f + i * 5f, -26f + i * 9f)
            drawRoundRect(WHITE, o, Size(22f, 15f), CornerRadius(2f)); drawRoundRect(Color(0xFFB9B2A6), o, Size(22f, 15f), CornerRadius(2f), style = Stroke(1.2f))
            drawPath(path { moveTo(o.x, o.y); lineTo(o.x + 11f, o.y + 8f); lineTo(o.x + 22f, o.y) }, RED, style = Stroke(1.6f, join = StrokeJoin.Round))
        }
        Prop.CHECK -> {
            drawCircle(GREEN, 12f, Offset(31f, -16f))
            drawPath(path { moveTo(25f, -16f); lineTo(29.5f, -11.5f); lineTo(37f, -21f) }, WHITE, style = Stroke(3.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Prop.PEOPLE -> {
            drawCircle(SKY, 4.5f, Offset(25f, -22f)); drawArc(SKY, 180f, 180f, true, Offset(18f, -16f), Size(14f, 14f))
            drawCircle(PINK, 4.5f, Offset(38f, -20f)); drawArc(PINK, 180f, 180f, true, Offset(31f, -14f), Size(14f, 14f))
            drawPath(heart(32f, -32f, 0.45f), RED)
        }
        Prop.SCROLL -> {
            drawRoundRect(WHITE, Offset(21f, -28f), Size(20f, 26f), CornerRadius(2f))
            drawRoundRect(Color(0xFFE6D3B0), Offset(19f, -30f), Size(24f, 5f), CornerRadius(2.5f)); drawRoundRect(Color(0xFFE6D3B0), Offset(19f, -5f), Size(24f, 5f), CornerRadius(2.5f))
            for (i in 0 until 3) { val y = -20f + i * 5f; drawCircle(GREEN, 1.4f, Offset(25f, y)); drawLine(Color(0xFFB9B2A6), Offset(28f, y), Offset(37f, y), 1.4f, StrokeCap.Round) }
        }
        Prop.TARGET -> {
            drawCircle(RED, 12f, Offset(30f, -14f)); drawCircle(WHITE, 8.5f, Offset(30f, -14f)); drawCircle(RED, 5f, Offset(30f, -14f)); drawCircle(WHITE, 1.8f, Offset(30f, -14f))
            drawLine(WOOD, Offset(30f, -14f), Offset(44f, -28f), 2f, StrokeCap.Round)
            drawPath(path { moveTo(44f, -28f); lineTo(46f, -33f); moveTo(44f, -28f); lineTo(49f, -30f) }, GREEN, style = Stroke(2f, cap = StrokeCap.Round))
        }
        Prop.BULB -> {
            drawCircle(AMBER, 9f, Offset(31f, -20f)); drawRoundRect(Color(0xFF8C93A3), Offset(27f, -12f), Size(8f, 7f), CornerRadius(1.5f))
            drawPath(path { moveTo(28f, -20f); lineTo(30f, -16f); lineTo(32f, -21f); lineTo(34f, -16f) }, BAND, style = Stroke(1.2f))
            for (a in listOf(-150.0, -90.0, -30.0)) { val r = Math.toRadians(a); drawLine(AMBER, Offset(31f + 12f * kotlin.math.cos(r).toFloat(), -20f + 12f * kotlin.math.sin(r).toFloat()), Offset(31f + 15f * kotlin.math.cos(r).toFloat(), -20f + 15f * kotlin.math.sin(r).toFloat()), 2f, StrokeCap.Round) }
        }
        Prop.ZZZ -> zees(22f, -14f, 1.2f)
        Prop.HEART -> { drawPath(heart(31f, -16f, 1.1f), PINK); drawPath(heart(31f, -16f, 1.1f), RED, style = Stroke(1.4f)) }
        Prop.BOX -> {
            drawRect(Color(0xFFC9955E), Offset(20f, -20f), Size(22f, 18f))
            drawPath(path { moveTo(18f, -20f); lineTo(22f, -27f); lineTo(40f, -27f); lineTo(44f, -20f); close() }, Color(0xFFDBA971))
            drawRect(Color(0xFFE6D3B0), Offset(28.5f, -27f), Size(5f, 25f))
        }
        Prop.WHEEL -> {
            drawCircle(INK, 12f, Offset(31f, -14f), style = Stroke(3.6f))
            drawCircle(INK, 3.5f, Offset(31f, -14f))
            drawPath(path { moveTo(19.5f, -16f); lineTo(27.5f, -14f); moveTo(34.5f, -14f); lineTo(42.5f, -16f); moveTo(31f, -10.5f); lineTo(31f, -2f) }, INK, style = Stroke(3f, cap = StrokeCap.Round))
        }
        Prop.GIFT -> {
            drawRect(RED, Offset(21f, -20f), Size(20f, 17f)); drawRect(Color(0xFFE85A4C), Offset(19f, -25f), Size(24f, 6f))
            drawRect(AMBER, Offset(29f, -25f), Size(4f, 22f))
            drawOval(AMBER, Offset(23f, -32f), Size(8f, 7f), style = Stroke(2f)); drawOval(AMBER, Offset(31f, -32f), Size(8f, 7f), style = Stroke(2f))
        }
        Prop.BALLOONS -> {
            listOf(Triple(24f, -24f, RED), Triple(36f, -26f, SKY), Triple(30f, -32f, AMBER)).forEach { (x, y, c) ->
                drawLine(INK.copy(alpha = 0.6f), Offset(x, y + 6f), Offset(30f, 4f), 1f)
                drawOval(c, Offset(x - 5f, y - 6.5f), Size(10f, 13f))
            }
        }
        // ── Gli oggetti delle imprese nuove
        Prop.DICE -> {
            drawRoundRect(WHITE, Offset(19f, -28f), Size(24f, 24f), CornerRadius(5f)); drawRoundRect(INK, Offset(19f, -28f), Size(24f, 24f), CornerRadius(5f), style = Stroke(1.4f))
            listOf(24f to -23f, 38f to -23f, 31f to -16f, 24f to -9f, 38f to -9f).forEach { (x, y) -> drawCircle(INK, 2.3f, Offset(x, y)) }
        }
        Prop.BUBBLES -> listOf(Triple(26f, -12f, 8f), Triple(38f, -23f, 6f), Triple(39f, -6f, 4f), Triple(24f, -29f, 3.5f)).forEach { (x, y, r) ->
            drawCircle(SKY.copy(alpha = 0.22f), r, Offset(x, y)); drawCircle(SKY, r, Offset(x, y), style = Stroke(1.6f))
            drawCircle(WHITE, r * 0.25f, Offset(x - r * 0.4f, y - r * 0.4f))
        }
        Prop.RAINBOW -> {
            listOf(RED, FLAME, AMBER, GREEN, SKY).forEachIndexed { i, c ->
                val r = 15f - i * 3f
                drawArc(c, 180f, 180f, false, Offset(31f - r, -8f - r), Size(2 * r, 2 * r), style = Stroke(3f))
            }
            for (x in listOf(17f, 45f)) { drawCircle(WHITE, 4f, Offset(x, -7f)); drawCircle(WHITE, 3f, Offset(x - 4f, -5f)); drawCircle(WHITE, 3f, Offset(x + 4f, -5f)) }
        }
        Prop.BUTTERFLY -> {
            drawOval(PINK, Offset(18f, -30f), Size(13f, 14f)); drawOval(PINK, Offset(31f, -30f), Size(13f, 14f))
            drawOval(AMBER, Offset(20f, -17f), Size(11f, 10f)); drawOval(AMBER, Offset(31f, -17f), Size(11f, 10f))
            drawCircle(WHITE, 2f, Offset(24f, -24f)); drawCircle(WHITE, 2f, Offset(38f, -24f))
            drawRoundRect(INK, Offset(29.5f, -28f), Size(3f, 20f), CornerRadius(1.5f))
            drawPath(path { moveTo(30f, -28f); quadraticTo(27f, -34f, 24f, -35f); moveTo(32f, -28f); quadraticTo(35f, -34f, 38f, -35f) }, INK, style = Stroke(1.2f, cap = StrokeCap.Round))
        }
        Prop.TENT -> {
            drawPath(path { moveTo(16f, -3f); lineTo(31f, -30f); lineTo(46f, -3f); close() }, Color(0xFFF08A24))
            drawPath(path { moveTo(26f, -3f); lineTo(31f, -15f); lineTo(36f, -3f); close() }, Color(0xFF8C4A12))
            drawLine(INK, Offset(31f, -30f), Offset(31f, -38f), 1.4f); drawPath(path { moveTo(31f, -38f); lineTo(38f, -35.5f); lineTo(31f, -33f); close() }, RED)
            drawLine(Color(0xFF2E7D4A), Offset(13f, -3f), Offset(49f, -3f), 2f, StrokeCap.Round)
        }
        Prop.SEEDLING -> {
            drawPath(path { moveTo(23f, -10f); lineTo(39f, -10f); lineTo(37f, 1f); lineTo(25f, 1f); close() }, Color(0xFFC9714A))
            drawRect(Color(0xFFB0603C), Offset(22f, -12f), Size(18f, 3f))
            drawLine(GREEN, Offset(31f, -12f), Offset(31f, -24f), 2f, StrokeCap.Round)
            rotate(-30f, Offset(27f, -25f)) { drawOval(GREEN, Offset(20f, -28.5f), Size(12f, 7f)) }
            rotate(30f, Offset(35f, -25f)) { drawOval(GREEN, Offset(30f, -28.5f), Size(12f, 7f)) }
        }
        Prop.DANDELION -> {
            drawLine(GREEN, Offset(31f, 0f), Offset(31f, -18f), 1.8f, StrokeCap.Round)
            val seed = Color(0xFF9AA3B5)
            for (i in 0 until 14) {
                val a = Math.toRadians(i * 360.0 / 14); val c = kotlin.math.cos(a).toFloat(); val s = kotlin.math.sin(a).toFloat()
                drawLine(seed, Offset(31f + c * 2.5f, -21f + s * 2.5f), Offset(31f + c * 10f, -21f + s * 10f), 1f)
                drawCircle(WHITE, 1.9f, Offset(31f + c * 11f, -21f + s * 11f)); drawCircle(seed, 1.9f, Offset(31f + c * 11f, -21f + s * 11f), style = Stroke(0.7f))
            }
            drawCircle(Color(0xFFC9A64E), 2.6f, Offset(31f, -21f))
            drawCircle(WHITE, 1.4f, Offset(44f, -33f)); drawLine(seed, Offset(44f, -31.5f), Offset(43f, -27f), 0.8f)
        }
        Prop.LOTUS -> {
            val light = Color(0xFFF7B6C2)
            for (a in listOf(-62f, 62f)) rotate(a, Offset(31f, -8f)) { drawOval(light, Offset(27.5f, -24f), Size(7f, 16f)) }
            for (a in listOf(-30f, 30f)) rotate(a, Offset(31f, -8f)) { drawOval(PINK.copy(alpha = 0.85f), Offset(27f, -26f), Size(8f, 18f)) }
            drawOval(PINK, Offset(27f, -29f), Size(8f, 21f))
            drawPath(path { moveTo(15f, -3f); quadraticTo(20f, -6f, 25f, -3f); quadraticTo(30f, 0f, 35f, -3f); quadraticTo(40f, -6f, 47f, -3f) }, SKY, style = thin)
        }
        Prop.CASTLE -> {
            val stone = Color(0xFFBFC6D1); val dark = Color(0xFF8C93A3)
            drawRect(stone, Offset(21f, -20f), Size(20f, 18f))
            drawRect(stone, Offset(16f, -28f), Size(8f, 26f)); drawRect(stone, Offset(38f, -28f), Size(8f, 26f))
            for (x in listOf(16f, 21f, 38f, 43f)) drawRect(stone, Offset(x, -31f), Size(3f, 3f))
            for (x in listOf(23f, 28f, 33f)) drawRect(stone, Offset(x, -23f), Size(3f, 3f))
            drawPath(path { moveTo(27f, -2f); lineTo(27f, -9f); quadraticTo(31f, -14f, 35f, -9f); lineTo(35f, -2f); close() }, dark)
            drawLine(INK, Offset(42f, -31f), Offset(42f, -39f), 1.2f); drawPath(path { moveTo(42f, -39f); lineTo(48f, -37f); lineTo(42f, -35f); close() }, RED)
        }
        Prop.PALETTE -> {
            drawPath(path { moveTo(31f, -32f); cubicTo(46f, -32f, 49f, -14f, 42f, -8f); cubicTo(37f, -4f, 34f, -10f, 30f, -6f); cubicTo(26f, -2f, 15f, -6f, 15f, -16f); cubicTo(15f, -26f, 22f, -32f, 31f, -32f); close() }, Color(0xFFE6D3B0))
            drawCircle(Color(0xFFF2E6D8), 3f, Offset(24f, -12f))
            listOf(Triple(23f, -24f, RED), Triple(31f, -27f, AMBER), Triple(39f, -23f, SKY), Triple(40f, -15f, GREEN)).forEach { (x, y, c) -> drawCircle(c, 3f, Offset(x, y)) }
            drawLine(WOOD, Offset(28f, -6f), Offset(46f, 2f), 2.4f, StrokeCap.Round); drawCircle(INK, 1.8f, Offset(27.5f, -6.5f))
        }
        Prop.GUITAR -> {
            val body = Color(0xFFD9893B)
            drawLine(WOOD, Offset(31f, -14f), Offset(43f, -32f), 3.2f, StrokeCap.Round)
            drawRoundRect(INK, Offset(40f, -37f), Size(6f, 7f), CornerRadius(1.5f))
            drawCircle(body, 9f, Offset(26f, -7f)); drawCircle(body, 6.5f, Offset(31.5f, -15f))
            drawCircle(INK, 2.6f, Offset(28f, -10f))
            drawLine(INK, Offset(22f, -3f), Offset(26f, -6f), 1.8f, StrokeCap.Round)
        }
        Prop.PLANET -> {
            drawCircle(Color(0xFFB08AE0), 10f, Offset(31f, -16f))
            drawArc(Color(0xFF9670CC), 20f, 140f, false, Offset(23f, -24f), Size(16f, 16f), style = Stroke(2f))
            rotate(-18f, Offset(31f, -16f)) { drawOval(AMBER, Offset(14f, -20f), Size(34f, 8f), style = Stroke(2.4f)) }
            drawPath(star(17f, -31f, 3f), AMBER); drawPath(star(45f, -4f, 2.5f), AMBER)
        }
        Prop.PODIUM -> {
            drawRect(Color(0xFFBFC6D1), Offset(15f, -12f), Size(11f, 12f)); drawRect(AMBER, Offset(26f, -19f), Size(11f, 19f)); drawRect(Color(0xFFD39A62), Offset(37f, -8f), Size(10f, 8f))
            drawPath(star(31.5f, -27f, 6f), AMBER); drawPath(star(31.5f, -27f, 6f), BAND, style = Stroke(1f))
            drawLine(WHITE, Offset(31.5f, -15f), Offset(31.5f, -7f), 2f, StrokeCap.Round)
        }
        Prop.FIREWORKS -> {
            listOf(Triple(25f, -24f, RED), Triple(38f, -13f, SKY)).forEach { (x, y, c) ->
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0); val cx = kotlin.math.cos(a).toFloat(); val sy = kotlin.math.sin(a).toFloat()
                    drawLine(c, Offset(x + cx * 3f, y + sy * 3f), Offset(x + cx * 8.5f, y + sy * 8.5f), 2f, StrokeCap.Round)
                }
                drawCircle(AMBER, 2f, Offset(x, y))
            }
            drawPath(star(41f, -31f, 3.5f), AMBER); drawPath(star(19f, -6f, 2.5f), AMBER)
        }
        Prop.CONFETTI -> {
            drawPath(path { moveTo(17f, 0f); lineTo(25f, -18f); lineTo(33f, -10f); close() }, AMBER)
            drawPath(path { moveTo(21f, -9f); lineTo(29f, -14f) }, RED, style = Stroke(1.6f))
            listOf(Triple(33f, -26f, RED), Triple(40f, -18f, SKY), Triple(28f, -32f, GREEN), Triple(43f, -30f, PINK), Triple(37f, -6f, AMBER), Triple(22f, -26f, SKY)).forEachIndexed { i, (x, y, c) ->
                rotate(i * 37f, Offset(x, y)) { drawRect(c, Offset(x - 2.5f, y - 1.3f), Size(5f, 2.6f)) }
            }
        }
        Prop.HAMMOCK -> {
            drawLine(WOOD, Offset(17f, -30f), Offset(17f, 1f), 2.6f, StrokeCap.Round); drawLine(WOOD, Offset(45f, -30f), Offset(45f, 1f), 2.6f, StrokeCap.Round)
            drawPath(path { moveTo(17f, -22f); quadraticTo(31f, -2f, 45f, -22f); quadraticTo(31f, -8f, 17f, -22f); close() }, SKY)
            drawPath(path { moveTo(17f, -22f); quadraticTo(31f, -2f, 45f, -22f) }, NAVY, style = Stroke(1.2f))
            for (x in listOf(13f, 49f)) { drawLine(GREEN, Offset(x + 4f * kotlin.math.sign(31f - x), -30f), Offset(x, -34f), 2f, StrokeCap.Round) }
        }
        Prop.SNAIL -> {
            val body = Color(0xFF9CC77E)
            drawRoundRect(body, Offset(15f, -9f), Size(31f, 7f), CornerRadius(3.5f))
            drawCircle(body, 4f, Offset(18f, -11f))
            drawLine(body, Offset(17f, -13f), Offset(15f, -20f), 1.6f, StrokeCap.Round); drawLine(body, Offset(20f, -13f), Offset(21f, -20f), 1.6f, StrokeCap.Round)
            drawCircle(INK, 1.3f, Offset(15f, -20f)); drawCircle(INK, 1.3f, Offset(21f, -20f))
            drawCircle(AMBER, 10f, Offset(34f, -17f))
            drawPath(path { moveTo(34f, -17f); quadraticTo(37f, -20f, 34f, -22f); quadraticTo(29f, -22f, 29f, -17f); quadraticTo(29f, -11f, 35f, -11f); quadraticTo(41f, -11f, 42f, -17f) }, WOOD, style = Stroke(1.8f, cap = StrokeCap.Round))
        }
        Prop.HALF -> {
            drawCircle(WHITE, 12f, Offset(31f, -16f))
            drawArc(GREEN, 90f, 180f, true, Offset(19f, -28f), Size(24f, 24f))
            drawCircle(INK, 12f, Offset(31f, -16f), style = Stroke(1.6f))
            drawLine(INK, Offset(31f, -28f), Offset(31f, -4f), 1.4f)
        }
        Prop.BIKE -> {
            drawCircle(INK, 7f, Offset(21f, -9f), style = Stroke(2f)); drawCircle(INK, 7f, Offset(41f, -9f), style = Stroke(2f))
            drawPath(path { moveTo(21f, -9f); lineTo(29f, -9f); lineTo(36f, -20f); lineTo(26f, -20f); close(); moveTo(29f, -9f); lineTo(25f, -24f); moveTo(36f, -20f); lineTo(41f, -9f); moveTo(36f, -20f); lineTo(35f, -26f) }, RED, style = Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawLine(INK, Offset(22f, -24.5f), Offset(28f, -24.5f), 2.4f, StrokeCap.Round); drawLine(INK, Offset(33f, -26.5f), Offset(38f, -27.5f), 2.2f, StrokeCap.Round)
        }
        Prop.SAILBOAT -> {
            drawPath(path { moveTo(16f, -8f); lineTo(46f, -8f); lineTo(41f, 0f); lineTo(21f, 0f); close() }, WOOD)
            drawLine(INK, Offset(31f, -8f), Offset(31f, -35f), 1.6f)
            drawPath(path { moveTo(32f, -34f); lineTo(32f, -10f); lineTo(44f, -10f); close() }, WHITE)
            drawPath(path { moveTo(30f, -31f); lineTo(30f, -10f); lineTo(20f, -10f); close() }, RED)
            drawPath(path { moveTo(13f, 2f); quadraticTo(17f, -1f, 21f, 2f); quadraticTo(25f, 5f, 29f, 2f); quadraticTo(33f, -1f, 37f, 2f); quadraticTo(41f, 5f, 47f, 2f) }, SKY, style = thin)
        }
        Prop.KEY -> {
            drawCircle(AMBER, 7f, Offset(23f, -25f), style = Stroke(3.4f))
            drawLine(AMBER, Offset(28f, -20f), Offset(43f, -5f), 3.4f, StrokeCap.Round)
            drawLine(AMBER, Offset(38f, -10f), Offset(42f, -14f), 3f, StrokeCap.Round); drawLine(AMBER, Offset(34f, -14f), Offset(37f, -17f), 3f, StrokeCap.Round)
            drawCircle(WOOD, 1.6f, Offset(23f, -25f))
        }
        Prop.FEATHER -> rotate(-35f, Offset(31f, -16f)) {
            drawOval(Color(0xFF9EC9F0), Offset(25f, -35f), Size(12f, 34f))
            drawLine(WHITE, Offset(31f, -33f), Offset(31f, 4f), 1.4f, StrokeCap.Round)
            for (i in 0 until 5) { val y = -28f + i * 5.5f; drawLine(WHITE.copy(alpha = 0.7f), Offset(31f, y + 3f), Offset(27f, y), 0.9f); drawLine(WHITE.copy(alpha = 0.7f), Offset(31f, y + 3f), Offset(35f, y), 0.9f) }
        }
        Prop.SUN -> {
            for (i in 0 until 10) {
                val a = Math.toRadians(i * 36.0); val c = kotlin.math.cos(a).toFloat(); val s = kotlin.math.sin(a).toFloat()
                drawLine(FLAME, Offset(31f + c * 11f, -16f + s * 11f), Offset(31f + c * 15.5f, -16f + s * 15.5f), 2.4f, StrokeCap.Round)
            }
            drawCircle(AMBER, 9f, Offset(31f, -16f))
        }
        Prop.PIZZA -> {
            drawPath(path { moveTo(18f, -28f); lineTo(44f, -28f); lineTo(31f, 1f); close() }, Color(0xFFF7D46B))
            drawRoundRect(Color(0xFFD9893B), Offset(16f, -32f), Size(30f, 6f), CornerRadius(3f))
            listOf(25f to -21f, 36f to -21f, 31f to -11f).forEach { (x, y) -> drawCircle(RED, 3f, Offset(x, y)) }
            drawCircle(GREEN, 1.4f, Offset(30f, -19f)); drawCircle(GREEN, 1.4f, Offset(34f, -14f))
        }
        Prop.TOAST -> {
            val wine = Color(0xFFB5304A)
            for ((x, a) in listOf(25f to -14f, 37f to 14f)) rotate(a, Offset(x, -4f)) {
                drawPath(path { moveTo(x - 5f, -28f); lineTo(x + 5f, -28f); quadraticTo(x + 5f, -16f, x, -15f); quadraticTo(x - 5f, -16f, x - 5f, -28f); close() }, WHITE.copy(alpha = 0.8f))
                drawPath(path { moveTo(x - 4.6f, -23f); lineTo(x + 4.6f, -23f); quadraticTo(x + 4.5f, -16f, x, -15.5f); quadraticTo(x - 4.5f, -16f, x - 4.6f, -23f); close() }, wine)
                drawLine(Color(0xFF8C93A3), Offset(x, -15f), Offset(x, -4f), 1.4f); drawLine(Color(0xFF8C93A3), Offset(x - 4f, -3.5f), Offset(x + 4f, -3.5f), 1.6f, StrokeCap.Round)
            }
            drawPath(star(31f, -34f, 3.5f), AMBER)
        }
        Prop.BED -> {
            drawRoundRect(WOOD, Offset(15f, -24f), Size(4f, 24f), CornerRadius(1.5f)); drawRoundRect(WOOD, Offset(44f, -14f), Size(3f, 14f), CornerRadius(1.5f))
            drawRect(WHITE, Offset(18f, -12f), Size(27f, 6f))
            drawRoundRect(SKY, Offset(27f, -15f), Size(18f, 9f), CornerRadius(2f))
            drawRoundRect(WHITE, Offset(19.5f, -17f), Size(8f, 5f), CornerRadius(2.5f))
            drawRect(WOOD, Offset(15f, -6f), Size(32f, 3f))
            drawPath(star(38f, -30f, 3.5f), AMBER)
        }
        Prop.CAR -> {
            drawPath(path { moveTo(21f, -17f); lineTo(25f, -26f); lineTo(37f, -26f); lineTo(42f, -17f); close() }, RED)
            drawPath(path { moveTo(24f, -18f); lineTo(26.5f, -24f); lineTo(30.5f, -24f); lineTo(30.5f, -18f); close(); moveTo(32.5f, -18f); lineTo(32.5f, -24f); lineTo(36f, -24f); lineTo(39f, -18f); close() }, SKY)
            drawRoundRect(RED, Offset(15f, -19f), Size(32f, 11f), CornerRadius(4f))
            drawCircle(AMBER, 1.8f, Offset(44f, -15f))
            for (x in listOf(23f, 39f)) { drawCircle(INK, 4.5f, Offset(x, -8f)); drawCircle(Color(0xFFBFC6D1), 1.8f, Offset(x, -8f)) }
        }
        Prop.ROAD -> {
            drawPath(path { moveTo(26f, -34f); lineTo(36f, -34f); lineTo(47f, 1f); lineTo(15f, 1f); close() }, Color(0xFF5B616B))
            listOf(Triple(-32f, -27f, 1.2f), Triple(-22f, -15f, 1.7f), Triple(-9f, 0f, 2.3f)).forEach { (a, b, w) -> drawLine(AMBER, Offset(31f, a), Offset(31f, b), w * 1.4f) }
            drawLine(WHITE, Offset(26f, -34f), Offset(15.5f, 1f), 1f); drawLine(WHITE, Offset(36f, -34f), Offset(46.5f, 1f), 1f)
        }
        Prop.TRAFFIC_LIGHT -> {
            drawLine(INK, Offset(31f, -4f), Offset(31f, 2f), 2.4f)
            drawRoundRect(INK, Offset(24f, -36f), Size(14f, 33f), CornerRadius(4f))
            drawCircle(RED.copy(alpha = 0.3f), 4f, Offset(31f, -29f)); drawCircle(AMBER.copy(alpha = 0.3f), 4f, Offset(31f, -19.5f))
            drawCircle(GREEN, 4.2f, Offset(31f, -10f)); drawCircle(WHITE.copy(alpha = 0.6f), 1.2f, Offset(29.5f, -11.5f))
        }
        Prop.FLAG_RACE -> {
            drawLine(INK, Offset(19f, -36f), Offset(19f, 2f), 2f, StrokeCap.Round)
            for (r in 0 until 4) for (c in 0 until 5) drawRect(if ((r + c) % 2 == 0) INK else WHITE, Offset(20f + c * 5f, -35f + r * 5f + c * 0.6f), Size(5f, 5f))
            drawRect(INK, Offset(20f, -35f), Size(25f, 23f), style = Stroke(0.8f))
        }
        Prop.SIGNPOST -> {
            drawRect(WOOD, Offset(29f, -32f), Size(4f, 33f))
            drawPath(path { moveTo(19f, -31f); lineTo(40f, -31f); lineTo(45f, -26.5f); lineTo(40f, -22f); lineTo(19f, -22f); close() }, AMBER)
            drawPath(path { moveTo(43f, -18f); lineTo(22f, -18f); lineTo(17f, -13.5f); lineTo(22f, -9f); lineTo(43f, -9f); close() }, GREEN)
            drawLine(WHITE, Offset(23f, -26.5f), Offset(36f, -26.5f), 1.4f, StrokeCap.Round); drawLine(WHITE, Offset(26f, -13.5f), Offset(39f, -13.5f), 1.4f, StrokeCap.Round)
        }
        Prop.STREETLAMP -> {
            drawPath(path { moveTo(36f, -26f); lineTo(25f, 1f); lineTo(47f, 1f); close() }, AMBER.copy(alpha = 0.35f))
            drawLine(NAVY, Offset(22f, 1f), Offset(22f, -33f), 2.6f, StrokeCap.Round)
            drawPath(path { moveTo(22f, -33f); quadraticTo(30f, -36f, 36f, -30f) }, NAVY, style = Stroke(2.2f, cap = StrokeCap.Round))
            drawPath(path { moveTo(31f, -30f); lineTo(41f, -30f); lineTo(39f, -26f); lineTo(33f, -26f); close() }, NAVY)
            drawCircle(AMBER, 2.4f, Offset(36f, -25f))
            drawPath(star(44f, -36f, 2.5f), AMBER)
        }
        Prop.HOUSE -> {
            drawRect(WHITE, Offset(19f, -20f), Size(24f, 19f)); drawRect(Color(0xFFB9B2A6), Offset(19f, -20f), Size(24f, 19f), style = Stroke(1f))
            drawPath(path { moveTo(15f, -19f); lineTo(31f, -34f); lineTo(47f, -19f); close() }, RED)
            drawRect(WOOD, Offset(28f, -11f), Size(6f, 10f)); drawCircle(AMBER, 0.9f, Offset(32.5f, -6f))
            drawRect(SKY, Offset(21.5f, -16f), Size(5f, 5f)); drawRect(SKY, Offset(35.5f, -16f), Size(5f, 5f))
            drawPath(heart(31f, -25f, 0.35f), WHITE)
        }
        Prop.MAP_PIN -> {
            drawOval(Color.Black.copy(alpha = 0.15f), Offset(25f, -2f), Size(12f, 4f))
            drawPath(path { moveTo(31f, 0f); cubicTo(25f, -10f, 21f, -15f, 21f, -22f); cubicTo(21f, -28f, 26f, -33f, 31f, -33f); cubicTo(36f, -33f, 41f, -28f, 41f, -22f); cubicTo(41f, -15f, 37f, -10f, 31f, 0f); close() }, RED)
            drawCircle(WHITE, 4f, Offset(31f, -22f))
        }
        Prop.MAILBOX -> {
            drawRect(WOOD, Offset(29f, -12f), Size(4f, 13f))
            drawRoundRect(SKY, Offset(17f, -28f), Size(28f, 16f), CornerRadius(7f, 7f))
            drawRoundRect(NAVY, Offset(17f, -28f), Size(9f, 16f), CornerRadius(4.5f))
            drawRect(WHITE, Offset(30f, -33f), Size(10f, 7f)); drawRect(WHITE, Offset(26f, -31f), Size(10f, 6f))
            drawLine(RED, Offset(44f, -18f), Offset(44f, -33f), 1.6f); drawRect(RED, Offset(44f, -33f), Size(5f, 4f))
        }
        Prop.TRAY -> {
            drawPath(path { moveTo(15f, -13f); lineTo(22f, -13f); lineTo(25f, -9f); lineTo(37f, -9f); lineTo(40f, -13f); lineTo(47f, -13f); lineTo(47f, 0f); lineTo(15f, 0f); close() }, Color(0xFF8C93A3))
            drawPath(path { moveTo(24f, -25f); lineTo(29f, -20f); lineTo(39f, -32f) }, GREEN, style = Stroke(3.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Prop.PENCIL -> rotate(-45f, Offset(31f, -16f)) {
            drawRect(AMBER, Offset(19f, -19.5f), Size(20f, 7f))
            drawLine(FLAME, Offset(19f, -16f), Offset(39f, -16f), 1f)
            drawPath(path { moveTo(39f, -19.5f); lineTo(46f, -16f); lineTo(39f, -12.5f); close() }, SKIN)
            drawPath(path { moveTo(44f, -17f); lineTo(46f, -16f); lineTo(44f, -15f); close() }, INK)
            drawRect(Color(0xFFBFC6D1), Offset(16f, -19.5f), Size(3f, 7f)); drawRoundRect(PINK, Offset(12f, -19.5f), Size(5f, 7f), CornerRadius(1.5f))
        }
        Prop.STONES -> {
            val grey = listOf(Color(0xFF8C93A3), Color(0xFFA9AFBC), Color(0xFFC3C8D2), Color(0xFFD9DDE4))
            listOf(Triple(-3f, 26f, 8f), Triple(-10.5f, 21f, 7f), Triple(-17f, 15f, 6f), Triple(-22.5f, 10f, 5f)).forEachIndexed { i, (y, w, h) ->
                drawOval(grey[i], Offset(31f - w / 2, y - h / 2), Size(w, h))
            }
            drawPath(path { moveTo(14f, 0f); quadraticTo(22f, -2f, 31f, 0f); quadraticTo(40f, 2f, 48f, 0f) }, SKY, style = thin)
            drawLine(GREEN, Offset(41f, -30f), Offset(41f, -22f), 1.4f, StrokeCap.Round); drawOval(GREEN, Offset(41f, -33f), Size(7f, 4f))
        }
        Prop.OWL -> {
            val feathers = Color(0xFF8B6B4A)
            drawPath(path { moveTo(22f, -28f); lineTo(24f, -35f); lineTo(28f, -30f); moveTo(40f, -28f); lineTo(38f, -35f); lineTo(34f, -30f) }, feathers, style = Stroke(2f, join = StrokeJoin.Round))
            drawOval(feathers, Offset(20f, -32f), Size(22f, 31f))
            drawOval(Color(0xFFE6D3B0), Offset(25f, -19f), Size(12f, 16f))
            for (x in listOf(26.5f, 35.5f)) { drawCircle(WHITE, 4.5f, Offset(x, -23f)); drawPath(path { moveTo(x - 2.5f, -23f); quadraticTo(x, -21f, x + 2.5f, -23f) }, INK, style = Stroke(1.3f, cap = StrokeCap.Round)) }
            drawPath(path { moveTo(29.5f, -20f); lineTo(32.5f, -20f); lineTo(31f, -17f); close() }, AMBER)
            drawLine(WOOD, Offset(16f, 0f), Offset(46f, 0f), 2.4f, StrokeCap.Round)
        }
        Prop.CAKE -> {
            drawRoundRect(Color(0xFFF7D9E3), Offset(19f, -16f), Size(24f, 14f), CornerRadius(2f))
            drawPath(path { moveTo(19f, -12f); quadraticTo(22f, -8f, 25f, -12f); quadraticTo(28f, -8f, 31f, -12f); quadraticTo(34f, -8f, 37f, -12f); quadraticTo(40f, -8f, 43f, -12f); lineTo(43f, -16f); lineTo(19f, -16f); close() }, WHITE)
            drawRect(SKY, Offset(29.5f, -26f), Size(3f, 10f))
            drawOval(FLAME, Offset(28.5f, -32f), Size(5f, 7f))
            drawCircle(RED, 1.8f, Offset(24f, -6f)); drawCircle(RED, 1.8f, Offset(38f, -6f))
        }
    }
}

private fun DrawScope.heartAt(x: Float, y: Float, s: Float) = drawPath(heart(x, y, s), PINK)

/** Tre soffi che escono dalla bocca */
private fun DrawScope.puffs(a: Float = 1f) {
    for (i in 0 until 3) {
        val y = -2f + (i - 1) * 8f; val x = 16f + i * 2f
        drawPath(path { moveTo(x, y); quadraticTo(x + 10f, y - 4f, x + 18f, y); quadraticTo(x + 22f, y + 3f, x + 18f, y + 5f) }, SKY.copy(alpha = a), style = thin)
    }
}

/** Le zeta del sonno, sempre più grandi */
private fun DrawScope.zees(x: Float, y: Float, s: Float) {
    listOf(Triple(0f, 6f, 4f), Triple(6f, -2f, 5.5f), Triple(13f, -11f, 7f)).forEach { (dx, dy, w) ->
        val ww = w * s; val ox = x + dx * s; val oy = y + dy * s
        drawPath(path { moveTo(ox, oy); lineTo(ox + ww, oy); lineTo(ox, oy + ww); lineTo(ox + ww, oy + ww) }, NAVY, style = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Il numero sull'adesivo, se ne ha uno. */
private fun label(m: Medal): String? = when (m) {
    Medal.HOUR, Medal.DAY_2H, Medal.WEEK_3H, Medal.MONTH_6H, Medal.TOTAL_10H, Medal.TOTAL_24H, Medal.TOTAL_50H, Medal.TOTAL_100H -> "${m.n / 60}h"
    Medal.UNDER_3H, Medal.UNDER_2H, Medal.UNDER_1H -> "<${m.n / 60}h"
    Medal.FEW_UNLOCKS, Medal.UNLOCKS_15 -> "<${m.n}"
    Medal.FRESH_AIR, Medal.DEEP_WORK, Medal.OFFLINE_6H, Medal.QUIET_DAY -> "${m.n}h"
    Medal.DOWNHILL -> "−20%"; Medal.HALF_DAY -> "½"; Medal.COMEBACK -> "+3"
    Medal.WEEK_1 -> "7"; Medal.MONTH_1 -> "1"; Medal.MONTHS_6 -> "6"; Medal.YEAR_1 -> "1"; Medal.YEARS_2 -> "2"
    Medal.FIRST, Medal.CLEAN_1, Medal.ROAD_1, Medal.HANDS_FREE -> null
    else -> if (m.n > 0) "${m.n}" else null
}

private fun DrawScope.badge(text: String) {
    val size = if (text.length >= 4) 9.5f else 12f
    val w = maxOf(24f, text.length * size * 0.62f + 10f)
    drawRoundRect(INK, Offset(40f - w, 22f), Size(w, 20f), CornerRadius(10f))
    drawRoundRect(PAPER, Offset(40f - w, 22f), Size(w, 20f), CornerRadius(10f), style = Stroke(2f))
    drawIntoCanvas { c ->
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE; textSize = size; textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        c.nativeCanvas.drawText(text, 40f - w / 2, 32f + size * 0.36f, p)
    }
}

/** L'adesivo come immagine trasparente, quadrata. */
fun stickerBitmap(m: Medal, px: Int = 512): android.graphics.Bitmap {
    val img = ImageBitmap(px, px)
    val k = px / 100f
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(img), Size(px.toFloat(), px.toFloat())) {
        withTransform({ translate(px / 2f, px / 2f); scale(k, k, Offset.Zero) }) { art(m) }
    }
    return img.asAndroidBitmap()
}

/** Manda l'adesivo a un'altra app (WhatsApp, Telegram…): un'immagine WebP 512×512 trasparente, come vogliono loro. */
fun shareSticker(context: Context, m: Medal, title: String) {
    val dir = java.io.File(context.cacheDir, "stickers").apply { mkdirs() }
    val file = java.io.File(dir, "sordina_${m.name.lowercase()}.webp")
    val bmp = stickerBitmap(m)
    file.outputStream().use {
        @Suppress("DEPRECATION")
        val fmt = if (android.os.Build.VERSION.SDK_INT >= 30) android.graphics.Bitmap.CompressFormat.WEBP_LOSSLESS else android.graphics.Bitmap.CompressFormat.WEBP
        bmp.compress(fmt, 100, it)
    }
    val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/webp"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Le app di Telegram (e Telegram X) che sanno importare un pacchetto di adesivi. */
fun telegramApp(context: Context): String? = listOf("org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram")
    .firstOrNull { runCatching { context.packageManager.getPackageInfo(it, 0) }.isSuccess }

/**
 * Gli adesivi vinti diventano un pacchetto di Telegram, con l'importazione ufficiale (Telegram 7.8 in poi): Telegram
 * li mostra, chiede un nome e crea il pacchetto. Ogni importazione è un pacchetto nuovo: Telegram non lascia aggiornarlo.
 * Da chiamare fuori dal thread principale (disegna gli adesivi).
 */
fun telegramImportIntent(context: Context, medals: List<Medal>, pkg: String): Intent {
    val dir = java.io.File(context.cacheDir, "stickers").apply { mkdirs() }
    val uris = ArrayList<android.net.Uri>()
    medals.forEach { m ->
        val file = java.io.File(dir, "sordina_${m.name.lowercase()}.webp")
        if (!file.exists()) file.outputStream().use {
            @Suppress("DEPRECATION")
            val fmt = if (android.os.Build.VERSION.SDK_INT >= 30) android.graphics.Bitmap.CompressFormat.WEBP_LOSSLESS else android.graphics.Bitmap.CompressFormat.WEBP
            stickerBitmap(m).compress(fmt, 100, it)
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", file)
        context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        uris += uri
    }
    return Intent("org.telegram.messenger.CREATE_STICKER_PACK").apply {
        setPackage(pkg)
        type = "image/*"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        putStringArrayListExtra("STICKER_EMOJIS", ArrayList(medals.map { WhatsAppStickers.emoji(it.group).substringBefore(',') }))
        putExtra("IMPORTER", context.packageName)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

/**
 * Le raccolte su Telegram: un pacchetto per categoria, creato una volta sola (dalla versione di prova) e uguale per tutti.
 * Quando completi una categoria, Sordina ti dà il suo link: niente di tuo viene pubblicato.
 * [LINKS]: il nome del link scelto in Telegram (t.me/addstickers/<nome>); una categoria senza link non mostra il pulsante.
 */
object TelegramPacks {
    val LINKS: Map<Medal.Group, String> = mapOf(
        Medal.Group.BRAKE to "SordinaFreno",
        Medal.Group.CLEAN to "Sordinasenzatentazioni",
        Medal.Group.TIME to "Sordinatemporipreso",
        Medal.Group.SERIES to "Sordinaserie",
        Medal.Group.LEVEL to "Sordinalivello",
        Medal.Group.PHONE to "Sordinatempo",
        Medal.Group.DRIVE to "Sordinaguida",
        Medal.Group.NOTIF to "Sordinanotifiche",
        Medal.Group.CHOICE to "Sordinascelte",
        Medal.Group.YEARS to "Sordinaanniversari",
    )

    fun link(g: Medal.Group): String? = LINKS[g]?.let { "https://t.me/addstickers/$it" }

    /** Apre il pacchetto in Telegram se c'è, se no nel browser (che propone di installarlo). */
    fun open(context: Context, g: Medal.Group) {
        val url = link(g) ?: return
        val i = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        telegramApp(context)?.let { p -> if (runCatching { context.startActivity(Intent(i).setPackage(p)) }.isSuccess) return }
        runCatching { context.startActivity(i) }
    }
}
