package com.mulocoolbot.gymtracker.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mulocoolbot.gymtracker.data.local.ExerciseHistoryEntity
import com.mulocoolbot.gymtracker.data.local.WorkoutSetEntity
import com.mulocoolbot.gymtracker.data.repository.GymRepository
import com.mulocoolbot.gymtracker.util.WorkoutCalculator
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round
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

        val activeExercise = when {
            interaction.selectedExerciseName in exerciseNames -> interaction.selectedExerciseName
            options.isNotEmpty() -> options.first().exerciseName
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
        if (muscleId !in MUSCLE_DEFINITIONS) return
        interactionState.update { current ->
            current.copy(selectedMuscleId = muscleId)
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
        private val POINT_DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

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
                val resolvedE1rm = if (set.e1rm > 0.0) {
                    set.e1rm
                } else {
                    WorkoutCalculator.calculateE1RM(set.weightKg, set.reps)
                }
                val dateLabel = if (set.timestampMillis >= 86_400_000L) {
                    Instant.ofEpochMilli(set.timestampMillis)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                        .format(POINT_DATE_FORMATTER)
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
            val roundedDelta = round(deltaKg * 10.0) / 10.0
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
                val storedMuscle = historyByExercise[set.exerciseName]?.muscleGroup?.uppercase(Locale.US)
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
            val upperStored = storedMuscle?.trim()?.uppercase(Locale.US)
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

            val name = exerciseName.lowercase(Locale.US)
            if (name.containsAny("bench", "chest", "pec", "fly", "push up", "pushup", "dip")) {
                matched.add("CHEST")
            }
            if (name.containsAny("rear delt", "face pull", "reverse fly")) {
                matched.add("REAR_SHOULDERS")
            } else if (name.containsAny("shoulder", "overhead", "ohp", "military", "lateral", "delt")) {
                matched.add("SHOULDERS")
            }
            if (name.containsAny("trap", "shrug", "upright row")) {
                matched.add("TRAPS")
            }
            if (name.containsAny("bicep", "curl", "chin up", "chinup", "hammer") && !name.contains("leg curl")) {
                matched.add("BICEPS")
            }
            if (name.containsAny("squat", "leg press", "lunge", "quad", "leg extension", "hack")) {
                matched.add("QUADS")
            }
            if (name.containsAny("ab", "core", "crunch", "plank", "sit up", "situp", "leg raise")) {
                matched.add("ABS")
            }
            if (name.containsAny("row", "pull", "lat", "deadlift", "chin", "back")) {
                matched.add("LATS")
            }
            if (name.containsAny("lower back", "hyperextension", "good morning", "deadlift")) {
                matched.add("LOWER_BACK")
            }
            if (name.containsAny("glute", "hip thrust", "bridge")) {
                matched.add("GLUTES")
            }
            if (name.containsAny("tricep", "skull", "pushdown", "dip", "bench", "overhead")) {
                matched.add("TRICEPS")
            }
            if (name.containsAny("hamstring", "rdl", "deadlift", "leg curl", "glute", "hip thrust")) {
                matched.add("HAMSTRINGS")
            }
            if (name.containsAny("calf", "calves")) {
                matched.add("CALVES")
            }
            return matched
        }

        fun buildProgressionForExercise(
            sets: List<WorkoutSetEntity>
        ): Pair<List<ProgressionPoint>, Double> {
            val sortedSets = sets.sortedBy { it.timestampMillis }
            val points = sortedSets.mapIndexed { index, set ->
                val resolvedE1rm = if (set.e1rm > 0.0) {
                    set.e1rm
                } else {
                    WorkoutCalculator.calculateE1RM(set.weightKg, set.reps)
                }
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
                if (previous > 0.0) {
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
                val name = rawName.lowercase(Locale.US)
                if (name.containsAny("bench", "chest", "pec", "fly", "push up", "pushup", "dip")) {
                    regions.add(MuscleRegion.CHEST)
                }
                if (name.containsAny("shoulder", "overhead", "ohp", "military", "lateral", "delt")) {
                    regions.add(MuscleRegion.SHOULDERS)
                }
                if (name.containsAny("bicep", "curl", "chin up", "chinup", "hammer") && !name.contains("leg curl")) {
                    regions.add(MuscleRegion.BICEPS)
                }
                if (name.containsAny("squat", "leg press", "lunge", "quad", "leg extension", "hack")) {
                    regions.add(MuscleRegion.QUADS)
                }
                if (name.containsAny("ab", "core", "crunch", "plank", "sit up", "situp", "leg raise")) {
                    regions.add(MuscleRegion.ABS)
                }
                if (name.containsAny("row", "pull", "lat", "deadlift", "chin", "back")) {
                    regions.add(MuscleRegion.LATS)
                }
                if (name.containsAny("tricep", "skull", "pushdown", "dip", "bench", "overhead")) {
                    regions.add(MuscleRegion.TRICEPS)
                }
                if (name.containsAny("hamstring", "rdl", "deadlift", "leg curl", "glute", "hip thrust")) {
                    regions.add(MuscleRegion.HAMSTRINGS)
                }
                if (name.containsAny("calf", "calves")) {
                    regions.add(MuscleRegion.CALVES)
                }
            }
            return regions
        }

        private fun formatCompactNumber(value: Double): String {
            return if (value % 1.0 == 0.0) {
                value.toInt().toString()
            } else {
                "%.1f".format(Locale.US, value)
            }
        }

        private fun String.containsAny(vararg keywords: String): Boolean {
            return keywords.any { this.contains(it) }
        }

        fun factory(repository: GymRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return StatsViewModel(repository) as T
                }
            }
        }
    }
}
