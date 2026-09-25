package com.gymlog.app.data.backup

/** Why an export or import could not go ahead. The data on the device is never modified. */
sealed class BackupException(message: String) : Exception(message) {
    class NotABackup : BackupException("not a GymLog backup")
    class NewerVersion(val version: Int) : BackupException("backup format $version is newer than supported")
    class Corrupt(detail: String) : BackupException("damaged backup: $detail")
    class WorkoutInProgress : BackupException("a workout is in progress")
}
