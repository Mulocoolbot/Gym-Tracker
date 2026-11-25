package com.mulocoolbot.gymtracker.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter

private object IosAuthBridge : AuthBridge {
    private const val OTP_NOTIFICATION_ID = "mfit_otp_code"

    override val isGoogleSignInSupported: Boolean = false

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
            identifier = OTP_NOTIFICATION_ID,
            content = content,
            trigger = trigger
        )
        UNUserNotificationCenter.currentNotificationCenter()
            .addNotificationRequest(request, withCompletionHandler = null)
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
