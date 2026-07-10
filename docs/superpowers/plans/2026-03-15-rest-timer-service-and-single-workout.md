# Rest Timer Foreground Service & Single Active Workout

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the in-composable rest timer with an Android foreground service so the timer, haptics, and lock screen notification survive app backgrounding/navigation, and enforce a single active workout constraint.

**Architecture:** A `RestTimerService` foreground service owns the countdown, notification, and vibration. Timer state is exposed via a `StateFlow` in a companion object singleton that the UI collects. The existing `RestTimerNotification` becomes a helper used by the service. For single-workout enforcement, `ActiveWorkoutScreen` checks for an existing IN_PROGRESS session before creating a new one.

**Tech Stack:** Android Service, CountDownTimer, StateFlow, Jetpack Compose state collection, Room

**Issues:** #17, #18, #19, #20

---

## File Structure

### New files
- `app/src/main/java/com/gymlog/app/service/RestTimerService.kt` - Foreground service managing countdown, notification, vibration
- `app/src/test/java/com/gymlog/app/service/RestTimerServiceTest.kt` - Unit tests for timer state logic

### Modified files
- `app/src/main/AndroidManifest.xml` - Add FOREGROUND_SERVICE permissions and service declaration
- `app/src/main/java/com/gymlog/app/notification/RestTimerNotification.kt` - Add notification builder method for service use, bump channel importance
- `app/src/main/java/com/gymlog/app/ui/workout/ActiveWorkoutScreen.kt` - Replace local timer state with service observation, add single-workout guard
- `app/src/main/java/com/gymlog/app/MainActivity.kt` - No changes needed (existing intent handling works)

---

## Chunk 1: RestTimerService and notification changes

### Task 1: Update AndroidManifest.xml

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add permissions and service declaration**

Add `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_SPECIAL_USE` permissions. Declare the service with `foregroundServiceType="specialUse"`.

```xml
<uses-permission android:name="android.permission.VIBRATE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />

<application ...>
    <service
        android:name=".service.RestTimerService"
        android:foregroundServiceType="specialUse"
        android:exported="false" />
    <activity ... />
</application>
```

- [ ] **Step 2: Verify build succeeds**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "Add foreground service permissions and declaration for rest timer"
```

### Task 2: Update RestTimerNotification for service use

**Files:**
- Modify: `app/src/main/java/com/gymlog/app/notification/RestTimerNotification.kt`

- [ ] **Step 1: Add a buildNotification method and bump channel importance**

The service needs to build a Notification object (not post it directly). Change channel importance from `IMPORTANCE_LOW` to `IMPORTANCE_DEFAULT` so the notification shows on lock screen. Add a `buildNotification()` method that returns a `Notification` and a `buildCompletedNotification()` for when the timer finishes.

```kotlin
package com.gymlog.app.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.gymlog.app.MainActivity
import com.gymlog.app.R

object RestTimerNotification {

    const val CHANNEL_ID = "rest_timer"
    const val NOTIFICATION_ID = 1
    const val EXTRA_SESSION_ID = "rest_timer_session_id"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Rest Timer",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Countdown during rest between sets"
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun buildNotification(context: Context, endTimeMs: Long, sessionId: Long?): Notification {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (sessionId != null) putExtra(EXTRA_SESSION_ID, sessionId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Rest Timer")
            .setContentText("Rest between sets")
            .setWhen(endTimeMs)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .build()
    }

    fun buildCompletedNotification(context: Context, sessionId: Long?): Notification {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (sessionId != null) putExtra(EXTRA_SESSION_ID, sessionId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Rest Complete")
            .setContentText("Time to start your next set")
            .setOngoing(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
    }

    fun show(context: Context, endTimeMs: Long, sessionId: Long? = null) {
        val notification = buildNotification(context, endTimeMs, sessionId)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(NOTIFICATION_ID)
    }
}
```

Key changes from current code:
- `CHANNEL_ID` and `NOTIFICATION_ID` are now `const` (were `private const`) so the service can reference them
- Channel importance bumped from `IMPORTANCE_LOW` to `IMPORTANCE_DEFAULT` for lock screen visibility
- New `buildNotification()` returns a `Notification` object instead of posting it
- New `buildCompletedNotification()` for the "Rest Complete" state
- Existing `show()` and `cancel()` kept for backward compatibility during migration

- [ ] **Step 2: Verify build succeeds**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/gymlog/app/notification/RestTimerNotification.kt
git commit -m "Add notification builder methods for service use, bump channel importance"
```

### Task 3: Create RestTimerService

**Files:**
- Create: `app/src/main/java/com/gymlog/app/service/RestTimerService.kt`

- [ ] **Step 1: Write the RestTimerService**

```kotlin
package com.gymlog.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.gymlog.app.notification.RestTimerNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TimerState(
    val isRunning: Boolean = false,
    val remainingSeconds: Int = 0,
    val totalSeconds: Int = 0,
    val sessionId: Long? = null,
    val endTimeMs: Long = 0L
)

class RestTimerService : Service() {

    private var countDownTimer: CountDownTimer? = null

    companion object {
        private const val ACTION_START = "com.gymlog.app.ACTION_START_TIMER"
        private const val ACTION_EXTEND = "com.gymlog.app.ACTION_EXTEND_TIMER"
        private const val ACTION_STOP = "com.gymlog.app.ACTION_STOP_TIMER"
        private const val EXTRA_DURATION = "duration_seconds"
        private const val EXTRA_SESSION_ID = "session_id"
        private const val EXTRA_EXTEND_SECONDS = "extend_seconds"

        private val _timerState = MutableStateFlow(TimerState())
        val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

        fun start(context: Context, durationSeconds: Int, sessionId: Long) {
            val intent = Intent(context, RestTimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_DURATION, durationSeconds)
                putExtra(EXTRA_SESSION_ID, sessionId)
            }
            context.startForegroundService(intent)
        }

        fun extend(context: Context, additionalSeconds: Int) {
            val intent = Intent(context, RestTimerService::class.java).apply {
                action = ACTION_EXTEND
                putExtra(EXTRA_EXTEND_SECONDS, additionalSeconds)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, RestTimerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val duration = intent.getIntExtra(EXTRA_DURATION, 90)
                val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1L)
                startTimer(duration, if (sessionId != -1L) sessionId else null)
            }
            ACTION_EXTEND -> {
                val additional = intent.getIntExtra(EXTRA_EXTEND_SECONDS, 90)
                extendTimer(additional)
            }
            ACTION_STOP -> {
                stopTimer()
            }
        }
        return START_NOT_STICKY
    }

    private fun startTimer(durationSeconds: Int, sessionId: Long?) {
        countDownTimer?.cancel()

        val endTimeMs = System.currentTimeMillis() + durationSeconds * 1000L
        val notification = RestTimerNotification.buildNotification(this, endTimeMs, sessionId)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                RestTimerNotification.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(RestTimerNotification.NOTIFICATION_ID, notification)
        }

        _timerState.value = TimerState(
            isRunning = true,
            remainingSeconds = durationSeconds,
            totalSeconds = durationSeconds,
            sessionId = sessionId,
            endTimeMs = endTimeMs
        )

        countDownTimer = object : CountDownTimer(durationSeconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val remaining = ((millisUntilFinished + 500) / 1000).toInt()
                _timerState.value = _timerState.value.copy(remainingSeconds = remaining)
            }

            override fun onFinish() {
                _timerState.value = _timerState.value.copy(remainingSeconds = 0, isRunning = false)
                vibrate()

                val completedNotification = RestTimerNotification.buildCompletedNotification(
                    this@RestTimerService,
                    _timerState.value.sessionId
                )
                val manager = getSystemService(android.app.NotificationManager::class.java)
                manager.notify(RestTimerNotification.NOTIFICATION_ID, completedNotification)

                // Auto-dismiss after 3 seconds
                android.os.Handler(mainLooper).postDelayed({
                    _timerState.value = TimerState()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }, 3000L)
            }
        }.start()
    }

    private fun extendTimer(additionalSeconds: Int) {
        val current = _timerState.value
        if (!current.isRunning) return

        countDownTimer?.cancel()

        val newRemaining = current.remainingSeconds + additionalSeconds
        val newTotal = current.totalSeconds + additionalSeconds
        val newEndTimeMs = System.currentTimeMillis() + newRemaining * 1000L

        // Update notification with new end time
        val notification = RestTimerNotification.buildNotification(this, newEndTimeMs, current.sessionId)
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(RestTimerNotification.NOTIFICATION_ID, notification)

        _timerState.value = current.copy(
            remainingSeconds = newRemaining,
            totalSeconds = newTotal,
            endTimeMs = newEndTimeMs
        )

        countDownTimer = object : CountDownTimer(newRemaining * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val remaining = ((millisUntilFinished + 500) / 1000).toInt()
                _timerState.value = _timerState.value.copy(remainingSeconds = remaining)
            }

            override fun onFinish() {
                _timerState.value = _timerState.value.copy(remainingSeconds = 0, isRunning = false)
                vibrate()

                val completedNotification = RestTimerNotification.buildCompletedNotification(
                    this@RestTimerService,
                    _timerState.value.sessionId
                )
                val mgr = getSystemService(android.app.NotificationManager::class.java)
                mgr.notify(RestTimerNotification.NOTIFICATION_ID, completedNotification)

                android.os.Handler(mainLooper).postDelayed({
                    _timerState.value = TimerState()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }, 3000L)
            }
        }.start()
    }

    private fun stopTimer() {
        countDownTimer?.cancel()
        countDownTimer = null
        _timerState.value = TimerState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun vibrate() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(VibratorManager::class.java)
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Vibrator::class.java)
            }
            vibrator?.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 300, 200, 300),
                    -1
                )
            )
        } catch (_: Exception) {
            // Vibration not available
        }
    }

    override fun onDestroy() {
        countDownTimer?.cancel()
        countDownTimer = null
        _timerState.value = TimerState()
        super.onDestroy()
    }
}
```

- [ ] **Step 2: Verify build succeeds**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/gymlog/app/service/RestTimerService.kt
git commit -m "Add RestTimerService foreground service for persistent rest timer"
```

### Task 4: Write unit tests for TimerState

**Files:**
- Create: `app/src/test/java/com/gymlog/app/service/RestTimerServiceTest.kt`

- [ ] **Step 1: Write tests for TimerState data class behavior**

```kotlin
package com.gymlog.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestTimerServiceTest {

    @Test
    fun `default TimerState is idle`() {
        val state = TimerState()
        assertFalse(state.isRunning)
        assertEquals(0, state.remainingSeconds)
        assertEquals(0, state.totalSeconds)
        assertNull(state.sessionId)
        assertEquals(0L, state.endTimeMs)
    }

    @Test
    fun `TimerState copy preserves fields`() {
        data class Case(
            val label: String,
            val initial: TimerState,
            val updated: TimerState,
            val expectedRemaining: Int,
            val expectedRunning: Boolean
        )

        val base = TimerState(
            isRunning = true,
            remainingSeconds = 90,
            totalSeconds = 90,
            sessionId = 42L,
            endTimeMs = 1000L
        )

        val cases = listOf(
            Case(
                "decrement remaining",
                base,
                base.copy(remainingSeconds = 89),
                expectedRemaining = 89,
                expectedRunning = true
            ),
            Case(
                "timer finished",
                base,
                base.copy(remainingSeconds = 0, isRunning = false),
                expectedRemaining = 0,
                expectedRunning = false
            ),
            Case(
                "extend timer",
                base,
                base.copy(remainingSeconds = 180, totalSeconds = 180),
                expectedRemaining = 180,
                expectedRunning = true
            )
        )

        for (case in cases) {
            assertEquals(case.label, case.expectedRemaining, case.updated.remainingSeconds)
            assertEquals(case.label, case.expectedRunning, case.updated.isRunning)
            assertEquals(case.label, 42L, case.updated.sessionId)
        }
    }
}
```

- [ ] **Step 2: Run tests**

Run: `./gradlew test --tests "com.gymlog.app.service.RestTimerServiceTest"`
Expected: All tests pass

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/gymlog/app/service/RestTimerServiceTest.kt
git commit -m "Add unit tests for TimerState"
```

---

## Chunk 2: Wire up UI to service and enforce single workout

### Task 5: Update ActiveWorkoutScreen to use RestTimerService

**Files:**
- Modify: `app/src/main/java/com/gymlog/app/ui/workout/ActiveWorkoutScreen.kt`

- [ ] **Step 1: Replace local timer state with service state collection**

Remove these local state variables:
```kotlin
// REMOVE these lines (79-83):
var showRestTimer by remember { mutableStateOf(false) }
var timerRunning by remember { mutableStateOf(false) }
var remainingSeconds by remember { mutableIntStateOf(90) }
var totalTimerSeconds by remember { mutableIntStateOf(90) }
```

Remove the `startTimerNotification` local function (lines 99-102).

Remove the entire countdown LaunchedEffect (lines 105-135).

Replace with service state collection:

```kotlin
// Collect timer state from service
val timerState by RestTimerService.timerState.collectAsState()
val showRestTimer = timerState.isRunning || timerState.remainingSeconds == 0
```

Add this import:
```kotlin
import androidx.compose.runtime.collectAsState
import com.gymlog.app.service.RestTimerService
```

- [ ] **Step 2: Update timer start calls**

Replace all timer start blocks. There are two locations:

Location 1 - `onSetUpdated` callback (around line 358-364):
```kotlin
// BEFORE:
if (updatedSet.status != SetStatus.PENDING) {
    remainingSeconds = 90
    totalTimerSeconds = 90
    showRestTimer = true
    timerRunning = true
    startTimerNotification(90)
}

// AFTER:
if (updatedSet.status != SetStatus.PENDING) {
    sessionId?.let { RestTimerService.start(context, 90, it) }
}
```

Location 2 - `SetCompletionModal.onComplete` callback (around line 422-427):
```kotlin
// BEFORE:
remainingSeconds = 90
totalTimerSeconds = 90
showRestTimer = true
timerRunning = true
startTimerNotification(90)

// AFTER:
sessionId?.let { RestTimerService.start(context, 90, it) }
```

- [ ] **Step 3: Update RestTimerBottomBar wiring**

Replace the bottom bar section (around line 264-278):
```kotlin
// BEFORE:
if (showRestTimer) {
    RestTimerBottomBar(
        remainingSeconds = remainingSeconds,
        totalSeconds = totalTimerSeconds,
        onExtend = {
            remainingSeconds += 90
            totalTimerSeconds += 90
            startTimerNotification(remainingSeconds)
        },
        onDismiss = {
            timerRunning = false
            showRestTimer = false
            RestTimerNotification.cancel(context)
        }
    )
}

// AFTER:
if (showRestTimer) {
    RestTimerBottomBar(
        remainingSeconds = timerState.remainingSeconds,
        totalSeconds = timerState.totalSeconds,
        onExtend = {
            RestTimerService.extend(context, 90)
        },
        onDismiss = {
            RestTimerService.stop(context)
        }
    )
}
```

- [ ] **Step 4: Update the Finish Workout button**

In the Finish Workout button onClick (around line 283), replace `RestTimerNotification.cancel(context)` with `RestTimerService.stop(context)`:

```kotlin
onClick = {
    RestTimerService.stop(context)
    scope.launch {
        // ... existing session completion code
    }
}
```

- [ ] **Step 5: Remove unused imports**

Remove these imports that are no longer needed:
- `android.os.VibrationEffect`
- `android.os.Vibrator`
- `android.os.VibratorManager`
- `com.gymlog.app.notification.RestTimerNotification`
- `kotlinx.coroutines.delay`
- `androidx.compose.runtime.mutableIntStateOf`

Add:
- `androidx.compose.runtime.collectAsState`
- `com.gymlog.app.service.RestTimerService`

- [ ] **Step 6: Verify build succeeds**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Run existing tests**

Run: `./gradlew test`
Expected: All tests pass

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/gymlog/app/ui/workout/ActiveWorkoutScreen.kt
git commit -m "Wire ActiveWorkoutScreen to RestTimerService instead of local timer state"
```

### Task 6: Enforce single active workout (Issue #17)

**Files:**
- Modify: `app/src/main/java/com/gymlog/app/ui/workout/ActiveWorkoutScreen.kt`

- [ ] **Step 1: Add single-workout guard to session creation**

In the `LaunchedEffect(workoutId, resumeSessionId)` block (around line 138), add a check before creating a new session. When `resumeSessionId` is null and `workoutId` is not null, first check if there's already an IN_PROGRESS session:

```kotlin
LaunchedEffect(workoutId, resumeSessionId) {
    if (resumeSessionId != null) {
        // Resume existing session (unchanged)
        sessionId = resumeSessionId
        val sets = sessionDao.getSetsForSession(resumeSessionId)
        val exerciseIds = sets.map { it.exerciseId }.distinct()
        for (eid in exerciseIds) {
            val exercise = exerciseDao.getById(eid) ?: continue
            val exerciseSets = sets.filter { it.exerciseId == eid }
            workoutState.addExercise(exercise, exerciseSets)
        }
        isLoading = false
    } else if (workoutId != null) {
        // Check for existing in-progress session first
        val existingSession = sessionDao.getInProgressSession()
        if (existingSession != null) {
            // Resume existing session instead of creating a new one
            sessionId = existingSession.id
            val sets = sessionDao.getSetsForSession(existingSession.id)
            val exerciseIds = sets.map { it.exerciseId }.distinct()
            for (eid in exerciseIds) {
                val exercise = exerciseDao.getById(eid) ?: continue
                val exerciseSets = sets.filter { it.exerciseId == eid }
                workoutState.addExercise(exercise, exerciseSets)
            }
            isLoading = false
            return@LaunchedEffect
        }

        // Create new session from workout (existing code, unchanged)
        val workout = workoutDao.getById(workoutId) ?: return@LaunchedEffect
        // ... rest of creation code stays the same
    }
}
```

- [ ] **Step 2: Verify build succeeds**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run all tests**

Run: `./gradlew test`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/gymlog/app/ui/workout/ActiveWorkoutScreen.kt
git commit -m "Enforce single active workout - resume existing session if one is in progress"
```

### Task 7: Restore timer state on screen resume

**Files:**
- Modify: `app/src/main/java/com/gymlog/app/ui/workout/ActiveWorkoutScreen.kt`

The `showRestTimer` derived state already handles this automatically because it reads from `RestTimerService.timerState` which is a process-level singleton. When the user swipes away and back, the Composable recomposes and collects the current StateFlow value, which still has the running timer.

- [ ] **Step 1: Verify the derived state handles recomposition correctly**

The line:
```kotlin
val showRestTimer = timerState.isRunning || timerState.remainingSeconds == 0
```

This should be refined. When the timer has completed and the 3-second auto-dismiss has passed, `timerState` resets to default `TimerState()` where both `isRunning` is false AND `remainingSeconds` is 0. We need to distinguish "timer finished, showing rest complete" from "no timer active":

```kotlin
val showRestTimer = timerState.isRunning || (timerState.sessionId != null && timerState.remainingSeconds == 0)
```

This way, the "Rest Complete" message shows only when there was an active timer that just finished (sessionId is still set), not when the timer was never started.

- [ ] **Step 2: Verify build succeeds**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/gymlog/app/ui/workout/ActiveWorkoutScreen.kt
git commit -m "Refine timer visibility to handle completed state correctly"
```

---

## Summary of what each issue gets

**Issue #18 (timer persists across navigation):** Timer state lives in `RestTimerService.timerState` (process-level StateFlow). Navigating away and back recomposes and collects current state. The foreground service keeps the countdown running even when the app is backgrounded.

**Issue #17 (single active workout):** Before creating a new `WorkoutSession`, check `getInProgressSession()`. If one exists, resume it instead.

**Issue #19 (haptics when backgrounded):** Vibration code moved from Compose LaunchedEffect to `RestTimerService.onFinish()`. The service runs in the background, so vibration fires regardless of app foreground state.

**Issue #20 (lock screen timer):** Channel importance bumped to `IMPORTANCE_DEFAULT`. Notification has `VISIBILITY_PUBLIC`. Foreground service notification naturally shows on lock screen. "Rest Complete" notification also shows on lock screen.
