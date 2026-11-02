package com.mulocoolbot.gymtracker.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.mulocoolbot.gymtracker.data.auth.SessionPreferences
import com.mulocoolbot.gymtracker.data.local.ExerciseHistoryEntity
import com.mulocoolbot.gymtracker.data.local.GymDao
import com.mulocoolbot.gymtracker.data.local.UserProfileEntity
import com.mulocoolbot.gymtracker.data.local.WorkoutPlanEntity
import com.mulocoolbot.gymtracker.data.local.WorkoutSessionEntity
import com.mulocoolbot.gymtracker.data.local.WorkoutSetEntity
import com.mulocoolbot.gymtracker.data.repository.GymRepository
import com.mulocoolbot.gymtracker.ui.plan.WorkoutPlanViewModel
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutPlanViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(scope: CoroutineScope): WorkoutPlanViewModel {
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(tempFolder.newFolder(), "test_plan_vm.preferences_pb")
        }
        val repository = GymRepository(
            dao = InMemoryTestGymDao(),
            sessionPreferences = SessionPreferences(dataStore)
        )
        return WorkoutPlanViewModel(repository)
    }

    @Test
    fun openWizard_setsWizardOpenAndStepOne() = runTest {
        val viewModel = createViewModel(backgroundScope)

        viewModel.openWizard()
        val state = viewModel.uiState.value

        assertTrue(state.isWizardOpen)
        assertEquals(1, state.wizardStep)
    }

    @Test
    fun toggleDaySelection_andGoToNextStepAdvancesToStepTwoAndPreservesDraftNamesOnBack() = runTest {
        val viewModel = createViewModel(backgroundScope)

        viewModel.openWizard()
        viewModel.toggleDaySelection(1)
        viewModel.toggleDaySelection(2)
        assertEquals(setOf(1, 2), viewModel.uiState.value.selectedDays)

        viewModel.goToNextStep()
        assertEquals(2, viewModel.uiState.value.wizardStep)

        viewModel.updateDraftPlanName(1, "Pull Day")
        viewModel.updateDraftPlanName(2, "Push Day")
        viewModel.goToPreviousStep()

        val stateAfterBack = viewModel.uiState.value
        assertEquals(1, stateAfterBack.wizardStep)
        assertEquals("Pull Day", stateAfterBack.draftPlanNames[1])
        assertEquals("Push Day", stateAfterBack.draftPlanNames[2])
    }

    @Test
    fun requestAndConfirmCancelWizard_showsDialogThenClosesAndResetsDraftState() = runTest {
        val viewModel = createViewModel(backgroundScope)

        viewModel.openWizard()
        viewModel.toggleDaySelection(1)
        viewModel.updateDraftPlanName(1, "Pull Day")

        viewModel.requestCancelWizard()
        assertTrue(viewModel.uiState.value.showDiscardDialog)

        viewModel.confirmDiscardWizard()
        val resetState = viewModel.uiState.value
        assertFalse(resetState.isWizardOpen)
        assertFalse(resetState.showDiscardDialog)
        assertTrue(resetState.selectedDays.isEmpty())
        assertTrue(resetState.draftPlanNames.isEmpty())
    }

    @Test
    fun saveWorkoutPlan_persistsSelectedDayNamesAndMarksUnselectedDaysAsRestDay() = runTest {
        val viewModel = createViewModel(backgroundScope)

        viewModel.openWizard()
        viewModel.toggleDaySelection(1)
        viewModel.toggleDaySelection(2)
        viewModel.goToNextStep()
        viewModel.updateDraftPlanName(1, "Pull Day")
        viewModel.updateDraftPlanName(2, "Push Day")

        viewModel.saveWorkoutPlan()

        val state = viewModel.uiState.value
        assertFalse(state.isWizardOpen)
        val plansByDay = state.weeklyPlan.associateBy { it.dayOfWeek }

        assertFalse(plansByDay.getValue(1).isRestDay)
        assertEquals("Pull Day", plansByDay.getValue(1).planName)

        assertFalse(plansByDay.getValue(2).isRestDay)
        assertEquals("Push Day", plansByDay.getValue(2).planName)

        listOf(0, 3, 4, 5, 6).forEach { day ->
            assertTrue(plansByDay.getValue(day).isRestDay)
            assertEquals("Rest Day", plansByDay.getValue(day).planName)
        }
    }
}

internal class InMemoryTestGymDao : GymDao {

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
        val updated = sessionsState.value.toMutableMap()
        updated[id] = session.copy(sessionId = id)
        sessionsState.value = updated
        return id
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

    override suspend fun getSessionById(sessionId: Long): WorkoutSessionEntity? {
        return sessionsState.value[sessionId]
    }

    override fun observeSessionsByDate(dateEpochDay: Long): Flow<List<WorkoutSessionEntity>> {
        return sessionsState.map { map ->
            map.values.filter { it.dateEpochDay == dateEpochDay }.sortedByDescending { it.startTimeMillis }
        }
    }

    override fun observeAllSessions(): Flow<List<WorkoutSessionEntity>> {
        return sessionsState.map { map -> map.values.sortedByDescending { it.startTimeMillis } }
    }

    override fun observeCaloriesByDate(dateEpochDay: Long): Flow<Int> {
        return sessionsState.map { map ->
            map.values.filter { it.dateEpochDay == dateEpochDay }.sumOf { it.caloriesBurned }
        }
    }

    override suspend fun insertSet(workoutSet: WorkoutSetEntity): Long {
        val id = if (workoutSet.setId == 0L) nextSetId++ else workoutSet.setId
        val updated = setsState.value.toMutableMap()
        updated[id] = workoutSet.copy(setId = id)
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
            map.values.filter { it.sessionId == sessionId }.sortedBy { it.setOrder }
        }
    }

    override suspend fun getSetsForSession(sessionId: Long): List<WorkoutSetEntity> {
        return setsState.value.values.filter { it.sessionId == sessionId }.sortedBy { it.setOrder }
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
        return setsState.map { map ->
            map.values.map { it.exerciseName }.distinct().sorted()
        }
    }

    override fun observeAllSets(): Flow<List<WorkoutSetEntity>> {
        return setsState.map { map ->
            map.values.sortedBy { it.timestampMillis }
        }
    }
}

