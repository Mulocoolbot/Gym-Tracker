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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

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

    fun validateBackupJson(jsonString: String): GymBackupPayload? {
        val trimmed = jsonString.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null

        val root = runCatching {
            json.parseToJsonElement(trimmed) as? JsonObject
        }.getOrNull() ?: return null

        val schemaVersion = (root["schemaVersion"] as? JsonPrimitive)?.intOrNull ?: return null
        if (schemaVersion !in 1..CURRENT_SCHEMA_VERSION) return null

        val userProfileNode = root["userProfile"]
        if (userProfileNode != null && userProfileNode !is JsonNull && userProfileNode !is JsonObject) {
            return null
        }
        if (root["workoutPlans"] !is JsonArray) return null
        if (root["exerciseHistory"] !is JsonArray) return null
        if (root["workoutSessions"] !is JsonArray) return null
        if (root["workoutSets"] !is JsonArray) return null

        val payload = runCatching {
            json.decodeFromString(GymBackupPayload.serializer(), trimmed)
        }.getOrNull() ?: return null

        return payload.takeIf(::isPayloadValid)
    }

    suspend fun importFromJson(dao: GymDao, jsonString: String): Boolean {
        val payload = validateBackupJson(jsonString) ?: return false

        return runCatching {
            dao.replaceAllBackupData(
                profile = payload.userProfile?.toEntity(),
                plans = payload.workoutPlans.map { it.toEntity() },
                history = payload.exerciseHistory.map { it.toEntity() },
                sessions = payload.workoutSessions.map { it.toEntity() },
                sets = payload.workoutSets.map { it.toEntity() }
            )
            true
        }.getOrDefault(false)
    }

    private fun isPayloadValid(payload: GymBackupPayload): Boolean {
        if (payload.schemaVersion !in 1..CURRENT_SCHEMA_VERSION) return false

        payload.userProfile?.let { profile ->
            if (profile.uid.isBlank() || profile.name.isBlank()) return false
            if (!profile.heightCm.isFinite() || profile.heightCm <= 0.0) return false
            if (!profile.weightKg.isFinite() || profile.weightKg <= 0.0) return false
            if (profile.currentStreak < 0 || profile.consecutiveMissed < 0) return false
        }

        val planDays = HashSet<Int>(payload.workoutPlans.size)
        for (plan in payload.workoutPlans) {
            if (plan.dayOfWeek !in 0..6 || !planDays.add(plan.dayOfWeek)) return false
        }

        for (history in payload.exerciseHistory) {
            if (history.normalizedName.isBlank()) return false
        }

        val sessionIds = HashSet<Long>(payload.workoutSessions.size)
        for (session in payload.workoutSessions) {
            if (session.sessionId <= 0L || !sessionIds.add(session.sessionId)) return false
            if (session.caloriesBurned < 0) return false
        }

        val setIds = HashSet<Long>(payload.workoutSets.size)
        for (set in payload.workoutSets) {
            if (set.setId < 0L) return false
            if (set.setId > 0L && !setIds.add(set.setId)) return false
            if (set.sessionId !in sessionIds) return false
            if (set.exerciseName.isBlank() || set.reps <= 0) return false
            if (!set.weightKg.isFinite() || set.weightKg < 0.0) return false
            if (!set.e1rm.isFinite() || set.e1rm < 0.0) return false
        }

        return true
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
