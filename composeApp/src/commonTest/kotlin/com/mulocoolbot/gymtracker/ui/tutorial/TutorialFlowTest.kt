package com.mulocoolbot.gymtracker.ui.tutorial

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.mulocoolbot.gymtracker.data.auth.AuthEntryMode
import com.mulocoolbot.gymtracker.data.auth.SessionPreferences
import com.mulocoolbot.gymtracker.data.local.TestInMemoryGymDao
import com.mulocoolbot.gymtracker.data.local.UserProfileEntity
import com.mulocoolbot.gymtracker.data.repository.GymRepository
import com.mulocoolbot.gymtracker.platform.AuthBridge
import com.mulocoolbot.gymtracker.platform.createTestPreferencesPath
import com.mulocoolbot.gymtracker.ui.auth.AuthOnboardingViewModel
import com.mulocoolbot.gymtracker.ui.navigation.BottomNavTab
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun tutorialSteps_coverAllFiveStepsInExactOrder() {
        assertEquals(5, MFitTutorialSteps.size)
        assertEquals(
            listOf(
                BottomNavTab.HOME,
                BottomNavTab.HOME,
                BottomNavTab.PLAN,
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
                TutorialTargetId.WORKOUT_START_AREA,
                TutorialTargetId.STATS_OVERVIEW_AREA
            ),
            MFitTutorialSteps.map { it.targetId }
        )
        assertEquals(listOf("1/5", "2/5", "3/5", "4/5", "5/5"), MFitTutorialSteps.map { it.indicatorText })
        assertEquals(listOf(false, false, false, false, true), MFitTutorialSteps.map { it.isLastStep })
    }
}
