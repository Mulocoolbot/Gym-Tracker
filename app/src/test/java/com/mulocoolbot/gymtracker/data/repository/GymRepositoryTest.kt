package com.mulocoolbot.gymtracker.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.mulocoolbot.gymtracker.data.auth.SessionPreferences
import com.mulocoolbot.gymtracker.data.local.ExerciseHistoryEntity
import com.mulocoolbot.gymtracker.data.local.GymDao
import com.mulocoolbot.gymtracker.data.local.UserProfileEntity
import com.mulocoolbot.gymtracker.data.local.WorkoutPlanEntity
import com.mulocoolbot.gymtracker.data.local.WorkoutSessionEntity
import com.mulocoolbot.gymtracker.data.local.WorkoutSetEntity
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GymRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createRepository(scope: CoroutineScope): GymRepository {
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(tempFolder.newFolder(), "test_repo_prefs.preferences_pb")
        }
        val sessionPreferences = SessionPreferences(dataStore)
        val fakeDao = FakeGymDao()
        return GymRepository(dao = fakeDao, sessionPreferences = sessionPreferences)
    }

    @Test
    fun completeOnboardingProfile_savesProfileAndInitializesSevenDayWeeklyPlan() = runTest {
        val repository = createRepository(backgroundScope)
        repository.signInWithEmail("alex@example.com")

        repository.completeOnboardingProfile(
            name = "Alex",
            birthDateMillis = 946684800000L,
            weightKg = 72.0,
            heightCm = 178.0
        )

        val profile = repository.userProfile.first()
        val session = repository.authSession.first()
        val plans = repository.weeklyPlan.first()

        assertNotNull(profile)
        assertEquals("Alex", profile?.name)
        assertEquals(946684800000L, profile?.birthDateMillis)
        assertEquals(72.0, profile?.weightKg ?: 0.0, 0.001)
        assertEquals(178.0, profile?.heightCm ?: 0.0, 0.001)
        assertTrue(session.isProfileCompleted)
        assertEquals(7, plans.size)
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6), plans.map { it.dayOfWeek })
    }

    @Test
    fun saveWeeklyWorkoutPlan_setsSelectedDaysActiveAndUnselectedDaysRest() = runTest {
        val repository = createRepository(backgroundScope)

        repository.saveWeeklyWorkoutPlan(
            mapOf(
                1 to "Pull Day",
                2 to "Push Day"
            )
        )

        val plansByDay = repository.weeklyPlan.first().associateBy { it.dayOfWeek }
        assertEquals(7, plansByDay.size)

        val monday = plansByDay.getValue(1)
        assertFalse(monday.isRestDay)
        assertEquals("Pull Day", monday.planName)

        val tuesday = plansByDay.getValue(2)
        assertFalse(tuesday.isRestDay)
        assertEquals("Push Day", tuesday.planName)

        listOf(0, 3, 4, 5, 6).forEach { restDayIndex ->
            val dayPlan = plansByDay.getValue(restDayIndex)
            assertTrue(dayPlan.isRestDay)
            assertEquals("Rest Day", dayPlan.planName)
        }
    }

    @Test
    fun recordWorkoutSession_computesCaloriesNormalizesExercisesComputesE1rmAndIncrementsStreak() = runTest {
        val repository = createRepository(backgroundScope)
        repository.signInWithEmail("alex@example.com")
        repository.completeOnboardingProfile(
            name = "Alex",
            birthDateMillis = 946684800000L,
            weightKg = 72.0,
            heightCm = 178.0
        )

        val todayEpochDay = 20760L
        val rawSet = WorkoutSetEntity(
            sessionId = 0L,
            exerciseName = "  incline   bench press ",
            setType = "NORMAL",
            setOrder = 1,
            weightKg = 65.0,
            reps = 10,
            e1rm = 0.0,
            timestampMillis = 15_000L
        )

        repository.recordWorkoutSession(
            planName = "Push Day",
            startMillis = 0L,
            endMillis = 30 * 60_000L,
            sets = listOf(rawSet),
            todayEpochDay = todayEpochDay
        )

        val todayCalories = repository.observeTodayCalories(todayEpochDay).first()
        val todaySets = repository.observeTodayCompletedSets(todayEpochDay).first()
        val history = repository.exerciseHistory.first()
        val updatedProfile = repository.userProfile.first()

        assertEquals(189, todayCalories)
        assertEquals(1, todaySets.size)
        assertEquals("Incline Bench Press", todaySets.first().exerciseName)
        assertEquals(86.7, todaySets.first().e1rm, 0.001)
        assertEquals(1, history.size)
        assertEquals("Incline Bench Press", history.first().normalizedName)
        assertEquals(1, updatedProfile?.currentStreak)
        assertEquals(todayEpochDay, updatedProfile?.lastWorkoutEpochDay)
    }
}

private class FakeGymDao : GymDao {

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
        return plansState.map { map -> map.values.sortedBy { it.dayOfWeek } }
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
        return historyState.map { map -> map.values.sortedByDescending { it.lastUsedMillis } }
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
        val stored = session.copy(sessionId = id)
        val updated = sessionsState.value.toMutableMap()
        updated[id] = stored
        sessionsState.value = updated
        return id
    }

    override suspend fun insertSessions(sessions: List<WorkoutSessionEntity>) {
        val updated = sessionsState.value.toMutableMap()
        sessions.forEach { session ->
            val id = if (session.sessionId == 0L) nextSessionId++ else session.sessionId
            updated[id] = session.copy(sessionId = id)
            if (id >= nextSessionId) nextSessionId = id + 1
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

        val updatedSets = setsState.value.filterValues { it.sessionId != session.sessionId }
        setsState.value = updatedSets
    }

    override suspend fun getSessionById(sessionId: Long): WorkoutSessionEntity? {
        return sessionsState.value[sessionId]
    }

    override suspend fun getAllSessions(): List<WorkoutSessionEntity> {
        return sessionsState.value.values.sortedBy { it.sessionId }
    }

    override fun observeSessionsByDate(dateEpochDay: Long): Flow<List<WorkoutSessionEntity>> {
        return sessionsState.map { map ->
            map.values
                .filter { it.dateEpochDay == dateEpochDay }
                .sortedByDescending { it.startTimeMillis }
        }
    }

    override fun observeAllSessions(): Flow<List<WorkoutSessionEntity>> {
        return sessionsState.map { map ->
            map.values.sortedByDescending { it.startTimeMillis }
        }
    }

    override fun observeCaloriesByDate(dateEpochDay: Long): Flow<Int> {
        return sessionsState.map { map ->
            map.values
                .filter { it.dateEpochDay == dateEpochDay }
                .sumOf { it.caloriesBurned }
        }
    }

    override suspend fun insertSet(workoutSet: WorkoutSetEntity): Long {
        val id = if (workoutSet.setId == 0L) nextSetId++ else workoutSet.setId
        val stored = workoutSet.copy(setId = id)
        val updated = setsState.value.toMutableMap()
        updated[id] = stored
        setsState.value = updated
        return id
    }

    override suspend fun insertSets(sets: List<WorkoutSetEntity>) {
        val updated = setsState.value.toMutableMap()
        sets.forEach { set ->
            val id = if (set.setId == 0L) nextSetId++ else set.setId
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
            val sessionIds = sessions.values
                .filter { it.dateEpochDay == dateEpochDay }
                .map { it.sessionId }
                .toSet()
            sets.values
                .filter { it.sessionId in sessionIds }
                .sortedBy { it.timestampMillis }
        }
    }

    override fun observeSetsForExercise(exerciseName: String): Flow<List<WorkoutSetEntity>> {
        return setsState.map { map ->
            map.values
                .filter { it.exerciseName == exerciseName }
                .sortedBy { it.timestampMillis }
        }
    }

    override fun observeLoggedExerciseNames(): Flow<List<String>> {
        return setsState.map { map ->
            map.values
                .map { it.exerciseName }
                .distinct()
                .sorted()
        }
    }

    override fun observeAllSets(): Flow<List<WorkoutSetEntity>> {
        return setsState.map { map ->
            map.values.sortedBy { it.timestampMillis }
        }
    }

    override suspend fun getAllSets(): List<WorkoutSetEntity> {
        return setsState.value.values.sortedBy { it.setId }
    }
}

