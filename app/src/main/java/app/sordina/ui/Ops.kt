package app.sordina.ui

/** Lo stato dei permessi, ricalcolato ogni volta che si torna su Sordina. */
data class PermState(
    val home: Boolean = false,
    val listener: Boolean = false,
    val policy: Boolean = false,
    val usage: Boolean = false,
    val post: Boolean = false,
    val contacts: Boolean = false,
    val calendar: Boolean = false,
    val mic: Boolean = false,
    val call: Boolean = false,
)

/** Le operazioni di sistema che le schermate chiedono all'attività. */
interface Ops {
    fun openHomeSettings()
    fun openListenerSettings()
    fun openPolicySettings()
    fun openUsageSettings()
    fun request(vararg permissions: String)
    fun exportSettings()
    fun importSettings()
    fun openUrl(url: String)
    /** Sceglie una foto come sfondo di Casa */
    fun pickWallpaper()
    /** Ricarica lo sfondo e il colore di rilievo */
    fun backdropChanged()
    /** Dopo un cambio delle impostazioni: ricalcola proposte, scheda, silenzio. */
    fun changed()
    /** Chiede di togliere Sordina dall'ottimizzazione della batteria */
    fun openBatterySettings()
}
