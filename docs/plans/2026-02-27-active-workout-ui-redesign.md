# Active Workout UI Redesign - Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Redesign the active workout screen with a compact StrongLifts-style layout, full-screen set completion modal, auto-starting rest timer bottom bar, and green/white color scheme.

**Architecture:** Replace the current expanded ExerciseCard (inline text fields + status chips per set) with compact exercise rows containing tappable set circles. Set completion moves to a full-screen dialog with large Easy/Hard/Incomplete buttons. The rest timer moves from an inline LazyColumn card to a persistent bottom bar that auto-starts on set completion.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Room

---

## Task 1: Create Green/White Color Theme

**Files:**
- Create: `app/src/main/java/com/gymlog/app/ui/theme/Color.kt`
- Create: `app/src/main/java/com/gymlog/app/ui/theme/Theme.kt`
- Modify: `app/src/main/java/com/gymlog/app/MainActivity.kt`

**Step 1: Create Color.kt**

Create `app/src/main/java/com/gymlog/app/ui/theme/Color.kt`:

```kotlin
package com.gymlog.app.ui.theme

import androidx.compose.ui.graphics.Color

// Primary - Green
val GymGreen = Color(0xFF4CAF50)
val GymGreenDark = Color(0xFF388E3C)
val GymGreenLight = Color(0xFFC8E6C9)
val OnGreen = Color.White

// Tertiary - Amber (Hard status)
val GymAmber = Color(0xFFFFA000)
val GymAmberLight = Color(0xFFFFECB3)
val OnAmber = Color.White

// Error - Red (Failed status)
val GymRed = Color(0xFFD32F2F)
val GymRedLight = Color(0xFFFFCDD2)
val OnRed = Color.White

// Neutrals
val GymBackground = Color(0xFFFAFAFA)
val GymSurface = Color.White
val GymSurfaceVariant = Color(0xFFE0E0E0)
val OnSurfaceMain = Color(0xFF1B1B1B)
val OnSurfaceVariantColor = Color(0xFF616161)
```

**Step 2: Create Theme.kt**

Create `app/src/main/java/com/gymlog/app/ui/theme/Theme.kt`:

```kotlin
package com.gymlog.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val GymLogColorScheme = lightColorScheme(
    primary = GymGreen,
    onPrimary = OnGreen,
    primaryContainer = GymGreenLight,
    onPrimaryContainer = GymGreenDark,
    secondary = GymGreenDark,
    onSecondary = OnGreen,
    tertiary = GymAmber,
    onTertiary = OnAmber,
    tertiaryContainer = GymAmberLight,
    error = GymRed,
    onError = OnRed,
    errorContainer = GymRedLight,
    background = GymBackground,
    onBackground = OnSurfaceMain,
    surface = GymSurface,
    onSurface = OnSurfaceMain,
    surfaceVariant = GymSurfaceVariant,
    onSurfaceVariant = OnSurfaceVariantColor,
)

@Composable
fun GymLogTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GymLogColorScheme,
        content = content
    )
}
```

**Step 3: Update MainActivity.kt**

In `app/src/main/java/com/gymlog/app/MainActivity.kt`, replace `MaterialTheme` with `GymLogTheme`:

Change line 12-14 from:
```kotlin
        setContent {
            MaterialTheme {
                GymLogNavigation()
```

To:
```kotlin
        setContent {
            GymLogTheme {
                GymLogNavigation()
```

Update imports: remove `androidx.compose.material3.MaterialTheme`, add `com.gymlog.app.ui.theme.GymLogTheme`.

**Step 4: Build and verify**

Run: `cd /home/chuck/devel/android/gymlog && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. The app should now render with green accents everywhere Material theme colors are used.

**Step 5: Commit**

```
git add app/src/main/java/com/gymlog/app/ui/theme/Color.kt \
       app/src/main/java/com/gymlog/app/ui/theme/Theme.kt \
       app/src/main/java/com/gymlog/app/MainActivity.kt
git commit -m "Add green/white color theme"
```

---

## Task 2: Create SetCompletionDialog Composable

**Files:**
- Modify: `app/src/main/java/com/gymlog/app/ui/workout/ActiveWorkoutScreen.kt`

This task adds the full-screen dialog composable to the file. It is not wired up yet - that happens in Task 4.

**Step 1: Add SetCompletionDialog composable**

Add the following composable to the bottom of `ActiveWorkoutScreen.kt` (replacing the old `SetStatusButtons` and `StatusChip` composables which are no longer needed):

```kotlin
private enum class ModalMode { STATUS_SELECTION, INCOMPLETE_REPS }

@Composable
private fun SetCompletionDialog(
    exerciseName: String,
    setNumber: Int,
    totalSets: Int,
    weightKg: Double?,
    repsTarget: Int?,
    onEasy: () -> Unit,
    onHard: () -> Unit,
    onIncomplete: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var mode by remember { mutableStateOf(ModalMode.STATUS_SELECTION) }
    var incompleteReps by remember { mutableIntStateOf(repsTarget ?: 0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when (mode) {
                ModalMode.STATUS_SELECTION -> {
                    StatusSelectionContent(
                        exerciseName = exerciseName,
                        setNumber = setNumber,
                        totalSets = totalSets,
                        weightKg = weightKg,
                        repsTarget = repsTarget,
                        onEasy = onEasy,
                        onHard = onHard,
                        onIncomplete = { mode = ModalMode.INCOMPLETE_REPS },
                        onDismiss = onDismiss
                    )
                }
                ModalMode.INCOMPLETE_REPS -> {
                    IncompleteRepsContent(
                        reps = incompleteReps,
                        onRepsChange = { incompleteReps = it },
                        onSave = { onIncomplete(incompleteReps) },
                        onBack = { mode = ModalMode.STATUS_SELECTION }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusSelectionContent(
    exerciseName: String,
    setNumber: Int,
    totalSets: Int,
    weightKg: Double?,
    repsTarget: Int?,
    onEasy: () -> Unit,
    onHard: () -> Unit,
    onIncomplete: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Dismiss",
                modifier = Modifier.size(32.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Set $setNumber of $totalSets",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(8.dp))

            val detailParts = mutableListOf(exerciseName)
            if (weightKg != null) {
                val w = if (weightKg == weightKg.toLong().toDouble())
                    weightKg.toLong().toString() else weightKg.toString()
                detailParts.add("$w kg")
            }
            if (repsTarget != null) {
                detailParts.add("x $repsTarget")
            }
            Text(
                text = detailParts.joinToString(" - "),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Easy button
            Button(
                onClick = onEasy,
                modifier = Modifier.fillMaxWidth().height(80.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.ThumbUp, contentDescription = null, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text("Easy", style = MaterialTheme.typography.headlineSmall)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Hard button
            Button(
                onClick = onHard,
                modifier = Modifier.fillMaxWidth().height(80.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                Icon(Icons.Default.Whatshot, contentDescription = null, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text("Hard", style = MaterialTheme.typography.headlineSmall)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Incomplete button
            OutlinedButton(
                onClick = onIncomplete,
                modifier = Modifier.fillMaxWidth().height(80.dp)
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text("Incomplete", style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

@Composable
private fun IncompleteRepsContent(
    reps: Int,
    onRepsChange: (Int) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "How many reps completed?",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            FilledIconButton(
                onClick = { onRepsChange((reps - 1).coerceAtLeast(0)) },
                modifier = Modifier.size(64.dp)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease reps", modifier = Modifier.size(32.dp))
            }
            Text(
                text = "$reps",
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.width(80.dp),
                textAlign = TextAlign.Center
            )
            FilledIconButton(
                onClick = { onRepsChange(reps + 1) },
                modifier = Modifier.size(64.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Increase reps", modifier = Modifier.size(32.dp))
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth().height(64.dp)
        ) {
            Text("Save", style = MaterialTheme.typography.titleLarge)
        }
    }
}
```

Required new imports to add at top of file:
```kotlin
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Dialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.DialogProperties
```

**Step 2: Build to verify compilation**

Run: `cd /home/chuck/devel/android/gymlog && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```
git add app/src/main/java/com/gymlog/app/ui/workout/ActiveWorkoutScreen.kt
git commit -m "Add full-screen set completion dialog composables"
```

---

## Task 3: Rewrite ExerciseCard with Compact Layout and Set Circles

**Files:**
- Modify: `app/src/main/java/com/gymlog/app/ui/workout/ActiveWorkoutScreen.kt`

Replace the current `ExerciseCard` composable (lines 394-487) and add a new `SetCircle` composable.

**Step 1: Replace ExerciseCard**

Replace the existing `ExerciseCard` composable with:

```kotlin
@Composable
private fun ExerciseCard(
    exercise: Exercise,
    sets: List<ExerciseSet>,
    onSetTapped: (Int) -> Unit,
    onWeightChangedForAll: (Double) -> Unit,
    onAddSet: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header row: name + summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    exercise.displayName(),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (exercise.type == ExerciseType.WEIGHT) {
                    val weight = sets.firstOrNull()?.weightKg ?: 0.0
                    val reps = sets.firstOrNull()?.repsCompleted ?: 0
                    val displayWeight = if (weight == weight.toLong().toDouble())
                        weight.toLong().toString() else weight.toString()
                    Text(
                        "${sets.size}x$reps  $displayWeight kg",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Weight controls for weight exercises
            if (exercise.type == ExerciseType.WEIGHT) {
                val currentWeight = sets.firstOrNull()?.weightKg ?: 0.0
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    IconButton(
                        onClick = {
                            val newWeight = (currentWeight - 2.5).coerceAtLeast(0.0)
                            onWeightChangedForAll(newWeight)
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease weight", modifier = Modifier.size(20.dp))
                    }
                    val displayWeight = if (currentWeight == currentWeight.toLong().toDouble())
                        currentWeight.toLong().toString() else currentWeight.toString()
                    Text(
                        "$displayWeight kg",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = {
                            val newWeight = currentWeight + 2.5
                            onWeightChangedForAll(newWeight)
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase weight", modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Set circles row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                sets.forEachIndexed { index, set ->
                    SetCircle(
                        set = set,
                        onClick = { onSetTapped(index) }
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onAddSet) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add")
                }
            }
        }
    }
}

@Composable
private fun SetCircle(set: ExerciseSet, onClick: () -> Unit) {
    val backgroundColor = when (set.status) {
        SetStatus.PENDING -> Color.Transparent
        SetStatus.EASY -> MaterialTheme.colorScheme.primary
        SetStatus.HARD -> MaterialTheme.colorScheme.tertiary
        SetStatus.PARTIAL -> MaterialTheme.colorScheme.tertiary
        SetStatus.FAILED -> MaterialTheme.colorScheme.error
    }
    val contentColor = when (set.status) {
        SetStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
        SetStatus.EASY -> MaterialTheme.colorScheme.onPrimary
        SetStatus.HARD -> MaterialTheme.colorScheme.onTertiary
        SetStatus.PARTIAL -> MaterialTheme.colorScheme.onTertiary
        SetStatus.FAILED -> MaterialTheme.colorScheme.onError
    }
    val borderColor = when (set.status) {
        SetStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> Color.Transparent
    }

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = backgroundColor,
        contentColor = contentColor,
        border = if (set.status == SetStatus.PENDING)
            BorderStroke(2.dp, borderColor) else null,
        modifier = Modifier.size(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "${set.repsCompleted ?: 0}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
```

Required new imports:
```kotlin
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
```

**Step 2: Remove old composables**

Delete these composables that are no longer used:
- `WeightSetRow` (old lines 489-624)
- `SetStatusButtons` (old lines 741-769)
- `StatusChip` (old lines 771-798)

Keep `NewCardioSetRow` and `CardioSetRow` for cardio exercises. For cardio exercises, the ExerciseCard will still call these inline.

**Step 3: Build to verify**

Run: `cd /home/chuck/devel/android/gymlog && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```
git add app/src/main/java/com/gymlog/app/ui/workout/ActiveWorkoutScreen.kt
git commit -m "Replace expanded set rows with compact card and set circles"
```

---

## Task 4: Rewrite RestTimerBar as Bottom Bar with Auto-Start

**Files:**
- Modify: `app/src/main/java/com/gymlog/app/ui/workout/ActiveWorkoutScreen.kt`

**Step 1: Replace RestTimerBar composable**

Replace the old `RestTimerBar` composable with a new version that includes a progress bar:

```kotlin
@Composable
private fun RestTimerBottomBar(
    remainingSeconds: Int,
    totalSeconds: Int,
    timerRunning: Boolean,
    onExtend: () -> Unit,
    onDismiss: () -> Unit
) {
    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val timeText = "%d:%02d".format(minutes, seconds)
    val totalMinutes = totalSeconds / 60
    val totalSeconds2 = totalSeconds % 60
    val totalText = "%d:%02d".format(totalMinutes, totalSeconds2)
    val progress = if (totalSeconds > 0) remainingSeconds.toFloat() / totalSeconds else 0f

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 3.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (remainingSeconds > 0) {
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Rest $totalText",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onExtend) {
                        Text("+1:30")
                    }
                } else {
                    Text(
                        text = "Rest Complete",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Dismiss timer",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            if (remainingSeconds > 0) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer,
                )
            }
        }
    }
}
```

Required new import:
```kotlin
import androidx.compose.material3.LinearProgressIndicator
```

**Step 2: Update timer state in ActiveWorkoutScreen**

Add a `totalRestSeconds` state variable alongside existing timer state (around line 104):

```kotlin
var totalRestSeconds by remember { mutableIntStateOf(90) }
```

**Step 3: Move timer from LazyColumn to Scaffold bottomBar**

In the `Scaffold` composable, change `bottomBar` from the simple "Finish Workout" button to a Column that stacks the rest timer above the finish button:

Replace the current `bottomBar` block (lines 261-288) with:

```kotlin
        bottomBar = {
            Column {
                if (showRestTimer) {
                    RestTimerBottomBar(
                        remainingSeconds = remainingSeconds,
                        totalSeconds = totalRestSeconds,
                        timerRunning = timerRunning,
                        onExtend = {
                            remainingSeconds += 90
                            totalRestSeconds += 90
                        },
                        onDismiss = {
                            timerRunning = false
                            showRestTimer = false
                        }
                    )
                }
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = {
                            scope.launch {
                                sessionId?.let { sid ->
                                    val session = sessionDao.getById(sid)
                                    if (session != null) {
                                        sessionDao.update(
                                            session.copy(
                                                status = SessionStatus.COMPLETED,
                                                completedAt = Instant.now()
                                            )
                                        )
                                    }
                                }
                                onFinish()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        enabled = sessionId != null
                    ) {
                        Text("Finish Workout")
                    }
                }
            }
        }
```

Remove the old inline rest timer from the LazyColumn (the `if (showRestTimer) { item(key = "rest_timer") { ... } }` block around lines 307-320).

**Step 4: Auto-start the timer**

Change line 336 from `timerRunning = false` to `timerRunning = true`. Also set totalRestSeconds:

```kotlin
if (updatedSet.status != SetStatus.PENDING) {
    remainingSeconds = 90
    totalRestSeconds = 90
    showRestTimer = true
    timerRunning = true
}
```

**Step 5: Build to verify**

Run: `cd /home/chuck/devel/android/gymlog && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```
git add app/src/main/java/com/gymlog/app/ui/workout/ActiveWorkoutScreen.kt
git commit -m "Move rest timer to bottom bar with auto-start and progress"
```

---

## Task 5: Wire Up Modal, Update ExerciseCard Callbacks, Handle Cardio

**Files:**
- Modify: `app/src/main/java/com/gymlog/app/ui/workout/ActiveWorkoutScreen.kt`

This task wires the set circle taps to the SetCompletionDialog and handles the cardio exercise case.

**Step 1: Add modal state variables**

Add these state variables in `ActiveWorkoutScreen` (after the timer state vars, around line 105):

```kotlin
// Modal state
var selectedExerciseId by remember { mutableStateOf<Long?>(null) }
var selectedSetIndex by remember { mutableIntStateOf(0) }
var showSetModal by remember { mutableStateOf(false) }
```

**Step 2: Update ExerciseCard call in LazyColumn**

Replace the current `items` block in the LazyColumn (around lines 321-389) with the new version that uses `onSetTapped` instead of `onSetUpdated`:

```kotlin
            items(
                items = workoutState.exercises,
                key = { it.id }
            ) { exercise ->
                val sets = workoutState.getExerciseSets(exercise.id)
                    ?: return@items

                if (exercise.type == ExerciseType.WEIGHT) {
                    ExerciseCard(
                        exercise = exercise,
                        sets = sets,
                        onSetTapped = { setIndex ->
                            selectedExerciseId = exercise.id
                            selectedSetIndex = setIndex
                            showSetModal = true
                        },
                        onWeightChangedForAll = { newWeight ->
                            val currentSets = workoutState.getExerciseSets(exercise.id) ?: return@ExerciseCard
                            scope.launch {
                                currentSets.forEachIndexed { index, s ->
                                    val updated = s.copy(weightKg = newWeight)
                                    workoutState.updateSet(exercise.id, index, updated)
                                    sessionDao.updateSet(updated)
                                }
                            }
                        },
                        onAddSet = {
                            scope.launch {
                                val currentSets = workoutState.getExerciseSetsCopy(exercise.id)
                                val lastSet = currentSets.lastOrNull()
                                val newSet = ExerciseSet(
                                    sessionId = sessionId!!,
                                    exerciseId = exercise.id,
                                    setNumber = currentSets.size + 1,
                                    weightKg = lastSet?.weightKg,
                                    repsCompleted = lastSet?.repsCompleted,
                                    status = SetStatus.PENDING
                                )
                                val insertedId = sessionDao.insertSet(newSet)
                                workoutState.addSet(exercise.id, newSet.copy(id = insertedId))
                            }
                        }
                    )
                } else {
                    // Cardio exercises keep inline editing
                    CardioExerciseCard(
                        exercise = exercise,
                        sets = sets,
                        onSetUpdated = { setIndex, updatedSet ->
                            workoutState.updateSet(exercise.id, setIndex, updatedSet)
                            scope.launch { sessionDao.updateSet(updatedSet) }
                            if (updatedSet.status != SetStatus.PENDING) {
                                remainingSeconds = 90
                                totalRestSeconds = 90
                                showRestTimer = true
                                timerRunning = true
                            }
                        },
                        onAddSet = {
                            scope.launch {
                                val currentSets = workoutState.getExerciseSetsCopy(exercise.id)
                                val lastSet = currentSets.lastOrNull()
                                val newSet = if (exercise.cardioFixedDimension != null) {
                                    when (exercise.cardioFixedDimension) {
                                        CardioFixedDimension.DISTANCE -> ExerciseSet(
                                            sessionId = sessionId!!,
                                            exerciseId = exercise.id,
                                            setNumber = currentSets.size + 1,
                                            durationSec = lastSet?.durationSec,
                                            status = SetStatus.PENDING
                                        )
                                        CardioFixedDimension.TIME -> ExerciseSet(
                                            sessionId = sessionId!!,
                                            exerciseId = exercise.id,
                                            setNumber = currentSets.size + 1,
                                            distanceM = lastSet?.distanceM,
                                            status = SetStatus.PENDING
                                        )
                                    }
                                } else {
                                    ExerciseSet(
                                        sessionId = sessionId!!,
                                        exerciseId = exercise.id,
                                        setNumber = currentSets.size + 1,
                                        distanceM = lastSet?.distanceM,
                                        durationSec = lastSet?.durationSec,
                                        status = SetStatus.PENDING
                                    )
                                }
                                val insertedId = sessionDao.insertSet(newSet)
                                workoutState.addSet(exercise.id, newSet.copy(id = insertedId))
                            }
                        }
                    )
                }
            }
```

**Step 3: Add CardioExerciseCard composable**

Add a simple card for cardio exercises that keeps the existing inline editing pattern:

```kotlin
@Composable
private fun CardioExerciseCard(
    exercise: Exercise,
    sets: List<ExerciseSet>,
    onSetUpdated: (Int, ExerciseSet) -> Unit,
    onAddSet: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                exercise.displayName(),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            sets.forEachIndexed { index, set ->
                if (exercise.cardioFixedDimension != null) {
                    NewCardioSetRow(
                        set = set,
                        setIndex = index + 1,
                        fixedDimension = exercise.cardioFixedDimension,
                        onUpdate = { updated -> onSetUpdated(index, updated) }
                    )
                } else {
                    CardioSetRow(
                        set = set,
                        setIndex = index + 1,
                        onUpdate = { updated -> onSetUpdated(index, updated) }
                    )
                }
                if (index < sets.size - 1) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }
            }

            TextButton(onClick = onAddSet) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Set")
            }
        }
    }
}
```

**Step 4: Add the dialog invocation**

Inside the `ActiveWorkoutScreen` composable, after the `Scaffold` block but before the closing `}` of the function (or just before the Scaffold), add the modal:

```kotlin
    // Set completion modal
    if (showSetModal && selectedExerciseId != null) {
        val exercise = workoutState.exercises.find { it.id == selectedExerciseId }
        val sets = selectedExerciseId?.let { workoutState.getExerciseSets(it) }
        val set = sets?.getOrNull(selectedSetIndex)
        if (exercise != null && set != null) {
            SetCompletionDialog(
                exerciseName = exercise.displayName(),
                setNumber = selectedSetIndex + 1,
                totalSets = sets.size,
                weightKg = set.weightKg,
                repsTarget = set.repsCompleted,
                onEasy = {
                    val updated = set.copy(status = SetStatus.EASY)
                    workoutState.updateSet(exercise.id, selectedSetIndex, updated)
                    scope.launch { sessionDao.updateSet(updated) }
                    showSetModal = false
                    remainingSeconds = 90
                    totalRestSeconds = 90
                    showRestTimer = true
                    timerRunning = true
                },
                onHard = {
                    val updated = set.copy(status = SetStatus.HARD)
                    workoutState.updateSet(exercise.id, selectedSetIndex, updated)
                    scope.launch { sessionDao.updateSet(updated) }
                    showSetModal = false
                    remainingSeconds = 90
                    totalRestSeconds = 90
                    showRestTimer = true
                    timerRunning = true
                },
                onIncomplete = { reps ->
                    val status = if (reps == 0) SetStatus.FAILED else SetStatus.PARTIAL
                    val updated = set.copy(status = status, repsCompleted = reps)
                    workoutState.updateSet(exercise.id, selectedSetIndex, updated)
                    scope.launch { sessionDao.updateSet(updated) }
                    showSetModal = false
                    remainingSeconds = 90
                    totalRestSeconds = 90
                    showRestTimer = true
                    timerRunning = true
                },
                onDismiss = { showSetModal = false }
            )
        }
    }
```

**Step 5: Build to verify**

Run: `cd /home/chuck/devel/android/gymlog && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```
git add app/src/main/java/com/gymlog/app/ui/workout/ActiveWorkoutScreen.kt
git commit -m "Wire set circles to completion modal, add cardio card"
```

---

## Task 6: Clean Up and Test on Device

**Files:**
- Modify: `app/src/main/java/com/gymlog/app/ui/workout/ActiveWorkoutScreen.kt` (cleanup)

**Step 1: Remove unused imports**

Review and remove any imports that are no longer referenced after removing WeightSetRow, SetStatusButtons, and StatusChip.

**Step 2: Build and install on device**

Run: `cd /home/chuck/devel/android/gymlog && ./gradlew installDebug`
Expected: App installs and launches with green theme.

**Step 3: Manual verification checklist**

Test each of these on device:
1. Green theme is visible throughout the app (calendar, workouts, exercises screens)
2. Start a new workout - exercises show compact cards with set circles
3. Tap a set circle - full-screen dialog appears with Easy/Hard/Incomplete
4. Tap "Easy" - circle turns green, dialog dismisses, rest timer auto-starts at bottom
5. Tap "Hard" - circle turns amber
6. Tap "Incomplete" - shows rep entry screen with +/- buttons
7. Enter reps and save - circle turns amber (PARTIAL) or red (FAILED if 0)
8. Rest timer shows countdown with progress bar
9. "+1:30" extends the timer
10. X dismisses the timer
11. Timer vibrates when reaching zero
12. "Finish Workout" button works below the timer
13. Weight +/- 2.5kg controls work on exercise card
14. "Add" button adds a new set circle
15. Cardio exercises still show inline editing

**Step 4: Final commit**

```
git add -A
git commit -m "Clean up imports and verify UI redesign"
```

---

## Task 7: Run Tests

**Step 1: Run existing tests**

Run: `cd /home/chuck/devel/android/gymlog && ./gradlew test`
Expected: All tests pass. The WeightSuggestion tests and ActiveWorkoutState tests should be unaffected since we didn't change those files.
