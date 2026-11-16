// SPDX-License-Identifier: GPL-3.0-or-later
// Klien MCP "Streamable HTTP" (spesifikasi MCP 2025-03-26): POST JSON-RPC ke satu URL,
// balasan berupa application/json atau text/event-stream (SSE). stdio TIDAK didukung di Android.
package app.snapgent.remote

import app.snapgent.mcp.ImageOut
import app.snapgent.mcp.McpJson
import app.snapgent.mcp.McpServer
import app.snapgent.mcp.McpTool
import app.snapgent.mcp.ToolCategory
import app.snapgent.mcp.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class RemoteMcpServer(
    override val id: String,
    override val displayName: String,
    private val endpoint: String,
    private val headers: Map<String, String>,
    private val http: OkHttpClient,
) : McpServer {

    @Volatile private var alive = false
    @Volatile private var lastError: String? = null
    @Volatile private var toolList: List<McpTool> = emptyList()
    @Volatile private var sessionId: String? = null
    private val nextId = AtomicInteger(1)

    override val isAlive: Boolean get() = alive
    override val error: String? get() = lastError
    override val tools: List<McpTool> get() = toolList

    override suspend fun start() {
        withContext(Dispatchers.IO) {
            try {
                sessionId = null
                request("initialize", buildJsonObject {
                    put("protocolVersion", PROTOCOL_VERSION)
                    put("capabilities", buildJsonObject { })
                    put("clientInfo", buildJsonObject {
                        put("name", "snapgent-android")
                        put("version", "1.0")
                    })
                }, INIT_TIMEOUT_MS)
                notify("notifications/initialized")
                val result = request("tools/list", buildJsonObject { }, INIT_TIMEOUT_MS)
                toolList = parseTools(result).map { it.copy(serverId = id) }
                alive = true
                lastError = null
            } catch (e: Exception) {
                alive = false
                toolList = emptyList()
                lastError = e.message ?: e.javaClass.simpleName
            }
        }
    }

    override suspend fun stop() {
        alive = false
        toolList = emptyList()
        sessionId = null
    }

    override suspend fun callTool(name: String, arguments: JsonObject, timeoutMs: Long): ToolResult {
        return withContext(Dispatchers.IO) {
            try {
                val params = buildJsonObject {
                    put("name", name)
                    put("arguments", arguments)
                }
                val result = request("tools/call", params, timeoutMs)
                parseCallResult(result)
            } catch (e: Exception) {
                ToolResult(false, kind = "tool_error", error = e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** Panggilan JSON-RPC blocking; dipanggil dari Dispatchers.IO. */
    private fun request(method: String, params: JsonObject, timeoutMs: Long): JsonObject {
        val id = nextId.getAndIncrement()
        val body = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            put("params", params)
        }
        val response = execute(body.toString(), timeoutMs)
        response.use { resp ->
            if (!resp.isSuccessful) {
                val snippet = resp.body?.string()?.take(200).orEmpty()
                throw IOException("HTTP ${resp.code}: $snippet")
            }
            val payload = resp.body?.string().orEmpty()
            val contentType = resp.header("Content-Type").orEmpty()
            val message = if (contentType.contains("text/event-stream")) {
                findSseResponse(payload, id)
            } else {
                McpJson.parseToJsonElement(payload).jsonObject
            } ?: throw IOException("Tidak ada balasan untuk $method")
            message["error"]?.let { errorElement ->
                val msg = (errorElement as? JsonObject)?.get("message")?.let { (it as? JsonPrimitive)?.contentOrNull }
                throw IOException(msg ?: "JSON-RPC error")
            }
            return message["result"]?.let { it as? JsonObject } ?: buildJsonObject { }
        }
    }

    private fun notify(method: String) {
        val body = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", method)
            put("params", buildJsonObject { })
        }
        execute(body.toString(), INIT_TIMEOUT_MS).use { resp ->
            if (!resp.isSuccessful && resp.code != 202) throw IOException("HTTP ${resp.code} pada $method")
        }
    }

    private fun execute(json: String, timeoutMs: Long): okhttp3.Response {
        val builder = Request.Builder()
            .url(endpoint)
            .post(json.toRequestBody(JSON_MEDIA))
            .header("Accept", "application/json, text/event-stream")
            .header("MCP-Protocol-Version", PROTOCOL_VERSION)
        headers.forEach { (key, value) -> builder.header(key, value) }
        sessionId?.let { builder.header("Mcp-Session-Id", it) }
        // Pakai client bersama: router sudah membatasi waktu total panggilan
        // (withTimeout), sehingga tiap request tidak perlu client baru.
        val response = http.newCall(builder.build()).execute()
        response.header("Mcp-Session-Id")?.let { sessionId = it }
        return response
    }

    companion object {
        const val PROTOCOL_VERSION = "2025-03-26"
        private const val INIT_TIMEOUT_MS = 15_000L
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        /** Cari balasan JSON-RPC dengan id yang cocok di dalam aliran SSE. */
        internal fun findSseResponse(payload: String, id: Int): JsonObject? {
            val dataLines = StringBuilder()
            fun flush(): JsonObject? {
                if (dataLines.isEmpty()) return null
                val parsed = runCatching { McpJson.parseToJsonElement(dataLines.toString()).jsonObject }.getOrNull()
                dataLines.clear()
                return parsed?.takeIf { (it["id"] as? JsonPrimitive)?.contentOrNull == id.toString() }
            }
            for (line in payload.lineSequence()) {
                when {
                    line.isEmpty() -> flush()?.let { return it }
                    line.startsWith("data:") -> {
                        if (dataLines.isNotEmpty()) dataLines.append('\n')
                        dataLines.append(line.removePrefix("data:").trimStart())
                    }
                }
            }
            return flush()
        }

        internal fun parseTools(result: JsonObject): List<McpTool> {
            val array = result["tools"] as? JsonArray ?: return emptyList()
            return array.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val name = (obj["name"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                McpTool(
                    name = name,
                    description = (obj["description"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                    inputSchema = obj["inputSchema"] as? JsonObject ?: buildJsonObject { put("type", "object") },
                    category = ToolCategory.REMOTE,
                    serverId = "",
                )
            }
        }

        internal fun parseCallResult(result: JsonObject): ToolResult {
            val content: List<JsonElement> = (result["content"] as? JsonArray)?.toList().orEmpty()
            val texts = mutableListOf<String>()
            val images = mutableListOf<ImageOut>()
            for (item in content) {
                val obj = item as? JsonObject ?: continue
                when ((obj["type"] as? JsonPrimitive)?.contentOrNull) {
                    "text" -> (obj["text"] as? JsonPrimitive)?.contentOrNull?.let { texts += it }
                    "image" -> {
                        val data = (obj["data"] as? JsonPrimitive)?.contentOrNull
                        if (data != null) {
                            images += ImageOut(data, (obj["mimeType"] as? JsonPrimitive)?.contentOrNull ?: "image/png")
                        }
                    }
                }
            }
            val text = texts.joinToString("\n")
            val isError = (result["isError"] as? JsonPrimitive)?.contentOrNull == "true"
            return if (isError) {
                ToolResult(false, kind = "tool_error", error = text.ifEmpty { "MCP tool failed." })
            } else {
                ToolResult(true, text = text, images = images)
            }
        }
    }
}
