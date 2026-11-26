package com.mulocoolbot.gymtracker.blackbox

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.mulocoolbot.gymtracker.data.auth.AuthEntryMode
import com.mulocoolbot.gymtracker.data.auth.SessionPreferences
import com.mulocoolbot.gymtracker.data.backup.GymBackupManager
import com.mulocoolbot.gymtracker.data.local.TestInMemoryGymDao
import com.mulocoolbot.gymtracker.data.local.UserProfileEntity
import com.mulocoolbot.gymtracker.data.repository.GymRepository
import com.mulocoolbot.gymtracker.platform.AuthBridge
import com.mulocoolbot.gymtracker.platform.createTestPreferencesPath
import com.mulocoolbot.gymtracker.timer.RestTimerController
import com.mulocoolbot.gymtracker.ui.auth.AppStartDestination
import com.mulocoolbot.gymtracker.ui.auth.AuthOnboardingViewModel
import com.mulocoolbot.gymtracker.ui.auth.EmailAuthStep
import com.mulocoolbot.gymtracker.ui.home.HomeViewModel
import com.mulocoolbot.gymtracker.ui.intro.MFitOpeningIntroTimeline
import com.mulocoolbot.gymtracker.ui.navigation.BottomNavTab
import com.mulocoolbot.gymtracker.ui.plan.WorkoutPlanSubPage
import com.mulocoolbot.gymtracker.ui.plan.WorkoutPlanViewModel
import com.mulocoolbot.gymtracker.ui.stats.HeatmapOrientation
import com.mulocoolbot.gymtracker.ui.stats.MuscleRegion
import com.mulocoolbot.gymtracker.ui.stats.StatsViewModel
import com.mulocoolbot.gymtracker.ui.stats.buildAnatomicalRegions
import com.mulocoolbot.gymtracker.ui.stats.computeChartNodeOffsets
import com.mulocoolbot.gymtracker.ui.tutorial.TutorialController
import com.mulocoolbot.gymtracker.ui.tutorial.TutorialTargetId
import com.mulocoolbot.gymtracker.ui.workout.WorkoutUiState
import com.mulocoolbot.gymtracker.ui.workout.WorkoutViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import okio.Path.Companion.toPath

@OptIn(ExperimentalCoroutinesApi::class)
class EndToEndBlackBoxTestSuite {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun offlineAuthBridge(): AuthBridge = object : AuthBridge {
        override val isGoogleSignInSupported: Boolean = false
        override val isCloudAuthConfigured: Boolean = false
        override fun syncEmailCredentials(email: String, password: String, isRegister: Boolean) = Unit
        override suspend fun deliverOtp(email: String, otpCode: String) = Unit
    }

    private fun createEnvironment(
        scope: CoroutineScope,
        prefsName: String
    ): Pair<TestInMemoryGymDao, GymRepository> {
        val dataStore = PreferenceDataStoreFactory.createWithPath(
            scope = scope,
            produceFile = { createTestPreferencesPath(prefsName).toPath() }
        )
        val dao = TestInMemoryGymDao()
        val repository = GymRepository(
            dao = dao,
            sessionPreferences = SessionPreferences(dataStore)
        )
        return dao to repository
    }

    @Test
    fun tc_e2e_01_first_launch_onboarding_and_new_user_tutorial() = runTest(testDispatcher) {
        val prefsPath = createTestPreferencesPath("e2e_01_clean_install.preferences_pb")
        val sharedDao = TestInMemoryGymDao()

        // Validate 4-barbell opening animation timeline
        assertEquals(2850L, MFitOpeningIntroTimeline.TOTAL_DURATION_MS)
        assertEquals(120L, MFitOpeningIntroTimeline.INNER_DROP_START_MS)
        assertEquals(420L, MFitOpeningIntroTimeline.INNER_IMPACT_MS)
        assertEquals(340L, MFitOpeningIntroTimeline.OUTER_DROP_START_MS)
        assertEquals(640L, MFitOpeningIntroTimeline.OUTER_IMPACT_MS)
        assertEquals(1080L, MFitOpeningIntroTimeline.ROD_START_MS)
        assertEquals(1220L, MFitOpeningIntroTimeline.METALLIC_LOCK_MS)
        assertEquals(1300L, MFitOpeningIntroTimeline.ROD_END_MS)
        assertEquals(1700L, MFitOpeningIntroTimeline.LEVITATE_START_MS)
        assertEquals(2150L, MFitOpeningIntroTimeline.LEVITATE_END_MS)
        assertEquals(1750L, MFitOpeningIntroTimeline.TEXT_REVEAL_START_MS)
        assertEquals(2250L, MFitOpeningIntroTimeline.TEXT_REVEAL_END_MS)
        assertTrue(MFitOpeningIntroTimeline.buildDeterministicCracks().isNotEmpty())
        assertTrue(MFitOpeningIntroTimeline.buildDeterministicSparks().isNotEmpty())
        assertEquals(1f, MFitOpeningIntroTimeline.computeRodProgress(1300L), 0.001f)
        assertEquals(1f, MFitOpeningIntroTimeline.computeLevitationProgress(2150L), 0.001f)
        assertEquals(1f, MFitOpeningIntroTimeline.computeTextRevealProgress(2850L), 0.001f)

        // Part 1: Full UI/ViewModel onboarding + 5-step tutorial + relogin flow
        val (_, repository) = createEnvironment(backgroundScope, "e2e_01_new_user.preferences_pb")
        repository.authSession.first()
        val authVm = AuthOnboardingViewModel(repository = repository, authBridge = offlineAuthBridge())

        assertTrue(authVm.uiState.value.isOpeningPlaying)
        authVm.onOpeningAnimationFinished()
        assertFalse(authVm.uiState.value.isOpeningPlaying)
        assertEquals(AppStartDestination.WELCOME, authVm.uiState.value.destination)

        authVm.onBeginFromWelcome()
        assertEquals(AppStartDestination.SIGN_IN, authVm.uiState.value.destination)
        assertEquals(AuthEntryMode.SIGN_IN, authVm.uiState.value.authEntryMode)

        authVm.requestEmailOtp("e2e.newuser@mfit.app", currentMillis = 10_000L)
        val otp1 = authVm.lastGeneratedOtpCode
        assertNotNull(otp1)
        authVm.verifyEmailOtp(otp1, currentMillis = 12_000L)

        assertEquals(AppStartDestination.PROFILE_SETUP, authVm.uiState.value.destination)
        assertTrue(authVm.uiState.value.authSession.shouldShowTutorial)
        assertFalse(authVm.uiState.value.authSession.hasCompletedTutorial)

        authVm.onSubmitProfile("E2E Athlete", 946684800000L, "75.0", "178")
        assertEquals(AppStartDestination.MAIN_HOME, authVm.uiState.value.destination)
        assertTrue(authVm.uiState.value.authSession.shouldShowTutorial)
        assertFalse(authVm.uiState.value.authSession.hasCompletedTutorial)

        val tutorialController = TutorialController()
        var activeTab = BottomNavTab.HOME
        val step1Ui = tutorialController.resolveUiState(authVm.uiState.value.authSession)
        assertTrue(step1Ui.isActive)
        assertEquals("1/10", step1Ui.currentStep.indicatorText)
        assertEquals(BottomNavTab.HOME, step1Ui.currentStep.tab)
        assertEquals(TutorialTargetId.HOME_STREAK, step1Ui.currentStep.targetId)
        assertTrue(step1Ui.showSkipButton)
        assertTrue(step1Ui.showNextButton)
        assertFalse(step1Ui.showDoneButton)

        val expectedProgression = listOf(
            Triple("2/10", BottomNavTab.HOME, TutorialTargetId.HOME_HERO_PLAN),
            Triple("3/10", BottomNavTab.PLAN, TutorialTargetId.PLAN_CREATE_AREA),
            Triple("4/10", BottomNavTab.PLAN, TutorialTargetId.PLAN_SELECT_DAYS_AREA),
            Triple("5/10", BottomNavTab.PLAN, TutorialTargetId.PLAN_INPUT_AREA),
            Triple("6/10", BottomNavTab.WORKOUT, TutorialTargetId.WORKOUT_START_AREA),
            Triple("7/10", BottomNavTab.WORKOUT, TutorialTargetId.WORKOUT_EXERCISE_MUSCLE_AREA),
            Triple("8/10", BottomNavTab.WORKOUT, TutorialTargetId.WORKOUT_SET_ENTRY_AREA),
            Triple("9/10", BottomNavTab.WORKOUT, TutorialTargetId.WORKOUT_REST_TIMER_AREA),
            Triple("10/10", BottomNavTab.STATS, TutorialTargetId.STATS_OVERVIEW_AREA)
        )
        expectedProgression.forEachIndexed { idx, (indicator, expectedTab, expectedTarget) ->
            activeTab = tutorialController.onNext()
            val ui = tutorialController.resolveUiState(authVm.uiState.value.authSession)
            assertTrue(ui.isActive)
            assertEquals(indicator, ui.currentStep.indicatorText)
            assertEquals(expectedTab, activeTab)
            assertEquals(expectedTab, ui.currentStep.tab)
            assertEquals(expectedTarget, ui.currentStep.targetId)
            val isLast = idx == expectedProgression.lastIndex
            assertEquals(!isLast, ui.showSkipButton)
            assertEquals(!isLast, ui.showNextButton)
            assertEquals(isLast, ui.showDoneButton)
        }

        tutorialController.onDone(
            onPersistCompleted = authVm::onCompleteTutorial,
            onNavigateHome = { activeTab = BottomNavTab.HOME }
        )
        assertEquals(BottomNavTab.HOME, activeTab)
        val afterDoneSession = authVm.uiState.value.authSession
        assertFalse(afterDoneSession.shouldShowTutorial)
        assertTrue(afterDoneSession.hasCompletedTutorial)
        assertFalse(tutorialController.resolveUiState(afterDoneSession).isActive)

        // Case A: Log out and Log In as same user -> tutorial must NOT reappear
        authVm.onToggleEmailLink("")
        assertEquals(AppStartDestination.SIGN_IN, authVm.uiState.value.destination)
        authVm.setAuthEntryMode(AuthEntryMode.LOG_IN)
        authVm.requestEmailOtp("e2e.newuser@mfit.app", currentMillis = 100_000L)
        val otpLogin = authVm.lastGeneratedOtpCode
        assertNotNull(otpLogin)
        authVm.verifyEmailOtp(otpLogin, currentMillis = 102_000L)
        assertEquals(AppStartDestination.MAIN_HOME, authVm.uiState.value.destination)
        assertFalse(authVm.uiState.value.authSession.shouldShowTutorial)
        assertFalse(TutorialController().resolveUiState(authVm.uiState.value.authSession).isActive)

        // Part 2: Persistence across DataStore cold reopen
        withContext(Dispatchers.IO) {
            val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val dataStore1 = PreferenceDataStoreFactory.createWithPath(
                    scope = scope1,
                    produceFile = { prefsPath.toPath() }
                )
                val repo1 = GymRepository(dao = sharedDao, sessionPreferences = SessionPreferences(dataStore1))
                repo1.signInWithEmail("e2e.persist@mfit.app", AuthEntryMode.SIGN_IN)
                repo1.completeOnboardingProfile("Persist Athlete", 946684800000L, 75.0, 178.0)
                assertTrue(repo1.authSession.first().shouldShowTutorial)

                val ctrl1 = TutorialController()
                repeat(9) { ctrl1.onNext() }
                ctrl1.onDone(
                    onPersistCompleted = { kotlinx.coroutines.runBlocking { repo1.completeTutorial() } },
                    onNavigateHome = {}
                )
                assertFalse(repo1.authSession.first().shouldShowTutorial)
                assertTrue(repo1.authSession.first().hasCompletedTutorial)
            } finally {
                scope1.coroutineContext[Job]!!.cancelAndJoin()
            }

            val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val dataStore2 = PreferenceDataStoreFactory.createWithPath(
                    scope = scope2,
                    produceFile = { prefsPath.toPath() }
                )
                val repo2 = GymRepository(dao = sharedDao, sessionPreferences = SessionPreferences(dataStore2))
                val reopenedSession = repo2.authSession.first()
                assertEquals(AppStartDestination.MAIN_HOME, AuthOnboardingViewModel.resolveDestination(reopenedSession))
                assertFalse(reopenedSession.shouldShowTutorial)
                assertTrue(reopenedSession.hasCompletedTutorial)
                assertFalse(TutorialController().resolveUiState(reopenedSession).isActive)
            } finally {
                scope2.coroutineContext[Job]!!.cancelAndJoin()
            }
        }

        // Case B: Existing user profile in fresh DataStore logging in via AuthEntryMode.LOG_IN -> no tutorial
        val (veteranDao, veteranRepo) = createEnvironment(backgroundScope, "e2e_01_veteran_login.preferences_pb")
        veteranRepo.authSession.first()
        veteranDao.upsertUserProfile(
            UserProfileEntity(
                uid = SessionPreferences.computeUidForEmail("e2e.veteran@mfit.app"),
                name = "Veteran Lifter",
                birthDateMillis = 915148800000L,
                heightCm = 180.0,
                weightKg = 80.0
            )
        )
        val veteranVm = AuthOnboardingViewModel(repository = veteranRepo, authBridge = offlineAuthBridge())
        veteranVm.onOpeningAnimationFinished()
        veteranVm.onBeginFromWelcome()
        veteranVm.setAuthEntryMode(AuthEntryMode.LOG_IN)
        veteranVm.requestEmailOtp("e2e.veteran@mfit.app", currentMillis = 10_000L)
        veteranVm.uiState.first { it.authStep == EmailAuthStep.OTP_VERIFICATION }
        val veteranOtp = veteranVm.lastGeneratedOtpCode
        assertNotNull(veteranOtp)
        veteranVm.verifyEmailOtp(veteranOtp, currentMillis = 12_000L)

        val veteranMainState = veteranVm.uiState.first { it.destination == AppStartDestination.MAIN_HOME }
        assertEquals(AppStartDestination.MAIN_HOME, veteranMainState.destination)
        assertFalse(veteranMainState.authSession.shouldShowTutorial)
        assertFalse(TutorialController().resolveUiState(veteranMainState.authSession).isActive)
    }

    @Test
    fun tc_e2e_02_full_workout_cycle_plan_workout_sets_and_home_streak() = runTest(testDispatcher) {
        val (_, repository) = createEnvironment(backgroundScope, "e2e_02_workout_cycle.preferences_pb")
        repository.signInWithEmail("e2e.lifter@mfit.app", AuthEntryMode.SIGN_IN)
        repository.completeOnboardingProfile("E2E Lifter", 946684800000L, 75.0, 178.0)

        val monday = LocalDate(2026, 11, 2)
        val epochDay = monday.toEpochDays().toLong()
        val planVm = WorkoutPlanViewModel(repository)
        val homeVm = HomeViewModel(repository = repository, currentDate = monday)
        val timerController = RestTimerController()
        val workoutVm = WorkoutViewModel(
            repository = repository,
            timerController = timerController,
            todayEpochDay = epochDay
        )

        assertEquals(0, homeVm.uiState.value.currentStreak)
        assertEquals(WorkoutPlanSubPage.EMPTY, planVm.uiState.value.subPage)

        // Create schedule in Plan
        planVm.startCreateOrEditPlan()
        assertEquals(WorkoutPlanSubPage.SELECT_DAYS, planVm.uiState.value.subPage)
        planVm.toggleDaySelection(1) // Monday
        planVm.toggleDaySelection(3) // Wednesday
        planVm.goToInputPlanPage()
        assertEquals(WorkoutPlanSubPage.INPUT_PLAN, planVm.uiState.value.subPage)
        planVm.updateDraftPlanName(1, "Push Day")
        planVm.updateDraftPlanName(3, "Pull Day")
        planVm.saveWorkoutPlan()

        assertEquals(WorkoutPlanSubPage.SAVED, planVm.uiState.value.subPage)
        assertEquals("Push Day", homeVm.uiState.value.selectedDayPlan.planName)
        assertFalse(homeVm.uiState.value.selectedDayPlan.isRestDay)

        // Start session in Workout & log 3 sets: warmup 60s, normal 120s, final 180s
        val triggeredDurations = mutableListOf<Long>()
        workoutVm.startWorkout(currentMillis = 1_700_000_000_000L)
        assertTrue(workoutVm.uiState.value.isWorkoutActive)

        workoutVm.onExerciseNameChanged("Bench Press")
        workoutVm.selectMuscleGroup("CHEST")

        // Set 1: Warmup (60s)
        workoutVm.selectSetType(WorkoutUiState.SET_TYPE_WARMUP)
        workoutVm.updateWeightInput("60")
        workoutVm.updateRepsInput("12")
        workoutVm.addSet { durationMillis, _ -> triggeredDurations.add(durationMillis) }
        assertEquals(60_000L, triggeredDurations.last())
        assertEquals("01:00", RestTimerController.formatMMSS(timerController.state.value.totalSeconds))
        assertEquals(84.0, workoutVm.uiState.value.activeSessionSets.last().e1rm, 0.001)

        // Set 2: Normal (120s)
        workoutVm.selectSetType(WorkoutUiState.SET_TYPE_NORMAL)
        workoutVm.updateWeightInput("80")
        workoutVm.updateRepsInput("8")
        workoutVm.addSet { durationMillis, _ -> triggeredDurations.add(durationMillis) }
        assertEquals(120_000L, triggeredDurations.last())
        assertEquals("02:00", RestTimerController.formatMMSS(timerController.state.value.totalSeconds))
        assertEquals(101.3, workoutVm.uiState.value.activeSessionSets.last().e1rm, 0.001)

        // Set 3: Final (180s)
        workoutVm.selectSetType(WorkoutUiState.SET_TYPE_FINAL)
        workoutVm.updateWeightInput("100")
        workoutVm.updateRepsInput("5")
        workoutVm.addSet { durationMillis, _ -> triggeredDurations.add(durationMillis) }
        assertEquals(180_000L, triggeredDurations.last())
        assertEquals("03:00", RestTimerController.formatMMSS(timerController.state.value.totalSeconds))
        assertEquals(116.7, workoutVm.uiState.value.activeSessionSets.last().e1rm, 0.001)

        assertEquals(listOf(60_000L, 120_000L, 180_000L), triggeredDurations)

        // Finish session & verify history and streak increment on Home
        workoutVm.requestFinishWorkout()
        assertTrue(workoutVm.uiState.value.showFinishDialog)

        var stopBridgeCalled = false
        workoutVm.stopWorkout(planName = homeVm.uiState.value.selectedDayPlan.planName) {
            stopBridgeCalled = true
        }

        assertTrue(stopBridgeCalled)
        assertFalse(workoutVm.uiState.value.isWorkoutActive)
        assertFalse(timerController.state.value.isRunning)
        assertEquals(3, workoutVm.uiState.value.todayCompletedSets.size)
        assertEquals(listOf("WARMUP", "NORMAL", "FINAL"), workoutVm.uiState.value.todayCompletedSets.map { it.setType })
        assertTrue(workoutVm.uiState.value.exerciseSuggestions.any { it.normalizedName == "Bench Press" && it.muscleGroup == "CHEST" })
        assertEquals(1, homeVm.uiState.value.currentStreak)
        assertTrue(homeVm.uiState.value.todayCalories > 0)
    }

    @Test
    fun tc_e2e_03_analytics_epley_e1rm_and_2d_muscle_heatmap() = runTest(testDispatcher) {
        val (_, repository) = createEnvironment(backgroundScope, "e2e_03_analytics.preferences_pb")
        repository.signInWithEmail("e2e.stats@mfit.app", AuthEntryMode.SIGN_IN)
        repository.completeOnboardingProfile("Stats Lifter", 946684800000L, 75.0, 178.0)

        val monday = LocalDate(2026, 11, 2)
        val workoutVm = WorkoutViewModel(
            repository = repository,
            timerController = RestTimerController(),
            todayEpochDay = monday.toEpochDays().toLong()
        )
        workoutVm.startWorkout(currentMillis = 1_700_000_000_000L)
        workoutVm.onExerciseNameChanged("Bench Press")
        workoutVm.selectMuscleGroup("CHEST")

        workoutVm.selectSetType(WorkoutUiState.SET_TYPE_WARMUP)
        workoutVm.updateWeightInput("60")
        workoutVm.updateRepsInput("12")
        workoutVm.addSet()

        workoutVm.selectSetType(WorkoutUiState.SET_TYPE_NORMAL)
        workoutVm.updateWeightInput("80")
        workoutVm.updateRepsInput("8")
        workoutVm.addSet()

        workoutVm.selectSetType(WorkoutUiState.SET_TYPE_FINAL)
        workoutVm.updateWeightInput("100")
        workoutVm.updateRepsInput("5")
        workoutVm.addSet()

        workoutVm.stopWorkout(planName = "Push Day")

        val statsVm = StatsViewModel(repository)
        val statsState = statsVm.uiState.value

        assertTrue(statsState.hasStatisticsData)
        assertEquals("Bench Press", statsState.selectedExerciseName)

        val option = statsState.selectedExerciseOption
        assertNotNull(option)
        assertEquals(listOf(84.0, 101.3, 116.7), option.points.map { it.e1rm })
        assertEquals(116.7, option.bestE1rm, 0.001)
        assertEquals("+32.7 kg", option.deltaKgText)
        assertTrue(option.isDeltaPositive)

        val offsets = computeChartNodeOffsets(
            points = option.points,
            width = 281f,
            height = 196f,
            horizontalPadding = 28f,
            topPadding = 28f,
            bottomPadding = 26f
        )
        assertEquals(3, offsets.size)
        assertTrue(offsets[0].y > offsets[1].y && offsets[1].y > offsets[2].y)

        assertEquals(3, statsState.muscleVolumes["CHEST"]?.weeklySets)
        assertEquals(3, statsState.muscleVolumes["TRICEPS"]?.weeklySets)
        assertEquals(0, statsState.muscleVolumes["QUADS"]?.weeklySets)
        assertEquals(0, statsState.muscleVolumes["BICEPS"]?.weeklySets)
        assertEquals(0, statsState.muscleVolumes["CALVES"]?.weeklySets)
        assertEquals(0, statsState.muscleVolumes["LATS"]?.weeklySets)
        assertTrue(MuscleRegion.CHEST in statsState.trainedMuscles)
        assertTrue(MuscleRegion.TRICEPS in statsState.trainedMuscles)

        val frontRegions = buildAnatomicalRegions(HeatmapOrientation.FRONT, 281f, 228f)
        val backRegions = buildAnatomicalRegions(HeatmapOrientation.BACK, 281f, 228f)
        assertTrue(frontRegions.any { it.first == "CHEST" })
        assertTrue(backRegions.any { it.first == "TRICEPS" })

        statsVm.switchHeatmapOrientation(HeatmapOrientation.BACK)
        assertEquals(HeatmapOrientation.BACK, statsVm.uiState.value.heatmapOrientation)
        assertEquals("LATS", statsVm.uiState.value.selectedMuscleId)
        statsVm.selectMuscleRegion("TRICEPS")
        assertEquals("TRICEPS", statsVm.uiState.value.selectedMuscleId)
    }

    @Test
    fun tc_e2e_04_cross_platform_backup_export_reset_import_portability() = runTest(testDispatcher) {
        val (dao1, repo1) = createEnvironment(backgroundScope, "e2e_04_source.preferences_pb")
        val authVm1 = AuthOnboardingViewModel(repository = repo1, authBridge = offlineAuthBridge())
        authVm1.onOpeningAnimationFinished()
        authVm1.onBeginFromWelcome()
        authVm1.onCreateAccount("backup.e2e@mfit.app", "strongPass123")
        authVm1.onSubmitProfile("Backup Athlete", 946684800000L, "75.0", "178")
        authVm1.onCompleteTutorial()

        val monday = LocalDate(2026, 11, 2)
        val epochDay = monday.toEpochDays().toLong()
        val planVm1 = WorkoutPlanViewModel(repo1)
        planVm1.startCreateOrEditPlan()
        planVm1.toggleDaySelection(1)
        planVm1.goToInputPlanPage()
        planVm1.updateDraftPlanName(1, "Push Day")
        planVm1.saveWorkoutPlan()

        val homeVm1 = HomeViewModel(repository = repo1, currentDate = monday)
        val workoutVm1 = WorkoutViewModel(
            repository = repo1,
            timerController = RestTimerController(),
            todayEpochDay = epochDay
        )
        workoutVm1.startWorkout(currentMillis = 1_700_000_000_000L)
        workoutVm1.onExerciseNameChanged("Bench Press")
        workoutVm1.selectMuscleGroup("CHEST")

        workoutVm1.selectSetType(WorkoutUiState.SET_TYPE_WARMUP)
        workoutVm1.updateWeightInput("60")
        workoutVm1.updateRepsInput("12")
        workoutVm1.addSet()

        workoutVm1.selectSetType(WorkoutUiState.SET_TYPE_NORMAL)
        workoutVm1.updateWeightInput("80")
        workoutVm1.updateRepsInput("8")
        workoutVm1.addSet()

        workoutVm1.selectSetType(WorkoutUiState.SET_TYPE_FINAL)
        workoutVm1.updateWeightInput("100")
        workoutVm1.updateRepsInput("5")
        workoutVm1.addSet()

        workoutVm1.stopWorkout(planName = "Push Day")

        val statsVm1 = StatsViewModel(repo1)
        val origProfile = dao1.getUserProfile()
        assertNotNull(origProfile)
        val origCalories = homeVm1.uiState.value.todayCalories
        val origStreak = homeVm1.uiState.value.currentStreak
        val origE1rmPoints = statsVm1.uiState.value.selectedExerciseOption!!.points.map { it.e1rm }
        assertEquals(1, origStreak)
        assertTrue(origCalories > 0)
        assertEquals(listOf(84.0, 101.3, 116.7), origE1rmPoints)

        // Export JSON
        val exportedJson = authVm1.exportBackupJson()
        assertTrue(exportedJson.contains("\"schemaVersion\": 1"))
        assertTrue(exportedJson.contains("\"userProfile\""))
        assertTrue(exportedJson.contains("\"workoutPlans\""))
        assertTrue(exportedJson.contains("\"exerciseHistory\""))
        assertTrue(exportedJson.contains("\"workoutSessions\""))
        assertTrue(exportedJson.contains("\"workoutSets\""))

        // Simulate app data reset (fresh DAO + fresh DataStore)
        val (dao2, repo2) = createEnvironment(backgroundScope, "e2e_04_reset_target.preferences_pb")
        val homeVm2 = HomeViewModel(repository = repo2, currentDate = monday)
        val planVm2 = WorkoutPlanViewModel(repo2)
        val workoutVm2 = WorkoutViewModel(
            repository = repo2,
            timerController = RestTimerController(),
            todayEpochDay = epochDay
        )
        val statsVm2 = StatsViewModel(repo2)

        assertEquals(0, homeVm2.uiState.value.currentStreak)
        assertEquals(0, homeVm2.uiState.value.todayCalories)
        assertTrue(homeVm2.uiState.value.selectedDayPlan.isRestDay)
        assertEquals(WorkoutPlanSubPage.EMPTY, planVm2.uiState.value.subPage)
        assertTrue(workoutVm2.uiState.value.todayCompletedSets.isEmpty())
        assertFalse(statsVm2.uiState.value.hasStatisticsData)

        // Import exported JSON back
        assertTrue(repo2.importBackupJson(exportedJson))

        assertEquals(origProfile, dao2.getUserProfile())
        assertEquals(origStreak, homeVm2.uiState.value.currentStreak)
        assertEquals(origCalories, homeVm2.uiState.value.todayCalories)
        assertEquals("Push Day", homeVm2.uiState.value.selectedDayPlan.planName)
        assertFalse(homeVm2.uiState.value.selectedDayPlan.isRestDay)
        assertEquals(WorkoutPlanSubPage.SAVED, planVm2.uiState.value.subPage)
        assertEquals(3, workoutVm2.uiState.value.todayCompletedSets.size)
        assertTrue(statsVm2.uiState.value.hasStatisticsData)
        assertEquals(origE1rmPoints, statsVm2.uiState.value.selectedExerciseOption!!.points.map { it.e1rm })
        assertEquals(3, statsVm2.uiState.value.muscleVolumes["CHEST"]?.weeklySets)
        assertEquals(3, statsVm2.uiState.value.muscleVolumes["TRICEPS"]?.weeklySets)
        assertFalse(repo2.authSession.first().shouldShowTutorial)

        // Corrupted JSON must be rejected and leave restored data intact
        assertFalse(repo2.importBackupJson("{corrupted"))
        assertEquals(origProfile, dao2.getUserProfile())
        assertEquals(3, workoutVm2.uiState.value.todayCompletedSets.size)

        // Cross-platform Android v2 JSON backup payload imports cleanly on KMP
        val androidV2Json = """
            {"schemaVersion": 1,"userProfile":{"uid":"android-v2-e2e","name":"Android User","birthDateMillis":915148800000,"heightCm":175.0,"weightKg":72.0,"currentStreak":4,"consecutiveMissed":0,"lastWorkoutEpochDay":20759,"isGoogleLinked":false,"isEmailLinked":true},"workoutPlans":[{"dayOfWeek":1,"isRestDay":false,"planName":"Upper Body"}],"exerciseHistory":[{"normalizedName":"Overhead Press","muscleGroup":"SHOULDERS","lastUsedMillis":123456}],"workoutSessions":[{"sessionId":1,"dateEpochDay":20759,"planName":"Upper Body","startTimeMillis":1000,"endTimeMillis":1801000,"caloriesBurned":189}],"workoutSets":[{"setId":1,"sessionId":1,"exerciseName":"Overhead Press","setType":"FINAL","setOrder":1,"weightKg":60.0,"reps":6,"e1rm":72.0,"timestampMillis":5000}]}
        """.trimIndent()
        val (androidImportDao, androidImportRepo) = createEnvironment(backgroundScope, "e2e_04_android_v2.preferences_pb")
        assertTrue(androidImportRepo.importBackupJson(androidV2Json))
        assertEquals("Android User", androidImportDao.getUserProfile()?.name)
        assertEquals(4, androidImportDao.getUserProfile()?.currentStreak)
        assertEquals(72.0, androidImportDao.getAllSets().single().e1rm, 0.001)
    }
}
