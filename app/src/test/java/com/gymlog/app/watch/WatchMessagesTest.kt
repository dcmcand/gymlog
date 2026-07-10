package com.gymlog.app.watch

import com.gymlog.app.data.SetStatus
import io.rebble.pebblekit2.common.model.PebbleDictionaryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchMessagesTest {

    @Test
    fun `buildContextMessage carries context and timer on pinned keys`() {
        val ctx = WatchContext("Bench", "60kg x 5", "Set 1 of 3")
        val m = buildContextMessage(ctx, durationSec = 90, running = true)
        assertEquals(PebbleDictionaryItem.Text("Bench"), m[WatchProtocol.KEY_EXERCISE_NAME])
        assertEquals(PebbleDictionaryItem.Text("60kg x 5"), m[WatchProtocol.KEY_TARGET_TEXT])
        assertEquals(PebbleDictionaryItem.Text("Set 1 of 3"), m[WatchProtocol.KEY_SET_LABEL])
        assertEquals(PebbleDictionaryItem.Int32(90), m[WatchProtocol.KEY_DURATION_SEC])
        assertEquals(PebbleDictionaryItem.Int32(1), m[WatchProtocol.KEY_RUNNING])
    }

    @Test
    fun `buildContextMessage with null context sends blanks and running 0`() {
        val m = buildContextMessage(null, durationSec = 0, running = false)
        assertEquals(PebbleDictionaryItem.Text(""), m[WatchProtocol.KEY_EXERCISE_NAME])
        assertEquals(PebbleDictionaryItem.Int32(0), m[WatchProtocol.KEY_RUNNING])
    }

    @Test
    fun `parseCommand maps cmd ints to statuses`() {
        assertEquals(
            SetStatus.EASY,
            parseCommand(mapOf(WatchProtocol.KEY_CMD to PebbleDictionaryItem.Int32(WatchProtocol.CMD_EASY))),
        )
        assertEquals(
            SetStatus.HARD,
            parseCommand(mapOf(WatchProtocol.KEY_CMD to PebbleDictionaryItem.Int32(WatchProtocol.CMD_HARD))),
        )
        assertNull(parseCommand(mapOf(99u to PebbleDictionaryItem.Int32(1))))
        assertNull(parseCommand(emptyMap()))
    }
}
