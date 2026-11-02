package com.mulocoolbot.gymtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.mulocoolbot.gymtracker.data.auth.SessionPreferences
import com.mulocoolbot.gymtracker.data.local.GymDatabase
import com.mulocoolbot.gymtracker.data.repository.GymRepository
import com.mulocoolbot.gymtracker.timer.RestTimerController
import com.mulocoolbot.gymtracker.ui.navigation.MainScaffold
import com.mulocoolbot.gymtracker.ui.theme.GymTrackerTheme
import java.time.LocalDate
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = GymDatabase.getInstance(applicationContext)
        val sessionPreferences = SessionPreferences(applicationContext)
        val repository = GymRepository(
            dao = database.gymDao(),
            sessionPreferences = sessionPreferences
        )

        lifecycleScope.launch {
            repository.evaluateStreakOnLaunch(LocalDate.now().toEpochDay())
        }

        setContent {
            GymTrackerTheme {
                MainScaffold(
                    repository = repository,
                    timerController = RestTimerController.instance
                )
            }
        }
    }
}
