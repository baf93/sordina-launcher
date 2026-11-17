package app.sordina.data

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import java.io.File

/**
 * Le immagini delle notifiche in sordina (la foto in chat, l'anteprima di una mail, la telecamera di casa).
 * La notifica viene posticipata e sparisce dalla tendina: l'immagine si copia subito, ridotta, nei file
 * privati di Sordina, e si cancella quando la notifica letta esce da Sordina. Mai nel backup.
 */
object NotifImages {
    private const val MAX_SIDE = 1080

    fun dir(context: Context) = File(context.filesDir, "held-img")

    fun file(context: Context, name: String) = File(dir(context), name)

    /** Salva l'immagine della notifica, se ne ha una; il nome del file, o null. */
    fun save(context: Context, sbn: StatusBarNotification): String? = runCatching {
        val name = "%08x.jpg".format((sbn.key + "|" + sbn.postTime).hashCode())
        val f = file(context, name)
        if (f.exists()) return@runCatching name
        val bmp = picture(context, sbn.notification) ?: return@runCatching null
        dir(context).mkdirs()
        val small = shrink(bmp, MAX_SIDE)
        val tmp = File(f.path + ".tmp")
        tmp.outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        tmp.renameTo(f)
        name
    }.getOrNull()

    /** Tiene solo le immagini di [keep]: le altre appartengono a notifiche già lette. */
    fun prune(context: Context, keep: Set<String>) {
        // Le appena salvate non ancora in elenco (la notifica sta entrando proprio ora) restano
        val fresh = System.currentTimeMillis() - 60_000
        dir(context).listFiles()?.forEach { if (it.name !in keep && it.lastModified() < fresh) it.delete() }
    }

    fun wipe(context: Context) { dir(context).deleteRecursively() }

    /** L'immagine per una scheda di Casa: solo in memoria, come le schede, piccola quanto basta per il fumetto. */
    fun bitmap(context: Context, sbn: StatusBarNotification): Bitmap? = runCatching { picture(context, sbn.notification)?.let { shrink(it, 720) } }.getOrNull()

    private fun picture(context: Context, n: Notification): Bitmap? {
        val e = n.extras ?: return null
        // BigPictureStyle: l'immagine grande (foto, telecamera, anteprima)
        // getParcelable con la classe esiste da Android 13: prima si chiede senza (sui 12 era un crash)
        if (Build.VERSION.SDK_INT >= 31) {
            (if (Build.VERSION.SDK_INT >= 33) e.getParcelable(Notification.EXTRA_PICTURE_ICON, Icon::class.java)
            else @Suppress("DEPRECATION") (e.getParcelable(Notification.EXTRA_PICTURE_ICON) as? Icon))?.let { icon ->
                (icon.loadDrawable(context) as? BitmapDrawable)?.bitmap?.let { return it }
            }
        }
        @Suppress("DEPRECATION")
        (e.getParcelable(Notification.EXTRA_PICTURE) as? Bitmap)?.let { return it }
        // MessagingStyle: la foto più recente mandata in chat (se l'app ce la lascia leggere)
        @Suppress("DEPRECATION")
        val msgs = e.getParcelableArray(Notification.EXTRA_MESSAGES)?.mapNotNull { it as? Bundle }.orEmpty()
        for (m in msgs.asReversed()) {
            val type = m.getString("type") ?: continue
            if (!type.startsWith("image/")) continue
            @Suppress("DEPRECATION")
            val uri = m.getParcelable("uri") as? Uri ?: continue
            decode(context, uri)?.let { return it }
        }
        return null
    }

    private fun decode(context: Context, uri: Uri): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
                val side = maxOf(info.size.width, info.size.height)
                if (side > MAX_SIDE) d.setTargetSampleSize(Integer.highestOneBit(side / MAX_SIDE).coerceAtLeast(1))
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

    private fun shrink(b: Bitmap, max: Int): Bitmap {
        val soft = if (Build.VERSION.SDK_INT >= 26 && b.config == Bitmap.Config.HARDWARE) b.copy(Bitmap.Config.ARGB_8888, false) else b
        val side = maxOf(soft.width, soft.height)
        if (side <= max) return soft
        val k = max.toFloat() / side
        return Bitmap.createScaledBitmap(soft, (soft.width * k).toInt().coerceAtLeast(1), (soft.height * k).toInt().coerceAtLeast(1), true)
    }
}
