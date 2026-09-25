package com.gymlog.app.ui.settings

import com.gymlog.app.data.backup.BackupException
import com.gymlog.app.data.backup.ImportPreview
import java.time.LocalDate

fun backupFileName(date: LocalDate): String = "gymlog-backup-$date.json"

fun workoutCount(n: Int): String = if (n == 1) "1 workout" else "$n workouts"

fun backupErrorMessage(e: Throwable): String = when (e) {
    is BackupException.NotABackup -> "That file isn't a GymLog backup."
    is BackupException.NewerVersion -> "This backup was made by a newer GymLog. Update GymLog, then try again."
    is BackupException.Corrupt -> "This backup is damaged and can't be imported."
    is BackupException.WorkoutInProgress -> "Finish or discard your current workout first."
    else -> "Couldn't read or write the file."
}

fun importConfirmText(p: ImportPreview): String =
    "This device's ${workoutCount(p.deviceWorkouts)} will be replaced by the file's " +
        "${workoutCount(p.fileWorkouts)}. This can't be undone."
