package com.mulocoolbot.gymtracker

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.firebase.auth.FirebaseAuth
import com.mulocoolbot.gymtracker.data.auth.SessionPreferences
import com.mulocoolbot.gymtracker.data.repository.GymRepository
import com.mulocoolbot.gymtracker.platform.AndroidPlatformContext
import com.mulocoolbot.gymtracker.platform.AuthPreferencesProvider
import com.mulocoolbot.gymtracker.platform.GymDatabaseProvider
import com.mulocoolbot.gymtracker.timer.RestTimerController
import com.mulocoolbot.gymtracker.ui.navigation.MainScaffold
import com.mulocoolbot.gymtracker.ui.theme.GymTrackerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidPlatformContext.init(applicationContext)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.parseColor("#101217")
        applyFullScreenMode()

        val database = GymDatabaseProvider.instance
        val sessionPreferences = SessionPreferences(
            dataStore = AuthPreferencesProvider.instance,
            onRemoteAuthSync = { email, password, isRegister ->
                runCatching {
                    val auth = FirebaseAuth.getInstance()
                    if (isRegister) {
                        auth.createUserWithEmailAndPassword(email, password)
                    } else {
                        auth.signInWithEmailAndPassword(email, password)
                    }
                }
            }
        )
        val repository = GymRepository(
            dao = database.gymDao(),
            sessionPreferences = sessionPreferences
        )

        setContent {
            GymTrackerTheme {
                MainScaffold(
                    repository = repository,
                    timerController = RestTimerController.instance
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyFullScreenMode()
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
