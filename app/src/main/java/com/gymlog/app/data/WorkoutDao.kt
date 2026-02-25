package com.gymlog.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workouts ORDER BY name ASC")
    fun getAll(): Flow<List<Workout>>

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getById(id: Long): Workout?

    @Insert
    suspend fun insert(workout: Workout): Long

    @Update
    suspend fun update(workout: Workout)

    @Delete
    suspend fun delete(workout: Workout)

    @Query("SELECT * FROM workout_exercises WHERE workoutId = :workoutId ORDER BY sortOrder ASC")
    suspend fun getExercisesForWorkout(workoutId: Long): List<WorkoutExercise>

    @Insert
    suspend fun insertWorkoutExercise(workoutExercise: WorkoutExercise): Long

    @Query("DELETE FROM workout_exercises WHERE workoutId = :workoutId")
    suspend fun deleteAllExercisesForWorkout(workoutId: Long)
}
