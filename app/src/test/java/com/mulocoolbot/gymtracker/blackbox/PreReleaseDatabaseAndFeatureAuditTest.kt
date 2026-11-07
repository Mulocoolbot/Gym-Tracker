package com.mulocoolbot.gymtracker.blackbox

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.mulocoolbot.gymtracker.data.auth.AuthEntryMode
import com.mulocoolbot.gymtracker.data.auth.SessionPreferences
import com.mulocoolbot.gymtracker.data.backup.GymBackupManager
import com.mulocoolbot.gymtracker.data.local.WorkoutSetEntity
import com.mulocoolbot.gymtracker.data.repository.GymRepository
import com.mulocoolbot.gymtracker.ui.InMemoryTestGymDao
import com.mulocoolbot.gymtracker.ui.auth.AppStartDestination
import com.mulocoolbot.gymtracker.ui.auth.AuthOnboardingViewModel
import com.mulocoolbot.gymtracker.ui.auth.EmailAuthStep
import com.mulocoolbot.gymtracker.ui.plan.WorkoutPlanSubPage
import com.mulocoolbot.gymtracker.ui.plan.WorkoutPlanViewModel
import com.mulocoolbot.gymtracker.ui.stats.StatsViewModel
import com.mulocoolbot.gymtracker.util.StreakEvaluator
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class PreReleaseDatabaseAndFeatureAuditTest {

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

    private fun createEnvironment(
        fileName: String
    ): Pair<InMemoryTestGymDao, GymRepository> {
        val scope = CoroutineScope(testDispatcher + SupervisorJob())
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) {
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
    fun audit_01_welcome_to_sign_in_and_log_in_restoration() = runTest(testDispatcher) {
        val (_, repository) = createEnvironment("audit_01.preferences_pb")
        val authVm = AuthOnboardingViewModel(repository)

        assertFalse(authVm.uiState.value.hasPassedWelcome)
        assertEquals(AppStartDestination.WELCOME, authVm.uiState.value.destination)

        authVm.onBeginFromWelcome()
        assertTrue(authVm.uiState.value.hasPassedWelcome)
        assertEquals(AuthEntryMode.SIGN_IN, authVm.uiState.value.authEntryMode)
        assertEquals(AppStartDestination.SIGN_IN, authVm.uiState.value.destination)

        authVm.onBackToWelcome()
        assertFalse(authVm.uiState.value.hasPassedWelcome)
        assertEquals(AppStartDestination.WELCOME, authVm.uiState.value.destination)

        authVm.onBeginFromWelcome()
        assertEquals(AppStartDestination.SIGN_IN, authVm.uiState.value.destination)

        authVm.requestEmailOtp(
            context = null,
            emailInput = "alex@mfit.app",
            currentMillis = 1_000_000L
        )
        assertEquals(EmailAuthStep.OTP_VERIFICATION, authVm.uiState.value.authStep)
        val initialOtp = authVm.lastGeneratedOtpCode
        assertNotNull(initialOtp)

        authVm.verifyEmailOtp(otpInput = initialOtp!!, currentMillis = 1_005_000L)
        assertEquals(AppStartDestination.PROFILE_SETUP, authVm.uiState.value.destination)
        assertTrue(repository.isEmailRegistered("alex@mfit.app"))

        authVm.onSubmitProfile(
            name = "Alex",
            birthDateMillis = 946684800000L,
            weightKgInput = "74.5",
            heightCmInput = "179.0"
        )
        assertEquals(AppStartDestination.MAIN_HOME, authVm.uiState.value.destination)
        val originalUid = repository.authSession.first().uid
        assertTrue(originalUid.isNotBlank())

        authVm.onToggleEmailLink("")
        assertFalse(repository.authSession.first().isAuthenticated)
        assertEquals(AppStartDestination.SIGN_IN, authVm.uiState.value.destination)

        authVm.setAuthEntryMode(AuthEntryMode.LOG_IN)
        assertEquals(AuthEntryMode.LOG_IN, authVm.uiState.value.authEntryMode)

        authVm.requestEmailOtp(
            context = null,
            emailInput = "unregistered@mfit.app",
            currentMillis = 2_000_000L
        )
        assertEquals(
            "Account not found. Switch to Sign In to create an account.",
            authVm.uiState.value.otpErrorMessage
        )

        authVm.requestEmailOtp(
            context = null,
            emailInput = "alex@mfit.app",
            currentMillis = 2_100_000L
        )
        assertNull(authVm.uiState.value.otpErrorMessage)
        assertEquals(EmailAuthStep.OTP_VERIFICATION, authVm.uiState.value.authStep)
        val loginOtp = authVm.lastGeneratedOtpCode
        assertNotNull(loginOtp)

        authVm.verifyEmailOtp(otpInput = loginOtp!!, currentMillis = 2_105_000L)
        val restoredSession = repository.authSession.first()
        assertTrue(restoredSession.isAuthenticated)
        assertTrue(restoredSession.isProfileCompleted)
        assertEquals(originalUid, restoredSession.uid)
        assertEquals(AppStartDestination.MAIN_HOME, authVm.uiState.value.destination)
        assertEquals("Alex", authVm.uiState.value.userProfile?.name)
    }

    @Test
    fun audit_02_workout_plan_empty_to_saved_and_schedule_streak_evaluation() = runTest(testDispatcher) {
        val (dao, repository) = createEnvironment("audit_02.preferences_pb")
        val planVm = WorkoutPlanViewModel(repository)

        assertFalse(planVm.uiState.value.hasSavedPlan)
        assertEquals(WorkoutPlanSubPage.EMPTY, planVm.uiState.value.subPage)

        planVm.startCreateOrEditPlan()
        assertEquals(WorkoutPlanSubPage.SELECT_DAYS, planVm.uiState.value.subPage)

        planVm.toggleDaySelection(1)
        planVm.toggleDaySelection(3)
        planVm.goToInputPlanPage()
        assertEquals(WorkoutPlanSubPage.INPUT_PLAN, planVm.uiState.value.subPage)

        planVm.updateDraftPlanName(1, "Pull Day")
        planVm.updateDraftPlanName(3, "Push Day")
        planVm.saveWorkoutPlan()

        assertTrue(planVm.uiState.value.hasSavedPlan)
        assertEquals(WorkoutPlanSubPage.SAVED, planVm.uiState.value.subPage)

        val sundayDate = LocalDate.of(2026, 11, 1)
        val sundayEpochDay = sundayDate.toEpochDay()
        val mondayEpochDay = sundayEpochDay + 1L
        val tuesdayEpochDay = sundayEpochDay + 2L
        val wednesdayEpochDay = sundayEpochDay + 3L
        val thursdayEpochDay = sundayEpochDay + 4L

        repository.completeOnboardingProfile(
            name = "Alex",
            birthDateMillis = 946684800000L,
            weightKg = 75.0,
            heightCm = 180.0
        )

        val baseProfile = dao.getUserProfile()!!.copy(
            currentStreak = 4,
            consecutiveMissed = 0,
            lastWorkoutEpochDay = sundayEpochDay
        )
        val savedPlans = dao.getWorkoutPlans()

        val afterCompletedMondayAndRestTuesday = StreakEvaluator.evaluateOnAppLaunch(
            profile = baseProfile.copy(lastWorkoutEpochDay = mondayEpochDay),
            weeklyPlan = savedPlans,
            todayEpochDay = wednesdayEpochDay
        )
        assertEquals(4, afterCompletedMondayAndRestTuesday.currentStreak)
        assertEquals(0, afterCompletedMondayAndRestTuesday.consecutiveMissed)

        val afterOneMissedMondayCheckedWednesday = StreakEvaluator.evaluateOnAppLaunch(
            profile = baseProfile,
            weeklyPlan = savedPlans,
            todayEpochDay = tuesdayEpochDay + 1L
        )
        assertEquals(4, afterOneMissedMondayCheckedWednesday.currentStreak)
        assertEquals(1, afterOneMissedMondayCheckedWednesday.consecutiveMissed)

        val afterTwoMissedMondayAndWednesdayCheckedThursday = StreakEvaluator.evaluateOnAppLaunch(
            profile = baseProfile,
            weeklyPlan = savedPlans,
            todayEpochDay = thursdayEpochDay
        )
        assertEquals(0, afterTwoMissedMondayAndWednesdayCheckedThursday.currentStreak)
        assertEquals(2, afterTwoMissedMondayAndWednesdayCheckedThursday.consecutiveMissed)
    }

    @Test
    fun audit_03_workout_logging_epley_e1rm_met_calories_and_stats_heatmap() = runTest(testDispatcher) {
        val (dao, repository) = createEnvironment("audit_03.preferences_pb")

        repository.completeOnboardingProfile(
            name = "Alex",
            birthDateMillis = 946684800000L,
            weightKg = 75.0,
            heightCm = 180.0
        )

        val setEntity = WorkoutSetEntity(
            sessionId = 0L,
            exerciseName = "  bench   press ",
            setType = "NORMAL",
            setOrder = 1,
            weightKg = 70.0,
            reps = 10,
            e1rm = 0.0,
            timestampMillis = 1_700_000_000_000L
        )

        repository.recordWorkoutSession(
            planName = "Push Day",
            startMillis = 1_000_000L,
            endMillis = 2_800_000L,
            sets = listOf(setEntity),
            todayEpochDay = 20765L,
            muscleGroupsByExercise = mapOf("Bench Press" to "CHEST")
        )

        val history = dao.getExerciseHistory()
        assertEquals(1, history.size)
        assertEquals("Bench Press", history.first().normalizedName)
        assertEquals("CHEST", history.first().muscleGroup)

        val storedSets = dao.getAllSets()
        assertEquals(1, storedSets.size)
        assertEquals("Bench Press", storedSets.first().exerciseName)
        assertEquals(93.3, storedSets.first().e1rm, 0.001)

        val storedSessions = dao.getAllSessions()
        assertEquals(1, storedSessions.size)
        assertTrue(storedSessions.first().caloriesBurned > 0)

        val statsVm = StatsViewModel(repository)
        val statsState = statsVm.uiState.value
        assertTrue(statsState.hasStatisticsData)
        assertEquals("Bench Press", statsState.selectedExerciseName)
        assertEquals(1, statsState.muscleVolumes["CHEST"]?.weeklySets)
    }

    @Test
    fun audit_04_full_5_table_json_backup_and_restore_integrity() = runTest(testDispatcher) {
        val (sourceDao, sourceRepo) = createEnvironment("audit_04_src.preferences_pb")

        sourceRepo.signInWithEmail("alex@mfit.app", AuthEntryMode.SIGN_IN)
        sourceRepo.completeOnboardingProfile(
            name = "Alex",
            birthDateMillis = 946684800000L,
            weightKg = 76.5,
            heightCm = 181.0
        )
        sourceRepo.saveWeeklyWorkoutPlan(
            mapOf(
                1 to "Pull Day",
                3 to "Push Day",
                5 to "Legs Day"
            )
        )
        sourceRepo.recordWorkoutSession(
            planName = "Push Day",
            startMillis = 1_000_000L,
            endMillis = 3_400_000L,
            sets = listOf(
                WorkoutSetEntity(
                    sessionId = 0L,
                    exerciseName = "Bench Press",
                    setType = "NORMAL",
                    setOrder = 1,
                    weightKg = 80.0,
                    reps = 8,
                    e1rm = 0.0,
                    timestampMillis = 1_500_000L
                ),
                WorkoutSetEntity(
                    sessionId = 0L,
                    exerciseName = "Overhead Press",
                    setType = "FINAL",
                    setOrder = 2,
                    weightKg = 50.0,
                    reps = 6,
                    e1rm = 0.0,
                    timestampMillis = 2_000_000L
                )
            ),
            todayEpochDay = 20766L,
            muscleGroupsByExercise = mapOf(
                "Bench Press" to "CHEST",
                "Overhead Press" to "SHOULDERS"
            )
        )

        val exportedJson = GymBackupManager.exportToJson(sourceDao)
        assertTrue(exportedJson.isNotBlank())

        val (targetDao, targetRepo) = createEnvironment("audit_04_dst.preferences_pb")
        assertTrue(targetRepo.importBackupJson(exportedJson))

        assertEquals(sourceDao.getUserProfile(), targetDao.getUserProfile())
        assertEquals(sourceDao.getUserProfile()?.currentStreak, targetDao.getUserProfile()?.currentStreak)
        assertEquals(sourceDao.getWorkoutPlans(), targetDao.getWorkoutPlans())
        assertEquals(sourceDao.getExerciseHistory(), targetDao.getExerciseHistory())
        assertEquals(sourceDao.getAllSessions(), targetDao.getAllSessions())
        assertEquals(sourceDao.getAllSets(), targetDao.getAllSets())
    }
}

