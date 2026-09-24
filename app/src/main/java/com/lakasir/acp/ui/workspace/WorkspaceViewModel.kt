package com.lakasir.acp.ui.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lakasir.acp.AcpApp
import com.lakasir.acp.acp.FsEntry
import com.lakasir.acp.data.repository.WorkspaceAvailability
import com.lakasir.acp.data.repository.WorkspaceRepository
import com.lakasir.acp.data.repository.WorkspaceRepository.AttachResult
import com.lakasir.acp.data.repository.WorkspaceResult
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WorkspaceViewModel(
    private val sessionId: Long,
    private val workspace: WorkspaceRepository,
) : ViewModel() {

    private val folders = MutableStateFlow<Map<String, FolderState>>(emptyMap())
    private val expanded = MutableStateFlow<Set<String>>(emptySet())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val availability: StateFlow<WorkspaceAvailability> = workspace.availability(sessionId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, WorkspaceAvailability.Offline)

    val root: StateFlow<FolderState?> = folders.map { it[WorkspacePaths.ROOT] }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val rows: StateFlow<List<TreeRow>> = combine(folders, expanded) { f, e -> flattenTree(f, e) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            availability.collect { state ->
                if (state is WorkspaceAvailability.Ready) refresh() else folders.value = emptyMap()
            }
        }
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            workspace.changes(sessionId).debounce(REFRESH_DEBOUNCE_MS).collect { refresh() }
        }
    }

    fun refresh() {
        if (availability.value !is WorkspaceAvailability.Ready) return
        (setOf(WorkspacePaths.ROOT) + expanded.value).forEach(::load)
    }

    fun toggle(entry: FsEntry) {
        if (!entry.isDirectory) return
        if (entry.path in expanded.value) {
            expanded.update { it - entry.path }
        } else {
            expanded.update { it + entry.path }
            if (folders.value[entry.path]?.loaded != true) load(entry.path)
        }
    }

    fun create(parent: String, name: String, directory: Boolean) {
        val path = WorkspacePaths.child(parent, name.trim())
        viewModelScope.launch {
            when (val result = workspace.run(sessionId) { create(path, directory) }) {
                is WorkspaceResult.Success -> {
                    if (parent != WorkspacePaths.ROOT) expanded.update { it + parent }
                    load(parent)
                    _messages.tryEmit("Created ${result.value.name}")
                }
                is WorkspaceResult.Failure -> _messages.tryEmit(result.message)
            }
        }
    }

    fun rename(entry: FsEntry, newName: String) {
        val parent = WorkspacePaths.parent(entry.path)
        val target = WorkspacePaths.child(parent, newName.trim())
        if (target == entry.path) return
        viewModelScope.launch {
            when (val result = workspace.run(sessionId) { rename(entry.path, target) }) {
                is WorkspaceResult.Success -> {
                    forget(entry.path)
                    workspace.detachUnder(sessionId, entry.path)
                    load(parent)
                }
                is WorkspaceResult.Failure -> _messages.tryEmit(result.message)
            }
        }
    }

    fun delete(entry: FsEntry) {
        val parent = WorkspacePaths.parent(entry.path)
        viewModelScope.launch {
            when (val result = workspace.run(sessionId) { delete(entry.path, recursive = entry.isDirectory) }) {
                is WorkspaceResult.Success -> {
                    forget(entry.path)
                    workspace.detachUnder(sessionId, entry.path)
                    load(parent)
                    _messages.tryEmit("Deleted ${entry.name}")
                }
                is WorkspaceResult.Failure -> _messages.tryEmit(result.message)
            }
        }
    }

    fun attach(path: String) {
        val message = when (workspace.attach(sessionId, path)) {
            AttachResult.Added -> "Attached ${WorkspacePaths.name(path)}"
            AttachResult.AlreadyAttached -> "${WorkspacePaths.name(path)} is already attached"
            AttachResult.LimitReached -> "Up to ${WorkspaceRepository.MAX_ATTACHMENTS} files can be attached"
        }
        _messages.tryEmit(message)
    }

    private fun forget(path: String) {
        expanded.update { set -> set.filterNot { WorkspacePaths.isUnder(it, path) }.toSet() }
        folders.update { map -> map.filterKeys { !WorkspacePaths.isUnder(it, path) } }
    }

    private fun load(path: String) {
        folders.update { it + (path to (it[path] ?: FolderState()).copy(loading = true)) }
        viewModelScope.launch {
            when (val result = workspace.run(sessionId) { list(path) }) {
                is WorkspaceResult.Success -> folders.update {
                    it + (path to FolderState(result.value.entries, loaded = true, truncated = result.value.truncated))
                }
                is WorkspaceResult.Failure -> if (result.isNotFound && path != WorkspacePaths.ROOT) {
                    forget(path)
                } else {
                    folders.update { it + (path to (it[path] ?: FolderState()).copy(loading = false, error = result.message)) }
                }
            }
        }
    }

    companion object {
        private const val REFRESH_DEBOUNCE_MS = 500L

        fun factory(sessionId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as AcpApp).container
                WorkspaceViewModel(sessionId, container.workspaceRepository)
            }
        }
    }
}
