// SPDX-License-Identifier: GPL-3.0-or-later
// Router pesan dari shim JS (chrome.runtime.sendMessage). Bentuk balasan mengikuti
// kontrak background.js upstream: ping, status, list_tools, call_tool, restart_mcp,
// reconnect, add_server, remove_server.
package app.snapgent.mcp

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Operasi siklus hidup yang butuh konteks app (restart & hapus server remote). */
interface ServerAdmin {
    /** Restart semua server. Mengembalikan true bila ada server yang aktif. */
    suspend fun restartAll(): Boolean

    /** Hapus server remote dari konfigurasi. Mengembalikan false bila id tidak ditemukan. */
    suspend fun removeRemote(id: String): Boolean
}

class BridgeRouter(
    private val registry: McpRegistry,
    private val policy: () -> ToolPolicy,
    private val approvals: ApprovalGate,
    private val log: LogBuffer,
    private val admin: ServerAdmin,
    private val bridgeVersion: String = "android-1.0",
) {
    suspend fun handle(message: JsonObject): JsonObject = when (message.str("type")) {
        "ping" -> buildJsonObject { put("ok", true) }
        "status" -> statusJson()
        "list_tools" -> listToolsJson()
        "call_tool" -> callTool(message)
        "restart_mcp" -> restartAndReport()
        "reconnect" -> buildJsonObject { put("ok", true) }
        "add_server" -> failJson("Server stdio tidak didukung di Android. Tambahkan server HTTP lewat menu MCP.")
        "remove_server" -> removeServer(message)
        else -> failJson("unknown message")
    }

    /** Status untuk popup/chat (bentuk sama dengan statusObj() di background.js). */
    fun statusJson(): JsonObject {
        val all = registry.all()
        val catalogSize = registry.catalog().size
        return buildJsonObject {
            put("type", "zs-status")
            put("connected", true)
            put("mcpAlive", all.any { it.isAlive })
            put("bridgeVersion", bridgeVersion)
            put("studio", JsonNull)
            put("studioApp", JsonNull)
            put("studioProc", JsonNull)
            put("tools", catalogSize)
            putJsonArray("servers") {
                all.forEach { server ->
                    addJsonObject {
                        put("id", server.id)
                        put("name", server.displayName)
                        put("alive", server.isAlive)
                        put("tools", if (server.isAlive) server.tools.size else 0)
                    }
                }
            }
        }
    }

    private fun listToolsJson(): JsonObject {
        val catalog = registry.catalog()
        return buildJsonObject {
            put("ok", true)
            putJsonArray("tools") {
                catalog.forEach { exposed ->
                    addJsonObject {
                        put("name", exposed.exposedName)
                        put("server", exposed.server.id)
                        put("description", exposed.tool.description)
                        put("inputSchema", exposed.tool.inputSchema)
                    }
                }
            }
        }
    }

    private suspend fun callTool(message: JsonObject): JsonObject {
        val name = message.str("name")
        if (name.isNullOrBlank()) {
            return resultJson(ToolResult(false, kind = "tool_error", error = "Invalid tool call."))
        }
        val arguments = (message["arguments"] as? JsonObject) ?: JsonObject(emptyMap())
        val preferredServer = message.str("server")
        val timeoutMs = (message.long("timeout") ?: 120_000L).coerceIn(1_000L, 300_000L)

        val exposed = registry.resolve(name, preferredServer)
            ?: return resultJson(ToolResult(false, kind = "unknown_tool", error = "Unknown or unavailable MCP tool: $name"))
        if (!preferredServer.isNullOrEmpty() && exposed.server.id != preferredServer) {
            return resultJson(
                ToolResult(false, kind = "unknown_tool", error = "MCP tool not available for the selected server: $name"),
            )
        }

        val current = policy()
        when (current.decide(exposed)) {
            ToolPolicy.Decision.DENY -> {
                log.warn("policy", "diblokir: ${exposed.exposedName}")
                return resultJson(denied("Tool '${exposed.exposedName}' diblokir oleh pengaturan izin."))
            }
            ToolPolicy.Decision.CONFIRM -> {
                val approved = approvals.requestApproval(
                    ApprovalRequest(
                        kind = ApprovalRequest.Kind.TOOL_CALL,
                        toolName = exposed.exposedName,
                        title = "Izinkan AI menjalankan ${exposed.exposedName}?",
                        details = arguments.toString().take(2_000),
                    ),
                )
                if (!approved) {
                    log.warn("policy", "ditolak pengguna: ${exposed.exposedName}")
                    return resultJson(denied("Dibatalkan oleh pengguna."))
                }
            }
            ToolPolicy.Decision.ALLOW -> Unit
        }

        log.info("tool", "-> ${exposed.exposedName} ${arguments.toString().take(160)}")
        val raw = try {
            withTimeout(timeoutMs) { exposed.server.callTool(exposed.tool.name, arguments, timeoutMs) }
        } catch (e: TimeoutCancellationException) {
            log.error("tool", "timeout: ${exposed.exposedName}")
            return resultJson(ToolResult(false, kind = "timeout", error = "Timeout setelah $timeoutMs ms"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("tool", "${exposed.exposedName}: ${e.message}")
            return resultJson(ToolResult(false, kind = "tool_error", error = e.message ?: e.javaClass.simpleName))
        }

        if (!raw.ok) {
            log.warn("tool", "<- ${exposed.exposedName} gagal: ${raw.error}")
            return resultJson(raw.copy(kind = raw.kind ?: "tool_error"))
        }

        if (current.reviewResults) {
            val approved = approvals.requestApproval(
                ApprovalRequest(
                    kind = ApprovalRequest.Kind.RESULT_REVIEW,
                    toolName = exposed.exposedName,
                    title = "Kirim hasil ${exposed.exposedName} ke chat?",
                    details = raw.text.take(4_000),
                ),
            )
            if (!approved) return resultJson(denied("Hasil ditolak pengguna sebelum dikirim."))
        }

        val limit = current.maxResponseChars.coerceIn(ToolPolicy.MIN_RESPONSE_CHARS, ToolPolicy.MAX_RESPONSE_CHARS)
        val text = if (raw.text.length > limit) {
            raw.text.take(limit) +
                "\n[dipotong: ${raw.text.length} karakter, batas $limit. Ubah di Pengaturan > Batas respons]"
        } else {
            raw.text
        }
        log.info("tool", "<- ${exposed.exposedName} (${text.length} karakter)")
        return resultJson(raw.copy(text = text))
    }

    private suspend fun restartAndReport(): JsonObject {
        val alive = admin.restartAll()
        return buildJsonObject { put("ok", true); put("alive", alive) }
    }

    private suspend fun removeServer(message: JsonObject): JsonObject {
        val id = message.str("server_id").orEmpty()
        if (id.isEmpty() || id == "vscode") return failJson("Invalid or protected MCP server id.")
        val removed = admin.removeRemote(id)
        return if (removed) {
            buildJsonObject { put("ok", true); put("restarting", false) }
        } else {
            failJson("Server '$id' tidak ditemukan.")
        }
    }

    private fun denied(message: String) = ToolResult(false, kind = "denied", error = message)

    companion object {
        fun failJson(message: String): JsonObject = buildJsonObject {
            put("ok", false)
            put("error", message)
        }

        fun resultJson(result: ToolResult): JsonObject = buildJsonObject {
            put("ok", result.ok)
            if (result.ok) {
                put("text", result.text)
                putJsonArray("images") {
                    result.images.forEach { image ->
                        addJsonObject {
                            put("data", image.data)
                            put("mimeType", image.mimeType)
                        }
                    }
                }
            } else {
                result.kind?.let { put("kind", it) }
                put("error", result.error ?: "MCP tool failed.")
            }
        }

        internal fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

        internal fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
    }
}
