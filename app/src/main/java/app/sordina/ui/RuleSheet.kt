package app.sordina.ui

import app.sordina.R
import app.sordina.data.Level
import app.sordina.data.RuleScope
import app.sordina.data.RuleTarget
import app.sordina.tr
import app.sordina.ui.theme.S

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RuleLabel(text: String) {
    Text(text, Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 4.dp), style = TextStyle(fontSize = 13.sp, color = S.c.soft))
}

/**
 * La regola per una notifica: prima a cosa vale (solo questo tipo, questa persona o gruppo, tutta l'app),
 * poi cosa fa. Si apre sulla regola già scelta, se c'è. Le righe hanno altezza fissa: cambiando portata
 * il foglio non salta.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleSheet(
    t: RuleTarget,
    ruleOf: (RuleTarget, RuleScope) -> Level?,
    onRule: (RuleTarget, RuleScope, Level?) -> Unit,
    onDismiss: () -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    val c = S.c
    val feel = LocalFeel.current
    var scope by remember(t) { mutableStateOf(t.scopes.firstOrNull { ruleOf(t, it) != null } ?: if (t.person != null) RuleScope.PERSON else t.scopes.first()) }
    val current = ruleOf(t, scope)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.card,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { Box(Modifier.padding(top = 12.dp, bottom = 14.dp).size(width = 36.dp, height = 4.dp).clip(CircleShape).background(c.dot)) },
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp).verticalScroll(rememberScrollState())) {
            Text(t.title, Modifier.padding(horizontal = 24.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 21.sp, color = c.ink))
            Text(listOfNotNull(t.appLabel, t.channelName?.takeIf { it.isNotBlank() }).joinToString(" · "),
                Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp), style = TextStyle(fontSize = 14.sp, color = c.soft))
            RuleLabel(tr(R.string.rule_scope))
            Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                t.scopes.forEach { s ->
                    val on = s == scope
                    val rule = ruleOf(t, s)
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(if (on) c.accent.copy(alpha = 0.18f) else c.line)
                            .clickable { feel.tick(); scope = s }.padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(s.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 13.sp, color = if (on) c.accent else c.soft))
                        Text(t.name(s), maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 15.sp, color = if (on) c.accent else c.ink))
                        // Sempre una riga, anche vuota: così le portate restano alte uguali
                        Text(rule?.label ?: tr(R.string.rule_none_short), maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 12.sp, color = c.soft))
                    }
                }
            }
            RuleLabel(tr(R.string.rule_does))
            Level.entries.forEach { lv ->
                LevelRow(lv.label, lv.hint, lv == current) { feel.confirm(); onDismiss(); onRule(t, scope, lv) }
            }
            // "Togli regola" c'è sempre (spento se non c'è nulla da togliere): niente salti sotto il dito
            LevelRow(tr(R.string.rule_remove), tr(R.string.rule_none), false, enabled = current != null) { feel.confirm(); onDismiss(); onRule(t, scope, null) }
            extra?.invoke()
        }
    }
}

@Composable
private fun LevelRow(label: String, hint: String, on: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val c = S.c
    Column(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 24.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = TextStyle(fontSize = 17.sp, color = when { on -> c.accent; enabled -> c.ink; else -> c.soft.copy(alpha = 0.5f) }))
            if (on) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = c.accent)
        }
        Text(hint, style = TextStyle(fontSize = 13.sp, color = if (enabled) c.soft else c.soft.copy(alpha = 0.5f)))
    }
}

/** Una notifica della tendina, in una riga: app · tipo, titolo, e la regola che ha già. */
@Composable
fun TrayRow(t: RuleTarget, rule: Level?, icon: ImageBitmap?, onClick: () -> Unit) {
    val c = S.c
    Row(
        Modifier.padding(vertical = 3.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.card)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Image(it, null, Modifier.size(28.dp).clip(CircleShape)) } ?: Spacer(Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(t.appLabel, t.channelName?.takeIf { it.isNotBlank() }, if (t.ongoing) tr(R.string.ongoing) else null).joinToString(" · "),
                maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 12.sp, color = c.soft),
            )
            Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 15.sp, color = c.ink))
        }
        rule?.let { Text(it.label, Modifier.padding(start = 8.dp), maxLines = 1, style = TextStyle(fontSize = 12.sp, color = c.accent)) }
    }
}
