package com.lakasir.acp.acp

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class FakeTransport : AcpTransport {
    private val channel = Channel<String>(Channel.UNLIMITED)
    override val incoming: Flow<String> = channel.receiveAsFlow()
    override val state = MutableStateFlow<TransportState>(TransportState.Disconnected)
    val sent = mutableListOf<String>()

    override fun connect(url: String) {
        state.value = TransportState.Connected
    }

    override fun send(text: String): Boolean {
        if (state.value != TransportState.Connected) return false
        sent += text
        return true
    }

    override fun close() {
        state.value = TransportState.Disconnected
    }

    fun receive(text: String) {
        channel.trySend(text)
    }

    fun lastSent(): JsonObject = AcpJson.parseToJsonElement(sent.last()).jsonObject
}
