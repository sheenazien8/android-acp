package com.lakasir.acp.acp

import kotlinx.serialization.Serializable

@Serializable
data class GitFileChange(
    val path: String,
    val origPath: String? = null,
    val index: String? = null,
    val worktree: String? = null,
    val conflicted: Boolean = false,
    val untracked: Boolean = false,
) {
    val isDirectory: Boolean get() = path.endsWith("/")
    val isStaged: Boolean get() = index != null && !conflicted && !untracked
    val hasWorktreeChanges: Boolean get() = worktree != null && !conflicted && !untracked
}

@Serializable
data class GitStatus(
    val isRepo: Boolean = false,
    val branch: String? = null,
    val detached: Boolean = false,
    val oid: String? = null,
    val upstream: String? = null,
    val ahead: Int = 0,
    val behind: Int = 0,
    val files: List<GitFileChange> = emptyList(),
    val truncated: Boolean = false,
)

@Serializable
data class GitDiff(
    val path: String,
    val oldText: String? = null,
    val newText: String = "",
    val binary: Boolean = false,
    val truncated: Boolean = false,
)

@Serializable
data class GitCommit(
    val hash: String,
    val shortHash: String,
    val subject: String = "",
    val author: String = "",
    val time: Long = 0,
)

@Serializable
data class GitLog(val commits: List<GitCommit> = emptyList())

@Serializable
data class GitFileStat(val path: String, val additions: Int? = null, val deletions: Int? = null)

@Serializable
data class GitCommitDetail(
    val hash: String,
    val shortHash: String,
    val subject: String = "",
    val body: String = "",
    val author: String = "",
    val email: String? = null,
    val time: Long = 0,
    val files: List<GitFileStat> = emptyList(),
)

@Serializable
data class GitCommitResult(val hash: String, val shortHash: String)
