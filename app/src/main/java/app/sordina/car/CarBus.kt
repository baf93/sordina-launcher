package app.sordina.car

import app.sordina.data.HomeRun
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Dal telefono allo schermo dell'auto: com'è andata l'impronta chiesta con "Impronta sul telefono".
 * Un solo esito alla volta; chi lo legge lo toglie.
 */
object CarBus {
    /** [done] null: hai annullato sul telefono */
    data class Outcome(val done: HomeRun.Done?, val at: Long = System.currentTimeMillis())

    val outcome = MutableStateFlow<Outcome?>(null)

    fun post(done: HomeRun.Done?) { outcome.value = Outcome(done) }

    fun take(): Outcome? = outcome.value.also { outcome.value = null }
}
