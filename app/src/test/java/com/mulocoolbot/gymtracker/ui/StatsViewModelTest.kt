package com.mulocoolbot.gymtracker.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.mulocoolbot.gymtracker.data.auth.SessionPreferences
import com.mulocoolbot.gymtracker.data.local.WorkoutSetEntity
import com.mulocoolbot.gymtracker.data.repository.GymRepository
import com.mulocoolbot.gymtracker.ui.stats.MuscleRegion
import com.mulocoolbot.gymtracker.ui.stats.StatsViewModel
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

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

    private fun createRepository(scope: CoroutineScope): GymRepository {
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(tempFolder.newFolder(), "test_stats_vm.preferences_pb")
        }
        return GymRepository(
            dao = InMemoryTestGymDao(),
            sessionPreferences = SessionPreferences(dataStore)
        )
    }

    @Test
    fun progressionPoints_forBenchPressComputesExpectedE1rmAndPositiveSixteenPointSevenPercentGain() = runTest {
        val repository = createRepository(backgroundScope)

        val firstSet = WorkoutSetEntity(
            sessionId = 0L,
            exerciseName = "Bench Press",
            setType = "NORMAL",
            setOrder = 1,
            weightKg = 60.0,
            reps = 8,
            e1rm = 0.0,
            timestampMillis = 1_000L
        )
        val secondSet = WorkoutSetEntity(
            sessionId = 0L,
            exerciseName = "Bench Press",
            setType = "NORMAL",
            setOrder = 2,
            weightKg = 70.0,
            reps = 8,
            e1rm = 0.0,
            timestampMillis = 2_000L
        )

        repository.recordWorkoutSession(
            planName = "Push Day",
            startMillis = 500L,
            endMillis = 3_500L,
            sets = listOf(firstSet, secondSet),
            todayEpochDay = 20760L
        )

        val viewModel = StatsViewModel(repository)
        val state = viewModel.uiState.value

        assertEquals("Bench Press", state.selectedExercise)
        assertEquals(2, state.progressionPoints.size)
        assertEquals(76.0, state.progressionPoints[0].e1rm, 0.001)
        assertEquals(88.7, state.progressionPoints[1].e1rm, 0.001)
        assertEquals(16.7, state.percentageChange, 0.001)
    }

    @Test
    fun trainedMuscles_mapsLoggedExercisesToExpectedMuscleRegions() = runTest {
        val repository = createRepository(backgroundScope)

        val sets = listOf(
            WorkoutSetEntity(
                sessionId = 0L,
                exerciseName = "Bench Press",
                setType = "NORMAL",
                setOrder = 1,
                weightKg = 80.0,
                reps = 8,
                e1rm = 0.0,
                timestampMillis = 1_000L
            ),
            WorkoutSetEntity(
                sessionId = 0L,
                exerciseName = "Barbell Squat",
                setType = "NORMAL",
                setOrder = 2,
                weightKg = 100.0,
                reps = 5,
                e1rm = 0.0,
                timestampMillis = 2_000L
            ),
            WorkoutSetEntity(
                sessionId = 0L,
                exerciseName = "Barbell Row",
                setType = "NORMAL",
                setOrder = 3,
                weightKg = 70.0,
                reps = 10,
                e1rm = 0.0,
                timestampMillis = 3_000L
            ),
            WorkoutSetEntity(
                sessionId = 0L,
                exerciseName = "Bicep Curl",
                setType = "NORMAL",
                setOrder = 4,
                weightKg = 16.0,
                reps = 12,
                e1rm = 0.0,
                timestampMillis = 4_000L
            )
        )

        repository.recordWorkoutSession(
            planName = "Full Body",
            startMillis = 500L,
            endMillis = 5_000L,
            sets = sets,
            todayEpochDay = 20760L
        )

        val viewModel = StatsViewModel(repository)
        val trained = viewModel.uiState.value.trainedMuscles

        assertTrue(MuscleRegion.CHEST in trained)
        assertTrue(MuscleRegion.TRICEPS in trained)
        assertTrue(MuscleRegion.QUADS in trained)
        assertTrue(MuscleRegion.LATS in trained)
        assertTrue(MuscleRegion.BICEPS in trained)
    }
}

