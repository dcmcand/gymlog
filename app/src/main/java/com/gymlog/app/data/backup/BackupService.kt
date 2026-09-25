package com.gymlog.app.data.backup

import java.time.Instant

data class BackupExport(val json: String, val workoutCount: Int)

/** A decoded, validated file waiting for the user to confirm the replace. */
data class ImportPreview(val data: BackupData, val fileWorkouts: Int, val deviceWorkouts: Int)

/**
 * Export and replace-all import. [onDataReplaced] runs after a successful import so callers can
 * drop in-memory state that referred to the old data (e.g. the active workout store).
 */
class BackupService(
    private val store: BackupStore,
    private val onDataReplaced: () -> Unit,
    private val now: () -> Instant = Instant::now,
) {
    suspend fun export(appVersion: String): BackupExport {
        val data = store.readAll()
        return BackupExport(BackupCodec.encode(data, now(), appVersion), data.sessions.size)
    }

    suspend fun preview(text: String): ImportPreview {
        if (store.hasWorkoutInProgress()) throw BackupException.WorkoutInProgress()
        val data = BackupCodec.decode(text)
        return ImportPreview(data, fileWorkouts = data.sessions.size, deviceWorkouts = store.readAll().sessions.size)
    }

    suspend fun import(preview: ImportPreview) {
        // Checked again: a workout may have started (e.g. from the watch) while the dialog was up.
        if (store.hasWorkoutInProgress()) throw BackupException.WorkoutInProgress()
        store.replaceAll(preview.data)
        onDataReplaced()
    }
}
