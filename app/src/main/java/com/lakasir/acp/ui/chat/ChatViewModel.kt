package com.lakasir.acp.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lakasir.acp.AcpApp
import com.lakasir.acp.data.local.SessionEntity
import com.lakasir.acp.data.repository.AcpRepository
import com.lakasir.acp.data.repository.ConnectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface InputAvailability {
    data object Ready : InputAvailability
    data object Offline : InputAvailability
    data object Resuming : InputAvailability
    data object NotResumable : InputAvailability
}

class ChatViewModel(
    private val sessionId: Long,
    private val repository: AcpRepository,
) : ViewModel() {

    private val mapper = ChatItemMapper()
    private val resuming = MutableStateFlow(false)

    val session: StateFlow<SessionEntity?> = repository.observeSession(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val items: StateFlow<List<ChatItem>?> = repository.observeMessages(sessionId)
        .map(mapper::map)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val connectionState: StateFlow<ConnectionState> = repository.connectionStates
        .map { states ->
            val profileId = session.value?.connectionProfileId
            if (profileId != null) states[profileId] ?: ConnectionState.Disconnected else ConnectionState.Disconnected
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectionState.Disconnected)

    val isBusy: StateFlow<Boolean> = repository.busySessions
        .map { sessionId in it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val availability: StateFlow<InputAvailability> = combine(
        repository.connectionStates,
        repository.attachedSessions,
        session.filterNotNull(),
        resuming,
    ) { states, attached, session, isResuming ->
        val connection = states[session.connectionProfileId]
        when {
            connection !is ConnectionState.Connected || connection.profileId != session.connectionProfileId -> InputAvailability.Offline
            sessionId in attached -> InputAvailability.Ready
            isResuming -> InputAvailability.Resuming
            else -> InputAvailability.NotResumable
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InputAvailability.Offline)

    init {
        viewModelScope.launch {
            combine(repository.connectionStates, session.filterNotNull()) { states, session ->
                states[session.connectionProfileId] is ConnectionState.Connected
            }.collect { connected ->
                if (connected) resume()
            }
        }
    }

    private suspend fun resume() {
        resuming.value = true
        try {
            repository.openSession(sessionId)
        } finally {
            resuming.value = false
        }
    }

    fun send(text: String) {
        if (text.isBlank()) return
        repository.sendPrompt(sessionId, text.trim())
    }

    fun cancel() {
        viewModelScope.launch { repository.cancel(sessionId) }
    }

    fun rename(title: String) {
        viewModelScope.launch { repository.renameSession(sessionId, title) }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            onDeleted()
        }
    }

    companion object {
        fun factory(sessionId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as AcpApp).container
                ChatViewModel(sessionId, container.repository)
            }
        }
    }
}
