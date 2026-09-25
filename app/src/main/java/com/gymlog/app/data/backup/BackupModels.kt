package com.gymlog.app.data.backup

import com.gymlog.app.data.CardioFixedDimension
import com.gymlog.app.data.Exercise
import com.gymlog.app.data.ExerciseSet
import com.gymlog.app.data.ExerciseType
import com.gymlog.app.data.SessionStatus
import com.gymlog.app.data.SetStatus
import com.gymlog.app.data.Workout
import com.gymlog.app.data.WorkoutExercise
import com.gymlog.app.data.WorkoutSession
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

const val BACKUP_FORMAT_VERSION = 1

/** Everything GymLog stores, as Room entities. */
data class BackupData(
    val exercises: List<Exercise>,
    val workouts: List<Workout>,
    val workoutExercises: List<WorkoutExercise>,
    val sessions: List<WorkoutSession>,
    val sets: List<ExerciseSet>,
)

// The file format. Deliberately separate from the Room entities so a schema change can't
// silently change (and break) the format of existing backups.

@Serializable
data class BackupFile(
    val formatVersion: Int,
    val exportedAt: String,
    val appVersion: String,
    val exercises: List<ExerciseDto>,
    val workouts: List<WorkoutDto>,
    val workoutExercises: List<WorkoutExerciseDto>,
    val sessions: List<SessionDto>,
    val sets: List<SetDto>,
)

@Serializable
data class ExerciseDto(
    val id: Long,
    val name: String,
    val type: ExerciseType,
    val cardioFixedDimension: CardioFixedDimension? = null,
    val fixedValue: Int? = null,
    val level: Int? = null,
    val distanceDisplayKm: Boolean = false,
    val weightIncrementKg: Double = 2.5,
)

@Serializable
data class WorkoutDto(val id: Long, val name: String)

@Serializable
data class WorkoutExerciseDto(
    val id: Long,
    val workoutId: Long,
    val exerciseId: Long,
    val targetSets: Int,
    val targetReps: Int? = null,
    val targetWeightKg: Double? = null,
    val targetDistanceM: Int? = null,
    val targetDurationSec: Int? = null,
    val sortOrder: Int,
)

@Serializable
data class SessionDto(
    val id: Long,
    val workoutId: Long? = null,
    val date: String,
    val status: SessionStatus,
    val startedAt: String,
    val completedAt: String? = null,
)

@Serializable
data class SetDto(
    val id: Long,
    val sessionId: Long,
    val exerciseId: Long,
    val setNumber: Int,
    val weightKg: Double? = null,
    val repsCompleted: Int? = null,
    val distanceM: Int? = null,
    val durationSec: Int? = null,
    val status: SetStatus,
)

fun BackupData.toFile(exportedAt: Instant, appVersion: String) = BackupFile(
    formatVersion = BACKUP_FORMAT_VERSION,
    exportedAt = exportedAt.toString(),
    appVersion = appVersion,
    exercises = exercises.map {
        ExerciseDto(it.id, it.name, it.type, it.cardioFixedDimension, it.fixedValue, it.level, it.distanceDisplayKm, it.weightIncrementKg)
    },
    workouts = workouts.map { WorkoutDto(it.id, it.name) },
    workoutExercises = workoutExercises.map {
        WorkoutExerciseDto(
            it.id, it.workoutId, it.exerciseId, it.targetSets, it.targetReps, it.targetWeightKg,
            it.targetDistanceM, it.targetDurationSec, it.sortOrder,
        )
    },
    sessions = sessions.map {
        SessionDto(it.id, it.workoutId, it.date.toString(), it.status, it.startedAt.toString(), it.completedAt?.toString())
    },
    sets = sets.map {
        SetDto(it.id, it.sessionId, it.exerciseId, it.setNumber, it.weightKg, it.repsCompleted, it.distanceM, it.durationSec, it.status)
    },
)

/** Throws [java.time.DateTimeException] if a date or instant string is malformed. */
fun BackupFile.toData() = BackupData(
    exercises = exercises.map {
        Exercise(it.id, it.name, it.type, it.cardioFixedDimension, it.fixedValue, it.level, it.distanceDisplayKm, it.weightIncrementKg)
    },
    workouts = workouts.map { Workout(it.id, it.name) },
    workoutExercises = workoutExercises.map {
        WorkoutExercise(
            it.id, it.workoutId, it.exerciseId, it.targetSets, it.targetReps, it.targetWeightKg,
            it.targetDistanceM, it.targetDurationSec, it.sortOrder,
        )
    },
    sessions = sessions.map {
        WorkoutSession(
            it.id, it.workoutId, LocalDate.parse(it.date), it.status,
            Instant.parse(it.startedAt), it.completedAt?.let(Instant::parse),
        )
    },
    sets = sets.map {
        ExerciseSet(it.id, it.sessionId, it.exerciseId, it.setNumber, it.weightKg, it.repsCompleted, it.distanceM, it.durationSec, it.status)
    },
)
