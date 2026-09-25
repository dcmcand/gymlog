package com.gymlog.app.watch

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pins the application ID (com.gymlog.app is taken on Google Play) and keeps the watchapp's
 * companion list in step with it. Unit tests run with the module dir (`app/`) as the working
 * directory.
 */
class AppIdTest {

    @Test
    fun `app and watchapp agree on the application id`() {
        val gradle = File("build.gradle.kts").readText()
        val pebble = File("../pebble/package.json").readText()
        data class Case(val name: String, val ok: Boolean)
        val cases = listOf(
            Case("applicationId is io.github.dcmcand.gymlog", gradle.contains("applicationId = \"io.github.dcmcand.gymlog\"")),
            Case("namespace stays com.gymlog.app", gradle.contains("namespace = \"com.gymlog.app\"")),
            Case("watchapp lists the new package", pebble.contains("\"package\": \"io.github.dcmcand.gymlog\"")),
            // Kept while users move from the old install; drop in a later release.
            Case("watchapp still lists the old package", pebble.contains("\"package\": \"com.gymlog.app\"")),
        )
        for (c in cases) assertTrue(c.name, c.ok)
    }
}
