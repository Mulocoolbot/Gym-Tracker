package com.mulocoolbot.gymtracker.blackbox

import androidx.compose.ui.geometry.Rect
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.mulocoolbot.gymtracker.data.auth.AuthEntryMode
import com.mulocoolbot.gymtracker.data.auth.SessionPreferences
import com.mulocoolbot.gymtracker.data.backup.GymBackupManager
import com.mulocoolbot.gymtracker.data.local.TestInMemoryGymDao
import com.mulocoolbot.gymtracker.data.local.UserProfileEntity
import com.mulocoolbot.gymtracker.data.local.WorkoutPlanEntity
import com.mulocoolbot.gymtracker.data.repository.GymRepository
import com.mulocoolbot.gymtracker.platform.AuthBridge
import com.mulocoolbot.gymtracker.platform.createTestPreferencesPath
import com.mulocoolbot.gymtracker.timer.RestTimerController
import com.mulocoolbot.gymtracker.ui.auth.AppStartDestination
import com.mulocoolbot.gymtracker.ui.auth.AuthOnboardingViewModel
import com.mulocoolbot.gymtracker.ui.navigation.BottomNavTab
import com.mulocoolbot.gymtracker.ui.plan.WorkoutPlanSubPage
import com.mulocoolbot.gymtracker.ui.plan.WorkoutPlanViewModel
import com.mulocoolbot.gymtracker.ui.theme.AppBackground
import com.mulocoolbot.gymtracker.ui.theme.CardBorder
import com.mulocoolbot.gymtracker.ui.theme.CardSurface
import com.mulocoolbot.gymtracker.ui.theme.TextMuted
import com.mulocoolbot.gymtracker.ui.theme.TextPrimary
import com.mulocoolbot.gymtracker.ui.theme.TutorialScrimFallback
import com.mulocoolbot.gymtracker.ui.theme.TutorialScrimWithBlur
import com.mulocoolbot.gymtracker.ui.theme.VoltAccent
import com.mulocoolbot.gymtracker.ui.tutorial.MFitTutorialSteps
import com.mulocoolbot.gymtracker.ui.tutorial.TutorialController
import com.mulocoolbot.gymtracker.ui.tutorial.TutorialTargetId
import com.mulocoolbot.gymtracker.ui.tutorial.computeSecondarySpotlightRect
import com.mulocoolbot.gymtracker.ui.tutorial.computeTutorialOverlayLayout
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
import okio.Path.Companion.toPath

@OptIn(ExperimentalCoroutinesApi::class)
class Sprint6BlackBoxTestSuite {

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
    fun tc_s6_01_trigger_new_user_sign_in_starts_tutorial_at_1_of_5() = runTest(testDispatcher) {
        val (_, repository) = createEnvironment(backgroundScope, "s6_01_new_email.preferences_pb")
        val authVm = AuthOnboardingViewModel(repository = repository, authBridge = offlineAuthBridge())

        authVm.onOpeningAnimationFinished()
        assertEquals(AppStartDestination.WELCOME, authVm.uiState.value.destination)

        authVm.onBeginFromWelcome()
        assertEquals(AppStartDestination.SIGN_IN, authVm.uiState.value.destination)
        assertEquals(AuthEntryMode.SIGN_IN, authVm.uiState.value.authEntryMode)

        authVm.requestEmailOtp("rookie.lifter@mfit.app", currentMillis = 10_000L)
        val otpCode = authVm.lastGeneratedOtpCode
        assertNotNull(otpCode)
        authVm.verifyEmailOtp(otpCode, currentMillis = 12_000L)

        assertEquals(AppStartDestination.PROFILE_SETUP, authVm.uiState.value.destination)
        assertTrue(authVm.uiState.value.authSession.shouldShowTutorial)
        assertFalse(authVm.uiState.value.authSession.hasCompletedTutorial)

        authVm.onSubmitProfile("Rookie Lifter", 946684800000L, "72.0", "178")
        assertEquals(AppStartDestination.MAIN_HOME, authVm.uiState.value.destination)

        val sessionAtMain = authVm.uiState.value.authSession
        assertTrue(sessionAtMain.shouldShowTutorial)
        assertFalse(sessionAtMain.hasCompletedTutorial)

        val controller = TutorialController()
        val uiState = controller.resolveUiState(sessionAtMain)
        assertTrue(uiState.isActive)
        assertEquals(0, uiState.stepIndex)
        assertEquals("1/10", uiState.currentStep.indicatorText)
        assertEquals(BottomNavTab.HOME, uiState.currentStep.tab)
        assertEquals(TutorialTargetId.HOME_STREAK, uiState.currentStep.targetId)

        val (_, googleRepo) = createEnvironment(backgroundScope, "s6_01_new_google.preferences_pb")
        val googleVm = AuthOnboardingViewModel(repository = googleRepo, authBridge = offlineAuthBridge())
        googleVm.onOpeningAnimationFinished()
        googleVm.onBeginFromWelcome()
        googleVm.onGoogleAuthenticated("rookie.google@mfit.app", "Rookie Google")
        googleVm.onSubmitProfile("Rookie Google", 946684800000L, "70.0", "175")
        assertEquals(AppStartDestination.MAIN_HOME, googleVm.uiState.value.destination)
        assertTrue(TutorialController().resolveUiState(googleVm.uiState.value.authSession).isActive)

        val (_, createAccRepo) = createEnvironment(backgroundScope, "s6_01_create_account.preferences_pb")
        val createAccVm = AuthOnboardingViewModel(repository = createAccRepo, authBridge = offlineAuthBridge())
        createAccVm.onOpeningAnimationFinished()
        createAccVm.onBeginFromWelcome()
        createAccVm.onCreateAccount("direct.signup@mfit.app", "strongPass99")
        createAccVm.onSubmitProfile("Direct Signup", 946684800000L, "75.0", "180")
        assertEquals(AppStartDestination.MAIN_HOME, createAccVm.uiState.value.destination)
        assertTrue(TutorialController().resolveUiState(createAccVm.uiState.value.authSession).isActive)
    }

    @Test
    fun tc_s6_02_bypass_existing_user_or_login_mode_never_shows_tutorial() = runTest(testDispatcher) {
        val (daoLogin, repoLogin) = createEnvironment(backgroundScope, "s6_02_login_email.preferences_pb")
        daoLogin.upsertUserProfile(
            UserProfileEntity(
                uid = SessionPreferences.computeUidForEmail("veteran@mfit.app"),
                name = "Veteran",
                birthDateMillis = 915148800000L,
                heightCm = 178.0,
                weightKg = 78.0
            )
        )
        val loginVm = AuthOnboardingViewModel(repository = repoLogin, authBridge = offlineAuthBridge())
        loginVm.onOpeningAnimationFinished()
        loginVm.onBeginFromWelcome()
        loginVm.setAuthEntryMode(AuthEntryMode.LOG_IN)
        loginVm.requestEmailOtp("veteran@mfit.app", currentMillis = 10_000L)
        loginVm.verifyEmailOtp(loginVm.lastGeneratedOtpCode!!, currentMillis = 12_000L)

        assertEquals(AppStartDestination.MAIN_HOME, loginVm.uiState.value.destination)
        assertFalse(loginVm.uiState.value.authSession.shouldShowTutorial)
        assertFalse(TutorialController().resolveUiState(loginVm.uiState.value.authSession).isActive)

        val (daoGoogleLogin, repoGoogleLogin) = createEnvironment(backgroundScope, "s6_02_login_google.preferences_pb")
        daoGoogleLogin.upsertUserProfile(
            UserProfileEntity(
                uid = SessionPreferences.computeUidForEmail("veteran.google@mfit.app"),
                name = "Veteran Google",
                birthDateMillis = 915148800000L,
                heightCm = 176.0,
                weightKg = 74.0
            )
        )
        val googleLoginVm = AuthOnboardingViewModel(repository = repoGoogleLogin, authBridge = offlineAuthBridge())
        googleLoginVm.onOpeningAnimationFinished()
        googleLoginVm.onBeginFromWelcome()
        googleLoginVm.setAuthEntryMode(AuthEntryMode.LOG_IN)
        googleLoginVm.onGoogleAuthenticated("veteran.google@mfit.app", "Veteran Google")
        assertEquals(AppStartDestination.MAIN_HOME, googleLoginVm.uiState.value.destination)
        assertFalse(googleLoginVm.uiState.value.authSession.shouldShowTutorial)
        assertFalse(TutorialController().resolveUiState(googleLoginVm.uiState.value.authSession).isActive)

        val (_, repoFreshGoogleLogin) = createEnvironment(backgroundScope, "s6_02_login_google_fresh.preferences_pb")
        val freshGoogleLoginVm =
            AuthOnboardingViewModel(repository = repoFreshGoogleLogin, authBridge = offlineAuthBridge())
        freshGoogleLoginVm.onOpeningAnimationFinished()
        freshGoogleLoginVm.onBeginFromWelcome()
        freshGoogleLoginVm.setAuthEntryMode(AuthEntryMode.LOG_IN)
        freshGoogleLoginVm.onGoogleAuthenticated("fresh.login@mfit.app", "Fresh Login")
        assertEquals(AppStartDestination.PROFILE_SETUP, freshGoogleLoginVm.uiState.value.destination)
        freshGoogleLoginVm.onSubmitProfile("Fresh Login", 946684800000L, "72.0", "175")
        assertEquals(AppStartDestination.MAIN_HOME, freshGoogleLoginVm.uiState.value.destination)
        assertFalse(freshGoogleLoginVm.uiState.value.authSession.shouldShowTutorial)
        assertFalse(TutorialController().resolveUiState(freshGoogleLoginVm.uiState.value.authSession).isActive)

        val (_, repoRegistered) = createEnvironment(backgroundScope, "s6_02_registered_key.preferences_pb")
        repoRegistered.signInWithEmail("already@mfit.app", AuthEntryMode.SIGN_IN)
        repoRegistered.completeOnboardingProfile("Already", 946684800000L, 70.0, 175.0)
        repoRegistered.completeTutorial()
        repoRegistered.unlinkEmail()
        repoRegistered.signInWithEmail("already@mfit.app", AuthEntryMode.SIGN_IN)
        assertFalse(repoRegistered.authSession.first().shouldShowTutorial)
        assertFalse(TutorialController().resolveUiState(repoRegistered.authSession.first()).isActive)

        val (_, repoOffline) = createEnvironment(backgroundScope, "s6_02_offline.preferences_pb")
        val offlineVm = AuthOnboardingViewModel(repository = repoOffline, authBridge = offlineAuthBridge())
        offlineVm.onOpeningAnimationFinished()
        offlineVm.onBeginFromWelcome()
        offlineVm.onContinueOffline()
        offlineVm.onSubmitProfile("Offline User", 946684800000L, "70.0", "175")
        assertEquals(AppStartDestination.MAIN_HOME, offlineVm.uiState.value.destination)
        assertFalse(offlineVm.uiState.value.authSession.shouldShowTutorial)

        val (backupDao, backupRepo) = createEnvironment(backgroundScope, "s6_02_backup.preferences_pb")
        backupDao.upsertUserProfile(
            UserProfileEntity(
                uid = "backup-uid",
                name = "Restored",
                birthDateMillis = 946684800000L,
                heightCm = 175.0,
                weightKg = 70.0
            )
        )
        val exported = GymBackupManager.exportToJson(backupDao)
        assertTrue(backupRepo.importBackupJson(exported))
        assertFalse(backupRepo.authSession.first().shouldShowTutorial)
    }

    @Test
    fun tc_s6_03_shows_only_once_persists_across_datastore_reopen() = runTest(testDispatcher) {
        listOf(true, false).forEach { finishViaDone ->
            val prefsPath = createTestPreferencesPath("s6_03_reopen_${finishViaDone}.preferences_pb")
            val sharedDao = TestInMemoryGymDao()

            withContext(Dispatchers.IO) {
                val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                val dataStore1 = PreferenceDataStoreFactory.createWithPath(
                    scope = scope1,
                    produceFile = { prefsPath.toPath() }
                )
                val repo1 = GymRepository(dao = sharedDao, sessionPreferences = SessionPreferences(dataStore1))
                repo1.signInWithEmail("once@mfit.app", AuthEntryMode.SIGN_IN)
                repo1.completeOnboardingProfile("Once User", 946684800000L, 73.0, 177.0)

                val sessionBefore = repo1.authSession.first()
                assertTrue(sessionBefore.shouldShowTutorial)
                assertFalse(sessionBefore.hasCompletedTutorial)

                val controller1 = TutorialController()
                if (finishViaDone) {
                    repeat(9) { controller1.onNext() }
                    controller1.onDone(
                        onPersistCompleted = {
                            kotlinx.coroutines.runBlocking { repo1.completeTutorial() }
                        },
                        onNavigateHome = {}
                    )
                } else {
                    controller1.onSkip(
                        onPersistCompleted = {
                            kotlinx.coroutines.runBlocking { repo1.completeTutorial() }
                        },
                        onNavigateHome = {}
                    )
                }

                val sessionAfterDismiss = repo1.authSession.first()
                assertFalse(sessionAfterDismiss.shouldShowTutorial)
                assertTrue(sessionAfterDismiss.hasCompletedTutorial)
                scope1.coroutineContext[Job]!!.cancelAndJoin()

                val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                val dataStore2 = PreferenceDataStoreFactory.createWithPath(
                    scope = scope2,
                    produceFile = { prefsPath.toPath() }
                )
                val repo2 = GymRepository(dao = sharedDao, sessionPreferences = SessionPreferences(dataStore2))
                val reopenedSession = repo2.authSession.first()
                assertEquals(
                    AppStartDestination.MAIN_HOME,
                    AuthOnboardingViewModel.resolveDestination(reopenedSession)
                )
                assertFalse(reopenedSession.shouldShowTutorial)
                assertTrue(reopenedSession.hasCompletedTutorial)

                val controller2 = TutorialController()
                assertFalse(controller2.resolveUiState(reopenedSession).isActive)

                repo2.signInWithGoogle("second@mfit.app", AuthEntryMode.SIGN_IN)
                assertFalse(repo2.authSession.first().shouldShowTutorial)
                assertFalse(controller2.resolveUiState(repo2.authSession.first()).isActive)

                scope2.coroutineContext[Job]!!.cancelAndJoin()
            }
        }
    }

    @Test
    fun tc_s6_04_five_step_sequence_auto_tab_switch_and_read_only_isolation() = runTest(testDispatcher) {
        val (_, repository) = createEnvironment(backgroundScope, "s6_04_sequence.preferences_pb")
        repository.signInWithEmail("stepper@mfit.app", AuthEntryMode.SIGN_IN)
        repository.completeOnboardingProfile("Stepper", 946684800000L, 74.0, 178.0)

        val planVm = WorkoutPlanViewModel(repository)
        val timerController = RestTimerController()
        val workoutVm = WorkoutViewModel(repository = repository, timerController = timerController)

        val controller = TutorialController()
        val session = repository.authSession.first()
        var currentTab = controller.resolveUiState(session).currentStep.tab

        val observedSteps = mutableListOf<String>()
        val observedTabs = mutableListOf<BottomNavTab>()
        val observedTargets = mutableListOf<TutorialTargetId>()

        fun recordCurrent() {
            val ui = controller.resolveUiState(session)
            observedSteps.add(ui.currentStep.indicatorText)
            observedTabs.add(currentTab)
            observedTargets.add(ui.currentStep.targetId)
        }

        recordCurrent()
        repeat(9) {
            currentTab = controller.onNext()
            recordCurrent()
        }

        assertEquals((1..10).map { "$it/10" }, observedSteps)
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
            observedTabs
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
            observedTargets
        )

        assertEquals(BottomNavTab.STATS, controller.onNext())
        assertEquals("10/10", controller.resolveUiState(session).currentStep.indicatorText)

        assertEquals(WorkoutPlanSubPage.EMPTY, planVm.uiState.value.subPage)
        assertFalse(workoutVm.uiState.value.isWorkoutActive)
        assertTrue(workoutVm.uiState.value.activeSessionSets.isEmpty())
        assertFalse(timerController.state.value.isRunning)
    }

    @Test
    fun tc_s6_05_visual_tokens_blur_fallback_connector_and_multi_device_bounds() {
        assertEquals(0xFF0B0C0EUL, AppBackground.value shr 32)
        assertEquals(0xFF13151BUL, CardSurface.value shr 32)
        assertEquals(0xFF222530UL, CardBorder.value shr 32)
        assertEquals(0xFFD4FF00UL, VoltAccent.value shr 32)
        assertEquals(0xFFF5F7FAUL, TextPrimary.value shr 32)
        assertEquals(0xFF8E95A5UL, TextMuted.value shr 32)
        assertTrue(TutorialScrimFallback.alpha > TutorialScrimWithBlur.alpha)

        assertEquals(10, MFitTutorialSteps.size)
        MFitTutorialSteps.forEachIndexed { idx, step ->
            assertEquals("${idx + 1}/10", step.indicatorText)
            assertTrue(step.title.isNotBlank())
            assertTrue(step.description.isNotBlank())
            assertFalse(step.title.any { it.code > 0x2600 })
            assertFalse(step.description.any { it.code > 0x2600 })
        }

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
                TutorialTargetId.HOME_STREAK to Rect(
                    20f,
                    contentTop + 148f,
                    (vp.width - 54f) / 2f + 20f,
                    contentTop + 312f
                ),
                TutorialTargetId.HOME_HERO_PLAN to Rect(
                    20f,
                    contentTop + 332f,
                    vp.width - 20f,
                    (contentTop + 530f).coerceAtMost(contentBottom)
                ),
                TutorialTargetId.PLAN_CREATE_AREA to Rect(
                    52f,
                    (contentTop + contentBottom) / 2f + 30f,
                    vp.width - 52f,
                    (contentTop + contentBottom) / 2f + 82f
                ),
                TutorialTargetId.PLAN_SELECT_DAYS_AREA to Rect(
                    20f,
                    contentTop + 110f,
                    vp.width - 20f,
                    (contentTop + 340f).coerceAtMost(contentBottom - 200f)
                ),
                TutorialTargetId.PLAN_INPUT_AREA to Rect(
                    20f,
                    contentTop + 110f,
                    vp.width - 20f,
                    (contentTop + 275f).coerceAtMost(contentBottom - 200f)
                ),
                TutorialTargetId.PLAN_SAVE_BUTTON to Rect(
                    vp.width - 108f,
                    contentTop + 4f,
                    vp.width - 20f,
                    contentTop + 48f
                ),
                TutorialTargetId.WORKOUT_START_AREA to Rect(
                    52f,
                    (contentTop + contentBottom) / 2f + 30f,
                    vp.width - 52f,
                    (contentTop + contentBottom) / 2f + 84f
                ),
                TutorialTargetId.WORKOUT_EXERCISE_MUSCLE_AREA to Rect(
                    40f,
                    contentTop + 80f,
                    vp.width - 40f,
                    contentTop + 215f
                ),
                TutorialTargetId.WORKOUT_SET_ENTRY_AREA to Rect(
                    40f,
                    contentTop + 225f,
                    vp.width - 40f,
                    (contentTop + 425f).coerceAtMost(contentBottom)
                ),
                TutorialTargetId.WORKOUT_REST_TIMER_AREA to Rect(
                    20f,
                    (contentBottom - 120f).coerceAtLeast(contentTop + 220f),
                    vp.width - 20f,
                    contentBottom - 12f
                ),
                TutorialTargetId.WORKOUT_FINISH_BUTTON to Rect(
                    vp.width - 116f,
                    contentTop + 4f,
                    vp.width - 20f,
                    contentTop + 48f
                ),
                TutorialTargetId.STATS_OVERVIEW_AREA to Rect(
                    20f,
                    contentTop + 56f,
                    vp.width - 20f,
                    (contentTop + 330f).coerceAtMost(contentBottom - 190f)
                )
            )

            MFitTutorialSteps.forEach { step ->
                val layout = computeTutorialOverlayLayout(
                    containerWidth = vp.width,
                    containerHeight = vp.height,
                    targetRect = targetRects.getValue(step.targetId),
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
                assertTrue(layout.spotlightRect.left >= 0f && layout.spotlightRect.right <= vp.width)
                assertTrue(layout.spotlightRect.top >= 0f && layout.spotlightRect.bottom <= vp.height)
                assertTrue(layout.cardTopPx >= vp.topInset + edgeMargin - 0.5f, "${vp.name} card clipped top")
                assertTrue(
                    layout.cardTopPx + cardHeight <= vp.height - vp.bottomInset - edgeMargin + 0.5f,
                    "${vp.name} card clipped bottom"
                )
                assertTrue(layout.cardLeftPx >= 0f && layout.cardRightPx <= vp.width)

                val cardRect =
                    Rect(layout.cardLeftPx, layout.cardTopPx, layout.cardRightPx, layout.cardTopPx + cardHeight)
                assertFalse(
                    cardRect.overlaps(layout.spotlightRect),
                    "${vp.name} step ${step.indicatorText}: card $cardRect overlapped spotlight ${layout.spotlightRect}"
                )

                step.secondaryTargetId?.let { secondaryId ->
                    val secondaryRect = computeSecondarySpotlightRect(
                        containerWidth = vp.width,
                        containerHeight = vp.height,
                        targetRect = targetRects.getValue(secondaryId),
                        cardRect = cardRect,
                        topSafeInsetPx = vp.topInset,
                        bottomSafeInsetPx = vp.bottomInset,
                        padPx = pad
                    )
                    assertTrue(
                        secondaryRect != null && !cardRect.overlaps(secondaryRect),
                        "${vp.name} step ${step.indicatorText} secondary spotlight missing or overlapping"
                    )
                }

                assertTrue(layout.showConnector, "${vp.name} step ${step.indicatorText} connector hidden")
                if (layout.placeCardBelow) {
                    assertTrue(layout.cardAnchor.y > layout.ringAnchor.y)
                } else {
                    assertTrue(layout.ringAnchor.y > layout.cardAnchor.y)
                }
            }
        }
    }

    @Test
    fun tc_s6_06_skip_button_from_step_2_and_step_3_closes_and_navigates_home() = runTest(testDispatcher) {
        listOf(1, 2).forEach { stepsToAdvance ->
            val (_, repository) = createEnvironment(backgroundScope, "s6_06_skip_${stepsToAdvance}.preferences_pb")
            repository.signInWithEmail("skipper@mfit.app", AuthEntryMode.SIGN_IN)
            repository.completeOnboardingProfile("Skipper", 946684800000L, 71.0, 174.0)

            val authVm = AuthOnboardingViewModel(repository = repository, authBridge = offlineAuthBridge())
            val controller = TutorialController()
            var activeTab = controller.resolveUiState(authVm.uiState.value.authSession).currentStep.tab

            repeat(stepsToAdvance) {
                activeTab = controller.onNext()
            }

            if (stepsToAdvance == 1) {
                assertEquals(
                    "2/10",
                    controller.resolveUiState(authVm.uiState.value.authSession).currentStep.indicatorText
                )
                assertEquals(BottomNavTab.HOME, activeTab)
            } else {
                assertEquals(
                    "3/10",
                    controller.resolveUiState(authVm.uiState.value.authSession).currentStep.indicatorText
                )
                assertEquals(BottomNavTab.PLAN, activeTab)
            }

            controller.onSkip(
                onPersistCompleted = authVm::onCompleteTutorial,
                onNavigateHome = { activeTab = BottomNavTab.HOME }
            )

            assertEquals(BottomNavTab.HOME, activeTab)
            assertFalse(controller.resolveUiState(authVm.uiState.value.authSession).isActive)
            assertFalse(authVm.uiState.value.authSession.shouldShowTutorial)
            assertTrue(authVm.uiState.value.authSession.hasCompletedTutorial)
        }
    }

    @Test
    fun tc_s6_07_last_step_hides_skip_and_next_shows_only_done_and_navigates_home() = runTest(testDispatcher) {
        val (_, repository) = createEnvironment(backgroundScope, "s6_07_done.preferences_pb")
        repository.signInWithEmail("finisher@mfit.app", AuthEntryMode.SIGN_IN)
        repository.completeOnboardingProfile("Finisher", 946684800000L, 76.0, 181.0)

        val authVm = AuthOnboardingViewModel(repository = repository, authBridge = offlineAuthBridge())
        val controller = TutorialController()
        var activeTab = controller.resolveUiState(authVm.uiState.value.authSession).currentStep.tab

        repeat(9) { stepIdx ->
            val ui = controller.resolveUiState(authVm.uiState.value.authSession)
            assertEquals("${stepIdx + 1}/10", ui.currentStep.indicatorText)
            assertTrue(ui.showSkipButton)
            assertTrue(ui.showNextButton)
            assertFalse(ui.showDoneButton)
            activeTab = controller.onNext()
        }

        val lastUi = controller.resolveUiState(authVm.uiState.value.authSession)
        assertEquals("10/10", lastUi.currentStep.indicatorText)
        assertEquals(BottomNavTab.STATS, activeTab)
        assertFalse(lastUi.showSkipButton)
        assertFalse(lastUi.showNextButton)
        assertTrue(lastUi.showDoneButton)

        controller.onDone(
            onPersistCompleted = authVm::onCompleteTutorial,
            onNavigateHome = { activeTab = BottomNavTab.HOME }
        )

        assertEquals(BottomNavTab.HOME, activeTab)
        assertFalse(controller.resolveUiState(authVm.uiState.value.authSession).isActive)
        assertFalse(authVm.uiState.value.authSession.shouldShowTutorial)
        assertTrue(authVm.uiState.value.authSession.hasCompletedTutorial)
    }
}
