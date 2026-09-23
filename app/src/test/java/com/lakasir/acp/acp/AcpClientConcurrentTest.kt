package com.lakasir.acp.acp

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AcpClientConcurrentTest {

    @Test
    fun `multiple clients share a scope without request id collisions`() = runTest(UnconfinedTestDispatcher()) {
        val transportA = FakeTransport()
        val transportB = FakeTransport()
        val clientA = AcpClient(transportA, backgroundScope, requestTimeoutMs = 1_000)
        val clientB = AcpClient(transportB, backgroundScope, requestTimeoutMs = 1_000)

        clientA.connect("h", 1)
        clientB.connect("h", 1)

        val resultA = async { clientA.initialize() }
        val resultB = async { clientB.initialize() }

        val requestA = transportA.lastSent()
        val requestB = transportB.lastSent()

        assertEquals(JsonPrimitive(1), requestA["id"])
        assertEquals(JsonPrimitive(1), requestB["id"])

        transportA.receive(
            AcpMessage.Response(
                JsonPrimitive(1),
                buildJsonObject {
                    put("protocolVersion", 1)
                    putJsonObject("agentCapabilities") { put("loadSession", false) }
                    putJsonObject("agentInfo") { put("name", "agent-a") }
                },
            ).encode()
        )
        transportB.receive(
            AcpMessage.Response(
                JsonPrimitive(1),
                buildJsonObject {
                    put("protocolVersion", 1)
                    putJsonObject("agentCapabilities") { put("loadSession", false) }
                    putJsonObject("agentInfo") { put("name", "agent-b") }
                },
            ).encode()
        )

        assertEquals("agent-a", resultA.await().name)
        assertEquals("agent-b", resultB.await().name)
    }
}
