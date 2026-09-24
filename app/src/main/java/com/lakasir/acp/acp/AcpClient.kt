package com.lakasir.acp.acp

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class AgentInfo(
    val protocolVersion: Int?,
    val name: String?,
    val version: String?,
    val loadSession: Boolean,
)

data class ResourceLink(val uri: String, val name: String) {
    companion object {
        fun forWorkspaceFile(cwd: String, path: String): ResourceLink {
            val absolute = cwd.trimEnd('/') + "/" + path.trimStart('/')
            return ResourceLink(uri = "file://${encodePath(absolute)}", name = path.substringAfterLast('/'))
        }

        private const val UNRESERVED = "-._~/"

        private fun encodePath(path: String): String = buildString {
            path.toByteArray(Charsets.UTF_8).forEach { byte ->
                val c = (byte.toInt() and 0xFF).toChar()
                if (c.isLetterOrDigit() && c.code < 0x80 || c in UNRESERVED) append(c) else append("%%%02X".format(byte.toInt() and 0xFF))
            }
        }
    }
}

class AcpClient(
    private val transport: AcpTransport,
    scope: CoroutineScope,
    private val requestTimeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val clientName: String = "acp-android",
    private val clientVersion: String = "0.1.0",
) {
    val state: StateFlow<TransportState> = transport.state

    private val nextId = AtomicLong(1)
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<JsonElement>>()

    private val _events = MutableSharedFlow<AcpEvent>(extraBufferCapacity = 256)
    val events: SharedFlow<AcpEvent> = _events.asSharedFlow()

    private val _protocolErrors = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val protocolErrors: SharedFlow<String> = _protocolErrors.asSharedFlow()

    @Volatile
    var agentInfo: AgentInfo? = null
        private set

    init {
        scope.launch { transport.incoming.collect(::handleIncoming) }
        scope.launch {
            transport.state.collect { if (it != TransportState.Connected) failPending() }
        }
    }

    fun connect(host: String, port: Int) {
        agentInfo = null
        transport.connect("ws://$host:$port${AcpMethods.ENDPOINT_PATH}")
    }

    fun disconnect() {
        transport.close()
        failPending()
    }

    fun session(sessionId: String): AcpSession = AcpSession(sessionId, this)

    suspend fun initialize(): AgentInfo {
        val params = buildJsonObject {
            put("protocolVersion", AcpMethods.PROTOCOL_VERSION)
            putJsonObject("clientCapabilities") {
                putJsonObject("fs") {
                    put("readTextFile", false)
                    put("writeTextFile", false)
                }
                put("terminal", false)
            }
            putJsonObject("clientInfo") {
                put("name", clientName)
                put("version", clientVersion)
            }
        }
        val result = request(AcpMethods.INITIALIZE, params) as? JsonObject
            ?: throw AcpException.InvalidResponse("initialize result is not an object")
        val capabilities = result["agentCapabilities"] as? JsonObject
        val info = result["agentInfo"] as? JsonObject
        return AgentInfo(
            protocolVersion = (result["protocolVersion"] as? JsonPrimitive)?.intOrNull,
            name = (info?.get("name") as? JsonPrimitive)?.contentOrNull,
            version = (info?.get("version") as? JsonPrimitive)?.contentOrNull,
            loadSession = (capabilities?.get("loadSession") as? JsonPrimitive)?.booleanOrNull ?: false,
        ).also { agentInfo = it }
    }

    suspend fun newSession(cwd: String): String {
        val params = buildJsonObject {
            put("cwd", cwd)
            putJsonArray("mcpServers") {}
        }
        val result = request(AcpMethods.SESSION_NEW, params) as? JsonObject
        return (result?.get("sessionId") as? JsonPrimitive)?.contentOrNull
            ?: throw AcpException.InvalidResponse("session/new returned no sessionId")
    }

    suspend fun loadSession(sessionId: String, cwd: String) {
        val params = buildJsonObject {
            put("sessionId", sessionId)
            put("cwd", cwd)
            putJsonArray("mcpServers") {}
        }
        request(AcpMethods.SESSION_LOAD, params, LOAD_TIMEOUT_MS)
    }

    suspend fun prompt(sessionId: String, text: String, links: List<ResourceLink> = emptyList()): String? {
        val params = buildJsonObject {
            put("sessionId", sessionId)
            put("prompt", buildJsonArray {
                if (text.isNotBlank() || links.isEmpty()) {
                    add(buildJsonObject {
                        put("type", PromptBlockType.TEXT)
                        put("text", text)
                    })
                }
                links.forEach { link ->
                    add(buildJsonObject {
                        put("type", PromptBlockType.RESOURCE_LINK)
                        put("uri", link.uri)
                        put("name", link.name)
                    })
                }
            })
        }
        val result = request(AcpMethods.SESSION_PROMPT, params, timeoutMs = null) as? JsonObject
        return (result?.get("stopReason") as? JsonPrimitive)?.contentOrNull
    }

    fun cancel(sessionId: String): Boolean =
        transport.send(
            AcpMessage.Notification(AcpMethods.SESSION_CANCEL, buildJsonObject { put("sessionId", sessionId) }).encode()
        )

    fun respondPermission(requestId: JsonElement, optionId: String?): Boolean {
        val outcome = buildJsonObject {
            putJsonObject("outcome") {
                if (optionId != null) {
                    put("outcome", "selected")
                    put("optionId", optionId)
                } else {
                    put("outcome", "cancelled")
                }
            }
        }
        return transport.send(AcpMessage.Response(requestId, outcome).encode())
    }

    suspend fun request(method: String, params: JsonElement?, timeoutMs: Long? = requestTimeoutMs): JsonElement {
        val id = nextId.getAndIncrement()
        val deferred = CompletableDeferred<JsonElement>()
        pending[id] = deferred
        try {
            if (!transport.send(AcpMessage.Request(JsonPrimitive(id), method, params).encode())) {
                throw AcpException.NotConnected()
            }
            if (timeoutMs == null) return deferred.await()
            return withTimeoutOrNull(timeoutMs) { deferred.await() } ?: throw AcpException.Timeout(method)
        } finally {
            pending.remove(id)
        }
    }

    private suspend fun handleIncoming(text: String) {
        val message = try {
            AcpMessage.parse(text)
        } catch (e: AcpParseException) {
            _protocolErrors.emit("${e.message}: ${text.take(200)}")
            return
        }
        when (message) {
            is AcpMessage.Response -> pendingFor(message.id)?.complete(message.result)
            is AcpMessage.ErrorResponse -> {
                val target = message.id?.let(::pendingFor)
                if (target != null) {
                    target.completeExceptionally(AcpException.Rpc(message.error))
                } else {
                    _protocolErrors.emit("Agent error: ${message.error.message} (${message.error.code})")
                }
            }
            is AcpMessage.Notification -> handleNotification(message)
            is AcpMessage.Request -> handleAgentRequest(message)
        }
    }

    private suspend fun handleNotification(message: AcpMessage.Notification) {
        if (message.method != AcpMethods.SESSION_UPDATE) return
        try {
            _events.emit(AcpEventParser.parseSessionUpdate(message.params))
        } catch (e: AcpParseException) {
            _protocolErrors.emit(e.message ?: "Invalid session/update")
        }
    }

    private suspend fun handleAgentRequest(message: AcpMessage.Request) {
        if (message.method == AcpMethods.SESSION_REQUEST_PERMISSION) {
            try {
                _events.emit(AcpEventParser.parsePermissionRequest(message.id, message.params))
                return
            } catch (e: AcpParseException) {
                _protocolErrors.emit(e.message ?: "Invalid permission request")
                transport.send(
                    AcpMessage.ErrorResponse(message.id, RpcError(JsonRpcErrorCode.PARSE_ERROR, e.message ?: "Invalid params")).encode()
                )
                return
            }
        }
        transport.send(
            AcpMessage.ErrorResponse(
                message.id,
                RpcError(JsonRpcErrorCode.METHOD_NOT_FOUND, "Method not supported by client: ${message.method}"),
            ).encode()
        )
    }

    private fun pendingFor(id: JsonElement): CompletableDeferred<JsonElement>? {
        val key = (id as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() } ?: return null
        return pending[key]
    }

    private fun failPending() {
        val snapshot = pending.values.toList()
        pending.clear()
        snapshot.forEach { it.completeExceptionally(AcpException.Disconnected()) }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 30_000L
        const val LOAD_TIMEOUT_MS = 120_000L
    }
}
