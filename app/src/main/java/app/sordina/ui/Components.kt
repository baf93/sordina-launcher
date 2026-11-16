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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
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
import androidx.lifecycle.repeatOnLifecycle

/**
 * Ora attuale, aggiornata allo scoccare di ogni minuto. Nessun ciclo: è Android che avvisa (il "minuto"
 * di sistema, il cambio d'ora), e solo mentre Casa si vede. In sottofondo o a schermo spento non succede niente.
 */
@Composable
fun rememberNow(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val r = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context, i: android.content.Intent) { now = LocalDateTime.now() }
        }
        val f = android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_TIME_TICK)
            addAction(android.content.Intent.ACTION_TIME_CHANGED)
            addAction(android.content.Intent.ACTION_TIMEZONE_CHANGED)
        }
        var on = false
        fun register() {
            if (on) return
            on = true
            now = LocalDateTime.now()
            androidx.core.content.ContextCompat.registerReceiver(context, r, f, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        }
        fun unregister() { if (on) { on = false; runCatching { context.unregisterReceiver(r) } } }
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            when (e) {
                androidx.lifecycle.Lifecycle.Event.ON_START -> register()
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> unregister()
                else -> {}
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs); unregister() }
    }
    return now
}

/** Ripete [block] ogni [every] millisecondi, solo mentre la schermata si vede: in sottofondo si ferma. */
@Composable
fun WhileVisible(every: Long, key: Any? = Unit, block: () -> Unit) {
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(owner, key) {
        owner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) { delay(every); block() }
        }
    }
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
fun Header(title: String, onBack: (() -> Unit)? = null, subtitle: String? = null, modifier: Modifier = Modifier) {
    val c = S.c
    // La stessa testata in ogni pagina: freccia in un tondo da 44 (comodo col pollice), titolo grande, sotto la nota
    androidx.compose.foundation.layout.Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.Top) {
        if (onBack != null) {
            androidx.compose.foundation.layout.Box(
                Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape).clickableNoRipple(onBack),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Rounded.ArrowBack, app.sordina.tr(app.sordina.R.string.back), Modifier.size(24.dp), tint = c.ink)
            }
            androidx.compose.foundation.layout.Spacer(Modifier.width(4.dp))
        }
        androidx.compose.foundation.layout.Column(Modifier.weight(1f).padding(top = 4.dp)) {
            Text(title, style = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, color = c.ink))
            if (subtitle != null) Text(subtitle, Modifier.padding(top = 2.dp), style = TextStyle(fontSize = 15.sp, color = c.soft))
        }
    }
}

/** Titolo piccolo sopra un gruppo. */
@Composable
fun Section(title: String) {
    androidx.compose.foundation.layout.Spacer(Modifier.height(20.dp))
    Text(title, Modifier.padding(start = 16.dp, bottom = 8.dp), style = TextStyle(fontSize = 13.sp, color = S.c.soft))
}

/** Un gruppo di righe in un blocco arrotondato come quelli della pagina Sordina, separate da una linea sottile. */
@Composable
fun Group(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    androidx.compose.foundation.layout.Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(S.c.line),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(1.dp),
        content = content,
    )
}

/**
 * Titolo di una parte di pagina, grande come le categorie della pagina Sordina; sotto, se serve, a cosa serve.
 */
@Composable
fun Topic(title: String, hint: String? = null) {
    androidx.compose.foundation.layout.Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 26.dp, bottom = 10.dp)) {
        Text(title, style = TextStyle(fontSize = 20.sp, color = S.c.ink))
        if (hint != null) Text(hint, Modifier.padding(top = 2.dp), style = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, color = S.c.soft))
    }
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
    // Tondo nell'accento, come i tasti della pagina Sordina
    androidx.compose.foundation.layout.Box(
        Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.accent.copy(alpha = 0.12f)),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) { androidx.compose.material3.Icon(icon, null, Modifier.size(19.dp), tint = c.accent) }
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
            .padding(horizontal = 16.dp, vertical = 14.dp).heightIn(min = 36.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        if (icon != null) { RowIcon(icon); androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp)) }
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(label, style = TextStyle(fontSize = 17.sp, color = c.ink))
            if (hint != null) Text(hint, Modifier.padding(top = 1.dp), style = TextStyle(fontSize = 13.sp, lineHeight = 17.sp, color = c.soft))
        }
        // Il valore cede per primo: su un telefono stretto con caratteri grandi l'etichetta non va spezzata a metà parola
        val valueMax = (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp * 0.45f).dp.coerceAtMost(220.dp)
        if (value.isNotEmpty()) Text(value, Modifier.padding(start = 12.dp).widthIn(max = valueMax), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = TextStyle(fontSize = 15.sp, color = c.soft))
        if (trailing != null) { androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp)); trailing() }
        else if (onClick != null) androidx.compose.material3.Icon(
            androidx.compose.material.icons.Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
            Modifier.padding(start = 4.dp).size(20.dp), tint = c.soft,
        )
    }
}

/**
 * Dove gli interruttori ricordano com'erano: le impostazioni si ricostruiscono dopo ogni modifica,
 * e senza memoria l'interruttore comparirebbe già spostato invece di scorrere.
 */
val LocalToggleMemory = androidx.compose.runtime.staticCompositionLocalOf<HashMap<String, Boolean>?> { null }

@Composable
fun Toggle(label: String, checked: Boolean, hint: String? = null, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onChange: (Boolean) -> Unit) {
    val c = S.c
    val memory = LocalToggleMemory.current
    // Primo fotogramma com'era prima, subito dopo com'è adesso: così il pallino scorre
    var shown by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(memory?.get(label) ?: checked) }
    androidx.compose.runtime.LaunchedEffect(checked) { shown = checked; memory?.set(label, checked) }
    Line(label, hint = hint, icon = icon, trailing = {
        androidx.compose.material3.Switch(
            shown, onChange,
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

/** Pulsante principale: la pillola alta 52, piena nel colore dell'inchiostro. Uguale ovunque. */
@Composable
fun Primary(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = S.c
    androidx.compose.foundation.layout.Box(
        modifier
            .height(52.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(26.dp))
            .background(c.ink)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) { Text(text, maxLines = 1, style = TextStyle(fontSize = 16.sp, color = c.bg)) }
}

@Composable
fun Secondary(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = S.c
    androidx.compose.foundation.layout.Box(
        modifier
            .height(52.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(26.dp))
            .border(1.dp, c.line, androidx.compose.foundation.shape.RoundedCornerShape(26.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) { Text(text, maxLines = 1, style = TextStyle(fontSize = 16.sp, color = c.ink)) }
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
    /** Solo una freccia, senza il valore: come una riga che porta altrove */
    arrow: Boolean = false,
    onSelect: (Int) -> Unit,
) {
    val c = S.c
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (arrow) Line(label, hint = hint, icon = icon) { open = true }
    else Line(label, value, hint, icon, trailing = {
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
fun Sheet(title: String, hint: String? = null, onDismiss: () -> Unit, leading: (@Composable () -> Unit)? = null, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
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
            androidx.compose.foundation.layout.Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                if (leading != null) { leading(); androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp)) }
                Text(title, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = TextStyle(fontSize = 21.sp, color = c.ink))
            }
            if (hint != null) Text(hint, Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp), style = TextStyle(fontSize = 14.sp, color = c.soft))
            androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

/** Conferma per le cose che non si possono annullare. */
@Composable
fun Confirm(title: String, text: String, action: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Sheet(title, text, onDismiss) {
        androidx.compose.foundation.layout.Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
            Primary(action, Modifier.fillMaxWidth()) { onDismiss(); onConfirm() }
            androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
            Secondary(app.sordina.tr(app.sordina.R.string.cancel), Modifier.fillMaxWidth(), onDismiss)
        }
    }
}

/**
 * L'informativa sull'accessibilità: cosa fa e cosa legge, con il consenso esplicito prima di aprire le impostazioni di Android
 * (Google lo chiede per i servizi di accessibilità che non sono strumenti per l'accessibilità).
 */
@Composable
fun AccessConsent() {
    val asking by app.sordina.service.SordinaAccess.asking.collectAsState()
    if (!asking) return
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val c = S.c
    val close = { app.sordina.service.SordinaAccess.asking.value = false }
    // Il testo si legge bene (colore pieno, 16 sp): l'informativa deve essere in evidenza, non una nota
    Sheet(app.sordina.tr(app.sordina.R.string.access_consent_title), onDismiss = close) {
        Text(
            app.sordina.tr(app.sordina.R.string.access_desc),
            Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp),
            style = TextStyle(fontSize = 16.sp, lineHeight = 23.sp, color = c.ink),
        )
        androidx.compose.foundation.layout.Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
            Primary(app.sordina.tr(app.sordina.R.string.access_consent_ok), Modifier.fillMaxWidth()) {
                close(); app.sordina.service.SordinaAccess.openSystemSettings(ctx)
            }
            androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
            Secondary(app.sordina.tr(app.sordina.R.string.cancel), Modifier.fillMaxWidth(), close)
        }
    }
}

/** Una riga del foglio: simbolo, etichetta, e a destra una nota. */
@Composable
fun SheetRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, hint: String? = null, accent: Boolean = false, onClick: () -> Unit) {
    val c = S.c
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        if (icon != null) { androidx.compose.material3.Icon(icon, null, Modifier.size(22.dp), tint = if (accent) c.accent else c.soft); androidx.compose.foundation.layout.Spacer(Modifier.width(16.dp)) }
        Text(label, Modifier.weight(1f), style = TextStyle(fontSize = 17.sp, color = if (accent) c.accent else c.ink))
        if (hint != null) Text(hint, Modifier.padding(start = 12.dp), style = TextStyle(fontSize = 14.sp, color = c.soft))
    }
}

/**
 * Un messaggio breve da mostrare (l'id distingue due messaggi uguali di fila).
 * [wait]: si aspetta una risposta, resta finché non arriva. [long]: una risposta da leggere, resta il tempo di leggerla.
 */
data class Said(val text: String, val wait: Boolean = false, val long: Boolean = false, val id: Long = System.nanoTime())

/** La pillola dei messaggi brevi: compare sopra la barra, sparisce da sola. */
@Composable
fun androidx.compose.foundation.layout.BoxScope.Bubble(said: Said?, onGone: () -> Unit) {
    val c = S.c
    var shown by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(said) }
    if (said != null) shown = said
    androidx.compose.runtime.LaunchedEffect(said?.id) {
        if (said == null) return@LaunchedEffect
        // In attesa al massimo 20 secondi; una risposta lunga circa una parola ogni quarto di secondo, fino a 10
        kotlinx.coroutines.delay(if (said.wait) 20_000L else if (said.long) (2500L + said.text.length * 50L).coerceAtMost(10_000L) else 2200L)
        onGone()
    }
    androidx.compose.animation.AnimatedVisibility(
        said != null,
        Modifier.align(androidx.compose.ui.Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 170.dp, start = 28.dp, end = 28.dp),
        enter = androidx.compose.animation.fadeIn(Motion.enter(220)) + androidx.compose.animation.slideInVertically(Motion.enter()) { it / 2 },
        exit = androidx.compose.animation.fadeOut(Motion.exit()),
    ) {
        Text(
            shown?.text.orEmpty(),
            Modifier.clip(RoundedCornerShape(22.dp)).background(c.ink).padding(horizontal = 20.dp, vertical = 12.dp),
            style = TextStyle(fontSize = 15.sp, color = c.bg, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
        )
    }
}

/** Pillola piccola, per le azioni dentro una riga ("Rimuovi", "Frena"). */
@Composable
fun SmallPill(text: String, onClick: () -> Unit) {
    val c = S.c
    androidx.compose.foundation.layout.Box(
        Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(c.accent.copy(alpha = 0.14f)).clickableNoRipple(onClick).padding(horizontal = 14.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) { Text(text, maxLines = 1, style = TextStyle(fontSize = 14.sp, color = c.accent)) }
}

/** Tondo con un simbolo, per le frecce dentro una riga. */
@Composable
fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = S.c
    androidx.compose.foundation.layout.Box(
        Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (enabled) c.tile else c.tile.copy(alpha = 0.4f))
            .let { if (enabled) it.clickableNoRipple(onClick) else it },
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) { androidx.compose.material3.Icon(icon, label, Modifier.size(20.dp), tint = if (enabled) c.ink else c.dot) }
}

/**
 * Come imePadding, ma salta subito alla posizione finale invece di seguire la tastiera fotogramma per fotogramma:
 * una sola impaginazione per apertura o chiusura, non venti (e il campo di testo non riavvisa la tastiera ogni volta).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@androidx.compose.runtime.Composable
fun Modifier.imeJump(): Modifier = this.then(Modifier.windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.imeAnimationTarget))

/** Quanto la tastiera (a fine animazione) sale sopra la barra di sistema: zero se è chiusa. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@androidx.compose.runtime.Composable
fun keyboardLift(): androidx.compose.ui.unit.Dp {
    val d = androidx.compose.ui.platform.LocalDensity.current
    val px = androidx.compose.foundation.layout.WindowInsets.imeAnimationTarget.getBottom(d) -
        androidx.compose.foundation.layout.WindowInsets.navigationBars.getBottom(d)
    return with(d) { px.coerceAtLeast(0).toDp() }
}

