package com.mulocoolbot.gymtracker.platform

import androidx.compose.runtime.Composable

private object AndroidAuthBridge : AuthBridge {
    override val isGoogleSignInSupported: Boolean = true

    override fun syncEmailCredentials(email: String, password: String, isRegister: Boolean) {
        runCatching {
            AndroidPlatformBindings.remoteAuthSync(email, password, isRegister)
        }
    }

    override suspend fun deliverOtp(email: String, otpCode: String) {
        runCatching {
            val context = AndroidPlatformContext.requireContext()
            AndroidPlatformBindings.deliverEmailOtp(context, email, otpCode)
        }
    }
}

actual fun createAuthBridge(): AuthBridge = AndroidAuthBridge

@Composable
actual fun rememberGoogleSignInAction(
    onSuccess: (email: String, displayName: String?) -> Unit,
    onErrorOrCancel: () -> Unit
): () -> Unit {
    return AndroidPlatformBindings.googleSignInActionFactory(onSuccess, onErrorOrCancel)
}
