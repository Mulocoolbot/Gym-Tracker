package com.mulocoolbot.gymtracker.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mulocoolbot.gymtracker.data.local.ExerciseHistoryEntity
import com.mulocoolbot.gymtracker.data.local.UserProfileEntity
import com.mulocoolbot.gymtracker.data.local.WorkoutPlanEntity
import com.mulocoolbot.gymtracker.data.local.WorkoutSetEntity
import com.mulocoolbot.gymtracker.data.repository.GymRepository
import com.mulocoolbot.gymtracker.timer.RestTimerController
import com.mulocoolbot.gymtracker.timer.RestTimerState
import com.mulocoolbot.gymtracker.timer.WorkoutLiveIslandState
import com.mulocoolbot.gymtracker.timer.resolveWorkoutLiveIslandState
import com.mulocoolbot.gymtracker.util.WorkoutCalculator
import com.mulocoolbot.gymtracker.util.currentEpochMillis
import com.mulocoolbot.gymtracker.util.formatCompactNumber
import com.mulocoolbot.gymtracker.util.formatTwoDigits
import com.mulocoolbot.gymtracker.util.parseDecimalInput
import com.mulocoolbot.gymtracker.util.todayLocalDate
import kotlin.jvm.JvmName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

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
    get() = when (setType.uppercase()) {
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
    val showFinishDialog: Boolean = false,
    val nextSetId: Long = 1L
)

private data class RepositoryWorkoutContext(
    val profile: UserProfileEntity?,
    val weeklyPlan: List<WorkoutPlanEntity>,
    val history: List<ExerciseHistoryEntity>,
    val todaySets: List<WorkoutSetEntity>
)

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutViewModel internal constructor(
    private val repository: GymRepository,
    private val timerController: RestTimerController,
    private val todayEpochDayProvider: () -> Long
) : ViewModel() {

    constructor(
        repository: GymRepository,
        timerController: RestTimerController = RestTimerController.instance
    ) : this(
        repository = repository,
        timerController = timerController,
        todayEpochDayProvider = { todayLocalDate().toEpochDays().toLong() }
    )

    constructor(
        repository: GymRepository,
        timerController: RestTimerController = RestTimerController.instance,
        todayEpochDay: Long
    ) : this(
        repository = repository,
        timerController = timerController,
        todayEpochDayProvider = { todayEpochDay }
    )

    private val sessionDraft = MutableStateFlow(ActiveSessionDraftState())
    private val activeEpochDayFlow = MutableStateFlow(todayEpochDayProvider())
    private val latestUserWeightKg = MutableStateFlow(70.0)
    private var elapsedTickerJob: Job? = null

    private val repositoryContext = combine(
        repository.userProfile,
        repository.weeklyPlan,
        repository.exerciseHistory,
        activeEpochDayFlow.flatMapLatest { repository.observeTodayCompletedSets(it) }
    ) { profile, plans, history, todaySets ->
        latestUserWeightKg.value = profile?.weightKg ?: 70.0
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
        timerController.state,
        activeEpochDayFlow
    ) { draft, repoContext, timerState, activeEpochDay ->
        val dayOfWeek = LocalDate.fromEpochDays(activeEpochDay.toInt()).dayOfWeek.isoDayNumber % 7
        val scheduledPlan = repoContext.weeklyPlan.firstOrNull { it.dayOfWeek == dayOfWeek }
        val resolvedPlanName =
            if (scheduledPlan != null && !scheduledPlan.isRestDay && scheduledPlan.planName.isNotBlank()) {
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

    val liveIslandState: StateFlow<WorkoutLiveIslandState> = combine(
        sessionDraft,
        timerController.state
    ) { draft, timerState ->
        resolveWorkoutLiveIslandState(
            isWorkoutActive = draft.isWorkoutActive,
            restTimerState = timerState
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = WorkoutLiveIslandState.Inactive
    )

    fun refreshCurrentDayIfNeeded() {
        val currentEpochDay = todayEpochDayProvider()
        if (activeEpochDayFlow.value != currentEpochDay) {
            activeEpochDayFlow.value = currentEpochDay
        }
    }

    fun startWorkout(currentMillis: Long = currentEpochMillis()) {
        refreshCurrentDayIfNeeded()
        var startedNow = false
        sessionDraft.update { current ->
            if (current.isWorkoutActive) {
                current
            } else {
                startedNow = true
                current.copy(
                    isWorkoutActive = true,
                    sessionStartMillis = currentMillis,
                    elapsedSeconds = 0L,
                    liveCaloriesBurned = 0,
                    activeSessionSets = emptyList(),
                    showFinishDialog = false
                )
            }
        }
        if (!startedNow) return

        elapsedTickerJob?.cancel()
        elapsedTickerJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive && sessionDraft.value.isWorkoutActive) {
                delay(1_000L)
                refreshCurrentDayIfNeeded()
                val startMillis = sessionDraft.value.sessionStartMillis
                if (startMillis > 0L && sessionDraft.value.isWorkoutActive) {
                    val nowMillis = currentEpochMillis()
                    val elapsed = ((nowMillis - startMillis) / 1_000L).coerceAtLeast(0L)
                    val calories = WorkoutCalculator.calculateCalories(
                        weightKg = currentUserWeightKg(),
                        startMillis = startMillis,
                        endMillis = nowMillis
                    )
                    sessionDraft.update {
                        if (!it.isWorkoutActive) it else it.copy(
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
            val currentVal = parseDecimalInput(current.weightInput) ?: 0.0
            val nextVal = (currentVal + deltaKg).coerceIn(0.0, MAX_WEIGHT_KG)
            current.copy(weightInput = formatCompactNumber(nextVal))
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
            val currentVal = current.repsInput.trim().toLongOrNull() ?: 0L
            val nextVal = (currentVal + deltaReps.toLong()).coerceIn(1L, MAX_REPS.toLong()).toInt()
            current.copy(repsInput = nextVal.toString())
        }
    }

    fun selectSetType(setType: String) {
        val normalizedType = setType.uppercase()
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
        refreshCurrentDayIfNeeded()
        val nowMillis = currentEpochMillis()
        var pendingTimer: Pair<Long, String>? = null

        sessionDraft.update { state ->
            if (!state.isWorkoutActive) return@update state
            val normalizedExercise = WorkoutCalculator.normalizeExerciseName(state.exerciseInput)
            val weightKg = parseDecimalInput(state.weightInput) ?: return@update state
            val reps = state.repsInput.trim().toIntOrNull() ?: return@update state
            if (normalizedExercise.isEmpty() ||
                !weightKg.isFinite() ||
                weightKg <= 0.0 ||
                weightKg > MAX_WEIGHT_KG ||
                reps !in 1..MAX_REPS
            ) {
                return@update state
            }

            val nextNormalNumber = computeNextNormalSetNumber(
                exerciseInput = normalizedExercise,
                sets = state.activeSessionSets
            )
            val setLabel = buildSetLabel(
                setType = state.selectedSetType,
                normalSetNumber = nextNormalNumber
            )
            val e1rm = WorkoutCalculator.calculateE1RM(weightKg = weightKg, reps = reps)
            val newSet = DraftWorkoutSet(
                id = state.nextSetId,
                exerciseName = normalizedExercise,
                muscleGroup = state.selectedMuscleGroup,
                setType = state.selectedSetType,
                setLabel = setLabel,
                setOrder = state.activeSessionSets.size + 1,
                weightKg = weightKg,
                reps = reps,
                e1rm = e1rm,
                timestampMillis = nowMillis
            )
            val restDurationMillis = WorkoutCalculator.getRestDurationMillis(state.selectedSetType)
            pendingTimer = restDurationMillis to "$setLabel · Rest Timer"
            state.copy(
                exerciseInput = normalizedExercise,
                activeSessionSets = state.activeSessionSets + newSet,
                nextSetId = state.nextSetId + 1L
            )
        }

        val (restDurationMillis, timerLabel) = pendingTimer ?: return
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
        if (!timerController.state.value.isRunning) return
        timerController.adjustTimer(deltaSeconds = deltaSeconds)
        onAdjustServiceTimer(deltaSeconds)
    }

    fun skipOrDismissRestTimer(onStopServiceTimer: () -> Unit = {}) {
        timerController.dismissOrSkip()
        onStopServiceTimer()
    }

    fun onStopRestTimerFromIsland(onStopRestTimerService: () -> Unit = {}) {
        timerController.dismissOrSkip()
        onStopRestTimerService()
    }

    fun onStopWorkoutFromIsland(
        planName: String = uiState.value.todayPlanName,
        onStopServiceTimer: () -> Unit = {}
    ) {
        if (!sessionDraft.value.isWorkoutActive) return
        finishAndPersistWorkout(
            planName = planName,
            onStopServiceTimer = onStopServiceTimer
        )
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
        val completedDraft = sessionDraft.getAndUpdate { current ->
            ActiveSessionDraftState(nextSetId = current.nextSetId)
        }
        if (!completedDraft.isWorkoutActive) return

        elapsedTickerJob?.cancel()
        val startMillis = completedDraft.sessionStartMillis.takeIf { it > 0L }
            ?: currentEpochMillis()
        val endMillis = currentEpochMillis().coerceAtLeast(startMillis + 60_000L)
        val loggedSets = completedDraft.activeSessionSets

        timerController.dismissOrSkip()
        onStopServiceTimer()

        if (loggedSets.isNotEmpty()) {
            val persistEpochDay = todayEpochDayProvider()
            activeEpochDayFlow.value = persistEpochDay
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
                withContext(NonCancellable) {
                    repository.recordWorkoutSession(
                        planName = planName,
                        startMillis = startMillis,
                        endMillis = endMillis,
                        sets = setEntities,
                        todayEpochDay = persistEpochDay,
                        muscleGroupsByExercise = muscleGroupsByExercise
                    )
                }
            }
        }
    }

    override fun onCleared() {
        elapsedTickerJob?.cancel()
        super.onCleared()
    }

    private fun currentUserWeightKg(): Double = latestUserWeightKg.value

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

    companion object {
        private const val MAX_WEIGHT_KG = 10_000.0
        private const val MAX_REPS = 10_000
        private val VALID_SET_TYPES = setOf(
            WorkoutUiState.SET_TYPE_WARMUP,
            WorkoutUiState.SET_TYPE_NORMAL,
            WorkoutUiState.SET_TYPE_FINAL
        )

        fun formatElapsedHHMMSS(elapsedSeconds: Long): String {
            val safeSeconds = elapsedSeconds.coerceAtLeast(0L)
            val hours = (safeSeconds / 3600L).toInt()
            val minutes = ((safeSeconds % 3600L) / 60L).toInt()
            val seconds = (safeSeconds % 60L).toInt()
            return "${formatTwoDigits(hours)}:${formatTwoDigits(minutes)}:${formatTwoDigits(seconds)}"
        }

        fun factory(
            repository: GymRepository,
            timerController: RestTimerController = RestTimerController.instance
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                WorkoutViewModel(
                    repository = repository,
                    timerController = timerController
                )
            }
        }
    }
}
