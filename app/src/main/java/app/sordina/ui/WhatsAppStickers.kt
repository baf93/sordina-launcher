package app.sordina.ui

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import app.sordina.data.Medal
import app.sordina.data.Medal.Group
import java.io.File

/**
 * Gli adesivi vinti diventano un pacchetto di WhatsApp, che cresce con le vittorie. WhatsApp chiede i dati a questo
 * provider (solo lui: serve il suo permesso) quando aggiungi il pacchetto o lo aggiorna; le immagini si disegnano la
 * prima volta che servono e poi restano nei file dell'app. Niente rete, niente in sottofondo.
 *
 * Regole di WhatsApp: da 3 a 30 adesivi per pacchetto, 512×512 WebP sotto i 100 KB, icona 96×96 PNG.
 * Oltre i 30 si apre "Sordina 2", in ordine di vittoria così i pacchetti già aggiunti non cambiano.
 */
class WhatsAppStickers : ContentProvider() {
    override fun onCreate() = true

    /**
     * Legge solo WhatsApp vero: chi chiama deve essere WhatsApp, e il permesso "com.whatsapp.sticker.READ"
     * deve essere quello definito da WhatsApp. Senza WhatsApp installato un'altra app potrebbe definirlo lei
     * e leggere quali imprese hai vinto.
     */
    private fun allowed(ctx: Context): Boolean = runCatching {
        val caller = callingPackage ?: return false
        if (caller !in WHATSAPP) return false
        val owner = ctx.packageManager.getPermissionInfo(READ_PERMISSION, 0).packageName
        owner in WHATSAPP
    }.getOrDefault(false)

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor? {
        val ctx = context ?: return null
        if (!allowed(ctx)) return null
        val seg = uri.pathSegments
        val packs = packs(ctx)
        return when {
            seg.size == 1 && seg[0] == "metadata" -> meta(ctx, packs.indices)
            seg.size == 2 && seg[0] == "metadata" -> meta(ctx, packs.indices.filter { id(it) == seg[1] })
            seg.size == 2 && seg[0] == "stickers" -> MatrixCursor(arrayOf("sticker_file_name", "sticker_emoji", "sticker_accessibility_text")).apply {
                packs.getOrNull(index(seg[1]))?.forEach { m -> addRow(arrayOf<Any>(file(m), emoji(m.group), ctx.medalTitle(m))) }
            }
            else -> null
        }
    }

    private fun meta(ctx: Context, which: Iterable<Int>) = MatrixCursor(arrayOf(
        "sticker_pack_identifier", "sticker_pack_name", "sticker_pack_publisher", "sticker_pack_icon",
        "android_play_store_link", "ios_app_download_link", "sticker_pack_publisher_email", "sticker_pack_publisher_website",
        "sticker_pack_privacy_policy_website", "sticker_pack_license_agreement_website",
        "image_data_version", "whatsapp_will_not_cache_stickers", "animated_sticker_pack",
    )).apply {
        val packs = packs(ctx)
        which.forEach { i -> addRow(arrayOf<Any>(id(i), name(i), "Sordina", TRAY, "", "", "", "", "", "", packs[i].size.toString(), 0, 0)) }
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        val ctx = context ?: return null
        if (!allowed(ctx)) return null
        val seg = uri.pathSegments
        if (seg.size != 3 || seg[0] != "stickers_asset") return null
        val f = if (seg[2] == TRAY) tray(ctx) else Medal.entries.firstOrNull { file(it) == seg[2] }?.let { image(ctx, it) } ?: return null
        return AssetFileDescriptor(ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY), 0, AssetFileDescriptor.UNKNOWN_LENGTH)
    }

    override fun getType(uri: Uri): String? = uri.lastPathSegment?.let {
        when {
            it.endsWith(".webp") -> "image/webp"
            it.endsWith(".png") -> "image/png"
            else -> "vnd.android.cursor.dir/vnd.${uri.authority}.${uri.pathSegments.firstOrNull()}"
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0

    companion object {
        private val WHATSAPP = setOf("com.whatsapp", "com.whatsapp.w4b")
        private const val READ_PERMISSION = "com.whatsapp.sticker.READ"
        private const val TRAY = "tray.png"
        const val MIN = 3
        private const val MAX = 30

        fun authority(ctx: Context) = ctx.packageName + ".whatsapp"
        fun id(i: Int) = "sordina_${i + 1}"
        fun name(i: Int) = if (i == 0) "Sordina" else "Sordina ${i + 1}"
        private fun index(id: String) = id.removePrefix("sordina_").toIntOrNull()?.minus(1) ?: -1
        private fun file(m: Medal) = m.name.lowercase() + ".webp"

        /** I pacchetti da mandare: gli adesivi vinti in ordine di vittoria, a gruppi di 30, solo quelli che ne hanno almeno 3. */
        fun packs(ctx: Context): List<List<Medal>> = app.sordina.SordinaApp.of(ctx).habits.wonMedals().entries
            .sortedWith(compareBy({ it.value }, { it.key.ordinal })).map { it.key }
            .chunked(MAX).filter { it.size >= MIN }

        /** L'invito a WhatsApp: lui chiede conferma e poi legge il pacchetto da qui. */
        fun addIntent(ctx: Context, i: Int): Intent = Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK")
            .putExtra("sticker_pack_id", id(i)).putExtra("sticker_pack_authority", authority(ctx)).putExtra("sticker_pack_name", name(i))

        fun hasWhatsApp(ctx: Context) = listOf("com.whatsapp", "com.whatsapp.w4b").any {
            runCatching { ctx.packageManager.getPackageInfo(it, 0) }.isSuccess
        }

        private fun dir(ctx: Context) = File(ctx.filesDir, "whatsapp").apply { mkdirs() }

        private fun image(ctx: Context, m: Medal): File = File(dir(ctx), file(m)).also { f ->
            if (!f.exists()) write(f, stickerBitmap(m), webp(), 100)
        }

        private fun tray(ctx: Context): File = File(dir(ctx), TRAY).also { f ->
            if (!f.exists()) write(f, stickerBitmap(Medal.FIRST, 96), Bitmap.CompressFormat.PNG, 100)
        }

        @Suppress("DEPRECATION")
        private fun webp() = if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP

        private fun write(f: File, bmp: Bitmap, fmt: Bitmap.CompressFormat, q: Int) {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.outputStream().use { bmp.compress(fmt, q, it) }
            tmp.renameTo(f)
        }

        internal fun emoji(g: Group) = when (g) {
            Group.BRAKE -> "✋,😌"
            Group.CLEAN -> "🌿,✨"
            Group.DRIVE -> "🚗,🛣️"
            Group.TIME -> "⏳,🎉"
            Group.SERIES -> "🔥,💪"
            Group.LEVEL -> "⭐,🏆"
            Group.PHONE -> "📵,🌿"
            Group.NOTIF -> "🔕,🤫"
            Group.CHOICE -> "👍,✨"
            Group.YEARS -> "🎂,🥳"
        }

        private fun Context.medalTitle(m: Medal): String =
            resources.getIdentifier("ach_" + m.name.lowercase(), "string", packageName).takeIf { it != 0 }?.let { getString(it) } ?: "Sordina"
    }
}
