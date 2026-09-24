package com.gymlog.app.watch

import java.util.UUID

/**
 * Message contract shared with the Alloy watchapp (in `pebble/`). The watchapp mirrors
 * these exact integer keys by pinning them in a `Map` passed to the Alloy `Message`
 * constructor (array-form keys would map to `10000+index` and never align - see the
 * interop spike results). Keep both sides in sync.
 */
object WatchProtocol {
    val WATCHAPP_UUID: UUID = UUID.fromString("59e50327-68d4-4e6e-8a32-7331cb13194e")

    // phone -> watch (UInt keys to match PebbleKit's Map<UInt, ...>; avoids const .toUInt())
    val KEY_EXERCISE_NAME: UInt = 0u
    val KEY_TARGET_TEXT: UInt = 1u
    val KEY_SET_LABEL: UInt = 2u
    val KEY_DURATION_SEC: UInt = 3u
    val KEY_RUNNING: UInt = 4u

    // watch -> phone
    val KEY_CMD: UInt = 10u
    const val CMD_EASY = 1
    const val CMD_HARD = 2
    const val CMD_EXTEND = 3
    const val CMD_NEXT = 4

    const val REST_SECONDS = 90
}
