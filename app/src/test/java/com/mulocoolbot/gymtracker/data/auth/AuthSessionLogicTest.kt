package com.mulocoolbot.gymtracker.data.auth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.mulocoolbot.gymtracker.ui.auth.AppStartDestination
import com.mulocoolbot.gymtracker.ui.auth.AuthOnboardingViewModel
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}

