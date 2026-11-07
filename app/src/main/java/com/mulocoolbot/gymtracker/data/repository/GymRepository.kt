package com.mulocoolbot.gymtracker.data.repository

import com.mulocoolbot.gymtracker.data.auth.AuthSession
import com.mulocoolbot.gymtracker.data.auth.SessionPreferences
import com.mulocoolbot.gymtracker.data.backup.GymBackupManager
import com.mulocoolbot.gymtracker.data.local.ExerciseHistoryEntity
import com.mulocoolbot.gymtracker.data.local.GymDao
import com.mulocoolbot.gymtracker.data.local.UserProfileEntity
import com.mulocoolbot.gymtracker.data.local.WorkoutPlanEntity
import com.mulocoolbot.gymtracker.data.local.WorkoutSessionEntity
import com.mulocoolbot.gymtracker.data.local.WorkoutSetEntity
import com.mulocoolbot.gymtracker.util.StreakEvaluator
import com.mulocoolbot.gymtracker.util.WorkoutCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class GymRepository(
    private val dao: GymDao,
    private val sessionPreferences: SessionPreferences
) {

    val authSession: Flow<AuthSession> = sessionPreferences.authSessionFlow
    val userProfile: Flow<UserProfileEntity?> = dao.observeUserProfile()
    val weeklyPlan: Flow<List<WorkoutPlanEntity>> = dao.observeWorkoutPlans()
    val exerciseHistory: Flow<List<ExerciseHistoryEntity>> = dao.observeExerciseHistory()

    fun observeTodayCalories(todayEpochDay: Long): Flow<Int> {
        return dao.observeCaloriesByDate(todayEpochDay)
    }

    fun observeTodayCompletedSets(todayEpochDay: Long): Flow<List<WorkoutSetEntity>> {
        return dao.observeSetsByDate(todayEpochDay)
    }

    fun observeSetsForExercise(exerciseName: String): Flow<List<WorkoutSetEntity>> {
        return dao.observeSetsForExercise(WorkoutCalculator.normalizeExerciseName(exerciseName))
    }

    fun observeLoggedExerciseNames(): Flow<List<String>> {
        return dao.observeLoggedExerciseNames()
    }

    fun observeAllSets(): Flow<List<WorkoutSetEntity>> {
        return dao.observeAllSets()
    }

    suspend fun registerWithEmailPassword(email: String, password: String): Boolean {
        val registered = sessionPreferences.registerWithEmailPassword(email, password)
        if (!registered) return false

        val session = sessionPreferences.authSessionFlow.first()
        val existingProfile = dao.getUserProfile()
        if (existingProfile != null && existingProfile.uid != session.uid) {
            dao.clearUserProfile()
            dao.upsertWorkoutPlans(buildDefaultRestWeeklyPlan())
            sessionPreferences.markProfileCompleted(false)
        } else if (existingProfile == null) {
            sessionPreferences.markProfileCompleted(false)
        } else {
            syncAuthFlagsToProfile()
        }
        return true
    }

    suspend fun loginWithEmailPassword(email: String, password: String): Boolean {
        val success = sessionPreferences.loginWithEmailPassword(email, password)
        if (!success) return false

        val existingProfile = dao.getUserProfile()
        if (existingProfile != null) {
            sessionPreferences.markProfileCompleted(true)
            syncAuthFlagsToProfile()
        } else {
            sessionPreferences.markProfileCompleted(false)
        }
        return true
    }

    suspend fun signInWithGoogle(email: String) {
        sessionPreferences.signInWithGoogle(email)
        val existingProfile = dao.getUserProfile()
        val session = sessionPreferences.authSessionFlow.first()
        if (existingProfile != null && existingProfile.uid == session.uid) {
            sessionPreferences.markProfileCompleted(true)
        }
        syncAuthFlagsToProfile()
    }

    suspend fun signInWithEmail(email: String) {
        sessionPreferences.signInWithEmail(email)
        val existingProfile = dao.getUserProfile()
        val session = sessionPreferences.authSessionFlow.first()
        if (existingProfile != null && existingProfile.uid == session.uid) {
            sessionPreferences.markProfileCompleted(true)
        }
        syncAuthFlagsToProfile()
    }

    suspend fun unlinkGoogle() {
        sessionPreferences.unlinkGoogle()
        syncAuthFlagsToProfile()
    }

    suspend fun unlinkEmail() {
        sessionPreferences.unlinkEmail()
        syncAuthFlagsToProfile()
    }

    suspend fun completeOnboardingProfile(
        name: String,
        birthDateMillis: Long,
        weightKg: Double,
        heightCm: Double
    ) {
        val session = sessionPreferences.authSessionFlow.first()
        val existingProfile = dao.getUserProfile()
        val activeUid = session.uid.ifBlank { existingProfile?.uid ?: "local-user" }

        val updatedProfile = existingProfile?.copy(
            uid = activeUid,
            name = name.trim(),
            birthDateMillis = birthDateMillis,
            heightCm = heightCm,
            weightKg = weightKg,
            isGoogleLinked = session.isGoogleLinked,
            isEmailLinked = session.isEmailLinked
        ) ?: UserProfileEntity(
            uid = activeUid,
            name = name.trim(),
            birthDateMillis = birthDateMillis,
            heightCm = heightCm,
            weightKg = weightKg,
            isGoogleLinked = session.isGoogleLinked,
            isEmailLinked = session.isEmailLinked
        )

        dao.upsertUserProfile(updatedProfile)

        val currentPlans = dao.getWorkoutPlans()
        if (currentPlans.isEmpty() || currentPlans.none { !it.isRestDay }) {
            dao.upsertWorkoutPlans(buildDefaultRestWeeklyPlan())
        }

        sessionPreferences.markProfileCompleted(true)
    }

    suspend fun updateProfileMetrics(
        name: String,
        weightKg: Double,
        heightCm: Double
    ) {
        val existingProfile = dao.getUserProfile() ?: return
        dao.upsertUserProfile(
            existingProfile.copy(
                name = name.trim(),
                weightKg = weightKg,
                heightCm = heightCm
            )
        )
    }

    suspend fun evaluateStreakOnLaunch(todayEpochDay: Long) {
        val profile = dao.getUserProfile() ?: return
        val currentPlans = dao.getWorkoutPlans()
        val evaluatedProfile = StreakEvaluator.evaluateOnAppLaunch(
            profile = profile,
            weeklyPlan = currentPlans,
            todayEpochDay = todayEpochDay
        )
        dao.upsertUserProfile(evaluatedProfile)
    }

    suspend fun saveWeeklyWorkoutPlan(selectedDaysWithNames: Map<Int, String>) {
        val plans = (0..6).map { dayOfWeek ->
            val rawName = selectedDaysWithNames[dayOfWeek]?.trim()
            if (rawName.isNullOrEmpty()) {
                WorkoutPlanEntity(
                    dayOfWeek = dayOfWeek,
                    isRestDay = true,
                    planName = "Rest Day"
                )
            } else {
                WorkoutPlanEntity(
                    dayOfWeek = dayOfWeek,
                    isRestDay = false,
                    planName = rawName
                )
            }
        }
        dao.upsertWorkoutPlans(plans)
    }

    suspend fun recordWorkoutSession(
        planName: String,
        startMillis: Long,
        endMillis: Long,
        sets: List<WorkoutSetEntity>,
        todayEpochDay: Long,
        muscleGroupsByExercise: Map<String, String> = emptyMap()
    ) {
        val profile = dao.getUserProfile()
        val calories = WorkoutCalculator.calculateCalories(
            weightKg = profile?.weightKg ?: 70.0,
            startMillis = startMillis,
            endMillis = endMillis
        )

        val cleanPlanName = planName.trim().ifEmpty { "Workout" }
        val sessionId = dao.insertSession(
            WorkoutSessionEntity(
                dateEpochDay = todayEpochDay,
                planName = cleanPlanName,
                startTimeMillis = startMillis,
                endTimeMillis = endMillis,
                caloriesBurned = calories
            )
        )

        val preparedSets = sets.mapIndexed { index, set ->
            val normalizedName = WorkoutCalculator.normalizeExerciseName(set.exerciseName)
            val calculatedE1rm = WorkoutCalculator.calculateE1RM(set.weightKg, set.reps)
            set.copy(
                setId = 0,
                sessionId = sessionId,
                exerciseName = normalizedName,
                setOrder = if (set.setOrder > 0) set.setOrder else index + 1,
                e1rm = calculatedE1rm
            )
        }

        if (preparedSets.isNotEmpty()) {
            dao.insertSets(preparedSets)
            val historyItems = preparedSets
                .groupBy { it.exerciseName }
                .map { (exerciseName, exerciseSets) ->
                    ExerciseHistoryEntity(
                        normalizedName = exerciseName,
                        muscleGroup = muscleGroupsByExercise[exerciseName]
                            ?.takeIf { it.isNotBlank() }
                            ?: cleanPlanName,
                        lastUsedMillis = exerciseSets.maxOf { it.timestampMillis }
                    )
                }
            dao.upsertExerciseHistories(historyItems)
        }

        if (profile != null) {
            val updatedProfile = StreakEvaluator.onWorkoutCompleted(
                profile = profile,
                todayEpochDay = todayEpochDay
            )
            dao.upsertUserProfile(updatedProfile)
        }
    }

    suspend fun exportBackupJson(): String {
        return GymBackupManager.exportToJson(dao)
    }

    suspend fun importBackupJson(jsonString: String): Boolean {
        val success = GymBackupManager.importFromJson(dao, jsonString)
        if (success && dao.getUserProfile() != null) {
            sessionPreferences.markProfileCompleted(true)
        }
        return success
    }

    private suspend fun syncAuthFlagsToProfile() {
        val profile = dao.getUserProfile() ?: return
        val session = sessionPreferences.authSessionFlow.first()
        dao.upsertUserProfile(
            profile.copy(
                uid = session.uid.ifBlank { profile.uid },
                isGoogleLinked = session.isGoogleLinked,
                isEmailLinked = session.isEmailLinked
            )
        )
    }

    private fun buildDefaultRestWeeklyPlan(): List<WorkoutPlanEntity> {
        return (0..6).map { dayOfWeek ->
            WorkoutPlanEntity(
                dayOfWeek = dayOfWeek,
                isRestDay = true,
                planName = "Rest Day"
            )
        }
    }
}
