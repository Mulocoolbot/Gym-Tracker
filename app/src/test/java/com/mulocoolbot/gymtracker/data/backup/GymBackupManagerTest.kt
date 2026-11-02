package com.mulocoolbot.gymtracker.data.backup

import com.mulocoolbot.gymtracker.data.local.ExerciseHistoryEntity
import com.mulocoolbot.gymtracker.data.local.UserProfileEntity
import com.mulocoolbot.gymtracker.data.local.WorkoutPlanEntity
import com.mulocoolbot.gymtracker.data.local.WorkoutSessionEntity
import com.mulocoolbot.gymtracker.data.local.WorkoutSetEntity
import com.mulocoolbot.gymtracker.ui.InMemoryTestGymDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GymBackupManagerTest {

    private val sampleProfile = UserProfileEntity(
        uid = "uid-backup-1",
        name = "Alex",
        birthDateMillis = 946684800000L,
        heightCm = 180.0,
        weightKg = 76.5,
        currentStreak = 6,
        consecutiveMissed = 0,
        lastWorkoutEpochDay = 20760L,
        isGoogleLinked = true,
        isEmailLinked = true
    )

    private val samplePlans = listOf(
        WorkoutPlanEntity(dayOfWeek = 0, isRestDay = true, planName = "Rest Day"),
        WorkoutPlanEntity(dayOfWeek = 1, isRestDay = false, planName = "Pull Day"),
        WorkoutPlanEntity(dayOfWeek = 2, isRestDay = false, planName = "Push Day"),
        WorkoutPlanEntity(dayOfWeek = 3, isRestDay = true, planName = "Rest Day"),
        WorkoutPlanEntity(dayOfWeek = 4, isRestDay = false, planName = "Legs"),
        WorkoutPlanEntity(dayOfWeek = 5, isRestDay = false, planName = "Upper"),
        WorkoutPlanEntity(dayOfWeek = 6, isRestDay = true, planName = "Rest Day")
    )

    private val sampleHistory = listOf(
        ExerciseHistoryEntity(
            normalizedName = "Bench Press",
            muscleGroup = "Push Day",
            lastUsedMillis = 50_000L
        )
    )

    private val sampleSession = WorkoutSessionEntity(
        sessionId = 10L,
        dateEpochDay = 20760L,
        planName = "Push Day",
        startTimeMillis = 10_000L,
        endTimeMillis = 40_000L,
        caloriesBurned = 220
    )

    private val sampleSet = WorkoutSetEntity(
        setId = 100L,
        sessionId = 10L,
        exerciseName = "Bench Press",
        setType = "NORMAL",
        setOrder = 1,
        weightKg = 80.0,
        reps = 8,
        e1rm = 101.3,
        timestampMillis = 25_000L
    )

    @Test
    fun exportAndImportJson_roundTripsAllEntitiesIntoCleanDaoWithoutDataLoss() = runTest {
        val sourceDao = InMemoryTestGymDao()
        sourceDao.upsertUserProfile(sampleProfile)
        sourceDao.upsertWorkoutPlans(samplePlans)
        sourceDao.upsertExerciseHistories(sampleHistory)
        sourceDao.insertSessions(listOf(sampleSession))
        sourceDao.insertSets(listOf(sampleSet))

        val exportedJson = GymBackupManager.exportToJson(sourceDao)

        assertTrue(exportedJson.contains("\"schemaVersion\": 1"))

        val targetDao = InMemoryTestGymDao()
        val importSuccess = GymBackupManager.importFromJson(targetDao, exportedJson)

        assertTrue(importSuccess)
        assertEquals(sampleProfile, targetDao.getUserProfile())
        assertEquals(samplePlans, targetDao.getWorkoutPlans())
        assertEquals(sampleHistory, targetDao.getExerciseHistory())
        assertEquals(listOf(sampleSession), targetDao.getAllSessions())
        assertEquals(listOf(sampleSet), targetDao.getAllSets())
    }

    @Test
    fun importFromJson_returnsFalseForMalformedOrEmptyInput() = runTest {
        val targetDao = InMemoryTestGymDao()

        assertFalse(GymBackupManager.importFromJson(targetDao, "{ invalid json"))
        assertFalse(GymBackupManager.importFromJson(targetDao, ""))
        assertFalse(GymBackupManager.importFromJson(targetDao, "{}"))
    }
}

