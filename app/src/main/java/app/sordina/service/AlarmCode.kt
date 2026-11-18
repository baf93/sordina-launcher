package app.sordina.service

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.text.InputType
import android.util.Base64
import android.widget.EditText
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import app.sordina.R
import app.sordina.data.Prefs
import app.sordina.data.SavedAlarm
import app.sordina.tr
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * I codici degli allarmi di Home Assistant, per disattivarli con l'impronta (qualunque `alarm_control_panel`).
 *
 * Ogni codice è cifrato con una chiave del Keystore di Android che si apre solo con un'impronta appena data (non col PIN
 * del telefono: chi lo conosce non disarma), nel chip sicuro dove c'è (StrongBox, su Samsung Knox Vault). Registrare
 * un'impronta nuova annulla la chiave: i codici vanno rimessi. Non finiscono mai nel backup né sullo schermo; a Home
 * Assistant il codice arriva solo nel momento in cui disattivi. Un allarme che non chiede codice si salva senza: lì
 * l'impronta fa da conferma.
 */
object AlarmCode {
    private const val ALIAS = "sordina_alarm"

    /** Android 11 e un'impronta forte registrata: senza, la funzione non c'è (si usa Home Assistant come sempre). */
    fun available(ctx: Context): Boolean = Build.VERSION.SDK_INT >= 30 &&
        ctx.getSystemService(BiometricManager::class.java)?.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    @RequiresApi(30)
    private fun key(ctx: Context): SecretKey {
        (keyStore().getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        fun spec(strong: Boolean) = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .apply { if (Build.VERSION.SDK_INT >= 30) setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG) }
            .setInvalidatedByBiometricEnrollment(true)
            .apply { if (strong) setIsStrongBoxBacked(true) }
            .build()
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val strongBox = ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        return try {
            gen.init(spec(strongBox)); gen.generateKey()
        } catch (_: StrongBoxUnavailableException) {
            gen.init(spec(false)); gen.generateKey()
        }
    }

    /** Dimentica tutto: la chiave e i codici (staccando Home Assistant, o se le impronte sono cambiate). */
    fun forgetAll(prefs: Prefs) {
        runCatching { keyStore().deleteEntry(ALIAS) }
        prefs.haAlarms = emptyMap()
    }

    fun forget(prefs: Prefs, entity: String) {
        prefs.haAlarms = prefs.haAlarms - entity
        if (prefs.haAlarms.isEmpty()) runCatching { keyStore().deleteEntry(ALIAS) }
    }

    /**
     * [onFail]: l'impronta non si può usare (troppi tentativi, sensore non disponibile, impronte tolte) o il riquadro
     * non si apre. Annullare (indietro, tocco fuori) non è un errore: lì non succede niente.
     */
    @RequiresApi(30)
    private fun prompt(activity: Activity, title: String, subtitle: String, negative: String, onNegative: () -> Unit, cipher: Cipher, onFail: () -> Unit, onOk: (Cipher) -> Unit) {
        runCatching {
            BiometricPrompt.Builder(activity)
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButton(negative, activity.mainExecutor) { _, _ -> onNegative() }
                .build()
                .authenticate(BiometricPrompt.CryptoObject(cipher), CancellationSignal(), activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        result.cryptoObject?.cipher?.let(onOk) ?: onFail()
                    }
                    override fun onAuthenticationError(code: Int, msg: CharSequence?) {
                        if (code != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED && code != BiometricPrompt.BIOMETRIC_ERROR_CANCELED) onFail()
                    }
                })
        }.onFailure { onFail() }
    }

    /** Salva [entity] col suo codice ([code] vuoto: l'allarme non lo chiede). Chiede l'impronta per cifrare. */
    fun save(activity: Activity, prefs: Prefs, entity: String, name: String, code: String, onDone: (Boolean) -> Unit) {
        if (code.isEmpty()) { prefs.haAlarms = prefs.haAlarms + (entity to SavedAlarm(name, "")); onDone(true); return }
        if (Build.VERSION.SDK_INT < 30) { onDone(false); return }
        val c = runCatching { Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(activity)) } }
            .recoverCatching { e -> if (e is KeyPermanentlyInvalidatedException) { forgetAll(prefs); Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(activity)) } } else throw e }
            .getOrElse { onDone(false); return }
        prompt(activity, tr(R.string.alarm_save_title, name), tr(R.string.alarm_save_sub), tr(R.string.cancel), { onDone(false) }, c, { onDone(false) }) { ok ->
            val stored = runCatching {
                val data = ok.doFinal(code.toByteArray())
                Base64.encodeToString(ok.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(data, Base64.NO_WRAP)
            }.getOrNull()
            if (stored != null) prefs.haAlarms = prefs.haAlarms + (entity to SavedAlarm(name, stored))
            onDone(stored != null)
        }
    }

    /**
     * Per disattivare [entity]: impronta, poi [onCode] col codice ("" se l'allarme non lo chiede).
     * Se l'impronta non va si può scrivere il codice a mano (non si salva). [onLost]: impronte cambiate, codici dimenticati.
     */
    fun use(activity: Activity, prefs: Prefs, entity: String, onCode: (String) -> Unit, onLost: () -> Unit, title: String? = null) {
        val saved = prefs.haAlarms[entity] ?: return
        val title = title ?: tr(R.string.alarm_off_x, saved.name)
        if (saved.enc.isEmpty()) { HomeGuard.ask(activity, title) { onCode("") }; return }
        if (Build.VERSION.SDK_INT < 30) { typeCode(activity, title, onCode = onCode); return }
        // Qualunque cosa vada storta qui, il codice si può sempre scrivere a mano: mai un tocco che non fa niente
        val byHand = { typeCode(activity, title, onCode = onCode) }
        val (iv, data) = runCatching { saved.enc.split(':').let { Base64.decode(it[0], Base64.NO_WRAP) to Base64.decode(it[1], Base64.NO_WRAP) } }.getOrElse { byHand(); return }
        val c = try {
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(activity), GCMParameterSpec(128, iv)) }
        } catch (_: KeyPermanentlyInvalidatedException) {
            forgetAll(prefs); onLost(); return
        } catch (_: Exception) {
            byHand(); return
        }
        prompt(activity, title, tr(R.string.alarm_use_sub), tr(R.string.alarm_type_code), byHand, c, byHand) { ok ->
            runCatching { String(ok.doFinal(data)) }.onSuccess(onCode).onFailure { byHand() }
        }
    }

    /** Il nome di una modalità di inserimento (away, home, night…), nella lingua dell'app */
    fun modeLabel(mode: String): String = tr(when (mode) {
        "home" -> R.string.alarm_mode_home
        "night" -> R.string.alarm_mode_night
        "vacation" -> R.string.alarm_mode_vacation
        "custom_bypass" -> R.string.alarm_mode_custom
        else -> R.string.alarm_mode_away
    })

    /** Un riquadro per scrivere un codice (cifre nascoste). Vale una volta, non si salva. */
    fun typeCode(activity: Activity, title: String, numeric: Boolean = true, onCode: (String) -> Unit) {
        val field = EditText(activity).apply {
            inputType = if (numeric) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = tr(R.string.alarm_code)
        }
        val pad = (20 * activity.resources.displayMetrics.density).toInt()
        val box = FrameLayout(activity).apply { setPadding(pad, pad / 2, pad, 0); addView(field) }
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(box)
            .setPositiveButton(tr(R.string.ok)) { _, _ -> field.text.toString().trim().takeIf { it.isNotEmpty() }?.let(onCode) }
            .setNegativeButton(tr(R.string.cancel), null)
            .show()
    }
}
