package com.lakasir.acp.acp

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AcpEventParserTest {

    private fun update(body: String) =
        AcpEventParser.parseSessionUpdate(AcpJson.parseToJsonElement("""{"sessionId":"s1","update":$body}"""))

    @Test
    fun `agent message chunk`() {
        val event = update("""{"sessionUpdate":"agent_message_chunk","content":{"type":"text","text":"hi"}}""")
        assertEquals("s1", event.sessionId)
        assertEquals("hi", (event as AcpEvent.MessageChunk).text)
    }

    @Test
    fun `thought and user chunks`() {
        val thought = update("""{"sessionUpdate":"agent_thought_chunk","content":{"type":"text","text":"hmm"}}""")
        val user = update("""{"sessionUpdate":"user_message_chunk","content":{"type":"text","text":"yo"}}""")
        assertEquals("hmm", (thought as AcpEvent.ThoughtChunk).text)
        assertEquals("yo", (user as AcpEvent.UserMessageChunk).text)
    }

    @Test
    fun `tool call with raw input`() {
        val event = update(
            """{"sessionUpdate":"tool_call","toolCallId":"c1","title":"Read file","kind":"read","status":"pending","rawInput":{"path":"/a"}}"""
        ) as AcpEvent.ToolCallStarted
        assertEquals("c1", event.toolCall.toolCallId)
        assertEquals("Read file", event.toolCall.title)
        assertEquals(ToolCallStatus.PENDING, event.toolCall.status)
        assertTrue(event.toolCall.rawInput != null)
    }

    @Test
    fun `tool call update with text and diff content`() {
        val event = update(
            """{"sessionUpdate":"tool_call_update","toolCallId":"c1","status":"completed","content":[
                {"type":"content","content":{"type":"text","text":"done"}},
                {"type":"diff","path":"/a.txt","oldText":"x","newText":"y"},
                {"type":"terminal","terminalId":"t1"}
            ]}"""
        ) as AcpEvent.ToolCallUpdated
        assertEquals(ToolCallStatus.COMPLETED, event.toolCall.status)
        assertEquals(
            listOf(ToolContent.Text("done"), ToolContent.Diff("/a.txt", "x", "y"), ToolContent.Terminal("t1")),
            event.toolCall.content,
        )
    }

    @Test
    fun `diff without old text is a new file`() {
        val event = update(
            """{"sessionUpdate":"tool_call_update","toolCallId":"c1","content":[{"type":"diff","path":"/n","newText":"y"}]}"""
        ) as AcpEvent.ToolCallUpdated
        assertEquals(ToolContent.Diff("/n", null, "y"), event.toolCall.content!!.single())
    }

    @Test
    fun `plan entries`() {
        val event = update(
            """{"sessionUpdate":"plan","entries":[{"content":"step","status":"pending","priority":"high"}]}"""
        ) as AcpEvent.Plan
        assertEquals(listOf(PlanEntry("step", "pending", "high")), event.entries)
    }

    @Test
    fun `unknown kind is preserved`() {
        val event = update("""{"sessionUpdate":"available_commands_update","availableCommands":[]}""")
        assertEquals("available_commands_update", (event as AcpEvent.Unknown).kind)
    }

    @Test(expected = AcpParseException::class)
    fun `missing session id throws`() {
        AcpEventParser.parseSessionUpdate(AcpJson.parseToJsonElement("""{"update":{"sessionUpdate":"plan"}}"""))
    }

    @Test
    fun `permission request`() {
        val params = AcpJson.parseToJsonElement(
            """{"sessionId":"s1","toolCall":{"toolCallId":"c1","title":"rm -rf"},"options":[
                {"optionId":"allow","name":"Allow","kind":"allow_once"},
                {"optionId":"deny","name":"Deny","kind":"reject_once"}]}"""
        )
        val event = AcpEventParser.parsePermissionRequest(JsonPrimitive(5), params)
        assertEquals("c1", event.toolCall.toolCallId)
        assertEquals(listOf("allow", "deny"), event.options.map { it.optionId })
        assertEquals(PermissionOptionKind.REJECT_ONCE, event.options[1].kind)
    }
}
