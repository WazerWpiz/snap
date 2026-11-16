// SPDX-License-Identifier: GPL-3.0-or-later
// Gerbang persetujuan manusia: permintaan dari router menunggu jawaban dialog di UI.
package app.snapgent.android

import app.snapgent.mcp.ApprovalGate
import app.snapgent.mcp.ApprovalRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

class ApprovalCoordinator : ApprovalGate {
    class Pending internal constructor(
        val request: ApprovalRequest,
        internal val answer: CompletableDeferred<Boolean>,
    )

    private val queue = Mutex()
    private val state = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = state.asStateFlow()

    /** Satu permintaan per saat; batas waktu sama dengan batas tunggu core (menolak bila habis). */
    override suspend fun requestApproval(request: ApprovalRequest): Boolean = queue.withLock {
        val answer = CompletableDeferred<Boolean>()
        state.value = Pending(request, answer)
        try {
            withTimeoutOrNull(APPROVAL_TIMEOUT_MS) { answer.await() } ?: false
        } finally {
            state.value = null
        }
    }

    fun answer(allow: Boolean) {
        state.value?.answer?.complete(allow)
    }

    companion object {
        const val APPROVAL_TIMEOUT_MS = 90_000L
    }
}
