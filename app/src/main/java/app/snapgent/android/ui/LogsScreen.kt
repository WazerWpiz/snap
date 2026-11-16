// SPDX-License-Identifier: GPL-3.0-or-later
package app.snapgent.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.snapgent.android.AppContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val LogWarn = Color(0xFFB26A00)

@Composable
fun LogsScreen(container: AppContainer) {
    val entries by container.log.entries.collectAsState()
    val format = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
    val listState = rememberLazyListState()
    val newest = entries.firstOrNull()?.seq

    // Terbaru di atas: ikut geser ke baris teratas saat ada log baru.
    LaunchedEffect(newest) {
        if (newest != null && entries.isNotEmpty()) listState.animateScrollToItem(0)
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Text(
                "Log (${entries.size})",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { container.log.clear() }) { Text("Bersihkan") }
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            // asReversed + key stabil: baris baru tidak membuat seluruh daftar digambar ulang.
            items(entries.asReversed(), key = { it.seq }) { entry ->
                val tint = when (entry.level) {
                    "E" -> MaterialTheme.colorScheme.error
                    "W" -> LogWarn
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    "${format.format(Date(entry.timeMillis))} ${entry.level} [${entry.tag}] ${entry.message}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = tint,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}