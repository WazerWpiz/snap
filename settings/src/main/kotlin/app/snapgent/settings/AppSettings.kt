// SPDX-License-Identifier: GPL-3.0-or-later
// Model pengaturan aplikasi. Disimpan sebagai satu JSON di DataStore; nilai string dipakai
// agar format file konfigurasi tetap stabil dan mudah dibaca.
package app.snapgent.settings

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    /** system | light | dark */
    val themeMode: String = "system",
    /** Jalankan foreground service agar sesi tidak mati saat layar mati. */
    val keepAlive: Boolean = false,
    /** Server workspace bawaan (id "vscode") aktif. */
    val builtinWorkspaceEnabled: Boolean = true,
    /** none | private | saf */
    val workspaceKind: String = "none",
    val workspaceUri: String? = null,
    val workspaceName: String? = null,
    /** Kategori tool yang diizinkan (wire name dari ToolCategory). */
    val enabledCategories: Set<String> = ALL_CATEGORIES,
    /** Kategori yang meminta konfirmasi manusia sebelum dijalankan. */
    val confirmCategories: Set<String> = DEFAULT_CONFIRM,
    /** Mode per-tool: nama tool -> allow | confirm | deny. */
    val toolOverrides: Map<String, String> = emptyMap(),
    /** true = hasil tool langsung dikirim ke chat. false = pengguna meninjau dulu. */
    val autoSendResults: Boolean = true,
    val maxResponseChars: Int = 60_000,
    val remoteServers: List<RemoteServerEntry> = emptyList(),
) {
    companion object {
        val ALL_CATEGORIES = setOf("read", "write", "delete", "exec", "git_write", "remote")
        val DEFAULT_CONFIRM = setOf("write", "delete", "exec", "git_write", "remote")
    }
}

@Serializable
data class RemoteServerEntry(
    val id: String,
    val name: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val enabled: Boolean = true,
)
