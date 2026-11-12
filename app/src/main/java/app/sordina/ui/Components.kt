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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.Check
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
    ) {
        if (onBack != null) {
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.AutoMirrored.Rounded.ArrowBack, app.sordina.tr(app.sordina.R.string.back),
                Modifier.clickableNoRipple(onBack).padding(end = 14.dp, top = 6.dp, bottom = 6.dp).size(24.dp), tint = c.ink,
            )
        }
        Text(title, style = TextStyle(fontSize = 28.sp, color = c.ink))
    }
}

/** Titolo piccolo sopra un gruppo. */
@Composable
fun Section(title: String) {
    androidx.compose.foundation.layout.Spacer(Modifier.height(20.dp))
    Text(title, Modifier.padding(start = 16.dp, bottom = 8.dp), style = TextStyle(fontSize = 13.sp, color = S.c.soft))
}

/** Un gruppo di righe in un riquadro arrotondato, separate da una linea sottile. */
@Composable
fun Group(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    androidx.compose.foundation.layout.Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(S.c.line),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(0.5.dp),
        content = content,
    )
}

@Composable
fun Rule() {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(0.5.dp).background(S.c.line))
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(start = 4.dp, top = 6.dp, bottom = 6.dp), style = TextStyle(fontSize = 13.sp, color = S.c.soft))
}

@Composable
private fun RowIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val c = S.c
    androidx.compose.foundation.layout.Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(c.dot.copy(alpha = 0.35f)),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) { androidx.compose.material3.Icon(icon, null, Modifier.size(20.dp), tint = c.ink) }
}

/** Riga: icona, etichetta (e sotto una nota), valore a destra, freccia se porta altrove. */
@Composable
fun Line(
    label: String,
    value: String = "",
    hint: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = S.c
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().background(c.card)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 14.dp).heightIn(min = 34.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        if (icon != null) { RowIcon(icon); androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp)) }
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(label, style = TextStyle(fontSize = 16.sp, color = c.ink))
            if (hint != null) Text(hint, style = TextStyle(fontSize = 13.sp, color = c.soft))
        }
        if (value.isNotEmpty()) Text(value, Modifier.padding(start = 12.dp).widthIn(max = 150.dp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = TextStyle(fontSize = 15.sp, color = c.soft))
        if (trailing != null) { androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp)); trailing() }
        else if (onClick != null) androidx.compose.material3.Icon(
            androidx.compose.material.icons.Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
            Modifier.padding(start = 4.dp).size(20.dp), tint = c.soft,
        )
    }
}

@Composable
fun Toggle(label: String, checked: Boolean, hint: String? = null, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onChange: (Boolean) -> Unit) {
    val c = S.c
    Line(label, hint = hint, icon = icon, trailing = {
        androidx.compose.material3.Switch(
            checked, onChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedTrackColor = c.accent, checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                uncheckedTrackColor = c.bg, uncheckedBorderColor = c.dot, uncheckedThumbColor = c.soft,
            ),
        )
    }) { onChange(!checked) }
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

/** Le card sono semitrasparenti sopra uno sfondo, se lo hai scelto. Una sola regola per tutta l'app. */
val LocalGlass = androidx.compose.runtime.staticCompositionLocalOf { false }
const val GLASS_ALPHA = 0.7f

/** Il colore di ogni card, barra e blocco sopra lo sfondo: pieno, oppure vetro se attivo. */
@Composable
fun surface(): androidx.compose.ui.graphics.Color =
    if (LocalGlass.current) S.c.card.copy(alpha = GLASS_ALPHA) else S.c.card

/** Un'opzione di una scelta: nome e, se serve, due parole che la spiegano. */
data class Option(val label: String, val hint: String? = null)

/**
 * Una riga con un valore: toccandola si apre un foglio con tutte le opzioni.
 * Niente cambia finché non scegli. Con [multi] si possono scegliere più opzioni.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun Pick(
    label: String,
    value: String,
    options: List<Option>,
    isOn: (Int) -> Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    hint: String? = null,
    title: String = label,
    multi: Boolean = false,
    onSelect: (Int) -> Unit,
) {
    val c = S.c
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Line(label, value, hint, icon, trailing = {
        androidx.compose.material3.Icon(
            androidx.compose.material.icons.Icons.Rounded.UnfoldMore, null, Modifier.size(18.dp), tint = c.soft,
        )
    }) { open = true }
    if (open) Sheet(title, onDismiss = { open = false }) {
        options.forEachIndexed { i, o ->
            val on = isOn(i)
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth().clickable { onSelect(i); if (!multi) open = false }.padding(horizontal = 24.dp, vertical = 13.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                    Text(o.label, style = TextStyle(fontSize = 17.sp, color = if (on) c.accent else c.ink))
                    o.hint?.let { Text(it, style = TextStyle(fontSize = 13.sp, color = c.soft)) }
                }
                if (on) androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Rounded.Check, null, Modifier.size(22.dp), tint = c.accent,
                )
            }
        }
        if (multi) Primary(app.sordina.tr(app.sordina.R.string.done), Modifier.padding(horizontal = 20.dp, vertical = 10.dp).fillMaxWidth()) { open = false }
    }
}

/** Il foglio dal basso, uguale ovunque. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun Sheet(title: String, hint: String? = null, onDismiss: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val c = S.c
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.card,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            androidx.compose.foundation.layout.Box(
                Modifier.padding(top = 12.dp, bottom = 14.dp).size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(c.dot),
            )
        },
    ) {
        androidx.compose.foundation.layout.Column(
            Modifier.navigationBarsPadding().padding(bottom = 12.dp)
                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
        ) {
            Text(title, Modifier.padding(horizontal = 24.dp), style = TextStyle(fontSize = 21.sp, color = c.ink))
            if (hint != null) Text(hint, Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp), style = TextStyle(fontSize = 14.sp, color = c.soft))
            androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

/** Conferma per le cose che non si possono annullare. */
@Composable
fun Confirm(title: String, text: String, action: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val c = S.c
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        title = { Text(title, style = TextStyle(fontSize = 20.sp, color = c.ink)) },
        text = { Text(text, style = TextStyle(fontSize = 15.sp, color = c.soft)) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onDismiss(); onConfirm() }) { Text(action, color = c.accent) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(app.sordina.tr(app.sordina.R.string.cancel), color = c.ink) } },
    )
}
