package com.mulocoolbot.gymtracker.platform

import androidx.compose.runtime.Composable

interface PlatformRestTimerBridge {
    fun startBackgroundTimer(durationMillis: Long, label: String)
    fun adjustBackgroundTimer(deltaSeconds: Int)
    fun stopBackgroundTimer()
    fun playIntroImpact(isMetallicLock: Boolean, intensity: Float = 1.0f)
}

expect fun createPlatformRestTimerBridge(): PlatformRestTimerBridge

@Composable
expect fun PlatformPermissionsRequester()

@Composable
expect fun rememberNotificationPermissionRequester(): () -> Unit
