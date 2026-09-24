package com.gymlog.app.watch

import io.rebble.pebblekit2.common.model.PebbleDictionaryItem

/**
 * Builds the phone -> watch message. Strings go as [PebbleDictionaryItem.Text]; numbers as
 * [PebbleDictionaryItem.Int32] (the interop spike showed Alloy exchanges integers as Int32,
 * and Int32 avoids the Kotlin unsigned-inline constructor awkwardness).
 */
fun buildContextMessage(
    ctx: WatchContext?,
    durationSec: Int,
    running: Boolean,
): Map<UInt, PebbleDictionaryItem> {
    val m = HashMap<UInt, PebbleDictionaryItem>()
    m[WatchProtocol.KEY_EXERCISE_NAME] = PebbleDictionaryItem.Text(ctx?.exerciseName ?: "")
    m[WatchProtocol.KEY_TARGET_TEXT] = PebbleDictionaryItem.Text(ctx?.targetText ?: "")
    m[WatchProtocol.KEY_SET_LABEL] = PebbleDictionaryItem.Text(ctx?.setLabel ?: "")
    m[WatchProtocol.KEY_DURATION_SEC] = PebbleDictionaryItem.Int32(durationSec)
    m[WatchProtocol.KEY_RUNNING] = PebbleDictionaryItem.Int32(if (running) 1 else 0)
    return m
}

/** A command the watch can send to the phone. */
enum class WatchCommand { EASY, HARD, EXTEND_REST, NEXT_EXERCISE }

/** Parses a watch -> phone command, or null if not a known command. */
fun parseWatchCommand(data: Map<UInt, PebbleDictionaryItem>): WatchCommand? {
    val value = when (val cmd = data[WatchProtocol.KEY_CMD]) {
        is PebbleDictionaryItem.Int32 -> cmd.value
        is PebbleDictionaryItem.Int16 -> cmd.value.toInt()
        is PebbleDictionaryItem.Int8 -> cmd.value.toInt()
        else -> return null
    }
    return when (value) {
        WatchProtocol.CMD_EASY -> WatchCommand.EASY
        WatchProtocol.CMD_HARD -> WatchCommand.HARD
        WatchProtocol.CMD_EXTEND -> WatchCommand.EXTEND_REST
        WatchProtocol.CMD_NEXT -> WatchCommand.NEXT_EXERCISE
        else -> null
    }
}
