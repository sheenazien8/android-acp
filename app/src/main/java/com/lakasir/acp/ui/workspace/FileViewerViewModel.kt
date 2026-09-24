package com.lakasir.acp.ui.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lakasir.acp.AcpApp
import com.lakasir.acp.acp.FsFile
import com.lakasir.acp.data.repository.WorkspaceRepository
import com.lakasir.acp.data.repository.WorkspaceRepository.AttachResult
import com.lakasir.acp.data.repository.WorkspaceResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FileViewerState(
    val loading: Boolean = true,
    val file: FsFile? = null,
    val error: String? = null,
    val editing: Boolean = false,
    val draft: String = "",
    val saving: Boolean = false,
    val conflict: Boolean = false,
    val rendered: Boolean = false,
) {
    val editable: Boolean get() = file?.text != null && !file.truncated
    val dirty: Boolean get() = editing && draft != file?.text
    val canRender: Boolean get() = file?.text != null && isMarkdown(file.path)
    val showRendered: Boolean get() = rendered && canRender && !editing

    companion object {
        private val MARKDOWN_EXTENSIONS = setOf("md", "markdown", "mdx", "mdown", "mkd")

        fun isMarkdown(path: String): Boolean =
            path.substringAfterLast('/').substringAfterLast('.', "").lowercase() in MARKDOWN_EXTENSIONS
    }
}

class FileViewerViewModel(
    private val sessionId: Long,
    val path: String,
    private val workspace: WorkspaceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(FileViewerState())
    val state: StateFlow<FileViewerState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null, conflict = false, editing = false) }
        viewModelScope.launch {
            when (val result = workspace.run(sessionId) { read(path) }) {
                is WorkspaceResult.Success -> _state.update { it.copy(loading = false, file = result.value) }
                is WorkspaceResult.Failure -> _state.update { it.copy(loading = false, error = result.message) }
            }
        }
    }

    fun startEditing() {
        val current = _state.value
        if (!current.editable) return
        _state.update { it.copy(editing = true, draft = current.file?.text.orEmpty()) }
    }

    fun updateDraft(text: String) {
        _state.update { it.copy(draft = text) }
    }

    fun cancelEditing() {
        _state.update { it.copy(editing = false, draft = "") }
    }

    fun save(overwrite: Boolean = false) {
        val current = _state.value
        val file = current.file ?: return
        if (!current.editing || current.saving) return
        _state.update { it.copy(saving = true, conflict = false) }
        viewModelScope.launch {
            val expected = if (overwrite) null else file.mtime
            when (val result = workspace.run(sessionId) { write(path, current.draft, expected) }) {
                is WorkspaceResult.Success -> {
                    _state.update {
                        it.copy(
                            saving = false,
                            editing = false,
                            file = file.copy(text = current.draft, mtime = result.value.mtime, size = result.value.size),
                        )
                    }
                    _messages.tryEmit("Saved")
                }
                is WorkspaceResult.Failure -> {
                    _state.update { it.copy(saving = false, conflict = result.isConflict) }
                    if (!result.isConflict) _messages.tryEmit(result.message)
                }
            }
        }
    }

    fun toggleRendered() {
        _state.update { it.copy(rendered = !it.rendered) }
    }

    fun dismissConflict() {
        _state.update { it.copy(conflict = false) }
    }

    fun attach() {
        val message = when (workspace.attach(sessionId, path)) {
            AttachResult.Added -> "Attached to the next prompt"
            AttachResult.AlreadyAttached -> "Already attached"
            AttachResult.LimitReached -> "Up to ${WorkspaceRepository.MAX_ATTACHMENTS} files can be attached"
        }
        _messages.tryEmit(message)
    }

    companion object {
        fun factory(sessionId: Long, path: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as AcpApp).container
                FileViewerViewModel(sessionId, path, container.workspaceRepository)
            }
        }
    }
}
