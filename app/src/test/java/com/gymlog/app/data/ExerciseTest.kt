package com.gymlog.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseTest {

    @Test
    fun `exercise has name and type`() {
        val exercise = Exercise(name = "Squat", type = ExerciseType.WEIGHT)
        assertEquals("Squat", exercise.name)
        assertEquals(ExerciseType.WEIGHT, exercise.type)
    }

    @Test
    fun `exercise id defaults to 0`() {
        val exercise = Exercise(name = "Deadlift", type = ExerciseType.WEIGHT)
        assertEquals(0L, exercise.id)
    }

    @Test
    fun `exercise type has WEIGHT and CARDIO`() {
        assertEquals(2, ExerciseType.entries.size)
        assertEquals("WEIGHT", ExerciseType.WEIGHT.name)
        assertEquals("CARDIO", ExerciseType.CARDIO.name)
    }
}
