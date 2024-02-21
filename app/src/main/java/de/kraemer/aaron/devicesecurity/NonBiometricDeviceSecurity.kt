package de.kraemer.aaron.devicesecurity

import android.app.KeyguardManager
import android.content.Context
import androidx.core.content.getSystemService

interface NonBiometricDeviceSecurityInterface {
    fun isDeviceSecuredByPinPatternOrPassword(): Boolean
}

class NonBiometricDeviceSecurity(private val context: Context) : NonBiometricDeviceSecurityInterface {
    override fun isDeviceSecuredByPinPatternOrPassword(): Boolean =
        context.getSystemService<KeyguardManager>()?.isDeviceSecure ?: false
}