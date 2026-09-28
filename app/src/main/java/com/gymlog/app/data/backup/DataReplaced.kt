package com.gymlog.app.data.backup

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Counts completed imports for this process. Screens that hold on to rows (e.g. saved tab back
 * stacks) watch it instead of receiving a callback, because an import can finish after the
 * screen that started it was recreated (rotation).
 */
object DataReplaced {
    private val _generation = MutableStateFlow(0)
    val generation: StateFlow<Int> = _generation.asStateFlow()

    fun signal() {
        _generation.update { it + 1 }
    }
}
