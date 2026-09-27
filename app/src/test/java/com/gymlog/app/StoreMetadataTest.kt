package com.gymlog.app

import org.junit.Assert.assertTrue
import org.junit.Test
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
}
