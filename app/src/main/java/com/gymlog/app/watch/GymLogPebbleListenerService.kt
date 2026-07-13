package com.gymlog.app.watch

import com.gymlog.app.data.GymLogDatabase
import com.gymlog.app.data.SetStatus
import com.gymlog.app.service.RestTimerService
import com.gymlog.app.ui.workout.ActiveWorkoutStore
import io.rebble.pebblekit2.client.BasePebbleListenerService
import io.rebble.pebblekit2.common.model.PebbleDictionaryItem
import io.rebble.pebblekit2.common.model.ReceiveResult
import io.rebble.pebblekit2.common.model.WatchIdentifier
import java.util.UUID

/**
 * Receives Easy/Hard commands from the watchapp (even when GymLog is backgrounded) and
 * completes the current set via the shared [ActiveWorkoutStore], then starts the next rest.
 */
class GymLogPebbleListenerService : BasePebbleListenerService() {

    override suspend fun onMessageReceived(
        watchappUUID: UUID,
        data: Map<UInt, PebbleDictionaryItem>,
        watch: WatchIdentifier,
    ): ReceiveResult {
        if (watchappUUID != WatchProtocol.WATCHAPP_UUID) return ReceiveResult.Ack
        val command = parseWatchCommand(data) ?: return ReceiveResult.Ack

        if (command == WatchCommand.EXTEND_REST) {
            // Extend the running rest; RestTimerService.extend is a no-op if none is running.
            try {
                RestTimerService.extend(applicationContext, WatchProtocol.REST_SECONDS)
            } catch (_: Exception) {
            }
            return ReceiveResult.Ack
        }

        val db = GymLogDatabase.getDatabase(applicationContext)
        val dao = db.workoutSessionDao()
        // The store may be empty if the OS killed our process since the workout started;
        // rebuild it from the in-progress session so a watch command still works (journey 5).
        if (ActiveWorkoutStore.state.value == null) {
            dao.getInProgressSession()?.let { ActiveWorkoutStore.load(dao, db.exerciseDao(), it.id) }
        }
        val status = if (command == WatchCommand.EASY) SetStatus.EASY else SetStatus.HARD
        val completed = ActiveWorkoutStore.completeCurrentSet(dao, status) ?: return ReceiveResult.Ack

        // Starting a foreground service from a background listener can be restricted on
        // Android 12+; the set is already recorded, so never let a timer-start failure crash us.
        try {
            RestTimerService.start(applicationContext, WatchProtocol.REST_SECONDS, completed.sessionId)
        } catch (_: Exception) {
            // ignore; the completion still persisted
        }
        PebbleBridge.pushContext(
            applicationContext,
            ActiveWorkoutStore.state.value?.watchContext(),
            WatchProtocol.REST_SECONDS,
            running = true,
        )
        return ReceiveResult.Ack
    }
}
