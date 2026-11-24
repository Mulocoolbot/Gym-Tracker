package com.mulocoolbot.gymtracker.util

import com.mulocoolbot.gymtracker.data.local.UserProfileEntity
import com.mulocoolbot.gymtracker.data.local.WorkoutPlanEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

class StreakEvaluatorTest {

    private val sundayEpochDay = LocalDate(2026, 11, 1).toEpochDays().toLong()

    private val sampleWeeklyPlan = listOf(
        WorkoutPlanEntity(dayOfWeek = 0, isRestDay = true, planName = "Rest"),
        WorkoutPlanEntity(dayOfWeek = 1, isRestDay = false, planName = "Push"),
        WorkoutPlanEntity(dayOfWeek = 2, isRestDay = true, planName = "Rest"),
        WorkoutPlanEntity(dayOfWeek = 3, isRestDay = false, planName = "Pull"),
        WorkoutPlanEntity(dayOfWeek = 4, isRestDay = false, planName = "Legs"),
        WorkoutPlanEntity(dayOfWeek = 5, isRestDay = true, planName = "Rest"),
        WorkoutPlanEntity(dayOfWeek = 6, isRestDay = false, planName = "Upper")
    )

    private fun createProfile(
        currentStreak: Int = 5,
        consecutiveMissed: Int = 0,
        lastWorkoutEpochDay: Long? = sundayEpochDay
    ) = UserProfileEntity(
        uid = "user-1",
        name = "Alex",
        birthDateMillis = 946684800000L,
        heightCm = 178.0,
        weightKg = 75.0,
        currentStreak = currentStreak,
        consecutiveMissed = consecutiveMissed,
        lastWorkoutEpochDay = lastWorkoutEpochDay
    )

    @Test
    fun evaluateOnAppLaunch_missingOneScheduledWorkoutDayIncrementsMissedAndKeepsStreak() {
        val profile = createProfile(currentStreak = 5, lastWorkoutEpochDay = sundayEpochDay)
        val evaluated = StreakEvaluator.evaluateOnAppLaunch(
            profile = profile,
            weeklyPlan = sampleWeeklyPlan,
            todayEpochDay = sundayEpochDay + 2
        )

        assertEquals(1, evaluated.consecutiveMissed)
        assertEquals(5, evaluated.currentStreak)
    }

    @Test
    fun evaluateOnAppLaunch_scheduledRestDaysDoNotIncrementConsecutiveMissed() {
        val mondayEpochDay = sundayEpochDay + 1
        val wednesdayEpochDay = sundayEpochDay + 3
        val profile = createProfile(currentStreak = 5, lastWorkoutEpochDay = mondayEpochDay)

        val evaluated = StreakEvaluator.evaluateOnAppLaunch(
            profile = profile,
            weeklyPlan = sampleWeeklyPlan,
            todayEpochDay = wednesdayEpochDay
        )

        assertEquals(0, evaluated.consecutiveMissed)
        assertEquals(5, evaluated.currentStreak)
    }

    @Test
    fun evaluateOnAppLaunch_missingTwoScheduledWorkoutDaysResetsStreakToZero() {
        val profile = createProfile(currentStreak = 5, lastWorkoutEpochDay = sundayEpochDay)
        val thursdayEpochDay = sundayEpochDay + 4

        val evaluated = StreakEvaluator.evaluateOnAppLaunch(
            profile = profile,
            weeklyPlan = sampleWeeklyPlan,
            todayEpochDay = thursdayEpochDay
        )

        assertEquals(2, evaluated.consecutiveMissed)
        assertEquals(0, evaluated.currentStreak)
    }

    @Test
    fun onWorkoutCompleted_incrementsStreakOncePerDayAndResetsMissedCounter() {
        val wednesdayEpochDay = sundayEpochDay + 3
        val initialProfile = createProfile(
            currentStreak = 4,
            consecutiveMissed = 1,
            lastWorkoutEpochDay = sundayEpochDay + 1
        )

        val afterFirst = StreakEvaluator.onWorkoutCompleted(initialProfile, wednesdayEpochDay)
        assertEquals(5, afterFirst.currentStreak)
        assertEquals(0, afterFirst.consecutiveMissed)
        assertEquals(wednesdayEpochDay, afterFirst.lastWorkoutEpochDay)

        val afterSecondSameDay = StreakEvaluator.onWorkoutCompleted(afterFirst, wednesdayEpochDay)
        assertEquals(5, afterSecondSameDay.currentStreak)
        assertEquals(0, afterSecondSameDay.consecutiveMissed)
        assertEquals(wednesdayEpochDay, afterSecondSameDay.lastWorkoutEpochDay)
    }
}
