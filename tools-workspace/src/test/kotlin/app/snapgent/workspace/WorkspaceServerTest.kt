// SPDX-License-Identifier: GPL-3.0-or-later
package app.snapgent.workspace

import app.snapgent.mcp.LogBuffer
import app.snapgent.mcp.ToolCategory
import app.snapgent.mcp.ToolResult
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.eclipse.jgit.api.Git
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class WorkspaceServerTest {

    private lateinit var root: File
    private lateinit var server: WorkspaceServer

    @Before
    fun setUp() {
        root = Files.createTempDirectory("snapgent-ws").toFile()
        server = WorkspaceServer({ JavaWorkspaceFs(root) }, LogBuffer())
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun call(name: String, args: JsonObject = JsonObject(emptyMap())): ToolResult = runBlocking {
        server.callTool(name, args, 10_000)
    }

    @Test
    fun exposesTwentyEightToolsWithExpectedCategories() {
        val tools = WorkspaceSchemas.tools("vscode")
        assertEquals(28, tools.size)
        assertEquals(ToolCategory.DELETE, WorkspaceSchemas.categoryOf("delete_file"))
        assertEquals(ToolCategory.EXEC, WorkspaceSchemas.categoryOf("run_command"))
        assertEquals(ToolCategory.READ, WorkspaceSchemas.categoryOf("read_file"))
    }

    @Test
    fun writeThenReadRoundTrip() {
        val write = call("write_file", buildJsonObject { put("path", "src/a.txt"); put("content", "halo") })
        assertTrue(write.ok)
        val read = call("read_file", buildJsonObject { put("path", "src/a.txt") })
        assertEquals("halo", read.text)
    }

    @Test
    fun pathEscapingWorkspaceIsRejected() {
        val result = call("read_file", buildJsonObject { put("path", "../secret.txt") })
        assertFalse(result.ok)
        assertTrue(result.error.orEmpty().contains("escapes"))
    }

    @Test
    fun editFileRequiresUniqueMatchUnlessReplaceAll() {
        call("write_file", buildJsonObject { put("path", "t.txt"); put("content", "x x") })
        val ambiguous = call("edit_file", buildJsonObject { put("path", "t.txt"); put("old_text", "x"); put("new_text", "y") })
        assertFalse(ambiguous.ok)

        val all = call(
            "edit_file",
            buildJsonObject { put("path", "t.txt"); put("old_text", "x"); put("new_text", "y"); put("replace_all", true) },
        )
        assertTrue(all.ok)
        assertEquals("y y", call("read_file", buildJsonObject { put("path", "t.txt") }).text)
    }

    @Test
    fun applyPatchIsAtomic() {
        call("write_file", buildJsonObject { put("path", "p.txt"); put("content", "abc") })
        val patch = buildJsonObject {
            put("path", "p.txt")
            putJsonArrayOf(
                buildJsonObject { put("old_text", "a"); put("new_text", "1") },
                buildJsonObject { put("old_text", "zzz"); put("new_text", "2") },
            )
        }
        val result = call("apply_patch", patch)
        assertFalse(result.ok)
        assertEquals("abc", call("read_file", buildJsonObject { put("path", "p.txt") }).text)
    }

    @Test
    fun deleteRefusesGitFolder() {
        File(root, ".git").mkdirs()
        File(root, ".git/config").writeText("[core]")
        val result = call("delete_file", buildJsonObject { put("path", ".git/config") })
        assertFalse(result.ok)
        assertTrue(result.error.orEmpty().contains("protected"))
    }

    @Test
    fun listTreeSkipsIgnoredDirectories() {
        File(root, "node_modules").mkdirs()
        File(root, "node_modules/lib.js").writeText("")
        File(root, "src").mkdirs()
        File(root, "src/main.kt").writeText("")
        val text = call("list_tree", buildJsonObject { put("path", "."); put("max_depth", 2) }).text
        assertTrue(text.contains("src/main.kt"))
        assertFalse(text.contains("node_modules"))
    }

    @Test
    fun findFilesMatchesGlobAcrossFolders() {
        File(root, "a").mkdirs()
        File(root, "a/SKILL.md").writeText("")
        File(root, "b.txt").writeText("")
        val text = call("find_files", buildJsonObject { put("path", "."); put("pattern", "**/SKILL.md") }).text
        assertTrue(text.contains("a/SKILL.md"))
        assertFalse(text.contains("b.txt"))
    }

    @Test
    fun gitStatusReportsUntrackedFile() {
        Git.init().setDirectory(root).call().close()
        File(root, "new.txt").writeText("baru")
        val result = call("git_status")
        assertTrue(result.ok)
        assertTrue(result.text.contains("?? new.txt"))
    }
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putJsonArrayOf(vararg items: JsonObject) {
    put("edits", kotlinx.serialization.json.JsonArray(items.toList()))
}
