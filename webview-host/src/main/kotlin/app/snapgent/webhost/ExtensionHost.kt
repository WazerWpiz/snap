// SPDX-License-Identifier: GPL-3.0-or-later
// Jembatan JS <-> Kotlin (pengganti background.js + WebSocket). Metode @JavascriptInterface
// hanya melayani halaman dengan origin provider yang sah; halaman lain ditolak.
package app.snapgent.webhost

import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.json.JSONObject

class ExtensionHost(
    private val site: ProviderSite,
    private val storage: WebStorage,
    private val scope: CoroutineScope,
    private val handler: suspend (JsonObject) -> JsonObject,
    private val logSink: (String) -> Unit,
) {
    @Volatile private var view: WebView? = null
    @Volatile private var currentUrl: String? = null

    internal fun attachView(webView: WebView) {
        view = webView
    }

    /** Dipanggil saat WebView tab di-destroy: cegah akses ke view yang sudah mati. */
    fun detachView() {
        view = null
    }

    /** Dipanggil dari WebViewClient.onPageStarted untuk dokumen utama. */
    fun onPageStarted(url: String?) {
        currentUrl = url
    }

    fun isAllowedNow(): Boolean {
        val origin = originOf(currentUrl) ?: return false
        return origin in site.allowedOrigins
    }

    @JavascriptInterface
    fun request(id: Int, json: String) {
        if (!isAllowedNow()) {
            deliver(id, errorJson("bridge tidak tersedia untuk halaman ini"))
            return
        }
        scope.launch {
            val response = try {
                handler(Json.parseToJsonElement(json).jsonObject).toString()
            } catch (e: Exception) {
                logSink("[${site.id}] request gagal: ${e.message}")
                errorJson(e.message ?: e.javaClass.simpleName)
            }
            deliver(id, response)
        }
    }

    @JavascriptInterface
    fun storageAll(): String = if (isAllowedNow()) storage.snapshotJson() else "{}"

    @JavascriptInterface
    fun storageWrite(json: String) {
        if (isAllowedNow()) storage.putAllJson(json)
    }

    @JavascriptInterface
    fun storageRemove(json: String) {
        if (!isAllowedNow()) return
        val keys = runCatching { Json.parseToJsonElement(json) }
            .getOrNull()
            ?.let { element ->
                when (element) {
                    is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                    is JsonPrimitive -> listOfNotNull(element.contentOrNull)
                    else -> null
                }
            }
            ?: return
        storage.removeKeys(keys)
    }

    @JavascriptInterface
    fun logFromPage(message: String) {
        if (isAllowedNow()) logSink("[${site.id}] ${message.take(500)}")
    }

    private fun deliver(id: Int, json: String) {
        val target = view ?: return
        target.post {
            target.evaluateJavascript(
                "window.__snapgentResolve && window.__snapgentResolve($id, ${JSONObject.quote(json)});",
                null,
            )
        }
    }

    private fun errorJson(message: String): String = buildJsonObject {
        put("ok", false)
        put("error", message)
    }.toString()

    companion object {
        internal fun originOf(url: String?): String? {
            if (url.isNullOrEmpty()) return null
            val uri = Uri.parse(url)
            val scheme = uri.scheme ?: return null
            val host = uri.host ?: return null
            return "$scheme://$host"
        }
    }
}
