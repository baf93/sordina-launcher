package app.sordina.ui.waiting

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sordina.data.Held
import app.sordina.data.Level
import app.sordina.ui.Cap
import app.sordina.ui.Header
import app.sordina.ui.Primary
import app.sordina.ui.SCard
import app.sordina.ui.Serif
import app.sordina.ui.clickableNoRipple
import app.sordina.ui.theme.S
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * In attesa: raggruppata per persona (o per app). Nulla scade: resta finché non lo leggi.
 * Tocco sul gruppo: si espande. Tocco su un messaggio: si apre nell'app, nel punto giusto.
 */
@Composable
fun WaitingScreen(
    groups: List<List<Held>>,
    listenerEnabled: Boolean,
    onEnableListener: () -> Unit,
    onOpen: (Held) -> Unit,
    onReadGroup: (String) -> Unit,
    onReadAll: () -> Unit,
    onBack: () -> Unit,
) {
    val c = S.c
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp, vertical = 20.dp)) {
        Header("In attesa", onBack)
        Spacer(Modifier.height(8.dp))
        if (!listenerEnabled) {
            SCard(onClick = onEnableListener) {
                Serif("Attiva l'accesso alle notifiche", size = 15.sp)
            }
            Spacer(Modifier.height(12.dp))
        }
        if (groups.isEmpty()) {
            Spacer(Modifier.weight(1f))
            Serif("Niente in attesa", Modifier.align(Alignment.CenterHorizontally), size = 20.sp, italic = true, color = c.soft)
            Spacer(Modifier.weight(1.4f))
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(groups, key = { it.first().groupKey }) { g -> Group(g, onOpen, onReadGroup) }
                item {
                    Spacer(Modifier.height(8.dp))
                    Primary("Tutto letto", Modifier.fillMaxWidth(), onReadAll)
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun Group(g: List<Held>, onOpen: (Held) -> Unit, onReadGroup: (String) -> Unit) {
    val c = S.c
    var open by rememberSaveable(g.first().groupKey) { mutableStateOf(false) }
    val last = g.first()
    val total = g.sumOf { it.count }
    SCard(Modifier.animateContentSize(), onClick = { if (g.size == 1 && last.count <= 1) onOpen(last) else open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Serif(last.groupTitle, Modifier.weight(1f), size = 17.sp, maxLines = 1)
                        Serif(time(last.time), Modifier.padding(start = 8.dp), size = 13.sp, color = c.soft)
        }
        Spacer(Modifier.height(4.dp))
        val who = if (last.conversation != null && last.person != null) "${last.person}: " else ""
        Serif(who + last.text, size = 15.sp, color = c.ink, maxLines = if (open) 12 else 3)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Serif(
                last.appLabel + if (total > 1) " · altri ${total - 1}" else "",
                Modifier.weight(1f), size = 13.sp, color = c.soft, italic = true,
            )
            Serif("Apri", Modifier.clickableNoRipple { onOpen(last) }.padding(horizontal = 8.dp, vertical = 4.dp), size = 14.sp, color = c.accent)
            Serif("Letto", Modifier.clickableNoRipple { onReadGroup(last.groupKey) }.padding(start = 8.dp, top = 4.dp, bottom = 4.dp), size = 14.sp, color = c.accent)
        }
        if (open && g.size > 1) {
            g.drop(1).forEach { h ->
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().clickableNoRipple { onOpen(h) }) {
                    val w = if (h.conversation != null && h.person != null) "${h.person}: " else ""
                    Serif(w + h.text, Modifier.weight(1f), size = 14.sp, color = c.soft, maxLines = 4)
                    Serif(time(h.time), Modifier.padding(start = 8.dp), size = 12.sp, color = c.soft)
                }
            }
        }
    }
}

private fun time(ms: Long): String {
    val t = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
    return if (t.toLocalDate() == LocalDate.now()) t.format(DateTimeFormatter.ofPattern("H:mm"))
    else t.format(DateTimeFormatter.ofPattern("d/M H:mm"))
}
