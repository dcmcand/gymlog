package com.gymlog.app.data.backup

import com.gymlog.app.data.SessionStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

private class FakeStore(var data: BackupData, var inProgress: Boolean = false) : BackupStore {
    var replaceCalls = 0
    override suspend fun readAll() = data
    override suspend fun replaceAll(data: BackupData) {
        replaceCalls++
        this.data = data
    }
    override suspend fun hasWorkoutInProgress() = inProgress
}

class BackupServiceTest {

    private val now = Instant.parse("2026-09-25T10:00:00Z")
    private val empty = BackupData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

    private fun service(store: FakeStore, onReplaced: () -> Unit = {}) = BackupService(store, onReplaced) { now }

    @Test
    fun `export encodes the whole store and counts workouts`() = runBlocking {
        val store = FakeStore(sampleBackupData())
        val export = service(store).export("1.5")
        assertEquals(2, export.workoutCount)
        assertEquals(sampleBackupData(), BackupCodec.decode(export.json))
        assertTrue(export.json.contains("\"exportedAt\": \"2026-09-25T10:00:00Z\""))
    }

    @Test
    fun `preview reports both counts without touching data`() = runBlocking {
        val store = FakeStore(empty)
        val file = BackupCodec.encode(sampleBackupData(), now, "1.5")
        val preview = service(store).preview(file)
        assertEquals(2, preview.fileWorkouts)
        assertEquals(0, preview.deviceWorkouts)
        assertEquals(0, store.replaceCalls)
    }

    @Test
    fun `import replaces everything and notifies`() = runBlocking {
        var notified = 0
        val store = FakeStore(empty)
        val svc = service(store) { notified++ }
        svc.import(svc.preview(BackupCodec.encode(sampleBackupData(), now, "1.5")))
        assertEquals(sampleBackupData(), store.data)
        assertEquals(1, notified)
        // The in-progress session in the file comes back as-is, so it can be resumed.
        assertEquals(SessionStatus.IN_PROGRESS, store.data.sessions.first { it.id == 31L }.status)
    }

    @Test
    fun `import is refused while a workout is in progress`() = runBlocking {
        data class Case(val name: String, val inProgressAtPreview: Boolean, val inProgressAtConfirm: Boolean)
        val cases = listOf(
            Case("in progress when the file is picked", inProgressAtPreview = true, inProgressAtConfirm = true),
            Case("import re-checks in-progress at confirm", inProgressAtPreview = false, inProgressAtConfirm = true),
        )
        val file = BackupCodec.encode(sampleBackupData(), now, "1.5")
        for (c in cases) {
            var notified = 0
            val store = FakeStore(empty, inProgress = c.inProgressAtPreview)
            val svc = service(store) { notified++ }
            try {
                val preview = svc.preview(file)
                store.inProgress = c.inProgressAtConfirm // e.g. a workout started from the watch
                svc.import(preview)
                fail("${c.name}: expected WorkoutInProgress")
            } catch (_: BackupException.WorkoutInProgress) {
            }
            assertEquals(c.name, 0, store.replaceCalls)
            assertEquals(c.name, empty, store.data)
            assertEquals(c.name, 0, notified)
        }
    }

    @Test
    fun `a bad file never reaches the store`() = runBlocking {
        val store = FakeStore(sampleBackupData())
        try {
            service(store).preview("not a backup")
            fail("expected NotABackup")
        } catch (_: BackupException.NotABackup) {
        }
        assertEquals(0, store.replaceCalls)
    }
}
