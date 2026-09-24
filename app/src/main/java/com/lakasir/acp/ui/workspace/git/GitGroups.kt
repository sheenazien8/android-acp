package com.lakasir.acp.ui.workspace.git

import com.lakasir.acp.acp.GitFileChange
import com.lakasir.acp.acp.GitStatus

data class GitGroups(
    val conflicts: List<GitFileChange> = emptyList(),
    val staged: List<GitFileChange> = emptyList(),
    val changes: List<GitFileChange> = emptyList(),
    val untracked: List<GitFileChange> = emptyList(),
) {
    val isClean: Boolean get() = conflicts.isEmpty() && staged.isEmpty() && changes.isEmpty() && untracked.isEmpty()

    companion object {
        fun of(status: GitStatus?): GitGroups {
            val files = status?.files.orEmpty()
            return GitGroups(
                conflicts = files.filter { it.conflicted },
                staged = files.filter { it.isStaged },
                changes = files.filter { it.hasWorktreeChanges },
                untracked = files.filter { it.untracked },
            )
        }
    }
}

data class GitDecorations(val files: Map<String, String> = emptyMap(), val dirs: Set<String> = emptySet()) {
    fun badgeFor(path: String, directory: Boolean): String? =
        files[path] ?: if (directory && path in dirs) DIR_MARK else null

    companion object {
        const val DIR_MARK = "•"
        val EMPTY = GitDecorations()

        fun of(status: GitStatus?): GitDecorations {
            if (status == null || !status.isRepo) return EMPTY
            val files = mutableMapOf<String, String>()
            val dirs = mutableSetOf<String>()
            status.files.forEach { change ->
                val path = change.path.trimEnd('/')
                files[path] = GitBadge.letter(change)
                var parent = path.substringBeforeLast('/', missingDelimiterValue = "")
                while (parent.isNotEmpty()) {
                    dirs += parent
                    parent = parent.substringBeforeLast('/', missingDelimiterValue = "")
                }
            }
            return GitDecorations(files, dirs)
        }
    }
}

enum class GitBadgeKind { Added, Modified, Deleted, Renamed, Conflict, Untracked, Other }

object GitBadge {
    fun letter(change: GitFileChange): String = when {
        change.conflicted -> "U"
        change.untracked -> "?"
        else -> change.worktree ?: change.index ?: "M"
    }

    fun letter(change: GitFileChange, staged: Boolean): String = when {
        change.conflicted -> "U"
        change.untracked -> "?"
        staged -> change.index ?: "M"
        else -> change.worktree ?: "M"
    }

    fun kind(letter: String): GitBadgeKind = when (letter) {
        "A" -> GitBadgeKind.Added
        "M", "T" -> GitBadgeKind.Modified
        "D" -> GitBadgeKind.Deleted
        "R", "C" -> GitBadgeKind.Renamed
        "U" -> GitBadgeKind.Conflict
        "?" -> GitBadgeKind.Untracked
        else -> GitBadgeKind.Other
    }

    fun description(letter: String): String = when (kind(letter)) {
        GitBadgeKind.Added -> "added"
        GitBadgeKind.Modified -> "modified"
        GitBadgeKind.Deleted -> "deleted"
        GitBadgeKind.Renamed -> "renamed"
        GitBadgeKind.Conflict -> "conflict"
        GitBadgeKind.Untracked -> "untracked"
        GitBadgeKind.Other -> if (letter == GitDecorations.DIR_MARK) "contains changes" else "changed"
    }
}

fun GitStatus.branchLabel(): String = when {
    detached -> "detached at ${oid ?: "?"}"
    branch != null -> branch
    else -> "no branch"
}

fun GitStatus.syncLabel(): String? = when {
    upstream == null -> null
    ahead == 0 && behind == 0 -> "up to date with $upstream"
    else -> listOfNotNull("↑$ahead".takeIf { ahead > 0 }, "↓$behind".takeIf { behind > 0 }).joinToString(" ") + " $upstream"
}
