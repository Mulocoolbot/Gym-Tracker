package com.mulocoolbot.gymtracker.blackbox

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.mulocoolbot.gymtracker.data.auth.SessionPreferences
import com.mulocoolbot.gymtracker.data.backup.GymBackupManager
import com.mulocoolbot.gymtracker.data.repository.GymRepository
import com.mulocoolbot.gymtracker.timer.RestTimerController
import com.mulocoolbot.gymtracker.ui.InMemoryTestGymDao
import com.mulocoolbot.gymtracker.ui.auth.AppStartDestination
import com.mulocoolbot.gymtracker.ui.auth.AuthOnboardingViewModel
import com.mulocoolbot.gymtracker.ui.plan.WorkoutPlanViewModel
import com.mulocoolbot.gymtracker.ui.workout.WorkoutUiState
import com.mulocoolbot.gymtracker.ui.workout.WorkoutViewModel
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class MFitBlackBoxTestSuite {

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

    private fun createTestEnvironment(
        fileName: String
    ): Pair<InMemoryTestGymDao, GymRepository> {
        val dataStoreScope = CoroutineScope(testDispatcher + SupervisorJob())
        val dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) {
            File(tempFolder.newFolder(), fileName)
        }
        val dao = InMemoryTestGymDao()
        val repository = GymRepository(
            dao = dao,
            sessionPreferences = SessionPreferences(dataStore)
        )
        return dao to repository
    }

    @Test
    fun tc_auth_01_to_04_onboarding_and_boundary_validation() = runTest(testDispatcher) {
        val (_, repository) = createTestEnvironment("bb_auth.preferences_pb")
        val authVm = AuthOnboardingViewModel(repository)
        advanceUntilIdle()

        val freshSession = repository.authSession.first()
        assertFalse(freshSession.isAuthenticated)
        assertFalse(freshSession.isProfileCompleted)
        assertEquals(AppStartDestination.SIGN_IN, authVm.uiState.value.destination)
        assertNotEquals(AppStartDestination.MAIN_HOME, authVm.uiState.value.destination)

        authVm.onCreateAccount("alex@mfit.app", "12345")
        assertFalse(repository.authSession.first().isAuthenticated)
        assertEquals(AppStartDestination.SIGN_IN, authVm.uiState.value.destination)
        assertNotNull(authVm.uiState.value.authError)

        authVm.onCreateAccount("alex@mfit.app", "123456")
        val registeredSession = repository.authSession.first()
        assertTrue(registeredSession.isAuthenticated)
        assertFalse(registeredSession.isProfileCompleted)
        assertEquals(AppStartDestination.PROFILE_SETUP, authVm.uiState.value.destination)
        assertNull(authVm.uiState.value.authError)

        authVm.onSubmitProfile(
            name = "   ",
            birthDateMillis = 946684800000L,
            weightKgInput = "72.0",
            heightCmInput = "176.0"
        )
        assertEquals(AppStartDestination.PROFILE_SETUP, authVm.uiState.value.destination)

        authVm.onSubmitProfile(
            name = "Alex",
            birthDateMillis = 946684800000L,
            weightKgInput = "0.0",
            heightCmInput = "176.0"
        )
        assertEquals(AppStartDestination.PROFILE_SETUP, authVm.uiState.value.destination)

        authVm.onSubmitProfile(
            name = "Alex",
            birthDateMillis = 946684800000L,
            weightKgInput = "-5.0",
            heightCmInput = "176.0"
        )
        assertEquals(AppStartDestination.PROFILE_SETUP, authVm.uiState.value.destination)

        authVm.onSubmitProfile(
            name = "Alex",
            birthDateMillis = 946684800000L,
            weightKgInput = "72.0",
            heightCmInput = "176.0"
        )
        assertEquals(AppStartDestination.MAIN_HOME, authVm.uiState.value.destination)
        assertEquals("Alex", authVm.uiState.value.userProfile?.name)
        assertEquals(72.0, authVm.uiState.value.userProfile?.weightKg ?: 0.0, 0.001)
        assertEquals(176.0, authVm.uiState.value.userProfile?.heightCm ?: 0.0, 0.001)
    }

    @Test
    fun tc_plan_01_to_03_wizard_state_transitions() = runTest(testDispatcher) {
        val (_, repository) = createTestEnvironment("bb_plan.preferences_pb")
        val planVm = WorkoutPlanViewModel(repository)

        planVm.openWizard()
        assertTrue(planVm.uiState.value.isWizardOpen)
        assertEquals(1, planVm.uiState.value.wizardStep)
        assertTrue(planVm.uiState.value.selectedDays.isEmpty())

        planVm.goToNextStep()
        assertEquals(1, planVm.uiState.value.wizardStep)

        planVm.toggleDaySelection(1)
        planVm.toggleDaySelection(4)
        assertEquals(setOf(1, 4), planVm.uiState.value.selectedDays)

        planVm.goToNextStep()
        assertEquals(2, planVm.uiState.value.wizardStep)

        planVm.updateDraftPlanName(1, "Pull Day")
        planVm.updateDraftPlanName(4, "Upper")
        assertEquals("Pull Day", planVm.uiState.value.draftPlanNames[1])
        assertEquals("Upper", planVm.uiState.value.draftPlanNames[4])

        planVm.goToPreviousStep()
        assertEquals(1, planVm.uiState.value.wizardStep)
        assertEquals("Pull Day", planVm.uiState.value.draftPlanNames[1])
        assertEquals("Upper", planVm.uiState.value.draftPlanNames[4])

        planVm.requestCancelWizard()
        assertTrue(planVm.uiState.value.showDiscardDialog)
        planVm.dismissDiscardDialog()
        assertFalse(planVm.uiState.value.showDiscardDialog)

        planVm.goToNextStep()
        assertEquals(2, planVm.uiState.value.wizardStep)
        planVm.saveWorkoutPlan()

        assertFalse(planVm.uiState.value.isWizardOpen)
        val savedByDay = planVm.uiState.value.weeklyPlan.associateBy { it.dayOfWeek }
        assertEquals(7, savedByDay.size)

        assertFalse(savedByDay.getValue(1).isRestDay)
        assertEquals("Pull Day", savedByDay.getValue(1).planName)

        assertFalse(savedByDay.getValue(4).isRestDay)
        assertEquals("Upper", savedByDay.getValue(4).planName)

        listOf(0, 2, 3, 5, 6).forEach { restDayIndex ->
            assertTrue(savedByDay.getValue(restDayIndex).isRestDay)
            assertEquals("Rest Day", savedByDay.getValue(restDayIndex).planName)
        }
    }

    @Test
    fun tc_wrk_01_to_07_workout_logger_and_rest_timer_boundaries() = runTest(testDispatcher) {
        val (_, repository) = createTestEnvironment("bb_workout.preferences_pb")
        val authVm = AuthOnboardingViewModel(repository)
        authVm.onCreateAccount("athlete@mfit.app", "strongpass")
        authVm.onSubmitProfile(
            name = "Alex",
            birthDateMillis = 946684800000L,
            weightKgInput = "75.0",
            heightCmInput = "180.0"
        )

        val todayEpochDay = 20765L
        val timerController = RestTimerController()
        val workoutVm = WorkoutViewModel(
            repository = repository,
            timerController = timerController,
            todayEpochDay = todayEpochDay
        )

        val triggeredServiceTimers = mutableListOf<Long>()
        val startMillis = System.currentTimeMillis() - 1_800_000L
        workoutVm.startWorkout(currentMillis = startMillis)
        assertTrue(workoutVm.uiState.value.isWorkoutActive)

        workoutVm.updateExerciseInput("  incline   dumbbell press ")
        workoutVm.updateWeightInput("0")
        workoutVm.updateRepsInput("10")
        workoutVm.addSet { triggeredServiceTimers.add(it) }
        assertTrue(workoutVm.uiState.value.activeSessionSets.isEmpty())
        assertTrue(triggeredServiceTimers.isEmpty())

        workoutVm.updateWeightInput("30")
        workoutVm.updateRepsInput("0")
        workoutVm.addSet { triggeredServiceTimers.add(it) }
        assertTrue(workoutVm.uiState.value.activeSessionSets.isEmpty())
        assertTrue(triggeredServiceTimers.isEmpty())

        workoutVm.updateWeightInput("24")
        workoutVm.updateRepsInput("12")
        workoutVm.selectSetType(WorkoutUiState.SET_TYPE_WARMUP)
        workoutVm.addSet { triggeredServiceTimers.add(it) }
        assertEquals(60_000L, triggeredServiceTimers.last())
        assertEquals("01:00", RestTimerController.formatMMSS(timerController.state.value.totalSeconds))
        assertEquals("Warmup", workoutVm.uiState.value.activeSessionSets.last().setLabel)
        assertEquals("Incline Dumbbell Press", workoutVm.uiState.value.activeSessionSets.last().exerciseName)

        workoutVm.updateWeightInput("32")
        workoutVm.updateRepsInput("10")
        workoutVm.selectSetType(WorkoutUiState.SET_TYPE_NORMAL)
        workoutVm.addSet { triggeredServiceTimers.add(it) }
        assertEquals(120_000L, triggeredServiceTimers.last())
        assertEquals("02:00", RestTimerController.formatMMSS(timerController.state.value.totalSeconds))
        assertEquals("Set 1", workoutVm.uiState.value.activeSessionSets.last().setLabel)
        assertEquals(2, workoutVm.uiState.value.nextNormalSetNumber)

        workoutVm.addSet { triggeredServiceTimers.add(it) }
        assertEquals(120_000L, triggeredServiceTimers.last())
        assertEquals("Set 2", workoutVm.uiState.value.activeSessionSets.last().setLabel)
        assertEquals(3, workoutVm.uiState.value.nextNormalSetNumber)

        workoutVm.updateWeightInput("36")
        workoutVm.updateRepsInput("8")
        workoutVm.selectSetType(WorkoutUiState.SET_TYPE_FINAL)
        workoutVm.addSet { triggeredServiceTimers.add(it) }
        assertEquals(180_000L, triggeredServiceTimers.last())
        assertEquals("03:00", RestTimerController.formatMMSS(timerController.state.value.totalSeconds))
        assertEquals("Final Set", workoutVm.uiState.value.activeSessionSets.last().setLabel)

        workoutVm.stopWorkout(planName = "Push Day")
        assertFalse(workoutVm.uiState.value.isWorkoutActive)
        assertEquals(4, workoutVm.uiState.value.todayCompletedSets.size)
        assertEquals("Incline Dumbbell Press", workoutVm.uiState.value.exerciseHistory.first().normalizedName)
        assertTrue(repository.observeTodayCalories(todayEpochDay).first() > 0)
        assertEquals(1, repository.userProfile.first()?.currentStreak)

        workoutVm.startWorkout(currentMillis = System.currentTimeMillis() - 600_000L)
        workoutVm.updateExerciseInput("Bench Press")
        workoutVm.updateWeightInput("80")
        workoutVm.updateRepsInput("8")
        workoutVm.selectSetType(WorkoutUiState.SET_TYPE_NORMAL)
        workoutVm.addSet()
        workoutVm.stopWorkout(planName = "Push Day")

        assertEquals(1, repository.userProfile.first()?.currentStreak)
    }

    @Test
    fun tc_set_02_to_04_multi_auth_decision_table_and_json_backup() = runTest(testDispatcher) {
        val (sourceDao, sourceRepo) = createTestEnvironment("bb_settings_src.preferences_pb")
        val authVm = AuthOnboardingViewModel(sourceRepo)

        authVm.onCreateAccount("alex@mfit.app", "secret123")
        authVm.onSubmitProfile(
            name = "Alex",
            birthDateMillis = 946684800000L,
            weightKgInput = "76.0",
            heightCmInput = "181.0"
        )
        sourceRepo.saveWeeklyWorkoutPlan(mapOf(1 to "Pull Day", 3 to "Push Day"))

        val originalUid = sourceRepo.authSession.first().uid
        assertTrue(originalUid.isNotBlank())
        assertEquals(AppStartDestination.MAIN_HOME, authVm.uiState.value.destination)

        authVm.onToggleGoogleLink("alex.google@gmail.com")
        val dualSession = sourceRepo.authSession.first()
        assertTrue(dualSession.isEmailLinked)
        assertTrue(dualSession.isGoogleLinked)
        assertEquals(originalUid, dualSession.uid)

        authVm.onToggleEmailLink("")
        val googleOnlySession = sourceRepo.authSession.first()
        assertFalse(googleOnlySession.isEmailLinked)
        assertTrue(googleOnlySession.isGoogleLinked)
        assertTrue(googleOnlySession.isAuthenticated)
        assertEquals(originalUid, googleOnlySession.uid)
        assertEquals(AppStartDestination.MAIN_HOME, authVm.uiState.value.destination)

        authVm.onToggleGoogleLink("")
        val unlinkedSession = sourceRepo.authSession.first()
        assertFalse(unlinkedSession.isEmailLinked)
        assertFalse(unlinkedSession.isGoogleLinked)
        assertFalse(unlinkedSession.isAuthenticated)
        assertEquals(AppStartDestination.SIGN_IN, authVm.uiState.value.destination)

        authVm.onSignInWithEmailPassword("alex@mfit.app", "secret123")
        val restoredSession = sourceRepo.authSession.first()
        assertTrue(restoredSession.isAuthenticated)
        assertEquals(originalUid, restoredSession.uid)
        assertEquals(AppStartDestination.MAIN_HOME, authVm.uiState.value.destination)
        assertEquals("Alex", authVm.uiState.value.userProfile?.name)

        val exportedJson = GymBackupManager.exportToJson(sourceDao)
        assertTrue(exportedJson.contains("\"schemaVersion\": 1"))

        val (targetDao, targetRepo) = createTestEnvironment("bb_settings_dst.preferences_pb")
        assertFalse(targetRepo.importBackupJson("{ malformed json"))

        assertTrue(targetRepo.importBackupJson(exportedJson))
        assertEquals(sourceDao.getUserProfile(), targetDao.getUserProfile())
        assertEquals(sourceDao.getWorkoutPlans(), targetDao.getWorkoutPlans())
        assertEquals(sourceDao.getAllSessions(), targetDao.getAllSessions())
        assertEquals(sourceDao.getAllSets(), targetDao.getAllSets())
    }
}

