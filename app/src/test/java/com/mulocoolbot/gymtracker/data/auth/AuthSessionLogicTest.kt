package com.mulocoolbot.gymtracker.data.auth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.mulocoolbot.gymtracker.data.local.UserProfileEntity
import com.mulocoolbot.gymtracker.ui.auth.AppStartDestination
import com.mulocoolbot.gymtracker.ui.auth.AuthOnboardingViewModel
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AuthSessionLogicTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createSessionPreferences(scope: CoroutineScope): SessionPreferences {
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(tempFolder.newFolder(), "test_auth_session.preferences_pb")
        }
        return SessionPreferences(dataStore)
    }

    @Test
    fun freshAuthSession_isUnauthenticatedAndRoutesStrictlyToSignInNeverMainHome() = runTest {
        val prefs = createSessionPreferences(backgroundScope)
        val freshSession = prefs.authSessionFlow.first()

        assertFalse(freshSession.isAuthenticated)
        assertFalse(freshSession.isProfileCompleted)
        assertEquals(
            AppStartDestination.SIGN_IN,
            AuthOnboardingViewModel.resolveDestination(freshSession, null)
        )
        assertNotEquals(
            AppStartDestination.MAIN_HOME,
            AuthOnboardingViewModel.resolveDestination(freshSession, null)
        )
    }

    @Test
    fun signInWithEmail_createsValidUidAndSetsEmailAuthenticatedState() = runTest {
        val prefs = createSessionPreferences(backgroundScope)

        prefs.signInWithEmail("alex@example.com")
        val session = prefs.authSessionFlow.first()

        assertTrue(session.uid.isNotBlank())
        assertEquals("alex@example.com", session.userEmail)
        assertNull(session.googleEmail)
        assertTrue(session.isEmailLinked)
        assertFalse(session.isGoogleLinked)
        assertTrue(session.isAuthenticated)
    }

    @Test
    fun signInWithGoogle_afterEmailSignInPreservesSameUidAndLinksBothProviders() = runTest {
        val prefs = createSessionPreferences(backgroundScope)

        prefs.signInWithEmail("alex@example.com")
        val initialSession = prefs.authSessionFlow.first()

        prefs.signInWithGoogle("alex.gym@gmail.com")
        val linkedSession = prefs.authSessionFlow.first()

        assertEquals(initialSession.uid, linkedSession.uid)
        assertEquals("alex@example.com", linkedSession.userEmail)
        assertEquals("alex.gym@gmail.com", linkedSession.googleEmail)
        assertTrue(linkedSession.isEmailLinked)
        assertTrue(linkedSession.isGoogleLinked)
        assertTrue(linkedSession.isAuthenticated)
    }

    @Test
    fun unlinkEmail_whileGoogleIsLinkedKeepsUidAndAuthenticatedState() = runTest {
        val prefs = createSessionPreferences(backgroundScope)

        prefs.signInWithEmail("alex@example.com")
        prefs.signInWithGoogle("alex.gym@gmail.com")
        prefs.markProfileCompleted(true)
        val initialUid = prefs.authSessionFlow.first().uid

        prefs.unlinkEmail()
        val afterEmailUnlink = prefs.authSessionFlow.first()

        assertFalse(afterEmailUnlink.isEmailLinked)
        assertNull(afterEmailUnlink.userEmail)
        assertTrue(afterEmailUnlink.isGoogleLinked)
        assertEquals("alex.gym@gmail.com", afterEmailUnlink.googleEmail)
        assertEquals(initialUid, afterEmailUnlink.uid)
        assertTrue(afterEmailUnlink.isProfileCompleted)
        assertTrue(afterEmailUnlink.isAuthenticated)
    }

    @Test
    fun unlinkRemainingProvider_transitionsIsAuthenticatedToFalseAndRoutesToSignIn() = runTest {
        val prefs = createSessionPreferences(backgroundScope)

        prefs.signInWithEmail("alex@example.com")
        prefs.signInWithGoogle("alex.gym@gmail.com")
        prefs.markProfileCompleted(true)
        val initialUid = prefs.authSessionFlow.first().uid

        prefs.unlinkEmail()
        prefs.unlinkGoogle()
        val finalSession = prefs.authSessionFlow.first()

        assertFalse(finalSession.isEmailLinked)
        assertFalse(finalSession.isGoogleLinked)
        assertEquals(initialUid, finalSession.uid)
        assertFalse(finalSession.isAuthenticated)
        assertEquals(
            AppStartDestination.SIGN_IN,
            AuthOnboardingViewModel.resolveDestination(finalSession, null)
        )
    }

    @Test
    fun registerAndLoginWithEmailPassword_persistsCredentialsAndRestoresSessionAfterLogout() = runTest {
        val prefs = createSessionPreferences(backgroundScope)

        val registered = prefs.registerWithEmailPassword("user@mfit.app", "secret123")
        val initialSession = prefs.authSessionFlow.first()

        assertTrue(registered)
        assertTrue(initialSession.isAuthenticated)
        assertFalse(initialSession.isProfileCompleted)
        assertEquals(
            AppStartDestination.PROFILE_SETUP,
            AuthOnboardingViewModel.resolveDestination(initialSession, null)
        )

        val completedProfile = UserProfileEntity(
            uid = initialSession.uid,
            name = "Alex",
            birthDateMillis = 946684800000L,
            heightCm = 178.0,
            weightKg = 74.0,
            isGoogleLinked = false,
            isEmailLinked = true
        )
        prefs.markProfileCompleted(true)
        val afterProfileSetup = prefs.authSessionFlow.first()
        assertEquals(
            AppStartDestination.MAIN_HOME,
            AuthOnboardingViewModel.resolveDestination(afterProfileSetup, completedProfile)
        )

        prefs.unlinkEmail()
        val loggedOutSession = prefs.authSessionFlow.first()
        assertFalse(loggedOutSession.isAuthenticated)
        assertEquals(
            AppStartDestination.SIGN_IN,
            AuthOnboardingViewModel.resolveDestination(loggedOutSession, completedProfile)
        )

        assertFalse(prefs.loginWithEmailPassword("user@mfit.app", "wrongpass"))
        assertTrue(prefs.loginWithEmailPassword("user@mfit.app", "secret123"))

        val restoredSession = prefs.authSessionFlow.first()
        assertTrue(restoredSession.isAuthenticated)
        assertTrue(restoredSession.isProfileCompleted)
        assertEquals(initialSession.uid, restoredSession.uid)
        assertEquals(
            AppStartDestination.MAIN_HOME,
            AuthOnboardingViewModel.resolveDestination(restoredSession, completedProfile)
        )
    }
}

