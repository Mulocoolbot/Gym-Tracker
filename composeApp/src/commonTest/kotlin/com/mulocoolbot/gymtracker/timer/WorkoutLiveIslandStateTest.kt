package com.mulocoolbot.gymtracker.timer

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.mulocoolbot.gymtracker.data.auth.SessionPreferences
import com.mulocoolbot.gymtracker.data.local.TestInMemoryGymDao
import com.mulocoolbot.gymtracker.data.repository.GymRepository
import com.mulocoolbot.gymtracker.platform.LiveIslandActionDispatcher
import com.mulocoolbot.gymtracker.platform.LiveIslandActionHandler
import com.mulocoolbot.gymtracker.platform.createTestPreferencesPath
import com.mulocoolbot.gymtracker.ui.workout.WorkoutUiState
import com.mulocoolbot.gymtracker.ui.workout.WorkoutViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.Path.Companion.toPath

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutLiveIslandStateTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        LiveIslandActionDispatcher.bind(null)
    }

    @AfterTest
    fun tearDown() {
        LiveIslandActionDispatcher.bind(null)
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        scope: CoroutineScope,
        prefsName: String,
        timerController: RestTimerController = RestTimerController()
    ): WorkoutViewModel {
        val dataStore = PreferenceDataStoreFactory.createWithPath(
            scope = scope,
            produceFile = { createTestPreferencesPath(prefsName).toPath() }
        )
        val repository = GymRepository(
            dao = TestInMemoryGymDao(),
            sessionPreferences = SessionPreferences(dataStore)
        )
        return WorkoutViewModel(
            repository = repository,
            timerController = timerController,
            todayEpochDay = 20760L
        )
    }

    @Test
    fun resolveWorkoutLiveIslandState_coversInactiveOngoingActiveAndExpiredTimer() {
        val idleTimer = RestTimerState()
        assertEquals(
            WorkoutLiveIslandState.Inactive,
            resolveWorkoutLiveIslandState(isWorkoutActive = false, restTimerState = idleTimer)
        )
        assertEquals(
            WorkoutLiveIslandState.WorkoutOngoing("Workout Still Going"),
            resolveWorkoutLiveIslandState(isWorkoutActive = true, restTimerState = idleTimer)
        )

        val runningTimer = RestTimerState(
            isRunning = true,
            totalSeconds = 120,
            remainingSeconds = 95,
            endTimeMillis = 2_000_000L
        )
        assertEquals(
            WorkoutLiveIslandState.Inactive,
            resolveWorkoutLiveIslandState(isWorkoutActive = false, restTimerState = runningTimer)
        )
        assertEquals(
            WorkoutLiveIslandState.RestTimerActive(endEpochMs = 2_000_000L, totalSeconds = 120),
            resolveWorkoutLiveIslandState(
                isWorkoutActive = true,
                restTimerState = runningTimer,
                nowEpochMs = 1_905_000L
            )
        )
        assertEquals(
            WorkoutLiveIslandState.WorkoutOngoing("Workout Still Going"),
            resolveWorkoutLiveIslandState(
                isWorkoutActive = true,
                restTimerState = runningTimer,
                nowEpochMs = 2_000_000L
            )
        )
    }

    @Test
    fun stopRestTimerFromIsland_stopsOnlyTimerAndSwitchesToWorkoutOngoing() = runTest(testDispatcher) {
        val timerController = RestTimerController()
        val viewModel = createViewModel(backgroundScope, "live_island_stop_timer.preferences_pb", timerController)
        var serviceStopCalls = 0

        LiveIslandActionDispatcher.bind(
            object : LiveIslandActionHandler {
                override fun onStopRestTimerFromIsland() {
                    viewModel.onStopRestTimerFromIsland { serviceStopCalls++ }
                }

                override fun onStopWorkoutFromIsland() {
                    viewModel.onStopWorkoutFromIsland(planName = "Push Day") { serviceStopCalls++ }
                }
            }
        )

        assertEquals(WorkoutLiveIslandState.Inactive, viewModel.liveIslandState.value)

        viewModel.startWorkout(currentMillis = 1_000_000L)
        assertEquals(
            WorkoutLiveIslandState.WorkoutOngoing("Workout Still Going"),
            viewModel.liveIslandState.value
        )

        viewModel.onExerciseNameChanged("Bench Press")
        viewModel.updateWeightInput("80")
        viewModel.updateRepsInput("8")
        viewModel.selectSetType(WorkoutUiState.SET_TYPE_NORMAL)
        viewModel.addSet()

        val activeIsland = viewModel.liveIslandState.value
        assertTrue(activeIsland is WorkoutLiveIslandState.RestTimerActive)
        assertEquals(120, activeIsland.totalSeconds)
        assertTrue(timerController.state.value.isRunning)

        viewModel.adjustRestTimer(deltaSeconds = 15)
        val adjustedIsland = viewModel.liveIslandState.value
        assertTrue(adjustedIsland is WorkoutLiveIslandState.RestTimerActive)
        assertEquals(activeIsland.endEpochMs + 15_000L, adjustedIsland.endEpochMs)

        LiveIslandActionDispatcher.onStopRestTimerFromIsland()

        assertEquals(1, serviceStopCalls)
        assertFalse(timerController.state.value.isRunning)
        assertFalse(timerController.state.value.alarmRinging)
        assertTrue(viewModel.uiState.value.isWorkoutActive)
        assertEquals(1, viewModel.uiState.value.activeSessionSets.size)
        assertEquals(
            WorkoutLiveIslandState.WorkoutOngoing("Workout Still Going"),
            viewModel.liveIslandState.value
        )

        viewModel.stopWorkout()
    }

    @Test
    fun timerNaturalExpiry_transitionsBackToWorkoutOngoing_andStopWorkoutSavesAndCloses() = runTest(testDispatcher) {
        val timerController = RestTimerController()
        val viewModel = createViewModel(backgroundScope, "live_island_stop_workout.preferences_pb", timerController)
        var serviceStopCalls = 0

        LiveIslandActionDispatcher.bind(
            object : LiveIslandActionHandler {
                override fun onStopRestTimerFromIsland() {
                    viewModel.onStopRestTimerFromIsland { serviceStopCalls++ }
                }

                override fun onStopWorkoutFromIsland() {
                    viewModel.onStopWorkoutFromIsland(planName = "Pull Day") { serviceStopCalls++ }
                }
            }
        )

        viewModel.startWorkout(currentMillis = 1_000_000L)
        viewModel.onExerciseNameChanged("Barbell Row")
        viewModel.updateWeightInput("75")
        viewModel.updateRepsInput("10")
        viewModel.selectSetType(WorkoutUiState.SET_TYPE_NORMAL)
        viewModel.addSet()

        val timerEnd = timerController.state.value.endTimeMillis
        assertTrue(viewModel.liveIslandState.value is WorkoutLiveIslandState.RestTimerActive)

        timerController.tick(currentMillis = timerEnd)

        assertFalse(timerController.state.value.isRunning)
        assertTrue(timerController.state.value.alarmRinging)
        assertTrue(viewModel.uiState.value.isWorkoutActive)
        assertEquals(
            WorkoutLiveIslandState.WorkoutOngoing("Workout Still Going"),
            viewModel.liveIslandState.value
        )

        LiveIslandActionDispatcher.onStopWorkoutFromIsland()

        assertEquals(1, serviceStopCalls)
        assertFalse(viewModel.uiState.value.isWorkoutActive)
        assertTrue(viewModel.uiState.value.activeSessionSets.isEmpty())
        assertFalse(timerController.state.value.isRunning)
        assertFalse(timerController.state.value.alarmRinging)
        assertEquals(WorkoutLiveIslandState.Inactive, viewModel.liveIslandState.value)
        assertEquals(1, viewModel.uiState.value.todayCompletedSets.size)
        assertEquals("Barbell Row", viewModel.uiState.value.todayCompletedSets.first().exerciseName)

        LiveIslandActionDispatcher.onStopWorkoutFromIsland()
        assertEquals(1, serviceStopCalls)
        assertEquals(WorkoutLiveIslandState.Inactive, viewModel.liveIslandState.value)
    }
}
