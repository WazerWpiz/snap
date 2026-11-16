// SPDX-License-Identifier: GPL-3.0-or-later
// Abstraksi filesystem workspace. Dua implementasi: folder privat / java.io.File (di sini)
// dan SAF DocumentFile (di modul app). Semua path berbentuk relatif terhadap root, "/" sebagai pemisah.
package app.snapgent.workspace

import java.io.File
import java.io.IOException
import java.nio.file.Files

data class FsEntry(val name: String, val path: String, val isDir: Boolean)

data class FsStat(val isDir: Boolean, val size: Long, val modifiedMillis: Long)

interface WorkspaceFs {
    val displayName: String

    /** Folder lokal di disk untuk git dan proses. null untuk workspace SAF. */
    val localRoot: File?

    /** Path relatif sudah dinormalisasi; "" = root. */
    fun stat(path: String): FsStat?
    fun list(path: String): List<FsEntry>
    fun readBytes(path: String): ByteArray
    /** Menulis file (membuat folder induk bila perlu). */
    fun writeBytes(path: String, bytes: ByteArray)
    fun createDirectories(path: String)
    /** Hapus satu file. */
    fun deleteFile(path: String)
    /** Pindahkan file; tujuan sudah dipastikan tidak ada oleh pemanggil. */
    fun move(from: String, to: String)
}

class JavaWorkspaceFs(private val root: File, override val displayName: String = root.name) : WorkspaceFs {
    private val canonicalRoot: String by lazy { root.canonicalPath }

    override val localRoot: File get() = root

    private fun file(path: String): File {
        val candidate = if (path.isEmpty()) root else File(root, path)
        val canonical = candidate.canonicalPath
        if (canonical != canonicalRoot && !canonical.startsWith(canonicalRoot + File.separator)) {
            throw IllegalArgumentException("Path escapes workspace root: $path")
        }
        return candidate
    }

    override fun stat(path: String): FsStat? {
        val f = file(path)
        if (!f.exists()) return null
        return FsStat(isDir = f.isDirectory, size = if (f.isFile) f.length() else 0L, modifiedMillis = f.lastModified())
    }

    override fun list(path: String): List<FsEntry> {
        val dir = file(path)
        return dir.listFiles().orEmpty().map { child ->
            FsEntry(child.name, WorkspacePaths.join(path, child.name), child.isDirectory)
        }
    }

    override fun readBytes(path: String): ByteArray = file(path).readBytes()

    override fun writeBytes(path: String, bytes: ByteArray) {
        val target = file(path)
        target.parentFile?.mkdirs()
        target.writeBytes(bytes)
    }

    override fun createDirectories(path: String) {
        file(path).mkdirs()
    }

    override fun deleteFile(path: String) {
        val target = file(path)
        if (!target.delete()) throw IOException("Gagal menghapus: $path")
    }

    override fun move(from: String, to: String) {
        val destination = file(to)
        destination.parentFile?.mkdirs()
        Files.move(file(from).toPath(), destination.toPath())
    }
}
