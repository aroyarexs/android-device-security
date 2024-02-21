package de.kraemer.aaron.devicesecurity

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager

object SystemSecurityIntent {

    @Deprecated("See enrollBiometricsIntent")
    val enrollFingerprintIntent = Intent(Settings.ACTION_FINGERPRINT_ENROLL)

    /**
     * https://developer.android.com/training/sign-in/biometric-auth#available
     *
     * @param authenticator We are using device strong and device credential as a combination for the authenticator.
     * This is only allowed for Android SDK versions >= 30.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    fun enrollBiometricsIntent(
        authenticator: Int = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    ) = Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
        putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, authenticator)
    }

    /**
     * @param authenticator Is used on SDKs >= 30. Therefore any combination should be allowed.
     * @see enrollBiometricsIntent
     */
    fun getBestIntentForUsersSdkVersion(
        authenticator: Int = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    ): Intent =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> enrollBiometricsIntent(authenticator)
            else -> enrollFingerprintIntent
        }
}