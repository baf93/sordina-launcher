package app.sordina.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sordina.R
import app.sordina.data.Habits
import app.sordina.data.Medal
import app.sordina.tr
import app.sordina.ui.theme.S
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle as DateText
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * I tuoi progressi: il livello, le giornate del mese (vinte, perse, neutre) e le medaglie Sordina.
 * Tutto dallo storico del freno, letto una volta quando apri la pagina.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ColumnScope.ProgressPage(habits: Habits, jump: Boolean = false) {
    val c = S.c
    val streak = remember { habits.streak() }
    val days = remember { habits.days() }
    // Le imprese si calcolano fuori dal thread principale (leggono anche l'utilizzo dei giorni passati)
    // Prima quelle già vinte (subito), poi il calcolo completo con il punto a cui sei arrivato per le altre
    val scored by androidx.compose.runtime.produceState<Pair<Map<Medal, LocalDate>, Medal.Score?>>(remember { habits.wonMedals() to null }) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { habits.score(fresh = true) }
    }
    val medals = scored.first
    val today = LocalDate.now()

    Player(streak)
    Row(Modifier.padding(top = 10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Fact("${streak.days}", tr(R.string.p_series), Modifier.weight(1f))
        Fact("${streak.best}", tr(R.string.p_best), Modifier.weight(1f))
        Fact(shortDuration(streak.totalMinutes), tr(R.string.p_total), Modifier.weight(1f))
    }

    // ── Le giornate: un mese alla volta, fin dove arriva lo storico
    Topic(tr(R.string.p_days), tr(R.string.p_days_hint))
    val first = YearMonth.from(days.keys.minOrNull() ?: today)
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var picked by remember { mutableStateOf<LocalDate?>(today) }
    val locale = Locale.getDefault()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.card).padding(horizontal = 12.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Arrow(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, month > first) { month = month.minusMonths(1) }
            Text(
                month.month.getDisplayName(DateText.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) } + " " + month.year,
                Modifier.weight(1f), textAlign = TextAlign.Center, style = TextStyle(fontSize = 17.sp, color = c.ink),
            )
            Arrow(Icons.AutoMirrored.Rounded.KeyboardArrowRight, month < YearMonth.from(today)) { month = month.plusMonths(1) }
        }
        Spacer(Modifier.height(8.dp))
        val letters = tr(R.string.week_letters).let { w -> if (w.contains(' ')) w.split(' ') else w.map { it.toString() } }.let { if (it.size == 7) it else List(7) { "" } }
        Row(Modifier.fillMaxWidth()) {
            letters.forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = TextStyle(fontSize = 11.sp, color = c.soft)) }
        }
        val lead = month.atDay(1).dayOfWeek.value - 1
        val cells = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        cells.chunked(7).forEach { week ->
            Row(Modifier.padding(top = 4.dp).fillMaxWidth()) {
                (0 until 7).forEach { i ->
                    val d = week.getOrNull(i)
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                        if (d != null) {
                            val v = days[d] ?: 0
                            val future = d.isAfter(today)
                            Box(
                                Modifier.fillMaxWidth().aspectRatio(1f).clip(CircleShape)
                                    .background(if (v > 0) c.accent else if (v < 0) LOSS else c.bg.copy(alpha = 0f))
                                    .then(if (d == picked) Modifier.border(2.dp, c.ink, CircleShape) else if (d == today) Modifier.border(1.5.dp, c.accent, CircleShape) else Modifier)
                                    .then(if (future) Modifier else Modifier.clickableNoRipple { picked = d }),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("${d.dayOfMonth}", style = TextStyle(fontSize = 13.sp,
                                    fontWeight = if (v != 0) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (v != 0) c.bg else if (future) c.soft.copy(alpha = 0.4f) else c.soft))
                            }
                        }
                    }
                }
            }
        }
        // La giornata che hai toccato
        picked?.let { d ->
            val v = days[d] ?: 0
            val label = d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)).replaceFirstChar { it.titlecase(locale) }
            Row(Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), style = TextStyle(fontSize = 14.sp, color = c.ink))
                Text(
                    if (v == 0) tr(R.string.p_day_none) else signed(v),
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (v > 0) c.accent else if (v < 0) LOSS else c.soft),
                )
            }
        }
    }

    // ── Gli adesivi: per gruppo, tre per riga. Toccato, si guarda grande e si manda a chi vuoi
    // Arrivando da un adesivo appena vinto, la pagina scende fin qui
    val here = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    LaunchedEffect(jump, medals.isNotEmpty()) { if (jump && medals.isNotEmpty()) here.bringIntoView(androidx.compose.ui.geometry.Rect(0f, 0f, 1f, 1800f)) }
    Box(Modifier.bringIntoViewRequester(here)) {
        Column { Topic(tr(R.string.p_medals, medals.size, Medal.entries.size), tr(R.string.p_medals_hint)) }
    }
    StickerPacks(medals)
    var open by remember { mutableStateOf<Medal?>(null) }
    Medal.Group.entries.forEach { g ->
        Text(groupName(g), Modifier.padding(start = 4.dp, top = 14.dp, bottom = 8.dp), style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium, color = c.accent))
        Medal.entries.filter { it.group == g }.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                row.forEach { m ->
                    val won = medals[m]
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).clickableNoRipple { open = m }.padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Sticker(m, won != null, 84.dp)
                        Text(medalName(m), Modifier.padding(top = 6.dp), textAlign = TextAlign.Center, maxLines = 2,
                            style = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = if (won != null) c.ink else c.soft))
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    open?.let { m ->
        val won = medals[m]
        val ctx = androidx.compose.ui.platform.LocalContext.current
        Sheet(medalName(m), onDismiss = { open = null }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.padding(vertical = 12.dp)) {
                    Sticker(m, won != null, 200.dp)
                    // Vinto: un bollino verde con la spunta, che si vede subito
                    if (won != null) Box(
                        Modifier.align(Alignment.TopEnd).size(52.dp).clip(CircleShape).background(WON).border(3.dp, c.bg, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Check, null, Modifier.size(32.dp), tint = androidx.compose.ui.graphics.Color.White) }
                }
                Text(medalHow(m), textAlign = TextAlign.Center, style = TextStyle(fontSize = 16.sp, color = c.ink))
                Text(
                    won?.let { tr(R.string.p_won_on, it.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))) } ?: tr(R.string.ach_locked),
                    Modifier.padding(top = 4.dp), style = TextStyle(fontSize = 14.sp, fontWeight = if (won != null) FontWeight.SemiBold else FontWeight.Normal, color = if (won != null) WON else c.soft),
                )
                if (won == null) scored.second?.progress(m)?.let { p ->
                    Box(Modifier.padding(top = 16.dp).fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(c.soft.copy(alpha = 0.25f))) {
                        Box(Modifier.fillMaxWidth(p.now / p.goal.toFloat()).height(10.dp).clip(RoundedCornerShape(5.dp)).background(c.accent))
                    }
                    Text(tr(R.string.ach_progress, p.now, p.goal), Modifier.padding(top = 6.dp), style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = c.ink))
                }
                if (won != null) {
                    Spacer(Modifier.height(18.dp))
                    Primary(tr(R.string.ach_share), Modifier.fillMaxWidth()) { runCatching { shareSticker(ctx, m, tr(R.string.ach_share)) } }
                }
            }
        }
    }
}

/** Il verde delle imprese vinte. */
private val WON = androidx.compose.ui.graphics.Color(0xFF2E9E5B)

@Composable
private fun Fact(value: String, label: String, modifier: Modifier = Modifier) {
    val c = S.c
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(c.card).padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = c.accent))
        Text(label, style = TextStyle(fontSize = 11.sp, color = c.soft))
    }
}

@Composable
private fun Arrow(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    val c = S.c
    Box(
        Modifier.size(36.dp).clip(CircleShape).then(if (enabled) Modifier.clickableNoRipple(onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(24.dp), tint = if (enabled) c.ink else c.soft.copy(alpha = 0.3f)) }
}

/** Il nome di un'impresa: le stringhe si chiamano ach_<nome>. */
@Composable
fun medalName(m: Medal): String = tr(achRes(m.name.lowercase()))

@Composable
private fun medalHow(m: Medal): String = tr(achRes(m.name.lowercase() + "_d"))

@Composable
private fun groupName(g: Medal.Group): String = tr(achRes("g_" + g.name.lowercase()))

@Composable
private fun achRes(key: String): Int {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    @Suppress("DiscouragedApi")
    return ctx.resources.getIdentifier("ach_$key", "string", ctx.packageName)
}

/**
 * Gli adesivi vinti come pacchetti veri. WhatsApp: un pacchetto che cresce (dal terzo adesivo), si tocca di nuovo per
 * aggiungere i nuovi. Telegram: l'importazione crea ogni volta un pacchetto nuovo con quelli vinti fin lì.
 */
@Composable
private fun StickerPacks(medals: Map<Medal, LocalDate>) {
    val c = S.c
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val wa = remember { WhatsAppStickers.hasWhatsApp(ctx) }
    val tg = remember { telegramApp(ctx) }
    if (!wa && tg == null) return
    val packs = remember(medals.size) { if (wa) WhatsAppStickers.packs(ctx) else emptyList() }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) {}
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = 4.dp, bottom = 4.dp)) {
        if (wa && packs.isEmpty()) {
            Text(tr(R.string.ach_wa_soon, WhatsAppStickers.MIN), Modifier.padding(horizontal = 4.dp, vertical = 6.dp), style = TextStyle(fontSize = 14.sp, color = c.soft))
        }
        packs.forEachIndexed { i, _ ->
            Secondary(
                if (packs.size == 1) tr(R.string.ach_wa) else tr(R.string.ach_wa_n, WhatsAppStickers.name(i)),
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
            ) { runCatching { launcher.launch(WhatsAppStickers.addIntent(ctx, i)) } }
        }
        if (tg != null && medals.isNotEmpty()) {
            Secondary(tr(R.string.ach_tg), Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                if (busy) return@Secondary
                busy = true
                scope.launch {
                    val won = medals.entries.sortedWith(compareBy({ it.value }, { it.key.ordinal })).map { it.key }
                    val intent = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { telegramImportIntent(ctx, won, tg) }.getOrNull() }
                    intent?.let { runCatching { ctx.startActivity(it) } }
                    busy = false
                }
            }
        }
        val hint = listOfNotNull(tr(R.string.ach_wa_hint).takeIf { packs.isNotEmpty() }, tr(R.string.ach_tg_hint).takeIf { tg != null && medals.isNotEmpty() })
        if (hint.isNotEmpty()) Text(hint.joinToString(" "), Modifier.padding(horizontal = 4.dp), style = TextStyle(fontSize = 13.sp, color = c.soft))
    }
}
