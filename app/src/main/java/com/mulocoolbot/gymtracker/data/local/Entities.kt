package com.mulocoolbot.gymtracker.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val uid: String,
    val name: String,
    val birthDateMillis: Long,
    val heightCm: Double,
    val weightKg: Double,
    val currentStreak: Int = 0,
    val consecutiveMissed: Int = 0,
    val lastWorkoutEpochDay: Long? = null,
    val isGoogleLinked: Boolean = false,
    val isEmailLinked: Boolean = false
)

@Entity(tableName = "workout_plan")
data class WorkoutPlanEntity(
    @PrimaryKey val dayOfWeek: Int,
    val isRestDay: Boolean,
    val planName: String
)

@Entity(tableName = "exercise_history")
data class ExerciseHistoryEntity(
    @PrimaryKey val normalizedName: String,
    val muscleGroup: String,
    val lastUsedMillis: Long
)

@Entity(tableName = "workout_session")
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val sessionId: Long = 0,
    val dateEpochDay: Long,
    val planName: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long? = null,
    val caloriesBurned: Int = 0
)

@Entity(
    tableName = "workout_set",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["exerciseName"])
    ]
)
data class WorkoutSetEntity(
    @PrimaryKey(autoGenerate = true) val setId: Long = 0,
    val sessionId: Long,
    val exerciseName: String,
    val setType: String,
    val setOrder: Int,
    val weightKg: Double,
    val reps: Int,
    val e1rm: Double,
    val timestampMillis: Long
)

