package com.lakasir.acp.ui.chat

import com.lakasir.acp.data.local.MessageEntity
import com.lakasir.acp.data.local.MessageRole
import com.lakasir.acp.data.local.MessageType
import com.lakasir.acp.data.model.ToolCallState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatItemMapperTest {

    private fun message(id: Long, role: MessageRole, type: MessageType, content: String) =
        MessageEntity(id = id, sessionId = 1, role = role, type = type, content = content, timestamp = id)

    @Test
    fun `maps each message type`() {
        val items = ChatItemMapper().map(
            listOf(
                message(1, MessageRole.USER, MessageType.TEXT, "hi"),
                message(2, MessageRole.AGENT, MessageType.TEXT, "hello"),
                message(3, MessageRole.SYSTEM, MessageType.TEXT, "Stopped: max_tokens"),
                message(4, MessageRole.TOOL, MessageType.TOOL_CALL, ToolCallState("c1", title = "Read").encode()),
                message(5, MessageRole.TOOL, MessageType.TOOL_CALL, "broken"),
                message(6, MessageRole.SYSTEM, MessageType.ERROR, "boom"),
            )
        )
        assertTrue(items[0] is ChatItem.UserText)
        assertTrue(items[1] is ChatItem.AgentText)
        assertTrue(items[2] is ChatItem.SystemNote)
        assertEquals("Read", (items[3] as ChatItem.Tool).state.title)
        assertTrue(items[4] is ChatItem.SystemNote)
        assertTrue(items[5] is ChatItem.Error)
    }

    @Test
    fun `unchanged messages reuse cached items`() {
        val mapper = ChatItemMapper()
        val first = message(1, MessageRole.USER, MessageType.TEXT, "hi")
        val streaming = message(2, MessageRole.AGENT, MessageType.TEXT, "he")
        val before = mapper.map(listOf(first, streaming))
        val after = mapper.map(listOf(first, streaming.copy(content = "hello")))
        assertSame(before[0], after[0])
        assertNotSame(before[1], after[1])
    }
}
