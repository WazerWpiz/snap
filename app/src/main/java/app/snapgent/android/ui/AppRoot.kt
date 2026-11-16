// SPDX-License-Identifier: GPL-3.0-or-later
// Kerangka UI: tema, navigasi sederhana berbasis state (tanpa navigation-compose),
// dan dialog persetujuan izin tool yang tampil di atas layar mana pun.
package app.snapgent.android.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.snapgent.android.AppContainer
import app.snapgent.settings.AppSettings

enum class Screen(val label: String) { CHAT("Chat"), MCP("MCP"), SETTINGS("Pengaturan"), LOGS("Log") }

@Composable
fun AppRoot(container: AppContainer) {
    val settings by container.settingsRepo.flow.collectAsState(initial = AppSettings())
    val dark = when (settings.themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(colorScheme = if (dark) darkScheme() else lightScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            var screen by remember { mutableStateOf(Screen.CHAT) }
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    when (screen) {
                        Screen.CHAT -> ChatScreen(container)
                        Screen.MCP -> McpScreen(container)
                        Screen.SETTINGS -> SettingsScreen(container)
                        Screen.LOGS -> LogsScreen(container)
                    }
                }
                NavigationBar {
                    Screen.entries.forEach { item ->
                        NavigationBarItem(
                            selected = screen == item,
                            onClick = { screen = item },
                            icon = { Text(item.label.take(1)) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
            ApprovalDialog(container)
        }
    }
}

@Composable
private fun ApprovalDialog(container: AppContainer) {
    val pending by container.approvals.pending.collectAsState()
    val request = pending?.request ?: return
    AlertDialog(
        onDismissRequest = { container.approvals.answer(false) },
        title = { Text(request.title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(request.details)
                Spacer(Modifier.padding(4.dp))
                Text("Tool: ${request.toolName}", style = MaterialTheme.typography.labelMedium)
            }
        },
        confirmButton = {
            Button(onClick = { container.approvals.answer(true) }) { Text("Izinkan") }
        },
        dismissButton = {
            TextButton(onClick = { container.approvals.answer(false) }) { Text("Tolak") }
        },
    )
}

/** Baris sederhana "label: nilai" dipakai di beberapa layar. */
@Composable
internal fun KeyValueRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun lightScheme(): ColorScheme = lightColorScheme()
private fun darkScheme(): ColorScheme = darkColorScheme()
