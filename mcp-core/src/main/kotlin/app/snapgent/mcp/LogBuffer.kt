// SPDX-License-Identifier: GPL-3.0-or-later
package app.snapgent.mcp

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LogEntry(val timeMillis: Long, val level: String, val tag: String, val message: String)

/** Buffer log melingkar yang bisa diamati UI (layar Log). */
class LogBuffer(private val capacity: Int = 500) {
    private val state = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = state.asStateFlow()

    @Synchronized
    fun log(level: String, tag: String, message: String) {
        val next = state.value + LogEntry(System.currentTimeMillis(), level, tag, message)
        state.value = next.takeLast(capacity)
    }

    fun info(tag: String, message: String) = log("I", tag, message)
    fun warn(tag: String, message: String) = log("W", tag, message)
    fun error(tag: String, message: String) = log("E", tag, message)

    @Synchronized
    fun clear() {
        state.value = emptyList()
    }
}
