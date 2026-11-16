// SPDX-License-Identifier: GPL-3.0-or-later
// Model dasar: kategori tool, definisi tool, hasil panggilan, dan kontrak server MCP.
// Pengganti protokol bridge.py (WebSocket) untuk sisi Android.
package app.snapgent.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

val McpJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
}

/** Kategori izin. [wire] dipakai di pengaturan dan file konfigurasi. */
enum class ToolCategory(val wire: String, val label: String) {
    READ("read", "Baca"),
    WRITE("write", "Tulis / edit"),
    DELETE("delete", "Hapus"),
    EXEC("exec", "Jalankan perintah"),
    GIT_WRITE("git_write", "Git stage / commit"),
    REMOTE("remote", "MCP remote");

    companion object {
        fun fromWire(value: String): ToolCategory? = entries.firstOrNull { it.wire == value }
    }
}

data class McpTool(
    /** Nama mentah dari server (tanpa prefix server). */
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
    val category: ToolCategory,
    val serverId: String,
)

data class ImageOut(val data: String, val mimeType: String)

/** Hasil satu panggilan tool. Bentuknya sama dengan balasan bridge ke core (ok/text/images atau ok/kind/error). */
data class ToolResult(
    val ok: Boolean,
    val text: String = "",
    val images: List<ImageOut> = emptyList(),
    val kind: String? = null,
    val error: String? = null,
)

/** Kontrak server MCP yang bisa didaftarkan ke [McpRegistry]. */
interface McpServer {
    val id: String
    val displayName: String
    val isAlive: Boolean
    val error: String?
    val tools: List<McpTool>

    suspend fun start()
    suspend fun stop()
    suspend fun callTool(name: String, arguments: JsonObject, timeoutMs: Long): ToolResult
}
