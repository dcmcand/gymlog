package com.gymlog.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface WorkoutSessionDao {
    @Query("SELECT * FROM workout_sessions WHERE date = :date")
    suspend fun getSessionsForDate(date: LocalDate): List<WorkoutSession>

    @Query("SELECT * FROM workout_sessions WHERE status = 'IN_PROGRESS' LIMIT 1")
    suspend fun getInProgressSession(): WorkoutSession?

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun getById(id: Long): WorkoutSession?

    @Query("SELECT DISTINCT date FROM workout_sessions WHERE date BETWEEN :start AND :end AND status = 'COMPLETED'")
    fun getWorkoutDatesInRange(start: LocalDate, end: LocalDate): Flow<List<LocalDate>>

    @Insert
    suspend fun insert(session: WorkoutSession): Long

    @Update
    suspend fun update(session: WorkoutSession)

    @Query("DELETE FROM workout_sessions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM exercise_sets WHERE sessionId = :sessionId ORDER BY exerciseId, setNumber")
    suspend fun getSetsForSession(sessionId: Long): List<ExerciseSet>

    @Query("SELECT * FROM exercise_sets WHERE sessionId = :sessionId AND exerciseId = :exerciseId ORDER BY setNumber")
    suspend fun getSetsForExercise(sessionId: Long, exerciseId: Long): List<ExerciseSet>

    @Insert
    suspend fun insertSet(set: ExerciseSet): Long

    @Insert
    suspend fun insertSets(sets: List<ExerciseSet>)

    @Update
    suspend fun updateSet(set: ExerciseSet)

    @Query("DELETE FROM exercise_sets WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Query("""
        SELECT es.* FROM exercise_sets es
        INNER JOIN workout_sessions ws ON es.sessionId = ws.id
        WHERE es.exerciseId = :exerciseId AND es.status != 'PENDING'
        ORDER BY ws.date DESC, es.setNumber ASC
        LIMIT 1
    """)
    suspend fun getLastCompletedSet(exerciseId: Long): ExerciseSet?

    @Query("""
        SELECT ws.* FROM workout_sessions ws
        INNER JOIN exercise_sets es ON es.sessionId = ws.id
        WHERE es.exerciseId = :exerciseId AND es.status != 'PENDING'
        ORDER BY ws.date DESC
        LIMIT 1
    """)
    suspend fun getLastSessionForExercise(exerciseId: Long): WorkoutSession?

    @Query("""
        SELECT MAX(es.weightKg) as maxWeight, ws.date
        FROM exercise_sets es
        INNER JOIN workout_sessions ws ON es.sessionId = ws.id
        WHERE es.exerciseId = :exerciseId AND es.status IN ('EASY', 'HARD') AND es.weightKg IS NOT NULL
        GROUP BY ws.date
        ORDER BY ws.date ASC
    """)
    suspend fun getWeightProgressForExercise(exerciseId: Long): List<ProgressEntry>

    @Query("""
        SELECT MAX(es.distanceM) as maxDistance, es.durationSec as minDuration, ws.date
        FROM exercise_sets es
        INNER JOIN workout_sessions ws ON es.sessionId = ws.id
        WHERE es.exerciseId = :exerciseId AND es.status IN ('EASY', 'HARD') AND es.distanceM IS NOT NULL
        GROUP BY ws.date
        ORDER BY ws.date ASC
    """)
    suspend fun getCardioProgressForExercise(exerciseId: Long): List<CardioProgressEntry>

    @Query("""
        SELECT MIN(es.durationSec) as minDuration, ws.date
        FROM exercise_sets es
        INNER JOIN workout_sessions ws ON es.sessionId = ws.id
        WHERE es.exerciseId = :exerciseId AND es.status IN ('EASY', 'HARD') AND es.durationSec IS NOT NULL
        GROUP BY ws.date
        ORDER BY ws.date ASC
    """)
    suspend fun getTimeProgressForExercise(exerciseId: Long): List<TimeProgressEntry>

    @Query("""
        SELECT MAX(es.distanceM) as maxDistance, ws.date
        FROM exercise_sets es
        INNER JOIN workout_sessions ws ON es.sessionId = ws.id
        WHERE es.exerciseId = :exerciseId AND es.status IN ('EASY', 'HARD') AND es.distanceM IS NOT NULL
        GROUP BY ws.date
        ORDER BY ws.date ASC
    """)
    suspend fun getDistanceProgressForExercise(exerciseId: Long): List<DistanceProgressEntry>
}
