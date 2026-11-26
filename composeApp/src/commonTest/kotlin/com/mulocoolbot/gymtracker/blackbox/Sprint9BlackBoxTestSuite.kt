package com.mulocoolbot.gymtracker.blackbox

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.mulocoolbot.gymtracker.data.auth.SessionPreferences
import com.mulocoolbot.gymtracker.data.local.TestInMemoryGymDao
import com.mulocoolbot.gymtracker.data.repository.GymRepository
import com.mulocoolbot.gymtracker.platform.LiveIslandActionDispatcher
import com.mulocoolbot.gymtracker.platform.LiveIslandActionHandler
import com.mulocoolbot.gymtracker.platform.createTestPreferencesPath
import com.mulocoolbot.gymtracker.timer.BackgroundTimerCoordinator
import com.mulocoolbot.gymtracker.timer.LocalTimerNotificationScheduler
import com.mulocoolbot.gymtracker.timer.RestTimerController
import com.mulocoolbot.gymtracker.timer.TimerAudioDuckingPort
import com.mulocoolbot.gymtracker.timer.WorkoutLiveIslandState
import com.mulocoolbot.gymtracker.timer.resolveWorkoutLiveIslandState
import com.mulocoolbot.gymtracker.ui.home.HomeViewModel
import com.mulocoolbot.gymtracker.ui.plan.WorkoutPlanViewModel
import com.mulocoolbot.gymtracker.ui.stats.StatsViewModel
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
class Sprint9BlackBoxTestSuite {

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

    private class FakeEnvironment(
        scope: CoroutineScope,
        prefsName: String
    ) {
        var fakeClockMs: Long = 10_000_000L
        var scheduledDelayMs: Long? = null
        var cancelledCount: Int = 0
        var backgroundStopCalls: Int = 0
        var completionAlertsPlayed: Int = 0

        val dao = TestInMemoryGymDao()
        val repository = GymRepository(
            dao = dao,
            sessionPreferences = SessionPreferences(
                PreferenceDataStoreFactory.createWithPath(
                    scope = scope,
                    produceFile = { createTestPreferencesPath(prefsName).toPath() }
                )
            )
        )
        val timerController = RestTimerController()
        val coordinator = BackgroundTimerCoordinator(
            controller = timerController,
            notifications = object : LocalTimerNotificationScheduler {
                override fun scheduleCompletion(delayMillis: Long, label: String) {
                    scheduledDelayMs = delayMillis
                }

                override fun cancel(clearDelivered: Boolean) {
                    cancelledCount++
                }
            },
            audio = object : TimerAudioDuckingPort {
                override fun playDuckedCompletionAlert() {
                    completionAlertsPlayed++
                }

                override fun stopAndUnduckAudio() = Unit
            },
            clockMillis = { fakeClockMs }
        )
        val workoutViewModel = WorkoutViewModel(
            repository = repository,
            timerController = timerController,
            todayEpochDay = 20780L
        )

        fun bindIslandHandler(planName: String = "Push Day") {
            LiveIslandActionDispatcher.bind(
                object : LiveIslandActionHandler {
                    override fun onStopRestTimerFromIsland() {
                        workoutViewModel.onStopRestTimerFromIsland {
                            backgroundStopCalls++
                            coordinator.onStopTimer()
                        }
                    }

                    override fun onStopWorkoutFromIsland() {
                        workoutViewModel.onStopWorkoutFromIsland(planName = planName) {
                            backgroundStopCalls++
                            coordinator.onStopTimer()
                        }
                    }
                }
            )
        }
    }

    @Test
    fun tc_s9_01_minimize_while_rest_timer_active() = runTest(testDispatcher) {
        val env = FakeEnvironment(backgroundScope, "s9_01.preferences_pb")
        env.bindIslandHandler()

        env.workoutViewModel.startWorkout(currentMillis = env.fakeClockMs)
        env.workoutViewModel.onExerciseNameChanged("Bench Press")
        env.workoutViewModel.updateWeightInput("80")
        env.workoutViewModel.updateRepsInput("8")
        env.workoutViewModel.selectSetType(WorkoutUiState.SET_TYPE_NORMAL)
        env.workoutViewModel.addSet { durationMillis, label ->
            env.coordinator.onStartTimer(durationMillis, label)
        }

        env.coordinator.onWillResignActive()
        env.coordinator.onDidEnterBackground()

        assertFalse(env.coordinator.isAppInForeground)
        val islandState = env.workoutViewModel.liveIslandState.value
        assertTrue(islandState is WorkoutLiveIslandState.RestTimerActive)
        assertEquals(120, islandState.totalSeconds)
        assertEquals(env.timerController.state.value.endTimeMillis, islandState.endEpochMs)
        assertTrue(env.scheduledDelayMs != null && env.scheduledDelayMs!! > 0L)

        env.workoutViewModel.stopWorkout()
    }

    @Test
    fun tc_s9_02_minimize_while_workout_active_without_timer() = runTest(testDispatcher) {
        val env = FakeEnvironment(backgroundScope, "s9_02.preferences_pb")
        env.bindIslandHandler()

        env.workoutViewModel.startWorkout(currentMillis = env.fakeClockMs)
        env.coordinator.onWillResignActive()
        env.coordinator.onDidEnterBackground()

        assertTrue(env.workoutViewModel.uiState.value.isWorkoutActive)
        assertFalse(env.timerController.state.value.isRunning)
        assertEquals(
            WorkoutLiveIslandState.WorkoutOngoing("Workout Still Going"),
            env.workoutViewModel.liveIslandState.value
        )

        env.workoutViewModel.stopWorkout()
    }

    @Test
    fun tc_s9_03_stop_button_in_timer_mode_stops_only_timer_and_switches_to_workout_going() = runTest(testDispatcher) {
        val env = FakeEnvironment(backgroundScope, "s9_03.preferences_pb")
        env.bindIslandHandler()

        env.workoutViewModel.startWorkout(currentMillis = env.fakeClockMs)
        env.workoutViewModel.onExerciseNameChanged("Incline Dumbbell Press")
        env.workoutViewModel.updateWeightInput("32.5")
        env.workoutViewModel.updateRepsInput("10")
        env.workoutViewModel.selectSetType(WorkoutUiState.SET_TYPE_NORMAL)
        env.workoutViewModel.addSet { durationMillis, label ->
            env.coordinator.onStartTimer(durationMillis, label)
        }
        env.workoutViewModel.addSet { durationMillis, label ->
            env.coordinator.onStartTimer(durationMillis, label)
        }

        env.coordinator.onDidEnterBackground()
        assertTrue(env.workoutViewModel.liveIslandState.value is WorkoutLiveIslandState.RestTimerActive)
        assertEquals(2, env.workoutViewModel.uiState.value.activeSessionSets.size)

        LiveIslandActionDispatcher.onStopRestTimerFromIsland()

        assertFalse(env.timerController.state.value.isRunning)
        assertFalse(env.timerController.state.value.alarmRinging)
        assertTrue(env.workoutViewModel.uiState.value.isWorkoutActive)
        assertEquals(2, env.workoutViewModel.uiState.value.activeSessionSets.size)
        assertEquals(1, env.backgroundStopCalls)
        assertTrue(env.cancelledCount >= 1)
        assertEquals(
            WorkoutLiveIslandState.WorkoutOngoing("Workout Still Going"),
            env.workoutViewModel.liveIslandState.value
        )

        env.workoutViewModel.stopWorkout()
    }

    @Test
    fun tc_s9_04_stop_button_in_workout_going_mode_saves_workout_and_dismisses_island() = runTest(testDispatcher) {
        val env = FakeEnvironment(backgroundScope, "s9_04.preferences_pb")
        env.bindIslandHandler(planName = "Upper Power")

        env.workoutViewModel.startWorkout(currentMillis = env.fakeClockMs)
        env.workoutViewModel.onExerciseNameChanged("Barbell Squat")
        env.workoutViewModel.updateWeightInput("100")
        env.workoutViewModel.updateRepsInput("5")
        env.workoutViewModel.selectSetType(WorkoutUiState.SET_TYPE_NORMAL)
        env.workoutViewModel.addSet { durationMillis, label ->
            env.coordinator.onStartTimer(durationMillis, label)
        }
        env.workoutViewModel.addSet { durationMillis, label ->
            env.coordinator.onStartTimer(durationMillis, label)
        }

        env.coordinator.onDidEnterBackground()
        LiveIslandActionDispatcher.onStopRestTimerFromIsland()
        assertEquals(
            WorkoutLiveIslandState.WorkoutOngoing("Workout Still Going"),
            env.workoutViewModel.liveIslandState.value
        )

        LiveIslandActionDispatcher.onStopWorkoutFromIsland()

        assertFalse(env.workoutViewModel.uiState.value.isWorkoutActive)
        assertTrue(env.workoutViewModel.uiState.value.activeSessionSets.isEmpty())
        assertFalse(env.timerController.state.value.isRunning)
        assertEquals(WorkoutLiveIslandState.Inactive, env.workoutViewModel.liveIslandState.value)
        assertEquals(2, env.workoutViewModel.uiState.value.todayCompletedSets.size)
        assertEquals("Barbell Squat", env.workoutViewModel.uiState.value.todayCompletedSets.first().exerciseName)

        env.coordinator.onWillEnterForeground()
        assertFalse(env.workoutViewModel.uiState.value.isWorkoutActive)
        assertEquals(WorkoutLiveIslandState.Inactive, env.workoutViewModel.liveIslandState.value)
    }

    @Test
    fun tc_s9_05_timer_natural_expiry_in_background_auto_transitions_to_workout_going() = runTest(testDispatcher) {
        val env = FakeEnvironment(backgroundScope, "s9_05.preferences_pb")
        env.bindIslandHandler(planName = "Leg Day")

        env.workoutViewModel.startWorkout(currentMillis = env.fakeClockMs)
        env.workoutViewModel.onExerciseNameChanged("Romanian Deadlift")
        env.workoutViewModel.updateWeightInput("90")
        env.workoutViewModel.updateRepsInput("8")
        env.workoutViewModel.selectSetType(WorkoutUiState.SET_TYPE_WARMUP)
        env.workoutViewModel.addSet { durationMillis, label ->
            env.coordinator.onStartTimer(durationMillis, label)
        }

        val endEpochMs = env.timerController.state.value.endTimeMillis
        env.coordinator.onDidEnterBackground()
        assertTrue(env.workoutViewModel.liveIslandState.value is WorkoutLiveIslandState.RestTimerActive)

        env.fakeClockMs = endEpochMs
        env.timerController.tick(currentMillis = env.fakeClockMs)

        assertFalse(env.timerController.state.value.isRunning)
        assertTrue(env.timerController.state.value.alarmRinging)
        assertTrue(env.workoutViewModel.uiState.value.isWorkoutActive)
        assertEquals(
            WorkoutLiveIslandState.WorkoutOngoing("Workout Still Going"),
            env.workoutViewModel.liveIslandState.value
        )
        assertEquals(
            WorkoutLiveIslandState.WorkoutOngoing("Workout Still Going"),
            resolveWorkoutLiveIslandState(
                isWorkoutActive = true,
                restTimerState = env.timerController.state.value.copy(isRunning = true),
                nowEpochMs = endEpochMs
            )
        )

        LiveIslandActionDispatcher.onStopWorkoutFromIsland()
        assertFalse(env.workoutViewModel.uiState.value.isWorkoutActive)
        assertEquals(WorkoutLiveIslandState.Inactive, env.workoutViewModel.liveIslandState.value)
        assertEquals(1, env.workoutViewModel.uiState.value.todayCompletedSets.size)
    }

    @Test
    fun tc_s9_06_minimize_on_home_plan_stats_without_active_workout_never_shows_island() = runTest(testDispatcher) {
        val env = FakeEnvironment(backgroundScope, "s9_06.preferences_pb")
        val homeVm = HomeViewModel(env.repository)
        val planVm = WorkoutPlanViewModel(env.repository)
        val statsVm = StatsViewModel(env.repository)

        assertFalse(homeVm.uiState.value.selectedDayPlan.planName.isBlank())
        assertEquals(0, planVm.uiState.value.weeklyPlan.count { !it.isRestDay })
        assertTrue(statsVm.uiState.value.exerciseOptions.isEmpty())
        assertFalse(env.workoutViewModel.uiState.value.isWorkoutActive)

        env.coordinator.onWillResignActive()
        env.coordinator.onDidEnterBackground()

        assertEquals(WorkoutLiveIslandState.Inactive, env.workoutViewModel.liveIslandState.value)
        assertEquals(
            WorkoutLiveIslandState.Inactive,
            resolveWorkoutLiveIslandState(
                isWorkoutActive = false,
                restTimerState = env.timerController.state.value
            )
        )
        assertEquals(null, env.scheduledDelayMs)
    }

    @Test
    fun tc_s9_07_sync_back_to_app_after_stop_timer_from_island() = runTest(testDispatcher) {
        val env = FakeEnvironment(backgroundScope, "s9_07.preferences_pb")
        env.bindIslandHandler()

        env.workoutViewModel.startWorkout(currentMillis = env.fakeClockMs)
        env.workoutViewModel.onExerciseNameChanged("Overhead Press")
        env.workoutViewModel.updateWeightInput("50")
        env.workoutViewModel.updateRepsInput("8")
        env.workoutViewModel.selectSetType(WorkoutUiState.SET_TYPE_NORMAL)
        env.workoutViewModel.addSet { durationMillis, label ->
            env.coordinator.onStartTimer(durationMillis, label)
        }

        env.coordinator.onDidEnterBackground()
        assertTrue(env.workoutViewModel.liveIslandState.value is WorkoutLiveIslandState.RestTimerActive)

        LiveIslandActionDispatcher.onStopRestTimerFromIsland()

        env.fakeClockMs += 5_000L
        env.coordinator.onWillEnterForeground()

        val uiAfterReopen = env.workoutViewModel.uiState.value
        assertTrue(uiAfterReopen.isWorkoutActive)
        assertFalse(uiAfterReopen.restTimerState.isRunning)
        assertFalse(uiAfterReopen.restTimerState.alarmRinging)
        assertEquals(1, uiAfterReopen.activeSessionSets.size)
        assertEquals(
            WorkoutLiveIslandState.WorkoutOngoing("Workout Still Going"),
            env.workoutViewModel.liveIslandState.value
        )

        env.workoutViewModel.addSet { durationMillis, label ->
            env.coordinator.onStartTimer(durationMillis, label)
        }
        assertEquals(2, env.workoutViewModel.uiState.value.activeSessionSets.size)
        assertTrue(env.workoutViewModel.liveIslandState.value is WorkoutLiveIslandState.RestTimerActive)

        env.workoutViewModel.stopWorkout()
    }
}
