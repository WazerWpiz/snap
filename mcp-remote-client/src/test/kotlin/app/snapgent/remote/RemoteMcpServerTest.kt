// SPDX-License-Identifier: GPL-3.0-or-later
package app.snapgent.remote

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteMcpServerTest {

    private val initializeReply = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setHeader("Mcp-Session-Id", "session-1")
        .setBody("""{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-03-26","capabilities":{}}}""")

    private val toolsReply = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody("""{"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"ping_tool","description":"d","inputSchema":{"type":"object"}}]}}""")

    @Test
    fun startsAndListsToolsFromJsonResponses() = runBlocking {
        val server = MockWebServer()
        server.start()
        server.enqueue(initializeReply)
        server.enqueue(MockResponse().setResponseCode(202))
        server.enqueue(toolsReply)
        try {
            val remote = RemoteMcpServer("remote", "Remote", server.url("/mcp").toString(), emptyMap(), OkHttpClient())
            remote.start()
            assertTrue(remote.isAlive)
            assertEquals(listOf("ping_tool"), remote.tools.map { it.name })
            assertEquals("remote", remote.tools.first().serverId)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun readsToolCallResultFromSseStream() = runBlocking {
        val server = MockWebServer()
        server.start()
        server.enqueue(initializeReply)
        server.enqueue(MockResponse().setResponseCode(202))
        server.enqueue(toolsReply)
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":3,\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"hi\"}]}}\n\n"),
        )
        try {
            val remote = RemoteMcpServer("remote", "Remote", server.url("/mcp").toString(), emptyMap(), OkHttpClient())
            remote.start()
            val result = remote.callTool("ping_tool", JsonObject(emptyMap()), 5_000)
            assertTrue(result.ok)
            assertEquals("hi", result.text)
        } finally {
            server.shutdown()
        }
    }
}
