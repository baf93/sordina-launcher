package app.sordina.ui.calendar

import android.content.Intent
import android.location.Address
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sordina.R
import app.sordina.tr
import app.sordina.ui.theme.S
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Il luogo di un impegno: mentre scrivi, i posti veri che corrispondono (il geocoder di Android, lo stesso di Maps,
 * senza chiavi né account); un tocco li sceglie. Il tondo con la mappa apre il posto in Google Maps (o nell'app di
 * mappe che hai) per controllarlo.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PlaceField(value: String, onChange: (String) -> Unit, placeholder: String) {
    val c = S.c
    val context = LocalContext.current
    val style = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, color = c.ink)
    // Scelto da un suggerimento (o com'era all'apertura): niente altri suggerimenti finché non lo cambi
    var settled by remember { mutableStateOf(true) }
    var found by remember { mutableStateOf<List<Pair<String, String?>>>(emptyList()) }
    LaunchedEffect(value, settled) {
        if (settled || value.trim().length < 3 || !Geocoder.isPresent()) { found = emptyList(); return@LaunchedEffect }
        delay(450)
        found = lookup(context, value.trim())
    }
    // Il campo e i suggerimenti si fanno vedere sopra la tastiera: quando lo tocchi e quando arrivano i posti
    val into = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focused, found) { if (focused) { delay(300); into.bringIntoView() } }
    Column(Modifier.fillMaxWidth().background(c.card).bringIntoViewRequester(into)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, style = style.copy(color = c.soft))
                BasicTextField(value, { onChange(it); settled = false }, singleLine = true, textStyle = style, cursorBrush = SolidColor(c.accent),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused })
            }
            if (value.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(40.dp).clip(CircleShape).background(c.bg).clickable(onClickLabel = tr(R.string.cal_place_map)) {
                    val q = Uri.encode(value.trim())
                    val maps = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$q")).setPackage("com.google.android.apps.maps")
                    runCatching { context.startActivity(maps.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.recoverCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$q")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Map, tr(R.string.cal_place_map), Modifier.size(20.dp), tint = c.ink) }
            }
        }
        found.forEach { (name, address) ->
            Row(Modifier.fillMaxWidth().clickable {
                onChange(listOfNotNull(name, address?.takeIf { it != name }).joinToString(", ")); settled = true; found = emptyList()
            }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Place, null, Modifier.size(20.dp), tint = c.accent)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 16.sp, color = c.ink))
                    address?.takeIf { it != name }?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 13.sp, color = c.soft)) }
                }
            }
        }
    }
}

/** I posti che corrispondono a [q]: nome (il locale, la via) e indirizzo completo. Niente rete: niente suggerimenti */
private suspend fun lookup(context: android.content.Context, q: String): List<Pair<String, String?>> = withContext(Dispatchers.IO) {
    val g = Geocoder(context, Locale.getDefault())
    val list: List<Address> = runCatching {
        if (Build.VERSION.SDK_INT >= 33) withTimeoutOrNull(4_000) {
            suspendCancellableCoroutine { cont ->
                g.getFromLocationName(q, 5, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) { if (cont.isActive) cont.resume(addresses) }
                    override fun onError(errorMessage: String?) { if (cont.isActive) cont.resume(emptyList()) }
                })
            }
        } ?: emptyList()
        else @Suppress("DEPRECATION") g.getFromLocationName(q, 5).orEmpty()
    }.getOrDefault(emptyList())
    list.mapNotNull { a ->
        val line = a.getAddressLine(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        // Il nome del posto se il geocoder lo sa ("Duomo di Milano"), se no la prima parte dell'indirizzo
        val name = a.featureName?.takeIf { it.isNotBlank() && !it.all { ch -> ch.isDigit() } && !line.startsWith(it) } ?: line.substringBefore(',')
        name to line
    }.distinctBy { it.second }.take(4)
}
