package com.gymlog.app.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gymlog.app.data.ExerciseSet
import com.gymlog.app.data.SetStatus
import com.gymlog.app.ui.theme.SetEasyColor
import com.gymlog.app.ui.theme.SetHardColor
import com.gymlog.app.ui.theme.SetPartialColor

data class SelectedSetInfo(
    val exerciseId: Long,
    val setIndex: Int,
    val set: ExerciseSet,
    val exerciseName: String,
    val targetReps: Int?
)

@Composable
fun SetCompletionModal(
    info: SelectedSetInfo,
    onComplete: (SetStatus, Int?) -> Unit,
    onDismiss: () -> Unit
) {
    var showRepEntry by remember { mutableStateOf(false) }
    var customReps by remember { mutableIntStateOf((info.targetReps ?: 5) - 1) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { /* consume click */ }
        ) {
            if (!showRepEntry) {
                StatusSelectionPage(
                    info = info,
                    onEasy = { onComplete(SetStatus.EASY, info.targetReps) },
                    onHard = { onComplete(SetStatus.HARD, info.targetReps) },
                    onIncomplete = { showRepEntry = true },
                    onCancel = onDismiss
                )
            } else {
                RepEntryPage(
                    targetReps = info.targetReps ?: 5,
                    customReps = customReps,
                    onRepsChanged = { customReps = it },
                    onConfirm = { onComplete(SetStatus.PARTIAL, customReps) },
                    onBack = { showRepEntry = false }
                )
            }
        }
    }
}

@Composable
private fun StatusSelectionPage(
    info: SelectedSetInfo,
    onEasy: () -> Unit,
    onHard: () -> Unit,
    onIncomplete: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            info.exerciseName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))

        val weightText = info.set.weightKg?.let {
            val display = if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
            "${display}kg"
        } ?: ""
        Text(
            "Set ${info.setIndex + 1} - $weightText - ${info.targetReps ?: 0} reps",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onEasy,
            colors = ButtonDefaults.buttonColors(containerColor = SetEasyColor),
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
        ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text("Easy", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onHard,
            colors = ButtonDefaults.buttonColors(containerColor = SetHardColor),
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
        ) {
            Icon(Icons.Default.Whatshot, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text("Hard", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onIncomplete,
            colors = ButtonDefaults.buttonColors(containerColor = SetPartialColor),
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text("Incomplete", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onCancel) {
            Text("Cancel")
        }
    }
}

@Composable
private fun RepEntryPage(
    targetReps: Int,
    customReps: Int,
    onRepsChanged: (Int) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                "How many reps completed?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            customReps.toString(),
            fontSize = 72.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "of $targetReps target",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onRepsChanged((customReps - 1).coerceAtLeast(0)) },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = "Decrease reps",
                    modifier = Modifier.size(32.dp)
                )
            }
            IconButton(
                onClick = { onRepsChanged(customReps + 1) },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Increase reps",
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onConfirm,
            colors = ButtonDefaults.buttonColors(containerColor = SetPartialColor),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Confirm", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}
