package com.gymlog.app.ui.settings

import com.gymlog.app.data.backup.BackupException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream

class BackupTasksTest {

    @Test
    fun `reading a picked file is capped so a huge file can't exhaust memory`() {
        data class Case(val name: String, val size: Int, val limit: Long, val accepted: Boolean)
        val cases = listOf(
            Case("small file", 10, 100, accepted = true),
            Case("exactly at the limit", 100, 100, accepted = true),
            Case("one byte over the limit", 101, 100, accepted = false),
            Case("far over the limit", 10_000, 100, accepted = false),
        )
        for (c in cases) {
            val input = ByteArrayInputStream(ByteArray(c.size) { 'a'.code.toByte() })
            try {
                val text = readBackupText(input, c.limit)
                assertTrue("${c.name}: should have been rejected", c.accepted)
                assertEquals(c.name, c.size, text.length)
            } catch (_: BackupException.NotABackup) {
                assertTrue("${c.name}: should have been accepted", !c.accepted)
            }
        }
    }

    @Test
    fun `reading keeps UTF-8 text intact`() {
        val text = "Farmer's walk \"Zercher\" Élévation 🏋️"
        assertEquals(text, readBackupText(ByteArrayInputStream(text.toByteArray()), 1000))
    }

    @Test
    fun `backup work finishes even if the screen that started it goes away`() = runBlocking {
        var finished = false
        val job = launch {
            runToCompletion {
                delay(200)
                finished = true
            }
        }
        delay(50)
        job.cancel() // e.g. the user rotates the phone or leaves Settings mid-export
        job.join()
        assertTrue("work must complete despite cancellation", finished)
    }

    @Test
    fun `a failure inside backup work still reaches the caller`() = runBlocking {
        try {
            runToCompletion { throw BackupException.Corrupt("x") }
            fail("expected Corrupt")
        } catch (_: BackupException.Corrupt) {
        }
    }
}
