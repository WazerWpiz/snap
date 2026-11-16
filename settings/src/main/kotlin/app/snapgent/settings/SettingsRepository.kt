// SPDX-License-Identifier: GPL-3.0-or-later
package app.snapgent.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "snapgent_settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore
    private val key = stringPreferencesKey("app_settings_json")

    val flow: Flow<AppSettings> = store.data.map { prefs -> decode(prefs[key]) }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val next = transform(decode(prefs[key]))
            prefs[key] = SettingsJson.encodeToString(AppSettings.serializer(), next)
        }
    }

    suspend fun replace(settings: AppSettings) = update { settings }

    private fun decode(raw: String?): AppSettings =
        raw?.let { runCatching { SettingsJson.decodeFromString(AppSettings.serializer(), it) }.getOrNull() }
            ?: AppSettings()

    companion object {
        val SettingsJson: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
