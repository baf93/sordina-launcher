package app.sordina.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import app.sordina.ui.theme.S

/** La sordina a colori: un disegno diverso per ogni posto in cui compare. */
enum class Mood {
    /** Primo avvio, permessi: è lei che chiede, con il fumetto */
    HELLO,
    /** Freno, mentre aspetti: concentrata, respira */
    WAIT,
    /** Freno, attesa finita: guarda verso di te */
    READY,
    /** Freno, hai lasciato stare: salta e ride */
    WON,
    /** Freno, hai aperto comunque: un sospiro, mai un rimprovero */
    SIGH,
    /** Pagina Sordina vuota: si gode il silenzio con un tè */
    REST,
    /** Fine del primo avvio: contenta, tra le stelline */
    DONE,
    /** Impostazioni: occhiali tondi e il quaderno delle tue regole */
    FIX,
    /** Calendario, un giorno senza impegni: occhiali da sole, il foglio del giorno bianco e un sole */
    FREE,
}

/** La sordina a contorno, per Casa e dintorni: ferma, nel colore del testo. */
enum class Pose {
    /** Riga di Casa, nessuna modalità */
    AWAKE,
    /** Riga di Casa, modalità di Android accesa: occhi chiusi */
    QUIET,
    /** Riga di Casa, di notte: cappellino */
    NIGHT,
    /** Riga di Casa, in auto: al volante, guarda la strada */
    DRIVE,
}

internal val CAP = Color(0xFF9A6A16)
internal val BODY = Color(0xFFE8A93A)
internal val BAND = Color(0xFFB07A1E)
internal val FACE = Color(0xFF3A2A0E)
internal val CHEEK = Color(0xFFD85A30).copy(alpha = 0.4f)
internal val PAPER = Color(0xFFFFFBF4)
private val HAT = Color(0xFFD85A30)
private val BRIM = Color(0xFF993C1D)
private val STEEL = Color(0xFF8C8A86)
private val TONGUE = Color(0xFFED93B1)

/** Lo spazio del disegno, in unità: un quadrato da -56 a 56 in x, da -66 a 46 in y. */
private const val V = 112f
private const val VCY = -10f

/**
 * La mascotte: una sordina d'ottone, disegno vettoriale.
 *
 * Batteria: si muove solo nei passaggi (un salto quando vinci, saluta o hai finito, un sospiro quando apri
 * comunque) e, nell'attesa del freno, respira piano finché il freno è sullo schermo. Altrove è ferma.
 */
@Composable
fun Mascot(
    mood: Mood, modifier: Modifier = Modifier,
    /** Esercizio di respirazione guidato da fuori: la fase del respiro (0..1: prima metà inspira, seconda espira), letta solo quando si disegna. Nel freno va con l'anello */
    breath: (() -> Float)? = null,
) {
    val anim = LocalFeel.current.animations
    val soft = S.c.soft
    val breathe = if (mood == Mood.WAIT && anim && breath == null) {
        val b by rememberInfiniteTransition(label = "breathe").animateFloat(
            0f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b",
        )
        b
    } else 0f
    val hop = remember { Animatable(1f) }
    LaunchedEffect(mood) {
        if ((mood == Mood.WON || mood == Mood.HELLO || mood == Mood.DONE) && anim) {
            hop.snapTo(0f); hop.animateTo(1f, tween(900, easing = LinearEasing))
        }
    }
    val sigh by animateFloatAsState(if (mood == Mood.SIGH) 1f else 0f, tween(if (anim) 350 else 0), label = "sigh")

    Canvas(modifier) {
        val k = minOf(size.width, size.height) / V
        val (hy, hx, hs) = hopAt(hop.value)
        val dy = hy + 4f * sigh
        val ph = breath?.invoke() ?: -1f
        val bx = if (ph < 0f) 0f else ((1 - kotlin.math.cos(2 * Math.PI * ph)) / 2).toFloat()
        val sx = hx * (1f + 0.03f * breathe) * (1f + 0.02f * sigh) * (1f + 0.13f * bx)
        val sy = hs * (1f - 0.03f * breathe) * (1f - 0.04f * sigh) * (1f + 0.16f * bx)
        withTransform({
            translate(size.width / 2, size.height / 2 - VCY * k)
            scale(k, k, Offset.Zero)
        }) {
            // Fumetto, tazza e stelline restano fermi: si muove solo la sordina
            extras(mood, soft)
            // I soffi d'aria escono dalla bocca mentre espira
            if (ph >= 0.5f) puffs((ph - 0.5f) * 2f, soft)
            withTransform({
                translate(0f, dy)
                scale(sx, sy, Offset(0f, 40f))
            }) { body(mood, ph) }
        }
    }
}

/** Il salto in quattro tempi: si schiaccia, sale, atterra, si assesta. (spostamento, larghezza, altezza) */
private fun hopAt(t: Float): Triple<Float, Float, Float> {
    if (t >= 1f) return Triple(0f, 1f, 1f)
    val keys = floatArrayOf(0f, 0.18f, 0.45f, 0.70f, 1f)
    val dy = floatArrayOf(0f, 0f, -22f, 0f, 0f)
    val sx = floatArrayOf(1f, 1.12f, 0.94f, 1.06f, 1f)
    val sy = floatArrayOf(1f, 0.82f, 1.08f, 0.95f, 1f)
    var i = 0
    while (i < 3 && t > keys[i + 1]) i++
    val u = ((t - keys[i]) / (keys[i + 1] - keys[i])).coerceIn(0f, 1f)
    val e = u * u * (3 - 2 * u)
    fun l(a: FloatArray) = a[i] + (a[i + 1] - a[i]) * e
    return Triple(l(dy), l(sx), l(sy))
}

internal fun path(build: Path.() -> Unit) = Path().apply(build)

internal fun DrawScope.sparkle(x: Float, y: Float, r: Float, color: Color) {
    drawPath(path { moveTo(x, y - r); lineTo(x, y + r); moveTo(x - r, y); lineTo(x + r, y) }, color, style = Stroke(2.6f, cap = StrokeCap.Round))
}

private fun DrawScope.extras(mood: Mood, soft: Color) {
    when (mood) {
        // Il fumetto con i puntini: sta per chiederti qualcosa
        Mood.HELLO -> {
            drawPath(path { moveTo(24f, -40f); lineTo(20f, -32f); lineTo(31f, -40f); close() }, PAPER)
            drawRoundRect(PAPER, Offset(20f, -62f), Size(34f, 22f), CornerRadius(11f))
            drawRoundRect(BAND, Offset(20f, -62f), Size(34f, 22f), CornerRadius(11f), style = Stroke(1.6f))
            for (x in listOf(29f, 37f, 45f)) drawCircle(FACE, 2.3f, Offset(x, -51f))
        }
        // Una tazza fumante accanto
        Mood.REST -> {
            val cup = path { moveTo(34f, 22f); lineTo(52f, 22f); quadraticTo(52f, 40f, 43f, 40f); quadraticTo(34f, 40f, 34f, 22f); close() }
            drawPath(cup, PAPER)
            drawPath(cup, BAND, style = Stroke(1.8f, join = StrokeJoin.Round))
            drawArc(BAND, -90f, 180f, false, Offset(47f, 25f), Size(9f, 9f), style = Stroke(1.8f))
            val steam = Stroke(1.8f, cap = StrokeCap.Round)
            drawPath(path { moveTo(40f, 17f); quadraticTo(37f, 13f, 40f, 9f); quadraticTo(43f, 5f, 40f, 1f) }, soft, style = steam)
            drawPath(path { moveTo(46f, 17f); quadraticTo(43f, 13f, 46f, 9f) }, soft, style = steam)
        }
        // Il quaderno degli appunti: ci scrive le tue regole
        Mood.FIX -> {
            drawRoundRect(PAPER, Offset(-58f, -8f), Size(20f, 26f), CornerRadius(3f))
            drawRoundRect(BAND, Offset(-58f, -8f), Size(20f, 26f), CornerRadius(3f), style = Stroke(1.8f))
            val lines = Stroke(1.6f, cap = StrokeCap.Round)
            drawPath(path { moveTo(-53f, 0f); lineTo(-43f, 0f); moveTo(-53f, 6f); lineTo(-43f, 6f); moveTo(-53f, 12f); lineTo(-47f, 12f) }, BAND, style = lines)
        }
        // Il foglio del giorno, bianco: niente da fare. In alto a destra un piccolo sole
        Mood.FREE -> {
            val page = Stroke(1.8f, join = StrokeJoin.Round)
            drawRoundRect(PAPER, Offset(-60f, -8f), Size(24f, 30f), CornerRadius(4f))
            drawRoundRect(HAT, Offset(-60f, -8f), Size(24f, 9f), CornerRadius(4f))
            drawRect(HAT, Offset(-60f, -3f), Size(24f, 4f))
            drawRoundRect(BAND, Offset(-60f, -8f), Size(24f, 30f), CornerRadius(4f), style = page)
            drawCircle(CAP, 2f, Offset(-53f, -8f)); drawCircle(CAP, 2f, Offset(-43f, -8f))
            // Un angolo che si piega: il foglio si stacca, la giornata è tua
            drawPath(path { moveTo(-42f, 22f); lineTo(-36f, 16f); lineTo(-42f, 16f); close() }, BODY.copy(alpha = 0.35f))
            val ray = Stroke(2.2f, cap = StrokeCap.Round)
            drawCircle(BODY, 6.5f, Offset(42f, -44f))
            for (i in 0 until 8) {
                val a = Math.PI * i / 4
                val c = kotlin.math.cos(a).toFloat(); val sn = kotlin.math.sin(a).toFloat()
                drawPath(path { moveTo(42f + 10f * c, -44f + 10f * sn); lineTo(42f + 14f * c, -44f + 14f * sn) }, BAND, style = ray)
            }
        }
        Mood.DONE -> {
            sparkle(-40f, -36f, 6f, BODY); sparkle(40f, -46f, 5f, BAND)
            sparkle(46f, -6f, 3.5f, BODY); sparkle(-46f, -4f, 3.5f, BAND); sparkle(-30f, -56f, 3f, BODY)
        }
        else -> {}
    }
}

internal fun bodyPath() = path {
    moveTo(-12f, -42f); quadraticTo(0f, -46f, 12f, -42f); lineTo(33f, 26f)
    quadraticTo(34f, 40f, 0f, 40f); quadraticTo(-34f, 40f, -33f, 26f); close()
}

internal fun DrawScope.body(mood: Mood, ph: Float = -1f) {
    drawRoundRect(CAP, Offset(-9f, -54f), Size(18f, 14f), CornerRadius(6f))
    drawPath(bodyPath(), BODY)
    drawPath(path { moveTo(-30f, 24f); quadraticTo(0f, 34f, 30f, 24f); lineTo(32f, 30f); quadraticTo(0f, 40f, -32f, 30f); close() }, BAND)
    // Guance più grandi quando ride; gonfie mentre inspira
    val b = when {
        mood == Mood.WON || mood == Mood.DONE -> 1.25f
        ph in 0f..0.5f -> 1f + 0.5f * kotlin.math.sin(Math.PI * ph * 2).toFloat()
        else -> 1f
    }
    drawOval(CHEEK, Offset(-17f - 5f * b, 12f - 3f * b), Size(10f * b, 6f * b))
    drawOval(CHEEK, Offset(17f - 5f * b, 12f - 3f * b), Size(10f * b, 6f * b))
    if (ph >= 0f) breathFace(ph) else face(mood)
}

/** La faccia dell'esercizio: inspira con gli occhi in su e la bocca chiusa, espira rilassata con la bocca a "o". */
internal fun DrawScope.breathFace(ph: Float) {
    val s = Stroke(2.5f, cap = StrokeCap.Round)
    if (ph < 0.5f) {
        drawCircle(FACE, 3.4f, Offset(-8f, 0f)); drawCircle(FACE, 3.4f, Offset(8f, 0f))
        drawCircle(PAPER, 1.1f, Offset(-7f, -1.2f)); drawCircle(PAPER, 1.1f, Offset(9f, -1.2f))
        drawPath(path { moveTo(-3f, 14f); lineTo(3f, 14f) }, FACE, style = s)
    } else {
        val e = (ph - 0.5f) * 2f
        drawPath(path { moveTo(-12f, 3f); quadraticTo(-8f, 6f, -4f, 3f); moveTo(4f, 3f); quadraticTo(8f, 6f, 12f, 3f) }, FACE, style = s)
        // La "o" si apre e poi si chiude piano
        val r = 2f + 2.5f * kotlin.math.sin(Math.PI * e).toFloat()
        drawOval(FACE, Offset(-r, 14f - r * 1.1f), Size(r * 2, r * 2.2f))
    }
}

/** Tre soffi che escono dalla bocca e svaniscono allontanandosi. [e] va da 0 a 1 durante l'espirazione. */
private fun DrawScope.puffs(e: Float, soft: Color) {
    val line = Stroke(2.2f, cap = StrokeCap.Round)
    for (i in 0 until 3) {
        val t = (e * 1.4f - i * 0.2f).coerceIn(0f, 1f)
        if (t <= 0f || t >= 1f) continue
        val x = 12f + 34f * t
        val y = 14f + (i - 1) * 7f * t
        drawPath(path { moveTo(x, y - 4f); quadraticTo(x + 3f, y, x, y + 4f) }, soft.copy(alpha = (1f - t) * 0.9f), style = line)
    }
}

internal fun DrawScope.face(mood: Mood) {
    val s = Stroke(2.5f, cap = StrokeCap.Round)
    fun stroke(build: Path.() -> Unit) = drawPath(path(build), FACE, style = s)
    fun eyes(x: Float = 0f, y: Float = 0f) { drawCircle(FACE, 3.2f, Offset(-8f + x, 2f + y)); drawCircle(FACE, 3.2f, Offset(8f + x, 2f + y)) }
    fun happyEyes() = stroke { moveTo(-12f, 3f); quadraticTo(-8f, -3f, -4f, 3f); moveTo(4f, 3f); quadraticTo(8f, -3f, 12f, 3f) }
    when (mood) {
        // Occhi in su, verso il fumetto
        Mood.HELLO -> {
            eyes(1.5f, -1.5f)
            stroke { moveTo(-6f, 12f); quadraticTo(0f, 18f, 6f, 12f) }
        }
        Mood.WAIT -> stroke {
            moveTo(-12f, 2f); lineTo(-4f, 2f); moveTo(4f, 2f); lineTo(12f, 2f)
            moveTo(-4f, 13f); lineTo(4f, 13f)
        }
        // Occhi grandi verso il basso, dove ci sono i tasti; la bocca a "o"
        Mood.READY -> {
            drawCircle(FACE, 3.8f, Offset(-8f, 3.5f)); drawCircle(FACE, 3.8f, Offset(8f, 3.5f))
            drawCircle(PAPER, 1.2f, Offset(-6.8f, 2.2f)); drawCircle(PAPER, 1.2f, Offset(9.2f, 2.2f))
            stroke { moveTo(-2.5f, 14f); quadraticTo(0f, 11f, 2.5f, 14f); quadraticTo(0f, 17f, -2.5f, 14f) }
        }
        Mood.WON -> {
            happyEyes()
            drawPath(path { moveTo(-7f, 10f); lineTo(7f, 10f); quadraticTo(6f, 19f, 0f, 19f); quadraticTo(-6f, 19f, -7f, 10f); close() }, FACE)
        }
        Mood.SIGH -> stroke {
            moveTo(-12f, 3f); lineTo(-4f, 3f); moveTo(4f, 3f); lineTo(12f, 3f)
            moveTo(-4f, 15f); quadraticTo(0f, 13f, 4f, 15f)
        }
        // Occhi chiusi rivolti alla tazza
        Mood.REST -> stroke {
            moveTo(-10f, 4f); quadraticTo(-6f, -1f, -2f, 4f); moveTo(6f, 4f); quadraticTo(10f, -1f, 14f, 4f)
            moveTo(-2f, 13f); quadraticTo(3f, 17f, 8f, 13f)
        }
        // Gli occhiali tondi, gli occhi sul quaderno, un mezzo sorriso
        Mood.FIX -> {
            val glass = Stroke(2f)
            drawCircle(PAPER.copy(alpha = 0.5f), 6.5f, Offset(-9f, 2f)); drawCircle(PAPER.copy(alpha = 0.5f), 6.5f, Offset(9f, 2f))
            drawCircle(FACE, 6.5f, Offset(-9f, 2f), style = glass); drawCircle(FACE, 6.5f, Offset(9f, 2f), style = glass)
            drawPath(path { moveTo(-2.5f, 1f); quadraticTo(0f, -1f, 2.5f, 1f) }, FACE, style = glass)
            drawCircle(FACE, 2.3f, Offset(-10.5f, 3f)); drawCircle(FACE, 2.3f, Offset(7.5f, 3f))
            stroke { moveTo(-4f, 14f); quadraticTo(0f, 16f, 4f, 14f) }
        }
        // Occhiali da sole, un riflesso sulle lenti, il sorriso di chi non ha niente da fare
        Mood.FREE -> {
            drawRoundRect(FACE, Offset(-16f, -3f), Size(13f, 10f), CornerRadius(5f))
            drawRoundRect(FACE, Offset(3f, -3f), Size(13f, 10f), CornerRadius(5f))
            drawPath(path { moveTo(-3f, 0f); quadraticTo(0f, -2f, 3f, 0f) }, FACE, style = Stroke(2f))
            val shine = Stroke(1.4f, cap = StrokeCap.Round)
            drawPath(path { moveTo(-13f, 0f); lineTo(-10f, -1.5f) }, PAPER.copy(alpha = 0.7f), style = shine)
            drawPath(path { moveTo(6f, 0f); lineTo(9f, -1.5f) }, PAPER.copy(alpha = 0.7f), style = shine)
            stroke { moveTo(-6f, 12f); quadraticTo(0f, 18f, 6f, 12f) }
        }
        // Occhi felici e un sorriso largo
        Mood.DONE -> {
            happyEyes()
            stroke { moveTo(-8f, 11f); quadraticTo(0f, 20f, 8f, 11f) }
        }
    }
}

/**
 * La sordina stilizzata: solo contorno, nel colore che le dai, ferma. Per la riga di Casa,
 * dove deve esserci senza disturbare.
 */
@Composable
fun MascotGlyph(pose: Pose, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        // Un quadrato da -52 a 52 in x, da -60 a 44 in y
        val k = minOf(size.width, size.height) / 104f
        val line = Stroke(1.6f * density / k, cap = StrokeCap.Round, join = StrokeJoin.Round)
        withTransform({
            translate(size.width / 2, size.height / 2 + 8f * k)
            scale(k, k, Offset.Zero)
        }) {
            // Il volante davanti: la sordina dietro non si vede attraverso
            val wheel = path { addOval(androidx.compose.ui.geometry.Rect(Offset(0f, 24f), 16f)) }
            if (pose == Pose.DRIVE) {
                drawPath(wheel, tint, style = line)
                drawCircle(tint, 4.5f, Offset(0f, 24f))
                drawPath(path { moveTo(-16f, 22f); lineTo(-4f, 24f); moveTo(4f, 24f); lineTo(16f, 22f); moveTo(0f, 28f); lineTo(0f, 40f) }, tint, style = line)
            }
            withTransform({ if (pose == Pose.DRIVE) clipPath(wheel, androidx.compose.ui.graphics.ClipOp.Difference) }) {
                if (pose == Pose.NIGHT) {
                    drawPath(path { moveTo(-12f, -43f); quadraticTo(0f, -62f, 22f, -56f); quadraticTo(12f, -52f, 12f, -43f) }, tint, style = line)
                    drawCircle(tint, 5f, Offset(25f, -56f))
                } else drawRoundRect(tint, Offset(-9f, -54f), Size(18f, 11f), CornerRadius(5f), style = line)
                drawPath(bodyPath(), tint, style = line)
                if (pose != Pose.DRIVE) drawPath(path { moveTo(-30f, 25f); quadraticTo(0f, 34f, 30f, 25f) }, tint, style = line)
                when (pose) {
                    Pose.NIGHT -> drawPath(path {
                        moveTo(-15f, 2f); quadraticTo(-9.5f, 8f, -4f, 2f); moveTo(4f, 2f); quadraticTo(9.5f, 8f, 15f, 2f)
                    }, tint, style = line)
                    Pose.QUIET -> {
                        drawPath(path {
                            moveTo(-15f, 5f); quadraticTo(-9.5f, -2f, -4f, 5f); moveTo(4f, 5f); quadraticTo(9.5f, -2f, 15f, 5f)
                        }, tint, style = line)
                        // In sordina: una zeta accanto al cappuccio, si vede anche da piccola
                        drawPath(path { moveTo(20f, -58f); lineTo(34f, -58f); lineTo(20f, -44f); lineTo(34f, -44f) }, tint, style = line)
                    }
                    // Occhi grandi sulla strada
                    Pose.DRIVE -> { drawCircle(tint, 5f, Offset(-9f, -2f)); drawCircle(tint, 5f, Offset(9f, -2f)) }
                    Pose.AWAKE -> { drawCircle(tint, 4.6f, Offset(-9f, 3f)); drawCircle(tint, 4.6f, Offset(9f, 3f)) }
                }
            }
        }
    }
}


