package com.mulocoolbot.gymtracker.util

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutCalculatorTest {

    @Test
    fun calculateE1RM_returnsExactWeightForSingleRep() {
        val result = WorkoutCalculator.calculateE1RM(weightKg = 100.0, reps = 1)
        assertEquals(100.0, result, 0.001)
    }

    @Test
    fun calculateE1RM_returnsExpectedEpleyValueForMultipleReps() {
        val result = WorkoutCalculator.calculateE1RM(weightKg = 65.0, reps = 10)
        assertEquals(86.7, result, 0.001)
    }

    @Test
    fun calculateE1RM_returnsZeroForZeroOrNegativeInputs() {
        assertEquals(0.0, WorkoutCalculator.calculateE1RM(weightKg = 0.0, reps = 10), 0.001)
        assertEquals(0.0, WorkoutCalculator.calculateE1RM(weightKg = -50.0, reps = 8), 0.001)
        assertEquals(0.0, WorkoutCalculator.calculateE1RM(weightKg = 65.0, reps = 0), 0.001)
        assertEquals(0.0, WorkoutCalculator.calculateE1RM(weightKg = 65.0, reps = -5), 0.001)
    }

    @Test
    fun calculateCalories_returnsAccurateKcalForThirtyMinuteSession() {
        val startMillis = 0L
        val endMillis = 30 * 60_000L
        val calories = WorkoutCalculator.calculateCalories(
            weightKg = 72.0,
            startMillis = startMillis,
            endMillis = endMillis,
            met = 5.0
        )
        assertEquals(189, calories)
    }

    @Test
    fun getRestDurationMillis_returnsDurationBySetType() {
        assertEquals(60_000L, WorkoutCalculator.getRestDurationMillis("WARMUP"))
        assertEquals(120_000L, WorkoutCalculator.getRestDurationMillis("NORMAL"))
        assertEquals(180_000L, WorkoutCalculator.getRestDurationMillis("FINAL"))
    }

    @Test
    fun normalizeExerciseName_trimsSpacesAndFormatsToTitleCase() {
        val normalized = WorkoutCalculator.normalizeExerciseName("  incline   bench press ")
        assertEquals("Incline Bench Press", normalized)
    }
}

