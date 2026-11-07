package com.mulocoolbot.gymtracker.blackbox

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.mulocoolbot.gymtracker.data.auth.AuthEntryMode
import com.mulocoolbot.gymtracker.data.auth.SessionPreferences
import com.mulocoolbot.gymtracker.data.repository.GymRepository
import com.mulocoolbot.gymtracker.ui.InMemoryTestGymDao
import com.mulocoolbot.gymtracker.ui.auth.AppStartDestination
import com.mulocoolbot.gymtracker.ui.auth.AuthOnboardingViewModel
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class OpeningIntroRoutingBlackBoxTest {

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

    private fun createRepository(fileName: String): GymRepository {
        val scope = CoroutineScope(testDispatcher + SupervisorJob())
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(tempFolder.newFolder(), fileName)
        }
        return GymRepository(
            dao = InMemoryTestGymDao(),
            sessionPreferences = SessionPreferences(dataStore)
        )
    }

    @Test
    fun tc_opn_01_app_launch_always_starts_with_opening_animation_playing() = runTest(testDispatcher) {
        val unauthenticatedRepo = createRepository("opn_01_unauth.preferences_pb")
        val unauthenticatedVm = AuthOnboardingViewModel(unauthenticatedRepo)
        assertTrue(unauthenticatedVm.uiState.value.isOpeningPlaying)

        val authenticatedRepo = createRepository("opn_01_auth.preferences_pb")
        authenticatedRepo.signInWithEmail("alex@mfit.app", AuthEntryMode.SIGN_IN)
        authenticatedRepo.completeOnboardingProfile(
            name = "Alex",
            birthDateMillis = 946684800000L,
            weightKg = 75.0,
            heightCm = 180.0
        )

        val authenticatedVm = AuthOnboardingViewModel(authenticatedRepo)
        assertTrue(authenticatedVm.uiState.value.isOpeningPlaying)
    }

    @Test
    fun tc_opn_02_fresh_install_or_reinstall_routes_to_welcome_begin_page_after_opening() = runTest(testDispatcher) {
        val repository = createRepository("opn_02_fresh.preferences_pb")
        val viewModel = AuthOnboardingViewModel(repository)

        assertTrue(viewModel.uiState.value.isOpeningPlaying)
        assertFalse(viewModel.uiState.value.authSession.isGoogleLinked)
        assertFalse(viewModel.uiState.value.authSession.isEmailLinked)

        viewModel.onOpeningAnimationFinished()
        assertFalse(viewModel.uiState.value.isOpeningPlaying)
        assertEquals(AppStartDestination.WELCOME, viewModel.uiState.value.destination)

        viewModel.onBeginFromWelcome()
        assertEquals(AppStartDestination.SIGN_IN, viewModel.uiState.value.destination)
    }

    @Test
    fun tc_opn_03_logged_in_user_routes_directly_to_home_page_after_opening() = runTest(testDispatcher) {
        val repository = createRepository("opn_03_logged_in.preferences_pb")
        repository.signInWithEmail("alex@mfit.app", AuthEntryMode.SIGN_IN)
        repository.completeOnboardingProfile(
            name = "Alex",
            birthDateMillis = 946684800000L,
            weightKg = 74.0,
            heightCm = 178.0
        )

        val viewModel = AuthOnboardingViewModel(repository)
        assertTrue(viewModel.uiState.value.isOpeningPlaying)

        viewModel.onOpeningAnimationFinished()
        assertFalse(viewModel.uiState.value.isOpeningPlaying)
        assertNotEquals(AppStartDestination.WELCOME, viewModel.uiState.value.destination)
        assertNotEquals(AppStartDestination.SIGN_IN, viewModel.uiState.value.destination)
        assertEquals(AppStartDestination.MAIN_HOME, viewModel.uiState.value.destination)
    }

    @Test
    fun tc_opn_04_relaunching_app_replays_opening_and_preserves_destination() = runTest(testDispatcher) {
        val repository = createRepository("opn_04_relaunch.preferences_pb")
        repository.signInWithEmail("alex@mfit.app", AuthEntryMode.SIGN_IN)
        repository.completeOnboardingProfile(
            name = "Alex",
            birthDateMillis = 946684800000L,
            weightKg = 74.0,
            heightCm = 178.0
        )

        val firstLaunchVm = AuthOnboardingViewModel(repository)
        assertTrue(firstLaunchVm.uiState.value.isOpeningPlaying)
        firstLaunchVm.onOpeningAnimationFinished()
        assertFalse(firstLaunchVm.uiState.value.isOpeningPlaying)
        assertEquals(AppStartDestination.MAIN_HOME, firstLaunchVm.uiState.value.destination)

        val secondLaunchVm = AuthOnboardingViewModel(repository)
        assertTrue(secondLaunchVm.uiState.value.isOpeningPlaying)
        secondLaunchVm.onOpeningAnimationFinished()
        assertFalse(secondLaunchVm.uiState.value.isOpeningPlaying)
        assertEquals(AppStartDestination.MAIN_HOME, secondLaunchVm.uiState.value.destination)
    }
}

