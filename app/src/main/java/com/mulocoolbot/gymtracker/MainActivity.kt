package com.mulocoolbot.gymtracker

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.firebase.auth.FirebaseAuth
import com.mulocoolbot.gymtracker.data.auth.EmailOtpSender
import com.mulocoolbot.gymtracker.platform.AndroidPlatformBindings
import com.mulocoolbot.gymtracker.platform.AndroidPlatformContext
import com.mulocoolbot.gymtracker.timer.RestTimerService
import com.mulocoolbot.gymtracker.ui.auth.rememberGoogleAuthLauncher

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerPlatformBindings()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.parseColor("#101217")
        applyFullScreenMode()

        setContent {
            GymTrackerApp()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyFullScreenMode()
        }
    }

    private fun registerPlatformBindings() {
        AndroidPlatformContext.init(applicationContext)
        AndroidPlatformBindings.startRestTimerService = { ctx, durationMillis, label ->
            RestTimerService.startRestTimer(ctx, durationMillis, label)
        }
        AndroidPlatformBindings.adjustRestTimerService = { ctx, deltaSeconds ->
            RestTimerService.adjustRestTimer(ctx, deltaSeconds)
        }
        AndroidPlatformBindings.stopRestTimerService = { ctx ->
            RestTimerService.stopRestTimer(ctx)
        }
        AndroidPlatformBindings.deliverEmailOtp = { ctx, email, otpCode ->
            EmailOtpSender.sendOtpEmail(ctx, email, otpCode)
        }
        AndroidPlatformBindings.remoteAuthSync = { email, password, isRegister ->
            runCatching {
                val auth = FirebaseAuth.getInstance()
                if (isRegister) {
                    auth.createUserWithEmailAndPassword(email, password)
                } else {
                    auth.signInWithEmailAndPassword(email, password)
                }
            }
        }
        AndroidPlatformBindings.googleSignInActionFactory = { onSuccess, onErrorOrCancel ->
            rememberGoogleAuthLauncher(
                onSuccess = onSuccess,
                onErrorOrCancel = onErrorOrCancel
            )
        }
    }

    private fun applyFullScreenMode() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
