package com.gymlog.app.ui.workout

import com.gymlog.app.data.ExerciseSet
import com.gymlog.app.data.ExerciseType
import com.gymlog.app.data.Exercise
import com.gymlog.app.data.SetStatus
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ActiveWorkoutStateTest {

    private lateinit var state: ActiveWorkoutState

    private val squat = Exercise(id = 1L, name = "Squat", type = ExerciseType.WEIGHT)
    private val rowing = Exercise(id = 2L, name = "Rowing", type = ExerciseType.CARDIO)

    private fun weightSet(id: Long, exerciseId: Long, setNumber: Int, weight: Double, reps: Int) =
        ExerciseSet(
            id = id,
            sessionId = 1L,
            exerciseId = exerciseId,
            setNumber = setNumber,
            weightKg = weight,
            repsCompleted = reps,
            status = SetStatus.PENDING
        )

    private fun cardioSet(id: Long, exerciseId: Long, setNumber: Int, distance: Int, duration: Int) =
        ExerciseSet(
            id = id,
            sessionId = 1L,
            exerciseId = exerciseId,
            setNumber = setNumber,
            distanceM = distance,
            durationSec = duration,
            status = SetStatus.PENDING
        )

    @Before
    fun setUp() {
        state = ActiveWorkoutState()
        state.addExercise(
            squat,
            listOf(
                weightSet(1L, 1L, 1, 60.0, 8),
                weightSet(2L, 1L, 2, 60.0, 8)
            )
        )
        state.addExercise(
            rowing,
            listOf(cardioSet(3L, 2L, 1, 2000, 480))
        )
    }

    @Test
    fun `updating set status returns new snapshot with change applied`() {
        val original = state.getExerciseSets(squat.id)
        val updated = original[0].copy(status = SetStatus.COMPLETED)

        state.updateSet(squat.id, 0, updated)

        val afterUpdate = state.getExerciseSets(squat.id)
        assertEquals(SetStatus.COMPLETED, afterUpdate[0].status)
    }

    @Test
    fun `updating set status does not affect other sets`() {
        val original = state.getExerciseSets(squat.id)
        val updated = original[0].copy(status = SetStatus.FAILED)

        state.updateSet(squat.id, 0, updated)

        val afterUpdate = state.getExerciseSets(squat.id)
        assertEquals(SetStatus.PENDING, afterUpdate[1].status)
    }

    @Test
    fun `updating weight returns new snapshot with weight changed`() {
        val original = state.getExerciseSets(squat.id)
        val updated = original[0].copy(weightKg = 65.0)

        state.updateSet(squat.id, 0, updated)

        val afterUpdate = state.getExerciseSets(squat.id)
        assertEquals(65.0, afterUpdate[0].weightKg!!, 0.01)
    }

    @Test
    fun `updating reps returns new snapshot with reps changed`() {
        val original = state.getExerciseSets(squat.id)
        val updated = original[0].copy(repsCompleted = 10)

        state.updateSet(squat.id, 0, updated)

        val afterUpdate = state.getExerciseSets(squat.id)
        assertEquals(10, afterUpdate[0].repsCompleted)
    }

    @Test
    fun `adding a set increases set count`() {
        val countBefore = state.getExerciseSets(squat.id).size

        state.addSet(squat.id, weightSet(10L, 1L, 3, 60.0, 8))

        val countAfter = state.getExerciseSets(squat.id).size
        assertEquals(countBefore + 1, countAfter)
    }

    @Test
    fun `state version increments on update`() {
        val versionBefore = state.version

        val sets = state.getExerciseSets(squat.id)
        state.updateSet(squat.id, 0, sets[0].copy(status = SetStatus.COMPLETED))

        assertEquals(versionBefore + 1, state.version)
    }

    @Test
    fun `state version increments on add set`() {
        val versionBefore = state.version

        state.addSet(squat.id, weightSet(10L, 1L, 3, 60.0, 8))

        assertEquals(versionBefore + 1, state.version)
    }

    @Test
    fun `exercises list returns all exercises in order`() {
        val exercises = state.exercises
        assertEquals(2, exercises.size)
        assertEquals("Squat", exercises[0].name)
        assertEquals("Rowing", exercises[1].name)
    }
}
