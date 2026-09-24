package com.lakasir.acp.ui.workspace.git

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lakasir.acp.AcpApp
import com.lakasir.acp.acp.GitCommit
import com.lakasir.acp.acp.GitFileChange
import com.lakasir.acp.acp.GitStatus
import com.lakasir.acp.acp.WorkspaceApi
import com.lakasir.acp.data.repository.WorkspaceAvailability
import com.lakasir.acp.data.repository.WorkspaceRepository
import com.lakasir.acp.data.repository.WorkspaceResult
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GitUiState(
    val status: GitStatus? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val commits: List<GitCommit> = emptyList(),
    val logLoading: Boolean = false,
    val logEnd: Boolean = false,
    val message: String = "",
    val committing: Boolean = false,
    val busyPaths: Set<String> = emptySet(),
) {
    val groups: GitGroups get() = GitGroups.of(status)
    val canCommit: Boolean get() = !committing && message.isNotBlank() && groups.staged.isNotEmpty()
}

class GitViewModel(
    private val sessionId: Long,
    private val workspace: WorkspaceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(GitUiState())
    val state: StateFlow<GitUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val enabled: StateFlow<Boolean> = workspace.availability(sessionId)
        .map { it is WorkspaceAvailability.Ready && it.features.git }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val decorations: StateFlow<GitDecorations> = _state
        .map { GitDecorations.of(it.status) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GitDecorations.EMPTY)

    private var statusJob: Job? = null

    init {
        viewModelScope.launch {
            enabled.collect { on -> if (on) refresh() else _state.value = GitUiState(message = _state.value.message) }
        }
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            workspace.changes(sessionId).debounce(REFRESH_DEBOUNCE_MS).collect { refresh() }
        }
    }

    fun refresh() {
        if (!enabled.value) return
        loadStatus()
        loadLog(reset = true)
    }

    fun updateMessage(message: String) {
        _state.update { it.copy(message = message) }
    }

    fun stage(file: GitFileChange) = mutate(pathsOf(listOf(file))) { gitStage(it) }

    fun unstage(file: GitFileChange) = mutate(pathsOf(listOf(file))) { gitUnstage(it) }

    fun stageAll(files: List<GitFileChange>) = mutate(pathsOf(files)) { gitStage(it) }

    fun unstageAll(files: List<GitFileChange>) = mutate(pathsOf(files)) { gitUnstage(it) }

    fun commit() {
        val current = _state.value
        if (!current.canCommit) return
        _state.update { it.copy(committing = true) }
        viewModelScope.launch {
            when (val result = workspace.run(sessionId) { gitCommit(current.message.trim()) }) {
                is WorkspaceResult.Success -> {
                    _state.update { it.copy(committing = false, message = "") }
                    _messages.tryEmit("Committed ${result.value.shortHash}")
                    refresh()
                }
                is WorkspaceResult.Failure -> {
                    _state.update { it.copy(committing = false) }
                    _messages.tryEmit(result.message)
                    loadStatus()
                }
            }
        }
    }

    fun loadMoreLog() {
        val current = _state.value
        if (current.logLoading || current.logEnd) return
        loadLog(reset = false)
    }

    private fun mutate(paths: List<String>, action: suspend WorkspaceApi.(List<String>) -> Unit) {
        if (paths.isEmpty()) return
        _state.update { it.copy(busyPaths = it.busyPaths + paths) }
        viewModelScope.launch {
            val result = workspace.run(sessionId) { action(paths) }
            _state.update { it.copy(busyPaths = it.busyPaths - paths.toSet()) }
            if (result is WorkspaceResult.Failure) _messages.tryEmit(result.message)
            loadStatus()
        }
    }

    private fun loadStatus() {
        statusJob?.cancel()
        _state.update { it.copy(loading = true) }
        statusJob = viewModelScope.launch {
            when (val result = workspace.run(sessionId) { gitStatus() }) {
                is WorkspaceResult.Success -> _state.update { it.copy(loading = false, error = null, status = result.value) }
                is WorkspaceResult.Failure -> _state.update { it.copy(loading = false, error = result.message) }
            }
        }
    }

    private fun loadLog(reset: Boolean) {
        val skip = if (reset) 0 else _state.value.commits.size
        _state.update { it.copy(logLoading = true) }
        viewModelScope.launch {
            when (val result = workspace.run(sessionId) { gitLog(LOG_PAGE, skip) }) {
                is WorkspaceResult.Success -> _state.update {
                    val page = result.value.commits
                    it.copy(
                        logLoading = false,
                        commits = if (reset) page else it.commits + page,
                        logEnd = page.size < LOG_PAGE,
                    )
                }
                is WorkspaceResult.Failure -> _state.update { it.copy(logLoading = false, logEnd = true) }
            }
        }
    }

    companion object {
        const val LOG_PAGE = 30
        private const val REFRESH_DEBOUNCE_MS = 500L

        fun pathsOf(files: List<GitFileChange>): List<String> =
            files.flatMap { listOfNotNull(it.path, it.origPath) }.distinct()

        fun factory(sessionId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as AcpApp).container
                GitViewModel(sessionId, container.workspaceRepository)
            }
        }
    }
}
