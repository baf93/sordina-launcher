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
    /** Posizione approssimativa: serve solo al meteo. */
    val location: Boolean = false,
    /** Android non limita Sordina in batteria. */
    val battery: Boolean = false,
    /** L'accessibilità di Sordina (facoltativa): freno istantaneo e doppio tocco. */
    val access: Boolean = false,
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
    /** Mette lo sfondo di Casa anche sulla schermata di blocco; [paper] è il fondo del tema */
    fun setLockWallpaper(paper: Int)
    /** Ricarica lo sfondo e il colore di rilievo */
    fun backdropChanged()
    /** Dopo un cambio delle impostazioni: ricalcola proposte, scheda, silenzio. */
    fun changed()
    /** Chiede di togliere Sordina dall'ottimizzazione della batteria */
    fun openBatterySettings()
    /** Le scorciatoie in auto (le tue o quelle proposte) */
    fun carApps(): List<String>
    /** Chiede impronta, volto o PIN del telefono; [onOk] solo se riesce (subito, se il telefono non ha un blocco) */
    fun unlock(title: String, onOk: () -> Unit)
    /** Apre un'app nascosta: niente freno, e il cervello non la impara */
    fun openHidden(a: app.sordina.data.AppEntry)
}
