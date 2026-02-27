package com.gymlog.app.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gymlog.app.data.CardioFixedDimension
import com.gymlog.app.data.Exercise
import com.gymlog.app.data.ExerciseSet
import com.gymlog.app.data.ExerciseType
import com.gymlog.app.data.SetStatus
import com.gymlog.app.data.displayName
import com.gymlog.app.ui.common.formatMMSS
import com.gymlog.app.ui.common.parseMMSS

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompactExerciseCard(
    exercise: Exercise,
    sets: List<ExerciseSet>,
    onSetTapped: (Int, ExerciseSet) -> Unit,
    onWeightChangedForAll: (Double) -> Unit,
    onSetUpdated: (Int, ExerciseSet) -> Unit,
    onAddSet: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (exercise.type == ExerciseType.WEIGHT) {
                WeightExerciseContent(
                    exercise = exercise,
                    sets = sets,
                    onSetTapped = onSetTapped,
                    onWeightChangedForAll = onWeightChangedForAll,
                    onAddSet = onAddSet
                )
            } else if (exercise.cardioFixedDimension != null) {
                NewCardioExerciseContent(
                    exercise = exercise,
                    sets = sets,
                    fixedDimension = exercise.cardioFixedDimension,
                    onSetUpdated = onSetUpdated,
                    onAddSet = onAddSet
                )
            } else {
                LegacyCardioExerciseContent(
                    exercise = exercise,
                    sets = sets,
                    onSetUpdated = onSetUpdated,
                    onAddSet = onAddSet
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeightExerciseContent(
    exercise: Exercise,
    sets: List<ExerciseSet>,
    onSetTapped: (Int, ExerciseSet) -> Unit,
    onWeightChangedForAll: (Double) -> Unit,
    onAddSet: () -> Unit
) {
    val currentWeight = sets.firstOrNull()?.weightKg ?: 0.0
    val targetReps = sets.firstOrNull()?.repsCompleted ?: 0
    val displayWeight = if (currentWeight == currentWeight.toLong().toDouble()) {
        currentWeight.toLong().toString()
    } else {
        currentWeight.toString()
    }

    // Row 1: exercise name + summary
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            exercise.displayName(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "${sets.size}x$targetReps ${displayWeight}kg",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Spacer(modifier = Modifier.height(4.dp))

    // Row 2: weight adjustment
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
        modifier = Modifier.fillMaxWidth()
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
        Text(
            "${displayWeight}kg",
            style = MaterialTheme.typography.bodyLarge,
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

    Spacer(modifier = Modifier.height(8.dp))

    // Row 3: set circles + add set
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            sets.forEachIndexed { index, set ->
                SetCircleIndicator(
                    setNumber = index + 1,
                    status = set.status,
                    repsCompleted = set.repsCompleted,
                    onClick = { onSetTapped(index, set) }
                )
            }
        }
        TextButton(onClick = onAddSet) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add Set")
        }
    }
}

@Composable
private fun NewCardioExerciseContent(
    exercise: Exercise,
    sets: List<ExerciseSet>,
    fixedDimension: CardioFixedDimension,
    onSetUpdated: (Int, ExerciseSet) -> Unit,
    onAddSet: () -> Unit
) {
    Text(
        exercise.displayName(),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(8.dp))

    sets.forEachIndexed { index, set ->
        NewCardioSetRow(
            set = set,
            setIndex = index + 1,
            fixedDimension = fixedDimension,
            onUpdate = { updated -> onSetUpdated(index, updated) }
        )
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

@Composable
private fun LegacyCardioExerciseContent(
    exercise: Exercise,
    sets: List<ExerciseSet>,
    onSetUpdated: (Int, ExerciseSet) -> Unit,
    onAddSet: () -> Unit
) {
    Text(
        exercise.displayName(),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(8.dp))

    sets.forEachIndexed { index, set ->
        LegacyCardioSetRow(
            set = set,
            setIndex = index + 1,
            onUpdate = { updated -> onSetUpdated(index, updated) }
        )
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

@Composable
private fun NewCardioSetRow(
    set: ExerciseSet,
    setIndex: Int,
    fixedDimension: CardioFixedDimension,
    onUpdate: (ExerciseSet) -> Unit
) {
    var timeText by remember { mutableStateOf(formatMMSS(set.durationSec)) }
    var distanceText by remember { mutableStateOf(set.distanceM?.toString() ?: "") }

    LaunchedEffect(set.durationSec) {
        val newFormatted = formatMMSS(set.durationSec)
        if (newFormatted != timeText) timeText = newFormatted
    }
    LaunchedEffect(set.distanceM) {
        val newText = set.distanceM?.toString() ?: ""
        if (newText != distanceText) distanceText = newText
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            "$setIndex",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(24.dp)
        )
        when (fixedDimension) {
            CardioFixedDimension.DISTANCE -> {
                OutlinedTextField(
                    value = timeText,
                    onValueChange = { newValue ->
                        timeText = newValue
                        val parsed = parseMMSS(newValue)
                        if (parsed != null) {
                            onUpdate(set.copy(durationSec = parsed))
                        }
                    },
                    label = { Text("MM:SS") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            CardioFixedDimension.TIME -> {
                OutlinedTextField(
                    value = distanceText,
                    onValueChange = { newValue ->
                        distanceText = newValue
                        val parsed = newValue.toIntOrNull()
                        if (parsed != null || newValue.isEmpty()) {
                            onUpdate(set.copy(distanceM = parsed))
                        }
                    },
                    label = { Text("m") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        CardioStatusButtons(
            status = set.status,
            onStatusChange = { status -> onUpdate(set.copy(status = status)) }
        )
    }
}

@Composable
private fun LegacyCardioSetRow(
    set: ExerciseSet,
    setIndex: Int,
    onUpdate: (ExerciseSet) -> Unit
) {
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
        CardioStatusButtons(
            status = set.status,
            onStatusChange = { status -> onUpdate(set.copy(status = status)) }
        )
    }
}

@Composable
private fun CardioStatusButtons(status: SetStatus, onStatusChange: (SetStatus) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        CardioStatusChip(
            selected = status == SetStatus.EASY,
            onClick = { onStatusChange(SetStatus.EASY) },
            icon = Icons.Default.ThumbUp,
            label = "Easy",
            selectedColor = MaterialTheme.colorScheme.primary,
            selectedContentColor = MaterialTheme.colorScheme.onPrimary
        )
        CardioStatusChip(
            selected = status == SetStatus.HARD,
            onClick = { onStatusChange(SetStatus.HARD) },
            icon = Icons.Default.Whatshot,
            label = "Hard",
            selectedColor = MaterialTheme.colorScheme.tertiary,
            selectedContentColor = MaterialTheme.colorScheme.onTertiary
        )
        CardioStatusChip(
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
private fun CardioStatusChip(
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
