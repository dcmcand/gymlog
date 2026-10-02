package com.gymlog.app.watch

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * Sending workout data to the Pebble app counts as sharing with another app (Play Data safety),
 * so it stays off until the user turns it on.
 */
class WatchOptInTest {

    /** In-memory SharedPreferences: only the calls WatchOptIn makes are implemented. */
    private fun fakePrefs(): SharedPreferences {
        val values = mutableMapOf<String, Any?>()
        lateinit var editor: SharedPreferences.Editor
        editor = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { _, m, args ->
            when (m.name) {
                "putBoolean" -> { values[args[0] as String] = args[1]; editor }
                "apply" -> Unit
                "commit" -> true
                else -> throw UnsupportedOperationException(m.name)
            }
        } as SharedPreferences.Editor
        return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences::class.java)) { _, m, args ->
            when (m.name) {
                "getBoolean" -> values[args[0] as String] as Boolean? ?: args[1]
                "edit" -> editor
                else -> throw UnsupportedOperationException(m.name)
            }
        } as SharedPreferences
    }

    @Test
    fun `watch link is off until the user turns it on`() {
        data class Case(val name: String, val writes: List<Boolean>, val expected: Boolean)
        val cases = listOf(
            Case("fresh install", emptyList(), false),
            Case("turned on", listOf(true), true),
            Case("turned on then off", listOf(true, false), false),
        )
        for (c in cases) {
            val prefs = fakePrefs()
            c.writes.forEach { WatchOptIn.setEnabled(prefs, it) }
            assertEquals(c.name, c.expected, WatchOptIn.isEnabled(prefs))
        }
    }
}
