# Data Export/Import and App ID Change Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users export all GymLog data to a JSON file and restore it (v1.5), then move the app to application ID `io.github.dcmcand.gymlog` (v2.0) without losing anyone's data.

**Architecture:** A plain-Kotlin `data.backup` package (DTOs + `BackupCodec` + `BackupService`) sits on a new Room `BackupDao` that implements a small `BackupStore` interface, so all logic is JVM-unit-testable. A new Settings screen (gear on the Calendar top bar) drives export/import through the Android document pickers. The ID change is a separate, later release so users can export from the old app first.

**Tech Stack:** Kotlin 2.2.10, Jetpack Compose Material 3, Room 2.8.4, kotlinx.serialization (plugin 2.2.10, `kotlinx-serialization-json` 1.9.0), JUnit 4.

**Spec:** `docs/superpowers/specs/2026-09-25-data-export-import-and-app-id-design.md`

## Journeys

| # | Item | Proof | Check method | Evidence |
|---|------|-------|--------------|----------|
| 1 | Open Settings from a gear on the Calendar tab; it shows the app version | Tapping the gear on Calendar opens Settings showing "GymLog 1.5" (v1.5 build) | narrated: on the phone, tap gear; capture `adb exec-out screencap` screenshot of Settings | |
| 2 | Export all data to a JSON file I choose, readable in a text editor | Export writes `gymlog-backup-<date>.json` to the chosen location (Downloads in the check); the pulled file is pretty-printed JSON with `formatVersion: 1` and table counts equal to the device DB's row counts | narrated: export on phone; `adb pull` the file; `jq` key/counts vs `sqlite3` `SELECT COUNT(*)` per table on the pulled DB | |
| 3 | Importing into a fresh install restores everything: exercises, workouts, sessions, sets, settings, history and progress charts | After import into a fresh install, every table's rows are identical to the source DB; history and a progress chart show the same data | automated: `BackupCodecTest` round trip + golden file; narrated: `sqlite3` dump of all 5 tables from source and destination DBs, `diff` is empty; screenshot of one progress chart on both installs | |
| 4 | Import asks before replacing; cancelling changes nothing | Picking a valid file shows the confirmation with both workout counts; Cancel leaves every table dump identical | narrated: screenshot of dialog; `sqlite3` table dump before and after Cancel, `diff` empty | |
| 5 | A bad file shows a clear error and changes nothing | Importing (a) a non-JSON file, (b) a backup with `formatVersion: 99`, (c) a backup with a set pointing at a missing session each shows its specific message; DB unchanged | automated: `BackupCodecTest` rejection table; narrated: push the three files, import each, screenshot each snackbar, `sqlite3` dump unchanged | |
| 6 | Cannot import during an in-progress workout, and I am told why | With a workout in progress, Import shows "Finish or discard your current workout first"; DB unchanged | automated: `BackupServiceTest` in-progress case; narrated: screenshot of the message | |
| 7 | A workout in progress at export time can be resumed after import | Export with an in-progress session, import into a fresh install, Calendar offers Resume and the active workout shows the same completed/pending sets | narrated: screenshots before export and after import/resume | |
| 8 | Renamed app installs alongside the old one and data moves across via export/import | `adb shell pm list packages` shows both `com.gymlog.app` and `io.github.dcmcand.gymlog`; after importing the 1.5 export into 2.0, all 5 tables diff clean between the two apps' DBs | narrated: `pm list packages` output; `sqlite3` dumps from both apps and empty `diff` | |
| 9 | Watchapp works with the renamed app (open mid-workout, Easy/Hard, skip, extend) | With only `io.github.dcmcand.gymlog` handling the watch, logcat shows context pushed on watchapp open, `10=1`/`10=2` completions recorded in the new app's DB, `10=4` cycling, `10=3` extending rest | narrated: decoded logcat transcript (same decoder used for 1.4) + `sqlite3` row showing the watch-completed set | |
| 10 | Rest timer notification opens the active workout in the renamed app | Completing a set starts the timer notification; tapping it opens the renamed app's active workout screen | narrated: screenshot of notification and of the screen it opens; `dumpsys activity` top activity shows `io.github.dcmcand.gymlog` | |
| 11 | No new permissions; still no internet | Merged manifest permissions for v1.5 and v2.0 equal 1.4's; no `INTERNET` | automated: existing `ManifestPermissionsTest`; narrated: `aapt2 dump permissions` on the 1.4, 1.5 and 2.0 APKs, compared | |

## Global Constraints

- Kotlin package / `namespace` stays `com.gymlog.app`; only `applicationId` changes (Task 6).
- New application ID: `io.github.dcmcand.gymlog` (exact string).
- kotlinx.serialization Gradle plugin version = the existing `kotlin` catalog version (2.2.10); runtime `org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0` (1.10+ require Kotlin 2.3).
- No new Android permissions; never add `INTERNET`. File access only through the system document pickers.
- `formatVersion` = 1. Enums by name, `LocalDate` and `Instant` as ISO-8601 strings, IDs preserved.
- Import always replaces all data; it is refused while a workout is in progress.
- No ViewModels or DI; screens get DAOs via `GymLogDatabase.getDatabase(context)`.
- Tests: JUnit 4, table-driven (`data class Case` + loop), backtick names. Run with `./gradlew testDebugUnitTest` (plain `./gradlew test --tests` fails on this project).
- User-visible copy and code comments: never use em dashes.
- `CLAUDE.md` is gitignored and must never be committed.
- Release: bump `versionCode` (major*10000 + minor*100 + patch) and the `versionName` default in `app/build.gradle.kts`, add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (500 characters max). Pushing tag `v<version>` publishes the GitHub release and the watchapp; always ask the user before pushing a tag.

## Review Focus

1. A workout is started (e.g. from the watch) between the import confirmation dialog appearing and the user tapping Replace: import must still refuse. Pinned by `BackupServiceTest` "import re-checks in-progress at confirm" (Task 3).
2. Exporting a brand-new install with no data, and importing that empty file: must produce/accept a valid file and the dialog shows 0 workouts. Pinned by `BackupCodecTest` "empty backup round trips" (Task 2).
3. Names with apostrophes, quotes, accents and emoji ("Farmer's walk", `"Zercher"`, "Élévation", "🏋️") must survive a round trip unchanged. Pinned by the round-trip fixture in `BackupCodecTest` (Task 2).
4. A hand-edited file with duplicate IDs in a table must fail as a damaged backup, not crash on a database constraint mid-transaction. Pinned by `BackupCodecTest` rejection case "duplicate set id" (Task 2).
5. An unknown enum value (e.g. `"status": "WARMUP"` from a newer app or hand edit) must be reported as a damaged backup, not crash. Pinned by `BackupCodecTest` rejection case "unknown enum value" (Task 2).

---

## File Structure

| File | Responsibility |
|------|----------------|
| `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts` | kotlinx.serialization plugin + runtime (Task 1); app ID and versions (Tasks 5, 6) |
| `app/src/main/java/com/gymlog/app/data/backup/BackupModels.kt` | `BackupData` (entity lists), `@Serializable` DTOs, entity <-> DTO mapping |
| `app/src/main/java/com/gymlog/app/data/backup/BackupException.kt` | Typed failures shared by codec, service and UI |
| `app/src/main/java/com/gymlog/app/data/backup/BackupCodec.kt` | JSON encode/decode + validation |
| `app/src/main/java/com/gymlog/app/data/backup/BackupStore.kt` | Interface the service depends on |
| `app/src/main/java/com/gymlog/app/data/backup/BackupDao.kt` | Room implementation of `BackupStore` |
| `app/src/main/java/com/gymlog/app/data/backup/BackupService.kt` | Export, import preview, import (in-progress guard) |
| `app/src/main/java/com/gymlog/app/data/GymLogDatabase.kt` | Exposes `backupDao()` |
| `app/src/main/java/com/gymlog/app/ui/settings/BackupMessages.kt` | Pure UI strings: file name, error text, dialog text, pluralization |
| `app/src/main/java/com/gymlog/app/ui/settings/SettingsScreen.kt` | Settings UI, document pickers, dialog, snackbars |
| `app/src/main/java/com/gymlog/app/ui/navigation/Screen.kt`, `GymLogNavigation.kt`, `ui/calendar/CalendarScreen.kt` | Settings route and gear icon |
| `pebble/package.json` | Companion packages (Task 6) |
| Tests under `app/src/test/java/com/gymlog/app/data/backup/` and `.../ui/settings/`, golden file `app/src/test/resources/backup-v1.json` | |

---

### Task 1: Serialization dependency and backup models

**Advances journeys:** 3

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/gymlog/app/data/backup/BackupModels.kt`
- Test: `app/src/test/java/com/gymlog/app/data/backup/BackupModelsTest.kt`

**Interfaces:**
- Produces: `data class BackupData(exercises: List<Exercise>, workouts: List<Workout>, workoutExercises: List<WorkoutExercise>, sessions: List<WorkoutSession>, sets: List<ExerciseSet>)`; `@Serializable data class BackupFile(formatVersion: Int, exportedAt: String, appVersion: String, exercises: List<ExerciseDto>, workouts: List<WorkoutDto>, workoutExercises: List<WorkoutExerciseDto>, sessions: List<SessionDto>, sets: List<SetDto>)`; `fun BackupData.toFile(exportedAt: Instant, appVersion: String): BackupFile`; `fun BackupFile.toData(): BackupData` (throws `java.time.DateTimeException` on bad dates); `const val BACKUP_FORMAT_VERSION = 1`.

- [ ] **Step 1: Add the dependency**

`gradle/libs.versions.toml`, add under `[versions]`:
```toml
kotlinxSerialization = "1.9.0"
```
under `[libraries]`:
```toml
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
```
under `[plugins]`:
```toml
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```
`build.gradle.kts` (root) plugins block, add:
```kotlin
    alias(libs.plugins.kotlin.serialization) apply false
```
`app/build.gradle.kts` plugins block, add:
```kotlin
    alias(libs.plugins.kotlin.serialization)
```
and in `dependencies`:
```kotlin
    implementation(libs.kotlinx.serialization.json)
```

- [ ] **Step 2: Write the failing test**

`app/src/test/java/com/gymlog/app/data/backup/BackupModelsTest.kt`:
```kotlin
package com.gymlog.app.data.backup

import com.gymlog.app.data.CardioFixedDimension
import com.gymlog.app.data.Exercise
import com.gymlog.app.data.ExerciseSet
import com.gymlog.app.data.ExerciseType
import com.gymlog.app.data.SessionStatus
import com.gymlog.app.data.SetStatus
import com.gymlog.app.data.Workout
import com.gymlog.app.data.WorkoutExercise
import com.gymlog.app.data.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Every field type populated, including nulls and non-ASCII names. Shared with BackupCodecTest. */
fun sampleBackupData() = BackupData(
    exercises = listOf(
        Exercise(id = 1, name = "Farmer's walk", type = ExerciseType.WEIGHT, weightIncrementKg = 5.0),
        Exercise(
            id = 2, name = "Rower \"Zercher\" Élévation 🏋️", type = ExerciseType.CARDIO,
            cardioFixedDimension = CardioFixedDimension.DISTANCE, fixedValue = 2000, level = 7,
            distanceDisplayKm = true,
        ),
        Exercise(id = 3, name = "Plank", type = ExerciseType.CARDIO, cardioFixedDimension = CardioFixedDimension.TIME, fixedValue = 60),
    ),
    workouts = listOf(Workout(id = 10, name = "Push")),
    workoutExercises = listOf(
        WorkoutExercise(id = 20, workoutId = 10, exerciseId = 1, targetSets = 5, targetReps = 8, targetWeightKg = 42.5, sortOrder = 0),
        WorkoutExercise(id = 21, workoutId = 10, exerciseId = 2, targetSets = 1, targetDistanceM = 2000, targetDurationSec = 480, sortOrder = 1),
    ),
    sessions = listOf(
        WorkoutSession(
            id = 30, workoutId = 10, date = LocalDate.of(2026, 9, 23), status = SessionStatus.COMPLETED,
            startedAt = Instant.parse("2026-09-23T17:00:00.123Z"), completedAt = Instant.parse("2026-09-23T18:01:02.456Z"),
        ),
        WorkoutSession(
            id = 31, workoutId = null, date = LocalDate.of(2026, 9, 24), status = SessionStatus.IN_PROGRESS,
            startedAt = Instant.parse("2026-09-24T07:00:00Z"),
        ),
    ),
    sets = listOf(
        ExerciseSet(id = 40, sessionId = 30, exerciseId = 1, setNumber = 1, weightKg = 42.5, repsCompleted = 8, status = SetStatus.EASY),
        ExerciseSet(id = 41, sessionId = 30, exerciseId = 2, setNumber = 1, distanceM = 2000, durationSec = 470, status = SetStatus.HARD),
        ExerciseSet(id = 42, sessionId = 31, exerciseId = 1, setNumber = 1, weightKg = null, repsCompleted = null, status = SetStatus.PENDING),
        ExerciseSet(id = 43, sessionId = 31, exerciseId = 3, setNumber = 1, durationSec = 55, status = SetStatus.PARTIAL),
        ExerciseSet(id = 44, sessionId = 31, exerciseId = 1, setNumber = 2, weightKg = 40.0, repsCompleted = 3, status = SetStatus.FAILED),
    ),
)

class BackupModelsTest {

    @Test
    fun `data survives mapping to the file model and back`() {
        data class Case(val name: String, val data: BackupData)
        val cases = listOf(
            Case("every field type", sampleBackupData()),
            Case("empty", BackupData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())),
        )
        for (c in cases) {
            val file = c.data.toFile(Instant.parse("2026-09-25T10:00:00Z"), "1.5")
            assertEquals(c.name, BACKUP_FORMAT_VERSION, file.formatVersion)
            assertEquals(c.name, "2026-09-25T10:00:00Z", file.exportedAt)
            assertEquals(c.name, "1.5", file.appVersion)
            assertEquals(c.name, c.data, file.toData())
        }
    }

    @Test
    fun `dates and instants are ISO-8601 strings`() {
        val file = sampleBackupData().toFile(Instant.EPOCH, "1.5")
        assertEquals("2026-09-23", file.sessions[0].date)
        assertEquals("2026-09-23T17:00:00.123Z", file.sessions[0].startedAt)
        assertEquals(null, file.sessions[1].completedAt)
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.gymlog.app.data.backup.BackupModelsTest"`
Expected: compilation FAIL, unresolved `BackupData` / `toFile`.

- [ ] **Step 4: Write the models**

`app/src/main/java/com/gymlog/app/data/backup/BackupModels.kt`:
```kotlin
package com.gymlog.app.data.backup

import com.gymlog.app.data.CardioFixedDimension
import com.gymlog.app.data.Exercise
import com.gymlog.app.data.ExerciseSet
import com.gymlog.app.data.ExerciseType
import com.gymlog.app.data.SessionStatus
import com.gymlog.app.data.SetStatus
import com.gymlog.app.data.Workout
import com.gymlog.app.data.WorkoutExercise
import com.gymlog.app.data.WorkoutSession
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

const val BACKUP_FORMAT_VERSION = 1

/** Everything GymLog stores, as Room entities. */
data class BackupData(
    val exercises: List<Exercise>,
    val workouts: List<Workout>,
    val workoutExercises: List<WorkoutExercise>,
    val sessions: List<WorkoutSession>,
    val sets: List<ExerciseSet>,
)

// The file format. Deliberately separate from the Room entities so a schema change can't
// silently change (and break) the format of existing backups.

@Serializable
data class BackupFile(
    val formatVersion: Int,
    val exportedAt: String,
    val appVersion: String,
    val exercises: List<ExerciseDto>,
    val workouts: List<WorkoutDto>,
    val workoutExercises: List<WorkoutExerciseDto>,
    val sessions: List<SessionDto>,
    val sets: List<SetDto>,
)

@Serializable
data class ExerciseDto(
    val id: Long,
    val name: String,
    val type: ExerciseType,
    val cardioFixedDimension: CardioFixedDimension? = null,
    val fixedValue: Int? = null,
    val level: Int? = null,
    val distanceDisplayKm: Boolean = false,
    val weightIncrementKg: Double = 2.5,
)

@Serializable
data class WorkoutDto(val id: Long, val name: String)

@Serializable
data class WorkoutExerciseDto(
    val id: Long,
    val workoutId: Long,
    val exerciseId: Long,
    val targetSets: Int,
    val targetReps: Int? = null,
    val targetWeightKg: Double? = null,
    val targetDistanceM: Int? = null,
    val targetDurationSec: Int? = null,
    val sortOrder: Int,
)

@Serializable
data class SessionDto(
    val id: Long,
    val workoutId: Long? = null,
    val date: String,
    val status: SessionStatus,
    val startedAt: String,
    val completedAt: String? = null,
)

@Serializable
data class SetDto(
    val id: Long,
    val sessionId: Long,
    val exerciseId: Long,
    val setNumber: Int,
    val weightKg: Double? = null,
    val repsCompleted: Int? = null,
    val distanceM: Int? = null,
    val durationSec: Int? = null,
    val status: SetStatus,
)

fun BackupData.toFile(exportedAt: Instant, appVersion: String) = BackupFile(
    formatVersion = BACKUP_FORMAT_VERSION,
    exportedAt = exportedAt.toString(),
    appVersion = appVersion,
    exercises = exercises.map {
        ExerciseDto(it.id, it.name, it.type, it.cardioFixedDimension, it.fixedValue, it.level, it.distanceDisplayKm, it.weightIncrementKg)
    },
    workouts = workouts.map { WorkoutDto(it.id, it.name) },
    workoutExercises = workoutExercises.map {
        WorkoutExerciseDto(
            it.id, it.workoutId, it.exerciseId, it.targetSets, it.targetReps, it.targetWeightKg,
            it.targetDistanceM, it.targetDurationSec, it.sortOrder,
        )
    },
    sessions = sessions.map {
        SessionDto(it.id, it.workoutId, it.date.toString(), it.status, it.startedAt.toString(), it.completedAt?.toString())
    },
    sets = sets.map {
        SetDto(it.id, it.sessionId, it.exerciseId, it.setNumber, it.weightKg, it.repsCompleted, it.distanceM, it.durationSec, it.status)
    },
)

/** Throws [java.time.DateTimeException] if a date or instant string is malformed. */
fun BackupFile.toData() = BackupData(
    exercises = exercises.map {
        Exercise(it.id, it.name, it.type, it.cardioFixedDimension, it.fixedValue, it.level, it.distanceDisplayKm, it.weightIncrementKg)
    },
    workouts = workouts.map { Workout(it.id, it.name) },
    workoutExercises = workoutExercises.map {
        WorkoutExercise(
            it.id, it.workoutId, it.exerciseId, it.targetSets, it.targetReps, it.targetWeightKg,
            it.targetDistanceM, it.targetDurationSec, it.sortOrder,
        )
    },
    sessions = sessions.map {
        WorkoutSession(
            it.id, it.workoutId, LocalDate.parse(it.date), it.status,
            Instant.parse(it.startedAt), it.completedAt?.let(Instant::parse),
        )
    },
    sets = sets.map {
        ExerciseSet(it.id, it.sessionId, it.exerciseId, it.setNumber, it.weightKg, it.repsCompleted, it.distanceM, it.durationSec, it.status)
    },
)
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.gymlog.app.data.backup.BackupModelsTest"`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts app/build.gradle.kts app/src/main/java/com/gymlog/app/data/backup/BackupModels.kt app/src/test/java/com/gymlog/app/data/backup/BackupModelsTest.kt
git commit -m "Add backup data models with kotlinx.serialization"
```

---

### Task 2: BackupCodec with validation and golden file

**Advances journeys:** 2, 3, 5

**Files:**
- Create: `app/src/main/java/com/gymlog/app/data/backup/BackupException.kt`
- Create: `app/src/main/java/com/gymlog/app/data/backup/BackupCodec.kt`
- Create: `app/src/test/resources/backup-v1.json`
- Test: `app/src/test/java/com/gymlog/app/data/backup/BackupCodecTest.kt`

**Interfaces:**
- Consumes: `BackupData`, `BackupFile`, `toFile`, `toData`, `BACKUP_FORMAT_VERSION`, `sampleBackupData()` (test helper) from Task 1.
- Produces: `sealed class BackupException : Exception` with `NotABackup`, `NewerVersion(version: Int)`, `Corrupt(detail: String)`, `WorkoutInProgress`; `object BackupCodec { fun encode(data: BackupData, exportedAt: Instant, appVersion: String): String; fun decode(text: String): BackupData }` (decode throws `BackupException`).

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/gymlog/app/data/backup/BackupCodecTest.kt`:
```kotlin
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
```

`app/src/test/resources/backup-v1.json` (golden file; never edit it to make a test pass, it pins format v1):
```json
{
  "formatVersion": 1,
  "exportedAt": "2026-09-25T10:00:00Z",
  "appVersion": "1.5",
  "exercises": [
    { "id": 1, "name": "bench press", "type": "WEIGHT", "cardioFixedDimension": null, "fixedValue": null,
      "level": null, "distanceDisplayKm": false, "weightIncrementKg": 2.5 }
  ],
  "workouts": [ { "id": 1, "name": "Push" } ],
  "workoutExercises": [
    { "id": 1, "workoutId": 1, "exerciseId": 1, "targetSets": 5, "targetReps": 8, "targetWeightKg": 40.0,
      "targetDistanceM": null, "targetDurationSec": null, "sortOrder": 0 }
  ],
  "sessions": [
    { "id": 1, "workoutId": 1, "date": "2026-09-24", "status": "IN_PROGRESS",
      "startedAt": "2026-09-24T17:00:00Z", "completedAt": null }
  ],
  "sets": [
    { "id": 1, "sessionId": 1, "exerciseId": 1, "setNumber": 1, "weightKg": 42.5, "repsCompleted": 8,
      "distanceM": null, "durationSec": null, "status": "EASY" },
    { "id": 2, "sessionId": 1, "exerciseId": 1, "setNumber": 2, "weightKg": 42.5, "repsCompleted": 8,
      "distanceM": null, "durationSec": null, "status": "PENDING" }
  ]
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.gymlog.app.data.backup.BackupCodecTest"`
Expected: compilation FAIL, unresolved `BackupCodec` / `BackupException`.

- [ ] **Step 3: Write the exception and codec**

`app/src/main/java/com/gymlog/app/data/backup/BackupException.kt`:
```kotlin
package com.gymlog.app.data.backup

/** Why an export or import could not go ahead. The data on the device is never modified. */
sealed class BackupException(message: String) : Exception(message) {
    class NotABackup : BackupException("not a GymLog backup")
    class NewerVersion(val version: Int) : BackupException("backup format $version is newer than supported")
    class Corrupt(detail: String) : BackupException("damaged backup: $detail")
    class WorkoutInProgress : BackupException("a workout is in progress")
}
```

`app/src/main/java/com/gymlog/app/data/backup/BackupCodec.kt`:
```kotlin
package com.gymlog.app.data.backup

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.time.DateTimeException
import java.time.Instant

object BackupCodec {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true // write every field so the file is self-describing
        ignoreUnknownKeys = true // additive fields from a newer app still import
    }

    fun encode(data: BackupData, exportedAt: Instant, appVersion: String): String =
        json.encodeToString(BackupFile.serializer(), data.toFile(exportedAt, appVersion))

    /** Parses and fully validates [text] before anything touches the database. */
    fun decode(text: String): BackupData {
        val root = try {
            json.parseToJsonElement(text)
        } catch (_: SerializationException) {
            throw BackupException.NotABackup()
        }
        val version = ((root as? JsonObject)?.get("formatVersion") as? JsonPrimitive)?.intOrNull
            ?: throw BackupException.NotABackup()
        if (version < 1) throw BackupException.NotABackup()
        if (version > BACKUP_FORMAT_VERSION) throw BackupException.NewerVersion(version)

        val data = try {
            json.decodeFromJsonElement(BackupFile.serializer(), root).toData()
        } catch (e: SerializationException) {
            throw BackupException.Corrupt(e.message ?: "unreadable")
        } catch (e: IllegalArgumentException) {
            throw BackupException.Corrupt(e.message ?: "unreadable")
        } catch (e: DateTimeException) {
            throw BackupException.Corrupt(e.message ?: "bad date")
        }
        validate(data)
        return data
    }

    private fun validate(d: BackupData) {
        fun unique(table: String, ids: List<Long>) {
            if (ids.size != ids.toSet().size) throw BackupException.Corrupt("duplicate id in $table")
        }
        unique("exercises", d.exercises.map { it.id })
        unique("workouts", d.workouts.map { it.id })
        unique("workoutExercises", d.workoutExercises.map { it.id })
        unique("sessions", d.sessions.map { it.id })
        unique("sets", d.sets.map { it.id })

        val exercises = d.exercises.map { it.id }.toSet()
        val workouts = d.workouts.map { it.id }.toSet()
        val sessions = d.sessions.map { it.id }.toSet()
        fun require(ok: Boolean, what: String) {
            if (!ok) throw BackupException.Corrupt(what)
        }
        d.workoutExercises.forEach {
            require(it.workoutId in workouts, "workout exercise ${it.id} -> missing workout ${it.workoutId}")
            require(it.exerciseId in exercises, "workout exercise ${it.id} -> missing exercise ${it.exerciseId}")
        }
        d.sessions.forEach {
            require(it.workoutId == null || it.workoutId in workouts, "session ${it.id} -> missing workout ${it.workoutId}")
        }
        d.sets.forEach {
            require(it.sessionId in sessions, "set ${it.id} -> missing session ${it.sessionId}")
            require(it.exerciseId in exercises, "set ${it.id} -> missing exercise ${it.exerciseId}")
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.gymlog.app.data.backup.*"`
Expected: PASS. If a text-replace rejection case fails its "fixture must differ" assertion, its match string does not occur in the pretty-printed output: print `good` once and correct the match string; never weaken the assertion.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/gymlog/app/data/backup/BackupException.kt app/src/main/java/com/gymlog/app/data/backup/BackupCodec.kt app/src/test/resources/backup-v1.json app/src/test/java/com/gymlog/app/data/backup/BackupCodecTest.kt
git commit -m "Add backup JSON codec with validation"
```

---

### Task 3: BackupStore, BackupDao and BackupService

**Advances journeys:** 3, 4, 6, 7

**Files:**
- Create: `app/src/main/java/com/gymlog/app/data/backup/BackupStore.kt`
- Create: `app/src/main/java/com/gymlog/app/data/backup/BackupDao.kt`
- Create: `app/src/main/java/com/gymlog/app/data/backup/BackupService.kt`
- Modify: `app/src/main/java/com/gymlog/app/data/GymLogDatabase.kt` (add `backupDao()`)
- Test: `app/src/test/java/com/gymlog/app/data/backup/BackupServiceTest.kt`

**Interfaces:**
- Consumes: `BackupData`, `BackupCodec`, `BackupException`, `sampleBackupData()`.
- Produces: `interface BackupStore { suspend fun readAll(): BackupData; suspend fun replaceAll(data: BackupData); suspend fun hasWorkoutInProgress(): Boolean }`; `GymLogDatabase.backupDao(): BackupDao`; `class BackupService(store: BackupStore, onDataReplaced: () -> Unit, now: () -> Instant = Instant::now)` with `suspend fun export(appVersion: String): BackupExport`, `suspend fun preview(text: String): ImportPreview`, `suspend fun import(preview: ImportPreview)`; `data class BackupExport(json: String, workoutCount: Int)`; `data class ImportPreview(data: BackupData, fileWorkouts: Int, deviceWorkouts: Int)`.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/gymlog/app/data/backup/BackupServiceTest.kt`:
```kotlin
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
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.gymlog.app.data.backup.BackupServiceTest"`
Expected: compilation FAIL, unresolved `BackupStore` / `BackupService`.

- [ ] **Step 3: Write the store interface, DAO and service**

`app/src/main/java/com/gymlog/app/data/backup/BackupStore.kt`:
```kotlin
package com.gymlog.app.data.backup

/** Whole-database access for backup and restore. Implemented by [BackupDao]. */
interface BackupStore {
    suspend fun readAll(): BackupData

    /** Atomically replaces all data; on any failure nothing changes. */
    suspend fun replaceAll(data: BackupData)

    suspend fun hasWorkoutInProgress(): Boolean
}
```

`app/src/main/java/com/gymlog/app/data/backup/BackupDao.kt`:
```kotlin
package com.gymlog.app.data.backup

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.gymlog.app.data.Exercise
import com.gymlog.app.data.ExerciseSet
import com.gymlog.app.data.Workout
import com.gymlog.app.data.WorkoutExercise
import com.gymlog.app.data.WorkoutSession

@Dao
abstract class BackupDao : BackupStore {
    @Query("SELECT * FROM exercises ORDER BY id") abstract suspend fun allExercises(): List<Exercise>
    @Query("SELECT * FROM workouts ORDER BY id") abstract suspend fun allWorkouts(): List<Workout>
    @Query("SELECT * FROM workout_exercises ORDER BY id") abstract suspend fun allWorkoutExercises(): List<WorkoutExercise>
    @Query("SELECT * FROM workout_sessions ORDER BY id") abstract suspend fun allSessions(): List<WorkoutSession>
    @Query("SELECT * FROM exercise_sets ORDER BY id") abstract suspend fun allSets(): List<ExerciseSet>

    @Query("DELETE FROM exercise_sets") abstract suspend fun deleteAllSets()
    @Query("DELETE FROM workout_sessions") abstract suspend fun deleteAllSessions()
    @Query("DELETE FROM workout_exercises") abstract suspend fun deleteAllWorkoutExercises()
    @Query("DELETE FROM workouts") abstract suspend fun deleteAllWorkouts()
    @Query("DELETE FROM exercises") abstract suspend fun deleteAllExercises()

    @Insert abstract suspend fun insertExercises(items: List<Exercise>)
    @Insert abstract suspend fun insertWorkouts(items: List<Workout>)
    @Insert abstract suspend fun insertWorkoutExercises(items: List<WorkoutExercise>)
    @Insert abstract suspend fun insertSessions(items: List<WorkoutSession>)
    @Insert abstract suspend fun insertSets(items: List<ExerciseSet>)

    @Query("SELECT EXISTS(SELECT 1 FROM workout_sessions WHERE status = 'IN_PROGRESS')")
    abstract override suspend fun hasWorkoutInProgress(): Boolean

    @Transaction
    override suspend fun readAll() = BackupData(
        allExercises(), allWorkouts(), allWorkoutExercises(), allSessions(), allSets(),
    )

    // Children deleted before parents and parents inserted before children, so foreign keys
    // hold at every step. Explicit ids advance SQLite's autoincrement past the imported maximum.
    @Transaction
    override suspend fun replaceAll(data: BackupData) {
        deleteAllSets()
        deleteAllSessions()
        deleteAllWorkoutExercises()
        deleteAllWorkouts()
        deleteAllExercises()
        insertExercises(data.exercises)
        insertWorkouts(data.workouts)
        insertWorkoutExercises(data.workoutExercises)
        insertSessions(data.sessions)
        insertSets(data.sets)
    }
}
```

`app/src/main/java/com/gymlog/app/data/GymLogDatabase.kt`: add the import `import com.gymlog.app.data.backup.BackupDao` and, after `abstract fun workoutSessionDao(): WorkoutSessionDao`:
```kotlin
    abstract fun backupDao(): BackupDao
```
(No schema change, so no version bump or migration.)

`app/src/main/java/com/gymlog/app/data/backup/BackupService.kt`:
```kotlin
package com.gymlog.app.data.backup

import java.time.Instant

data class BackupExport(val json: String, val workoutCount: Int)

/** A decoded, validated file waiting for the user to confirm the replace. */
data class ImportPreview(val data: BackupData, val fileWorkouts: Int, val deviceWorkouts: Int)

/**
 * Export and replace-all import. [onDataReplaced] runs after a successful import so callers can
 * drop in-memory state that referred to the old data (e.g. the active workout store).
 */
class BackupService(
    private val store: BackupStore,
    private val onDataReplaced: () -> Unit,
    private val now: () -> Instant = Instant::now,
) {
    suspend fun export(appVersion: String): BackupExport {
        val data = store.readAll()
        return BackupExport(BackupCodec.encode(data, now(), appVersion), data.sessions.size)
    }

    suspend fun preview(text: String): ImportPreview {
        if (store.hasWorkoutInProgress()) throw BackupException.WorkoutInProgress()
        val data = BackupCodec.decode(text)
        return ImportPreview(data, fileWorkouts = data.sessions.size, deviceWorkouts = store.readAll().sessions.size)
    }

    suspend fun import(preview: ImportPreview) {
        // Checked again: a workout may have started (e.g. from the watch) while the dialog was up.
        if (store.hasWorkoutInProgress()) throw BackupException.WorkoutInProgress()
        store.replaceAll(preview.data)
        onDataReplaced()
    }
}
```

- [ ] **Step 4: Run tests and build**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: all tests PASS, BUILD SUCCESSFUL (the build step proves Room accepts `BackupDao`'s queries and the `@Transaction` overrides).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/gymlog/app/data/backup/BackupStore.kt app/src/main/java/com/gymlog/app/data/backup/BackupDao.kt app/src/main/java/com/gymlog/app/data/backup/BackupService.kt app/src/main/java/com/gymlog/app/data/GymLogDatabase.kt app/src/test/java/com/gymlog/app/data/backup/BackupServiceTest.kt
git commit -m "Add backup service and Room backup DAO"
```

---

### Task 4: Settings screen with export and import

**Advances journeys:** 1, 2, 4, 5, 6, 11

**Files:**
- Create: `app/src/main/java/com/gymlog/app/ui/settings/BackupMessages.kt`
- Create: `app/src/main/java/com/gymlog/app/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/gymlog/app/ui/navigation/Screen.kt`
- Modify: `app/src/main/java/com/gymlog/app/ui/navigation/GymLogNavigation.kt` (Calendar composable ~line 93, add Settings composable)
- Modify: `app/src/main/java/com/gymlog/app/ui/calendar/CalendarScreen.kt:67-95`
- Test: `app/src/test/java/com/gymlog/app/ui/settings/BackupMessagesTest.kt`

**Interfaces:**
- Consumes: `BackupService`, `BackupExport`, `ImportPreview`, `BackupException`, `GymLogDatabase.backupDao()`, `ActiveWorkoutStore.clear()`.
- Produces: `Screen.Settings` (route `"settings"`); `SettingsScreen(onNavigateBack: () -> Unit)`; `CalendarScreen(..., onSettingsClick: () -> Unit = {})`; `fun backupFileName(date: LocalDate): String`, `fun workoutCount(n: Int): String`, `fun backupErrorMessage(e: Throwable): String`, `fun importConfirmText(p: ImportPreview): String`.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/gymlog/app/ui/settings/BackupMessagesTest.kt`:
```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.gymlog.app.ui.settings.BackupMessagesTest"`
Expected: compilation FAIL, unresolved `backupFileName`.

- [ ] **Step 3: Write the messages**

`app/src/main/java/com/gymlog/app/ui/settings/BackupMessages.kt`:
```kotlin
package com.gymlog.app.ui.settings

import com.gymlog.app.data.backup.BackupException
import com.gymlog.app.data.backup.ImportPreview
import java.time.LocalDate

fun backupFileName(date: LocalDate): String = "gymlog-backup-$date.json"

fun workoutCount(n: Int): String = if (n == 1) "1 workout" else "$n workouts"

fun backupErrorMessage(e: Throwable): String = when (e) {
    is BackupException.NotABackup -> "That file isn't a GymLog backup."
    is BackupException.NewerVersion -> "This backup was made by a newer GymLog. Update GymLog, then try again."
    is BackupException.Corrupt -> "This backup is damaged and can't be imported."
    is BackupException.WorkoutInProgress -> "Finish or discard your current workout first."
    else -> "Couldn't read or write the file."
}

fun importConfirmText(p: ImportPreview): String =
    "This device's ${workoutCount(p.deviceWorkouts)} will be replaced by the file's " +
        "${workoutCount(p.fileWorkouts)}. This can't be undone."
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.gymlog.app.ui.settings.BackupMessagesTest"`
Expected: PASS.

- [ ] **Step 5: Write the Settings screen**

`app/src/main/java/com/gymlog/app/ui/settings/SettingsScreen.kt`:
```kotlin
package com.gymlog.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gymlog.app.data.GymLogDatabase
import com.gymlog.app.data.backup.BackupService
import com.gymlog.app.data.backup.ImportPreview
import com.gymlog.app.ui.workout.ActiveWorkoutStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val service = remember {
        BackupService(GymLogDatabase.getDatabase(context).backupDao(), onDataReplaced = { ActiveWorkoutStore.clear() })
    }
    val appVersion = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var busy by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<ImportPreview?>(null) }

    // Runs [work] off the main thread with the buttons disabled; its result or error goes to
    // the snackbar. A null result means "nothing to announce" (e.g. the confirm dialog opened).
    fun runBackupTask(work: suspend () -> String?) {
        busy = true
        scope.launch {
            val message = try {
                withContext(Dispatchers.IO) { work() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                backupErrorMessage(e)
            }
            busy = false
            message?.let { snackbar.showSnackbar(it) }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult // picker cancelled
        runBackupTask {
            val export = service.export(appVersion)
            // "wt" truncates, so overwriting a longer existing file can't leave stale bytes.
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(export.json.toByteArray()) }
                ?: throw IOException("could not open $uri")
            "Exported ${workoutCount(export.workoutCount)}"
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult // picker cancelled
        runBackupTask {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: throw IOException("could not open $uri")
            val preview = service.preview(text)
            withContext(Dispatchers.Main) { pendingImport = preview }
            null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Backup", style = MaterialTheme.typography.titleMedium)
            Text(
                "Save all your exercises, workouts and history to a file, or restore from one. " +
                    "Importing replaces everything on this device.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = { exportLauncher.launch(backupFileName(LocalDate.now())) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Export data") }
            OutlinedButton(
                // Some file managers label .json as text/plain or octet-stream.
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Import data") }
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("GymLog $appVersion", style = MaterialTheme.typography.bodySmall)
        }
    }

    pendingImport?.let { preview ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Replace all data?") },
            text = { Text(importConfirmText(preview)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    runBackupTask {
                        service.import(preview)
                        "Imported ${workoutCount(preview.fileWorkouts)}"
                    }
                }) { Text("Replace") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text("Cancel") }
            },
        )
    }
}
```

Note: `onDataReplaced` (`ActiveWorkoutStore.clear()`) runs on the IO dispatcher; `ActiveWorkoutStore` is a `MutableStateFlow`, which is thread-safe.

- [ ] **Step 6: Wire navigation and the gear icon**

`Screen.kt`, add inside the sealed class:
```kotlin
    data object Settings : Screen("settings")
```

`CalendarScreen.kt`: add the parameter `onSettingsClick: () -> Unit = {}` after `onWorkoutClick`, add imports `androidx.compose.material.icons.filled.Settings` and `androidx.compose.material3.IconButton`, and replace
```kotlin
        topBar = { TopAppBar(title = { Text("GymLog") }) },
```
with
```kotlin
        topBar = {
            TopAppBar(
                title = { Text("GymLog") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
```

`GymLogNavigation.kt`: import `com.gymlog.app.ui.settings.SettingsScreen`; in the `CalendarScreen(` call add
```kotlin
                    onSettingsClick = {
                        navController.navigate(Screen.Settings.route)
                    },
```
and add a destination next to the others:
```kotlin
            composable(Screen.Settings.route) {
                SettingsScreen(onNavigateBack = { navController.popBackStack() })
            }
```
(The bottom bar hides automatically: it only shows for the three tab routes.)

- [ ] **Step 7: Run everything**

Run: `./gradlew testDebugUnitTest lintDebug assembleDebug`
Expected: all tests PASS (including `ManifestPermissionsTest`), lint clean, BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/gymlog/app/ui/settings app/src/test/java/com/gymlog/app/ui/settings app/src/main/java/com/gymlog/app/ui/navigation/Screen.kt app/src/main/java/com/gymlog/app/ui/navigation/GymLogNavigation.kt app/src/main/java/com/gymlog/app/ui/calendar/CalendarScreen.kt
git commit -m "Add Settings screen with data export and import"
```

---

### Task 5: v1.5 release prep and on-device verification

**Advances journeys:** 1, 2, 3, 4, 5, 6, 7, 11

**Files:**
- Modify: `app/build.gradle.kts` (`versionCode = 10500`, `versionName` default `"1.5"`)
- Create: `fastlane/metadata/android/en-US/changelogs/10500.txt`
- Modify (local only, gitignored): `CLAUDE.md`

**Interfaces:**
- Consumes: the finished v1.5 feature (Tasks 1-4).
- Produces: a v1.5 debug build on the phone and the evidence for journeys 1-7 and 11 (v1.5 part).

- [ ] **Step 1: Bump version and add the changelog**

`app/build.gradle.kts`: `versionCode = 10500` and `versionName = System.getenv("VERSION_NAME") ?: "1.5"`.

`fastlane/metadata/android/en-US/changelogs/10500.txt`:
```text
New Settings screen (gear icon on the Calendar tab) with data export and import. Export saves all your exercises, workouts and history to a readable JSON file you choose; import restores it, replacing what's on the device. Use it for backups or to move to a new phone. No new permissions.
```
Check: `wc -c` on the file is under 500.

`CLAUDE.md` (local, never committed): under Architecture, change "Room with SQLite (version 6, with migrations 3->4, 4->5, 5->6 plus destructive fallback)" to "Room with SQLite (version 7, with migrations 3->4 through 6->7 plus destructive fallback)", and add a line: "**Backup:** `data/backup/` holds JSON export/import (kotlinx.serialization DTOs, `BackupCodec`, `BackupService`); Settings screen drives it."

- [ ] **Step 2: Build and install on the phone**

Run: `./gradlew testDebugUnitTest lintDebug assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk`
Expected: tests pass, `Success`.

- [ ] **Step 3: Walk journeys 1-7 on the device and record evidence**

Use a scratch directory for pulled files. For DB dumps use:
```bash
pull_db() { # $1 = package, $2 = out prefix
  for f in gymlog_database gymlog_database-wal gymlog_database-shm; do adb exec-out run-as "$1" cat databases/$f > "$2.$f" 2>/dev/null; done
  sqlite3 "$2.gymlog_database" "PRAGMA wal_checkpoint;" >/dev/null
  for t in exercises workouts workout_exercises workout_sessions exercise_sets; do
    echo "== $t"; sqlite3 -header "$2.gymlog_database" "SELECT * FROM $t ORDER BY id;"
  done > "$2.dump.txt"
}
```
(Journey 3/7 "fresh install" on one phone: after exporting, `adb shell pm clear com.gymlog.app` gives an empty app, then import.) For journey 5, create and push the three bad files: a text file, a copy of the export with `"formatVersion": 99`, and a copy with one set's `sessionId` set to 999999. Paste each journey's evidence into the plan's Journeys table.

- [ ] **Step 4: Permissions check (journey 11, v1.5 part)**

Run: `aapt2 dump permissions app/build/outputs/apk/debug/app-debug.apk` and compare with the same command on the v1.4 APK from the GitHub release (`gh release download v1.4 -p app-release.apk -D <scratch>`).
Expected: identical permission lists, no `android.permission.INTERNET`.

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts fastlane/metadata/android/en-US/changelogs/10500.txt docs/superpowers/plans/2026-09-25-data-export-import-and-app-id.md
git commit -m "Prepare 1.5: data export and import"
```
Then ask the user whether to merge and tag `v1.5` (tag push publishes the release and the watchapp). Users must be on 1.5 and able to export before Task 6 ships.

---

### Task 6: App ID change to io.github.dcmcand.gymlog (v2.0)

**Advances journeys:** 8, 9, 10, 11

**Files:**
- Modify: `app/build.gradle.kts` (`applicationId`, `versionCode = 20000`, `versionName` default `"2.0"`)
- Modify: `pebble/package.json` (`companionApp.android.apps`)
- Create: `fastlane/metadata/android/en-US/changelogs/20000.txt`
- Modify (local only, gitignored): `CLAUDE.md`

**Interfaces:**
- Consumes: v1.5 released (Task 5) so users can export.
- Produces: the v2.0 build that sub-projects 2 and 3 submit to F-Droid and Play.

- [ ] **Step 1: Re-run the package-name audit**

Run: `grep -rn 'com\.gymlog\.app"\|APPLICATION_ID\|packageName\|setPackage' app/src/main --include=*.kt --include=*.xml`
Expected: only the `context.packageName` use in `SettingsScreen.kt` (correct for any ID) and no hardcoded application ID. Any other hit must be explained or fixed before continuing.

- [ ] **Step 2: Change the ID and versions**

`app/build.gradle.kts`: `applicationId = "io.github.dcmcand.gymlog"` (leave `namespace = "com.gymlog.app"`), `versionCode = 20000`, `versionName = System.getenv("VERSION_NAME") ?: "2.0"`.

`pebble/package.json`, the `companionApp.android.apps` array becomes:
```json
          { "package": "io.github.dcmcand.gymlog" },
          { "package": "com.gymlog.app" }
```
(keep the surrounding structure exactly as it is; only the array contents change).

`fastlane/metadata/android/en-US/changelogs/20000.txt`:
```text
GymLog has a new app ID (io.github.dcmcand.gymlog) so it can be published on F-Droid and Google Play. It installs as a separate app: in your old GymLog (1.5 or later) use Settings > Export data, then in this version use Settings > Import data, check your history, and uninstall the old app. The Pebble watch app works with either.
```
Check: under 500 characters.

`CLAUDE.md` (local, never committed): change "Namespace/App ID: `com.gymlog.app`" to "Namespace: `com.gymlog.app`; App ID: `io.github.dcmcand.gymlog` (changed in 2.0; `com.gymlog.app` is taken on Google Play)".

- [ ] **Step 3: Build both apps and run all checks**

Run: `./gradlew testDebugUnitTest lintDebug assembleDebug && (cd pebble && pebble build)`
Expected: tests pass, lint clean, both builds succeed; `unzip -p pebble/build/pebble.pbw appinfo.json | grep -A4 companionApp` shows both packages.

- [ ] **Step 4: Verify journeys 8-11 on the device**

1. Journey 8: with 1.5 (`com.gymlog.app`) installed and holding real data, `adb install app/build/outputs/apk/debug/app-debug.apk`; `adb shell pm list packages | grep -i gymlog` must list both. Export in 1.5, import in 2.0, then `pull_db com.gymlog.app old` and `pull_db io.github.dcmcand.gymlog new` (helper from Task 5) and `diff old.dump.txt new.dump.txt` must be empty.
2. Journey 9: uninstall the old app first (`adb uninstall com.gymlog.app`, after the user confirms the new app's data looks right), because the Core app routes watch messages to one companion. Sideload the new `.pbw`, capture logcat while exercising open-mid-workout, Easy/Hard, tap-to-cycle and hold-to-extend, decode with the 1.4 decoder script, and pull the new DB to show the watch-completed set.
3. Journey 10: complete a set, tap the rest timer notification, screenshot, and `adb shell dumpsys activity activities | grep -m1 mResumedActivity` shows `io.github.dcmcand.gymlog`.
4. Journey 11: `aapt2 dump permissions` on the 2.0 APK equals the 1.4 and 1.5 lists.

Paste all evidence into the Journeys table.

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts pebble/package.json fastlane/metadata/android/en-US/changelogs/20000.txt docs/superpowers/plans/2026-09-25-data-export-import-and-app-id.md
git commit -m "Change application ID to io.github.dcmcand.gymlog for 2.0"
```
Then run the Journeys verification gate (all 11 rows need fresh evidence) before any completion claim, and ask the user before merging or tagging `v2.0`.
