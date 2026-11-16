// SPDX-License-Identifier: GPL-3.0-or-later
// Utilitas path dan glob (fnmatch) meniru perilaku vscode_mcp.py.
package app.snapgent.workspace

import java.io.File
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

object WorkspacePaths {
    /** Normalisasi path dari AI: "/" dianggap relatif terhadap root; ".." yang keluar root ditolak. */
    fun normalize(raw: String?): String {
        val parts = ArrayList<String>()
        for (segment in (raw ?: "").trim().replace('\\', '/').split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> {
                    if (parts.isEmpty()) throw IllegalArgumentException("Path escapes workspace root: $raw")
                    parts.removeAt(parts.lastIndex)
                }
                else -> parts.add(segment)
            }
        }
        return parts.joinToString("/")
    }

    fun join(base: String, name: String): String = if (base.isEmpty()) name else "$base/$name"

    /** Path relatif terhadap [base] (seperti os.path.relpath untuk file di dalam base). */
    fun relativeTo(path: String, base: String): String =
        if (base.isEmpty()) path else path.removePrefix("$base/")
}

object Glob {
    /** fnmatch: "*" juga mencocokkan "/", "?" satu karakter, [...] kelas karakter. */
    fun matches(pattern: String, text: String): Boolean = toRegex(pattern).matches(text)

    private fun toRegex(pattern: String): Regex {
        val sb = StringBuilder()
        var i = 0
        while (i < pattern.length) {
            val c = pattern[i]
            when (c) {
                '*' -> sb.append(".*")
                '?' -> sb.append('.')
                '[' -> {
                    val end = pattern.indexOf(']', i + 1)
                    if (end < 0) {
                        sb.append("\\[")
                    } else {
                        var body = pattern.substring(i + 1, end)
                        if (body.startsWith("!")) body = "^" + body.substring(1)
                        sb.append('[').append(body.replace("\\", "\\\\")).append(']')
                        i = end
                    }
                }
                else -> sb.append(Regex.escape(c.toString()))
            }
            i++
        }
        return Regex(sb.toString())
    }
}

/** Menjalankan program di folder workspace dengan batas waktu. */
class ShellRunner(private val maxChars: Int = 200_000) {
    fun run(command: List<String>, cwd: File, timeoutSeconds: Long): String {
        val process = try {
            ProcessBuilder(command).directory(cwd).start()
        } catch (e: IOException) {
            throw IOException("Perintah tidak tersedia di perangkat: ${command.first()} (${e.message})")
        }
        val stdout = CompletableFuture.supplyAsync { process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() } }
        val stderr = CompletableFuture.supplyAsync { process.errorStream.bufferedReader(Charsets.UTF_8).use { it.readText() } }
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw IOException("Timeout setelah ${timeoutSeconds}s")
        }
        val out = stdout.get(5, TimeUnit.SECONDS).trim()
        val err = stderr.get(5, TimeUnit.SECONDS).trim()
        val code = process.exitValue()
        val text = out.ifEmpty { err }.ifEmpty { "(no output)" }.take(maxChars)
        if (code != 0) throw IOException("Command exited with status $code:\n$text")
        return text
    }
}
