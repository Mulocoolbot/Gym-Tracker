package com.mulocoolbot.gymtracker.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSBundle
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter

private object IosAuthBridge : AuthBridge {
    override val isGoogleSignInSupported: Boolean = false

    override val isCloudAuthConfigured: Boolean
        get() = hasFirebasePlist()

    override fun syncEmailCredentials(email: String, password: String, isRegister: Boolean) = Unit

    override suspend fun deliverOtp(email: String, otpCode: String) {
        requestIosNotificationAuthorization()
        val content = UNMutableNotificationContent().apply {
            setTitle("MFit Verification Code")
            setBody("Your verification code for $email is $otpCode")
            setSound(UNNotificationSound.defaultSound())
        }
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(
            timeInterval = 1.0,
            repeats = false
        )
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = IOS_OTP_NOTIFICATION_ID,
            content = content,
            trigger = trigger
        )
        UNUserNotificationCenter.currentNotificationCenter()
            .addNotificationRequest(request, withCompletionHandler = null)
    }

    private fun hasFirebasePlist(): Boolean {
        return runCatching {
            NSBundle.mainBundle.pathForResource(
                name = "GoogleService-Info",
                ofType = "plist"
            ) != null
        }.getOrDefault(false)
    }
}

actual fun createAuthBridge(): AuthBridge = IosAuthBridge

@Composable
actual fun rememberGoogleSignInAction(
    onSuccess: (email: String, displayName: String?) -> Unit,
    onErrorOrCancel: () -> Unit
): () -> Unit {
    return remember(onErrorOrCancel) { { onErrorOrCancel() } }
}
