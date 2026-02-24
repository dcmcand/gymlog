package com.gymlog.app.ui.templates

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gymlog.app.data.ExerciseType
import com.gymlog.app.data.GymLogDatabase
import com.gymlog.app.data.WorkoutTemplate
import com.gymlog.app.data.WorkoutTemplateExercise
import kotlinx.coroutines.launch

data class TemplateExerciseEntry(
    val exerciseId: Long,
    val exerciseName: String,
    val exerciseType: ExerciseType,
    val targetSets: Int = 3,
    val targetReps: Int? = 8,
    val targetWeightKg: Double? = null,
    val targetDistanceM: Int? = null,
    val targetDurationSec: Int? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTemplateScreen(templateId: Long?, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { GymLogDatabase.getDatabase(context) }
    val templateDao = db.workoutTemplateDao()
    val exerciseDao = db.exerciseDao()
    val scope = rememberCoroutineScope()

    var templateName by remember { mutableStateOf("") }
    val exerciseEntries = remember { mutableStateListOf<TemplateExerciseEntry>() }
    var showExercisePicker by remember { mutableStateOf(false) }
    val allExercises by exerciseDao.getAll().collectAsState(initial = emptyList())
    var isLoaded by remember { mutableStateOf(templateId == null || templateId == 0L) }

    // Load existing template if editing
    LaunchedEffect(templateId) {
        if (templateId != null && templateId != 0L) {
            val template = templateDao.getById(templateId)
            if (template != null) {
                templateName = template.name
                val templateExercises = templateDao.getExercisesForTemplate(templateId)
                exerciseEntries.clear()
                for (te in templateExercises) {
                    val exercise = exerciseDao.getById(te.exerciseId)
                    if (exercise != null) {
                        exerciseEntries.add(
                            TemplateExerciseEntry(
                                exerciseId = exercise.id,
                                exerciseName = exercise.name,
                                exerciseType = exercise.type,
                                targetSets = te.targetSets,
                                targetReps = te.targetReps,
                                targetWeightKg = te.targetWeightKg,
                                targetDistanceM = te.targetDistanceM,
                                targetDurationSec = te.targetDurationSec
                            )
                        )
                    }
                }
            }
            isLoaded = true
        }
    }

    val isEditing = templateId != null && templateId != 0L

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Edit Template" else "New Template") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                val id = if (isEditing) {
                                    templateDao.update(
                                        WorkoutTemplate(
                                            id = templateId!!,
                                            name = templateName.trim()
                                        )
                                    )
                                    templateId
                                } else {
                                    templateDao.insert(
                                        WorkoutTemplate(name = templateName.trim())
                                    )
                                }
                                templateDao.deleteAllExercisesForTemplate(id)
                                exerciseEntries.forEachIndexed { index, entry ->
                                    templateDao.insertTemplateExercise(
                                        WorkoutTemplateExercise(
                                            templateId = id,
                                            exerciseId = entry.exerciseId,
                                            targetSets = entry.targetSets,
                                            targetReps = entry.targetReps,
                                            targetWeightKg = entry.targetWeightKg,
                                            targetDistanceM = entry.targetDistanceM,
                                            targetDurationSec = entry.targetDurationSec,
                                            sortOrder = index
                                        )
                                    )
                                }
                                onNavigateBack()
                            }
                        },
                        enabled = templateName.isNotBlank()
                    ) { Text("Save") }
                }
            )
        }
    ) { padding ->
        if (!isLoaded) return@Scaffold

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                OutlinedTextField(
                    value = templateName,
                    onValueChange = { templateName = it },
                    label = { Text("Template name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            itemsIndexed(exerciseEntries) { index, entry ->
                ExerciseEntryCard(
                    entry = entry,
                    onUpdate = { updated -> exerciseEntries[index] = updated },
                    onRemove = { exerciseEntries.removeAt(index) }
                )
            }

            item {
                TextButton(onClick = { showExercisePicker = true }) {
                    Text("+ Add Exercise")
                }
            }
        }
    }

    if (showExercisePicker) {
        AlertDialog(
            onDismissRequest = { showExercisePicker = false },
            title = { Text("Add Exercise") },
            text = {
                Column {
                    if (allExercises.isEmpty()) {
                        Text("No exercises. Create some in the Exercises tab first.")
                    } else {
                        allExercises.forEach { exercise ->
                            ListItem(
                                headlineContent = { Text(exercise.name) },
                                supportingContent = { Text(exercise.type.name) },
                                modifier = Modifier.clickable {
                                    val defaults = if (exercise.type == ExerciseType.WEIGHT) {
                                        TemplateExerciseEntry(
                                            exerciseId = exercise.id,
                                            exerciseName = exercise.name,
                                            exerciseType = exercise.type,
                                            targetSets = 3,
                                            targetReps = 8,
                                            targetWeightKg = 20.0
                                        )
                                    } else {
                                        TemplateExerciseEntry(
                                            exerciseId = exercise.id,
                                            exerciseName = exercise.name,
                                            exerciseType = exercise.type,
                                            targetSets = 1,
                                            targetReps = null,
                                            targetDistanceM = 2000,
                                            targetDurationSec = 600
                                        )
                                    }
                                    exerciseEntries.add(defaults)
                                    showExercisePicker = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExercisePicker = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ExerciseEntryCard(
    entry: TemplateExerciseEntry,
    onUpdate: (TemplateExerciseEntry) -> Unit,
    onRemove: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(entry.exerciseName, style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (entry.exerciseType == ExerciseType.WEIGHT) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = entry.targetSets.toString(),
                        onValueChange = {
                            onUpdate(entry.copy(targetSets = it.toIntOrNull() ?: entry.targetSets))
                        },
                        label = { Text("Sets") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = entry.targetReps?.toString() ?: "",
                        onValueChange = { onUpdate(entry.copy(targetReps = it.toIntOrNull())) },
                        label = { Text("Reps") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = entry.targetWeightKg?.toString() ?: "",
                        onValueChange = {
                            onUpdate(entry.copy(targetWeightKg = it.toDoubleOrNull()))
                        },
                        label = { Text("kg") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = entry.targetDistanceM?.toString() ?: "",
                        onValueChange = {
                            onUpdate(entry.copy(targetDistanceM = it.toIntOrNull()))
                        },
                        label = { Text("Meters") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = entry.targetDurationSec?.toString() ?: "",
                        onValueChange = {
                            onUpdate(entry.copy(targetDurationSec = it.toIntOrNull()))
                        },
                        label = { Text("Seconds") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }
        }
    }
}
