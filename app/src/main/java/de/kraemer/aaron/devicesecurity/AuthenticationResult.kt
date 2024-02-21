package de.kraemer.aaron.devicesecurity

import androidx.biometric.BiometricPrompt

sealed class AuthenticationResult {

    companion object {
        /**
         * The method has been called from a device running SDK 29 or lower.
         */
        const val ERROR_UNSUPPORTED_SDK_VERSION = 100
    }

    class Succeeded(val result: BiometricPrompt.AuthenticationResult) : AuthenticationResult() {

        override fun toString(): String {
            return "AuthenticationResult.Succeeded{result.authenticationType: ${result.authenticationType}}"
        }
    }

    /**
     * See [BiometricPrompt.AuthenticationCallback.onAuthenticationError] for details.
     * @param errorCode Either a [BiometricPrompt] error code or [ERROR_UNSUPPORTED_SDK_VERSION].
     */
    class Error(val errorCode: Int, val errString: CharSequence) : AuthenticationResult() {
        val didUserCanceled = errorCode == BiometricPrompt.ERROR_USER_CANCELED

        override fun toString(): String {
            return "AuthenticationResult.Error{errorCode: $errorCode, errString: $errString}"
        }
    }
}