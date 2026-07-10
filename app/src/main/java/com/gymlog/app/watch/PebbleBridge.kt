package com.gymlog.app.watch

import android.content.Context
import io.rebble.pebblekit2.client.DefaultPebbleSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Sends workout context + timer state to the Alloy watchapp over PebbleKitAndroid2 (Bluetooth
 * via the Core app; no INTERNET/BT permission needed here). Fire-and-forget: if the watch or
 * Core app is absent, the send fails silently and the phone flow is unaffected (journey 9).
 */
object PebbleBridge {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var sender: DefaultPebbleSender? = null

    private fun sender(context: Context): DefaultPebbleSender =
        sender ?: DefaultPebbleSender(context.applicationContext).also { sender = it }

    fun pushContext(context: Context, ctx: WatchContext?, durationSec: Int, running: Boolean) {
        val s = sender(context)
        scope.launch {
            try {
                s.sendDataToPebble(WatchProtocol.WATCHAPP_UUID, buildContextMessage(ctx, durationSec, running))
            } catch (_: Exception) {
                // Watch/Core app not available; ignore.
            }
        }
    }
}
