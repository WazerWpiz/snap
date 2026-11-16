// SPDX-License-Identifier: GPL-3.0-or-later
// Port Kotlin dari vscode_mcp.py: 28 tool workspace, nama dan parameter identik dengan upstream
// sehingga prompt sistem dan core/main.js tetap cocok. Server id "vscode" dipertahankan
// karena core mencari server dengan id itu.
package app.snapgent.workspace

import app.snapgent.mcp.LogBuffer
import app.snapgent.mcp.McpJson
import app.snapgent.mcp.McpServer
import app.snapgent.mcp.McpTool
import app.snapgent.mcp.ToolCategory
import app.snapgent.mcp.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

class ToolException(message: String) : Exception(message)

class WorkspaceServer(
    private val workspace: () -> WorkspaceFs?,
    private val log: LogBuffer,
    private val shell: ShellRunner = ShellRunner(),
) : McpServer {

    override val id: String = SERVER_ID
    override val displayName: String = "Workspace (bawaan)"
    override val isAlive: Boolean get() = workspace() != null
    override val error: String? get() = if (workspace() == null) "Pilih folder workspace di Pengaturan." else null
    override val tools: List<McpTool> get() = if (workspace() == null) emptyList() else WorkspaceSchemas.tools(id)

    override suspend fun start() = Unit

    override suspend fun stop() = Unit

    override suspend fun callTool(name: String, arguments: JsonObject, timeoutMs: Long): ToolResult {
        val fs = workspace() ?: return ToolResult(false, kind = "no_workspace", error = "Pilih folder workspace di Pengaturan.")
        return withContext(Dispatchers.IO) {
            try {
                ToolResult(true, text = dispatch(fs, name, arguments))
            } catch (e: Exception) {
                log.warn("workspace", "$name gagal: ${e.message}")
                ToolResult(false, kind = "tool_error", error = e.message ?: e.javaClass.simpleName)
            }
        }
    }

    internal fun dispatch(fs: WorkspaceFs, name: String, a: JsonObject): String = when (name) {
        "get_workspace_info" -> buildJsonObject {
            put("name", fs.displayName)
            put("path", fs.localRoot?.absolutePath ?: "(SAF) ${fs.displayName}")
            put("is_git_repository", fs.stat(".git") != null)
        }.toString()
        "get_project_info" -> projectInfo(fs).toString()
        "list_dir" -> listDir(fs, a.str("path") ?: ".").toString()
        "list_tree" -> listTree(fs, a.str("path") ?: ".", a.int("max_depth") ?: 3).toString()
        "find_files" -> findFiles(fs, a.str("path") ?: ".", a.str("pattern") ?: "*").toString()
        "search_files" -> searchFiles(
            fs,
            a.req("path"),
            a.req("query"),
            a.str("file_pattern") ?: "*",
            a.bool("case_sensitive") ?: false,
        ).toString()
        "read_file" -> readFile(fs, a.req("path"))
        "read_files" -> readFiles(fs, a.strList("paths")).toString()
        "read_file_lines" -> readFileLines(fs, a.req("path"), a.int("start_line") ?: 1, a.int("end_line") ?: 200)
        "get_file_info" -> fileInfo(fs, a.req("path")).toString()
        "get_file_outline" -> fileOutline(fs, a.req("path")).toString()
        "create_file" -> createFile(fs, a.req("path"), a.str("content") ?: "")
        "write_file" -> writeFile(fs, a.req("path"), a.req("content"))
        "edit_file" -> editFile(fs, a.req("path"), a.req("old_text"), a.req("new_text"), a.bool("replace_all") ?: false)
        "create_directory" -> createDirectory(fs, a.req("path"))
        "move_file" -> transfer(fs, a.req("source"), a.req("destination"), move = true)
        "copy_file" -> transfer(fs, a.req("source"), a.req("destination"), move = false)
        "git_status" -> git(fs).status()
        "git_diff" -> git(fs).diff(a.str("path")?.let { WorkspacePaths.normalize(it) }.orEmpty())
        "git_log" -> {
            val limit = a.int("limit") ?: 10
            if (limit < 1 || limit > 50) throw ToolException("limit must be between 1 and 50.")
            git(fs).log(limit)
        }
        "git_branches" -> git(fs).branches()
        "run_command" -> runCommand(fs, a.req("command"), a.strList("args"))
        "run_project_script" -> runProjectScript(fs, a.req("script"))
        "run_tests" -> runTests(fs)
        "delete_file" -> deleteFile(fs, a.req("path"))
        "apply_patch" -> applyPatch(fs, a.req("path"), a["edits"] as? JsonArray)
        "git_add" -> {
            val paths = a.strList("paths")
            if (paths.isEmpty()) throw ToolException("paths must be a non-empty list.")
            val relatives = paths.map { WorkspacePaths.normalize(it) }
            if (relatives.contains("")) throw ToolException("Stage specific paths, not the whole workspace.")
            git(fs).add(relatives)
        }
        "git_commit" -> {
            val message = a.str("message")?.trim().orEmpty()
            if (message.isEmpty()) throw ToolException("Commit message cannot be empty.")
            git(fs).commit(message)
        }
        else -> throw ToolException("Unknown tool: $name")
    }

    // ---------- navigasi & info ----------

    private fun projectInfo(fs: WorkspaceFs): JsonObject {
        val manifests = mutableListOf<JsonElement>()
        val patterns = listOf("package.json", "pyproject.toml", "Cargo.toml", "go.mod", "pom.xml", "*.sln")
        for (pattern in patterns) {
            fs.list("").filter { !it.isDir && Glob.matches(pattern, it.name) }
                .sortedBy { it.name }
                .take(10)
                .forEach { entry ->
                    manifests += buildJsonObject {
                        put("path", entry.path)
                        if (entry.name == "package.json") {
                            try {
                                val pkg = McpJson.parseToJsonElement(readText(fs, entry.path)).jsonObject
                                put("name", pkg["name"] ?: JsonNull)
                                put("scripts", pkg["scripts"] ?: buildJsonObject { })
                            } catch (e: Exception) {
                                put("error", e.message ?: "invalid package.json")
                            }
                        }
                    }
                }
        }
        return buildJsonObject {
            put("workspace", fs.displayName)
            put("manifests", JsonArray(manifests))
        }
    }

    private fun listDir(fs: WorkspaceFs, path: String): JsonArray {
        val target = WorkspacePaths.normalize(path)
        val st = fs.stat(target) ?: throw ToolException("Directory not found: $path")
        if (!st.isDir) throw ToolException("Not a directory: $path")
        val children = fs.list(target).sortedWith(compareBy<FsEntry>({ !it.isDir }, { it.name.lowercase() }))
        return JsonArray(children.map { entryJson(it) })
    }

    private fun listTree(fs: WorkspaceFs, path: String, maxDepth: Int): JsonArray {
        val target = WorkspacePaths.normalize(path)
        val st = fs.stat(target)
        if (st == null || !st.isDir) throw ToolException("Not a directory: $path")
        val limit = maxDepth.coerceIn(0, 8)
        val result = mutableListOf<JsonElement>()

        fun walk(dir: String, depth: Int) {
            val children = visibleChildren(fs, dir)
            val dirs = children.filter { it.isDir }.sortedBy { it.name }
            val files = children.filter { !it.isDir }.sortedBy { it.name }
            for (entry in dirs + files) {
                if (result.size >= MAX_RESULTS) return
                result += buildJsonObject {
                    put("path", entry.path)
                    put("type", if (entry.isDir) "directory" else "file")
                }
            }
            if (depth >= limit) return
            for (sub in dirs) {
                if (result.size >= MAX_RESULTS) return
                walk(sub.path, depth + 1)
            }
        }

        walk(target, 0)
        return JsonArray(result)
    }

    private fun findFiles(fs: WorkspaceFs, path: String, pattern: String): JsonArray {
        val base = WorkspacePaths.normalize(path)
        val st = fs.stat(base) ?: throw ToolException("Path not found: $path")
        if (!st.isDir) throw ToolException("Not a directory: $path")
        val matches = iterFiles(fs, base)
            .filter { Glob.matches(pattern, it.name) || Glob.matches(pattern, WorkspacePaths.relativeTo(it.path, base)) }
            .take(MAX_RESULTS)
            .map { JsonPrimitive(it.path) }
        return JsonArray(matches)
    }

    private fun searchFiles(
        fs: WorkspaceFs,
        path: String,
        query: String,
        filePattern: String,
        caseSensitive: Boolean,
    ): JsonArray {
        val target = WorkspacePaths.normalize(path)
        val st = fs.stat(target) ?: throw ToolException("Path not found: $path")
        if (query.isBlank()) throw ToolException("Search query cannot be empty.")
        val needle = if (caseSensitive) query else query.lowercase()
        val files = if (st.isDir) iterFiles(fs, target) else listOf(FsEntry(target.substringAfterLast('/'), target, false))
        val out = mutableListOf<JsonElement>()
        outer@ for (file in files) {
            if (!Glob.matches(filePattern, file.name)) continue
            val size = fs.stat(file.path)?.size ?: continue
            if (size > MAX_SEARCH_BYTES) continue
            val text = readText(fs, file.path)
            for ((index, line) in text.lines().withIndex()) {
                val hay = if (caseSensitive) line else line.lowercase()
                if (needle in hay) {
                    out += buildJsonObject {
                        put("path", file.path)
                        put("line", index + 1)
                        put("text", line.take(500))
                    }
                    if (out.size >= MAX_RESULTS) break@outer
                }
            }
        }
        return JsonArray(out)
    }

    // ---------- baca ----------

    private fun readFile(fs: WorkspaceFs, path: String): String {
        val target = WorkspacePaths.normalize(path)
        val st = fs.stat(target)
        if (st == null || st.isDir) throw ToolException("File not found: $path")
        val content = readText(fs, target)
        return if (content.length > MAX_FILE_CHARS) content.take(MAX_FILE_CHARS) + "\n[truncated]" else content
    }

    private fun readFiles(fs: WorkspaceFs, paths: List<String>): JsonArray {
        if (paths.size > 50) throw ToolException("Read at most 50 files per request.")
        return JsonArray(paths.map { p ->
            buildJsonObject {
                put("path", p)
                put("content", readFile(fs, p))
            }
        })
    }

    private fun readFileLines(fs: WorkspaceFs, path: String, startLine: Int, endLine: Int): String {
        if (startLine < 1 || endLine < startLine) {
            throw ToolException("Line numbers must be positive, and end_line must be >= start_line.")
        }
        if (endLine - startLine > 999) throw ToolException("Read at most 1000 lines per request.")
        val lines = readFile(fs, path).lines()
        return (startLine..minOf(endLine, lines.size)).joinToString("\n") { number ->
            "$number: ${lines[number - 1]}"
        }
    }

    private fun fileInfo(fs: WorkspaceFs, path: String): JsonObject {
        val target = WorkspacePaths.normalize(path)
        val st = fs.stat(target) ?: throw ToolException("Path not found: $path")
        val name = target.substringAfterLast('/')
        val dot = name.lastIndexOf('.')
        return buildJsonObject {
            put("path", target)
            put("type", if (st.isDir) "directory" else "file")
            put("size_bytes", st.size)
            put("modified", st.modifiedMillis / 1000.0)
            put("suffix", if (dot > 0) name.substring(dot) else "")
        }
    }

    private fun fileOutline(fs: WorkspaceFs, path: String): JsonArray {
        val lines = readFile(fs, path).lines()
        val out = mutableListOf<JsonElement>()
        for ((index, line) in lines.withIndex()) {
            for (pattern in OUTLINE_PATTERNS) {
                val match = pattern.find(line) ?: continue
                out += buildJsonObject {
                    put("name", match.groupValues[1])
                    put("line", index + 1)
                }
                break
            }
            if (out.size >= MAX_RESULTS) break
        }
        return JsonArray(out)
    }

    // ---------- tulis ----------

    private fun createFile(fs: WorkspaceFs, path: String, content: String): String {
        val target = WorkspacePaths.normalize(path)
        if (fs.stat(target) != null) throw ToolException("File already exists: $path")
        fs.writeBytes(target, content.toByteArray(Charsets.UTF_8))
        return "Created $target"
    }

    private fun writeFile(fs: WorkspaceFs, path: String, content: String): String {
        val target = WorkspacePaths.normalize(path)
        fs.writeBytes(target, content.toByteArray(Charsets.UTF_8))
        return "Saved $target"
    }

    private fun editFile(fs: WorkspaceFs, path: String, oldText: String, newText: String, replaceAll: Boolean): String {
        if (oldText.isEmpty()) throw ToolException("old_text cannot be empty.")
        val target = requireFile(fs, path)
        val content = readText(fs, target)
        val occurrences = countOccurrences(content, oldText)
        if (occurrences == 0) throw ToolException("The exact old_text was not found.")
        if (!replaceAll && occurrences != 1) {
            throw ToolException("old_text occurs $occurrences times; provide a unique snippet or set replace_all.")
        }
        val updated = if (replaceAll) content.replace(oldText, newText) else content.replaceFirst(oldText, newText)
        fs.writeBytes(target, updated.toByteArray(Charsets.UTF_8))
        return "Updated $target (${if (replaceAll) occurrences else 1} replacement(s))"
    }

    private fun createDirectory(fs: WorkspaceFs, path: String): String {
        val target = WorkspacePaths.normalize(path)
        fs.createDirectories(target)
        return "Directory ready: ${target.ifEmpty { "." }}"
    }

    private fun transfer(fs: WorkspaceFs, source: String, destination: String, move: Boolean): String {
        val from = requireFile(fs, source)
        val to = WorkspacePaths.normalize(destination)
        if (fs.stat(to) != null) throw ToolException("Destination already exists: $destination")
        if (move) {
            fs.move(from, to)
            return "Moved $from to $to"
        }
        fs.writeBytes(to, fs.readBytes(from))
        return "Copied $from to $to"
    }

    private fun deleteFile(fs: WorkspaceFs, path: String): String {
        val target = WorkspacePaths.normalize(path)
        if (target.isEmpty() || target.split('/').contains(".git")) {
            throw ToolException("Refusing to delete protected path: $path")
        }
        val st = fs.stat(target) ?: throw ToolException("File not found: $path")
        if (st.isDir) throw ToolException("Not a file (directories are not deleted): $path")
        fs.deleteFile(target)
        return "Deleted $target"
    }

    private fun applyPatch(fs: WorkspaceFs, path: String, edits: JsonArray?): String {
        if (edits == null || edits.isEmpty()) throw ToolException("edits must be a non-empty list.")
        val target = requireFile(fs, path)
        var content = readText(fs, target)
        edits.forEachIndexed { index, element ->
            val number = index + 1
            val edit = element as? JsonObject ?: throw ToolException("Edit $number must be an object with old_text and new_text.")
            val oldText = edit.str("old_text")
            val replacement = edit.str("new_text")
            if (oldText.isNullOrEmpty() || replacement == null) {
                throw ToolException("Edit $number needs a non-empty old_text and a string new_text.")
            }
            val occurrences = countOccurrences(content, oldText)
            if (occurrences != 1) {
                throw ToolException("Edit $number: old_text occurs $occurrences times; it must match exactly once. No changes were written.")
            }
            content = content.replaceFirst(oldText, replacement)
        }
        fs.writeBytes(target, content.toByteArray(Charsets.UTF_8))
        return "Patched $target (${edits.size} edit(s))"
    }

    // ---------- proses & git ----------

    private fun runCommand(fs: WorkspaceFs, command: String, args: List<String>): String {
        if (command.isBlank()) throw ToolException("Command cannot be empty.")
        val root = localRoot(fs)
        return shell.run(listOf(command) + args, root, 30)
    }

    private fun runProjectScript(fs: WorkspaceFs, script: String): String {
        if (script !in SAFE_SCRIPTS) throw ToolException("Script must be one of: ${SAFE_SCRIPTS.sorted().joinToString(", ")}.")
        val root = localRoot(fs)
        val pkg = fs.stat("package.json") ?: throw ToolException("No package.json found in the workspace root.")
        if (pkg.isDir) throw ToolException("No package.json found in the workspace root.")
        val parsed = try {
            McpJson.parseToJsonElement(readText(fs, "package.json")).jsonObject
        } catch (e: Exception) {
            throw ToolException("Could not read package.json: ${e.message}")
        }
        val scripts = parsed["scripts"] as? JsonObject
        if (scripts == null || !scripts.containsKey(script)) {
            throw ToolException("package.json does not define the '$script' script.")
        }
        val manager = when {
            fs.stat("pnpm-lock.yaml") != null -> "pnpm"
            fs.stat("yarn.lock") != null -> "yarn"
            fs.stat("bun.lock") != null || fs.stat("bun.lockb") != null -> "bun"
            else -> "npm"
        }
        return shell.run(listOf(manager, "run", script), root, 300)
    }

    private fun runTests(fs: WorkspaceFs): String {
        val root = localRoot(fs)
        return when {
            fs.stat("package.json") != null -> runProjectScript(fs, "test")
            fs.stat("pyproject.toml") != null || fs.stat("pytest.ini") != null ->
                shell.run(listOf("python3", "-m", "pytest"), root, 300)
            fs.list("").any { !it.isDir && (it.name.endsWith(".sln") || it.name.endsWith(".csproj")) } ->
                shell.run(listOf("dotnet", "test"), root, 300)
            else -> throw ToolException("No supported test setup found (package.json, pytest, or .NET project).")
        }
    }

    private fun git(fs: WorkspaceFs): GitOps {
        val root = fs.localRoot ?: throw ToolException("Operasi Git hanya untuk workspace folder lokal (bukan SAF).")
        return GitOps(root)
    }

    private fun localRoot(fs: WorkspaceFs): File =
        fs.localRoot ?: throw ToolException("Fitur ini memerlukan workspace folder lokal (bukan SAF).")

    // ---------- helper ----------

    private fun requireFile(fs: WorkspaceFs, path: String): String {
        val target = WorkspacePaths.normalize(path)
        val st = fs.stat(target)
        if (st == null || st.isDir) throw ToolException("File not found: $path")
        return target
    }

    private fun readText(fs: WorkspaceFs, path: String): String = String(fs.readBytes(path), Charsets.UTF_8)

    private fun visibleChildren(fs: WorkspaceFs, dir: String): List<FsEntry> =
        fs.list(dir).filterNot { it.isDir && it.name in IGNORED_DIRECTORIES }

    /** Semua file di bawah [base] dengan urutan seperti os.walk (file dulu, lalu subfolder). */
    private fun iterFiles(fs: WorkspaceFs, base: String): List<FsEntry> {
        val out = mutableListOf<FsEntry>()
        fun walk(dir: String) {
            val children = visibleChildren(fs, dir)
            children.filter { !it.isDir }.sortedBy { it.name }.forEach { out += it }
            children.filter { it.isDir }.sortedBy { it.name }.forEach { walk(it.path) }
        }
        walk(base)
        return out
    }

    private fun entryJson(entry: FsEntry): JsonObject = buildJsonObject {
        put("name", entry.name)
        put("path", entry.path)
        put("type", if (entry.isDir) "directory" else "file")
    }

    private fun countOccurrences(text: String, needle: String): Int {
        var count = 0
        var index = text.indexOf(needle)
        while (index >= 0) {
            count++
            index = text.indexOf(needle, index + needle.length)
        }
        return count
    }

    companion object {
        const val SERVER_ID = "vscode"
        const val MAX_RESULTS = 500
        const val MAX_FILE_CHARS = 200_000
        const val MAX_SEARCH_BYTES = 2L * 1024 * 1024
        val SAFE_SCRIPTS = setOf("build", "check", "format", "lint", "test", "typecheck")
        val IGNORED_DIRECTORIES = setOf(
            ".git", ".hg", ".svn", ".venv", "venv", "node_modules",
            "__pycache__", ".pytest_cache", ".mypy_cache", ".next", "dist", "build",
        )
        private val OUTLINE_PATTERNS = listOf(
            Regex("""^\s*(?:async\s+)?def\s+([A-Za-z_]\w*)"""),
            Regex("""^\s*class\s+([A-Za-z_]\w*)"""),
            Regex("""^\s*(?:export\s+)?(?:async\s+)?function\s+([A-Za-z_\x24][\w\x24]*)"""),
            Regex("""^\s*(?:export\s+)?(?:const|let|var)\s+([A-Za-z_\x24][\w\x24]*)\s*=\s*(?:async\s*)?(?:function|\([^)]*\)\s*=>|[A-Za-z_\x24][\w\x24]*\s*=>)"""),
        )
    }
}

// ---------- pengambil argumen ----------

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.req(key: String): String =
    str(key) ?: throw ToolException("Parameter '$key' wajib diisi.")

private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

private fun JsonObject.strList(key: String): List<String> =
    (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
