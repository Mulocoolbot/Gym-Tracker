package com.mulocoolbot.gymtracker.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.mulocoolbot.gymtracker.timer.RestTimerController
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionDuckOthers
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AudioToolbox.AudioServicesPlaySystemSound
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter

private object IosRestTimerBridge : PlatformRestTimerBridge {
    private const val TIMER_NOTIFICATION_ID = "mfit_rest_timer"

    private val bridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tickerJob: Job? = null

    override fun startBackgroundTimer(durationMillis: Long, label: String) {
        scheduleLocalTimerNotification(
            seconds = (durationMillis / 1000L).toInt(),
            label = label
        )
        startForegroundTicker()
    }

    override fun adjustBackgroundTimer(deltaSeconds: Int) {
        val state = RestTimerController.instance.state.value
        if (!state.isRunning || state.remainingSeconds <= 0) {
            cancelLocalTimerNotification()
        } else {
            scheduleLocalTimerNotification(
                seconds = state.remainingSeconds,
                label = state.label
            )
        }
    }

    override fun stopBackgroundTimer() {
        tickerJob?.cancel()
        tickerJob = null
        cancelLocalTimerNotification()
    }

    override fun playIntroImpact(isMetallicLock: Boolean, intensity: Float) {
        val style = if (isMetallicLock) {
            UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium
        } else {
            UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy
        }
        UIImpactFeedbackGenerator(style = style).apply {
            prepare()
            impactOccurred()
        }
    }

    private fun startForegroundTicker() {
        tickerJob?.cancel()
        tickerJob = bridgeScope.launch {
            val controller = RestTimerController.instance
            while (controller.state.value.isRunning) {
                delay(250L)
                controller.tick()
                if (controller.state.value.alarmRinging) {
                    triggerForegroundCompletionAlert()
                    break
                }
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun triggerForegroundCompletionAlert() {
        UINotificationFeedbackGenerator().apply {
            prepare()
            notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
        }
        val session = AVAudioSession.sharedInstance()
        session.setCategory(
            AVAudioSessionCategoryPlayback,
            withOptions = AVAudioSessionCategoryOptionDuckOthers,
            error = null
        )
        session.setActive(true, error = null)
        AudioServicesPlaySystemSound(1005u)
    }

    private fun scheduleLocalTimerNotification(seconds: Int, label: String) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.removePendingNotificationRequestsWithIdentifiers(listOf(TIMER_NOTIFICATION_ID))

        val content = UNMutableNotificationContent().apply {
            setTitle("Rest Finished")
            setBody("$label · 00:00")
            setSound(UNNotificationSound.defaultSound())
        }
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(
            timeInterval = seconds.coerceAtLeast(1).toDouble(),
            repeats = false
        )
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = TIMER_NOTIFICATION_ID,
            content = content,
            trigger = trigger
        )
        center.addNotificationRequest(request, withCompletionHandler = null)
    }

    private fun cancelLocalTimerNotification() {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.removePendingNotificationRequestsWithIdentifiers(listOf(TIMER_NOTIFICATION_ID))
        center.removeDeliveredNotificationsWithIdentifiers(listOf(TIMER_NOTIFICATION_ID))
    }
}

actual fun createPlatformRestTimerBridge(): PlatformRestTimerBridge = IosRestTimerBridge

@Composable
actual fun PlatformPermissionsRequester() {
    LaunchedEffect(Unit) {
        requestIosNotificationAuthorization()
    }
}

@Composable
actual fun rememberNotificationPermissionRequester(): () -> Unit {
    return remember { { requestIosNotificationAuthorization() } }
}

internal fun requestIosNotificationAuthorization() {
    val options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
    UNUserNotificationCenter.currentNotificationCenter()
        .requestAuthorizationWithOptions(options) { _, _ -> }
}
