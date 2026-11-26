package com.mulocoolbot.gymtracker.ui.tutorial

import com.mulocoolbot.gymtracker.data.local.WorkoutPlanEntity
import com.mulocoolbot.gymtracker.timer.RestTimerState
import com.mulocoolbot.gymtracker.ui.home.HomeUiState
import com.mulocoolbot.gymtracker.ui.home.WeekDayItem
import com.mulocoolbot.gymtracker.ui.plan.WorkoutPlanSubPage
import com.mulocoolbot.gymtracker.ui.plan.WorkoutPlanUiState
import com.mulocoolbot.gymtracker.ui.stats.ChartPointUi
import com.mulocoolbot.gymtracker.ui.stats.ExerciseStatOption
import com.mulocoolbot.gymtracker.ui.stats.HeatmapOrientation
import com.mulocoolbot.gymtracker.ui.stats.MuscleRegion
import com.mulocoolbot.gymtracker.ui.stats.ProgressionPoint
import com.mulocoolbot.gymtracker.ui.stats.StatsUiState
import com.mulocoolbot.gymtracker.ui.stats.StatsViewModel
import com.mulocoolbot.gymtracker.ui.workout.DraftWorkoutSet
import com.mulocoolbot.gymtracker.ui.workout.WorkoutUiState

object TutorialPreviewData {

    private val previewSelectedDays = setOf(1, 3, 5)

    private val previewDraftPlanNames = mapOf(
        1 to "Push Day",
        3 to "Pull Day",
        5 to "Legs"
    )

    private val fallbackWeekDays = listOf(
        WeekDayItem(0, "Sun", "01", isRestDay = true),
        WeekDayItem(1, "Mon", "02", isRestDay = false),
        WeekDayItem(2, "Tue", "03", isRestDay = true),
        WeekDayItem(3, "Wed", "04", isRestDay = false),
        WeekDayItem(4, "Thu", "05", isRestDay = true),
        WeekDayItem(5, "Fri", "06", isRestDay = false),
        WeekDayItem(6, "Sat", "07", isRestDay = true)
    )

    private val previewSessionSets = listOf(
        DraftWorkoutSet(
            id = -1L,
            exerciseName = "Bench Press",
            muscleGroup = "CHEST",
            setType = WorkoutUiState.SET_TYPE_WARMUP,
            setLabel = "Warmup",
            setOrder = 1,
            weightKg = 40.0,
            reps = 12,
            e1rm = 56.0,
            timestampMillis = 1L
        ),
        DraftWorkoutSet(
            id = -2L,
            exerciseName = "Bench Press",
            muscleGroup = "CHEST",
            setType = WorkoutUiState.SET_TYPE_NORMAL,
            setLabel = "Set 1",
            setOrder = 1,
            weightKg = 60.0,
            reps = 10,
            e1rm = 80.0,
            timestampMillis = 2L
        )
    )

    private val previewRestTimerState = RestTimerState(
        isRunning = true,
        totalSeconds = 120,
        remainingSeconds = 105,
        endTimeMillis = 0L,
        alarmRinging = false,
        label = "Rest · Set 1"
    )

    private val previewChartPoints = listOf(
        ChartPointUi(0, "W1", "52.5 kg × 10", 52.5, 10, 70.0),
        ChartPointUi(1, "W2", "55 kg × 10", 55.0, 10, 73.3),
        ChartPointUi(2, "W3", "57.5 kg × 10", 57.5, 10, 76.7),
        ChartPointUi(3, "W4", "60 kg × 10", 60.0, 10, 80.0)
    )

    private val previewStatsUiState: StatsUiState by lazy {
        val volumes = StatsViewModel.buildDefaultMuscleVolumes().toMutableMap()
        volumes["CHEST"] = volumes.getValue("CHEST").copy(weeklySets = 10)
        volumes["SHOULDERS"] = volumes.getValue("SHOULDERS").copy(weeklySets = 4)
        volumes["TRICEPS"] = volumes.getValue("TRICEPS").copy(weeklySets = 6)

        StatsUiState(
            hasStatisticsData = true,
            exerciseOptions = listOf(
                ExerciseStatOption(
                    exerciseName = "Bench Press",
                    muscleGroup = "CHEST",
                    bestE1rm = 80.0,
                    deltaKgText = "+10 kg",
                    isDeltaPositive = true,
                    points = previewChartPoints
                )
            ),
            selectedExerciseName = "Bench Press",
            isExerciseDropdownOpen = false,
            selectedPointIndex = 3,
            heatmapOrientation = HeatmapOrientation.FRONT,
            selectedMuscleId = "CHEST",
            muscleVolumes = volumes,
            exercises = listOf("Bench Press"),
            progressionPoints = listOf(
                ProgressionPoint("W1", 52.5, 10, 70.0),
                ProgressionPoint("W2", 55.0, 10, 73.3),
                ProgressionPoint("W3", 57.5, 10, 76.7),
                ProgressionPoint("W4", 60.0, 10, 80.0)
            ),
            percentageChange = 4.3,
            trainedMuscles = setOf(MuscleRegion.CHEST, MuscleRegion.SHOULDERS, MuscleRegion.TRICEPS)
        )
    }

    fun resolveHomeUiState(
        actual: HomeUiState,
        tutorialUiState: TutorialUiState
    ): HomeUiState {
        if (!tutorialUiState.isActive) return actual

        val weekDays = if (actual.weekDays.size == 7) {
            actual.weekDays.map { day ->
                day.copy(isRestDay = day.dayOfWeek !in previewSelectedDays && day.dayOfWeek != actual.selectedDayOfWeek)
            }
        } else {
            fallbackWeekDays.map { day ->
                day.copy(isRestDay = day.dayOfWeek !in previewSelectedDays && day.dayOfWeek != actual.selectedDayOfWeek)
            }
        }

        return HomeUiState(
            formattedHeaderDate = actual.formattedHeaderDate.ifBlank { "Monday, 12 Oct" },
            selectedDayOfWeek = actual.selectedDayOfWeek,
            weekDays = weekDays,
            currentStreak = 5,
            todayCalories = 320,
            calorieProgress = (320f / 600f).coerceIn(0.08f, 1f),
            selectedDayPlan = WorkoutPlanEntity(
                dayOfWeek = actual.selectedDayOfWeek,
                isRestDay = false,
                planName = "Push Day"
            )
        )
    }

    fun resolveWorkoutPlanUiState(
        actual: WorkoutPlanUiState,
        tutorialUiState: TutorialUiState
    ): WorkoutPlanUiState {
        if (!tutorialUiState.isActive) return actual

        return when (tutorialUiState.currentStep.targetId) {
            TutorialTargetId.PLAN_SELECT_DAYS_AREA -> WorkoutPlanUiState(
                subPage = WorkoutPlanSubPage.SELECT_DAYS,
                hasSavedPlan = false,
                selectedDays = previewSelectedDays
            )
            TutorialTargetId.PLAN_INPUT_AREA,
            TutorialTargetId.PLAN_SAVE_BUTTON -> WorkoutPlanUiState(
                subPage = WorkoutPlanSubPage.INPUT_PLAN,
                hasSavedPlan = false,
                selectedDays = previewSelectedDays,
                draftPlanNames = previewDraftPlanNames
            )
            else -> WorkoutPlanUiState(
                subPage = WorkoutPlanSubPage.EMPTY,
                hasSavedPlan = false
            )
        }
    }

    fun resolveWorkoutUiState(
        actual: WorkoutUiState,
        tutorialUiState: TutorialUiState
    ): WorkoutUiState {
        if (!tutorialUiState.isActive) return actual

        return when (tutorialUiState.currentStep.targetId) {
            TutorialTargetId.WORKOUT_EXERCISE_MUSCLE_AREA -> WorkoutUiState(
                isWorkoutActive = true,
                todayPlanName = "Push Day",
                elapsedSeconds = 420L,
                liveCaloriesBurned = 45,
                exerciseInput = "Bench Press",
                selectedMuscleGroup = "CHEST",
                weightInput = "60",
                repsInput = "10",
                selectedSetType = WorkoutUiState.SET_TYPE_NORMAL,
                nextNormalSetNumber = 1,
                activeSessionSets = emptyList(),
                restTimerState = RestTimerState()
            )
            TutorialTargetId.WORKOUT_SET_ENTRY_AREA -> WorkoutUiState(
                isWorkoutActive = true,
                todayPlanName = "Push Day",
                elapsedSeconds = 845L,
                liveCaloriesBurned = 112,
                exerciseInput = "Bench Press",
                selectedMuscleGroup = "CHEST",
                weightInput = "60",
                repsInput = "10",
                selectedSetType = WorkoutUiState.SET_TYPE_NORMAL,
                nextNormalSetNumber = 2,
                activeSessionSets = previewSessionSets,
                restTimerState = RestTimerState()
            )
            TutorialTargetId.WORKOUT_REST_TIMER_AREA,
            TutorialTargetId.WORKOUT_FINISH_BUTTON -> WorkoutUiState(
                isWorkoutActive = true,
                todayPlanName = "Push Day",
                elapsedSeconds = 960L,
                liveCaloriesBurned = 128,
                exerciseInput = "Bench Press",
                selectedMuscleGroup = "CHEST",
                weightInput = "60",
                repsInput = "10",
                selectedSetType = WorkoutUiState.SET_TYPE_NORMAL,
                nextNormalSetNumber = 2,
                activeSessionSets = previewSessionSets,
                restTimerState = previewRestTimerState
            )
            else -> WorkoutUiState(
                isWorkoutActive = false,
                todayPlanName = "Push Day",
                todayCompletedSets = emptyList(),
                restTimerState = RestTimerState()
            )
        }
    }

    fun resolveStatsUiState(
        actual: StatsUiState,
        tutorialUiState: TutorialUiState
    ): StatsUiState {
        if (!tutorialUiState.isActive) return actual
        return previewStatsUiState
    }
}
