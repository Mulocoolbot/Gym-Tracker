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
import com.mulocoolbot.gymtracker.ui.plan.WorkoutPlanSubPage
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
    fun tc_plan2_01_initial_state_shows_empty_no_workout_plan() = runTest {
        val viewModel = createViewModel(backgroundScope)

        val state = viewModel.uiState.value
        assertFalse(state.hasSavedPlan)
        assertEquals(WorkoutPlanSubPage.EMPTY, state.subPage)
        assertEquals(7, state.savedDays.size)
        assertTrue(state.savedDays.all { it.isRestDay })
    }

    @Test
    fun tc_plan2_02_and_03_select_days_boundary_and_advance_to_input_page() = runTest {
        val viewModel = createViewModel(backgroundScope)

        viewModel.startCreateOrEditPlan()
        assertEquals(WorkoutPlanSubPage.SELECT_DAYS, viewModel.uiState.value.subPage)
        assertTrue(viewModel.uiState.value.selectedDays.isEmpty())

        viewModel.goToInputPlanPage()
        assertEquals(WorkoutPlanSubPage.SELECT_DAYS, viewModel.uiState.value.subPage)

        viewModel.toggleDaySelection(1)
        viewModel.toggleDaySelection(2)
        assertEquals(setOf(1, 2), viewModel.uiState.value.selectedDays)

        viewModel.goToInputPlanPage()
        assertEquals(WorkoutPlanSubPage.INPUT_PLAN, viewModel.uiState.value.subPage)
    }

    @Test
    fun tc_plan2_04_edit_days_button_returns_to_page_1_preserving_draft_strings() = runTest {
        val viewModel = createViewModel(backgroundScope)

        viewModel.startCreateOrEditPlan()
        viewModel.toggleDaySelection(1)
        viewModel.toggleDaySelection(2)
        viewModel.goToInputPlanPage()
        assertEquals(WorkoutPlanSubPage.INPUT_PLAN, viewModel.uiState.value.subPage)

        viewModel.updateDraftPlanName(1, "Pull Day")
        viewModel.goToEditDaysPage()
        assertEquals(WorkoutPlanSubPage.SELECT_DAYS, viewModel.uiState.value.subPage)
        assertEquals(setOf(1, 2), viewModel.uiState.value.selectedDays)

        viewModel.goToInputPlanPage()
        assertEquals(WorkoutPlanSubPage.INPUT_PLAN, viewModel.uiState.value.subPage)
        assertEquals("Pull Day", viewModel.uiState.value.draftPlanNames[1])
    }

    @Test
    fun tc_plan2_05_cancel_button_opens_discard_modal_and_returns_to_empty_or_saved() = runTest {
        val viewModel = createViewModel(backgroundScope)

        viewModel.startCreateOrEditPlan()
        viewModel.toggleDaySelection(1)
        viewModel.requestCancel()
        assertTrue(viewModel.uiState.value.showDiscardDialog)

        viewModel.dismissDiscardDialog()
        assertFalse(viewModel.uiState.value.showDiscardDialog)
        assertEquals(WorkoutPlanSubPage.SELECT_DAYS, viewModel.uiState.value.subPage)

        viewModel.goToInputPlanPage()
        viewModel.updateDraftPlanName(1, "Pull Day")
        viewModel.requestCancel()
        assertTrue(viewModel.uiState.value.showDiscardDialog)

        viewModel.confirmDiscard()
        val stateAfterDiscard = viewModel.uiState.value
        assertFalse(stateAfterDiscard.showDiscardDialog)
        assertFalse(stateAfterDiscard.hasSavedPlan)
        assertEquals(WorkoutPlanSubPage.EMPTY, stateAfterDiscard.subPage)
        assertTrue(stateAfterDiscard.selectedDays.isEmpty())
        assertTrue(stateAfterDiscard.draftPlanNames.isEmpty())
    }

    @Test
    fun tc_plan2_06_save_persists_plan_and_transitions_to_saved_page() = runTest {
        val viewModel = createViewModel(backgroundScope)

        viewModel.startCreateOrEditPlan()
        viewModel.toggleDaySelection(1)
        viewModel.toggleDaySelection(2)
        viewModel.goToInputPlanPage()
        viewModel.updateDraftPlanName(1, "Pull Day")
        viewModel.updateDraftPlanName(2, "Push Day")

        viewModel.saveWorkoutPlan()

        val state = viewModel.uiState.value
        assertTrue(state.hasSavedPlan)
        assertEquals(WorkoutPlanSubPage.SAVED, state.subPage)
        val plansByDay = state.savedDays.associateBy { it.dayOfWeek }

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
        val updated = sessionsState.value.toMutableMap()
        updated[id] = session.copy(sessionId = id)
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
        setsState.value = setsState.value.filterValues { it.sessionId != session.sessionId }
    }

    override suspend fun getSessionById(sessionId: Long): WorkoutSessionEntity? {
        return sessionsState.value[sessionId]
    }

    override suspend fun getAllSessions(): List<WorkoutSessionEntity> {
        return sessionsState.value.values.sortedBy { it.sessionId }
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

    override suspend fun getAllSets(): List<WorkoutSetEntity> {
        return setsState.value.values.sortedBy { it.setId }
    }
}
