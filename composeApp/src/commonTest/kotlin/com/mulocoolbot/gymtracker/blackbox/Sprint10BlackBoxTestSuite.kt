package com.mulocoolbot.gymtracker.blackbox

import androidx.compose.ui.geometry.Rect
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.mulocoolbot.gymtracker.data.auth.AuthEntryMode
import com.mulocoolbot.gymtracker.data.auth.SessionPreferences
import com.mulocoolbot.gymtracker.data.local.TestInMemoryGymDao
import com.mulocoolbot.gymtracker.data.repository.GymRepository
import com.mulocoolbot.gymtracker.platform.AuthBridge
import com.mulocoolbot.gymtracker.platform.createTestPreferencesPath
import com.mulocoolbot.gymtracker.timer.BackgroundTimerCoordinator
import com.mulocoolbot.gymtracker.timer.LocalTimerNotificationScheduler
import com.mulocoolbot.gymtracker.timer.RestTimerController
import com.mulocoolbot.gymtracker.timer.TimerAudioDuckingPort
import com.mulocoolbot.gymtracker.timer.WorkoutLiveIslandState
import com.mulocoolbot.gymtracker.timer.resolveWorkoutLiveIslandState
import com.mulocoolbot.gymtracker.ui.auth.AuthOnboardingViewModel
import com.mulocoolbot.gymtracker.ui.home.HomeViewModel
import com.mulocoolbot.gymtracker.ui.navigation.BottomNavTab
import com.mulocoolbot.gymtracker.ui.plan.WorkoutPlanSubPage
import com.mulocoolbot.gymtracker.ui.plan.WorkoutPlanViewModel
import com.mulocoolbot.gymtracker.ui.stats.HeatmapOrientation
import com.mulocoolbot.gymtracker.ui.stats.StatsViewModel
import com.mulocoolbot.gymtracker.ui.theme.VoltAccent
import com.mulocoolbot.gymtracker.ui.tutorial.MFitTutorialSteps
import com.mulocoolbot.gymtracker.ui.tutorial.TutorialController
import com.mulocoolbot.gymtracker.ui.tutorial.TutorialPreviewData
import com.mulocoolbot.gymtracker.ui.tutorial.TutorialTargetId
import com.mulocoolbot.gymtracker.ui.tutorial.computeSecondarySpotlightRect
import com.mulocoolbot.gymtracker.ui.tutorial.computeTutorialOverlayLayout
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.Path.Companion.toPath

@OptIn(ExperimentalCoroutinesApi::class)
class Sprint10BlackBoxTestSuite {

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
        override val isGoogleSignInSupported: Boolean = true
        override val isCloudAuthConfigured: Boolean = true
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
    fun tc_s10_01_ten_step_sequence_and_indicators() = runTest(testDispatcher) {
        val (_, repository) = createEnvironment(backgroundScope, "s10_01_sequence.preferences_pb")
        val authVm = AuthOnboardingViewModel(repository = repository, authBridge = offlineAuthBridge())
        authVm.onOpeningAnimationFinished()
        authVm.onBeginFromWelcome()
        authVm.requestEmailOtp("s10.sequence@mfit.app", currentMillis = 10_000L)
        authVm.verifyEmailOtp(authVm.lastGeneratedOtpCode!!, currentMillis = 12_000L)
        authVm.onSubmitProfile("S10 Tester", 946684800000L, "72.0", "176")

        val controller = TutorialController()
        val session = authVm.uiState.value.authSession
        val indicators = mutableListOf<String>()
        val tabs = mutableListOf<BottomNavTab>()
        val primaryTargets = mutableListOf<TutorialTargetId>()
        val secondaryTargets = mutableListOf<TutorialTargetId?>()
        val skipVisibility = mutableListOf<Boolean>()
        val nextVisibility = mutableListOf<Boolean>()
        val doneVisibility = mutableListOf<Boolean>()

        fun recordCurrent() {
            val ui = controller.resolveUiState(session)
            assertTrue(ui.isActive)
            indicators.add(ui.currentStep.indicatorText)
            tabs.add(ui.currentStep.tab)
            primaryTargets.add(ui.currentStep.targetId)
            secondaryTargets.add(ui.currentStep.secondaryTargetId)
            skipVisibility.add(ui.showSkipButton)
            nextVisibility.add(ui.showNextButton)
            doneVisibility.add(ui.showDoneButton)
            assertFalse(ui.currentStep.title.any { it.code > 0x2600 })
            assertFalse(ui.currentStep.description.any { it.code > 0x2600 })
        }

        recordCurrent()
        repeat(9) {
            val nextTab = controller.onNext()
            assertEquals(controller.resolveUiState(session).currentStep.tab, nextTab)
            recordCurrent()
        }

        assertEquals((1..10).map { "$it/10" }, indicators)
        assertEquals(
            listOf(
                BottomNavTab.HOME,
                BottomNavTab.HOME,
                BottomNavTab.PLAN,
                BottomNavTab.PLAN,
                BottomNavTab.PLAN,
                BottomNavTab.WORKOUT,
                BottomNavTab.WORKOUT,
                BottomNavTab.WORKOUT,
                BottomNavTab.WORKOUT,
                BottomNavTab.STATS
            ),
            tabs
        )
        assertEquals(
            listOf(
                TutorialTargetId.HOME_STREAK,
                TutorialTargetId.HOME_HERO_PLAN,
                TutorialTargetId.PLAN_CREATE_AREA,
                TutorialTargetId.PLAN_SELECT_DAYS_AREA,
                TutorialTargetId.PLAN_INPUT_AREA,
                TutorialTargetId.WORKOUT_START_AREA,
                TutorialTargetId.WORKOUT_EXERCISE_MUSCLE_AREA,
                TutorialTargetId.WORKOUT_SET_ENTRY_AREA,
                TutorialTargetId.WORKOUT_REST_TIMER_AREA,
                TutorialTargetId.STATS_OVERVIEW_AREA
            ),
            primaryTargets
        )
        assertEquals(
            listOf(
                null,
                null,
                null,
                null,
                TutorialTargetId.PLAN_SAVE_BUTTON,
                null,
                null,
                null,
                TutorialTargetId.WORKOUT_FINISH_BUTTON,
                null
            ),
            secondaryTargets
        )
        assertEquals(listOf(true, true, true, true, true, true, true, true, true, false), skipVisibility)
        assertEquals(listOf(true, true, true, true, true, true, true, true, true, false), nextVisibility)
        assertEquals(listOf(false, false, false, false, false, false, false, false, false, true), doneVisibility)
    }

    @Test
    fun tc_s10_02_ephemeral_dummy_preview_data_across_all_steps() = runTest(testDispatcher) {
        val (_, repository) = createEnvironment(backgroundScope, "s10_02_preview.preferences_pb")
        repository.signInWithEmail("s10.preview@mfit.app", AuthEntryMode.SIGN_IN)
        repository.completeOnboardingProfile("Preview Athlete", 946684800000L, 74.0, 178.0)

        val homeVm = HomeViewModel(repository)
        val planVm = WorkoutPlanViewModel(repository)
        val timerController = RestTimerController()
        val workoutVm = WorkoutViewModel(repository = repository, timerController = timerController)
        val statsVm = StatsViewModel(repository)
        val controller = TutorialController()
        val session = repository.authSession.first()

        val step1Home = TutorialPreviewData.resolveHomeUiState(homeVm.uiState.value, controller.resolveUiState(session))
        assertEquals(5, step1Home.currentStreak)
        assertEquals("Push Day", step1Home.selectedDayPlan.planName)
        assertFalse(step1Home.selectedDayPlan.isRestDay)

        controller.onNext()
        val step2Home = TutorialPreviewData.resolveHomeUiState(homeVm.uiState.value, controller.resolveUiState(session))
        assertEquals("Push Day", step2Home.selectedDayPlan.planName)

        controller.onNext()
        val step3Plan = TutorialPreviewData.resolveWorkoutPlanUiState(planVm.uiState.value, controller.resolveUiState(session))
        assertEquals(WorkoutPlanSubPage.EMPTY, step3Plan.subPage)

        controller.onNext()
        val step4Plan = TutorialPreviewData.resolveWorkoutPlanUiState(planVm.uiState.value, controller.resolveUiState(session))
        assertEquals(WorkoutPlanSubPage.SELECT_DAYS, step4Plan.subPage)
        assertEquals(setOf(1, 3, 5), step4Plan.selectedDays)

        controller.onNext()
        val step5Plan = TutorialPreviewData.resolveWorkoutPlanUiState(planVm.uiState.value, controller.resolveUiState(session))
        assertEquals(WorkoutPlanSubPage.INPUT_PLAN, step5Plan.subPage)
        assertEquals(mapOf(1 to "Push Day", 3 to "Pull Day", 5 to "Legs"), step5Plan.draftPlanNames)

        controller.onNext()
        val step6Workout = TutorialPreviewData.resolveWorkoutUiState(workoutVm.uiState.value, controller.resolveUiState(session))
        assertFalse(step6Workout.isWorkoutActive)
        assertEquals("Push Day", step6Workout.todayPlanName)
        assertTrue(step6Workout.todayCompletedSets.isEmpty())

        controller.onNext()
        val step7Workout = TutorialPreviewData.resolveWorkoutUiState(workoutVm.uiState.value, controller.resolveUiState(session))
        assertTrue(step7Workout.isWorkoutActive)
        assertEquals("Bench Press", step7Workout.exerciseInput)
        assertEquals("CHEST", step7Workout.selectedMuscleGroup)
        assertTrue(step7Workout.activeSessionSets.isEmpty())
        assertFalse(step7Workout.restTimerState.isRunning)

        controller.onNext()
        val step8Workout = TutorialPreviewData.resolveWorkoutUiState(workoutVm.uiState.value, controller.resolveUiState(session))
        assertTrue(step8Workout.isWorkoutActive)
        assertEquals("60", step8Workout.weightInput)
        assertEquals("10", step8Workout.repsInput)
        assertEquals(WorkoutUiState.SET_TYPE_NORMAL, step8Workout.selectedSetType)
        assertEquals(2, step8Workout.activeSessionSets.size)
        assertFalse(step8Workout.restTimerState.isRunning)

        controller.onNext()
        val step9Workout = TutorialPreviewData.resolveWorkoutUiState(workoutVm.uiState.value, controller.resolveUiState(session))
        assertTrue(step9Workout.isWorkoutActive)
        assertTrue(step9Workout.restTimerState.isRunning)
        assertEquals(105, step9Workout.restTimerState.remainingSeconds)
        assertEquals("01:45", RestTimerController.formatMMSS(step9Workout.restTimerState.remainingSeconds))

        controller.onNext()
        val step10Stats = TutorialPreviewData.resolveStatsUiState(statsVm.uiState.value, controller.resolveUiState(session))
        assertTrue(step10Stats.hasStatisticsData)
        assertEquals("Bench Press", step10Stats.selectedExerciseName)
        assertEquals(4, step10Stats.selectedExerciseOption?.points?.size)
        assertEquals(HeatmapOrientation.FRONT, step10Stats.heatmapOrientation)
        assertEquals("CHEST", step10Stats.selectedMuscleId)
        assertTrue((step10Stats.muscleVolumes["CHEST"]?.weeklySets ?: 0) >= 8)
    }

    @Test
    fun tc_s10_03_zero_pollution_after_skip_from_mid_tutorial() = runTest(testDispatcher) {
        listOf(4, 7).forEach { nextClicks ->
            val (dao, repository) = createEnvironment(backgroundScope, "s10_03_skip_${nextClicks}.preferences_pb")
            val authVm = AuthOnboardingViewModel(repository = repository, authBridge = offlineAuthBridge())
            authVm.onOpeningAnimationFinished()
            authVm.onBeginFromWelcome()
            authVm.requestEmailOtp("s10.skip.${nextClicks}@mfit.app", currentMillis = 10_000L)
            authVm.verifyEmailOtp(authVm.lastGeneratedOtpCode!!, currentMillis = 12_000L)
            authVm.onSubmitProfile("Skip User", 946684800000L, "70.0", "175")

            val homeVm = HomeViewModel(repository)
            val planVm = WorkoutPlanViewModel(repository)
            val workoutVm = WorkoutViewModel(repository = repository, timerController = RestTimerController())
            val statsVm = StatsViewModel(repository)
            val controller = TutorialController()

            var activeTab = controller.resolveUiState(authVm.uiState.value.authSession).currentStep.tab
            repeat(nextClicks) {
                activeTab = controller.onNext()
            }
            val expectedStepNumber = nextClicks + 1
            assertEquals("$expectedStepNumber/10", controller.resolveUiState(authVm.uiState.value.authSession).currentStep.indicatorText)

            controller.onSkip(
                onPersistCompleted = authVm::onCompleteTutorial,
                onNavigateHome = { activeTab = BottomNavTab.HOME }
            )

            assertEquals(BottomNavTab.HOME, activeTab)
            val afterSkipUi = controller.resolveUiState(authVm.uiState.value.authSession)
            assertFalse(afterSkipUi.isActive)
            assertFalse(authVm.uiState.value.authSession.shouldShowTutorial)
            assertTrue(authVm.uiState.value.authSession.hasCompletedTutorial)

            val resolvedHome = TutorialPreviewData.resolveHomeUiState(homeVm.uiState.value, afterSkipUi)
            val resolvedPlan = TutorialPreviewData.resolveWorkoutPlanUiState(planVm.uiState.value, afterSkipUi)
            val resolvedWorkout = TutorialPreviewData.resolveWorkoutUiState(workoutVm.uiState.value, afterSkipUi)
            val resolvedStats = TutorialPreviewData.resolveStatsUiState(statsVm.uiState.value, afterSkipUi)

            assertEquals(0, resolvedHome.currentStreak)
            assertTrue(resolvedHome.selectedDayPlan.isRestDay)
            assertEquals(WorkoutPlanSubPage.EMPTY, resolvedPlan.subPage)
            assertFalse(resolvedPlan.hasSavedPlan)
            assertFalse(resolvedWorkout.isWorkoutActive)
            assertTrue(resolvedWorkout.activeSessionSets.isEmpty())
            assertTrue(resolvedWorkout.todayCompletedSets.isEmpty())
            assertFalse(resolvedStats.hasStatisticsData)

            assertTrue(dao.getWorkoutPlans().all { it.isRestDay })
            assertTrue(dao.getAllSessions().isEmpty())
            assertTrue(dao.getAllSets().isEmpty())
            assertTrue(dao.getExerciseHistory().isEmpty())
            assertEquals(0, dao.getUserProfile()?.currentStreak)
        }
    }

    @Test
    fun tc_s10_04_zero_pollution_after_done_on_final_step() = runTest(testDispatcher) {
        val (dao, repository) = createEnvironment(backgroundScope, "s10_04_done.preferences_pb")
        val authVm = AuthOnboardingViewModel(repository = repository, authBridge = offlineAuthBridge())
        authVm.onOpeningAnimationFinished()
        authVm.onBeginFromWelcome()
        authVm.requestEmailOtp("s10.done@mfit.app", currentMillis = 10_000L)
        authVm.verifyEmailOtp(authVm.lastGeneratedOtpCode!!, currentMillis = 12_000L)
        authVm.onSubmitProfile("Done User", 946684800000L, "75.0", "180")

        val homeVm = HomeViewModel(repository)
        val planVm = WorkoutPlanViewModel(repository)
        val workoutVm = WorkoutViewModel(repository = repository, timerController = RestTimerController())
        val statsVm = StatsViewModel(repository)
        val controller = TutorialController()

        var activeTab = BottomNavTab.HOME
        repeat(9) {
            activeTab = controller.onNext()
        }
        assertEquals(BottomNavTab.STATS, activeTab)

        val step10Ui = controller.resolveUiState(authVm.uiState.value.authSession)
        assertEquals("10/10", step10Ui.currentStep.indicatorText)
        assertFalse(step10Ui.showSkipButton)
        assertFalse(step10Ui.showNextButton)
        assertTrue(step10Ui.showDoneButton)

        controller.onDone(
            onPersistCompleted = authVm::onCompleteTutorial,
            onNavigateHome = { activeTab = BottomNavTab.HOME }
        )

        assertEquals(BottomNavTab.HOME, activeTab)
        val afterDoneUi = controller.resolveUiState(authVm.uiState.value.authSession)
        assertFalse(afterDoneUi.isActive)
        assertFalse(authVm.uiState.value.authSession.shouldShowTutorial)
        assertTrue(authVm.uiState.value.authSession.hasCompletedTutorial)

        val resolvedHome = TutorialPreviewData.resolveHomeUiState(homeVm.uiState.value, afterDoneUi)
        val resolvedPlan = TutorialPreviewData.resolveWorkoutPlanUiState(planVm.uiState.value, afterDoneUi)
        val resolvedWorkout = TutorialPreviewData.resolveWorkoutUiState(workoutVm.uiState.value, afterDoneUi)
        val resolvedStats = TutorialPreviewData.resolveStatsUiState(statsVm.uiState.value, afterDoneUi)

        assertEquals(0, resolvedHome.currentStreak)
        assertTrue(resolvedHome.selectedDayPlan.isRestDay)
        assertEquals(WorkoutPlanSubPage.EMPTY, resolvedPlan.subPage)
        assertFalse(resolvedPlan.hasSavedPlan)
        assertFalse(resolvedWorkout.isWorkoutActive)
        assertTrue(resolvedWorkout.activeSessionSets.isEmpty())
        assertTrue(resolvedWorkout.todayCompletedSets.isEmpty())
        assertFalse(resolvedStats.hasStatisticsData)

        assertTrue(dao.getWorkoutPlans().all { it.isRestDay })
        assertTrue(dao.getAllSessions().isEmpty())
        assertTrue(dao.getAllSets().isEmpty())
        assertTrue(dao.getExerciseHistory().isEmpty())
        assertEquals(0, dao.getUserProfile()?.currentStreak)
    }

    @Test
    fun tc_s10_05_isolation_from_rest_timer_service_notifications_and_dynamic_island() = runTest(testDispatcher) {
        val (_, repository) = createEnvironment(backgroundScope, "s10_05_isolation.preferences_pb")
        repository.signInWithEmail("s10.isolation@mfit.app", AuthEntryMode.SIGN_IN)
        repository.completeOnboardingProfile("Isolation User", 946684800000L, 73.0, 177.0)

        var scheduledNotifications = 0
        var completionAlerts = 0
        val timerController = RestTimerController()
        val coordinator = BackgroundTimerCoordinator(
            controller = timerController,
            notifications = object : LocalTimerNotificationScheduler {
                override fun scheduleCompletion(delayMillis: Long, label: String) {
                    scheduledNotifications++
                }

                override fun cancel(clearDelivered: Boolean) = Unit
            },
            audio = object : TimerAudioDuckingPort {
                override fun playDuckedCompletionAlert() {
                    completionAlerts++
                }

                override fun stopAndUnduckAudio() = Unit
            },
            clockMillis = { 10_000_000L }
        )
        val workoutVm = WorkoutViewModel(repository = repository, timerController = timerController)
        val controller = TutorialController()
        val session = repository.authSession.first()

        repeat(8) {
            controller.onNext()
        }
        val step9Ui = controller.resolveUiState(session)
        assertEquals("9/10", step9Ui.currentStep.indicatorText)
        assertEquals(TutorialTargetId.WORKOUT_REST_TIMER_AREA, step9Ui.currentStep.targetId)

        val previewWorkout = TutorialPreviewData.resolveWorkoutUiState(workoutVm.uiState.value, step9Ui)
        assertTrue(previewWorkout.restTimerState.isRunning)
        assertEquals("01:45", RestTimerController.formatMMSS(previewWorkout.restTimerState.remainingSeconds))

        assertFalse(timerController.state.value.isRunning)
        assertEquals(0, timerController.state.value.remainingSeconds)
        assertFalse(timerController.state.value.alarmRinging)
        assertEquals(0, scheduledNotifications)
        assertEquals(0, completionAlerts)
        assertTrue(coordinator.isAppInForeground)
        assertEquals(WorkoutLiveIslandState.Inactive, workoutVm.liveIslandState.value)
        assertEquals(
            WorkoutLiveIslandState.Inactive,
            resolveWorkoutLiveIslandState(
                isWorkoutActive = workoutVm.uiState.value.isWorkoutActive,
                restTimerState = workoutVm.uiState.value.restTimerState
            )
        )
    }

    @Test
    fun tc_s10_06_spotlight_accuracy_and_non_overlapping_card_across_all_10_steps() {
        assertEquals(0xFFD4FF00UL, VoltAccent.value shr 32)
        assertEquals(10, MFitTutorialSteps.size)

        data class DeviceViewport(
            val name: String,
            val width: Float,
            val height: Float,
            val topInset: Float,
            val bottomInset: Float
        )

        val viewports = listOf(
            DeviceViewport("Android Compact", 360f, 640f, 24f, 16f),
            DeviceViewport("iPhone SE", 375f, 667f, 20f, 0f),
            DeviceViewport("iPhone 15 Pro", 393f, 852f, 59f, 34f),
            DeviceViewport("Pixel 8", 412f, 915f, 28f, 24f),
            DeviceViewport("iPhone 16 Pro Max", 430f, 932f, 59f, 34f)
        )

        val cardHeight = 182f
        val pad = 6f
        val maxCorner = 28f
        val connectorGap = 20f
        val edgeMargin = 14f
        val cardHorizontalMargin = 20f
        val cardAnchorInset = 28f
        val minConnectorGap = 4f

        viewports.forEach { vp ->
            val contentTop = vp.topInset + 24f
            val contentBottom = vp.height - vp.bottomInset - 96f
            val targetRects = mapOf(
                TutorialTargetId.HOME_STREAK to Rect(20f, contentTop + 148f, (vp.width - 54f) / 2f + 20f, contentTop + 312f),
                TutorialTargetId.HOME_HERO_PLAN to Rect(20f, contentTop + 332f, vp.width - 20f, (contentTop + 530f).coerceAtMost(contentBottom)),
                TutorialTargetId.PLAN_CREATE_AREA to Rect(52f, (contentTop + contentBottom) / 2f + 30f, vp.width - 52f, (contentTop + contentBottom) / 2f + 82f),
                TutorialTargetId.PLAN_SELECT_DAYS_AREA to Rect(20f, contentTop + 110f, vp.width - 20f, (contentTop + 340f).coerceAtMost(contentBottom - 200f)),
                TutorialTargetId.PLAN_INPUT_AREA to Rect(20f, contentTop + 110f, vp.width - 20f, (contentTop + 275f).coerceAtMost(contentBottom - 200f)),
                TutorialTargetId.PLAN_SAVE_BUTTON to Rect(vp.width - 108f, contentTop + 4f, vp.width - 20f, contentTop + 48f),
                TutorialTargetId.WORKOUT_START_AREA to Rect(52f, (contentTop + contentBottom) / 2f + 30f, vp.width - 52f, (contentTop + contentBottom) / 2f + 84f),
                TutorialTargetId.WORKOUT_EXERCISE_MUSCLE_AREA to Rect(40f, contentTop + 80f, vp.width - 40f, contentTop + 215f),
                TutorialTargetId.WORKOUT_SET_ENTRY_AREA to Rect(40f, contentTop + 225f, vp.width - 40f, (contentTop + 425f).coerceAtMost(contentBottom)),
                TutorialTargetId.WORKOUT_REST_TIMER_AREA to Rect(20f, (contentBottom - 120f).coerceAtLeast(contentTop + 220f), vp.width - 20f, contentBottom - 12f),
                TutorialTargetId.WORKOUT_FINISH_BUTTON to Rect(vp.width - 116f, contentTop + 4f, vp.width - 20f, contentTop + 48f),
                TutorialTargetId.STATS_OVERVIEW_AREA to Rect(20f, contentTop + 56f, vp.width - 20f, (contentTop + 330f).coerceAtMost(contentBottom - 190f))
            )

            MFitTutorialSteps.forEach { step ->
                val expectedTarget = targetRects.getValue(step.targetId)
                val layout = computeTutorialOverlayLayout(
                    containerWidth = vp.width,
                    containerHeight = vp.height,
                    targetRect = expectedTarget,
                    cardHeightPx = cardHeight,
                    topSafeInsetPx = vp.topInset,
                    bottomSafeInsetPx = vp.bottomInset,
                    padPx = pad,
                    maxCornerPx = maxCorner,
                    connectorGapPx = connectorGap,
                    edgeMarginPx = edgeMargin,
                    cardHorizontalMarginPx = cardHorizontalMargin,
                    cardAnchorInsetPx = cardAnchorInset,
                    minConnectorGapPx = minConnectorGap
                )

                assertTrue(layout.hasSpotlight, "${vp.name} step ${step.indicatorText} missing spotlight")
                assertTrue(layout.spotlightRect.left <= expectedTarget.left, "${vp.name} ${step.indicatorText} spotlight left mismatch")
                assertTrue(layout.spotlightRect.right >= expectedTarget.right, "${vp.name} ${step.indicatorText} spotlight right mismatch")
                assertTrue(layout.spotlightRect.top >= vp.topInset, "${vp.name} ${step.indicatorText} spotlight clipped top")
                assertTrue(layout.spotlightRect.bottom <= vp.height - vp.bottomInset, "${vp.name} ${step.indicatorText} spotlight clipped bottom")

                val cardRect = Rect(layout.cardLeftPx, layout.cardTopPx, layout.cardRightPx, layout.cardTopPx + cardHeight)
                assertTrue(cardRect.top >= vp.topInset + edgeMargin - 0.5f, "${vp.name} ${step.indicatorText} card clipped top")
                assertTrue(cardRect.bottom <= vp.height - vp.bottomInset - edgeMargin + 0.5f, "${vp.name} ${step.indicatorText} card clipped bottom")
                assertFalse(
                    cardRect.overlaps(layout.spotlightRect),
                    "${vp.name} ${step.indicatorText} card overlapped spotlight"
                )
                assertTrue(layout.showConnector, "${vp.name} ${step.indicatorText} connector hidden")

                step.secondaryTargetId?.let { secondaryId ->
                    val secondaryTarget = targetRects.getValue(secondaryId)
                    val secondaryRect = computeSecondarySpotlightRect(
                        containerWidth = vp.width,
                        containerHeight = vp.height,
                        targetRect = secondaryTarget,
                        cardRect = cardRect,
                        topSafeInsetPx = vp.topInset,
                        bottomSafeInsetPx = vp.bottomInset,
                        padPx = pad
                    )
                    assertNotNull(secondaryRect, "${vp.name} ${step.indicatorText} missing secondary spotlight")
                    assertFalse(cardRect.overlaps(secondaryRect), "${vp.name} ${step.indicatorText} card overlapped secondary spotlight")
                }
            }
        }
    }
}
