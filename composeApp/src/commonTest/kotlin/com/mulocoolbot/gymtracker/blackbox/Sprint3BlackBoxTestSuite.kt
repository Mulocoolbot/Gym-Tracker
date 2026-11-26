package com.mulocoolbot.gymtracker.blackbox

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.mulocoolbot.gymtracker.data.auth.RestTimerPreferences
import com.mulocoolbot.gymtracker.data.auth.SessionPreferences
import com.mulocoolbot.gymtracker.data.backup.GymBackupManager
import com.mulocoolbot.gymtracker.data.local.ExerciseHistoryEntity
import com.mulocoolbot.gymtracker.data.local.TestInMemoryGymDao
import com.mulocoolbot.gymtracker.data.local.UserProfileEntity
import com.mulocoolbot.gymtracker.data.local.WorkoutPlanEntity
import com.mulocoolbot.gymtracker.data.local.WorkoutSessionEntity
import com.mulocoolbot.gymtracker.data.local.WorkoutSetEntity
import com.mulocoolbot.gymtracker.platform.createTestPreferencesPath
import com.mulocoolbot.gymtracker.util.StreakEvaluator
import com.mulocoolbot.gymtracker.util.WorkoutCalculator
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import okio.Path.Companion.toPath

class Sprint3BlackBoxTestSuite {

    @Test
    fun tc_s3_01_local_database_crud_workout_plan_session_and_set() = runTest {
        val dao = TestInMemoryGymDao()

        val initialPlans = (0..6).map { day ->
            WorkoutPlanEntity(
                dayOfWeek = day,
                isRestDay = day % 2 == 0,
                planName = if (day % 2 == 0) "Rest Day" else "Split $day"
            )
        }
        dao.upsertWorkoutPlans(initialPlans.reversed())
        val readPlans = dao.getWorkoutPlans()
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6), readPlans.map { it.dayOfWeek })
        assertTrue(readPlans[0].isRestDay)
        assertEquals("Split 1", readPlans[1].planName)

        dao.upsertWorkoutPlan(WorkoutPlanEntity(dayOfWeek = 0, isRestDay = false, planName = "Push Day"))
        assertFalse(dao.observeWorkoutPlans().first()[0].isRestDay)
        assertEquals("Push Day", dao.observeWorkoutPlans().first()[0].planName)

        dao.upsertWorkoutPlan(WorkoutPlanEntity(dayOfWeek = 0, isRestDay = true, planName = "Rest Day"))
        assertTrue(dao.getWorkoutPlans()[0].isRestDay)

        val epochDay = 20760L
        val sessionId = dao.insertSession(
            WorkoutSessionEntity(
                dateEpochDay = epochDay,
                planName = "Push",
                startTimeMillis = 1_000L,
                endTimeMillis = null,
                caloriesBurned = 150
            )
        )
        val createdSession = dao.getSessionById(sessionId)
        assertNotNull(createdSession)
        assertEquals("Push", createdSession.planName)
        assertEquals(150, dao.observeCaloriesByDate(epochDay).first())

        val updatedSession = createdSession.copy(
            planName = "Push Heavy",
            endTimeMillis = 2_400_000L,
            caloriesBurned = 260
        )
        dao.updateSession(updatedSession)
        assertEquals("Push Heavy", dao.getSessionById(sessionId)?.planName)
        assertEquals(260, dao.observeCaloriesByDate(epochDay).first())
        assertEquals(1, dao.observeSessionsByDate(epochDay).first().size)

        val set1Id = dao.insertSet(
            WorkoutSetEntity(
                sessionId = sessionId,
                exerciseName = "Bench Press",
                setType = "WARMUP",
                setOrder = 1,
                weightKg = 60.0,
                reps = 10,
                e1rm = 80.0,
                timestampMillis = 2_000L
            )
        )
        dao.insertSets(
            listOf(
                WorkoutSetEntity(
                    sessionId = sessionId,
                    exerciseName = "Bench Press",
                    setType = "NORMAL",
                    setOrder = 2,
                    weightKg = 100.0,
                    reps = 5,
                    e1rm = 116.7,
                    timestampMillis = 3_000L
                )
            )
        )

        assertEquals(2, dao.getSetsForSession(sessionId).size)
        assertEquals(2, dao.observeSetsByDate(epochDay).first().size)
        assertEquals(2, dao.observeSetsForExercise("Bench Press").first().size)
        assertEquals(listOf("Bench Press"), dao.observeLoggedExerciseNames().first())

        val firstSet = dao.getSetsForSession(sessionId).first { it.setId == set1Id }
        dao.deleteSet(firstSet)
        val remainingSets = dao.getSetsForSession(sessionId)
        assertEquals(1, remainingSets.size)
        assertEquals(100.0, remainingSets.single().weightKg, 0.001)

        dao.deleteSession(updatedSession)
        assertNull(dao.getSessionById(sessionId))
        assertTrue(dao.getSetsForSession(sessionId).isEmpty())
        assertEquals(0, dao.observeCaloriesByDate(epochDay).first())
    }

    @Test
    fun tc_s3_02_epley_e1rm_accuracy_and_safe_edge_cases() {
        val fiveReps = WorkoutCalculator.calculateE1RM(weightKg = 100.0, reps = 5)
        assertTrue(fiveReps.isFinite())
        assertTrue(abs(fiveReps - 116.67) <= 0.05)
        assertEquals(116.7, fiveReps, 0.001)

        val oneRep = WorkoutCalculator.calculateE1RM(weightKg = 80.0, reps = 1)
        assertEquals(80.0, oneRep, 0.001)

        val tenReps = WorkoutCalculator.calculateE1RM(weightKg = 65.0, reps = 10)
        assertEquals(86.7, tenReps, 0.001)

        val invalidCases = listOf(
            100.0 to 0,
            100.0 to -3,
            0.0 to 5,
            -40.0 to 5,
            Double.NaN to 5,
            Double.POSITIVE_INFINITY to 5,
            Double.NEGATIVE_INFINITY to 5
        )
        invalidCases.forEach { (weight, reps) ->
            val result = WorkoutCalculator.calculateE1RM(weight, reps)
            assertFalse(result.isNaN())
            assertTrue(result.isFinite())
            assertEquals(0.0, result, 0.001)
        }
    }

    @Test
    fun tc_s3_03_schedule_aware_streak_tracking() {
        val sundayEpochDay = LocalDate(2026, 11, 1).toEpochDays().toLong()
        val mondayEpochDay = sundayEpochDay + 1
        val tuesdayEpochDay = sundayEpochDay + 2
        val wednesdayEpochDay = sundayEpochDay + 3
        val thursdayEpochDay = sundayEpochDay + 4

        val weeklyPlan = listOf(
            WorkoutPlanEntity(dayOfWeek = 0, isRestDay = true, planName = "Rest Day"),
            WorkoutPlanEntity(dayOfWeek = 1, isRestDay = false, planName = "Pull Day"),
            WorkoutPlanEntity(dayOfWeek = 2, isRestDay = true, planName = "Rest Day"),
            WorkoutPlanEntity(dayOfWeek = 3, isRestDay = false, planName = "Push Day"),
            WorkoutPlanEntity(dayOfWeek = 4, isRestDay = false, planName = "Legs"),
            WorkoutPlanEntity(dayOfWeek = 5, isRestDay = true, planName = "Rest Day"),
            WorkoutPlanEntity(dayOfWeek = 6, isRestDay = false, planName = "Upper")
        )

        val baseProfile = UserProfileEntity(
            uid = "uid-streak",
            name = "Alex",
            birthDateMillis = 946684800000L,
            heightCm = 178.0,
            weightKg = 75.0,
            currentStreak = 5,
            consecutiveMissed = 0,
            lastWorkoutEpochDay = mondayEpochDay
        )

        val afterRestDay = StreakEvaluator.evaluateOnAppLaunch(
            profile = baseProfile,
            weeklyPlan = weeklyPlan,
            todayEpochDay = wednesdayEpochDay
        )
        assertEquals(0, afterRestDay.consecutiveMissed)
        assertEquals(5, afterRestDay.currentStreak)

        val afterOneMissedWorkout = StreakEvaluator.evaluateOnAppLaunch(
            profile = baseProfile.copy(lastWorkoutEpochDay = sundayEpochDay),
            weeklyPlan = weeklyPlan,
            todayEpochDay = tuesdayEpochDay
        )
        assertEquals(1, afterOneMissedWorkout.consecutiveMissed)
        assertEquals(5, afterOneMissedWorkout.currentStreak)

        val afterTwoMissedWorkouts = StreakEvaluator.evaluateOnAppLaunch(
            profile = baseProfile.copy(lastWorkoutEpochDay = sundayEpochDay),
            weeklyPlan = weeklyPlan,
            todayEpochDay = thursdayEpochDay
        )
        assertEquals(2, afterTwoMissedWorkouts.consecutiveMissed)
        assertEquals(0, afterTwoMissedWorkouts.currentStreak)

        val completedToday = StreakEvaluator.onWorkoutCompleted(afterOneMissedWorkout, wednesdayEpochDay)
        assertEquals(6, completedToday.currentStreak)
        assertEquals(0, completedToday.consecutiveMissed)
        assertEquals(wednesdayEpochDay, completedToday.lastWorkoutEpochDay)

        val completedSecondSessionSameDay = StreakEvaluator.onWorkoutCompleted(completedToday, wednesdayEpochDay)
        assertEquals(6, completedSecondSessionSameDay.currentStreak)
    }

    @Test
    fun tc_s3_04_datastore_persistence_survives_reinitialization() = runTest {
        val prefsPath = createTestPreferencesPath("tc_s3_04_reinit.preferences_pb")

        withContext(Dispatchers.IO) {
            val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val dataStore1 = PreferenceDataStoreFactory.createWithPath(
                scope = scope1,
                produceFile = { prefsPath.toPath() }
            )
            val prefs1 = SessionPreferences(dataStore1)

            assertEquals(RestTimerPreferences(60, 120, 180), prefs1.restTimerPreferencesFlow.first())

            prefs1.signInWithEmail("athlete@mfit.app")
            prefs1.markProfileCompleted(true)
            prefs1.updateRestTimerDurations(warmupSeconds = 60, normalSeconds = 120, finalSeconds = 180)
            prefs1.updateRestTimerDurations(warmupSeconds = 75, normalSeconds = 135, finalSeconds = 210)

            val savedSession = prefs1.authSessionFlow.first()
            val savedUid = savedSession.uid
            assertTrue(savedUid.isNotBlank())

            scope1.coroutineContext[Job]!!.cancelAndJoin()

            val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val dataStore2 = PreferenceDataStoreFactory.createWithPath(
                scope = scope2,
                produceFile = { prefsPath.toPath() }
            )
            val prefs2 = SessionPreferences(dataStore2)

            val restoredSession = prefs2.authSessionFlow.first()
            val restoredTimerPrefs = prefs2.restTimerPreferencesFlow.first()

            assertEquals(savedUid, restoredSession.uid)
            assertEquals("athlete@mfit.app", restoredSession.userEmail)
            assertTrue(restoredSession.isEmailLinked)
            assertTrue(restoredSession.isProfileCompleted)
            assertEquals(RestTimerPreferences(75, 135, 210), restoredTimerPrefs)

            scope2.coroutineContext[Job]!!.cancelAndJoin()
        }
    }

    @Test
    fun tc_s3_05_five_entity_json_backup_lossless_and_android_v2_compatible() = runTest {
        val sourceDao = TestInMemoryGymDao()
        val profile = UserProfileEntity(
            uid = "uid-v2-roundtrip",
            name = "Alex \"Volt\" Mercer",
            birthDateMillis = 946684800000L,
            heightCm = 180.5,
            weightKg = 76.5,
            currentStreak = 7,
            consecutiveMissed = 0,
            lastWorkoutEpochDay = 20760L,
            isGoogleLinked = true,
            isEmailLinked = true
        )
        val plans = listOf(
            WorkoutPlanEntity(dayOfWeek = 0, isRestDay = true, planName = "Rest Day"),
            WorkoutPlanEntity(dayOfWeek = 1, isRestDay = false, planName = "Pull Day"),
            WorkoutPlanEntity(dayOfWeek = 2, isRestDay = false, planName = "Push Day")
        )
        val history = listOf(
            ExerciseHistoryEntity(
                normalizedName = "Bench Press",
                muscleGroup = "CHEST",
                lastUsedMillis = 50_000L
            )
        )
        val sessions = listOf(
            WorkoutSessionEntity(
                sessionId = 10L,
                dateEpochDay = 20760L,
                planName = "Push Day",
                startTimeMillis = 10_000L,
                endTimeMillis = 40_000L,
                caloriesBurned = 220
            ),
            WorkoutSessionEntity(
                sessionId = 11L,
                dateEpochDay = 20761L,
                planName = "Pull Day",
                startTimeMillis = 80_000L,
                endTimeMillis = null,
                caloriesBurned = 0
            )
        )
        val sets = listOf(
            WorkoutSetEntity(
                setId = 100L,
                sessionId = 10L,
                exerciseName = "Bench Press",
                setType = "NORMAL",
                setOrder = 1,
                weightKg = 100.0,
                reps = 5,
                e1rm = 116.7,
                timestampMillis = 25_000L
            )
        )

        sourceDao.upsertUserProfile(profile)
        sourceDao.upsertWorkoutPlans(plans)
        sourceDao.upsertExerciseHistories(history)
        sourceDao.insertSessions(sessions)
        sourceDao.insertSets(sets)

        val exportedJson = GymBackupManager.exportToJson(sourceDao)
        assertTrue(exportedJson.contains("\"schemaVersion\": 1"))
        assertTrue(exportedJson.contains("\"userProfile\""))
        assertTrue(exportedJson.contains("\"workoutPlans\""))
        assertTrue(exportedJson.contains("\"exerciseHistory\""))
        assertTrue(exportedJson.contains("\"workoutSessions\""))
        assertTrue(exportedJson.contains("\"workoutSets\""))

        val targetDao = TestInMemoryGymDao()
        assertTrue(GymBackupManager.importFromJson(targetDao, exportedJson))
        assertEquals(profile, targetDao.getUserProfile())
        assertEquals(plans, targetDao.getWorkoutPlans())
        assertEquals(history, targetDao.getExerciseHistory())
        assertEquals(sessions, targetDao.getAllSessions())
        assertEquals(sets, targetDao.getAllSets())

        val androidV2BackupJson = """
            {"schemaVersion": 1,"userProfile":{"uid":"android-v2-uid","name":"Jordan","birthDateMillis":915148800000,"heightCm":175.0,"weightKg":72.0,"currentStreak":3,"consecutiveMissed":1,"lastWorkoutEpochDay":null,"isGoogleLinked":false,"isEmailLinked":true},"workoutPlans":[{"dayOfWeek":1,"isRestDay":false,"planName":"Upper Body"}],"exerciseHistory":[{"normalizedName":"Overhead Press","muscleGroup":"SHOULDERS","lastUsedMillis":123456}],"workoutSessions":[{"sessionId":1,"dateEpochDay":20750,"planName":"Upper Body","startTimeMillis":1000,"endTimeMillis":1801000,"caloriesBurned":189}],"workoutSets":[{"setId":1,"sessionId":1,"exerciseName":"Overhead Press","setType":"FINAL","setOrder":1,"weightKg":60.0,"reps":6,"e1rm":72.0,"timestampMillis":5000}]}
        """.trimIndent()

        val v2RestoreDao = TestInMemoryGymDao()
        assertTrue(GymBackupManager.importFromJson(v2RestoreDao, androidV2BackupJson))
        assertEquals("android-v2-uid", v2RestoreDao.getUserProfile()?.uid)
        assertNull(v2RestoreDao.getUserProfile()?.lastWorkoutEpochDay)
        assertEquals("Upper Body", v2RestoreDao.getWorkoutPlans().single().planName)
        assertEquals("Overhead Press", v2RestoreDao.getExerciseHistory().single().normalizedName)
        assertEquals(189, v2RestoreDao.getAllSessions().single().caloriesBurned)
        assertEquals(72.0, v2RestoreDao.getAllSets().single().e1rm, 0.001)

        assertFalse(GymBackupManager.importFromJson(TestInMemoryGymDao(), "{ invalid"))
        assertFalse(GymBackupManager.importFromJson(TestInMemoryGymDao(), ""))
        assertFalse(GymBackupManager.importFromJson(TestInMemoryGymDao(), "{}"))
    }
}
