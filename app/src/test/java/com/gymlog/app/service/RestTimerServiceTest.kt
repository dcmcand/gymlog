package com.gymlog.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestTimerServiceTest {

    @Test
    fun `default TimerState is idle`() {
        val state = TimerState()
        assertFalse(state.isRunning)
        assertEquals(0, state.remainingSeconds)
        assertEquals(0, state.totalSeconds)
        assertNull(state.sessionId)
        assertEquals(0L, state.endTimeMs)
    }

    @Test
    fun `TimerState transitions - table driven`() {
        data class Case(
            val label: String,
            val state: TimerState,
            val expectedRemaining: Int,
            val expectedRunning: Boolean,
            val expectedTotal: Int
        )

        val running = TimerState(
            isRunning = true,
            remainingSeconds = 90,
            totalSeconds = 90,
            sessionId = 42L,
            endTimeMs = 1000L
        )

        val cases = listOf(
            Case(
                "tick decrements remaining",
                running.copy(remainingSeconds = 89),
                expectedRemaining = 89,
                expectedRunning = true,
                expectedTotal = 90
            ),
            Case(
                "timer finishes",
                running.copy(remainingSeconds = 0, isRunning = false),
                expectedRemaining = 0,
                expectedRunning = false,
                expectedTotal = 90
            ),
            Case(
                "extend adds to remaining and total",
                running.copy(remainingSeconds = 180, totalSeconds = 180),
                expectedRemaining = 180,
                expectedRunning = true,
                expectedTotal = 180
            )
        )

        for (case in cases) {
            assertEquals(case.label, case.expectedRemaining, case.state.remainingSeconds)
            assertEquals(case.label, case.expectedRunning, case.state.isRunning)
            assertEquals(case.label, case.expectedTotal, case.state.totalSeconds)
            assertEquals(case.label, 42L, case.state.sessionId)
        }
    }

    @Test
    fun `running state has all fields populated`() {
        val state = TimerState(
            isRunning = true,
            remainingSeconds = 45,
            totalSeconds = 90,
            sessionId = 7L,
            endTimeMs = 999L
        )
        assertTrue(state.isRunning)
        assertEquals(45, state.remainingSeconds)
        assertEquals(90, state.totalSeconds)
        assertEquals(7L, state.sessionId)
        assertEquals(999L, state.endTimeMs)
    }
}
