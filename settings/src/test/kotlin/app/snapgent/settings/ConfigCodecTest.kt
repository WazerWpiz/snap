// SPDX-License-Identifier: GPL-3.0-or-later
package app.snapgent.settings

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ConfigCodecTest {

    @Test
    fun exportThenImportKeepsSettingsAndWebStorage() {
        val settings = AppSettings(
            themeMode = "dark",
            maxResponseChars = 90_000,
            toolOverrides = mapOf("delete_file" to "deny"),
            remoteServers = listOf(RemoteServerEntry("r1", "Remote", "https://example.com/mcp", mapOf("X-A" to "1"))),
        )
        val webStorage = mapOf("zsCustomPrompt" to JsonPrimitive("Balas singkat"))

        val text = ConfigCodec.export(settings, webStorage)
        val back = ConfigCodec.import(text)

        assertEquals(settings, back.settings)
        assertEquals(webStorage, back.webStorage)
    }

    @Test
    fun rejectsForeignFormat() {
        val foreign = """{"format":"lain","version":1}"""
        assertThrows(IllegalArgumentException::class.java) { ConfigCodec.import(foreign) }
    }

    @Test
    fun rejectsNewerVersion() {
        val newer = """{"format":"snapgent-android-config","version":99}"""
        assertThrows(IllegalArgumentException::class.java) { ConfigCodec.import(newer) }
    }
}
