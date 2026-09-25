package com.gymlog.app.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

class BackupCodecTest {

    private val exportedAt = Instant.parse("2026-09-25T10:00:00Z")
    private val empty = BackupData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

    @Test
    fun `backups round trip through JSON`() {
        data class Case(val name: String, val data: BackupData)
        val cases = listOf(
            Case("every field type and unicode names", sampleBackupData()),
            Case("empty backup round trips", empty),
        )
        for (c in cases) {
            val json = BackupCodec.encode(c.data, exportedAt, "1.5")
            assertEquals(c.name, c.data, BackupCodec.decode(json))
        }
    }

    @Test
    fun `encoded file is readable pretty JSON with a format version`() {
        val json = BackupCodec.encode(sampleBackupData(), exportedAt, "1.5")
        assertTrue(json.contains("\n"))
        assertTrue(json.contains("\"formatVersion\": 1"))
        assertTrue(json.contains("\"appVersion\": \"1.5\""))
        assertTrue(json.contains("Farmer's walk"))
    }

    @Test
    fun `unknown keys are ignored`() {
        val json = BackupCodec.encode(empty, exportedAt, "1.5")
            .replaceFirst("{", "{\n  \"futureField\": {\"x\": 1},")
        assertEquals(empty, BackupCodec.decode(json))
    }

    @Test
    fun `golden v1 file still decodes`() {
        val text = javaClass.getResource("/backup-v1.json")!!.readText()
        val data = BackupCodec.decode(text)
        assertEquals(1, data.exercises.size)
        assertEquals("bench press", data.exercises[0].name)
        assertEquals(1, data.sessions.size)
        assertEquals(null, data.sessions[0].completedAt)
        assertEquals(2, data.sets.size)
    }

    // Encoding does not validate, so this produces a well-formed file with bad contents.
    private fun broken(edit: (BackupData) -> BackupData) = BackupCodec.encode(edit(sampleBackupData()), exportedAt, "1.5")

    @Test
    fun `bad files are rejected with a typed error`() {
        val good = BackupCodec.encode(sampleBackupData(), exportedAt, "1.5")
        data class Case(val name: String, val text: String, val check: (BackupException) -> Boolean)
        val cases = listOf(
            Case("not JSON", "hello, this is a text file", { it is BackupException.NotABackup }),
            Case("JSON array", "[1, 2, 3]", { it is BackupException.NotABackup }),
            Case("JSON object without formatVersion", "{\"name\": \"x\"}", { it is BackupException.NotABackup }),
            Case("formatVersion zero", good.replace("\"formatVersion\": 1", "\"formatVersion\": 0"), { it is BackupException.NotABackup }),
            Case(
                "newer formatVersion", good.replace("\"formatVersion\": 1", "\"formatVersion\": 99"),
                { it is BackupException.NewerVersion && it.version == 99 },
            ),
            Case("missing required field", "{\"formatVersion\": 1}", { it is BackupException.Corrupt }),
            Case("unknown enum value", good.replace("\"status\": \"PARTIAL\"", "\"status\": \"WARMUP\""), { it is BackupException.Corrupt }),
            Case("bad date", good.replace("\"2026-09-23\"", "\"23/09/2026\""), { it is BackupException.Corrupt }),
            Case("set -> missing session", broken { it.copy(sets = it.sets.map { s -> if (s.id == 40L) s.copy(sessionId = 999) else s }) }, { it is BackupException.Corrupt }),
            Case("set -> missing exercise", broken { it.copy(sets = it.sets.map { s -> if (s.id == 43L) s.copy(exerciseId = 999) else s }) }, { it is BackupException.Corrupt }),
            Case("workoutExercise -> missing workout", broken { it.copy(workoutExercises = it.workoutExercises.map { w -> w.copy(workoutId = 999) }) }, { it is BackupException.Corrupt }),
            Case("workoutExercise -> missing exercise", broken { it.copy(workoutExercises = it.workoutExercises.map { w -> w.copy(exerciseId = 999) }) }, { it is BackupException.Corrupt }),
            Case("session -> missing workout", broken { it.copy(sessions = it.sessions.map { x -> if (x.id == 30L) x.copy(workoutId = 999) else x }) }, { it is BackupException.Corrupt }),
            Case("duplicate set id", broken { it.copy(sets = it.sets.map { s -> if (s.id == 41L) s.copy(id = 40) else s }) }, { it is BackupException.Corrupt }),
        )
        for (c in cases) {
            assertTrue("fixture for '${c.name}' must differ from the good file", c.text != good)
            try {
                BackupCodec.decode(c.text)
                fail("${c.name}: expected a BackupException")
            } catch (e: BackupException) {
                assertTrue("${c.name}: wrong error ${e::class.simpleName}", c.check(e))
            }
        }
    }

    @Test
    fun `a session with no workout is valid`() {
        val data = sampleBackupData() // session 31 has workoutId = null
        assertEquals(data, BackupCodec.decode(BackupCodec.encode(data, exportedAt, "1.5")))
    }
}
