package com.mulocoolbot.gymtracker.util

import com.mulocoolbot.gymtracker.data.local.UserProfileEntity
import com.mulocoolbot.gymtracker.data.local.WorkoutPlanEntity
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

object StreakEvaluator {

    fun evaluateOnAppLaunch(
        profile: UserProfileEntity,
        weeklyPlan: List<WorkoutPlanEntity>,
        todayEpochDay: Long
    ): UserProfileEntity {
        val lastWorkoutDay = profile.lastWorkoutEpochDay ?: return profile
        if (todayEpochDay <= lastWorkoutDay + 1) return profile

        val planByDay = weeklyPlan.associateBy { it.dayOfWeek }
        var missedCount = 0

        for (epochDay in (lastWorkoutDay + 1) until todayEpochDay) {
            val dayOfWeek = getDayOfWeekIndex(epochDay)
            val plan = planByDay[dayOfWeek]
            if (plan != null && !plan.isRestDay && plan.planName.isNotBlank()) {
                missedCount++
            }
        }

        val updatedStreak = if (missedCount >= 2) 0 else profile.currentStreak
        return profile.copy(
            currentStreak = updatedStreak,
            consecutiveMissed = missedCount
        )
    }

    fun onWorkoutCompleted(
        profile: UserProfileEntity,
        todayEpochDay: Long
    ): UserProfileEntity {
        val lastDay = profile.lastWorkoutEpochDay
        if (lastDay != null && todayEpochDay <= lastDay) {
            return profile
        }
        return profile.copy(
            currentStreak = profile.currentStreak + 1,
            consecutiveMissed = 0,
            lastWorkoutEpochDay = todayEpochDay
        )
    }

    private fun getDayOfWeekIndex(epochDay: Long): Int {
        return LocalDate.fromEpochDays(epochDay.coerceIn(-365_000L, 365_000L).toInt()).dayOfWeek.isoDayNumber % 7
    }
}
