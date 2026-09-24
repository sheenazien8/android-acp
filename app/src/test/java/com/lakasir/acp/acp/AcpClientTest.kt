package com.lakasir.acp.acp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AcpClientTest {

    private val transport = FakeTransport()

    private fun TestScope.client(scope: CoroutineScope = backgroundScope): AcpClient =
        AcpClient(transport, scope, requestTimeoutMs = 1_000).also { it.connect("h", 1) }

    private fun lastId(): Long = transport.lastSent()["id"]!!.jsonPrimitive.content.toLong()

    @Test
    fun `initialize sends capabilities and parses agent info`() = runTest(UnconfinedTestDispatcher()) {
        val client = client()
        val result = async { client.initialize() }
        val sent = transport.lastSent()
        assertEquals("initialize", sent["method"]!!.jsonPrimitive.content)
        val fs = sent["params"]!!.jsonObject["clientCapabilities"]!!.jsonObject["fs"]!!.jsonObject
        assertEquals("false", fs["readTextFile"]!!.jsonPrimitive.content)
        val configOptions = sent["params"]!!.jsonObject["clientCapabilities"]!!.jsonObject["session"]!!.jsonObject["configOptions"]!!.jsonObject
        assertTrue(configOptions.isEmpty())

        transport.receive(
            """{"jsonrpc":"2.0","id":${lastId()},"result":{"protocolVersion":1,"agentCapabilities":{"loadSession":true},"agentInfo":{"name":"agent","version":"1.0"}}}"""
        )
        val info = result.await()
        assertEquals("agent", info.name)
        assertTrue(info.loadSession)
        assertEquals(info, client.agentInfo)
    }

    @Test
    fun `responses are correlated by id`() = runTest(UnconfinedTestDispatcher()) {
        val client = client()
        val first = async { client.newSession("/a") }
        val firstId = lastId()
        val second = async { client.newSession("/b") }
        val secondId = lastId()

        transport.receive("""{"jsonrpc":"2.0","id":$secondId,"result":{"sessionId":"two"}}""")
        transport.receive("""{"jsonrpc":"2.0","id":$firstId,"result":{"sessionId":"one"}}""")

        assertEquals("one", first.await().sessionId)
        assertEquals("two", second.await().sessionId)
    }

    @Test
    fun `error response throws rpc exception`() = runTest(UnconfinedTestDispatcher()) {
        val client = client()
        val result = async { runCatching { client.newSession("/a") } }
        transport.receive("""{"jsonrpc":"2.0","id":${lastId()},"error":{"code":-32000,"message":"boom"}}""")
        val error = result.await().exceptionOrNull()
        assertTrue(error is AcpException.Rpc)
        assertEquals(-32000, (error as AcpException.Rpc).error.code)
    }

    @Test
    fun `request times out`() = runTest(UnconfinedTestDispatcher()) {
        val client = client()
        val result = async { runCatching { client.newSession("/a") } }
        advanceTimeBy(1_001)
        runCurrent()
        assertTrue(result.await().exceptionOrNull() is AcpException.Timeout)
    }

    @Test
    fun `prompt has no timeout`() = runTest(UnconfinedTestDispatcher()) {
        val client = client()
        val result = async { client.prompt("s1", "hi") }
        val id = lastId()
        advanceTimeBy(60_000)
        assertFalse(result.isCompleted)
        transport.receive("""{"jsonrpc":"2.0","id":$id,"result":{"stopReason":"end_turn"}}""")
        val got = result.await()
        assertEquals("end_turn", got)
    }

    @Test
    fun `pending requests fail on disconnect`() = runTest(UnconfinedTestDispatcher()) {
        val client = client()
        val result = async { runCatching { client.newSession("/a") } }
        transport.close()
        assertTrue(result.await().exceptionOrNull() is AcpException.Disconnected)
    }

    @Test
    fun `request while disconnected fails fast`() = runTest(UnconfinedTestDispatcher()) {
        val client = AcpClient(transport, backgroundScope)
        try {
            client.newSession("/a")
            fail("expected NotConnected")
        } catch (e: AcpException.NotConnected) {
        }
    }

    @Test
    fun `session updates are routed to the matching session`() = runTest(UnconfinedTestDispatcher()) {
        val client = client()
        val received = async { client.session("s2").events.first() }
        transport.receive(chunk("s1", "one"))
        transport.receive(chunk("s2", "two"))
        assertEquals("two", (received.await() as AcpEvent.MessageChunk).text)
    }

    @Test
    fun `permission request is surfaced and answered`() = runTest(UnconfinedTestDispatcher()) {
        val client = client()
        val received = async { client.events.first() }
        transport.receive(
            """{"jsonrpc":"2.0","id":9,"method":"session/request_permission","params":{"sessionId":"s1","toolCall":{"toolCallId":"c1"},"options":[{"optionId":"ok","name":"Allow","kind":"allow_once"}]}}"""
        )
        val request = received.await() as AcpEvent.PermissionRequest
        assertEquals(JsonPrimitive(9), request.requestId)

        client.respondPermission(request.requestId, "ok")
        val outcome = transport.lastSent()["result"]!!.jsonObject["outcome"]!!.jsonObject
        assertEquals("selected", outcome["outcome"]!!.jsonPrimitive.content)
        assertEquals("ok", outcome["optionId"]!!.jsonPrimitive.content)

        client.respondPermission(request.requestId, null)
        val cancelled = transport.lastSent()["result"]!!.jsonObject["outcome"]!!.jsonObject
        assertEquals("cancelled", cancelled["outcome"]!!.jsonPrimitive.content)
    }

    @Test
    fun `unsupported agent requests get method not found`() = runTest(UnconfinedTestDispatcher()) {
        client()
        transport.receive("""{"jsonrpc":"2.0","id":4,"method":"fs/read_text_file","params":{}}""")
        val sent = transport.lastSent()
        assertEquals(4, sent["id"]!!.jsonPrimitive.content.toInt())
        assertEquals("-32601", sent["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
    }

    @Test
    fun `malformed frames become protocol errors`() = runTest(UnconfinedTestDispatcher()) {
        val client = client()
        val error = async { client.protocolErrors.first() }
        transport.receive("{broken")
        assertTrue(error.await().startsWith("Invalid JSON"))
    }

    @Test
    fun `cancel sends notification`() = runTest(UnconfinedTestDispatcher()) {
        val client = client()
        assertTrue(client.cancel("s1"))
        val sent = transport.lastSent()
        assertEquals("session/cancel", sent["method"]!!.jsonPrimitive.content)
        assertFalse(sent.containsKey("id"))
    }

    private fun chunk(sessionId: String, text: String) =
        """{"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"$sessionId","update":{"sessionUpdate":"agent_message_chunk","content":{"type":"text","text":"$text"}}}}"""
}
