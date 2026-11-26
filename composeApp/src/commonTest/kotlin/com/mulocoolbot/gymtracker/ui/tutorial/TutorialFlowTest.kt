package com.mulocoolbot.gymtracker.ui.tutorial

import androidx.compose.ui.geometry.Rect
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.mulocoolbot.gymtracker.data.auth.AuthEntryMode
import com.mulocoolbot.gymtracker.data.auth.SessionPreferences
import com.mulocoolbot.gymtracker.data.local.TestInMemoryGymDao
import com.mulocoolbot.gymtracker.data.local.UserProfileEntity
import com.mulocoolbot.gymtracker.data.repository.GymRepository
import com.mulocoolbot.gymtracker.platform.AuthBridge
import com.mulocoolbot.gymtracker.platform.createTestPreferencesPath
import com.mulocoolbot.gymtracker.ui.auth.AuthOnboardingViewModel
import com.mulocoolbot.gymtracker.ui.home.HomeViewModel
import com.mulocoolbot.gymtracker.ui.navigation.BottomNavTab
import com.mulocoolbot.gymtracker.ui.plan.WorkoutPlanSubPage
import com.mulocoolbot.gymtracker.ui.plan.WorkoutPlanViewModel
import com.mulocoolbot.gymtracker.ui.stats.StatsViewModel
import com.mulocoolbot.gymtracker.ui.workout.WorkoutUiState
import com.mulocoolbot.gymtracker.ui.workout.WorkoutViewModel
import com.mulocoolbot.gymtracker.timer.RestTimerController
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
class TutorialFlowTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
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
    fun newSignInUser_activatesTutorial_andPersistsCompletionAcrossReopen() = runTest(testDispatcher) {
        val prefsPath = createTestPreferencesPath("tutorial_new_user.preferences_pb")
        val dataStore1 = PreferenceDataStoreFactory.createWithPath(
            scope = backgroundScope,
            produceFile = { prefsPath.toPath() }
        )
        val dao = TestInMemoryGymDao()
        val repo1 = GymRepository(dao = dao, sessionPreferences = SessionPreferences(dataStore1))
        val vm = AuthOnboardingViewModel(
            repository = repo1,
            authBridge = object : AuthBridge {
                override val isGoogleSignInSupported: Boolean = true
                override val isCloudAuthConfigured: Boolean = true
                override fun syncEmailCredentials(email: String, password: String, isRegister: Boolean) = Unit
                override suspend fun deliverOtp(email: String, otpCode: String) = Unit
            }
        )

        vm.setAuthEntryMode(AuthEntryMode.SIGN_IN)
        vm.onEmailSignIn("newuser@mfit.app")
        val afterSignIn = repo1.authSession.first()
        assertTrue(afterSignIn.shouldShowTutorial)
        assertFalse(afterSignIn.hasCompletedTutorial)

        vm.onSubmitProfile("New Athlete", 946684800000L, "72.0", "178")
        val afterProfile = repo1.authSession.first()
        assertTrue(afterProfile.isProfileCompleted)
        assertTrue(afterProfile.shouldShowTutorial)
        assertFalse(afterProfile.hasCompletedTutorial)

        vm.onCompleteTutorial()
        val afterComplete = repo1.authSession.first()
        assertFalse(afterComplete.shouldShowTutorial)
        assertTrue(afterComplete.hasCompletedTutorial)

        repo1.signInWithEmail("newuser@mfit.app", AuthEntryMode.SIGN_IN)
        val afterResignIn = repo1.authSession.first()
        assertFalse(afterResignIn.shouldShowTutorial)
        assertTrue(afterResignIn.hasCompletedTutorial)
    }

    @Test
    fun newGoogleSignInUser_activatesTutorialOnce() = runTest(testDispatcher) {
        val (_, repository) = createEnvironment(backgroundScope, "tutorial_google_new.preferences_pb")

        repository.signInWithGoogle("fresh.google@mfit.app", AuthEntryMode.SIGN_IN)
        val session = repository.authSession.first()
        assertTrue(session.shouldShowTutorial)
        assertFalse(session.hasCompletedTutorial)

        repository.completeTutorial()
        val completed = repository.authSession.first()
        assertFalse(completed.shouldShowTutorial)
        assertTrue(completed.hasCompletedTutorial)
    }

    @Test
    fun existingOrLoginOrOfflineUser_neverActivatesTutorial() = runTest(testDispatcher) {
        val (daoLogin, repoLogin) = createEnvironment(backgroundScope, "tutorial_login_only.preferences_pb")
        daoLogin.upsertUserProfile(
            UserProfileEntity(
                uid = SessionPreferences.computeUidForEmail("existing@mfit.app"),
                name = "Existing",
                birthDateMillis = 946684800000L,
                heightCm = 175.0,
                weightKg = 70.0
            )
        )
        repoLogin.signInWithEmail("existing@mfit.app", AuthEntryMode.LOG_IN)
        assertFalse(repoLogin.authSession.first().shouldShowTutorial)

        val (_, repoGoogleLogin) = createEnvironment(backgroundScope, "tutorial_google_login.preferences_pb")
        repoGoogleLogin.signInWithGoogle("returning@mfit.app", AuthEntryMode.LOG_IN)
        assertFalse(repoGoogleLogin.authSession.first().shouldShowTutorial)

        val (_, repoOffline) = createEnvironment(backgroundScope, "tutorial_offline.preferences_pb")
        repoOffline.continueOffline()
        assertFalse(repoOffline.authSession.first().shouldShowTutorial)

        val (_, repoRegistered) = createEnvironment(backgroundScope, "tutorial_already_registered.preferences_pb")
        repoRegistered.signInWithEmail("first@mfit.app", AuthEntryMode.SIGN_IN)
        assertTrue(repoRegistered.authSession.first().shouldShowTutorial)
        repoRegistered.completeTutorial()
        repoRegistered.signInWithGoogle("linked.google@mfit.app", AuthEntryMode.SIGN_IN)
        assertFalse(repoRegistered.authSession.first().shouldShowTutorial)
    }

    @Test
    fun tutorialSteps_coverAllTenStepsInExactOrder() {
        assertEquals(10, MFitTutorialSteps.size)
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
            MFitTutorialSteps.map { it.tab }
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
            MFitTutorialSteps.map { it.targetId }
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
            MFitTutorialSteps.map { it.secondaryTargetId }
        )
        assertEquals(
            (1..10).map { "$it/10" },
            MFitTutorialSteps.map { it.indicatorText }
        )
        assertEquals(
            listOf(false, false, false, false, false, false, false, false, false, true),
            MFitTutorialSteps.map { it.isLastStep }
        )
        MFitTutorialSteps.forEach { step ->
            assertFalse(step.title.any { it.code > 0x2600 })
            assertFalse(step.description.any { it.code > 0x2600 })
        }
    }

    @Test
    fun tutorialPreviewData_providesEphemeralStepPreviews_andClearsImmediatelyOnSkipOrDone() = runTest(testDispatcher) {
        listOf(5, 9, 10).forEach { exitAtStep ->
            val (_, repository) = createEnvironment(
                backgroundScope,
                "tutorial_preview_exit_${exitAtStep}.preferences_pb"
            )
            repository.signInWithEmail("preview_${exitAtStep}@mfit.app", AuthEntryMode.SIGN_IN)
            repository.completeOnboardingProfile("Preview User", 946684800000L, 70.0, 175.0)

            val homeVm = HomeViewModel(repository)
            val planVm = WorkoutPlanViewModel(repository)
            val timerController = RestTimerController()
            val workoutVm = WorkoutViewModel(repository = repository, timerController = timerController)
            val statsVm = StatsViewModel(repository)
            val controller = TutorialController()
            val session = repository.authSession.first()

            fun currentUi() = controller.resolveUiState(session)

            val step1Ui = currentUi()
            assertTrue(step1Ui.isActive)
            val previewHome1 = TutorialPreviewData.resolveHomeUiState(homeVm.uiState.value, step1Ui)
            assertEquals(5, previewHome1.currentStreak)
            assertEquals("Push Day", previewHome1.selectedDayPlan.planName)
            assertFalse(previewHome1.selectedDayPlan.isRestDay)

            controller.onNext()
            val step2Ui = currentUi()
            val previewHome2 = TutorialPreviewData.resolveHomeUiState(homeVm.uiState.value, step2Ui)
            assertEquals("Push Day", previewHome2.selectedDayPlan.planName)

            controller.onNext()
            val previewPlan3 = TutorialPreviewData.resolveWorkoutPlanUiState(planVm.uiState.value, currentUi())
            assertEquals(WorkoutPlanSubPage.EMPTY, previewPlan3.subPage)

            controller.onNext()
            val previewPlan4 = TutorialPreviewData.resolveWorkoutPlanUiState(planVm.uiState.value, currentUi())
            assertEquals(WorkoutPlanSubPage.SELECT_DAYS, previewPlan4.subPage)
            assertEquals(setOf(1, 3, 5), previewPlan4.selectedDays)

            controller.onNext()
            val previewPlan5 = TutorialPreviewData.resolveWorkoutPlanUiState(planVm.uiState.value, currentUi())
            assertEquals(WorkoutPlanSubPage.INPUT_PLAN, previewPlan5.subPage)
            assertEquals("Push Day", previewPlan5.draftPlanNames[1])
            assertEquals("Pull Day", previewPlan5.draftPlanNames[3])
            assertEquals("Legs", previewPlan5.draftPlanNames[5])

            if (exitAtStep >= 6) {
                controller.onNext()
                val previewWorkout6 = TutorialPreviewData.resolveWorkoutUiState(workoutVm.uiState.value, currentUi())
                assertFalse(previewWorkout6.isWorkoutActive)
                assertEquals("Push Day", previewWorkout6.todayPlanName)

                controller.onNext()
                val previewWorkout7 = TutorialPreviewData.resolveWorkoutUiState(workoutVm.uiState.value, currentUi())
                assertTrue(previewWorkout7.isWorkoutActive)
                assertEquals("Bench Press", previewWorkout7.exerciseInput)
                assertEquals("CHEST", previewWorkout7.selectedMuscleGroup)

                controller.onNext()
                val previewWorkout8 = TutorialPreviewData.resolveWorkoutUiState(workoutVm.uiState.value, currentUi())
                assertTrue(previewWorkout8.isWorkoutActive)
                assertEquals("60", previewWorkout8.weightInput)
                assertEquals("10", previewWorkout8.repsInput)
                assertEquals(WorkoutUiState.SET_TYPE_NORMAL, previewWorkout8.selectedSetType)
                assertEquals(2, previewWorkout8.activeSessionSets.size)

                controller.onNext()
                val previewWorkout9 = TutorialPreviewData.resolveWorkoutUiState(workoutVm.uiState.value, currentUi())
                assertTrue(previewWorkout9.restTimerState.isRunning)
                assertEquals(105, previewWorkout9.restTimerState.remainingSeconds)
                assertEquals("01:45", RestTimerController.formatMMSS(previewWorkout9.restTimerState.remainingSeconds))
                assertFalse(timerController.state.value.isRunning)
            }

            if (exitAtStep == 10) {
                controller.onNext()
                val previewStats10 = TutorialPreviewData.resolveStatsUiState(statsVm.uiState.value, currentUi())
                assertTrue(previewStats10.hasStatisticsData)
                assertEquals("Bench Press", previewStats10.selectedExerciseName)
                assertEquals("CHEST", previewStats10.selectedMuscleId)
                assertTrue((previewStats10.muscleVolumes["CHEST"]?.weeklySets ?: 0) >= 8)
            }

            var navigatedTab = currentUi().currentStep.tab
            if (exitAtStep == 10) {
                controller.onDone(
                    onPersistCompleted = {},
                    onNavigateHome = { navigatedTab = BottomNavTab.HOME }
                )
            } else {
                controller.onSkip(
                    onPersistCompleted = {},
                    onNavigateHome = { navigatedTab = BottomNavTab.HOME }
                )
            }

            assertEquals(BottomNavTab.HOME, navigatedTab)
            val dismissedUi = controller.resolveUiState(session)
            assertFalse(dismissedUi.isActive)

            val restoredHome = TutorialPreviewData.resolveHomeUiState(homeVm.uiState.value, dismissedUi)
            val restoredPlan = TutorialPreviewData.resolveWorkoutPlanUiState(planVm.uiState.value, dismissedUi)
            val restoredWorkout = TutorialPreviewData.resolveWorkoutUiState(workoutVm.uiState.value, dismissedUi)
            val restoredStats = TutorialPreviewData.resolveStatsUiState(statsVm.uiState.value, dismissedUi)

            assertEquals(0, restoredHome.currentStreak)
            assertTrue(restoredHome.selectedDayPlan.isRestDay)
            assertEquals(WorkoutPlanSubPage.EMPTY, restoredPlan.subPage)
            assertFalse(restoredWorkout.isWorkoutActive)
            assertTrue(restoredWorkout.activeSessionSets.isEmpty())
            assertFalse(restoredStats.hasStatisticsData)

            assertTrue(repository.weeklyPlan.first().all { it.isRestDay })
            assertTrue(repository.observeAllSets().first().isEmpty())
            assertTrue(repository.exerciseHistory.first().isEmpty())
            assertFalse(timerController.state.value.isRunning)
        }
    }

    @Test
    fun computeSecondarySpotlightRect_highlightsTopRightActionsWithoutOverlappingCard() {
        val cardRect = Rect(20f, 340f, 373f, 520f)
        val topRightButton = Rect(280f, 70f, 373f, 114f)
        val valid = computeSecondarySpotlightRect(
            containerWidth = 393f,
            containerHeight = 852f,
            targetRect = topRightButton,
            cardRect = cardRect,
            topSafeInsetPx = 59f,
            bottomSafeInsetPx = 34f,
            padPx = 6f
        )
        assertNotNull(valid)
        assertFalse(valid.overlaps(cardRect))

        val overlappingTarget = Rect(100f, 360f, 200f, 410f)
        val ignored = computeSecondarySpotlightRect(
            containerWidth = 393f,
            containerHeight = 852f,
            targetRect = overlappingTarget,
            cardRect = cardRect,
            topSafeInsetPx = 59f,
            bottomSafeInsetPx = 34f,
            padPx = 6f
        )
        assertNull(ignored)
    }

    @Test
    fun tutorialOverlayLayout_neverOverlapsSpotlight_onIphoneSeAndDynamicIsland() {
        val scenarios = listOf(
            // iPhone SE (compact screen) with tall mid-screen target and tall card
            Triple("iPhone SE tall mid target", floatArrayOf(375f, 667f, 20f, 0f), Rect(40f, 220f, 335f, 430f)),
            // iPhone 15/16 Pro Dynamic Island with top-edge target
            Triple("Dynamic Island top target", floatArrayOf(393f, 852f, 59f, 34f), Rect(20f, 30f, 373f, 210f)),
            // iPhone 15/16 Pro Dynamic Island with bottom target near home indicator
            Triple("Dynamic Island bottom target", floatArrayOf(393f, 852f, 59f, 34f), Rect(20f, 620f, 373f, 780f))
        )

        scenarios.forEach { (label, metrics, target) ->
            val width = metrics[0]
            val height = metrics[1]
            val topInset = metrics[2]
            val bottomInset = metrics[3]
            val cardHeight = 230f

            val layout = computeTutorialOverlayLayout(
                containerWidth = width,
                containerHeight = height,
                targetRect = target,
                cardHeightPx = cardHeight,
                topSafeInsetPx = topInset,
                bottomSafeInsetPx = bottomInset,
                padPx = 6f,
                maxCornerPx = 28f,
                connectorGapPx = 20f,
                edgeMarginPx = 14f,
                cardHorizontalMarginPx = 20f,
                cardAnchorInsetPx = 28f,
                minConnectorGapPx = 4f
            )

            assertTrue(layout.hasSpotlight, "$label missing spotlight")
            assertTrue(layout.spotlightRect.top >= topInset, "$label spotlight clipped into safe area")
            assertTrue(layout.cardTopPx >= topInset + 14f - 0.5f, "$label card clipped top")
            assertTrue(layout.cardTopPx + cardHeight <= height - bottomInset - 14f + 0.5f, "$label card clipped bottom")

            val cardRect = Rect(layout.cardLeftPx, layout.cardTopPx, layout.cardRightPx, layout.cardTopPx + cardHeight)
            assertFalse(
                cardRect.overlaps(layout.spotlightRect),
                "$label card $cardRect overlapped spotlight ${layout.spotlightRect}"
            )
        }
    }
}
