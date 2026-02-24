package com.gymlog.app.ui.progress

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.gymlog.app.data.CardioProgressEntry
import com.gymlog.app.data.Exercise
import com.gymlog.app.data.ExerciseType
import com.gymlog.app.data.GymLogDatabase
import com.gymlog.app.data.ProgressEntry
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseProgressScreen(exerciseId: Long, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { GymLogDatabase.getDatabase(context) }
    val sessionDao = db.workoutSessionDao()
    val exerciseDao = db.exerciseDao()

    var exercise by remember { mutableStateOf<Exercise?>(null) }
    var weightProgress by remember { mutableStateOf<List<ProgressEntry>>(emptyList()) }
    var cardioProgress by remember { mutableStateOf<List<CardioProgressEntry>>(emptyList()) }
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(exerciseId) {
        exercise = exerciseDao.getById(exerciseId)
        exercise?.let { ex ->
            if (ex.type == ExerciseType.WEIGHT) {
                weightProgress = sessionDao.getWeightProgressForExercise(exerciseId)
                if (weightProgress.isNotEmpty()) {
                    modelProducer.runTransaction {
                        lineSeries { series(weightProgress.map { it.maxWeight }) }
                    }
                }
            } else {
                cardioProgress = sessionDao.getCardioProgressForExercise(exerciseId)
                if (cardioProgress.isNotEmpty()) {
                    modelProducer.runTransaction {
                        lineSeries { series(cardioProgress.map { it.maxDistance.toDouble() }) }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(exercise?.name ?: "Progress") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            val ex = exercise ?: return@Scaffold
            val hasData = if (ex.type == ExerciseType.WEIGHT) {
                weightProgress.isNotEmpty()
            } else {
                cardioProgress.isNotEmpty()
            }

            if (!hasData) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No workout data yet for ${ex.name}")
                }
                return@Scaffold
            }

            CartesianChartHost(
                rememberCartesianChart(rememberLineCartesianLayer()),
                modelProducer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            val dateFormatter = DateTimeFormatter.ofPattern("MMM d")

            if (ex.type == ExerciseType.WEIGHT && weightProgress.isNotEmpty()) {
                val latest = weightProgress.last()
                val best = weightProgress.maxBy { it.maxWeight }
                Text(
                    "Sessions: ${weightProgress.size}",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    "Last: ${latest.maxWeight} kg (${latest.date.format(dateFormatter)})",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    "Personal Best: ${best.maxWeight} kg (${best.date.format(dateFormatter)})",
                    style = MaterialTheme.typography.bodyLarge
                )
            } else if (cardioProgress.isNotEmpty()) {
                val latest = cardioProgress.last()
                val best = cardioProgress.maxBy { it.maxDistance }
                Text(
                    "Sessions: ${cardioProgress.size}",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    "Last: ${latest.maxDistance}m (${latest.date.format(dateFormatter)})",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    "Best Distance: ${best.maxDistance}m (${best.date.format(dateFormatter)})",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}
