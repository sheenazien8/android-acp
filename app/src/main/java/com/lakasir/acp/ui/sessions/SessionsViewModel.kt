package com.lakasir.acp.ui.sessions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.lakasir.acp.data.local.ConnectionProfileEntity
import com.lakasir.acp.data.local.SessionSummary
import com.lakasir.acp.data.repository.AcpRepository
import com.lakasir.acp.data.repository.ConnectionState
import com.lakasir.acp.ui.navigation.Sessions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SessionsViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: AcpRepository,
) : ViewModel() {

    val profileId: Long = savedStateHandle.toRoute<Sessions>().profileId

    val profile: StateFlow<ConnectionProfileEntity?> = repository.observeProfile(profileId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val sessions: StateFlow<List<SessionSummary>?> = repository.observeSessions(profileId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val connectionState: StateFlow<ConnectionState> = repository.connectionStates
        .map { it[profileId] ?: ConnectionState.Disconnected }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectionState.Disconnected)

    val busySessions: StateFlow<Set<Long>> = repository.busySessions

    private val _creating = MutableStateFlow(false)
    val creating: StateFlow<Boolean> = _creating.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _createdSession = MutableStateFlow<Long?>(null)
    val createdSession: StateFlow<Long?> = _createdSession.asStateFlow()

    fun createSession() {
        if (_creating.value) return
        viewModelScope.launch {
            _creating.value = true
            try {
                _createdSession.value = repository.createSession(profileId)
            } catch (e: Exception) {
                _error.value = "Could not create session: ${e.message}"
            } finally {
                _creating.value = false
            }
        }
    }

    fun connect() {
        profile.value?.let(repository::connect)
    }

    fun disconnect() {
        repository.disconnect(profileId)
    }

    fun renameSession(id: Long, title: String) {
        viewModelScope.launch { repository.renameSession(id, title) }
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch { repository.deleteSession(id) }
    }

    fun onCreatedSessionOpened() {
        _createdSession.value = null
    }

    fun onErrorShown() {
        _error.value = null
    }
}
