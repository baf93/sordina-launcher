package app.sordina.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sordina.data.AppEntry
import app.sordina.ui.theme.S
import kotlinx.coroutines.delay

/**
 * Attrito, solo per le app scelte: "Perché lo apri?". Non blocca mai:
 * con un motivo si apre subito, per abitudine dopo qualche secondo.
 */
@Composable
fun BrakeScreen(
    app: AppEntry,
    opensToday: Int,
    seconds: Int,
    onOpen: (habit: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    val c = S.c
    var habit by remember { mutableStateOf(false) }
    var left by remember { mutableIntStateOf(seconds) }
    LaunchedEffect(habit) {
        if (!habit) return@LaunchedEffect
        while (left > 0) { delay(1000); left-- }
        onOpen(true)
    }
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Cap(app.label)
        Spacer(Modifier.height(14.dp))
        Serif("Perché lo apri?", size = 32.sp)
        Spacer(Modifier.height(10.dp))
        Serif(
            when (opensToday) {
                0 -> "È la prima volta oggi."
                1 -> "L'hai già aperta una volta oggi."
                else -> "L'hai già aperta $opensToday volte oggi."
            },
            size = 16.sp, color = c.soft, italic = true,
        )
        Spacer(Modifier.weight(1f))
        if (!habit) {
            Primary("Per un motivo", Modifier.fillMaxWidth()) { onOpen(false) }
            Spacer(Modifier.height(10.dp))
            Secondary("Per abitudine", Modifier.fillMaxWidth()) { if (seconds == 0) onOpen(true) else habit = true }
        } else {
            Serif("Si apre tra $left…", size = 22.sp, italic = true)
            Spacer(Modifier.height(10.dp))
            Hint("Respira. Puoi ancora cambiare idea.")
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            Serif("Lascia stare", Modifier.clickableNoRipple(onCancel).padding(12.dp), size = 15.sp, color = c.accent)
        }
    }
}
