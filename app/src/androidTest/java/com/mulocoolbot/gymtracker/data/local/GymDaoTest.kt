package com.mulocoolbot.gymtracker.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GymDaoTest {

    private lateinit var database: GymDatabase
    private lateinit var dao: GymDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, GymDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.gymDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun upsertAndObserveUserProfile_emitsExpectedProfileState() = runTest {
        val profile = UserProfileEntity(
            uid = "user-123",
            name = "Jordan",
            birthDateMillis = 946684800000L,
            heightCm = 180.0,
            weightKg = 78.5,
            currentStreak = 3,
            consecutiveMissed = 0,
            lastWorkoutEpochDay = 20000L,
            isGoogleLinked = true,
            isEmailLinked = false
        )

        dao.upsertUserProfile(profile)
        val storedProfile = dao.observeUserProfile().first()

        assertEquals(profile, storedProfile)
    }

    @Test
    fun upsertWorkoutPlans_returnsAllSevenDaysSortedByDayOfWeek() = runTest {
        val shuffledPlans = listOf(
            WorkoutPlanEntity(dayOfWeek = 5, isRestDay = false, planName = "Legs"),
            WorkoutPlanEntity(dayOfWeek = 1, isRestDay = false, planName = "Push"),
            WorkoutPlanEntity(dayOfWeek = 0, isRestDay = true, planName = "Rest"),
            WorkoutPlanEntity(dayOfWeek = 6, isRestDay = true, planName = "Rest"),
            WorkoutPlanEntity(dayOfWeek = 3, isRestDay = false, planName = "Pull"),
            WorkoutPlanEntity(dayOfWeek = 2, isRestDay = true, planName = "Rest"),
            WorkoutPlanEntity(dayOfWeek = 4, isRestDay = false, planName = "Upper")
        )

        dao.upsertWorkoutPlans(shuffledPlans)
        val plans = dao.observeWorkoutPlans().first()

        assertEquals(7, plans.size)
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6), plans.map { it.dayOfWeek })
    }

    @Test
    fun insertSessionsAndSets_returnsExpectedSetsAndSumsDailyCalories() = runTest {
        val epochDay = 20760L
        val firstSessionId = dao.insertSession(
            WorkoutSessionEntity(
                dateEpochDay = epochDay,
                planName = "Push",
                startTimeMillis = 1000L,
                endTimeMillis = 2000L,
                caloriesBurned = 210
            )
        )
        dao.insertSession(
            WorkoutSessionEntity(
                dateEpochDay = epochDay,
                planName = "Core",
                startTimeMillis = 3000L,
                endTimeMillis = 4000L,
                caloriesBurned = 90
            )
        )

        val firstSet = WorkoutSetEntity(
            sessionId = firstSessionId,
            exerciseName = "Bench Press",
            setType = "WARMUP",
            setOrder = 1,
            weightKg = 60.0,
            reps = 10,
            e1rm = 80.0,
            timestampMillis = 1100L
        )
        val secondSet = WorkoutSetEntity(
            sessionId = firstSessionId,
            exerciseName = "Bench Press",
            setType = "NORMAL",
            setOrder = 2,
            weightKg = 80.0,
            reps = 8,
            e1rm = 101.3,
            timestampMillis = 1200L
        )
        dao.insertSets(listOf(firstSet, secondSet))

        val sessionSets = dao.observeSetsForSession(firstSessionId).first()
        val totalCalories = dao.observeCaloriesByDate(epochDay).first()

        assertEquals(2, sessionSets.size)
        assertEquals("Bench Press", sessionSets.first().exerciseName)
        assertEquals(300, totalCalories)
    }

    @Test
    fun deleteSession_cascadesAndDeletesAssociatedWorkoutSets() = runTest {
        val sessionId = dao.insertSession(
            WorkoutSessionEntity(
                dateEpochDay = 20760L,
                planName = "Pull",
                startTimeMillis = 1000L,
                endTimeMillis = 2500L,
                caloriesBurned = 180
            )
        )
        dao.insertSet(
            WorkoutSetEntity(
                sessionId = sessionId,
                exerciseName = "Barbell Row",
                setType = "NORMAL",
                setOrder = 1,
                weightKg = 70.0,
                reps = 10,
                e1rm = 93.3,
                timestampMillis = 1500L
            )
        )

        val insertedSession = dao.getSessionById(sessionId)!!
        dao.deleteSession(insertedSession)

        val remainingSets = dao.observeSetsForSession(sessionId).first()
        assertTrue(remainingSets.isEmpty())
    }
}

