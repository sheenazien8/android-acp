package com.lakasir.acp.acp

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AcpMessageTest {

    @Test
    fun `round trips all message kinds`() {
        val params = buildJsonObject { put("sessionId", "s1") }
        val messages = listOf(
            AcpMessage.Request(JsonPrimitive(1), "session/prompt", params),
            AcpMessage.Response(JsonPrimitive(1), params),
            AcpMessage.ErrorResponse(JsonPrimitive(2), RpcError(-32601, "nope")),
            AcpMessage.Notification("session/cancel", params),
        )
        messages.forEach { assertEquals(it, AcpMessage.parse(it.encode())) }
    }

    @Test
    fun `encodes jsonrpc version`() {
        val encoded = AcpMessage.Notification("x", null).encode()
        assertTrue(encoded.contains("\"jsonrpc\":\"2.0\""))
    }

    @Test
    fun `string ids are kept`() {
        val parsed = AcpMessage.parse("""{"jsonrpc":"2.0","id":"abc","method":"m"}""")
        assertEquals(AcpMessage.Request(JsonPrimitive("abc"), "m", null), parsed)
    }

    @Test
    fun `null result is a response`() {
        val parsed = AcpMessage.parse("""{"jsonrpc":"2.0","id":3,"result":null}""")
        assertTrue(parsed is AcpMessage.Response)
    }

    @Test(expected = AcpParseException::class)
    fun `malformed json throws parse exception`() {
        AcpMessage.parse("{not json")
    }

    @Test(expected = AcpParseException::class)
    fun `non object throws parse exception`() {
        AcpMessage.parse("[1,2]")
    }

    @Test(expected = AcpParseException::class)
    fun `unrecognized shape throws parse exception`() {
        AcpMessage.parse("""{"jsonrpc":"2.0"}""")
    }
}
