package com.gymlog.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gymlog.app.data.GymLogDatabase
import com.gymlog.app.data.SessionStatus
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

data class SessionSummary(
    val sessionId: Long,
    val workoutName: String,
    val exerciseCount: Int,
    val status: String,
    val isInProgress: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onNewWorkoutClick: () -> Unit,
    onResumeWorkout: (Long) -> Unit,
    onWorkoutClick: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val db = remember { GymLogDatabase.getDatabase(context) }
    val sessionDao = db.workoutSessionDao()
    val workoutDao = db.workoutDao()
    val scope = rememberCoroutineScope()

    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var selectedDaySessions by remember { mutableStateOf<List<SessionSummary>>(emptyList()) }
    var inProgressSessionId by remember { mutableStateOf<Long?>(null) }

    val firstDay = currentMonth.atDay(1)
    val lastDay = currentMonth.atEndOfMonth()
    val workoutDates by sessionDao.getWorkoutDatesInRange(firstDay, lastDay)
        .collectAsState(initial = emptyList())

    // Check for in-progress session
    LaunchedEffect(Unit) {
        val session = sessionDao.getInProgressSession()
        inProgressSessionId = session?.id
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("GymLog") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewWorkoutClick) {
                Icon(Icons.Default.Add, contentDescription = "New workout")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Resume workout banner
            if (inProgressSessionId != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onResumeWorkout(inProgressSessionId!!) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        "Workout in progress - tap to resume",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Month navigation header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous month"
                    )
                }
                Text(
                    text = "${
                        currentMonth.month.getDisplayName(
                            TextStyle.FULL,
                            Locale.getDefault()
                        )
                    } ${currentMonth.year}",
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next month"
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Day-of-week headers (Mon through Sun)
            Row(modifier = Modifier.fillMaxWidth()) {
                val daysOfWeek = listOf(
                    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY,
                    DayOfWeek.SUNDAY
                )
                daysOfWeek.forEach { day ->
                    Text(
                        text = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Calendar grid
            val firstDayOfWeek = firstDay.dayOfWeek
            val startOffset = (firstDayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
            val daysInMonth = currentMonth.lengthOfMonth()
            val totalCells = startOffset + daysInMonth
            val rows = (totalCells + 6) / 7

            for (row in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 0..6) {
                        val cellIndex = row * 7 + col
                        val dayNum = cellIndex - startOffset + 1

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .then(
                                    if (dayNum in 1..daysInMonth) {
                                        Modifier.clickable {
                                            val date = currentMonth.atDay(dayNum)
                                            selectedDate = date
                                            scope.launch {
                                                val sessions =
                                                    sessionDao.getSessionsForDate(date)
                                                selectedDaySessions = sessions.map { session ->
                                                    val workoutName =
                                                        session.workoutId?.let {
                                                            workoutDao.getById(it)?.name
                                                        } ?: "Freestyle"
                                                    val sets =
                                                        sessionDao.getSetsForSession(session.id)
                                                    val exerciseCount =
                                                        sets.map { it.exerciseId }
                                                            .distinct().size
                                                    val statusLabel = session.status.name
                                                        .lowercase().replace('_', ' ')
                                                    SessionSummary(
                                                        sessionId = session.id,
                                                        workoutName = workoutName,
                                                        exerciseCount = exerciseCount,
                                                        status = statusLabel,
                                                        isInProgress = session.status == SessionStatus.IN_PROGRESS
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        Modifier
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (dayNum in 1..daysInMonth) {
                                val date = currentMonth.atDay(dayNum)
                                val isToday = date == LocalDate.now()
                                val hasWorkout = date in workoutDates

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = dayNum.toString(),
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selectedDate == date)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurface
                                    )
                                    if (hasWorkout) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Selected day session cards
            if (selectedDate != null && selectedDaySessions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = selectedDate.toString(),
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                selectedDaySessions.forEach { summary ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                if (summary.isInProgress) {
                                    onResumeWorkout(summary.sessionId)
                                } else {
                                    onWorkoutClick(summary.sessionId)
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = summary.workoutName,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "${summary.exerciseCount} exercises",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = summary.status,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else if (selectedDate != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "No workouts on $selectedDate",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}
