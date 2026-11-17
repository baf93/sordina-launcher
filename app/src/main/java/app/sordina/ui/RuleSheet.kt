package app.sordina.ui

import app.sordina.R
import app.sordina.data.RuleOp
import app.sordina.data.RuleTarget
import app.sordina.tr
import app.sordina.ui.theme.S

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    Text(text, Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 4.dp), style = TextStyle(fontSize = 13.sp, color = S.c.soft))
}

/**
 * Le scelte su una notifica, nel modello "tutto aspetta in Sordina, tranne": far entrare chi l'ha mandata
 * (da quest'app), far entrare tutta l'app, oppure spegnere quel tipo o quella chat dentro un'app che entra.
 * Ogni riga si accende e si spegne con un tocco; la spunta dice com'è adesso.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleSheet(
    t: RuleTarget,
    ops: List<RuleOp>,
    isOn: (RuleTarget, RuleOp) -> Boolean,
    onOp: (RuleTarget, RuleOp) -> Unit,
    onDismiss: () -> Unit,
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
            Text(listOfNotNull(t.appLabel, t.channelName?.takeIf { it.isNotBlank() && t.person == null }).joinToString(" · "),
                Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp), style = TextStyle(fontSize = 14.sp, color = c.soft))
            RuleLabel(tr(R.string.rule_next))
            ops.forEach { op ->
                val (label, hint) = when (op) {
                    RuleOp.PERSON_IN -> tr(R.string.rule_person_in, t.personTitle ?: t.person.orEmpty()) to tr(R.string.rule_person_in_hint, t.appLabel)
                    RuleOp.APP_IN -> tr(R.string.rule_app_in, t.appLabel) to tr(R.string.rule_app_in_hint)
                    RuleOp.OFF -> tr(R.string.rule_off, t.kindName) to tr(R.string.rule_off_hint, t.appLabel)
                }
                LevelRow(label, hint, isOn(t, op)) { feel.confirm(); onDismiss(); onOp(t, op) }
            }
            extra?.invoke()
        }
    }
}

@Composable
private fun LevelRow(label: String, hint: String, on: Boolean, onClick: () -> Unit) {
    val c = S.c
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 17.sp, color = if (on) c.accent else c.ink))
            if (on) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = c.accent)
        }
        Text(hint, style = TextStyle(fontSize = 13.sp, color = c.soft))
    }
}
