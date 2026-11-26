package com.mulocoolbot.gymtracker.data.repository

import com.mulocoolbot.gymtracker.data.auth.AuthEntryMode
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

    suspend fun isEmailRegistered(email: String): Boolean {
        return sessionPreferences.isEmailRegistered(email)
    }

    suspend fun hasExistingUserProfile(email: String): Boolean {
        val profile = dao.getUserProfile() ?: return false
        val expectedUid = SessionPreferences.computeUidForEmail(email)
        return profile.uid == expectedUid
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

    suspend fun signInWithGoogle(
        email: String,
        entryMode: AuthEntryMode = AuthEntryMode.SIGN_IN
    ) {
        val wasRegistered = sessionPreferences.isEmailRegistered(email)
        sessionPreferences.signInWithGoogle(email, entryMode)
        finalizeProviderSignIn(email = email, entryMode = entryMode, wasRegistered = wasRegistered)
    }

    suspend fun signInWithEmail(
        email: String,
        entryMode: AuthEntryMode = AuthEntryMode.SIGN_IN
    ) {
        val wasRegistered = sessionPreferences.isEmailRegistered(email)
        sessionPreferences.signInWithEmail(email, entryMode)
        finalizeProviderSignIn(email = email, entryMode = entryMode, wasRegistered = wasRegistered)
    }

    private suspend fun finalizeProviderSignIn(
        email: String,
        entryMode: AuthEntryMode,
        wasRegistered: Boolean
    ) {
        val existingProfile = dao.getUserProfile()
        val session = sessionPreferences.authSessionFlow.first()
        val hasMatchingProfile = existingProfile != null && existingProfile.uid == session.uid

        if (hasMatchingProfile || (entryMode == AuthEntryMode.LOG_IN && (wasRegistered || existingProfile != null))) {
            sessionPreferences.markProfileCompleted(true)
            sessionPreferences.markTutorialCompleted()
            if (existingProfile != null) {
                syncAuthFlagsToProfile()
            } else {
                dao.replaceSingleUserProfile(
                    UserProfileEntity(
                        uid = session.uid,
                        name = email.trim().substringBefore("@").ifBlank { "Athlete" },
                        birthDateMillis = 946684800000L,
                        heightCm = 175.0,
                        weightKg = 70.0,
                        isGoogleLinked = session.isGoogleLinked,
                        isEmailLinked = session.isEmailLinked
                    )
                )
            }
        } else {
            sessionPreferences.markProfileCompleted(false)
            if (entryMode == AuthEntryMode.LOG_IN) {
                sessionPreferences.markTutorialCompleted()
            }
            syncAuthFlagsToProfile()
        }
    }

    suspend fun completeTutorial() {
        sessionPreferences.markTutorialCompleted()
    }

    suspend fun unlinkGoogle() {
        sessionPreferences.unlinkGoogle()
        syncAuthFlagsToProfile()
    }

    suspend fun unlinkEmail() {
        sessionPreferences.unlinkEmail()
        syncAuthFlagsToProfile()
    }

    suspend fun continueOffline() {
        val existingProfile = dao.getUserProfile()
        sessionPreferences.continueOffline(preferredUid = existingProfile?.uid)
        if (existingProfile != null) {
            sessionPreferences.markProfileCompleted(true)
            syncAuthFlagsToProfile()
        } else {
            sessionPreferences.markProfileCompleted(false)
        }
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

        dao.replaceSingleUserProfile(updatedProfile)

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
        dao.replaceSingleUserProfile(
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
        dao.replaceSingleUserProfile(evaluatedProfile)
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
        val preparedSets = sets.mapIndexedNotNull { index, set ->
            val normalizedName = WorkoutCalculator.normalizeExerciseName(set.exerciseName)
            if (normalizedName.isEmpty() ||
                !set.weightKg.isFinite() ||
                set.weightKg <= 0.0 ||
                set.weightKg > 10_000.0 ||
                set.reps !in 1..10_000
            ) {
                null
            } else {
                set.copy(
                    setId = 0L,
                    sessionId = 0L,
                    exerciseName = normalizedName,
                    setOrder = if (set.setOrder > 0) set.setOrder else index + 1,
                    e1rm = WorkoutCalculator.calculateE1RM(set.weightKg, set.reps)
                )
            }
        }
        if (sets.isNotEmpty() && preparedSets.isEmpty()) return

        val profile = dao.getUserProfile()
        val safeEndMillis = endMillis.coerceAtLeast(startMillis)
        val calories = WorkoutCalculator.calculateCalories(
            weightKg = profile?.weightKg ?: 70.0,
            startMillis = startMillis,
            endMillis = safeEndMillis
        )

        val cleanPlanName = planName.trim().ifEmpty { "Workout" }
        val session = WorkoutSessionEntity(
            dateEpochDay = todayEpochDay,
            planName = cleanPlanName,
            startTimeMillis = startMillis,
            endTimeMillis = safeEndMillis,
            caloriesBurned = calories
        )

        val normalizedMuscleMap = muscleGroupsByExercise.entries.associate { (key, value) ->
            WorkoutCalculator.normalizeExerciseName(key) to value
        }

        val historyItems = preparedSets
            .groupBy { it.exerciseName }
            .map { (exerciseName, exerciseSets) ->
                ExerciseHistoryEntity(
                    normalizedName = exerciseName,
                    muscleGroup = normalizedMuscleMap[exerciseName]
                        ?.takeIf { it.isNotBlank() }
                        ?: cleanPlanName,
                    lastUsedMillis = exerciseSets.maxOf { it.timestampMillis }
                )
            }

        val updatedProfile = profile?.let { currentProfile ->
            val evaluatedProfile = StreakEvaluator.evaluateOnAppLaunch(
                profile = currentProfile,
                weeklyPlan = dao.getWorkoutPlans(),
                todayEpochDay = todayEpochDay
            )
            StreakEvaluator.onWorkoutCompleted(
                profile = evaluatedProfile,
                todayEpochDay = todayEpochDay
            )
        }

        dao.recordWorkoutSessionTransaction(
            session = session,
            sets = preparedSets,
            historyItems = historyItems,
            updatedProfile = updatedProfile
        )
    }

    suspend fun exportBackupJson(): String {
        return GymBackupManager.exportToJson(dao)
    }

    suspend fun importBackupJson(jsonString: String): Boolean {
        val success = GymBackupManager.importFromJson(dao, jsonString)
        if (!success) return false
        val hasProfile = dao.getUserProfile() != null
        if (hasProfile) {
            sessionPreferences.markProfileCompleted(true)
        }
        if (hasProfile || dao.getAllSessions().isNotEmpty() || dao.getWorkoutPlans().any { !it.isRestDay }) {
            sessionPreferences.markTutorialCompleted()
        }
        return true
    }

    private suspend fun syncAuthFlagsToProfile() {
        val profile = dao.getUserProfile() ?: return
        val session = sessionPreferences.authSessionFlow.first()
        dao.replaceSingleUserProfile(
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
