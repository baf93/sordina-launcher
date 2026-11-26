package app.sordina.data

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Le riunioni online si aprono nella loro app (Teams, Zoom, Meet, Webex), non nel browser né in Outlook.
 * Su molti telefoni il link di Teams non è "verificato" per l'app: Android lo darebbe al browser o chiederebbe.
 */
object MeetingApps {
    private val BY_HOST = listOf(
        Regex("(^|\\.)teams\\.(microsoft|live)\\.com$") to listOf("com.microsoft.teams"),
        Regex("(^|\\.)zoom\\.us$") to listOf("us.zoom.videomeetings"),
        Regex("^meet\\.google\\.com$") to listOf("com.google.android.apps.tachyon", "com.google.android.apps.meetings"),
        Regex("(^|\\.)webex\\.com$") to listOf("com.cisco.webex.meetings"),
    )
    private val BY_NAME = listOf(
        Regex("\\bteams\\b", RegexOption.IGNORE_CASE) to "com.microsoft.teams",
        Regex("\\bzoom\\b", RegexOption.IGNORE_CASE) to "us.zoom.videomeetings",
        Regex("\\bgoogle meet\\b", RegexOption.IGNORE_CASE) to "com.google.android.apps.tachyon",
        Regex("\\bwebex\\b", RegexOption.IGNORE_CASE) to "com.cisco.webex.meetings",
    )

    /** Il link aperto nell'app della riunione, se c'è e lo sa aprire; se no com'è (browser o scelta di Android) */
    fun view(context: Context, url: String): Intent {
        val uri = Uri.parse(url)
        val host = uri.host?.lowercase().orEmpty()
        val pkgs = BY_HOST.firstOrNull { it.first.containsMatchIn(host) }?.second.orEmpty()
        for (p in pkgs) {
            val i = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE).setPackage(p)
            if (i.resolveActivity(context.packageManager) != null) return i
        }
        return Intent(Intent.ACTION_VIEW, uri)
    }

    /** Senza link (Outlook non lo mette nella notifica): l'app della riunione nominata nel testo, se è installata */
    fun appFor(context: Context, text: String): Intent? {
        val pkg = BY_NAME.firstOrNull { it.first.containsMatchIn(text) }?.second ?: return null
        return context.packageManager.getLaunchIntentForPackage(pkg)
    }
}
