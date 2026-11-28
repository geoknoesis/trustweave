package org.trustweave.referencewallet.lib

import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat

/**
 * Built-in Android framework BiometricPrompt (API 29+) with device-credential fallback.
 * When a device has no screen lock enrolled, invokes [onNeedsManualConfirm] so callers
 * present an explicit confirmation dialog rather than silently bypassing verification.
 */
object BiometricHelper {

    fun isDeviceSecurityAvailable(context: Context): Boolean {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (km?.isDeviceSecure == true) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bm = context.getSystemService(Context.BIOMETRIC_SERVICE) as? BiometricManager
            val res = bm?.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
            return res == BiometricManager.BIOMETRIC_SUCCESS
        }
        return false
    }

    fun authenticate(
        context: Context,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onNeedsManualConfirm: () -> Unit,
        onFailure: (String?) -> Unit,
    ) {
        if (!isDeviceSecurityAvailable(context)) {
            onNeedsManualConfirm()
            return
        }

        val builder = BiometricPrompt.Builder(context)
            .setTitle(title)
            .setSubtitle(subtitle)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }

        val executor = ContextCompat.getMainExecutor(context)
        val cancellationSignal = CancellationSignal()
        builder.build().authenticate(
            cancellationSignal,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    val userCancelled =
                        errorCode == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED ||
                            errorCode == BiometricPrompt.BIOMETRIC_ERROR_CANCELED
                    onFailure(if (userCancelled) null else errString?.toString())
                }

                override fun onAuthenticationFailed() {
                    // System prompt remains open for retry.
                }
            },
        )
    }
}
