// SPDX-License-Identifier: GPL-3.0-or-later
// Pengaturan: tema, workspace (SAF), kategori & izin tool, auto-send, batas respons,
// system prompt, layanan latar, dan impor/ekspor konfigurasi.
package app.snapgent.android.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import app.snapgent.android.AppContainer
import app.snapgent.android.service.SessionService
import app.snapgent.mcp.ToolCategory
import app.snapgent.settings.AppSettings
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

private const val PROMPT_KEY = "zsCustomPrompt"

@Composable
fun SettingsScreen(container: AppContainer) {
    val context = LocalContext.current
    val settings by container.settingsRepo.flow.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var promptDraft by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        promptDraft = (container.webStorage.get(PROMPT_KEY) as? JsonPrimitive)?.content.orEmpty()
    }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        val name = DocumentFile.fromTreeUri(context, uri)?.name
        scope.launch {
            container.settingsRepo.update { it.copy(workspaceKind = "saf", workspaceUri = uri.toString(), workspaceName = name) }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(container.exportConfig().toByteArray()) }
            }.onSuccess { message = "Konfigurasi diekspor." }.onFailure { message = "Ekspor gagal: ${it.message}" }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                        ?: error("file tidak dapat dibaca")
                    container.importConfig(text)
                }.onSuccess { message = "Konfigurasi diimpor." }.onFailure { message = "Impor gagal: ${it.message}" }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Pengaturan", style = MaterialTheme.typography.titleLarge)
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

        SectionTitle("Tampilan")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("system" to "Sistem", "light" to "Terang", "dark" to "Gelap").forEach { (value, label) ->
                FilterChip(
                    selected = settings.themeMode == value,
                    onClick = { scope.launch { container.settingsRepo.update { it.copy(themeMode = value) } } },
                    label = { Text(label) },
                )
            }
        }

        SectionTitle("Workspace")
        Text(container.workspace.describe(settings))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { pickFolder.launch(null) }) { Text("Pilih folder") }
            OutlinedButton(onClick = {
                scope.launch { container.settingsRepo.update { it.copy(workspaceKind = "private", workspaceUri = null, workspaceName = null) } }
            }) { Text("Pakai folder privat") }
        }
        Text(
            "Folder SAF tidak mendukung git dan run_command lokal.",
            style = MaterialTheme.typography.bodySmall,
        )

        SectionTitle("Kategori tool")
        ToolCategory.entries.forEach { category ->
            val enabled = category.wire in settings.enabledCategories
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(category.label, modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { on ->
                    scope.launch {
                        container.settingsRepo.update { s ->
                            s.copy(enabledCategories = if (on) s.enabledCategories + category.wire else s.enabledCategories - category.wire)
                        }
                    }
                })
            }
        }

        SectionTitle("Konfirmasi sebelum jalan")
        ToolCategory.entries.forEach { category ->
            val confirm = category.wire in settings.confirmCategories
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(category.label, modifier = Modifier.weight(1f))
                Switch(checked = confirm, onCheckedChange = { on ->
                    scope.launch {
                        container.settingsRepo.update { s ->
                            s.copy(confirmCategories = if (on) s.confirmCategories + category.wire else s.confirmCategories - category.wire)
                        }
                    }
                })
            }
        }

        SectionTitle("Respons")
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Kirim hasil tool otomatis", modifier = Modifier.weight(1f))
            Switch(checked = settings.autoSendResults, onCheckedChange = { on ->
                scope.launch { container.settingsRepo.update { it.copy(autoSendResults = on) } }
            })
        }
        var maxText by remember(settings.maxResponseChars) { mutableStateOf(settings.maxResponseChars.toString()) }
        OutlinedTextField(
            value = maxText,
            onValueChange = { raw ->
                maxText = raw.filter { it.isDigit() }.take(7)
                maxText.toIntOrNull()?.takeIf { it in 1_000..2_000_000 }?.let { value ->
                    scope.launch { container.settingsRepo.update { it.copy(maxResponseChars = value) } }
                }
            },
            label = { Text("Batas ukuran respons (karakter, 1.000–2.000.000)") },
            singleLine = true,
        )

        SectionTitle("System prompt")
        promptDraft?.let { draft ->
            OutlinedTextField(
                value = draft,
                onValueChange = { promptDraft = it },
                label = { Text("Tambahan system prompt") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = {
                container.webStorage.setValue(PROMPT_KEY, JsonPrimitive(draft))
                message = "System prompt disimpan. Muat ulang tab provider untuk menerapkannya."
            }) { Text("Simpan prompt") }
        }

        SectionTitle("Layanan latar")
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Jaga sesi tetap hidup (foreground service)", modifier = Modifier.weight(1f))
            Switch(checked = settings.keepAlive, onCheckedChange = { on ->
                SessionService.sync(context, on)
                scope.launch { container.settingsRepo.update { it.copy(keepAlive = on) } }
            })
        }

        SectionTitle("Konfigurasi")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { exportLauncher.launch("snapgent-config.json") }) { Text("Ekspor") }
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Impor") }
        }
        HorizontalDivider()
        Text("Snapgent Android 0.1 — GPL-3.0. Berdasarkan herdodp/snapgent-connector.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SectionTitle(text: String) {
    HorizontalDivider()
    Text(text, style = MaterialTheme.typography.titleMedium)
}
