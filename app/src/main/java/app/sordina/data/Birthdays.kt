package app.sordina.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import java.time.LocalDate

/** Un compleanno di oggi dalla rubrica: nome e, se si sa l'anno, quanti anni compie. */
data class Birthday(val name: String, val age: Int?)

/** I compleanni di oggi, letti dalla rubrica sul telefono. Basta il permesso dei contatti. */
fun birthdaysToday(context: Context, today: LocalDate = LocalDate.now()): List<Birthday> {
    if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return emptyList()
    val out = LinkedHashMap<String, Birthday>()
    // "1990-05-12", "--05-12" o "19900512": si guardano mese e giorno
    val md = Regex("(\\d{4}|-)-?(\\d{2})-?(\\d{2})")
    runCatching {
        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data.DISPLAY_NAME, ContactsContract.CommonDataKinds.Event.START_DATE),
            "${ContactsContract.Data.MIMETYPE}=? AND ${ContactsContract.CommonDataKinds.Event.TYPE}=?",
            arrayOf(ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString()),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0)?.trim().orEmpty()
                val m = md.find(c.getString(1).orEmpty()) ?: continue
                if (name.isBlank() || m.groupValues[2].toInt() != today.monthValue || m.groupValues[3].toInt() != today.dayOfMonth) continue
                val year = m.groupValues[1].toIntOrNull()?.takeIf { it in 1900 until today.year }
                out[name] = Birthday(name, year?.let { today.year - it })
            }
        }
    }
    return out.values.toList()
}
