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

    // phone -> watch
    const val KEY_EXERCISE_NAME = 0
    const val KEY_TARGET_TEXT = 1
    const val KEY_SET_LABEL = 2
    const val KEY_DURATION_SEC = 3
    const val KEY_RUNNING = 4

    // watch -> phone
    const val KEY_CMD = 10
    const val CMD_EASY = 1
    const val CMD_HARD = 2

    const val REST_SECONDS = 90
}
