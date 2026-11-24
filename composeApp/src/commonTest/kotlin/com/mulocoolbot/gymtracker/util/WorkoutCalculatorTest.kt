package com.mulocoolbot.gymtracker.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

class WorkoutCalculatorTest {

    @Test
    fun calculateE1RM_returnsExactWeightForSingleRep() {
        assertEquals(100.0, WorkoutCalculator.calculateE1RM(weightKg = 100.0, reps = 1), 0.001)
    }

    @Test
    fun calculateE1RM_returnsExpectedEpleyValueForMultipleReps() {
        assertEquals(86.7, WorkoutCalculator.calculateE1RM(weightKg = 65.0, reps = 10), 0.001)
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
        val calories = WorkoutCalculator.calculateCalories(
            weightKg = 72.0,
            startMillis = 0L,
            endMillis = 30 * 60_000L,
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
        assertEquals("Incline Bench Press", WorkoutCalculator.normalizeExerciseName("  incline   bench press "))
    }

    @Test
    fun calculateAge_computesCompletedYearsAcrossTimezone() {
        val zone = TimeZone.currentSystemDefault()
        val birthMillis = LocalDateTime(2000, 5, 10, 0, 0).toInstant(zone).toEpochMilliseconds()
        val beforeBirthdayMillis = LocalDateTime(2026, 5, 9, 12, 0).toInstant(zone).toEpochMilliseconds()
        val onBirthdayMillis = LocalDateTime(2026, 5, 10, 12, 0).toInstant(zone).toEpochMilliseconds()

        assertEquals(25, WorkoutCalculator.calculateAge(birthMillis, beforeBirthdayMillis))
        assertEquals(26, WorkoutCalculator.calculateAge(birthMillis, onBirthdayMillis))
        assertEquals(0, WorkoutCalculator.calculateAge(0L, onBirthdayMillis))
    }
}
