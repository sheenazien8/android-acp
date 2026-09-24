package com.lakasir.acp.ui.workspace.git

import com.lakasir.acp.acp.GitFileChange
import com.lakasir.acp.acp.GitStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitGroupsTest {

    private val both = GitFileChange("src/Main.kt", index = "M", worktree = "M")
    private val stagedOnly = GitFileChange("README.md", index = "A")
    private val renamed = GitFileChange("docs/new.md", origPath = "docs/old.md", index = "R")
    private val untracked = GitFileChange("notes/", untracked = true)
    private val conflict = GitFileChange("app/Conflict.kt", index = "U", worktree = "U", conflicted = true)
    private val status = GitStatus(isRepo = true, branch = "main", files = listOf(both, stagedOnly, renamed, untracked, conflict))

    @Test
    fun `files are grouped and a file changed in both places appears twice`() {
        val groups = GitGroups.of(status)
        assertEquals(listOf(conflict), groups.conflicts)
        assertEquals(listOf(both, stagedOnly, renamed), groups.staged)
        assertEquals(listOf(both), groups.changes)
        assertEquals(listOf(untracked), groups.untracked)
        assertFalse(groups.isClean)
        assertTrue(GitGroups.of(GitStatus(isRepo = true)).isClean)
        assertTrue(GitGroups.of(null).isClean)
    }

    @Test
    fun `badge letters depend on the side shown`() {
        assertEquals("M", GitBadge.letter(both, staged = true))
        assertEquals("A", GitBadge.letter(stagedOnly, staged = true))
        assertEquals("U", GitBadge.letter(conflict, staged = false))
        assertEquals("?", GitBadge.letter(untracked, staged = false))
        assertEquals(GitBadgeKind.Renamed, GitBadge.kind("R"))
        assertEquals("untracked", GitBadge.description("?"))
    }

    @Test
    fun `decorations mark files and their parent folders`() {
        val decorations = GitDecorations.of(status)
        assertEquals("M", decorations.badgeFor("src/Main.kt", directory = false))
        assertEquals("?", decorations.badgeFor("notes", directory = true))
        assertEquals(GitDecorations.DIR_MARK, decorations.badgeFor("docs", directory = true))
        assertEquals(GitDecorations.DIR_MARK, decorations.badgeFor("src", directory = true))
        assertNull(decorations.badgeFor("docs", directory = false))
        assertNull(decorations.badgeFor("other.kt", directory = false))
        assertEquals(GitDecorations.EMPTY, GitDecorations.of(GitStatus(isRepo = false)))
    }

    @Test
    fun `branch and sync labels`() {
        assertEquals("main", status.branchLabel())
        assertEquals("detached at abc1234", GitStatus(isRepo = true, detached = true, oid = "abc1234").branchLabel())
        assertNull(status.syncLabel())
        assertEquals("up to date with origin/main", status.copy(upstream = "origin/main").syncLabel())
        assertEquals("↑2 ↓1 origin/main", status.copy(upstream = "origin/main", ahead = 2, behind = 1).syncLabel())
    }

    @Test
    fun `staging a rename includes both paths`() {
        assertEquals(listOf("docs/new.md", "docs/old.md", "README.md"), GitViewModel.pathsOf(listOf(renamed, stagedOnly)))
    }

    @Test
    fun `commit needs a message and staged files`() {
        val ready = GitUiState(status = status, message = "Fix")
        assertTrue(ready.canCommit)
        assertFalse(ready.copy(message = "  ").canCommit)
        assertFalse(ready.copy(committing = true).canCommit)
        assertFalse(ready.copy(status = GitStatus(isRepo = true, files = listOf(untracked))).canCommit)
    }
}
