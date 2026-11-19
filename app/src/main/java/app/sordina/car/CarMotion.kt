package app.sordina.car

import androidx.car.app.CarContext
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.Speed
import androidx.core.content.ContextCompat

/**
 * Se l'auto si muove, per dire "fermati" prima di chiedere il codice dell'allarme o l'impronta.
 * Non tutte le auto la danno, e Android Auto può non concederla: allora [moving] resta null e decide Android Auto,
 * che comunque non mostra la tastiera e non fa toccare i pulsanti "solo da fermi" mentre guidi.
 */
class CarMotion {
    /** true in movimento, false fermi, null se l'auto non lo dice */
    @Volatile var moving: Boolean? = null
        private set

    private val listener = OnCarDataAvailableListener<Speed> { s ->
        fun ok(v: CarValue<Float>) = v.status == CarValue.STATUS_SUCCESS && v.value != null
        val v = s.displaySpeedMetersPerSecond.takeIf(::ok) ?: s.rawSpeedMetersPerSecond.takeIf(::ok)
        // Sopra i 5 km/h si guida; al semaforo si è fermi
        moving = v?.value?.let { it > MOVING_MPS }
    }

    fun start(cc: CarContext) {
        runCatching { cc.getCarService(CarHardwareManager::class.java).carInfo.addSpeedListener(ContextCompat.getMainExecutor(cc), listener) }
    }

    fun stop(cc: CarContext) {
        runCatching { cc.getCarService(CarHardwareManager::class.java).carInfo.removeSpeedListener(listener) }
        moving = null
    }

    private companion object { const val MOVING_MPS = 1.4f }
}
