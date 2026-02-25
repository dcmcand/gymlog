package com.gymlog.app.ui.calendar

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gymlog.app.data.Exercise
import com.gymlog.app.data.ExerciseSet
import com.gymlog.app.data.ExerciseType
import com.gymlog.app.data.GymLogDatabase
import com.gymlog.app.data.SetStatus
import com.gymlog.app.data.WorkoutSession

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(sessionId: Long, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { GymLogDatabase.getDatabase(context) }
    val sessionDao = db.workoutSessionDao()
    val exerciseDao = db.exerciseDao()
    val templateDao = db.workoutTemplateDao()

    var session by remember { mutableStateOf<WorkoutSession?>(null) }
    var templateName by remember { mutableStateOf<String?>(null) }
    var exerciseGroups by remember {
        mutableStateOf<List<Pair<Exercise, List<ExerciseSet>>>>(emptyList())
    }

    LaunchedEffect(sessionId) {
        val s = sessionDao.getById(sessionId) ?: return@LaunchedEffect
        session = s
        templateName = s.templateId?.let { templateDao.getById(it)?.name }

        val sets = sessionDao.getSetsForSession(sessionId)
        val grouped = groupSetsByExercise(sets)
        exerciseGroups = grouped.mapNotNull { (exerciseId, exerciseSets) ->
            val exercise = exerciseDao.getById(exerciseId)
            exercise?.let { it to exerciseSets }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workout Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        val currentSession = session
        if (currentSession == null) return@Scaffold

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            item {
                Column {
                    Text(
                        text = templateName ?: "Workout",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = currentSession.date.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = currentSession.status.name.lowercase().replace('_', ' '),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Exercise cards
            items(exerciseGroups) { (exercise, sets) ->
                ExerciseDetailCard(exercise = exercise, sets = sets)
            }
        }
    }
}

@Composable
private fun ExerciseDetailCard(exercise: Exercise, sets: List<ExerciseSet>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Column headers
            if (exercise.type == ExerciseType.WEIGHT) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Set",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(36.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Weight",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Reps",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(48.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Status",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(32.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Set",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(36.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Distance",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Duration",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Status",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(32.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider()
            Spacer(modifier = Modifier.height(4.dp))

            sets.forEach { set ->
                if (exercise.type == ExerciseType.WEIGHT) {
                    WeightSetDetailRow(set)
                } else {
                    CardioSetDetailRow(set)
                }
            }
        }
    }
}

@Composable
private fun WeightSetDetailRow(set: ExerciseSet) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${set.setNumber}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(36.dp)
        )
        Text(
            "${set.weightKg ?: "-"} kg",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            "${set.repsCompleted ?: "-"}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(48.dp)
        )
        SetStatusIcon(
            status = set.status,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun CardioSetDetailRow(set: ExerciseSet) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${set.setNumber}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(36.dp)
        )
        Text(
            "${set.distanceM ?: "-"} m",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            formatDuration(set.durationSec),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        SetStatusIcon(
            status = set.status,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SetStatusIcon(status: SetStatus, modifier: Modifier = Modifier) {
    when (status) {
        SetStatus.COMPLETED -> Icon(
            Icons.Default.Check,
            contentDescription = "Completed",
            tint = MaterialTheme.colorScheme.primary,
            modifier = modifier
        )
        SetStatus.PARTIAL -> Icon(
            Icons.Default.Remove,
            contentDescription = "Partial",
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = modifier
        )
        SetStatus.FAILED -> Icon(
            Icons.Default.Close,
            contentDescription = "Failed",
            tint = MaterialTheme.colorScheme.error,
            modifier = modifier
        )
        SetStatus.PENDING -> Icon(
            Icons.Default.Remove,
            contentDescription = "Pending",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
        )
    }
}

internal fun formatDuration(seconds: Int?): String {
    if (seconds == null) return "-"
    val min = seconds / 60
    val sec = seconds % 60
    return "${min}m ${sec}s"
}
