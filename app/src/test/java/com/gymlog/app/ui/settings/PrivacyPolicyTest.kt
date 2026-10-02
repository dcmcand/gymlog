package com.gymlog.app.ui.settings

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Play requires a privacy policy URL; Settings links to the same file. Runs from `app/`. */
class PrivacyPolicyTest {

    private val repoBlobPrefix = "https://github.com/dcmcand/gymlog/blob/main/"

    @Test
    fun `settings link points at the policy file in the repo`() {
        val path = PRIVACY_POLICY_URL.removePrefix(repoBlobPrefix)
        data class Case(val name: String, val ok: Boolean)
        val cases = listOf(
            Case("URL is on the main branch of the repo", PRIVACY_POLICY_URL.startsWith(repoBlobPrefix)),
            Case("URL names a file that exists ($path)", File("../$path").isFile),
            Case("URL is the root PRIVACY.md", path == "PRIVACY.md"),
        )
        for (c in cases) assertTrue(c.name, c.ok)
    }

    @Test
    fun `policy states the claims the store forms rely on`() {
        val policy = File("../PRIVACY.md").readText()
        data class Case(val name: String, val ok: Boolean)
        val cases = listOf(
            Case("no internet permission", policy.contains("no internet permission", ignoreCase = true)),
            Case("nothing collected, shared or sold", policy.contains("does not collect, share or sell", ignoreCase = true)),
            Case("data stays on the device", policy.contains("stays on your device", ignoreCase = true)),
            Case("no analytics or ads", policy.contains("no analytics", ignoreCase = true) && policy.contains("no ads", ignoreCase = true)),
            Case("pebble integration covered", policy.contains("Pebble")),
            Case("contact given", policy.contains("https://github.com/dcmcand/gymlog/issues")),
            Case("effective date given", Regex("""Effective date: \d{4}-\d{2}-\d{2}""").containsMatchIn(policy)),
            Case("no em dashes", !policy.contains('—')),
        )
        for (c in cases) assertTrue(c.name, c.ok)
    }
}
