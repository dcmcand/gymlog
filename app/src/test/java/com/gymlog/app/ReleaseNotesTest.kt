package com.gymlog.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Users install from GitHub Releases, so the per-version fastlane changelog (the only place
 * upgrade steps like the 2.0 app-ID move are written) must be what the release shows. Unit
 * tests run with the module dir (`app/`) as the working directory.
 */
class ReleaseNotesTest {

    @Test
    fun `github releases show the fastlane changelog and the readme explains the 2_0 move`() {
        val workflow = File("../.github/workflows/release.yml").readText()
        val readme = File("../README.md").readText()
        data class Case(val name: String, val ok: Boolean)
        val cases = listOf(
            Case("release job derives the versionCode from the tag", workflow.contains("VERSION_CODE=")),
            Case(
                "release body comes from the versionCode's changelog",
                workflow.contains("body_path: fastlane/metadata/android/en-US/changelogs/\${{ steps.version.outputs.VERSION_CODE }}.txt"),
            ),
            Case("the 2.0 changelog exists", File("../fastlane/metadata/android/en-US/changelogs/20000.txt").exists()),
            Case("readme has upgrade steps for 1.x users", readme.contains("Upgrading from 1.x")),
        )
        for (c in cases) assertTrue(c.name, c.ok)
    }
}
