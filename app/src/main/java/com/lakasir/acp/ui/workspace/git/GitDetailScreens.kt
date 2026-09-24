package com.lakasir.acp.ui.workspace.git

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lakasir.acp.AcpApp
import com.lakasir.acp.acp.GitCommitDetail
import com.lakasir.acp.acp.GitDiff
import com.lakasir.acp.acp.WorkspaceApi
import com.lakasir.acp.data.model.DiffState
import com.lakasir.acp.data.repository.WorkspaceRepository
import com.lakasir.acp.data.repository.WorkspaceResult
import com.lakasir.acp.ui.chat.components.DiffBlock
import com.lakasir.acp.ui.components.EmptyState
import com.lakasir.acp.ui.components.relativeTime
import com.lakasir.acp.ui.theme.AcpTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoadState<T>(val loading: Boolean = true, val value: T? = null, val error: String? = null)

class GitLoadViewModel<T>(
    private val sessionId: Long,
    private val workspace: WorkspaceRepository,
    private val load: suspend WorkspaceApi.() -> T,
) : ViewModel() {

    private val _state = MutableStateFlow(LoadState<T>())
    val state: StateFlow<LoadState<T>> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            _state.value = when (val result = workspace.run(sessionId, load)) {
                is WorkspaceResult.Success -> LoadState(loading = false, value = result.value)
                is WorkspaceResult.Failure -> LoadState(loading = false, error = result.message)
            }
        }
    }

    companion object {
        fun <T> factory(sessionId: Long, load: suspend WorkspaceApi.() -> T): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as AcpApp).container
                GitLoadViewModel(sessionId, container.workspaceRepository, load)
            }
        }
    }
}

private const val SCREEN_DIFF_LINES = 3000

@Composable
fun GitDiffScreen(sessionId: Long, path: String, staged: Boolean, origPath: String?, onBack: () -> Unit) {
    val viewModel: GitLoadViewModel<GitDiff> = viewModel(
        key = "git-diff-$sessionId-$staged-$path",
        factory = GitLoadViewModel.factory(sessionId) { gitDiff(path, staged, origPath) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    DetailScaffold(
        title = path.substringAfterLast('/'),
        subtitle = (if (staged) "Staged · " else "Unstaged · ") + path,
        onBack = onBack,
        onReload = viewModel::reload,
        state = state,
    ) { diff ->
        when {
            diff.binary -> EmptyState("Binary file", "Diff isn't available for binary files.")
            diff.oldText == diff.newText -> EmptyState("No content changes", "Only the file name or mode changed.")
            else -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (diff.truncated) {
                    Text(
                        "The file is large, only the first 512 KB are compared.",
                        style = MaterialTheme.typography.labelSmall,
                        color = AcpTheme.extended.warning,
                    )
                }
                DiffBlock(DiffState(path, diff.oldText, diff.newText), maxLines = SCREEN_DIFF_LINES)
            }
        }
    }
}

@Composable
fun GitCommitScreen(sessionId: Long, hash: String, onBack: () -> Unit) {
    val viewModel: GitLoadViewModel<GitCommitDetail> = viewModel(
        key = "git-commit-$sessionId-$hash",
        factory = GitLoadViewModel.factory(sessionId) { gitShow(hash) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    DetailScaffold(
        title = state.value?.subject ?: "Commit",
        subtitle = hash.take(7),
        onBack = onBack,
        onReload = viewModel::reload,
        state = state,
    ) { commit -> CommitDetail(commit) }
}

@Composable
private fun CommitDetail(commit: GitCommitDetail) {
    SelectionContainer {
        LazyColumn(
            Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item(key = "header") {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(commit.subject, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    if (commit.body.isNotBlank()) {
                        Text(commit.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(
                        listOfNotNull(commit.author, commit.email?.let { "<$it>" }, relativeTime(commit.time)).joinToString(" "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(commit.hash, style = AcpTheme.code.codeSmall, color = AcpTheme.extended.accentText)
                }
            }
            item(key = "files-header") {
                Column {
                    HorizontalDivider(Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.outline)
                    Text(
                        "${commit.files.size} ${if (commit.files.size == 1) "file" else "files"} changed",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            items(commit.files, key = { it.path }) { file ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 36.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        file.path,
                        modifier = Modifier.weight(1f),
                        style = AcpTheme.code.codeSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (file.additions == null) {
                        Text("binary", style = AcpTheme.code.codeSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("+${file.additions}", style = AcpTheme.code.codeSmall, color = AcpTheme.extended.diffAdded)
                        Text(" −${file.deletions ?: 0}", style = AcpTheme.code.codeSmall, color = AcpTheme.extended.diffRemoved)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> DetailScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onReload: () -> Unit,
    state: LoadState<T>,
    content: @Composable (T) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            subtitle,
                            style = AcpTheme.code.codeSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = onReload) { Icon(Icons.Filled.Refresh, contentDescription = "Reload") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val value = state.value
            when {
                state.loading && value == null -> CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), strokeWidth = 2.dp)
                state.error != null -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState("Couldn't load", state.error, Modifier.weight(1f))
                    OutlinedButton(onClick = onReload, modifier = Modifier.padding(bottom = 32.dp)) { Text("Try again") }
                }
                value != null -> content(value)
            }
        }
    }
}
