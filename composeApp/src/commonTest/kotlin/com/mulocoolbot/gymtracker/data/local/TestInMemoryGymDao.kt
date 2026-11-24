package com.mulocoolbot.gymtracker.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

internal class TestInMemoryGymDao : GymDao {

    private val profileState = MutableStateFlow<UserProfileEntity?>(null)
    private val plansState = MutableStateFlow<Map<Int, WorkoutPlanEntity>>(emptyMap())
    private val historyState = MutableStateFlow<Map<String, ExerciseHistoryEntity>>(emptyMap())
    private val sessionsState = MutableStateFlow<Map<Long, WorkoutSessionEntity>>(emptyMap())
    private val setsState = MutableStateFlow<Map<Long, WorkoutSetEntity>>(emptyMap())

    private var nextSessionId = 1L
    private var nextSetId = 1L

    override fun observeUserProfile(): Flow<UserProfileEntity?> = profileState

    override suspend fun getUserProfile(): UserProfileEntity? = profileState.value

    override suspend fun upsertUserProfile(profile: UserProfileEntity) {
        profileState.value = profile
    }

    override suspend fun clearUserProfile() {
        profileState.value = null
    }

    override fun observeWorkoutPlans(): Flow<List<WorkoutPlanEntity>> {
        return plansState.map { it.values.sortedBy { plan -> plan.dayOfWeek } }
    }

    override suspend fun getWorkoutPlans(): List<WorkoutPlanEntity> {
        return plansState.value.values.sortedBy { it.dayOfWeek }
    }

    override suspend fun upsertWorkoutPlans(plans: List<WorkoutPlanEntity>) {
        val updated = plansState.value.toMutableMap()
        plans.forEach { updated[it.dayOfWeek] = it }
        plansState.value = updated
    }

    override suspend fun upsertWorkoutPlan(plan: WorkoutPlanEntity) {
        val updated = plansState.value.toMutableMap()
        updated[plan.dayOfWeek] = plan
        plansState.value = updated
    }

    override fun observeExerciseHistory(): Flow<List<ExerciseHistoryEntity>> {
        return historyState.map { it.values.sortedByDescending { item -> item.lastUsedMillis } }
    }

    override suspend fun getExerciseHistory(): List<ExerciseHistoryEntity> {
        return historyState.value.values.sortedByDescending { it.lastUsedMillis }
    }

    override suspend fun upsertExerciseHistory(exercise: ExerciseHistoryEntity) {
        val updated = historyState.value.toMutableMap()
        updated[exercise.normalizedName] = exercise
        historyState.value = updated
    }

    override suspend fun upsertExerciseHistories(exercises: List<ExerciseHistoryEntity>) {
        val updated = historyState.value.toMutableMap()
        exercises.forEach { updated[it.normalizedName] = it }
        historyState.value = updated
    }

    override suspend fun insertSession(session: WorkoutSessionEntity): Long {
        val id = if (session.sessionId == 0L) nextSessionId++ else session.sessionId
        if (id >= nextSessionId) nextSessionId = id + 1
        val updated = sessionsState.value.toMutableMap()
        updated[id] = session.copy(sessionId = id)
        sessionsState.value = updated
        return id
    }

    override suspend fun insertSessions(sessions: List<WorkoutSessionEntity>) {
        val updated = sessionsState.value.toMutableMap()
        sessions.forEach { session ->
            val id = if (session.sessionId == 0L) nextSessionId++ else session.sessionId
            if (id >= nextSessionId) nextSessionId = id + 1
            updated[id] = session.copy(sessionId = id)
        }
        sessionsState.value = updated
    }

    override suspend fun updateSession(session: WorkoutSessionEntity) {
        val updated = sessionsState.value.toMutableMap()
        updated[session.sessionId] = session
        sessionsState.value = updated
    }

    override suspend fun deleteSession(session: WorkoutSessionEntity) {
        val updatedSessions = sessionsState.value.toMutableMap()
        updatedSessions.remove(session.sessionId)
        sessionsState.value = updatedSessions
        setsState.value = setsState.value.filterValues { it.sessionId != session.sessionId }
    }

    override suspend fun getSessionById(sessionId: Long): WorkoutSessionEntity? = sessionsState.value[sessionId]

    override suspend fun getAllSessions(): List<WorkoutSessionEntity> {
        return sessionsState.value.values.sortedBy { it.sessionId }
    }

    override fun observeSessionsByDate(dateEpochDay: Long): Flow<List<WorkoutSessionEntity>> {
        return sessionsState.map { map ->
            map.values.filter { it.dateEpochDay == dateEpochDay }.sortedByDescending { it.startTimeMillis }
        }
    }

    override fun observeAllSessions(): Flow<List<WorkoutSessionEntity>> {
        return sessionsState.map { it.values.sortedByDescending { session -> session.startTimeMillis } }
    }

    override fun observeCaloriesByDate(dateEpochDay: Long): Flow<Int> {
        return sessionsState.map { map ->
            map.values.filter { it.dateEpochDay == dateEpochDay }.sumOf { it.caloriesBurned }
        }
    }

    override suspend fun insertSet(workoutSet: WorkoutSetEntity): Long {
        val id = if (workoutSet.setId == 0L) nextSetId++ else workoutSet.setId
        if (id >= nextSetId) nextSetId = id + 1
        val updated = setsState.value.toMutableMap()
        updated[id] = workoutSet.copy(setId = id)
        setsState.value = updated
        return id
    }

    override suspend fun insertSets(sets: List<WorkoutSetEntity>) {
        val updated = setsState.value.toMutableMap()
        sets.forEach { set ->
            val id = if (set.setId == 0L) nextSetId++ else set.setId
            if (id >= nextSetId) nextSetId = id + 1
            updated[id] = set.copy(setId = id)
        }
        setsState.value = updated
    }

    override suspend fun deleteSet(workoutSet: WorkoutSetEntity) {
        val updated = setsState.value.toMutableMap()
        updated.remove(workoutSet.setId)
        setsState.value = updated
    }

    override fun observeSetsForSession(sessionId: Long): Flow<List<WorkoutSetEntity>> {
        return setsState.map { map ->
            map.values
                .filter { it.sessionId == sessionId }
                .sortedWith(compareBy<WorkoutSetEntity> { it.setOrder }.thenBy { it.timestampMillis })
        }
    }

    override suspend fun getSetsForSession(sessionId: Long): List<WorkoutSetEntity> {
        return setsState.value.values
            .filter { it.sessionId == sessionId }
            .sortedWith(compareBy<WorkoutSetEntity> { it.setOrder }.thenBy { it.timestampMillis })
    }

    override fun observeSetsByDate(dateEpochDay: Long): Flow<List<WorkoutSetEntity>> {
        return combine(sessionsState, setsState) { sessions, sets ->
            val sessionIds = sessions.values.filter { it.dateEpochDay == dateEpochDay }.map { it.sessionId }.toSet()
            sets.values.filter { it.sessionId in sessionIds }.sortedBy { it.timestampMillis }
        }
    }

    override fun observeSetsForExercise(exerciseName: String): Flow<List<WorkoutSetEntity>> {
        return setsState.map { map ->
            map.values.filter { it.exerciseName == exerciseName }.sortedBy { it.timestampMillis }
        }
    }

    override fun observeLoggedExerciseNames(): Flow<List<String>> {
        return setsState.map { it.values.map { set -> set.exerciseName }.distinct().sorted() }
    }

    override fun observeAllSets(): Flow<List<WorkoutSetEntity>> {
        return setsState.map { it.values.sortedBy { set -> set.timestampMillis } }
    }

    override suspend fun getAllSets(): List<WorkoutSetEntity> {
        return setsState.value.values.sortedBy { it.setId }
    }
}
