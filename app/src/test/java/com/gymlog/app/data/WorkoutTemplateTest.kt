package com.gymlog.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutTemplateTest {

    @Test
    fun `template has name and auto-generated id`() {
        val template = WorkoutTemplate(name = "Push Day")
        assertEquals("Push Day", template.name)
        assertEquals(0L, template.id)
    }

    @Test
    fun `template exercise links template to exercise with targets`() {
        val te = WorkoutTemplateExercise(
            templateId = 1L,
            exerciseId = 2L,
            targetSets = 5,
            targetReps = 8,
            targetWeightKg = 100.0,
            targetDistanceM = null,
            targetDurationSec = null,
            sortOrder = 0
        )
        assertEquals(1L, te.templateId)
        assertEquals(2L, te.exerciseId)
        assertEquals(5, te.targetSets)
        assertEquals(8, te.targetReps)
        assertEquals(100.0, te.targetWeightKg!!, 0.01)
        assertNull(te.targetDistanceM)
        assertNull(te.targetDurationSec)
        assertEquals(0, te.sortOrder)
    }

    @Test
    fun `cardio template exercise has distance and duration`() {
        val te = WorkoutTemplateExercise(
            templateId = 1L,
            exerciseId = 3L,
            targetSets = 1,
            targetReps = null,
            targetWeightKg = null,
            targetDistanceM = 2000,
            targetDurationSec = 480,
            sortOrder = 2
        )
        assertEquals(2000, te.targetDistanceM)
        assertEquals(480, te.targetDurationSec)
        assertNull(te.targetReps)
        assertNull(te.targetWeightKg)
    }
}
