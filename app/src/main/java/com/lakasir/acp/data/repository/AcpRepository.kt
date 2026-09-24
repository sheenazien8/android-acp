package com.lakasir.acp.data.repository

import android.util.Log
import com.lakasir.acp.acp.AcpClient
import com.lakasir.acp.acp.AcpClientFactory
import com.lakasir.acp.acp.AcpEndpoint
import com.lakasir.acp.acp.AcpEvent
import com.lakasir.acp.acp.AcpException
import com.lakasir.acp.acp.BridgeFeatures
import com.lakasir.acp.acp.PermissionOption
import com.lakasir.acp.acp.PermissionOptionKind
import com.lakasir.acp.acp.ResourceLink
import com.lakasir.acp.acp.ToolCallStatus
import com.lakasir.acp.acp.TransportState
import com.lakasir.acp.acp.WorkspaceApi
import com.lakasir.acp.data.local.AppDatabase
import com.lakasir.acp.data.local.ConnectionProfileEntity
import com.lakasir.acp.data.local.MessageEntity
import com.lakasir.acp.data.local.MessageRole
import com.lakasir.acp.data.local.MessageType
import com.lakasir.acp.data.local.SessionEntity
import com.lakasir.acp.data.local.SessionSummary
import com.lakasir.acp.data.model.PermissionRecord
import com.lakasir.acp.data.model.PlanCodec
import com.lakasir.acp.data.model.ToolCallState
import com.lakasir.acp.data.model.UserPromptPayload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext

class AcpRepository(
    database: AppDatabase,
    private val clientFactory: AcpClientFactory,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val profileDao = database.connectionProfileDao()
    private val sessionDao = database.sessionDao()
    private val messageDao = database.messageDao()

    private val _connectionStates = MutableStateFlow<Map<Long, ConnectionState>>(emptyMap())
    val connectionStates: StateFlow<Map<Long, ConnectionState>> = _connectionStates.asStateFlow()

    private val _busySessions = MutableStateFlow<Set<Long>>(emptySet())
    val busySessions: StateFlow<Set<Long>> = _busySessions.asStateFlow()

    private val _attachedSessions = MutableStateFlow<Set<Long>>(emptySet())
    val attachedSessions: StateFlow<Set<Long>> = _attachedSessions.asStateFlow()

    private val _pendingPermissions = MutableStateFlow<List<PendingPermission>>(emptyList())
    val pendingPermissions: StateFlow<List<PendingPermission>> = _pendingPermissions.asStateFlow()

    private val _activeProfileIds = MutableStateFlow<Set<Long>>(emptySet())
    val activeProfileIds: StateFlow<Set<Long>> = _activeProfileIds.asStateFlow()

    private val _completedTurns = MutableSharedFlow<CompletedTurn>(extraBufferCapacity = 16)
    val completedTurns: SharedFlow<CompletedTurn> = _completedTurns.asSharedFlow()

    private val _bridgeFeatures = MutableStateFlow<Map<Long, BridgeFeatures?>>(emptyMap())
    val bridgeFeatures: StateFlow<Map<Long, BridgeFeatures?>> = _bridgeFeatures.asStateFlow()

    private val _workspaceChanges = MutableSharedFlow<Long>(extraBufferCapacity = 16)
    val workspaceChanges: SharedFlow<Long> = _workspaceChanges.asSharedFlow()

    private val retrySignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val clients = mutableMapOf<Long, AcpClient>()
    private val connectJobs = mutableMapOf<Long, Job>()
    private val loadingRemoteIds = mutableMapOf<Long, MutableSet<String>>()
    private val writeMutex = Mutex()

    fun observeProfiles(): Flow<List<ConnectionProfileEntity>> = profileDao.observeAll()
    fun observeProfile(id: Long): Flow<ConnectionProfileEntity?> = profileDao.observe(id)
    fun observeSessions(profileId: Long): Flow<List<SessionSummary>> = sessionDao.observeSummaries(profileId)
    fun observeSession(id: Long): Flow<SessionEntity?> = sessionDao.observe(id)
    fun observeMessages(sessionId: Long): Flow<List<MessageEntity>> = messageDao.observeBySession(sessionId)

    suspend fun saveProfile(profile: ConnectionProfileEntity): Long =
        if (profile.id == 0L) {
            profileDao.insert(profile.copy(createdAt = clock()))
        } else {
            profileDao.update(profile)
            profile.id
        }

    suspend fun deleteProfile(profile: ConnectionProfileEntity) {
        disconnect(profile.id)
        profileDao.delete(profile)
    }

    suspend fun renameSession(id: Long, title: String) {
        val normalized = normalizeTitle(title) ?: return
        sessionDao.updateTitle(id, normalized)
    }

    suspend fun setAutoApprove(id: Long, enabled: Boolean) {
        sessionDao.updateAutoApprove(id, enabled)
        if (!enabled) return
        _pendingPermissions.value.filter { it.localSessionId == id }.forEach { pending ->
            autoApproveOption(pending.request.options)?.let { answerPermission(pending, it.optionId, auto = true) }
        }
    }

    suspend fun deleteSession(id: Long) {
        if (id in _busySessions.value) {
            cancel(id)
        } else {
            _pendingPermissions.value.filter { it.localSessionId == id }.forEach { answerPermission(it, null) }
        }
        _busySessions.update { it - id }
        _attachedSessions.update { it - id }
        sessionDao.delete(id)
    }

    fun connect(profile: ConnectionProfileEntity) {
        disconnect(profile.id)
        val client = clientFactory.create(scope, profile.allowInsecureTls)
        clients[profile.id] = client
        loadingRemoteIds[profile.id] = mutableSetOf()
        scope.launch { client.events.collect { event -> handleEvent(profile.id, event) } }
        scope.launch { client.protocolErrors.collect { Log.w(TAG, it) } }
        connectJobs[profile.id] = scope.launch { runConnection(profile, client) }
        _activeProfileIds.update { it + profile.id }
    }

    fun disconnect(profileId: Long) {
        connectJobs.remove(profileId)?.cancel()
        clients.remove(profileId)?.disconnect()
        loadingRemoteIds.remove(profileId)
        _activeProfileIds.update { it - profileId }
        scope.launch { clearProfileConnectionState(profileId) }
    }

    fun disconnect() {
        val ids = clients.keys.toList()
        ids.forEach { disconnect(it) }
    }

    fun retryNow() {
        retrySignal.tryEmit(Unit)
    }

    suspend fun createSession(profileId: Long): Long {
        val profile = requireConnectedProfile(profileId)
        val client = clients[profileId] ?: throw AcpException.NotConnected()
        val remoteId = client.newSession(profile.cwd)
        val now = clock()
        val id = sessionDao.insert(
            SessionEntity(
                remoteSessionId = remoteId,
                connectionProfileId = profileId,
                title = DEFAULT_TITLE,
                createdAt = now,
                updatedAt = now,
            )
        )
        _attachedSessions.update { it + id }
        return id
    }

    suspend fun openSession(localId: Long) {
        if (localId in _attachedSessions.value) return
        val session = sessionDao.get(localId) ?: return
        val profile = connectedProfileOrNull(session.connectionProfileId) ?: return
        val client = clients[session.connectionProfileId] ?: return
        if (client.agentInfo?.loadSession != true) return

        loadingRemoteIds.getOrPut(session.connectionProfileId) { mutableSetOf() } += session.remoteSessionId
        try {
            client.loadSession(session.remoteSessionId, profile.cwd)
            _attachedSessions.update { it + localId }
        } catch (e: AcpException) {
            insertMessage(localId, MessageRole.SYSTEM, MessageType.ERROR, "Could not resume session: ${e.message}")
        } finally {
            loadingRemoteIds[session.connectionProfileId]?.remove(session.remoteSessionId)
        }
    }

    suspend fun workspace(localSessionId: Long): WorkspaceApi? {
        val session = sessionDao.get(localSessionId) ?: return null
        val profile = connectedProfileOrNull(session.connectionProfileId) ?: return null
        val client = clients[profile.id] ?: return null
        return WorkspaceApi(client, profile.cwd)
    }

    fun sendPrompt(localId: Long, text: String, attachments: List<String> = emptyList()) {
        scope.launch {
            val session = sessionDao.get(localId) ?: return@launch
            val client = clients[session.connectionProfileId] ?: return@launch
            val cwd = profileDao.get(session.connectionProfileId)?.cwd ?: return@launch
            val links = attachments.map { ResourceLink.forWorkspaceFile(cwd, it) }
            val payload = UserPromptPayload(attachments).takeIf { attachments.isNotEmpty() }?.encode()
            insertMessage(localId, MessageRole.USER, MessageType.TEXT, text, rawJson = payload)
            if (session.title == DEFAULT_TITLE) {
                normalizeTitle(text.ifBlank { links.firstOrNull()?.name.orEmpty() })?.let { sessionDao.updateTitle(localId, it) }
            }
            _busySessions.update { it + localId }
            try {
                val stopReason = client.prompt(session.remoteSessionId, text, links)
                val title = sessionDao.get(localId)?.title ?: return@launch
                if (stopReason != null && stopReason != STOP_END_TURN) {
                    insertMessage(localId, MessageRole.SYSTEM, MessageType.TEXT, "Stopped: $stopReason")
                }
                _completedTurns.tryEmit(CompletedTurn(localId, title, error = null))
            } catch (e: AcpException) {
                val title = sessionDao.get(localId)?.title ?: return@launch
                val error = e.message ?: "Prompt failed"
                insertMessage(localId, MessageRole.SYSTEM, MessageType.ERROR, error)
                _completedTurns.tryEmit(CompletedTurn(localId, title, error))
            } finally {
                _busySessions.update { it - localId }
                sessionDao.touch(localId, clock())
            }
        }
    }

    suspend fun cancel(localId: Long) {
        val session = sessionDao.get(localId) ?: return
        val client = clients[session.connectionProfileId] ?: return
        _pendingPermissions.value.filter { it.localSessionId == localId }.forEach { answerPermission(it, null) }
        client.cancel(session.remoteSessionId)
    }

    suspend fun answerPermission(pending: PendingPermission, optionId: String?, auto: Boolean = false) {
        _pendingPermissions.update { list -> list.filterNot { it.request.requestId == pending.request.requestId } }
        val profileId = sessionDao.get(pending.localSessionId)?.connectionProfileId ?: return
        val client = clients[profileId] ?: return
        client.respondPermission(pending.request.requestId, optionId)
        val choice = pending.request.options.firstOrNull { it.optionId == optionId }?.name ?: "Cancelled"
        writeMutex.withLock {
            val message = messageDao.get(pending.messageId) ?: return@withLock
            val record = PermissionRecord.decode(message.content) ?: return@withLock
            messageDao.update(message.copy(content = record.copy(choice = choice, auto = auto).encode()))
        }
    }

    private suspend fun runConnection(profile: ConnectionProfileEntity, client: AcpClient) {
        var attempt = 0
        while (coroutineContext.isActive) {
            updateConnectionState(profile.id, ConnectionState.Connecting(profile.id, attempt))
            val endpoint = runCatching { AcpEndpoint.from(profileDao.get(profile.id) ?: profile) }.getOrNull()
            var reason = "Connection closed"
            val opened = if (endpoint == null) {
                TransportState.Failed(INVALID_ADDRESS)
            } else {
                client.connect(endpoint)
                client.state.first { it != TransportState.Connecting }
            }
            if (opened == TransportState.Connected) {
                try {
                    val agent = client.initialize()
                    profileDao.touchLastConnected(profile.id, clock())
                    attempt = 0
                    updateConnectionState(profile.id, ConnectionState.Connected(profile.id, agent))
                    scope.launch {
                        val features = WorkspaceApi.hello(client)
                        if (clients[profile.id] === client) _bridgeFeatures.update { it + (profile.id to features) }
                    }
                    client.state.first { it != TransportState.Connected }
                } catch (e: AcpException) {
                    reason = "Initialize failed: ${e.message}"
                }
            } else if (opened is TransportState.Failed) {
                reason = opened.reason
            }
            clearProfileConnectionState(profile.id)
            client.disconnect()
            attempt++
            val backoff = backoffMs(attempt)
            updateConnectionState(profile.id, ConnectionState.Error(profile.id, reason, backoff))
            withTimeoutOrNull(backoff) { retrySignal.first() }
        }
    }

    private suspend fun clearProfileConnectionState(profileId: Long) {
        val affected = sessionIdsForProfile(profileId)
        _busySessions.update { it - affected }
        _attachedSessions.update { it - affected }
        _pendingPermissions.update { list -> list.filterNot { it.localSessionId in affected } }
        loadingRemoteIds[profileId]?.clear()
        _bridgeFeatures.update { it - profileId }
        updateConnectionState(profileId, ConnectionState.Disconnected)
    }

    private suspend fun sessionIdsForProfile(profileId: Long): Set<Long> =
        sessionDao.observeSummaries(profileId).first().map { it.session.id }.toSet()

    private fun updateConnectionState(profileId: Long, state: ConnectionState) {
        _connectionStates.update { it + (profileId to state) }
    }

    private suspend fun handleEvent(profileId: Long, event: AcpEvent) {
        try {
            if (event !is AcpEvent.PermissionRequest && event.sessionId in (loadingRemoteIds[profileId] ?: emptySet())) return
            val localId = localSessionId(profileId, event.sessionId) ?: return
            writeMutex.withLock { persist(localId, event) }
            sessionDao.touch(localId, clock())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist event", e)
        }
    }

    private suspend fun persist(localId: Long, event: AcpEvent) {
        when (event) {
            is AcpEvent.MessageChunk -> appendText(localId, MessageType.TEXT, event.text, event.raw.toString())
            is AcpEvent.ThoughtChunk -> appendText(localId, MessageType.THOUGHT, event.text, event.raw.toString())
            is AcpEvent.UserMessageChunk -> Unit
            is AcpEvent.ToolCallStarted -> upsertToolCall(localId, event.toolCall.toolCallId, event.raw.toString()) {
                it?.merge(event.toolCall) ?: ToolCallState.from(event.toolCall)
            }.also { if (changesWorkspace(it)) _workspaceChanges.tryEmit(localId) }
            is AcpEvent.ToolCallUpdated -> upsertToolCall(localId, event.toolCall.toolCallId, event.raw.toString()) {
                it?.merge(event.toolCall) ?: ToolCallState.from(event.toolCall)
            }.also { if (changesWorkspace(it)) _workspaceChanges.tryEmit(localId) }
            is AcpEvent.Plan -> {
                val content = PlanCodec.encode(event.entries)
                val last = messageDao.last(localId)
                if (last?.type == MessageType.PLAN) {
                    messageDao.update(last.copy(content = content, rawJson = event.raw.toString()))
                } else {
                    insertMessage(localId, MessageRole.AGENT, MessageType.PLAN, content, rawJson = event.raw.toString())
                }
            }
            is AcpEvent.Unknown -> insertMessage(localId, MessageRole.SYSTEM, MessageType.TEXT, event.kind, rawJson = event.raw.toString())
            is AcpEvent.PermissionRequest -> {
                val session = sessionDao.get(localId)
                val autoOption = autoApproveOption(event.options).takeIf { session?.autoApprove == true }
                val autoAnswered = autoOption != null &&
                    clients[session!!.connectionProfileId]?.respondPermission(event.requestId, autoOption.optionId) == true
                val record = PermissionRecord(
                    title = event.toolCall.title ?: event.toolCall.kind ?: "Permission requested",
                    kind = event.toolCall.kind,
                    input = event.toolCall.rawInput?.toString(),
                    options = event.options.map { it.name },
                    choice = autoOption?.name.takeIf { autoAnswered },
                    auto = autoAnswered,
                )
                val messageId = insertMessage(
                    localId, MessageRole.TOOL, MessageType.PERMISSION_REQUEST, record.encode(),
                    toolCallId = event.toolCall.toolCallId, rawJson = event.raw.toString(),
                )
                if (autoOption == null) {
                    val title = session?.title ?: DEFAULT_TITLE
                    _pendingPermissions.update { it + PendingPermission(localId, title, messageId, event) }
                }
            }
        }
    }

    private suspend fun appendText(localId: Long, type: MessageType, text: String, rawJson: String) {
        if (text.isEmpty()) return
        val last = messageDao.last(localId)
        if (canAppend(last, MessageRole.AGENT, type)) {
            messageDao.appendContent(last!!.id, text)
        } else {
            insertMessage(localId, MessageRole.AGENT, type, text, rawJson = rawJson)
        }
    }

    private suspend fun upsertToolCall(
        localId: Long,
        toolCallId: String,
        rawJson: String,
        transform: (ToolCallState?) -> ToolCallState,
    ): ToolCallState {
        val existing = messageDao.findToolCall(localId, toolCallId)
        return if (existing == null) {
            transform(null).also { insertMessage(localId, MessageRole.TOOL, MessageType.TOOL_CALL, it.encode(), toolCallId, rawJson) }
        } else {
            transform(ToolCallState.decode(existing.content)).also {
                messageDao.update(existing.copy(content = it.encode(), rawJson = rawJson))
            }
        }
    }

    private suspend fun insertMessage(
        sessionId: Long,
        role: MessageRole,
        type: MessageType,
        content: String,
        toolCallId: String? = null,
        rawJson: String? = null,
    ): Long = messageDao.insert(
        MessageEntity(
            sessionId = sessionId,
            role = role,
            type = type,
            content = content,
            toolCallId = toolCallId,
            timestamp = clock(),
            rawJson = rawJson,
        )
    )

    private suspend fun localSessionId(profileId: Long, remoteId: String): Long? {
        return sessionDao.findByRemoteId(profileId, remoteId)?.id
    }

    private suspend fun requireConnectedProfile(profileId: Long): ConnectionProfileEntity =
        connectedProfileOrNull(profileId) ?: throw AcpException.NotConnected()

    private suspend fun connectedProfileOrNull(profileId: Long): ConnectionProfileEntity? {
        val state = _connectionStates.value[profileId]
        if (state !is ConnectionState.Connected || state.profileId != profileId) return null
        return profileDao.get(profileId)
    }

    companion object {
        private const val TAG = "AcpRepository"
        const val DEFAULT_TITLE = "New session"
        const val INVALID_ADDRESS = "Invalid bridge address"
        private const val STOP_END_TURN = "end_turn"
        private const val MAX_BACKOFF_MS = 30_000L
        const val MAX_TITLE_LENGTH = 60

        fun normalizeTitle(raw: String): String? =
            raw.trim().lineSequence().first().trim().take(MAX_TITLE_LENGTH).takeIf { it.isNotEmpty() }

        fun backoffMs(attempt: Int): Long = minOf(1_000L shl (attempt - 1).coerceIn(0, 5), MAX_BACKOFF_MS)

        fun autoApproveOption(options: List<PermissionOption>): PermissionOption? =
            options.firstOrNull { it.kind == PermissionOptionKind.ALLOW_ONCE }
                ?: options.firstOrNull { it.kind == PermissionOptionKind.ALLOW_ALWAYS }

        private val WORKSPACE_TOOL_KINDS = setOf("edit", "delete", "move", "execute")

        fun changesWorkspace(state: ToolCallState): Boolean =
            state.status == ToolCallStatus.COMPLETED && (state.kind in WORKSPACE_TOOL_KINDS || state.diffs.isNotEmpty())

        fun canAppend(last: MessageEntity?, role: MessageRole, type: MessageType): Boolean =
            last != null && last.role == role && last.type == type
    }
}
