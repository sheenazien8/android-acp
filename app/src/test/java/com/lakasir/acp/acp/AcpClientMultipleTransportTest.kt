package com.lakasir.acp.acp

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AcpClientMultipleTransportTest {

    @Test
    fun `two clients can connect and initialize independently`() = runTest(UnconfinedTestDispatcher()) {
        val transportA = FakeTransport()
        val clientA = AcpClient(transportA, backgroundScope)
        val transportB = FakeTransport()
        val clientB = AcpClient(transportB, backgroundScope)

        clientA.connect("host-a", 1)
        clientB.connect("host-b", 2)

        val initA = async { clientA.initialize() }
        val initB = async { clientB.initialize() }

        launch {
            transportA.sentFlow.first { it.isNotEmpty() }
            transportA.receive(initializeResponse())
        }
        launch {
            transportB.sentFlow.first { it.isNotEmpty() }
            transportB.receive(initializeResponse(agentName = "agent-b"))
        }

        assertEquals("agent-a", initA.await().name)
        assertEquals("agent-b", initB.await().name)
    }

    @Test
    fun `events from one client are not received by the other`() = runTest(UnconfinedTestDispatcher()) {
        val transportA = FakeTransport()
        val clientA = AcpClient(transportA, backgroundScope)
        val transportB = FakeTransport()
        val clientB = AcpClient(transportB, backgroundScope)

        val eventsA = mutableListOf<AcpEvent>()
        val eventsB = mutableListOf<AcpEvent>()
        backgroundScope.launch { clientA.events.collect(eventsA::add) }
        backgroundScope.launch { clientB.events.collect(eventsB::add) }

        transportA.receive(sessionUpdate("session-a"))
        transportB.receive(sessionUpdate("session-b"))

        assertTrue(eventsA.any { it.sessionId == "session-a" })
        assertTrue(eventsB.any { it.sessionId == "session-b" })
        assertTrue(eventsA.none { it.sessionId == "session-b" })
        assertTrue(eventsB.none { it.sessionId == "session-a" })
    }

    private fun initializeResponse(agentName: String = "agent-a"): String {
        return buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 1)
            putJsonObject("result") {
                put("protocolVersion", 1)
                putJsonObject("agentCapabilities") {
                    put("loadSession", false)
                }
                putJsonObject("agentInfo") {
                    put("name", agentName)
                    put("version", "1.0")
                }
            }
        }.toString()
    }

    private fun sessionUpdate(sessionId: String): String {
        return buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", "session/update")
            putJsonObject("params") {
                put("sessionId", sessionId)
                putJsonObject("update") {
                    put("sessionUpdate", "agent_message_chunk")
                    putJsonObject("content") {
                        put("type", "text")
                        put("text", "hello")
                    }
                }
            }
        }.toString()
    }
}
