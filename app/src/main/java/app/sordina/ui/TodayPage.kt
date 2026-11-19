package app.sordina.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sordina.R
import app.sordina.SordinaApp
import app.sordina.data.TodayItem
import app.sordina.service.Classifier
import app.sordina.tr
import app.sordina.ui.theme.S
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/** Il perché in parole, con i nomi delle sezioni di Impostazioni → Notifiche. */
fun reasonText(item: TodayItem): String = when (item.reason) {
    Classifier.REASON_OTP, Classifier.REASON_PAY_ALERT -> tr(R.string.notif_security)
    Classifier.REASON_APP -> tr(R.string.today_r_app, item.app)
    Classifier.REASON_CHANNEL -> tr(R.string.today_r_type)
    Classifier.REASON_PERSON -> if (item.passed) tr(R.string.today_r_person_app, item.app) else tr(R.string.today_r_person_off)
    "persona importante" -> tr(R.string.today_r_person)
    Classifier.REASON_MISSED -> tr(R.string.notif_missed)
    Classifier.REASON_REMINDER -> tr(R.string.notif_remind)
    "urgente" -> tr(R.string.today_r_urgent)
    "insiste" -> tr(R.string.today_r_insist)
    "conversazione in corso" -> tr(R.string.today_r_talk)
    "sistema" -> tr(R.string.today_r_system)
    "chiamata, sveglia o servizio" -> tr(R.string.today_r_call)
    "lasciata passare" -> tr(R.string.today_r_let)
    "modalità" -> tr(R.string.today_r_mode)
    Classifier.REASON_BRAKED -> tr(R.string.today_r_braked)
    else -> if (item.passed) tr(R.string.today_r_passed) else tr(R.string.today_r_default)
}

/** La riga in Impostazioni → Notifiche: "12 passate · 48 in sordina". */
fun todaySummary(items: List<TodayItem>): String {
    if (items.isEmpty()) return tr(R.string.today_none)
    val passed = items.count { it.passed }
    return tr(R.string.today_summary, passed, items.size - passed)
}

/**
 * Oggi: le notifiche arrivate da mezzanotte, in ordine di tempo, con il motivo sotto. Due pillole (passate, in sordina).
 * Solo da leggere: per cambiare una regola si torna in Notifiche.
 */
@Composable
fun TodayPage(app: SordinaApp) {
    val all by app.today.items.collectAsState()
    val items = remember(all) { app.today.current() }
    var passed by rememberSaveable { mutableStateOf(true) }
    val nPassed = items.count { it.passed }

    FlowPills(listOf(tr(R.string.today_passed, nPassed), tr(R.string.today_held, items.size - nPassed)), { (it == 0) == passed }) { passed = it == 0 }

    val shown = items.filter { it.passed == passed }.sortedByDescending { it.time }
    if (shown.isEmpty()) {
        Text(tr(if (passed) R.string.today_empty_passed else R.string.today_empty_held), Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp),
            style = TextStyle(fontSize = 16.sp, lineHeight = 23.sp, color = S.c.soft))
        return
    }
    val time = DateFormat.getTimeInstance(DateFormat.SHORT)
    shown.groupBy { part(it.time) }.forEach { (p, list) ->
        Topic(tr(p))
        Group {
            list.forEach { i ->
                val title = if (i.who == i.app) i.app else "${i.who} · ${i.app}"
                Line(title, time.format(Date(i.time)), reasonText(i))
            }
        }
    }
}

/** Mattina, pomeriggio, sera: come si racconta una giornata. */
private fun part(t: Long): Int {
    val h = Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.HOUR_OF_DAY)
    return when {
        h < 12 -> R.string.today_morning
        h < 18 -> R.string.today_afternoon
        else -> R.string.today_evening
    }
}
