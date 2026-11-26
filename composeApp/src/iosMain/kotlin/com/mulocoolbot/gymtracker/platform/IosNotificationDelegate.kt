package com.mulocoolbot.gymtracker.platform

import com.mulocoolbot.gymtracker.timer.RestTimerController
import com.mulocoolbot.gymtracker.util.currentEpochMillis
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionList
import platform.UserNotifications.UNNotificationPresentationOptionNone
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject

internal const val IOS_REST_TIMER_NOTIFICATION_ID = "mfit_rest_timer"
internal const val IOS_OTP_NOTIFICATION_ID = "mfit_otp_code"

internal var isIosAppInForeground: Boolean = true
private var notificationCenterDelegate: IosNotificationCenterDelegate? = null

private class IosNotificationCenterDelegate : NSObject(), UNUserNotificationCenterDelegateProtocol {
    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (UNNotificationPresentationOptions) -> Unit
    ) {
        val identifier = willPresentNotification.request.identifier
        if (identifier == IOS_REST_TIMER_NOTIFICATION_ID && isIosAppInForeground) {
            // Suppress duplicate banner in foreground because the in-app ducked alert handles completion.
            withCompletionHandler(UNNotificationPresentationOptionNone)
        } else {
            val options = UNNotificationPresentationOptionBanner or
                UNNotificationPresentationOptionList or
                UNNotificationPresentationOptionSound
            withCompletionHandler(options)
        }
    }

    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        didReceiveNotificationResponse: UNNotificationResponse,
        withCompletionHandler: () -> Unit
    ) {
        if (didReceiveNotificationResponse.notification.request.identifier == IOS_REST_TIMER_NOTIFICATION_ID) {
            RestTimerController.instance.tick(currentEpochMillis())
        }
        withCompletionHandler()
    }
}

internal fun ensureIosNotificationDelegateInstalled() {
    if (notificationCenterDelegate != null) return
    val delegate = IosNotificationCenterDelegate()
    notificationCenterDelegate = delegate
    UNUserNotificationCenter.currentNotificationCenter().delegate = delegate
}

internal fun requestIosNotificationAuthorization() {
    ensureIosNotificationDelegateInstalled()
    val options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
    UNUserNotificationCenter.currentNotificationCenter()
        .requestAuthorizationWithOptions(options) { _, _ -> }
}
