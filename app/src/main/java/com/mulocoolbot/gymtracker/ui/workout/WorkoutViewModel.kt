package com.mulocoolbot.gymtracker.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mulocoolbot.gymtracker.data.local.ExerciseHistoryEntity
import com.mulocoolbot.gymtracker.data.local.UserProfileEntity
import com.mulocoolbot.gymtracker.data.local.WorkoutPlanEntity
import com.mulocoolbot.gymtracker.data.local.WorkoutSetEntity
import com.mulocoolbot.gymtracker.data.repository.GymRepository
import com.mulocoolbot.gymtracker.timer.RestTimerController
import com.mulocoolbot.gymtracker.timer.RestTimerState
import com.mulocoolbot.gymtracker.util.WorkoutCalculator
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class DraftWorkoutSet(
    val id: Long = 0L,
    val exerciseName: String,
    val muscleGroup: String = "LATS",
    val setType: String,
    val setLabel: String,
    val setOrder: Int,
    val weightKg: Double,
    val reps: Int,
    val e1rm: Double,
    val timestampMillis: Long
)

data class WorkoutUiState(
    val isWorkoutActive: Boolean = false,
    val todayPlanName: String = "Open Workout",
    val elapsedSeconds: Long = 0L,
    val liveCaloriesBurned: Int = 0,
    val exerciseInput: String = "",
    val selectedMuscleGroup: String = "LATS",
    val weightInput: String = "60",
    val repsInput: String = "10",
    val selectedSetType: String = SET_TYPE_NORMAL,
    val nextNormalSetNumber: Int = 1,
    val activeSessionSets: List<DraftWorkoutSet> = emptyList(),
    val todayCompletedSets: List<WorkoutSetEntity> = emptyList(),
    val exerciseSuggestions: List<ExerciseHistoryEntity> = emptyList(),
    val showFinishDialog: Boolean = false,
    val sessionStartMillis: Long = 0L,
    val restTimerState: RestTimerState = RestTimerState()
) {
    val currentExerciseInput: String
        get() = exerciseInput

    val exerciseHistory: List<ExerciseHistoryEntity>
        get() = exerciseSuggestions

    companion object {
        const val SET_TYPE_WARMUP = "WARMUP"
        const val SET_TYPE_NORMAL = "NORMAL"
        const val SET_TYPE_FINAL = "FINAL"
    }
}

val WorkoutSetEntity.setLabel: String
    get() = when (setType.uppercase(Locale.US)) {
        WorkoutUiState.SET_TYPE_WARMUP -> "Warmup"
        WorkoutUiState.SET_TYPE_FINAL -> "Final Set"
        else -> "Set $setOrder"
    }

private data class ActiveSessionDraftState(
    val isWorkoutActive: Boolean = false,
    val sessionStartMillis: Long = 0L,
    val elapsedSeconds: Long = 0L,
    val liveCaloriesBurned: Int = 0,
    val exerciseInput: String = "",
    val selectedMuscleGroup: String = "LATS",
    val weightInput: String = "60",
    val repsInput: String = "10",
    val selectedSetType: String = WorkoutUiState.SET_TYPE_NORMAL,
    val activeSessionSets: List<DraftWorkoutSet> = emptyList(),
    val showFinishDialog: Boolean = false
)

private data class RepositoryWorkoutContext(
    val profile: UserProfileEntity?,
    val weeklyPlan: List<WorkoutPlanEntity>,
    val history: List<ExerciseHistoryEntity>,
    val todaySets: List<WorkoutSetEntity>
)

class WorkoutViewModel(
    private val repository: GymRepository,
    private val timerController: RestTimerController = RestTimerController.instance,
    private val todayEpochDay: Long = LocalDate.now().toEpochDay()
) : ViewModel() {

    private val sessionDraft = MutableStateFlow(ActiveSessionDraftState())
    private val tickerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var elapsedTickerJob: Job? = null
    private var nextDraftSetId: Long = 1L
    private var latestUserWeightKg: Double = 70.0

    private val repositoryContext = combine(
        repository.userProfile,
        repository.weeklyPlan,
        repository.exerciseHistory,
        repository.observeTodayCompletedSets(todayEpochDay)
    ) { profile, plans, history, todaySets ->
        latestUserWeightKg = profile?.weightKg ?: 70.0
        RepositoryWorkoutContext(
            profile = profile,
            weeklyPlan = plans,
            history = history,
            todaySets = todaySets
        )
    }

    val uiState: StateFlow<WorkoutUiState> = combine(
        sessionDraft,
        repositoryContext,
        timerController.state
    ) { draft, repoContext, timerState ->
        val dayOfWeek = LocalDate.ofEpochDay(todayEpochDay).dayOfWeek.value % 7
        val scheduledPlan = repoContext.weeklyPlan.firstOrNull { it.dayOfWeek == dayOfWeek }
        val resolvedPlanName = if (scheduledPlan != null && !scheduledPlan.isRestDay && scheduledPlan.planName.isNotBlank()) {
            scheduledPlan.planName
        } else {
            "Open Workout"
        }

        val nextNormal = computeNextNormalSetNumber(
            exerciseInput = draft.exerciseInput,
            sets = draft.activeSessionSets
        )

        WorkoutUiState(
            isWorkoutActive = draft.isWorkoutActive,
            todayPlanName = resolvedPlanName,
            elapsedSeconds = draft.elapsedSeconds,
            liveCaloriesBurned = draft.liveCaloriesBurned,
            exerciseInput = draft.exerciseInput,
            selectedMuscleGroup = draft.selectedMuscleGroup,
            weightInput = draft.weightInput,
            repsInput = draft.repsInput,
            selectedSetType = draft.selectedSetType,
            nextNormalSetNumber = nextNormal,
            activeSessionSets = draft.activeSessionSets,
            todayCompletedSets = repoContext.todaySets,
            exerciseSuggestions = repoContext.history,
            showFinishDialog = draft.showFinishDialog,
            sessionStartMillis = draft.sessionStartMillis,
            restTimerState = timerState
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = WorkoutUiState()
    )

    fun startWorkout(currentMillis: Long = System.currentTimeMillis()) {
        elapsedTickerJob?.cancel()
        sessionDraft.update { current ->
            current.copy(
                isWorkoutActive = true,
                sessionStartMillis = currentMillis,
                elapsedSeconds = 0L,
                liveCaloriesBurned = 0,
                activeSessionSets = emptyList(),
                showFinishDialog = false
            )
        }
        elapsedTickerJob = tickerScope.launch {
            while (isActive && sessionDraft.value.isWorkoutActive) {
                delay(1_000L)
                val startMillis = sessionDraft.value.sessionStartMillis
                if (startMillis > 0L) {
                    val nowMillis = System.currentTimeMillis()
                    val elapsed = ((nowMillis - startMillis) / 1_000L).coerceAtLeast(0L)
                    val calories = WorkoutCalculator.calculateCalories(
                        weightKg = currentUserWeightKg(),
                        startMillis = startMillis,
                        endMillis = nowMillis
                    )
                    sessionDraft.update {
                        it.copy(
                            elapsedSeconds = elapsed,
                            liveCaloriesBurned = calories
                        )
                    }
                }
            }
        }
    }

    fun onExerciseNameChanged(raw: String) {
        val normalized = WorkoutCalculator.normalizeExerciseName(raw)
        val matchedSuggestion = uiState.value.exerciseSuggestions.firstOrNull {
            it.normalizedName.equals(normalized, ignoreCase = true)
        }
        sessionDraft.update { current ->
            current.copy(
                exerciseInput = raw,
                selectedMuscleGroup = matchedSuggestion?.muscleGroup
                    ?.takeIf { it.isNotBlank() }
                    ?: current.selectedMuscleGroup
            )
        }
    }

    fun updateExerciseInput(input: String) {
        onExerciseNameChanged(input)
    }

    fun onExerciseInputChange(input: String) {
        onExerciseNameChanged(input)
    }

    fun selectExerciseSuggestion(item: ExerciseHistoryEntity) {
        sessionDraft.update { current ->
            current.copy(
                exerciseInput = item.normalizedName,
                selectedMuscleGroup = item.muscleGroup.ifBlank { current.selectedMuscleGroup }
            )
        }
    }

    fun selectMuscleGroup(muscle: String) {
        if (muscle.isBlank()) return
        sessionDraft.update { it.copy(selectedMuscleGroup = muscle) }
    }

    fun updateWeightInput(input: String) {
        sessionDraft.update { it.copy(weightInput = input) }
    }

    fun onWeightInputChange(input: String) {
        updateWeightInput(input)
    }

    fun stepWeight(deltaKg: Double) {
        sessionDraft.update { current ->
            val currentVal = current.weightInput.trim().toDoubleOrNull() ?: 0.0
            val nextVal = (currentVal + deltaKg).coerceAtLeast(0.0)
            current.copy(weightInput = formatWeightNumber(nextVal))
        }
    }

    fun updateRepsInput(input: String) {
        sessionDraft.update { it.copy(repsInput = input) }
    }

    fun onRepsInputChange(input: String) {
        updateRepsInput(input)
    }

    fun stepReps(deltaReps: Int) {
        sessionDraft.update { current ->
            val currentVal = current.repsInput.trim().toIntOrNull() ?: 0
            val nextVal = (currentVal + deltaReps).coerceAtLeast(1)
            current.copy(repsInput = nextVal.toString())
        }
    }

    fun selectSetType(setType: String) {
        val normalizedType = setType.uppercase(Locale.US)
        if (normalizedType in VALID_SET_TYPES) {
            sessionDraft.update { it.copy(selectedSetType = normalizedType) }
        }
    }

    fun onSelectSetType(setType: String) {
        selectSetType(setType)
    }

    fun addSet(
        onTriggerServiceTimer: (durationMillis: Long, label: String) -> Unit = { _, _ -> }
    ) {
        val current = sessionDraft.value
        val normalizedExercise = WorkoutCalculator.normalizeExerciseName(current.exerciseInput)
        val weightKg = current.weightInput.trim().toDoubleOrNull() ?: return
        val reps = current.repsInput.trim().toIntOrNull() ?: return
        if (normalizedExercise.isEmpty() || weightKg <= 0.0 || reps <= 0) return

        val nextNormalNumber = computeNextNormalSetNumber(
            exerciseInput = normalizedExercise,
            sets = current.activeSessionSets
        )
        val setLabel = buildSetLabel(
            setType = current.selectedSetType,
            normalSetNumber = nextNormalNumber
        )
        val e1rm = WorkoutCalculator.calculateE1RM(weightKg = weightKg, reps = reps)
        val nowMillis = System.currentTimeMillis()
        val setId = nextDraftSetId++

        val newSet = DraftWorkoutSet(
            id = setId,
            exerciseName = normalizedExercise,
            muscleGroup = current.selectedMuscleGroup,
            setType = current.selectedSetType,
            setLabel = setLabel,
            setOrder = current.activeSessionSets.size + 1,
            weightKg = weightKg,
            reps = reps,
            e1rm = e1rm,
            timestampMillis = nowMillis
        )

        sessionDraft.update { state ->
            state.copy(
                exerciseInput = normalizedExercise,
                activeSessionSets = state.activeSessionSets + newSet
            )
        }

        val restDurationMillis = WorkoutCalculator.getRestDurationMillis(current.selectedSetType)
        val timerLabel = "$setLabel · Rest Timer"
        timerController.startTimer(
            durationMillis = restDurationMillis,
            label = timerLabel,
            currentMillis = nowMillis
        )
        onTriggerServiceTimer(restDurationMillis, timerLabel)
    }

    @JvmName("addSetWithDurationOnly")
    fun addSet(onTriggerServiceTimer: (Long) -> Unit) {
        addSet { durationMillis, _ -> onTriggerServiceTimer(durationMillis) }
    }

    fun deleteSessionSet(setId: Long) {
        sessionDraft.update { current ->
            val filtered = current.activeSessionSets.filterNot { it.id == setId }
            val normalCounters = mutableMapOf<String, Int>()
            val renumbered = filtered.mapIndexed { index, set ->
                val updatedLabel = if (set.setType == WorkoutUiState.SET_TYPE_NORMAL) {
                    val nextNum = (normalCounters[set.exerciseName] ?: 0) + 1
                    normalCounters[set.exerciseName] = nextNum
                    "Set $nextNum"
                } else {
                    set.setLabel
                }
                set.copy(
                    setLabel = updatedLabel,
                    setOrder = index + 1
                )
            }
            current.copy(activeSessionSets = renumbered)
        }
    }

    fun adjustRestTimer(
        deltaSeconds: Int,
        onAdjustServiceTimer: (Int) -> Unit = {}
    ) {
        timerController.adjustTimer(deltaSeconds = deltaSeconds)
        onAdjustServiceTimer(deltaSeconds)
    }

    fun skipOrDismissRestTimer(onStopServiceTimer: () -> Unit = {}) {
        timerController.dismissOrSkip()
        onStopServiceTimer()
    }

    fun requestFinishWorkout() {
        sessionDraft.update { it.copy(showFinishDialog = true) }
    }

    fun dismissFinishDialog() {
        sessionDraft.update { it.copy(showFinishDialog = false) }
    }

    fun confirmFinishWorkout(onStopServiceTimer: () -> Unit = {}) {
        finishAndPersistWorkout(
            planName = uiState.value.todayPlanName,
            onStopServiceTimer = onStopServiceTimer
        )
    }

    fun stopWorkout(
        planName: String = uiState.value.todayPlanName,
        onStopServiceTimer: () -> Unit = {}
    ) {
        finishAndPersistWorkout(
            planName = planName,
            onStopServiceTimer = onStopServiceTimer
        )
    }

    private fun finishAndPersistWorkout(
        planName: String,
        onStopServiceTimer: () -> Unit
    ) {
        elapsedTickerJob?.cancel()
        val completedDraft = sessionDraft.value
        val startMillis = completedDraft.sessionStartMillis.takeIf { it > 0L }
            ?: System.currentTimeMillis()
        val endMillis = System.currentTimeMillis().coerceAtLeast(startMillis + 60_000L)
        val loggedSets = completedDraft.activeSessionSets

        timerController.dismissOrSkip()
        onStopServiceTimer()

        sessionDraft.value = ActiveSessionDraftState()

        if (loggedSets.isNotEmpty()) {
            val setEntities = loggedSets.mapIndexed { index, draftSet ->
                WorkoutSetEntity(
                    sessionId = 0L,
                    exerciseName = draftSet.exerciseName,
                    setType = draftSet.setType,
                    setOrder = index + 1,
                    weightKg = draftSet.weightKg,
                    reps = draftSet.reps,
                    e1rm = draftSet.e1rm,
                    timestampMillis = draftSet.timestampMillis
                )
            }
            val muscleGroupsByExercise = loggedSets.associate {
                it.exerciseName to it.muscleGroup
            }
            viewModelScope.launch {
                repository.recordWorkoutSession(
                    planName = planName,
                    startMillis = startMillis,
                    endMillis = endMillis,
                    sets = setEntities,
                    todayEpochDay = todayEpochDay,
                    muscleGroupsByExercise = muscleGroupsByExercise
                )
            }
        }
    }

    override fun onCleared() {
        elapsedTickerJob?.cancel()
        tickerScope.cancel()
        super.onCleared()
    }

    private fun currentUserWeightKg(): Double = latestUserWeightKg

    private fun computeNextNormalSetNumber(
        exerciseInput: String,
        sets: List<DraftWorkoutSet>
    ): Int {
        val normalized = WorkoutCalculator.normalizeExerciseName(exerciseInput)
        if (normalized.isEmpty()) return 1
        val existingNormalCount = sets.count {
            it.setType == WorkoutUiState.SET_TYPE_NORMAL && it.exerciseName == normalized
        }
        return existingNormalCount + 1
    }

    private fun buildSetLabel(setType: String, normalSetNumber: Int): String = when (setType) {
        WorkoutUiState.SET_TYPE_WARMUP -> "Warmup"
        WorkoutUiState.SET_TYPE_FINAL -> "Final Set"
        else -> "Set $normalSetNumber"
    }

    private fun formatWeightNumber(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            "%.1f".format(Locale.US, value)
        }
    }

    companion object {
        private val VALID_SET_TYPES = setOf(
            WorkoutUiState.SET_TYPE_WARMUP,
            WorkoutUiState.SET_TYPE_NORMAL,
            WorkoutUiState.SET_TYPE_FINAL
        )

        fun formatElapsedHHMMSS(elapsedSeconds: Long): String {
            val safeSeconds = elapsedSeconds.coerceAtLeast(0L)
            val hours = safeSeconds / 3600L
            val minutes = (safeSeconds % 3600L) / 60L
            val seconds = safeSeconds % 60L
            return "%02d:%02d:%02d".format(Locale.US, hours, minutes, seconds)
        }

        fun factory(
            repository: GymRepository,
            timerController: RestTimerController = RestTimerController.instance
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return WorkoutViewModel(
                        repository = repository,
                        timerController = timerController
                    ) as T
                }
            }
        }
    }
}
