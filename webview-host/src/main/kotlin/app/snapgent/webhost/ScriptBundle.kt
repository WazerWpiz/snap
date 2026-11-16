// SPDX-License-Identifier: GPL-3.0-or-later
// Menyusun script yang disuntikkan ke WebView: shim chrome.* + bundle core/providers/main
// yang dijalankan setelah DOM siap (meniru document_idle pada content script extension).
package app.snapgent.webhost

import android.content.Context
import org.json.JSONObject

object ScriptBundle {

    fun readAsset(context: Context, path: String): String =
        context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }

    fun startupScript(context: Context, site: ProviderSite): String {
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
