// SPDX-License-Identifier: GPL-3.0-or-later
package app.snapgent.android

import android.app.Application

class SnapgentApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.start()
    }
}
