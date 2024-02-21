package de.kraemer.aaron.devicesecurity

import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.DeprecatedSinceApi
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

internal sealed class DeviceSecurity {

    sealed interface Authenticatable {

        suspend fun authenticate(activity: FragmentActivity): AuthenticationResult
    }

    companion object {

        /**
         * ⚠️ Unsupported on SDK < 30 -> https://developer.android.com/reference/androidx/biometric/BiometricManager#canAuthenticate(int)
         */
        private const val deviceCredentialAuthenticator = BiometricManager.Authenticators.DEVICE_CREDENTIAL

        /**
         * Be aware of the following error while changing the biometric authenticators.
         * A combination of BIOMETRIC_STRONG and DEVICE_CREDENTIAL isn't supported on SDK 28 - 29 and will result in an error.
         * See for details https://developer.android.com/reference/androidx/biometric/BiometricManager#canAuthenticate(int)
         */
        private const val biometricAuthenticator =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK

        private const val deviceCredentialOrBiometricAuthenticator = biometricAuthenticator or deviceCredentialAuthenticator

        /**
         * Takes into account the running device's SDK version and whether or not it can access newer API levels.
         * @param context Application or activity context
         * @see DeviceSecurity.DeviceCredential.Legacy
         * @see DeviceSecurity.DeviceCredential.Modern
         */
        fun getStrongestEnrolledAuthenticator(context: Context): DeviceSecurity {
            val biometricAuthState = BiometricManager.from(context).canAuthenticate(
                biometricAuthenticator
            )
            val deviceSecurity = if (biometricAuthState == BiometricManager.BIOMETRIC_SUCCESS) {
                Biometry
            } else {
                val isDeviceSecure = NonBiometricDeviceSecurity(context).isDeviceSecuredByPinPatternOrPassword()
                if (isDeviceSecure) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        DeviceCredential.Modern
                    } else {
                        DeviceCredential.Legacy
                    }
                } else {
                    val canEnrollBiometry = biometricAuthState == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
                    NoneEnrolled(canEnrollBiometry)
                }
            }
            return deviceSecurity
        }

        /**
         * @return `false` if it can't be enrolled or is already enrolled, otherwise `true`.
         */
        fun canEnrollBiometry(context: Context): Boolean {
            return BiometricManager.from(context)
                .canAuthenticate(biometricAuthenticator) == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
        }
    }

    protected fun FragmentActivity.createBiometricPrompt(continuation: Continuation<AuthenticationResult>): BiometricPrompt {
        return BiometricPrompt(
            this,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    continuation.resume(AuthenticationResult.Error(errorCode, errString))
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    continuation.resume(AuthenticationResult.Succeeded(result))
                }

                override fun onAuthenticationFailed() {}
            }
        )
    }

    // ################
    // Sealed Classes
    // ################

    sealed class DeviceCredential : DeviceSecurity(), Authenticatable {

        companion object {

            /**
             * @return Null if biometry can't be enrolled on this device.
             */
            fun intentToStartBiometryEnrollment(context: Context): Intent? {
                return if (canEnrollBiometry(context)) {
                    SystemSecurityIntent.getBestIntentForUsersSdkVersion(authenticator = biometricAuthenticator)
                } else {
                    null
                }
            }
        }

        /**
         * The [Legacy] type is usually returned on devices with SDK <= 29 (Android 10).
         */
        object Legacy : DeviceCredential() {

            @DeprecatedSinceApi(
                Build.VERSION_CODES.R,
                "Use SecondFactorDeviceSecurity.DeviceCredential.Modern.authenticate()"
            )
            override suspend fun authenticate(
                activity: FragmentActivity
            ): AuthenticationResult = suspendCoroutine { continuation ->
                @Suppress("DEPRECATION") // We are aware of this. If possible we use the "Modern" approach.
                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("<your_title>")
                    .setDescription("<your_description>")
                    .setDeviceCredentialAllowed(true)
                    .build()
                activity.createBiometricPrompt(continuation).authenticate(promptInfo)
            }

            override fun toString(): String {
                return "Legacy"
            }
        }

        /**
         * The modern approach uses the [BiometricPrompt] with authenticator combinations
         * which are only available on devices running SDK >= 30.
         * Do not access this object directly.
         * Always use [getStrongestEnrolledAuthenticator] to receive an instance of this class.
         */
        object Modern : DeviceCredential() {

            /**
             * Need to be run on the main thread e.g. [kotlinx.coroutines.Dispatchers.Main].
             */
            @TargetApi(Build.VERSION_CODES.R) // API 30
            override suspend fun authenticate(
                activity: FragmentActivity
            ): AuthenticationResult = suspendCoroutine { continuation ->
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    continuation.resume(
                        AuthenticationResult.Error(
                            AuthenticationResult.ERROR_UNSUPPORTED_SDK_VERSION,
                            "Accessing this method from a device running SDK 29 or lower."
                        )
                    )
                    return@suspendCoroutine
                }

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("<your_title>")
                    .setDescription("<your_description>")
                    .setAllowedAuthenticators(deviceCredentialAuthenticator)
                    // using device credential alone is only supported on SDK >= 30, therefore this method is annotated with RequiresApi
                    .build()

                activity.createBiometricPrompt(continuation).authenticate(promptInfo)
            }

            override fun toString(): String {
                return "Modern"
            }
        }
    }

    object Biometry : DeviceSecurity(), Authenticatable {

        /**
         * Need to be run on the main thread e.g. [kotlinx.coroutines.Dispatchers.Main].
         */
        override suspend fun authenticate(activity: FragmentActivity): AuthenticationResult = suspendCoroutine { continuation ->
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("<your_title>")
                .setDescription("<your_description>")
                .setAllowedAuthenticators(deviceCredentialOrBiometricAuthenticator)
                // Do not call setNegativeButtonText().
                // DEVICE_CREDENTIAL is part of the authenticators and will be automatically used as negative button.
                .build()

            activity.createBiometricPrompt(continuation).authenticate(promptInfo)
        }

        override fun toString(): String {
            return "Biometry"
        }
    }

    /**
     * Neither biometry nor device credentials are enrolled on this device.
     */
    class NoneEnrolled internal constructor(private val canEnrollBiometry: Boolean) : DeviceSecurity() {

        fun intentToStartEnrollment(): Intent {
            val authenticator = if (canEnrollBiometry) {
                deviceCredentialOrBiometricAuthenticator
            } else {
                deviceCredentialAuthenticator
            }
            return SystemSecurityIntent.getBestIntentForUsersSdkVersion(authenticator)
        }

        override fun toString(): String {
            return "NoneEnrolled{canEnrollBiometry: $canEnrollBiometry}"
        }
    }
}