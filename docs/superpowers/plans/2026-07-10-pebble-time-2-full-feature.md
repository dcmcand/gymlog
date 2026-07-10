# Pebble Time 2 Watch Integration - Full Feature Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show the rest timer + current-set context on a paired Pebble Time 2 and let the user complete the current set (Easy/Hard) from the watch, preserving GymLog's offline/no-internet stance.

**Architecture:** A singleton `ActiveWorkoutStore` becomes the source of truth for the in-progress session (both `ActiveWorkoutScreen` and the watch path read/mutate through it). A `PebbleBridge` uses PebbleKitAndroid2 to push `{context, timer}` to an Alloy watchapp and receive Easy/Hard commands, which call `ActiveWorkoutStore.completeCurrentSet`. The Alloy watchapp (in `pebble/`) renders the countdown + context and sends commands. Phone->watch is proven; watch->phone works once the phone has pushed at least one message (spike result).

**Tech Stack:** Kotlin/Compose (existing app), Room; PebbleKitAndroid2 `io.rebble.pebblekit2:client:1.2.0` (Maven Central); Alloy (JS/Moddable, `pebble/message` + `pebble/button` + Poco) targeting `emery`.

## Global Constraints

- Min SDK 31, JDK 21. Compile/Target SDK 36.
- **No `INTERNET` and no `BLUETOOTH*` permission** may be added to the app (PebbleKitAndroid2 brokers via the Core app over IPC).
- Watchapp UUID (both sides, verbatim): `59e50327-68d4-4e6e-8a32-7331cb13194e`.
- Watchapp is **Alloy, embeddedjs-only, no `src/pkjs/`**.
- Message keys are **pinned integers**: watch side passes `keys` as a `Map` with explicit integers; Android side hardcodes the same integers. (Array form maps to `10000+index` - never use it here.)
- Protocol integers: phone->watch `exerciseName=0, targetText=1, setLabel=2, durationSec=3, running=4`; watch->phone `cmd=10` with `cmdEasy=1, cmdHard=2`.
- Ordering invariant: the phone must push at least one message to the watch before the watch can send. `PebbleBridge` pushes current context when a workout becomes active and whenever the timer state changes.
- Tests: JUnit4, table-driven with inline `data class Case` + loops, backtick names (see `WeightSuggestionTest.kt`).

## Journeys (definition of done - carried verbatim from the spec)

| # | Item | Proof | Check method | Evidence |
|---|------|-------|--------------|----------|
| 1 | Pebble shows the live rest countdown ticking in real time | Watch seconds decrement ~1/s to 0, matching the phone within ~1s, with one duration message per rest (not per-second) | narrated | *(empty)* |
| 2 | Pebble shows current exercise, target weight x reps, "Set X of Y" | Watch shows the active exercise name, target like "60kg x 5", and "Set 2 of 4" matching the phone | narrated | *(empty)* |
| 3 | Easy/Hard on the watch records the current set with that difficulty + target reps | After pressing Hard, the first-PENDING set in the DB has status=HARD and repsCompleted = its target | automated (`ActiveWorkoutStoreTest`) + narrated | *(empty)* |
| 4 | Watch completion starts the next rest timer on both devices | The press invokes `RestTimerService` and the watch begins a fresh countdown | automated + narrated | *(empty)* |
| 5 | Watch completion works with the phone locked / app backgrounded | Screen off, GymLog not foreground: pressing Easy records the set via the listener service | narrated: adb logcat + result on unlock | *(empty)* |
| 6 | Returning to the phone reflects the watch-made change | Opening `ActiveWorkoutScreen` after a backgrounded watch completion shows the set completed + timer running, no stale state | narrated | *(empty)* |
| 7 | Completing a set on the phone updates the watch | Completing a set in the app advances the watch context and restarts the watch countdown | narrated | *(empty)* |
| 8 | All sets complete -> watch shows a done/idle state | With no PENDING set left, the watch shows "workout done"/nothing-pending | narrated | *(empty)* |
| 9 | No active workout / watch absent -> app unaffected, no crash | GymLog behaves as before with no workout/watch; Pebble code never crashes it | automated (`./gradlew test` + build) + narrated | *(empty)* |
| 10 | No INTERNET/Bluetooth permission added | Merged `AndroidManifest` declares neither `INTERNET` nor any `BLUETOOTH*` permission | automated (`ManifestPermissionsTest`) | *(empty)* |

---

### Task 1: Add PebbleKitAndroid2 dependency + shared watch protocol

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts:52-66` (dependencies block)
- Create: `app/src/main/java/com/gymlog/app/watch/WatchProtocol.kt`

**Interfaces:**
- Produces: `WatchProtocol` (UUID + integer key/command constants) consumed by all later phone-side tasks and mirrored by the watchapp.

`Advances journeys: none (rationale: dependency + shared constants scaffolding).`

- [ ] **Step 1: Add the version + library to the catalog**

In `gradle/libs.versions.toml`, under `[versions]` add `pebblekit2 = "1.2.0"`, and under `[libraries]` add:
```toml
pebblekit2-client = { group = "io.rebble.pebblekit2", name = "client", version.ref = "pebblekit2" }
```

- [ ] **Step 2: Reference it in the app module**

In `app/build.gradle.kts` dependencies block add:
```kotlin
implementation(libs.pebblekit2.client)
```

- [ ] **Step 3: Create `WatchProtocol.kt`**

```kotlin
package com.gymlog.app.watch

import java.util.UUID

object WatchProtocol {
    val WATCHAPP_UUID: UUID = UUID.fromString("59e50327-68d4-4e6e-8a32-7331cb13194e")

    // phone -> watch
    const val KEY_EXERCISE_NAME = 0
    const val KEY_TARGET_TEXT = 1
    const val KEY_SET_LABEL = 2
    const val KEY_DURATION_SEC = 3
    const val KEY_RUNNING = 4

    // watch -> phone
    const val KEY_CMD = 10
    const val CMD_EASY = 1
    const val CMD_HARD = 2

    const val REST_SECONDS = 90
}
```

- [ ] **Step 4: Confirm it resolves**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL` (PebbleKitAndroid2 downloads from Maven Central).

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/com/gymlog/app/watch/WatchProtocol.kt
git commit -m "Add PebbleKitAndroid2 dependency and watch message protocol"
```

---

### Task 2: `ActiveWorkoutStore` - source of truth + set completion

**Files:**
- Create: `app/src/main/java/com/gymlog/app/watch/WatchContext.kt`
- Create: `app/src/main/java/com/gymlog/app/ui/workout/ActiveWorkoutStore.kt`
- Test: `app/src/test/java/com/gymlog/app/ActiveWorkoutStoreTest.kt`

**Interfaces:**
- Consumes: `WorkoutSessionDao` (`getById`, `getSetsForSession`, `updateSet`), `ExerciseDao.getById`, `SetStatus`, `ExerciseSet`.
- Produces:
  - `data class WatchContext(val exerciseName: String, val targetText: String, val setLabel: String)`
  - Pure fns: `firstPendingIndex(sets: List<ExerciseSet>): Int` (-1 if none); `completeSet(set: ExerciseSet, status: SetStatus): ExerciseSet`; `watchContextFor(exerciseName: String, set: ExerciseSet, positionInExercise: Int, countInExercise: Int): WatchContext`.
  - `ActiveWorkoutStore` singleton: `val state: StateFlow<ActiveWorkoutSnapshot?>`, `suspend fun load(dao, exerciseDao, sessionId)`, `suspend fun completeCurrentSet(dao, status): ExerciseSet?` (returns the completed set or null if none pending), `fun clear()`.

- [ ] **Step 1: Write failing tests for the pure functions**

```kotlin
package com.gymlog.app

import com.gymlog.app.data.ExerciseSet
import com.gymlog.app.data.SetStatus
import com.gymlog.app.ui.workout.completeSet
import com.gymlog.app.ui.workout.firstPendingIndex
import com.gymlog.app.ui.workout.watchContextFor
import org.junit.Assert.assertEquals
import org.junit.Test

private fun set(id: Long, num: Int, status: SetStatus, reps: Int? = 5, weight: Double? = 60.0) =
    ExerciseSet(id = id, sessionId = 1, exerciseId = 1, setNumber = num, weightKg = weight, repsCompleted = reps, status = status)

class ActiveWorkoutStoreTest {
    @Test fun `firstPendingIndex returns first PENDING in order`() {
        data class Case(val sets: List<ExerciseSet>, val expected: Int)
        val cases = listOf(
            Case(listOf(set(1,1,SetStatus.EASY), set(2,2,SetStatus.PENDING), set(3,3,SetStatus.PENDING)), 1),
            Case(listOf(set(1,1,SetStatus.PENDING)), 0),
            Case(listOf(set(1,1,SetStatus.EASY), set(2,2,SetStatus.HARD)), -1),
            Case(emptyList(), -1),
        )
        for (c in cases) assertEquals(c.expected, firstPendingIndex(c.sets))
    }

    @Test fun `completeSet sets status and keeps target reps`() {
        val s = set(1, 1, SetStatus.PENDING, reps = 5)
        assertEquals(SetStatus.HARD, completeSet(s, SetStatus.HARD).status)
        assertEquals(5, completeSet(s, SetStatus.HARD).repsCompleted)
    }

    @Test fun `watchContextFor formats weight reps and set label`() {
        val ctx = watchContextFor("Bench Press", set(1, 2, SetStatus.PENDING, reps = 5, weight = 60.0), positionInExercise = 2, countInExercise = 4)
        assertEquals("Bench Press", ctx.exerciseName)
        assertEquals("60kg x 5", ctx.targetText)
        assertEquals("Set 2 of 4", ctx.setLabel)
    }
}
```

- [ ] **Step 2: Run to confirm failure**

Run: `./gradlew test --tests "com.gymlog.app.ActiveWorkoutStoreTest"`
Expected: FAIL (unresolved references).

- [ ] **Step 3: Implement `WatchContext.kt` + pure functions and the store**

```kotlin
// WatchContext.kt
package com.gymlog.app.watch

data class WatchContext(
    val exerciseName: String,
    val targetText: String,
    val setLabel: String,
)
```

```kotlin
// ActiveWorkoutStore.kt
package com.gymlog.app.ui.workout

import com.gymlog.app.data.Exercise
import com.gymlog.app.data.ExerciseDao
import com.gymlog.app.data.ExerciseSet
import com.gymlog.app.data.SetStatus
import com.gymlog.app.data.WorkoutSession
import com.gymlog.app.data.WorkoutSessionDao
import com.gymlog.app.data.displayName
import com.gymlog.app.watch.WatchContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Ordered sets == the DAO's getSetsForSession order (exerciseId, setNumber).
fun firstPendingIndex(sets: List<ExerciseSet>): Int =
    sets.indexOfFirst { it.status == SetStatus.PENDING }

fun completeSet(set: ExerciseSet, status: SetStatus): ExerciseSet =
    set.copy(status = status)

private fun formatWeight(kg: Double?): String {
    if (kg == null) return ""
    return if (kg == kg.toLong().toDouble()) kg.toLong().toString() else kg.toString()
}

fun watchContextFor(
    exerciseName: String,
    set: ExerciseSet,
    positionInExercise: Int,
    countInExercise: Int,
): WatchContext {
    val w = formatWeight(set.weightKg)
    val target = if (w.isEmpty()) "${set.repsCompleted ?: 0} reps" else "${w}kg x ${set.repsCompleted ?: 0}"
    return WatchContext(
        exerciseName = exerciseName,
        targetText = target,
        setLabel = "Set $positionInExercise of $countInExercise",
    )
}

data class ActiveWorkoutSnapshot(
    val session: WorkoutSession,
    val orderedSets: List<ExerciseSet>,
    val exerciseNames: Map<Long, String>,
) {
    val currentIndex: Int get() = firstPendingIndex(orderedSets)
    val currentSet: ExerciseSet? get() = orderedSets.getOrNull(currentIndex)
    fun watchContext(): WatchContext? {
        val idx = currentIndex
        if (idx < 0) return null
        val set = orderedSets[idx]
        val forExercise = orderedSets.filter { it.exerciseId == set.exerciseId }
        val position = forExercise.indexOfFirst { it.id == set.id } + 1
        return watchContextFor(exerciseNames[set.exerciseId] ?: "", set, position, forExercise.size)
    }
}

object ActiveWorkoutStore {
    private val _state = MutableStateFlow<ActiveWorkoutSnapshot?>(null)
    val state: StateFlow<ActiveWorkoutSnapshot?> = _state.asStateFlow()

    suspend fun load(dao: WorkoutSessionDao, exerciseDao: ExerciseDao, sessionId: Long) {
        val session = dao.getById(sessionId) ?: return
        val sets = dao.getSetsForSession(sessionId)
        val names = sets.map { it.exerciseId }.distinct()
            .mapNotNull { id -> exerciseDao.getById(id)?.let { id to it.displayName() } }.toMap()
        _state.value = ActiveWorkoutSnapshot(session, sets, names)
    }

    // Returns the completed set, or null if there was no pending set.
    suspend fun completeCurrentSet(dao: WorkoutSessionDao, status: SetStatus): ExerciseSet? {
        val snap = _state.value ?: return null
        val idx = snap.currentIndex
        if (idx < 0) return null
        val updated = completeSet(snap.orderedSets[idx], status)
        dao.updateSet(updated)
        val newSets = snap.orderedSets.toMutableList().apply { this[idx] = updated }
        _state.value = snap.copy(orderedSets = newSets)
        return updated
    }

    fun applyExternalSetUpdate(updated: ExerciseSet) {
        val snap = _state.value ?: return
        val i = snap.orderedSets.indexOfFirst { it.id == updated.id }
        if (i < 0) return
        _state.value = snap.copy(orderedSets = snap.orderedSets.toMutableList().apply { this[i] = updated })
    }

    fun clear() { _state.value = null }
}
```

- [ ] **Step 4: Run tests to confirm pass**

Run: `./gradlew test --tests "com.gymlog.app.ActiveWorkoutStoreTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/gymlog/app/watch/WatchContext.kt app/src/main/java/com/gymlog/app/ui/workout/ActiveWorkoutStore.kt app/src/test/java/com/gymlog/app/ActiveWorkoutStoreTest.kt
git commit -m "Add ActiveWorkoutStore with current-set derivation and completion"
```

---

### Task 3: Watch message encode/decode (pure, testable)

**Files:**
- Create: `app/src/main/java/com/gymlog/app/watch/WatchMessages.kt`
- Test: `app/src/test/java/com/gymlog/app/WatchMessagesTest.kt`

**Interfaces:**
- Consumes: `WatchProtocol`, `WatchContext`, PebbleKitAndroid2 `PebbleDictionaryItem`.
- Produces:
  - `fun buildContextMessage(ctx: WatchContext?, durationSec: Int, running: Boolean): Map<UInt, PebbleDictionaryItem>`
  - `fun parseCommand(data: Map<UInt, PebbleDictionaryItem>): SetStatus?` (maps `CMD_EASY`->EASY, `CMD_HARD`->HARD, else null)

- [ ] **Step 1: Write failing tests**

```kotlin
package com.gymlog.app

import com.gymlog.app.data.SetStatus
import com.gymlog.app.watch.WatchContext
import com.gymlog.app.watch.WatchProtocol
import com.gymlog.app.watch.buildContextMessage
import com.gymlog.app.watch.parseCommand
import io.rebble.pebblekit2.common.model.PebbleDictionaryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchMessagesTest {
    @Test fun `buildContextMessage carries context and timer on pinned keys`() {
        val ctx = WatchContext("Bench", "60kg x 5", "Set 1 of 3")
        val m = buildContextMessage(ctx, durationSec = 90, running = true)
        assertEquals(PebbleDictionaryItem.Text("Bench"), m[WatchProtocol.KEY_EXERCISE_NAME.toUInt()])
        assertEquals(PebbleDictionaryItem.Text("60kg x 5"), m[WatchProtocol.KEY_TARGET_TEXT.toUInt()])
        assertEquals(PebbleDictionaryItem.Text("Set 1 of 3"), m[WatchProtocol.KEY_SET_LABEL.toUInt()])
        assertEquals(PebbleDictionaryItem.UInt16(90u), m[WatchProtocol.KEY_DURATION_SEC.toUInt()])
        assertEquals(PebbleDictionaryItem.UInt8(1u), m[WatchProtocol.KEY_RUNNING.toUInt()])
    }

    @Test fun `parseCommand maps cmd ints to statuses`() {
        assertEquals(SetStatus.EASY, parseCommand(mapOf(WatchProtocol.KEY_CMD.toUInt() to PebbleDictionaryItem.UInt8(WatchProtocol.CMD_EASY.toUByte()))))
        assertEquals(SetStatus.HARD, parseCommand(mapOf(WatchProtocol.KEY_CMD.toUInt() to PebbleDictionaryItem.UInt8(WatchProtocol.CMD_HARD.toUByte()))))
        assertNull(parseCommand(mapOf(99u to PebbleDictionaryItem.UInt8(1u))))
    }
}
```

- [ ] **Step 2: Run to confirm failure**

Run: `./gradlew test --tests "com.gymlog.app.WatchMessagesTest"`
Expected: FAIL (unresolved). NOTE: confirm the exact `PebbleDictionaryItem` subtype names against the resolved dependency (`PebbleDictionaryItem.Text`, `.UInt8`, `.UInt16`); adjust the constructors in both test and impl if the library names differ (e.g. `Int32`). This is the one place library-name drift can bite - reconcile it here before implementing.

- [ ] **Step 3: Implement `WatchMessages.kt`**

```kotlin
package com.gymlog.app.watch

import com.gymlog.app.data.SetStatus
import io.rebble.pebblekit2.common.model.PebbleDictionaryItem

fun buildContextMessage(ctx: WatchContext?, durationSec: Int, running: Boolean): Map<UInt, PebbleDictionaryItem> {
    val m = HashMap<UInt, PebbleDictionaryItem>()
    m[WatchProtocol.KEY_EXERCISE_NAME.toUInt()] = PebbleDictionaryItem.Text(ctx?.exerciseName ?: "")
    m[WatchProtocol.KEY_TARGET_TEXT.toUInt()] = PebbleDictionaryItem.Text(ctx?.targetText ?: "")
    m[WatchProtocol.KEY_SET_LABEL.toUInt()] = PebbleDictionaryItem.Text(ctx?.setLabel ?: "")
    m[WatchProtocol.KEY_DURATION_SEC.toUInt()] = PebbleDictionaryItem.UInt16(durationSec.toUShort())
    m[WatchProtocol.KEY_RUNNING.toUInt()] = PebbleDictionaryItem.UInt8(if (running) 1u else 0u)
    return m
}

fun parseCommand(data: Map<UInt, PebbleDictionaryItem>): SetStatus? {
    val cmd = data[WatchProtocol.KEY_CMD.toUInt()] ?: return null
    val value = when (cmd) {
        is PebbleDictionaryItem.UInt8 -> cmd.value.toInt()
        is PebbleDictionaryItem.Int32 -> cmd.value
        else -> return null
    }
    return when (value) {
        WatchProtocol.CMD_EASY -> SetStatus.EASY
        WatchProtocol.CMD_HARD -> SetStatus.HARD
        else -> null
    }
}
```

- [ ] **Step 4: Run tests, reconcile any library type-name differences, confirm pass**

Run: `./gradlew test --tests "com.gymlog.app.WatchMessagesTest"`
Expected: PASS (after reconciling `PebbleDictionaryItem` subtype names with the actual library API).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/gymlog/app/watch/WatchMessages.kt app/src/test/java/com/gymlog/app/WatchMessagesTest.kt
git commit -m "Add watch message encode/decode with pinned integer keys"
```

`Advances journeys: 1, 2, 3, 7 (message contract that carries context and commands).`

---

### Task 4: `PebbleBridge` + listener service + manifest, no new permissions

**Files:**
- Create: `app/src/main/java/com/gymlog/app/watch/PebbleBridge.kt`
- Create: `app/src/main/java/com/gymlog/app/watch/GymLogPebbleListenerService.kt`
- Modify: `app/src/main/AndroidManifest.xml` (add `<queries>` + listener `<service>`; add NO permissions)
- Test: `app/src/test/java/com/gymlog/app/ManifestPermissionsTest.kt`

**Interfaces:**
- Consumes: PebbleKitAndroid2 `DefaultPebbleSender`/`PebbleSender`, `BasePebbleListenerService`, `WatchProtocol`, `WatchMessages`, `ActiveWorkoutStore`, `RestTimerService`, `WorkoutSessionDao`.
- Produces:
  - `object PebbleBridge { fun pushContext(context: Context, ctx: WatchContext?, durationSec: Int, running: Boolean) }` (fire-and-forget; launches a coroutine calling `sendDataToPebble(WATCHAPP_UUID, buildContextMessage(...))`).
  - `GymLogPebbleListenerService : BasePebbleListenerService` overriding `onMessageReceived(uuid, data, watch)` -> `parseCommand` -> `ActiveWorkoutStore.completeCurrentSet(dao, status)` -> on non-null, `RestTimerService.start(context, WatchProtocol.REST_SECONDS, session.id)` -> push refreshed context.

- [ ] **Step 1: Write the manifest-permissions test (journey 10) first**

```kotlin
package com.gymlog.app

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class ManifestPermissionsTest {
    @Test fun `manifest declares no internet or bluetooth permission`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertFalse("INTERNET must not be requested", manifest.contains("android.permission.INTERNET"))
        assertFalse("no BLUETOOTH* permission", Regex("android\\.permission\\.BLUETOOTH").containsMatchIn(manifest))
    }
}
```

- [ ] **Step 2: Run to confirm it passes now (guards against regressions in this task)**

Run: `./gradlew test --tests "com.gymlog.app.ManifestPermissionsTest"`
Expected: PASS (no such permissions yet). It must still PASS after Step 4.

- [ ] **Step 3: Implement `PebbleBridge.kt`**

```kotlin
package com.gymlog.app.watch

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import io.rebble.pebblekit2.client.DefaultPebbleSender

object PebbleBridge {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun pushContext(context: Context, ctx: WatchContext?, durationSec: Int, running: Boolean) {
        val sender = DefaultPebbleSender(context.applicationContext)
        scope.launch {
            try {
                sender.sendDataToPebble(WatchProtocol.WATCHAPP_UUID, buildContextMessage(ctx, durationSec, running))
            } catch (_: Exception) {
                // Watch not connected / Core app absent: ignore, phone flow is unaffected (journey 9).
            }
        }
    }
}
```
NOTE: confirm `DefaultPebbleSender`'s constructor and `sendDataToPebble` signature against `io.rebble.pebblekit2:client:1.2.0` (the README shows `DefaultPebbleSender().sendDataToPebble(UUID, map)`); adjust construction (context vs no-arg) to match the actual API.

- [ ] **Step 4: Implement the listener service + register it in the manifest**

```kotlin
package com.gymlog.app.watch

import com.gymlog.app.data.GymLogDatabase
import com.gymlog.app.service.RestTimerService
import com.gymlog.app.ui.workout.ActiveWorkoutStore
import io.rebble.pebblekit2.client.BasePebbleListenerService
import io.rebble.pebblekit2.common.model.PebbleDictionaryItem
import io.rebble.pebblekit2.common.model.WatchIdentifier
import java.util.UUID

class GymLogPebbleListenerService : BasePebbleListenerService() {
    override suspend fun onMessageReceived(
        watchappUUID: UUID,
        data: Map<UInt, PebbleDictionaryItem>,
        watch: WatchIdentifier,
    ): ReceiveResult {
        if (watchappUUID != WatchProtocol.WATCHAPP_UUID) return ReceiveResult.Ack
        val status = parseCommand(data) ?: return ReceiveResult.Ack
        val dao = GymLogDatabase.getDatabase(applicationContext).workoutSessionDao()
        val completed = ActiveWorkoutStore.completeCurrentSet(dao, status)
        if (completed != null) {
            RestTimerService.start(applicationContext, WatchProtocol.REST_SECONDS, completed.sessionId)
            val ctx = ActiveWorkoutStore.state.value?.watchContext()
            PebbleBridge.pushContext(applicationContext, ctx, WatchProtocol.REST_SECONDS, running = true)
        }
        return ReceiveResult.Ack
    }
}
```
Manifest (inside `<application>`), plus a top-level `<queries>` per the PebbleKitAndroid2 README. Add NO `<uses-permission>`:
```xml
<service
    android:name=".watch.GymLogPebbleListenerService"
    android:exported="true">
    <intent-filter>
        <action android:name="io.rebble.pebblekit2.RECEIVE_DATA_FROM_WATCH" />
    </intent-filter>
</service>
```
```xml
<queries>
    <intent>
        <action android:name="io.rebble.pebblekit2.SEND_DATA_TO_WATCH" />
    </intent>
</queries>
```
NOTE: confirm the exact `BasePebbleListenerService` method signature, `ReceiveResult` enum values, `WatchIdentifier` type, and the manifest action strings / `exported` requirement against the sample in the README; adjust to match.

- [ ] **Step 5: Build + run the full test suite (journeys 9, 10)**

Run: `./gradlew assembleDebug test`
Expected: `BUILD SUCCESSFUL`; `ManifestPermissionsTest` still PASSES; all existing tests green.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/gymlog/app/watch/PebbleBridge.kt app/src/main/java/com/gymlog/app/watch/GymLogPebbleListenerService.kt app/src/main/AndroidManifest.xml app/src/test/java/com/gymlog/app/ManifestPermissionsTest.kt
git commit -m "Add PebbleBridge and watch listener service (no new permissions)"
```

`Advances journeys: 3, 5, 9, 10.`

---

### Task 5: Wire the app to the store and push context to the watch

**Files:**
- Modify: `app/src/main/java/com/gymlog/app/ui/workout/ActiveWorkoutScreen.kt`
- Modify: `app/src/main/java/com/gymlog/app/service/RestTimerService.kt` (or a small observer) to trigger pushes on timer-state change

**Interfaces:**
- Consumes: `ActiveWorkoutStore`, `PebbleBridge`, `RestTimerService.timerState`.

`Advances journeys: 1, 2, 6, 7, 8.`

- [ ] **Step 1: Load the store when the workout screen initializes**

In `ActiveWorkoutScreen`'s init `LaunchedEffect` (after `sessionId` is known and sets are loaded), call `ActiveWorkoutStore.load(sessionDao, exerciseDao, sessionId)`. Keep the existing `ActiveWorkoutState` for now for rendering; the store runs alongside as the watch's source of truth.

- [ ] **Step 2: On any set completion in the UI, update the store and push context**

In both completion callbacks (`onComplete` in the modal at `ActiveWorkoutScreen.kt` and `onSetUpdated`), after `sessionDao.updateSet(updatedSet)` add `ActiveWorkoutStore.applyExternalSetUpdate(updatedSet)` and then push the new current context:
```kotlin
val ctx = ActiveWorkoutStore.state.value?.watchContext()
PebbleBridge.pushContext(context, ctx, WatchProtocol.REST_SECONDS, running = true)
```

- [ ] **Step 3: Push timer/context on rest-timer state changes**

In `ActiveWorkoutScreen`, add a `LaunchedEffect(timerState)` that pushes the current context with `durationSec = timerState.remainingSeconds` (or `totalSeconds` on start) and `running = timerState.isRunning`, so the watch tracks start/extend/stop/finish. When `!isRunning && remainingSeconds==0`, push `running=false` so the watch shows the done/idle state (journey 8).

- [ ] **Step 4: Refresh from the store when returning to foreground (journey 6)**

Add a `LifecycleEventObserver` (ON_RESUME) that re-reads `ActiveWorkoutStore.state` (already updated by the listener service) and reconciles the visible `ActiveWorkoutState` sets, so a watch-made completion appears without a manual reload.

- [ ] **Step 5: Build**

Run: `./gradlew assembleDebug test`
Expected: `BUILD SUCCESSFUL`, tests green.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/gymlog/app/ui/workout/ActiveWorkoutScreen.kt app/src/main/java/com/gymlog/app/service/RestTimerService.kt
git commit -m "Push workout context + timer to the watch, reconcile watch-made changes"
```

---

### Task 6: The Alloy watchapp (`pebble/`)

**Files:**
- Create: `pebble/` Alloy project (`pebble new-project --alloy pebble`), then edit `pebble/package.json` and `pebble/src/embeddedjs/main.js`; delete `pebble/src/pkjs/`.

**Interfaces:**
- Mirrors `WatchProtocol`: UUID `59e50327-68d4-4e6e-8a32-7331cb13194e`; `messageKeys` object form pinning 0..4 and 10; `Message` constructed with `keys: new Map([...])` using those integers; `companionApp.android.apps[].package = "com.gymlog.app"`.

`Advances journeys: 1, 2, 3, 4, 8.`

- [ ] **Step 1: Scaffold + configure** — `cd pebble` under the repo (or scaffold then move). Set `package.json`: `watchapp.watchface=false`, `targetPlatforms:["emery"]`, the UUID, `messageKeys` object form (`{"EXERCISE":0,"TARGET":1,"SETLABEL":2,"DURATION":3,"RUNNING":4,"CMD":10}`), and `companionApp.android.apps[].package="com.gymlog.app"`. Delete `src/pkjs/`.

- [ ] **Step 2: Implement `src/embeddedjs/main.js`** — Poco UI drawing three lines (exercise name, target text, and a countdown derived from a local ticking timer seeded by `DURATION`/`RUNNING`), and the set label. Use `new Message({ keys: new Map([["EXERCISE",0],["TARGET",1],["SETLABEL",2],["DURATION",3],["RUNNING",4],["CMD",10]]), onReadable(){ update fields, (re)start local countdown } })`. Two `Button`s: UP -> `message.write(new Map([["CMD",1]]))` (Easy), DOWN -> `write [["CMD",2]]` (Hard); wrap in try/catch (send is valid after the first inbound message). When `RUNNING==0`, show a done/idle screen (journey 8).

- [ ] **Step 3: Build** — `cd pebble && pebble build`. Expected: `.pbw` created for `emery`.

- [ ] **Step 4: Commit**

```bash
git add pebble
git commit -m "Add Alloy watchapp for GymLog rest timer and set completion"
```

---

### Task 7: End-to-end hardware verification (all journeys)

**Files:** none (verification only). Produces evidence for the Journeys table.

`Advances journeys: 1-10 (verification gate).`

- [ ] **Step 1: Install** — build+install the app to the phone (`adb install -r`), sideload the watchapp `.pbw` (push to `/sdcard/Download`, open with the Core app), open the watchapp.
- [ ] **Step 2: Start a workout on the phone.** Confirm the watch shows exercise/target/"Set X of Y" (journeys 2, 7) and, when a rest starts, a live countdown (journey 1).
- [ ] **Step 3: Complete a set from the watch (Easy/Hard).** Confirm via `adb logcat` the listener received the command, the DB set is HARD/EASY (journey 3), and the next rest timer starts on both devices (journey 4).
- [ ] **Step 4: Lock the phone, complete a set from the watch.** Confirm it records (journey 5); unlock and confirm the screen reflects it (journey 6).
- [ ] **Step 5: Complete all sets.** Confirm the watch shows the done/idle state (journey 8).
- [ ] **Step 6: No-watch regression.** With the watch off/unpaired, use the app normally; confirm no crashes and identical behavior (journey 9). Confirm merged manifest has no INTERNET/BLUETOOTH (`ManifestPermissionsTest` + grep the merged manifest) (journey 10).
- [ ] **Step 7: Paste evidence into the Journeys table** in this plan (and the spec), then run the `verification-before-completion` gate before any completion claim.

---

## Self-review notes

- **Spec coverage:** watchapp (Task 6), `PebbleBridge` (Task 4), `ActiveWorkoutStore` (Task 2), pinned protocol (Tasks 1/3), permissions guarantee (Task 4 + `ManifestPermissionsTest`), current-set model (Task 2), no-pkjs + writable-after-inbound ordering (Tasks 4/6, from spike results). All covered.
- **Placeholder scan:** the three `NOTE:` items (PebbleKitAndroid2 `PebbleDictionaryItem` subtype names, `DefaultPebbleSender` construction, `BasePebbleListenerService` signature/`ReceiveResult`/manifest actions) are explicit "confirm against the resolved dependency and adjust" reconciliation steps, not vague TODOs - they exist because the exact 1.2.0 API names must be read from the dependency at implementation time.
- **Type consistency:** `WatchContext`, `firstPendingIndex`, `completeSet`, `watchContextFor`, `buildContextMessage`, `parseCommand`, `ActiveWorkoutStore.completeCurrentSet/applyExternalSetUpdate` are used consistently across tasks.
- **Journey mapping:** every task has an `Advances journeys:` line; scaffolding tasks (1) are `none` with rationale.
