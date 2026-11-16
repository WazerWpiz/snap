// SPDX-License-Identifier: GPL-3.0-or-later
// Operasi Git lewat JGit (tidak ada binary git di Android). Hanya untuk workspace folder lokal.
package app.snapgent.workspace

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.dircache.DirCacheIterator
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.treewalk.FileTreeIterator
import org.eclipse.jgit.treewalk.filter.PathFilter
import java.io.ByteArrayOutputStream
import java.io.File

class GitOps(private val root: File) {

    private fun open(): Git {
        if (!File(root, ".git").exists()) throw IllegalStateException("Workspace bukan repository Git.")
        return Git.open(root)
    }

    fun status(): String = open().use { git ->
        val status = git.status().call()
        val lines = mutableListOf("## ${git.repository.branch}")
        status.added.sorted().forEach { lines += "A  $it" }
        status.changed.sorted().forEach { lines += "M  $it" }
        status.removed.sorted().forEach { lines += "D  $it" }
        status.modified.sorted().forEach { lines += " M $it" }
        status.missing.sorted().forEach { lines += " D $it" }
        status.conflicting.sorted().forEach { lines += "UU $it" }
        status.untracked.sorted().forEach { lines += "?? $it" }
        lines.joinToString("\n")
    }

    /** Diff perubahan yang belum di-stage (index vs working tree), mirip `git diff`. */
    fun diff(path: String): String = open().use { git ->
        val repo: Repository = git.repository
        val out = ByteArrayOutputStream()
        DiffFormatter(out).use { formatter ->
            formatter.setRepository(repo)
            if (path.isNotEmpty()) formatter.setPathFilter(PathFilter.create(path))
            val entries = formatter.scan(DirCacheIterator(repo.readDirCache()), FileTreeIterator(repo))
            formatter.format(entries)
            formatter.flush()
        }
        out.toString(Charsets.UTF_8.name()).ifEmpty { "(no output)" }
    }

    fun log(limit: Int): String = open().use { git ->
        val commits = git.log().setMaxCount(limit).call()
        commits.joinToString("\n") { commit ->
            "${commit.abbreviate(7).name()} ${commit.shortMessage}"
        }.ifEmpty { "(no commits)" }
    }

    fun branches(): String = open().use { git ->
        git.branchList().call()
            .map { Repository.shortenRefName(it.name) }
            .sorted()
            .joinToString("\n")
            .ifEmpty { "(no branches)" }
    }

    fun add(paths: List<String>): String = open().use { git ->
        val command = git.add()
        paths.forEach { command.addFilepattern(it) }
        command.call()
        "Staged ${paths.size} path(s): " + paths.joinToString(", ")
    }

    fun commit(message: String): String = open().use { git ->
        val commit = git.commit().setMessage(message).call()
        "[${git.repository.branch} ${commit.abbreviate(7).name()}] ${message.lineSequence().first()}"
    }
}
