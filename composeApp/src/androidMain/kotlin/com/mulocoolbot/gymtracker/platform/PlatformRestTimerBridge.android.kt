package com.mulocoolbot.gymtracker.platform

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.mulocoolbot.gymtracker.ui.components.SystemPermissionsRequester
import com.mulocoolbot.gymtracker.ui.intro.OpeningSoundEffects

private object AndroidRestTimerBridge : PlatformRestTimerBridge {
    override fun startBackgroundTimer(durationMillis: Long, label: String) {
        runCatching {
            val context = AndroidPlatformContext.requireContext()
            AndroidPlatformBindings.startRestTimerService(context, durationMillis, label)
        }
    }

    override fun adjustBackgroundTimer(deltaSeconds: Int) {
        runCatching {
            val context = AndroidPlatformContext.requireContext()
            AndroidPlatformBindings.adjustRestTimerService(context, deltaSeconds)
        }
    }

    override fun stopBackgroundTimer() {
        runCatching {
            val context = AndroidPlatformContext.requireContext()
            AndroidPlatformBindings.stopRestTimerService(context)
        }
    }

    override fun playIntroImpact(isMetallicLock: Boolean, intensity: Float) {
        runCatching {
            val context = AndroidPlatformContext.requireContext()
            if (isMetallicLock) {
                OpeningSoundEffects.playMetallicLock(context)
            } else {
                OpeningSoundEffects.playHeavyBoom(context, intensity)
            }
        }
    }
}

actual fun createPlatformRestTimerBridge(): PlatformRestTimerBridge = AndroidRestTimerBridge

@Composable
actual fun PlatformPermissionsRequester() {
    SystemPermissionsRequester()
}

@Composable
actual fun rememberNotificationPermissionRequester(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    return remember(context, launcher) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val status = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                )
                if (status != PackageManager.PERMISSION_GRANTED) {
                    runCatching {
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
        }
    }
}
