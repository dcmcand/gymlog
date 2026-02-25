package com.gymlog.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutTest {

    @Test
    fun `workout has name and auto-generated id`() {
        val workout = Workout(name = "Push Day")
        assertEquals("Push Day", workout.name)
        assertEquals(0L, workout.id)
    }

    @Test
    fun `workout exercise links workout to exercise with targets`() {
        val we = WorkoutExercise(
            workoutId = 1L,
            exerciseId = 2L,
            targetSets = 5,
            targetReps = 8,
            targetWeightKg = 100.0,
            targetDistanceM = null,
            targetDurationSec = null,
            sortOrder = 0
        )
        assertEquals(1L, we.workoutId)
        assertEquals(2L, we.exerciseId)
        assertEquals(5, we.targetSets)
        assertEquals(8, we.targetReps)
        assertEquals(100.0, we.targetWeightKg!!, 0.01)
        assertNull(we.targetDistanceM)
        assertNull(we.targetDurationSec)
        assertEquals(0, we.sortOrder)
    }

    @Test
    fun `cardio workout exercise has distance and duration`() {
        val we = WorkoutExercise(
            workoutId = 1L,
            exerciseId = 3L,
            targetSets = 1,
            targetReps = null,
            targetWeightKg = null,
            targetDistanceM = 2000,
            targetDurationSec = 480,
            sortOrder = 2
        )
        assertEquals(2000, we.targetDistanceM)
        assertEquals(480, we.targetDurationSec)
        assertNull(we.targetReps)
        assertNull(we.targetWeightKg)
    }
}
