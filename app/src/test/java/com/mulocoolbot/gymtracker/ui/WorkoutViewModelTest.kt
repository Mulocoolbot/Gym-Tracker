package com.mulocoolbot.gymtracker.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.mulocoolbot.gymtracker.data.auth.SessionPreferences
import com.mulocoolbot.gymtracker.data.repository.GymRepository
import com.mulocoolbot.gymtracker.timer.RestTimerController
import com.mulocoolbot.gymtracker.ui.workout.WorkoutUiState
import com.mulocoolbot.gymtracker.ui.workout.WorkoutViewModel
import com.mulocoolbot.gymtracker.ui.workout.setLabel
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        scope: CoroutineScope,
        timerController: RestTimerController = RestTimerController()
    ): WorkoutViewModel {
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(tempFolder.newFolder(), "test_workout_vm.preferences_pb")
        }
        val repository = GymRepository(
            dao = InMemoryTestGymDao(),
            sessionPreferences = SessionPreferences(dataStore)
        )
        return WorkoutViewModel(
            repository = repository,
            timerController = timerController,
            todayEpochDay = 20760L
        )
    }

    @Test
    fun startWorkout_setsIsWorkoutActiveTrue() = runTest {
        val viewModel = createViewModel(backgroundScope)

        viewModel.startWorkout(currentMillis = 1_000_000L)

        assertTrue(viewModel.uiState.value.isWorkoutActive)
        viewModel.stopWorkout()
    }

    @Test
    fun addSet_recordsWarmupNormalAndFinalLabelsAndStartsMatchingRestTimers() = runTest {
        val timerController = RestTimerController()
        val viewModel = createViewModel(backgroundScope, timerController)
        val triggeredDurations = mutableListOf<Long>()

        viewModel.startWorkout(currentMillis = 1_000_000L)
        viewModel.updateExerciseInput("bench press")
        viewModel.updateWeightInput("60")
        viewModel.updateRepsInput("10")

        viewModel.selectSetType(WorkoutUiState.SET_TYPE_WARMUP)
        viewModel.addSet(onTriggerServiceTimer = { duration -> triggeredDurations.add(duration) })

        assertEquals(60_000L, triggeredDurations.last())
        assertEquals(60, timerController.state.value.totalSeconds)
        assertEquals("Warmup", viewModel.uiState.value.activeSessionSets.last().setLabel)

        viewModel.updateWeightInput("80")
        viewModel.updateRepsInput("8")
        viewModel.selectSetType(WorkoutUiState.SET_TYPE_NORMAL)
        viewModel.addSet(onTriggerServiceTimer = { duration -> triggeredDurations.add(duration) })

        assertEquals(120_000L, triggeredDurations.last())
        assertEquals(120, timerController.state.value.totalSeconds)
        assertEquals("Set 1", viewModel.uiState.value.activeSessionSets.last().setLabel)
        assertEquals(2, viewModel.uiState.value.nextNormalSetNumber)

        viewModel.addSet(onTriggerServiceTimer = { duration -> triggeredDurations.add(duration) })

        assertEquals(120_000L, triggeredDurations.last())
        assertEquals("Set 2", viewModel.uiState.value.activeSessionSets.last().setLabel)
        assertEquals(3, viewModel.uiState.value.nextNormalSetNumber)

        viewModel.selectSetType(WorkoutUiState.SET_TYPE_FINAL)
        viewModel.addSet(onTriggerServiceTimer = { duration -> triggeredDurations.add(duration) })

        assertEquals(180_000L, triggeredDurations.last())
        assertEquals(180, timerController.state.value.totalSeconds)
        assertEquals("Final Set", viewModel.uiState.value.activeSessionSets.last().setLabel)
        assertEquals(listOf(60_000L, 120_000L, 120_000L, 180_000L), triggeredDurations)

        viewModel.stopWorkout()
    }

    @Test
    fun stopWorkout_persistsSessionAndSetsAndResetsActiveState() = runTest {
        val timerController = RestTimerController()
        val viewModel = createViewModel(backgroundScope, timerController)

        viewModel.startWorkout(currentMillis = 1_000_000L)
        viewModel.updateExerciseInput("incline dumbbell press")
        viewModel.updateWeightInput("30")
        viewModel.updateRepsInput("10")
        viewModel.selectSetType(WorkoutUiState.SET_TYPE_NORMAL)
        viewModel.addSet()

        viewModel.stopWorkout(planName = "Push Day")

        val state = viewModel.uiState.value
        assertFalse(state.isWorkoutActive)
        assertTrue(state.activeSessionSets.isEmpty())
        assertFalse(timerController.state.value.isRunning)
        assertEquals(1, state.todayCompletedSets.size)
        assertEquals("Incline Dumbbell Press", state.todayCompletedSets.first().exerciseName)
        assertEquals("Set 1", state.todayCompletedSets.first().setLabel)
    }

    @Test
    fun tc_wrk3_full_user_interaction_simulation() = runTest {
        val timerController = RestTimerController()
        val viewModel = createViewModel(backgroundScope, timerController)
        val triggeredTimers = mutableListOf<Pair<Long, String>>()

        assertFalse(viewModel.uiState.value.isWorkoutActive)
        assertFalse(viewModel.uiState.value.showFinishDialog)

        viewModel.startWorkout(currentMillis = 1_000_000L)
        assertTrue(viewModel.uiState.value.isWorkoutActive)

        viewModel.onExerciseNameChanged("  lat   pulldown ")
        viewModel.selectMuscleGroup("LATS")
        assertEquals("LATS", viewModel.uiState.value.selectedMuscleGroup)

        assertEquals("60", viewModel.uiState.value.weightInput)
        viewModel.stepWeight(2.5)
        viewModel.stepWeight(2.5)
        assertEquals("65", viewModel.uiState.value.weightInput)

        assertEquals("10", viewModel.uiState.value.repsInput)
        viewModel.stepReps(-1)
        viewModel.stepReps(-1)
        assertEquals("8", viewModel.uiState.value.repsInput)

        viewModel.updateWeightInput("2.0")
        viewModel.stepWeight(-2.5)
        assertEquals("0", viewModel.uiState.value.weightInput)
        viewModel.updateWeightInput("65")

        viewModel.updateRepsInput("1")
        viewModel.stepReps(-1)
        assertEquals("1", viewModel.uiState.value.repsInput)
        viewModel.updateRepsInput("8")

        viewModel.selectSetType(WorkoutUiState.SET_TYPE_WARMUP)
        viewModel.addSet { durationMillis, label ->
            triggeredTimers.add(durationMillis to label)
        }
        assertEquals(1, viewModel.uiState.value.activeSessionSets.size)
        assertEquals("Warmup", viewModel.uiState.value.activeSessionSets.last().setLabel)
        assertEquals("Lat Pulldown", viewModel.uiState.value.activeSessionSets.last().exerciseName)
        assertEquals(60_000L, triggeredTimers.last().first)
        assertEquals("Warmup · Rest Timer", triggeredTimers.last().second)

        viewModel.selectSetType(WorkoutUiState.SET_TYPE_NORMAL)
        viewModel.addSet { durationMillis, label ->
            triggeredTimers.add(durationMillis to label)
        }
        assertEquals("Set 1", viewModel.uiState.value.activeSessionSets.last().setLabel)
        assertEquals(2, viewModel.uiState.value.nextNormalSetNumber)
        assertEquals(120_000L, triggeredTimers.last().first)

        viewModel.addSet { durationMillis, label ->
            triggeredTimers.add(durationMillis to label)
        }
        assertEquals("Set 2", viewModel.uiState.value.activeSessionSets.last().setLabel)
        assertEquals(3, viewModel.uiState.value.nextNormalSetNumber)
        assertEquals(120_000L, triggeredTimers.last().first)

        val firstNormalSetId = viewModel.uiState.value.activeSessionSets.first {
            it.setLabel == "Set 1"
        }.id
        viewModel.deleteSessionSet(firstNormalSetId)
        val remainingNormalSets = viewModel.uiState.value.activeSessionSets.filter {
            it.setType == WorkoutUiState.SET_TYPE_NORMAL
        }
        assertEquals(1, remainingNormalSets.size)
        assertEquals("Set 1", remainingNormalSets.first().setLabel)
        assertEquals(2, viewModel.uiState.value.nextNormalSetNumber)

        viewModel.selectSetType(WorkoutUiState.SET_TYPE_FINAL)
        viewModel.addSet { durationMillis, label ->
            triggeredTimers.add(durationMillis to label)
        }
        assertEquals("Final Set", viewModel.uiState.value.activeSessionSets.last().setLabel)
        assertEquals(180_000L, triggeredTimers.last().first)
        assertEquals("Final Set · Rest Timer", triggeredTimers.last().second)

        timerController.startTimer(
            durationMillis = 120_000L,
            label = "Set 1 · Rest Timer",
            currentMillis = 2_000_000L
        )
        assertEquals(120, timerController.state.value.remainingSeconds)
        timerController.adjustTimer(deltaSeconds = -15, currentMillis = 2_000_000L)
        assertEquals(105, timerController.state.value.remainingSeconds)
        timerController.adjustTimer(deltaSeconds = 15, currentMillis = 2_000_000L)
        assertEquals(120, timerController.state.value.remainingSeconds)

        viewModel.requestFinishWorkout()
        assertTrue(viewModel.uiState.value.showFinishDialog)

        viewModel.dismissFinishDialog()
        assertFalse(viewModel.uiState.value.showFinishDialog)
        assertTrue(viewModel.uiState.value.isWorkoutActive)

        var serviceStopped = false
        viewModel.confirmFinishWorkout {
            serviceStopped = true
        }
        assertTrue(serviceStopped)

        val finalState = viewModel.uiState.value
        assertFalse(finalState.isWorkoutActive)
        assertFalse(finalState.showFinishDialog)
        assertTrue(finalState.activeSessionSets.isEmpty())
        assertEquals(3, finalState.todayCompletedSets.size)
        assertTrue(finalState.todayCompletedSets.all { it.e1rm > 0.0 })
        assertEquals("Lat Pulldown", finalState.todayCompletedSets.first().exerciseName)
    }
}
