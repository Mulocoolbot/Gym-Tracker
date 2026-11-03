package com.mulocoolbot.gymtracker.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.mulocoolbot.gymtracker.data.auth.SessionPreferences
import com.mulocoolbot.gymtracker.data.local.WorkoutSessionEntity
import com.mulocoolbot.gymtracker.data.repository.GymRepository
import com.mulocoolbot.gymtracker.ui.home.HomeViewModel
import java.io.File
import java.time.LocalDate
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
class HomeViewModelTest {

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

    private fun createEnvironment(scope: CoroutineScope): Pair<InMemoryTestGymDao, GymRepository> {
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(tempFolder.newFolder(), "test_home_vm.preferences_pb")
        }
        val dao = InMemoryTestGymDao()
        val repository = GymRepository(
            dao = dao,
            sessionPreferences = SessionPreferences(dataStore)
        )
        return dao to repository
    }

    @Test
    fun selectDay_updatesSelectedDayOfWeekAndMatchesSelectedDayPlan() = runTest {
        val (_, repository) = createEnvironment(backgroundScope)
        repository.saveWeeklyWorkoutPlan(
            mapOf(
                1 to "Pull Day",
                3 to "Push Day"
            )
        )
        val mondayDate = LocalDate.of(2026, 11, 2)
        val viewModel = HomeViewModel(repository = repository, currentDate = mondayDate)

        assertEquals(1, viewModel.uiState.value.selectedDayOfWeek)
        assertEquals("Pull Day", viewModel.uiState.value.selectedDayPlan.planName)
        assertFalse(viewModel.uiState.value.selectedDayPlan.isRestDay)

        viewModel.selectDay(3)
        val wednesdayState = viewModel.uiState.value
        assertEquals(3, wednesdayState.selectedDayOfWeek)
        assertEquals("Push Day", wednesdayState.selectedDayPlan.planName)
        assertFalse(wednesdayState.selectedDayPlan.isRestDay)
    }

    @Test
    fun selectDay_onRestDayExposesIsRestDayTrue() = runTest {
        val (_, repository) = createEnvironment(backgroundScope)
        repository.saveWeeklyWorkoutPlan(
            mapOf(
                1 to "Pull Day"
            )
        )
        val mondayDate = LocalDate.of(2026, 11, 2)
        val viewModel = HomeViewModel(repository = repository, currentDate = mondayDate)

        viewModel.selectDay(0)
        val sundayState = viewModel.uiState.value

        assertEquals(0, sundayState.selectedDayOfWeek)
        assertTrue(sundayState.selectedDayPlan.isRestDay)
        assertEquals("Rest Day", sundayState.selectedDayPlan.planName)
    }

    @Test
    fun tc_ui1_03_calorie_progress_bar_boundaries() = runTest {
        val (dao, repository) = createEnvironment(backgroundScope)
        val mondayDate = LocalDate.of(2026, 11, 2)
        val epochDay = mondayDate.toEpochDay()
        val viewModel = HomeViewModel(repository = repository, currentDate = mondayDate)

        assertEquals(0, viewModel.uiState.value.todayCalories)
        assertEquals(0f, viewModel.uiState.value.calorieProgress, 0.0001f)

        val sessionId = dao.insertSession(
            WorkoutSessionEntity(
                dateEpochDay = epochDay,
                planName = "Pull Day",
                startTimeMillis = 1_000L,
                endTimeMillis = 2_000L,
                caloriesBurned = 300
            )
        )
        assertEquals(300, viewModel.uiState.value.todayCalories)
        assertEquals(0.5f, viewModel.uiState.value.calorieProgress, 0.0001f)

        dao.updateSession(
            WorkoutSessionEntity(
                sessionId = sessionId,
                dateEpochDay = epochDay,
                planName = "Pull Day",
                startTimeMillis = 1_000L,
                endTimeMillis = 2_000L,
                caloriesBurned = 1200
            )
        )
        assertEquals(1200, viewModel.uiState.value.todayCalories)
        assertEquals(1.0f, viewModel.uiState.value.calorieProgress, 0.0001f)
        assertTrue(viewModel.uiState.value.calorieProgress <= 1.0f)
    }

    @Test
    fun tc_ui1_04_hero_workout_card_rest_day_transition() = runTest {
        val (_, repository) = createEnvironment(backgroundScope)
        repository.saveWeeklyWorkoutPlan(
            mapOf(
                1 to "Pull Day",
                4 to "Upper Body"
            )
        )
        val mondayDate = LocalDate.of(2026, 11, 2)
        val viewModel = HomeViewModel(repository = repository, currentDate = mondayDate)

        assertFalse(viewModel.uiState.value.selectedDayPlan.isRestDay)
        assertEquals("Pull Day", viewModel.uiState.value.selectedDayPlan.planName)

        viewModel.selectDay(2)
        assertTrue(viewModel.uiState.value.selectedDayPlan.isRestDay)
        assertEquals("Rest Day", viewModel.uiState.value.selectedDayPlan.planName)

        viewModel.selectDay(4)
        assertFalse(viewModel.uiState.value.selectedDayPlan.isRestDay)
        assertEquals("Upper Body", viewModel.uiState.value.selectedDayPlan.planName)
    }
}
