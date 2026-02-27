package com.gymlog.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class WeightSuggestionTest {

    // -- roundToNearest2Point5 --

    @Test
    fun `roundToNearest2Point5 - table driven`() {
        data class Case(val input: Double, val expected: Double)
        val cases = listOf(
            Case(0.0, 0.0),
            Case(2.5, 2.5),
            Case(5.0, 5.0),
            Case(1.0, 0.0),
            Case(1.25, 2.5),
            Case(1.24, 0.0),
            Case(1.26, 2.5),
            Case(3.75, 5.0),
            Case(6.3, 7.5),
            Case(100.0, 100.0),
            Case(101.0, 100.0),
            Case(101.25, 102.5),
            Case(101.24, 100.0),
            Case(72.0, 72.5),
            Case(73.75, 75.0),
        )
        for (case in cases) {
            assertEquals(
                "roundToNearest2Point5(${case.input})",
                case.expected,
                roundToNearest2Point5(case.input),
                0.001
            )
        }
    }

    // -- suggestWeight: no data --

    @Test
    fun `empty sets returns null`() {
        assertNull(suggestWeight(emptyList(), LocalDate.of(2026, 2, 20), LocalDate.of(2026, 2, 25)))
    }

    @Test
    fun `all pending sets returns null`() {
        val sets = listOf(makeSet(weightKg = 100.0, status = SetStatus.PENDING))
        assertNull(suggestWeight(sets, LocalDate.of(2026, 2, 20), LocalDate.of(2026, 2, 25)))
    }

    @Test
    fun `null weights returns null`() {
        val sets = listOf(makeSet(weightKg = null, status = SetStatus.EASY))
        assertNull(suggestWeight(sets, LocalDate.of(2026, 2, 20), LocalDate.of(2026, 2, 25)))
    }

    // -- suggestWeight: recent (<=10 days) --

    @Test
    fun `recent - table driven`() {
        data class Case(
            val label: String,
            val statuses: List<SetStatus>,
            val baseWeight: Double,
            val expected: Double
        )
        val today = LocalDate.of(2026, 2, 25)
        val recent = LocalDate.of(2026, 2, 20) // 5 days ago
        val cases = listOf(
            Case("all easy +5%", listOf(SetStatus.EASY, SetStatus.EASY, SetStatus.EASY), 100.0, 105.0),
            Case("single hard still increases", listOf(SetStatus.EASY, SetStatus.HARD), 100.0, 105.0),
            Case("partial stays same", listOf(SetStatus.EASY, SetStatus.PARTIAL), 100.0, 100.0),
            Case("failed -5%", listOf(SetStatus.EASY, SetStatus.FAILED), 100.0, 95.0),
            Case("failed beats partial", listOf(SetStatus.PARTIAL, SetStatus.FAILED), 100.0, 95.0),
            Case("failed beats hard", listOf(SetStatus.HARD, SetStatus.FAILED), 100.0, 95.0),
            Case("all easy 80kg", listOf(SetStatus.EASY), 80.0, 85.0),
            Case("multiple hard stays same", listOf(SetStatus.EASY, SetStatus.HARD, SetStatus.HARD), 100.0, 100.0),
            Case("single hard among easy increases", listOf(SetStatus.EASY, SetStatus.EASY, SetStatus.HARD), 100.0, 105.0),
        )
        for (case in cases) {
            val sets = case.statuses.map { makeSet(weightKg = case.baseWeight, status = it) }
            assertEquals(
                case.label,
                case.expected,
                suggestWeight(sets, recent, today)!!,
                0.001
            )
        }
    }

    // -- suggestWeight: stale (>10 days) --

    @Test
    fun `stale - table driven`() {
        data class Case(
            val label: String,
            val statuses: List<SetStatus>,
            val baseWeight: Double,
            val expected: Double
        )
        val today = LocalDate.of(2026, 2, 25)
        val stale = LocalDate.of(2026, 2, 10) // 15 days ago
        val cases = listOf(
            Case("all easy stays same", listOf(SetStatus.EASY, SetStatus.EASY), 100.0, 100.0),
            Case("hard -10%", listOf(SetStatus.EASY, SetStatus.HARD), 100.0, 90.0),
            Case("partial -20%", listOf(SetStatus.EASY, SetStatus.PARTIAL), 100.0, 80.0),
            Case("failed -20%", listOf(SetStatus.EASY, SetStatus.FAILED), 100.0, 80.0),
            Case("hard 80kg -10%", listOf(SetStatus.HARD), 80.0, 72.5),
        )
        for (case in cases) {
            val sets = case.statuses.map { makeSet(weightKg = case.baseWeight, status = it) }
            assertEquals(
                case.label,
                case.expected,
                suggestWeight(sets, stale, today)!!,
                0.001
            )
        }
    }

    // -- boundary: exactly 10 days uses recent rules --

    @Test
    fun `exactly 10 days uses recent rules`() {
        val today = LocalDate.of(2026, 2, 25)
        val tenDaysAgo = LocalDate.of(2026, 2, 15)
        val sets = listOf(makeSet(weightKg = 100.0, status = SetStatus.EASY))
        // Recent: all easy -> +5%
        assertEquals(105.0, suggestWeight(sets, tenDaysAgo, today)!!, 0.001)
    }

    @Test
    fun `11 days uses stale rules`() {
        val today = LocalDate.of(2026, 2, 25)
        val elevenDaysAgo = LocalDate.of(2026, 2, 14)
        val sets = listOf(makeSet(weightKg = 100.0, status = SetStatus.EASY))
        // Stale: all easy -> same
        assertEquals(100.0, suggestWeight(sets, elevenDaysAgo, today)!!, 0.001)
    }

    // -- max weight as base --

    @Test
    fun `uses heaviest weight across sets as base`() {
        val today = LocalDate.of(2026, 2, 25)
        val recent = LocalDate.of(2026, 2, 20)
        val sets = listOf(
            makeSet(weightKg = 40.0, status = SetStatus.EASY),  // warmup
            makeSet(weightKg = 80.0, status = SetStatus.EASY),  // warmup
            makeSet(weightKg = 100.0, status = SetStatus.EASY), // working
            makeSet(weightKg = 100.0, status = SetStatus.EASY), // working
        )
        // Base = 100, all easy recent -> 105
        assertEquals(105.0, suggestWeight(sets, recent, today)!!, 0.001)
    }

    // -- issue examples --

    @Test
    fun `50kg all easy recent should suggest 52 point 5 kg`() {
        val today = LocalDate.of(2026, 2, 25)
        val recent = LocalDate.of(2026, 2, 20)
        val sets = listOf(
            makeSet(weightKg = 50.0, status = SetStatus.EASY),
            makeSet(weightKg = 50.0, status = SetStatus.EASY),
            makeSet(weightKg = 50.0, status = SetStatus.EASY),
        )
        assertEquals(52.5, suggestWeight(sets, recent, today)!!, 0.001)
    }

    @Test
    fun `issue example - 100kg all easy 5 days ago = 105kg`() {
        val today = LocalDate.of(2026, 2, 25)
        val fiveDaysAgo = LocalDate.of(2026, 2, 20)
        val sets = listOf(
            makeSet(weightKg = 100.0, status = SetStatus.EASY),
            makeSet(weightKg = 100.0, status = SetStatus.EASY),
            makeSet(weightKg = 100.0, status = SetStatus.EASY),
        )
        assertEquals(105.0, suggestWeight(sets, fiveDaysAgo, today)!!, 0.001)
    }

    @Test
    fun `issue example - 80kg hard 14 days ago = 72 point 5 kg`() {
        val today = LocalDate.of(2026, 2, 25)
        val fourteenDaysAgo = LocalDate.of(2026, 2, 11)
        val sets = listOf(
            makeSet(weightKg = 80.0, status = SetStatus.HARD),
            makeSet(weightKg = 80.0, status = SetStatus.HARD),
        )
        // Stale + hard -> 0.90 * 80 = 72.0 -> rounds to 72.5
        assertEquals(72.5, suggestWeight(sets, fourteenDaysAgo, today)!!, 0.001)
    }

    // -- mixed statuses priority --

    @Test
    fun `failed takes priority over partial and hard`() {
        val today = LocalDate.of(2026, 2, 25)
        val recent = LocalDate.of(2026, 2, 20)
        val sets = listOf(
            makeSet(weightKg = 100.0, status = SetStatus.EASY),
            makeSet(weightKg = 100.0, status = SetStatus.HARD),
            makeSet(weightKg = 100.0, status = SetStatus.PARTIAL),
            makeSet(weightKg = 100.0, status = SetStatus.FAILED),
        )
        // Recent + failed -> 0.95 * 100 = 95.0
        assertEquals(95.0, suggestWeight(sets, recent, today)!!, 0.001)
    }

    @Test
    fun `stale - failed and partial both give -20 percent`() {
        val today = LocalDate.of(2026, 2, 25)
        val stale = LocalDate.of(2026, 2, 10)
        val sets = listOf(
            makeSet(weightKg = 100.0, status = SetStatus.PARTIAL),
            makeSet(weightKg = 100.0, status = SetStatus.FAILED),
        )
        assertEquals(80.0, suggestWeight(sets, stale, today)!!, 0.001)
    }

    // -- minimum 2.5kg step for light weights --

    @Test
    fun `light weights still increase by at least 2 point 5 kg`() {
        data class Case(val label: String, val baseWeight: Double, val expected: Double)
        val today = LocalDate.of(2026, 2, 25)
        val recent = LocalDate.of(2026, 2, 20)
        val cases = listOf(
            Case("10kg all easy", 10.0, 12.5),
            Case("15kg all easy", 15.0, 17.5),
            Case("20kg all easy", 20.0, 22.5),
            Case("22.5kg all easy", 22.5, 25.0),
        )
        for (case in cases) {
            val sets = listOf(makeSet(weightKg = case.baseWeight, status = SetStatus.EASY))
            assertEquals(
                case.label,
                case.expected,
                suggestWeight(sets, recent, today)!!,
                0.001
            )
        }
    }

    @Test
    fun `light weights still decrease by at least 2 point 5 kg`() {
        data class Case(val label: String, val baseWeight: Double, val expected: Double)
        val today = LocalDate.of(2026, 2, 25)
        val recent = LocalDate.of(2026, 2, 20)
        val cases = listOf(
            Case("10kg failed", 10.0, 7.5),
            Case("15kg failed", 15.0, 12.5),
            Case("20kg failed", 20.0, 17.5),
        )
        for (case in cases) {
            val sets = listOf(makeSet(weightKg = case.baseWeight, status = SetStatus.FAILED))
            assertEquals(
                case.label,
                case.expected,
                suggestWeight(sets, recent, today)!!,
                0.001
            )
        }
    }

    @Test
    fun `very light weight decrease does not go below zero`() {
        val today = LocalDate.of(2026, 2, 25)
        val stale = LocalDate.of(2026, 2, 10)
        val sets = listOf(makeSet(weightKg = 2.5, status = SetStatus.FAILED))
        // 2.5 * 0.80 = 2.0, rounds to 2.5 (same), forced to 2.5-2.5=0.0
        assertEquals(0.0, suggestWeight(sets, stale, today)!!, 0.001)
    }

    // -- pending sets are filtered out --

    @Test
    fun `pending sets are ignored for weight and status`() {
        val today = LocalDate.of(2026, 2, 25)
        val recent = LocalDate.of(2026, 2, 20)
        val sets = listOf(
            makeSet(weightKg = 200.0, status = SetStatus.PENDING), // should be ignored
            makeSet(weightKg = 100.0, status = SetStatus.EASY),
        )
        // Base = 100 (not 200), all completed easy -> 105
        assertEquals(105.0, suggestWeight(sets, recent, today)!!, 0.001)
    }

    private fun makeSet(
        weightKg: Double? = null,
        status: SetStatus = SetStatus.EASY
    ): ExerciseSet = ExerciseSet(
        sessionId = 1L,
        exerciseId = 1L,
        setNumber = 1,
        weightKg = weightKg,
        status = status
    )
}
