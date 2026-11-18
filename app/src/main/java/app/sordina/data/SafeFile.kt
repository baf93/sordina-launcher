package app.sordina.data

import java.io.File

/**
 * Scrive tutto o niente: prima un file accanto, poi lo scambio. Se il telefono si spegne a metà,
 * resta la versione di prima invece di un file rotto (che alla lettura sembrerebbe vuoto).
 */
fun File.writeSafely(text: String) {
    val tmp = File(parentFile, "$name.tmp")
    tmp.writeText(text)
    if (!tmp.renameTo(this)) { writeText(text); tmp.delete() }
}
