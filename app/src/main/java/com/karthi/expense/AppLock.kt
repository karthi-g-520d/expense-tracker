package com.karthi.expense

import android.content.Context
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Locks the app behind the phone's own screen lock (fingerprint, face, pattern, PIN or password).
 * On by default; does nothing when the phone has no screen lock set up.
 */
class AppLock(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("app_lock", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(value) = prefs.edit().putBoolean("enabled", value).apply()

    /** True when the phone has a fingerprint, face, pattern, PIN or password set. */
    val available: Boolean
        get() = BiometricManager.from(app).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Whether to ask: on a fresh start, or after the app was in the background for a while.
     * Stays locked until unlocked, even if the user cancels and comes straight back.
     */
    fun shouldLock(): Boolean {
        if (!enabled || !available) {
            locked = false
            return false
        }
        if (!locked) {
            val since = backgroundedAt
            locked = if (since == null) !unlockedThisProcess else SystemClock.elapsedRealtime() - since > RELOCK_AFTER_MS
        }
        return locked
    }

    fun onBackground() {
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    /** Creates the unlock prompt; call from onCreate. [onResult] gets true when unlocked. */
    fun createPrompt(activity: FragmentActivity, onResult: (Boolean) -> Unit): BiometricPrompt =
        BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    prompting = false
                    unlockedThisProcess = true
                    locked = false
                    backgroundedAt = null
                    onResult(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    prompting = false
                    // If the screen lock was removed meanwhile there is nothing to check against: let them in.
                    val noLock = errorCode == BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL ||
                        errorCode == BiometricPrompt.ERROR_HW_NOT_PRESENT
                    if (noLock) {
                        unlockedThisProcess = true
                        locked = false
                    }
                    onResult(noLock)
                }
            },
        )

    /** Shows the system unlock sheet. */
    fun show(prompt: BiometricPrompt) {
        // On older phones the PIN screen is its own activity; coming back from it must not ask twice.
        if (prompting) return
        prompting = true
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Expense Tracker")
                .setSubtitle("Use your fingerprint, pattern or PIN")
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build()
        )
    }

    private companion object {
        // Weak biometrics plus device credential is the combination supported on every Android version.
        const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        const val RELOCK_AFTER_MS = 60_000L

        // Per process, so rotating the screen or a quick trip to Google sign-in doesn't lock again.
        var unlockedThisProcess = false
        var locked = false
        var prompting = false
        var backgroundedAt: Long? = null
    }
}
