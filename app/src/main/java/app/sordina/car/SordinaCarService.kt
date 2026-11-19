package app.sordina.car

import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Sordina sullo schermo dell'auto (Android Auto). Un'app tra le altre, non la Home dell'auto: Casa con le routine,
 * quello che aspetta in sordina, l'agenda di oggi e Parla. Gira sul telefono, Android Auto ne mostra i modelli.
 */
class SordinaCarService : CarAppService() {
    // Solo Android Auto (e gli altri host di Google) possono collegarsi; nelle prove anche l'unità desktop
    override fun createHostValidator(): HostValidator =
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        else HostValidator.Builder(applicationContext).addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample).build()

    override fun onCreateSession(sessionInfo: SessionInfo): Session = CarSession()
}

/** Un collegamento con l'auto: la velocità vive quanto lui */
class CarSession : Session() {
    val motion = CarMotion()

    override fun onCreateScreen(intent: Intent): Screen {
        motion.start(carContext)
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) { motion.stop(carContext); CarVoice.release() }
        })
        return CarHome(carContext, motion)
    }
}
