// SPDX-License-Identifier: GPL-3.0-or-later
// Menyusun script yang disuntikkan ke WebView: shim chrome.* + bundle core/providers/main.
//
// Pembagian penting untuk performa:
//  - shim.js (~4 KB) didaftarkan sebagai document-start script. Ukurannya kecil, jadi tidak
//    menunda skrip halaman, dan chrome.* sudah ada sebelum bundle isolasi berjalan.
//  - bundle isolasi (gabungan core/*.js + providers/*.js + CSS, ~480 KB untuk sebagian besar
//    provider) TIDAK didaftarkan sebagai document-start script. Document-start script
//    dikompilasi SEBELUM skrip situs berjalan, sehingga 480 KB itu dulu menunda first paint
//    dan hydration (situs terasa sangat lambat). Bundle ini sekarang disuntikkan lewat
//    evaluateJavascript setelah frame pertama halaman benar-benar digambar (lihat
//    GuardedWebViewClient.onPageFinished) — halaman situs mendapat main thread lebih dulu.
//
// Hasil build di-cache (LRU kecil) supaya membuka/menutup tab tidak membaca aset berulang kali.
package app.snapgent.webhost

import android.content.Context
import org.json.JSONObject

object ScriptBundle {

    private const val ASSET_CACHE_SIZE = 16
    private const val BOOT_CACHE_SIZE = 4

    private val assetCache = object : LinkedHashMap<String, String>(ASSET_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > ASSET_CACHE_SIZE
    }

    private val bootCache = object : LinkedHashMap<String, String>(BOOT_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > BOOT_CACHE_SIZE
    }

    fun readAsset(context: Context, path: String): String = synchronized(assetCache) {
        assetCache.getOrPut(path) {
            context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
    }

    /** Shim chrome.runtime/chrome.storage (~4 KB); aman dijalankan di document-start. */
    fun shim(context: Context): String = readAsset(context, "snapgent-android/shim.js")

    /**
     * Bundle isolasi (CSS + core/providers/main) sebagai satu IIFE yang idempoten.
     * Disuntikkan setelah halaman selesai dimuat, bukan di document-start.
     */
    fun bootScript(context: Context, site: ProviderSite): String = synchronized(bootCache) {
        bootCache.getOrPut(site.id) { build(context, site) }
    }

    private fun build(context: Context, site: ProviderSite): String {
        val css = site.styleFiles.joinToString("\n") { readAsset(context, "${SiteCatalog.ROOT}/$it") }
        val sources = site.isolatedScripts.joinToString("\n;\n") { readAsset(context, "${SiteCatalog.ROOT}/$it") }
        return buildString {
            append("(function () {\n")
            append("  if (window.top !== window) return;\n")
            append("  if (window.__snapgentBooted) return;\n")
            append("  window.__snapgentBooted = true;\n")
            append("  try {\n")
            append("    var css = ").append(JSONObject.quote(css)).append(";\n")
            append("    var style = document.createElement('style');\n")
            append("    style.textContent = css;\n")
            append("    (document.head || document.documentElement).appendChild(style);\n")
            append("  } catch (e) { console.error('[snapgent-android] css', e); }\n")
            append("  try {\n")
            append(sources).append('\n')
            append("  } catch (e) { console.error('[snapgent-android]', e); }\n")
            append("})();\n")
        }
    }
}
