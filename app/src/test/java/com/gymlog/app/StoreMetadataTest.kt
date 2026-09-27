package com.gymlog.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import java.io.File

/**
 * Store and F-Droid requirements that are easy to break by accident. Unit tests run with the
 * module dir (`app/`) as the working directory.
 */
class StoreMetadataTest {

    @Test
    fun `apks carry no google-encrypted dependency blob`() {
        // F-Droid ships our signing block as-is for reproducible builds, and rejects this blob.
        val gradle = File("build.gradle.kts").readText()
        assertTrue(Regex("""dependenciesInfo\s*\{[^}]*includeInApk\s*=\s*false""").containsMatchIn(gradle))
    }

    @Test
    fun `release apks do not depend on how the source was checked out`() {
        // AGP embeds the git revision (or an error if it finds no .git dir) unless told not to,
        // so a worktree or tarball build would differ from F-Droid's clone.
        val gradle = File("build.gradle.kts").readText()
        assertTrue(Regex("""release\s*\{[^}]*vcsInfo\.include\s*=\s*false""").containsMatchIn(gradle))
    }

    private val listing = File("../fastlane/metadata/android/en-US")

    @Test
    fun `fastlane text follows the F-Droid limits`() {
        val short = File(listing, "short_description.txt").readText().trim()
        data class Case(val name: String, val ok: Boolean)
        val cases = mutableListOf(
            Case("short description under 80 characters (${short.length})", short.length < 80),
            Case("short description has no trailing period", !short.endsWith(".")),
            Case("title present", File(listing, "title.txt").readText().isNotBlank()),
        )
        File(listing, "changelogs").listFiles()!!.forEach { f ->
            val n = f.readText().trim().length
            cases += Case("changelog ${f.name} at most 500 characters ($n)", n <= 500)
        }
        for (c in cases) assertTrue(c.name, c.ok)
    }

    @Test
    fun `store icon is a 512 px square png`() {
        val icon = File(listing, "images/icon.png")
        assertTrue("icon.png exists", icon.exists())
        DataInputStream(icon.inputStream()).use { input ->
            val header = ByteArray(16).also { input.readFully(it) }
            assertTrue("PNG signature", header.copyOfRange(1, 4).decodeToString() == "PNG")
            val width = input.readInt()
            val height = input.readInt()
            assertTrue("512x512 (was ${width}x$height)", width == 512 && height == 512)
        }
    }
}
