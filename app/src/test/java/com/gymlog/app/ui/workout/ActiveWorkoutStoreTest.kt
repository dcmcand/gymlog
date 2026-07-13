package com.gymlog.app.ui.workout

import com.gymlog.app.data.ExerciseSet
import com.gymlog.app.data.SetStatus
import org.junit.Assert.assertEquals
import org.junit.Test

private fun set(
    id: Long,
    num: Int,
    status: SetStatus,
    reps: Int? = 5,
    weight: Double? = 60.0,
    exerciseId: Long = 1,
) = ExerciseSet(
    id = id,
    sessionId = 1,
    exerciseId = exerciseId,
    setNumber = num,
    weightKg = weight,
    repsCompleted = reps,
    status = status,
)

class ActiveWorkoutStoreTest {

    @Test
    fun `firstPendingIndex returns first PENDING in order`() {
        data class Case(val sets: List<ExerciseSet>, val expected: Int)
        val cases = listOf(
            Case(listOf(set(1, 1, SetStatus.EASY), set(2, 2, SetStatus.PENDING), set(3, 3, SetStatus.PENDING)), 1),
            Case(listOf(set(1, 1, SetStatus.PENDING)), 0),
            Case(listOf(set(1, 1, SetStatus.EASY), set(2, 2, SetStatus.HARD)), -1),
            Case(emptyList(), -1),
        )
        for (c in cases) assertEquals(c.expected, firstPendingIndex(c.sets))
    }

    @Test
    fun `completeSet sets status and keeps target reps`() {
        val s = set(1, 1, SetStatus.PENDING, reps = 5)
        assertEquals(SetStatus.HARD, completeSet(s, SetStatus.HARD).status)
        assertEquals(5, completeSet(s, SetStatus.HARD).repsCompleted)
    }

    @Test
    fun `watchContextFor formats weight reps and set label`() {
        val ctx = watchContextFor(
            "Bench Press",
            set(1, 2, SetStatus.PENDING, reps = 5, weight = 60.0),
            positionInExercise = 2,
            countInExercise = 4,
        )
        assertEquals("Bench Press", ctx.exerciseName)
        assertEquals("60kg x 5", ctx.targetText)
        assertEquals("Set 2 of 4", ctx.setLabel)
    }

    @Test
    fun `snapshot derives current set and watch context`() {
        val sets = listOf(
            set(1, 1, SetStatus.EASY, exerciseId = 1),
            set(2, 2, SetStatus.PENDING, reps = 8, weight = 40.0, exerciseId = 1),
        )
        val snap = ActiveWorkoutSnapshot(
            session = com.gymlog.app.data.WorkoutSession(
                id = 1,
                workoutId = 1,
                date = java.time.LocalDate.of(2026, 7, 10),
                status = com.gymlog.app.data.SessionStatus.IN_PROGRESS,
                startedAt = java.time.Instant.EPOCH,
            ),
            orderedSets = sets,
            exerciseNames = mapOf(1L to "Squat"),
        )
        assertEquals(1, snap.currentIndex)
        assertEquals(2L, snap.currentSet?.id)
        assertEquals("Squat", snap.watchContext()?.exerciseName)
        assertEquals("40kg x 8", snap.watchContext()?.targetText)
        assertEquals("Set 2 of 2", snap.watchContext()?.setLabel)
    }
}
