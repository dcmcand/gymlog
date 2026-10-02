package com.gymlog.app.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.gymlog.app.data.GymLogDatabase
import com.gymlog.app.data.backup.BackupService
import com.gymlog.app.data.backup.DataReplaced
import com.gymlog.app.data.backup.ImportPreview
import com.gymlog.app.service.RestTimerService
import com.gymlog.app.ui.workout.ActiveWorkoutStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val service = remember {
        BackupService(GymLogDatabase.getDatabase(context).backupDao(), onDataReplaced = {
            ActiveWorkoutStore.clear()
            DataReplaced.signal()
            // A leftover rest timer would point at a session id that now means something else.
            val timer = RestTimerService.timerState.value
            if (timer.isRunning || timer.sessionId != null) RestTimerService.stop(context)
        })
    }
    val appVersion = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var busy by remember { mutableStateOf(false) }
    // The picked file survives rotation; its preview (not saveable) is rebuilt from it.
    var pickedUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var pendingImport by remember { mutableStateOf<ImportPreview?>(null) }

    // Runs [work] off the main thread with the buttons disabled; its result or error goes to
    // the snackbar. A null result means "nothing to announce" (e.g. the confirm dialog opened).
    fun runBackupTask(work: suspend () -> String?) {
        busy = true
        scope.launch {
            val message = try {
                runToCompletion { work() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                backupErrorMessage(e)
            }
            busy = false
            message?.let { snackbar.showSnackbar(it) }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult // picker cancelled
        runBackupTask {
            val export = service.export(appVersion)
            openForOverwrite { mode -> context.contentResolver.openOutputStream(uri, mode) }
                ?.use { it.write(export.json.toByteArray()) }
                ?: throw IOException("could not open $uri")
            "Exported ${workoutCount(export.workoutCount)}"
        }
    }

    fun previewPicked(uri: Uri) = runBackupTask {
        try {
            val text = context.contentResolver.openInputStream(uri)?.use { readBackupText(it) }
                ?: throw IOException("could not open $uri")
            val preview = service.preview(text)
            withContext(Dispatchers.Main) { pendingImport = preview }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { pickedUri = null }
            throw e
        }
        null
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult // picker cancelled
        pickedUri = uri
        previewPicked(uri)
    }

    // After a rotation the dialog's preview is gone but the picked file is remembered.
    LaunchedEffect(Unit) {
        // busy covers a picker result delivered on recreation that already started a preview.
        pickedUri?.let { if (pendingImport == null && !busy) previewPicked(it) }
    }

    // A ROM without a document picker would otherwise crash on launch.
    fun launchPicker(launch: () -> Unit) {
        try {
            launch()
        } catch (_: ActivityNotFoundException) {
            scope.launch { snackbar.showSnackbar("No file manager is available to pick a file.") }
        }
    }

    // Stay put while an export or import runs, so the user sees how it ended.
    BackHandler(enabled = busy) {}

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, enabled = !busy) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Backup", style = MaterialTheme.typography.titleMedium)
            Text(
                "Save all your exercises, workouts and history to a file, or restore from one. " +
                    "Importing replaces everything on this device.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = { launchPicker { exportLauncher.launch(backupFileName(LocalDate.now())) } },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Export data") }
            OutlinedButton(
                // Some file managers label .json as text/plain or octet-stream.
                onClick = { launchPicker { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) } },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Import data") }
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("About", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(
                onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, PRIVACY_POLICY_URL.toUri()))
                    } catch (_: ActivityNotFoundException) {
                        // No browser (some ROMs ship none): show where to read it instead.
                        scope.launch { snackbar.showSnackbar("No browser found. Privacy policy: $PRIVACY_POLICY_URL") }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Privacy policy") }
            Text("GymLog $appVersion", style = MaterialTheme.typography.bodySmall)
        }
    }

    pendingImport?.let { preview ->
        AlertDialog(
            onDismissRequest = { pendingImport = null; pickedUri = null },
            title = { Text("Replace all data?") },
            text = { Text(importConfirmText(preview)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    pickedUri = null
                    runBackupTask {
                        service.import(preview)
                        "Imported ${workoutCount(preview.fileWorkouts)}"
                    }
                }) { Text("Replace") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null; pickedUri = null }) { Text("Cancel") }
            },
        )
    }
}
