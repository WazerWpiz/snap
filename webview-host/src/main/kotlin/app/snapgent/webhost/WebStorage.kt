// SPDX-License-Identifier: GPL-3.0-or-later
// Penyimpanan untuk chrome.storage.local milik core (SharedPreferences, sinkron agar shim
// bisa membaca snapshot saat dokumen dimulai). Kunci seperti zsCustomPrompt tersimpan di sini.
package app.snapgent.webhost

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class WebStorage(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun snapshot(): JsonObject = buildJsonObject {
        for ((key, value) in prefs.all) {
            if (value is String) put(key, parseOrRaw(value))
        }
    }

    fun snapshotJson(): String = snapshot().toString()

    fun get(key: String): JsonElement? = prefs.getString(key, null)?.let { parseOrRaw(it) }

    fun setValue(key: String, value: JsonElement) {
        prefs.edit().putString(key, value.toString()).apply()
    }

    /** Menerima objek JSON dari chrome.storage.local.set(). */
    fun putAllJson(json: String) {
        val obj = runCatching { Json.parseToJsonElement(json) as? JsonObject }.getOrNull() ?: return
        val editor = prefs.edit()
        for ((key, value) in obj) editor.putString(key, value.toString())
        editor.apply()
    }

    /** Ganti seluruh isi (dipakai saat impor konfigurasi). */
    fun replaceAll(values: Map<String, JsonElement>) {
        val editor = prefs.edit().clear()
        for ((key, value) in values) editor.putString(key, value.toString())
        editor.apply()
    }

    /** Hapus kunci (dipakai chrome.storage.local.remove di shim). */
    fun removeKeys(keys: Collection<String>) {
        if (keys.isEmpty()) return
        val editor = prefs.edit()
        keys.forEach { editor.remove(it) }
        editor.apply()
    }

    fun exportAll(): Map<String, JsonElement> = snapshot().toMap()

    private fun parseOrRaw(raw: String): JsonElement =
        runCatching { Json.parseToJsonElement(raw) }.getOrElse { JsonPrimitive(raw) }

    companion object {
        const val PREFS = "snapgent_web_storage"
    }
}
