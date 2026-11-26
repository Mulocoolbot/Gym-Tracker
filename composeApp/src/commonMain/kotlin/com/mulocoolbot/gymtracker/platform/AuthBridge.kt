package com.mulocoolbot.gymtracker.platform

import androidx.compose.runtime.Composable

interface AuthBridge {
    val isGoogleSignInSupported: Boolean
    val isCloudAuthConfigured: Boolean
        get() = isGoogleSignInSupported
    fun syncEmailCredentials(email: String, password: String, isRegister: Boolean)
    suspend fun deliverOtp(email: String, otpCode: String)
}

expect fun createAuthBridge(): AuthBridge

@Composable
expect fun rememberGoogleSignInAction(
    onSuccess: (email: String, displayName: String?) -> Unit,
    onErrorOrCancel: () -> Unit = {}
): () -> Unit
