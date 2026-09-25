package com.gymlog.app.data.backup

/** Whole-database access for backup and restore. Implemented by [BackupDao]. */
interface BackupStore {
    suspend fun readAll(): BackupData

    /** Atomically replaces all data; on any failure nothing changes. */
    suspend fun replaceAll(data: BackupData)

    suspend fun hasWorkoutInProgress(): Boolean
}
