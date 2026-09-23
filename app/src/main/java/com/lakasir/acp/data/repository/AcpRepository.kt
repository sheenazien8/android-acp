package com.lakasir.acp.data.repository

import android.util.Log
import com.lakasir.acp.acp.AcpClient
import com.lakasir.acp.acp.AcpClientFactory
import com.lakasir.acp.acp.AcpEvent
import com.lakasir.acp.acp.AcpException
import com.lakasir.acp.acp.TransportState
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    suspend fun deleteSession(id: Long) {
        sessionDao.delete(id)
    }

    fun connect(profile: ConnectionProfileEntity) {
        disconnect(profile.id)
        val client = clientFactory.create(scope)
        clients[profile.id] = client
        loadingRemoteIds[profile.id] = mutableSetOf()
        scope.launch { client.events.collect { event -> handleEvent(profile.id, event) } }
        scope.launch { client.protocolErrors.collect { Log.w(TAG, it) } }
        connectJobs[profile.id] = scope.launch { runConnection(profile, client) }
    }

    fun disconnect(profileId: Long) {
        connectJobs.remove(profileId)?.cancel()
        clients.remove(profileId)?.disconnect()
        loadingRemoteIds.remove(profileId)
        scope.launch { clearProfileConnectionState(profileId) }
    }

    fun disconnect() {
        val ids = clients.keys.toList()
        ids.forEach { disconnect(it) }
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

    fun sendPrompt(localId: Long, text: String) {
        scope.launch {
            val session = sessionDao.get(localId) ?: return@launch
            val client = clients[session.connectionProfileId] ?: return@launch
            insertMessage(localId, MessageRole.USER, MessageType.TEXT, text)
            if (session.title == DEFAULT_TITLE) sessionDao.updateTitle(localId, text.lineSequence().first().take(60))
            _busySessions.update { it + localId }
            try {
                val stopReason = client.prompt(session.remoteSessionId, text)
                if (stopReason != null && stopReason != STOP_END_TURN) {
                    insertMessage(localId, MessageRole.SYSTEM, MessageType.TEXT, "Stopped: $stopReason")
                }
            } catch (e: AcpException) {
                insertMessage(localId, MessageRole.SYSTEM, MessageType.ERROR, e.message ?: "Prompt failed")
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

    suspend fun answerPermission(pending: PendingPermission, optionId: String?) {
        _pendingPermissions.update { list -> list.filterNot { it.request.requestId == pending.request.requestId } }
        val profileId = sessionDao.get(pending.localSessionId)?.connectionProfileId ?: return
        val client = clients[profileId] ?: return
        client.respondPermission(pending.request.requestId, optionId)
        val choice = pending.request.options.firstOrNull { it.optionId == optionId }?.name ?: "Cancelled"
        writeMutex.withLock {
            val message = messageDao.get(pending.messageId) ?: return@withLock
            val record = PermissionRecord.decode(message.content) ?: return@withLock
            messageDao.update(message.copy(content = record.copy(choice = choice).encode()))
        }
    }

    private suspend fun runConnection(profile: ConnectionProfileEntity, client: AcpClient) {
        var attempt = 0
        while (coroutineContext.isActive) {
            updateConnectionState(profile.id, ConnectionState.Connecting(profile.id, attempt))
            client.connect(profile.host, profile.port)
            var reason = "Connection closed"
            val opened = client.state.first { it != TransportState.Connecting }
            if (opened == TransportState.Connected) {
                try {
                    val agent = client.initialize()
                    profileDao.touchLastConnected(profile.id, clock())
                    attempt = 0
                    updateConnectionState(profile.id, ConnectionState.Connected(profile.id, agent))
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
            delay(backoff)
        }
    }

    private suspend fun clearProfileConnectionState(profileId: Long) {
        val affected = sessionIdsForProfile(profileId)
        _busySessions.update { it - affected }
        _attachedSessions.update { it - affected }
        _pendingPermissions.update { list -> list.filterNot { it.localSessionId in affected } }
        loadingRemoteIds[profileId]?.clear()
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
            }
            is AcpEvent.ToolCallUpdated -> upsertToolCall(localId, event.toolCall.toolCallId, event.raw.toString()) {
                it?.merge(event.toolCall) ?: ToolCallState.from(event.toolCall)
            }
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
                val record = PermissionRecord(
                    title = event.toolCall.title ?: event.toolCall.kind ?: "Permission requested",
                    kind = event.toolCall.kind,
                    input = event.toolCall.rawInput?.toString(),
                    options = event.options.map { it.name },
                )
                val messageId = insertMessage(
                    localId, MessageRole.TOOL, MessageType.PERMISSION_REQUEST, record.encode(),
                    toolCallId = event.toolCall.toolCallId, rawJson = event.raw.toString(),
                )
                val title = sessionDao.get(localId)?.title ?: DEFAULT_TITLE
                _pendingPermissions.update { it + PendingPermission(localId, title, messageId, event) }
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
    ) {
        val existing = messageDao.findToolCall(localId, toolCallId)
        if (existing == null) {
            insertMessage(localId, MessageRole.TOOL, MessageType.TOOL_CALL, transform(null).encode(), toolCallId, rawJson)
        } else {
            val next = transform(ToolCallState.decode(existing.content))
            messageDao.update(existing.copy(content = next.encode(), rawJson = rawJson))
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
        private const val STOP_END_TURN = "end_turn"
        private const val MAX_BACKOFF_MS = 30_000L

        fun backoffMs(attempt: Int): Long = minOf(1_000L shl (attempt - 1).coerceIn(0, 5), MAX_BACKOFF_MS)

        fun canAppend(last: MessageEntity?, role: MessageRole, type: MessageType): Boolean =
            last != null && last.role == role && last.type == type
    }
}
