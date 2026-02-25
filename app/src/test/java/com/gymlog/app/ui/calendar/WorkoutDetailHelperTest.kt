package com.gymlog.app.ui.calendar

import com.gymlog.app.data.ExerciseSet
import com.gymlog.app.data.SetStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutDetailHelperTest {

    private fun makeSet(
        exerciseId: Long,
        setNumber: Int,
        weight: Double? = null,
        reps: Int? = null,
        distance: Int? = null,
        duration: Int? = null,
        status: SetStatus = SetStatus.COMPLETED
    ) = ExerciseSet(
        id = 0L,
        sessionId = 1L,
        exerciseId = exerciseId,
        setNumber = setNumber,
        weightKg = weight,
        repsCompleted = reps,
        distanceM = distance,
        durationSec = duration,
        status = status
    )

    @Test
    fun `groupSetsByExercise groups sets by exerciseId preserving order`() {
        val sets = listOf(
            makeSet(exerciseId = 1L, setNumber = 1, weight = 60.0, reps = 8),
            makeSet(exerciseId = 1L, setNumber = 2, weight = 60.0, reps = 8),
            makeSet(exerciseId = 2L, setNumber = 1, distance = 2000, duration = 480)
        )

        val grouped = groupSetsByExercise(sets)

        assertEquals(2, grouped.size)
        assertEquals(1L, grouped[0].first)
        assertEquals(2, grouped[0].second.size)
        assertEquals(2L, grouped[1].first)
        assertEquals(1, grouped[1].second.size)
    }

    @Test
    fun `groupSetsByExercise returns empty list for no sets`() {
        val grouped = groupSetsByExercise(emptyList())
        assertEquals(0, grouped.size)
    }

    @Test
    fun `groupSetsByExercise preserves set order within exercise`() {
        val sets = listOf(
            makeSet(exerciseId = 1L, setNumber = 1, weight = 50.0, reps = 8),
            makeSet(exerciseId = 1L, setNumber = 2, weight = 55.0, reps = 6),
            makeSet(exerciseId = 1L, setNumber = 3, weight = 60.0, reps = 5)
        )

        val grouped = groupSetsByExercise(sets)

        assertEquals(1, grouped.size)
        assertEquals(3, grouped[0].second.size)
        assertEquals(50.0, grouped[0].second[0].weightKg!!, 0.01)
        assertEquals(55.0, grouped[0].second[1].weightKg!!, 0.01)
        assertEquals(60.0, grouped[0].second[2].weightKg!!, 0.01)
    }

    @Test
    fun `formatDuration returns dash for null`() {
        assertEquals("-", formatDuration(null))
    }

    @Test
    fun `formatDuration formats minutes and seconds`() {
        assertEquals("8m 0s", formatDuration(480))
    }

    @Test
    fun `formatDuration formats seconds only`() {
        assertEquals("0m 45s", formatDuration(45))
    }

    @Test
    fun `formatDuration formats mixed minutes and seconds`() {
        assertEquals("7m 30s", formatDuration(450))
    }

    @Test
    fun `groupSetsByExercise preserves exercise appearance order`() {
        val sets = listOf(
            makeSet(exerciseId = 3L, setNumber = 1, weight = 100.0, reps = 5),
            makeSet(exerciseId = 1L, setNumber = 1, weight = 60.0, reps = 8),
            makeSet(exerciseId = 3L, setNumber = 2, weight = 100.0, reps = 5)
        )

        val grouped = groupSetsByExercise(sets)

        assertEquals(2, grouped.size)
        assertEquals(3L, grouped[0].first)
        assertEquals(2, grouped[0].second.size)
        assertEquals(1L, grouped[1].first)
        assertEquals(1, grouped[1].second.size)
    }
}
