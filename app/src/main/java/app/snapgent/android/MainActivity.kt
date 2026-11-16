// SPDX-License-Identifier: GPL-3.0-or-later
package app.snapgent.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.snapgent.android.ui.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as SnapgentApplication).container
        setContent {
            AppRoot(container)
        }
    }
}
