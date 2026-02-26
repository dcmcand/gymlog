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

    @Test
    fun `displayName for weight exercise returns just name`() {
        val exercise = Exercise(name = "Bench Press", type = ExerciseType.WEIGHT)
        assertEquals("Bench Press", exercise.displayName())
    }

    @Test
    fun `displayName for fixed distance in km`() {
        val exercise = Exercise(
            name = "Run",
            type = ExerciseType.CARDIO,
            cardioFixedDimension = CardioFixedDimension.DISTANCE,
            fixedValue = 5000,
            distanceDisplayKm = true
        )
        assertEquals("Run - 5k", exercise.displayName())
    }

    @Test
    fun `displayName for fixed distance in meters`() {
        val exercise = Exercise(
            name = "Sprint",
            type = ExerciseType.CARDIO,
            cardioFixedDimension = CardioFixedDimension.DISTANCE,
            fixedValue = 400,
            distanceDisplayKm = false
        )
        assertEquals("Sprint - 400m", exercise.displayName())
    }

    @Test
    fun `displayName for fixed time`() {
        val exercise = Exercise(
            name = "Exercise Bike",
            type = ExerciseType.CARDIO,
            cardioFixedDimension = CardioFixedDimension.TIME,
            fixedValue = 1800
        )
        assertEquals("Exercise Bike - 30min", exercise.displayName())
    }

    @Test
    fun `displayName for fixed distance with level`() {
        val exercise = Exercise(
            name = "Exercise Bike",
            type = ExerciseType.CARDIO,
            cardioFixedDimension = CardioFixedDimension.TIME,
            fixedValue = 1800,
            level = 7
        )
        assertEquals("Exercise Bike - 30min - L7", exercise.displayName())
    }

    @Test
    fun `displayName for fixed distance km with level`() {
        val exercise = Exercise(
            name = "Run",
            type = ExerciseType.CARDIO,
            cardioFixedDimension = CardioFixedDimension.DISTANCE,
            fixedValue = 5000,
            distanceDisplayKm = true,
            level = 3
        )
        assertEquals("Run - 5k - L3", exercise.displayName())
    }

    @Test
    fun `displayName for legacy cardio returns just name`() {
        val exercise = Exercise(
            name = "Rowing",
            type = ExerciseType.CARDIO
        )
        assertEquals("Rowing", exercise.displayName())
    }
}
