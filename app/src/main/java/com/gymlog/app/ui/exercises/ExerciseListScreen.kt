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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.unit.dp
import com.gymlog.app.data.Exercise
import com.gymlog.app.data.ExerciseType
import com.gymlog.app.data.GymLogDatabase
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
                            onDelete = { scope.launch { exerciseDao.delete(exercise) } }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddExerciseDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, type ->
                scope.launch {
                    exerciseDao.insert(Exercise(name = name, type = type))
                }
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun ExerciseRow(exercise: Exercise, onClick: () -> Unit, onDelete: () -> Unit) {
    ListItem(
        headlineContent = { Text(exercise.name) },
        modifier = Modifier.clickable(onClick = onClick),
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    )
}

@Composable
private fun AddExerciseDialog(onDismiss: () -> Unit, onConfirm: (String, ExerciseType) -> Unit) {
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ExerciseType.WEIGHT) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Exercise") },
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
                        label = { Text("Weight") }
                    )
                    FilterChip(
                        selected = selectedType == ExerciseType.CARDIO,
                        onClick = { selectedType = ExerciseType.CARDIO },
                        label = { Text("Cardio") }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), selectedType) },
                enabled = name.isNotBlank()
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
