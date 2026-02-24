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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
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

data class ExerciseWithSets(
    val exercise: Exercise,
    val sets: MutableList<ExerciseSet>
)

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
    val exercisesWithSets = remember { mutableStateListOf<ExerciseWithSets>() }
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
            exercisesWithSets.add(ExerciseWithSets(exercise, sets.toMutableList()))
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
                items = exercisesWithSets,
                key = { it.exercise.id }
            ) { exerciseWithSets ->
                ExerciseCard(
                    exerciseWithSets = exerciseWithSets,
                    onSetUpdated = { index, updatedSet ->
                        exerciseWithSets.sets[index] = updatedSet
                        scope.launch { sessionDao.updateSet(updatedSet) }
                    },
                    onAddSet = {
                        scope.launch {
                            val lastSet = exerciseWithSets.sets.lastOrNull()
                            val newSet = ExerciseSet(
                                sessionId = sessionId!!,
                                exerciseId = exerciseWithSets.exercise.id,
                                setNumber = exerciseWithSets.sets.size + 1,
                                weightKg = lastSet?.weightKg,
                                repsCompleted = lastSet?.repsCompleted,
                                distanceM = lastSet?.distanceM,
                                durationSec = lastSet?.durationSec,
                                status = SetStatus.PENDING
                            )
                            val insertedId = sessionDao.insertSet(newSet)
                            exerciseWithSets.sets.add(newSet.copy(id = insertedId))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ExerciseCard(
    exerciseWithSets: ExerciseWithSets,
    onSetUpdated: (Int, ExerciseSet) -> Unit,
    onAddSet: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                exerciseWithSets.exercise.name,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            exerciseWithSets.sets.forEachIndexed { index, set ->
                if (exerciseWithSets.exercise.type == ExerciseType.WEIGHT) {
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
                if (index < exerciseWithSets.sets.size - 1) {
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
            Text(
                "${set.weightKg ?: 0.0} kg",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
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

        // Weight increment chips
        WeightIncrementChips(
            onIncrement = { inc ->
                val newWeight = (set.weightKg ?: 0.0) + inc
                onUpdate(set.copy(weightKg = newWeight))
            },
            onDecrement = { dec ->
                val newWeight = ((set.weightKg ?: 0.0) - dec).coerceAtLeast(0.0)
                onUpdate(set.copy(weightKg = newWeight))
            }
        )
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
