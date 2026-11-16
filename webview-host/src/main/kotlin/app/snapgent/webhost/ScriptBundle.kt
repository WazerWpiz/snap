// SPDX-License-Identifier: GPL-3.0-or-later
// Menyusun script yang disuntikkan ke WebView: shim chrome.* + bundle core/providers/main
// yang dijalankan setelah DOM siap (meniru document_idle pada content script extension).
// Hasil build di-cache (LRU kecil) supaya membuka/menutup tab tidak membaca aset
// berulang kali dari disk.
package app.snapgent.webhost

import android.content.Context
import org.json.JSONObject

object ScriptBundle {

    private const val ASSET_CACHE_SIZE = 16
    private const val STARTUP_CACHE_SIZE = 4

    private val assetCache = object : LinkedHashMap<String, String>(ASSET_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > ASSET_CACHE_SIZE
    }

    private val startupCache = object : LinkedHashMap<String, String>(STARTUP_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > STARTUP_CACHE_SIZE
    }

    fun readAsset(context: Context, path: String): String = synchronized(assetCache) {
        assetCache.getOrPut(path) {
            context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
    }

    fun startupScript(context: Context, site: ProviderSite): String = synchronized(startupCache) {
        startupCache.getOrPut(site.id) { build(context, site) }
    }

    private fun build(context: Context, site: ProviderSite): String {
        val shim = readAsset(context, "snapgent-android/shim.js")
        val css = site.styleFiles.joinToString("\n") { readAsset(context, "${SiteCatalog.ROOT}/$it") }
        val sources = site.isolatedScripts.joinToString("\n;\n") { readAsset(context, "${SiteCatalog.ROOT}/$it") }
        return buildString {
            append(shim).append('\n')
            append("(function () {\n")
            append("  if (window.top !== window) return;\n")
            append("  var boot = function () {\n")
            append("    if (window.__snapgentBooted) return;\n")
            append("    window.__snapgentBooted = true;\n")
            append("    try {\n")
            append("      var css = ").append(JSONObject.quote(css)).append(";\n")
            append("      var style = document.createElement('style');\n")
            append("      style.textContent = css;\n")
            append("      (document.head || document.documentElement).appendChild(style);\n")
            append("    } catch (e) { console.error('[snapgent-android] css', e); }\n")
            append("    try {\n")
            append(sources).append('\n')
            append("    } catch (e) { console.error('[snapgent-android]', e); }\n")
            append("  };\n")
            append("  if (document.readyState === 'loading') {\n")
            append("    document.addEventListener('DOMContentLoaded', boot, { once: true });\n")
            append("  } else {\n")
            append("    boot();\n")
            append("  }\n")
            append("})();\n")
        }
    }
}