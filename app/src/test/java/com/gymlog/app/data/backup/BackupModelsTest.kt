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
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Every field type populated, including nulls and non-ASCII names. Shared with BackupCodecTest. */
fun sampleBackupData() = BackupData(
    exercises = listOf(
        Exercise(id = 1, name = "Farmer's walk", type = ExerciseType.WEIGHT, weightIncrementKg = 5.0),
        Exercise(
            id = 2, name = "Rower \"Zercher\" Élévation 🏋️", type = ExerciseType.CARDIO,
            cardioFixedDimension = CardioFixedDimension.DISTANCE, fixedValue = 2000, level = 7,
            distanceDisplayKm = true,
        ),
        Exercise(id = 3, name = "Plank", type = ExerciseType.CARDIO, cardioFixedDimension = CardioFixedDimension.TIME, fixedValue = 60),
    ),
    workouts = listOf(Workout(id = 10, name = "Push")),
    workoutExercises = listOf(
        WorkoutExercise(id = 20, workoutId = 10, exerciseId = 1, targetSets = 5, targetReps = 8, targetWeightKg = 42.5, sortOrder = 0),
        WorkoutExercise(id = 21, workoutId = 10, exerciseId = 2, targetSets = 1, targetDistanceM = 2000, targetDurationSec = 480, sortOrder = 1),
    ),
    sessions = listOf(
        WorkoutSession(
            id = 30, workoutId = 10, date = LocalDate.of(2026, 9, 23), status = SessionStatus.COMPLETED,
            startedAt = Instant.parse("2026-09-23T17:00:00.123Z"), completedAt = Instant.parse("2026-09-23T18:01:02.456Z"),
        ),
        WorkoutSession(
            id = 31, workoutId = null, date = LocalDate.of(2026, 9, 24), status = SessionStatus.IN_PROGRESS,
            startedAt = Instant.parse("2026-09-24T07:00:00Z"),
        ),
    ),
    sets = listOf(
        ExerciseSet(id = 40, sessionId = 30, exerciseId = 1, setNumber = 1, weightKg = 42.5, repsCompleted = 8, status = SetStatus.EASY),
        ExerciseSet(id = 41, sessionId = 30, exerciseId = 2, setNumber = 1, distanceM = 2000, durationSec = 470, status = SetStatus.HARD),
        ExerciseSet(id = 42, sessionId = 31, exerciseId = 1, setNumber = 1, weightKg = null, repsCompleted = null, status = SetStatus.PENDING),
        ExerciseSet(id = 43, sessionId = 31, exerciseId = 3, setNumber = 1, durationSec = 55, status = SetStatus.PARTIAL),
        ExerciseSet(id = 44, sessionId = 31, exerciseId = 1, setNumber = 2, weightKg = 40.0, repsCompleted = 3, status = SetStatus.FAILED),
    ),
)

class BackupModelsTest {

    @Test
    fun `data survives mapping to the file model and back`() {
        data class Case(val name: String, val data: BackupData)
        val cases = listOf(
            Case("every field type", sampleBackupData()),
            Case("empty", BackupData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())),
        )
        for (c in cases) {
            val file = c.data.toFile(Instant.parse("2026-09-25T10:00:00Z"), "1.5")
            assertEquals(c.name, BACKUP_FORMAT_VERSION, file.formatVersion)
            assertEquals(c.name, "2026-09-25T10:00:00Z", file.exportedAt)
            assertEquals(c.name, "1.5", file.appVersion)
            assertEquals(c.name, c.data, file.toData())
        }
    }

    @Test
    fun `dates and instants are ISO-8601 strings`() {
        val file = sampleBackupData().toFile(Instant.EPOCH, "1.5")
        assertEquals("2026-09-23", file.sessions[0].date)
        assertEquals("2026-09-23T17:00:00.123Z", file.sessions[0].startedAt)
        assertEquals(null, file.sessions[1].completedAt)
    }
}
