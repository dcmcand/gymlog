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

    @Test
    fun `release workflow uploads a signed aab as an artifact only`() {
        val workflow = File("../.github/workflows/release.yml").readText()
        // The GitHub release step runs from the gh-release action to the next job.
        val releaseStep = workflow.substringAfter("softprops/action-gh-release").substringBefore("publish-pebble:")
        data class Case(val name: String, val ok: Boolean)
        val cases = listOf(
            Case("bundle is built", workflow.contains("run: ./gradlew bundleRelease")),
            Case("bundle is built after the APK guard", workflow.indexOf("bundleRelease") > workflow.indexOf("apk_check.py signing-block")),
            Case("bundle goes to a workflow artifact", workflow.contains("uses: actions/upload-artifact@v7")),
            Case("artifact name", workflow.contains("name: app-release-aab")),
            Case("artifact path", workflow.contains("path: app/build/outputs/bundle/release/app-release.aab")),
            Case("a missing bundle fails the release", workflow.contains("if-no-files-found: error")),
            Case("release step found", releaseStep.length < workflow.length),
            Case("github release still ships the apk", releaseStep.contains("files: app/build/outputs/apk/release/app-release.apk")),
            Case("github release ships no aab", !releaseStep.contains(".aab")),
        )
        for (c in cases) assertTrue(c.name, c.ok)
    }

    @Test
    fun `rest timer declares its special use for play review`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        // The service element with a body (a self-closing tag has no room for the property).
        val service = Regex("""(?s)<service\b[^>]*android:name="\.service\.RestTimerService"[^>]*[^/]>.*?</service>""")
            .find(manifest)?.value.orEmpty()
        data class Case(val name: String, val ok: Boolean)
        val cases = listOf(
            Case("RestTimerService has a body", service.isNotEmpty()),
            Case("still specialUse (shortService is capped at about 3 minutes)", service.contains("""android:foregroundServiceType="specialUse"""")),
            Case("subtype property declared", service.contains("""android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"""")),
            Case("subtype explains the rest timer to the reviewer", Regex("""android:value="Rest timer between gym sets:[^"]{60,}"""").containsMatchIn(service)),
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

    private data class PngInfo(val width: Int, val height: Int, val bitDepth: Int, val colorType: Int)

    /** Reads the IHDR chunk. Color type 2 = RGB (no alpha), 6 = RGBA. */
    private fun pngInfo(file: File): PngInfo = DataInputStream(file.inputStream()).use { input ->
        val header = ByteArray(16).also { input.readFully(it) }
        assertTrue("${file.name}: PNG signature", header.copyOfRange(1, 4).decodeToString() == "PNG")
        PngInfo(input.readInt(), input.readInt(), input.readUnsignedByte(), input.readUnsignedByte())
    }

    @Test
    fun `store icon is a 512 px square png`() {
        val icon = File(listing, "images/icon.png")
        assertTrue("icon.png exists", icon.exists())
        val info = pngInfo(icon)
        data class Case(val name: String, val ok: Boolean)
        val cases = listOf(
            Case("512x512 (was ${info.width}x${info.height})", info.width == 512 && info.height == 512),
            // Play wants a "32-bit PNG (with alpha)": 8 bits per channel, RGBA (color type 6).
            Case("8 bits per channel (was ${info.bitDepth})", info.bitDepth == 8),
            Case("RGBA color type 6 (was ${info.colorType})", info.colorType == 6),
            Case("at most 1024 KB (was ${icon.length()} bytes)", icon.length() <= 1024 * 1024),
        )
        for (c in cases) assertTrue(c.name, c.ok)
    }

    @Test
    fun `feature graphic and screenshots follow the play rules`() {
        data class Case(val name: String, val ok: Boolean)
        val cases = mutableListOf<Case>()
        val feature = File(listing, "images/featureGraphic.png")
        cases += Case("featureGraphic.png exists", feature.exists())
        if (feature.exists()) {
            val f = pngInfo(feature)
            cases += Case("feature graphic 1024x500 (was ${f.width}x${f.height})", f.width == 1024 && f.height == 500)
            cases += Case("feature graphic 24-bit RGB, no alpha (bit depth ${f.bitDepth}, color type ${f.colorType})", f.bitDepth == 8 && f.colorType == 2)
        }
        val shots = File(listing, "images/phoneScreenshots").listFiles { f -> f.extension == "png" }.orEmpty()
        cases += Case("2 to 8 phone screenshots (was ${shots.size})", shots.size in 2..8)
        for (shot in shots.sortedBy { it.name }) {
            val s = pngInfo(shot)
            val (short, long) = minOf(s.width, s.height) to maxOf(s.width, s.height)
            cases += Case("${shot.name}: sides within 320-3840 px (${s.width}x${s.height})", short >= 320 && long <= 3840)
            cases += Case("${shot.name}: long side at most twice the short (${s.width}x${s.height})", long <= 2 * short)
            cases += Case("${shot.name}: 24-bit RGB, no alpha (bit depth ${s.bitDepth}, color type ${s.colorType})", s.bitDepth == 8 && s.colorType == 2)
        }
        val full = File(listing, "full_description.txt").readText().trim()
        cases += Case("full description at most 4000 characters (${full.length})", full.length <= 4000)
        for (c in cases) assertTrue(c.name, c.ok)
    }
}
