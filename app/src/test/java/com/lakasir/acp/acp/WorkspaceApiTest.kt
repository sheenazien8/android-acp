package com.lakasir.acp.acp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceApiTest {

    private val transport = FakeTransport()

    private fun TestScope.client(scope: CoroutineScope = backgroundScope): AcpClient =
        AcpClient(transport, scope, requestTimeoutMs = 1_000).also { it.connect("h", 1) }

    private fun lastId(): Long = transport.lastSent()["id"]!!.jsonPrimitive.content.toLong()

    private fun params(): JsonObject = transport.lastSent()["params"]!!.jsonObject

    @Test
    fun `list sends cwd and path and decodes entries`() = runTest(UnconfinedTestDispatcher()) {
        val api = WorkspaceApi(client(), "/home/me/project")
        val result = async { api.list("src") }
        assertEquals(LakasirMethods.FS_LIST, transport.lastSent()["method"]!!.jsonPrimitive.content)
        assertEquals("/home/me/project", params()["cwd"]!!.jsonPrimitive.content)
        assertEquals("src", params()["path"]!!.jsonPrimitive.content)
        transport.receive(
            """{"jsonrpc":"2.0","id":${lastId()},"result":{"path":"src","truncated":true,"entries":[
                {"name":"main","path":"src/main","type":"dir","size":0,"mtime":5},
                {"name":"a.kt","path":"src/a.kt","type":"file","size":12,"mtime":7,"symlink":true,"extra":1}
            ]}}"""
        )
        val listing = result.await()
        assertTrue(listing.truncated)
        assertEquals(listOf("main", "a.kt"), listing.entries.map { it.name })
        assertTrue(listing.entries[0].isDirectory)
        assertTrue(listing.entries[1].isFile)
        assertTrue(listing.entries[1].symlink)
        assertEquals(12L, listing.entries[1].size)
    }

    @Test
    fun `read decodes binary and truncated files`() = runTest(UnconfinedTestDispatcher()) {
        val api = WorkspaceApi(client(), "/p")
        val result = async { api.read("img.png") }
        transport.receive(
            """{"jsonrpc":"2.0","id":${lastId()},"result":{"path":"img.png","text":null,"binary":true,"truncated":true,"size":900000,"mtime":3}}"""
        )
        val file = result.await()
        assertNull(file.text)
        assertTrue(file.binary)
        assertTrue(file.truncated)
        assertEquals(900_000L, file.size)
    }

    @Test
    fun `write sends expectedMtime only when given`() = runTest(UnconfinedTestDispatcher()) {
        val api = WorkspaceApi(client(), "/p")
        val first = async { api.write("a.txt", "hi", expectedMtime = 42) }
        assertEquals(42L, params()["expectedMtime"]!!.jsonPrimitive.content.toLong())
        transport.receive("""{"jsonrpc":"2.0","id":${lastId()},"result":{"path":"a.txt","mtime":43,"size":2}}""")
        assertEquals(43L, first.await().mtime)

        val second = async { api.write("a.txt", "hi", expectedMtime = null) }
        assertFalse(params().containsKey("expectedMtime"))
        transport.receive("""{"jsonrpc":"2.0","id":${lastId()},"result":{"path":"a.txt","mtime":44}}""")
        assertEquals(44L, second.await().mtime)
    }

    @Test
    fun `conflict surfaces as rpc error`() = runTest(UnconfinedTestDispatcher()) {
        val api = WorkspaceApi(client(), "/p")
        val result = async { runCatching { api.write("a.txt", "x", 1) } }
        transport.receive("""{"jsonrpc":"2.0","id":${lastId()},"error":{"code":-32010,"message":"changed"}}""")
        val error = result.await().exceptionOrNull() as AcpException.Rpc
        assertEquals(LakasirErrorCode.CONFLICT, error.error.code)
    }

    @Test
    fun `hello parses features`() = runTest(UnconfinedTestDispatcher()) {
        val client = client()
        val result = async { WorkspaceApi.hello(client) }
        assertEquals(LakasirMethods.HELLO, transport.lastSent()["method"]!!.jsonPrimitive.content)
        transport.receive("""{"jsonrpc":"2.0","id":${lastId()},"result":{"version":1,"fs":true,"git":false}}""")
        assertEquals(BridgeFeatures(1, fs = true, git = false), result.await())
    }

    @Test
    fun `hello returns null when the bridge has no extensions`() = runTest(UnconfinedTestDispatcher()) {
        val client = client()
        val result = async { WorkspaceApi.hello(client) }
        transport.receive("""{"jsonrpc":"2.0","id":${lastId()},"error":{"code":-32601,"message":"Method not found"}}""")
        assertNull(result.await())
    }

    @Test
    fun `hello returns null when the connection drops`() = runTest(UnconfinedTestDispatcher()) {
        val client = client()
        val result = async { WorkspaceApi.hello(client) }
        client.disconnect()
        assertNull(result.await())
    }

    @Test
    fun `prompt sends text and resource links`() = runTest(UnconfinedTestDispatcher()) {
        val client = client()
        backgroundScope.launch { client.prompt("s1", "Review these", listOf(ResourceLink.forWorkspaceFile("/p", "src/a.kt"))) }
        val prompt = params()["prompt"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("text", "resource_link"), prompt.map { it["type"]!!.jsonPrimitive.content })
        assertEquals("file:///p/src/a.kt", prompt[1]["uri"]!!.jsonPrimitive.content)
        assertEquals("a.kt", prompt[1]["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `prompt with only attachments omits the text block`() = runTest(UnconfinedTestDispatcher()) {
        val client = client()
        backgroundScope.launch { client.prompt("s1", "", listOf(ResourceLink.forWorkspaceFile("/p", "a.kt"))) }
        val prompt = params()["prompt"]!!.jsonArray
        assertEquals(1, prompt.size)
        assertEquals("resource_link", prompt[0].jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `git status decodes branch and file changes`() = runTest(UnconfinedTestDispatcher()) {
        val api = WorkspaceApi(client(), "/p")
        val result = async { api.gitStatus() }
        assertEquals(LakasirMethods.GIT_STATUS, transport.lastSent()["method"]!!.jsonPrimitive.content)
        transport.receive(
            """{"jsonrpc":"2.0","id":${lastId()},"result":{"isRepo":true,"branch":"main","detached":false,"oid":"abc1234",
                "upstream":null,"ahead":1,"behind":0,"truncated":false,"files":[
                {"path":"a.kt","origPath":null,"index":"M","worktree":null,"conflicted":false,"untracked":false},
                {"path":"new/","origPath":null,"index":null,"worktree":null,"conflicted":false,"untracked":true}
            ]}}"""
        )
        val status = result.await()
        assertEquals("main", status.branch)
        assertEquals(1, status.ahead)
        assertTrue(status.files[0].isStaged)
        assertTrue(status.files[1].untracked)
        assertTrue(status.files[1].isDirectory)
    }

    @Test
    fun `git not a repository decodes with defaults`() = runTest(UnconfinedTestDispatcher()) {
        val api = WorkspaceApi(client(), "/p")
        val result = async { api.gitStatus() }
        transport.receive("""{"jsonrpc":"2.0","id":${lastId()},"result":{"isRepo":false,"files":[]}}""")
        assertFalse(result.await().isRepo)
    }

    @Test
    fun `git stage sends paths and diff sends origPath`() = runTest(UnconfinedTestDispatcher()) {
        val api = WorkspaceApi(client(), "/p")
        val stage = async { api.gitStage(listOf("a.kt", "b.kt")) }
        assertEquals(listOf("a.kt", "b.kt"), params()["paths"]!!.jsonArray.map { it.jsonPrimitive.content })
        transport.receive("""{"jsonrpc":"2.0","id":${lastId()},"result":{}}""")
        stage.await()

        val diff = async { api.gitDiff("new.md", staged = true, origPath = "old.md") }
        assertEquals("old.md", params()["origPath"]!!.jsonPrimitive.content)
        assertEquals("true", params()["staged"]!!.jsonPrimitive.content)
        transport.receive("""{"jsonrpc":"2.0","id":${lastId()},"result":{"path":"new.md","oldText":null,"newText":"x"}}""")
        assertNull(diff.await().oldText)
    }

    @Test
    fun `git commit decodes the new hash`() = runTest(UnconfinedTestDispatcher()) {
        val api = WorkspaceApi(client(), "/p")
        val result = async { api.gitCommit("Fix it") }
        assertEquals("Fix it", params()["message"]!!.jsonPrimitive.content)
        transport.receive("""{"jsonrpc":"2.0","id":${lastId()},"result":{"hash":"abcdef1234","shortHash":"abcdef1"}}""")
        assertEquals("abcdef1", result.await().shortHash)
    }

    @Test
    fun `resource link uri is absolute and percent-encoded`() {
        assertEquals(
            "file:///home/me/my%20project/src/caf%C3%A9%23.kt",
            ResourceLink.forWorkspaceFile("/home/me/my project/", "src/café#.kt").uri,
        )
        assertEquals("café#.kt", ResourceLink.forWorkspaceFile("/p", "src/café#.kt").name)
        assertEquals("file:///p/a.kt", ResourceLink.forWorkspaceFile("/p", "/a.kt").uri)
    }
}
