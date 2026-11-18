package app.sordina.service

import android.app.Activity
import android.app.AlertDialog
import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import app.sordina.R
import app.sordina.tr

/**
 * Prima di aprire o disarmare qualcosa in casa ([app.sordina.data.HomeRisk]): impronta (o volto), oppure il PIN del
 * telefono se l'impronta non va. Senza blocco schermo non c'è niente da verificare: basta un "Sì" in un riquadro.
 */
object HomeGuard {
    fun ask(activity: Activity, what: String, onOk: () -> Unit) {
        val secure = activity.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
        if (!secure || Build.VERSION.SDK_INT < 29) { confirm(activity, what, onOk); return }
        val b = BiometricPrompt.Builder(activity)
            .setTitle(what)
            .setSubtitle(tr(R.string.home_guard_why))
        if (Build.VERSION.SDK_INT >= 30) b.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        else @Suppress("DEPRECATION") b.setDeviceCredentialAllowed(true)
        runCatching {
            b.build().authenticate(CancellationSignal(), activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = onOk()
                // Annullato o troppi tentativi: non parte niente (Android ha già detto perché)
            })
        }.onFailure { confirm(activity, what, onOk) }
    }

    private fun confirm(activity: Activity, what: String, onOk: () -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle(what)
            .setMessage(tr(R.string.home_guard_why))
            .setPositiveButton(tr(R.string.home_guard_go)) { _, _ -> onOk() }
            .setNegativeButton(tr(R.string.cancel), null)
            .show()
    }
}
