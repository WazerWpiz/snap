// SPDX-License-Identifier: GPL-3.0-or-later
// Membaca manifest.json upstream (vendor/snapgent-extension) dan mengelompokkan content_scripts
// per situs AI. Dengan begitu daftar provider ikut berubah otomatis saat vendor diperbarui.
package app.snapgent.webhost

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ProviderSite(
    /** Nama file provider tanpa ekstensi, mis. "claude" untuk providers/claude.js. */
    val id: String,
    val displayName: String,
    val startUrl: String,
    /** Origin yang boleh menerima shim dan bridge, mis. "https://claude.ai". */
    val allowedOrigins: Set<String>,
    /** Script MAIN world (tap jaringan/CodeMirror), dijalankan di document start. */
    val mainWorldScripts: List<String>,
    /** Script isolated world yang digabung dalam satu bundle, sesuai urutan manifest. */
    val isolatedScripts: List<String>,
    val styleFiles: List<String>,
)

object SiteCatalog {
    const val ROOT = "snapgent-extension"

    private val labels = mapOf(
        "chatgpt" to "ChatGPT",
        "claude" to "Claude",
        "gemini" to "Google Gemini",
        "kimi" to "Kimi",
        "glm" to "GLM (Z.ai)",
        "arena" to "Arena",
        "meta" to "Meta AI",
        "ptero" to "Ptero",
        "grok" to "Grok",
        "mistral" to "Mistral (eksperimental)",
        "qwen" to "Qwen",
        "deepseek" to "DeepSeek",
    )

    fun load(context: Context): List<ProviderSite> {
        val text = context.assets.open("$ROOT/manifest.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val entries = JSONObject(text).optJSONArray("content_scripts") ?: JSONArray()
        val groups = LinkedHashMap<String, Group>()
        for (i in 0 until entries.length()) {
            val entry = entries.getJSONObject(i)
            val matches = entry.getJSONArray("matches").toStringList()
            val group = groups.getOrPut(matches.sorted().joinToString("|")) { Group(matches) }
            val js = entry.getJSONArray("js").toStringList()
            if (entry.optString("world") == "MAIN") group.main += js else group.isolated += js
            entry.optJSONArray("css")?.let { group.css += it.toStringList() }
        }
        return groups.values.mapNotNull { group ->
            val id = group.isolated.firstOrNull { it.startsWith("providers/") }
                ?.removePrefix("providers/")?.removeSuffix(".js")
                ?: return@mapNotNull null
            ProviderSite(
                id = id,
                displayName = labels[id] ?: id.replaceFirstChar { it.uppercaseChar() },
                startUrl = group.matches.first().removeSuffix("*"),
                allowedOrigins = group.matches.map { it.removeSuffix("/*") }.toSet(),
                mainWorldScripts = group.main.distinct(),
                isolatedScripts = group.isolated.distinct(),
                styleFiles = group.css.distinct(),
            )
        }
    }

    private class Group(val matches: List<String>) {
        val main = mutableListOf<String>()
        val isolated = mutableListOf<String>()
        val css = mutableListOf<String>()
    }

    private fun JSONArray.toStringList(): List<String> = (0 until length()).map { getString(it) }
}
