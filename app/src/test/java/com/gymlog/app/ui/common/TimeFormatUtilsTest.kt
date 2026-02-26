package com.gymlog.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeFormatUtilsTest {

    @Test
    fun `parseMMSS parses valid time`() {
        assertEquals(1530, parseMMSS("25:30"))
    }

    @Test
    fun `parseMMSS parses zero minutes`() {
        assertEquals(45, parseMMSS("0:45"))
    }

    @Test
    fun `parseMMSS parses zero seconds`() {
        assertEquals(300, parseMMSS("5:00"))
    }

    @Test
    fun `parseMMSS returns null for empty string`() {
        assertNull(parseMMSS(""))
    }

    @Test
    fun `parseMMSS returns null for invalid format`() {
        assertNull(parseMMSS("abc"))
    }

    @Test
    fun `parseMMSS returns null for seconds 60 or above`() {
        assertNull(parseMMSS("5:60"))
    }

    @Test
    fun `parseMMSS returns null for missing colon`() {
        assertNull(parseMMSS("530"))
    }

    @Test
    fun `parseMMSS trims whitespace`() {
        assertEquals(90, parseMMSS("  1:30  "))
    }

    @Test
    fun `formatMMSS formats seconds correctly`() {
        assertEquals("25:30", formatMMSS(1530))
    }

    @Test
    fun `formatMMSS formats zero minutes`() {
        assertEquals("0:45", formatMMSS(45))
    }

    @Test
    fun `formatMMSS pads single-digit seconds`() {
        assertEquals("5:00", formatMMSS(300))
    }

    @Test
    fun `formatMMSS returns dash for null`() {
        assertEquals("-", formatMMSS(null))
    }

    @Test
    fun `formatMMSS formats zero`() {
        assertEquals("0:00", formatMMSS(0))
    }

    @Test
    fun `round trip parse then format`() {
        val original = "12:05"
        val seconds = parseMMSS(original)
        assertEquals(original, formatMMSS(seconds))
    }

    @Test
    fun `round trip format then parse`() {
        val originalSeconds = 450
        val formatted = formatMMSS(originalSeconds)
        assertEquals(originalSeconds, parseMMSS(formatted))
    }
}
