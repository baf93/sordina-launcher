package app.sordina.ui

import app.sordina.R
import app.sordina.data.RuleOp
import app.sordina.data.RuleTarget
import app.sordina.tr
import app.sordina.ui.theme.S

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RuleLabel(text: String) {
    Text(text, Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 6.dp), style = TextStyle(fontSize = 13.sp, color = S.c.soft))
}

/**
 * Le scelte su una notifica, nel modello "tutto aspetta in Sordina, tranne": far entrare chi l'ha mandata,
 * far entrare tutta l'app, oppure far aspettare quel tipo o quella chat dentro un'app che entra.
 * Poche righe, nessuna spiegazione: il cerchio pieno dice com'è adesso, un tocco lo cambia.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleSheet(
    t: RuleTarget,
    ops: List<RuleOp>,
    isOn: (RuleTarget, RuleOp) -> Boolean,
    onOp: (RuleTarget, RuleOp) -> Unit,
    onDismiss: () -> Unit,
    top: (@Composable () -> Unit)? = null,
    /** Una nota sotto la scelta ("Entra già ovunque"): la riga allora si legge ma non si tocca */
    note: (RuleTarget, RuleOp) -> String? = { _, _ -> null },
    extra: (@Composable () -> Unit)? = null,
) {
    val c = S.c
    val feel = LocalFeel.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.card,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { Box(Modifier.padding(top = 12.dp, bottom = 14.dp).size(width = 36.dp, height = 4.dp).clip(CircleShape).background(c.dot)) },
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp).verticalScroll(rememberScrollState())) {
            Text(t.title, Modifier.padding(horizontal = 24.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 21.sp, color = c.ink))
            Text(t.appLabel, Modifier.padding(start = 24.dp, end = 24.dp, top = 2.dp, bottom = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontSize = 14.sp, color = c.soft))
            top?.invoke()
            ops.forEach { op ->
                val label = when (op) {
                    RuleOp.PERSON_IN -> tr(R.string.rule_person_in, t.personTitle ?: t.person.orEmpty())
                    RuleOp.APP_IN -> tr(R.string.rule_app_in, t.appLabel)
                    RuleOp.OFF -> tr(R.string.rule_off, t.kindName)
                }
                val n = note(t, op)
                CheckRow(label, isOn(t, op), n, enabled = n == null) { feel.confirm(); onDismiss(); onOp(t, op) }
            }
            extra?.invoke()
        }
    }
}

/** Una scelta che resta: a destra il cerchio, pieno quando vale. */
@Composable
private fun CheckRow(label: String, on: Boolean, note: String? = null, enabled: Boolean = true, onClick: () -> Unit) {
    val c = S.c
    Row(
        // Una casella da spuntare anche per TalkBack: dice "selezionato" o "non selezionato"
        Modifier.fillMaxWidth()
            .toggleable(value = on, enabled = enabled, role = Role.Checkbox, onValueChange = { onClick() })
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 17.sp, color = c.ink))
            note?.let { Text(it, Modifier.padding(top = 2.dp), style = TextStyle(fontSize = 14.sp, color = c.soft)) }
        }
        Spacer(Modifier.width(16.dp))
        Box(
            Modifier.size(24.dp).clip(CircleShape).let { m -> if (on) m.background(c.accent) else m.border(1.5.dp, c.dot, CircleShape) },
            contentAlignment = Alignment.Center,
        ) { if (on) Icon(Icons.Rounded.Check, null, Modifier.size(15.dp), tint = c.bg) }
    }
}
