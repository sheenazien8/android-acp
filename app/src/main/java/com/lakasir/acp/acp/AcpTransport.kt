package com.lakasir.acp.acp

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

sealed interface TransportState {
    data object Disconnected : TransportState
    data object Connecting : TransportState
    data object Connected : TransportState
    data class Failed(val reason: String) : TransportState
}

interface AcpTransport {
    val incoming: Flow<String>
    val state: StateFlow<TransportState>
    fun connect(url: String)
    fun send(text: String): Boolean
    fun close()
}

class OkHttpAcpTransport(
    private val client: OkHttpClient = defaultClient(),
) : AcpTransport {

    private val incomingChannel = Channel<String>(Channel.UNLIMITED)
    override val incoming: Flow<String> = incomingChannel.receiveAsFlow()

    private val _state = MutableStateFlow<TransportState>(TransportState.Disconnected)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    @Volatile
    private var socket: WebSocket? = null

    override fun connect(url: String) {
        socket?.cancel()
        _state.value = TransportState.Connecting
        socket = client.newWebSocket(Request.Builder().url(url).build(), Listener())
    }

    override fun send(text: String): Boolean {
        val current = socket ?: return false
        if (_state.value != TransportState.Connected) return false
        return current.send(text)
    }

    override fun close() {
        val current = socket
        socket = null
        current?.close(1000, null)
        _state.value = TransportState.Disconnected
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (webSocket === socket) _state.value = TransportState.Connected
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (webSocket === socket) incomingChannel.trySend(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (webSocket === socket) {
                socket = null
                _state.value = TransportState.Disconnected
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (webSocket === socket) {
                socket = null
                _state.value = TransportState.Failed(t.message ?: t.javaClass.simpleName)
            }
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }
}
