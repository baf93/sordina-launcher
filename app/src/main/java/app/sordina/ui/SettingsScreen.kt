@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.sordina.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sordina.ui.theme.S
import app.sordina.ui.theme.ThemeFamily
import app.sordina.ui.theme.ThemeMode

/** Impostazioni: una sola lista. Per ora l'aspetto e i permessi. */
@Composable
fun SettingsScreen(
    family: ThemeFamily,
    mode: ThemeMode,
    usageGranted: Boolean,
    isDefaultHome: Boolean,
    version: String,
    onFamily: (ThemeFamily) -> Unit,
    onMode: (ThemeMode) -> Unit,
    onUsage: () -> Unit,
    onDefaultHome: () -> Unit,
) {
    val c = S.c
    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Cap("Impostazioni")
        Spacer(Modifier.height(16.dp))

        Section("Aspetto")
        Choice("Tema", ThemeFamily.entries.map { it.label }, family.ordinal) { onFamily(ThemeFamily.entries[it]) }
        Choice("Chiaro o scuro", ThemeMode.entries.map { it.label }, mode.ordinal) { onMode(ThemeMode.entries[it]) }

        Section("Permessi")
        Line("Sordina è la Home", if (isDefaultHome) "sì" else "imposta", onDefaultHome)
        Line("Accesso all'utilizzo", if (usageGranted) "concesso" else "concedi", onUsage)
        Serif(
            "Serve per le sei proposte. I dati restano sul telefono.",
            Modifier.padding(top = 4.dp), size = 13.sp, color = c.soft, italic = true,
        )

        Section("Informazioni")
        Line("Versione", version, null)
        Line("Licenza", "GPL-3.0", null)
        Serif(
            "Open source, gratuita, senza account né pubblicità.",
            Modifier.padding(top = 4.dp), size = 13.sp, color = c.soft, italic = true,
        )
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(20.dp))
    Cap(title)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun Line(label: String, value: String, onClick: (() -> Unit)?) {
    val c = S.c
    Column(Modifier.fillMaxWidth().let { if (onClick != null) it.combinedClickable(onClick = onClick) else it }) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Serif(label, size = 16.sp)
            Spacer(Modifier.weight(1f))
            Serif(value, size = 14.sp, color = if (onClick != null) c.accent else c.soft, italic = true)
        }
    }
}

@Composable
private fun Choice(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = S.c
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
    Serif(label, Modifier.padding(top = 12.dp, bottom = 8.dp), size = 16.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 12.dp)) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (on) c.ink else c.tile)
                    .combinedClickable { onSelect(i) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Serif(o, size = 14.sp, color = if (on) c.bg else c.ink)
            }
        }
    }
}
