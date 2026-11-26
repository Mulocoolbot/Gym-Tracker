package com.mulocoolbot.gymtracker.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.mulocoolbot.gymtracker.timer.BackgroundTimerCoordinator
import com.mulocoolbot.gymtracker.timer.LocalTimerNotificationScheduler
import com.mulocoolbot.gymtracker.timer.RestTimerController
import com.mulocoolbot.gymtracker.timer.TimerAudioDuckingPort
import com.mulocoolbot.gymtracker.util.currentEpochMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter

private object IosRestTimerBridge : PlatformRestTimerBridge {
    private val bridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val audioCoordinator = IosTimerAudioCoordinator(bridgeScope)
    private val lifecycleObservers = mutableListOf<Any>()

    private val notificationScheduler = object : LocalTimerNotificationScheduler {
        override fun scheduleCompletion(delayMillis: Long, label: String) {
            val center = UNUserNotificationCenter.currentNotificationCenter()
            center.removePendingNotificationRequestsWithIdentifiers(listOf(IOS_REST_TIMER_NOTIFICATION_ID))

            val content = UNMutableNotificationContent().apply {
                setTitle("Rest Finished")
                setBody("$label · 00:00")
                setSound(UNNotificationSound.defaultSound())
            }
            val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(
                timeInterval = (delayMillis / 1000.0).coerceAtLeast(0.5),
                repeats = false
            )
            val request = UNNotificationRequest.requestWithIdentifier(
                identifier = IOS_REST_TIMER_NOTIFICATION_ID,
                content = content,
                trigger = trigger
            )
            center.addNotificationRequest(request, withCompletionHandler = null)
        }

        override fun cancel(clearDelivered: Boolean) {
            val center = UNUserNotificationCenter.currentNotificationCenter()
            center.removePendingNotificationRequestsWithIdentifiers(listOf(IOS_REST_TIMER_NOTIFICATION_ID))
            if (clearDelivered) {
                center.removeDeliveredNotificationsWithIdentifiers(listOf(IOS_REST_TIMER_NOTIFICATION_ID))
            }
        }
    }

    private val audioPort = object : TimerAudioDuckingPort {
        override fun playDuckedCompletionAlert() {
            UINotificationFeedbackGenerator().apply {
                prepare()
                notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
            }
            audioCoordinator.playCompletionAlert()
        }

        override fun stopAndUnduckAudio() {
            audioCoordinator.stopAndRestoreAudio()
        }
    }

    private val coordinator = BackgroundTimerCoordinator(
        controller = RestTimerController.instance,
        notifications = notificationScheduler,
        audio = audioPort,
        clockMillis = ::currentEpochMillis
    )

    private var lifecycleRegistered: Boolean = false
    private var tickerJob: Job? = null

    override fun startBackgroundTimer(durationMillis: Long, label: String) {
        ensureLifecycleObservers()
        requestIosNotificationAuthorization()
        coordinator.onStartTimer(durationMillis = durationMillis, label = label)
        if (coordinator.isAppInForeground) {
            startForegroundTicker()
        }
    }

    override fun adjustBackgroundTimer(deltaSeconds: Int) {
        ensureLifecycleObservers()
        coordinator.onAdjustTimer(deltaSeconds)
        val state = RestTimerController.instance.state.value
        if (state.alarmRinging) {
            tickerJob?.cancel()
            tickerJob = null
        } else if (state.isRunning && coordinator.isAppInForeground && tickerJob?.isActive != true) {
            startForegroundTicker()
        }
    }

    override fun stopBackgroundTimer() {
        tickerJob?.cancel()
        tickerJob = null
        coordinator.onStopTimer()
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

    override fun playSetCompletedHaptic() {
        UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleLight).apply {
            prepare()
            impactOccurred()
        }
    }

    private fun ensureLifecycleObservers() {
        ensureIosNotificationDelegateInstalled()
        if (lifecycleRegistered) return
        lifecycleRegistered = true

        val center = NSNotificationCenter.defaultCenter
        val mainQueue = NSOperationQueue.mainQueue

        lifecycleObservers += center.addObserverForName(
            name = UIApplicationWillResignActiveNotification,
            `object` = null,
            queue = mainQueue
        ) { _ ->
            coordinator.onWillResignActive()
        }

        lifecycleObservers += center.addObserverForName(
            name = UIApplicationDidEnterBackgroundNotification,
            `object` = null,
            queue = mainQueue
        ) { _ ->
            isIosAppInForeground = false
            tickerJob?.cancel()
            tickerJob = null
            coordinator.onDidEnterBackground()
        }

        listOf(
            UIApplicationWillEnterForegroundNotification,
            UIApplicationDidBecomeActiveNotification
        ).forEach { name ->
            lifecycleObservers += center.addObserverForName(
                name = name,
                `object` = null,
                queue = mainQueue
            ) { _ ->
                isIosAppInForeground = true
                coordinator.onWillEnterForeground()
                if (RestTimerController.instance.state.value.isRunning && tickerJob?.isActive != true) {
                    startForegroundTicker()
                }
            }
        }
    }

    private fun startForegroundTicker() {
        tickerJob?.cancel()
        tickerJob = bridgeScope.launch {
            while (coordinator.isAppInForeground && RestTimerController.instance.state.value.isRunning) {
                delay(200L)
                if (!coordinator.onForegroundTick()) {
                    break
                }
            }
        }
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
