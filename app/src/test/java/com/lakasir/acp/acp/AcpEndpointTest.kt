package com.lakasir.acp.acp

import com.lakasir.acp.data.local.ConnectionProfileEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.UnknownServiceException
import javax.net.ssl.SSLHandshakeException

@OptIn(ExperimentalCoroutinesApi::class)
class AcpEndpointTest {

    @Test
    fun `defaults keep the legacy lan url`() {
        val endpoint = AcpEndpoint.of("192.168.1.10", 8080)
        assertEquals("ws://192.168.1.10:8080/acp", endpoint.url)
        assertFalse(endpoint.isSecure)
        assertTrue(endpoint.headers.isEmpty())
    }

    @Test
    fun `migrated profile maps to the same url`() {
        val profile = ConnectionProfileEntity(1, "LAN", "192.168.1.10", 8080, "/home/dev", 0)
        assertEquals("ws://192.168.1.10:8080/acp", AcpEndpoint.from(profile).url)
    }

    @Test
    fun `paths are normalized`() {
        assertEquals("/acp", AcpEndpoint.normalizePath(""))
        assertEquals("/acp", AcpEndpoint.normalizePath("   "))
        assertEquals("/bridge", AcpEndpoint.normalizePath("bridge"))
        assertEquals("/bridge", AcpEndpoint.normalizePath("/bridge/"))
        assertEquals("/a/b", AcpEndpoint.normalizePath(" /a/b// "))
        assertEquals("/", AcpEndpoint.normalizePath("/"))
    }

    @Test
    fun `wss with custom path and explicit port`() {
        val endpoint = AcpEndpoint.of("bridge.example.com", 443, scheme = "wss", path = "agents/main/")
        assertEquals("wss://bridge.example.com:443/agents/main", endpoint.url)
        assertTrue(endpoint.isSecure)
    }

    @Test
    fun `ipv6 hosts are bracketed`() {
        assertEquals("ws://[::1]:8767/acp", AcpEndpoint.of("::1", 8767).url)
        assertEquals("ws://[fe80::1]:8767/acp", AcpEndpoint.of("[fe80::1]", 8767).url)
    }

    @Test
    fun `token becomes a bearer header and never appears in url or toString`() {
        val endpoint = AcpEndpoint.of("h.example", 443, scheme = "wss", token = "  s3cret-token  ")
        assertEquals(mapOf("Authorization" to "Bearer s3cret-token"), endpoint.headers)
        assertFalse(endpoint.url.contains("s3cret"))
        assertFalse(endpoint.toString().contains("s3cret"))
        assertTrue(AcpEndpoint.of("h", 1, token = "   ").headers.isEmpty())
    }

    @Test
    fun `insecure tls only applies to wss`() {
        assertFalse(AcpEndpoint.of("h", 1, scheme = "ws", allowInsecureTls = true).allowInsecureTls)
        assertTrue(AcpEndpoint.of("h", 1, scheme = "wss", allowInsecureTls = true).allowInsecureTls)
    }

    @Test
    fun `invalid hosts fail validation`() {
        assertFalse(AcpEndpoint.isValid("bad host", 80, "/acp"))
        assertFalse(AcpEndpoint.isValid("a..b", 80, "/acp"))
        assertTrue(AcpEndpoint.isValid("localhost", 80, "/acp"))
    }

    @Test
    fun `client passes url and headers to the transport`() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeTransport()
        val client = AcpClient(transport, backgroundScope)
        client.connect(AcpEndpoint.of("h.example", 8443, scheme = "wss", token = "abc"))
        assertEquals("wss://h.example:8443/acp", transport.lastUrl)
        assertEquals("Bearer abc", transport.lastHeaders["Authorization"])
    }

    @Test
    fun `handshake failures map to readable reasons`() {
        val io = IOException("Expected HTTP 101 response but was '401 Unauthorized'")
        assertEquals(OkHttpAcpTransport.TOKEN_REJECTED, OkHttpAcpTransport.failureReason(io, 401))
        assertEquals(OkHttpAcpTransport.TOKEN_REJECTED, OkHttpAcpTransport.failureReason(io, 403))
        assertEquals(OkHttpAcpTransport.BRIDGE_NOT_READY, OkHttpAcpTransport.failureReason(io, 503))
        assertEquals("Bridge refused the connection (HTTP 404)", OkHttpAcpTransport.failureReason(io, 404))
        assertEquals(OkHttpAcpTransport.TLS_FAILED, OkHttpAcpTransport.failureReason(SSLHandshakeException("bad cert"), null))
        assertEquals(
            OkHttpAcpTransport.CLEARTEXT_BLOCKED,
            OkHttpAcpTransport.failureReason(UnknownServiceException("CLEARTEXT communication to h not permitted"), null),
        )
        assertEquals("timeout", OkHttpAcpTransport.failureReason(IOException("timeout"), null))
    }
}
