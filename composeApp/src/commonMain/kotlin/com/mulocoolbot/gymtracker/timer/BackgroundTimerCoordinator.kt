package com.mulocoolbot.gymtracker.timer

interface LocalTimerNotificationScheduler {
    fun scheduleCompletion(delayMillis: Long, label: String)
    fun cancel(clearDelivered: Boolean)
}

interface TimerAudioDuckingPort {
    fun playDuckedCompletionAlert()
    fun stopAndUnduckAudio()
}

class BackgroundTimerCoordinator(
    private val controller: RestTimerController,
    private val notifications: LocalTimerNotificationScheduler,
    private val audio: TimerAudioDuckingPort,
    private val clockMillis: () -> Long
) {
    var isAppInForeground: Boolean = true
        private set

    private var lastAlertedEndTimeMillis: Long = 0L

    fun onStartTimer(durationMillis: Long, label: String) {
        audio.stopAndUnduckAudio()
        lastAlertedEndTimeMillis = 0L

        if (!controller.state.value.isRunning) {
            controller.startTimer(
                durationMillis = durationMillis,
                label = label,
                currentMillis = clockMillis()
            )
        }
        val activeState = controller.state.value
        val remainingMillis = (activeState.endTimeMillis - clockMillis()).coerceAtLeast(500L)
        notifications.scheduleCompletion(remainingMillis, activeState.label)
    }

    fun onAdjustTimer(deltaSeconds: Int) {
        val state = controller.state.value
        when {
            state.alarmRinging -> {
                notifications.cancel(clearDelivered = false)
            }
            !state.isRunning || state.remainingSeconds <= 0 -> {
                notifications.cancel(clearDelivered = true)
            }
            else -> {
                val remainingMillis = state.endTimeMillis - clockMillis()
                if (remainingMillis > 0L) {
                    notifications.scheduleCompletion(remainingMillis, state.label)
                } else {
                    controller.tick(clockMillis())
                    val updated = controller.state.value
                    if (updated.alarmRinging) {
                        notifications.cancel(clearDelivered = false)
                        triggerCompletionIfNeeded(updated.endTimeMillis)
                    }
                }
            }
        }
    }

    fun onStopTimer() {
        lastAlertedEndTimeMillis = 0L
        audio.stopAndUnduckAudio()
        notifications.cancel(clearDelivered = true)
    }

    fun onWillResignActive() {
        val state = controller.state.value
        if (!state.isRunning) return
        val remainingMillis = state.endTimeMillis - clockMillis()
        if (remainingMillis > 0L) {
            notifications.scheduleCompletion(remainingMillis, state.label)
        }
    }

    fun onDidEnterBackground() {
        isAppInForeground = false
        val state = controller.state.value
        if (!state.isRunning) return
        val remainingMillis = state.endTimeMillis - clockMillis()
        if (remainingMillis > 0L) {
            notifications.scheduleCompletion(remainingMillis, state.label)
        } else {
            controller.tick(clockMillis())
        }
    }

    fun onWillEnterForeground() {
        isAppInForeground = true
        val before = controller.state.value
        if (!before.isRunning) return

        val now = clockMillis()
        val remainingMillis = before.endTimeMillis - now
        controller.tick(now)

        val after = controller.state.value
        if (after.isRunning && remainingMillis > 0L) {
            notifications.scheduleCompletion(remainingMillis, after.label)
        } else if (after.alarmRinging) {
            notifications.cancel(clearDelivered = false)
            if (remainingMillis > -1_000L) {
                triggerCompletionIfNeeded(after.endTimeMillis)
            } else {
                lastAlertedEndTimeMillis = after.endTimeMillis
            }
        }
    }

    fun onForegroundTick(): Boolean {
        if (!isAppInForeground || !controller.state.value.isRunning) return false
        controller.tick(clockMillis())
        val current = controller.state.value
        if (current.alarmRinging) {
            triggerCompletionIfNeeded(current.endTimeMillis)
            return false
        }
        return current.isRunning
    }

    private fun triggerCompletionIfNeeded(endTimeMillis: Long) {
        if (endTimeMillis != 0L && endTimeMillis == lastAlertedEndTimeMillis) return
        lastAlertedEndTimeMillis = endTimeMillis
        notifications.cancel(clearDelivered = false)
        audio.playDuckedCompletionAlert()
    }
}
