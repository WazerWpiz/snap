// SPDX-License-Identifier: GPL-3.0-or-later
// Manajer MCP: server bawaan (workspace) dan server remote HTTP. Tambah, hapus, toggle, tes koneksi.
package app.snapgent.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Switch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.snapgent.android.AppContainer
import app.snapgent.settings.AppSettings
import app.snapgent.settings.RemoteServerEntry
import app.snapgent.workspace.WorkspaceServer
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun McpScreen(container: AppContainer) {
    val settings by container.settingsRepo.flow.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Server MCP", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Button(onClick = { adding = true }) { Text("Tambah remote") }
        }
        Spacer(Modifier.height(4.dp))
        testResult?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        LazyColumn(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Workspace (bawaan, id ${WorkspaceServer.SERVER_ID})")
                            Text(container.workspace.describe(settings), style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = settings.builtinWorkspaceEnabled,
                            onCheckedChange = { on ->
                                scope.launch { container.settingsRepo.update { it.copy(builtinWorkspaceEnabled = on) } }
                            },
                        )
                    }
                }
            }
            items(settings.remoteServers, key = { it.id }) { entry ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.name)
                                Text(entry.url, style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(
                                checked = entry.enabled,
                                onCheckedChange = { on ->
                                    scope.launch {
                                        container.settingsRepo.update { s ->
                                            s.copy(remoteServers = s.remoteServers.map { if (it.id == entry.id) it.copy(enabled = on) else it })
                                        }
                                    }
                                },
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                scope.launch { testResult = "Menguji ${entry.name}..."; testResult = container.testRemote(entry) }
                            }) { Text("Tes koneksi") }
                            TextButton(onClick = {
                                scope.launch {
                                    container.settingsRepo.update { s ->
                                        s.copy(remoteServers = s.remoteServers.filterNot { it.id == entry.id })
                                    }
                                }
                            }) { Text("Hapus") }
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        AddRemoteDialog(
            onDismiss = { adding = false },
            onSave = { entry ->
                adding = false
                scope.launch {
                    container.settingsRepo.update { s -> s.copy(remoteServers = s.remoteServers + entry) }
                }
            },
        )
    }
}

@Composable
private fun AddRemoteDialog(onDismiss: () -> Unit, onSave: (RemoteServerEntry) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://") }
    var headers by remember { mutableStateOf("") }
    val valid = name.isNotBlank() && (url.startsWith("https://") || url.startsWith("http://"))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Server MCP remote (HTTP)") },
        text = {
            Column {
                Text("Hanya Streamable HTTP. Server stdio tidak didukung di Android.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama") }, singleLine = true)
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("URL endpoint") }, singleLine = true)
                OutlinedTextField(
                    value = headers,
                    onValueChange = { headers = it },
                    label = { Text("Header (Nama: nilai, satu per baris)") },
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(
                    RemoteServerEntry(
                        id = UUID.randomUUID().toString(),
                        name = name.trim(),
                        url = url.trim(),
                        headers = parseHeaders(headers),
                    ),
                )
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

internal fun parseHeaders(raw: String): Map<String, String> =
    raw.lines()
        .mapNotNull { line ->
            val idx = line.indexOf(':')
            if (idx <= 0) null else line.substring(0, idx).trim() to line.substring(idx + 1).trim()
        }
        .filter { it.first.isNotEmpty() }
        .toMap()
