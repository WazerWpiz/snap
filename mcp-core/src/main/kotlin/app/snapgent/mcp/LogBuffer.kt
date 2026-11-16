// SPDX-License-Identifier: GPL-3.0-or-later
package app.snapgent.mcp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LogEntry(
    /** Nomor urut monotonik: dipakai sebagai key stabil di LazyColumn. */
    val seq: Long,
    val timeMillis: Long,
    val level: String,
    val tag: String,
    val message: String,
)

/**
 * Buffer log melingkar yang bisa diamati UI (layar Log).
 *
 * Penyimpanan internal memakai ArrayDeque dan snapshot hanya dibuat bila ada
 * yang mendengarkan, sehingga logging intensif (banyak panggilan tool) tidak
 * menyalin list 500 baris tiap kali satu baris log muncul.
 */
class LogBuffer(private val capacity: Int = 500, scope: CoroutineScope? = null) {
    private val deque = ArrayDeque<LogEntry>()
    private val state = MutableStateFlow<List<LogEntry>>(emptyList())
    private var nextSeq = 0L

    init {
        if (scope != null) {
            // Saat UI mulai mendengarkan, kirim snapshot penuh sekali.
            scope.launch {
                state.subscriptionCount.collect { count ->
                    if (count > 0) publish()
                }
            }
        }
    }

    val entries: StateFlow<List<LogEntry>> = state.asStateFlow()

    @Synchronized
    fun log(level: String, tag: String, message: String) {
        deque.addLast(LogEntry(nextSeq++, System.currentTimeMillis(), level, tag, message))
        while (deque.size > capacity) deque.removeFirst()
        publish()
    }

    fun info(tag: String, message: String) = log("I", tag, message)
    fun warn(tag: String, message: String) = log("W", tag, message)
    fun error(tag: String, message: String) = log("E", tag, message)

    @Synchronized
    fun clear() {
        deque.clear()
        state.value = emptyList()
    }

    private fun publish() {
        if (state.subscriptionCount.value == 0) return
        state.value = if (deque.isEmpty()) emptyList() else deque.toList()
    }
}