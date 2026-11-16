// SPDX-License-Identifier: GPL-3.0-or-later
// Kebijakan izin per-tool: kategori aktif, mode konfirmasi, batas ukuran respons,
// dan gerbang persetujuan yang diisi oleh UI.
package app.snapgent.mcp

/** Mode per-tool yang bisa diatur pengguna. */
enum class ToolOverride(val wire: String) {
    ALLOW("allow"), CONFIRM("confirm"), DENY("deny");

    companion object {
        fun fromWire(value: String?): ToolOverride? = entries.firstOrNull { it.wire == value }
    }
}

data class ToolPolicy(
    val enabledCategories: Set<ToolCategory> = ToolCategory.entries.toSet(),
    val confirmCategories: Set<ToolCategory> = setOf(
        ToolCategory.WRITE,
        ToolCategory.DELETE,
        ToolCategory.EXEC,
        ToolCategory.GIT_WRITE,
        ToolCategory.REMOTE,
    ),
    /** true = hasil tool ditinjau pengguna sebelum dikirim balik ke chat (auto-send mati). */
    val reviewResults: Boolean = false,
    val maxResponseChars: Int = 60_000,
    /** Kunci = nama tool yang terlihat oleh AI (exposedName). */
    val toolOverrides: Map<String, ToolOverride> = emptyMap(),
) {
    enum class Decision { ALLOW, CONFIRM, DENY }

    fun decide(tool: ExposedTool): Decision {
        toolOverrides[tool.exposedName]?.let { override ->
            return when (override) {
                ToolOverride.ALLOW -> Decision.ALLOW
                ToolOverride.CONFIRM -> Decision.CONFIRM
                ToolOverride.DENY -> Decision.DENY
            }
        }
        val category = tool.tool.category
        return when {
            category !in enabledCategories -> Decision.DENY
            category in confirmCategories -> Decision.CONFIRM
            else -> Decision.ALLOW
        }
    }

    companion object {
        const val MIN_RESPONSE_CHARS = 2_000
        const val MAX_RESPONSE_CHARS = 1_000_000
    }
}

data class ApprovalRequest(
    val kind: Kind,
    val toolName: String,
    val title: String,
    val details: String,
) {
    enum class Kind { TOOL_CALL, RESULT_REVIEW }
}

/** Gerbang persetujuan manusia. Implementasi nyata ada di app (dialog). */
interface ApprovalGate {
    suspend fun requestApproval(request: ApprovalRequest): Boolean
}

/** Tool yang sudah diresolusi: nama tampilan AI + data tool asli. */
data class ExposedTool(val exposedName: String, val tool: McpTool, val server: McpServer)
