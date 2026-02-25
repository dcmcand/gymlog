package com.gymlog.app.ui.workout

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.gymlog.app.data.Exercise
import com.gymlog.app.data.ExerciseSet
import com.gymlog.app.data.ExerciseType
import com.gymlog.app.data.GymLogDatabase
import com.gymlog.app.data.SessionStatus
import com.gymlog.app.data.SetStatus
import com.gymlog.app.data.WorkoutSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    templateId: Long? = null,
    resumeSessionId: Long? = null,
    onFinish: () -> Unit,
    onDelete: () -> Unit = onFinish
) {
    val context = LocalContext.current
    val db = remember { GymLogDatabase.getDatabase(context) }
    val sessionDao = db.workoutSessionDao()
    val templateDao = db.workoutTemplateDao()
    val exerciseDao = db.exerciseDao()
    val scope = rememberCoroutineScope()

    var sessionId by remember { mutableStateOf<Long?>(null) }
    val workoutState = remember { ActiveWorkoutState() }
    var isLoading by remember { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Rest timer state
    var showRestTimer by remember { mutableStateOf(false) }
    var timerRunning by remember { mutableStateOf(false) }
    var remainingSeconds by remember { mutableIntStateOf(90) }

    // Countdown effect
    LaunchedEffect(timerRunning) {
        if (timerRunning) {
            while (remainingSeconds > 0) {
                delay(1000L)
                remainingSeconds--
            }
            // Vibrate when timer reaches zero
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(VibratorManager::class.java)
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Vibrator::class.java)
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            }
            // Brief "Rest Complete" display, then auto-hide
            delay(2000L)
            timerRunning = false
            showRestTimer = false
        }
    }

    // Initialize: either resume existing session or create new from template
    LaunchedEffect(templateId, resumeSessionId) {
        if (resumeSessionId != null) {
            // Resume existing session
            sessionId = resumeSessionId
            val sets = sessionDao.getSetsForSession(resumeSessionId)
            val exerciseIds = sets.map { it.exerciseId }.distinct()
            for (eid in exerciseIds) {
                val exercise = exerciseDao.getById(eid) ?: continue
                val exerciseSets = sets.filter { it.exerciseId == eid }
                workoutState.addExercise(exercise, exerciseSets)
            }
            isLoading = false
        } else if (templateId != null) {
            // Create new session from template
            val template = templateDao.getById(templateId) ?: return@LaunchedEffect
            val templateExercises = templateDao.getExercisesForTemplate(templateId)

            val newSessionId = sessionDao.insert(
                WorkoutSession(
                    templateId = templateId,
                    date = LocalDate.now(),
                    status = SessionStatus.IN_PROGRESS,
                    startedAt = Instant.now()
                )
            )
            sessionId = newSessionId

            for (te in templateExercises) {
                val exercise = exerciseDao.getById(te.exerciseId) ?: continue
                val lastSet = sessionDao.getLastCompletedSet(te.exerciseId)

                val sets = mutableListOf<ExerciseSet>()
                for (setNum in 1..te.targetSets) {
                    val set = if (exercise.type == ExerciseType.WEIGHT) {
                        ExerciseSet(
                            sessionId = newSessionId,
                            exerciseId = exercise.id,
                            setNumber = setNum,
                            weightKg = lastSet?.weightKg ?: te.targetWeightKg,
                            repsCompleted = te.targetReps,
                            status = SetStatus.PENDING
                        )
                    } else {
                        ExerciseSet(
                            sessionId = newSessionId,
                            exerciseId = exercise.id,
                            setNumber = setNum,
                            distanceM = lastSet?.distanceM ?: te.targetDistanceM,
                            durationSec = lastSet?.durationSec ?: te.targetDurationSec,
                            status = SetStatus.PENDING
                        )
                    }
                    val insertedId = sessionDao.insertSet(set)
                    sets.add(set.copy(id = insertedId))
                }
                workoutState.addExercise(exercise, sets)
            }
            isLoading = false
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete workout?") },
            text = { Text("This will permanently delete this workout and all its sets.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    scope.launch {
                        sessionId?.let { sessionDao.deleteById(it) }
                        onDelete()
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Active Workout") },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete workout")
                    }
                }
            )
        },
        bottomBar = {
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
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showRestTimer) {
                item(key = "rest_timer") {
                    RestTimerBar(
                        remainingSeconds = remainingSeconds,
                        timerRunning = timerRunning,
                        onStart = { timerRunning = true },
                        onExtend = { remainingSeconds += 90 },
                        onDismiss = {
                            timerRunning = false
                            showRestTimer = false
                        }
                    )
                }
            }
            items(
                items = workoutState.exercises,
                key = { it.id }
            ) { exercise ->
                val sets = workoutState.getExerciseSets(exercise.id)
                    ?: return@items
                ExerciseCard(
                    exercise = exercise,
                    sets = sets,
                    onSetUpdated = { setIndex, updatedSet ->
                        workoutState.updateSet(exercise.id, setIndex, updatedSet)
                        scope.launch { sessionDao.updateSet(updatedSet) }
                        if (updatedSet.status != SetStatus.PENDING) {
                            remainingSeconds = 90
                            showRestTimer = true
                            timerRunning = false
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
                                distanceM = lastSet?.distanceM,
                                durationSec = lastSet?.durationSec,
                                status = SetStatus.PENDING
                            )
                            val insertedId = sessionDao.insertSet(newSet)
                            workoutState.addSet(exercise.id, newSet.copy(id = insertedId))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ExerciseCard(
    exercise: Exercise,
    sets: List<ExerciseSet>,
    onSetUpdated: (Int, ExerciseSet) -> Unit,
    onAddSet: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                exercise.name,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            sets.forEachIndexed { index, set ->
                if (exercise.type == ExerciseType.WEIGHT) {
                    WeightSetRow(
                        set = set,
                        setIndex = index + 1,
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

@Composable
private fun WeightSetRow(set: ExerciseSet, setIndex: Int, onUpdate: (ExerciseSet) -> Unit) {
    val weightText = set.weightKg?.let {
        if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
    } ?: ""
    var weightFieldValue by remember(set.weightKg) {
        mutableStateOf(TextFieldValue(weightText))
    }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Set $setIndex",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.width(40.dp)
            )
            OutlinedTextField(
                value = weightFieldValue,
                onValueChange = { newValue ->
                    weightFieldValue = newValue
                    val parsed = newValue.text.toDoubleOrNull()
                    if (parsed != null || newValue.text.isEmpty()) {
                        onUpdate(set.copy(weightKg = parsed))
                    }
                },
                label = { Text("kg") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            weightFieldValue = weightFieldValue.copy(
                                selection = TextRange(0, weightFieldValue.text.length)
                            )
                        }
                    },
                singleLine = true
            )
            // Reps with +/- buttons
            IconButton(
                onClick = {
                    val newReps = ((set.repsCompleted ?: 1) - 1).coerceAtLeast(0)
                    onUpdate(set.copy(repsCompleted = newReps))
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = "Decrease reps",
                    modifier = Modifier.size(16.dp)
                )
            }
            Text("${set.repsCompleted ?: 0}", style = MaterialTheme.typography.bodyLarge)
            IconButton(
                onClick = {
                    val newReps = (set.repsCompleted ?: 0) + 1
                    onUpdate(set.copy(repsCompleted = newReps))
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Increase reps",
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            // Status buttons
            SetStatusButtons(
                status = set.status,
                onStatusChange = { status -> onUpdate(set.copy(status = status)) }
            )
        }

        // Weight +/- 2.5 kg buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 40.dp, top = 4.dp)
        ) {
            IconButton(
                onClick = {
                    val newWeight = ((set.weightKg ?: 0.0) - 2.5).coerceAtLeast(0.0)
                    onUpdate(set.copy(weightKg = newWeight))
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = "Decrease weight by 2.5 kg",
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                "2.5 kg",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
            IconButton(
                onClick = {
                    val newWeight = (set.weightKg ?: 0.0) + 2.5
                    onUpdate(set.copy(weightKg = newWeight))
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Increase weight by 2.5 kg",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun CardioSetRow(set: ExerciseSet, setIndex: Int, onUpdate: (ExerciseSet) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            "Set $setIndex",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(40.dp)
        )
        OutlinedTextField(
            value = set.distanceM?.toString() ?: "",
            onValueChange = { onUpdate(set.copy(distanceM = it.toIntOrNull())) },
            label = { Text("m") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        OutlinedTextField(
            value = set.durationSec?.toString() ?: "",
            onValueChange = { onUpdate(set.copy(durationSec = it.toIntOrNull())) },
            label = { Text("sec") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        SetStatusButtons(
            status = set.status,
            onStatusChange = { status -> onUpdate(set.copy(status = status)) }
        )
    }
}

@Composable
private fun SetStatusButtons(status: SetStatus, onStatusChange: (SetStatus) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        StatusChip(
            selected = status == SetStatus.EASY,
            onClick = { onStatusChange(SetStatus.EASY) },
            icon = Icons.Default.ThumbUp,
            label = "Easy",
            selectedColor = MaterialTheme.colorScheme.primary,
            selectedContentColor = MaterialTheme.colorScheme.onPrimary
        )
        StatusChip(
            selected = status == SetStatus.HARD,
            onClick = { onStatusChange(SetStatus.HARD) },
            icon = Icons.Default.Whatshot,
            label = "Hard",
            selectedColor = MaterialTheme.colorScheme.tertiary,
            selectedContentColor = MaterialTheme.colorScheme.onTertiary
        )
        StatusChip(
            selected = status == SetStatus.PARTIAL,
            onClick = { onStatusChange(SetStatus.PARTIAL) },
            icon = Icons.Default.Remove,
            label = "Partial",
            selectedColor = MaterialTheme.colorScheme.secondary,
            selectedContentColor = MaterialTheme.colorScheme.onSecondary
        )
        StatusChip(
            selected = status == SetStatus.FAILED,
            onClick = { onStatusChange(SetStatus.FAILED) },
            icon = Icons.Default.Close,
            label = "Fail",
            selectedColor = MaterialTheme.colorScheme.error,
            selectedContentColor = MaterialTheme.colorScheme.onError
        )
    }
}

@Composable
private fun StatusChip(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    selectedColor: Color,
    selectedContentColor: Color
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (selected) selectedColor else Color.Transparent,
        contentColor = if (selected) selectedContentColor
            else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(14.dp))
            if (selected) {
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun RestTimerBar(
    remainingSeconds: Int,
    timerRunning: Boolean,
    onStart: () -> Unit,
    onExtend: () -> Unit,
    onDismiss: () -> Unit
) {
    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val timeText = "%d:%02d".format(minutes, seconds)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!timerRunning && remainingSeconds > 0) {
                Button(onClick = onStart) {
                    Text("Rest $timeText")
                }
            } else if (remainingSeconds > 0) {
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.width(12.dp))
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
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Dismiss timer",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
