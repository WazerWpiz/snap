// SPDX-License-Identifier: GPL-3.0-or-later
// Skema 28 tool diambil dari resource workspace-tools.json (hasil scripts/gen-workspace-schemas.py
// terhadap vscode_mcp.py upstream), jadi nama dan parameter tetap sinkron dengan upstream.
package app.snapgent.workspace

import app.snapgent.mcp.McpJson
import app.snapgent.mcp.McpTool
import app.snapgent.mcp.ToolCategory
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

object WorkspaceSchemas {
    private val writeTools = setOf(
        "create_file", "write_file", "edit_file", "create_directory", "move_file", "copy_file", "apply_patch",
    )
    private val execTools = setOf("run_command", "run_project_script", "run_tests")
    private val gitWriteTools = setOf("git_add", "git_commit")

    fun categoryOf(name: String): ToolCategory = when (name) {
        in writeTools -> ToolCategory.WRITE
        "delete_file" -> ToolCategory.DELETE
        in execTools -> ToolCategory.EXEC
        in gitWriteTools -> ToolCategory.GIT_WRITE
        else -> ToolCategory.READ
    }

    fun tools(serverId: String): List<McpTool> = baseTools.map { it.copy(serverId = serverId) }

    private val baseTools: List<McpTool> by lazy { load() }

    private fun load(): List<McpTool> {
        val text = WorkspaceSchemas::class.java.getResourceAsStream("/workspace-tools.json")
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: error("workspace-tools.json tidak ditemukan di resource")
        val array = McpJson.parseToJsonElement(text) as JsonArray
        return array.map { element ->
            val obj = element as JsonObject
            val name = (obj["name"] as JsonPrimitive).contentOrNull.orEmpty()
            McpTool(
                name = name,
                description = (obj["description"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                inputSchema = obj["inputSchema"] as JsonObject,
                category = categoryOf(name),
                serverId = "vscode",
            )
        }
    }
}
