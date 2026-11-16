// SPDX-License-Identifier: GPL-3.0-or-later
// Workspace aktif: folder privat aplikasi atau folder yang dipilih lewat SAF (DocumentFile).
package app.snapgent.android

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import app.snapgent.mcp.LogBuffer
import app.snapgent.settings.AppSettings
import app.snapgent.workspace.JavaWorkspaceFs
import app.snapgent.workspace.WorkspaceFs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class WorkspaceStatus(val label: String, val ready: Boolean)

class WorkspaceManager(private val context: Context, private val log: LogBuffer) {
    @Volatile private var fs: WorkspaceFs? = null
    @Volatile private var key: String = ""

    private val statusState = MutableStateFlow(WorkspaceStatus("Belum dipilih", false))
    val status: StateFlow<WorkspaceStatus> = statusState.asStateFlow()

    fun currentFs(): WorkspaceFs? = fs

    /** Dipanggil setiap pengaturan berubah. Membuka ulang workspace hanya bila sumbernya berganti. */
    fun restore(settings: AppSettings) {
        val wantedKey = when (settings.workspaceKind) {
            "private" -> "private"
            "saf" -> "saf:${settings.workspaceUri}"
            else -> "none"
        }
        if (wantedKey != key) {
            key = wantedKey
            fs = when (settings.workspaceKind) {
                "private" -> JavaWorkspaceFs(privateDir(), "workspace privat")
                "saf" -> settings.workspaceUri?.let { uri ->
                    runCatching { SafWorkspaceFs(context, Uri.parse(uri)) }
                        .onFailure { log.error("workspace", "SAF tidak dapat dibuka: ${it.message}") }
                        .getOrNull()
                }
                else -> null
            }
            log.info("workspace", describe(settings))
        }
        statusState.value = WorkspaceStatus(describe(settings), fs != null && canAccess(settings))
    }

    fun describe(settings: AppSettings): String = when (settings.workspaceKind) {
        "private" -> "Folder privat aplikasi"
        "saf" -> "Folder: ${settings.workspaceName ?: "tanpa nama"}"
        else -> "Belum dipilih"
    }

    private fun canAccess(settings: AppSettings): Boolean = when (settings.workspaceKind) {
        "private" -> true
        "saf" -> settings.workspaceUri?.let { uri ->
            runCatching { DocumentFile.fromTreeUri(context, Uri.parse(uri))?.canRead() }.getOrNull()
        } == true
        else -> false
    }

    fun privateDir(): File = File(context.filesDir, "workspace").apply { mkdirs() }
}
