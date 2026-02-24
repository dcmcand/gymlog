package com.gymlog.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class WorkoutSessionTest {

    @Test
    fun `session status has IN_PROGRESS and COMPLETED`() {
        assertEquals(2, SessionStatus.entries.size)
    }

    @Test
    fun `set status has PENDING, COMPLETED, PARTIAL, FAILED`() {
        assertEquals(4, SetStatus.entries.size)
    }

    @Test
    fun `session links to template with date and status`() {
        val now = Instant.now()
        val session = WorkoutSession(
            templateId = 1L,
            date = LocalDate.of(2026, 2, 24),
            status = SessionStatus.IN_PROGRESS,
            startedAt = now
        )
        assertEquals(1L, session.templateId)
        assertEquals(LocalDate.of(2026, 2, 24), session.date)
        assertEquals(SessionStatus.IN_PROGRESS, session.status)
        assertEquals(now, session.startedAt)
        assertNull(session.completedAt)
    }

    @Test
    fun `weight exercise set has weight and reps`() {
        val set = ExerciseSet(
            sessionId = 1L,
            exerciseId = 2L,
            setNumber = 1,
            weightKg = 100.0,
            repsCompleted = 8,
            status = SetStatus.COMPLETED
        )
        assertEquals(100.0, set.weightKg!!, 0.01)
        assertEquals(8, set.repsCompleted)
        assertEquals(SetStatus.COMPLETED, set.status)
        assertNull(set.distanceM)
        assertNull(set.durationSec)
    }

    @Test
    fun `cardio exercise set has distance and duration`() {
        val set = ExerciseSet(
            sessionId = 1L,
            exerciseId = 3L,
            setNumber = 1,
            distanceM = 2000,
            durationSec = 480,
            status = SetStatus.COMPLETED
        )
        assertEquals(2000, set.distanceM)
        assertEquals(480, set.durationSec)
        assertNull(set.weightKg)
        assertNull(set.repsCompleted)
    }
}
