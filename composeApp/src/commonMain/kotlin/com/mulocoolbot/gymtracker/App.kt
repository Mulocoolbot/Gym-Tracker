package com.mulocoolbot.gymtracker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.mulocoolbot.gymtracker.data.auth.SessionPreferences
import com.mulocoolbot.gymtracker.data.repository.GymRepository
import com.mulocoolbot.gymtracker.platform.AuthPreferencesProvider
import com.mulocoolbot.gymtracker.platform.GymDatabaseProvider
import com.mulocoolbot.gymtracker.platform.createAuthBridge
import com.mulocoolbot.gymtracker.timer.RestTimerController
import com.mulocoolbot.gymtracker.ui.navigation.MainScaffold
import com.mulocoolbot.gymtracker.ui.theme.GymTrackerTheme

@Composable
fun GymTrackerApp(
    repository: GymRepository? = null,
    timerController: RestTimerController = RestTimerController.instance
) {
    val resolvedRepository = repository ?: remember {
        val authBridge = createAuthBridge()
        val database = GymDatabaseProvider.instance
        val sessionPreferences = SessionPreferences(
            dataStore = AuthPreferencesProvider.instance,
            onRemoteAuthSync = authBridge::syncEmailCredentials
        )
        GymRepository(
            dao = database.gymDao(),
            sessionPreferences = sessionPreferences
        )
    }

    GymTrackerTheme {
        MainScaffold(
            repository = resolvedRepository,
            timerController = timerController
        )
    }
}
