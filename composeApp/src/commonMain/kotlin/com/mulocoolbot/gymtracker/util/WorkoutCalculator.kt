package com.mulocoolbot.gymtracker.util

import kotlin.math.round
import kotlin.math.roundToInt
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearsUntil

object WorkoutCalculator {

    fun calculateE1RM(weightKg: Double, reps: Int): Double {
        if (!weightKg.isFinite() || weightKg <= 0.0 || reps <= 0) return 0.0
        if (reps == 1) return weightKg
        val raw = weightKg * (1.0 + reps / 30.0)
        return round(raw * 10.0) / 10.0
    }

    fun calculateCalories(
        weightKg: Double,
        startMillis: Long,
        endMillis: Long,
        met: Double = 5.0
    ): Int {
        if (!weightKg.isFinite() || !met.isFinite() || weightKg <= 0.0 || endMillis <= startMillis || met <= 0.0) return 0
        val durationMinutes = (endMillis - startMillis) / 60_000.0
        val calories = ((met * 3.5 * weightKg) / 200.0) * durationMinutes
        return calories.roundToInt()
    }

    fun getRestDurationMillis(setType: String): Long = when (setType.uppercase()) {
        "WARMUP" -> 60_000L
        "FINAL" -> 180_000L
        else -> 120_000L
    }

    fun normalizeExerciseName(raw: String): String {
        return raw.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { it.titlecase() }
            }
    }

    fun calculateAge(
        birthDateMillis: Long,
        currentMillis: Long = Clock.System.now().toEpochMilliseconds()
    ): Int {
        if (birthDateMillis <= 0L || birthDateMillis > currentMillis) return 0
        val zone = TimeZone.currentSystemDefault()
        val birthDate = Instant.fromEpochMilliseconds(birthDateMillis).toLocalDateTime(zone).date
        val currentDate = Instant.fromEpochMilliseconds(currentMillis).toLocalDateTime(zone).date
        return birthDate.yearsUntil(currentDate).coerceAtLeast(0)
    }
}
