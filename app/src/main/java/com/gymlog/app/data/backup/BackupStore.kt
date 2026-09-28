package com.gymlog.app.data.backup

/** Whole-database access for backup and restore. Implemented by [BackupDao]. */
interface BackupStore {
    suspend fun readAll(): BackupData

    /**
     * Atomically replaces all data; on any failure nothing changes. Throws
     * [BackupException.WorkoutInProgress] if a workout is in progress when the replace starts.
     */
    suspend fun replaceAll(data: BackupData)

    suspend fun countSessions(): Int

    suspend fun hasWorkoutInProgress(): Boolean
}
