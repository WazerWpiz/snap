// SPDX-License-Identifier: GPL-3.0-or-later
// Kerangka UI: tema, navigasi sederhana berbasis state (tanpa navigation-compose),
// dan dialog persetujuan izin tool yang tampil di atas layar mana pun.
package app.snapgent.android.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.snapgent.android.AppContainer
import app.snapgent.android.R
import app.snapgent.settings.AppSettings

enum class Screen(val label: String, val labelRes: Int, val iconRes: Int) {
    CHAT("Chat", R.string.nav_chat, R.drawable.ic_chat),
    MCP("MCP", R.string.nav_mcp, R.drawable.ic_mcp),
    SETTINGS("Pengaturan", R.string.nav_settings, R.drawable.ic_settings),
    LOGS("Log", R.string.nav_logs, R.drawable.ic_logs),
}

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
            Column(
                modifier = Modifier
                    // Jangan sampai konten/panel navigasi tertimpa status bar atau nav bar sistem.
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .imePadding(),
            ) {
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
                            icon = {
                                Icon(
                                    painter = painterResource(item.iconRes),
                                    contentDescription = null,
                                )
                            },
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
                // Argumen/JSON hasil tool: monospace supaya mudah dibaca.
                Text(
                    request.details,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(4.dp))
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

// Warna merek ringan (hanya beberapa peran) supaya aplikasi terasa konsisten
// tanpa menarik dependencies warna dinamis tambahan.
private val BrandTeal = Color(0xFF00695C)
private val BrandTealLight = Color(0xFF4DB6AC)
private val BrandTealDark = Color(0xFF00382F)
private val BrandSand = Color(0xFFFFF4E5)

private fun lightScheme(): ColorScheme = lightColorScheme(
    primary = BrandTeal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2DFDB),
    onPrimaryContainer = BrandTealDark,
    secondary = Color(0xFF4E5A57),
    background = Color(0xFFFBFDFA),
    onBackground = Color(0xFF191C19),
    surface = Color(0xFFFBFDFA),
    onSurface = Color(0xFF191C19),
    surfaceVariant = Color(0xFFDDE5E1),
    onSurfaceVariant = Color(0xFF414944),
)

private fun darkScheme(): ColorScheme = darkColorScheme(
    primary = BrandTealLight,
    onPrimary = Color(0xFF00382F),
    primaryContainer = BrandTealDark,
    onPrimaryContainer = Color(0xFFB2DFDB),
    secondary = Color(0xFFBFC9C5),
    background = Color(0xFF191C19),
    onBackground = Color(0xFFE1E3DF),
    surface = Color(0xFF191C19),
    onSurface = Color(0xFFE1E3DF),
    surfaceVariant = Color(0xFF414944),
    onSurfaceVariant = Color(0xFFC1C9C4),
)