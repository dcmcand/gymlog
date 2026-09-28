package com.gymlog.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import java.io.File

/**
 * Store and F-Droid requirements that are easy to break by accident. Unit tests run with the
 * module dir (`app/`) as the working directory.
 */
class StoreMetadataTest {

    /** True if [block] { ... } contains [setting] on its own, uncommented line. */
    private fun hasSetting(gradle: String, block: String, setting: String): Boolean {
        val body = Regex("""(?s)\b${Regex.escape(block)}\s*\{([^}]*)""").find(gradle)?.groupValues?.get(1) ?: return false
        val want = setting.replace(" ", "")
        return body.lines().any { it.trim().replace(" ", "") == want }
    }

    @Test
    fun `setting checks ignore commented-out lines`() {
        data class Case(val name: String, val gradle: String, val expected: Boolean)
        val cases = listOf(
            Case("set", "dependenciesInfo {\n    includeInApk = false\n}", true),
            Case("commented out", "dependenciesInfo {\n    // includeInApk = false\n}", false),
            Case("inline comment", "dependenciesInfo { // includeInApk = false\n}", false),
            Case("set to true", "dependenciesInfo {\n    includeInApk = true\n}", false),
            Case("block missing", "android {\n}", false),
        )
        for (c in cases) assertEquals(c.name, c.expected, hasSetting(c.gradle, "dependenciesInfo", "includeInApk = false"))
    }

    @Test
    fun `apks carry no google-encrypted dependency blob`() {
        // F-Droid ships our signing block as-is for reproducible builds, and rejects this blob.
        val gradle = File("build.gradle.kts").readText()
        assertTrue(hasSetting(gradle, "dependenciesInfo", "includeInApk = false"))
    }

    @Test
    fun `release builds are shrunk with R8`() {
        // F-Droid asked for it; without it the APK is ~48 MB, mostly unused library code.
        val gradle = File("build.gradle.kts").readText()
        data class Case(val name: String, val ok: Boolean)
        val cases = listOf(
            Case("code shrinking on", hasSetting(gradle, "release", "isMinifyEnabled = true")),
            Case("resource shrinking on", hasSetting(gradle, "release", "isShrinkResources = true")),
            Case("project rules keep names readable", File("proguard-rules.pro").takeIf { it.exists() }?.readLines()?.any { it.trim() == "-dontobfuscate" } == true),
        )
        for (c in cases) assertTrue(c.name, c.ok)
    }

    @Test
    fun `release apks do not depend on how the source was checked out`() {
        // AGP embeds the git revision (or an error if it finds no .git dir) unless told not to,
        // so a worktree or tarball build would differ from F-Droid's clone.
        val gradle = File("build.gradle.kts").readText()
        assertTrue(hasSetting(gradle, "release", "vcsInfo.include = false"))
    }

    @Test
    fun `version is a plain literal that F-Droid can read`() {
        // fdroidserver's update checker reads versionCode/versionName from build.gradle.kts with
        // regexes: an expression like getenv(...) ?: "x" makes it find no version at all.
        val gradle = File("build.gradle.kts").readText()
        data class Case(val name: String, val ok: Boolean)
        val cases = listOf(
            Case("versionName is a quoted literal", Regex("""(?m)^\s*versionName\s*=\s*"[0-9.]+"\s*$""").containsMatchIn(gradle)),
            Case("versionCode is an integer literal", Regex("""(?m)^\s*versionCode\s*=\s*\d+\s*$""").containsMatchIn(gradle)),
            Case("no VERSION_NAME override (CI and F-Droid must build the same version)", !gradle.contains("VERSION_NAME")),
        )
        for (c in cases) assertTrue(c.name, c.ok)
    }

    private val listing = File("../fastlane/metadata/android/en-US")

    @Test
    fun `fastlane text follows the F-Droid limits`() {
        val short = File(listing, "short_description.txt").readText().trim()
        data class Case(val name: String, val ok: Boolean)
        val cases = mutableListOf(
            Case("short description at most 80 characters (${short.length})", short.length <= 80),
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
            val bitDepth = input.readUnsignedByte()
            val colorType = input.readUnsignedByte()
            assertTrue("512x512 (was ${width}x$height)", width == 512 && height == 512)
            // Play wants a "32-bit PNG (with alpha)": 8 bits per channel, RGBA (color type 6).
            assertTrue("8 bits per channel (was $bitDepth)", bitDepth == 8)
            assertTrue("RGBA color type 6 (was $colorType)", colorType == 6)
        }
    }
}
