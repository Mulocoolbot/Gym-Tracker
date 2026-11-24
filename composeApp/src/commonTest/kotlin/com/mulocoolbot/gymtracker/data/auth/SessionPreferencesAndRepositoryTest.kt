package com.mulocoolbot.gymtracker.data.auth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.mulocoolbot.gymtracker.data.local.TestInMemoryGymDao
import com.mulocoolbot.gymtracker.data.local.WorkoutSetEntity
import com.mulocoolbot.gymtracker.data.repository.GymRepository
import com.mulocoolbot.gymtracker.platform.createTestPreferencesPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath

class SessionPreferencesAndRepositoryTest {

    private fun createPreferences(
        scope: kotlinx.coroutines.CoroutineScope,
        name: String
    ): SessionPreferences {
        val dataStore = PreferenceDataStoreFactory.createWithPath(
            scope = scope,
            produceFile = { createTestPreferencesPath(name).toPath() }
        )
        return SessionPreferences(dataStore)
    }

    @Test
    fun signInLinkAndUnlink_preservesUidAndAuthFlags() = runTest {
        val prefs = createPreferences(backgroundScope, "auth_link.preferences_pb")
        val initial = prefs.authSessionFlow.first()
        assertFalse(initial.isAuthenticated)

        prefs.signInWithEmail("alex@example.com")
        val afterEmail = prefs.authSessionFlow.first()
        assertTrue(afterEmail.isAuthenticated)
        assertTrue(afterEmail.isEmailLinked)
        assertFalse(afterEmail.isGoogleLinked)

        prefs.signInWithGoogle("alex.gym@gmail.com")
        val linkedBoth = prefs.authSessionFlow.first()
        assertEquals(afterEmail.uid, linkedBoth.uid)
        assertTrue(linkedBoth.isEmailLinked)
        assertTrue(linkedBoth.isGoogleLinked)

        prefs.unlinkEmail()
        val onlyGoogle = prefs.authSessionFlow.first()
        assertFalse(onlyGoogle.isEmailLinked)
        assertNull(onlyGoogle.userEmail)
        assertTrue(onlyGoogle.isGoogleLinked)
        assertTrue(onlyGoogle.isAuthenticated)

        prefs.unlinkGoogle()
        val unauthenticated = prefs.authSessionFlow.first()
        assertFalse(unauthenticated.isAuthenticated)
    }

    @Test
    fun repositoryOnboardingPlanAndSessionRecording_workEndToEnd() = runTest {
        val prefs = createPreferences(backgroundScope, "repo_e2e.preferences_pb")
        val dao = TestInMemoryGymDao()
        val repository = GymRepository(dao = dao, sessionPreferences = prefs)

        repository.signInWithEmail("alex@example.com")
        repository.completeOnboardingProfile(
            name = "Alex",
            birthDateMillis = 946684800000L,
            weightKg = 72.0,
            heightCm = 178.0
        )

        val profile = repository.userProfile.first()
        assertNotNull(profile)
        assertEquals(7, repository.weeklyPlan.first().size)

        repository.saveWeeklyWorkoutPlan(mapOf(1 to "Pull Day", 3 to "Push Day"))
        val plansByDay = repository.weeklyPlan.first().associateBy { it.dayOfWeek }
        assertFalse(plansByDay.getValue(1).isRestDay)
        assertTrue(plansByDay.getValue(2).isRestDay)

        val todayEpochDay = 20760L
        repository.recordWorkoutSession(
            planName = "Push Day",
            startMillis = 0L,
            endMillis = 30 * 60_000L,
            sets = listOf(
                WorkoutSetEntity(
                    sessionId = 0L,
                    exerciseName = "  incline   bench press ",
                    setType = "NORMAL",
                    setOrder = 1,
                    weightKg = 65.0,
                    reps = 10,
                    e1rm = 0.0,
                    timestampMillis = 15_000L
                )
            ),
            todayEpochDay = todayEpochDay
        )

        assertEquals(189, repository.observeTodayCalories(todayEpochDay).first())
        val sets = repository.observeTodayCompletedSets(todayEpochDay).first()
        assertEquals("Incline Bench Press", sets.single().exerciseName)
        assertEquals(86.7, sets.single().e1rm, 0.001)
        assertEquals(1, repository.userProfile.first()?.currentStreak)
    }
}
