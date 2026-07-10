package com.istitutiverona.conteggioore.sicurezza

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

// Blocco app: biometria o PIN/sequenza del dispositivo. Attivo di default.
object Blocco {
    private const val AUTH = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    private fun prefs(c: Context) = c.getSharedPreferences("sicurezza", Context.MODE_PRIVATE)

    fun attivo(c: Context): Boolean = prefs(c).getBoolean("blocco", true)
    fun imposta(c: Context, on: Boolean) = prefs(c).edit().putBoolean("blocco", on).apply()

    /** Il dispositivo ha biometria o PIN configurati? Senza, il blocco non si può applicare. */
    fun disponibile(c: Context): Boolean =
        BiometricManager.from(c).canAuthenticate(AUTH) == BiometricManager.BIOMETRIC_SUCCESS

    /** Mostra il prompt biometria/PIN. onEsito(true) se autenticato. */
    fun chiedi(activity: FragmentActivity, titolo: String, onEsito: (Boolean) -> Unit) {
        val prompt = BiometricPrompt(
            activity, ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) =
                    onEsito(true)
                override fun onAuthenticationError(code: Int, msg: CharSequence) = onEsito(false)
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(titolo)
                .setAllowedAuthenticators(AUTH)
                .build()
        )
    }
}
