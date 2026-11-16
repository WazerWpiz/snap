// SPDX-License-Identifier: GPL-3.0-or-later
// Layar Chat: bar tab provider (multi-tab), indikator workspace, dan WebView provider aktif.
package app.snapgent.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.snapgent.android.AppContainer
import app.snapgent.android.ChatTab
import app.snapgent.android.R
import app.snapgent.webhost.ChatWebViews

private val StatusGreen = Color(0xFF2E7D32)

@Composable
fun ChatScreen(container: AppContainer) {
    val tabs by container.tabs.tabs.collectAsState()
    val activeId by container.tabs.active.collectAsState()
    val workspace by container.workspace.status.collectAsState()
    val active = tabs.firstOrNull { it.id == activeId }
    var menuOpen by remember { mutableStateOf(false) }

    // Tab yang tidak aktif dijeda (JS/timer/video berhenti) supaya hemat memori & baterai.
    LaunchedEffect(activeId, tabs) {
        container.tabs.activate(activeId)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                OutlinedButton(onClick = { menuOpen = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_add),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Provider")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    container.sites.forEach { site ->
                        DropdownMenuItem(
                            text = { Text(site.displayName) },
                            onClick = {
                                menuOpen = false
                                container.tabs.open(site)
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            AssistChip(
                onClick = { },
                label = { Text(workspace.label, maxLines = 1) },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (workspace.ready) StatusGreen else MaterialTheme.colorScheme.outline,
                                shape = CircleShape,
                            ),
                    )
                },
            )
            Spacer(Modifier.weight(1f))
            // Muat ulang tab aktif (dulu tidak ada jalan masuk ke ChatTab.reload()).
            IconButton(onClick = { active?.reload() }, enabled = active != null) {
                Icon(
                    painter = painterResource(R.drawable.ic_refresh),
                    contentDescription = stringResource(R.string.action_reload_tab),
                )
            }
        }

        if (tabs.isNotEmpty()) {
            LazyRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                items(tabs, key = { it.id }) { tab ->
                    TabChip(
                        tab,
                        selected = tab.id == activeId,
                        onSelect = { container.tabs.select(tab.id) },
                        onClose = { container.tabs.close(tab.id) },
                    )
                }
            }
        }

        when {
            !ChatWebViews.isSupported -> CenteredMessage(
                "Android System WebView terlalu lama untuk fitur ini. Perbarui WebView dari Play Store.",
            )
            active == null -> CenteredMessage("Pilih provider dari tombol \"Provider\" untuk memulai sesi.")
            else -> ProviderWebView(tab = active, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun CenteredMessage(text: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

@Composable
private fun TabChip(tab: ChatTab, selected: Boolean, onSelect: () -> Unit, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 6.dp)) {
        FilterChip(selected = selected, onClick = onSelect, label = { Text(tab.site.displayName) })
        IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.action_close_tab, tab.site.displayName),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ProviderWebView(tab: ChatTab, modifier: Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    // key(tab.id): tanpa ini AndroidView mempertahankan WebView lama saat tab berganti,
    // sehingga layar tetap menampilkan halaman tab sebelumnya.
    key(tab.id) {
        AndroidView(
            modifier = modifier,
            factory = { ctx -> tab.webView(ctx) },
            update = { },
        )
    }
    // Jeda/lanjutkan WebView mengikuti siklus hidup activity (layar mati / pindah aplikasi).
    DisposableEffect(lifecycleOwner, tab.id) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> tab.setActive(false)
                Lifecycle.Event.ON_RESUME -> tab.setActive(true)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Compose melepas node ini saat tab berganti atau layar Chat ditinggalkan.
            tab.setActive(false)
        }
    }
}