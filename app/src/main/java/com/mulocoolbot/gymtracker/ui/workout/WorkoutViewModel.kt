package com.mulocoolbot.gymtracker.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mulocoolbot.gymtracker.data.local.ExerciseHistoryEntity
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
    val exerciseName: String,
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
    val sessionStartMillis: Long = 0L,
    val elapsedSeconds: Long = 0L,
    val currentExerciseInput: String = "",
    val weightInput: String = "",
    val repsInput: String = "",
    val selectedSetType: String = SET_TYPE_NORMAL,
    val activeSessionSets: List<DraftWorkoutSet> = emptyList(),
    val todayCompletedSets: List<DraftWorkoutSet> = emptyList(),
    val exerciseHistory: List<ExerciseHistoryEntity> = emptyList(),
    val restTimerState: RestTimerState = RestTimerState(),
    val nextNormalSetNumber: Int = 1
) {
    companion object {
        const val SET_TYPE_WARMUP = "WARMUP"
        const val SET_TYPE_NORMAL = "NORMAL"
        const val SET_TYPE_FINAL = "FINAL"
    }
}

private data class ActiveSessionDraftState(
    val isWorkoutActive: Boolean = false,
    val sessionStartMillis: Long = 0L,
    val elapsedSeconds: Long = 0L,
    val currentExerciseInput: String = "",
    val weightInput: String = "",
    val repsInput: String = "",
    val selectedSetType: String = WorkoutUiState.SET_TYPE_NORMAL,
    val activeSessionSets: List<DraftWorkoutSet> = emptyList()
)

class WorkoutViewModel(
    private val repository: GymRepository,
    private val timerController: RestTimerController = RestTimerController.instance,
    private val todayEpochDay: Long = LocalDate.now().toEpochDay()
) : ViewModel() {

    private val sessionDraft = MutableStateFlow(ActiveSessionDraftState())
    private val tickerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var elapsedTickerJob: Job? = null

    val uiState: StateFlow<WorkoutUiState> = combine(
        sessionDraft,
        repository.exerciseHistory,
        repository.observeTodayCompletedSets(todayEpochDay),
        timerController.state
    ) { draft, history, todaySets, timerState ->
        val nextNormal = computeNextNormalSetNumber(
            exerciseInput = draft.currentExerciseInput,
            sets = draft.activeSessionSets
        )
        WorkoutUiState(
            isWorkoutActive = draft.isWorkoutActive,
            sessionStartMillis = draft.sessionStartMillis,
            elapsedSeconds = draft.elapsedSeconds,
            currentExerciseInput = draft.currentExerciseInput,
            weightInput = draft.weightInput,
            repsInput = draft.repsInput,
            selectedSetType = draft.selectedSetType,
            activeSessionSets = draft.activeSessionSets,
            todayCompletedSets = mapCompletedEntitiesToDisplaySets(todaySets),
            exerciseHistory = history,
            restTimerState = timerState,
            nextNormalSetNumber = nextNormal
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = WorkoutUiState()
    )

    fun updateExerciseInput(input: String) {
        sessionDraft.update { it.copy(currentExerciseInput = input) }
    }

    fun onExerciseInputChange(input: String) {
        updateExerciseInput(input)
    }

    fun updateWeightInput(input: String) {
        sessionDraft.update { it.copy(weightInput = input) }
    }

    fun onWeightInputChange(input: String) {
        updateWeightInput(input)
    }

    fun updateRepsInput(input: String) {
        sessionDraft.update { it.copy(repsInput = input) }
    }

    fun onRepsInputChange(input: String) {
        updateRepsInput(input)
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

    fun startWorkout(currentMillis: Long = System.currentTimeMillis()) {
        elapsedTickerJob?.cancel()
        sessionDraft.update { current ->
            current.copy(
                isWorkoutActive = true,
                sessionStartMillis = currentMillis,
                elapsedSeconds = 0L,
                activeSessionSets = emptyList()
            )
        }
        elapsedTickerJob = tickerScope.launch {
            while (isActive && sessionDraft.value.isWorkoutActive) {
                delay(1_000L)
                val startMillis = sessionDraft.value.sessionStartMillis
                if (startMillis > 0L) {
                    val elapsed = ((System.currentTimeMillis() - startMillis) / 1_000L).coerceAtLeast(0L)
                    sessionDraft.update { it.copy(elapsedSeconds = elapsed) }
                }
            }
        }
    }

    fun addSet(onTriggerServiceTimer: (Long) -> Unit = {}) {
        val current = sessionDraft.value
        val normalizedExercise = WorkoutCalculator.normalizeExerciseName(current.currentExerciseInput)
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

        val newSet = DraftWorkoutSet(
            exerciseName = normalizedExercise,
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
                currentExerciseInput = normalizedExercise,
                activeSessionSets = state.activeSessionSets + newSet
            )
        }

        val restDurationMillis = WorkoutCalculator.getRestDurationMillis(current.selectedSetType)
        timerController.startTimer(durationMillis = restDurationMillis, currentMillis = nowMillis)
        onTriggerServiceTimer(restDurationMillis)
    }

    fun skipOrDismissRestTimer(onStopServiceTimer: () -> Unit = {}) {
        timerController.dismissOrSkip()
        onStopServiceTimer()
    }

    fun stopWorkout(
        planName: String = "Workout",
        onStopServiceTimer: () -> Unit = {}
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
            viewModelScope.launch {
                repository.recordWorkoutSession(
                    planName = planName,
                    startMillis = startMillis,
                    endMillis = endMillis,
                    sets = setEntities,
                    todayEpochDay = todayEpochDay
                )
            }
        }
    }

    override fun onCleared() {
        elapsedTickerJob?.cancel()
        tickerScope.cancel()
        super.onCleared()
    }

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

    private fun mapCompletedEntitiesToDisplaySets(
        entities: List<WorkoutSetEntity>
    ): List<DraftWorkoutSet> {
        val normalCountBySessionAndExercise = mutableMapOf<Pair<Long, String>, Int>()
        return entities.map { entity ->
            val label = when (entity.setType.uppercase(Locale.US)) {
                WorkoutUiState.SET_TYPE_WARMUP -> "Warmup"
                WorkoutUiState.SET_TYPE_FINAL -> "Final Set"
                else -> {
                    val key = entity.sessionId to entity.exerciseName
                    val updatedCount = (normalCountBySessionAndExercise[key] ?: 0) + 1
                    normalCountBySessionAndExercise[key] = updatedCount
                    "Set $updatedCount"
                }
            }
            DraftWorkoutSet(
                exerciseName = entity.exerciseName,
                setType = entity.setType,
                setLabel = label,
                setOrder = entity.setOrder,
                weightKg = entity.weightKg,
                reps = entity.reps,
                e1rm = entity.e1rm,
                timestampMillis = entity.timestampMillis
            )
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

