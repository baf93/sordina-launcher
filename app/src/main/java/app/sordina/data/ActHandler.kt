package app.sordina.data

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Telephony
import android.telecom.TelecomManager

/**
 * Quale app eseguirà un'azione, con le stesse scelte di [Actions]: serve a mostrarne l'icona accanto
 * alla proposta ("Scrivi a Elisa" con WhatsApp, "Chiama" con il Telefono). Null se la fa Sordina da sola
 * (torcia, casa, calcoli) o se Android chiederebbe ogni volta con quale app.
 */
object ActHandler {
    fun packageOf(context: Context, act: Act): String? = runCatching {
        val pm = context.packageManager
        fun installed(p: String) = runCatching { pm.getPackageInfo(p, 0) }.isSuccess
        fun resolve(i: Intent): String? = pm.resolveActivity(i, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName?.takeIf { it != "android" && !it.contains("resolver") }
        when (act) {
            is Act.Call -> context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
                ?: resolve(Intent(Intent.ACTION_DIAL, Uri.parse("tel:1")))
            is Act.Message -> if (act.via == Via.SMS) Telephony.Sms.getDefaultSmsPackage(context)
                else act.via.pkgs.firstOrNull { installed(it) }
            is Act.Timer -> resolve(Intent(AlarmClock.ACTION_SET_TIMER))
            is Act.Alarm -> resolve(Intent(AlarmClock.ACTION_SET_ALARM))
            is Act.Navigate -> "com.google.android.apps.maps".takeIf { installed(it) }
                ?: resolve(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=x")))
            is Act.Music -> "com.spotify.music".takeIf { installed(it) }
                ?: resolve(Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH))
            is Act.Web -> resolve(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, "x"))
                ?: resolve(Intent(Intent.ACTION_VIEW, Uri.parse("https://duckduckgo.com")))
            else -> null
        }
    }.getOrNull()
}
