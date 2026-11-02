package com.mulocoolbot.gymtracker.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mulocoolbot.gymtracker.data.local.WorkoutSetEntity
import com.mulocoolbot.gymtracker.data.repository.GymRepository
import com.mulocoolbot.gymtracker.util.WorkoutCalculator
import java.util.Locale
import kotlin.math.round
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

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

data class ProgressionPoint(
    val weekLabel: String,
    val weightKg: Double,
    val reps: Int,
    val e1rm: Double
)

data class StatsUiState(
    val exercises: List<String> = emptyList(),
    val selectedExercise: String = "",
    val progressionPoints: List<ProgressionPoint> = emptyList(),
    val percentageChange: Double = 0.0,
    val trainedMuscles: Set<MuscleRegion> = emptySet()
)

class StatsViewModel(
    repository: GymRepository
) : ViewModel() {

    private val selectedExerciseFlow = MutableStateFlow("")

    val uiState: StateFlow<StatsUiState> = combine(
        repository.observeAllSets(),
        repository.exerciseHistory,
        selectedExerciseFlow
    ) { allSets, history, requestedExercise ->
        val setsByExercise = allSets.groupBy { it.exerciseName }
        val exerciseNames = (setsByExercise.keys + history.map { it.normalizedName })
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

        val activeExercise = when {
            requestedExercise in exerciseNames -> requestedExercise
            exerciseNames.isNotEmpty() -> exerciseNames.first()
            else -> ""
        }

        val exerciseSets = setsByExercise[activeExercise].orEmpty()
        val (points, pctChange) = buildProgressionForExercise(exerciseSets)
        val muscles = inferTrainedMuscles(exerciseNames)

        StatsUiState(
            exercises = exerciseNames,
            selectedExercise = activeExercise,
            progressionPoints = points,
            percentageChange = pctChange,
            trainedMuscles = muscles
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = StatsUiState()
    )

    fun selectExercise(exerciseName: String) {
        selectedExerciseFlow.value = exerciseName
    }

    companion object {
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

