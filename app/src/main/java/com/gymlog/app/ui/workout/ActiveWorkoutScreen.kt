package com.gymlog.app.ui.workout

import android.Manifest
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gymlog.app.data.CardioFixedDimension
import com.gymlog.app.data.ExerciseSet
import com.gymlog.app.data.ExerciseType
import com.gymlog.app.data.GymLogDatabase
import com.gymlog.app.data.SessionStatus
import com.gymlog.app.data.SetStatus
import com.gymlog.app.data.WorkoutSession
import com.gymlog.app.data.displayName
import com.gymlog.app.data.suggestWeight
import com.gymlog.app.notification.RestTimerNotification
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    workoutId: Long? = null,
    resumeSessionId: Long? = null,
    onFinish: () -> Unit,
    onDelete: () -> Unit = onFinish
) {
    val context = LocalContext.current
    val db = remember { GymLogDatabase.getDatabase(context) }
    val sessionDao = db.workoutSessionDao()
    val workoutDao = db.workoutDao()
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
    var totalTimerSeconds by remember { mutableIntStateOf(90) }

    // Modal state
    var selectedSetInfo by remember { mutableStateOf<SelectedSetInfo?>(null) }

    // Notification permission (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not - in-app timer works regardless */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun startTimerNotification(seconds: Int) {
        val endTimeMs = System.currentTimeMillis() + seconds * 1000L
        RestTimerNotification.show(context, endTimeMs, sessionId)
    }

    // Countdown effect
    LaunchedEffect(timerRunning) {
        if (timerRunning) {
            while (remainingSeconds > 0) {
                delay(1000L)
                remainingSeconds--
            }
            RestTimerNotification.cancel(context)
            // Vibrate when timer reaches zero
            try {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(VibratorManager::class.java)
                    vibratorManager?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Vibrator::class.java)
                }
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 300, 200, 300),
                        -1
                    )
                )
            } catch (_: Exception) {
                // Vibration not available
            }
            // Brief "Rest Complete" display, then auto-hide
            delay(2000L)
            timerRunning = false
            showRestTimer = false
        }
    }

    // Initialize: either resume existing session or create new from workout
    LaunchedEffect(workoutId, resumeSessionId) {
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
        } else if (workoutId != null) {
            // Create new session from workout
            val workout = workoutDao.getById(workoutId) ?: return@LaunchedEffect
            val workoutExercises = workoutDao.getExercisesForWorkout(workoutId)

            val newSessionId = sessionDao.insert(
                WorkoutSession(
                    workoutId = workoutId,
                    date = LocalDate.now(),
                    status = SessionStatus.IN_PROGRESS,
                    startedAt = Instant.now()
                )
            )
            sessionId = newSessionId

            for (te in workoutExercises) {
                val exercise = exerciseDao.getById(te.exerciseId) ?: continue
                val lastSet = sessionDao.getLastCompletedSet(te.exerciseId)

                // Compute suggested weight once per exercise
                val suggestedWeight: Double? = if (exercise.type == ExerciseType.WEIGHT) {
                    val lastSession = sessionDao.getLastSessionForExercise(te.exerciseId)
                    if (lastSession != null) {
                        val prevSets = sessionDao.getSetsForExercise(lastSession.id, te.exerciseId)
                        suggestWeight(prevSets, lastSession.date) ?: te.targetWeightKg
                    } else {
                        te.targetWeightKg
                    }
                } else null

                val sets = mutableListOf<ExerciseSet>()
                for (setNum in 1..te.targetSets) {
                    val set = if (exercise.type == ExerciseType.WEIGHT) {
                        ExerciseSet(
                            sessionId = newSessionId,
                            exerciseId = exercise.id,
                            setNumber = setNum,
                            weightKg = suggestedWeight,
                            repsCompleted = te.targetReps,
                            status = SetStatus.PENDING
                        )
                    } else if (exercise.cardioFixedDimension != null) {
                        // New-style cardio: only pre-fill the variable dimension
                        when (exercise.cardioFixedDimension) {
                            CardioFixedDimension.DISTANCE -> ExerciseSet(
                                sessionId = newSessionId,
                                exerciseId = exercise.id,
                                setNumber = setNum,
                                durationSec = lastSet?.durationSec,
                                status = SetStatus.PENDING
                            )
                            CardioFixedDimension.TIME -> ExerciseSet(
                                sessionId = newSessionId,
                                exerciseId = exercise.id,
                                setNumber = setNum,
                                distanceM = lastSet?.distanceM,
                                status = SetStatus.PENDING
                            )
                        }
                    } else {
                        // Legacy cardio
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

    // Wrap Scaffold in a Box so the modal can overlay everything
    Box(modifier = Modifier.fillMaxSize()) {
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
                Column {
                    if (showRestTimer) {
                        RestTimerBottomBar(
                            remainingSeconds = remainingSeconds,
                            totalSeconds = totalTimerSeconds,
                            onExtend = {
                                remainingSeconds += 90
                                totalTimerSeconds += 90
                                startTimerNotification(remainingSeconds)
                            },
                            onDismiss = {
                                timerRunning = false
                                showRestTimer = false
                                RestTimerNotification.cancel(context)
                            }
                        )
                    }
                    Surface(tonalElevation = 3.dp) {
                        Button(
                            onClick = {
                                RestTimerNotification.cancel(context)
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
                    CompactExerciseCard(
                        exercise = exercise,
                        sets = sets,
                        onSetTapped = { setIndex, set ->
                            selectedSetInfo = SelectedSetInfo(
                                exerciseId = exercise.id,
                                setIndex = setIndex,
                                set = set,
                                exerciseName = exercise.displayName(),
                                targetReps = set.repsCompleted
                            )
                        },
                        onWeightChangedForAll = { newWeight ->
                            val currentSets = workoutState.getExerciseSets(exercise.id) ?: return@CompactExerciseCard
                            scope.launch {
                                currentSets.forEachIndexed { index, s ->
                                    val updated = s.copy(weightKg = newWeight)
                                    workoutState.updateSet(exercise.id, index, updated)
                                    sessionDao.updateSet(updated)
                                }
                            }
                        },
                        onSetUpdated = { setIndex, updatedSet ->
                            workoutState.updateSet(exercise.id, setIndex, updatedSet)
                            scope.launch { sessionDao.updateSet(updatedSet) }
                            if (updatedSet.status != SetStatus.PENDING) {
                                remainingSeconds = 90
                                totalTimerSeconds = 90
                                showRestTimer = true
                                timerRunning = true
                                startTimerNotification(90)
                            }
                        },
                        onAddSet = {
                            scope.launch {
                                val currentSets = workoutState.getExerciseSetsCopy(exercise.id)
                                val lastSet = currentSets.lastOrNull()
                                val newSet = if (exercise.cardioFixedDimension != null) {
                                    // New-style cardio: only copy the variable dimension
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
                                    // Weight or legacy cardio
                                    ExerciseSet(
                                        sessionId = sessionId!!,
                                        exerciseId = exercise.id,
                                        setNumber = currentSets.size + 1,
                                        weightKg = lastSet?.weightKg,
                                        repsCompleted = lastSet?.repsCompleted,
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
        }

        // Modal overlay - outside Scaffold so it covers everything
        selectedSetInfo?.let { info ->
            SetCompletionModal(
                info = info,
                onComplete = { status, reps ->
                    val updatedSet = info.set.copy(
                        status = status,
                        repsCompleted = reps
                    )
                    workoutState.updateSet(info.exerciseId, info.setIndex, updatedSet)
                    scope.launch { sessionDao.updateSet(updatedSet) }

                    // Auto-start rest timer
                    remainingSeconds = 90
                    totalTimerSeconds = 90
                    showRestTimer = true
                    timerRunning = true
                    startTimerNotification(90)

                    selectedSetInfo = null
                },
                onDismiss = { selectedSetInfo = null }
            )
        }
    }
}
