package com.gymlog.app.ui.exercises

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gymlog.app.data.CardioFixedDimension
import com.gymlog.app.data.Exercise
import com.gymlog.app.data.ExerciseType
import com.gymlog.app.data.GymLogDatabase
import com.gymlog.app.data.displayName
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseListScreen(onExerciseClick: (Long) -> Unit) {
    val context = LocalContext.current
    val db = remember { GymLogDatabase.getDatabase(context) }
    val exerciseDao = db.exerciseDao()
    val exercises by exerciseDao.getAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingExercise by remember { mutableStateOf<Exercise?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Exercises") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add exercise")
            }
        }
    ) { padding ->
        val weightExercises = exercises.filter { it.type == ExerciseType.WEIGHT }
        val cardioExercises = exercises.filter { it.type == ExerciseType.CARDIO }

        if (exercises.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No exercises yet. Tap + to add one.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp)
            ) {
                if (weightExercises.isNotEmpty()) {
                    item {
                        Text(
                            "Weight",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(weightExercises, key = { it.id }) { exercise ->
                        ExerciseRow(
                            exercise = exercise,
                            onClick = { onExerciseClick(exercise.id) },
                            onEdit = { editingExercise = exercise },
                            onDelete = { scope.launch { exerciseDao.delete(exercise) } }
                        )
                    }
                }
                if (cardioExercises.isNotEmpty()) {
                    item {
                        Text(
                            "Cardio",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(cardioExercises, key = { it.id }) { exercise ->
                        ExerciseRow(
                            exercise = exercise,
                            onClick = { onExerciseClick(exercise.id) },
                            onEdit = { editingExercise = exercise },
                            onDelete = { scope.launch { exerciseDao.delete(exercise) } }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog || editingExercise != null) {
        ExerciseDialog(
            existing = editingExercise,
            onDismiss = {
                showAddDialog = false
                editingExercise = null
            },
            onConfirm = { exercise ->
                scope.launch {
                    if (exercise.id != 0L) exerciseDao.update(exercise)
                    else exerciseDao.insert(exercise)
                }
                showAddDialog = false
                editingExercise = null
            }
        )
    }
}

@Composable
private fun ExerciseRow(
    exercise: Exercise,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ListItem(
        headlineContent = { Text(exercise.displayName()) },
        modifier = Modifier.clickable(onClick = onClick),
        trailingContent = {
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    )
}

@Composable
private fun ExerciseDialog(
    existing: Exercise? = null,
    onDismiss: () -> Unit,
    onConfirm: (Exercise) -> Unit
) {
    val isEdit = existing != null

    // Pre-fill the cardio fixed-value text in its display units (km vs m, min vs s).
    val initialFixedValueText = remember(existing) {
        val ex = existing ?: return@remember ""
        val v = ex.fixedValue ?: return@remember ""
        when (ex.cardioFixedDimension) {
            CardioFixedDimension.DISTANCE -> if (ex.distanceDisplayKm) (v / 1000).toString() else v.toString()
            CardioFixedDimension.TIME -> (v / 60).toString()
            null -> ""
        }
    }

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var selectedType by remember { mutableStateOf(existing?.type ?: ExerciseType.WEIGHT) }
    var fixedDimension by remember {
        mutableStateOf(existing?.cardioFixedDimension ?: CardioFixedDimension.DISTANCE)
    }
    var fixedValueText by remember { mutableStateOf(initialFixedValueText) }
    var distanceDisplayKm by remember { mutableStateOf(existing?.distanceDisplayKm ?: true) }
    var levelText by remember { mutableStateOf(existing?.level?.toString() ?: "") }
    var incrementText by remember { mutableStateOf(existing?.weightIncrementKg?.toString() ?: "2.5") }

    fun parsedIncrement(): Double? = incrementText.toDoubleOrNull()?.takeIf { it > 0 }

    fun buildExercise(): Exercise {
        val id = existing?.id ?: 0L
        if (selectedType == ExerciseType.WEIGHT) {
            return Exercise(
                id = id,
                name = name.trim(),
                type = ExerciseType.WEIGHT,
                weightIncrementKg = parsedIncrement() ?: 2.5
            )
        }
        val rawValue = fixedValueText.toIntOrNull()
        val fixedValue = when {
            rawValue == null -> null
            fixedDimension == CardioFixedDimension.DISTANCE && distanceDisplayKm -> rawValue * 1000
            fixedDimension == CardioFixedDimension.TIME -> rawValue * 60
            else -> rawValue
        }
        return Exercise(
            id = id,
            name = name.trim(),
            type = ExerciseType.CARDIO,
            cardioFixedDimension = fixedDimension,
            fixedValue = fixedValue,
            level = levelText.toIntOrNull(),
            distanceDisplayKm = distanceDisplayKm
        )
    }

    val previewExercise = buildExercise()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Edit Exercise" else "Add Exercise") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Exercise name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedType == ExerciseType.WEIGHT,
                        onClick = { selectedType = ExerciseType.WEIGHT },
                        label = { Text("Weight") },
                        enabled = !isEdit
                    )
                    FilterChip(
                        selected = selectedType == ExerciseType.CARDIO,
                        onClick = { selectedType = ExerciseType.CARDIO },
                        label = { Text("Cardio") },
                        enabled = !isEdit
                    )
                }

                if (selectedType == ExerciseType.WEIGHT) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = incrementText,
                        onValueChange = { incrementText = it },
                        label = { Text("Increment (kg)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = parsedIncrement() == null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (selectedType == ExerciseType.CARDIO) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Fixed dimension",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = fixedDimension == CardioFixedDimension.DISTANCE,
                            onClick = { fixedDimension = CardioFixedDimension.DISTANCE },
                            label = { Text("Distance") }
                        )
                        FilterChip(
                            selected = fixedDimension == CardioFixedDimension.TIME,
                            onClick = { fixedDimension = CardioFixedDimension.TIME },
                            label = { Text("Time") }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (fixedDimension == CardioFixedDimension.DISTANCE) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = fixedValueText,
                                onValueChange = { fixedValueText = it },
                                label = { Text(if (distanceDisplayKm) "Distance (km)" else "Distance (m)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = distanceDisplayKm,
                                onClick = { distanceDisplayKm = true },
                                label = { Text("km") }
                            )
                            FilterChip(
                                selected = !distanceDisplayKm,
                                onClick = { distanceDisplayKm = false },
                                label = { Text("m") }
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = fixedValueText,
                            onValueChange = { fixedValueText = it },
                            label = { Text("Duration (minutes)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = levelText,
                        onValueChange = { levelText = it },
                        label = { Text("Level (optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (name.isNotBlank() && fixedValueText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            previewExercise.displayName(),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            val isValid = name.isNotBlank() && when (selectedType) {
                ExerciseType.WEIGHT -> parsedIncrement() != null
                ExerciseType.CARDIO -> fixedValueText.toIntOrNull() != null
            }
            TextButton(
                onClick = { onConfirm(buildExercise()) },
                enabled = isValid
            ) { Text(if (isEdit) "Save" else "Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
