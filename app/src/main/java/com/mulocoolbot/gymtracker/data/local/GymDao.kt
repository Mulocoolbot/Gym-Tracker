package com.mulocoolbot.gymtracker.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface GymDao {

    @Query("SELECT * FROM user_profile LIMIT 1")
    fun observeUserProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile LIMIT 1")
    suspend fun getUserProfile(): UserProfileEntity?

    @Upsert
    suspend fun upsertUserProfile(profile: UserProfileEntity)

    @Query("DELETE FROM user_profile")
    suspend fun clearUserProfile()

    @Query("SELECT * FROM workout_plan ORDER BY dayOfWeek ASC")
    fun observeWorkoutPlans(): Flow<List<WorkoutPlanEntity>>

    @Query("SELECT * FROM workout_plan ORDER BY dayOfWeek ASC")
    suspend fun getWorkoutPlans(): List<WorkoutPlanEntity>

    @Upsert
    suspend fun upsertWorkoutPlans(plans: List<WorkoutPlanEntity>)

    @Upsert
    suspend fun upsertWorkoutPlan(plan: WorkoutPlanEntity)

    @Query("SELECT * FROM exercise_history ORDER BY lastUsedMillis DESC")
    fun observeExerciseHistory(): Flow<List<ExerciseHistoryEntity>>

    @Upsert
    suspend fun upsertExerciseHistory(exercise: ExerciseHistoryEntity)

    @Upsert
    suspend fun upsertExerciseHistories(exercises: List<ExerciseHistoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: WorkoutSessionEntity): Long

    @Update
    suspend fun updateSession(session: WorkoutSessionEntity)

    @Delete
    suspend fun deleteSession(session: WorkoutSessionEntity)

    @Query("SELECT * FROM workout_session WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: Long): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_session WHERE dateEpochDay = :dateEpochDay ORDER BY startTimeMillis DESC")
    fun observeSessionsByDate(dateEpochDay: Long): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_session ORDER BY startTimeMillis DESC")
    fun observeAllSessions(): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT COALESCE(SUM(caloriesBurned), 0) FROM workout_session WHERE dateEpochDay = :dateEpochDay")
    fun observeCaloriesByDate(dateEpochDay: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSet(workoutSet: WorkoutSetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSets(sets: List<WorkoutSetEntity>)

    @Delete
    suspend fun deleteSet(workoutSet: WorkoutSetEntity)

    @Query("SELECT * FROM workout_set WHERE sessionId = :sessionId ORDER BY setOrder ASC, timestampMillis ASC")
    fun observeSetsForSession(sessionId: Long): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM workout_set WHERE sessionId = :sessionId ORDER BY setOrder ASC, timestampMillis ASC")
    suspend fun getSetsForSession(sessionId: Long): List<WorkoutSetEntity>

    @Query(
        """
        SELECT ws.* FROM workout_set ws
        INNER JOIN workout_session s ON ws.sessionId = s.sessionId
        WHERE s.dateEpochDay = :dateEpochDay
        ORDER BY ws.timestampMillis ASC
        """
    )
    fun observeSetsByDate(dateEpochDay: Long): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM workout_set WHERE exerciseName = :exerciseName ORDER BY timestampMillis ASC")
    fun observeSetsForExercise(exerciseName: String): Flow<List<WorkoutSetEntity>>

    @Query("SELECT DISTINCT exerciseName FROM workout_set ORDER BY exerciseName ASC")
    fun observeLoggedExerciseNames(): Flow<List<String>>
}

