package com.gymlog.app.data.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class DataReplacedTest {

    @Test
    fun `each import bumps the generation by one`() {
        data class Case(val signals: Int)
        for (c in listOf(Case(1), Case(3))) {
            val before = DataReplaced.generation.value
            repeat(c.signals) { DataReplaced.signal() }
            assertEquals("${c.signals} signals", before + c.signals, DataReplaced.generation.value)
        }
    }
}
