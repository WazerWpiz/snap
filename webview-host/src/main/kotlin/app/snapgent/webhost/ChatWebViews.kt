// SPDX-License-Identifier: GPL-3.0-or-later
// Membuat WebView untuk satu situs AI: pengaturan aman, shim + bundle sebagai document-start
// script yang hanya aktif di origin provider, dan navigasi yang dijaga.
package app.snapgent.webhost

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

object ChatWebViews {

    val isSupported: Boolean
        get() = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

    @SuppressLint("SetJavaScriptEnabled")
    fun create(context: Context, site: ProviderSite, host: ExtensionHost, debuggable: Boolean): WebView {
        check(isSupported) { "Android System WebView terlalu lama. Perbarui WebView dari Play Store." }
        WebView.setWebContentsDebuggingEnabled(debuggable)
        val view = WebView(context)
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            mediaPlaybackRequiresUserGesture = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            // Tampilkan sebagai Chrome biasa: sebagian situs menolak UA WebView ("; wv").
            userAgentString = userAgentString.replace("; wv", "").replace("Version/4.0 ", "")
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(view, true)
        }
        view.addJavascriptInterface(host, BRIDGE_NAME)
        for (path in site.mainWorldScripts) {
            WebViewCompat.addDocumentStartJavaScript(
                view,
                ScriptBundle.readAsset(context, "${SiteCatalog.ROOT}/$path"),
                site.allowedOrigins,
            )
        }
        WebViewCompat.addDocumentStartJavaScript(view, ScriptBundle.startupScript(context, site), site.allowedOrigins)
        view.webViewClient = GuardedWebViewClient(context, host)
        // Tanpa ChromeClient sendiri, Permintaan mikrofon (input suara di situs AI)
        // ditolak diam-diam oleh WebChromeClient bawaan.
        view.webChromeClient = ChatChromeClient()
        host.attachView(view)
        return view
    }

    const val BRIDGE_NAME = "SnapgentAndroid"
}

/**
 * Menyetujui HANYA permintaan mikrofon (input suara pada situs AI chat) dan
 * menolak sisanya (kamera, geolocation) agar privasi tetap terbatas.
 */
private class ChatChromeClient : WebChromeClient() {
    override fun onPermissionRequest(request: android.webkit.PermissionRequest) {
        val granted = request.resources?.filter { it == android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE }
        if (granted.isNullOrEmpty()) {
            request.deny()
        } else {
            request.grant(granted.toTypedArray())
        }
    }
}

/** Halaman web tetap di WebView; skema lain (intent:, mailto:, dll.) dibuka aplikasi lain. */
class GuardedWebViewClient(
    private val context: Context,
    private val host: ExtensionHost,
) : WebViewClient() {

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        host.onPageStarted(url)
        super.onPageStarted(view, url, favicon)
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val uri = request?.url ?: return false
        return when (uri.scheme) {
            "http", "https", "about", "blob", "data", "javascript" -> false
            else -> {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                true
            }
        }
    }
}
