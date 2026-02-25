package com.gymlog.app.ui.workout

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
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
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(templateId: Long, onFinish: () -> Unit) {
    val context = LocalContext.current
    val db = remember { GymLogDatabase.getDatabase(context) }
    val sessionDao = db.workoutSessionDao()
    val templateDao = db.workoutTemplateDao()
    val exerciseDao = db.exerciseDao()
    val scope = rememberCoroutineScope()

    var sessionId by remember { mutableStateOf<Long?>(null) }
    val workoutState = remember { ActiveWorkoutState() }
    var isLoading by remember { mutableStateOf(true) }

    // Initialize session and populate sets from template
    LaunchedEffect(templateId) {
        val template = templateDao.getById(templateId) ?: return@LaunchedEffect
        val templateExercises = templateDao.getExercisesForTemplate(templateId)

        // Create a new IN_PROGRESS session
        val newSessionId = sessionDao.insert(
            WorkoutSession(
                templateId = templateId,
                date = LocalDate.now(),
                status = SessionStatus.IN_PROGRESS,
                startedAt = Instant.now()
            )
        )
        sessionId = newSessionId

        // Create sets for each exercise based on template defaults
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

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Active Workout") })
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
        IconButton(
            onClick = { onStatusChange(SetStatus.COMPLETED) },
            modifier = Modifier.size(32.dp),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = if (status == SetStatus.COMPLETED)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Completed",
                modifier = Modifier.size(18.dp)
            )
        }
        IconButton(
            onClick = { onStatusChange(SetStatus.PARTIAL) },
            modifier = Modifier.size(32.dp),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = if (status == SetStatus.PARTIAL)
                    MaterialTheme.colorScheme.tertiary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Icon(
                Icons.Default.Remove,
                contentDescription = "Partial",
                modifier = Modifier.size(18.dp)
            )
        }
        IconButton(
            onClick = { onStatusChange(SetStatus.FAILED) },
            modifier = Modifier.size(32.dp),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = if (status == SetStatus.FAILED)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Failed",
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
