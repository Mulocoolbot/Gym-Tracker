package com.mulocoolbot.gymtracker.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mulocoolbot.gymtracker.data.local.ExerciseHistoryEntity
import com.mulocoolbot.gymtracker.data.local.WorkoutSetEntity
import com.mulocoolbot.gymtracker.data.repository.GymRepository
import com.mulocoolbot.gymtracker.util.WorkoutCalculator
import com.mulocoolbot.gymtracker.util.formatCompactNumber
import com.mulocoolbot.gymtracker.util.formatShortMonthDay
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class HeatmapOrientation {
    FRONT,
    BACK
}

enum class MuscleRegion {
    CHEST,
    SHOULDERS,
    BICEPS,
    QUADS,
    ABS,
    LATS,
    TRICEPS,
    HAMSTRINGS,
    CALVES
}

data class ChartPointUi(
    val index: Int,
    val dateLabel: String,
    val setSummary: String,
    val weightKg: Double,
    val reps: Int,
    val e1rm: Double
)

data class ExerciseStatOption(
    val exerciseName: String,
    val muscleGroup: String,
    val bestE1rm: Double,
    val deltaKgText: String,
    val isDeltaPositive: Boolean,
    val points: List<ChartPointUi>
)

data class MuscleVolumeUi(
    val id: String,
    val displayName: String,
    val orientation: HeatmapOrientation,
    val weeklySets: Int
)

data class ProgressionPoint(
    val weekLabel: String,
    val weightKg: Double,
    val reps: Int,
    val e1rm: Double
)

data class StatsUiState(
    val hasStatisticsData: Boolean = false,
    val exerciseOptions: List<ExerciseStatOption> = emptyList(),
    val selectedExerciseName: String = "",
    val isExerciseDropdownOpen: Boolean = false,
    val selectedPointIndex: Int = 0,
    val heatmapOrientation: HeatmapOrientation = HeatmapOrientation.FRONT,
    val selectedMuscleId: String = "CHEST",
    val muscleVolumes: Map<String, MuscleVolumeUi> = StatsViewModel.buildDefaultMuscleVolumes(),
    val exercises: List<String> = emptyList(),
    val progressionPoints: List<ProgressionPoint> = emptyList(),
    val percentageChange: Double = 0.0,
    val trainedMuscles: Set<MuscleRegion> = emptySet()
) {
    val selectedExercise: String
        get() = selectedExerciseName

    val selectedExerciseOption: ExerciseStatOption?
        get() = exerciseOptions.firstOrNull { it.exerciseName == selectedExerciseName }
            ?: exerciseOptions.firstOrNull()
}

private data class StatsInteractionState(
    val selectedExerciseName: String = "",
    val isExerciseDropdownOpen: Boolean = false,
    val customPointIndex: Int? = null,
    val heatmapOrientation: HeatmapOrientation = HeatmapOrientation.FRONT,
    val selectedMuscleId: String = "CHEST"
)

class StatsViewModel(
    repository: GymRepository
) : ViewModel() {

    private val interactionState = MutableStateFlow(StatsInteractionState())

    val uiState: StateFlow<StatsUiState> = combine(
        repository.observeAllSets(),
        repository.exerciseHistory,
        interactionState
    ) { allSets, history, interaction ->
        val setsByExercise = allSets.groupBy { it.exerciseName }
        val historyByExercise = history.associateBy { it.normalizedName }
        val exerciseNames = (setsByExercise.keys + history.map { it.normalizedName })
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

        val options = exerciseNames.mapNotNull { name ->
            val sets = setsByExercise[name].orEmpty()
            if (sets.isEmpty()) {
                null
            } else {
                buildExerciseStatOption(
                    exerciseName = name,
                    sets = sets,
                    historyItem = historyByExercise[name]
                )
            }
        }

        val optionNames = options.map { it.exerciseName }.toSet()
        val activeExercise = when {
            interaction.selectedExerciseName in optionNames -> interaction.selectedExerciseName
            options.isNotEmpty() -> options.first().exerciseName
            interaction.selectedExerciseName in exerciseNames -> interaction.selectedExerciseName
            exerciseNames.isNotEmpty() -> exerciseNames.first()
            else -> ""
        }

        val activeOption = options.firstOrNull { it.exerciseName == activeExercise }
        val pointsCount = activeOption?.points?.size ?: 0
        val latestPointIndex = (pointsCount - 1).coerceAtLeast(0)
        val resolvedPointIndex = interaction.customPointIndex
            ?.coerceIn(0, latestPointIndex)
            ?: latestPointIndex

        val activeSets = setsByExercise[activeExercise].orEmpty()
        val (legacyPoints, pctChange) = buildProgressionForExercise(activeSets)
        val trainedRegions = inferTrainedMuscles(exerciseNames)
        val volumes = computeMuscleVolumes(
            allSets = allSets,
            historyByExercise = historyByExercise
        )

        StatsUiState(
            hasStatisticsData = allSets.isNotEmpty(),
            exerciseOptions = options,
            selectedExerciseName = activeExercise,
            isExerciseDropdownOpen = interaction.isExerciseDropdownOpen,
            selectedPointIndex = resolvedPointIndex,
            heatmapOrientation = interaction.heatmapOrientation,
            selectedMuscleId = interaction.selectedMuscleId,
            muscleVolumes = volumes,
            exercises = exerciseNames,
            progressionPoints = legacyPoints,
            percentageChange = pctChange,
            trainedMuscles = trainedRegions
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = StatsUiState()
    )

    fun toggleExerciseDropdown() {
        interactionState.update { current ->
            current.copy(isExerciseDropdownOpen = !current.isExerciseDropdownOpen)
        }
    }

    fun dismissExerciseDropdown() {
        interactionState.update { current ->
            current.copy(isExerciseDropdownOpen = false)
        }
    }

    fun selectExercise(exerciseName: String) {
        val option = uiState.value.exerciseOptions.firstOrNull { it.exerciseName == exerciseName }
        val latestIndex = ((option?.points?.size ?: 1) - 1).coerceAtLeast(0)
        interactionState.update { current ->
            current.copy(
                selectedExerciseName = exerciseName,
                selectedPointIndexOrLatest = latestIndex,
                isExerciseDropdownOpen = false
            )
        }
    }

    fun selectChartPoint(index: Int) {
        val activePoints = uiState.value.selectedExerciseOption?.points.orEmpty()
        val clamped = if (activePoints.isEmpty()) {
            index.coerceAtLeast(0)
        } else {
            index.coerceIn(0, activePoints.lastIndex)
        }
        interactionState.update { current ->
            current.copy(customPointIndex = clamped)
        }
    }

    fun switchHeatmapOrientation(orientation: HeatmapOrientation) {
        interactionState.update { current ->
            val currentMuscleMeta = MUSCLE_DEFINITIONS[current.selectedMuscleId]
            val nextMuscleId = if (currentMuscleMeta?.second == orientation) {
                current.selectedMuscleId
            } else {
                if (orientation == HeatmapOrientation.FRONT) "CHEST" else "LATS"
            }
            current.copy(
                heatmapOrientation = orientation,
                selectedMuscleId = nextMuscleId
            )
        }
    }

    fun selectMuscleRegion(muscleId: String) {
        val meta = MUSCLE_DEFINITIONS[muscleId] ?: return
        interactionState.update { current ->
            StatsInteractionState(
                selectedExerciseName = current.selectedExerciseName,
                isExerciseDropdownOpen = current.isExerciseDropdownOpen,
                customPointIndex = current.customPointIndex,
                heatmapOrientation = meta.second,
                selectedMuscleId = muscleId
            )
        }
    }

    private fun StatsInteractionState.copy(
        selectedExerciseName: String = this.selectedExerciseName,
        selectedPointIndexOrLatest: Int,
        isExerciseDropdownOpen: Boolean = this.isExerciseDropdownOpen
    ): StatsInteractionState = StatsInteractionState(
        selectedExerciseName = selectedExerciseName,
        isExerciseDropdownOpen = isExerciseDropdownOpen,
        customPointIndex = selectedPointIndexOrLatest,
        heatmapOrientation = this.heatmapOrientation,
        selectedMuscleId = this.selectedMuscleId
    )

    companion object {
        private val MUSCLE_DEFINITIONS: LinkedHashMap<String, Pair<String, HeatmapOrientation>> = linkedMapOf(
            "CHEST" to ("Chest" to HeatmapOrientation.FRONT),
            "SHOULDERS" to ("Shoulders" to HeatmapOrientation.FRONT),
            "BICEPS" to ("Biceps" to HeatmapOrientation.FRONT),
            "ABS" to ("Core / Abs" to HeatmapOrientation.FRONT),
            "QUADS" to ("Quads" to HeatmapOrientation.FRONT),
            "TRAPS" to ("Traps" to HeatmapOrientation.BACK),
            "REAR_SHOULDERS" to ("Rear Delts" to HeatmapOrientation.BACK),
            "LATS" to ("Lats" to HeatmapOrientation.BACK),
            "TRICEPS" to ("Triceps" to HeatmapOrientation.BACK),
            "LOWER_BACK" to ("Lower Back" to HeatmapOrientation.BACK),
            "GLUTES" to ("Glutes" to HeatmapOrientation.BACK),
            "HAMSTRINGS" to ("Hamstrings" to HeatmapOrientation.BACK),
            "CALVES" to ("Calves" to HeatmapOrientation.BACK)
        )

        fun buildDefaultMuscleVolumes(): Map<String, MuscleVolumeUi> {
            return MUSCLE_DEFINITIONS.mapValues { (id, pair) ->
                MuscleVolumeUi(
                    id = id,
                    displayName = pair.first,
                    orientation = pair.second,
                    weeklySets = 0
                )
            }
        }

        private fun buildExerciseStatOption(
            exerciseName: String,
            sets: List<WorkoutSetEntity>,
            historyItem: ExerciseHistoryEntity?
        ): ExerciseStatOption {
            val sortedSets = sets.sortedBy { it.timestampMillis }
            val chartPoints = sortedSets.mapIndexed { index, set ->
                val resolvedE1rm = resolveFiniteE1rm(set)
                val dateLabel = if (set.timestampMillis >= 86_400_000L) {
                    formatShortMonthDay(set.timestampMillis)
                } else {
                    "Set ${index + 1}"
                }
                val weightText = formatCompactNumber(set.weightKg)
                ChartPointUi(
                    index = index,
                    dateLabel = dateLabel,
                    setSummary = "$weightText kg × ${set.reps}",
                    weightKg = set.weightKg,
                    reps = set.reps,
                    e1rm = resolvedE1rm
                )
            }

            val bestE1rm = chartPoints.maxOfOrNull { it.e1rm } ?: 0.0
            val deltaKg = if (chartPoints.size >= 2) {
                chartPoints.last().e1rm - chartPoints.first().e1rm
            } else {
                0.0
            }
            val roundedDelta = if (deltaKg.isFinite()) {
                (deltaKg.coerceIn(-1_000_000_000.0, 1_000_000_000.0) * 10.0).roundToLong() / 10.0
            } else {
                0.0
            }
            val isPositive = roundedDelta >= 0.0
            val deltaPrefix = if (isPositive) "+" else "-"
            val deltaKgText = "$deltaPrefix${formatCompactNumber(abs(roundedDelta))} kg"

            val resolvedMuscleGroup = resolvePrimaryMuscleId(
                exerciseName = exerciseName,
                storedMuscle = historyItem?.muscleGroup
            )

            return ExerciseStatOption(
                exerciseName = exerciseName,
                muscleGroup = resolvedMuscleGroup,
                bestE1rm = bestE1rm,
                deltaKgText = deltaKgText,
                isDeltaPositive = isPositive,
                points = chartPoints
            )
        }

        private fun computeMuscleVolumes(
            allSets: List<WorkoutSetEntity>,
            historyByExercise: Map<String, ExerciseHistoryEntity>
        ): Map<String, MuscleVolumeUi> {
            val counts = MUSCLE_DEFINITIONS.keys.associateWith { 0 }.toMutableMap()

            allSets.forEach { set ->
                val storedMuscle = historyByExercise[set.exerciseName]?.muscleGroup?.uppercase()
                val matchedIds = inferMuscleIdsForExercise(
                    exerciseName = set.exerciseName,
                    storedMuscle = storedMuscle
                )
                matchedIds.forEach { id ->
                    counts[id] = (counts[id] ?: 0) + 1
                }
            }

            return MUSCLE_DEFINITIONS.mapValues { (id, meta) ->
                MuscleVolumeUi(
                    id = id,
                    displayName = meta.first,
                    orientation = meta.second,
                    weeklySets = counts[id] ?: 0
                )
            }
        }

        private fun resolvePrimaryMuscleId(
            exerciseName: String,
            storedMuscle: String?
        ): String {
            val upperStored = storedMuscle?.trim()?.uppercase()
            if (upperStored != null && upperStored in MUSCLE_DEFINITIONS) {
                return upperStored
            }
            return inferMuscleIdsForExercise(exerciseName, upperStored).firstOrNull() ?: "CHEST"
        }

        private fun inferMuscleIdsForExercise(
            exerciseName: String,
            storedMuscle: String?
        ): Set<String> {
            val matched = linkedSetOf<String>()
            if (storedMuscle != null && storedMuscle in MUSCLE_DEFINITIONS) {
                matched.add(storedMuscle)
            }

            val name = exerciseName.lowercase()
            val tokens = name.words()
            if (tokens.hasWordPrefix("bench", "chest", "pec", "fly", "pushup", "dip") || name.contains("push up")) {
                matched.add("CHEST")
            }
            if (name.contains("rear delt") || name.contains("face pull") || name.contains("reverse fly")) {
                matched.add("REAR_SHOULDERS")
            } else if (tokens.hasWordPrefix("shoulder", "overhead", "ohp", "military", "lateral", "delt")) {
                matched.add("SHOULDERS")
            }
            if (tokens.hasWordPrefix("trap", "shrug") || name.contains("upright row")) {
                matched.add("TRAPS")
            }
            if ((tokens.hasWordPrefix("bicep", "curl", "chinup", "hammer") || tokens.hasAnyWord("chin") || name.contains("chin up")) &&
                !name.contains("leg curl") &&
                !name.contains("nordic curl")
            ) {
                matched.add("BICEPS")
            }
            if (tokens.hasWordPrefix("squat", "lunge", "quad", "hack") || name.contains("leg press") || name.contains("leg extension")) {
                matched.add("QUADS")
            }
            if (tokens.hasAnyWord("ab", "abs", "core", "plank", "situp") ||
                tokens.hasWordPrefix("crunch") ||
                name.contains("sit up") ||
                name.contains("leg raise")
            ) {
                matched.add("ABS")
            }
            if ((tokens.hasAnyWord("lat", "lats", "row", "rows", "chin", "pullup", "pullups", "pulldown", "pulldowns") ||
                    tokens.hasWordPrefix("deadlift") ||
                    name.contains("pull up") ||
                    name.contains("pull down") ||
                    name.contains("lat pull") ||
                    (tokens.hasAnyWord("back") &&
                        !name.contains("back squat") &&
                        !name.contains("lower back") &&
                        !name.contains("kickback"))) &&
                !name.contains("upright row") &&
                !name.contains("face pull")
            ) {
                matched.add("LATS")
            }
            if (name.contains("lower back") || tokens.hasWordPrefix("hyperextension", "deadlift") || name.contains("good morning")) {
                matched.add("LOWER_BACK")
            }
            if (tokens.hasWordPrefix("glute", "bridge") || name.contains("hip thrust")) {
                matched.add("GLUTES")
            }
            if (tokens.hasWordPrefix("tricep", "skull", "pushdown", "dip", "bench", "overhead", "kickback")) {
                matched.add("TRICEPS")
            }
            if (tokens.hasWordPrefix("hamstring", "rdl", "deadlift", "glute") || name.contains("leg curl") || name.contains("hip thrust")) {
                matched.add("HAMSTRINGS")
            }
            if (tokens.hasWordPrefix("calf", "calves")) {
                matched.add("CALVES")
            }
            return matched
        }

        private fun resolveFiniteE1rm(set: WorkoutSetEntity): Double {
            return if (set.e1rm.isFinite() && set.e1rm > 0.0 && set.e1rm <= 1_000_000_000.0) {
                set.e1rm
            } else {
                WorkoutCalculator.calculateE1RM(set.weightKg, set.reps)
            }
        }

        fun buildProgressionForExercise(
            sets: List<WorkoutSetEntity>
        ): Pair<List<ProgressionPoint>, Double> {
            val sortedSets = sets.sortedBy { it.timestampMillis }
            val points = sortedSets.mapIndexed { index, set ->
                val resolvedE1rm = resolveFiniteE1rm(set)
                ProgressionPoint(
                    weekLabel = "W${index + 1}",
                    weightKg = set.weightKg,
                    reps = set.reps,
                    e1rm = resolvedE1rm
                )
            }

            val percentageChange = if (points.size >= 2) {
                val previous = points[points.size - 2].e1rm
                val latest = points.last().e1rm
                if (previous > 0.0 && previous.isFinite() && latest.isFinite()) {
                    round((((latest - previous) / previous) * 100.0) * 10.0) / 10.0
                } else {
                    0.0
                }
            } else {
                0.0
            }

            return points to percentageChange
        }

        fun inferTrainedMuscles(exerciseNames: Iterable<String>): Set<MuscleRegion> {
            val regions = mutableSetOf<MuscleRegion>()
            exerciseNames.forEach { rawName ->
                val name = rawName.lowercase()
                val tokens = name.words()
                if (tokens.hasWordPrefix("bench", "chest", "pec", "fly", "pushup", "dip") || name.contains("push up")) {
                    regions.add(MuscleRegion.CHEST)
                }
                if (tokens.hasWordPrefix("shoulder", "overhead", "ohp", "military", "lateral", "delt") ||
                    name.contains("face pull") ||
                    name.contains("reverse fly")
                ) {
                    regions.add(MuscleRegion.SHOULDERS)
                }
                if ((tokens.hasWordPrefix("bicep", "curl", "chinup", "hammer") || tokens.hasAnyWord("chin") || name.contains("chin up")) &&
                    !name.contains("leg curl") &&
                    !name.contains("nordic curl")
                ) {
                    regions.add(MuscleRegion.BICEPS)
                }
                if (tokens.hasWordPrefix("squat", "lunge", "quad", "hack") || name.contains("leg press") || name.contains("leg extension")) {
                    regions.add(MuscleRegion.QUADS)
                }
                if (tokens.hasAnyWord("ab", "abs", "core", "plank", "situp") ||
                    tokens.hasWordPrefix("crunch") ||
                    name.contains("sit up") ||
                    name.contains("leg raise")
                ) {
                    regions.add(MuscleRegion.ABS)
                }
                if ((tokens.hasAnyWord("lat", "lats", "row", "rows", "chin", "pullup", "pullups", "pulldown", "pulldowns") ||
                        tokens.hasWordPrefix("deadlift") ||
                        name.contains("pull up") ||
                        name.contains("pull down") ||
                        name.contains("lat pull") ||
                        (tokens.hasAnyWord("back") &&
                            !name.contains("back squat") &&
                            !name.contains("lower back") &&
                            !name.contains("kickback"))) &&
                    !name.contains("upright row") &&
                    !name.contains("face pull")
                ) {
                    regions.add(MuscleRegion.LATS)
                }
                if (tokens.hasWordPrefix("tricep", "skull", "pushdown", "dip", "bench", "overhead", "kickback")) {
                    regions.add(MuscleRegion.TRICEPS)
                }
                if (tokens.hasWordPrefix("hamstring", "rdl", "deadlift", "glute") || name.contains("leg curl") || name.contains("hip thrust")) {
                    regions.add(MuscleRegion.HAMSTRINGS)
                }
                if (tokens.hasWordPrefix("calf", "calves")) {
                    regions.add(MuscleRegion.CALVES)
                }
            }
            return regions
        }

        private val WORD_SPLIT_REGEX = Regex("[^a-z0-9]+")

        private fun String.words(): Set<String> =
            split(WORD_SPLIT_REGEX).filter { it.isNotEmpty() }.toSet()

        private fun Set<String>.hasAnyWord(vararg targets: String): Boolean =
            targets.any { it in this }

        private fun Set<String>.hasWordPrefix(vararg prefixes: String): Boolean =
            any { token -> prefixes.any { prefix -> token.startsWith(prefix) } }

        fun factory(repository: GymRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { StatsViewModel(repository) }
        }
    }
}
