// SPDX-License-Identifier: GPL-3.0-or-later
package app.snapgent.mcp

import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandParserTest {

    @Test
    fun parsesJsonEnvelopeWithParams() {
        val reply = "Saya baca dulu. {\"command\":\"read_file\",\"params\":{\"path\":\"README.md\"}} selesai"
        val commands = CommandParser.parse(reply)
        assertEquals(1, commands.size)
        assertEquals("read_file", commands[0].name)
        assertEquals("README.md", commands[0].arguments["path"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun keepsBracesInsideStringValues() {
        val reply = "{\"command\":\"edit_file\",\"params\":{\"path\":\"a.kt\",\"old_text\":\"fun a() { }\",\"new_text\":\"}\"}}"
        val commands = CommandParser.parse(reply)
        assertEquals(1, commands.size)
        assertEquals("fun a() { }", commands[0].arguments["old_text"]?.jsonPrimitive?.contentOrNull)
        assertEquals("}", commands[0].arguments["new_text"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun acceptsToolKeyWithArgumentsAndParamlessCommand() {
        val reply = "{\"tool\":\"git_status\"} lalu {\"tool\":\"list_dir\",\"arguments\":{\"path\":\"src\"}}"
        val commands = CommandParser.parse(reply)
        assertEquals(listOf("git_status", "list_dir"), commands.map { it.name })
        assertEquals("src", commands[1].arguments["path"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun parsesLuaBlockWithDatamodelSuffix() {
        val reply = "###LUA:Server### return 1 ###END_LUA###"
        val commands = CommandParser.parse(reply)
        assertEquals(1, commands.size)
        assertEquals("execute_luau", commands[0].name)
        assertEquals("return 1", commands[0].arguments["code"]?.jsonPrimitive?.contentOrNull)
        assertEquals("Server", commands[0].arguments["datamodel_type"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun ignoresPlainTextAndBrokenJson() {
        assertTrue(CommandParser.parse("Halo, tidak ada perintah di sini.").isEmpty())
        assertTrue(CommandParser.parse("{\"command\": \"read_file\", \"params\": {").isEmpty())
    }
}
