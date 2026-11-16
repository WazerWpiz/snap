// SPDX-License-Identifier: GPL-3.0-or-later
package app.snapgent.mcp

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeServer(
    override val id: String,
    private val toolNames: List<String>,
    private val category: ToolCategory = ToolCategory.READ,
    private val output: String = "ok-output",
) : McpServer {
    override val displayName = id
    override var isAlive = true
    override val error: String? = null
    override val tools: List<McpTool>
        get() = toolNames.map { McpTool(it, "desc $it", JsonObject(emptyMap()), category, id) }
    var lastArgs: JsonObject? = null

    override suspend fun start() = Unit
    override suspend fun stop() = Unit
    override suspend fun callTool(name: String, arguments: JsonObject, timeoutMs: Long): ToolResult {
        lastArgs = arguments
        return ToolResult(true, text = "$output:$name")
    }
}

private class FixedApprovals(private val answer: Boolean) : ApprovalGate {
    var asked = 0
    override suspend fun requestApproval(request: ApprovalRequest): Boolean {
        asked++
        return answer
    }
}

private object NoAdmin : ServerAdmin {
    override suspend fun restartAll() = true
    override suspend fun removeRemote(id: String) = false
}

class BridgeRouterTest {

    private fun router(
        registry: McpRegistry,
        policy: ToolPolicy = ToolPolicy(),
        approvals: ApprovalGate = FixedApprovals(true),
    ) = BridgeRouter(registry, { policy }, approvals, LogBuffer(), NoAdmin)

    @Test
    fun pingAndStatusFollowBackgroundContract() = runBlocking {
        val registry = McpRegistry(LogBuffer())
        registry.register(FakeServer("vscode", listOf("list_dir")))
        val router = router(registry)

        assertEquals(true, router.handle(buildJsonObject { put("type", "ping") })["ok"]?.jsonPrimitive?.content?.toBoolean())
        val status = router.handle(buildJsonObject { put("type", "status") })
        assertEquals("zs-status", status["type"]?.jsonPrimitive?.content)
        assertEquals(1, status["tools"]?.jsonPrimitive?.content?.toInt())
        assertEquals("vscode", (status["servers"] as JsonArray)[0].jsonObject["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun listToolsPrefixesCollidingNames() = runBlocking {
        val registry = McpRegistry(LogBuffer())
        registry.register(FakeServer("vscode", listOf("list_dir", "read_file")))
        registry.register(FakeServer("remote", listOf("read_file")))
        val names = registry.catalog().map { it.exposedName }.sorted()
        assertEquals(listOf("list_dir", "remote/read_file", "vscode/read_file"), names)
    }

    @Test
    fun callToolRoutesToServerAndTruncatesResponse() = runBlocking {
        val registry = McpRegistry(LogBuffer())
        val server = FakeServer("vscode", listOf("read_file"), output = "x".repeat(5_000))
        registry.register(server)
        val router = router(registry, ToolPolicy(maxResponseChars = 2_000))

        val reply = router.handle(
            buildJsonObject {
                put("type", "call_tool")
                put("name", "read_file")
                put("server", "vscode")
                put("arguments", buildJsonObject { put("path", "a.txt") })
            },
        )
        assertEquals(true, reply["ok"]?.jsonPrimitive?.content?.toBoolean())
        val text = reply["text"]?.jsonPrimitive?.content.orEmpty()
        assertTrue(text.startsWith("x".repeat(2_000)))
        assertTrue(text.contains("dipotong"))
        assertEquals("a.txt", server.lastArgs?.get("path")?.jsonPrimitive?.content)
    }

    @Test
    fun deniedCategoryNeverRunsTool() = runBlocking {
        val registry = McpRegistry(LogBuffer())
        val server = FakeServer("vscode", listOf("delete_file"), category = ToolCategory.DELETE)
        registry.register(server)
        val router = router(registry, ToolPolicy(enabledCategories = ToolCategory.entries.toSet() - ToolCategory.DELETE))

        val reply = router.handle(buildJsonObject { put("type", "call_tool"); put("name", "delete_file") })
        assertEquals(false, reply["ok"]?.jsonPrimitive?.content?.toBoolean())
        assertEquals("denied", reply["kind"]?.jsonPrimitive?.content)
        assertEquals(null, server.lastArgs)
    }

    @Test
    fun confirmationRejectedBlocksWriteTool() = runBlocking {
        val registry = McpRegistry(LogBuffer())
        val server = FakeServer("vscode", listOf("write_file"), category = ToolCategory.WRITE)
        registry.register(server)
        val approvals = FixedApprovals(false)

        val reply = router(registry, approvals = approvals).handle(
            buildJsonObject { put("type", "call_tool"); put("name", "write_file") },
        )
        assertEquals(1, approvals.asked)
        assertFalse(reply["ok"]?.jsonPrimitive?.content?.toBoolean() ?: true)
        assertEquals(null, server.lastArgs)
    }

    @Test
    fun unknownToolReturnsUnknownKind() = runBlocking {
        val reply = router(McpRegistry(LogBuffer())).handle(
            buildJsonObject { put("type", "call_tool"); put("name", "nope") },
        )
        assertEquals("unknown_tool", reply["kind"]?.jsonPrimitive?.content)
        assertEquals(JsonPrimitive(false), reply["ok"])
    }
}
