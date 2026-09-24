package com.lakasir.acp.ui.workspace.git

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lakasir.acp.acp.GitCommit
import com.lakasir.acp.acp.GitFileChange
import com.lakasir.acp.acp.GitStatus
import com.lakasir.acp.ui.components.EmptyState
import com.lakasir.acp.ui.components.relativeTime
import com.lakasir.acp.ui.theme.AcpTheme

data class GitActions(
    val onRefresh: () -> Unit,
    val onOpenDiff: (GitFileChange, staged: Boolean) -> Unit,
    val onOpenCommit: (GitCommit) -> Unit,
    val onStage: (GitFileChange) -> Unit,
    val onUnstage: (GitFileChange) -> Unit,
    val onStageAll: (List<GitFileChange>) -> Unit,
    val onUnstageAll: (List<GitFileChange>) -> Unit,
    val onMessageChange: (String) -> Unit,
    val onCommit: () -> Unit,
    val onLoadMore: () -> Unit,
)

@Composable
fun GitPane(state: GitUiState, actions: GitActions, modifier: Modifier = Modifier) {
    val status = state.status
    Column(modifier.fillMaxSize()) {
        when {
            status == null && state.error != null -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EmptyState("Couldn't load git status", state.error, Modifier.weight(1f))
                OutlinedButton(onClick = actions.onRefresh, modifier = Modifier.padding(bottom = 32.dp)) { Text("Try again") }
            }
            status == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
            }
            !status.isRepo -> EmptyState("Not a git repository", "The workspace folder isn't inside a git repository.")
            else -> {
                BranchHeader(status, loading = state.loading)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                GitList(state, status, actions, Modifier.weight(1f))
                if (state.groups.staged.isNotEmpty() || state.message.isNotBlank()) CommitBox(state, actions)
            }
        }
    }
}

@Composable
private fun BranchHeader(status: GitStatus, loading: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.AccountTree,
            contentDescription = "Branch",
            modifier = Modifier.padding(end = 8.dp).size(18.dp),
            tint = AcpTheme.extended.accentText,
        )
        Column(Modifier.weight(1f)) {
            Text(
                status.branchLabel(),
                style = AcpTheme.code.codeMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            status.syncLabel()?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
    }
}

@Composable
private fun GitList(state: GitUiState, status: GitStatus, actions: GitActions, modifier: Modifier) {
    val groups = state.groups
    var collapsed by rememberSaveable { mutableStateOf(setOf<String>()) }
    val toggle = { name: String -> collapsed = if (name in collapsed) collapsed - name else collapsed + name }

    LazyColumn(modifier.fillMaxSize()) {
        if (groups.isClean) {
            item(key = "clean") { Note("No changes. The working tree is clean.") }
        }
        section("Conflicts", groups.conflicts, staged = false, state, actions, collapsed, toggle, bulk = null, onRow = actions.onStage)
        section(
            "Staged", groups.staged, staged = true, state, actions, collapsed, toggle,
            bulk = "Unstage all" to { actions.onUnstageAll(groups.staged) }, onRow = actions.onUnstage,
        )
        section(
            "Changes", groups.changes, staged = false, state, actions, collapsed, toggle,
            bulk = "Stage all" to { actions.onStageAll(groups.changes) }, onRow = actions.onStage,
        )
        section(
            "Untracked", groups.untracked, staged = false, state, actions, collapsed, toggle,
            bulk = "Stage all" to { actions.onStageAll(groups.untracked) }, onRow = actions.onStage,
        )
        if (status.truncated) item(key = "truncated") { Note("Too many changes, only the first ones are shown") }

        item(key = "history-header") {
            SectionHeader("History", count = null, expanded = "History" !in collapsed, onToggle = { toggle("History") }, bulk = null)
        }
        if ("History" !in collapsed) {
            if (state.commits.isEmpty() && !state.logLoading) item(key = "history-empty") { Note("No commits yet") }
            items(state.commits, key = { "commit-${it.hash}" }) { commit -> CommitRow(commit) { actions.onOpenCommit(commit) } }
            if (state.logLoading) {
                item(key = "history-loading") {
                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            } else if (!state.logEnd) {
                item(key = "history-more") {
                    TextButton(onClick = actions.onLoadMore, modifier = Modifier.padding(start = 8.dp)) { Text("Load more") }
                }
            }
        }
    }
}

private fun LazyListScope.section(
    name: String,
    files: List<GitFileChange>,
    staged: Boolean,
    state: GitUiState,
    actions: GitActions,
    collapsed: Set<String>,
    onToggle: (String) -> Unit,
    bulk: Pair<String, () -> Unit>?,
    onRow: ((GitFileChange) -> Unit)?,
) {
    if (files.isEmpty()) return
    val expanded = name !in collapsed
    item(key = "section-$name") {
        SectionHeader(name, files.size, expanded, onToggle = { onToggle(name) }, bulk = bulk)
    }
    if (!expanded) return
    items(files, key = { "$name-${it.path}" }) { file ->
        GitFileRow(
            file = file,
            staged = staged,
            busy = file.path in state.busyPaths,
            onOpen = { actions.onOpenDiff(file, staged) },
            onAction = onRow?.let { { it(file) } },
        )
    }
}

@Composable
private fun SectionHeader(name: String, count: Int?, expanded: Boolean, onToggle: () -> Unit, bulk: Pair<String, () -> Unit>?) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = if (expanded) "Collapse $name" else "Expand $name", onClick = onToggle)
            .heightIn(min = 44.dp)
            .padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            if (count != null) "$name ($count)" else name,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (bulk != null && expanded) TextButton(onClick = bulk.second) { Text(bulk.first) }
    }
}

@Composable
fun gitBadgeColor(letter: String): Color = when (GitBadge.kind(letter)) {
    GitBadgeKind.Added, GitBadgeKind.Untracked -> AcpTheme.extended.diffAdded
    GitBadgeKind.Deleted -> AcpTheme.extended.diffRemoved
    GitBadgeKind.Modified -> AcpTheme.extended.warning
    GitBadgeKind.Renamed -> AcpTheme.extended.accentText
    GitBadgeKind.Conflict -> MaterialTheme.colorScheme.error
    GitBadgeKind.Other -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun GitBadgeText(letter: String, modifier: Modifier = Modifier) {
    Text(
        letter,
        modifier = modifier.semantics { contentDescription = GitBadge.description(letter) },
        style = AcpTheme.code.codeSmall,
        color = gitBadgeColor(letter),
    )
}

@Composable
private fun GitFileRow(file: GitFileChange, staged: Boolean, busy: Boolean, onOpen: () -> Unit, onAction: (() -> Unit)?) {
    val letter = GitBadge.letter(file, staged)
    val path = file.path.trimEnd('/')
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !file.isDirectory, onClickLabel = "Show diff", onClick = onOpen)
            .heightIn(min = 48.dp)
            .padding(start = 32.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GitBadgeText(letter, Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(
                path.substringAfterLast('/') + if (file.isDirectory) "/" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val detail = listOfNotNull(
                path.substringBeforeLast('/', "").ifEmpty { null },
                file.origPath?.let { "from $it" },
            ).joinToString(" · ")
            if (detail.isNotEmpty()) {
                Text(
                    detail,
                    style = AcpTheme.code.codeSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        when {
            busy -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            }
            onAction != null -> IconButton(onClick = onAction) {
                Icon(
                    if (staged) Icons.Filled.Remove else Icons.Filled.Add,
                    contentDescription = if (staged) "Unstage $path" else "Stage $path",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun CommitRow(commit: GitCommit, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Show commit", onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(start = 32.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            commit.subject,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row {
            Text(commit.shortHash, style = AcpTheme.code.codeSmall, color = AcpTheme.extended.accentText)
            Text(
                " · ${commit.author} · ${relativeTime(commit.time)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CommitBox(state: GitUiState, actions: GitActions) {
    Column(Modifier.fillMaxWidth().imePadding()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.message,
                onValueChange = actions.onMessageChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Commit message") },
                minLines = 2,
                maxLines = 5,
                enabled = !state.committing,
                shape = MaterialTheme.shapes.small,
                textStyle = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = actions.onCommit, enabled = state.canCommit, modifier = Modifier.fillMaxWidth()) {
                val count = state.groups.staged.size
                Text(if (state.committing) "Committing…" else "Commit $count ${if (count == 1) "file" else "files"}")
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private val previewActions = GitActions({}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {})

@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 320, heightDp = 640)
@Composable
private fun GitPaneDarkPreview() {
    AcpTheme(darkTheme = true) {
        GitPane(
            GitUiState(
                status = GitStatus(
                    isRepo = true, branch = "feature/files", upstream = "origin/feature/files", ahead = 2,
                    files = listOf(
                        GitFileChange("app/src/Main.kt", index = "M"),
                        GitFileChange("docs/plan/15-git.md", worktree = "M"),
                        GitFileChange("bridge/src/git.js", untracked = true),
                    ),
                ),
                commits = listOf(GitCommit("abc1234def", "abc1234", "Add workspace sidebar", "Dev", System.currentTimeMillis() - 3_600_000)),
                message = "Update docs",
            ),
            previewActions,
        )
    }
}
