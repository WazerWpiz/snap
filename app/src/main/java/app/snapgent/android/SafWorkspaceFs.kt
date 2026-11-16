// SPDX-License-Identifier: GPL-3.0-or-later
// Implementasi WorkspaceFs di atas Storage Access Framework. Tidak ada path java.io, jadi
// git dan perintah lokal tidak tersedia pada workspace jenis ini (dinyatakan di tool).
package app.snapgent.android

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import app.snapgent.workspace.FsEntry
import app.snapgent.workspace.FsStat
import app.snapgent.workspace.WorkspaceFs
import app.snapgent.workspace.WorkspacePaths
import java.io.File
import java.io.IOException

class SafWorkspaceFs(private val context: Context, val treeUri: Uri) : WorkspaceFs {
    private val root: DocumentFile = DocumentFile.fromTreeUri(context, treeUri)
        ?: throw IOException("Folder workspace tidak dapat dibuka")

    override val displayName: String get() = root.name ?: "workspace"

    override val localRoot: File? = null

    private fun find(path: String): DocumentFile? {
        if (path.isEmpty()) return root
        var current: DocumentFile = root
        for (segment in path.split('/')) {
            current = current.findFile(segment) ?: return null
        }
        return current
    }

    override fun stat(path: String): FsStat? {
        val doc = find(path) ?: return null
        return FsStat(isDir = doc.isDirectory, size = doc.length(), modifiedMillis = doc.lastModified())
    }

    override fun list(path: String): List<FsEntry> {
        val dir = find(path) ?: return emptyList()
        return dir.listFiles().map { child ->
            val name = child.name.orEmpty()
            FsEntry(name, WorkspacePaths.join(path, name), child.isDirectory)
        }
    }

    override fun readBytes(path: String): ByteArray {
        val doc = find(path) ?: throw IOException("File tidak ditemukan: $path")
        return context.contentResolver.openInputStream(doc.uri)?.use { it.readBytes() }
            ?: throw IOException("Tidak dapat membaca: $path")
    }

    override fun writeBytes(path: String, bytes: ByteArray) {
        val segments = path.split('/').filter { it.isNotEmpty() }
        require(segments.isNotEmpty()) { "Path file kosong" }
        var dir: DocumentFile = root
        for (segment in segments.dropLast(1)) {
            dir = childDirectory(dir, segment)
        }
        val name = segments.last()
        val file = dir.findFile(name)
            ?: dir.createFile("application/octet-stream", name)
            ?: throw IOException("Gagal membuat file: $name")
        context.contentResolver.openOutputStream(file.uri, "wt")?.use { it.write(bytes) }
            ?: throw IOException("Gagal menulis: $name")
    }

    override fun createDirectories(path: String) {
        var dir: DocumentFile = root
        for (segment in path.split('/').filter { it.isNotEmpty() }) {
            dir = childDirectory(dir, segment)
        }
    }

    override fun deleteFile(path: String) {
        val doc = find(path) ?: return
        if (!doc.delete()) throw IOException("Gagal menghapus: $path")
    }

    override fun move(from: String, to: String) {
        writeBytes(to, readBytes(from))
        deleteFile(from)
    }

    private fun childDirectory(parent: DocumentFile, name: String): DocumentFile {
        parent.findFile(name)?.takeIf { it.isDirectory }?.let { return it }
        return parent.createDirectory(name) ?: throw IOException("Gagal membuat folder: $name")
    }
}
