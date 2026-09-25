package com.gymlog.app.data.backup

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.gymlog.app.data.Exercise
import com.gymlog.app.data.ExerciseSet
import com.gymlog.app.data.Workout
import com.gymlog.app.data.WorkoutExercise
import com.gymlog.app.data.WorkoutSession

@Dao
abstract class BackupDao : BackupStore {
    @Query("SELECT * FROM exercises ORDER BY id") abstract suspend fun allExercises(): List<Exercise>
    @Query("SELECT * FROM workouts ORDER BY id") abstract suspend fun allWorkouts(): List<Workout>
    @Query("SELECT * FROM workout_exercises ORDER BY id") abstract suspend fun allWorkoutExercises(): List<WorkoutExercise>
    @Query("SELECT * FROM workout_sessions ORDER BY id") abstract suspend fun allSessions(): List<WorkoutSession>
    @Query("SELECT * FROM exercise_sets ORDER BY id") abstract suspend fun allSets(): List<ExerciseSet>

    @Query("DELETE FROM exercise_sets") abstract suspend fun deleteAllSets()
    @Query("DELETE FROM workout_sessions") abstract suspend fun deleteAllSessions()
    @Query("DELETE FROM workout_exercises") abstract suspend fun deleteAllWorkoutExercises()
    @Query("DELETE FROM workouts") abstract suspend fun deleteAllWorkouts()
    @Query("DELETE FROM exercises") abstract suspend fun deleteAllExercises()

    @Insert abstract suspend fun insertExercises(items: List<Exercise>)
    @Insert abstract suspend fun insertWorkouts(items: List<Workout>)
    @Insert abstract suspend fun insertWorkoutExercises(items: List<WorkoutExercise>)
    @Insert abstract suspend fun insertSessions(items: List<WorkoutSession>)
    @Insert abstract suspend fun insertSets(items: List<ExerciseSet>)

    @Query("SELECT EXISTS(SELECT 1 FROM workout_sessions WHERE status = 'IN_PROGRESS')")
    abstract override suspend fun hasWorkoutInProgress(): Boolean

    @Transaction
    override suspend fun readAll() = BackupData(
        allExercises(), allWorkouts(), allWorkoutExercises(), allSessions(), allSets(),
    )

    // Children deleted before parents and parents inserted before children, so foreign keys
    // hold at every step. Explicit ids advance SQLite's autoincrement past the imported maximum.
    @Transaction
    override suspend fun replaceAll(data: BackupData) {
        deleteAllSets()
        deleteAllSessions()
        deleteAllWorkoutExercises()
        deleteAllWorkouts()
        deleteAllExercises()
        insertExercises(data.exercises)
        insertWorkouts(data.workouts)
        insertWorkoutExercises(data.workoutExercises)
        insertSessions(data.sessions)
        insertSets(data.sets)
    }
}
