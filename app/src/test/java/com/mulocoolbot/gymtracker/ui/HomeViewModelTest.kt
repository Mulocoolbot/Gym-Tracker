package com.mulocoolbot.gymtracker.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.mulocoolbot.gymtracker.data.auth.SessionPreferences
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

    private fun createRepository(scope: CoroutineScope): GymRepository {
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(tempFolder.newFolder(), "test_home_vm.preferences_pb")
        }
        return GymRepository(
            dao = InMemoryTestGymDao(),
            sessionPreferences = SessionPreferences(dataStore)
        )
    }

    @Test
    fun selectDay_updatesSelectedDayOfWeekAndMatchesSelectedDayPlan() = runTest {
        val repository = createRepository(backgroundScope)
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
        val repository = createRepository(backgroundScope)
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
}

