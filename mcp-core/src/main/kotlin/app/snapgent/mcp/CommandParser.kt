// SPDX-License-Identifier: GPL-3.0-or-later
// Port Kotlin dari core/parser.js (subset): envelope JSON {"command"|"tool": ..., "params"|"arguments": {...}}
// dan blok ###LUA### ... ###END_LUA###. Dipakai fitur "Uji parser" di aplikasi dan dites unit.
package app.snapgent.mcp

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

data class ParsedCommand(val name: String, val arguments: JsonObject)

object CommandParser {
    private val keyRegex = Regex("\"(command|tool)\"")
    private val luaStart = Regex("###\\s*lua(?:\\s*[:\\-_ ]\\s*(edit|client|server))?\\s*(?:###|---)", RegexOption.IGNORE_CASE)
    private val luaEnd = Regex("###\\s*end[_\\- ]?lua\\s*###", RegexOption.IGNORE_CASE)

    fun parse(text: String): List<ParsedCommand> {
        val out = ArrayList<ParsedCommand>()
        var lastEnd = -1
        for (match in keyRegex.findAll(text)) {
            val keyPos = match.range.first
            if (keyPos < lastEnd) continue
            val start = text.lastIndexOf('{', keyPos)
            if (start < 0 || start < lastEnd) continue
            val end = matchBrace(text, start)
            if (end < 0) continue
            val obj = runCatching { McpJson.parseToJsonElement(text.substring(start, end + 1)) }
                .getOrNull() as? JsonObject ?: continue
            val nameElement = obj["command"] ?: obj["tool"]
            val name = (nameElement as? JsonPrimitive)?.contentOrNull?.trim()
            if (name.isNullOrEmpty()) continue
            val args = (obj["params"] ?: obj["arguments"] ?: obj["args"]) as? JsonObject ?: JsonObject(emptyMap())
            out += ParsedCommand(name, args)
            lastEnd = end + 1
        }
        if (out.isNotEmpty()) return out

        var from = 0
        while (true) {
            val start = luaStart.find(text, from) ?: break
            val end = luaEnd.find(text, start.range.last + 1) ?: break
            val code = text.substring(start.range.last + 1, end.range.first).trim()
            val dataModel: JsonElement = JsonPrimitive(
                when (start.groupValues[1].lowercase()) {
                    "client" -> "Client"
                    "server" -> "Server"
                    else -> "Edit"
                },
            )
            out += ParsedCommand(
                "execute_luau",
                buildJsonObject {
                    put("code", code)
                    put("datamodel_type", dataModel)
                },
            )
            from = end.range.last + 1
        }
        return out
    }

    /** Indeks '}' yang menutup '{' di [start], dengan melewati isi string JSON. -1 bila tidak seimbang. */
    internal fun matchBrace(text: String, start: Int): Int {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (c == '\\') {
                    escaped = true
                } else if (c == '"') {
                    inString = false
                }
            } else if (c == '"') {
                inString = true
            } else if (c == '{') {
                depth++
            } else if (c == '}') {
                depth--
                if (depth == 0) return i
            }
        }
        return -1
    }
}
