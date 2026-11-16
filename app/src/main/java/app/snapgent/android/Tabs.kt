// SPDX-License-Identifier: GPL-3.0-or-later
// Multi-tab: tiap tab membawa satu WebView provider dan satu ExtensionHost.
package app.snapgent.android

import android.content.Context
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
        view?.reload()
    }

    fun destroy() {
        view?.stopLoading()
        view?.destroy()
        view = null
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

    fun close(id: String) {
        val current = tabState.value
        val tab = current.firstOrNull { it.id == id } ?: return
        tab.destroy()
        val remaining = current.filterNot { it.id == id }
        tabState.value = remaining
        if (activeState.value == id) activeState.value = remaining.lastOrNull()?.id
    }
}
