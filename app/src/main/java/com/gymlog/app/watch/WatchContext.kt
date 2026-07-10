package com.gymlog.app.watch

/** What the watch shows for the current set. */
data class WatchContext(
    val exerciseName: String,
    val targetText: String,
    val setLabel: String,
)
