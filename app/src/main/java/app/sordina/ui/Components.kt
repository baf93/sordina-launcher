package app.sordina.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sordina.ui.theme.S
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

/** Ora attuale, aggiornata allo scoccare di ogni minuto. */
@Composable
fun rememberNow(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            val n = LocalDateTime.now()
            now = n
            delay((60 - n.second) * 1000L - n.nano / 1_000_000 + 50)
        }
    }
    return now
}

fun LocalDateTime.dateLabel(): String {
    val l = Locale.getDefault()
    val day = dayOfWeek.getDisplayName(JTextStyle.FULL, l)
    val month = month.getDisplayName(JTextStyle.FULL, l)
    return "$day $dayOfMonth $month".replaceFirstChar { it.titlecase(l) }
}

fun LocalDateTime.shortLabel(): String {
    val l = Locale.getDefault()
    val day = dayOfWeek.getDisplayName(JTextStyle.SHORT, l).replaceFirstChar { it.titlecase(l) }
    return "$day · " + format(DateTimeFormatter.ofPattern("H:mm"))
}

/** Etichetta piccola, discreta. */
@Composable
fun Cap(text: String, modifier: Modifier = Modifier, color: Color = S.c.soft) {
    Text(text, modifier = modifier, maxLines = 1, style = TextStyle(fontSize = 12.sp, color = color))
}

@Composable
fun Serif(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 16.sp,
    color: Color = S.c.ink,
    italic: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text,
        modifier = modifier,
        maxLines = maxLines,
        style = TextStyle(
            fontFamily = S.style.serif,
            fontSize = size,
            color = color,
            fontStyle = FontStyle.Normal,
        ),
    )
}

/**
 * Linea della giornata, dalle 6 a mezzanotte, con i riepiloghi come pallini.
 * [summaries] in ore (es. 8, 13, 19).
 */
@Composable
fun DayLine(now: LocalDateTime, summaries: List<Int>, modifier: Modifier = Modifier) {
    val c = S.c
    val start = 6.0
    val end = 24.0
    fun pos(h: Double) = ((h - start) / (end - start)).coerceIn(0.0, 1.0).toFloat()
    val nowH = now.hour + now.minute / 60.0
    Canvas(modifier.fillMaxWidth().height(16.dp)) {
        val y = size.height / 2
        val stroke = 2.dp.toPx()
        drawLine(c.line, Offset(0f, y), Offset(size.width, y), stroke)
        val p = pos(nowH) * size.width
        drawLine(c.ink, Offset(0f, y), Offset(p, y), stroke)
        for (h in summaries) {
            val x = pos(h.toDouble()) * size.width
            val r = 4.dp.toPx()
            if (h > nowH) {
                drawCircle(c.bg, r, Offset(x, y))
                drawCircle(c.accent, r, Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
            } else {
                drawCircle(c.accent, r, Offset(x, y))
            }
        }
        drawLine(c.accent, Offset(p, y - 6.dp.toPx()), Offset(p, y + 6.dp.toPx()), 2.dp.toPx())
    }
}

// Elementi comuni delle schermate di servizio (impostazioni, primo avvio, Oggi)

@Composable
fun Header(title: String, onBack: (() -> Unit)? = null) {
    val c = S.c
    androidx.compose.foundation.layout.Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    ) {
        if (onBack != null) {
            Serif("←", Modifier.clickableNoRipple(onBack).padding(end = 14.dp, top = 4.dp, bottom = 4.dp), size = 20.sp, color = c.soft)
        }
        Serif(title, size = 26.sp)
    }
}

@Composable
fun Section(title: String) {
    androidx.compose.foundation.layout.Spacer(Modifier.height(22.dp))
    Cap(title)
    androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
}

@Composable
fun Rule() {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(1.dp).background(S.c.line))
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Serif(text, modifier.padding(top = 2.dp, bottom = 6.dp), size = 13.sp, color = S.c.soft, italic = true)
}

/** Riga con etichetta e valore; il valore è nel colore d'accento se si può toccare. */
@Composable
fun Line(label: String, value: String = "", hint: String? = null, onClick: (() -> Unit)? = null) {
    val c = S.c
    androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().let { if (onClick != null) it.clickableNoRipple(onClick) else it }) {
        Rule()
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Serif(label, size = 16.sp)
                if (hint != null) Serif(hint, size = 13.sp, color = c.soft, italic = true)
            }
            if (value.isNotEmpty()) Serif(value, Modifier.padding(start = 12.dp), size = 14.sp, color = if (onClick != null) c.accent else c.soft, italic = true)
        }
    }
}

@Composable
fun Toggle(label: String, checked: Boolean, hint: String? = null, onChange: (Boolean) -> Unit) {
    Line(label, if (checked) "sì" else "no", hint) { onChange(!checked) }
}

/** Scelte a pillola, una sola attiva. */
@Composable
fun Choice(label: String?, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    if (label != null) {
        Rule()
        Serif(label, Modifier.padding(top = 12.dp, bottom = 8.dp), size = 16.sp)
    }
    FlowPills(options, { it == selected }, onSelect)
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FlowPills(options: List<String>, isOn: (Int) -> Boolean, onSelect: (Int) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(bottom = 12.dp),
    ) {
        options.forEachIndexed { i, o -> Pill(o, isOn(i)) { onSelect(i) } }
    }
}

@Composable
fun Pill(text: String, on: Boolean, onClick: () -> Unit) {
    val c = S.c
    androidx.compose.foundation.layout.Box(
        Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
            .background(if (on) c.ink else c.tile)
            .clickableNoRipple(onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) { Serif(text, size = 14.sp, color = if (on) c.bg else c.ink) }
}

/** Pulsante principale, pieno nel colore dell'inchiostro. */
@Composable
fun Primary(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = S.c
    androidx.compose.foundation.layout.Box(
        modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .background(c.ink)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) { Serif(text, size = 16.sp, color = c.bg) }
}

@Composable
fun Secondary(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = S.c
    androidx.compose.foundation.layout.Box(
        modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .border(1.dp, c.line, androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) { Serif(text, size = 16.sp, color = c.ink) }
}

/** Scheda con bordo sottile, sfondo "card" del tema. */
@Composable
fun SCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val c = S.c
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
    androidx.compose.foundation.layout.Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.card)
            .border(1.dp, c.line, shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(14.dp),
        content = content,
    )
}

/** Campo di testo nello stile del tema, con riga sotto. */
@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    keyboard: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
) {
    val c = S.c
    androidx.compose.foundation.layout.Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        androidx.compose.foundation.layout.Box {
            if (value.isEmpty()) Serif(placeholder, size = 16.sp, color = c.soft, italic = true)
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(fontFamily = S.style.serif, fontSize = 16.sp, color = c.ink),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent),
                visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboard),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
        Rule()
    }
}

/** Tocco senza l'effetto a onda: più calmo, adatto alle righe di testo. */
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.then(
    Modifier.pointerInput(onClick) { detectTapGestures(onTap = { onClick() }) },
)
