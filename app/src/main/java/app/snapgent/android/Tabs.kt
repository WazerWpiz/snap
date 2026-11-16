// SPDX-License-Identifier: GPL-3.0-or-later
// Multi-tab: tiap tab membawa satu WebView provider dan satu ExtensionHost.
package app.snapgent.android

import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView
import app.snapgent.webhost.ChatWebViews
import app.snapgent.webhost.ExtensionHost
import app.snapgent.webhost.ProviderSite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class ChatTab(val id: String, val site: ProviderSite, container: AppContainer) {
    val host = ExtensionHost(
        site = site,
        storage = container.webStorage,
        scope = container.scope,
        handler = { message -> container.router.handle(message) },
        logSink = { line -> container.log.info("web", line) },
    )

    private var view: WebView? = null

    /** WebView dibuat sekali dan dimuat dengan URL provider. Melempar bila WebView tidak mendukung. */
    fun webView(context: Context): WebView {
        view?.let { return it }
        val created = ChatWebViews.create(context, site, host, BuildConfig.DEBUG)
        view = created
        created.loadUrl(site.startUrl)
        return created
    }

    fun reload() {
        // No-op bila WebView belum pernah dibuat (tab belum pernah tampil).
        view?.reload()
    }

    /**
     * Jeda/lanjutkan tab: WebView non-aktif berhenti menjalankan JS, animasi, dan video,
     * dan renderer-nya diturunkan prioritasnya supaya tab aktif tidak berebut CPU.
     * Sengaja hanya onPause/onResume (per-view); pauseTimers()/resumeTimers() bersifat
     * global dan akan menjeda SEMUA WebView, termasuk tab yang sedang tampil.
     */
    fun setActive(active: Boolean) {
        val current = view ?: return
        if (active) {
            current.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
            current.onResume()
        } else {
            // waivedWhenNotVisible=false: renderer tab latar tidak dibunuh, jadi saat
            // tab dibuka lagi halamannya tidak perlu dimuat ulang (tidak terasa lambat).
            current.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, false)
            current.onPause()
        }
    }

    fun destroy() {
        val current = view ?: return
        view = null
        // Lepas dari parent DULU: destroy() pada WebView yang masih menempel
        // pada hierarki view menyebabkan crash di runtime.
        (current.parent as? ViewGroup)?.removeView(current)
        current.stopLoading()
        current.destroy()
        // Host tidak boleh lagi menyentuh WebView yang sudah di-destroy.
        host.detachView()
    }
}

class ChatTabs(private val container: AppContainer) {
    private val tabState = MutableStateFlow<List<ChatTab>>(emptyList())
    val tabs: StateFlow<List<ChatTab>> = tabState.asStateFlow()

    private val activeState = MutableStateFlow<String?>(null)
    val active: StateFlow<String?> = activeState.asStateFlow()

    /** Buka provider; bila sudah ada tab-nya, tab itu yang diaktifkan. */
    fun open(site: ProviderSite): ChatTab {
        tabState.value.firstOrNull { it.site.id == site.id }?.let {
            activeState.value = it.id
            return it
        }
        val tab = ChatTab(UUID.randomUUID().toString(), site, container)
        tabState.value = tabState.value + tab
        activeState.value = tab.id
        return tab
    }

    fun select(id: String) {
        activeState.value = id
    }

    /** Jalankan tab [id] dan jeda sisanya (dipanggil dari UI setiap tab aktif berubah). */
    fun activate(id: String?) {
        tabState.value.forEach { it.setActive(it.id == id) }
    }

    fun close(id: String) {
        val current = tabState.value
        val tab = current.firstOrNull { it.id == id } ?: return
        val wasActive = activeState.value == id
        tab.destroy()
        val remaining = current.filterNot { it.id == id }
        tabState.value = remaining
        activeState.value = when {
            !wasActive -> activeState.value
            else -> remaining.lastOrNull()?.id
        }
        activate(activeState.value)
    }
}