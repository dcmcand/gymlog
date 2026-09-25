package com.gymlog.app.ui.settings

import com.gymlog.app.data.backup.BackupData
import com.gymlog.app.data.backup.BackupException
import com.gymlog.app.data.backup.ImportPreview
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

class BackupMessagesTest {

    @Test
    fun `backup file name carries the date`() {
        assertEquals("gymlog-backup-2026-09-25.json", backupFileName(LocalDate.of(2026, 9, 25)))
    }

    @Test
    fun `workout counts pluralize`() {
        data class Case(val n: Int, val expected: String)
        val cases = listOf(Case(0, "0 workouts"), Case(1, "1 workout"), Case(145, "145 workouts"))
        for (c in cases) assertEquals(c.expected, workoutCount(c.n))
    }

    @Test
    fun `each failure has its own plain message`() {
        data class Case(val e: Throwable, val expected: String)
        val cases = listOf(
            Case(BackupException.NotABackup(), "That file isn't a GymLog backup."),
            Case(BackupException.NewerVersion(2), "This backup was made by a newer GymLog. Update GymLog, then try again."),
            Case(BackupException.Corrupt("x"), "This backup is damaged and can't be imported."),
            Case(BackupException.WorkoutInProgress(), "Finish or discard your current workout first."),
            Case(IOException("disk"), "Couldn't read or write the file."),
        )
        for (c in cases) assertEquals(c.expected, backupErrorMessage(c.e))
    }

    @Test
    fun `confirm text names both counts`() {
        val empty = BackupData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals(
            "This device's 145 workouts will be replaced by the file's 1 workout. This can't be undone.",
            importConfirmText(ImportPreview(empty, fileWorkouts = 1, deviceWorkouts = 145)),
        )
    }
}
