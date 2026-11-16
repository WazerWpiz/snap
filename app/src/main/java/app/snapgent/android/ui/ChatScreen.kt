// SPDX-License-Identifier: GPL-3.0-or-later
// Layar Chat: bar tab provider (multi-tab), indikator workspace, dan WebView provider aktif.
package app.snapgent.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.snapgent.android.AppContainer
import app.snapgent.android.ChatTab
import app.snapgent.webhost.ChatWebViews

@Composable
fun ChatScreen(container: AppContainer) {
    val context = LocalContext.current
    val tabs by container.tabs.tabs.collectAsState()
    val activeId by container.tabs.active.collectAsState()
    val workspace by container.workspace.status.collectAsState()
    val active = tabs.firstOrNull { it.id == activeId }
    var menuOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                OutlinedButton(onClick = { menuOpen = true }) { Text("Provider +") }
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
            Spacer(Modifier.padding(horizontal = 4.dp))
            AssistChip(
                onClick = { },
                label = { Text(workspace.label, maxLines = 1) },
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        if (tabs.isNotEmpty()) {
            LazyRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                items(tabs, key = { it.id }) { tab ->
                    TabChip(tab, selected = tab.id == activeId, onSelect = { container.tabs.select(tab.id) }, onClose = { container.tabs.close(tab.id) })
                }
            }
        }

        if (!ChatWebViews.isSupported) {
            Text(
                "Android System WebView terlalu lama untuk fitur ini. Perbarui WebView dari Play Store.",
                modifier = Modifier.padding(16.dp),
            )
        } else if (active == null) {
            Text(
                "Pilih provider dari tombol \"Provider +\" untuk memulai sesi.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            ProviderWebView(tab = active, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun TabChip(tab: ChatTab, selected: Boolean, onSelect: () -> Unit, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilterChip(selected = selected, onClick = onSelect, label = { Text(tab.site.displayName) })
        IconButton(onClick = onClose) { Text("×") }
    }
}

@Composable
private fun ProviderWebView(tab: ChatTab, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx -> tab.webView(ctx) },
        update = { },
    )
}
