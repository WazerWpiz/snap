// SPDX-License-Identifier: GPL-3.0-or-later
// Impor/ekspor konfigurasi sebagai satu file JSON. Isi webStorage adalah kunci chrome.storage
// milik core (mis. zsCustomPrompt, zsCustomMcpServers) sehingga ikut terbawa.
package app.snapgent.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ConfigFile(
    val format: String = ConfigCodec.FORMAT,
    val version: Int = ConfigCodec.VERSION,
    val settings: AppSettings = AppSettings(),
    val webStorage: Map<String, JsonElement> = emptyMap(),
)

object ConfigCodec {
    const val FORMAT = "snapgent-android-config"
    const val VERSION = 1

    fun export(settings: AppSettings, webStorage: Map<String, JsonElement>): String =
        SettingsRepository.SettingsJson.encodeToString(
            ConfigFile.serializer(),
            ConfigFile(settings = settings, webStorage = webStorage),
        )

    /** Melempar IllegalArgumentException bila format atau versi tidak dikenal. */
    fun import(text: String): ConfigFile {
        val file = try {
            SettingsRepository.SettingsJson.decodeFromString(ConfigFile.serializer(), text)
        } catch (e: Exception) {
            throw IllegalArgumentException("File bukan konfigurasi Snapgent yang valid: ${e.message}")
        }
        require(file.format == FORMAT) { "Format tidak dikenali: ${file.format}" }
        require(file.version <= VERSION) { "Versi konfigurasi ${file.version} lebih baru dari aplikasi ini." }
        return file
    }
}
