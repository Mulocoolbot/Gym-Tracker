@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.mulocoolbot.gymtracker.data.backup

import com.mulocoolbot.gymtracker.data.local.ExerciseHistoryEntity
import com.mulocoolbot.gymtracker.data.local.GymDao
import com.mulocoolbot.gymtracker.data.local.UserProfileEntity
import com.mulocoolbot.gymtracker.data.local.WorkoutPlanEntity
import com.mulocoolbot.gymtracker.data.local.WorkoutSessionEntity
import com.mulocoolbot.gymtracker.data.local.WorkoutSetEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object GymBackupManager {

    private const val CURRENT_SCHEMA_VERSION = 1

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = true
    }

    suspend fun exportToJson(dao: GymDao): String {
        val payload = GymBackupPayload(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            userProfile = dao.getUserProfile()?.toBackup(),
            workoutPlans = dao.getWorkoutPlans().map { it.toBackup() },
            exerciseHistory = dao.getExerciseHistory().map { it.toBackup() },
            workoutSessions = dao.getAllSessions().map { it.toBackup() },
            workoutSets = dao.getAllSets().map { it.toBackup() }
        )
        return json.encodeToString(GymBackupPayload.serializer(), payload)
    }

    suspend fun importFromJson(dao: GymDao, jsonString: String): Boolean {
        val trimmed = jsonString.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return false

        val payload = runCatching {
            json.decodeFromString(GymBackupPayload.serializer(), trimmed)
        }.getOrNull() ?: return false

        if (payload.schemaVersion <= 0) return false

        return runCatching {
            payload.userProfile?.let { dao.upsertUserProfile(it.toEntity()) }
            if (payload.workoutPlans.isNotEmpty()) {
                dao.upsertWorkoutPlans(payload.workoutPlans.map { it.toEntity() })
            }
            if (payload.exerciseHistory.isNotEmpty()) {
                dao.upsertExerciseHistories(payload.exerciseHistory.map { it.toEntity() })
            }
            if (payload.workoutSessions.isNotEmpty()) {
                dao.insertSessions(payload.workoutSessions.map { it.toEntity() })
            }
            if (payload.workoutSets.isNotEmpty()) {
                dao.insertSets(payload.workoutSets.map { it.toEntity() })
            }
            true
        }.getOrDefault(false)
    }

    @Serializable
    data class GymBackupPayload(
        val schemaVersion: Int,
        val userProfile: UserProfileBackup? = null,
        val workoutPlans: List<WorkoutPlanBackup> = emptyList(),
        val exerciseHistory: List<ExerciseHistoryBackup> = emptyList(),
        val workoutSessions: List<WorkoutSessionBackup> = emptyList(),
        val workoutSets: List<WorkoutSetBackup> = emptyList()
    )

    @Serializable
    data class UserProfileBackup(
        val uid: String,
        val name: String,
        val birthDateMillis: Long,
        val heightCm: Double,
        val weightKg: Double,
        val currentStreak: Int = 0,
        val consecutiveMissed: Int = 0,
        val lastWorkoutEpochDay: Long? = null,
        val isGoogleLinked: Boolean = false,
        val isEmailLinked: Boolean = false
    )

    @Serializable
    data class WorkoutPlanBackup(
        val dayOfWeek: Int,
        val isRestDay: Boolean,
        val planName: String
    )

    @Serializable
    data class ExerciseHistoryBackup(
        val normalizedName: String,
        val muscleGroup: String,
        val lastUsedMillis: Long
    )

    @Serializable
    data class WorkoutSessionBackup(
        val sessionId: Long,
        val dateEpochDay: Long,
        val planName: String,
        val startTimeMillis: Long,
        val endTimeMillis: Long? = null,
        val caloriesBurned: Int = 0
    )

    @Serializable
    data class WorkoutSetBackup(
        val setId: Long,
        val sessionId: Long,
        val exerciseName: String,
        val setType: String,
        val setOrder: Int,
        val weightKg: Double,
        val reps: Int,
        val e1rm: Double,
        val timestampMillis: Long
    )

    private fun UserProfileEntity.toBackup() = UserProfileBackup(
        uid = uid,
        name = name,
        birthDateMillis = birthDateMillis,
        heightCm = heightCm,
        weightKg = weightKg,
        currentStreak = currentStreak,
        consecutiveMissed = consecutiveMissed,
        lastWorkoutEpochDay = lastWorkoutEpochDay,
        isGoogleLinked = isGoogleLinked,
        isEmailLinked = isEmailLinked
    )

    private fun UserProfileBackup.toEntity() = UserProfileEntity(
        uid = uid,
        name = name,
        birthDateMillis = birthDateMillis,
        heightCm = heightCm,
        weightKg = weightKg,
        currentStreak = currentStreak,
        consecutiveMissed = consecutiveMissed,
        lastWorkoutEpochDay = lastWorkoutEpochDay,
        isGoogleLinked = isGoogleLinked,
        isEmailLinked = isEmailLinked
    )

    private fun WorkoutPlanEntity.toBackup() = WorkoutPlanBackup(
        dayOfWeek = dayOfWeek,
        isRestDay = isRestDay,
        planName = planName
    )

    private fun WorkoutPlanBackup.toEntity() = WorkoutPlanEntity(
        dayOfWeek = dayOfWeek,
        isRestDay = isRestDay,
        planName = planName
    )

    private fun ExerciseHistoryEntity.toBackup() = ExerciseHistoryBackup(
        normalizedName = normalizedName,
        muscleGroup = muscleGroup,
        lastUsedMillis = lastUsedMillis
    )

    private fun ExerciseHistoryBackup.toEntity() = ExerciseHistoryEntity(
        normalizedName = normalizedName,
        muscleGroup = muscleGroup,
        lastUsedMillis = lastUsedMillis
    )

    private fun WorkoutSessionEntity.toBackup() = WorkoutSessionBackup(
        sessionId = sessionId,
        dateEpochDay = dateEpochDay,
        planName = planName,
        startTimeMillis = startTimeMillis,
        endTimeMillis = endTimeMillis,
        caloriesBurned = caloriesBurned
    )

    private fun WorkoutSessionBackup.toEntity() = WorkoutSessionEntity(
        sessionId = sessionId,
        dateEpochDay = dateEpochDay,
        planName = planName,
        startTimeMillis = startTimeMillis,
        endTimeMillis = endTimeMillis,
        caloriesBurned = caloriesBurned
    )

    private fun WorkoutSetEntity.toBackup() = WorkoutSetBackup(
        setId = setId,
        sessionId = sessionId,
        exerciseName = exerciseName,
        setType = setType,
        setOrder = setOrder,
        weightKg = weightKg,
        reps = reps,
        e1rm = e1rm,
        timestampMillis = timestampMillis
    )

    private fun WorkoutSetBackup.toEntity() = WorkoutSetEntity(
        setId = setId,
        sessionId = sessionId,
        exerciseName = exerciseName,
        setType = setType,
        setOrder = setOrder,
        weightKg = weightKg,
        reps = reps,
        e1rm = e1rm,
        timestampMillis = timestampMillis
    )
}
