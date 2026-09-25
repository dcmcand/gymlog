package com.gymlog.app.ui.settings

import com.gymlog.app.data.backup.BackupException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Real backups are a few hundred KB; anything this big is not one. */
const val MAX_BACKUP_BYTES = 32L * 1024 * 1024

/**
 * Reads a picked file as UTF-8, refusing anything over [limit] bytes so picking a video or
 * archive can't exhaust memory.
 */
fun readBackupText(input: InputStream, limit: Long = MAX_BACKUP_BYTES): String {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val n = input.read(buffer)
        if (n < 0) break
        total += n
        if (total > limit) throw BackupException.NotABackup()
        out.write(buffer, 0, n)
    }
    return out.toByteArray().decodeToString()
}

/**
 * Runs backup work off the main thread and to completion even if the calling screen goes away
 * (rotation, back): a cancelled export would otherwise leave an empty file behind, and a
 * cancelled import could skip its post-import cleanup.
 */
suspend fun <T> runToCompletion(block: suspend () -> T): T =
    withContext(NonCancellable + Dispatchers.IO) { block() }
